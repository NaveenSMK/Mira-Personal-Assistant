package com.example.voicefirst

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

interface LocalModelEngine {
    suspend fun load(modelUri: Uri): ModelLoadResult
    suspend fun generate(prompt: String): ModelGenerationResult
    fun isLoaded(): Boolean
    suspend fun unload()
}

data class ModelLoadResult(val success: Boolean, val message: String)
data class ModelGenerationResult(val success: Boolean, val text: String, val message: String = "")

/**
 * LiteRT-LM adapter for .litertlm bundles. This class is isolated behind LocalModelEngine,
 * so another runtime can be added without changing the agent, memory, tools, or tasks.
 */
class LiteRtLocalModelEngine(context: Context) : LocalModelEngine {
    companion object {
        private const val MAX_MODEL_BYTES = 6L * 1024L * 1024L * 1024L
        private const val RESERVED_FREE_BYTES = 256L * 1024L * 1024L
    }

    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val inferenceMutex = Mutex()

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var activeModelFile: File? = null

    override suspend fun load(modelUri: Uri): ModelLoadResult = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(modelUri)
            if (!displayName.endsWith(".litertlm", ignoreCase = true)) {
                return@withContext ModelLoadResult(
                    false,
                    "This runtime accepts .litertlm bundles only. GGUF files need a separate llama.cpp runtime adapter."
                )
            }

            val stagingFile = File(modelDir, "voicefirst-model.staging")
            val candidateFile = File(modelDir, "voicefirst-${System.currentTimeMillis()}.litertlm")
            stagingFile.delete()

            try {
                val input = appContext.contentResolver.openInputStream(modelUri)
                    ?: return@withContext ModelLoadResult(false, "Couldn't read the selected model file.")
                input.use { copyModelBounded(it, stagingFile) }

                if (!stagingFile.isFile || stagingFile.length() < 1_000_000L) {
                    stagingFile.delete()
                    return@withContext ModelLoadResult(false, "This file doesn't look like a valid model bundle.")
                }
                if (!stagingFile.renameTo(candidateFile)) {
                    stagingFile.delete()
                    return@withContext ModelLoadResult(false, "Couldn't stage the selected model file.")
                }

                // Keep the currently active model available until the candidate initializes.
                val candidateEngine = try {
                    createInitializedEngine(candidateFile.absolutePath, useGpu = true)
                } catch (_: Exception) {
                    try {
                        createInitializedEngine(candidateFile.absolutePath, useGpu = false)
                    } catch (e: Exception) {
                        candidateFile.delete()
                        return@withContext ModelLoadResult(
                            false, "Model couldn't initialize: ${e.message ?: e.javaClass.simpleName}"
                        )
                    }
                }

                val candidateConversation = try {
                    candidateEngine.createConversation()
                } catch (e: Exception) {
                    runCatching { candidateEngine.close() }
                    candidateFile.delete()
                    return@withContext ModelLoadResult(
                        false, "Couldn't start a conversation: ${e.message ?: e.javaClass.simpleName}"
                    )
                }

                // Swap only after the candidate is ready. Keep its path unchanged because
                // native runtimes may lazily access model resources after initialization.
                val previousFile = activeModelFile
                closeActiveModel()
                engine = candidateEngine
                conversation = candidateConversation
                activeModelFile = candidateFile
                if (previousFile != null && previousFile != candidateFile) previousFile.delete()

                ModelLoadResult(true, "Local model loaded. Memories, tasks, and tools remain independent of the model.")
            } catch (cancelled: CancellationException) {
                stagingFile.delete()
                candidateFile.delete()
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                stagingFile.delete()
                candidateFile.delete()
                ModelLoadResult(false, "The model needs more memory than Android could provide. Try a smaller or more compatible bundle.")
            } catch (e: Exception) {
                stagingFile.delete()
                candidateFile.delete()
                ModelLoadResult(false, "Model couldn't be loaded: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Prevent a selected document from exhausting private app storage. */
    private fun copyModelBounded(input: InputStream, destination: File) {
        val availableForModel = (modelDir.usableSpace - RESERVED_FREE_BYTES).coerceAtLeast(0L)
        val limit = minOf(MAX_MODEL_BYTES, availableForModel)
        require(limit >= 1_000_000L) { "Not enough free storage to safely stage a model." }
        var copied = 0L
        BufferedInputStream(input).use { source ->
            BufferedOutputStream(destination.outputStream()).use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= limit) {
                        "Model file exceeds the safe copy limit or available storage. Free space and try again."
                    }
                    sink.write(buffer, 0, count)
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "model.litertlm"
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = cursor.getString(index) ?: name
                    }
                }
        }
        return name
    }

    private fun createInitializedEngine(path: String, useGpu: Boolean): Engine {
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val candidate = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                cacheDir = appContext.cacheDir.absolutePath
            )
        )
        try {
            candidate.initialize()
            return candidate
        } catch (t: Throwable) {
            runCatching { candidate.close() }
            throw t
        }
    }

    override suspend fun generate(prompt: String): ModelGenerationResult =
        inferenceMutex.withLock {
            withContext(Dispatchers.IO) {
                val chat = conversation
                    ?: return@withContext ModelGenerationResult(false, "", "Load a local model first.")
                try {
                    val output = StringBuilder()
                    chat.sendMessageAsync(prompt).collect { message ->
                        val part = message.contents.toString()
                        if (part.isNotBlank()) output.append(part)
                    }
                    val answer = output.toString().trim()
                    if (answer.isBlank()) ModelGenerationResult(false, "", "The model returned an empty response.")
                    else ModelGenerationResult(true, answer)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    ModelGenerationResult(false, "", "Local inference failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

    override fun isLoaded(): Boolean = conversation != null && engine != null

    override suspend fun unload() = inferenceMutex.withLock {
        withContext(Dispatchers.IO) { closeActiveModel() }
    }

    /** Must be called while holding inferenceMutex. */
    private fun closeActiveModel() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        activeModelFile = null
    }
}

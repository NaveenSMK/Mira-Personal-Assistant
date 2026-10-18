package com.naveensmk.mira

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
    /** Reopen the last model imported into private app storage, if one exists. */
    suspend fun loadStoredModel(): ModelLoadResult = ModelLoadResult(false, "No saved local model is available.")
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
        private const val KEY_MODEL_FILE = "model_file"
        private const val KEY_MODEL_DISPLAY_NAME = "model_display_name"
        private const val KEY_MODEL_PROVIDER = "model_provider"
    }

    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val modelPreferences = appContext.getSharedPreferences("mira_model", Context.MODE_PRIVATE)
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

            val stagingFile = File(modelDir, "mira-model.staging")
            val candidateFile = File(modelDir, "mira-${System.currentTimeMillis()}.litertlm")
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
                modelPreferences.edit()
                    .putString(KEY_MODEL_FILE, candidateFile.name)
                    .putString(KEY_MODEL_DISPLAY_NAME, displayName)
                    .putString(KEY_MODEL_PROVIDER, "litert-lm")
                    .apply()
                modelDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) && it != candidateFile }
                    ?.forEach { runCatching { it.delete() } }
                if (previousFile != null && previousFile != candidateFile) runCatching { previousFile.delete() }

                ModelLoadResult(true, "Model imported and loaded: $displayName. MIRA will restore it automatically on future launches.")
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

    override suspend fun loadStoredModel(): ModelLoadResult = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            if (engine != null && conversation != null) {
                return@withContext ModelLoadResult(true, "Local model is already loaded.")
            }
            val savedName = modelPreferences.getString(KEY_MODEL_FILE, null)
            val storedFile = savedName?.let { name ->
                val candidate = File(modelDir, name)
                runCatching { candidate.canonicalFile.parentFile == modelDir.canonicalFile }.getOrDefault(false)
                    .takeIf { it }?.let { candidate }
            }?.takeIf { it.isFile && it.length() >= 1_000_000L }
                ?: modelDir.listFiles()?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) && it.length() >= 1_000_000L }
                    ?.maxByOrNull { it.lastModified() }

            if (storedFile == null) {
                return@withContext ModelLoadResult(false, "No saved local model yet. Import one in Settings → On-device model.")
            }

            try {
                val candidateEngine = try {
                    createInitializedEngine(storedFile.absolutePath, useGpu = true)
                } catch (_: Exception) {
                    createInitializedEngine(storedFile.absolutePath, useGpu = false)
                }
                val candidateConversation = try {
                    candidateEngine.createConversation()
                } catch (e: Exception) {
                    runCatching { candidateEngine.close() }
                    throw e
                }
                closeActiveModel()
                engine = candidateEngine
                conversation = candidateConversation
                activeModelFile = storedFile
                val displayName = modelPreferences.getString(KEY_MODEL_DISPLAY_NAME, storedFile.name) ?: storedFile.name
                modelPreferences.edit().putString(KEY_MODEL_FILE, storedFile.name).putString(KEY_MODEL_PROVIDER, "litert-lm").apply()
                ModelLoadResult(true, "Local model ready: $displayName")
            } catch (memory: OutOfMemoryError) {
                closeActiveModel()
                ModelLoadResult(false, "Android ran out of memory while restoring the model. Try a smaller compatible model or close other apps.")
            } catch (e: Exception) {
                closeActiveModel()
                ModelLoadResult(false, "Saved model could not be restored: ${e.message ?: e.javaClass.simpleName}. Update it in Settings.")
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
                val activeEngine = engine
                    ?: return@withContext ModelGenerationResult(false, "", "Load a local model first.")

                // LiteRT-LM supports one active session per engine for these bundles.
                // Release the prior session synchronously before opening a new one.
                val previousConversation = conversation
                conversation = null
                runCatching { previousConversation?.close() }
                val chat = try {
                    activeEngine.createConversation()
                } catch (e: Exception) {
                    return@withContext ModelGenerationResult(
                        false, "", "Couldn't start a fresh model conversation: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
                conversation = chat

                suspend fun collectReply(target: Conversation, request: String): String {
                    val output = StringBuilder()
                    target.sendMessageAsync(request).collect { message ->
                        val part = message.contents.toString()
                        if (part.isNotBlank()) output.append(part)
                    }
                    return output.toString().trim()
                }

                val boundedPrompt = prompt.take(2_000)
                try {
                    val answer = collectReply(chat, boundedPrompt)
                    if (answer.isBlank()) ModelGenerationResult(false, "", "The model returned an empty response.")
                    else ModelGenerationResult(true, answer)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (first: Exception) {
                    val failureDetails = generateSequence(first as Throwable?) { it.cause }
                        .mapNotNull { it.message }.joinToString(" | ").ifBlank { first.javaClass.simpleName }
                    val isPrefillCapacityError = failureDetails.contains("prefill", ignoreCase = true) ||
                        failureDetails.contains("state entries", ignoreCase = true) ||
                        failureDetails.contains("remaining capacity", ignoreCase = true)
                    if (!isPrefillCapacityError) {
                        ModelGenerationResult(false, "", "Local inference failed: $failureDetails")
                    } else {
                        // A new session plus a smaller prompt gives the bundle room for its
                        // smallest supported prefill signature. Close synchronously first.
                        conversation = null
                        runCatching { chat.close() }
                        val retryChat = try { activeEngine.createConversation() } catch (_: Exception) { null }
                        if (retryChat == null) {
                            ModelGenerationResult(false, "", "Local inference ran out of prefill state. Try a shorter request or update the model bundle in Settings.")
                        } else {
                            conversation = retryChat
                            val compact = if (boundedPrompt.length > 1_100) {
                                boundedPrompt.take(450) + "\n[Optional context trimmed to fit model capacity.]\n" + boundedPrompt.takeLast(600)
                            } else boundedPrompt
                            try {
                                val answer = collectReply(retryChat, compact)
                                if (answer.isBlank()) ModelGenerationResult(false, "", "Local inference returned no text after a prefill retry.")
                                else ModelGenerationResult(true, answer)
                            } catch (retryError: Exception) {
                                ModelGenerationResult(false, "", "Local inference failed after a shorter-prompt retry: ${retryError.message ?: retryError.javaClass.simpleName}. Try a smaller prompt or update the model bundle in Settings.")
                            }
                        }
                    }
                }
            }
        }

    override fun isLoaded(): Boolean = engine != null && activeModelFile?.isFile == true

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

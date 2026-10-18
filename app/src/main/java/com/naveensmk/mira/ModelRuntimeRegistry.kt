package com.naveensmk.mira

import android.content.Context

/**
 * Provider-neutral runtime registry. Agent, memory, tool policy, and UI depend on
 * LocalModelEngine rather than a particular model family or vendor API.
 */
interface ModelRuntimeProvider {
    val id: String
    val displayName: String
    val supportedExtensions: Set<String>
    fun create(context: Context): LocalModelEngine
    fun availabilityMessage(): String
}

class LiteRtLmProvider : ModelRuntimeProvider {
    override val id = "litert-lm"
    override val displayName = "LiteRT-LM (.litertlm)"
    override val supportedExtensions = setOf("litertlm")
    override fun create(context: Context): LocalModelEngine = LiteRtLocalModelEngine(context)
    override fun availabilityMessage() = "Available for compatible .litertlm bundles."
}

class LlamaCppProvider : ModelRuntimeProvider {
    override val id = "llama.cpp"
    override val displayName = "llama.cpp (.gguf)"
    override val supportedExtensions = setOf("gguf")
    override fun create(context: Context): LocalModelEngine = UnavailableLocalModelEngine(
        "The llama.cpp adapter is planned but not installed. GGUF files cannot be loaded by LiteRT-LM."
    )
    override fun availabilityMessage() = "Planned; needs a separate tested native/JNI adapter."
}

class UnavailableLocalModelEngine(private val reason: String) : LocalModelEngine {
    override suspend fun load(modelUri: android.net.Uri) = ModelLoadResult(false, reason)
    override suspend fun generate(prompt: String) = ModelGenerationResult(false, "", reason)
    override fun isLoaded() = false
    override suspend fun unload() = Unit
}

class ModelRuntimeRegistry(
    providers: List<ModelRuntimeProvider> = listOf(LiteRtLmProvider(), LlamaCppProvider())
) {
    private val byId = providers.associateBy { it.id }
    fun all(): List<ModelRuntimeProvider> = byId.values.toList()
    fun find(id: String): ModelRuntimeProvider? = byId[id]
    fun providerForFileName(fileName: String): ModelRuntimeProvider? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return byId.values.firstOrNull { extension in it.supportedExtensions }
    }
}

data class ModelProfile(
    val id: String,
    val displayName: String,
    val providerId: String,
    val fileExtension: String,
    val downloadPage: String
)

object BuiltInModelCatalog {
    val defaultProfile = ModelProfile(
        id = "gemma-4-e2b-instruct-litert",
        displayName = "Gemma 4 E2B Instruct",
        providerId = "litert-lm",
        fileExtension = "litertlm",
        downloadPage = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    )
    fun profiles(): List<ModelProfile> = listOf(defaultProfile)
}

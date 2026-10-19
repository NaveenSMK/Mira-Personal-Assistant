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


class ModelRuntimeRegistry(
    providers: List<ModelRuntimeProvider> = listOf(LiteRtLmProvider())
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


/**
 * Owns the large native model engine for the lifetime of the app process, not an Activity.
 * Activity recreation must never initialize a second multi-gigabyte model beside the first.
 */
object MiraModelEngineOwner {
    @Volatile private var engine: LocalModelEngine? = null
    @Volatile private var providerId: String? = null

    @Synchronized
    fun getOrCreate(context: Context, provider: ModelRuntimeProvider): LocalModelEngine {
        val existing = engine
        if (existing != null && providerId == provider.id) return existing
        check(existing == null) {
            "A different model runtime is already active. Unload it explicitly before changing providers."
        }
        return provider.create(context.applicationContext).also {
            engine = it
            providerId = provider.id
        }
    }
}

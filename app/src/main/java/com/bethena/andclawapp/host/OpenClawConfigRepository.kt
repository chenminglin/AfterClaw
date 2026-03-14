package com.bethena.andclawapp.host

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REDACTED_SENTINEL = "__OPENCLAW_REDACTED__"

data class ConfigFieldHint(
    val label: String? = null,
    val help: String? = null,
    val hintPath: String? = null,
)

data class OpenClawCoreConfigHints(
    val workspace: ConfigFieldHint = ConfigFieldHint(),
    val repoRoot: ConfigFieldHint = ConfigFieldHint(),
    val primaryModel: ConfigFieldHint = ConfigFieldHint(),
    val fallbackModels: ConfigFieldHint = ConfigFieldHint(),
    val providers: ConfigFieldHint = ConfigFieldHint(),
)

data class ProviderConfigItem(
    val rowId: String = UUID.randomUUID().toString(),
    val originalProviderId: String? = null,
    val providerId: String = "",
    val api: String = "",
    val baseUrl: String = "",
    val apiKeyInput: String = "",
    val apiKeyChanged: Boolean = false,
    val hasExistingApiKey: Boolean = false,
)

data class OpenClawCoreConfigForm(
    val workspace: String = "",
    val repoRoot: String = "",
    val primaryModel: String = "",
    val fallbackModels: List<String> = emptyList(),
    val providers: List<ProviderConfigItem> = emptyList(),
)

data class OpenClawCoreConfigSnapshot(
    val baseHash: String,
    val configPath: String,
    val baseConfigJson: String,
    val previewJson: String,
    val hints: OpenClawCoreConfigHints,
    val form: OpenClawCoreConfigForm,
)

class OpenClawConfigRepository(
    private val hostController: AndClawHostController,
) {
    suspend fun loadCoreConfig(): OpenClawCoreConfigSnapshot =
        withContext(Dispatchers.IO) {
            val snapshot = hostController.callGatewayRpc("config.get", JSONObject())
            val baseHash =
                snapshot.optString("hash").takeIf { it.isNotBlank() }
                    ?: throw IOException("未能读取配置版本，请重新加载后再试。")
            val configPath = snapshot.optString("path").ifBlank { "openclaw.json" }
            val config = snapshot.optJSONObject("config") ?: JSONObject()
            val prettyJson = config.toString(2)

            OpenClawCoreConfigSnapshot(
                baseHash = baseHash,
                configPath = configPath,
                baseConfigJson = prettyJson,
                previewJson = prettyJson,
                hints = loadHints(),
                form = config.toCoreConfigForm(),
            )
        }

    suspend fun saveCoreConfig(
        snapshot: OpenClawCoreConfigSnapshot,
        form: OpenClawCoreConfigForm,
    ): OpenClawCoreConfigSnapshot =
        withContext(Dispatchers.IO) {
            validateProviders(form.providers)

            val mergedConfig = JSONObject(snapshot.baseConfigJson)
            mergeCoreConfigForm(
                target = mergedConfig,
                form = form,
            )

            hostController.callGatewayRpc(
                method = "config.set",
                params =
                    JSONObject()
                        .put("raw", mergedConfig.toString(2))
                        .put("baseHash", snapshot.baseHash),
            )

            loadCoreConfig()
        }

    private suspend fun loadHints(): OpenClawCoreConfigHints {
        val workspaceLookup = hostController.callGatewayRpc("config.schema.lookup", jsonObjectOf("path" to "agents.defaults.workspace"))
        val repoRootLookup = hostController.callGatewayRpc("config.schema.lookup", jsonObjectOf("path" to "agents.defaults.repoRoot"))
        val modelLookup = hostController.callGatewayRpc("config.schema.lookup", jsonObjectOf("path" to "agents.defaults.model"))
        val providersLookup = hostController.callGatewayRpc("config.schema.lookup", jsonObjectOf("path" to "models.providers"))

        return OpenClawCoreConfigHints(
            workspace = workspaceLookup.toFieldHint(),
            repoRoot = repoRootLookup.toFieldHint(),
            primaryModel = modelLookup.childHint("primary"),
            fallbackModels = modelLookup.childHint("fallbacks"),
            providers = providersLookup.toFieldHint(),
        )
    }

    private fun validateProviders(providers: List<ProviderConfigItem>) {
        val seen = linkedSetOf<String>()
        providers.forEachIndexed { index, provider ->
            val providerId = provider.providerId.trim()
            if (providerId.isEmpty()) {
                throw IOException("第 ${index + 1} 个 Provider 还没有填写 providerId。")
            }
            if (!seen.add(providerId)) {
                throw IOException("Provider ID \"$providerId\" 重复了，请修改后再保存。")
            }
        }
    }

    private fun mergeCoreConfigForm(
        target: JSONObject,
        form: OpenClawCoreConfigForm,
    ) {
        val agents = ensureObject(target, "agents")
        val defaults = ensureObject(agents, "defaults")
        val model = ensureObject(defaults, "model")
        val models = ensureObject(target, "models")

        putOrRemoveString(defaults, "workspace", form.workspace)
        putOrRemoveString(defaults, "repoRoot", form.repoRoot)
        putOrRemoveString(model, "primary", form.primaryModel)

        val cleanedFallbacks =
            form.fallbackModels
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (cleanedFallbacks.isEmpty()) {
            model.remove("fallbacks")
        } else {
            model.put("fallbacks", JSONArray(cleanedFallbacks))
        }

        val existingProviders = models.optJSONObject("providers") ?: JSONObject()
        val nextProviders = JSONObject()
        form.providers.forEach { provider ->
            val providerId = provider.providerId.trim()
            val baseProvider =
                copyExistingProvider(
                    providers = existingProviders,
                    provider = provider,
                )

            putOrRemoveString(baseProvider, "api", provider.api)
            putOrRemoveString(baseProvider, "baseUrl", provider.baseUrl)
            if (provider.apiKeyChanged) {
                putOrRemoveString(baseProvider, "apiKey", provider.apiKeyInput)
            }
            nextProviders.put(providerId, baseProvider)
        }

        if (nextProviders.length() == 0) {
            models.remove("providers")
        } else {
            models.put("providers", nextProviders)
        }

        cleanupEmptyObject(defaults, "model")
        cleanupEmptyObject(agents, "defaults")
        cleanupEmptyObject(target, "agents")
        cleanupEmptyObject(target, "models")
    }

    private fun copyExistingProvider(
        providers: JSONObject,
        provider: ProviderConfigItem,
    ): JSONObject {
        val sourceId =
            provider.originalProviderId?.takeIf { providers.has(it) }
                ?: provider.providerId.trim().takeIf { it.isNotEmpty() && providers.has(it) }
        val baseProvider = sourceId?.let { providers.optJSONObject(it) }
        return if (baseProvider != null) {
            JSONObject(baseProvider.toString())
        } else {
            JSONObject()
        }
    }
}

private fun JSONObject.toCoreConfigForm(): OpenClawCoreConfigForm {
    val defaults = optJSONObject("agents")?.optJSONObject("defaults")
    val model = defaults?.optJSONObject("model")
    val providers = optJSONObject("models")?.optJSONObject("providers")

    return OpenClawCoreConfigForm(
        workspace = defaults?.optString("workspace").orEmpty(),
        repoRoot = defaults?.optString("repoRoot").orEmpty(),
        primaryModel = model?.optString("primary").orEmpty(),
        fallbackModels = model?.optStringList("fallbacks").orEmpty(),
        providers =
            providers
                ?.let(::providerIds)
                ?.map { providerId ->
                    val provider = providers.optJSONObject(providerId) ?: JSONObject()
                    val apiKeyValue = provider.opt("apiKey")
                    ProviderConfigItem(
                        originalProviderId = providerId,
                        providerId = providerId,
                        api = provider.optString("api").orEmpty(),
                        baseUrl = provider.optString("baseUrl").orEmpty(),
                        apiKeyInput =
                            if (apiKeyValue is String && apiKeyValue != REDACTED_SENTINEL) {
                                apiKeyValue
                            } else {
                                ""
                            },
                        hasExistingApiKey = apiKeyValue != null && apiKeyValue != JSONObject.NULL,
                    )
                }
                .orEmpty(),
    )
}

private fun JSONObject.toFieldHint(): ConfigFieldHint {
    val hint = optJSONObject("hint")
    return ConfigFieldHint(
        label = hint?.optString("label")?.takeIf { it.isNotBlank() },
        help = hint?.optString("help")?.takeIf { it.isNotBlank() },
        hintPath = optString("hintPath").takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.childHint(key: String): ConfigFieldHint {
    val child =
        optJSONArray("children")
            ?.firstObjectOrNull { it.optString("key") == key }
    return ConfigFieldHint(
        label = child?.optJSONObject("hint")?.optString("label")?.takeIf { it.isNotBlank() },
        help = child?.optJSONObject("hint")?.optString("help")?.takeIf { it.isNotBlank() },
        hintPath = child?.optString("hintPath")?.takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.optStringList(key: String): List<String>? {
    val array = optJSONArray(key) ?: return null
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

private fun JSONArray.firstObjectOrNull(predicate: (JSONObject) -> Boolean): JSONObject? {
    for (index in 0 until length()) {
        val candidate = optJSONObject(index) ?: continue
        if (predicate(candidate)) {
            return candidate
        }
    }
    return null
}

private fun ensureObject(target: JSONObject, key: String): JSONObject {
    val existing = target.optJSONObject(key)
    if (existing != null) {
        return existing
    }
    return JSONObject().also { target.put(key, it) }
}

private fun cleanupEmptyObject(target: JSONObject, key: String) {
    val nested = target.optJSONObject(key) ?: return
    if (nested.length() == 0) {
        target.remove(key)
    }
}

private fun putOrRemoveString(target: JSONObject, key: String, value: String) {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        target.remove(key)
    } else {
        target.put(key, trimmed)
    }
}

private fun providerIds(providers: JSONObject): List<String> {
    val ids = mutableListOf<String>()
    val iterator = providers.keys()
    while (iterator.hasNext()) {
        ids += iterator.next()
    }
    return ids.sorted()
}

private fun jsonObjectOf(vararg pairs: Pair<String, String>): JSONObject {
    return JSONObject().apply {
        pairs.forEach { (key, value) ->
            put(key, value)
        }
    }
}

package com.bethena.andclawapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bethena.andclawapp.host.OpenClawConfigRepository
import com.bethena.andclawapp.host.OpenClawCoreConfigForm
import com.bethena.andclawapp.host.OpenClawCoreConfigHints
import com.bethena.andclawapp.host.OpenClawCoreConfigSnapshot
import com.bethena.andclawapp.host.ProviderConfigItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OpenClawConfigScreenState(
    private val repository: OpenClawConfigRepository,
    private val scope: CoroutineScope,
) {
    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    var saveMessage by mutableStateOf<String?>(null)
        private set

    var snapshot by mutableStateOf<OpenClawCoreConfigSnapshot?>(null)
        private set

    var form by mutableStateOf(OpenClawCoreConfigForm())
        private set

    var fallbackModelsText by mutableStateOf("")
        private set

    val hints: OpenClawCoreConfigHints
        get() = snapshot?.hints ?: OpenClawCoreConfigHints()

    val configPath: String?
        get() = snapshot?.configPath

    val previewJson: String
        get() = snapshot?.previewJson.orEmpty()

    val hasLoadedData: Boolean
        get() = snapshot != null

    val isDirty: Boolean
        get() = snapshot?.form?.let { form != it } == true

    fun load(force: Boolean = false) {
        if (isLoading || isSaving) {
            return
        }
        if (!force && snapshot != null) {
            return
        }
        scope.launch {
            isLoading = true
            if (force) {
                loadError = null
            }
            try {
                val loaded = repository.loadCoreConfig()
                snapshot = loaded
                form = loaded.form
                fallbackModelsText = loaded.form.fallbackModels.joinToString("\n")
                loadError = null
            } catch (error: Throwable) {
                loadError = error.toUserFacingMessage()
            } finally {
                isLoading = false
            }
        }
    }

    fun save() {
        val loadedSnapshot = snapshot ?: return
        if (isLoading || isSaving) {
            return
        }
        scope.launch {
            isSaving = true
            saveMessage = null
            loadError = null
            try {
                val updated = repository.saveCoreConfig(loadedSnapshot, form)
                snapshot = updated
                form = updated.form
                fallbackModelsText = updated.form.fallbackModels.joinToString("\n")
                saveMessage = "已保存，将在下次启动 OpenClaw 时生效。"
            } catch (error: Throwable) {
                loadError = error.toUserFacingMessage()
            } finally {
                isSaving = false
            }
        }
    }

    fun clearSaveMessage() {
        saveMessage = null
    }

    fun discardChanges() {
        form = snapshot?.form ?: OpenClawCoreConfigForm()
        fallbackModelsText = form.fallbackModels.joinToString("\n")
    }

    fun updateWorkspace(value: String) {
        saveMessage = null
        form = form.copy(workspace = value)
    }

    fun updateRepoRoot(value: String) {
        saveMessage = null
        form = form.copy(repoRoot = value)
    }

    fun updatePrimaryModel(value: String) {
        saveMessage = null
        form = form.copy(primaryModel = value)
    }

    fun updateFallbacksFromText(value: String) {
        saveMessage = null
        fallbackModelsText = value
        form =
            form.copy(
                fallbackModels =
                    value.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList(),
            )
    }

    fun addProvider() {
        saveMessage = null
        form = form.copy(providers = form.providers + ProviderConfigItem())
    }

    fun removeProvider(rowId: String) {
        saveMessage = null
        form = form.copy(providers = form.providers.filterNot { it.rowId == rowId })
    }

    fun updateProviderId(rowId: String, value: String) {
        updateProvider(rowId) { it.copy(providerId = value) }
    }

    fun updateProviderApi(rowId: String, value: String) {
        updateProvider(rowId) { it.copy(api = value) }
    }

    fun updateProviderBaseUrl(rowId: String, value: String) {
        updateProvider(rowId) { it.copy(baseUrl = value) }
    }

    fun updateProviderApiKey(rowId: String, value: String) {
        updateProvider(
            rowId = rowId,
            transform = {
                it.copy(
                    apiKeyInput = value,
                    apiKeyChanged = true,
                )
            },
        )
    }

    private fun updateProvider(
        rowId: String,
        transform: (ProviderConfigItem) -> ProviderConfigItem,
    ) {
        saveMessage = null
        form =
            form.copy(
                providers =
                    form.providers.map { provider ->
                        if (provider.rowId == rowId) {
                            transform(provider)
                        } else {
                            provider
                        }
                    },
            )
    }
}

@Composable
fun rememberOpenClawConfigScreenState(
    repository: OpenClawConfigRepository,
): OpenClawConfigScreenState {
    val scope = rememberCoroutineScope()
    return remember(repository, scope) {
        OpenClawConfigScreenState(repository, scope)
    }
}

private fun Throwable.toUserFacingMessage(): String {
    val message = message?.trim().orEmpty()
    return when {
        message.isBlank() -> javaClass.simpleName
        message.startsWith("Gateway call failed:", ignoreCase = true) ->
            message.removePrefix("Gateway call failed:").trim().ifBlank { message }
        else -> message
    }
}

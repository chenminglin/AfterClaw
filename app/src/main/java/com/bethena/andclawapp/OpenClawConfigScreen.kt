package com.bethena.andclawapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bethena.andclawapp.host.HostUiState
import com.bethena.andclawapp.host.ProviderConfigItem

/**
 * OpenClaw 配置页面：允许用户通过本地 Gateway RPC 编辑 OpenClaw 的核心配置文件
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OpenClawConfigScreen(
    hostState: HostUiState, // 宿主 UI 状态
    onBack: () -> Unit,      // 返回回调
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val configState = rememberOpenClawConfigScreenState(context.openClawConfigRepository())
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val fallbackModelsText = remember(configState.form.fallbackModels) {
        configState.form.fallbackModels.joinToString("\n")
    }

    fun handleBack() {
        if (configState.isDirty && !configState.isSaving) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::handleBack)

    LaunchedEffect(hostState.gatewayRunning) {
        if (hostState.gatewayRunning) {
            configState.load()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        configState.discardChanges()
                        showDiscardDialog = false
                        onBack()
                    },
                ) {
                    Text(s.discardChanges)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(s.continueEditing)
                }
            },
            title = { Text(s.unsavedChangesTitle) },
            text = { Text(s.unsavedChangesDesc) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = s.openClawConfig,
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = ::handleBack) {
                        Text(
                            text = s.back,
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    if (hostState.gatewayRunning) {
                        TextButton(
                            onClick = { configState.load(force = true) },
                            enabled = !configState.isLoading && !configState.isSaving,
                        ) {
                            Text(
                                text = s.reload,
                                color = Color(0xFFD6EEFF),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFF8FAFC),
                ),
            )
        },
        bottomBar = {
            ConfigActionBar(
                isDirty = configState.isDirty,
                canSave = hostState.gatewayRunning && configState.hasLoadedData,
                isSaving = configState.isSaving,
                onDiscard = { configState.discardChanges() },
                onSave = { configState.save() },
                s = s
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(hostBackgroundBrush())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    title = s.coreConfigTitle,
                    detail = s.coreConfigDesc,
                    accent = Color(0xFF38BDF8),
                )
            }

            if (!hostState.gatewayRunning) {
                item {
                    DetailCard(
                        title = s.gatewayNotRunning,
                        accent = Color(0xFFF97316),
                        lines = listOf(s.gatewayNotRunningDesc),
                    )
                }
            }

            configState.saveMessage?.let { message ->
                item {
                    DetailCard(
                        title = s.saved,
                        accent = Color(0xFF34D399),
                        lines = listOf(message),
                    )
                }
            }

            configState.loadError?.let { error ->
                item {
                    DetailCard(
                        title = s.error,
                        accent = Color(0xFFF97316),
                        lines = listOf(error),
                        monospace = true,
                    )
                }
            }

            when {
                configState.isLoading && !configState.hasLoadedData -> {
                    item {
                        StatusCard(
                            title = s.readingConfig,
                            detail = s.readingConfigDesc,
                            accent = Color(0xFF38BDF8),
                        )
                    }
                }

                !configState.hasLoadedData -> {
                    item {
                        ActionRow(
                            primaryLabel = s.reloadConfig,
                            onPrimary = { configState.load(force = true) },
                            secondaryLabel = if (hostState.gatewayRunning) null else s.back,
                            onSecondary = if (hostState.gatewayRunning) null else onBack,
                        )
                    }
                }

                else -> {
                    item {
                        DetailCard(
                            title = s.configFile,
                            accent = Color(0xFFF59E0B),
                            lines =
                                listOfNotNull(
                                    configState.configPath?.let { "${s.path}: $it" },
                                    "${s.readMethod}: openclaw gateway call config.get",
                                    "${s.writeMethod}: openclaw gateway call config.set",
                                ),
                            monospace = true,
                        )
                    }

                    item {
                        ConfigSectionCard(
                            title = s.runDir,
                            accent = Color(0xFF38BDF8),
                            detail = s.runDirDesc,
                        ) {
                            ConfigTextField(
                                label = configState.hints.workspace.label ?: "workspace",
                                value = configState.form.workspace,
                                onValueChange = configState::updateWorkspace,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.workspace.help
                                        ?: s.workspaceHelp,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ConfigTextField(
                                label = configState.hints.repoRoot.label ?: "repoRoot",
                                value = configState.form.repoRoot,
                                onValueChange = configState::updateRepoRoot,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.repoRoot.help
                                        ?: s.repoRootHelp,
                            )
                        }
                    }

                    item {
                        ConfigSectionCard(
                            title = s.defaultModel,
                            accent = Color(0xFF8B5CF6),
                            detail = s.defaultModelDesc,
                        ) {
                            ConfigTextField(
                                label = configState.hints.primaryModel.label ?: "primary model",
                                value = configState.form.primaryModel,
                                onValueChange = configState::updatePrimaryModel,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.primaryModel.help
                                        ?: s.primaryModelHelp,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ConfigTextField(
                                label = configState.hints.fallbackModels.label ?: "fallback models",
                                value = fallbackModelsText,
                                onValueChange = configState::updateFallbacksFromText,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.fallbackModels.help
                                        ?: s.fallbackModelsHelp,
                                singleLine = false,
                                minLines = 4,
                            )
                        }
                    }

                    item {
                        ConfigSectionCard(
                            title = "Provider",
                            accent = Color(0xFFF59E0B),
                            detail =
                                configState.hints.providers.help
                                    ?: s.providerDesc,
                        ) {
                            if (configState.form.providers.isEmpty()) {
                                Text(
                                    text = s.noProviderDesc,
                                    color = Color(0xFFBFDBFE),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            configState.form.providers.forEach { provider ->
                                ProviderEditorCard(
                                    provider = provider,
                                    enabled = hostState.gatewayRunning && !configState.isSaving,
                                    onProviderIdChange = { configState.updateProviderId(provider.rowId, it) },
                                    onApiChange = { configState.updateProviderApi(provider.rowId, it) },
                                    onBaseUrlChange = { configState.updateProviderBaseUrl(provider.rowId, it) },
                                    onApiKeyChange = { configState.updateProviderApiKey(provider.rowId, it) },
                                    onRemove = { configState.removeProvider(provider.rowId) },
                                    s = s
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            FilledTonalButton(
                                onClick = { configState.addProvider() },
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                            ) {
                                Text(
                                    text = s.addProvider,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    item {
                        ConfigSectionCard(
                            title = s.currentConfigPreview,
                            accent = Color(0xFF34D399),
                            detail = s.currentConfigPreviewDesc,
                        ) {
                            SelectionContainer {
                                Text(
                                    text = configState.previewJson.ifBlank { "{}" },
                                    color = Color(0xFFE2E8F0),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配置页底部操作栏：包含“放弃修改”和“保存配置”按钮
 */
@Composable
fun ConfigActionBar(
    isDirty: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    s: AppStrings
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1608111F)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = s.saveNextEffect,
                color = Color(0xFFBFDBFE),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && isDirty,
                ) {
                    Text(s.discardChanges)
                }
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = canSave && isDirty && !isSaving,
                ) {
                    Text(
                        text = if (isSaving) s.saving else s.saveConfig,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * 配置卡片容器：带标题、描边重点色和可选详情描述
 */
@Composable
fun ConfigSectionCard(
    title: String,
    accent: Color,
    detail: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(12.dp)
                            .background(accent, RoundedCornerShape(999.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                detail?.let {
                    Text(
                        text = it,
                        color = Color(0xFFBFDBFE),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                content()
            },
        )
    }
}

/**
 * 专用于配置页的自定义文本输入框
 */
@Composable
fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    help: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(help) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
    )
}

/**
 * Provider 编辑卡片：用于编辑单个 provider 的配置
 */
@Composable
fun ProviderEditorCard(
    provider: ProviderConfigItem,
    enabled: Boolean,
    onProviderIdChange: (String) -> Unit,
    onApiChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRemove: () -> Unit,
    s: AppStrings
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1A0F172A)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = provider.providerId.ifBlank { s.newProvider },
                    color = Color(0xFFF8FAFC),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onRemove, enabled = enabled) {
                    Text(s.delete)
                }
            }

            ConfigTextField(
                label = "providerId",
                value = provider.providerId,
                onValueChange = onProviderIdChange,
                enabled = enabled,
                help = s.providerIdHelp,
            )

            ConfigTextField(
                label = "api",
                value = provider.api,
                onValueChange = onApiChange,
                enabled = enabled,
                help = s.apiHelp,
            )

            ConfigTextField(
                label = "baseUrl",
                value = provider.baseUrl,
                onValueChange = onBaseUrlChange,
                enabled = enabled,
                help = s.baseUrlHelp,
            )

            ConfigTextField(
                label = "apiKey",
                value = provider.apiKeyInput,
                onValueChange = onApiKeyChange,
                enabled = enabled,
                help = providerApiKeyHelp(provider, s),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
    }
}

/**
 * 根据 Provider 的当前状态返回其 API Key 输入框的辅助文本
 */
private fun providerApiKeyHelp(provider: ProviderConfigItem, s: AppStrings): String {
    return when {
        provider.apiKeyChanged && provider.apiKeyInput.isBlank() ->
            s.apiKeyClearHelp
        provider.hasExistingApiKey && !provider.apiKeyChanged ->
            s.apiKeyExistingHelp
        else -> s.apiKeyDefaultHelp
    }
}

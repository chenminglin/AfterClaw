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
                    Text("放弃修改")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            },
            title = { Text("未保存的修改") },
            text = { Text("当前页面还有未保存的配置修改，返回后这些修改会丢失。") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenClaw 配置",
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = ::handleBack) {
                        Text(
                            text = "返回",
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
                                text = "重新加载",
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
                    title = "本机核心配置",
                    detail =
                        "这里只编辑 OpenClaw 的启动必需项。保存后将在下次启动 OpenClaw 时生效，不会立即重启当前 Gateway。",
                    accent = Color(0xFF38BDF8),
                )
            }

            if (!hostState.gatewayRunning) {
                item {
                    DetailCard(
                        title = "Gateway 未运行",
                        accent = Color(0xFFF97316),
                        lines = listOf("只有本地 Gateway 已启动时，才能读取和保存 OpenClaw 配置。"),
                    )
                }
            }

            configState.saveMessage?.let { message ->
                item {
                    DetailCard(
                        title = "已保存",
                        accent = Color(0xFF34D399),
                        lines = listOf(message),
                    )
                }
            }

            configState.loadError?.let { error ->
                item {
                    DetailCard(
                        title = "错误",
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
                            title = "正在读取配置",
                            detail = "正在通过本地 Gateway RPC 获取当前配置与 schema 提示。",
                            accent = Color(0xFF38BDF8),
                        )
                    }
                }

                !configState.hasLoadedData -> {
                    item {
                        ActionRow(
                            primaryLabel = "重新加载配置",
                            onPrimary = { configState.load(force = true) },
                            secondaryLabel = if (hostState.gatewayRunning) null else "返回",
                            onSecondary = if (hostState.gatewayRunning) null else onBack,
                        )
                    }
                }

                else -> {
                    item {
                        DetailCard(
                            title = "当前配置文件",
                            accent = Color(0xFFF59E0B),
                            lines =
                                listOfNotNull(
                                    configState.configPath?.let { "路径: $it" },
                                    "读取方式: openclaw gateway call config.get",
                                    "保存方式: openclaw gateway call config.set",
                                ),
                            monospace = true,
                        )
                    }

                    item {
                        ConfigSectionCard(
                            title = "运行目录",
                            accent = Color(0xFF38BDF8),
                            detail = "配置默认工作目录和仓库根目录。",
                        ) {
                            ConfigTextField(
                                label = configState.hints.workspace.label ?: "workspace",
                                value = configState.form.workspace,
                                onValueChange = configState::updateWorkspace,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.workspace.help
                                        ?: "默认工作目录。留空时由 OpenClaw 自行决定。",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ConfigTextField(
                                label = configState.hints.repoRoot.label ?: "repoRoot",
                                value = configState.form.repoRoot,
                                onValueChange = configState::updateRepoRoot,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.repoRoot.help
                                        ?: "默认仓库根目录。留空时不强制指定。",
                            )
                        }
                    }

                    item {
                        ConfigSectionCard(
                            title = "默认模型",
                            accent = Color(0xFF8B5CF6),
                            detail = "配置默认 primary model 和 fallback models。",
                        ) {
                            ConfigTextField(
                                label = configState.hints.primaryModel.label ?: "primary model",
                                value = configState.form.primaryModel,
                                onValueChange = configState::updatePrimaryModel,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.primaryModel.help
                                        ?: "例如：openai/gpt-5-mini。",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ConfigTextField(
                                label = configState.hints.fallbackModels.label ?: "fallback models",
                                value = fallbackModelsText,
                                onValueChange = configState::updateFallbacksFromText,
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                                help =
                                    configState.hints.fallbackModels.help
                                        ?: "一行一个模型 ID，按顺序作为降级候选。",
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
                                    ?: "这里只编辑 providerId、api、baseUrl 和 apiKey。",
                        ) {
                            if (configState.form.providers.isEmpty()) {
                                Text(
                                    text = "当前还没有 provider，保存前可以先添加一个。",
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
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            FilledTonalButton(
                                onClick = { configState.addProvider() },
                                enabled = hostState.gatewayRunning && !configState.isSaving,
                            ) {
                                Text(
                                    text = "新增 Provider",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    item {
                        ConfigSectionCard(
                            title = "当前配置预览",
                            accent = Color(0xFF34D399),
                            detail = "这是最近一次从 Gateway 读取到的已脱敏配置，未包含当前未保存的修改。",
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
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1608111F)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "保存后将在下次启动 OpenClaw 时生效。",
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
                    Text("放弃修改")
                }
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = canSave && isDirty && !isSaving,
                ) {
                    Text(
                        text = if (isSaving) "保存中..." else "保存配置",
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
                    text = provider.providerId.ifBlank { "新 Provider" },
                    color = Color(0xFFF8FAFC),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onRemove, enabled = enabled) {
                    Text("删除")
                }
            }

            ConfigTextField(
                label = "providerId",
                value = provider.providerId,
                onValueChange = onProviderIdChange,
                enabled = enabled,
                help = "例如：openai、anthropic、openrouter。",
            )

            ConfigTextField(
                label = "api",
                value = provider.api,
                onValueChange = onApiChange,
                enabled = enabled,
                help = "Provider API 类型，例如 responses、chat.completions。",
            )

            ConfigTextField(
                label = "baseUrl",
                value = provider.baseUrl,
                onValueChange = onBaseUrlChange,
                enabled = enabled,
                help = "可选。自定义网关地址时再填写。",
            )

            ConfigTextField(
                label = "apiKey",
                value = provider.apiKeyInput,
                onValueChange = onApiKeyChange,
                enabled = enabled,
                help = providerApiKeyHelp(provider),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
    }
}

/**
 * 根据 Provider 的当前状态返回其 API Key 输入框的辅助文本
 */
private fun providerApiKeyHelp(provider: ProviderConfigItem): String {
    return when {
        provider.apiKeyChanged && provider.apiKeyInput.isBlank() ->
            "保存时会清除此 Provider 的 apiKey。"
        provider.hasExistingApiKey && !provider.apiKeyChanged ->
            "当前已存在 apiKey；保持不改时会沿用现有值。"
        else -> "可选。只有显式输入新值时才会覆盖当前 apiKey。"
    }
}

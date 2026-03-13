package com.bethena.andclawapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bethena.andclawapp.host.HostForegroundService
import com.bethena.andclawapp.host.HostUiState
import com.bethena.andclawapp.ui.theme.Android_clawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        HostForegroundService.start(this)
        setContent {
            Android_clawTheme {
                HostRootScreen()
            }
        }
    }
}

private enum class HostRoute {
    Init,
    Detail,
    Main,
}

private enum class InitStepStatus {
    Pending,
    InProgress,
    Completed,
    Failed,
}

private data class InitStepUiModel(
    val number: Int,
    val title: String,
    val detail: String,
    val status: InitStepStatus,
)

@Composable
private fun HostRootScreen() {
    val context = LocalContext.current
    val controller = context.hostController()
    val state by controller.state.collectAsState()
    var route by rememberSaveable { mutableStateOf(HostRoute.Init) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.gatewayRunning, state.lastError) {
        if (state.gatewayRunning && state.lastError == null) {
            route = HostRoute.Main
        } else if (route == HostRoute.Main) {
            route = HostRoute.Init
        }
    }

    when (route) {
        HostRoute.Detail ->
            HostConsoleScreen(
                state = state,
                onBack = {
                    route = if (state.gatewayRunning && state.lastError == null) {
                        HostRoute.Main
                    } else {
                        HostRoute.Init
                    }
                },
            )

        HostRoute.Main ->
            MainHostScreen(
                state = state,
                onOpenConsole = { route = HostRoute.Detail },
            )

        HostRoute.Init ->
            InitFlowScreen(
                state = state,
                onRetry = { controller.bootstrapHost() },
                onOpenDetail = { route = HostRoute.Detail },
            )
    }
}

@Composable
private fun InitFlowScreen(
    state: HostUiState,
    onRetry: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val steps = remember(state) { buildInitSteps(state) }
    val activeStep = steps.firstOrNull { it.status == InitStepStatus.InProgress }
    val failedStep = steps.firstOrNull { it.status == InitStepStatus.Failed }
    val accent =
        when {
            failedStep != null -> Color(0xFFF97316)
            state.gatewayRunning -> Color(0xFF34D399)
            else -> Color(0xFF38BDF8)
        }
    val summary =
        when {
            state.gatewayRunning && state.lastError == null -> "OpenClaw 已就绪"
            failedStep != null && state.lastError != null -> state.lastError
            activeStep != null -> activeStep.detail
            else -> state.runtimeSummary
        }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hostBackgroundBrush())
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "正在初始化 AndClaw",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "首次启动会准备运行环境、安装 OpenClaw，并启动本地网关。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFBFDBFE),
                )
            }

            StatusCard(
                title =
                    when {
                        state.gatewayRunning && state.lastError == null -> "OpenClaw 已就绪"
                        failedStep != null -> "初始化失败"
                        activeStep != null -> activeStep.title
                        else -> "正在初始化"
                    },
                detail = summary,
                accent = accent,
                progressLabel = state.busyProgressLabel,
                progress = state.busyProgress,
            )

            steps.forEach { step ->
                InitStepCard(step = step)
            }

            if (state.lastError != null || state.busyTask != null || !state.gatewayRunning) {
                ActionRow(
                    primaryLabel = "查看详情",
                    onPrimary = onOpenDetail,
                    secondaryLabel = if (state.lastError != null) "重试初始化" else null,
                    onSecondary = if (state.lastError != null) onRetry else null,
                )
            }

            if (state.lastError != null) {
                DetailCard(
                    title = "错误摘要",
                    accent = Color(0xFFF97316),
                    lines = listOfNotNull(state.failedStep?.let { "失败步骤: $it" }, state.lastError),
                    monospace = true,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainHostScreen(
    state: HostUiState,
    onOpenConsole: () -> Unit,
) {
    val statusTitle =
        when {
            state.lastError != null -> "Setup stopped"
            state.busyTask != null -> state.busyTask ?: "Working"
            state.gatewayRunning -> "OpenClaw is ready"
            state.openClawInstalled -> "Starting OpenClaw"
            state.runtimeInstalled -> "Installing OpenClaw"
            else -> "Preparing device"
        }
    val statusBody =
        when {
            state.lastError != null && state.failedStep != null -> "Failed at ${state.failedStep}."
            else -> state.runtimeSummary
        }
    val statusAccent =
        when {
            state.lastError != null -> Color(0xFFF97316)
            state.gatewayRunning -> Color(0xFF34D399)
            else -> Color(0xFF38BDF8)
        }
    val showProgress = state.busyTask != null && state.lastError == null && state.busyProgressLabel != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    actionIconContentColor = Color(0xFFD6EEFF),
                ),
                actions = {
                    TextButton(onClick = onOpenConsole) {
                        Text(
                            text = "控制台",
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hostBackgroundBrush())
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AndClaw Host",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "OpenClaw setup and startup now run automatically on your phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFBFDBFE),
                )
            }

            StatusCard(
                title = statusTitle,
                detail = statusBody,
                accent = statusAccent,
                progressLabel = if (showProgress) state.busyProgressLabel else null,
                progress = if (showProgress) state.busyProgress else null,
            )

            state.lastError?.let { error ->
                DetailCard(
                    title = "Error",
                    accent = Color(0xFFF97316),
                    lines = listOf(error),
                    monospace = true,
                )
            }

            if (state.gatewayRunning) {
                AccessCard(
                    title = "Access",
                    accent = Color(0xFFF59E0B),
                    gatewayAddress = "127.0.0.1:${state.gatewayPort}",
                    gatewayToken = state.gatewayToken,
                    accessLink = buildGatewayAccessLink(
                        port = state.gatewayPort,
                        token = state.gatewayToken,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun buildGatewayAccessLink(
    port: Int,
    token: String,
): String {
    return "http://127.0.0.1:$port/?token=$token"
}

private fun hostBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color(0xFF08111F),
            Color(0xFF0E223B),
            Color(0xFF16365B),
        )
    )
}

private fun buildInitSteps(state: HostUiState): List<InitStepUiModel> {
    val activeIndex = resolveActiveInitStepIndex(state)
    val failedIndex = resolveFailedInitStepIndex(state)
    val progressDetail = buildInitStepProgressDetail(state)

    return listOf(
        InitStepUiModel(
            number = 1,
            title = "检查设备环境",
            detail = "启动本地服务并检查当前设备是否可运行。${progressDetail(0)}",
            status = resolveStepStatus(index = 0, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 2,
            title = "准备运行环境",
            detail = "初始化宿主运行时、Ubuntu 与 Node 环境。${progressDetail(1)}",
            status = resolveStepStatus(index = 1, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 3,
            title = "安装 OpenClaw",
            detail = "检查并完成 OpenClaw 安装，必要时自动修复。${progressDetail(2)}",
            status = resolveStepStatus(index = 2, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 4,
            title = "启动本地网关",
            detail = "启动本地 Gateway，生成访问地址与令牌。${progressDetail(3)}",
            status = resolveStepStatus(index = 3, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
    )
}

private fun buildInitStepProgressDetail(state: HostUiState): (Int) -> String {
    val activeIndex = resolveActiveInitStepIndex(state)
    val label = state.busyProgressLabel?.takeIf { it.isNotBlank() }
    val task = state.busyTask?.takeIf { it.isNotBlank() }
    val detail =
        buildString {
            if (!task.isNullOrBlank()) {
                append("\n当前阶段：")
                append(task)
            }
            if (!label.isNullOrBlank()) {
                append("\n当前进度：")
                append(label)
            }
        }

    return { stepIndex ->
        if (stepIndex == activeIndex && detail.isNotBlank()) {
            detail
        } else {
            ""
        }
    }
}

private fun resolveActiveInitStepIndex(state: HostUiState): Int {
    if (state.gatewayRunning && state.lastError == null) {
        return 4
    }
    val busyTask = state.busyTask
    return when {
        busyTask == "Starting local bridge" -> 0
        busyTask == "Preparing host runtime" ||
            busyTask == "Downloading Ubuntu runtime" ||
            busyTask == "Extracting Ubuntu runtime" ||
            busyTask == "Preparing Node runtime" ||
            busyTask == "Downloading Node runtime" ||
            busyTask == "Extracting Node runtime" -> 1

        busyTask == "Installing OpenClaw" -> 2
        busyTask == "Starting OpenClaw gateway" || busyTask == "Starting Gateway" -> 3
        state.openClawInstalled && !state.gatewayRunning -> 3
        state.runtimeInstalled && !state.openClawInstalled -> 2
        state.runtimeInstalled -> 3
        state.bridgeRunning || state.serviceRunning -> 1
        else -> 0
    }
}

private fun resolveFailedInitStepIndex(state: HostUiState): Int? {
    if (state.lastError == null) {
        return null
    }
    val failedStep = state.failedStep.orEmpty()
    return when {
        failedStep == "Starting local bridge" -> 0
        failedStep == "Preparing host runtime" ||
            failedStep == "Downloading Ubuntu runtime" ||
            failedStep == "Extracting Ubuntu runtime" ||
            failedStep == "Preparing Node runtime" ||
            failedStep == "Downloading Node runtime" ||
            failedStep == "Extracting Node runtime" -> 1

        failedStep == "Installing OpenClaw" -> 2
        failedStep == "Starting OpenClaw gateway" ||
            failedStep == "Starting Gateway" ||
            state.lastError.contains("Gateway exited", ignoreCase = true) -> 3

        state.openClawInstalled -> 3
        state.runtimeInstalled -> 2
        else -> 0
    }
}

private fun resolveStepStatus(
    index: Int,
    activeIndex: Int,
    failedIndex: Int?,
): InitStepStatus {
    return when {
        failedIndex == index -> InitStepStatus.Failed
        activeIndex > index -> InitStepStatus.Completed
        activeIndex == index -> InitStepStatus.InProgress
        else -> InitStepStatus.Pending
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HostConsoleScreen(
    state: HostUiState,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var showAllLogs by rememberSaveable { mutableStateOf(true) }
    val visibleLogs = remember(state.logs, showAllLogs) {
        buildConsoleLogLines(
            logs = state.logs,
            showAllLogs = showAllLogs,
        )
    }
    val listState = rememberLazyListState()
    val steps = remember(state) { buildInitSteps(state) }

    LaunchedEffect(visibleLogs.size) {
        if (visibleLogs.isNotEmpty()) {
            listState.animateScrollToItem(visibleLogs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "初始化详情",
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "返回",
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showAllLogs = !showAllLogs }) {
                        Text(
                            text = if (showAllLogs) "只看 OpenClaw" else "全部日志",
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFF8FAFC),
                ),
            )
        }
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
                    title = "初始化详情",
                    detail = state.busyTask ?: state.runtimeSummary,
                    accent = when {
                        state.lastError != null -> Color(0xFFF97316)
                        state.gatewayRunning -> Color(0xFF34D399)
                        else -> Color(0xFF38BDF8)
                    },
                    progressLabel = state.busyProgressLabel,
                    progress = state.busyProgress,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    steps.forEach { step ->
                        InitStepCard(step = step)
                    }
                }
            }

            if (state.lastError != null) {
                item {
                    DetailCard(
                        title = "最近错误",
                        accent = Color(0xFFF97316),
                        lines = listOfNotNull(state.failedStep?.let { "失败步骤: $it" }, state.lastError),
                        monospace = true,
                    )
                }
            }

            item {
                LogConsoleCard(
                    title = if (showAllLogs) "全部日志" else "OpenClaw 日志",
                    accent = Color(0xFF38BDF8),
                    lines = visibleLogs,
                    emptyMessage =
                        if (showAllLogs) {
                            "还没有可显示的宿主日志。"
                        } else {
                            "还没有捕获到 OpenClaw Gateway 的输出。"
                        },
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                )
            }
        }
    }
}

private fun buildConsoleLogLines(
    logs: List<String>,
    showAllLogs: Boolean,
): List<String> {
    if (logs.isEmpty()) {
        return emptyList()
    }
    if (showAllLogs) {
        return logs
    }

    val launchIndex = logs.indexOfLast { line ->
        line.contains("Launching Gateway on loopback:") ||
            line.contains("Starting Gateway") ||
            line.contains("Starting OpenClaw gateway")
    }
    val relevantWindow = if (launchIndex >= 0) logs.drop(launchIndex) else logs

    return relevantWindow.filter { line ->
        line.contains("[gateway]") ||
            line.contains("Launching Gateway on loopback:") ||
            line.contains("Gateway process exited with code") ||
            line.contains("Gateway is already running on port")
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    accent: Color,
    progressLabel: String? = null,
    progress: Float? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1AF8FAFC)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE2E8F0),
            )
            progressLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBFDBFE),
                    fontFamily = FontFamily.Monospace,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun InitStepCard(step: InitStepUiModel) {
    val (accent, badgeText, badgeColor) =
        when (step.status) {
            InitStepStatus.Pending -> Triple(Color(0xFF64748B), "未开始", Color(0xFF94A3B8))
            InitStepStatus.InProgress -> Triple(Color(0xFF38BDF8), "进行中", Color(0xFF7DD3FC))
            InitStepStatus.Completed -> Triple(Color(0xFF34D399), "已完成", Color(0xFF6EE7B7))
            InitStepStatus.Failed -> Triple(Color(0xFFF97316), "失败", Color(0xFFFDA4AF))
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(34.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.number.toString(),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = primaryLabel,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = secondaryLabel,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LogConsoleCard(
    title: String,
    accent: Color,
    lines: List<String>,
    emptyMessage: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            if (lines.isEmpty()) {
                Text(
                    text = emptyMessage,
                    color = Color(0xFFBFDBFE),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                SelectionContainer(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line,
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

@Composable
private fun AccessCard(
    title: String,
    accent: Color,
    gatewayAddress: String,
    gatewayToken: String,
    accessLink: String,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Gateway",
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = gatewayAddress,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Token",
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = gatewayToken,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Link",
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = accessLink,
                        color = Color(0xFFBFDBFE),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(accessLink))
                    Toast.makeText(context, "访问链接已复制", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(
                    text = "复制链接",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    accent: Color,
    lines: List<String>,
    monospace: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            lines.forEach { line ->
                Text(
                    text = line,
                    color = Color(0xFFE2E8F0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }
    }
}

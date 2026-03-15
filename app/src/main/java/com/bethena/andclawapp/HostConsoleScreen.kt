package com.bethena.andclawapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bethena.andclawapp.host.HostUiState

/**
 * 宿主控制台页面：展示初始化的详细日志
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HostConsoleScreen(
    state: HostUiState,  // 宿主 UI 状态
    onBack: () -> Unit,   // 返回回调
) {
    val s = LocalAppStrings.current
    BackHandler(onBack = onBack)

    var showAllLogs by rememberSaveable { mutableStateOf(true) }
    val visibleLogs = remember(state.logs, showAllLogs) {
        buildConsoleLogLines(
            logs = state.logs,
            showAllLogs = showAllLogs,
        )
    }
    val listState = rememberLazyListState()
    val steps = remember(state, s) { buildInitSteps(state, s) }

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
                        text = s.initDetail,
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = s.back,
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showAllLogs = !showAllLogs }) {
                        Text(
                            text = if (showAllLogs) s.onlyOpenClaw else s.allLogs,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    title = s.initDetail,
                    detail = (state.busyTask ?: state.runtimeSummary)!!,
                    accent = when {
                        state.lastError != null -> Color(0xFFF97316)
                        state.gatewayReady -> Color(0xFF34D399)
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
                        title = s.recentError,
                        accent = Color(0xFFF97316),
                        lines = listOfNotNull(state.failedStep?.let { s.failedStepAt(it) }, state.lastError),
                        monospace = true,
                    )
                }
            }

            item {
                LogConsoleCard(
                    title = if (showAllLogs) s.allLogs else s.openClawLogs,
                    accent = Color(0xFF38BDF8),
                    lines = visibleLogs,
                    emptyMessage =
                        if (showAllLogs) {
                            s.noHostLogs
                        } else {
                            s.noGatewayLogs
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

/**
 * 根据是否只看 OpenClaw 日志，筛选并返回控制台显示的日志行
 */
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

package com.bethena.andclawapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bethena.andclawapp.host.HostUiState

/**
 * 初始化流程页面：展示所有初始化步骤的实时进度
 */
@Composable
fun InitFlowScreen(
    state: HostUiState,       // 宿主 UI 状态
    onRetry: () -> Unit,      // 重试回调
    onOpenDetail: () -> Unit, // 查看详情回调
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
                detail = summary!!,
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

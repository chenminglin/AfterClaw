package com.bethena.andclawapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bethena.andclawapp.host.HostUiState

/**
 * 宿主主界面：当 Gateway 运行正常时展示，包含访问信息和配置入口
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainHostScreen(
    state: HostUiState,         // 宿主 UI 状态
    onOpenConsole: () -> Unit,  // 打开控制台回调
    onOpenConfig: () -> Unit,   // 打开配置页回调
) {
    val s = LocalAppStrings.current
    val statusTitle =
        when {
            state.lastError != null -> s.initFailed
            state.busyTask != null -> state.busyTask ?: s.initializing
            state.gatewayRunning -> s.openClawReady
            state.openClawInstalled -> s.initializing
            state.runtimeInstalled -> s.initializing
            else -> s.initializing
        }
    val statusBody =
        when {
            state.lastError != null && state.failedStep != null -> s.failedStepAt(state.failedStep)
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
                    // 语言切换按钮
                    TextButton(onClick = { LanguageManager.toggle() }) {
                        Text(
                            text = s.languageToggle,
                            color = Color(0xFFD6EEFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (state.gatewayRunning) {
                        TextButton(onClick = onOpenConfig) {
                            Text(
                                text = s.config,
                                color = Color(0xFFD6EEFF),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    TextButton(onClick = onOpenConsole) {
                        Text(
                            text = s.console,
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = s.appTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = s.autoSetupDesc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFBFDBFE),
                )
            }

            StatusCard(
                title = statusTitle,
                detail = statusBody!!,
                accent = statusAccent,
                progressLabel = if (showProgress) state.busyProgressLabel else null,
                progress = if (showProgress) state.busyProgress else null,
            )

            state.lastError?.let { error ->
                DetailCard(
                    title = s.error,
                    accent = Color(0xFFF97316),
                    lines = listOf(error),
                    monospace = true,
                )
            }

            if (state.gatewayRunning) {
                AccessCard(
                    title = s.access,
                    accent = Color(0xFFF59E0B),
                    gatewayAddress = "127.0.0.1:${state.gatewayPort}",
                    gatewayToken = state.gatewayToken!!,
                    accessLink = buildGatewayAccessLink(
                        port = state.gatewayPort,
                        token = state.gatewayToken!!,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

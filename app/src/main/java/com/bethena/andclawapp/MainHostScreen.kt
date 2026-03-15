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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onReRunOnboarding: () -> Unit, // 重新运行引导回调
    onApprovePairing: (String, String) -> Unit, // 批准配对回调 (channel, code)
) {
    val s = LocalAppStrings.current
    val statusTitle =
        when {
            state.lastError != null -> s.initFailed
            state.busyTask != null -> state.busyTask ?: s.initializing
            state.gatewayReady -> s.openClawReady
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
            state.gatewayReady -> Color(0xFF34D399)
            else -> Color(0xFF38BDF8)
        }
    val showProgress =
        state.busyTask != null && state.lastError == null && state.busyProgressLabel != null

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
                    if (state.gatewayReady) {
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
            if (state.gatewayReady) {
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

                Spacer(modifier = Modifier.height(8.dp))

                // 渠道配对卡片
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color(
                            0x1AF8FAFC
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = s.pairingTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = s.pairingDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0),
                        )

                        var pairingCode by remember { mutableStateOf("") }
                        var selectedChannel by remember { mutableStateOf("telegram") }

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it.uppercase() },
                            label = { Text(s.pairingCodeLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = state.busyTask == null,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF38BDF8),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                            )
                        )

                        ActionRow(
                            primaryLabel = s.approvePairing,
                            onPrimary = {
                                if (pairingCode.isNotBlank()) {
                                    onApprovePairing(selectedChannel, pairingCode)
                                    pairingCode = ""
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                DetailCard(
                    title = s.onboardingTitle,
                    accent = Color(0xFF38BDF8),
                    lines = listOf("如果您需要更改初始核心设置（如模型、认证等），可以重新运行引导向导。"),
                )

                ActionRow(
                    primaryLabel = s.reRunOnboarding,
                    onPrimary = onReRunOnboarding,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

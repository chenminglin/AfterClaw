package com.bethena.andclawapp

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bethena.andclawapp.host.HostForegroundService
import com.bethena.andclawapp.host.HostUiState

/**
 * 宿主根界面：负责整个宿主各页面（路由）的切换逻辑
 */
@Composable
fun HostRootScreen() {
    val context = LocalContext.current
    val controller = context.hostController()
    val state by controller.state.collectAsState()
    var route by rememberSaveable { mutableStateOf(HostRoute.Welcome) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.gatewayReady, state.lastError, state.needsOnboarding) {
        if (state.gatewayReady && state.lastError == null) {
            if (state.needsOnboarding) {
                route = HostRoute.Onboarding
            } else {
                route = HostRoute.Main
            }
        } else if (route == HostRoute.Main || route == HostRoute.Onboarding) {
            route = resolveFallbackRoute(state)
        }
    }

    when (route) {
        HostRoute.Welcome ->
            WelcomeScreen(
                onStart = {
                    route = HostRoute.Init
                    HostForegroundService.start(
                        context,
                        HostForegroundService.ACTION_ENSURE_HOST,
                    )
                },
            )

        HostRoute.Detail ->
            HostConsoleScreen(
                state = state,
                onBack = {
                    route = if (state.gatewayReady && state.lastError == null) {
                        if (state.needsOnboarding) HostRoute.Onboarding else HostRoute.Main
                    } else {
                        resolveFallbackRoute(state)
                    }
                },
            )

        HostRoute.Config ->
            OpenClawConfigScreen(
                hostState = state,
                onBack = { route = HostRoute.Main },
            )

        HostRoute.Onboarding ->
            OnboardingScreen(
                onFinished = {
                    route = HostRoute.Main
                    controller.startGateway() // Restart to pick up new config
                },
                onBack = {
                    route = HostRoute.Init
                }
            )

        HostRoute.Main ->
            MainHostScreen(
                state = state,
                onOpenConsole = { route = HostRoute.Detail },
                onOpenConfig = { route = HostRoute.Config },
                onReRunOnboarding = {
                    controller.resetOpenClawConfig()
                },
                onApprovePairing = { channel, code ->
                    controller.approveChannelPairing(channel, code)
                }
            )

        HostRoute.Init ->
            InitFlowScreen(
                state = state,
                onRetry = {
                    HostForegroundService.start(
                        context,
                        HostForegroundService.ACTION_ENSURE_HOST,
                    )
                },
                onOpenDetail = { route = HostRoute.Detail },
            )
    }
}

/**
 * 当路由需要回退时，根据当前 state 解析具体该回退到哪个页面
 */
fun resolveFallbackRoute(state: HostUiState): HostRoute {
    return if (state.serviceRunning || state.busyTask != null || state.lastError != null) {
        HostRoute.Init
    } else {
        HostRoute.Welcome
    }
}

package com.bethena.andclawapp

import com.bethena.andclawapp.host.HostUiState

/**
 * 构建网关访问链接
 * @param port 端口号
 * @param token 访问令牌
 * @return 拼接后的访问 URL
 */
fun buildGatewayAccessLink(
    port: Int,
    token: String,
): String {
    return "http://127.0.0.1:$port/?token=$token"
}

/**
 * 构建初始化步骤列表
 * @param state 当前宿主 UI 状态
 * @param s 多语言字符串对象
 * @return 包含所有步骤的列表
 */
fun buildInitSteps(state: HostUiState, s: AppStrings): List<InitStepUiModel> {
    val activeIndex = resolveActiveInitStepIndex(state)
    val failedIndex = resolveFailedInitStepIndex(state)
    val progressDetail = buildInitStepProgressDetail(state, s)

    return listOf(
        InitStepUiModel(
            number = 1,
            title = s.step1Title,
            detail = "${s.step1Desc}${progressDetail(0)}",
            status = resolveStepStatus(index = 0, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 2,
            title = s.step2Title,
            detail = "${s.step2Desc}${progressDetail(1)}",
            status = resolveStepStatus(index = 1, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 3,
            title = s.step3Title,
            detail = "${s.step3Desc}${progressDetail(2)}",
            status = resolveStepStatus(index = 2, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
        InitStepUiModel(
            number = 4,
            title = s.step4Title,
            detail = "${s.step4Desc}${progressDetail(3)}",
            status = resolveStepStatus(index = 3, activeIndex = activeIndex, failedIndex = failedIndex),
        ),
    )
}

/**
 * 构建初始化步骤的进度详情
 */
private fun buildInitStepProgressDetail(state: HostUiState, s: AppStrings): (Int) -> String {
    val activeIndex = resolveActiveInitStepIndex(state)
    val label = state.busyProgressLabel?.takeIf { it.isNotBlank() }
    val task = state.busyTask?.takeIf { it.isNotBlank() }
    val detail =
        buildString {
            if (!task.isNullOrBlank()) {
                append("\n${s.currentPhase}")
                append(task)
            }
            if (!label.isNullOrBlank()) {
                append("\n${s.currentProgress}")
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

/**
 * 解析当前正处于活动状态的初始化步骤索引
 */
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

/**
 * 解析失败的步骤索引
 */
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

/**
 * 解析步骤状态
 */
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

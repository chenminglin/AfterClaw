package com.bethena.andclawapp.host

/**
 * 代表了 AndClaw 宿主环境的 UI 状态。
 * 该状态包含了服务的运行情况、各种后台任务的进度以及各个组件（如网关、桥接等）的状态信息。
 */
data class HostUiState(
    /** 服务是否正在运行 */
    val serviceRunning: Boolean = false,
    /** 当前正在执行的繁忙任务名称，为空则表示无任务 */
    val busyTask: String? = null,
    /** 失败的步骤名称 */
    val failedStep: String? = null,
    /** 任务进度（0.0 到 1.0 之间），为空代表不确定进度的任务 */
    val busyProgress: Float? = null,
    /** 任务进度的文字标签说明 */
    val busyProgressLabel: String? = null,
    /** 运行时环境是否已成功安装 */
    val runtimeInstalled: Boolean = false,
    /** OpenClaw 是否已成功安装 */
    val openClawInstalled: Boolean = false,
    /** Gateway 网关服务是否正在运行 */
    val gatewayRunning: Boolean = false,
    /** Bridge 桥接服务是否正在运行 */
    val bridgeRunning: Boolean = false,
    /** Bridge 桥接服务监听的端口号 */
    val bridgePort: Int = DEVICE_BRIDGE_PORT,
    /** Gateway 网关服务监听的端口号 */
    val gatewayPort: Int = GATEWAY_PORT,
    /** 运行时状态的简短摘要说明 */
    val runtimeSummary: String = "Bundled runtime has not been prepared yet.",
    /** 用于验证 Gateway 调用的访问令牌 */
    val gatewayToken: String = "",
    /** 运行日志列表，保留最近的日志输出 */
    val logs: List<String> = emptyList(),
    /** 最近一次发生的错误信息 */
    val lastError: String? = null,
)

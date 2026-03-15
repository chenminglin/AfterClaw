package com.bethena.andclawapp

/**
 * 宿主页面的路由枚举
 */
enum class HostRoute {
    Welcome,    // 欢迎页
    Init,       // 初始化页
    Onboarding, // 新手引导页 (openclaw onboard)
    Detail,     // 控制台/详情页
    Config,     // 配置页
    Main,       // 主界面
}

/**
 * 初始化步骤的状态
 */
enum class InitStepStatus {
    Pending,    // 待处理
    InProgress, // 进行中
    Completed,  // 已完成
    Failed,     // 失败
}

/**
 * 初始化步骤的 UI 模型
 */
data class InitStepUiModel(
    val number: Int,             // 步骤编号
    val title: String,           // 标题
    val detail: String,          // 详情
    val status: InitStepStatus,  // 状态
)

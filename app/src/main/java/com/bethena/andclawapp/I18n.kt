package com.bethena.andclawapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 语言枚举
 */
enum class AppLanguage {
    CN, // 中文
    EN  // 英文
}

/**
 * 界面文字 I18n 接口
 */
interface AppStrings {
    // Welcome Screen
    val appTitle: String
    val welcomeDesc: String
    val readyToStartTitle: String
    val readyToStartDesc: String
    val whatHappensTitle: String
    val steps: List<String>
    val startInit: String

    // Init Flow Screen
    val initAndClawTitle: String
    val initAndClawDesc: String
    val openClawReady: String
    val initFailed: String
    val initializing: String
    val viewDetail: String
    val retryInit: String
    val errorSummary: String
    val failedStepAt: (String) -> String

    // Host Utils / Steps
    val step1Title: String
    val step1Desc: String
    val step2Title: String
    val step2Desc: String
    val step3Title: String
    val step3Desc: String
    val step4Title: String
    val step4Desc: String
    val currentPhase: String
    val currentProgress: String

    // Main Host Screen
    val config: String
    val console: String
    val autoSetupDesc: String
    val access: String
    val gateway: String
    val token: String
    val link: String
    val copyLink: String
    val linkCopied: String

    // Console Screen
    val initDetail: String
    val back: String
    val onlyOpenClaw: String
    val allLogs: String
    val recentError: String
    val openClawLogs: String
    val noHostLogs: String
    val noGatewayLogs: String

    // Config Screen
    val openClawConfig: String
    val discardChanges: String
    val continueEditing: String
    val unsavedChangesTitle: String
    val unsavedChangesDesc: String
    val reload: String
    val saveConfig: String
    val saving: String
    val coreConfigTitle: String
    val coreConfigDesc: String
    val gatewayNotRunning: String
    val gatewayNotRunningDesc: String
    val saved: String
    val error: String
    val readingConfig: String
    val readingConfigDesc: String
    val reloadConfig: String
    val configFile: String
    val path: String
    val readMethod: String
    val writeMethod: String
    val runDir: String
    val runDirDesc: String
    val workspaceHelp: String
    val repoRootHelp: String
    val defaultModel: String
    val defaultModelDesc: String
    val primaryModelHelp: String
    val fallbackModelsHelp: String
    val providerDesc: String
    val noProviderDesc: String
    val addProvider: String
    val currentConfigPreview: String
    val currentConfigPreviewDesc: String
    val nextLaunchEffectDesc: String
    val delete: String
    val newProvider: String
    val apiKeyClearHelp: String
    val apiKeyKeepHelp: String
    val apiKeyOptionalHelp: String
    val providerIdHelp: String
    val apiHelp: String
    val baseUrlHelp: String
    val apiKeyExistingHelp: String
    val apiKeyDefaultHelp: String

    // Onboarding
    val onboardingTitle: String
    val resetOnboarding: String
    val reRunOnboarding: String

    // Pairing
    val pairingTitle: String
    val pairingDesc: String
    val pairingCodeLabel: String
    val pairingChannelLabel: String
    val approvePairing: String
    val pairingSuccess: String

    // Common
    val languageToggle: String
    val saveNextEffect: String

    // Badges
    val pending: String
    val inProgress: String
    val completed: String
    val failed: String
}

/**
 * 中文实现
 */
object ChineseStrings : AppStrings {
    override val appTitle = "AfterClaw"
    override val welcomeDesc = "点击开始后，应用才会启动前台服务并初始化 OpenClaw 运行环境。"
    override val readyToStartTitle = "准备开始"
    override val readyToStartDesc = "初始化会按顺序完成设备检查、运行环境准备、OpenClaw 安装和本地网关启动。"
    override val whatHappensTitle = "启动后会执行"
    override val steps = listOf(
        "1. 检查设备环境",
        "2. 准备 Ubuntu 与 Node 运行环境",
        "3. 安装或修复 OpenClaw",
        "4. 启动本地 Gateway 并生成访问地址"
    )
    override val startInit = "开始初始化"

    override val initAndClawTitle = "正在初始化 AndClaw"
    override val initAndClawDesc = "首次启动会准备运行环境、安装 OpenClaw，并启动本地网关。"
    override val openClawReady = "OpenClaw 已就绪"
    override val initFailed = "初始化失败"
    override val initializing = "正在初始化"
    override val viewDetail = "查看详情"
    override val retryInit = "重试初始化"
    override val errorSummary = "错误摘要"
    override val failedStepAt: (String) -> String = { "失败步骤: $it" }

    override val step1Title = "检查设备环境"
    override val step1Desc = "启动本地服务并检查当前设备是否可运行。"
    override val step2Title = "准备运行环境"
    override val step2Desc = "初始化宿主运行时、Ubuntu 与 Node 环境。"
    override val step3Title = "安装 OpenClaw"
    override val step3Desc = "检查并完成 OpenClaw 安装，必要时自动修复。"
    override val step4Title = "启动本地网关"
    override val step4Desc = "启动本地 Gateway，生成访问地址与令牌。"
    override val currentPhase = "当前阶段："
    override val currentProgress = "当前进度："

    override val config = "配置"
    override val console = "控制台"
    override val autoSetupDesc = "OpenClaw 的安装和启动现在在您的手机上自动运行。"
    override val access = "访问信息"
    override val gateway = "网关"
    override val token = "令牌"
    override val link = "链接"
    override val copyLink = "复制链接"
    override val linkCopied = "访问链接已复制"

    override val initDetail = "初始化详情"
    override val back = "返回"
    override val onlyOpenClaw = "只看 OpenClaw"
    override val allLogs = "全部日志"
    override val recentError = "最近错误"
    override val openClawLogs = "OpenClaw 日志"
    override val noHostLogs = "还没有可显示的宿主日志。"
    override val noGatewayLogs = "还没有捕获到 OpenClaw Gateway 的输出。"

    override val openClawConfig = "OpenClaw 配置"
    override val discardChanges = "放弃修改"
    override val continueEditing = "继续编辑"
    override val unsavedChangesTitle = "未保存的修改"
    override val unsavedChangesDesc = "当前页面还有未保存的配置修改，返回后这些修改会丢失。"
    override val reload = "重新加载"
    override val saveConfig = "保存配置"
    override val saving = "保存中..."
    override val coreConfigTitle = "本机核心配置"
    override val coreConfigDesc = "这里只编辑 OpenClaw 的启动必需项。保存后将在下次启动 OpenClaw 时生效，不会立即重启当前 Gateway。"
    override val gatewayNotRunning = "Gateway 未运行"
    override val gatewayNotRunningDesc = "只有本地 Gateway 已启动时，才能读取和保存 OpenClaw 配置。"
    override val saved = "已保存"
    override val error = "错误"
    override val readingConfig = "正在读取配置"
    override val readingConfigDesc = "正在通过本地 Gateway RPC 获取当前配置与 schema 提示。"
    override val reloadConfig = "重新加载配置"
    override val configFile = "当前配置文件"
    override val path = "路径"
    override val readMethod = "读取方式"
    override val writeMethod = "保存方式"
    override val runDir = "运行目录"
    override val runDirDesc = "配置默认工作目录和仓库根目录。"
    override val workspaceHelp = "默认工作目录。留空时由 OpenClaw 自行决定。"
    override val repoRootHelp = "默认仓库根目录。留空时不强制指定。"
    override val defaultModel = "默认模型"
    override val defaultModelDesc = "配置默认 primary model 和 fallback models。"
    override val primaryModelHelp = "例如：openai/gpt-5-mini。"
    override val fallbackModelsHelp = "一行一个模型 ID，按顺序作为降级候选。"
    override val providerDesc = "这里只编辑 providerId、api、baseUrl 和 apiKey。"
    override val noProviderDesc = "当前还没有 provider，保存前可以先添加一个。"
    override val addProvider = "新增 Provider"
    override val currentConfigPreview = "当前配置预览"
    override val currentConfigPreviewDesc = "这是最近一次从 Gateway 读取到的已脱敏配置，未包含当前未保存的修改。"
    override val nextLaunchEffectDesc = "保存后将在下次启动 OpenClaw 时生效。"
    override val delete = "删除"
    override val newProvider = "新 Provider"
    override val apiKeyClearHelp = "保存时会清除此 Provider 的 apiKey。"
    override val apiKeyKeepHelp = "当前已存在 apiKey；保持不改时会沿用现有值。"
    override val apiKeyOptionalHelp = "可选。只有显式输入新值时才会覆盖当前 apiKey。"
    override val providerIdHelp = "例如：openai、anthropic、openrouter。"
    override val apiHelp = "Provider API 类型，例如 responses、chat.completions。"
    override val baseUrlHelp = "可选。自定义网关地址时再填写。"
    override val apiKeyExistingHelp = "当前已存在 apiKey；保持不改时会沿用现有值。"
    override val apiKeyDefaultHelp = "可选。只有显式输入新值时才会覆盖当前 apiKey。"

    override val onboardingTitle = "新手引导"
    override val resetOnboarding = "重新设置"
    override val reRunOnboarding = "重新运行新手引导"

    override val pairingTitle = "渠道配对"
    override val pairingDesc = "当您在 Telegram 等聊天工具中发起 /start 后，请在此输入配对代码以批准访问。"
    override val pairingCodeLabel = "配对代码 (例如: 2KFAPNAC)"
    override val pairingChannelLabel = "渠道"
    override val approvePairing = "批准配对"
    override val pairingSuccess = "配对批准成功"

    override val languageToggle = "English"
    override val saveNextEffect = "保存后将在下次启动 OpenClaw 时生效。"

    override val pending = "未开始"
    override val inProgress = "进行中"
    override val completed = "已完成"
    override val failed = "失败"
}

/**
 * 英文实现
 */
object EnglishStrings : AppStrings {
    override val appTitle = "AfterClaw"
    override val welcomeDesc = "Initialization starts only after you click 'Start Initialization' to launch the foreground service and prepare the environment."
    override val readyToStartTitle = "Ready to start"
    override val readyToStartDesc = "Setup will perform device check, prepare environment, install OpenClaw, and launch local gateway in order."
    override val whatHappensTitle = "What happens next"
    override val steps = listOf(
        "1. Check device environment",
        "2. Prepare Ubuntu & Node context",
        "3. Install or repair OpenClaw",
        "4. Start local Gateway & generate URL"
    )
    override val startInit = "Start Initialization"

    override val initAndClawTitle = "Initializing AndClaw"
    override val initAndClawDesc = "First-time setup prepares the runtime, installs OpenClaw, and starts the local gateway."
    override val openClawReady = "OpenClaw is ready"
    override val initFailed = "Setup failed"
    override val initializing = "Initializing"
    override val viewDetail = "View Details"
    override val retryInit = "Retry Setup"
    override val errorSummary = "Error Summary"
    override val failedStepAt: (String) -> String = { "Failed at: $it" }

    override val step1Title = "Checking environment"
    override val step1Desc = "Starting local service and checking device compatibility."
    override val step2Title = "Preparing runtime"
    override val step2Desc = "Initializing host runtime, Ubuntu, and Node environment."
    override val step3Title = "Installing OpenClaw"
    override val step3Desc = "Scanning and completing OpenClaw installation, repairing if needed."
    override val step4Title = "Starting local gateway"
    override val step4Desc = "Launching local Gateway and generating access token."
    override val currentPhase = "Current phase: "
    override val currentProgress = "Current progress: "

    override val config = "Config"
    override val console = "Console"
    override val autoSetupDesc = "OpenClaw setup and startup now run automatically on your phone."
    override val access = "Access"
    override val gateway = "Gateway"
    override val token = "Token"
    override val link = "Link"
    override val copyLink = "Copy Link"
    override val linkCopied = "Link copied to clipboard"

    override val initDetail = "Setup Detail"
    override val back = "Back"
    override val onlyOpenClaw = "OpenClaw only"
    override val allLogs = "All logs"
    override val recentError = "Recent error"
    override val openClawLogs = "OpenClaw Logs"
    override val noHostLogs = "No host logs available yet."
    override val noGatewayLogs = "No Gateway output captured yet."

    override val openClawConfig = "OpenClaw Config"
    override val discardChanges = "Discard"
    override val continueEditing = "Keep Editing"
    override val unsavedChangesTitle = "Unsaved Changes"
    override val unsavedChangesDesc = "You have unsaved changes. They will be lost if you leave this page."
    override val reload = "Reload"
    override val saveConfig = "Save Config"
    override val saving = "Saving..."
    override val coreConfigTitle = "Core Config"
    override val coreConfigDesc = "Edit essential startup items here. Changes take effect on next launch, current Gateway won't restart immediately."
    override val gatewayNotRunning = "Gateway Not Running"
    override val gatewayNotRunningDesc = "Local Gateway must be running to read or save OpenClaw config."
    override val saved = "Saved"
    override val error = "Error"
    override val readingConfig = "Reading Config"
    override val readingConfigDesc = "Fetching current config and schema via local Gateway RPC."
    override val reloadConfig = "Reload Config"
    override val configFile = "Config File"
    override val path = "Path"
    override val readMethod = "Read via"
    override val writeMethod = "Save via"
    override val runDir = "Runtime Directory"
    override val runDirDesc = "Configure default workspace and repository root."
    override val workspaceHelp = "Default working directory. Left empty to let OpenClaw decide."
    override val repoRootHelp = "Default repository root. Left empty to not enforce."
    override val defaultModel = "Default Models"
    override val defaultModelDesc = "Configure primary and fallback models."
    override val primaryModelHelp = "e.g. openai/gpt-5-mini"
    override val fallbackModelsHelp = "One model ID per line, used in order as candidates."
    override val providerDesc = "Edit providerId, api, baseUrl, and apiKey here."
    override val noProviderDesc = "No providers yet. You can add one before saving."
    override val addProvider = "Add Provider"
    override val currentConfigPreview = "Current Preview"
    override val currentConfigPreviewDesc = "Last redacted config read from Gateway, not including unsaved changes."
    override val nextLaunchEffectDesc = "Changes will take effect on next OpenClaw launch."
    override val delete = "Delete"
    override val newProvider = "New Provider"
    override val apiKeyClearHelp = "Saving will clear apiKey for this Provider."
    override val apiKeyKeepHelp = "Current apiKey exists; it will be kept if not modified."
    override val apiKeyOptionalHelp = "Optional. Only explicit input will override current apiKey."
    override val providerIdHelp = "e.g. openai, anthropic, openrouter."
    override val apiHelp = "Provider API type, e.g. responses, chat.completions."
    override val baseUrlHelp = "Optional. Fill in only when using custom gateway."
    override val apiKeyExistingHelp = "Current apiKey exists; it will be kept if not modified."
    override val apiKeyDefaultHelp = "Optional. Only explicit input will override current apiKey."

    override val onboardingTitle = "Onboarding"
    override val resetOnboarding = "Reset"
    override val reRunOnboarding = "Re-run Onboarding"

    override val pairingTitle = "Channel Pairing"
    override val pairingDesc = "Enter the code received in Telegram or other channels after running /start to approve access."
    override val pairingCodeLabel = "Pairing Code (e.g. 2KFAPNAC)"
    override val pairingChannelLabel = "Channel"
    override val approvePairing = "Approve Pairing"
    override val pairingSuccess = "Pairing approved successfully"

    override val languageToggle = "中文"
    override val saveNextEffect = "Changes take effect on next launch."

    override val pending = "Pending"
    override val inProgress = "In Progress"
    override val completed = "Completed"
    override val failed = "Failed"
}

/**
 * 提供 CompositionLocal 用于 UI 访问
 */
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { ChineseStrings }

/**
 * 语言管理器：用于全局管理语言状态
 */
object LanguageManager {
    var currentLanguage by mutableStateOf(AppLanguage.CN)
    
    val strings: AppStrings
        get() = if (currentLanguage == AppLanguage.CN) ChineseStrings else EnglishStrings

    fun toggle() {
        currentLanguage = if (currentLanguage == AppLanguage.CN) AppLanguage.EN else AppLanguage.CN
    }
}

/**
 * 提供 I18n 注入的根包装器
 */
@Composable
fun I18nProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppStrings provides LanguageManager.strings) {
        content()
    }
}

package com.bethena.andclawapp.host

/**
 * 日志输出标签
 */
internal const val LOG_TAG = "AndClawHost"
/**
 * 捆绑的 Proot 运行时的压缩包名称
 */
internal const val RUNTIME_ASSET_NAME = "proot-runtime-arm64-v8a.zip"
internal const val RUNTIME_VERSION = "proot-mvp"
internal const val HOST_RUNTIME_LAYOUT_VERSION = "2"
internal const val UBUNTU_RUNTIME_VERSION = "ubuntu-questing-aarch64-pd-v4.37.0"
internal const val UBUNTU_RUNTIME_LAYOUT_VERSION = "3"
internal const val UBUNTU_ROOTFS_URL =
    "https://easycli.sh/proot-distro/ubuntu-questing-aarch64-pd-v4.37.0.tar.xz"
internal const val UBUNTU_ARCHIVE_MIN_BYTES = 40L * 1024 * 1024
internal const val UBUNTU_DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
internal const val UBUNTU_DOWNLOAD_READ_TIMEOUT_MS = 120_000
internal const val UBUNTU_DOWNLOAD_MAX_ATTEMPTS = 3
internal const val NODE_RUNTIME_VERSION = "node-v22.22.1-linux-arm64"
internal const val NODE_RUNTIME_LAYOUT_VERSION = "2"
internal const val NODE_RUNTIME_URL =
    "https://nodejs.org/dist/v22.22.1/node-v22.22.1-linux-arm64.tar.xz"
internal const val NODE_RUNTIME_SHA256 =
    "0f3550d58d45e5d3cf7103d9e3f69937f09fe82fb5dd474c66a5d816fa58c9ee"
internal const val NODE_ARCHIVE_MIN_BYTES = 20L * 1024 * 1024
internal const val NODE_DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
internal const val NODE_DOWNLOAD_READ_TIMEOUT_MS = 120_000
internal const val NODE_DOWNLOAD_MAX_ATTEMPTS = 3
internal const val PROOT_FAKE_KERNEL_RELEASE = "6.17.0-AndClaw"
/**
 * 宿主状态 SharedPreferences 的名称
 */
internal const val PREFS_NAME = "andclaw_host_prefs"
internal const val PREF_GATEWAY_TOKEN = "gateway_token"
/**
 * 设备桥接服务的绑定端口
 */
internal const val DEVICE_BRIDGE_PORT = 18791
/**
 * Gateway 服务的绑定端口
 */
internal const val GATEWAY_PORT = 18789
internal const val MAX_LOG_LINES = 240
internal const val GATEWAY_RPC_OUTPUT_BEGIN = "__ANDCLAW_GATEWAY_RPC_BEGIN__"
internal const val GATEWAY_RPC_OUTPUT_END = "__ANDCLAW_GATEWAY_RPC_END__"

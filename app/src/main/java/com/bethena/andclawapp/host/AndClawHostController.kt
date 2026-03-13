package com.bethena.andclawapp.host

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import com.bethena.andclawapp.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.cert.CertificateExpiredException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLHandshakeException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import org.tukaani.xz.XZInputStream

private const val LOG_TAG = "AndClawHost"
private const val RUNTIME_ASSET_NAME = "proot-runtime-arm64-v8a.zip"
private const val RUNTIME_VERSION = "proot-mvp"
private const val HOST_RUNTIME_LAYOUT_VERSION = "2"
private const val UBUNTU_RUNTIME_VERSION = "ubuntu-questing-aarch64-pd-v4.37.0"
private const val UBUNTU_RUNTIME_LAYOUT_VERSION = "3"
private const val UBUNTU_ROOTFS_URL =
    "https://easycli.sh/proot-distro/ubuntu-questing-aarch64-pd-v4.37.0.tar.xz"
private const val UBUNTU_ARCHIVE_MIN_BYTES = 40L * 1024 * 1024
private const val UBUNTU_DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
private const val UBUNTU_DOWNLOAD_READ_TIMEOUT_MS = 120_000
private const val UBUNTU_DOWNLOAD_MAX_ATTEMPTS = 3
private const val NODE_RUNTIME_VERSION = "node-v22.22.1-linux-arm64"
private const val NODE_RUNTIME_LAYOUT_VERSION = "2"
private const val NODE_RUNTIME_URL =
    "https://nodejs.org/dist/v22.22.1/node-v22.22.1-linux-arm64.tar.xz"
private const val NODE_RUNTIME_SHA256 =
    "0f3550d58d45e5d3cf7103d9e3f69937f09fe82fb5dd474c66a5d816fa58c9ee"
private const val NODE_ARCHIVE_MIN_BYTES = 20L * 1024 * 1024
private const val NODE_DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
private const val NODE_DOWNLOAD_READ_TIMEOUT_MS = 120_000
private const val NODE_DOWNLOAD_MAX_ATTEMPTS = 3
private const val PROOT_FAKE_KERNEL_RELEASE = "6.17.0-AndClaw"
private const val PREFS_NAME = "andclaw_host_prefs"
private const val PREF_GATEWAY_TOKEN = "gateway_token"
private const val DEVICE_BRIDGE_PORT = 18791
private const val GATEWAY_PORT = 18789
private const val MAX_LOG_LINES = 240

data class HostUiState(
    val serviceRunning: Boolean = false,
    val busyTask: String? = null,
    val failedStep: String? = null,
    val busyProgress: Float? = null,
    val busyProgressLabel: String? = null,
    val runtimeInstalled: Boolean = false,
    val openClawInstalled: Boolean = false,
    val gatewayRunning: Boolean = false,
    val bridgeRunning: Boolean = false,
    val bridgePort: Int = DEVICE_BRIDGE_PORT,
    val gatewayPort: Int = GATEWAY_PORT,
    val runtimeSummary: String = "Bundled runtime has not been prepared yet.",
    val gatewayToken: String = "",
    val logs: List<String> = emptyList(),
    val lastError: String? = null,
)

class AndClawHostController(
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskMutex = Mutex()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val currentGatewayProcess = AtomicReference<Process?>(null)
    private var gatewayLogJob: Job? = null
    private var bridgeServer: DeviceBridgeServer? = null

    private val gatewayToken: String by lazy {
        prefs.getString(PREF_GATEWAY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: run {
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(PREF_GATEWAY_TOKEN, created).apply()
            created
        }
    }

    private val _state = MutableStateFlow(
        HostUiState(
            gatewayToken = gatewayToken,
        )
    )
    val state: StateFlow<HostUiState> = _state.asStateFlow()

    private val runtimeRootDir = File(appContext.filesDir, "runtime/versions/$RUNTIME_VERSION")
    private val runtimeBinDir = File(runtimeRootDir, "bin")
    private val runtimeLibDir = File(runtimeRootDir, "lib")
    private val runtimeRootfsDir = File(runtimeRootDir, "rootfs")
    private val prootWrapper = File(runtimeBinDir, "proot")
    private val bundledProotLib = File(runtimeLibDir, "libproot.so")
    private val termuxPrefixHostDir = File(runtimeRootfsDir, "data/data/com.termux/files/usr")
    private val termuxProotLoaderHostFile = File(termuxPrefixHostDir, "libexec/proot/loader")
    private val termuxProotLoader32HostFile = File(termuxPrefixHostDir, "libexec/proot/loader32")
    private val gatewayStateHostDir = File(appContext.filesDir, "gateway-state")
    private val prootTmpHostDir = File(appContext.cacheDir, "proot-tmp")
    private val runtimeDownloadsDir = File(appContext.filesDir, "runtime/downloads")
    private val ubuntuRuntimeDir = File(appContext.filesDir, "runtime/guests/$UBUNTU_RUNTIME_VERSION")
    private val ubuntuRootfsDir = File(ubuntuRuntimeDir, "rootfs")
    private val guestHomeHostDir = File(ubuntuRootfsDir, "root")
    private val guestHomeMountHostDir = gatewayStateHostDir
    private val guestOpenClawHostDir = File(guestHomeHostDir, ".openclaw")
    private val guestCompatHostFile = File(guestOpenClawHostDir, "proot-compat.cjs")
    private val nodeRuntimeHostDir = File(ubuntuRootfsDir, "usr/local")
    private val nodeRuntimeBinHostDir = File(nodeRuntimeHostDir, "bin")
    private val guestUsrLocalBinHostDir = File(nodeRuntimeHostDir, "bin")
    private val guestUsrLocalNodeModulesHostDir = File(nodeRuntimeHostDir, "lib/node_modules")
    private val guestOpenClawPackageHostDir = File(guestUsrLocalNodeModulesHostDir, "openclaw")
    private val guestNpmRuntimeCacheHostDir = File(ubuntuRootfsDir, "tmp/andclaw-npm-cache")
    private val guestNpmRuntimeCacacheHostDir = File(guestNpmRuntimeCacheHostDir, "_cacache")
    private val guestNpmRuntimeTmpHostDir = File(guestNpmRuntimeCacacheHostDir, "tmp")
    private val guestNpmRuntimeContentHostDir = File(guestNpmRuntimeCacacheHostDir, "content-v2")
    private val guestNpmRuntimeIndexHostDir = File(guestNpmRuntimeCacacheHostDir, "index-v5")
    private val guestNpmRuntimeLogsHostDir = File(guestNpmRuntimeCacheHostDir, "_logs")
    private val guestNpmRuntimeScratchHostDir = File(ubuntuRootfsDir, "tmp/andclaw-npm-tmp")
    private val ubuntuArchiveFile = File(runtimeDownloadsDir, "$UBUNTU_RUNTIME_VERSION.tar.xz")
    private val nodeArchiveFile = File(runtimeDownloadsDir, "$NODE_RUNTIME_VERSION.tar.xz")
    private val hostRuntimeLayoutMarker = File(runtimeRootDir, ".andclaw-layout-version")
    private val ubuntuRuntimeLayoutMarker = File(ubuntuRuntimeDir, ".andclaw-layout-version")
    private val ubuntuRuntimeProgressMarker = File(ubuntuRuntimeDir, ".andclaw-extract-progress")
    private val nodeRuntimeLayoutMarker = File(ubuntuRuntimeDir, ".andclaw-node-layout-version")
    private val nodeRuntimeProgressMarker = File(ubuntuRuntimeDir, ".andclaw-node-extract-progress")

    private val guestHomePath = "/root"
    private val guestHomeMountPath = "/root/home"
    private val guestOpenClawPath = "$guestHomePath/.openclaw"
    private val guestCompatPath = "$guestOpenClawPath/proot-compat.cjs"
    private val guestNodeRuntimePath = "/usr/local"
    private val guestNodeBinPath = "$guestNodeRuntimePath/bin"
    private val guestNodeBinaryPath = "$guestNodeBinPath/node"
    private val guestNpmCliPath = "$guestNodeRuntimePath/lib/node_modules/npm/bin/npm-cli.js"
    private val guestOpenClawPackagePath = "$guestNodeRuntimePath/lib/node_modules/openclaw"
    private val guestNpmCachePath = "/tmp/andclaw-npm-cache"
    private val guestNpmTmpPath = "/tmp/andclaw-npm-tmp"
    private val guestOpenClawBinPath = "$guestNodeBinPath/openclaw"
    private val guestWorkingPath = guestHomePath
    private val bridgeUrl = "http://127.0.0.1:$DEVICE_BRIDGE_PORT"
    private val packagedHostProot: File?
        get() {
            val nativeDir = appContext.applicationInfo.nativeLibraryDir ?: return null
            return File(nativeDir, "libproot.so").takeIf { it.isFile && it.canExecute() }
        }
    private val legacyDataPrefix = "/data/data/${appContext.packageName}"

    init {
        scope.launch {
            try {
                ensureBridgeServer()
                refreshState()
                if (!isRuntimePrepared()) {
                    addLog("Bundled runtime is waiting to be prepared.")
                } else {
                    addLog("Bundled runtime already present.")
                }
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                Log.e(LOG_TAG, "Failed to initialize bundled runtime host", t)
                addLog("ERROR: $message")
                _state.update { it.copy(lastError = message) }
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _state.update { it.copy(serviceRunning = running) }
    }

    fun bootstrapHost() {
        scope.launch {
            runBusyTask("Starting local bridge") {
                ensureBridgeServer()

                updateBusyTask("Preparing host runtime")
                importBundledRuntimeIfNeeded()

                ensureUbuntuGuestRootfs()
                updateBusyTask("Preparing Node runtime")
                ensureBundledNodeRuntime()

                ensureOpenClawInstalled(forceReinstall = false)

                updateBusyTask("Starting OpenClaw gateway")
                if (!probeOpenClawInstalled()) {
                    throw IOException("OpenClaw is not installed inside the bundled runtime yet.")
                }
                if (currentGatewayProcess.get()?.isAlive == true) {
                    addLog("Gateway is already running on port $GATEWAY_PORT.")
                } else {
                    val command = buildGatewayCommand()
                    addLog("Launching Gateway on loopback:$GATEWAY_PORT.")
                    val process = buildShellProcess(command)
                    currentGatewayProcess.set(process)
                    gatewayLogJob?.cancel()
                    gatewayLogJob = scope.launch {
                        streamProcessOutput(process, "gateway")
                        val exitCode = process.waitFor()
                        addLog("Gateway process exited with code $exitCode.")
                        currentGatewayProcess.compareAndSet(process, null)
                        refreshState(errorMessage = if (exitCode == 0) null else "Gateway exited with code $exitCode")
                    }
                }

                refreshState()
            }
        }
    }

    fun installOrUpdateOpenClaw() {
        scope.launch {
            runBusyTask("Installing OpenClaw") {
                ensureBridgeServer()
                importBundledRuntimeIfNeeded()
                ensureUbuntuGuestRootfs()
                updateBusyTask("Preparing Node runtime")
                ensureBundledNodeRuntime()
                ensureOpenClawInstalled(forceReinstall = true)
                refreshState()
            }
        }
    }

    fun startGateway() {
        scope.launch {
            runBusyTask("Starting Gateway") {
                ensureBridgeServer()
                importBundledRuntimeIfNeeded()
                ensureUbuntuGuestRootfs()
                updateBusyTask("Preparing Node runtime")
                ensureBundledNodeRuntime()
                if (!probeOpenClawInstalled()) {
                    throw IOException("OpenClaw is not installed inside the bundled runtime yet.")
                }
                if (currentGatewayProcess.get()?.isAlive == true) {
                    addLog("Gateway is already running on port $GATEWAY_PORT.")
                    refreshState()
                    return@runBusyTask
                }
                val command = buildGatewayCommand()
                addLog("Launching Gateway on loopback:$GATEWAY_PORT.")
                val process = buildShellProcess(command)
                currentGatewayProcess.set(process)
                gatewayLogJob?.cancel()
                gatewayLogJob = scope.launch {
                    streamProcessOutput(process, "gateway")
                    val exitCode = process.waitFor()
                    addLog("Gateway process exited with code $exitCode.")
                    currentGatewayProcess.compareAndSet(process, null)
                    refreshState(errorMessage = if (exitCode == 0) null else "Gateway exited with code $exitCode")
                }
                refreshState()
            }
        }
    }

    fun stopGateway() {
        scope.launch {
            val process = currentGatewayProcess.getAndSet(null)
            if (process == null) {
                addLog("Gateway is not running.")
                refreshState()
                return@launch
            }
            addLog("Stopping Gateway process.")
            process.destroy()
            gatewayLogJob?.cancel()
            gatewayLogJob = null
            if (process.isAlive) {
                process.destroyForcibly()
            }
            refreshState()
        }
    }

    private suspend fun runBusyTask(
        title: String,
        block: suspend () -> Unit,
    ) {
        taskMutex.withLock {
            updateBusyTask(title)
            try {
                block()
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                val failedStep = state.value.busyTask ?: title
                Log.e(LOG_TAG, failedStep, t)
                addLog("ERROR [$failedStep]: $message")
                _state.update {
                    it.copy(
                        lastError = message,
                        failedStep = failedStep,
                        busyProgress = null,
                        busyProgressLabel = null,
                    )
                }
            } finally {
                _state.update {
                    it.copy(
                        busyTask = null,
                        busyProgress = null,
                        busyProgressLabel = null,
                    )
                }
            }
        }
    }

    private fun updateBusyTask(title: String) {
        _state.update {
            it.copy(
                busyTask = title,
                lastError = null,
                failedStep = null,
                busyProgress = null,
                busyProgressLabel = null,
            )
        }
        addLog(title)
    }

    private fun updateBusyProgress(
        fraction: Float?,
        label: String,
    ) {
        _state.update {
            it.copy(
                busyProgress = fraction?.coerceIn(0f, 1f),
                busyProgressLabel = label,
            )
        }
    }

    private suspend fun ensureOpenClawInstalled(forceReinstall: Boolean) {
        updateBusyTask("Installing OpenClaw")
        updateBusyProgress(
            fraction = 0.05f,
            label = "检查 OpenClaw 安装状态",
        )
        if (!forceReinstall && probeOpenClawInstalled()) {
            addLog("OpenClaw already installed.")
            updateBusyProgress(
                fraction = 1f,
                label = "OpenClaw 已安装，无需重新安装",
            )
            return
        }

        updateBusyProgress(
            fraction = 0.18f,
            label = "准备 OpenClaw 安装目录",
        )
        prepareOpenClawInstallWorkspace()

        updateBusyProgress(
            fraction = 0.34f,
            label = "检查 Node 与 npm 运行环境",
        )
        logInstallPreflight()

        updateBusyProgress(
            fraction = 0.52f,
            label = "正在下载并安装 OpenClaw",
        )
        val exitCode = runOneShotShellCommand(
            command = buildOpenClawInstallCommand(),
            label = "install",
        )
        if (exitCode != 0) {
            throw IOException("OpenClaw installer exited with code $exitCode")
        }

        updateBusyProgress(
            fraction = 0.84f,
            label = "正在写入 OpenClaw 命令入口",
        )
        ensureGuestNodePackageWrappers("openclaw")

        updateBusyProgress(
            fraction = 0.94f,
            label = "正在校验 OpenClaw 安装结果",
        )
        if (!probeOpenClawInstalled()) {
            throw IOException("OpenClaw installation verification failed.")
        }

        updateBusyProgress(
            fraction = 1f,
            label = "OpenClaw 安装完成",
        )
    }

    private suspend fun ensureBridgeServer() {
        if (bridgeServer != null) {
            return
        }
        val server = DeviceBridgeServer(
            port = DEVICE_BRIDGE_PORT,
            responseProvider = { path -> buildBridgeResponse(path) },
            onLog = { addLog(it) },
        )
        server.start(scope)
        bridgeServer = server
        refreshState()
    }

    private fun buildBridgeResponse(path: String): DeviceBridgeServer.Response {
        val body = when (path.substringBefore('?')) {
            "/", "/health" -> healthJson()
            "/device/info" -> deviceInfoJson()
            "/device/status" -> deviceStatusJson()
            "/host/state" -> hostStateJson()
            else -> JSONObject().put("error", "not_found").put("path", path).toString()
        }
        val status = if (path.substringBefore('?') in setOf("/", "/health", "/device/info", "/device/status", "/host/state")) 200 else 404
        return DeviceBridgeServer.Response(
            statusCode = status,
            body = body,
        )
    }

    private fun healthJson(): String {
        return JSONObject()
            .put("ok", true)
            .put("bridgePort", DEVICE_BRIDGE_PORT)
            .put("gatewayPort", GATEWAY_PORT)
            .put("runtimeInstalled", isRuntimePrepared())
            .put("openclawInstalled", state.value.openClawInstalled)
            .put("gatewayRunning", currentGatewayProcess.get()?.isAlive == true)
            .toString()
    }

    private fun deviceInfoJson(): String {
        return JSONObject()
            .put("appId", appContext.packageName)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("device", Build.DEVICE)
            .put("model", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("uptimeSeconds", (SystemClock.elapsedRealtime() / 1000.0 * 10).roundToInt() / 10.0)
            .toString()
    }

    private fun deviceStatusJson(): String {
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val batteryPct = if (level in 0..100) level else JSONObject.NULL
        return JSONObject()
            .put("batteryPercent", batteryPct)
            .put("bridgeUrl", bridgeUrl)
            .put("runtimeInstalled", isRuntimePrepared())
            .put("gatewayRunning", currentGatewayProcess.get()?.isAlive == true)
            .put("openclawInstalled", state.value.openClawInstalled)
            .toString()
    }

    private fun hostStateJson(): String {
        val current = state.value
        return JSONObject()
            .put("serviceRunning", current.serviceRunning)
            .put("busyTask", current.busyTask ?: JSONObject.NULL)
            .put("failedStep", current.failedStep ?: JSONObject.NULL)
            .put("busyProgress", current.busyProgress ?: JSONObject.NULL)
            .put("busyProgressLabel", current.busyProgressLabel ?: JSONObject.NULL)
            .put("lastError", current.lastError ?: JSONObject.NULL)
            .put("runtimeSummary", current.runtimeSummary)
            .put("gatewayPort", current.gatewayPort)
            .put("bridgePort", current.bridgePort)
            .put("openclawInstalled", current.openClawInstalled)
            .put("gatewayRunning", current.gatewayRunning)
            .toString()
    }

    private suspend fun importBundledRuntimeIfNeeded() {
        if (isHostRuntimePrepared()) {
            addLog("Runtime already prepared at ${runtimeRootDir.absolutePath}.")
            ensureRuntimeHelpers()
            return
        }

        if (runtimeRootDir.exists()) {
            addLog("Bundled runtime layout changed, rebuilding host runtime.")
        }

        addLog("Importing bundled proot runtime into ${runtimeRootDir.absolutePath}.")
        runtimeRootDir.deleteRecursively()
        runtimeRootDir.mkdirs()
        unzipRuntimeAsset()
        copyBundledProotLibrary()
        restoreSymlinksIfPresent(File(runtimeRootDir, "SYMLINKS.txt"))
        restoreSymlinksIfPresent(File(runtimeRootDir, "rootfs/data/data/com.termux/files/usr/SYMLINKS.txt"))
        markTreeExecutable(runtimeRootDir)
        writeTextFile(hostRuntimeLayoutMarker, HOST_RUNTIME_LAYOUT_VERSION)
        ensureRuntimeHelpers()
        addLog("Bundled runtime imported successfully.")
    }

    private suspend fun ensureUbuntuGuestRootfs() {
        if (isUbuntuGuestPrepared()) {
            addLog("Ubuntu guest rootfs already prepared at ${ubuntuRootfsDir.absolutePath}.")
            ensureRuntimeHelpers()
            return
        }

        if (ubuntuRuntimeDir.exists()) {
            addLog("Ubuntu guest runtime layout changed, rebuilding guest rootfs.")
        }

        updateBusyTask("Downloading Ubuntu runtime")
        downloadUbuntuArchiveIfNeeded()

        updateBusyTask("Extracting Ubuntu runtime")
        extractUbuntuArchiveIfNeeded()
        ensureRuntimeHelpers()
    }

    private suspend fun ensureBundledNodeRuntime() {
        if (isNodeRuntimePrepared()) {
            addLog("Bundled Node runtime already prepared at ${nodeRuntimeHostDir.absolutePath}.")
            return
        }

        if (nodeRuntimeHostDir.exists()) {
            addLog("Bundled Node runtime layout changed, rebuilding Node runtime.")
        }

        updateBusyTask("Downloading Node runtime")
        downloadNodeArchiveIfNeeded()

        updateBusyTask("Extracting Node runtime")
        extractNodeArchiveIfNeeded()
    }

    private fun prepareOpenClawInstallWorkspace() {
        addLog("Resetting OpenClaw install workspace.")
        guestNpmRuntimeCacheHostDir.deleteRecursively()
        guestNpmRuntimeScratchHostDir.deleteRecursively()
        guestOpenClawPackageHostDir.deleteRecursively()
        File(guestUsrLocalBinHostDir, "openclaw").delete()
        ensureRuntimeHelpers()
    }

    private fun unzipRuntimeAsset() {
        appContext.assets.open(RUNTIME_ASSET_NAME).use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = safeResolveZipEntry(runtimeRootDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun safeResolveZipEntry(root: File, name: String): File {
        val resolved = File(root, name)
        val rootPath = root.canonicalPath
        val candidatePath = resolved.canonicalPath
        if (!candidatePath.startsWith(rootPath)) {
            throw IOException("Archive entry escaped destination: $name")
        }
        return resolved
    }

    private fun copyBundledProotLibrary() {
        runtimeLibDir.mkdirs()
        val nativeDir = appContext.applicationInfo.nativeLibraryDir ?: throw IOException("nativeLibraryDir missing")
        val source = File(nativeDir, "libproot.so")
        if (!source.exists()) {
            throw IOException("libproot.so was not packaged into the APK")
        }
        source.copyTo(bundledProotLib, overwrite = true)
        bundledProotLib.setExecutable(true, false)
    }

    private fun restoreSymlinksIfPresent(manifestFile: File) {
        if (!manifestFile.exists()) {
            return
        }
        manifestFile.readLines(StandardCharsets.UTF_8).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                return@forEach
            }
            val pair = parseSymlinkLine(line) ?: return@forEach
            val linkFile = File(runtimeRootDir, pair.second.removePrefix("./"))
            linkFile.parentFile?.mkdirs()
            try {
                if (linkFile.exists() || linkFile.isSymbolicLinkCompat()) {
                    linkFile.delete()
                }
                Os.symlink(pair.first, linkFile.absolutePath)
            } catch (err: ErrnoException) {
                if (err.errno != android.system.OsConstants.EEXIST) {
                    throw IOException("Failed to restore symlink for ${linkFile.absolutePath}: ${err.message}", err)
                }
            }
        }
    }

    private fun downloadUbuntuArchiveIfNeeded() {
        downloadArchiveIfNeeded(
            label = "Ubuntu guest archive",
            archiveFile = ubuntuArchiveFile,
            minBytes = UBUNTU_ARCHIVE_MIN_BYTES,
            downloadUrl = URL(UBUNTU_ROOTFS_URL),
            connectTimeoutMs = UBUNTU_DOWNLOAD_CONNECT_TIMEOUT_MS,
            readTimeoutMs = UBUNTU_DOWNLOAD_READ_TIMEOUT_MS,
            maxAttempts = UBUNTU_DOWNLOAD_MAX_ATTEMPTS,
        )
    }

    private fun downloadNodeArchiveIfNeeded() {
        runtimeDownloadsDir.mkdirs()
        if (nodeArchiveFile.isFile && nodeArchiveFile.length() >= NODE_ARCHIVE_MIN_BYTES) {
            try {
                validateNodeArchive(nodeArchiveFile)
                addLog("Node runtime archive already cached at ${nodeArchiveFile.absolutePath}.")
                return
            } catch (err: IOException) {
                addLog("Cached Node runtime archive is invalid, redownloading: ${err.message}")
                nodeArchiveFile.delete()
            }
        }

        downloadArchiveIfNeeded(
            label = "Node runtime archive",
            archiveFile = nodeArchiveFile,
            minBytes = NODE_ARCHIVE_MIN_BYTES,
            downloadUrl = URL(NODE_RUNTIME_URL),
            connectTimeoutMs = NODE_DOWNLOAD_CONNECT_TIMEOUT_MS,
            readTimeoutMs = NODE_DOWNLOAD_READ_TIMEOUT_MS,
            maxAttempts = NODE_DOWNLOAD_MAX_ATTEMPTS,
        )
        validateNodeArchive(nodeArchiveFile)
        addLog("Node runtime archive checksum verified.")
    }

    private fun downloadArchiveIfNeeded(
        label: String,
        archiveFile: File,
        minBytes: Long,
        downloadUrl: URL,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        maxAttempts: Int,
    ) {
        runtimeDownloadsDir.mkdirs()
        if (archiveFile.isFile && archiveFile.length() >= minBytes) {
            addLog("$label already cached at ${archiveFile.absolutePath}.")
            return
        }

        addLog("Downloading $label from $downloadUrl.")
        var lastError: IOException? = null

        repeat(maxAttempts) { attemptIndex ->
            val attempt = attemptIndex + 1
            if (archiveFile.exists()) {
                archiveFile.delete()
            }
            if (attempt > 1) {
                addLog("Retrying $label download (attempt $attempt/$maxAttempts).")
            }

            try {
                downloadArchiveOnce(
                    label = label,
                    archiveFile = archiveFile,
                    minBytes = minBytes,
                    downloadUrl = downloadUrl,
                    connectTimeoutMs = connectTimeoutMs,
                    readTimeoutMs = readTimeoutMs,
                )
                return
            } catch (err: IOException) {
                val explained = explainDownloadFailure(downloadUrl, err)
                lastError = explained
                if (attempt >= maxAttempts || !shouldRetryUbuntuDownload(explained)) {
                    throw explained
                }
                addLog("$label download attempt $attempt failed: ${explained.message}.")
            }
        }

        throw lastError ?: IOException("$label download failed.")
    }

    private fun downloadArchiveOnce(
        label: String,
        archiveFile: File,
        minBytes: Long,
        downloadUrl: URL,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ) {
        val connection =
            try {
                (downloadUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                }
            } catch (err: IOException) {
                throw explainDownloadFailure(downloadUrl, err)
            }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("$label download failed with HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var downloadedBytes = 0L
            var nextLogThreshold = 5L * 1024 * 1024
            var lastProgressPercent = -1
            updateBusyProgress(
                fraction = if (totalBytes != null) 0f else null,
                label = formatByteProgress(0L, totalBytes),
            )

            connection.inputStream.use { input ->
                archiveFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes != null) {
                            val progressPercent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (progressPercent != lastProgressPercent) {
                                lastProgressPercent = progressPercent
                                updateBusyProgress(
                                    fraction = downloadedBytes.toFloat() / totalBytes.toFloat(),
                                    label = formatByteProgress(downloadedBytes, totalBytes),
                                )
                            }
                        } else if (downloadedBytes >= nextLogThreshold) {
                            updateBusyProgress(
                                fraction = null,
                                label = formatByteProgress(downloadedBytes, null),
                            )
                        }
                        if (downloadedBytes >= nextLogThreshold) {
                            val downloadedMb = downloadedBytes / (1024 * 1024)
                            val totalMb = totalBytes?.div(1024 * 1024)
                            if (totalMb != null) {
                                addLog("$label download progress: ${downloadedMb}MB / ${totalMb}MB.")
                            } else {
                                addLog("$label download progress: ${downloadedMb}MB.")
                            }
                            nextLogThreshold += 5L * 1024 * 1024
                        }
                    }
                }
            }

            if (archiveFile.length() < minBytes) {
                throw IOException("$label download is incomplete (${archiveFile.length()} bytes).")
            }
            updateBusyProgress(
                fraction = 1f,
                label = formatByteProgress(archiveFile.length(), totalBytes ?: archiveFile.length()),
            )
            addLog("$label downloaded to ${archiveFile.absolutePath}.")
        } finally {
            connection.disconnect()
        }
    }

    private fun shouldRetryUbuntuDownload(err: IOException): Boolean {
        if (err is SSLHandshakeException || err.findCause<CertificateExpiredException>() != null) {
            return false
        }
        if (err is SocketTimeoutException) {
            return true
        }
        val message = err.message?.lowercase(Locale.US).orEmpty()
        return "timeout" in message || "connection reset" in message || "unexpected end of stream" in message
    }

    private fun explainDownloadFailure(
        url: URL,
        err: IOException,
    ): IOException {
        if (err is SSLHandshakeException || err.findCause<CertificateExpiredException>() != null) {
            return IOException(
                "HTTPS certificate validation failed for ${url.host}. The server certificate appears to be expired or invalid.",
                err,
            )
        }
        return err
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun formatByteProgress(
        currentBytes: Long,
        totalBytes: Long?,
    ): String {
        val currentMb = currentBytes / (1024.0 * 1024.0)
        return if (totalBytes != null && totalBytes > 0L) {
            val totalMb = totalBytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.1f MB / %.1f MB", currentMb, totalMb)
        } else {
            String.format(Locale.US, "%.1f MB downloaded", currentMb)
        }
    }

    private fun formatEntryProgress(
        processedEntries: Int,
        totalEntries: Int?,
        noun: String,
    ): String {
        return if (totalEntries != null && totalEntries > 0) {
            "$noun $processedEntries / $totalEntries 项"
        } else {
            "$noun $processedEntries 项"
        }
    }

    private fun countArchiveEntries(archiveFile: File): Int {
        var entryCount = 0
        TarArchiveInputStream(XZInputStream(BufferedInputStream(FileInputStream(archiveFile)))).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                if (stripArchiveRoot(entry.name) != null) {
                    entryCount += 1
                }
                entry = tar.nextTarEntry
            }
        }
        return entryCount
    }

    private fun extractUbuntuArchiveIfNeeded() {
        if (isUbuntuGuestPrepared()) {
            return
        }

        addLog("Extracting Ubuntu guest rootfs into ${ubuntuRootfsDir.absolutePath}.")
        ubuntuRuntimeDir.deleteRecursively()
        ubuntuRootfsDir.mkdirs()
        writeTextFile(ubuntuRuntimeProgressMarker, "starting\n")

        var extractedEntries = 0
        TarArchiveInputStream(XZInputStream(BufferedInputStream(FileInputStream(ubuntuArchiveFile)))).use { tar ->
            var entry = tar.nextTarEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                val relativePath = stripArchiveRoot(entry.name)
                if (relativePath != null) {
                    if (extractedEntries == 0 || extractedEntries % 250 == 0) {
                        writeTextFile(
                            ubuntuRuntimeProgressMarker,
                            "entry=${extractedEntries + 1}\npath=${entry.name}\nsize=${entry.size}\n"
                        )
                    }
                    val outFile = safeResolveZipEntry(ubuntuRootfsDir, relativePath)
                    when {
                        entry.isDirectory -> {
                            outFile.mkdirs()
                            applyPosixMode(outFile, entry.mode)
                        }
                        entry.isSymbolicLink -> {
                            outFile.parentFile?.mkdirs()
                            try {
                                if (outFile.exists() || outFile.isSymbolicLinkCompat()) {
                                    outFile.delete()
                                }
                                Os.symlink(entry.linkName, outFile.absolutePath)
                            } catch (err: ErrnoException) {
                                if (err.errno != android.system.OsConstants.EEXIST) {
                                    throw IOException("Failed to restore Ubuntu symlink $relativePath", err)
                                }
                            }
                        }
                        entry.isLink -> {
                            val target = safeResolveZipEntry(ubuntuRootfsDir, stripArchiveRoot(entry.linkName) ?: entry.linkName)
                            outFile.parentFile?.mkdirs()
                            target.copyTo(outFile, overwrite = true)
                            applyPosixMode(outFile, entry.mode)
                        }
                        entry.isFile -> {
                            outFile.parentFile?.mkdirs()
                            copyTarEntryContents(
                                tar = tar,
                                entryName = entry.name,
                                entrySize = entry.size,
                                buffer = buffer,
                                outputFile = outFile,
                                label = "Ubuntu rootfs",
                            )
                            applyPosixMode(outFile, entry.mode)
                        }
                    }
                    extractedEntries += 1
                    if (extractedEntries % 2000 == 0) {
                        addLog("Ubuntu rootfs extraction progress: ${extractedEntries} entries.")
                    }
                }
                entry = tar.nextTarEntry
            }
        }

        listOf("tmp", "run", "dev", "proc", "sys", "root").forEach { path ->
            File(ubuntuRootfsDir, path).mkdirs()
        }
        applyPosixMode(File(ubuntuRootfsDir, "tmp"), 0b1111111111)
        applyPosixMode(File(ubuntuRootfsDir, "var/tmp"), 0b1111111111)
        applyPosixMode(File(ubuntuRootfsDir, "run"), 0b111101101)
        applyPosixMode(File(ubuntuRootfsDir, "dev"), 0b111101101)
        applyPosixMode(File(ubuntuRootfsDir, "proc"), 0b111101101)
        applyPosixMode(File(ubuntuRootfsDir, "sys"), 0b111101101)
        applyPosixMode(File(ubuntuRootfsDir, "root"), 0b111000000)

        if (
            !ubuntuRootfsDir.isDirectory ||
            !File(ubuntuRootfsDir, "bin/bash").isFile ||
            !File(ubuntuRootfsDir, "etc/os-release").isFile
        ) {
            throw IOException("Ubuntu guest rootfs extraction did not produce a runnable filesystem.")
        }
        writeTextFile(ubuntuRuntimeLayoutMarker, UBUNTU_RUNTIME_LAYOUT_VERSION)
        ubuntuRuntimeProgressMarker.delete()
        addLog("Ubuntu guest rootfs extracted successfully.")
    }

    private fun extractNodeArchiveIfNeeded() {
        if (isNodeRuntimePrepared()) {
            return
        }

        addLog("Extracting Node runtime into ${nodeRuntimeHostDir.absolutePath}.")
        nodeRuntimeHostDir.deleteRecursively()
        nodeRuntimeHostDir.mkdirs()
        writeTextFile(nodeRuntimeProgressMarker, "starting\n")
        val totalEntries = countArchiveEntries(nodeArchiveFile).takeIf { it > 0 }
        updateBusyProgress(
            fraction = if (totalEntries != null) 0f else null,
            label = formatEntryProgress(0, totalEntries, "已解压"),
        )

        var extractedEntries = 0
        TarArchiveInputStream(XZInputStream(BufferedInputStream(FileInputStream(nodeArchiveFile)))).use { tar ->
            var entry = tar.nextTarEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                val relativePath = stripArchiveRoot(entry.name)
                if (relativePath != null) {
                    if (extractedEntries == 0 || extractedEntries % 100 == 0) {
                        writeTextFile(
                            nodeRuntimeProgressMarker,
                            "entry=${extractedEntries + 1}\npath=${entry.name}\nsize=${entry.size}\n"
                        )
                    }
                    val outFile = safeResolveZipEntry(nodeRuntimeHostDir, relativePath)
                    when {
                        entry.isDirectory -> {
                            outFile.mkdirs()
                            applyPosixMode(outFile, entry.mode)
                        }
                        entry.isSymbolicLink -> {
                            outFile.parentFile?.mkdirs()
                            try {
                                if (outFile.exists() || outFile.isSymbolicLinkCompat()) {
                                    outFile.delete()
                                }
                                Os.symlink(entry.linkName, outFile.absolutePath)
                            } catch (err: ErrnoException) {
                                if (err.errno != android.system.OsConstants.EEXIST) {
                                    throw IOException("Failed to restore Node symlink $relativePath", err)
                                }
                            }
                        }
                        entry.isLink -> {
                            val target = safeResolveZipEntry(nodeRuntimeHostDir, stripArchiveRoot(entry.linkName) ?: entry.linkName)
                            outFile.parentFile?.mkdirs()
                            target.copyTo(outFile, overwrite = true)
                            applyPosixMode(outFile, entry.mode)
                        }
                        entry.isFile -> {
                            outFile.parentFile?.mkdirs()
                            copyTarEntryContents(
                                tar = tar,
                                entryName = entry.name,
                                entrySize = entry.size,
                                buffer = buffer,
                                outputFile = outFile,
                                label = "Node runtime",
                            )
                            applyPosixMode(outFile, entry.mode)
                        }
                    }
                    extractedEntries += 1
                    if (extractedEntries <= 3 || extractedEntries % 25 == 0 || extractedEntries == totalEntries) {
                        updateBusyProgress(
                            fraction =
                                totalEntries?.let {
                                    (extractedEntries.toFloat() / it.toFloat()).coerceIn(0f, 1f)
                                },
                            label = formatEntryProgress(extractedEntries, totalEntries, "已解压"),
                        )
                    }
                    if (extractedEntries % 500 == 0) {
                        addLog("Node runtime extraction progress: ${extractedEntries} entries.")
                    }
                }
                entry = tar.nextTarEntry
            }
        }

        markTreeExecutable(nodeRuntimeBinHostDir)
        val nodeBinary = File(nodeRuntimeBinHostDir, "node")
        val npmBinary = File(nodeRuntimeBinHostDir, "npm")
        if (!nodeBinary.isFile || !npmBinary.isFile) {
            throw IOException("Node runtime extraction did not produce node and npm binaries.")
        }
        writeTextFile(nodeRuntimeLayoutMarker, NODE_RUNTIME_LAYOUT_VERSION)
        nodeRuntimeProgressMarker.delete()
        updateBusyProgress(
            fraction = 1f,
            label = formatEntryProgress(extractedEntries, totalEntries ?: extractedEntries, "已解压"),
        )
        addLog("Node runtime extracted successfully.")
    }

    private fun validateNodeArchive(file: File) {
        val actual = sha256(file)
        if (!actual.equals(NODE_RUNTIME_SHA256, ignoreCase = true)) {
            throw IOException("Expected SHA-256 $NODE_RUNTIME_SHA256 but got $actual")
        }
    }

    private fun copyTarEntryContents(
        tar: TarArchiveInputStream,
        entryName: String,
        entrySize: Long,
        buffer: ByteArray,
        outputFile: File,
        label: String,
    ) {
        outputFile.outputStream().use { output ->
            var remaining = entrySize
            var zeroReadCount = 0
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = tar.read(buffer, 0, toRead)
                when {
                    read < 0 -> {
                        throw IOException(
                            "$label entry ended early for $entryName after ${entrySize - remaining} of $entrySize bytes."
                        )
                    }
                    read == 0 -> {
                        zeroReadCount += 1
                        if (zeroReadCount >= 32) {
                            throw IOException("$label extraction stalled on $entryName with $remaining bytes remaining.")
                        }
                        Thread.yield()
                    }
                    else -> {
                        zeroReadCount = 0
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun writeTextFile(file: File, value: String) {
        file.parentFile?.mkdirs()
        file.writeText(value, StandardCharsets.UTF_8)
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun hasLayoutMarker(file: File, expected: String): Boolean {
        return try {
            file.isFile && file.readText(StandardCharsets.UTF_8).trim() == expected
        } catch (_: IOException) {
            false
        }
    }

    private fun stripArchiveRoot(path: String): String? {
        val cleaned = path.trim().removePrefix("./").trimStart('/')
        if (cleaned.isEmpty()) {
            return null
        }
        val separator = cleaned.indexOf('/')
        return if (separator == -1) {
            null
        } else {
            cleaned.substring(separator + 1).takeIf { it.isNotBlank() }
        }
    }

    private fun parseSymlinkLine(line: String): Pair<String, String>? {
        return when {
            line.contains("\u2190") -> {
                val parts = line.split("\u2190", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            line.contains("->") -> {
                val parts = line.split("->", limit = 2)
                if (parts.size == 2) parts[1].trim() to parts[0].trim() else null
            }
            else -> null
        }
    }

    private fun applyPosixMode(
        file: File,
        mode: Int,
    ) {
        if (!file.exists() || file.isSymbolicLinkCompat()) {
            return
        }
        try {
            Os.chmod(file.absolutePath, mode and 0xFFF)
        } catch (_: ErrnoException) {
            val executable = mode and 0b001001001 != 0
            file.setReadable(true, false)
            file.setWritable(true, true)
            file.setExecutable(executable, false)
        }
    }

    private fun markTreeExecutable(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                file.setWritable(true, true)
                file.setExecutable(true, false)
            }
        }
    }

    private fun ensureRuntimeHelpers() {
        guestHomeHostDir.mkdirs()
        guestHomeMountHostDir.mkdirs()
        guestOpenClawHostDir.mkdirs()
        guestUsrLocalBinHostDir.mkdirs()
        guestUsrLocalNodeModulesHostDir.mkdirs()
        ensureUbuntuGuestCompatibilityFiles()
        writeGuestProotCompatFile()
        writeBridgeScript(
            name = "andclaw-device-info",
            path = "/device/info",
        )
        writeBridgeScript(
            name = "andclaw-device-status",
            path = "/device/status",
        )
        writeBridgeScript(
            name = "andclaw-host-state",
            path = "/host/state",
        )
    }

    private fun writeGuestProotCompatFile() {
        guestCompatHostFile.parentFile?.mkdirs()
        guestCompatHostFile.writeText(
            """
            'use strict';

            const fs = require('node:fs');
            const path = require('node:path');
            const os = require('node:os');

            const fallbackCwd = process.env.PWD || process.env.HOME || '/root';
            const originalCwd = process.cwd.bind(process);
            const originalChdir = process.chdir.bind(process);

            process.cwd = function andClawCompatCwd() {
              try {
                return originalCwd();
              } catch (error) {
                if (error && (error.code === 'ENOSYS' || error.code === 'ENOENT')) {
                  return process.env.PWD || fallbackCwd;
                }
                throw error;
              }
            };

            process.chdir = function andClawCompatChdir(directory) {
              try {
                return originalChdir(directory);
              } catch (error) {
                if (error && (error.code === 'ENOSYS' || error.code === 'ENOENT')) {
                  process.env.PWD = directory || fallbackCwd;
                  return;
                }
                throw error;
              }
            };

            process.env.PWD = process.env.PWD || fallbackCwd;

            const originalHomedir = os.homedir.bind(os);
            os.homedir = function andClawCompatHomedir() {
              try {
                return originalHomedir();
              } catch (_) {
                return process.env.HOME || '/root';
              }
            };

            const originalTmpdir = os.tmpdir.bind(os);
            os.tmpdir = function andClawCompatTmpdir() {
              try {
                return originalTmpdir() || '/tmp';
              } catch (_) {
                return '/tmp';
              }
            };

            const originalHostname = os.hostname.bind(os);
            os.hostname = function andClawCompatHostname() {
              try {
                return originalHostname();
              } catch (_) {
                return 'localhost';
              }
            };

            const fallbackNetworkInterfaces = Object.freeze({
              lo: [
                {
                  address: '127.0.0.1',
                  netmask: '255.0.0.0',
                  family: 'IPv4',
                  mac: '00:00:00:00:00:00',
                  internal: true,
                  cidr: '127.0.0.1/8',
                },
              ],
              andclaw0: [
                {
                  address: '127.0.0.1',
                  netmask: '255.0.0.0',
                  family: 'IPv4',
                  mac: '02:00:00:00:00:01',
                  internal: false,
                  cidr: '127.0.0.1/8',
                },
              ],
            });

            const originalNetworkInterfaces = os.networkInterfaces.bind(os);
            os.networkInterfaces = function andClawCompatNetworkInterfaces() {
              try {
                return originalNetworkInterfaces();
              } catch (_) {
                return fallbackNetworkInterfaces;
              }
            };

            function mkdirFallback(target) {
              const resolved = path.resolve(String(target));
              const parts = resolved.split(path.sep).filter(Boolean);
              let cursor = resolved.startsWith(path.sep) ? path.sep : '';
              for (const part of parts) {
                cursor = cursor === path.sep ? cursor + part : cursor + path.sep + part;
                try {
                  fs.mkdirSync(cursor);
                } catch (error) {
                  if (!error || (error.code !== 'EEXIST' && error.code !== 'EISDIR')) {
                    // Ignore proot mkdir oddities and keep walking the tree.
                  }
                }
              }
            }

            const originalMkdirSync = fs.mkdirSync.bind(fs);
            fs.mkdirSync = function andClawCompatMkdirSync(target, options) {
              try {
                return originalMkdirSync(target, options);
              } catch (error) {
                if (error && (error.code === 'ENOSYS' || (error.code === 'ENOENT' && options && options.recursive))) {
                  mkdirFallback(target);
                  return undefined;
                }
                if (error && (error.code === 'EEXIST' || error.code === 'EISDIR')) {
                  return undefined;
                }
                throw error;
              }
            };

            const originalMkdir = fs.mkdir.bind(fs);
            fs.mkdir = function andClawCompatMkdir(target, options, callback) {
              if (typeof options === 'function') {
                callback = options;
                options = undefined;
              }
              try {
                fs.mkdirSync(target, options);
                if (callback) callback(null);
              } catch (error) {
                if (callback) callback(error);
                else throw error;
              }
            };

            if (fs.promises && fs.promises.mkdir) {
              const originalPromisesMkdir = fs.promises.mkdir.bind(fs.promises);
              fs.promises.mkdir = async function andClawCompatPromisesMkdir(target, options) {
                try {
                  return await originalPromisesMkdir(target, options);
                } catch (error) {
                  if (error && (error.code === 'ENOSYS' || (error.code === 'ENOENT' && options && options.recursive))) {
                    mkdirFallback(target);
                    return undefined;
                  }
                  if (error && (error.code === 'EEXIST' || error.code === 'EISDIR')) {
                    return undefined;
                  }
                  throw error;
                }
              };
            }

            const originalRenameSync = fs.renameSync.bind(fs);
            fs.renameSync = function andClawCompatRenameSync(from, to) {
              try {
                return originalRenameSync(from, to);
              } catch (error) {
                if (error && (error.code === 'ENOSYS' || error.code === 'EXDEV')) {
                  fs.copyFileSync(from, to);
                  try { fs.unlinkSync(from); } catch (_) {}
                  return undefined;
                }
                throw error;
              }
            };

            const originalRename = fs.rename.bind(fs);
            fs.rename = function andClawCompatRename(from, to, callback) {
              return originalRename(from, to, (error) => {
                if (error && (error.code === 'ENOSYS' || error.code === 'EXDEV')) {
                  try {
                    fs.copyFileSync(from, to);
                    try { fs.unlinkSync(from); } catch (_) {}
                    if (callback) callback(null);
                    return;
                  } catch (copyError) {
                    if (callback) callback(copyError);
                    return;
                  }
                }
                if (callback) callback(error || null);
              });
            };

            for (const method of ['chmod', 'chown', 'lchown']) {
              const syncMethod = fs[method + 'Sync'];
              if (typeof syncMethod === 'function') {
                fs[method + 'Sync'] = function andClawCompatFsSync() {
                  try {
                    return syncMethod.apply(fs, arguments);
                  } catch (error) {
                    if (error && (error.code === 'ENOSYS' || error.code === 'EPERM')) {
                      return undefined;
                    }
                    throw error;
                  }
                };
              }
              const asyncMethod = fs[method];
              if (typeof asyncMethod === 'function') {
                fs[method] = function andClawCompatFsAsync() {
                  const args = Array.from(arguments);
                  const callback = typeof args[args.length - 1] === 'function' ? args.pop() : null;
                  try {
                    if (syncMethod) {
                      syncMethod.apply(fs, args);
                    }
                    if (callback) callback(null);
                  } catch (error) {
                    if (error && (error.code === 'ENOSYS' || error.code === 'EPERM')) {
                      if (callback) callback(null);
                      return;
                    }
                    if (callback) callback(error);
                    else throw error;
                  }
                };
              }
            }

            const originalWatch = fs.watch.bind(fs);
            fs.watch = function andClawCompatWatch(target, options, listener) {
              try {
                return originalWatch(target, options, listener);
              } catch (error) {
                if (error && (error.code === 'ENOSYS' || error.code === 'ENOSPC' || error.code === 'ENOENT')) {
                  return {
                    close() {},
                    ref() { return this; },
                    unref() { return this; },
                  };
                }
                throw error;
              }
            };
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        guestCompatHostFile.setReadable(true, false)
    }

    private fun ensureUbuntuGuestCompatibilityFiles() {
        if (!ubuntuRootfsDir.isDirectory) {
            return
        }
        File(ubuntuRootfsDir, "etc/apt/apt.conf.d").mkdirs()
        File(ubuntuRootfsDir, "proc/sys/crypto").mkdirs()
        File(ubuntuRootfsDir, "dev/shm").mkdirs()
        File(ubuntuRootfsDir, "root/home").mkdirs()
        File(ubuntuRootfsDir, "usr/local/bin").mkdirs()
        File(ubuntuRootfsDir, "usr/local/lib/node_modules").mkdirs()
        File(ubuntuRootfsDir, "var/cache/apt/archives/partial").mkdirs()
        File(ubuntuRootfsDir, "var/lib/apt/lists/partial").mkdirs()
        listOf(
            guestNpmRuntimeCacheHostDir,
            guestNpmRuntimeCacacheHostDir,
            guestNpmRuntimeTmpHostDir,
            guestNpmRuntimeContentHostDir,
            guestNpmRuntimeIndexHostDir,
            guestNpmRuntimeLogsHostDir,
            guestNpmRuntimeScratchHostDir,
            guestOpenClawHostDir,
            guestUsrLocalNodeModulesHostDir,
            guestUsrLocalBinHostDir,
        ).forEach { dir ->
            dir.mkdirs()
        }
        writeTextFile(
            File(ubuntuRootfsDir, "etc/apt/apt.conf.d/99andclaw-proot"),
            """
            APT::Sandbox::User "root";
            DPkg::Use-Pty "0";
            #clear APT::Update::Post-Invoke-Success;
            #clear DPkg::Post-Invoke;
            #clear DPkg::Pre-Install-Pkgs;
            """.trimIndent(),
        )
        val resolvConf = File(ubuntuRootfsDir, "etc/resolv.conf")
        if (resolvConf.exists() || resolvConf.isSymbolicLinkCompat()) {
            resolvConf.delete()
        }
        writeTextFile(
            resolvConf,
            buildString {
                append("# Generated by AndClawHost\n")
                guestDnsServers().forEach { server ->
                    append("nameserver ")
                    append(server)
                    append('\n')
                }
            }.trimEnd(),
        )
    }

    private fun guestDnsServers(): List<String> {
        val servers =
            try {
                val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
                val activeNetwork = connectivityManager?.activeNetwork
                connectivityManager
                    ?.getLinkProperties(activeNetwork)
                    ?.dnsServers
                    ?.mapNotNull { it.hostAddress?.takeIf { host -> host.isNotBlank() } }
                    ?.distinct()
                    .orEmpty()
            } catch (err: SecurityException) {
                addLog("DNS probe fell back to public resolvers: ${err.message}")
                emptyList()
            }
        return if (servers.isNotEmpty()) {
            servers
        } else {
            listOf("1.1.1.1", "8.8.8.8")
        }
    }

    private fun writeBridgeScript(
        name: String,
        path: String,
    ) {
        val file = File(guestUsrLocalBinHostDir, name)
        file.writeText(
            """
            |#!/bin/sh
            |set -eu
            |exec /usr/local/bin/node -e "const url = process.argv[1]; fetch(url).then(async (response) => { if (!response.ok) { throw new Error('HTTP ' + response.status); } process.stdout.write(await response.text()); }).catch((error) => { console.error(error.message); process.exit(1); });" "${'$'}{ANDCLAW_DEVICE_BRIDGE_URL:-$bridgeUrl}$path"
            |
            """.trimMargin()
        )
        file.setReadable(true, false)
        file.setExecutable(true, false)
    }

    private fun resolveGuestNodePackageBinEntries(packageName: String): LinkedHashMap<String, String> {
        val packageDir = File(guestUsrLocalNodeModulesHostDir, packageName)
        val packageJsonFile = File(packageDir, "package.json")
        val entries = linkedMapOf<String, String>()
        if (!packageJsonFile.isFile) {
            return entries
        }

        val packageJson = JSONObject(packageJsonFile.readText(StandardCharsets.UTF_8))
        when (val bin = packageJson.opt("bin")) {
            is String -> {
                if (bin.isNotBlank()) {
                    entries[packageName] = bin
                }
            }
            is JSONObject -> {
                bin.keys().forEach { key ->
                    val relativePath = bin.optString(key)
                    if (relativePath.isNotBlank()) {
                        entries[key] = relativePath
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            listOf("bin/$packageName.js", "bin/$packageName", "cli.js", "index.js").forEach { candidate ->
                if (File(packageDir, candidate).isFile) {
                    entries[packageName] = candidate
                    return@forEach
                }
            }
        }
        return entries
    }

    private fun resolveGuestNodePackageEntryPath(
        packageName: String,
        executableName: String = packageName,
    ): String? {
        val relativePath = resolveGuestNodePackageBinEntries(packageName)[executableName] ?: return null
        val normalizedPath = relativePath.trimStart('/')
        val targetFile = resolveGuestNodePackageEntryHostFile(packageName, executableName) ?: return null
        if (!targetFile.isFile) {
            return null
        }
        return "$guestNodeRuntimePath/lib/node_modules/$packageName/$normalizedPath"
    }

    private fun resolveGuestNodePackageEntryHostFile(
        packageName: String,
        executableName: String = packageName,
    ): File? {
        val relativePath = resolveGuestNodePackageBinEntries(packageName)[executableName] ?: return null
        val normalizedPath = relativePath.trimStart('/')
        val targetFile = File(guestUsrLocalNodeModulesHostDir, "$packageName/$normalizedPath")
        return targetFile.takeIf { it.isFile }
    }

    private fun isGuestNodeEntryWrappedByShell(entryFile: File): Boolean {
        if (!entryFile.isFile) {
            return false
        }
        return try {
            BufferedReader(InputStreamReader(FileInputStream(entryFile), StandardCharsets.UTF_8)).use { reader ->
                val firstLine = reader.readLine() ?: return false
                val secondLine = reader.readLine() ?: return false
                firstLine == "#!/bin/sh" && secondLine.startsWith("exec /usr/local/bin/node ")
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun ensureGuestNodePackageWrappers(packageName: String) {
        val packageDir = File(guestUsrLocalNodeModulesHostDir, packageName)
        val packageJsonFile = File(packageDir, "package.json")
        if (!packageJsonFile.isFile) {
            return
        }

        val entries = resolveGuestNodePackageBinEntries(packageName)

        entries.forEach { (name, relativePath) ->
            val normalizedPath = relativePath.trimStart('/')
            val targetFile = File(packageDir, normalizedPath)
            if (!targetFile.isFile) {
                addLog("Skipping wrapper for $name because $normalizedPath is missing.")
                return@forEach
            }
            val wrapperFile = File(guestUsrLocalBinHostDir, name)
            if (wrapperFile.exists()) {
                wrapperFile.delete()
            }
            val guestPackagePath = "$guestNodeRuntimePath/lib/node_modules/$packageName/$normalizedPath"
            writeTextFile(
                wrapperFile,
                """
                |#!/bin/sh
                |exec $guestNodeBinaryPath "$guestPackagePath" "${'$'}@"
                |
                """.trimMargin()
            )
            wrapperFile.setReadable(true, false)
            wrapperFile.setExecutable(true, false)
        }
    }

    private fun isRuntimePrepared(): Boolean {
        return isHostRuntimePrepared() && isUbuntuGuestPrepared()
    }

    private fun isHostRuntimePrepared(): Boolean {
        return hasLayoutMarker(hostRuntimeLayoutMarker, HOST_RUNTIME_LAYOUT_VERSION) &&
            prootWrapper.isFile &&
            prootWrapper.canExecute() &&
            runtimeRootfsDir.isDirectory &&
            File(termuxPrefixHostDir, "bin/proot").isFile &&
            File(termuxPrefixHostDir, "lib").isDirectory
    }

    private fun isUbuntuGuestPrepared(): Boolean {
        return hasLayoutMarker(ubuntuRuntimeLayoutMarker, UBUNTU_RUNTIME_LAYOUT_VERSION) &&
            ubuntuRootfsDir.isDirectory &&
            File(ubuntuRootfsDir, "bin/bash").isFile &&
            File(ubuntuRootfsDir, "etc/os-release").isFile
    }

    private fun isNodeRuntimePrepared(): Boolean {
        return hasLayoutMarker(nodeRuntimeLayoutMarker, NODE_RUNTIME_LAYOUT_VERSION) &&
            File(nodeRuntimeBinHostDir, "node").isFile &&
            File(nodeRuntimeBinHostDir, "npm").isFile
    }

    private suspend fun probeOpenClawInstalled(): Boolean {
        if (!isRuntimePrepared()) {
            return false
        }
        ensureGuestNodePackageWrappers("openclaw")
        val entryHostFile = resolveGuestNodePackageEntryHostFile("openclaw")
        if (entryHostFile == null || isGuestNodeEntryWrappedByShell(entryHostFile)) {
            if (entryHostFile == null) {
                addLog("OpenClaw probe failed: entry file is missing.")
            } else {
                addLog("OpenClaw probe detected a wrapped entry script; reinstalling package.")
            }
            return false
        }
        val result = try {
            runShellCommandForResult(
                "test -x ${shellQuote(guestOpenClawBinPath)} && test -f ${shellQuote("$guestOpenClawPackagePath/package.json")}"
            )
        } catch (err: IOException) {
            addLog("OpenClaw probe failed: ${err.message}")
            return false
        }
        if (result.exitCode == 124) {
            addLog("OpenClaw probe timed out inside the bundled runtime.")
        }
        return result.exitCode == 0
    }

    private suspend fun refreshState(errorMessage: String? = null) {
        val hostReady = isHostRuntimePrepared()
        val guestReady = isUbuntuGuestPrepared()
        val installed = hostReady && guestReady
        val gatewayRunning = currentGatewayProcess.get()?.isAlive == true
        val openClawInstalled =
            if (installed) {
                try {
                    probeOpenClawInstalled()
                } catch (t: Throwable) {
                    addLog("OpenClaw state refresh failed: ${t.message ?: t.javaClass.simpleName}")
                    false
                }
            } else {
                false
            }
        val runtimeSummary = when {
            gatewayRunning -> "OpenClaw is running on 127.0.0.1:$GATEWAY_PORT."
            !hostReady -> "Preparing the bundled host runtime."
            !guestReady -> "Preparing the Ubuntu guest runtime."
            !openClawInstalled -> "Runtime is ready. Installing OpenClaw is next."
            else -> "OpenClaw is installed. Starting the gateway."
        }
        _state.update {
            it.copy(
                runtimeInstalled = installed,
                openClawInstalled = openClawInstalled,
                bridgeRunning = bridgeServer != null,
                gatewayRunning = gatewayRunning,
                runtimeSummary = runtimeSummary,
                lastError = errorMessage ?: it.lastError,
            )
        }
    }

    private fun buildGatewayCommand(): String {
        return buildGuestOpenClawCommand(
            listOf(
                "gateway",
                "--allow-unconfigured",
                "--bind",
                "loopback",
                "--port",
                GATEWAY_PORT.toString(),
                "--auth",
                "token",
            )
        )
    }

    private fun buildShellProcess(command: String): Process {
        if (!isRuntimePrepared()) {
            throw IOException("Runtime is not ready.")
        }
        val launchCommand = buildProotLaunchCommand(command)
        logLaunchCommand(launchCommand, command)
        if (!launchCommand.workingDir.exists()) {
            launchCommand.workingDir.mkdirs()
        }
        val processBuilder = ProcessBuilder(launchCommand.argv)
        processBuilder.directory(launchCommand.workingDir)
        processBuilder.redirectErrorStream(true)
        val env = processBuilder.environment()
        env.putAll(launchCommand.environment)
        return processBuilder.start()
    }

    private suspend fun logInstallPreflight() {
        val guestNodeVersionCommand = "${shellQuote(guestNodeBinaryPath)} --version"
        val guestNpmVersionCommand = buildGuestNpmCommand(listOf("--version"))
        val guestNpmPrefixCommand = buildGuestNpmCommand(listOf("config", "get", "prefix"))
        addLog(
            buildString {
                append("[diag] hostRuntimeRoot=")
                append(runtimeRootDir.absolutePath)
                append(", prootWrapper=")
                append(prootWrapper.exists())
                append("/")
                append(prootWrapper.canExecute())
                append(", prootLib=")
                append(bundledProotLib.exists())
                append(", hostRootfs=")
                append(runtimeRootfsDir.exists())
                append(", termuxProot=")
                append(File(termuxPrefixHostDir, "bin/proot").exists())
                append(", ubuntuRootfs=")
                append(ubuntuRootfsDir.exists())
                append(", ubuntuBash=")
                append(File(ubuntuRootfsDir, "bin/bash").exists())
                append(", ubuntuOsRelease=")
                append(File(ubuntuRootfsDir, "etc/os-release").exists())
                append(", nodeRuntime=")
                append(nodeRuntimeHostDir.exists())
                append(", nodeBinary=")
                append(File(nodeRuntimeBinHostDir, "node").exists())
                append(", npmBinary=")
                append(File(nodeRuntimeBinHostDir, "npm").exists())
                append(", guestHome=")
                append(guestHomeHostDir.exists())
                append(", guestCompat=")
                append(guestCompatHostFile.exists())
            }
        )

        val checks =
            listOf(
                "pwd",
                "cat /etc/os-release 2>&1 | head -n 3 || true",
                "command -v bash && test -x ${shellQuote(guestNodeBinaryPath)} && test -f ${shellQuote(guestNpmCliPath)} || true",
                "$guestNodeVersionCommand 2>&1 || true",
                "$guestNpmVersionCommand 2>&1 || true",
                "$guestNpmPrefixCommand 2>&1 || true",
                "ls -ld / /root /usr /usr/bin /bin 2>&1 || true",
                "ls -l /bin/bash /usr/bin/env 2>&1 || true",
                "ls -ld \"${'$'}HOME\" \"$guestHomeMountPath\" \"${'$'}HOME/.openclaw\" \"/usr/local/bin\" \"$guestNodeBinPath\" 2>&1 || true",
                "echo HOME=${'$'}HOME",
                "echo PATH=${'$'}PATH",
                "echo LD_LIBRARY_PATH=${'$'}LD_LIBRARY_PATH",
            )

        checks.forEachIndexed { index, check ->
            val result = runShellCommandForResult(check)
            val output = result.output.ifBlank { "<empty>" }
            addLog("[diag:$index] exit=${result.exitCode} cmd=$check")
            output.lineSequence().forEach { line ->
                addLog("[diag:$index] $line")
            }
        }
    }

    private fun logLaunchCommand(
        launchCommand: LaunchCommand,
        command: String,
    ) {
        addLog("[proc] command=$command")
        addLog("[proc] workdir=${launchCommand.workingDir.absolutePath}")
        addLog("[proc] argv=${launchCommand.argv.joinToString(" ")}")
        launchCommand.environment.toSortedMap().forEach { (key, value) ->
            addLog("[proc-env] $key=$value")
        }
    }

    private fun buildProotLaunchCommand(commandText: String): LaunchCommand {
        val hostProotBinary =
            when {
                prootWrapper.isFile && prootWrapper.canExecute() -> prootWrapper
                packagedHostProot != null -> packagedHostProot!!
                else -> throw IOException("No executable proot launcher is available for the bundled runtime.")
            }
        gatewayStateHostDir.mkdirs()
        prootTmpHostDir.mkdirs()

        val guestStatePath = "$guestOpenClawPath/state"
        val guestPath =
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        val guestShell = if (File(ubuntuRootfsDir, "bin/bash").isFile) "/bin/bash" else "/bin/sh"
        setupFakeGuestSysData()
        val hostRootfsPath = toProotHostPath(ubuntuRootfsDir)
        val hostGatewayStatePath = toProotHostPath(gatewayStateHostDir)
        val hostProotTmpPath = toProotHostPath(prootTmpHostDir)

        val argv = mutableListOf<String>()
        argv += hostProotBinary.absolutePath
        argv += "-L"
        argv += "--kernel-release=$PROOT_FAKE_KERNEL_RELEASE"
        argv += "--kill-on-exit"
        argv += "--link2symlink"
        argv += "-0"
        argv += "-r"
        argv += hostRootfsPath
        argv += "-b"
        argv += "/dev"
        argv += "-b"
        argv += "/dev/urandom:/dev/random"
        argv += "-b"
        argv += "/proc"
        argv += "-b"
        argv += "/proc/self/fd:/dev/fd"
        argv += "-b"
        argv += "/proc/self/fd/0:/dev/stdin"
        argv += "-b"
        argv += "/proc/self/fd/1:/dev/stdout"
        argv += "-b"
        argv += "/proc/self/fd/2:/dev/stderr"
        argv += "-b"
        argv += "/sys"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.loadavg"))}:/proc/loadavg"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.stat"))}:/proc/stat"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.uptime"))}:/proc/uptime"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.version"))}:/proc/version"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.vmstat"))}:/proc/vmstat"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.sysctl_entry_cap_last_cap"))}:/proc/sys/kernel/cap_last_cap"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.sysctl_inotify_max_user_watches"))}:/proc/sys/fs/inotify/max_user_watches"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "proc/.fips_enabled"))}:/proc/sys/crypto/fips_enabled"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "sys/.empty"))}:/sys/fs/selinux"
        argv += "-b"
        argv += "${toProotHostPath(File(ubuntuRootfsDir, "tmp"))}:/dev/shm"
        argv += "-b"
        argv += "$hostGatewayStatePath:$guestHomeMountPath"
        argv += "-w"
        argv += guestWorkingPath
        argv += "/usr/bin/env"
        argv += "-i"
        argv += "HOME=$guestHomePath"
        argv += "LANG=C.UTF-8"
        argv += "PATH=$guestPath"
        argv += "TERM=xterm-256color"
        argv += "TMPDIR=/tmp"
        argv += "OPENCLAW_HOME=$guestHomePath"
        argv += "OPENCLAW_STATE_DIR=$guestStatePath"
        argv += "OPENCLAW_CONFIG_PATH=$guestOpenClawPath/openclaw.json"
        argv += "ANDCLAW_DEVICE_BRIDGE_URL=$bridgeUrl"
        argv += "OPENCLAW_SKIP_CHANNELS=1"
        argv += "OPENCLAW_SKIP_GMAIL_WATCHER=1"
        argv += "OPENCLAW_SKIP_CANVAS_HOST=1"
        argv += "OPENCLAW_SKIP_BROWSER_CONTROL_SERVER=1"
        argv += "OPENCLAW_DISABLE_BONJOUR=1"
        // Android host supervision owns restarts; avoid detached self-respawn inside the guest.
        argv += "OPENCLAW_NO_RESPAWN=1"
        argv += "OPENCLAW_GATEWAY_TOKEN=$gatewayToken"
        argv += "DEBIAN_FRONTEND=noninteractive"
        argv += guestShell
        argv += "-lc"
        argv += buildGuestShellCommand(commandText = commandText)

        return LaunchCommand(
            argv = argv,
            environment = buildHostProotEnvironment(hostProotBinary, hostProotTmpPath),
            workingDir = guestHomeHostDir,
        )
    }

    private fun buildHostProotEnvironment(
        hostProotBinary: File,
        hostProotTmpPath: String,
    ): Map<String, String> {
        val pathSegments =
            listOfNotNull(
                hostProotBinary.parentFile?.let(::toProotHostPath),
                "/system/bin",
                "/system/xbin",
            )
        val environment = linkedMapOf(
            "PATH" to pathSegments.joinToString(":"),
            "LD_LIBRARY_PATH" to toProotHostPath(File(termuxPrefixHostDir, "lib")),
            "PROOT_TMP_DIR" to hostProotTmpPath,
            "PROOT_F2FS_WORKAROUND" to "1",
            "TERM" to "xterm-256color",
        )
        if (termuxProotLoaderHostFile.isFile) {
            environment["PROOT_LOADER"] = toProotHostPath(termuxProotLoaderHostFile)
        }
        if (termuxProotLoader32HostFile.isFile) {
            environment["PROOT_LOADER_32"] = toProotHostPath(termuxProotLoader32HostFile)
        }
        return environment
    }

    private fun buildGuestNpmCommand(arguments: List<String>): String {
        return buildNodeLauncherCommand(
            entryPath = guestNpmCliPath,
            arguments = arguments,
            moduleMode = false,
        )
    }

    private fun buildGuestOpenClawCommand(arguments: List<String>): String {
        val entryPath =
            resolveGuestNodePackageEntryPath("openclaw")
                ?: guestOpenClawBinPath
        return buildNodeLauncherCommand(
            entryPath = entryPath,
            arguments = arguments,
            moduleMode = true,
        )
    }

    private fun buildNodeLauncherCommand(
        entryPath: String,
        arguments: List<String>,
        moduleMode: Boolean,
    ): String {
        val launcherScript = if (moduleMode) buildModuleNodeLauncherScript() else buildCommonJsNodeLauncherScript()
        return buildList {
            add("ANDCLAW_ENTRY=${shellQuote(entryPath)}")
            add("ANDCLAW_FALLBACK_CWD=${shellQuote(guestHomePath)}")
            add(shellQuote(guestNodeBinaryPath))
            if (moduleMode) {
                add("--input-type=module")
            }
            add("-e")
            add(shellQuote(launcherScript))
            arguments.forEach { argument ->
                add(shellQuote(argument))
            }
        }.joinToString(" ")
    }

    private fun buildCommonJsNodeLauncherScript(): String {
        return """
            const entry = process.env.ANDCLAW_ENTRY;
            const compatPath = ${shellQuote(guestCompatPath)};
            try {
              require(compatPath);
            } catch (error) {
              const fallback = process.env.PWD || process.env.ANDCLAW_FALLBACK_CWD || process.env.HOME || '/root';
              const originalCwd = process.cwd.bind(process);
              process.cwd = () => {
                try {
                  return originalCwd();
                } catch (_) {
                  return process.env.PWD || fallback;
                }
              };
              process.env.PWD = process.env.PWD || fallback;
            }
            const userArgs = process.argv.slice(1);
            const normalizedArgs = userArgs[0] === entry ? userArgs.slice(1) : userArgs;
            process.argv = [process.argv[0], entry, ...normalizedArgs];
            require(entry);
        """.trimIndent()
    }

    private fun buildModuleNodeLauncherScript(): String {
        return """
            import { createRequire } from 'node:module';
            import { pathToFileURL } from 'node:url';

            const entry = process.env.ANDCLAW_ENTRY;
            const compatPath = ${shellQuote(guestCompatPath)};
            try {
              const compatRequire = createRequire(import.meta.url);
              compatRequire(compatPath);
            } catch (error) {
              const fallback = process.env.PWD || process.env.ANDCLAW_FALLBACK_CWD || process.env.HOME || '/root';
              const originalCwd = process.cwd.bind(process);
              process.cwd = () => {
                try {
                  return originalCwd();
                } catch (_) {
                  return process.env.PWD || fallback;
                }
              };
              process.env.PWD = process.env.PWD || fallback;
            }
            const userArgs = process.argv.slice(1);
            const normalizedArgs = userArgs[0] === entry ? userArgs.slice(1) : userArgs;
            process.argv = [process.argv[0], entry, ...normalizedArgs];
            await import(pathToFileURL(entry).href);
        """.trimIndent()
    }

    private fun buildGuestShellCommand(
        commandText: String,
    ): String {
        return """
            cd "${'$'}HOME" 2>/dev/null || cd /tmp 2>/dev/null || true
            export PWD="${'$'}(pwd 2>/dev/null || printf '%s' "${'$'}HOME")"
            $commandText
        """.trimIndent()
    }

    private fun setupFakeGuestSysData() {
        listOf(
            File(ubuntuRootfsDir, "proc"),
            File(ubuntuRootfsDir, "sys"),
            File(ubuntuRootfsDir, "sys/.empty"),
            File(ubuntuRootfsDir, "proc/sys/kernel"),
            File(ubuntuRootfsDir, "proc/sys/crypto"),
            File(ubuntuRootfsDir, "proc/sys/fs/inotify"),
            File(ubuntuRootfsDir, guestHomePath.removePrefix("/")),
            File(ubuntuRootfsDir, guestHomeMountPath.removePrefix("/")),
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        ensureFakeGuestFile("proc/.loadavg", "0.12 0.07 0.02 2/165 765\n")
        ensureFakeGuestFile("proc/.stat", "cpu  1957 0 2877 93280 262 342 254 87 0 0\nctxt 140223\nbtime 1680020856\nprocesses 772\nprocs_running 2\nprocs_blocked 0\n")
        ensureFakeGuestFile("proc/.uptime", "124.08 932.80\n")
        ensureFakeGuestFile("proc/.version", "Linux version $PROOT_FAKE_KERNEL_RELEASE (andclaw@android) #1 SMP PREEMPT_DYNAMIC Fri Mar 12 00:00:00 UTC 2026\n")
        ensureFakeGuestFile("proc/.vmstat", "nr_free_pages 1743136\nnr_dirty 0\npgpgin 890508\npgpgout 0\npgfault 176973\n")
        ensureFakeGuestFile("proc/.sysctl_entry_cap_last_cap", "40\n")
        ensureFakeGuestFile("proc/.sysctl_inotify_max_user_watches", "1048576\n")
        ensureFakeGuestFile("proc/.fips_enabled", "0\n")
    }

    private fun ensureFakeGuestFile(
        relativePath: String,
        content: String,
    ) {
        val file = File(ubuntuRootfsDir, relativePath)
        if (!file.exists()) {
            writeTextFile(file, content)
        }
        file.setReadable(true, false)
    }

    private fun toProotHostPath(file: File): String {
        val path = file.absolutePath
        val userPrefix = "/data/user/0/${appContext.packageName}"
        return if (path.startsWith(userPrefix)) {
            legacyDataPrefix + path.removePrefix(userPrefix)
        } else {
            path
        }
    }

    private fun buildOpenClawInstallCommand(): String {
        val guestNodeVersionCommand = "${shellQuote(guestNodeBinaryPath)} --version"
        val guestNpmVersionCommand = buildGuestNpmCommand(listOf("--version"))
        val guestNpmPrefixCommand = buildGuestNpmCommand(listOf("config", "get", "prefix"))
        val guestNpmInstallCommand =
            buildGuestNpmCommand(
                listOf(
                    "install",
                    "-g",
                    "--legacy-peer-deps",
                    "--no-update-notifier",
                    "--no-audit",
                    "--no-fund",
                    "openclaw@2026.3.8",
                )
            )
        return """
            set -eu
            export PWD="${'$'}(pwd 2>/dev/null || printf '%s' "${'$'}HOME")"
            export NPM_CONFIG_PREFIX="/usr/local"
            export npm_config_prefix="${'$'}NPM_CONFIG_PREFIX"
            export NPM_CONFIG_CACHE="$guestNpmCachePath"
            export npm_config_cache="${'$'}NPM_CONFIG_CACHE"
            export npm_config_tmp="$guestNpmTmpPath"
            export npm_config_update_notifier=false
            export npm_config_audit=false
            export npm_config_fund=false
            export npm_config_yes=true
            export npm_config_loglevel=info
            export npm_config_legacy_peer_deps=true
            echo "[npm-diag] pwd=${'$'}(pwd 2>&1 || true)"
            echo "[npm-diag] HOME=${'$'}HOME"
            echo "[npm-diag] PATH=${'$'}PATH"
            echo "[npm-diag] prefix=${'$'}NPM_CONFIG_PREFIX"
            mkdir -p /usr/local/bin /usr/local/lib/node_modules
            if ! command -v git >/dev/null 2>&1; then
              echo "[npm-diag] git missing in guest, installing via apt-get"
              apt-get update
              apt-get install -y --no-install-recommends git
            fi
            git --version
            test -x ${shellQuote(guestNodeBinaryPath)}
            test -f ${shellQuote(guestNpmCliPath)}
            $guestNodeVersionCommand
            $guestNpmVersionCommand
            $guestNpmPrefixCommand
            $guestNpmInstallCommand
            test -f ${shellQuote("$guestOpenClawPackagePath/package.json")}
            ls -ld ${shellQuote(guestOpenClawPackagePath)} 2>&1 || true
        """.trimIndent()
    }

    private suspend fun runOneShotShellCommand(
        command: String,
        label: String,
    ): Int {
        val process = buildShellProcess(command)
        streamProcessOutput(process, label)
        return process.waitFor()
    }

    private suspend fun runShellCommandForResult(command: String): CommandResult {
        val process = buildShellProcess(command)
        val finished = process.waitFor(12, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
            return CommandResult(exitCode = 124, output = "timed out")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.exitValue()
        return CommandResult(exitCode = exitCode, output = output.trim())
    }

    private suspend fun streamProcessOutput(
        process: Process,
        label: String,
    ) {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                maybeUpdateBusyProgressFromProcessLine(label, line)
                addLog("[$label] $line")
            }
        }
    }

    private fun maybeUpdateBusyProgressFromProcessLine(
        label: String,
        line: String,
    ) {
        if (label != "install") {
            return
        }

        when {
            "[npm-diag] git missing in guest" in line -> {
                updateBusyProgress(
                    fraction = 0.58f,
                    label = "缺少 git，正在补齐安装依赖",
                )
            }
            line.startsWith("[npm-diag] pwd=") -> {
                updateBusyProgress(
                    fraction = 0.56f,
                    label = "正在检查安装环境",
                )
            }
            line.startsWith("[npm-diag] prefix=") -> {
                updateBusyProgress(
                    fraction = 0.6f,
                    label = "npm 环境检查完成，准备下载 OpenClaw",
                )
            }
            "fetch GET" in line || "http fetch" in line -> {
                updateBusyProgress(
                    fraction = 0.68f,
                    label = "正在下载 OpenClaw npm 包",
                )
            }
            line.startsWith("added ") || line.contains("packages are looking for funding") -> {
                updateBusyProgress(
                    fraction = 0.78f,
                    label = "OpenClaw 包已写入，正在整理运行入口",
                )
            }
            "package.json" in line && guestOpenClawPackagePath in line -> {
                updateBusyProgress(
                    fraction = 0.92f,
                    label = "已写入 OpenClaw 文件，正在校验安装结果",
                )
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "$timestamp  $message"
        Log.i(LOG_TAG, message)
        _state.update { current ->
            current.copy(logs = (current.logs + line).takeLast(MAX_LOG_LINES))
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    private data class LaunchCommand(
        val argv: List<String>,
        val environment: Map<String, String>,
        val workingDir: File,
    )
}

private fun File.isSymbolicLinkCompat(): Boolean {
    return try {
        val canonical = canonicalFile
        val absoluteParent = absoluteFile.parentFile ?: return false
        canonical != File(absoluteParent, name).canonicalFile
    } catch (_: IOException) {
        false
    }
}

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

private class DeviceBridgeServer(
    private val port: Int,
    private val responseProvider: (String) -> Response,
    private val onLog: (String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (serverSocket != null) {
            return
        }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            onLog("Local device bridge is listening on 127.0.0.1:$port.")
            while (isActive) {
                try {
                    val client = socket.accept()
                    launch {
                        handleClient(client)
                    }
                } catch (err: IOException) {
                    if (isActive) {
                        onLog("Device bridge stopped accepting connections: ${err.message}")
                    }
                    break
                }
            }
        }
    }

    suspend fun stop() {
        acceptJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        acceptJob = null
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) {
                // Ignore headers for now.
            }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val response = responseProvider(path)
            val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
            val writer = socket.getOutputStream()
            val statusText = if (response.statusCode == 200) "OK" else "Not Found"
            writer.write("HTTP/1.1 ${response.statusCode} $statusText\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Content-Type: ${response.contentType}\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("Connection: close\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write("\r\n".toByteArray(StandardCharsets.UTF_8))
            writer.write(bodyBytes)
            writer.flush()
        }
    }

    data class Response(
        val statusCode: Int,
        val contentType: String = "application/json; charset=utf-8",
        val body: String,
    )
}

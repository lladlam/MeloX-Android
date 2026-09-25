package com.lladlam.melox.platform.xiaomi

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.net.IConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.INetworkManagementService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Optional Shizuku-backed XMSF compatibility path for HyperOS Super Island.
 *
 * Some HyperOS builds accept third-party Focus notifications directly; others appear to race
 * Xiaomi's XMSF handling. When the user explicitly grants MeloX Shizuku access, this helper
 * applies the same short OEM firewall pulse used by Halcyon: block XMSF, publish the Focus
 * notification, wait 100 ms, then restore the default rule. No Shizuku means no firewall
 * changes at all -- the notification is published directly.
 */
internal object ShizukuXmsfNetworkHelper {
    private const val TAG = "MeloXShizukuIsland"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val OEM_DENY_CHAIN = 9
    private const val RULE_DEFAULT = 0
    private const val RULE_ALLOW = 1
    private const val RULE_DENY = 2
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 500L
    private const val XMSF_PULSE_MS = 100L

    private data class ServiceBackend(
        val serviceName: String,
        val stubClassName: String,
        val label: String,
    )

    private val backends = listOf(
        ServiceBackend("connectivity", "android.net.IConnectivityManager\$Stub", "ConnectivityManager"),
        ServiceBackend("network_management", "android.os.INetworkManagementService\$Stub", "NetworkManagementService"),
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permissionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val networkMutex = Mutex()
    private val permissionMutex = Mutex()
    private val wrappedServices = ConcurrentHashMap<String, Any>()

    @Volatile
    private var permissionRequestAttempted = false
    @Volatile
    private var dispatchGeneration = 0L
    @Volatile
    private var xmsfNetworkingBlocked = false

    private var permissionProbeJob: Job? = null
    private var dispatchJob: Job? = null
    private var nextRequestCode = 1200

    /**
     * Playback and cold-start entry. Waits for a live binder and binds keepalive, but never
     * calls requestPermission(). Authorization stays on the settings button.
     */
    fun prepare(context: Context) {
        val appContext = context.applicationContext
        installHiddenApiExemptions()
        synchronized(this) {
            if (permissionProbeJob?.isActive == true) return
            permissionProbeJob = permissionScope.launch {
                val granted = isPermissionGrantedWhenReady()
                Log.i(
                    TAG,
                    "permission check: binderAlive=${runCatching { Shizuku.pingBinder() }.getOrDefault(false)} granted=$granted",
                )
                if (granted) {
                    ShizukuKeepAliveService.ensureBound(appContext)
                    restoreXmsfNetworkingAsync(appContext)
                }
            }
        }
    }

    /**
     * The only path allowed to show the Shizuku authorization dialog.
     * Call this from the settings screen after an explicit user action.
     */
    fun requestPermission(context: Context) {
        val appContext = context.applicationContext
        installHiddenApiExemptions()
        permissionScope.launch {
            permissionMutex.withLock {
                if (isPermissionGranted()) {
                    ShizukuKeepAliveService.ensureBound(appContext)
                    return@withLock
                }
                if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return@withLock
                permissionRequestAttempted = true
                if (ensureShizukuPermission()) {
                    ShizukuKeepAliveService.ensureBound(appContext)
                    restoreXmsfNetworkingAsync(appContext)
                }
            }
        }
    }

    /** Publish through the Super Island foreground service. Pulse XMSF only when already granted. */
    fun dispatchFocusNotification(
        context: Context,
        notification: Notification,
    ) {
        val appContext = context.applicationContext
        installHiddenApiExemptions()
        ioScope.launch {
            if (!isPermissionGrantedWhenReady()) {
                Log.i(TAG, "sending Focus notification directly")
                publishDirect(appContext, notification)
                return@launch
            }
            ShizukuKeepAliveService.ensureBound(appContext)

            val generation = synchronized(this@ShizukuXmsfNetworkHelper) {
                dispatchGeneration += 1L
                dispatchJob?.cancel()
                dispatchGeneration
            }
            dispatchJob = ioScope.launch {
                networkMutex.withLock {
                    if (generation != dispatchGeneration) return@withLock

                    val blocked = withContext(NonCancellable) {
                        setXmsfNetworkingEnabled(appContext, enabled = false)
                    }
                    if (!blocked) {
                        Log.i(TAG, "sending Focus notification directly")
                        publishDirect(appContext, notification)
                        return@withLock
                    }
                    xmsfNetworkingBlocked = true

                    try {
                        if (generation == dispatchGeneration) {
                            publishDirect(appContext, notification)
                        }
                        delay(XMSF_PULSE_MS)
                    } catch (_: CancellationException) {
                        // A newer lyric owns the next pulse. finally still restores XMSF first.
                    } finally {
                        withContext(NonCancellable) {
                            restoreXmsfNetworking()
                        }
                    }
                }
            }
        }
    }

    fun clearFocusNotification(context: Context) {
        synchronized(this) {
            dispatchGeneration += 1L
            dispatchJob?.cancel()
            dispatchJob = null
        }
        XiaomiSuperIslandLyricService.stop(context.applicationContext)
        restoreXmsfNetworkingAsync(context.applicationContext)
    }

    private fun publishDirect(context: Context, notification: Notification) {
        XiaomiSuperIslandLyricService.publish(context, notification)
    }

    fun installHiddenApiExemptions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
            .onSuccess { Log.i(TAG, "Hidden API exemptions installed for process") }
            .onFailure { Log.w(TAG, "Unable to install hidden API exemptions", it) }
    }

    private fun hasPermission(): Boolean = isPermissionGranted()

    private fun isPermissionGranted(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    private suspend fun isPermissionGrantedWhenReady(): Boolean {
        repeat(20) {
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return isPermissionGranted()
            delay(250L)
        }
        return isPermissionGranted()
    }

    private suspend fun ensureShizukuPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return@withContext false
        if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext true
        }

        suspendCancellableCoroutine { continuation ->
            val requestCode = synchronized(this@ShizukuXmsfNetworkHelper) {
                nextRequestCode = (nextRequestCode + 1).coerceAtLeast(1201)
                nextRequestCode
            }
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { returnedCode, result ->
                if (returnedCode != requestCode || !continuation.isActive) return@OnRequestPermissionResultListener
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resume(result == PackageManager.PERMISSION_GRANTED)
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            runCatching { Shizuku.requestPermission(requestCode) }
                .onFailure {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    if (continuation.isActive) continuation.resume(false)
                }
        }
    }

    private fun restoreXmsfNetworkingAsync(context: Context) {
        ioScope.launch {
            networkMutex.withLock {
                if (xmsfNetworkingBlocked || hasPermission()) {
                    withContext(NonCancellable) { restoreXmsfNetworking() }
                }
            }
        }
    }

    private suspend fun restoreXmsfNetworking() {
        if (!xmsfNetworkingBlocked && !hasPermission()) return
        val restored = setXmsfNetworkingEnabledInternal(enabled = true)
        if (restored) {
            xmsfNetworkingBlocked = false
        } else if (xmsfNetworkingBlocked) {
            Log.w(TAG, "XMSF restore was requested but no compatible backend was available")
        }
    }

    private suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        val appContext = context.applicationContext
        val uid = runCatching { appContext.packageManager.getPackageUid(XMSF_PACKAGE, 0) }
            .getOrElse {
                Log.i(TAG, "XMSF is not installed")
                return false
            }
        if (!hasPermission()) return false
        return applyFirewallRuleWithRetry(uid, enabled)
    }

    private suspend fun setXmsfNetworkingEnabledInternal(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        // System package UID is stable for the current boot. Resolve it through a small context-free
        // shell-compatible fallback only when a previous pulse actually marked XMSF blocked.
        val uid = currentXmsfUid ?: return false
        return applyFirewallRuleWithRetry(uid, enabled)
    }

    @Volatile
    private var currentXmsfUid: Int? = null

    private suspend fun applyFirewallRuleWithRetry(uid: Int, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            currentXmsfUid = uid
            var lastFailure: Throwable? = null
            repeat(MAX_RETRIES) { attempt ->
                try {
                    applyFirewallRule(uid, enabled)
                    Log.d(TAG, "XMSF networking ${if (enabled) "restored" else "blocked"} for uid=$uid")
                    return@withContext true
                } catch (error: Throwable) {
                    lastFailure = error
                    wrappedServices.clear()
                    if (attempt + 1 < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }
            Log.w(TAG, "No compatible XMSF firewall backend", lastFailure)
            false
        }

    private fun hookedConnectivityManager(): IConnectivityManager {
        val originalBinder = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
            ?: error("connectivity binder is null")
        val original = IConnectivityManager.Stub.asInterface(originalBinder)
            ?: error("ConnectivityManager unavailable")
        return IConnectivityManager.Stub.asInterface(ShizukuBinderWrapper(original.asBinder()))
            ?: error("ConnectivityManager wrapper unavailable")
    }

    private fun hookedNetworkManagementService(): INetworkManagementService {
        val originalBinder = SystemServiceHelper.getSystemService("network_management")
            ?: error("network_management binder is null")
        val original = INetworkManagementService.Stub.asInterface(originalBinder)
            ?: error("NetworkManagementService unavailable")
        return INetworkManagementService.Stub.asInterface(ShizukuBinderWrapper(original.asBinder()))
            ?: error("NetworkManagementService wrapper unavailable")
    }

    private fun applyFirewallRule(uid: Int, enabled: Boolean) {
        runCatching {
            val service = hookedConnectivityManager()
            val rule = if (enabled) RULE_DEFAULT else RULE_DENY
            if (!enabled) service.setFirewallChainEnabled(OEM_DENY_CHAIN, true)
            service.setUidFirewallRule(OEM_DENY_CHAIN, uid, rule)
            return
        }.onFailure { typedConnectivityFailure ->
            Log.w(TAG, "Typed connectivity firewall path failed; trying legacy binder", typedConnectivityFailure)
            runCatching {
                val service = hookedNetworkManagementService()
                val rule = if (enabled) RULE_DEFAULT else RULE_DENY
                if (!enabled) service.setFirewallChainEnabled(OEM_DENY_CHAIN, true)
                service.setUidFirewallRule(OEM_DENY_CHAIN, uid, rule)
                return
            }.onFailure { typedLegacyFailure ->
                Log.w(TAG, "Typed network-management path failed; trying reflection", typedLegacyFailure)
            }
        }

        val failures = mutableListOf<String>()
        for (backend in backends) {
            try {
                applyFirewallRule(getWrappedService(backend), uid, enabled)
                return
            } catch (error: Throwable) {
                failures += "${backend.label}: ${error.message ?: error.javaClass.name}"
            }
        }
        error("No compatible firewall backend. ${failures.joinToString(" | ")}")
    }

    private fun applyFirewallRule(service: Any, uid: Int, enabled: Boolean) {
        val failures = mutableListOf<String>()
        if (!enabled) {
            runCatching { call(service, listOf("setFirewallChainEnabled"), OEM_DENY_CHAIN, true) }
                .onFailure { failures += "chain: ${it.message}" }
        }

        val modernRule = if (enabled) RULE_DEFAULT else RULE_DENY
        val modernAttempts = listOf<() -> Unit>(
            { call(service, listOf("setUidFirewallRule", "setFirewallUidRule"), OEM_DENY_CHAIN, uid, modernRule) },
            { call(service, listOf("setUidFirewallRules", "setFirewallUidRules"), OEM_DENY_CHAIN, intArrayOf(uid), intArrayOf(modernRule)) },
        )
        if (runAttempts(modernAttempts, failures)) return

        if (!enabled) {
            runCatching { call(service, listOf("setFirewallEnabled"), true) }
                .onFailure { failures += "legacy chain: ${it.message}" }
        }
        val legacyRule = if (enabled) RULE_ALLOW else RULE_DENY
        val legacyAttempts = listOf<() -> Unit>(
            { call(service, listOf("setUidFirewallRule", "setFirewallUidRule"), uid, enabled) },
            { call(service, listOf("setUidFirewallRule", "setFirewallUidRule"), uid, legacyRule) },
            { call(service, listOf("setUidFirewallRules", "setFirewallUidRules"), intArrayOf(uid), intArrayOf(legacyRule)) },
        )
        if (runAttempts(legacyAttempts, failures)) return
        error("No compatible firewall method on ${service.javaClass.name}: ${failures.joinToString(" | ")}")
    }

    private fun runAttempts(attempts: List<() -> Unit>, failures: MutableList<String>): Boolean {
        attempts.forEach { attempt ->
            try {
                attempt()
                return true
            } catch (error: Throwable) {
                failures += error.message ?: error.javaClass.name
            }
        }
        return false
    }

    private fun getWrappedService(backend: ServiceBackend): Any =
        wrappedServices[backend.serviceName] ?: synchronized(this) {
            wrappedServices[backend.serviceName] ?: run {
                val binder = SystemServiceHelper.getSystemService(backend.serviceName)
                    ?: error("${backend.serviceName} binder is null")
                val stub = Class.forName(backend.stubClassName)
                val asInterface = stub.getMethod("asInterface", IBinder::class.java)
                val original = asInterface.invoke(null, binder) ?: error("${backend.label} unavailable")
                val originalBinder = original.javaClass.getMethod("asBinder").invoke(original) as? IBinder
                    ?: error("${backend.label} binder unavailable")
                val wrapped = asInterface.invoke(null, ShizukuBinderWrapper(originalBinder))
                    ?: error("${backend.label} wrapper unavailable")
                wrappedServices[backend.serviceName] = wrapped
                wrapped
            }
        }

    private fun call(target: Any, names: List<String>, vararg args: Any) {
        val methods = names.flatMap { name ->
            target.javaClass.methods.filter { it.name == name && it.parameterCount == args.size }
        }
        if (methods.isEmpty()) error("Missing ${names.joinToString()}(${args.size})")
        var lastFailure: Throwable? = null
        methods.forEach { method ->
            try {
                invoke(target, method, args)
                return
            } catch (error: Throwable) {
                lastFailure = error
            }
        }
        throw lastFailure ?: NoSuchMethodException(names.joinToString())
    }

    private fun invoke(target: Any, method: Method, args: Array<out Any>) {
        method.isAccessible = true
        val adapted = Array(args.size) { index ->
            val expected = method.parameterTypes[index]
            val value = args[index]
            when {
                expected == Int::class.javaPrimitiveType -> when (value) {
                    is Boolean -> if (value) 1 else 0
                    is Number -> value.toInt()
                    else -> error("Unsupported int argument")
                }
                expected == Boolean::class.javaPrimitiveType -> when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    else -> error("Unsupported boolean argument")
                }
                expected == IntArray::class.java && value is IntArray -> value
                expected.isInstance(value) -> value
                else -> error("Argument does not match ${expected.name}")
            }
        }
        try {
            method.invoke(target, *adapted)
        } catch (error: InvocationTargetException) {
            throw error.targetException ?: error
        }
    }
}

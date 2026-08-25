package com.lladlam.melox.core.remoteconfig

import android.content.Context
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.core.network.MeloXGitHubRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MeloXRemoteConfigRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val mutableStatus = MutableStateFlow(MeloXRemoteConfigStatus())
    val status: StateFlow<MeloXRemoteConfigStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var client: MeloXRemoteConfigClient? = null

    @Volatile
    private var versionCode: Int = 0

    @Volatile
    private var periodicRefreshJob: Job? = null

    fun initializeAndRefresh(context: Context, versionCode: Int, force: Boolean = false) {
        val appContext = context.applicationContext
        synchronized(this) {
            this.versionCode = versionCode
            if (client == null) {
                client = MeloXRemoteConfigClient(
                    store = MeloXRemoteConfigStore(appContext),
                    verifier = MeloXRemoteConfigVerifier(),
                    routing = MeloXGitHubRouting(appContext),
                )
            }
            periodicRefreshJob?.cancel()
            periodicRefreshJob = scope.launch {
                while (isActive && MeloXRemoteConfigConsent.enabled(appContext)) {
                    delay(MeloXRemoteConfigRefreshIntervalMs)
                    if (MeloXRemoteConfigConsent.enabled(appContext) && MeloXAppVisibility.isForeground) {
                        refresh(force = true)
                    }
                }
            }
        }
        scope.launch {
            mutableStatus.value = client?.load(versionCode) ?: return@launch
            refresh(force = force)
            if (mutableStatus.value.error != null) {
                delay(1_500L)
                refresh(force = true)
            }
        }
    }

    suspend fun refresh(force: Boolean = true) = refreshMutex.withLock {
        val active = client ?: return@withLock
        mutableStatus.value = mutableStatus.value.copy(refreshing = true, error = null)
        mutableStatus.value = active.refresh(versionCode, force).copy(refreshing = false)
    }

    suspend fun clearCache(context: Context? = null) = refreshMutex.withLock {
        if (context != null && !MeloXRemoteConfigConsent.enabled(context)) {
            periodicRefreshJob?.cancel()
            periodicRefreshJob = null
        }
        val active = client
        mutableStatus.value = when {
            active != null -> active.clear(versionCode)
            context != null -> {
                MeloXRemoteConfigStore(context.applicationContext).clearEnvelope()
                MeloXRemoteConfigStatus()
            }
            else -> MeloXRemoteConfigStatus()
        }
    }
}

internal const val MeloXRemoteConfigRefreshIntervalMs = 2L * 60L * 60L * 1_000L

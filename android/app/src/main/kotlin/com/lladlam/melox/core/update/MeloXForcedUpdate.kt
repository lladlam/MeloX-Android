package com.lladlam.melox.core.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide forced-update prompt. Set only after a verified remote-config
 * gate and a `*-security` release are both present; the UI must not let the
 * user dismiss it.
 */
object MeloXForcedUpdate {
    private val mutableRelease = MutableStateFlow<MeloXRelease?>(null)
    val release: StateFlow<MeloXRelease?> = mutableRelease.asStateFlow()

    fun require(release: MeloXRelease) {
        if (release.securityCritical) mutableRelease.value = release
    }

    fun clear() {
        mutableRelease.value = null
    }
}

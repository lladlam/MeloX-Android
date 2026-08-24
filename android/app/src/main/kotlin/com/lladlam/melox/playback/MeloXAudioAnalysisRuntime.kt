package com.lladlam.melox.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeloXAudioAnalysisProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
)

object MeloXAudioAnalysisRuntime {
    private val lock = Any()
    private val _progress = MutableStateFlow(MeloXAudioAnalysisProgress())
    val progress: StateFlow<MeloXAudioAnalysisProgress> = _progress.asStateFlow()

    fun start(total: Int, completed: Int = 0) {
        synchronized(lock) {
            _progress.value = MeloXAudioAnalysisProgress(
                running = completed < total,
                completed = completed.coerceIn(0, total),
                total = total,
            )
        }
    }

    fun advance(failed: Boolean) {
        synchronized(lock) {
            val current = _progress.value
            _progress.value = current.copy(
                completed = current.completed + 1,
                failed = current.failed + if (failed) 1 else 0,
                running = current.completed + 1 < current.total,
            )
        }
    }
}

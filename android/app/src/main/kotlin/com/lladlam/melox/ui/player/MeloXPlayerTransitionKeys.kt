package com.lladlam.melox.ui.player

internal data class MeloXPlayerArtworkKey(val mediaId: String?)

internal data object MeloXPlayerShellKey

internal fun sharedPlayerArtworkKey(mediaId: String?): MeloXPlayerArtworkKey = MeloXPlayerArtworkKey(mediaId)

internal fun sharedPlayerShellKey(): MeloXPlayerShellKey = MeloXPlayerShellKey

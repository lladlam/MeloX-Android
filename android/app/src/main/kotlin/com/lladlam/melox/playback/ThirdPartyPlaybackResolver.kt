package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.thirdparty.ResolverKind

internal data class ThirdPartyTrack(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val sourceLabel: String,
    /** Quality identifier requested from the third-party source. */
    val sourceQuality: String? = null,
    /** Best-effort actual tier; null when the source cannot report one. */
    val actualQuality: AudioQualityTier? = null,
)

/** One configured API resolver or an imported LX script source. */
internal fun interface ThirdPartySource {
    fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack?

    fun cacheIdentity(): String = ""
}

/**
 * Final stage of the playback chain:
 *
 * 1. the current provider,
 * 2. a strict full-audio match from another built-in source,
 * 3. user-configured third-party resolvers.
 *
 * Only sources that are actually configured participate; an unset CHKSZ key or
 * an empty LX list simply returns null. Imported LX scripts keep their own
 * source/quality selection inside [LxUserPlaybackResolver].
 */
class ThirdPartyPlaybackResolver private constructor(
    private val enabledProvider: () -> Boolean,
    private val membershipOnlyProvider: () -> Boolean,
    sourceProvider: () -> List<ThirdPartySource>,
) {
    private val sources by lazy(sourceProvider)

    constructor(
        context: Context,
        enabledProvider: () -> Boolean,
        membershipOnlyProvider: () -> Boolean = { false },
    ) : this(
        enabledProvider = enabledProvider,
        membershipOnlyProvider = membershipOnlyProvider,
        sourceProvider = {
            listOf(
                ConfiguredResolverSource(context, ResolverKind.Meting),
                ConfiguredResolverSource(context, ResolverKind.Chksz),
                ConfiguredResolverSource(context, ResolverKind.Unm),
                withInstalledLxGuard(LxThirdPartySource(LxUserPlaybackResolver(context))),
            )
        },
    )

    internal constructor(
        enabledProvider: () -> Boolean,
        membershipOnlyProvider: () -> Boolean,
        sources: List<ThirdPartySource>,
    ) : this(
        enabledProvider = enabledProvider,
        membershipOnlyProvider = membershipOnlyProvider,
        sourceProvider = { sources },
    )

    fun cacheIdentity(): String = if (!enabledProvider()) {
        "disabled"
    } else {
        "membershipOnly=${membershipOnlyProvider()}:" + sources.joinToString("|") { it.cacheIdentity() }
    }

    /**
     * @param membershipFailure whether the current source failed because of
     *   membership/rights. Used by the "only for membership tracks" option.
     */
    internal fun resolve(
        track: MusicTrack,
        quality: AudioQualityTier,
        membershipFailure: Boolean,
    ): ThirdPartyTrack? {
        if (!enabledProvider() || track.id.sourceId.builtIn == null) return null
        if (membershipOnlyProvider() && !membershipFailure) return null
        for (source in sources) {
            val result = runCatching { source.resolve(track, quality) }
                .onFailure { Log.w(TAG, "Third-party source failed: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull()
                ?: continue
            if (result.url.startsWith("http://") || result.url.startsWith("https://")) {
                Log.i(
                    TAG,
                    "Resolved via ${result.sourceLabel} source=${track.id.sourceId.value} " +
                        "requested=${quality.name} sourceQuality=${result.sourceQuality ?: "unknown"} " +
                        "actual=${result.actualQuality?.name ?: "unknown"}",
                )
                return result
            }
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

private class LxThirdPartySource(
    private val resolver: LxUserPlaybackResolver,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        resolver.resolve(track, quality)?.let {
            ThirdPartyTrack(
                url = it.url,
                requestHeaders = it.requestHeaders,
                sourceLabel = "lx:${it.sourceId}",
                sourceQuality = it.sourceQuality,
                actualQuality = it.actualQuality,
            )
        }

    override fun cacheIdentity(): String = "lx:${resolver.cacheIdentity()}"
}

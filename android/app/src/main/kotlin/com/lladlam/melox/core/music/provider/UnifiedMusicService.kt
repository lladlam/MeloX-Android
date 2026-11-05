package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.model.MusicPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Explicit opt-in cross-provider search.
 *
 * This class deliberately does not decide which providers participate. The caller
 * must pass the already-approved source whitelist from [MusicProviderSelectionStore].
 * It also does not perform automatic playback fallback or try to bypass provider
 * availability/rights: results keep their original provider identity.
 */
class UnifiedMusicService(
    private val registry: MusicProviderRegistry,
) {
    data class SearchFailure(
        val sourceId: SourceId,
        val message: String,
    ) {
        constructor(source: MusicSource, message: String) : this(SourceId.fromBuiltIn(source), message)
        val source: MusicSource get() = sourceId.builtIn ?: error("Dynamic search failure: ${sourceId.value}")
    }

    data class SearchResult(
        val tracks: List<MusicTrack>,
        val failures: List<SearchFailure> = emptyList(),
        val aggregated: List<AggregatedTrack> = emptyList(),
        val pages: Map<SourceId, MusicPage<MusicTrack>> = emptyMap(),
    )

    suspend fun searchSongs(
        query: String,
        sources: Set<MusicSource>,
        page: Int = 1,
        pageSizePerProvider: Int = 20,
    ): SearchResult = searchSourceIds(query, sources.mapTo(linkedSetOf(), SourceId::fromBuiltIn), page, pageSizePerProvider)

    suspend fun searchSourceIds(
        query: String,
        sources: Set<SourceId>,
        page: Int = 1,
        pageSizePerProvider: Int = 20,
    ): SearchResult = coroutineScope {
        val normalized = query.trim()
        if (normalized.isEmpty() || sources.isEmpty()) return@coroutineScope SearchResult(emptyList())

        require(page > 0 && pageSizePerProvider in 1..100 && sources.size <= 64)
        val generation = registry.generation
        val orderedSources = sources.sortedBy { it.value }
        val requests = orderedSources.map { source ->
            async {
                val provider = registry[source]
                val search = provider as? SearchCapability
                if (search == null || !provider.supports(MusicCapability.Search)) {
                    source to Result.failure<MusicPage<MusicTrack>>(IllegalStateException("${source.displayName} 当前不可用或没有搜索能力"))
                } else {
                    source to try {
                        val result = search.searchSongs(
                            query = normalized,
                            page = page.coerceAtLeast(1),
                            pageSize = pageSizePerProvider.coerceIn(1, 50),
                        )
                        check(registry.generation == generation && registry[source] === provider) { "音源状态已变化" }
                        require(result.items.all { it.id.sourceId == source }) { "search source identity mismatch" }
                        Result.success(result)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Result.failure(error) }
                }
            }
        }.awaitAll()

        // Keep provider results distinct. Same-title tracks are not silently
        // merged here because their rights/quality/account availability differ.
        val pages = requests.mapNotNull { (source, result) -> result.getOrNull()?.let { source to it } }.toMap()
        val tracks = pages.values.flatMap { it.items }
        SearchResult(
            tracks = tracks,
            failures = requests.mapNotNull { (source, result) -> result.exceptionOrNull()?.let { SearchFailure(source, it.message ?: "搜索失败") } },
            aggregated = TrackAggregation.aggregate(tracks),
            pages = pages,
        )
    }

    data class AggregationResult(
        val tracks: List<MusicTrack>,
        val failures: List<SearchFailure> = emptyList(),
    )

    suspend fun collectAggregationTracks(
        sources: Set<MusicSource>,
        pageSizePerProvider: Int = 100,
    ): AggregationResult = coroutineScope {
        val results = MusicSource.entries.filter(sources::contains).map { source ->
            async {
                val capability = registry[source] as? LocalAggregationCapability
                if (capability == null) {
                    ProviderResult(source, emptyList(), null)
                } else {
                    runCatching { capability.aggregationTracks(page = 1, pageSize = pageSizePerProvider.coerceIn(1, 200)).items }
                        .fold({ ProviderResult(source, it, null) }, { ProviderResult(source, emptyList(), SearchFailure(source, it.message ?: "${source.displayName} 聚合数据读取失败")) })
                }
            }
        }.awaitAll()
        AggregationResult(results.flatMap(ProviderResult::tracks), results.mapNotNull(ProviderResult::failure))
    }

    suspend fun collectRecommendationCandidates(
        sources: Set<MusicSource>,
        limitPerProvider: Int = 30,
    ): List<MusicTrack> = collectRecommendationCandidatesSourceIds(sources.mapTo(linkedSetOf(), SourceId::fromBuiltIn), limitPerProvider)

    suspend fun collectRecommendationCandidatesSourceIds(
        sources: Set<SourceId>,
        limitPerProvider: Int = 30,
    ): List<MusicTrack> = coroutineScope {
        require(sources.size <= 64)
        val generation = registry.generation
        sources.sortedBy { it.value }.map { source ->
            async {
                val provider = registry[source] ?: return@async emptyList()
                if (!provider.supports(MusicCapability.HomeRecommendations)) return@async emptyList()
                val feed = provider as? HomeFeedCapability ?: return@async emptyList()
                try {
                    val home = feed.homeFeed(
                        playlistLimit = 6,
                        newSongLimit = limitPerProvider.coerceIn(1, 50),
                        rankingLimit = 4,
                    )
                    check(registry.generation == generation && registry[source] === provider) { "source changed" }
                    (home.newSongs + home.rankings.flatMap { it.previewTracks }).also { tracks -> require(tracks.all { it.id.sourceId == source }) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            }
        }.awaitAll().flatten().distinctBy { "${it.id.sourceId.value}:${it.id.value}" }
    }

    private data class ProviderResult(
        val source: MusicSource,
        val tracks: List<MusicTrack>,
        val failure: SearchFailure?,
    )
}

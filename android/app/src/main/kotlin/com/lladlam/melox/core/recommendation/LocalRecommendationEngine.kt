package com.lladlam.melox.core.recommendation

import android.content.Context
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.core.music.provider.TrackAggregation
import com.lladlam.melox.core.music.provider.UnifiedMusicService
import com.lladlam.melox.core.music.model.MusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sqrt

object LocalRecommendationEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null

    fun start(context: Context) {
        if (!LocalRecommendationStore.isAlgorithmEnabled(context)) return
        job?.cancel()
        job = scope.launch {
            val features = withContext(Dispatchers.IO) { LocalRecommendationStore.readFeatures(context) }
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.QuickScoring, 0, features.size, message = "快速记录行为；不重建推荐歌单")
            if (features.isEmpty()) {
                LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.QuickScoring, 0, 0, message = "已记录行为，等待后台全量分析")
                return@launch
            }
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.QuickScoring, features.size, features.size, message = "快速更新完成；请运行后台全量分析生成新推荐")
        }
    }

    fun startFullAnalysis(context: Context) {
        if (!LocalRecommendationStore.isAlgorithmEnabled(context) || !LocalRecommendationStore.hasPersonalizationConsent(context)) return
        job?.cancel()
        job = scope.launch {
            val existing = withContext(Dispatchers.IO) { LocalRecommendationStore.readFeatures(context) }
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.SyncPreparing, 0, 0, message = "准备本地聚合数据", isFullAnalysis = true)
            val trainingTracks = runCatching {
                val registry = MeloXMusicProviders.create(context)
                UnifiedMusicService(registry).collectAggregationTracks(MusicSource.entries.toSet(), pageSizePerProvider = 100).tracks
            }.getOrDefault(emptyList())
            val candidateTracks = runCatching {
                val registry = MeloXMusicProviders.create(context)
                val selection = com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
                val dynamic = if (selection.unifiedEnabled(context)) selection.unifiedSourceIds(context).filter { it.builtIn == null } else emptyList()
                val sources = MusicSource.entries.mapTo(linkedSetOf(), com.lladlam.melox.core.music.model.SourceId::fromBuiltIn) + dynamic
                UnifiedMusicService(registry).collectRecommendationCandidatesSourceIds(sources, limitPerProvider = 30)
            }.getOrDefault(emptyList())
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.Syncing, trainingTracks.size, trainingTracks.size + candidateTracks.size, message = "已采集 ${trainingTracks.size} 个训练来源和 ${candidateTracks.size} 个候选来源", isFullAnalysis = true)
            val trainingGroups = TrackAggregation.aggregate(trainingTracks)
            val candidateGroups = TrackAggregation.aggregate(candidateTracks)
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.IdentityMerging, trainingGroups.size + candidateGroups.size, trainingTracks.size + candidateTracks.size, message = "已合并训练集与候选集歌曲身份", isFullAnalysis = true)
            val imported = trainingGroups.mapNotNull { group ->
                val candidate = group.recommendation ?: return@mapNotNull null
                val key = "${group.key.title}|${group.key.artist}|${group.key.version}"
                val old = existing.firstOrNull { it.trackKey == key }
                LocalTrackFeatures(
                    trackKey = key,
                    title = candidate.track.title,
                    artist = candidate.track.artistText,
                    playCount = old?.playCount ?: 0,
                    completedCount = old?.completedCount ?: 0,
                    skipCount = old?.skipCount ?: 0,
                    // The aggregation collection is the training corpus. It is
                    // never emitted directly as recommendations.
                    liked = old?.liked ?: true,
                    lastPlayedAtMs = old?.lastPlayedAtMs ?: 0L,
                    sourceCount = group.candidates.size,
                    versionKind = group.key.version.name,
                    qualityPreference = candidate.totalScore / 100f,
                )
            }
            withContext(Dispatchers.IO) {
                LocalRecommendationStore.writeFeatures(context, imported)
                LocalRecommendationStore.writeCandidateTracks(context, candidateGroups.mapNotNull { it.recommendation?.track })
            }
            val trainingKeys = imported.mapTo(hashSetOf(), LocalTrackFeatures::trackKey)
            val positiveSamples = imported.filter { it.liked || it.completedCount > 0 || it.playCount > 0 }
            val trainingArtistWeights = trainArtistWeights(positiveSamples)
            val preference = trainPreferenceVector(positiveSamples)
            val features = imported
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.RuleScoring, 0, features.size, message = "计算规则推荐", isFullAnalysis = true)
            val rules = features.mapIndexed { index, item ->
                LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.RuleScoring, index + 1, features.size, message = "计算规则推荐", isFullAnalysis = true)
                item to ruleScore(item)
            }
            val model = features.mapIndexed { index, item ->
                LocalRecommendationStore.progress.value = LocalAnalysisProgress(LocalAnalysisStage.ModelScoring, index + 1, features.size, message = "运行本地轻量模型", isFullAnalysis = true)
                item to modelScore(item)
            }.toMap()
            val modelWeight = when {
                features.sumOf { it.playCount } < 30 -> .15f
                features.sumOf { it.playCount } < 200 -> .40f
                else -> .60f
            }
            val recommendations = diversify(candidateGroups.mapNotNull { group ->
                val candidate = group.recommendation ?: return@mapNotNull null
                // The user's library/history is training data, never the output list.
                val candidateKey = "${group.key.title}|${group.key.artist}|${group.key.version}"
                if (candidateKey in trainingKeys) return@mapNotNull null
                val similarity = cosine(
                    preference,
                    LocalRecommendationStore.featureVector(candidate.track.title, candidate.track.artistText),
                )
                if (positiveSamples.isEmpty() || similarity < .42f) return@mapNotNull null
                val artistWeight = trainingArtistWeights[TrackAggregation.normalize(candidate.track.artistText)] ?: 0f
                val learned = (similarity * 100f + artistWeight * 30f - candidatePenalty(candidate.track, positiveSamples)).coerceIn(-100f, 100f)
                val rule = similarity * 100f - candidatePenalty(candidate.track, positiveSamples)
                LocalRecommendationItem(
                    trackKey = candidateKey,
                    title = candidate.track.title,
                    artist = candidate.track.artistText,
                    score = rule * (1f - modelWeight) + learned * modelWeight,
                    ruleScore = rule,
                    modelScore = learned,
                    reason = "与收藏/高完成度歌曲相似 ${(similarity * 100f).toInt()}% · ${candidate.reason}",
                )
            }.sortedWith(compareByDescending<LocalRecommendationItem> { it.score }.thenBy { it.artist }).let { ranked ->
                val exploit = ranked.take(27)
                val explore = ranked.drop(27).filter { it.score in 40f..70f }.shuffled().take(3)
                (exploit + explore).distinctBy(LocalRecommendationItem::trackKey).take(30)
            })
            withContext(Dispatchers.IO) {
                LocalRecommendationStore.writeRecommendations(context, recommendations.take(100))
                LocalRecommendationStore.writeModelMetadata(context, LocalModelMetadata(sampleCount = features.sumOf { it.playCount }, trackCount = features.size, trainedAtMs = System.currentTimeMillis()))
            }
            LocalRecommendationStore.progress.value = LocalAnalysisProgress(
                LocalAnalysisStage.Completed,
                features.size,
                features.size,
                message = if (recommendations.isEmpty()) "分析完成，但没有达到相似度阈值的候选歌曲" else "分析完成，生成 ${recommendations.size} 首相似推荐",
                isFullAnalysis = true,
            )
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun ruleScore(item: LocalTrackFeatures): Float {
        val recencyDays = ((System.currentTimeMillis() - item.lastPlayedAtMs).coerceAtLeast(0L) / 86_400_000L).toFloat()
        val recency = if (item.lastPlayedAtMs == 0L) 0f else (20f * exp(-recencyDays / 14f))
        return (if (item.liked) 100f else 0f) + item.playCount * 4f + item.completedCount * 8f - item.skipCount * 20f + recency + item.sourceCount * 2f
    }

    private fun modelScore(item: LocalTrackFeatures): Float =
        (item.completedCount * 0.45f + item.playCount * 0.20f + if (item.liked) 0.35f else 0f - item.skipCount * 0.30f + item.qualityPreference * 0.15f).coerceIn(-1f, 1f) * 100f

    private fun reasonFor(item: LocalTrackFeatures, modelScore: Float): String = when {
        item.liked -> "明确喜欢"
        item.completedCount >= 3 -> "完成播放偏好"
        item.qualityPreference > .5f -> "音质偏好"
        modelScore > 35f -> "轻量模型预测"
        else -> "近期播放偏好"
    }

    private fun diversify(items: List<LocalRecommendationItem>): List<LocalRecommendationItem> {
        val result = mutableListOf<LocalRecommendationItem>()
        val artistCounts = mutableMapOf<String, Int>()
        items.forEach { item ->
            val count = artistCounts[item.artist].orZero()
            if (count < 2 || result.size < 3) {
                result += item
                artistCounts[item.artist] = count + 1
            }
        }
        items.filterNot(result::contains).forEach(result::add)
        return result
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun trainPreferenceVector(samples: List<LocalTrackFeatures>): FloatArray {
        val vector = FloatArray(64)
        if (samples.isEmpty()) return vector
        samples.forEach { sample ->
            val reward = when {
                sample.skipCount > sample.completedCount -> -1.5f
                sample.liked -> 2f
                sample.completedCount > 0 -> 1f
                else -> .25f
            }
            val features = LocalRecommendationStore.featureVector(sample.title, sample.artist)
            features.indices.forEach { index -> vector[index] += features[index] * reward }
        }
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] = (vector[it] / norm).coerceIn(-1f, 1f) }
        return vector
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        return if (leftNorm <= 0f || rightNorm <= 0f) 0f else {
            (dot / (sqrt(leftNorm) * sqrt(rightNorm))).coerceIn(-1f, 1f)
        }
    }

    private fun candidateSimilarity(
        candidate: com.lladlam.melox.core.music.model.MusicTrack,
        training: List<LocalTrackFeatures>,
    ): Float {
        val candidateArtist = TrackAggregation.normalize(candidate.artistText)
        val candidateTitle = TrackAggregation.normalize(candidate.title)
        val candidateVector = contentVector(candidateTitle, candidateArtist)
        return training.maxOfOrNull { item -> cosine(candidateVector, contentVector(TrackAggregation.normalize(item.title), TrackAggregation.normalize(item.artist))) } ?: 0f
    }

    private fun candidatePenalty(candidate: com.lladlam.melox.core.music.model.MusicTrack, training: List<LocalTrackFeatures>): Float {
        val artist = TrackAggregation.normalize(candidate.artistText)
        return if (training.any { TrackAggregation.normalize(it.artist) == artist && it.skipCount > it.completedCount }) 35f else 0f
    }

    private fun contentVector(title: String, artist: String): FloatArray {
        val vector = FloatArray(96)
        (title + " " + artist).windowed(2, 1, partialWindows = true).forEach { token ->
            val index = (token.hashCode() and Int.MAX_VALUE) % vector.size
            vector[index] += 1f
        }
        return vector
    }

    /** Five small SGD-style passes over implicit positives and sampled negatives. */
    private fun trainArtistWeights(features: List<LocalTrackFeatures>): Map<String, Float> {
        val weights = mutableMapOf<String, Float>()
        repeat(5) {
            features.shuffled().forEach { item ->
                val artist = TrackAggregation.normalize(item.artist)
                val positive = (if (item.liked) 1f else 0f) +
                    (item.completedCount.coerceAtMost(10) / 10f) -
                    (item.skipCount.coerceAtMost(5) / 5f)
                val current = weights[artist] ?: 0f
                val prediction = 1f / (1f + kotlin.math.exp(-current))
                weights[artist] = current + .08f * (positive.coerceIn(-1f, 1f) - prediction)
            }
        }
        return weights
    }
}

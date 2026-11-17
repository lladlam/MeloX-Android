package com.lladlam.melox.core.provider.bilibili

import com.lladlam.melox.core.lyrics.LyricTimingKind
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.MusicTrack
import kotlin.math.abs
import kotlin.math.max

data class BilibiliTitleCandidate(
    val title: String,
    val artist: String? = null,
)

enum class LyricDurationConfidence { High, Low }

data class EffectiveLyricDuration(
    val durationMs: Long,
    val confidence: LyricDurationConfidence,
)

data class BilibiliLyricSourceResult(
    val source: String,
    val document: LyricsDocument,
    val catalogTrack: MusicTrack,
)

data class BilibiliReplacementSelection(
    val track: MusicTrack,
    val score: Int,
)

object BilibiliLyricAlignment {
    private val WrapperNoise = Regex(
        """(?i)(?:官方(?:完整版)?|official(?:\s+(?:music\s+)?video)?|mv|4k|8k|hi-res|动态歌词|歌词版|完整版|高清|无损|杜比|纯享版|单曲循环)""".replace("\\\\", "\\"),
    )
    private val Brackets = Regex("""[【\[（(]([^】\]）)]+)[】\]）)]""".replace("\\\\", "\\"))
    private val Version = Regex("""(?i)\b(?:live|remix|dj|mix|acoustic|instrumental)\b|现场版?|重混|混音版?|电音版?|伴奏版?""".replace("\\\\", "\\"))
    private val UnsafeReplacement = Regex("(?i)合集|串烧|reaction|反应|教程|教学|翻唱|cover|变速|加速|慢速|剪辑|cut|片段|盘点|点评|解析")

    fun extractTitleCandidates(rawTitle: String, uploader: String? = null): List<BilibiliTitleCandidate> {
        val decoded = BilibiliProvider.cleanTitle(rawTitle)
            .replace(Regex("[|｜]"), " ")
            // Variation selectors / zero-width joiners are common in Bilibili
            // emoji-decorated titles and must not become part of a search term.
            .replace(Regex("[\uFE0F\u200D\u200B]"), "")
            .replace(WrapperNoise, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '_', '·', ':', '：', '!', '！', '?', '？')
        if (decoded.isBlank()) return emptyList()
        val versions = Version.findAll(decoded).map { it.value }.toList().distinct()
        fun retainVersion(value: String): String {
            val clean = value.replace(Brackets) { match ->
                match.groupValues[1].takeIf { Version.containsMatchIn(it) }.orEmpty()
            }.replace(Regex("""\s+"""), " ").trim(' ', '-', '_', '·', ':', '：')
            if (clean.isBlank()) return clean
            return versions.fold(clean) { result, version ->
                if (result.contains(version, ignoreCase = true)) result else "$result $version"
            }.trim()
        }
        val ordered = mutableListOf<BilibiliTitleCandidate>()
        val quotedTitles = buildList {
            Regex("《([^》]{1,100})》").findAll(decoded).forEach { add(it.groupValues[1]) }
            Regex("「([^」]{1,100})」").findAll(decoded).forEach { add(it.groupValues[1]) }
        }
        // The outer video title often names the anime/album while the final
        // quoted value in the part title is the actual song.
        quotedTitles.asReversed().forEach { quoted ->
            retainVersion(quoted).takeIf(String::isNotBlank)?.let {
                ordered += BilibiliTitleCandidate(it)
                ordered += BilibiliTitleCandidate(it, uploader?.takeIf(String::isNotBlank))
            }
        }
        // Bilibili cover titles frequently use "song / vocalist" or
        // "song / vocalist / arranger". Search the leftmost segment as a plain
        // song title; otherwise the whole slash-joined string can never pass
        // the catalog matcher even when a perfect song result exists.
        val slashParts = decoded.split(Regex("""\s*[/／\\]\s*"""))
            .map { retainVersion(it) }
            .filter(String::isNotBlank)
        if (slashParts.size >= 2) {
            val plainTitle = slashParts.first()
            ordered += BilibiliTitleCandidate(plainTitle)
            ordered += BilibiliTitleCandidate(
                plainTitle,
                slashParts.drop(1).joinToString(" ").takeIf(String::isNotBlank),
            )
        }
        // "song feat. artist" and "song ft. artist" have the same problem.
        val featParts = decoded.split(Regex("""(?i)\s+(?:feat\.?|ft\.?|with)\s+"""))
            .map { retainVersion(it) }
            .filter(String::isNotBlank)
        if (featParts.size >= 2) {
            ordered += BilibiliTitleCandidate(featParts.first())
            ordered += BilibiliTitleCandidate(featParts.first(), featParts.drop(1).joinToString(" "))
        }
        val separators = listOf(" - ", " – ", " — ", "_", "－")
        separators.firstNotNullOfOrNull { separator ->
            decoded.split(separator, limit = 2).takeIf { it.size == 2 }
        }?.let { (left, right) ->
            val cleanLeft = retainVersion(left)
            val cleanRight = retainVersion(right)
            if (cleanLeft.isNotBlank() && cleanRight.isNotBlank()) {
                // Bilibili multi-part videos commonly use "collection - song".
                // Search the part title without treating the collection/uploader
                // as the recording artist before trying structured artist forms.
                ordered += BilibiliTitleCandidate(cleanRight)
                ordered += BilibiliTitleCandidate(cleanRight, cleanLeft)
                ordered += BilibiliTitleCandidate(cleanLeft, cleanRight)
            }
        }
        retainVersion(
            decoded.replace(Regex("《[^》]+》|「[^」]+」"), " "),
        ).takeIf(String::isNotBlank)?.let {
            ordered += BilibiliTitleCandidate(it)
            ordered += BilibiliTitleCandidate(it, uploader?.takeIf(String::isNotBlank))
        }
        return ordered.distinctBy { normalize(it.title) to normalize(it.artist.orEmpty()) }
    }

    fun effectiveDuration(document: LyricsDocument): EffectiveLyricDuration? {
        if (document.lines.isEmpty()) return null
        val syllableEnd = document.lines.flatMap { it.syllables }.maxOfOrNull { it.endTimeMs }
        val authoredLineEnd = document.lines.mapNotNull { line ->
            line.durationMs
                ?.takeIf { it > 0 && line.timingKind == LyricTimingKind.Precise }
                ?.let { line.timeMs + it }
        }.maxOrNull()
        val high = listOfNotNull(syllableEnd, authoredLineEnd).filter { it > 0 }.maxOrNull()
        if (high != null) return EffectiveLyricDuration(high, LyricDurationConfidence.High)
        val lastLine = document.lines.maxOfOrNull { it.timeMs }?.takeIf { it > 0 } ?: return null
        return EffectiveLyricDuration(lastLine, LyricDurationConfidence.Low)
    }

    fun consensus(results: List<BilibiliLyricSourceResult>): Long? {
        if (results.map { it.source }.distinct().size != 3) return null
        val durations = results.map { effectiveDuration(it.document) ?: return null }
        if (durations.any { it.confidence != LyricDurationConfidence.High }) return null
        val values = durations.map(EffectiveLyricDuration::durationMs)
        val center = values.sorted()[1]
        val tolerance = max(3_000L, center / 100L)
        return center.takeIf { values.all { abs(it - center) <= tolerance } }
    }

    fun audioClearlyMismatches(audioDurationMs: Long, lyricDurationMs: Long): Boolean {
        if (audioDurationMs <= 0 || lyricDurationMs <= 0) return false
        val delta = audioDurationMs - lyricDurationMs
        val earlyCutTolerance = max(3_000L, lyricDurationMs / 50L)
        val outroTolerance = max(12_000L, lyricDurationMs / 12L)
        return delta < -earlyCutTolerance || delta > outroTolerance
    }

    fun selectReplacement(
        candidates: List<MusicTrack>,
        originalIdentity: String,
        title: String,
        artist: String?,
        consensusDurationMs: Long,
    ): BilibiliReplacementSelection? {
        val titleKey = normalize(title)
        val artistKey = normalize(artist.orEmpty())
        if (titleKey.isBlank()) return null
        return candidates.asSequence()
            .filter { it.id.value != originalIdentity }
            .filterNot { UnsafeReplacement.containsMatchIn(it.title) }
            .mapNotNull { track ->
                val duration = track.durationMs ?: return@mapNotNull null
                val delta = abs(duration - consensusDurationMs)
                val tolerance = max(3_000L, consensusDurationMs / 100L)
                if (delta > tolerance) return@mapNotNull null
                val candidateTitle = normalize(track.title)
                if (!candidateTitle.contains(titleKey)) return@mapNotNull null
                val exactTitle = candidateTitle == titleKey
                val artistMatch = artistKey.isNotBlank() && (
                    candidateTitle.contains(artistKey) || normalize(track.artistText).contains(artistKey)
                )
                val score = 70 + (if (exactTitle) 20 else 0) + (if (artistMatch) 15 else 0) - (delta / 1_000L).toInt()
                BilibiliReplacementSelection(track, score)
            }
            .filter { it.score >= 85 }
            .maxByOrNull(BilibiliReplacementSelection::score)
    }

    fun bestLyrics(results: List<BilibiliLyricSourceResult>): LyricsDocument = results
        .filter { it.document.lines.isNotEmpty() }
        .maxByOrNull { result ->
            result.document.lines.count { it.syllables.isNotEmpty() } * 10_000 +
                result.document.lines.count { it.timingKind == LyricTimingKind.Precise } * 100 +
                result.document.lines.size
        }?.document ?: LyricsDocument(emptyList())

    fun isSafeCatalogMatch(candidate: MusicTrack, requested: BilibiliTitleCandidate): Boolean {
        val requestedTitle = normalize(requested.title)
        val candidateTitle = normalize(candidate.title)
        if (requestedTitle.isBlank() || !candidateTitle.contains(requestedTitle)) return false
        if (significantPunctuation(requested.title) != significantPunctuation(candidate.title)) return false
        val requestedVersion = versionKey(requested.title)
        if (requestedVersion != versionKey(candidate.title)) return false
        val artist = normalize(requested.artist.orEmpty())
        return artist.isBlank() || candidateTitle.contains(artist) || normalize(candidate.artistText).contains(artist)
    }

    private fun versionKey(value: String): String = Version.findAll(value).joinToString("|") { normalize(it.value) }
    internal fun significantPunctuation(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '!', '！' -> append('!')
                '?', '？' -> append('?')
            }
        }
    }
    internal fun normalize(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}

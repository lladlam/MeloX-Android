package com.lladlam.melox.core.lyrics

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Parses Apple Music/AMLL TTML including timed spans and iTunes extensions. */
object TtmlLyricsParser {
    fun parse(content: String, script: MeloXLyricScript = MeloXLyricScript.Original): LyricsDocument {
        if (!content.contains("http://www.w3.org/ns/ttml")) return LyricsDocument(emptyList())
        val document = runCatching {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(preformat(content))))
        }.getOrNull() ?: return LyricsDocument(emptyList())
        val root = document.documentElement ?: return LyricsDocument(emptyList())
        val agents = parseAgents(root)
        val translations = parseTranslations(root, script)
        val transliterations = parseTransliterations(root)
        val paragraphs = root.getElementsByTagNameNS("*", "p")
        val lines = buildList {
            for (index in 0 until paragraphs.length) {
                (paragraphs.item(index) as? Element)
                    ?.let { parseParagraph(it, agents, translations, transliterations, script) }
                    ?.let(::add)
            }
        }.sortedBy(LyricLine::timeMs)
        val quality = if (lines.any { it.syllables.isNotEmpty() }) LyricQuality.Authored else LyricQuality.LineSynchronized
        return LyricsDocument(lines, LyricSource.AmlL, quality, pseudoTimingAllowed = quality == LyricQuality.LineSynchronized)
    }

    private fun parseParagraph(
        paragraph: Element,
        agents: Map<String, LyricAgent>,
        translations: Map<String, SelectedTranslation>,
        transliterations: Map<String, List<String>>,
        script: MeloXLyricScript,
    ): LyricLine? {
        val start = paragraph.attr("begin")?.parseTime() ?: return null
        val end = paragraph.attr("end")?.parseTime()
        val key = paragraph.attr("itunes:key", "key")
        val agent = agents[paragraph.attr("ttm:agent", "agent")]
        val directSpans = paragraph.directElements("span")
        var syllables = parseSyllables(paragraph)
        val phonetics = transliterations[key]
        val romanizationSyllables = if (phonetics != null && phonetics.size == syllables.size) {
            syllables.zip(phonetics) { syllable, text ->
                LyricSyllable(text, syllable.startTimeMs, syllable.endTimeMs)
            }
        } else emptyList()
        val inlineRoman = directSpans.firstOrNull { it.hasRole("x-roman") }?.allText()?.trim()
        val romanization = inlineRoman ?: romanizationSyllables.joinToString(" ") { it.text }.takeIf(String::isNotBlank)
        val inlineTranslation = selectTranslation(directSpans.filter {
            it.hasRole("x-translation") && !it.hasRole("x-bg") && it.isChineseTranslation()
        }, script)
        val translation = inlineTranslation?.text ?: translations[key]?.text?.splitTranslation()?.first
        val storedTranslation = translations[key]
        val adaptTranslation = when {
            inlineTranslation != null -> !inlineTranslation.exact
            storedTranslation != null -> !storedTranslation.exact
            else -> false
        }
        val accompaniment = directSpans.filter { it.hasRole("x-bg") }.mapNotNull { background ->
            parseBackground(background, key, agent, translations, script)
        }
        val plainText = paragraph.primaryText().trim()
        if (syllables.isEmpty() && plainText.isNotBlank()) {
            return LyricLine(
                timeMs = start,
                durationMs = end?.minus(start)?.coerceAtLeast(1L),
                text = plainText,
                translation = translation,
                adaptTranslation = adaptTranslation,
                romanization = romanization,
                agent = agent,
                timingKind = LyricTimingKind.LineSynchronized,
                accompaniment = accompaniment,
            )
        }
        val text = syllables.joinToString("") { it.text }.trim()
        if (text.isBlank() && accompaniment.isEmpty()) return null
        return LyricLine(
            timeMs = start,
            durationMs = end?.minus(start)?.coerceAtLeast(1L),
            text = text,
            syllables = syllables,
            translation = translation,
            adaptTranslation = adaptTranslation,
            romanization = romanization,
            romanizationSyllables = romanizationSyllables,
            agent = agent,
            timingKind = if (syllables.isEmpty()) LyricTimingKind.LineSynchronized else LyricTimingKind.Precise,
            accompaniment = accompaniment,
        )
    }

    private fun parseBackground(
        element: Element,
        parentKey: String?,
        parentAgent: LyricAgent?,
        translations: Map<String, SelectedTranslation>,
        script: MeloXLyricScript,
    ): LyricAccompaniment? {
        val syllables = parseSyllables(element)
        if (syllables.isEmpty()) return null
        val key = element.attr("itunes:key", "key") ?: parentKey
        val selected = selectTranslation(element.directElements("span").filter {
            it.hasRole("x-translation") && it.isChineseTranslation()
        }, script)
        val translation = selected?.text ?: translations[key]?.text?.splitTranslation()?.let { it.second ?: it.first }
        val start = element.attr("begin")?.parseTime() ?: syllables.first().startTimeMs
        val end = element.attr("end")?.parseTime() ?: syllables.last().endTimeMs
        return LyricAccompaniment(
            timeMs = start,
            durationMs = (end - start).coerceAtLeast(1L),
            text = syllables.joinToString("") { it.text }.trim(),
            syllables = syllables,
            translation = translation,
            agent = parentAgent,
        )
    }

    private fun parseSyllables(parent: Element): List<LyricSyllable> = buildList {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.localTag != "span" || element.hasRole("x-translation") || element.hasRole("x-bg") || element.hasRole("x-roman")) continue
            val start = element.attr("begin")?.parseTime() ?: continue
            val end = element.attr("end")?.parseTime() ?: continue
            var text = element.allText()
            val next = children.item(index + 1)
            if (next?.nodeType == Node.TEXT_NODE) text += next.nodeValue.orEmpty()
            if (text.isNotEmpty()) add(LyricSyllable(text, start, end.coerceAtLeast(start + 1L)))
        }
        if (isNotEmpty()) this[lastIndex] = last().copy(text = last().text.trimEnd())
    }

    private fun parseAgents(root: Element): Map<String, LyricAgent> {
        val nodes = root.getElementsByTagNameNS("*", "agent")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val id = element.attr("xml:id", "id").orEmpty()
                if (id.isNotBlank()) put(id, LyricAgent(id, id, if (index == 0) LyricAgentAlignment.Normal else LyricAgentAlignment.Flipped))
            }
        }
    }

    private data class SelectedTranslation(val text: String, val exact: Boolean)

    private fun parseTranslations(root: Element, script: MeloXLyricScript): Map<String, SelectedTranslation> {
        val grouped = linkedMapOf<String, MutableList<Element>>()
        val nodes = root.getElementsByTagNameNS("*", "translation")
        for (index in 0 until nodes.length) {
            val texts = (nodes.item(index) as? Element)?.getElementsByTagNameNS("*", "text") ?: continue
            for (textIndex in 0 until texts.length) {
                val text = texts.item(textIndex) as? Element ?: continue
                val key = text.attr("for") ?: continue
                if (text.isChineseTranslation()) grouped.getOrPut(key, ::mutableListOf).add(text)
            }
        }
        return grouped.mapNotNull { (key, candidates) ->
            selectTranslation(candidates, script)?.let { key to it }
        }.toMap()
    }

    private fun parseTransliterations(root: Element): Map<String, List<String>> = buildMap {
        val nodes = root.getElementsByTagNameNS("*", "transliteration")
        for (index in 0 until nodes.length) {
            val texts = (nodes.item(index) as? Element)?.getElementsByTagNameNS("*", "text") ?: continue
            for (textIndex in 0 until texts.length) {
                val text = texts.item(textIndex) as? Element ?: continue
                val key = text.attr("for") ?: continue
                val spans = text.getElementsByTagNameNS("*", "span")
                val values = buildList {
                    for (spanIndex in 0 until spans.length) spans.item(spanIndex).textContent.trim().takeIf(String::isNotBlank)?.let(::add)
                }
                if (values.isNotEmpty()) put(key, values)
            }
        }
    }

    private fun Element.primaryText(): String = buildString {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index)
            when {
                child.nodeType == Node.TEXT_NODE -> append(child.nodeValue)
                child is Element && !child.hasRole("x-translation") && !child.hasRole("x-bg") && !child.hasRole("x-roman") -> append(child.allText())
            }
        }
    }.decodeEntities()

    private fun Element.directElements(name: String): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) (nodes.item(index) as? Element)?.takeIf { it.localTag == name }?.let(::add)
    }

    private val Element.localTag: String get() = localName ?: tagName.substringAfter(':')
    private fun Element.hasRole(value: String): Boolean = attr("ttm:role", "role") == value
    private fun Element.allText(): String = textContent.orEmpty().decodeEntities()
    private fun Element.attr(vararg names: String): String? {
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            if (names.any { it == attribute.nodeName || it == attribute.localName }) return attribute.nodeValue
        }
        return null
    }

    private fun String.parseTime(): Long? {
        val parts = split(':')
        val seconds = parts.lastOrNull()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val minutes = parts.dropLast(1).lastOrNull()?.toLongOrNull() ?: 0L
        val hours = parts.dropLast(2).lastOrNull()?.toLongOrNull() ?: 0L
        return ((hours * 3_600L + minutes * 60L) * 1_000L + seconds * 1_000.0).toLong()
    }

    private fun String.splitTranslation(): Pair<String, String?> {
        if (!endsWith('）')) return trim() to null
        val start = lastIndexOf('（')
        if (start < 0) return trim() to null
        return substring(0, start).trim() to substring(start + 1, lastIndex).trim().takeIf(String::isNotBlank)
    }

    private fun selectTranslation(candidates: List<Element>, script: MeloXLyricScript): SelectedTranslation? {
        if (candidates.isEmpty()) return null
        if (script == MeloXLyricScript.Original) {
            return SelectedTranslation(candidates.first().allText().trim(), exact = false)
        }
        val wanted = if (script == MeloXLyricScript.Traditional) "hant" else "hans"
        val opposite = if (wanted == "hant") "hans" else "hant"
        candidates.firstOrNull { it.languageTag().contains(wanted) }?.let {
            return SelectedTranslation(it.allText().trim(), exact = true)
        }
        val fallback = candidates.firstOrNull { !it.languageTag().contains(opposite) } ?: candidates.first()
        return SelectedTranslation(fallback.allText().trim(), exact = false)
    }

    /** AMLL publishes alternate translations together with an explicit TTML language tag. */
    private fun Element.isChineseTranslation(): Boolean {
        val language = attr("xml:lang", "lang")?.trim()?.lowercase() ?: return false
        return language == "zh" ||
            language == "cmn" ||
            language.startsWith("zh-") ||
            language == "cmn-hans" ||
            language == "cmn-hant"
    }

    private fun Element.languageTag(): String = attr("xml:lang", "lang").orEmpty().trim().lowercase()

    private fun String.decodeEntities(): String = replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&apos;", "'").replace("&quot;", "\"")

    private fun preformat(value: String): String = value.replace("  ", "")
        .replace(" </span><span", "</span> <span").replace(",</span><span", ",</span> <span")
}

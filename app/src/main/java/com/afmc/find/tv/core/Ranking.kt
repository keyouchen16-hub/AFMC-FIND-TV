package com.afmc.find.tv.core

import com.afmc.find.tv.model.SearchHit
import com.afmc.find.tv.model.TorrentResult
import java.text.Normalizer
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Port of the desktop SearchQuality rules: canonicalisation, hard constraints, match tiers and scoring.
 * All patterns are compiled once at class-load time — compiling them per row made ranking of a few hundred
 * rows take seconds on a TV CPU.
 */
object Ranking {
    private val CJK = Regex("[\\u3400-\\u9fff]")
    private val DIGITS = "零一二三四五六七八九"

    private val RE_SEASON_CN = Regex("第([0-9一二三四五六七八九十]+)(季|集)")
    private val RE_SEASON_EN = Regex("\\bseason[ ._-]*(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
    private val RE_SXXEXX = Regex("\\b(s\\d{1,2})(e\\d{1,3})\\b", RegexOption.IGNORE_CASE)
    private val RE_SE = Regex("\\b([se])(\\d{1,3})\\b")
    private val RE_4K = Regex("\\b4k\\b", RegexOption.IGNORE_CASE)
    private val RE_8K = Regex("\\b8k\\b", RegexOption.IGNORE_CASE)
    private val RE_NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private val RE_CONSTRAINT = Regex("\\b(?:(?:19|20)\\d{2}|\\d{3,4}p|s\\d{2,3}|e\\d{2,3})\\b", RegexOption.IGNORE_CASE)
    private val RE_VERSION = Regex("(?<!\\d)\\d{1,4}(?:\\.\\d{1,4})+(?!\\d)")

    private val RE_EXTRA = Regex(
        "\\b(?:addons?|plugins?|tutorials?|courses?|overview|fundamentals|guide|bible|essentials)\\b|教程|课程|插件|指南|入门",
        RegexOption.IGNORE_CASE,
    )
    private val RE_CODE_SUFFIX = Regex("\\b(?:epub|pdf|mobi|flac|mp3|aac|bluray|hevc|x264|x265)\\b", RegexOption.IGNORE_CASE)
    private val RE_TITLE_PREFIX = Regex("^(?:(?:\\[[^\\]]{1,100}\\]|【[^】]{1,100}】)\\s*)+")
    private val RE_URL_PREFIX = Regex("^(?:https?://)?www\\.[^\\s]+\\s*-\\s*", RegexOption.IGNORE_CASE)
    private val RE_SERIES = Regex("\\bs\\d{2,3}\\b")
    private val RE_SERIES_BOUNDARY = Regex("^(?:(?:19|20)\\d{2}|s\\d{2,3})$")
    private val RE_VIDEO_FILE = Regex("\\.(?:mp4|mkv|avi|webm)\\b", RegexOption.IGNORE_CASE)
    private val RE_DIGIT_DOT = Regex("\\d+\\.\\d+")

    private val RE_CAT_RESEARCH = Regex("dataset|科研|数据集|academic")
    private val RE_CAT_BOOK = Regex("e[- ]?books?|audio.?book|书|\\.epub\\b|\\.pdf\\b")
    private val RE_CAT_ANIME = Regex("anime|动漫|动画")
    private val RE_CAT_VIDEO_META = Regex("\\b(?:movies?|film|video|tv)\\b|电影|影视|剧|视频")
    private val RE_CAT_VIDEO_EXT = Regex("\\.(?:mkv|mp4|m4v|avi|mov|webm|m2ts)\\b", RegexOption.IGNORE_CASE)
    private val RE_CAT_AUDIO_TRAP = Regex("\\b(?:dual|multi(?:ple)?)\\s*[- .]?\\s*audio\\b")
    private val RE_CAT_MUSIC = Regex("music|audio|音乐|\\.flac\\b|\\.mp3\\b")
    private val RE_CAT_SOFTWARE = Regex("software|application|软件|linux|ubuntu|\\b(?:debian|blender|libreoffice|gimp)\\b|\\.iso\\b")
    private val RE_CAT_GAME = Regex("game|游戏|console")
    private val RE_CAT_VIDEO = Regex("movie|film|电影|剧|tv|video|视频|\\b(?:1080p|2160p|720p|bluray|web.?dl|s\\d{2})\\b")

    private val RE_TECH = Regex(
        "\\b(?:\\d{3,4}p|[48]k|BluRay|WEB[- .]?DL|x26[45]|H[ .]?26[45]|HEVC|FLAC|MP3|EPUB|PDF|S\\d{1,2}E\\d{1,3})\\b|国语|粤语|中字|双语",
        RegexOption.IGNORE_CASE,
    )

    fun canonical(text: String): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        s = s.replace("中文字幕", "中字")
        s = RE_SEASON_CN.replace(s) { m ->
            val n = chineseNumber(m.groupValues[1])
            if (n >= 0) " " + (if (m.groupValues[2] == "季") "s" else "e") + n.toString().padStart(2, '0') + " "
            else m.value
        }
        s = RE_SEASON_EN.replace(s) { " s" + it.groupValues[1].toInt().toString().padStart(2, '0') + " " }
        s = RE_SXXEXX.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        s = RE_SE.replace(s) { it.groupValues[1] + it.groupValues[2].toInt().toString().padStart(2, '0') }
        s = RE_4K.replace(s, "2160p")
        s = RE_8K.replace(s, "4320p")
        return RE_NON_WORD.replace(s, " ").trim()
    }

    private fun chineseNumber(raw: String): Int {
        if (raw.toIntOrNull() != null) return raw.toInt()
        if (raw == "十") return 10
        if (raw.contains('十')) {
            val parts = raw.split('十')
            val tens = if (parts[0].isEmpty()) 1 else DIGITS.indexOf(parts[0][0])
            val ones = if (parts.size > 1 && parts[1].isNotEmpty()) DIGITS.indexOf(parts[1][0]) else 0
            return tens * 10 + ones
        }
        return if (raw.length == 1) DIGITS.indexOf(raw[0]) else -1
    }

    private fun containsToken(text: String, token: String): Boolean =
        if (CJK.containsMatchIn(token)) text.contains(token)
        else " $text ".contains(" $token ")

    fun constraints(query: String): List<String> {
        val canonical = canonical(query)
        val out = LinkedHashSet<String>()
        RE_CONSTRAINT.findAll(canonical).forEach { out.add(it.value.lowercase()) }
        RE_VERSION.findAll(query).forEach { out.add(canonical(it.value)) }
        listOf("国语", "粤语", "中字", "双语").forEach { if (canonical.contains(it)) out.add(it) }
        return out.toList()
    }

    fun matchesConstraints(query: String, title: String): Boolean {
        val t = canonical(title)
        return constraints(query).all { containsToken(t, it) }
    }

    /** 0 = name and constraints matched, 1 = name matched but constraints missing, 2 = partial, 3 = no name match. */
    fun matchTier(query: String, title: String): Int {
        val q = canonical(query)
        if (q.isEmpty()) return 0
        val cons = constraints(query)
        var name = " $q "
        cons.forEach { name = name.replace(" $it ", " ") }
        val tokens = name.split(' ').filter { it.isNotBlank() }.distinct()
        val t = canonical(title)
        if (tokens.isEmpty()) return if (cons.all { containsToken(t, it) }) 0 else 3
        val matched = tokens.count { containsToken(t, it) }
        if (matched == 0) return 3
        if (matched < tokens.size) return 2
        return if (cons.all { containsToken(t, it) }) 0 else 1
    }

    private fun nameOrderScore(query: String, title: String): Double {
        var name = " " + canonical(query) + " "
        constraints(query).forEach { name = name.replace(" $it ", " ") }
        name = RE_CODE_SUFFIX.replace(name, "")
        val tokens = name.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0

        var clean = RE_TITLE_PREFIX.replace(title, "")
        clean = RE_URL_PREFIX.replace(clean, "")
        val words = canonical(clean).split(' ').filter { it.isNotBlank() }

        var position = -1
        var first = -1
        for (token in tokens) {
            var found = -1
            for (i in position + 1 until words.size) {
                if (words[i] == token) { found = i; break }
            }
            if (found < 0) return if (tokens.size > 1) -12.0 else 0.0
            if (first < 0) first = found
            position = found
        }
        var seriesBonus = 0.0
        if (RE_SERIES.containsMatchIn(canonical(query))) {
            val boundary = words.indexOfFirst { RE_SERIES_BOUNDARY.matches(it) }
            if (boundary > 0 && words.take(boundary) == tokens) seriesBonus = 24.0
        }
        return 12.0 + (if (first == 0) 30.0 else if (first <= 2) 12.0 else 0.0) + seriesBonus
    }

    fun score(query: String, row: TorrentResult, nowSeconds: Long): Double {
        val q = canonical(query)
        val title = canonical(row.title)
        val tokens = q.split(' ').filter { it.isNotBlank() }.distinct()
        if (tokens.isEmpty()) return 0.0
        val coverage = tokens.count { containsToken(title, it) }.toDouble() / tokens.size

        val category = categoryOf(row)
        val tags = techTags(row.title)

        var score = 70.0 * coverage * coverage + if (q == title) 5.0 else 0.0
        score += nameOrderScore(query, row.title)
        if (category != "其他") score += 8.0
        if (tags.isNotEmpty()) score += 8.0
        if (q == title && category == "其他" && tags.isEmpty()) score -= 10.0

        val softwareQuery = categoryOf(TorrentResult(title = query, source = "query")) == "软件"
        if ((softwareQuery || RE_DIGIT_DOT.containsMatchIn(query)) && !RE_EXTRA.containsMatchIn(query)) {
            if (RE_EXTRA.containsMatchIn(row.title)) score -= 30.0
            if (softwareQuery && (category == "书籍" || RE_VIDEO_FILE.containsMatchIn(row.title))) score -= 35.0
        }

        score += min(20.0, log10(1.0 + max(0, row.seeders ?: 0)) * 5.0)
        row.published?.let { date ->
            if (date <= nowSeconds) {
                val days = (nowSeconds - date) / 86400.0
                score += 5.0 / (1.0 + days / 365.0)
            }
        }
        score -= constraints(query).count { !containsToken(title, it) } * 100.0
        return score
    }

    fun categoryOf(row: TorrentResult): String {
        val text = (row.category + " " + row.title).lowercase()
        if (RE_CAT_RESEARCH.containsMatchIn(text)) return "科研"
        if (RE_CAT_BOOK.containsMatchIn(text)) return "书籍"
        if (RE_CAT_ANIME.containsMatchIn(text)) return "动漫"
        if (RE_CAT_VIDEO_META.containsMatchIn(row.category.lowercase()) || RE_CAT_VIDEO_EXT.containsMatchIn(row.title)) {
            return "影视"
        }
        val cleaned = RE_CAT_AUDIO_TRAP.replace(text, "")
        if (RE_CAT_MUSIC.containsMatchIn(cleaned)) return "音乐"
        if (RE_CAT_SOFTWARE.containsMatchIn(cleaned)) return "软件"
        if (RE_CAT_GAME.containsMatchIn(cleaned)) return "游戏"
        if (RE_CAT_VIDEO.containsMatchIn(cleaned)) return "影视"
        return "其他"
    }

    fun techTags(title: String): List<String> =
        RE_TECH.findAll(title).map { it.value }.distinct().take(5).toList()

    fun rankAll(query: String, rows: List<TorrentResult>, nowSeconds: Long = System.currentTimeMillis() / 1000): List<SearchHit> {
        val hits = rows.map { row ->
            SearchHit(row, matchTier(query, row.title), score(query, row, nowSeconds))
        }
        return hits.sortedWith(
            compareBy<SearchHit> { it.tier }
                .thenByDescending { it.score }
                .thenByDescending { it.result.seeders ?: -1 },
        )
    }

    fun dedupe(rows: List<TorrentResult>): List<TorrentResult> {
        val seen = LinkedHashMap<String, TorrentResult>()
        for (row in rows) {
            val key = Links.normalizeHash(row.hash) ?: Links.magnetHash(row.magnet)
                ?: ("title:" + canonical(row.title))
            val previous = seen[key]
            val better = previous == null ||
                (row.seeders ?: -1) > (previous.seeders ?: -1) ||
                (row.seeders == previous.seeders && (row.sizeBytes ?: 0) > (previous.sizeBytes ?: 0))
            if (better) seen[key] = row
        }
        return seen.values.toList()
    }
}

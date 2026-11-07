package com.afmc.find.tv.sources

import com.afmc.find.tv.core.Links
import com.afmc.find.tv.core.SearchSource
import com.afmc.find.tv.model.TorrentResult
import com.afmc.find.tv.net.Http
import com.afmc.find.tv.net.Rss
import com.afmc.find.tv.net.Sizes
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.min

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun isoDate(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    Rss.parseDate(text)?.let { return it }
    return try {
        val cleaned = text.replace("Z", "+00:00")
        val base = if (cleaned.length >= 19) cleaned.substring(0, 19) else return null
        Rss.parseDate(base.replace('T', ' '))?.let { baseSeconds ->
            val offset = Regex("([+-])(\\d{2}):(\\d{2})$").find(cleaned)
            if (offset == null) baseSeconds else {
                val sign = if (offset.groupValues[1] == "-") -1 else 1
                baseSeconds - sign * (offset.groupValues[2].toLong() * 3600 + offset.groupValues[3].toLong() * 60)
            }
        }
    } catch (_: Exception) {
        null
    }
}

class KnabenSource : SearchSource {
    override val id = "knaben"
    override val name = "Knaben"

    override fun search(query: String): List<TorrentResult> {
        val body = Http.get("https://api.knaben.org/v2/search?q=${enc(query)}&s=100&f=0")
        val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
        val out = ArrayList<TorrentResult>()
        for (i in 0 until min(hits.length(), 150)) {
            val item = hits.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isEmpty()) continue
            val hash = item.optString("hash").takeIf { it.isNotBlank() }
            val magnet = item.optString("magnetUrl").takeIf { it.startsWith("magnet:", true) }
                ?: Links.magnetFor(hash, title) ?: continue
            val origin = item.optString("tracker").trim()
            out.add(
                TorrentResult(
                    title = title,
                    source = if (origin.isBlank()) name else "Knaben · $origin",
                    magnet = magnet,
                    hash = hash ?: Links.magnetHash(magnet),
                    sizeBytes = item.optLong("bytes", -1).takeIf { it > 0 },
                    seeders = (item.optInt("seeders", -1)).takeIf { it >= 0 },
                    category = item.optString("category"),
                    published = isoDate(item.optString("date")),
                    detailsUrl = item.optString("details").takeIf { it.startsWith("http") },
                ),
            )
        }
        return out
    }
}

class BitSearchSource : SearchSource {
    override val id = "bitsearch"
    override val name = "BitSearch"

    private fun category(code: Int): String = when (code) {
        1 -> "其他"
        2 -> "电影"
        3 -> "剧集"
        4 -> "动漫"
        5 -> "软件"
        6 -> "游戏"
        7 -> "音乐"
        8 -> "有声书"
        9 -> "电子书 / 课程"
        10 -> "其他"
        else -> "其他"
    }

    override fun search(query: String): List<TorrentResult> {
        val body = Http.get("https://bitsearch.eu/api/v1/search?q=${enc(query)}&limit=100&sort=relevance&page=1")
        val root = JSONObject(body)
        if (!root.optBoolean("success", true)) return emptyList()
        val rows = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<TorrentResult>()
        for (i in 0 until min(rows.length(), 100)) {
            val item = rows.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isEmpty()) continue
            val hash = item.optString("infohash").takeIf { it.isNotBlank() }
            val magnet = item.optString("magnetUrl").takeIf { it.startsWith("magnet:", true) }
                ?: Links.magnetFor(hash, title) ?: continue
            out.add(
                TorrentResult(
                    title = title,
                    source = name,
                    magnet = magnet,
                    hash = hash ?: Links.magnetHash(magnet),
                    sizeBytes = item.optLong("size", -1).takeIf { it > 0 },
                    seeders = item.optInt("seeders", -1).takeIf { it >= 0 },
                    category = category(item.optInt("category", 0)),
                    published = isoDate(item.optString("createdAt")),
                    detailsUrl = item.optString("id").takeIf { it.isNotBlank() }
                        ?.let { "https://bitsearch.eu/torrent/$it" },
                ),
            )
        }
        return out
    }
}

class PirateBaySource : SearchSource {
    override val id = "piratebay"
    override val name = "海盗湾"

    private fun category(code: Int): String = when (code / 100) {
        1 -> "音频"
        2 -> "视频"
        3 -> "软件"
        4 -> "游戏"
        5 -> "其他"
        6 -> "其他"
        else -> "其他"
    }

    override fun search(query: String): List<TorrentResult> {
        val body = Http.get("https://apibay.org/q.php?q=${enc(query)}")
        val rows = JSONArray(body)
        if (rows.length() == 1 && rows.optJSONObject(0)?.optString("name") == "No results returned") {
            return emptyList()
        }
        val out = ArrayList<TorrentResult>()
        for (i in 0 until min(rows.length(), 100)) {
            val item = rows.optJSONObject(i) ?: continue
            val title = item.optString("name").trim()
            val hash = item.optString("info_hash").takeIf { it.isNotBlank() } ?: continue
            if (title.isEmpty()) continue
            val id = item.optString("id")
            out.add(
                TorrentResult(
                    title = title,
                    source = name,
                    magnet = Links.magnetFor(hash, title),
                    hash = hash,
                    sizeBytes = item.optString("size").toLongOrNull()?.takeIf { it > 0 },
                    seeders = item.optString("seeders").toIntOrNull(),
                    category = category(item.optString("category").toIntOrNull() ?: 0),
                    published = item.optString("added").toLongOrNull()?.takeIf { it > 0 },
                    detailsUrl = if (id.isNotBlank()) "https://thepiratebay.org/description.php?id=$id" else null,
                ),
            )
        }
        return out
    }
}

class NyaaSource : SearchSource {
    override val id = "nyaa"
    override val name = "Nyaa"

    override fun search(query: String): List<TorrentResult> {
        val xml = Http.get("https://nyaa.si/?page=rss&q=${enc(query)}&c=0_0&f=0")
        return Rss.items(xml).mapNotNull { item ->
            val title = item.title.trim()
            if (title.isEmpty()) return@mapNotNull null
            val hash = item.raw["nyaa:infohash"]?.trim()?.takeIf { it.length == 40 }
            val magnetFromLink = item.link?.takeIf { it.startsWith("magnet:", true) }
                ?: item.enclosure?.takeIf { it.startsWith("magnet:", true) }
            val magnet = magnetFromLink ?: Links.magnetFor(hash, title) ?: return@mapNotNull null
            TorrentResult(
                title = title,
                source = name,
                magnet = magnet,
                hash = hash ?: Links.magnetHash(magnet),
                sizeBytes = Sizes.parse(item.raw["nyaa:size"]),
                seeders = item.raw["nyaa:seeders"]?.toIntOrNull(),
                category = item.raw["nyaa:category"] ?: "动漫",
                published = item.pubDate,
                detailsUrl = item.guid ?: item.link,
            )
        }
    }
}

class DmhySource : SearchSource {
    override val id = "dmhy"
    override val name = "动漫花园"

    private fun sizeOf(item: com.afmc.find.tv.net.RssItem): Long? {
        Sizes.parse(item.description)?.let { return it }
        val bracket = Regex("\\[([\\d.]+\\s*[GMT]i?B)\\]", RegexOption.IGNORE_CASE).find(item.title) ?: return null
        return Sizes.parse(bracket.groupValues[1])
    }

    override fun search(query: String): List<TorrentResult> {
        val xml = Http.get("https://share.dmhy.org/topics/rss/rss.xml?keyword=${enc(query)}")
        return Rss.items(xml).mapNotNull { item ->
            val title = item.title.trim()
            if (title.isEmpty()) return@mapNotNull null
            val magnet = listOfNotNull(item.enclosure, item.link)
                .firstOrNull { it.startsWith("magnet:", true) } ?: return@mapNotNull null
            val hash = Links.magnetHash(magnet) ?: return@mapNotNull null
            TorrentResult(
                title = title,
                source = name,
                magnet = magnet,
                hash = hash,
                sizeBytes = sizeOf(item),
                seeders = null,
                category = "动漫",
                published = item.pubDate,
                detailsUrl = item.link?.takeIf { it.startsWith("http") },
            )
        }
    }
}

class TorrentsCsvSource : SearchSource {
    override val id = "torrentscsv"
    override val name = "Torrents.csv"

    override fun search(query: String): List<TorrentResult> {
        val body = Http.get("https://torrents-csv.com/service/search?q=${enc(query)}&size=100")
        val rows = JSONObject(body).optJSONArray("torrents") ?: return emptyList()
        val out = ArrayList<TorrentResult>()
        for (i in 0 until min(rows.length(), 100)) {
            val item = rows.optJSONObject(i) ?: continue
            val title = item.optString("name").trim()
            val hash = item.optString("infohash").trim().takeIf { it.isNotBlank() } ?: continue
            if (title.isEmpty()) continue
            out.add(
                TorrentResult(
                    title = title,
                    source = name,
                    magnet = Links.magnetFor(hash, title),
                    hash = hash,
                    sizeBytes = item.optLong("size_bytes", -1).takeIf { it > 0 },
                    seeders = item.optInt("seeders", -1).takeIf { it >= 0 },
                    category = "其他",
                    published = item.optLong("created_unix", -1).takeIf { it > 0 },
                ),
            )
        }
        return out
    }
}

class TorznabSource(
    override val id: String = "torznab",
    override val name: String = "Torznab",
    private val endpoint: String,
    private val apiKey: String,
) : SearchSource {
    override fun search(query: String): List<TorrentResult> {
        val base = endpoint.trim().trimEnd('?', '&')
        val separator = if (base.contains('?')) '&' else '?'
        val url = "$base${separator}t=search&q=${enc(query)}&limit=100" +
            if (apiKey.isBlank()) "" else "&apikey=${enc(apiKey)}"
        val xml = Http.get(url)
        return Rss.items(xml).mapNotNull { item ->
            val title = (item.raw["title"] ?: item.title).trim()
            if (title.isEmpty()) return@mapNotNull null
            val hash = item.attrs["infohash"]?.trim()
                ?: item.raw["torznab:infohash"]?.trim()
                ?: item.link?.let { Links.magnetHash(it) }?.substringAfter(':')?.take(40)
            val magnetFromLink = item.link?.takeIf { it.startsWith("magnet:", true) }
                ?: item.enclosure?.takeIf { it.startsWith("magnet:", true) }
            val magnet = magnetFromLink ?: Links.magnetFor(hash, title) ?: return@mapNotNull null
            TorrentResult(
                title = title,
                source = name,
                magnet = magnet,
                hash = hash ?: Links.magnetHash(magnet),
                sizeBytes = item.attrs["size"]?.toLongOrNull()
                    ?: Sizes.parse(item.raw["size"]),
                seeders = (item.attrs["seeders"] ?: item.raw["torznab:seeders"])?.toIntOrNull(),
                category = item.raw["category"] ?: "其他",
                published = item.pubDate,
                detailsUrl = item.link?.takeIf { it.startsWith("http") },
            )
        }
    }
}

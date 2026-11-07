package com.afmc.find.tv.net

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class RssItem(
    val title: String,
    val link: String?,
    val guid: String?,
    val description: String?,
    val pubDate: Long?,
    val enclosure: String?,
    val attrs: Map<String, String>,
    val raw: Map<String, String>,
)

object Rss {
    private val DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    fun parseDate(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        for (pattern in DATE_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = true
                if (pattern.endsWith("'Z'")) format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(text) ?: continue
                return date.time / 1000
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    fun items(xml: String): List<RssItem> {
        val parser = try {
            XmlPullParserFactory.newInstance().newPullParser()
        } catch (e: Exception) {
            return emptyList()
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
        } catch (e: Exception) {
            return emptyList()
        }

        val out = ArrayList<RssItem>()
        var event = runCatching { parser.eventType }.getOrDefault(XmlPullParser.END_DOCUMENT)
        var current: HashMap<String, String>? = null
        var itemAttrs = HashMap<String, String>()
        var enclosure: String? = null
        var textTag: String? = null
        var buffer = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    when (name) {
                        "item", "entry" -> {
                            current = HashMap()
                            itemAttrs = HashMap()
                            enclosure = null
                        }
                        else -> {
                            if (current != null) {
                                if (name.endsWith("attr")) {
                                    val attrName = parser.getAttributeValue(null, "name")
                                    val attrValue = parser.getAttributeValue(null, "value")
                                    if (!attrName.isNullOrEmpty() && attrValue != null) {
                                        itemAttrs[attrName.lowercase()] = attrValue
                                    }
                                }
                                if (name == "enclosure") {
                                    enclosure = parser.getAttributeValue(null, "url")
                                }
                                textTag = name
                                buffer = StringBuilder()
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (current != null && textTag != null) buffer.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    val map = current
                    if (map != null && textTag == name) {
                        val value = buffer.toString().trim()
                        if (value.isNotEmpty()) map.putIfAbsent(name, value)
                        textTag = null
                    }
                    if (name == "item" || name == "entry") {
                        if (map != null) {
                            out.add(
                                RssItem(
                                    title = map["title"] ?: "",
                                    link = map["link"],
                                    guid = map["guid"] ?: map["id"],
                                    description = map["description"] ?: map["summary"],
                                    pubDate = parseDate(map["pubdate"] ?: map["published"] ?: map["updated"]),
                                    enclosure = enclosure,
                                    attrs = itemAttrs,
                                    raw = map,
                                ),
                            )
                        }
                        current = null
                    }
                }
            }
            event = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
        }
        return out
    }
}

object Sizes {
    private val RE = Regex("([\\d.]+)\\s*(Ti?B|Gi?B|Mi?B|Ki?B|B)", RegexOption.IGNORE_CASE)

    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val match = RE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        if (value < 0) return null
        val factor = when (match.groupValues[2].uppercase().replace("I", "")) {
            "TB" -> Math.pow(1024.0, 4.0)
            "GB" -> Math.pow(1024.0, 3.0)
            "MB" -> Math.pow(1024.0, 2.0)
            "KB" -> 1024.0
            else -> 1.0
        }
        val bytes = value * factor
        return if (bytes >= Long.MAX_VALUE.toDouble()) null else bytes.toLong()
    }
}

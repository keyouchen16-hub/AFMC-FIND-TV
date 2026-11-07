package com.afmc.find.tv.core

import com.afmc.find.tv.model.TorrentResult

data class SourceInfo(
    val id: String,
    val name: String,
    val description: String,
    val defaultEnabled: Boolean = true,
)

/** Mirrors SiteSources.Catalog, trimmed to sources the TV build can reach without a browser. */
object SourceCatalog {
    val builtIn = listOf(
        SourceInfo("knaben", "Knaben", "综合聚合 · 官方 API v2 · 显示原始索引来源"),
        SourceInfo("bitsearch", "BitSearch", "综合搜索 · 官方 API · 免费额度 200 请求/IP/天"),
        SourceInfo("piratebay", "海盗湾", "APIBay 直连 · 标题/类别/大小/做种/时间 · 按哈希去重"),
        SourceInfo("nyaa", "Nyaa", "动漫、音乐及东亚资源 · 官方 RSS · 单次最多 75 条"),
        SourceInfo("dmhy", "动漫花园", "中文动漫、音乐等 · 关键词 RSS · 做种/大小未知不估算"),
        SourceInfo("torrentscsv", "Torrents.csv", "综合资源 · 公开 API · 游标翻页，最多 3 页 · 5 分钟缓存"),
        SourceInfo("torznab", "Torznab / Jackett", "自建索引器 · 填写 API 地址与 Key 后参与聚合", defaultEnabled = false),
    )

    fun byId(id: String): SourceInfo? = builtIn.firstOrNull { it.id == id }

    val defaultEnabled: Set<String> = builtIn.filter { it.defaultEnabled }.map { it.id }.toSet()
}

/** A source run. Implementations must be blocking; the engine dispatches them on IO threads. */
interface SearchSource {
    val id: String
    val name: String
    fun search(query: String): List<TorrentResult>
}

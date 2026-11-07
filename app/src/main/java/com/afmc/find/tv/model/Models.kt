package com.afmc.find.tv.model

/** One aggregated search result. Mirrors the desktop AFMC-FIND SearchResult, minus playback state. */
data class TorrentResult(
    val title: String,
    val source: String,
    val magnet: String? = null,
    val hash: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val category: String = "其他",
    val published: Long? = null,
    val detailsUrl: String? = null,
) {
    val sizeText: String
        get() {
            val n = sizeBytes ?: return "未知"
            return when {
                n >= 1024L * 1024 * 1024 -> String.format("%.2f GB", n / 1024.0 / 1024 / 1024)
                n >= 1024L * 1024 -> String.format("%.2f MB", n / 1024.0 / 1024)
                else -> "$n B"
            }
        }

    val seedsText: String get() = seeders?.toString() ?: "未知"
}

data class SearchHit(
    val result: TorrentResult,
    val tier: Int,
    val score: Double,
)

data class SourceOutcome(
    val name: String,
    val count: Int,
    val error: String?,
)

object Categories {
    const val ALL = "全部"
    val filters = listOf(ALL, "影视", "动漫", "音乐", "软件", "游戏", "书籍", "科研", "其他")
}

object SortModes {
    const val RELEVANCE = "相关度"
    const val SEEDERS = "做种"
    const val SIZE = "大小"
    const val DATE = "时间"
    val all = listOf(RELEVANCE, SEEDERS, SIZE, DATE)
}

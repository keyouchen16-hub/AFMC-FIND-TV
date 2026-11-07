package com.afmc.find.tv.core

import android.content.Context
import com.afmc.find.tv.model.SearchHit
import com.afmc.find.tv.model.SourceOutcome
import com.afmc.find.tv.model.TorrentResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Collections

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("afmc_find_tv", Context.MODE_PRIVATE)

    init {
        // 1.3.0 起 Nyaa 默认关闭：老安装一次性从启用列表移除，用户仍可在设置里重新打开。
        if (sp.getInt("defaults_version", 0) < 2) {
            val current = sp.getStringSet("enabled_sources", null)
            if (current != null) {
                sp.edit().putStringSet("enabled_sources", current.toMutableSet().apply { remove("nyaa") }).apply()
            }
            sp.edit().putInt("defaults_version", 2).apply()
        }
    }

    var enabledSources: Set<String>
        get() = sp.getStringSet("enabled_sources", null) ?: SourceCatalog.defaultEnabled
        set(value) = sp.edit().putStringSet("enabled_sources", value).apply()

    var torznabUrl: String
        get() = sp.getString("torznab_url", "") ?: ""
        set(value) = sp.edit().putString("torznab_url", value.trim()).apply()

    var torznabKey: String
        get() = sp.getString("torznab_key", "") ?: ""
        set(value) = sp.edit().putString("torznab_key", value.trim()).apply()

    fun isEnabled(id: String): Boolean = enabledSources.contains(id)

    fun setEnabled(id: String, enabled: Boolean) {
        val next = enabledSources.toMutableSet()
        if (enabled) next.add(id) else next.remove(id)
        enabledSources = next
    }

    fun buildSources(): List<SearchSource> {
        val enabled = enabledSources
        val out = ArrayList<SearchSource>()
        if ("knaben" in enabled) out.add(com.afmc.find.tv.sources.KnabenSource())
        if ("bitsearch" in enabled) out.add(com.afmc.find.tv.sources.BitSearchSource())
        if ("piratebay" in enabled) out.add(com.afmc.find.tv.sources.PirateBaySource())
        if ("nyaa" in enabled) out.add(com.afmc.find.tv.sources.NyaaSource())
        if ("dmhy" in enabled) out.add(com.afmc.find.tv.sources.DmhySource())
        if ("torrentscsv" in enabled) out.add(com.afmc.find.tv.sources.TorrentsCsvSource())
        val url = torznabUrl
        if ("torznab" in enabled && url.isNotBlank()) {
            out.add(com.afmc.find.tv.sources.TorznabSource(endpoint = url, apiKey = torznabKey))
        }
        return out
    }
}

/**
 * Runs every enabled source in parallel and streams ranked partial results as soon as a source answers,
 * so the first rows appear in roughly the fastest source's latency instead of the slowest one's.
 */
class SearchEngine(private val prefs: Prefs) {

    suspend fun search(
        query: String,
        onProgress: suspend (SourceOutcome, List<SearchHit>) -> Unit = { _, _ -> },
    ): List<SearchHit> = coroutineScope {
        val sources = prefs.buildSources()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        pruneCache()
        val collected: MutableList<TorrentResult> = Collections.synchronizedList(ArrayList())
        fun snapshot(): List<TorrentResult> = synchronized(collected) { collected.toList() }

        val started = System.currentTimeMillis()
        val jobs = sources.map { source ->
            async(Dispatchers.IO) {
                val sourceStarted = System.currentTimeMillis()
                val key = query + "|" + source.id
                val cached = cache[key]?.takeIf { System.currentTimeMillis() - it.at < CACHE_TTL_MS }
                val cooldown = synchronized(health) { health[source.id] }
                if (cached == null && cooldown != null && cooldown.blockedUntil > sourceStarted) {
                    // 刚刚失败过的源先跳过，避免每次搜索都为同一个死链等超时。
                    android.util.Log.i("AFMC-FIND", "${source.name}: skipped (cooldown)")
                    val outcome = SourceOutcome(source.name, 0, "跳过")
                    withContext(Dispatchers.Main) { onProgress(outcome, Ranking.rankAll(query, Ranking.dedupe(snapshot()))) }
                    return@async emptyList()
                }

                var rows: List<TorrentResult> = emptyList()
                var outcome = SourceOutcome(source.name, 0, null)
                if (cached != null) {
                    rows = cached.rows
                    outcome = SourceOutcome(source.name, rows.size, null)
                } else {
                    try {
                        rows = withTimeout(SOURCE_TIMEOUT_MS) { source.search(query) }
                        outcome = SourceOutcome(source.name, rows.size, null)
                        synchronized(cache) { cache[key] = CacheEntry(rows, System.currentTimeMillis()) }
                        synchronized(health) { health.remove(source.id) }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        outcome = SourceOutcome(source.name, 0, describe(t))
                        val failures = synchronized(health) {
                            val next = (health[source.id]?.failures ?: 0) + 1
                            val cooldownMs = when {
                                next <= 1 -> 2 * 60_000L
                                next == 2 -> 10 * 60_000L
                                else -> 30 * 60_000L
                            }
                            health[source.id] = Health(next, System.currentTimeMillis() + cooldownMs)
                            next
                        }
                        android.util.Log.w("AFMC-FIND", "source ${source.name} failed ($failures): ${t.javaClass.simpleName} ${t.message}")
                    }
                }

                android.util.Log.i(
                    "AFMC-FIND",
                    "${source.name}: ${rows.size} rows in ${System.currentTimeMillis() - sourceStarted}ms" +
                        if (cached != null) " (cached)" else "",
                )

                collected.addAll(rows)
                val partial = withContext(Dispatchers.Default) {
                    Ranking.rankAll(query, Ranking.dedupe(snapshot()))
                }
                withContext(Dispatchers.Main) { onProgress(outcome, partial) }
                rows
            }
        }

        jobs.awaitAll()
        android.util.Log.i(
            "AFMC-FIND",
            "search '$query' finished in ${System.currentTimeMillis() - started}ms, ${snapshot().size} raw rows",
        )
        withContext(Dispatchers.Default) { Ranking.rankAll(query, Ranking.dedupe(snapshot())) }
    }

    private fun pruneCache() {
        val now = System.currentTimeMillis()
        synchronized(cache) { cache.entries.removeAll { now - it.value.at > CACHE_TTL_MS } }
    }

    private fun describe(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException -> "连接超时"
        is java.net.UnknownHostException -> "域名无法解析"
        is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "网络请求失败"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "搜索失败"
    }

    /**
     * 启动时探测各源连通性：不可达的源先冷却，后续搜索不必再为同一个死链等超时。
     */
    suspend fun warmUp() = coroutineScope {
        prefs.buildSources().forEach { source ->
            launch(Dispatchers.IO) {
                // 只建立 TCP/TLS 连接，不用探测结果判定来源死活（HEAD 对慢站不可靠）。
                runCatching { com.afmc.find.tv.net.Http.head(source.host) }
            }
        }
    }

    private companion object {
        const val SOURCE_TIMEOUT_MS = 10_000L
        const val CACHE_TTL_MS = 5 * 60_000L

        data class CacheEntry(val rows: List<TorrentResult>, val at: Long)
        data class Health(val failures: Int, val blockedUntil: Long)

        val cache = HashMap<String, CacheEntry>()
        val health = HashMap<String, Health>()
    }
}

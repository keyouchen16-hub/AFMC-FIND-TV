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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("afmc_find_tv", Context.MODE_PRIVATE)

    var enabledSources: Set<String>
        get() = sp.getStringSet("enabled_sources", null) ?: SourceCatalog.defaultEnabled
        set(value) = sp.edit().putStringSet("enabled_sources", value).apply()

    var torznabUrl: String
        get() = sp.getString("torznab_url", "") ?: ""
        set(value) = sp.edit().putString("torznab_url", value.trim()).apply()

    var torznabKey: String
        get() = sp.getString("torznab_key", "") ?: ""
        set(value) = sp.edit().putString("torznab_key", value.trim()).apply()

    var lastQuery: String
        get() = sp.getString("last_query", "") ?: ""
        set(value) = sp.edit().putString("last_query", value).apply()

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

/** Runs every enabled source in parallel, then de-duplicates and ranks. */
class SearchEngine(private val prefs: Prefs) {

    suspend fun search(
        query: String,
        onOutcome: (SourceOutcome) -> Unit = {},
    ): List<SearchHit> = coroutineScope {
        val sources = prefs.buildSources()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        val jobs = sources.map { source ->
            async(Dispatchers.IO) {
                try {
                    val rows = withTimeout(25_000) { source.search(query) }
                    withContext(Dispatchers.Main) { onOutcome(SourceOutcome(source.name, rows.size, null)) }
                    rows
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    android.util.Log.w("AFMC-FIND", "source ${source.name} failed: ${t.javaClass.simpleName} ${t.message}")
                    withContext(Dispatchers.Main) { onOutcome(SourceOutcome(source.name, 0, describe(t))) }
                    emptyList()
                }
            }
        }

        val collected: List<TorrentResult> = jobs.awaitAll().flatten()
        Ranking.rankAll(query, Ranking.dedupe(collected))
    }

    private fun describe(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException -> "连接超时"
        is java.net.UnknownHostException -> "域名无法解析"
        is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "网络请求失败"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "搜索失败"
    }
}

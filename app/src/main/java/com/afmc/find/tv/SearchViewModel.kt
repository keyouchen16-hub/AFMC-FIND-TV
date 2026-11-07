package com.afmc.find.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afmc.find.tv.core.Prefs
import com.afmc.find.tv.core.Ranking
import com.afmc.find.tv.core.SearchEngine
import com.afmc.find.tv.model.Categories
import com.afmc.find.tv.model.SearchHit
import com.afmc.find.tv.model.SortModes
import com.afmc.find.tv.model.SourceOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val engine = SearchEngine(prefs)

    var query by mutableStateOf(prefs.lastQuery)
        private set
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var outcomes by mutableStateOf<List<SourceOutcome>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var hasSearched by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var filterCategory by mutableStateOf(Categories.ALL)
        private set
    var seededOnly by mutableStateOf(false)
        private set
    var sortMode by mutableStateOf(SortModes.RELEVANCE)
        private set

    var enabledSources by mutableStateOf(prefs.enabledSources)
        private set
    var torznabUrl by mutableStateOf(prefs.torznabUrl)
        private set
    var torznabKey by mutableStateOf(prefs.torznabKey)
        private set

    private var job: Job? = null

    fun search(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        job?.cancel()
        query = text
        prefs.lastQuery = text
        hits = emptyList()
        outcomes = emptyList()
        errorMessage = null
        running = true
        hasSearched = true
        filterCategory = Categories.ALL
        seededOnly = false
        sortMode = SortModes.RELEVANCE

        job = viewModelScope.launch {
            try {
                val found = engine.search(text) { outcome -> outcomes = outcomes + outcome }
                hits = found
                if (found.isEmpty()) errorMessage = "没有找到匹配结果，换个关键词试试"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                errorMessage = t.message ?: "搜索失败"
            } finally {
                running = false
            }
        }
    }

    fun visibleHits(): List<SearchHit> {
        var list = hits.filter { hit ->
            (filterCategory == Categories.ALL || Ranking.categoryOf(hit.result) == filterCategory) &&
                (!seededOnly || (hit.result.seeders ?: 0) > 0)
        }
        list = when (sortMode) {
            SortModes.SEEDERS -> list.sortedWith(
                compareByDescending<SearchHit> { it.result.seeders ?: -1 }.thenBy { it.tier },
            )
            SortModes.SIZE -> list.sortedWith(
                compareByDescending<SearchHit> { it.result.sizeBytes ?: -1 }.thenBy { it.tier },
            )
            SortModes.DATE -> list.sortedWith(
                compareByDescending<SearchHit> { it.result.published ?: 0 }.thenBy { it.tier },
            )
            else -> list
        }
        return list
    }

    fun setCategory(category: String) { filterCategory = category }
    fun toggleSeeded() { seededOnly = !seededOnly }

    fun cycleSort() {
        val next = (SortModes.all.indexOf(sortMode) + 1) % SortModes.all.size
        sortMode = SortModes.all[next]
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        prefs.setEnabled(id, enabled)
        enabledSources = prefs.enabledSources
    }

    fun isSourceEnabled(id: String) = enabledSources.contains(id)

    fun updateTorznabUrl(url: String) {
        prefs.torznabUrl = url
        torznabUrl = prefs.torznabUrl
    }

    fun updateTorznabKey(key: String) {
        prefs.torznabKey = key
        torznabKey = prefs.torznabKey
    }
}

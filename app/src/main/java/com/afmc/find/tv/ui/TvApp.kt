package com.afmc.find.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afmc.find.tv.R
import com.afmc.find.tv.SearchViewModel
import com.afmc.find.tv.core.Ranking
import com.afmc.find.tv.model.Categories
import com.afmc.find.tv.model.SearchHit
import com.afmc.find.tv.model.SortModes
import com.afmc.find.tv.model.SourceOutcome
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface InputTarget {
    data object None : InputTarget
    data object Query : InputTarget
    data object TorznabUrl : InputTarget
    data object TorznabKey : InputTarget
}

@Composable
fun TvApp() {
    val vm: SearchViewModel = viewModel()
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<SearchHit?>(null) }
    var inputTarget by remember { mutableStateOf<InputTarget>(InputTarget.None) }
    var suppressSearchClickUntil by remember { mutableStateOf(0L) }

    fun refocusSearch() {
        scope.launch { kotlinx.coroutines.delay(120); runCatching { searchFocus.requestFocus() } }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { searchFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 26.dp)) {
            Header(
                query = vm.query,
                searchFocus = searchFocus,
                onSearchClick = {
                    if (System.currentTimeMillis() >= suppressSearchClickUntil) {
                        inputTarget = InputTarget.Query
                    }
                },
                onSettingsClick = { showSettings = true },
            )
            Spacer(Modifier.height(14.dp))
            StatusRow(vm)
            if (vm.hasSearched) {
                Spacer(Modifier.height(10.dp))
                FilterRow(vm)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f)) {
                when {
                    vm.running && vm.hits.isEmpty() -> LoadingState(vm)
                    vm.hits.isEmpty() -> EmptyState(vm)
                    else -> ResultList(vm) { detail = it }
                }
            }
            Footer()
        }

        detail?.let { hit ->
            DetailOverlay(
                hit = hit,
                onClose = {
                    detail = null
                    refocusSearch()
                },
            )
        }

        if (showSettings) {
            SettingsOverlay(
                vm = vm,
                onEditUrl = { inputTarget = InputTarget.TorznabUrl },
                onEditKey = { inputTarget = InputTarget.TorznabKey },
                onClose = {
                    showSettings = false
                    refocusSearch()
                },
            )
        }

        when (inputTarget) {
            is InputTarget.Query -> InputOverlay(
                title = "搜索关键词",
                initial = vm.query,
                hint = "例如：流浪地球 2、老友记、Blender",
                confirmLabel = "搜索",
                onConfirm = { text ->
                    suppressSearchClickUntil = System.currentTimeMillis() + 800
                    inputTarget = InputTarget.None
                    vm.search(text)
                },
                onCancel = { inputTarget = InputTarget.None },
            )
            is InputTarget.TorznabUrl -> InputOverlay(
                title = "Torznab API 地址",
                initial = vm.prefs.torznabUrl,
                hint = "例如：http://192.168.1.10:9117/api/v2.0/indexers/all/results/torznab",
                confirmLabel = "保存",
                onConfirm = { text ->
                    vm.updateTorznabUrl(text)
                    inputTarget = InputTarget.None
                },
                onCancel = { inputTarget = InputTarget.None },
            )
            is InputTarget.TorznabKey -> InputOverlay(
                title = "Torznab API Key",
                initial = vm.prefs.torznabKey,
                hint = "留空表示索引器无需 Key",
                confirmLabel = "保存",
                onConfirm = { text ->
                    vm.updateTorznabKey(text)
                    inputTarget = InputTarget.None
                },
                onCancel = { inputTarget = InputTarget.None },
            )
            InputTarget.None -> Unit
        }
    }
}

@Composable
private fun Header(
    query: String,
    searchFocus: FocusRequester,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = "AFMC FIND",
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "AFMC FIND",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = TextPrimary,
            )
            Text("Find the You.", fontSize = 12.sp, color = TextDim, letterSpacing = 1.sp)
        }

        Spacer(Modifier.width(32.dp))

        TvSurface(
            onClick = onSearchClick,
            modifier = Modifier.weight(1f).focusRequester(searchFocus),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
        ) { focused ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (focused) Accent else TextMuted,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = query.ifBlank { "搜索影片、剧集、动漫、软件……" },
                    fontSize = 20.sp,
                    color = if (query.isBlank()) TextMuted else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        ActionButton("设置", onSettingsClick)
    }
}

@Composable
private fun StatusRow(vm: SearchViewModel) {
    val enabledCount = vm.prefs.enabledSources.size
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (vm.running && vm.outcomes.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Accent,
                strokeWidth = 2.dp,
            )
            Text("正在向 $enabledCount 个来源发起搜索…", fontSize = 14.sp, color = TextMuted)
        }
        vm.outcomes.forEach { outcome -> OutcomeLabel(outcome) }
        if (vm.running && vm.outcomes.isNotEmpty()) {
            Text("搜索中…", fontSize = 14.sp, color = TextDim)
        }
    }
}

@Composable
private fun OutcomeLabel(outcome: SourceOutcome) {
    val text = if (outcome.error == null) "${outcome.name} ${outcome.count}" else "${outcome.name} 失败"
    val color = when {
        outcome.error != null -> Danger
        outcome.count > 0 -> Success
        else -> TextDim
    }
    Text(text, fontSize = 14.sp, color = color)
}

@Composable
private fun FilterRow(vm: SearchViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Categories.filters.forEach { category ->
                Pill(
                    text = category,
                    selected = vm.filterCategory == category,
                    onClick = { vm.setCategory(category) },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Pill("仅做种", vm.seededOnly, onClick = { vm.toggleSeeded() })
        Spacer(Modifier.width(8.dp))
        Pill("排序 · ${vm.sortMode}", false, onClick = { vm.cycleSort() })
    }
}

@Composable
private fun ResultList(vm: SearchViewModel, onOpen: (SearchHit) -> Unit) {
    val visible = vm.visibleHits()
    if (visible.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("当前筛选下没有结果", fontSize = 18.sp, color = TextMuted)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(visible) { _, hit -> ResultCard(hit) { onOpen(hit) } }
    }
}

@Composable
private fun ResultCard(hit: SearchHit, onClick: () -> Unit) {
    val r = hit.result
    val category = Ranking.categoryOf(r)
    val tags = Ranking.techTags(r.title)
    val date = formatDate(r.published)

    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (focused) AccentSoft else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.source, fontSize = 13.sp, color = AccentSoft.copy(alpha = 0.85f))
                    Dot()
                    Text(r.sizeText, fontSize = 13.sp, color = TextMuted)
                    Dot()
                    Text(
                        "做种 ${r.seedsText}",
                        fontSize = 13.sp,
                        color = if ((r.seeders ?: 0) > 0) Success else TextMuted,
                    )
                    Dot()
                    Text(category, fontSize = 13.sp, color = TextMuted)
                    if (date.isNotEmpty()) {
                        Dot()
                        Text(date, fontSize = 13.sp, color = TextDim)
                    }
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Text(
                    tags.joinToString(" · "),
                    fontSize = 12.sp,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(vm: SearchViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
            Spacer(Modifier.height(18.dp))
            Text("正在聚合搜索「${vm.query}」", fontSize = 18.sp, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("多源并行查询，命中后自动去重排序", fontSize = 14.sp, color = TextDim)
        }
    }
}

@Composable
private fun EmptyState(vm: SearchViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(720.dp)) {
            Text(
                if (!vm.hasSearched) "搜索一次，聚合所有来源" else (vm.errorMessage ?: "没有匹配结果"),
                fontSize = 22.sp,
                color = if (vm.hasSearched && vm.errorMessage != null) Danger else TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (!vm.hasSearched) {
                    "按「设置」可以启用 / 停用搜索源，或接入自建 Torznab 索引器。搜到结果后按 OK 查看磁力、二维码与详情。"
                } else {
                    "试试更短的片名、加年份或分辨率，或换一个搜索源。"
                },
                fontSize = 15.sp,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "方向键移动 · OK 确认 · 返回键关闭",
            fontSize = 12.sp,
            color = TextDim,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "本机不存储资源索引 · 仅聚合公开接口",
            fontSize = 12.sp,
            color = TextDim,
        )
    }
}

fun formatDate(epoch: Long?): String {
    if (epoch == null || epoch <= 0) return ""
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epoch * 1000))
    } catch (_: Exception) {
        ""
    }
}

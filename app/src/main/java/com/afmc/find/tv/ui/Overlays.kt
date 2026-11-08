package com.afmc.find.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afmc.find.tv.SearchViewModel
import com.afmc.find.tv.core.Ranking
import com.afmc.find.tv.core.SourceCatalog
import com.afmc.find.tv.core.SourceInfo
import com.afmc.find.tv.model.SearchHit
import com.afmc.find.tv.util.Qr
import kotlinx.coroutines.delay

@Composable
fun DetailOverlay(hit: SearchHit, onClose: () -> Unit) {
    val context = LocalContext.current
    val result = hit.result
    var showQr by remember { mutableStateOf(false) }
    var qrWasOpen by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val qrFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 1040.dp)
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Stroke, RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            Text(
                result.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.source, fontSize = 14.sp, color = AccentSoft)
                Dot()
                Text(result.sizeText, fontSize = 14.sp, color = TextMuted)
                Dot()
                Text(
                    "做种 ${result.seedsText}",
                    fontSize = 14.sp,
                    color = if ((result.seeders ?: 0) > 0) Success else TextMuted,
                )
                Dot()
                Text(Ranking.categoryOf(result), fontSize = 14.sp, color = TextMuted)
                if (formatDate(result.published).isNotEmpty()) {
                    Dot()
                    Text(formatDate(result.published), fontSize = 14.sp, color = TextDim)
                }
            }
            if (Ranking.techTags(result.title).isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    Ranking.techTags(result.title).joinToString(" · "),
                    fontSize = 13.sp,
                    color = TextDim,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("磁力链接", fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.height(6.dp))
            Text(
                result.magnet ?: "该结果没有可用的磁力链接",
                fontSize = 12.sp,
                color = TextMuted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(22.dp))
            val quarkIntent = remember { launcherIntent(context, "com.quark.yun.tv") }
            val pikpakInstalled = remember {
                runCatching { context.packageManager.getPackageInfo("com.pikcloud.pikpak", 0) }.isSuccess
            }
            val magnet = result.magnet
            val hasQuark = quarkIntent != null
            val hasPikpak = pikpakInstalled && !magnet.isNullOrBlank()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    hasQuark -> ActionButton(
                        text = "下载到夸克",
                        accent = true,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = {
                            if (magnet.isNullOrBlank()) {
                                Toast.makeText(context, "这条结果没有磁力链接", Toast.LENGTH_SHORT).show()
                            } else {
                                copyText(context, magnet, "磁力已复制，在夸克「离线下载」里粘贴")
                                runCatching {
                                    context.startActivity(quarkIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure {
                                    Toast.makeText(context, "无法打开夸克，磁力已复制", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                    hasPikpak -> ActionButton(
                        text = "下载到 PikPak",
                        accent = true,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { sendMagnet(context, "com.pikcloud.pikpak", magnet) },
                    )
                    else -> ActionButton(
                        text = "打开磁力",
                        accent = true,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { openUri(context, magnet) },
                    )
                }
                if (hasQuark && hasPikpak) {
                    ActionButton("下载到 PikPak", onClick = { sendMagnet(context, "com.pikcloud.pikpak", magnet) })
                }
                if (!magnet.isNullOrBlank()) {
                    ActionButton("复制磁力", onClick = { copyText(context, magnet, "已复制磁力链接") })
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!magnet.isNullOrBlank()) {
                    ActionButton("二维码", onClick = { showQr = true }, modifier = Modifier.focusRequester(qrFocus))
                    ActionButton("其他应用", onClick = { openUri(context, magnet) })
                }
                result.detailsUrl?.let { url ->
                    ActionButton("详情网页", onClick = { openUri(context, url) })
                }
                ActionButton("关闭", onClose)
            }
        }
    }

    if (showQr) {
        QrOverlay(text = result.magnet.orEmpty(), onClose = { showQr = false })
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(showQr) {
        if (showQr) {
            qrWasOpen = true
        } else if (qrWasOpen) {
            delay(120)
            runCatching { qrFocus.requestFocus() }
        }
    }
}

@Composable
fun QrOverlay(text: String, onClose: () -> Unit) {
    val image = remember(text) { Qr.bitmap(text) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(28.dp),
        ) {
            Text("手机扫码获取磁力", fontSize = 18.sp, color = Color(0xFF0B0C0E), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "磁力二维码",
                    modifier = Modifier.size(380.dp),
                )
            } else {
                Text("二维码生成失败", color = Danger)
            }
            Spacer(Modifier.height(16.dp))
            ActionButton("关闭", onClose, modifier = Modifier.focusRequester(focus))
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
}

@Composable
fun InputOverlay(
    title: String,
    initial: String,
    hint: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler(onBack = onCancel)

    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .onPreviewKeyEvent { event ->
                if (!fieldFocused || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (text.isNotBlank()) {
                            onConfirm(text)
                        } else {
                            // 键盘可能没自动弹出：先按 OK 应该重新调出输入法，而不是无响应。
                            runCatching { keyboard?.show() }
                        }
                        true
                    }
                    else -> false
                }
            }
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Stroke, RoundedCornerShape(18.dp))
                .padding(28.dp),
        ) {
            Text(title, fontSize = 20.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHigh)
                    .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 24.sp, color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onConfirm(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    if (text.isNotBlank()) { onConfirm(text); true } else false
                                }
                                Key.DirectionDown -> {
                                    runCatching { keyboard?.hide() }
                                    runCatching { confirmFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        },
                )
                if (text.isEmpty()) {
                    Text(hint, fontSize = 20.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "按 OK 调出键盘 · 输入后按 OK 或回车提交 · ↓ 切到按钮",
                fontSize = 12.sp,
                color = TextDim,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    confirmLabel,
                    onClick = { onConfirm(text) },
                    accent = true,
                    modifier = Modifier.focusRequester(confirmFocus),
                )
                ActionButton("取消", onCancel)
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
        runCatching { keyboard?.show() }
        delay(600)
        runCatching { keyboard?.show() }
    }
}

@Composable
fun SettingsOverlay(
    vm: SearchViewModel,
    onEditUrl: () -> Unit,
    onEditKey: () -> Unit,
    onClearRecent: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 1020.dp)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                .border(1.dp, Stroke, RoundedCornerShape(18.dp))
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                ActionButton("完成", onClose, modifier = Modifier.focusRequester(focus))
            }
            Spacer(Modifier.height(14.dp))
            Text("搜索源 · 按 OK 启用或停用", fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(SourceCatalog.builtIn) { info ->
                    SourceRow(
                        info = info,
                        enabled = vm.isSourceEnabled(info.id),
                        onToggle = { vm.setSourceEnabled(info.id, !vm.isSourceEnabled(info.id)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TorznabRow(
                label = "最近搜索",
                value = "${vm.recentSearches.size} 条",
                action = "清除",
                onEdit = onClearRecent,
            )
            Spacer(Modifier.height(8.dp))
            TorznabRow(
                label = "Torznab 地址",
                value = vm.torznabUrl.ifBlank { "未填写（可不填）" },
                onEdit = onEditUrl,
            )
            Spacer(Modifier.height(8.dp))
            TorznabRow(
                label = "Torznab API Key",
                value = if (vm.torznabKey.isBlank()) "未填写（可不填）" else "已设置（${vm.torznabKey.length} 位）",
                onEdit = onEditKey,
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
}

@Composable
private fun SourceRow(info: SourceInfo, enabled: Boolean, onToggle: () -> Unit) {
    TvSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        selected = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) TextPrimary else TextMuted,
                )
                Text(info.description, fontSize = 12.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                if (enabled) "已启用" else "已停用",
                fontSize = 13.sp,
                color = if (enabled) Success else TextDim,
            )
        }
    }
}

@Composable
private fun TorznabRow(label: String, value: String, action: String = "修改", onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = TextDim)
            Text(value, fontSize = 14.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        ActionButton(action, onEdit)
    }
}

private fun copyText(context: Context, text: String?, toast: String) {
    if (text.isNullOrBlank()) {
        Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
        return
    }
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("AFMC FIND", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/** Resolves an app launcher that only registers a Leanback category (e.g. Quark TV). */
private fun launcherIntent(context: Context, packageName: String): Intent? {
    val pm = context.packageManager
    pm.getLaunchIntentForPackage(packageName)?.let { return it }
    val leanback = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .setPackage(packageName)
    val info = pm.queryIntentActivities(leanback, 0).firstOrNull()?.activityInfo ?: return null
    return Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        component = android.content.ComponentName(info.packageName, info.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun sendMagnet(context: Context, packageName: String, magnet: String?) {
    if (magnet.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet))
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        copyText(context, magnet, "无法直接打开，磁力已复制")
    }
}

private fun openUri(context: Context, uriText: String?) {
    if (uriText.isNullOrBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriText)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "电视上没有可处理该链接的应用，请用二维码或复制磁力", Toast.LENGTH_LONG).show()
    }
}

package com.shinyalatte.copipecho

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(applicationContext).also { it.load() }
        handleIntent(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(
                primary = Color(0xFF7FD3BC), onPrimary = Color(0xFF00382D),
                primaryContainer = Color(0xFF1F5145), onPrimaryContainer = Color(0xFFCFEDE4),
                background = Color(0xFF121614), surface = Color(0xFF121614),
                surfaceContainer = Color(0xFF1B201E), surfaceContainerHigh = Color(0xFF242A27),
            ) else lightColorScheme(
                primary = Color(0xFF2E7D6B), primaryContainer = Color(0xFFCFEDE4),
                onPrimaryContainer = Color(0xFF0B3B30),
                background = Color(0xFFF6F8F7), surface = Color(0xFFF6F8F7),
                surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFECF1EF),
            )
            MaterialTheme(colorScheme = scheme) {
                App(store, sharedText, onCopied = ::afterCopy)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 他アプリの「共有」「テキスト選択メニュー」から受け取る */
    private fun handleIntent(i: Intent?) {
        val t = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!t.isNullOrBlank()) sharedText.value = t
    }

    private fun afterCopy(text: String, snipId: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("定型文", text))
        store.markUsed(snipId)
        val s = store.settings.value
        // Android 13以降はOS側がコピー通知を出すので、それ以前だけ自前で出す
        if (s.showToast && android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
        when (s.tapAction) {
            TapAction.COPY_BACK -> moveTaskToBack(true)
            TapAction.COPY_CLOSE -> finishAndRemoveTask()
            TapAction.COPY_ONLY -> Unit
        }
    }
}

// ======================================================================
// 画面
// ======================================================================

private sealed interface Dlg {
    data class EditSnip(val snip: Snip) : Dlg
    data class EditFolder(val folder: Folder) : Dlg
    data class SnipMenu(val snip: Snip) : Dlg
    data class FolderMenu(val folder: Folder) : Dlg
    data class Move(val snipId: String?, val folderId: String?) : Dlg
    data class Fill(val snip: Snip, val keys: List<String>) : Dlg
    data class ConfirmDelete(val label: String, val onYes: () -> Unit) : Dlg
    data object Settings : Dlg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(store: Store, sharedText: MutableState<String?>, onCopied: (String, String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val settings by store.settings
    val haptic = LocalHapticFeedback.current

    fun tap(s: Snip) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val keys = if (settings.fillPlaceholders) Placeholder.find(s.body) else emptyList()
        if (keys.isNotEmpty()) dlg = Dlg.Fill(s, keys) else onCopied(s.body, s.id)
    }

    // 共有で受け取ったら、今のフォルダに新規作成として開く
    LaunchedEffect(sharedText.value) {
        sharedText.value?.let {
            dlg = Dlg.EditSnip(Snip(store.newId(), "", it, current))
            sharedText.value = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(store.toJson().toString(2).toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, "書き出しました", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "書き出し失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val n = if (text.trimStart().startsWith("{")) store.fromJson(JSONObject(text), replace = false).also { store.save() }
                    else store.importPlain(text, current)
            n
        }.onSuccess { Toast.makeText(ctx, "${it}件を取り込みました", Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(ctx, "読み込み失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }

    BackHandler(enabled = searching || current != null) {
        if (searching) { searching = false; query = "" }
        else current = store.folders.firstOrNull { it.id == current }?.parentId
    }

    // ---- 表示するもの ----
    val sorter: (List<Snip>) -> List<Snip> = { list ->
        val base = when (settings.sort) {
            SortMode.MANUAL -> list.sortedBy { it.order }
            SortMode.RECENT -> list.sortedByDescending { it.lastUsed }
            SortMode.FREQUENT -> list.sortedByDescending { it.uses }
            SortMode.TITLE -> list.sortedBy { it.displayTitle }
        }
        base.sortedByDescending { it.pinned }
    }
    val q = query.trim()
    val subFolders = if (searching) emptyList() else store.folders.filter { it.parentId == current }.sortedBy { it.order }
    val shownSnips = if (searching) {
        if (q.isEmpty()) emptyList()
        else sorter(store.snips.filter { it.title.contains(q, true) || it.body.contains(q, true) })
    } else sorter(store.snips.filter { it.folderId == current })
    val pinnedEverywhere = if (!searching && current == null) store.snips.filter { it.pinned && it.folderId != null } else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (searching || current != null) IconButton(onClick = {
                        if (searching) { searching = false; query = "" }
                        else current = store.folders.firstOrNull { it.id == current }?.parentId
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") }
                },
                title = {
                    if (searching) {
                        val fr = remember { FocusRequester() }
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("全フォルダから検索") }, singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr),
                        )
                        LaunchedEffect(Unit) { fr.requestFocus() }
                    } else Text("コピペ棚", fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "検索") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "メニュー") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("設定") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; dlg = Dlg.Settings })
                            DropdownMenuItem(text = { Text("バックアップを書き出す") }, leadingIcon = { Icon(Icons.Default.Upload, null) },
                                onClick = { menuOpen = false; exportLauncher.launch("copipe-dana-backup.json") })
                            DropdownMenuItem(text = { Text("ファイルから取り込む") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { dlg = Dlg.EditFolder(Folder(store.newId(), "", current, System.currentTimeMillis())) }) {
                    Icon(Icons.Default.CreateNewFolder, "新規フォルダ")
                }
                ExtendedFloatingActionButton(
                    onClick = { dlg = Dlg.EditSnip(Snip(store.newId(), "", "", current)) },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("定型文") },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!searching) item {
                Breadcrumb(store.path(current)) { current = it }
            }
            if (pinnedEverywhere.isNotEmpty()) {
                item { SectionLabel("ピン留め") }
                items(pinnedEverywhere, key = { "p" + it.id }) { s ->
                    SnipCard(s, settings.previewLines, s.id in expanded, store.path(s.folderId).joinToString(" / ") { it.name },
                        onTap = { tap(s) }, onLong = { dlg = Dlg.SnipMenu(s) },
                        onToggle = { expanded = if (s.id in expanded) expanded - s.id else expanded + s.id })
                }
                item { SectionLabel("トップ") }
            }
            items(subFolders, key = { "f" + it.id }) { f ->
                val count = store.snips.count { it.folderId == f.id } + store.folders.count { it.parentId == f.id }
                FolderRow(f, count, onTap = { current = f.id }, onLong = { dlg = Dlg.FolderMenu(f) })
            }
            items(shownSnips, key = { it.id }) { s ->
                SnipCard(s, settings.previewLines, s.id in expanded,
                    if (searching) store.path(s.folderId).joinToString(" / ") { it.name }.ifEmpty { "トップ" } else null,
                    onTap = { tap(s) }, onLong = { dlg = Dlg.SnipMenu(s) },
                    onToggle = { expanded = if (s.id in expanded) expanded - s.id else expanded + s.id })
            }
            if (subFolders.isEmpty() && shownSnips.isEmpty()) item {
                Text(
                    if (searching) (if (q.isEmpty()) "キーワードを入れてください" else "見つかりませんでした") else "まだ何もありません。右下の＋から追加できます",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    // ---- ダイアログ ----
    when (val d = dlg) {
        null -> Unit
        is Dlg.EditSnip -> SnipEditor(d.snip, onDismiss = { dlg = null }) { store.upsertSnip(it); dlg = null }
        is Dlg.EditFolder -> NameDialog(if (d.folder.name.isEmpty()) "新しいフォルダ" else "フォルダ名を変更", d.folder.name,
            onDismiss = { dlg = null }) { store.upsertFolder(d.folder.copy(name = it)); dlg = null }
        is Dlg.SnipMenu -> ActionSheet(d.snip.displayTitle, onDismiss = { dlg = null }, listOf(
            Triple(Icons.Default.Edit, "編集") { dlg = Dlg.EditSnip(d.snip) },
            Triple(Icons.Default.ContentCopy, "穴埋めせずそのままコピー") { dlg = null; onCopied(d.snip.body, d.snip.id) },
            Triple(Icons.Outlined.PushPin, if (d.snip.pinned) "ピン留めを外す" else "ピン留め（トップにも表示）") {
                store.upsertSnip(d.snip.copy(pinned = !d.snip.pinned)); dlg = null },
            Triple(Icons.Default.DriveFileMove, "フォルダを移動") { dlg = Dlg.Move(d.snip.id, null) },
            Triple(Icons.Default.FileCopy, "複製") {
                store.upsertSnip(d.snip.copy(id = store.newId(), title = d.snip.title.ifBlank { d.snip.displayTitle } + "（コピー）",
                    uses = 0, lastUsed = 0, order = System.currentTimeMillis())); dlg = null },
            Triple(Icons.Default.Delete, "削除") { dlg = Dlg.ConfirmDelete("「${d.snip.displayTitle}」を削除します") { store.deleteSnip(d.snip.id) } },
        ))
        is Dlg.FolderMenu -> ActionSheet(d.folder.name, onDismiss = { dlg = null }, listOf(
            Triple(Icons.Default.Edit, "名前を変更") { dlg = Dlg.EditFolder(d.folder) },
            Triple(Icons.Default.DriveFileMove, "フォルダごと移動") { dlg = Dlg.Move(null, d.folder.id) },
            Triple(Icons.Default.Delete, "フォルダを削除（中身は1つ上へ）") {
                dlg = Dlg.ConfirmDelete("「${d.folder.name}」を削除します。中の定型文は消えず、1つ上の階層へ移ります") { store.deleteFolder(d.folder.id) } },
        ))
        is Dlg.Move -> MoveDialog(store, exclude = d.folderId, onDismiss = { dlg = null }) { dest ->
            if (d.snipId != null) store.snips.firstOrNull { it.id == d.snipId }?.let { store.upsertSnip(it.copy(folderId = dest)) }
            if (d.folderId != null) store.folders.firstOrNull { it.id == d.folderId }?.let { store.upsertFolder(it.copy(parentId = dest)) }
            dlg = null
        }
        is Dlg.Fill -> FillDialog(d.snip, d.keys, onDismiss = { dlg = null }) { text -> dlg = null; onCopied(text, d.snip.id) }
        is Dlg.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dlg = null }, title = { Text("削除の確認") }, text = { Text(d.label) },
            confirmButton = { TextButton(onClick = { d.onYes(); dlg = null }) { Text("削除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("やめる") } },
        )
        Dlg.Settings -> SettingsDialog(settings, onDismiss = { dlg = null }) { store.settings.value = it; store.save() }
    }
}

@Composable
private fun Breadcrumb(path: List<Folder>, onGo: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = { onGo(null) }, label = { Text("トップ") }, leadingIcon = { Icon(Icons.Default.Home, null, Modifier.size(18.dp)) })
        path.forEach { f ->
            Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).combinedClickableSafe { onGo(f.id) }.padding(4.dp))
        }
    }
}

@Composable
private fun SectionLabel(t: String) =
    Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, start = 4.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(f: Folder, count: Int, onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(f.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f))
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnipCard(
    s: Snip, previewLines: Int, open: Boolean, location: String?,
    onTap: () -> Unit, onLong: () -> Unit, onToggle: () -> Unit,
) {
    val hasTitle = s.title.isNotBlank()
    // タイトル未入力なら1行目が見出しになるので、プレビューは2行目以降
    val previewSource = if (hasTitle) s.body else s.body.lines().dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim()
    val lineCount = s.body.lines().size
    val canExpand = previewSource.isNotEmpty() && (lineCount > previewLines + 1 || s.body.length > 80)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(s.displayTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = if (open) 3 else 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (location != null && location.isNotEmpty())
                        Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (canExpand) IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "たたむ" else "全文を見る")
                } else Spacer(Modifier.width(12.dp))
            }
            val shown = if (open) s.body else previewSource
            val lines = if (open) Int.MAX_VALUE else previewLines
            if (lines > 0 && shown.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(shown, maxLines = lines, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
            }
            if (open) Row(Modifier.padding(top = 4.dp, end = 12.dp)) {
                Text("${s.body.length}文字・${s.uses}回使用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SnipEditor(src: Snip, onDismiss: () -> Unit, onSave: (Snip) -> Unit) {
    var title by remember { mutableStateOf(src.title) }
    var body by remember { mutableStateOf(src.body) }
    val keys = remember(body) { Placeholder.find(body) }
    AlertDialog(
        onDismissRequest = {}, // うっかり外側タップで消えないように
        title = { Text(if (src.body.isEmpty() && src.title.isEmpty()) "新しい定型文" else "編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("名前（空なら本文1行目）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, label = { Text("本文") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                if (keys.isNotEmpty()) Text("穴埋め項目：" + keys.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(enabled = body.isNotBlank(), onClick = { onSave(src.copy(title = title.trim(), body = body)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun FillDialog(s: Snip, keys: List<String>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val values = remember { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("穴埋めしてコピー") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(s.displayTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("空欄の項目は [ ] のまま残ります", style = MaterialTheme.typography.labelSmall)
                keys.forEachIndexed { i, k ->
                    val fr = remember { FocusRequester() }
                    OutlinedTextField(values[k] ?: "", { values[k] = it }, label = { Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(fr))
                    if (i == 0) LaunchedEffect(Unit) { fr.requestFocus() }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(Placeholder.apply(s.body, values)) }) { Text("コピー") } },
        dismissButton = { TextButton(onClick = { onDone(s.body) }) { Text("そのままコピー") } },
    )
}

@Composable
private fun NameDialog(title: String, init: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(init) }
    val fr = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(fr)) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
    LaunchedEffect(Unit) { fr.requestFocus() }
}

@Composable
private fun ActionSheet(title: String, onDismiss: () -> Unit, actions: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                actions.forEach { (icon, label, act) ->
                    Row(Modifier.fillMaxWidth().combinedClickableSafe(act).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
}

@Composable
private fun MoveDialog(store: Store, exclude: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("移動先") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().combinedClickableSafe { onPick(null) }.padding(vertical = 10.dp)) {
                    Icon(Icons.Default.Home, null); Spacer(Modifier.width(12.dp)); Text("トップ")
                }
                store.flatTree(exclude).forEach { (f, depth) ->
                    Row(Modifier.fillMaxWidth().combinedClickableSafe { onPick(f.id) }.padding(start = (depth * 20).dp, top = 10.dp, bottom = 10.dp)) {
                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(12.dp)); Text(f.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

@Composable
private fun SettingsDialog(s: Settings, onDismiss: () -> Unit, onChange: (Settings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("タップしたとき")
                TapAction.entries.forEach { a ->
                    Row(Modifier.fillMaxWidth().combinedClickableSafe { onChange(s.copy(tapAction = a)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.tapAction == a, { onChange(s.copy(tapAction = a)) }); Text(a.label)
                    }
                }
                SectionLabel("本文プレビューの行数")
                Row {
                    listOf(0, 1, 2, 3, 5).forEach { n ->
                        FilterChip(s.previewLines == n, { onChange(s.copy(previewLines = n)) }, label = { Text(if (n == 0) "なし" else "${n}行") },
                            modifier = Modifier.padding(end = 6.dp))
                    }
                }
                SectionLabel("並び順")
                SortMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().combinedClickableSafe { onChange(s.copy(sort = m)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.sort == m, { onChange(s.copy(sort = m)) }); Text(m.label)
                    }
                }
                SectionLabel("その他")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("[ ] の穴埋め入力を出す", Modifier.weight(1f))
                    Switch(s.fillPlaceholders, { onChange(s.copy(fillPlaceholders = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("コピー時に通知（Android 12以前）", Modifier.weight(1f))
                    Switch(s.showToast, { onChange(s.copy(showToast = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(applicationContext).also { it.load() }
        handleIntent(intent)
        setContent {
            val st by store.settings
            val dark = when (st.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val acc = ACCENTS[st.accent.coerceIn(0, ACCENTS.lastIndex)]
            val scheme = if (dark) darkColorScheme(
                primary = acc.dPrimary, onPrimary = Color(0xFF101010),
                primaryContainer = acc.dContainer, onPrimaryContainer = acc.dOnContainer,
                background = Color(0xFF131416), surface = Color(0xFF131416),
                surfaceContainer = Color(0xFF1C1E21), surfaceContainerHigh = Color(0xFF26292D),
            ) else lightColorScheme(
                primary = acc.lPrimary, primaryContainer = acc.lContainer,
                onPrimaryContainer = acc.lOnContainer,
                background = Color(0xFFF5F6F7), surface = Color(0xFFF5F6F7),
                surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFECEEF0),
            )
            val base = LocalDensity.current
            val scale = when (st.fontSize) { 0 -> 0.9f; 2 -> 1.15f; else -> 1f }
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * scale)) {
                MaterialTheme(colorScheme = scheme) {
                    App(store, sharedText, onCopy = ::copyText, onLeave = ::leave)
                }
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

    private fun copyText(text: String, snipId: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("定型文", text))
        store.markUsed(snipId)
    }

    private fun leave(action: TapAction) {
        when (action) {
            TapAction.COPY_BACK -> moveTaskToBack(true)
            TapAction.COPY_CLOSE -> finishAndRemoveTask()
            else -> Unit
        }
    }
}

private class Accent(
    val name: String, val lPrimary: Color, val lContainer: Color, val lOnContainer: Color,
    val dPrimary: Color, val dContainer: Color, val dOnContainer: Color,
)

private val ACCENTS = listOf(
    Accent("緑", Color(0xFF2E7D6B), Color(0xFFCFEDE4), Color(0xFF0B3B30), Color(0xFF7FD3BC), Color(0xFF1F5145), Color(0xFFCFEDE4)),
    Accent("青", Color(0xFF2F6BD8), Color(0xFFDCE6FB), Color(0xFF0E2A5E), Color(0xFF9CBBF5), Color(0xFF223A66), Color(0xFFDCE6FB)),
    Accent("紫", Color(0xFF7A4FC4), Color(0xFFEADFFA), Color(0xFF331A5C), Color(0xFFC9B0F2), Color(0xFF3E2C62), Color(0xFFEADFFA)),
    Accent("赤", Color(0xFFC63D4F), Color(0xFFFADDE1), Color(0xFF5A1220), Color(0xFFF2A2AD), Color(0xFF5E2630), Color(0xFFFADDE1)),
    Accent("オレンジ", Color(0xFFD06A1C), Color(0xFFFCE4CF), Color(0xFF5A2A05), Color(0xFFF5B47F), Color(0xFF5E3A1C), Color(0xFFFCE4CF)),
    Accent("グレー", Color(0xFF555B63), Color(0xFFE3E6EA), Color(0xFF22262B), Color(0xFFB9C0C8), Color(0xFF3A3F45), Color(0xFFE3E6EA)),
)

// ======================================================================
// 画面
// ======================================================================

private sealed interface Dlg {
    data class EditSnip(val snip: Snip) : Dlg
    data class EditFolder(val folder: Folder) : Dlg
    data class SnipMenu(val snip: Snip) : Dlg
    data class FolderMenu(val folder: Folder) : Dlg
    data class Rename(val snip: Snip) : Dlg
    data class Move(val snipIds: Set<String>, val folderIds: Set<String>, val base: String?) : Dlg
    data class NewFolderMove(val move: Move) : Dlg
    data class Fill(val snip: Snip, val keys: List<String>) : Dlg
    data class ConfirmDelete(val label: String, val onYes: () -> Unit) : Dlg
    data object Settings : Dlg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(store: Store, sharedText: MutableState<String?>, onCopy: (String, String) -> Unit, onLeave: (TapAction) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val settings by store.settings
    val haptic = LocalHapticFeedback.current

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var flashId by remember { mutableStateOf<String?>(null) }

    /** コピー → カードを光らせて帯で知らせる → 設定に応じて少し待ってから閉じる */
    fun onCopied(text: String, id: String) {
        onCopy(text, id)
        if (settings.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        flashId = id
        val name = store.snips.firstOrNull { it.id == id }?.displayTitle ?: ""
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar("コピーしました：$name", duration = SnackbarDuration.Short) }
            val leaving = settings.tapAction == TapAction.COPY_BACK || settings.tapAction == TapAction.COPY_CLOSE
            delay(if (leaving) 450 else 900)
            flashId = null
            if (leaving) { snackbar.currentSnackbarData?.dismiss(); onLeave(settings.tapAction) }
        }
    }

    fun copyFlow(s: Snip) {
        val keys = if (settings.fillPlaceholders) Placeholder.find(s.body) else emptyList()
        if (keys.isNotEmpty()) dlg = Dlg.Fill(s, keys) else onCopied(s.body, s.id)
    }

    fun toggle(s: Snip) { expanded = if (s.id in expanded) expanded - s.id else expanded + s.id }

    fun tap(s: Snip) = if (settings.tapAction == TapAction.SHOW_FULL) toggle(s) else copyFlow(s)

    fun longPress(s: Snip) {
        if (settings.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dlg = when (settings.longAction) {
            LongAction.MENU -> Dlg.SnipMenu(s)
            LongAction.EDIT -> Dlg.EditSnip(s)
            LongAction.RENAME -> Dlg.Rename(s)
        }
    }

    val doubleTap: ((Snip) -> Unit)? = when (settings.doubleAction) {
        DoubleAction.NONE -> null
        DoubleAction.RAW_COPY -> { s -> onCopied(s.body, s.id) }
        DoubleAction.EDIT -> { s -> dlg = Dlg.EditSnip(s) }
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

    BackHandler(enabled = reordering || searching || current != null) {
        if (reordering) { reordering = false; selected = emptySet() }
        else if (searching) { searching = false; query = "" }
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
                    if (reordering) IconButton(onClick = { reordering = false; selected = emptySet() }) { Icon(Icons.Default.Close, "並べ替えを終える") }
                    else if (searching || current != null) IconButton(onClick = {
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
                    } else if (reordering) Text(if (selected.isEmpty()) "並べ替え・整理" else "${selected.size}件を選択中", fontWeight = FontWeight.Bold)
                    else Text("ラテコピペ", fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (reordering) {
                        TextButton(onClick = { reordering = false; selected = emptySet() }) { Text("完了", fontWeight = FontWeight.Bold) }
                        return@TopAppBar
                    }
                    if (!searching) IconButton(onClick = {
                        if (settings.sort != SortMode.MANUAL) {
                            store.settings.value = settings.copy(sort = SortMode.MANUAL); store.save()
                            scope.launch { snackbar.showSnackbar("並び順を「手動」に切り替えました") }
                        }
                        reordering = true
                    }) { Icon(Icons.Default.SwapVert, "並べ替え・整理") }
                    if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "検索") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "メニュー") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("設定") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; dlg = Dlg.Settings })
                            DropdownMenuItem(text = { Text("バックアップを書き出す") }, leadingIcon = { Icon(Icons.Default.Upload, null) },
                                onClick = { menuOpen = false; exportLauncher.launch("lattecopi-backup.json") })
                            DropdownMenuItem(text = { Text("ファイルから取り込む") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (reordering && selected.isNotEmpty()) BottomAppBar {
                val sIds = selected.filter { it.startsWith("s:") }.map { it.drop(2) }.toSet()
                val fIds = selected.filter { it.startsWith("f:") }.map { it.drop(2) }.toSet()
                BarAction(Icons.Default.DriveFileMove, "移動") { dlg = Dlg.Move(sIds, fIds, current) }
                if (sIds.isNotEmpty()) {
                    val allPinned = store.snips.filter { it.id in sIds }.all { it.pinned }
                    BarAction(Icons.Default.PushPin, if (allPinned) "ピン外す" else "ピン留め") { store.setPinned(sIds, !allPinned); selected = emptySet() }
                }
                BarAction(Icons.Default.Delete, "削除") {
                    dlg = Dlg.ConfirmDelete("${selected.size}件を削除します。フォルダの中身は1つ上の階層へ移ります") {
                        sIds.forEach { store.deleteSnip(it) }; fIds.forEach { store.deleteFolder(it) }; selected = emptySet()
                    }
                }
                BarAction(Icons.Default.Deselect, "選択解除") { selected = emptySet() }
            }
        },
        floatingActionButton = {
            if (!searching && !reordering) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (reordering) {
            ReorderList(store, current, selected, pad,
                onSelect = { k -> selected = if (k in selected) selected - k else selected + k },
                onOpen = { id -> current = id; selected = emptySet() },
                onCrumb = { current = it; selected = emptySet() })
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!searching) item {
                Breadcrumb(store.path(current)) { current = it }
            }
            if (pinnedEverywhere.isNotEmpty()) {
                item { SectionLabel("ピン留め（全フォルダ）") }
                items(pinnedEverywhere, key = { "p" + it.id }) { s ->
                    SnipCard(s, settings.previewLines, s.id in expanded, store.path(s.folderId).joinToString(" / ") { it.name },
                        flashing = flashId == s.id, onTap = { tap(s) }, onLong = { longPress(s) },
                        onDouble = doubleTap?.let { f -> { f(s) } }, onToggle = { toggle(s) }, onRename = { dlg = Dlg.Rename(s) },
                        onPin = { store.upsertSnip(s.copy(pinned = !s.pinned)) })
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
                    flashing = flashId == s.id, onTap = { tap(s) }, onLong = { longPress(s) },
                    onDouble = doubleTap?.let { f -> { f(s) } }, onToggle = { toggle(s) }, onRename = { dlg = Dlg.Rename(s) },
                        onPin = { store.upsertSnip(s.copy(pinned = !s.pinned)) })
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
        is Dlg.Rename -> NameDialog("名前を変更", d.snip.title, allowEmpty = true,
            hint = "空にすると本文1行目を表示", onDismiss = { dlg = null }) {
            store.snips.firstOrNull { x -> x.id == d.snip.id }?.let { x -> store.upsertSnip(x.copy(title = it)) }; dlg = null }
        is Dlg.SnipMenu -> ActionSheet(d.snip.displayTitle, onDismiss = { dlg = null }, listOf(
            Triple(Icons.Default.DriveFileRenameOutline, "名前を変更") { dlg = Dlg.Rename(d.snip) },
            Triple(Icons.Default.Edit, "本文を編集") { dlg = Dlg.EditSnip(d.snip) },
            Triple(Icons.Default.ContentCopy, "穴埋めせずそのままコピー") { dlg = null; onCopied(d.snip.body, d.snip.id) },
            Triple(Icons.Outlined.PushPin, if (d.snip.pinned) "ピン留めを外す" else "ピン留め（トップにも表示）") {
                store.upsertSnip(d.snip.copy(pinned = !d.snip.pinned)); dlg = null },
            Triple(Icons.Default.DriveFileMove, "フォルダを移動") { dlg = Dlg.Move(setOf(d.snip.id), emptySet(), current) },
            Triple(Icons.Default.FileCopy, "複製") {
                store.upsertSnip(d.snip.copy(id = store.newId(), title = d.snip.title.ifBlank { d.snip.displayTitle } + "（コピー）",
                    uses = 0, lastUsed = 0, order = System.currentTimeMillis())); dlg = null },
            Triple(Icons.Default.Delete, "削除") { dlg = Dlg.ConfirmDelete("「${d.snip.displayTitle}」を削除します") { store.deleteSnip(d.snip.id) } },
        ))
        is Dlg.FolderMenu -> ActionSheet(d.folder.name, onDismiss = { dlg = null }, listOf(
            Triple(Icons.Default.Edit, "名前を変更") { dlg = Dlg.EditFolder(d.folder) },
            Triple(Icons.Default.DriveFileMove, "フォルダごと移動") { dlg = Dlg.Move(emptySet(), setOf(d.folder.id), current) },
            Triple(Icons.Default.Delete, "フォルダを削除（中身は1つ上へ）") {
                dlg = Dlg.ConfirmDelete("「${d.folder.name}」を削除します。中の定型文は消えず、1つ上の階層へ移ります") { store.deleteFolder(d.folder.id) } },
        ))
        is Dlg.Move -> MoveDialog(store, exclude = d.folderIds, onDismiss = { dlg = null },
            onNewFolder = { dlg = Dlg.NewFolderMove(d) }) { dest ->
            store.moveItems(d.snipIds, d.folderIds, dest); selected = emptySet(); dlg = null
            scope.launch { snackbar.showSnackbar("移動しました：${if (dest == null) "トップ" else store.folders.firstOrNull { it.id == dest }?.name}") }
        }
        is Dlg.NewFolderMove -> NameDialog("新しいフォルダに移動", "", hint = "フォルダ名", onDismiss = { dlg = null }) { name ->
            val nf = Folder(store.newId(), name, d.move.base, System.currentTimeMillis())
            store.upsertFolder(nf); store.moveItems(d.move.snipIds, d.move.folderIds, nf.id); selected = emptySet(); dlg = null
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
    s: Snip, previewLines: Int, open: Boolean, location: String?, flashing: Boolean,
    onTap: () -> Unit, onLong: () -> Unit, onDouble: (() -> Unit)?, onToggle: () -> Unit, onRename: () -> Unit, onPin: () -> Unit,
) {
    val bg by animateColorAsState(
        if (flashing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "flash")
    val hasTitle = s.title.isNotBlank()
    // タイトル未入力なら1行目が見出しになるので、プレビューは2行目以降
    val previewSource = if (hasTitle) s.body else s.body.lines().dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim()
    val lineCount = s.body.lines().size
    val canExpand = previewSource.isNotEmpty() && (lineCount > previewLines + 1 || s.body.length > 80)
    Surface(
        color = bg, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp,
        border = if (s.pinned) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onTap, onLongClick = onLong, onDoubleClick = onDouble),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (flashing) Icon(Icons.Default.CheckCircle, "コピー済み", Modifier.size(18.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(s.displayTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = if (open) 3 else 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (!hasTitle) Text("名前なし", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).clip(MaterialTheme.shapes.small).combinedClickableSafe(onRename).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                    if (location != null && location.isNotEmpty())
                        Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onPin, modifier = Modifier.size(40.dp)) {
                    if (s.pinned) Icon(Icons.Default.PushPin, "ピン留めを外す", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.PushPin, "ピン留め", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
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
private fun NameDialog(title: String, init: String, allowEmpty: Boolean = false, hint: String? = null, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(init) }
    val fr = remember { FocusRequester() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = if (hint != null) { { Text(hint) } } else null,
            modifier = Modifier.fillMaxWidth().focusRequester(fr)) },
        confirmButton = { TextButton(enabled = allowEmpty || name.isNotBlank(), onClick = { onOk(name.trim()) }) { Text("OK") } },
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
private fun MoveDialog(store: Store, exclude: Set<String>, onDismiss: () -> Unit, onNewFolder: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("移動先を選ぶ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().combinedClickableSafe(onNewFolder).padding(vertical = 10.dp)) {
                    Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                    Text("新しいフォルダを作って移動", color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
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
private fun <E> RadioList(items: List<E>, selected: E, label: (E) -> String, onPick: (E) -> Unit) {
    items.forEach { e ->
        Row(Modifier.fillMaxWidth().combinedClickableSafe { onPick(e) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == e, { onPick(e) }); Text(label(e))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(s: Settings, onDismiss: () -> Unit, onChange: (Settings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("見た目")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { m -> FilterChip(s.theme == m, { onChange(s.copy(theme = m)) }, label = { Text(m.label) }) }
                }
                Text("アクセント色", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ACCENTS.forEachIndexed { i, a ->
                        FilterChip(s.accent == i, { onChange(s.copy(accent = i)) }, label = { Text(a.name) },
                            leadingIcon = { Icon(Icons.Default.Circle, null, tint = a.lPrimary, modifier = Modifier.size(14.dp)) })
                    }
                }
                Text("文字サイズ", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("小", "中", "大").forEachIndexed { i, t -> FilterChip(s.fontSize == i, { onChange(s.copy(fontSize = i)) }, label = { Text(t) }) }
                }
                Text("本文プレビューの行数", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3, 5).forEach { n ->
                        FilterChip(s.previewLines == n, { onChange(s.copy(previewLines = n)) }, label = { Text(if (n == 0) "なし" else "${n}行") })
                    }
                }

                SectionLabel("タップ")
                RadioList(TapAction.entries, s.tapAction, { it.label }) { onChange(s.copy(tapAction = it)) }
                SectionLabel("長押し")
                RadioList(LongAction.entries, s.longAction, { it.label }) { onChange(s.copy(longAction = it)) }
                SectionLabel("ダブルタップ")
                RadioList(DoubleAction.entries, s.doubleAction, { it.label }) { onChange(s.copy(doubleAction = it)) }
                SectionLabel("並び順")
                RadioList(SortMode.entries, s.sort, { it.label }) { onChange(s.copy(sort = it)) }

                SectionLabel("その他")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("[ ] の穴埋め入力を出す", Modifier.weight(1f))
                    Switch(s.fillPlaceholders, { onChange(s.copy(fillPlaceholders = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("コピー時に振動", Modifier.weight(1f))
                    Switch(s.vibrate, { onChange(s.copy(vibrate = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

@Composable
private fun RowScope.BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null); Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private class Entry(val key: String, val folder: Folder?, val snip: Snip?) {
    /** ドラッグで入れ替えできるのは同じグループ同士だけ（フォルダ／ピン留め／通常） */
    val group get() = when { folder != null -> 0; snip!!.pinned -> 1; else -> 2 }
}

private fun buildEntries(store: Store, current: String?): List<Entry> =
    store.folders.filter { it.parentId == current }.sortedBy { it.order }.map { Entry("f:" + it.id, it, null) } +
    store.snips.filter { it.folderId == current }.sortedWith(compareByDescending<Snip> { it.pinned }.thenBy { it.order })
        .map { Entry("s:" + it.id, null, it) }

@Composable
private fun ReorderList(
    store: Store, current: String?, selected: Set<String>, pad: PaddingValues,
    onSelect: (String) -> Unit, onOpen: (String) -> Unit, onCrumb: (String?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sig = store.folders.toList() to store.snips.toList()
    var entries by remember(current, sig) { mutableStateOf(buildEntries(store, current)) }
    val listState = rememberLazyListState()
    val rs = rememberReorderableLazyListState(listState) { from, to ->
        val a = entries.indexOfFirst { it.key == from.key }
        val b = entries.indexOfFirst { it.key == to.key }
        if (a >= 0 && b >= 0 && entries[a].group == entries[b].group) {
            entries = entries.toMutableList().apply { add(b, removeAt(a)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    fun commit() = store.applyOrder(entries.mapNotNull { it.folder?.id }, entries.mapNotNull { it.snip?.id })

    LazyColumn(
        state = listState, modifier = Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "crumb") { Breadcrumb(store.path(current), onCrumb) }
        item(key = "help") {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                Text("右の ≡ をつかんで上下に動かすと順番が変わります。左のチェックで選ぶと、下のボタンでまとめて移動・ピン留め・削除ができます。フォルダ名をタップすると中に入れます。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        if (entries.isEmpty()) item(key = "empty") {
            Text("このフォルダは空です", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        items(entries, key = { it.key }) { e ->
            ReorderableItem(rs, key = e.key) { dragging ->
                val elev by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "elev")
                val sel = e.key in selected
                val cs = MaterialTheme.colorScheme
                Surface(
                    shape = MaterialTheme.shapes.large, shadowElevation = elev,
                    color = when { sel -> cs.primaryContainer; e.folder != null -> cs.surfaceContainerHigh; else -> cs.surfaceContainer },
                    border = if (e.snip?.pinned == true) BorderStroke(1.5.dp, cs.primary) else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(sel, { onSelect(e.key) })
                        val icon = when { e.folder != null -> Icons.Default.Folder; e.snip!!.pinned -> Icons.Default.PushPin; else -> Icons.Default.Description }
                        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            e.folder?.name ?: e.snip!!.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (e.folder != null) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clickable { if (e.folder != null) onOpen(e.folder.id) else onSelect(e.key) }.padding(vertical = 14.dp),
                        )
                        if (e.folder != null) Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant)
                        Icon(Icons.Default.DragHandle, "つかんで並べ替え",
                            modifier = Modifier.draggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { commit() },
                            ).padding(horizontal = 14.dp, vertical = 14.dp))
                    }
                }
            }
        }
    }
}

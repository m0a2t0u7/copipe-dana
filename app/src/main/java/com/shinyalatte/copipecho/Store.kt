package com.shinyalatte.copipecho

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Folder(val id: String, val name: String, val parentId: String?, val order: Long)

data class Snip(
    val id: String,
    val title: String,
    val body: String,
    val folderId: String?,
    val pinned: Boolean = false,
    val uses: Int = 0,
    val lastUsed: Long = 0,
    val order: Long = System.currentTimeMillis(),
) {
    /** タイトル未入力なら本文1行目を見出しにする */
    val displayTitle: String
        get() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "（空）" }
}

/** タップしたときの動作 */
enum class TapAction(val label: String) {
    COPY_BACK("コピーして元のアプリに戻る"),
    COPY_CLOSE("コピーしてアプリを閉じる"),
    COPY_ONLY("コピーのみ（画面はそのまま）"),
}

enum class SortMode(val label: String) {
    MANUAL("作成順"), RECENT("最近使った順"), FREQUENT("よく使う順"), TITLE("名前順"),
}

class Settings(
    val tapAction: TapAction = TapAction.COPY_BACK,
    val previewLines: Int = 2,
    val sort: SortMode = SortMode.MANUAL,
    val fillPlaceholders: Boolean = true,
    val showToast: Boolean = true,
) {
    fun copy(
        tapAction: TapAction = this.tapAction, previewLines: Int = this.previewLines,
        sort: SortMode = this.sort, fillPlaceholders: Boolean = this.fillPlaceholders,
        showToast: Boolean = this.showToast,
    ) = Settings(tapAction, previewLines, sort, fillPlaceholders, showToast)
}

/** 端末内のJSONファイル1つに全部保存する。書き込みは一時ファイル経由で壊れにくくする */
class Store(private val ctx: Context) {
    val folders = mutableStateListOf<Folder>()
    val snips = mutableStateListOf<Snip>()
    val settings = mutableStateOf(Settings())

    private val file get() = File(ctx.filesDir, "data.json")

    fun load() {
        val f = file
        if (!f.exists()) { seed(); save(); return }
        runCatching { fromJson(JSONObject(f.readText()), replace = true) }
            .onFailure { File(ctx.filesDir, "data.broken.json").writeText(f.readText()) }
    }

    fun save() {
        val tmp = File(ctx.filesDir, "data.tmp")
        tmp.writeText(toJson().toString())
        if (file.exists()) file.copyTo(File(ctx.filesDir, "data.bak.json"), overwrite = true)
        tmp.renameTo(file)
    }

    private fun seed() {
        val fid = newId()
        folders += Folder(fid, "プロンプト", null, 0)
        snips += Snip(
            newId(), "使い方", "タップでコピー、長押しで編集・移動・削除。\n本文に [ここ] のような角かっこがあると、コピー前に穴埋め入力が出ます。\n例：[ツール名] を改善してください。",
            fid,
        )
    }

    // ---------- 操作 ----------
    fun newId() = UUID.randomUUID().toString()

    fun upsertSnip(s: Snip) {
        val i = snips.indexOfFirst { it.id == s.id }
        if (i >= 0) snips[i] = s else snips += s
        save()
    }
    fun deleteSnip(id: String) { snips.removeAll { it.id == id }; save() }

    fun markUsed(id: String) {
        val i = snips.indexOfFirst { it.id == id }
        if (i >= 0) snips[i] = snips[i].copy(uses = snips[i].uses + 1, lastUsed = System.currentTimeMillis())
        save()
    }

    fun upsertFolder(f: Folder) {
        val i = folders.indexOfFirst { it.id == f.id }
        if (i >= 0) folders[i] = f else folders += f
        save()
    }

    /** フォルダ削除：中身は1つ上の階層へ移す（データを消さない） */
    fun deleteFolder(id: String) {
        val target = folders.firstOrNull { it.id == id } ?: return
        val parent = target.parentId
        snips.replaceAll { if (it.folderId == id) it.copy(folderId = parent) else it }
        folders.replaceAll { if (it.parentId == id) it.copy(parentId = parent) else it }
        folders.removeAll { it.id == id }
        save()
    }

    fun path(folderId: String?): List<Folder> {
        val out = ArrayList<Folder>()
        var cur = folders.firstOrNull { it.id == folderId }
        var guard = 0
        while (cur != null && guard++ < 50) { out.add(0, cur); cur = folders.firstOrNull { it.id == cur!!.parentId } }
        return out
    }

    /** 移動先の選択用。自分自身と子孫は除外できる */
    fun flatTree(exclude: String? = null): List<Pair<Folder, Int>> {
        val out = ArrayList<Pair<Folder, Int>>()
        fun walk(parent: String?, depth: Int) {
            folders.filter { it.parentId == parent }.sortedBy { it.order }.forEach {
                if (it.id == exclude) return@forEach
                out += it to depth; walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    // ---------- JSON ----------
    fun toJson(): JSONObject = JSONObject().apply {
        put("app", "copipe-dana"); put("version", 1)
        put("folders", JSONArray().apply {
            folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: JSONObject.NULL).put("order", it.order)) }
        })
        put("snips", JSONArray().apply {
            snips.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body)
                    .put("folderId", it.folderId ?: JSONObject.NULL).put("pinned", it.pinned)
                    .put("uses", it.uses).put("lastUsed", it.lastUsed).put("order", it.order))
            }
        })
        val s = settings.value
        put("settings", JSONObject().put("tapAction", s.tapAction.name).put("previewLines", s.previewLines)
            .put("sort", s.sort.name).put("fill", s.fillPlaceholders).put("toast", s.showToast))
    }

    /** replace=false のときは既存データに追加（ID重複は上書き） */
    fun fromJson(o: JSONObject, replace: Boolean): Int {
        val fs = o.optJSONArray("folders") ?: JSONArray()
        val ss = o.optJSONArray("snips") ?: JSONArray()
        if (replace) { folders.clear(); snips.clear() }
        for (i in 0 until fs.length()) {
            val j = fs.getJSONObject(i)
            val f = Folder(j.getString("id"), j.optString("name"), j.optStringOrNull("parentId"), j.optLong("order"))
            folders.removeAll { it.id == f.id }; folders += f
        }
        for (i in 0 until ss.length()) {
            val j = ss.getJSONObject(i)
            val s = Snip(j.getString("id"), j.optString("title"), j.optString("body"), j.optStringOrNull("folderId"),
                j.optBoolean("pinned"), j.optInt("uses"), j.optLong("lastUsed"), j.optLong("order"))
            snips.removeAll { it.id == s.id }; snips += s
        }
        if (replace) o.optJSONObject("settings")?.let { j ->
            settings.value = Settings(
                runCatching { TapAction.valueOf(j.optString("tapAction")) }.getOrDefault(TapAction.COPY_BACK),
                j.optInt("previewLines", 2),
                runCatching { SortMode.valueOf(j.optString("sort")) }.getOrDefault(SortMode.MANUAL),
                j.optBoolean("fill", true), j.optBoolean("toast", true),
            )
        }
        return ss.length()
    }

    /** 1行1件のテキストを取り込む（コピペリストのテキスト書き出し等の移行用） */
    fun importPlain(text: String, folderId: String?): Int {
        val blocks = text.split(Regex("\\n\\s*-{3,}\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        blocks.forEachIndexed { i, b -> snips += Snip(newId(), "", b, folderId, order = System.currentTimeMillis() + i) }
        save(); return blocks.size
    }
}

private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

/** [〇〇] / ［〇〇］ を穴埋め項目として取り出す（重複は1回だけ聞く） */
object Placeholder {
    private val re = Regex("[\\[［]([^\\[\\]［］\\n]{1,60})[\\]］]")
    fun find(body: String): List<String> = re.findAll(body).map { it.groupValues[1] }.distinct().toList()
    fun apply(body: String, values: Map<String, String>): String =
        re.replace(body) { m -> values[m.groupValues[1]]?.takeIf { it.isNotEmpty() } ?: m.value }
}

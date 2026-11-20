package com.chmouel.liseur.ui.library

import android.content.Context
import com.chmouel.liseur.data.gdrive.DriveStateSync
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Liburuville's shelves, the same ones the web app keeps in its shared
 * `state.json`: definitions `{ id, name, t, deleted }` and, per book, the
 * list of shelf ids in `meta.shelves`. Changes made here wait in a small
 * pending file until the next Drive sync folds them in.
 */
object LiburuShelves {
    data class Shelf(val id: String, val name: String)

    private const val PENDING = "liburu-shelves-pending.json"
    val version = MutableStateFlow(0)

    fun keyOf(url: String) = if (url.startsWith("gdrive:")) "g:" + url.removePrefix("gdrive:") else url

    private fun state(context: Context): JSONObject =
        runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull() ?: JSONObject()

    fun pending(context: Context): JSONObject =
        runCatching { JSONObject(File(context.filesDir, PENDING).readText()) }.getOrNull()
            ?: JSONObject().put("shelves", JSONObject()).put("books", JSONObject())

    private fun savePending(context: Context, p: JSONObject) {
        File(context.filesDir, PENDING).writeText(p.toString())
        version.value++
    }

    fun clearPending(context: Context) {
        File(context.filesDir, PENDING).delete()
    }

    /** Shelves by id, and each book's shelf ids, with pending edits laid over the web's. */
    fun load(context: Context): Pair<List<Shelf>, Map<String, Set<String>>> {
        val st = state(context)
        val p = pending(context)
        val defs = HashMap<String, JSONObject>()
        st.optJSONObject("shelves")?.let { o -> o.keys().forEach { k -> o.optJSONObject(k)?.let { defs[k] = it } } }
        p.optJSONObject("shelves")?.let { o ->
            o.keys().forEach { k ->
                val mine = o.optJSONObject(k) ?: return@forEach
                if (mine.optLong("t") >= (defs[k]?.optLong("t") ?: 0L)) defs[k] = mine
            }
        }
        val shelves = defs.values.filter { !it.optBoolean("deleted") }
            .map { Shelf(it.optString("id"), it.optString("name")) }
            .sortedBy { it.name.lowercase() }
        val membership = HashMap<String, Set<String>>()
        st.optJSONObject("books")?.let { b ->
            b.keys().forEach { k ->
                val ids = b.optJSONObject(k)?.optJSONObject("meta")?.optJSONArray("shelves") ?: return@forEach
                membership[k] = (0 until ids.length()).map { ids.optString(it) }.toSet()
            }
        }
        p.optJSONObject("books")?.let { b ->
            b.keys().forEach { k ->
                val ids = b.optJSONObject(k)?.optJSONArray("shelves") ?: return@forEach
                membership[k] = (0 until ids.length()).map { ids.optString(it) }.toSet()
            }
        }
        return shelves to membership
    }

    fun saveShelf(context: Context, id: String?, name: String, deleted: Boolean = false): String {
        val p = pending(context)
        val shelfId = id ?: ("s" + System.currentTimeMillis().toString(36) + (0..1295).random().toString(36))
        p.getJSONObject("shelves").put(
            shelfId,
            JSONObject().put("id", shelfId).put("name", name).put("t", System.currentTimeMillis()).put("deleted", deleted),
        )
        savePending(context, p)
        return shelfId
    }

    fun setBookShelves(context: Context, bookUrl: String, ids: Set<String>) {
        val p = pending(context)
        p.getJSONObject("books").put(
            keyOf(bookUrl),
            JSONObject().put("shelves", JSONArray(ids.toList())).put("t", System.currentTimeMillis()),
        )
        savePending(context, p)
    }

    /** Folds pending edits into the shared state; true when something changed. */
    fun applyPending(context: Context, state: JSONObject): Boolean {
        val p = pending(context)
        var changed = false
        val shelves = state.optJSONObject("shelves") ?: JSONObject().also { state.put("shelves", it) }
        p.optJSONObject("shelves")?.let { o ->
            o.keys().forEach { k ->
                val mine = o.getJSONObject(k)
                if (mine.optLong("t") > (shelves.optJSONObject(k)?.optLong("t") ?: 0L)) { shelves.put(k, mine); changed = true }
            }
        }
        val books = state.optJSONObject("books") ?: JSONObject().also { state.put("books", it) }
        p.optJSONObject("books")?.let { o ->
            o.keys().forEach { k ->
                val mine = o.getJSONObject(k)
                val entry = books.optJSONObject(k) ?: JSONObject().put("bookId", k).put("ann", JSONObject()).also { books.put(k, it) }
                val meta = entry.optJSONObject("meta") ?: JSONObject().put("status", "none").also { entry.put("meta", it) }
                meta.put("shelves", mine.getJSONArray("shelves")).put("t", maxOf(meta.optLong("t"), mine.optLong("t")))
                changed = true
            }
        }
        return changed
    }
}

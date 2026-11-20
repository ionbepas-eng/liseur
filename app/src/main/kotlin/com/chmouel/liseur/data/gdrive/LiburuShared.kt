package com.chmouel.liseur.data.gdrive

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Parts of the web's `state.json` the app now edits too: the vocabulary
 * (`vocab`, keyed «lang:word») and annotations added here to a book's `ann`
 * (imports from Play Libros). Both merge newest-`t`-wins, like the web.
 */
object LiburuShared {
    private const val VOCAB = "liburu-vocab.json"
    private const val ANN_PENDING = "liburu-ann-pending.json"
    val version = MutableStateFlow(0)

    /** The book open in the reader, so a saved word remembers where it was met. */
    @Volatile var currentBookTitle: String? = null

    private fun read(context: Context, name: String) =
        runCatching { JSONObject(File(context.filesDir, name).readText()) }.getOrNull() ?: JSONObject()
    private fun write(context: Context, name: String, o: JSONObject) = File(context.filesDir, name).writeText(o.toString())

    // --- Vocabulary -------------------------------------------------------------------
    fun vocab(context: Context): List<JSONObject> {
        val all = read(context, VOCAB)
        return all.keys().asSequence().mapNotNull { all.optJSONObject(it) }.filter { !it.optBoolean("deleted") }
            .sortedByDescending { it.optLong("created") }.toList()
    }

    fun isSaved(context: Context, lang: String, word: String) =
        read(context, VOCAB).optJSONObject("$lang:${word.lowercase()}")?.let { !it.optBoolean("deleted") } == true

    fun saveWord(context: Context, word: String, lang: String, def: String?, book: String?) {
        val all = read(context, VOCAB)
        val id = "$lang:${word.lowercase()}"
        val now = System.currentTimeMillis()
        val e = all.optJSONObject(id) ?: JSONObject().put("created", now).put("reviews", 0)
        e.put("id", id).put("word", word).put("lang", lang).put("deleted", false).put("t", now)
        def?.takeIf { it.isNotBlank() }?.let { e.put("def", it) }
        book?.let { e.put("book", it) }
        write(context, VOCAB, all.put(id, e))
        version.value++
    }

    /** A flashcard answer: «La sabía» counts a review, «Otra vez» only moves it back. */
    fun reviewWord(context: Context, id: String, ok: Boolean) {
        val all = read(context, VOCAB)
        val now = System.currentTimeMillis()
        all.optJSONObject(id)?.let {
            it.put("reviews", it.optInt("reviews") + if (ok) 1 else 0).put("lastReview", now).put("t", now)
            write(context, VOCAB, all); version.value++
        }
    }

    fun deleteWord(context: Context, id: String) {
        val all = read(context, VOCAB)
        all.optJSONObject(id)?.let { it.put("deleted", true).put("t", System.currentTimeMillis()); write(context, VOCAB, all); version.value++ }
    }

    /** Both ways with the web's `vocab`; true when the shared file changed. */
    fun mergeVocab(context: Context, state: JSONObject): Boolean {
        val mine = read(context, VOCAB)
        val theirs = state.optJSONObject("vocab") ?: JSONObject()
        var local = false
        var remote = false
        theirs.keys().forEach { k ->
            val r = theirs.optJSONObject(k) ?: return@forEach
            if (r.optLong("t") > (mine.optJSONObject(k)?.optLong("t") ?: -1L)) { mine.put(k, r); local = true }
        }
        mine.keys().forEach { k ->
            val m = mine.optJSONObject(k) ?: return@forEach
            if (m.optLong("t") > (theirs.optJSONObject(k)?.optLong("t") ?: -1L)) { theirs.put(k, m); remote = true }
        }
        if (local) { write(context, VOCAB, mine); version.value++ }
        if (remote) state.put("vocab", theirs)
        return remote
    }

    // --- Wishlist («Me interesa»), the web's `wishlist` ---------------------------------
    private const val WISH = "liburu-wishlist.json"

    fun wishlist(context: Context): List<JSONObject> {
        val all = read(context, WISH)
        return all.keys().asSequence().mapNotNull { all.optJSONObject(it) }.filter { !it.optBoolean("deleted") }.sortedByDescending { it.optLong("t") }.toList()
    }

    fun isWished(context: Context, key: String) = read(context, WISH).optJSONObject(key)?.let { !it.optBoolean("deleted") } == true

    fun toggleWish(context: Context, item: JSONObject, on: Boolean) {
        val all = read(context, WISH)
        val key = item.optString("key")
        val e = JSONObject()
        listOf("key", "title", "author", "year", "cover", "link", "source", "desc").forEach { k -> item.opt(k)?.let { e.put(k, it) } }
        e.put("desc", item.optString("desc").take(600)).put("t", System.currentTimeMillis()).put("deleted", !on)
        write(context, WISH, all.put(key, e))
        version.value++
    }

    fun mergeWishlist(context: Context, state: JSONObject): Boolean = mergeMap(context, WISH, state, "wishlist")

    // --- Retos (challenges), shared with the web's `retos` ------------------------------
    private const val RETOS = "liburu-retos.json"

    /** Your own challenges: what this device has, overlaid on what the web last sent. */
    fun retos(context: Context, state: JSONObject?): List<JSONObject> {
        val mine = read(context, RETOS)
        val theirs = state?.optJSONObject("retos") ?: JSONObject()
        val all = HashMap<String, JSONObject>()
        theirs.keys().forEach { k -> theirs.optJSONObject(k)?.let { all[k] = it } }
        mine.keys().forEach { k -> mine.optJSONObject(k)?.let { m -> if (m.optLong("t") >= (all[k]?.optLong("t") ?: -1L)) all[k] = m } }
        return all.values.filter { !it.optBoolean("deleted") }
    }

    fun saveReto(context: Context, reto: JSONObject) {
        val all = read(context, RETOS)
        val id = reto.optString("id").ifBlank { "r" + java.lang.Long.toString(System.currentTimeMillis(), 36) }
        write(context, RETOS, all.put(id, reto.put("id", id).put("t", System.currentTimeMillis())))
        version.value++
    }

    fun deleteReto(context: Context, reto: JSONObject) {
        val all = read(context, RETOS)
        write(context, RETOS, all.put(reto.optString("id"), JSONObject(reto.toString()).put("deleted", true).put("t", System.currentTimeMillis())))
        version.value++
    }

    fun mergeRetos(context: Context, state: JSONObject): Boolean = mergeMap(context, RETOS, state, "retos")

    private fun mergeMap(context: Context, file: String, state: JSONObject, field: String): Boolean {
        val mine = read(context, file)
        val theirs = state.optJSONObject(field) ?: JSONObject()
        var local = false
        var remote = false
        theirs.keys().forEach { k ->
            val r = theirs.optJSONObject(k) ?: return@forEach
            if (r.optLong("t") > (mine.optJSONObject(k)?.optLong("t") ?: -1L)) { mine.put(k, r); local = true }
        }
        mine.keys().forEach { k ->
            val m = mine.optJSONObject(k) ?: return@forEach
            if (m.optLong("t") > (theirs.optJSONObject(k)?.optLong("t") ?: -1L)) { theirs.put(k, m); remote = true }
        }
        if (local) { write(context, file, mine); version.value++ }
        if (remote) state.put(field, theirs)
        return remote
    }

    // --- Annotations added here -------------------------------------------------------
    fun addAnnotations(context: Context, bookKey: String, anns: List<JSONObject>) {
        val p = read(context, ANN_PENDING)
        val book = p.optJSONObject(bookKey) ?: JSONObject().also { p.put(bookKey, it) }
        anns.forEach { book.put(it.getString("id"), it) }
        write(context, ANN_PENDING, p)
        version.value++
    }

    fun pendingAnnotations(context: Context): JSONObject = read(context, ANN_PENDING)

    /** Puts the pending annotations into the books of `state.json`. */
    fun applyAnnotations(context: Context, state: JSONObject): Boolean {
        val p = read(context, ANN_PENDING)
        if (p.length() == 0) return false
        val books = state.optJSONObject("books") ?: JSONObject().also { state.put("books", it) }
        p.keys().forEach { key ->
            val entry = books.optJSONObject(key) ?: JSONObject().put("bookId", key).put("meta", JSONObject().put("status", "none")).also { books.put(key, it) }
            val ann = entry.optJSONObject("ann") ?: JSONObject().also { entry.put("ann", it) }
            val add = p.getJSONObject(key)
            add.keys().forEach { id -> if (!ann.has(id)) ann.put(id, add.getJSONObject(id)) }
        }
        return true
    }

    fun clearAnnotations(context: Context) { File(context.filesDir, ANN_PENDING).delete() }
}

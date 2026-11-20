package com.chmouel.liseur.ui.library

import android.content.Context
import com.chmouel.liseur.domain.Identifier
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

/**
 * What the reader wrote over a book's own data (a missing year, the real
 * publisher, a better synopsis). Kept apart from the file's metadata, which
 * a re-read of the EPUB would rewrite, and laid over it when shown.
 */
object LiburuEdits {
    private const val FILE = "liburu-book-edits.json"
    val FIELDS = listOf("title", "author", "year", "genres", "publisher", "isbn", "pages", "language", "synopsis")
    val version = MutableStateFlow(0)

    private fun all(context: Context): JSONObject =
        runCatching { JSONObject(File(context.filesDir, FILE).readText()) }.getOrNull() ?: JSONObject()

    fun get(context: Context, bookUrl: String): Map<String, String> {
        val o = all(context).optJSONObject(LiburuShelves.keyOf(bookUrl)) ?: return emptyMap()
        return FIELDS.mapNotNull { k -> o.optString(k).takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    }

    fun save(context: Context, bookUrl: String, values: Map<String, String>) {
        val root = all(context)
        val o = JSONObject()
        values.forEach { (k, v) -> if (v.isNotBlank()) o.put(k, v.trim()) }
        // An emptied entry stays, with its time, so the other devices drop theirs too.
        root.put(LiburuShelves.keyOf(bookUrl), o.put("t", System.currentTimeMillis()))
        File(context.filesDir, FILE).writeText(root.toString())
        version.value++
    }

    /** Everything, for the device's file on Drive. */
    fun export(context: Context): JSONObject = all(context)

    /** Takes each book's edits from another device when they are newer here. */
    fun merge(context: Context, remote: JSONObject) {
        val root = all(context)
        var changed = false
        remote.keys().forEach { k ->
            val theirs = remote.optJSONObject(k) ?: return@forEach
            val mine = root.optJSONObject(k)
            if (theirs.optLong("t") > (mine?.optLong("t") ?: 0L)) { root.put(k, theirs); changed = true }
        }
        if (changed) {
            File(context.filesDir, FILE).writeText(root.toString())
            version.value++
        }
    }

    /** The book as the reader wants it shown. */
    fun apply(details: BookDetails, e: Map<String, String>, net: JSONObject? = null): BookDetails {
        // What the internet found fills in the publishing data (the EPUB's is
        // often the scanner's); the reader's own edits win over both.
        val gb = net?.optJSONObject("gb")
        if (e.isEmpty() && gb == null) return details
        fun g(k: String) = gb?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
        val m0 = details.metadata
        val m = m0.copy(
            publishers = g("publisher")?.let { listOf(it) } ?: m0.publishers,
            published = g("publishedDate") ?: m0.published,
            pageCount = gb?.optInt("pageCount")?.takeIf { it > 0 } ?: m0.pageCount,
            identifiers = if (m0.identifiers.none { it.scheme == "isbn" } && g("isbn13") != null) {
                listOf(Identifier("isbn", g("isbn13")!!)) + m0.identifiers
            } else m0.identifiers,
        )
        val isbn = e["isbn"]?.filter { it.isLetterOrDigit() }
        return details.copy(
            book = details.book.copy(
                title = e["title"] ?: details.book.title,
                author = e["author"] ?: details.book.author,
            ),
            metadata = m.copy(
                published = e["year"] ?: m.published,
                subjects = e["genres"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: m.subjects,
                publishers = e["publisher"]?.let { listOf(it) } ?: m.publishers,
                identifiers = if (isbn != null) listOf(Identifier("isbn", isbn)) + m.identifiers.filter { it.scheme != "isbn" } else m.identifiers,
                pageCount = e["pages"]?.toIntOrNull() ?: m.pageCount,
                languages = e["language"]?.let { listOf(it) } ?: m.languages,
                description = e["synopsis"] ?: m.description,
            ),
        )
    }
}

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
        if (o.length() == 0) root.remove(LiburuShelves.keyOf(bookUrl)) else root.put(LiburuShelves.keyOf(bookUrl), o.put("t", System.currentTimeMillis()))
        File(context.filesDir, FILE).writeText(root.toString())
        version.value++
    }

    /** The book as the reader wants it shown. */
    fun apply(details: BookDetails, e: Map<String, String>): BookDetails {
        if (e.isEmpty()) return details
        val m = details.metadata
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

package com.chmouel.liseur.ui.discover

import android.content.Context
import com.chmouel.liseur.container
import com.chmouel.liseur.data.opds.FreeCatalog
import com.chmouel.liseur.data.remote.RemoteBook
import com.chmouel.liseur.data.remote.ServerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The free classics of Descubrir: Project Gutenberg (Spanish, most read),
 * textos.info and ManyBooks, each saved once as an anonymous catalog so
 * «Añadir a mi biblioteca» downloads through Liseur's own browse path.
 */
object LiburuClassics {
    /** One row of Descubrir: a saved catalog and the shelf of it to show. */
    data class Source(val key: String, val name: String, val catalog: FreeCatalog, val shelf: String)

    // ManyBooks answers apps with a Cloudflare challenge, so textos.info
    // gives a second row instead.
    val SOURCES = listOf(
        Source("textos-populares", "textos.info", FreeCatalog.TEXTOS_INFO, "https://www.textos.info/populares.atom"),
        Source("gutenberg-es", "Project Gutenberg", FreeCatalog.GUTENBERG, "gutenberg:shelf:es:POPULAR"),
    )
    private val cache = HashMap<String, List<RemoteBook>>()
    private val ids = HashMap<FreeCatalog, Long>()

    fun byKey(key: String) = SOURCES.firstOrNull { it.key == key } ?: SOURCES.first()

    suspend fun serverId(context: Context, source: FreeCatalog): Long? = ids[source] ?: withContext(Dispatchers.IO) {
        val c = context.container
        val saved = c.database.remoteServerDao().browseServers().firstOrNull { FreeCatalog.of(it) == source }?.id
        (saved ?: c.browseCatalog.connect(ServerKind.CUSTOM, source.address, "", "", "", "", false).id)
            ?.also { ids[source] = it }
    }

    /** About fifteen books to start from, cached for the session. */
    suspend fun books(context: Context, row: Source, fresh: Boolean = false): List<RemoteBook> {
        if (!fresh) cache[row.key]?.let { return it }
        val id = serverId(context, row.catalog) ?: return emptyList()
        val repo = context.container.browseCatalog
        val found = runCatching {
            var listing = repo.listing(id, categoryId = row.shelf)
            if (listing.books.size < 5 && listing.categories.isNotEmpty()) {
                val pick = listing.categories.firstOrNull { PREFERRED.containsMatchIn(it.title) } ?: listing.categories.first()
                listing = repo.listing(id, categoryId = pick.id)
            }
            val all = listing.books.toMutableList()
            listing.nextPage?.takeIf { all.size < 15 }?.let { page ->
                all += runCatching { repo.listing(id, categoryId = row.shelf, page = page).books }.getOrDefault(emptyList())
            }
            all.filter { it.downloadHref != null }.distinctBy { it.remoteId }.take(15)
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) cache[row.key] = found
        return found
    }

    /** The source's own search (OPDS), or its most read when [q] is blank. */
    suspend fun search(context: Context, row: Source, q: String): List<RemoteBook> {
        if (q.isBlank()) return books(context, row)
        val id = serverId(context, row.catalog) ?: return emptyList()
        val enc = java.net.URLEncoder.encode(q.trim(), "UTF-8")
        val url = when (row.catalog) {
            FreeCatalog.TEXTOS_INFO -> "https://www.textos.info/busqueda.atom?query=$enc"
            else -> "https://www.gutenberg.org/ebooks/search.opds/?query=$enc"
        }
        return runCatching { context.container.browseCatalog.listing(id, categoryId = url).books.filter { it.downloadHref != null }.distinctBy { it.remoteId }.take(20) }
            .getOrDefault(emptyList())
    }

    fun coverUrl(source: FreeCatalog, book: RemoteBook): String? {
        val href = book.coverHref ?: return null
        return source.address.toHttpUrlOrNull()?.resolve(href)?.toString() ?: href
    }

    private val PREFERRED = Regex("popular|más leídos|mas leidos|recomend|destacad|nuev|new|recent|top", RegexOption.IGNORE_CASE)
}

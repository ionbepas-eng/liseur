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
    val SOURCES = listOf(FreeCatalog.TEXTOS_INFO, FreeCatalog.GUTENBERG, FreeCatalog.MANYBOOKS)
    private val cache = HashMap<FreeCatalog, List<RemoteBook>>()
    private val ids = HashMap<FreeCatalog, Long>()

    fun name(source: FreeCatalog) = when (source) {
        FreeCatalog.GUTENBERG -> "Project Gutenberg"
        FreeCatalog.TEXTOS_INFO -> "textos.info"
        FreeCatalog.MANYBOOKS -> "ManyBooks"
        FreeCatalog.GALLICA -> "Gallica"
    }

    suspend fun serverId(context: Context, source: FreeCatalog): Long? = ids[source] ?: withContext(Dispatchers.IO) {
        val c = context.container
        val saved = c.database.remoteServerDao().browseServers().firstOrNull { FreeCatalog.of(it) == source }?.id
        (saved ?: c.browseCatalog.connect(ServerKind.CUSTOM, source.address, "", "", "", "", false).id)
            ?.also { ids[source] = it }
    }

    /** About fifteen books to start from, cached for the session. */
    suspend fun books(context: Context, source: FreeCatalog, fresh: Boolean = false): List<RemoteBook> {
        if (!fresh) cache[source]?.let { return it }
        val id = serverId(context, source) ?: return emptyList()
        val repo = context.container.browseCatalog
        val found = runCatching {
            var listing = if (source == FreeCatalog.GUTENBERG) {
                repo.listing(id, categoryId = "gutenberg:shelf:es:POPULAR")
            } else {
                repo.listing(id)
            }
            if (listing.books.size < 5 && listing.categories.isNotEmpty()) {
                val pick = listing.categories.firstOrNull { PREFERRED.containsMatchIn(it.title) } ?: listing.categories.first()
                listing = repo.listing(id, categoryId = pick.id)
            }
            val all = listing.books.toMutableList()
            listing.nextPage?.takeIf { all.size < 15 }?.let { page ->
                all += runCatching { repo.listing(id, categoryId = null, page = page).books }.getOrDefault(emptyList())
            }
            all.filter { it.downloadHref != null }.distinctBy { it.remoteId }.take(15)
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) cache[source] = found
        return found
    }

    fun coverUrl(source: FreeCatalog, book: RemoteBook): String? {
        val href = book.coverHref ?: return null
        return source.address.toHttpUrlOrNull()?.resolve(href)?.toString() ?: href
    }

    private val PREFERRED = Regex("popular|más leídos|mas leidos|recomend|destacad|nuev|new|recent|top", RegexOption.IGNORE_CASE)
}

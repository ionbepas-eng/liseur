package com.chmouel.liseur.ui.library

import android.content.Context
import com.chmouel.liseur.data.gdrive.HardcoverSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * A book's data from the internet, as the web's enrich.js gathers it:
 * Google Books (publisher, date, pages, genres, ISBN, other books by the
 * author) and Hardcover through the user's Worker (rating, readers,
 * reviews, how the ratings spread). Kept 30 days; «Actualizar datos»
 * fetches it again.
 */
object LiburuEnrich {
    private const val FILE = "liburu-enrich.json"
    const val TTL = 30L * 86_400_000L
    val version = MutableStateFlow(0)
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    private fun all(context: Context): JSONObject =
        runCatching { JSONObject(File(context.filesDir, FILE).readText()) }.getOrNull() ?: JSONObject()

    fun get(context: Context, bookUrl: String): JSONObject? = all(context).optJSONObject(LiburuShelves.keyOf(bookUrl))

    private fun put(context: Context, bookUrl: String, data: JSONObject) {
        val root = all(context).put(LiburuShelves.keyOf(bookUrl), data)
        File(context.filesDir, FILE).writeText(root.toString())
        version.value++
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun short(t: String) = norm(t.split(Regex("[:(\\[]"))[0])
    private fun sameTitle(a: String?, b: String?): Boolean {
        val x = short(a.orEmpty()); val y = short(b.orEmpty())
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
    }

    private fun getJson(url: String): JSONObject? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) JSONObject(it.body.string()) else null }
    }.getOrNull()

    private fun https(u: String?) = u?.replace("http:", "https:")?.replace("&edge=curl", "")

    /** Fetches everything again; returns false when nothing at all could be reached. */
    /** A comic: one the reader has opened as such, or a Drive file that holds pages rather than an EPUB. */
    fun isComic(bookUrl: String) = LiburuComics.isComic(bookUrl) ||
        (bookUrl.startsWith("gdrive:") && com.chmouel.liseur.data.gdrive.LiburuPeek.of(bookUrl.removePrefix("gdrive:"))?.optBoolean("comic") == true) ||
        bookUrl.substringBefore('?').lowercase().let { it.endsWith(".cbz") || it.endsWith(".cbr") }

    suspend fun refresh(context: Context, bookUrl: String, title: String, author: String?, isbn: String?, language: String?): Boolean =
        withContext(Dispatchers.IO) {
            val out = JSONObject().put("t", System.currentTimeMillis())
            // Comics: an ISBN (a tomo) still goes to Google Books first; an issue goes to the Grand Comics Database.
            val comic = isComic(bookUrl)
            val bookIsbn = isbn?.filter(Char::isDigit)?.takeIf { (it.length == 13 && (it.startsWith("978") || it.startsWith("979"))) || it.length == 10 }
            val gb = if (comic) {
                (bookIsbn?.let { runCatching { google(title, author, it, language) }.getOrNull() })
                    ?: runCatching { LiburuComicInfo.lookup(title, language) }.getOrNull()
            } else runCatching { google(title, author, isbn, language) }.getOrNull()
            gb?.let { out.put("gb", it) }
            val by = runCatching { byAuthor(title, author) }.getOrNull()
            by?.let { out.put("byAuthor", it) }
            val hc = runCatching { hardcover(context, title, author, gb?.optString("isbn13")?.takeIf { it.isNotEmpty() } ?: isbn) }.getOrNull()
            hc?.let { out.put("hc", it) }
            val ok = gb != null || hc != null || (by?.length() ?: 0) > 0
            if (ok) put(context, bookUrl, out)
            ok
        }

    private fun google(title: String, author: String?, isbn: String?, language: String?): JSONObject? {
        val queries = listOfNotNull(
            isbn?.filter { it.isLetterOrDigit() }?.takeIf { it.length >= 10 }?.let { "isbn:$it" },
            "intitle:\"${short(title)}\"" + (author?.split(' ')?.lastOrNull()?.takeIf { it.isNotBlank() }?.let { " inauthor:\"$it\"" } ?: ""),
        )
        for (q in queries) {
            val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder()
                .addQueryParameter("q", q).addQueryParameter("maxResults", "8").addQueryParameter("printType", "books").build()
            val items = getJson(url.toString())?.optJSONArray("items") ?: continue
            val vols = (0 until items.length()).map { items.getJSONObject(it).optJSONObject("volumeInfo") ?: JSONObject() }
                .filter { q.startsWith("isbn:") || sameTitle(it.optString("title"), title) }
            if (vols.isEmpty()) continue
            val lang = language?.take(2)
            val sorted = vols.sortedWith(
                compareByDescending<JSONObject> { it.optString("language") == lang }
                    .thenByDescending { it.optInt("ratingsCount") }
                    .thenByDescending { it.optInt("pageCount") },
            )
            val best = sorted.first()
            fun ids(v: JSONObject, type: String): String {
                val a = v.optJSONArray("industryIdentifiers") ?: return ""
                return (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("type") == type }?.optString("identifier").orEmpty()
            }
            val cats = sorted.take(4).flatMap { v -> v.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList() }.distinct().take(6)
            val rated = sorted.firstOrNull { it.optInt("ratingsCount") > 0 }
            return JSONObject()
                .put("publisher", best.optString("publisher"))
                .put("publishedDate", best.optString("publishedDate"))
                .put("pageCount", best.optInt("pageCount").takeIf { it > 0 } ?: sorted.firstOrNull { it.optInt("pageCount") > 0 }?.optInt("pageCount") ?: 0)
                .put("categories", JSONArray(cats))
                .put("language", best.optString("language"))
                .put("isbn13", ids(best, "ISBN_13"))
                .put("isbn10", ids(best, "ISBN_10"))
                .put("rating", rated?.optDouble("averageRating") ?: 0.0)
                .put("ratingsCount", rated?.optInt("ratingsCount") ?: 0)
                .put("link", best.optString("infoLink"))
                .put("desc", sorted.firstOrNull { it.optString("description").length > 80 }?.optString("description")
                    ?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(1500).orEmpty())
        }
        return null
    }

    private fun byAuthor(title: String, author: String?): JSONArray? {
        val a = author?.takeIf { it.isNotBlank() } ?: return null
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", "inauthor:\"$a\"").addQueryParameter("maxResults", "20")
            .addQueryParameter("printType", "books").addQueryParameter("orderBy", "relevance").build()
        val items = getJson(url.toString())?.optJSONArray("items") ?: return null
        val seen = HashSet<String>()
        val out = JSONArray()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val v = it.optJSONObject("volumeInfo") ?: continue
            val t = v.optString("title")
            if (t.isBlank() || sameTitle(t, title) || !seen.add(short(t))) continue
            val cover = https(v.optJSONObject("imageLinks")?.let { l -> l.optString("thumbnail").ifEmpty { l.optString("smallThumbnail") } })
            out.put(
                JSONObject().put("title", t)
                    .put("author", v.optJSONArray("authors")?.let { a2 -> (0 until a2.length()).joinToString(", ") { a2.getString(it) } }.orEmpty())
                    .put("year", v.optString("publishedDate").take(4))
                    .put("cover", cover.orEmpty())
                    .put("link", v.optString("infoLink")),
            )
            if (out.length() >= 12) break
        }
        return out
    }

    private fun hardcover(context: Context, title: String, author: String?, isbn: String?): JSONObject? {
        val hc = HardcoverSync(context, http)
        val search = "query Buscar(\$q: String!) { search(query: \$q, query_type: \"Book\", per_page: 6, page: 1) { results } }"
        var doc: JSONObject? = null
        for (q in listOfNotNull(isbn?.takeIf { it.isNotBlank() }, "${short(title)} ${author.orEmpty()}".trim())) {
            val hits = hc.gql(search, JSONObject().put("q", q))?.optJSONObject("search")?.optJSONObject("results")?.optJSONArray("hits") ?: continue
            val docs = (0 until hits.length()).mapNotNull { hits.getJSONObject(it).optJSONObject("document") }
            doc = if (q == isbn) docs.firstOrNull() else docs.firstOrNull { sameTitle(it.optString("title"), title) }
            if (doc != null) break
        }
        if (doc == null) return JSONObject().put("found", false)
        val id = doc.optString("id").toIntOrNull() ?: return JSONObject().put("found", false)
        val out = JSONObject().put("found", true).put("id", id).put("slug", doc.optString("slug"))
            .put("rating", doc.optDouble("rating", 0.0)).put("ratingsCount", doc.optInt("ratings_count"))
            .put("readers", doc.optInt("users_read_count").takeIf { it > 0 } ?: doc.optInt("users_count"))
            .put("genres", doc.optJSONArray("genres") ?: JSONArray())
            .put("link", doc.optString("slug").takeIf { it.isNotEmpty() }?.let { "https://hardcover.app/books/$it" }.orEmpty())
        runCatching {
            val r = hc.gql(
                "query Opiniones(\$id: Int!) { user_books(where: {book_id: {_eq: \$id}, has_review: {_eq: true}}, order_by: {likes_count: desc}, limit: 5) { rating review_raw reviewed_at likes_count user { username } } }",
                JSONObject().put("id", id),
            )
            val ub = r?.optJSONArray("user_books") ?: JSONArray()
            val reviews = JSONArray()
            for (i in 0 until ub.length()) {
                val x = ub.getJSONObject(i)
                val text = x.optString("review_raw").takeIf { it.isNotBlank() && it != "null" } ?: continue
                reviews.put(
                    JSONObject().put("user", x.optJSONObject("user")?.optString("username") ?: "Lector")
                        .put("rating", x.optDouble("rating", 0.0)).put("text", text.take(1200))
                        .put("date", x.optString("reviewed_at")),
                )
            }
            out.put("reviews", reviews)
        }
        runCatching {
            val counts = JSONArray()
            for ((lo, hi) in listOf(4.5 to 5.0, 3.5 to 4.0, 2.5 to 3.0, 1.5 to 2.0, 0.5 to 1.0)) {
                val r = hc.gql(
                    "query Dist(\$id: Int!, \$lo: numeric!, \$hi: numeric!) { user_books_aggregate(where: {book_id: {_eq: \$id}, rating: {_gte: \$lo, _lte: \$hi}}) { aggregate { count } } }",
                    JSONObject().put("id", id).put("lo", lo).put("hi", hi),
                )
                counts.put(r?.optJSONObject("user_books_aggregate")?.optJSONObject("aggregate")?.optInt("count") ?: 0)
            }
            out.put("dist", counts)
        }
        return out
    }
}

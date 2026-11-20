package com.chmouel.liseur.ui.discover

import android.content.Context
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.gdrive.DriveStateSync
import com.chmouel.liseur.data.gdrive.LiburuCovers
import com.chmouel.liseur.ui.library.LiburuShelves
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** Descubrir's rows from the internet, as the web's discover.js builds them (Google Books). */
object LiburuDiscoverNet {
    data class Ext(val key: String, val title: String, val author: String, val year: String, val cover: String, val link: String, val desc: String, val why: String) {
        fun json(): JSONObject = JSONObject().put("key", key).put("title", title).put("author", author).put("year", year)
            .put("cover", cover).put("link", link).put("source", "Google Books").put("desc", desc)
    }
    data class Row(val title: String, val sub: String, val items: List<Ext>)

    private const val TTL = 3L * 86_400_000L
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .split(Regex("[:(\\[]"))[0].replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun cacheFile(context: Context) = File(context.cacheDir, "liburu-discover.json")

    private fun cached(context: Context, key: String, fetch: () -> JSONArray): JSONArray {
        val f = cacheFile(context)
        val all = runCatching { JSONObject(f.readText()) }.getOrNull() ?: JSONObject()
        all.optJSONObject(key)?.takeIf { System.currentTimeMillis() - it.optLong("t") < TTL }?.let { return it.getJSONArray("v") }
        return runCatching { fetch() }.onSuccess { v -> f.writeText(all.put(key, JSONObject().put("t", System.currentTimeMillis()).put("v", v)).toString()) }
            .getOrElse { all.optJSONObject(key)?.optJSONArray("v") ?: JSONArray() }
    }

    /** Google Books without a key runs out of its shared quota; Open Library then answers instead. */
    private fun gb(q: String, orderBy: String = "relevance", max: Int = 20): JSONArray =
        runCatching { gbOnly(q, orderBy, max) }.getOrNull()?.takeIf { it.length() > 0 } ?: ol(q, orderBy, max)

    private fun ol(q: String, orderBy: String, max: Int): JSONArray {
        val author = Regex("inauthor:\"([^\"]+)\"").find(q)?.groupValues?.get(1)
        val subject = Regex("subject:(?:\"([^\"]+)\"|(\\S+))").find(q)?.groupValues?.let { it[1].ifEmpty { it[2] } }
        val rest = q.replace(Regex("(inauthor|subject):(\"[^\"]*\"|\\S+)"), " ").trim()
        val b = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "$max").addQueryParameter("fields", "key,title,author_name,first_publish_year,cover_i")
        when {
            author != null -> b.addQueryParameter("author", author).addQueryParameter("sort", if (orderBy == "newest") "new" else "rating")
            else -> b.addQueryParameter("q", listOfNotNull(subject?.let { "subject:\"${it.trim()}\"" }, rest.takeIf { it.isNotBlank() }).joinToString(" ")).addQueryParameter("sort", "rating")
        }
        val docs = http.newCall(Request.Builder().url(b.build()).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("ol ${r.code}")
            JSONObject(r.body.string()).optJSONArray("docs") ?: JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val cover = d.optLong("cover_i").takeIf { it > 0 } ?: continue
            out.put(
                JSONObject().put("key", "ol:" + d.optString("key")).put("title", d.optString("title"))
                    .put("author", d.optJSONArray("author_name")?.optString(0).orEmpty())
                    .put("date", d.optInt("first_publish_year").takeIf { it > 0 }?.toString().orEmpty())
                    .put("cover", "https://covers.openlibrary.org/b/id/$cover-M.jpg")
                    .put("link", "https://openlibrary.org" + d.optString("key")).put("desc", ""),
            )
        }
        return out
    }

    private fun gbOnly(q: String, orderBy: String = "relevance", max: Int = 20): JSONArray {
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder().addQueryParameter("q", q)
            .addQueryParameter("maxResults", "$max").addQueryParameter("printType", "books").addQueryParameter("orderBy", orderBy).build()
        val items = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("gb ${r.code}")
            JSONObject(r.body.string()).optJSONArray("items") ?: JSONArray()
        }
        val out = JSONArray()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val v = it.optJSONObject("volumeInfo") ?: continue
            val cover = v.optJSONObject("imageLinks")?.let { l -> l.optString("thumbnail").ifEmpty { l.optString("smallThumbnail") } }
                ?.replace("http:", "https:")?.replace("&edge=curl", "").orEmpty()
            if (v.optString("title").isBlank() || cover.isEmpty()) continue
            out.put(
                JSONObject().put("key", "gb:" + it.optString("id")).put("title", v.optString("title"))
                    .put("author", v.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }.orEmpty())
                    .put("date", v.optString("publishedDate")).put("cover", cover).put("link", v.optString("infoLink"))
                    .put("desc", v.optString("description").replace(Regex("<[^>]+>"), "").take(600)),
            )
        }
        return out
    }

    private fun toExt(a: JSONArray, why: String) = (0 until a.length()).map { a.getJSONObject(it) }.map {
        Ext(it.optString("key"), it.optString("title"), it.optString("author"), it.optString("date").take(4), it.optString("cover"), it.optString("link"), it.optString("desc"), why)
    }

    private val SHELF_Q = mapOf(
        "cine" to "subject:\"Performing Arts\" film history", "clásicos" to "subject:fiction classics", "clasicos" to "subject:fiction classics",
        "ensayo" to "subject:\"Social Science\" ensayo", "ensayo gay" to "subject:\"Social Science\" gay studies", "lgtb" to "subject:\"LGBTQ+\" history",
        "fantasía medieval" to "subject:fiction epic fantasy", "fantasia medieval" to "subject:fiction epic fantasy", "fantasía" to "subject:fiction fantasy",
        "fitness" to "subject:\"Health & Fitness\" strength training", "historia" to "subject:history", "novela" to "subject:fiction literary novela",
        "novela gay" to "subject:fiction gay", "pop" to "subject:\"Biography & Autobiography\" entertainment hollywood",
    )

    /** All the rows the web shows after the library's own: authors' news, «Porque te gustó…», by shelf. */
    suspend fun rows(context: Context, books: List<Book>, rated: Set<String>): List<Row> = withContext(Dispatchers.IO) {
        val owned = books.map { norm(it.title) }.toSet()
        fun notOwned(list: List<Ext>): List<Ext> { val seen = HashSet<String>(); return list.filter { val k = norm(it.title); k !in owned && seen.add(k) } }
        val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull() ?: JSONObject()
        val web = state.optJSONObject("books") ?: JSONObject()
        fun meta(b: Book) = web.optJSONObject(LiburuShelves.keyOf(b.url))?.optJSONObject("meta")
        val finished = books.filter { it.finished || meta(it)?.optString("status") == "finished" }
        val out = ArrayList<Row>()
        // Authors you like, and what they published in the last two years.
        val authors = finished.mapNotNull { it.author?.takeIf { a -> a.isNotBlank() && !Regex("desconocido|varios|an[oó]nimo", RegexOption.IGNORE_CASE).containsMatchIn(a) } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
        val limit = java.time.LocalDate.now().minusYears(2).toString()
        val news = authors.flatMap { a ->
            toExt(cached(context, "news:$a") { gb("inauthor:\"$a\"", "newest", 12) }, "Nuevo de $a")
                .filter { it.year.isNotEmpty() && norm(it.author).contains(norm(a).split(' ').last()) }
        }.filter { (it.year + "-12-31") >= limit }
        notOwned(news).sortedByDescending { it.year }.take(16).takeIf { it.isNotEmpty() }?.let { out += Row("Novedades de tus autores", "Publicados en los dos últimos años", it) }
        // «Porque te gustó…»: your best rated (or latest finished), by their genre.
        val rating = { b: Book -> meta(b)?.optDouble("rating")?.takeIf { !it.isNaN() } ?: if (b.url in rated) 4.0 else 0.0 }
        val fav = finished.filter { rating(it) >= 4 }.sortedByDescending { rating(it) }.take(2).ifEmpty {
            finished.sortedByDescending { it.finishedAt ?: 0L }.take(2)
        }
        for (b in fav) {
            // The genre as the web takes it: Google Books' first category, else the file's own subjects.
            val cat = (com.chmouel.liseur.ui.library.LiburuEnrich.get(context, b.url)?.optJSONObject("gb")?.optJSONArray("categories")?.optString(0)?.takeIf { it.isNotBlank() }
                ?: b.url.takeIf { it.startsWith("gdrive:") }?.let { LiburuCovers.genres(it.removePrefix("gdrive:")) }?.optJSONArray("c")?.optString(0)
                ?: b.url.takeIf { it.startsWith("gdrive:") }?.let { com.chmouel.liseur.data.gdrive.LiburuPeek.of(it.removePrefix("gdrive:")) }?.optJSONArray("subjects")?.optString(0))
                ?.split("/")?.first()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val items = notOwned(toExt(cached(context, "cat:$cat") { gb("subject:\"$cat\"", max = 15) }, "Porque leíste ${b.title}")).take(14)
            if (items.isNotEmpty()) out += Row("Porque te gustó ${b.title}", "Libros sobre los mismos temas", items)
        }
        // Your biggest shelves, with the genre's most read.
        val (shelves, membership) = LiburuShelves.load(context)
        val count = HashMap<String, Int>()
        membership.values.forEach { ids -> ids.forEach { count[it] = (count[it] ?: 0) + 1 } }
        shelves.filter { (count[it.id] ?: 0) >= 3 }.sortedByDescending { count[it.id] }.take(4).forEach { s ->
            val q = SHELF_Q[s.name.lowercase()] ?: "subject:\"${s.name}\""
            val items = notOwned(toExt(cached(context, "shelf:${s.name}") { gb(q) }, "Para tu estantería ${s.name}")).take(14)
            if (items.isNotEmpty()) out += Row(s.name, "Para tu estantería · más leídos del género", items)
        }
        out
    }
}

package com.chmouel.liseur.data.gdrive

import android.content.Context
import android.util.Log
import com.chmouel.liseur.data.db.BookDao
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Covers for the Drive books not downloaded yet, as the web finds them:
 * Google Books first, then Open Library. Kept by Drive id so the catalog
 * hands them back on every refresh; a miss is retried after a month.
 */
object LiburuCovers {
    private const val FILE = "liburu-covers.json"
    private const val RETRY = 30L * 86_400_000L
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    @Volatile private var memo: JSONObject? = null
    private var dir: File? = null

    fun init(context: Context) { dir = context.applicationContext.filesDir }

    private fun all(): JSONObject = memo ?: (
        runCatching { JSONObject(File(dir, FILE).readText()) }.getOrNull() ?: JSONObject()
    ).also { memo = it }

    @Synchronized private fun put(id: String, url: String) {
        val root = all().put(id, JSONObject().put("u", url).put("t", System.currentTimeMillis()))
        runCatching { File(dir, FILE).writeText(root.toString()) }
    }

    /** The cover found for Drive file [id], if any. */
    fun cached(id: String): String? = all().optJSONObject(id)?.optString("u")?.takeIf { it.isNotEmpty() }

    /** Looks up to [limit] coverless Drive books; returns how many it found. */
    suspend fun fill(bookDao: BookDao, limit: Int = 80): Int {
        if (dir == null) return 0
        val prefix = "gdrive:"
        val now = System.currentTimeMillis()
        val todo = bookDao.allOnce().filter { b ->
            b.url.startsWith(prefix) && b.coverPath == null && b.coverUrl.isNullOrEmpty() &&
                all().optJSONObject(b.url.removePrefix(prefix))?.let { e ->
                    e.optString("u").isEmpty() && now - e.optLong("t") > RETRY
                } ?: true
        }.take(limit)
        var found = 0
        for (b in todo) {
            val id = b.url.removePrefix(prefix)
            val url = runCatching { google(b.title, b.author) ?: openLibrary(b.title, b.author) }
                .onFailure { Log.i("LiburuCovers", "lookup failed", it) }.getOrNull()
            put(id, url.orEmpty())
            if (url != null) { bookDao.setFoundCover(b.url, url); found++ }
            Thread.sleep(350)
        }
        return found
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun short(t: String) = norm(t.split(Regex("[:(\\[]"))[0])
    private fun same(a: String, b: String): Boolean {
        val x = short(a); val y = short(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
    }

    private fun get(url: String): JSONObject? =
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) JSONObject(it.body.string()) else null }

    private fun google(title: String, author: String?): String? {
        val q = "intitle:\"${short(title)}\"" + (author?.split(' ')?.lastOrNull()?.takeIf { it.isNotBlank() }?.let { " inauthor:\"$it\"" } ?: "")
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", q).addQueryParameter("maxResults", "6").addQueryParameter("printType", "books").build()
        val items = get(url.toString())?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val v = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
            if (!same(v.optString("title"), title)) continue
            val img = v.optJSONObject("imageLinks")?.let { it.optString("thumbnail").ifEmpty { it.optString("smallThumbnail") } }
                ?.takeIf { it.isNotEmpty() } ?: continue
            return img.replace("http:", "https:").replace("&edge=curl", "")
        }
        return null
    }

    private fun openLibrary(title: String, author: String?): String? {
        val b = "https://openlibrary.org/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("title", short(title)).addQueryParameter("limit", "5").addQueryParameter("fields", "title,cover_i")
        author?.takeIf { it.isNotBlank() }?.let { b.addQueryParameter("author", it) }
        val docs = get(b.build().toString())?.optJSONArray("docs") ?: return null
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val cover = d.optInt("cover_i")
            if (cover > 0 && same(d.optString("title"), title)) return "https://covers.openlibrary.org/b/id/$cover-L.jpg"
        }
        return null
    }
}

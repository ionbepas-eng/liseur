package com.chmouel.liseur.audio

import android.content.Context
import com.chmouel.liseur.data.gdrive.DriveAuth
import com.chmouel.liseur.data.gdrive.DriveStateSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * Audiobooks as the web finds them: in a Drive folder named «Audiolibros»,
 * one subfolder per book (its tracks, in order, and maybe a cover image),
 * or a single audio file. Ids are the web's («a:<folder id>»), so progress
 * read on the web is where the app picks up.
 */
object LiburuAudiobooks {
    data class Track(val id: String, val name: String, val size: Long, val mime: String)
    data class Audiobook(val id: String, val title: String, val author: String, val tracks: List<Track>, val coverId: String?, val cover: String?)

    private const val FILE = "liburu-audiobooks.json"
    val list = MutableStateFlow<List<Audiobook>>(emptyList())
    val lastError = MutableStateFlow<String?>(null)
    private val AUDIO = Regex("\\.(mp3|m4a|m4b|aac|ogg|opus|flac|wav)$", RegexOption.IGNORE_CASE)
    private val IMG = Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
    private const val FOLDER = "application/vnd.google-apps.folder"

    @Volatile var app: Context? = null

    /** The Drive id of an audiobook (its folder, or its single file), when this url is one. */
    fun driveIdOf(url: String): String? = url.takeIf { it.startsWith("gdrive:") }?.removePrefix("gdrive:")?.takeIf { id -> list.value.any { it.id == "a:$id" } }
    fun isAudio(url: String) = driveIdOf(url) != null
    fun byUrl(url: String) = driveIdOf(url)?.let { id -> list.value.firstOrNull { it.id == "a:$id" } }

    /** Where a downloaded audiobook's tracks are kept. */
    fun dir(context: Context, book: Audiobook) = File(context.filesDir, "audio/" + book.id.removePrefix("a:"))
    fun localTrack(context: Context, book: Audiobook, t: Track): File? = File(dir(context, book), t.id).takeIf { it.exists() && it.length() > 0 }

    /** Downloads every track (resuming what is there), reporting the share done. */
    fun downloadAll(context: Context, book: Audiobook, onProgress: (Float) -> Unit) {
        val d = dir(context, book).apply { mkdirs() }
        val total = book.tracks.sumOf { it.size.coerceAtLeast(1) }
        var done = 0L
        for (t in book.tracks) {
            val f = File(d, t.id)
            if (f.exists() && t.size > 0 && f.length() == t.size) { done += t.size; onProgress(done.toFloat() / total); continue }
            val part = File(d, t.id + ".part")
            http.newCall(Request.Builder().url("${DriveAuth.BASE_URL}/files/${t.id}?alt=media").build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("Drive respondió ${r.code}")
                r.body.byteStream().use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var n: Int; var since = 0L
                    while (input.read(buf).also { n = it } != -1) { out.write(buf, 0, n); since += n; if (since > 1_000_000) { done += since; since = 0; onProgress(done.toFloat() / total) } }
                    done += since
                } }
            }
            part.renameTo(f)
            onProgress(done.toFloat() / total)
        }
    }

    fun removeLocal(context: Context, url: String) { byUrl(url)?.let { dir(context, it).deleteRecursively() } }

    fun init(context: Context) {
        app = context.applicationContext
        if (list.value.isNotEmpty()) return
        list.value = runCatching { parse(JSONArray(File(context.filesDir, FILE).readText())) }.getOrDefault(emptyList())
    }

    private fun parse(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        val t = o.getJSONArray("tracks")
        Audiobook(o.getString("id"), o.getString("title"), o.optString("author"),
            (0 until t.length()).map { t.getJSONObject(it) }.map { Track(it.getString("id"), it.getString("name"), it.optLong("size"), it.optString("mime")) },
            o.optString("coverId").ifEmpty { null }, o.optString("cover").ifEmpty { null })
    }

    private fun save(context: Context, books: List<Audiobook>) {
        val a = JSONArray()
        books.forEach { b ->
            a.put(JSONObject().put("id", b.id).put("title", b.title).put("author", b.author).put("coverId", b.coverId ?: "").put("cover", b.cover ?: "")
                .put("tracks", JSONArray(b.tracks.map { JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("mime", it.mime) })))
        }
        File(context.filesDir, FILE).writeText(a.toString())
        list.value = books
    }

    private val http by lazy { DriveAuth.client() }
    private fun children(id: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var token: String? = null
        do {
            val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$id' in parents and trashed=false").addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size)")
                .apply { token?.let { addQueryParameter("pageToken", it) } }.build()
            val j = http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (!r.isSuccessful) throw java.io.IOException("Drive ${r.code}"); JSONObject(r.body.string()) }
            j.optJSONArray("files")?.let { f -> (0 until f.length()).forEach { out += f.getJSONObject(it) } }
            token = j.optString("nextPageToken").ifEmpty { null }
        } while (token != null)
        return out
    }

    /** «Autor - Título», «Título (Audiolibro)»… as the web reads folder names. */
    private fun fromName(name: String): Pair<String, String> {
        val clean = name.replace(Regex("[\\[(](audio\\s*libro|audiobook|unabridged|íntegro|mp3|m4b)[^\\])]*[\\])]", RegexOption.IGNORE_CASE), "").replace('_', ' ').trim()
        val parts = clean.split(Regex("\\s+[-–—]\\s+"))
        return if (parts.size >= 2) parts.drop(1).joinToString(" - ").trim() to parts[0].trim() else clean to ""
    }

    suspend fun scan(context: Context): Int = withContext(Dispatchers.IO) {
        val q = "name='Audiolibros' and mimeType='$FOLDER' and trashed=false"
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("fields", "files(id,name)").build()
        val roots = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Drive respondió ${r.code}" + if (r.code == 401) " (reconecta Google Drive)" else "")
            JSONObject(r.body.string()).optJSONArray("files")
        } ?: return@withContext list.value.size
        val coll = Collator.getInstance(Locale("es")).apply { strength = Collator.PRIMARY }
        val numeric = Comparator<Track> { a, b ->
            val ka = a.name.replace(Regex("\\d+")) { it.value.padStart(8, '0') }; val kb = b.name.replace(Regex("\\d+")) { it.value.padStart(8, '0') }
            coll.compare(ka, kb)
        }
        val old = list.value.associateBy { it.id }
        val found = ArrayList<Audiobook>()
        for (r in 0 until roots.length()) {
            for (c in children(roots.getJSONObject(r).getString("id"))) {
                val name = c.getString("name")
                if (c.optString("mimeType") == FOLDER) {
                    val tracks = ArrayList<Track>(); var cover: String? = null
                    for (f in children(c.getString("id"))) {
                        val n = f.getString("name")
                        if (f.optString("mimeType") == FOLDER) {
                            children(f.getString("id")).filter { AUDIO.containsMatchIn(it.getString("name")) || it.optString("mimeType").startsWith("audio/") }
                                .forEach { t -> tracks += Track(t.getString("id"), n + "/" + t.getString("name"), t.optLong("size"), t.optString("mimeType")) }
                        } else if (AUDIO.containsMatchIn(n) || f.optString("mimeType").startsWith("audio/")) tracks += Track(f.getString("id"), n, f.optLong("size"), f.optString("mimeType"))
                        else if (IMG.containsMatchIn(n) && (cover == null || Regex("cover|portada|folder|front", RegexOption.IGNORE_CASE).containsMatchIn(n))) cover = f.getString("id")
                    }
                    if (tracks.isEmpty()) continue
                    val (title, author) = fromName(name)
                    val id = "a:" + c.getString("id")
                    found += Audiobook(id, title.ifEmpty { name }, author, tracks.sortedWith(numeric), cover, old[id]?.cover)
                } else if (AUDIO.containsMatchIn(name)) {
                    val (title, author) = fromName(name.replace(AUDIO, ""))
                    val id = "a:" + c.getString("id")
                    found += Audiobook(id, title, author, listOf(Track(c.getString("id"), name, c.optLong("size"), c.optString("mimeType"))), null, old[id]?.cover)
                }
            }
        }
        // Covers: the folder's own image, else the web's choice, else Apple's audiobook artwork.
        val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull()
        val withCovers = found.map { b ->
            val dir = File(context.filesDir, "liburu-audio-covers").apply { mkdirs() }
            val local = File(dir, b.id.removePrefix("a:") + ".jpg")
            val pick = state?.optJSONObject("books")?.optJSONObject(b.id)?.optJSONObject("meta")?.optString("coverPick")?.takeIf { it.startsWith("http") }
            val cover = when {
                local.exists() -> "file://" + local.path
                b.coverId != null -> runCatching {
                    http.newCall(Request.Builder().url("${DriveAuth.BASE_URL}/files/${b.coverId}?alt=media").build()).execute().use { r -> if (r.isSuccessful) local.writeBytes(r.body.bytes()) }
                    "file://" + local.path
                }.getOrNull()
                pick != null -> pick
                b.cover != null -> b.cover
                else -> runCatching { apple(b.title, b.author) }.getOrNull()
            }
            b.copy(cover = cover)
        }.sortedBy { it.title.lowercase() }
        save(context, withCovers)
        lastError.value = null
        withCovers.size
    }

    private fun apple(title: String, author: String): String? {
        val term = java.net.URLEncoder.encode("$title $author".trim(), "UTF-8")
        for (country in listOf("es", "us")) {
            val j = okhttp3.OkHttpClient().newCall(Request.Builder().url("https://itunes.apple.com/search?term=$term&media=audiobook&country=$country&limit=5").build()).execute()
                .use { if (it.isSuccessful) JSONObject(it.body.string()) else null } ?: continue
            val r = j.optJSONArray("results") ?: continue
            for (i in 0 until r.length()) {
                val x = r.getJSONObject(i)
                val name = x.optString("collectionName").lowercase()
                if (name.contains(title.lowercase().take(12))) return x.optString("artworkUrl100").replace(Regex("/\\d+x\\d+(bb)?\\."), "/600x600bb.")
            }
        }
        return null
    }

    // --- Where you are: track and second, kept here and taken from the web when newer -------
    private fun prefs(context: Context) = context.getSharedPreferences("liburu-audio", 0)

    fun position(context: Context, id: String): Pair<Int, Long> {
        val p = prefs(context)
        var track = p.getInt("$id:i", 0); var ms = p.getLong("$id:ms", 0); val t = p.getLong("$id:t", 0)
        val web = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull()
            ?.optJSONObject("books")?.optJSONObject(id)?.optJSONObject("pos")
        if (web != null && web.optLong("t") > t) {
            Regex("^(\\d+):([\\d.]+)$").find(web.optString("cfi"))?.let { m -> track = m.groupValues[1].toInt(); ms = (m.groupValues[2].toDouble() * 1000).toLong() }
        }
        return track to ms
    }

    fun savePosition(context: Context, id: String, track: Int, ms: Long, fraction: Float) {
        prefs(context).edit().putInt("$id:i", track).putLong("$id:ms", ms).putFloat("$id:f", fraction).putLong("$id:t", System.currentTimeMillis()).apply()
    }

    fun fraction(context: Context, id: String): Float = prefs(context).getFloat("$id:f", 0f)
    fun rate(context: Context, id: String) = prefs(context).getFloat("$id:rate", 1f)
    fun setRate(context: Context, id: String, r: Float) = prefs(context).edit().putFloat("$id:rate", r).apply()
}

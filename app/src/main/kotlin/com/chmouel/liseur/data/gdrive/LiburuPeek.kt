package com.chmouel.liseur.data.gdrive

import android.content.Context
import android.util.Log
import com.chmouel.liseur.data.db.BookDao
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * Reads what a Drive EPUB says about itself (series, synopsis, subjects,
 * date, publisher, ISBN and its own cover) without downloading it: a few
 * byte ranges of the zip, its directory, the OPF and the cover image. For
 * a CBZ, the first page becomes the cover. The catalog hands this back on
 * every refresh, so Drive books group into their series as the web does.
 */
object LiburuPeek {
    private const val FILE = "liburu-peek.json"
    private const val TAG = "LiburuPeek"
    @Volatile private var memo: JSONObject? = null
    private var dir: File? = null
    private val http: OkHttpClient by lazy { DriveAuth.client() }

    fun init(context: Context) { dir = context.applicationContext.filesDir }

    private fun all(): JSONObject = memo ?: (
        runCatching { JSONObject(File(dir, FILE).readText()) }.getOrNull() ?: JSONObject()
    ).also { memo = it }

    @Synchronized private fun put(id: String, entry: JSONObject) {
        val root = all().put(id, entry.put("t", System.currentTimeMillis()))
        runCatching { File(dir, FILE).writeText(root.toString()) }
    }

    fun of(id: String): JSONObject? = all().optJSONObject(id)

    /** The cover file read from the book, as a URI the image loader takes. */
    fun coverUri(id: String): String? = of(id)?.optString("cover")?.takeIf { it.isNotEmpty() && File(it).exists() }
        ?.let { "file://$it" }

    /** Reads up to [limit] Drive books not looked at yet; returns how many it read. */
    suspend fun fill(bookDao: BookDao, limit: Int = 40): Int {
        val d = dir ?: return 0
        // Comics read before ComicInfo.xml was looked at are read once more for it.
        val todo = bookDao.allOnce().filter { it.url.startsWith("gdrive:") && of(it.url.removePrefix("gdrive:")).let { p -> p == null || (p.optBoolean("comic") && !p.has("ci")) } }.take(limit)
        var done = 0
        for (b in todo) {
            val id = b.url.removePrefix("gdrive:")
            val entry = runCatching { peek(id, b.sizeBytes, File(d, "liburu-covers").apply { mkdirs() }) }
                .onFailure { Log.i(TAG, "peek failed for a book", it) }.getOrNull()
            // A failed read is noted too, so one broken file is not fetched forever.
            put(id, entry ?: JSONObject().put("failed", true))
            if (entry != null) done++
        }
        return done
    }

    private class Entry(val name: String, val method: Int, val compSize: Long, val size: Long, val offset: Long)

    private fun range(id: String, from: Long, to: Long?): ByteArray {
        val req = Request.Builder().url("${DriveAuth.BASE_URL}/files/$id?alt=media")
            .header("Range", "bytes=$from-${to ?: ""}").build()
        http.newCall(req).execute().use { r ->
            if (r.code != 206 && r.code != 200) throw java.io.IOException("range ${r.code}")
            return r.body.bytes()
        }
    }

    private fun tail(id: String, n: Long): ByteArray {
        val req = Request.Builder().url("${DriveAuth.BASE_URL}/files/$id?alt=media").header("Range", "bytes=-$n").build()
        http.newCall(req).execute().use { r ->
            if (r.code != 206 && r.code != 200) throw java.io.IOException("tail ${r.code}")
            val total = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            lastTotal = total
            return r.body.bytes()
        }
    }
    private var lastTotal: Long? = null

    private fun directory(id: String, size: Long?): List<Entry> {
        val end: ByteArray
        val total: Long
        if (size != null && size > 0) {
            total = size
            end = range(id, maxOf(0, size - 66_000), size - 1)
        } else {
            end = tail(id, 66_000)
            total = lastTotal ?: throw java.io.IOException("no size")
        }
        val bb = ByteBuffer.wrap(end).order(ByteOrder.LITTLE_ENDIAN)
        var eocd = -1
        for (i in end.size - 22 downTo 0) if (bb.getInt(i) == 0x06054b50) { eocd = i; break }
        if (eocd < 0) throw java.io.IOException("no zip end")
        val cdSize = bb.getInt(eocd + 12).toLong() and 0xffffffffL
        val cdOffset = bb.getInt(eocd + 16).toLong() and 0xffffffffL
        val windowStart = total - end.size
        val cd = if (cdOffset >= windowStart) end.copyOfRange((cdOffset - windowStart).toInt(), (cdOffset - windowStart + cdSize).toInt())
        else range(id, cdOffset, cdOffset + cdSize - 1)
        val c = ByteBuffer.wrap(cd).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<Entry>()
        var p = 0
        while (p + 46 <= cd.size && c.getInt(p) == 0x02014b50) {
            val method = c.getShort(p + 10).toInt() and 0xffff
            val comp = c.getInt(p + 20).toLong() and 0xffffffffL
            val size = c.getInt(p + 24).toLong() and 0xffffffffL
            val nLen = c.getShort(p + 28).toInt() and 0xffff
            val xLen = c.getShort(p + 30).toInt() and 0xffff
            val cLen = c.getShort(p + 32).toInt() and 0xffff
            val off = c.getInt(p + 42).toLong() and 0xffffffffL
            val name = String(cd, p + 46, nLen, Charsets.UTF_8)
            out += Entry(name, method, comp, size, off)
            p += 46 + nLen + xLen + cLen
        }
        return out
    }

    private fun read(id: String, e: Entry, max: Long = 6_000_000): ByteArray? {
        if (e.compSize > max) return null
        val raw = range(id, e.offset, e.offset + 30 + 1024 + e.compSize)
        val h = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        if (h.getInt(0) != 0x04034b50) return null
        val start = 30 + (h.getShort(26).toInt() and 0xffff) + (h.getShort(28).toInt() and 0xffff)
        val data = raw.copyOfRange(start, (start + e.compSize).toInt().coerceAtMost(raw.size))
        return when (e.method) {
            0 -> data
            8 -> Inflater(true).run {
                setInput(data)
                val out = ByteArray(e.size.toInt().coerceAtLeast(1))
                var n = 0
                while (n < out.size && !finished()) { val r = inflate(out, n, out.size - n); if (r == 0 && (needsInput() || needsDictionary())) break; n += r }
                end(); out.copyOf(n)
            }
            else -> null
        }
    }

    private val IMAGE = Regex("\\.(jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE)

    private fun peek(id: String, size: Long?, covers: File): JSONObject {
        val entries = directory(id, size)
        val out = JSONObject()
        val byName = entries.associateBy { it.name }
        val opfEntry = entries.firstOrNull { it.name.endsWith(".opf", true) }
        if (opfEntry == null) {
            // A comic: the first page, in natural order, is its cover.
            val first = entries.filter { IMAGE.containsMatchIn(it.name) && !it.name.startsWith("__MACOSX") }
                .minWithOrNull(compareBy({ it.name.count { c -> c == '/' } }, { it.name.lowercase().replace(Regex("\\d+")) { m -> m.value.padStart(8, '0') } }))
            first?.let { e -> read(id, e)?.let { saveCover(covers, id, it, e.name)?.let { f -> out.put("cover", f) } } }
            out.put("comic", true)
            // ComicInfo.xml, the tag file comic tools leave inside the archive.
            val ci = entries.firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", true) }?.let { read(id, it, 400_000) }?.toString(Charsets.UTF_8)
            out.put("ci", ci != null)
            if (ci != null) {
                fun tag(t: String) = Regex("<$t>([\\s\\S]*?)</$t>", RegexOption.IGNORE_CASE).find(ci)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
                tag("Series")?.let { out.put("series", it); tag("Number")?.toDoubleOrNull()?.let { n -> out.put("index", n) } }
                tag("Summary")?.let { out.put("desc", it.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(4000)) }
                tag("Year")?.let { y -> out.put("date", y + (tag("Month")?.padStart(2, '0')?.let { "-$it" } ?: "")) }
                tag("Publisher")?.let { out.put("publisher", it) }
                tag("LanguageISO")?.let { out.put("lang", it) }
                tag("GTIN")?.let { out.put("isbn", it) }
                tag("Writer")?.let { out.put("writer", it) }
                out.put("subjects", JSONArray(tag("Genre")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(6) ?: emptyList<String>()))
            }
            return out
        }
        val opf = read(id, opfEntry)?.toString(Charsets.UTF_8) ?: return out
        fun meta(name: String) = Regex("<meta[^>]*name=\"$name\"[^>]*content=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(opf)?.groupValues?.get(1)
            ?: Regex("<meta[^>]*content=\"([^\"]*)\"[^>]*name=\"$name\"", RegexOption.IGNORE_CASE).find(opf)?.groupValues?.get(1)
        fun dc(tag: String) = Regex("<dc:$tag[^>]*>([\\s\\S]*?)</dc:$tag>", RegexOption.IGNORE_CASE).findAll(opf).map { unescape(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }.toList()
        // Series: calibre's, else EPUB 3's belongs-to-collection.
        var series = meta("calibre:series")?.let(::unescape)
        var index = meta("calibre:series_index")?.toDoubleOrNull()
        if (series == null) {
            Regex("<meta[^>]*property=\"belongs-to-collection\"[^>]*?(?:id=\"([^\"]+)\")?[^>]*>([^<]+)</meta>").find(opf)?.let { m ->
                series = unescape(m.groupValues[2]).trim()
                val ref = m.groupValues[1]
                if (ref.isNotEmpty()) index = Regex("<meta[^>]*refines=\"#$ref\"[^>]*property=\"group-position\"[^>]*>([^<]+)</meta>").find(opf)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
            }
        }
        series?.takeIf { it.isNotBlank() }?.let { out.put("series", it); index?.let { i -> out.put("index", i) } }
        dc("description").firstOrNull()?.let { out.put("desc", it.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(4000)) }
        out.put("subjects", JSONArray(dc("subject").take(8)))
        dc("date").firstOrNull()?.let { out.put("date", it.take(10)) }
        dc("publisher").firstOrNull()?.let { out.put("publisher", it) }
        dc("language").firstOrNull()?.let { out.put("lang", it) }
        dc("identifier").map { it.filter(Char::isDigit) }.firstOrNull { it.length == 13 && (it.startsWith("978") || it.startsWith("979")) }?.let { out.put("isbn", it) }
        // The cover the book itself carries.
        val base = opfEntry.name.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val coverId = meta("cover")
        val href = Regex("<item[^>]*properties=\"[^\"]*cover-image[^\"]*\"[^>]*href=\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
            ?: Regex("<item[^>]*href=\"([^\"]+)\"[^>]*properties=\"[^\"]*cover-image").find(opf)?.groupValues?.get(1)
            ?: coverId?.let { cid ->
                Regex("<item[^>]*id=\"${Regex.escape(cid)}\"[^>]*href=\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
                    ?: Regex("<item[^>]*href=\"([^\"]+)\"[^>]*id=\"${Regex.escape(cid)}\"").find(opf)?.groupValues?.get(1)
            }
        href?.let { h ->
            val path = normalize(base + java.net.URLDecoder.decode(h, "UTF-8"))
            byName[path]?.takeIf { IMAGE.containsMatchIn(it.name) }?.let { e ->
                read(id, e)?.let { saveCover(covers, id, it, e.name)?.let { f -> out.put("cover", f) } }
            }
        }
        return out
    }

    private fun normalize(p: String): String {
        val parts = ArrayList<String>()
        p.split('/').forEach { s -> when (s) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); else -> parts += s } }
        return parts.joinToString("/")
    }

    private fun saveCover(dir: File, id: String, bytes: ByteArray, name: String): String? {
        // Kept small: a cover on a shelf needs no more than this.
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outHeight / (sample * 2) >= 900) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val f = File(dir, "$id.jpg")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return f.absolutePath
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
}

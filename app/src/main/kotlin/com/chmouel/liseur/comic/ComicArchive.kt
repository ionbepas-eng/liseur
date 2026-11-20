package com.chmouel.liseur.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** A CBZ: a zip of page images, read in natural order (2 before 10). */
class ComicArchive(private val file: File) : AutoCloseable {
    private val zip = ZipFile(file)
    val pages: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && IMAGE.containsMatchIn(it.name) && !it.name.substringAfterLast('/').startsWith(".") && !it.name.startsWith("__MACOSX") }
        .map { it.name }
        .sortedWith(NaturalOrder)
        .toList()

    /** Decodes a page no larger than needed for [maxSide] pixels on its long side. */
    fun page(index: Int, maxSide: Int): Bitmap? {
        val entry = zip.getEntry(pages.getOrNull(index) ?: return null) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, opts) }
    }

    override fun close() = zip.close()

    companion object {
        private val IMAGE = Regex("\\.(jpe?g|png|webp|gif|bmp|avif)$", RegexOption.IGNORE_CASE)

        /** A zip with images and no EPUB container is a comic. */
        fun isComic(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val zin = ZipInputStream(raw.buffered())
                var images = 0
                var n = 0
                while (n < 400) {
                    val e = zin.nextEntry ?: break
                    n++
                    if (e.name == "mimetype" || e.name.startsWith("META-INF/container")) return false
                    if (IMAGE.containsMatchIn(e.name)) images++
                    if (images >= 3) return true
                }
                images > 0
            } ?: false
        }.getOrDefault(false)

        /** A local file for the archive: the download itself, or a cached copy of a content URI. */
        fun localFile(context: Context, uri: Uri): File? {
            if (uri.scheme == "file") return uri.path?.let(::File)
            val out = File(context.cacheDir, "comic-${uri.toString().hashCode()}.cbz")
            if (!out.exists()) context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            return out.takeIf { it.exists() }
        }
    }
}

private object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) (p.toBigInteger().compareTo(q.toBigInteger())) else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size - y.size
    }
}

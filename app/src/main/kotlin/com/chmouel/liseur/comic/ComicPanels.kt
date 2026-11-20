package com.chmouel.liseur.comic

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs

/**
 * Finds a page's panels from its gutters: the rows, then within each row
 * the columns, where the page's own background colour runs unbroken.
 * Coordinates come back as fractions of the page, in reading order
 * (right to left in manga). A page it cannot cut cleanly is one panel.
 */
object ComicPanels {
    fun detect(page: Bitmap, manga: Boolean): List<RectF> {
        val w = 320
        val h = (page.height * w.toFloat() / page.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(page, w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== page) small.recycle()
        fun lum(c: Int) = ((c shr 16 and 0xff) * 299 + (c shr 8 and 0xff) * 587 + (c and 0xff) * 114) / 1000
        // The background: the most common brightness along the page's edge (white or black gutters).
        val edge = IntArray(256)
        for (x in 0 until w) { edge[lum(px[x])]++; edge[lum(px[(h - 1) * w + x])]++ }
        for (y in 0 until h) { edge[lum(px[y * w])]++; edge[lum(px[y * w + w - 1])]++ }
        val bg = edge.indices.maxBy { edge[it] }
        val ink = BooleanArray(w * h) { abs(lum(px[it]) - bg) > 38 }

        fun runs(count: (Int) -> Int, n: Int, across: Int, minLen: Int): List<IntRange> {
            val out = ArrayList<IntRange>()
            var start = -1
            for (i in 0..n) {
                val full = i < n && count(i) > across * 0.012
                if (full && start < 0) start = i
                if (!full && start >= 0) { if (i - start >= minLen) out += start until i; start = -1 }
            }
            return out
        }
        val rows = runs({ y -> (0 until w).count { ink[y * w + it] } }, h, w, (h * 0.05f).toInt())
        val panels = ArrayList<RectF>()
        for (r in rows) {
            val cols = runs({ x -> r.count { ink[it * w + x] } }, w, r.last - r.first + 1, (w * 0.1f).toInt())
            val ordered = if (manga) cols.reversed() else cols
            for (c in ordered) {
                // Tighten each panel to its own content.
                val inner = runs({ y -> c.count { ink[y * w + it] } }, h, c.last - c.first + 1, 2).filter { it.first <= r.last && it.last >= r.first }
                val top = inner.minOfOrNull { it.first }?.coerceAtLeast(r.first) ?: r.first
                val bottom = inner.maxOfOrNull { it.last }?.coerceAtMost(r.last) ?: r.last
                panels += RectF(c.first / w.toFloat(), top / h.toFloat(), (c.last + 1) / w.toFloat(), (bottom + 1) / h.toFloat())
            }
        }
        val whole = listOf(RectF(0f, 0f, 1f, 1f))
        if (panels.size < 2 || panels.size > 14) return whole
        // A cut that leaves most of the page out is not a cut of panels.
        val covered = panels.sumOf { (it.width() * it.height()).toDouble() }
        return if (covered < 0.45) whole else panels
    }
}

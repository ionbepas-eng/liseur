package com.chmouel.liseur.ui.library.styles

import android.content.Context
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.ReadingSession
import com.chmouel.liseur.data.gdrive.DriveStateSync
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Level and monthly challenges, worked out as Liburuville web does
 * (achieve.js): XP = pages + 100 × books finished + 50 × achievement tiers.
 * Android's own sittings and every web device's are counted together.
 */
object LiburuProgress {
    val LEVELS = listOf(
        0 to "Curioso", 500 to "Hojeador", 1500 to "Lector", 3500 to "Lector asiduo", 7000 to "Devorador",
        12000 to "Bibliófilo", 20000 to "Ratón de biblioteca", 32000 to "Erudito", 50000 to "Sabio",
        75000 to "Guardián de Alejandría", 110000 to "Leyenda", 160000 to "Inmortal",
    )

    data class Level(val n: Int, val name: String, val xp: Int, val from: Int, val to: Int?, val nextName: String?) {
        val pct: Float get() = if (to == null) 1f else ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    data class Reto(val title: String, val value: Double, val target: Double, val daysLeft: Int, val auto: Boolean) {
        val done: Boolean get() = value >= target
    }

    data class Summary(val level: Level, val retos: List<Reto>, val monthName: String)

    private data class Sit(val key: String, val start: Long, val seconds: Double, val from: Double?, val to: Double?, val pages: Double?)

    fun levelOf(xp: Double): Level {
        var i = 0
        while (i + 1 < LEVELS.size && xp >= LEVELS[i + 1].first) i++
        val next = LEVELS.getOrNull(i + 1)
        return Level(i + 1, LEVELS[i].second, xp.toInt(), LEVELS[i].first, next?.first, next?.second)
    }

    private fun keyOf(url: String) = if (url.startsWith("gdrive:")) "g:" + url.removePrefix("gdrive:") else url

    fun compute(context: Context, androidSessions: List<ReadingSession>, books: List<Book>, zone: ZoneId): Summary {
        val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull() ?: JSONObject()
        val webSessions = runCatching { JSONArray(File(context.filesDir, DriveStateSync.SESSIONS_CACHE).readText()) }.getOrNull() ?: JSONArray()
        val webBooks = state.optJSONObject("books") ?: JSONObject()
        val pagesByKey = books.associate { keyOf(it.url) to (it.remotePageCount?.toDouble() ?: 300.0) }
        fun pagesOf(key: String) = pagesByKey[key] ?: 300.0

        val sits = mutableListOf<Sit>()
        for (s in androidSessions) {
            if (s.durationMs <= 0) continue
            sits += Sit(keyOf(s.bookUrl), s.startedAt, s.durationMs / 1000.0, s.startProgression, s.endProgression, null)
        }
        for (i in 0 until webSessions.length()) {
            val o = webSessions.optJSONObject(i) ?: continue
            sits += Sit(
                o.optString("bookId"), o.optLong("start"), o.optDouble("seconds", 0.0),
                o.optDouble("fromFrac").takeIf { !it.isNaN() }, o.optDouble("toFrac").takeIf { !it.isNaN() },
                o.optDouble("pages").takeIf { !it.isNaN() },
            )
        }
        fun sitPages(s: Sit) = s.pages ?: if (s.from != null && s.to != null && s.to > s.from) (s.to - s.from) * pagesOf(s.key) else 0.0

        // Finished books, from both sides.
        val finishedAt = HashMap<String, Long>()
        webBooks.keys().forEach { k ->
            val meta = webBooks.optJSONObject(k)?.optJSONObject("meta") ?: return@forEach
            if (meta.optString("status") == "finished") finishedAt[k] = meta.optLong("finishedAt", meta.optLong("t"))
        }
        books.forEach { b -> b.finishedAt?.let { finishedAt.putIfAbsent(keyOf(b.url), it) } }

        val pagesByBook = HashMap<String, Double>()
        sits.forEach { pagesByBook[it.key] = (pagesByBook[it.key] ?: 0.0) + sitPages(it) }
        finishedAt.keys.forEach { pagesByBook[it] = maxOf(pagesByBook[it] ?: 0.0, pagesOf(it)) }
        val tiers = state.optJSONObject("achv")?.let { a -> a.keys().asSequence().sumOf { a.optJSONObject(it)?.optInt("tier") ?: 0 } } ?: 0
        val xp = pagesByBook.values.sum() + 100.0 * finishedAt.size + 50.0 * tiers + com.chmouel.liseur.ui.share.LiburuMissions.xp()

        // Shelves whose name says "clásico".
        val shelves = state.optJSONObject("shelves") ?: JSONObject()
        val classicShelves = shelves.keys().asSequence()
            .filter { id -> shelves.optJSONObject(id)?.let { !it.optBoolean("deleted") && Regex("cl[aá]sic", RegexOption.IGNORE_CASE).containsMatchIn(it.optString("name")) } == true }
            .toSet()
        fun isClassic(key: String): Boolean {
            val ids = webBooks.optJSONObject(key)?.optJSONObject("meta")?.optJSONArray("shelves") ?: return false
            return (0 until ids.length()).any { ids.optString(it) in classicShelves }
        }

        fun progress(type: String, from: Long, to: Long, bookId: String? = null): Double {
            val ses = sits.filter { it.start in from until to }
            val fin = finishedAt.filterValues { it in from until to }
            return when (type) {
                "pages" -> ses.sumOf(::sitPages)
                "minutes" -> ses.sumOf { it.seconds } / 60
                "books" -> fin.size.toDouble()
                "days" -> ses.groupBy { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
                    .count { (_, d) -> d.sumOf { it.seconds } >= 60 }.toDouble()
                "classic" -> fin.keys.count(::isClassic).toDouble()
                "morning" -> ses.count { java.time.Instant.ofEpochMilli(it.start).atZone(zone).hour < 9 }.toDouble()
                "finish" -> if (bookId != null && (finishedAt[bookId] ?: -1) in from until to) 1.0 else 0.0
                else -> 0.0
            }
        }
        fun monthStart(d: LocalDate) = d.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val today = LocalDate.now(zone)
        val from = monthStart(today)
        val to = monthStart(today.plusMonths(1))
        val daysLeft = (today.lengthOfMonth() - today.dayOfMonth)
        val prev = (1..3).map { k -> monthStart(today.minusMonths(k.toLong())) to monthStart(today.minusMonths(k - 1L)) }
        val avgPages = prev.sumOf { (f, t) -> progress("pages", f, t) } / 3
        val avgDays = prev.sumOf { (f, t) -> progress("days", f, t) } / 3
        fun roundTo(v: Double, k: Int) = maxOf(k, (Math.round(v / k) * k).toInt())
        val pagesTarget = minOf(3000, roundTo(maxOf(300.0, avgPages * 1.1), 50))
        val daysTarget = minOf(today.lengthOfMonth(), maxOf(10, Math.round(avgDays).toInt() + 2))
        val variants = listOf("en" to 1, "words" to 15, "highlights" to 10, "morning" to 5, "classic" to 1, "books" to 2, "minutes" to 600)
        val (vt, vn) = variants[((today.year * 12 + today.monthValue - 1) % variants.size)]

        val retos = mutableListOf(
            Reto(verb("pages", pagesTarget), progress("pages", from, to), pagesTarget.toDouble(), daysLeft, true),
            Reto(verb("days", daysTarget), progress("days", from, to), daysTarget.toDouble(), daysLeft, true),
            Reto(verb(vt, vn), progress(vt, from, to), vn.toDouble(), daysLeft, true),
        )
        val custom = state.optJSONObject("retos") ?: JSONObject()
        val now = System.currentTimeMillis()
        custom.keys().forEach { id ->
            val r = custom.optJSONObject(id) ?: return@forEach
            if (r.optBoolean("deleted")) return@forEach
            val f = r.optLong("from"); val t = r.optLong("to", Long.MAX_VALUE)
            if (now < f || now >= t) return@forEach
            val type = r.optString("type")
            val target = r.optDouble("target", 1.0)
            val title = r.optString("name").takeIf { it.isNotBlank() } ?: verb(type, target.toInt())
            val left = ((t - now) / 86_400_000L).toInt()
            retos += Reto(title, progress(type, f, t, r.optString("bookId")), target, left, false)
        }
        val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))
        return Summary(levelOf(xp), retos, month)
    }

    private fun fmt(n: Int) = String.format(java.util.Locale("es"), "%,d", n).replace(',', '.')

    fun verb(type: String, n: Int): String = when (type) {
        "pages" -> "Lee ${fmt(n)} páginas"
        "minutes" -> if (n % 60 != 0) "Lee $n minutos" else "Lee ${n / 60} ${if (n == 60) "hora" else "horas"}"
        "books" -> "Termina $n ${if (n == 1) "libro" else "libros"}"
        "days" -> "Lee $n días distintos"
        "en" -> "Termina $n ${if (n == 1) "libro" else "libros"} en inglés"
        "classic" -> if (n == 1) "Termina un clásico" else "Termina $n clásicos"
        "words" -> "Guarda $n palabras nuevas"
        "highlights" -> "Haz $n subrayados"
        "morning" -> "Lee antes de las 9 en $n ocasiones"
        else -> "Reto"
    }
}

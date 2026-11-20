package com.chmouel.liseur.data.gdrive

import com.chmouel.liseur.data.db.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer

/**
 * The notes Google Play Libros keeps in Drive («Notas de "Título"»), read as
 * Markdown and turned into highlights, as the web's playnotes.js does.
 */
object LiburuPlayNotes {
    data class Doc(val id: String, val name: String, val title: String, val modified: String)
    data class Item(val text: String, val note: String, val color: String, val chapter: String, val page: Int?, val created: Long?)

    private val http by lazy { DriveAuth.client() }

    suspend fun findDocs(): List<Doc> = withContext(Dispatchers.IO) {
        val url = "${DriveAuth.BASE_URL}/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "mimeType='application/vnd.google-apps.document' and trashed=false and (name contains 'Notas de' or name contains 'Notes from')")
            .addQueryParameter("fields", "files(id,name,modifiedTime)")
            .addQueryParameter("pageSize", "300")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Drive ${r.code}")
            val a = JSONObject(r.body.string()).optJSONArray("files") ?: return@use emptyList()
            (0 until a.length()).map { a.getJSONObject(it) }.map { Doc(it.getString("id"), it.optString("name"), titleFromDocName(it.optString("name")), it.optString("modifiedTime")) }
        }
    }

    suspend fun export(id: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("${DriveAuth.BASE_URL}/files/$id/export?mimeType=text/markdown").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("export ${r.code}")
            r.body.string()
        }
    }

    fun titleFromDocName(name: String): String {
        val m = Regex("^(?:Notas de|Notes from)\\s*[\"“«]\\s*(.+?)\\s*[\"”»]\\s*$", RegexOption.IGNORE_CASE).find(name)
        return (m?.groupValues?.get(1) ?: name).replace(Regex("\\.(pdf|epub)$", RegexOption.IGNORE_CASE), "").trim()
    }

    private val MONTHS = mapOf(
        "enero" to 0, "febrero" to 1, "marzo" to 2, "abril" to 3, "mayo" to 4, "junio" to 5, "julio" to 6, "agosto" to 7,
        "septiembre" to 8, "setiembre" to 8, "octubre" to 9, "noviembre" to 10, "diciembre" to 11,
        "january" to 0, "february" to 1, "march" to 2, "april" to 3, "may" to 4, "june" to 5, "july" to 6, "august" to 7,
        "september" to 8, "october" to 9, "november" to 10, "december" to 11,
    )
    private val COLORS = listOf(
        Regex("amarill|yellow", RegexOption.IGNORE_CASE) to "#F6D365",
        Regex("roj|red|naranj|orange|rosa|pink", RegexOption.IGNORE_CASE) to "#F2A7C3",
        Regex("verde|green", RegexOption.IGNORE_CASE) to "#A8D8A0",
        Regex("azul|blue", RegexOption.IGNORE_CASE) to "#9EC9F0",
    )
    private val DATE = Regex("(\\d{1,2}) de ([a-záéíóú]+) de (\\d{4})|([A-Z][a-z]+) (\\d{1,2}), (\\d{4})")

    private fun clean(s: String) = s.replace(Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|<>~])"), "$1")
        .replace(Regex("!\\[[^\\]]*\\]\\[[^\\]]*\\]|!\\[[^\\]]*\\]\\([^)]*\\)"), "")
        .replace("&#10;", " ").replace(Regex("\\s+"), " ").trim()

    private fun date(m: MatchResult?): Long? {
        m ?: return null
        val g = m.groupValues
        val mo = MONTHS[(g[2].ifEmpty { g[4] }).lowercase()] ?: return null
        val y = (g[3].ifEmpty { g[6] }).toIntOrNull() ?: return null
        val d = (g[1].ifEmpty { g[5] }).toIntOrNull() ?: return null
        return java.time.LocalDate.of(y, mo + 1, d).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun parse(md: String): List<Item> {
        var mode: String? = null
        var heading = ""
        val byKey = LinkedHashMap<String, Item>()
        for (raw in md.lines()) {
            if (raw.startsWith("[image")) continue
            val l = raw.trim()
            if (Regex("^#\\s").containsMatchIn(l)) {
                mode = when {
                    Regex("por color|by colou?r", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "color"
                    Regex("todas tus anotaciones|all your annotations", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "all"
                    else -> mode
                }
                continue
            }
            if (l.startsWith("## ")) { heading = clean(l.removePrefix("##").trim().trim('*')); continue }
            if (!l.startsWith("|") || !l.contains("play.google.com/books/reader")) continue
            val cell = clean(l.removePrefix("|").trimEnd().removeSuffix("|"))
            val link = Regex("\\[([^\\]]*)\\]\\((https?:[^)\\s]+)\\)\\s*$").find(cell)
            val body = if (link != null) cell.substring(0, link.range.first).trim() else cell
            val dm = DATE.find(body)
            val before = if (dm != null) body.substring(0, dm.range.first).trim() else body
            val after = if (dm != null) body.substring(dm.range.last + 1).trim() else ""
            val q = Regex("^\\*([\\s\\S]+?)\\*\\s*([\\s\\S]*)$").find(before) ?: continue
            val text = q.groupValues[1].trim()
            val note = listOf(q.groupValues[2], after).joinToString(" ").trim()
            val pg = link?.let { Regex("pg=GBS\\.PA(\\d+)").find(it.groupValues[2])?.groupValues?.get(1)?.toIntOrNull() } ?: link?.groupValues?.get(1)?.toIntOrNull()
            val key = "$text|${pg ?: ""}"
            var item = byKey[key] ?: Item(text, "", "", "", pg, date(dm))
            if (note.isNotEmpty() && item.note.isEmpty()) item = item.copy(note = note)
            if (mode == "color") COLORS.firstOrNull { it.first.containsMatchIn(heading) }?.let { item = item.copy(color = it.second) }
            else if (mode == "all") item = item.copy(chapter = heading)
            byKey[key] = item
        }
        return byKey.values.map { if (it.color.isEmpty()) it.copy(color = "#F6D365") else it }.sortedBy { it.page ?: 0 }
    }

    /** The web's stable id, so importing twice adds nothing. */
    fun noteId(bookKey: String, text: String): String {
        var h = 2166136261L.toInt()
        for (c in bookKey + text) { h = h xor c.code; h *= 16777619 }
        return "pl" + (h.toLong() and 0xffffffffL).toString(36)
    }

    private fun norm(s: String?) = Normalizer.normalize((s ?: "").lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    fun match(books: List<Book>, title: String): Book? {
        val t = norm(title)
        if (t.isEmpty()) return null
        var best: Book? = null
        var score = 0
        for (b in books) {
            val bt = norm(b.title)
            val s = when {
                bt == t -> 3
                (bt.startsWith(t) || t.startsWith(bt)) && minOf(bt.length, t.length) > 6 -> 2
                bt.contains(t) || t.contains(bt) -> 1
                else -> 0
            }
            if (s > score) { score = s; best = b }
        }
        return best
    }
}

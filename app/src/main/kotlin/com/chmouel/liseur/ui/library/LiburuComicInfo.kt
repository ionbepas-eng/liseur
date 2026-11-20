package com.chmouel.liseur.ui.library

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Comics from the Grand Comics Database (comics.org), the open catalogue of
 * some two million issues; its read API needs no key. From a file name such
 * as «Absolute Batman Annual 001 (2025) (Digital)» it finds the series and
 * the issue, and gives back the same shape as the Google Books data
 * (publisher, date, pages, genres, synopsis, link), so the ficha shows it
 * like a book's.
 */
object LiburuComicInfo {
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private const val API = "https://www.comics.org/api"

    private fun json(url: String): JSONObject? = runCatching {
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute()
            .use { if (it.isSuccessful) JSONObject(it.body.string()) else null }
    }.getOrNull()

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    data class Parsed(val name: String, val number: String?, val year: Int?)

    /** «Absolute Batman Annual 001 (2025) (Digital) (Zone-Empire)» → name, number, year. */
    fun parse(title: String): Parsed {
        val year = Regex("\\((\\d{4})\\)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        var t = title.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").replace('_', ' ').trim()
        val num = Regex("(?:#|\\bn[ºo.]?\\s*)?(\\d{1,4}(?:\\.\\d)?)\\s*$").find(t)
            ?: Regex("(?:#|\\s)(\\d{1,4})(?:\\s|$)").findAll(t).lastOrNull()
        var number: String? = null
        if (num != null) {
            number = num.groupValues[1].trimStart('0').ifEmpty { "0" }
            t = t.removeRange(num.range).trim()
        }
        t = t.replace(Regex("\\s+(-|–|:)\\s*$"), "").replace(Regex("\\s+"), " ").trim()
        return Parsed(t, number, year)
    }

    fun lookup(title: String, language: String?): JSONObject? {
        val p = parse(title)
        if (p.name.length < 2) return null
        val words = norm(p.name).split(' ').filter { it.isNotEmpty() }
        val wanted = words.toSet()
        // The API matches names containing the text; the first words find the family of series.
        val probe = words.take(2).joinToString(" ").ifEmpty { return null }
        val list = json("$API/series/name/${URLEncoder.encode(probe, "UTF-8").replace("+", "%20")}/?format=json")?.optJSONArray("results") ?: return null
        val lang = language?.take(2)?.lowercase()
        val scored = (0 until list.length()).map { list.getJSONObject(it) }.map { s ->
            val nameWords = norm(s.optString("name")).split(' ').filter { it.isNotEmpty() }.filter { it.toIntOrNull() == null }.toSet()
            val overlap = nameWords.intersect(wanted).size
            val extra = (nameWords - wanted).size
            val began = s.optInt("year_began")
            var score = overlap * 10 - extra * 6
            if (lang != null && s.optString("language") == lang) score += 4
            if (s.optString("language") == "en") score += 1
            if (p.year != null && began > 0) score -= minOf(10, kotlin.math.abs(p.year - began))
            s to score
        }.sortedByDescending { it.second }
        val series = scored.firstOrNull()?.first ?: return null
        val seriesUrl = series.optString("api_url")
        val detail = json(seriesUrl) ?: series
        val issues = detail.optJSONArray("active_issues") ?: JSONArray()
        val descs = detail.optJSONArray("issue_descriptors") ?: JSONArray()
        var pick = -1
        for (i in 0 until descs.length()) {
            val d = descs.optString(i)
            val n = d.substringBefore(' ').substringBefore('[').trim().trimStart('0').ifEmpty { "0" }
            if (p.number == null || n == p.number) { if (pick < 0 || !d.contains('[')) pick = i; if (!d.contains('[')) break }
        }
        if (pick < 0 && issues.length() > 0 && p.number == null) pick = 0
        val issue = if (pick >= 0) json(issues.optString(pick)) else null
        return toBookShape(detail, issue)
    }

    private fun toBookShape(series: JSONObject, issue: JSONObject?): JSONObject {
        val out = JSONObject().put("src", "Grand Comics Database")
        val stories = issue?.optJSONArray("story_set")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val main = stories.firstOrNull { it.optString("type").contains("story") } ?: stories.firstOrNull()
        fun clean(s: String?) = s.orEmpty().replace(Regex("\\s*\\((credited|signed[^)]*|painted|digital)\\)"), "").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() && it != "None" && it != "?" }
        val writer = clean(main?.optString("script"))
        val artist = clean(main?.optString("pencils"))
        val synopsis = stories.map { it.optString("synopsis") }.firstOrNull { it.isNotBlank() }
        val desc = buildString {
            if (synopsis != null) append(synopsis.trim())
            else {
                val titles = stories.filter { it.optString("type").contains("story") }.map { it.optString("title") }.filter { it.isNotBlank() }
                if (titles.isNotEmpty()) append(titles.joinToString(" · ") { "«$it»" }).append(". ")
                clean(main?.optString("characters"))?.let { append("Personajes: ").append(it.split(';').take(8).joinToString(", ") { c -> c.trim() }).append('.') }
            }
        }.trim()
        val credits = listOfNotNull(writer?.let { "Guion: $it" }, artist?.takeIf { it != writer }?.let { "Dibujo: $it" }).joinToString(" · ")
        val issueId = issue?.optString("api_url")?.let { Regex("/issue/(\\d+)").find(it)?.groupValues?.get(1) }
        out.put("desc", listOf(desc, credits).filter { it.isNotBlank() }.joinToString("\n\n"))
            .put("publisher", issue?.optString("indicia_publisher")?.takeIf { it.isNotBlank() } ?: "")
            .put("publishedDate", issue?.optString("on_sale_date")?.takeIf { it.isNotBlank() } ?: issue?.optString("key_date").orEmpty().replace("-00", ""))
            .put("pageCount", issue?.optString("page_count")?.toDoubleOrNull()?.toInt() ?: 0)
            .put("categories", JSONArray(stories.map { it.optString("genre") }.flatMap { it.split(';') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(4)))
            .put("language", series.optString("language"))
            .put("isbn13", issue?.optString("isbn").orEmpty().filter(Char::isDigit))
            .put("isbn10", "")
            .put("rating", 0.0).put("ratingsCount", 0)
            .put("link", issueId?.let { "https://www.comics.org/issue/$it/" } ?: "https://www.comics.org/")
            .put("series", issue?.optString("series_name") ?: series.optString("name"))
            .put("cover", issue?.optString("cover").orEmpty())
            .put("writer", writer.orEmpty())
        return out
    }
}

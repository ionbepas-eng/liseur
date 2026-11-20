package com.chmouel.liseur.ui.library.styles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chmouel.liseur.data.remote.RemoteHttp
import java.io.File
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/** «Actualidad literaria»: the web app's RSS feeds, read directly (no bridge needed in an app). */
object LiburuNews {
    data class Item(val source: String, val title: String, val link: String, val date: Long, val image: String?)

    val DEFAULT_FEEDS = listOf(
        "Babelio" to "https://es.babelio.com/rss_articles_babelio.php",
        "Babelia · El País" to "https://feeds.elpais.com/mrss-s/list/ep/site/elpais.com/section/babelia",
    )
    private const val TTL = 30 * 60 * 1000L

    fun feeds(context: Context): List<Pair<String, String>> {
        val raw = context.getSharedPreferences("liburu", Context.MODE_PRIVATE).getString("feeds", null) ?: return DEFAULT_FEEDS
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("name") to o.getString("url") } }
        }.getOrDefault(DEFAULT_FEEDS)
    }

    fun setFeeds(context: Context, list: List<Pair<String, String>>) {
        val a = JSONArray(list.map { JSONObject().put("name", it.first).put("url", it.second) })
        context.getSharedPreferences("liburu", Context.MODE_PRIVATE).edit().putString("feeds", a.toString()).apply()
        File(context.cacheDir, "liburu-news.json").delete()
    }

    suspend fun load(context: Context): List<Item> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "liburu-news.json")
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < TTL) {
            decode(cache.readText())?.let { return@withContext it }
        }
        val http = RemoteHttp.default()
        val items = coroutineScope {
            feeds(context).map { (name, url) ->
                async {
                    runCatching {
                        http.newCall(Request.Builder().url(url).header("User-Agent", "Liburuville/Android").build()).execute().use {
                            if (it.isSuccessful) parse(it.body.string(), name) else emptyList()
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.sortedByDescending { it.date }.take(20)
        if (items.isNotEmpty()) cache.writeText(encode(items))
        items.ifEmpty { if (cache.exists()) decode(cache.readText()).orEmpty() else emptyList() }
    }

    private fun encode(items: List<Item>) = JSONArray(items.map {
        JSONObject().put("s", it.source).put("t", it.title).put("l", it.link).put("d", it.date).put("i", it.image ?: "")
    }).toString()

    private fun decode(text: String) = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Item(o.getString("s"), o.getString("t"), o.getString("l"), o.getLong("d"), o.optString("i").ifEmpty { null }) } }
    }.getOrNull()

    private val IMG = Regex("""<img[^>]+src=["']([^"']+)""", RegexOption.IGNORE_CASE)
    private fun strip(html: String) = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun parseDate(s: String): Long = runCatching { ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { ZonedDateTime.parse(s.trim()).toInstant().toEpochMilli() }.getOrDefault(0L)

    fun parse(xml: String, source: String): List<Item> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val out = mutableListOf<Item>()
        var inItem = false
        var title = ""; var link = ""; var date = 0L; var image: String? = null; var desc = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 25) {
            val name = p.name?.substringAfter(':')?.lowercase()
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "item" || name == "entry" -> { inItem = true; title = ""; link = ""; date = 0L; image = null; desc = "" }
                    !inItem -> Unit
                    name == "title" -> title = strip(p.nextText())
                    name == "link" -> {
                        val href = p.getAttributeValue(null, "href")
                        link = href ?: p.nextText().trim()
                    }
                    name == "pubdate" || name == "published" || name == "updated" || name == "date" -> if (date == 0L) date = parseDate(p.nextText())
                    (name == "content" || name == "thumbnail") && p.getAttributeValue(null, "url") != null -> if (image == null) image = p.getAttributeValue(null, "url")
                    name == "enclosure" && (p.getAttributeValue(null, "type") ?: "").startsWith("image") -> if (image == null) image = p.getAttributeValue(null, "url")
                    name == "description" || name == "summary" -> desc = runCatching { p.nextText() }.getOrDefault("")
                }
                XmlPullParser.END_TAG -> if (name == "item" || name == "entry") {
                    inItem = false
                    if (image == null) image = IMG.find(desc)?.groupValues?.get(1)
                    if (title.isNotBlank() && link.startsWith("http")) out += Item(source, title, link, date, image)
                }
            }
            event = runCatching { p.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
        }
        return out
    }
}

private fun ago(date: Long): String {
    if (date <= 0) return ""
    val min = (System.currentTimeMillis() - date) / 60_000
    return when {
        min < 60 -> "hace $min min"
        min < 24 * 60 -> "hace ${min / 60} h"
        else -> "hace ${min / (24 * 60)} d"
    }
}

@Composable
internal fun LiburuNewsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items by produceState(emptyList<LiburuNews.Item>()) { value = LiburuNews.load(context) }
    if (items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Actualidad literaria",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.link }) { item ->
                Column(
                    Modifier.width(280.dp).clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (item.image != null) {
                        AsyncImage(
                            model = item.image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(20.dp)),
                        )
                    }
                    Row {
                        Text(
                            listOf(item.source, ago(item.date)).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

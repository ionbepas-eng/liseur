package com.chmouel.liseur.ui.stats

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmouel.liseur.container
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.ReadingSession
import com.chmouel.liseur.data.gdrive.DriveStateSync
import com.chmouel.liseur.data.gdrive.LiburuCovers
import com.chmouel.liseur.data.gdrive.LiburuPeek
import com.chmouel.liseur.ui.library.BookCover
import com.chmouel.liseur.ui.library.styles.LiburuPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale("es", "ES")
private val Gold = Color(0xFFE8B83A)

/** One reading session, Android's or the web's, the way the web counts them. */
private data class Sit(val key: String, val start: Long, val seconds: Double, val from: Double?, val to: Double?, val pages: Double?, val device: String = "android-here")

private fun keyOf(url: String) = if (url.startsWith("gdrive:")) "g:" + url.removePrefix("gdrive:") else url

/** Everything the web's Estadísticas shows, worked out from the same data. */
private class StatsData(context: Context, val books: List<Book>, android: List<ReadingSession>) {
    val byKey: Map<String, Book> = books.associateBy { keyOf(it.url) }
    val sits = ArrayList<Sit>()
    val finishedAt = HashMap<String, Long>()
    val startedAt = HashMap<String, Long>()
    val rating = HashMap<String, Double>()
    val webBooks: JSONObject
    init {
        val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull() ?: JSONObject()
        webBooks = state.optJSONObject("books") ?: JSONObject()
        android.filter { it.durationMs > 0 }.forEach { s ->
            sits += Sit(keyOf(s.bookUrl), s.startedAt, s.durationMs / 1000.0, s.startProgression, s.endProgression, null)
        }
        val web = runCatching { JSONArray(File(context.filesDir, DriveStateSync.SESSIONS_CACHE).readText()) }.getOrNull() ?: JSONArray()
        for (i in 0 until web.length()) {
            val o = web.optJSONObject(i) ?: continue
            sits += Sit(o.optString("bookId"), o.optLong("start"), o.optDouble("seconds", 0.0),
                o.optDouble("fromFrac").takeIf { !it.isNaN() }, o.optDouble("toFrac").takeIf { !it.isNaN() }, o.optDouble("pages").takeIf { !it.isNaN() },
                device = if (o.optBoolean("manual")) "" else o.optString("device"))
        }
        webBooks.keys().forEach { k ->
            val m = webBooks.optJSONObject(k)?.optJSONObject("meta") ?: return@forEach
            if (m.optString("status") == "finished") finishedAt[k] = m.optLong("finishedAt", m.optLong("t"))
            m.optLong("startedAt").takeIf { it > 0 }?.let { startedAt[k] = it }
            m.optDouble("rating").takeIf { !it.isNaN() && it > 0 }?.let { rating[k] = it }
        }
        books.forEach { b -> b.finishedAt?.let { finishedAt.putIfAbsent(keyOf(b.url), it) } }
    }
    fun pagesOf(key: String): Double {
        val b = byKey[key]
        return b?.remotePageCount?.toDouble() ?: 300.0
    }
    fun sitPages(s: Sit) = s.pages ?: if (s.from != null && s.to != null && s.to > s.from) (s.to - s.from) * pagesOf(s.key) else 0.0
    fun peek(key: String): JSONObject? = if (key.startsWith("g:")) LiburuPeek.of(key.removePrefix("g:")) else null
    fun genres(key: String): JSONObject? = if (key.startsWith("g:")) LiburuCovers.genres(key.removePrefix("g:")) else null
    fun year(key: String): Int? = peek(key)?.optString("date")?.take(4)?.toIntOrNull() ?: genres(key)?.optString("y")?.toIntOrNull()
    fun series(key: String): String? = byKey[key]?.seriesName?.takeIf { it.isNotBlank() } ?: peek(key)?.optString("series")?.takeIf { it.isNotBlank() }
}

private fun dayOf(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
private fun fmtNum(n: Number) = String.format(ES, "%,d", n.toLong()).replace(',', '.')
private fun fmtMin(min: Double): String = when {
    min < 1 -> "—"
    min < 60 -> "${min.toInt()} min"
    else -> "${(min / 60).toInt()} h ${(min % 60).toInt().toString().padStart(2, '0')}"
}

/** Liburuville's Estadísticas, card by card as on the web. */
@Composable
fun LiburuStatsScreen(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onClassic: () -> Unit,
    bottomBarSpace: Dp,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val android by remember { context.container.database.readingSessionDao().observeAll() }.collectAsState(emptyList())
    val data = remember(books, android) { StatsData(context, books, android) }
    val now = LocalDate.now(zone)
    var year by rememberSaveable { mutableIntStateOf(now.year) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now(zone).toString()) }
    var goalVersion by remember { mutableIntStateOf(0) }
    val inYear = { ms: Long -> dayOf(ms, zone).year == year }
    val finished = data.finishedAt.filter { inYear(it.value) }.entries.sortedBy { it.value }.mapNotNull { e -> data.byKey[e.key]?.let { e.key to it } }
    val sits = data.sits.filter { inYear(it.start) }
    val totalMin = sits.sumOf { it.seconds } / 60
    val totalPages = sits.sumOf { data.sitPages(it) }
    val streak = remember(data) { streaks(data.sits.map { dayOf(it.start, zone) }.toSet(), now) }
    val label = if (year == now.year) "Este año" else "$year"
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(360.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomBarSpace + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalItemSpacing = 18.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Text("Estadísticas", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 4.dp))
        }
        item(span = StaggeredGridItemSpan.FullLine) { RecapChips(data, zone) }
        item(span = StaggeredGridItemSpan.FullLine) { Achievements(data, zone) }
        // Year
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundArrow("‹") { year-- }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Caps("Estadísticas de $year")
                        Text("${finished.size} ${if (finished.size == 1) "libro" else "libros"}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    }
                    RoundArrow("›", enabled = year < now.year) { year++ }
                }
                if (finished.isNotEmpty()) CoverShelf(finished.takeLast(14).map { it.second }, onOpenBook)
                Tiles(listOf("${(totalMin / 60).toInt()}" to "horas", fmtNum(totalPages) to "páginas", "${streak.first}" to "racha · récord ${streak.second}"))
            }
        }
        // Month with calendar
        item { MonthCard(data, YearMonth.parse(month), zone, onOpenBook, { month = it.toString() }) }
        // Goal
        item {
            val goal = remember(goalVersion) { LiburuPrefs.yearGoal(context) }
            var editing by remember { mutableStateOf(false) }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalRing(finished.size, goal)
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Meta de $year", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        val start = LocalDate.of(year, 1, 1)
                        val expected = if (year == now.year) goal * (now.dayOfYear.toDouble() / start.lengthOfYear()) else 0.0
                        Text(
                            when {
                                goal <= 0 -> "Aún no has puesto una meta"
                                finished.size >= goal -> "¡Meta cumplida! 🎉"
                                year == now.year && finished.size >= expected -> if (finished.size - expected >= 1) "Vas bien: ${(finished.size - expected).toInt()} por delante del ritmo" else "Vas bien: al ritmo previsto"
                                year == now.year -> "Vas ${kotlin.math.ceil(expected - finished.size).toInt()} por detrás del ritmo"
                                else -> "${(finished.size * 100 / goal.coerceAtLeast(1))} % completado"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { editing = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                            Text(if (goal > 0) "Cambiar meta" else "Establecer meta de lectura")
                        }
                    }
                }
            }
            if (editing) {
                var v by remember { mutableStateOf(goal.toString()) }
                AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text("Meta de $year") },
                    text = { OutlinedTextField(v, { v = it.filter(Char::isDigit) }, label = { Text("¿Cuántos libros quieres leer este año?") }, singleLine = true) },
                    confirmButton = {
                        TextButton(onClick = {
                            v.toIntOrNull()?.takeIf { it > 0 }?.let {
                                LiburuPrefs.setYearGoal(context, it)
                                context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                            }
                            goalVersion++; editing = false
                        }) { Text("Guardar") }
                    },
                )
            }
        }
        // Series
        item {
            val ser = LinkedHashMap<String, MutableList<Book>>()
            finished.forEach { (k, b) -> data.series(k)?.let { ser.getOrPut(it) { mutableListOf() } += b } }
            Card("Series", label) {
                BigNum("${ser.size}", if (ser.size == 1) "serie" else "series")
                if (ser.isEmpty()) Empty("No terminaste libros de series ${if (year == now.year) "este año." else "en $year."}")
                ser.entries.take(5).forEach { (n, bs) -> RowItem(bs.take(3), n, "${bs.size}") }
            }
        }
        // Decades
        item {
            val dec = sortedMapOf<Int, Int>()
            finished.forEach { (k, _) -> data.year(k)?.let { y -> dec[y / 10 * 10] = (dec[y / 10 * 10] ?: 0) + 1 } }
            Card("Cuándo se escribieron", label) {
                if (dec.isEmpty()) Empty("Los años de publicación aparecen a medida que terminas libros.")
                else {
                    VBars(dec.values.map { it.toDouble() }, dec.keys.map { "${it.toString().takeLast(2)}s" }, 120.dp)
                    finished.mapNotNull { (k, b) -> data.year(k)?.let { b to it } }.minByOrNull { it.second }?.let { (b, y) ->
                        Text("El más antiguo: ${b.title} ($y)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Authors
        item {
            val au = HashMap<String, Int>()
            finished.forEach { (_, b) -> b.author?.takeIf { it.isNotBlank() }?.let { au[it] = (au[it] ?: 0) + 1 } }
            val minA = HashMap<String, Double>()
            sits.forEach { s -> data.byKey[s.key]?.author?.takeIf { it.isNotBlank() }?.let { minA[it] = (minA[it] ?: 0.0) + s.seconds / 60 } }
            val top = (au.keys + minA.keys).distinct().sortedWith(compareByDescending<String> { au[it] ?: 0 }.thenByDescending { minA[it] ?: 0.0 }).take(6)
            Card("Autores", label) {
                if (top.isEmpty()) Empty("Termina un libro para ver tus autores.")
                else {
                    BigNum("${au.size}", if (au.size == 1) "autor terminado" else "autores terminados")
                    top.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                            Text(a, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(au[a]?.let { "$it ${if (it == 1) "libro" else "libros"}" }, fmtMin(minA[a] ?: 0.0)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        // Ratings
        item {
            val rated = finished.mapNotNull { (k, b) -> data.rating[k]?.let { b to it } }
            Card("Calificaciones", label) {
                if (rated.isEmpty()) Empty("Califica los libros que terminas para ver esto.")
                else {
                    val avg = rated.map { it.second }.average()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigNum(String.format(ES, "%.1f", avg), "media")
                        Spacer(Modifier.width(18.dp))
                        HBars((5 downTo 1).map { n -> "$n ★" to rated.count { Math.round(it.second).toInt() == n } }, Modifier.weight(1f))
                    }
                    Text("Tus favoritos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CoverShelf(rated.sortedByDescending { it.second }.take(4).map { it.first }, onOpenBook)
                }
            }
        }
        // Pages per month
        item {
            val pm = DoubleArray(12)
            sits.forEach { pm[dayOf(it.start, zone).monthValue - 1] += data.sitPages(it) }
            val last = if (year == now.year) now.monthValue - 1 else 11
            Card("Páginas", label) {
                BigNum(fmtNum(totalPages), "páginas")
                LineChart(pm.take(last + 1))
                Text("Calculadas con tu avance en cada sesión y el número de páginas de cada edición.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Time and reader clock
        item {
            val hours = DoubleArray(24)
            sits.forEach { hours[Instant.ofEpochMilli(it.start).atZone(zone).hour] += it.seconds / 60 }
            val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
            val night = hours.slice(20..23).sum() + hours.slice(0..3).sum()
            val morning = hours.slice(5..11).sum()
            val chrono = when { totalMin < 5 -> null; night > morning * 1.3 -> "Búho"; morning > night * 1.3 -> "Alondra"; else -> "Lector de tarde" }
            val days = sits.map { dayOf(it.start, zone) }.toSet().size
            Card("Tiempo de lectura", label) {
                if (totalMin < 1) Empty("Abre cualquier libro y la app cronometra sola cada sesión, en todos tus dispositivos.")
                else {
                    Tiles(listOf(fmtMin(totalMin) to "en total", fmtMin(totalMin / days.coerceAtLeast(1)) to "por día leído", fmtMin((sits.maxOfOrNull { it.seconds } ?: 0.0) / 60) to "sesión más larga"))
                    chrono?.let {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Text("sueles leer entre las $peak y las ${(peak + 1) % 24} h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    VBars(hours.toList(), null, 64.dp, highlight = peak)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("0 h", "6 h", "12 h", "18 h", "23 h").forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        // Speed
        item {
            val sp = finished.mapNotNull { (k, b) ->
                val st = data.startedAt[k] ?: data.sits.filter { it.key == k }.minOfOrNull { it.start } ?: return@mapNotNull null
                Triple(b, k, maxOf(1L, (data.finishedAt.getValue(k) - st) / 86_400_000L))
            }.sortedBy { it.third }
            Card("Velocidad para terminar", label) {
                if (sp.isEmpty()) Empty("Añade fechas de inicio para ver cuánto tardas en leer tus libros.")
                else {
                    val avg = sp.map { it.third }.average().toInt()
                    BigNum("$avg", if (avg == 1) "día de media" else "días de media")
                    RowItem(listOf(sp.first().first), sp.first().first.title, "${sp.first().third} días", "El más rápido", onOpenBook)
                    if (sp.size > 1) RowItem(listOf(sp.last().first), sp.last().first.title, "${sp.last().third} días", "El que más te llevó", onOpenBook)
                }
            }
        }
        // Genres
        item {
            val gen = HashMap<String, Int>()
            finished.forEach { (k, _) ->
                val cats = data.genres(k)?.optJSONArray("c")?.let { a -> (0 until a.length()).flatMap { a.getString(it).split("/").map(String::trim) } }.orEmpty()
                cats.filter { it.isNotEmpty() }.distinct().take(4).forEach { gen[it] = (gen[it] ?: 0) + 1 }
            }
            val top = gen.entries.sortedByDescending { it.value }.take(8)
            Card("Géneros", label) {
                BigNum("${gen.size}", if (gen.size == 1) "género" else "géneros")
                if (top.isEmpty()) Empty(if (finished.isEmpty()) "Termina un libro para ver tus géneros." else "Los géneros llegan de Google Books a medida que la app los consulta.")
                else HBars(top.map { it.key to it.value }, Modifier.fillMaxWidth(), wide = true)
            }
        }
        // Most read
        item {
            val per = HashMap<String, Double>()
            sits.forEach { per[it.key] = (per[it.key] ?: 0.0) + it.seconds / 60 }
            val top = per.entries.sortedByDescending { it.value }.take(5).mapNotNull { e -> data.byKey[e.key]?.let { it to e.value } }
            if (top.isNotEmpty()) Card("Lo que más has leído", label) {
                top.forEach { (b, m) -> RowItem(listOf(b), b.title, fmtMin(m), b.author, onOpenBook) }
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            TextButton(onClick = onClassic) { Text("Más estadísticas (vista detallada) ›", fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun streaks(days: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
    var cur = 0
    var d = if (today in days) today else today.minusDays(1)
    while (d in days) { cur++; d = d.minusDays(1) }
    var best = 0
    days.sorted().fold(null as LocalDate? to 0) { (prev, run), x ->
        val r = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
        best = maxOf(best, r); x to r
    }
    return cur to maxOf(best, cur)
}

@Composable
private fun Card(title: String? = null, sub: String? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            content()
        }
    }
}

@Composable private fun Caps(t: String) = Text(t.uppercase(ES), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Empty(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun BigNum(n: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(n, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(ES), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun Tiles(tiles: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth()) {
        tiles.forEachIndexed { i, (v, l) ->
            if (i > 0) Box(Modifier.width(1.dp).height(56.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(l.uppercase(ES), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RoundArrow(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(t, style = MaterialTheme.typography.titleLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline) }
    }
}

@Composable
private fun CoverShelf(books: List<Book>, onOpen: (Book) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { it.url }) { b -> BookCover(b, Modifier.width(54.dp).aspectRatio(2f / 3f).clickable { onOpen(b) }) }
    }
}

@Composable
private fun RowItem(covers: List<Book>, title: String, trailing: String, over: String? = null, onOpen: ((Book) -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().let { m -> if (onOpen != null && covers.isNotEmpty()) m.clickable { onOpen(covers.first()) } else m }) {
        Box(Modifier.width(if (covers.size > 1) 64.dp else 36.dp)) {
            covers.forEachIndexed { i, b -> BookCover(b, Modifier.padding(start = (i * 14).dp).width(36.dp).aspectRatio(2f / 3f)) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (over != null) Text(over, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HBars(rows: List<Pair<String, Int>>, modifier: Modifier, wide: Boolean = false) {
    val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(if (wide) 130.dp else 40.dp))
                Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(c.toFloat() / max).fillMaxHeight().background(if (wide) MaterialTheme.colorScheme.primary else Gold, RoundedCornerShape(4.dp)))
                }
                Text("$c", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).width(24.dp))
            }
        }
    }
}

@Composable
private fun VBars(values: List<Double>, labels: List<String>?, height: Dp, highlight: Int? = null) {
    val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(0.0001)
    val accent = MaterialTheme.colorScheme.primary
    val soft = accent.copy(alpha = 0.35f)
    Row(Modifier.fillMaxWidth().height(height + if (labels != null) 18.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(height * (v / max).toFloat()).background(if (highlight == null || highlight == i) accent else soft, RoundedCornerShape(3.dp)))
                labels?.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun LineChart(values: List<Double>) {
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val step = size.width / 11f
            val base = size.height - 6f
            drawLine(line, Offset(0f, base), Offset(size.width, base), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f)))
            val pts = values.mapIndexed { i, v -> Offset(i * step, base - (v / max).toFloat() * (size.height - 20f)) }
            if (pts.size > 1) {
                val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                val fill = Path().apply { addPath(p); lineTo(pts.last().x, base); lineTo(0f, base); close() }
                drawPath(fill, accent.copy(alpha = 0.12f))
                drawPath(p, accent, style = Stroke(width = 7f, cap = StrokeCap.Round))
            }
            pts.forEachIndexed { i, o -> if (values[i] > 0) drawCircle(accent, 9f, o) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            "EFMAMJJASOND".forEach { Text("$it", style = MaterialTheme.typography.labelSmall, color = ink) }
        }
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 20f
            drawArc(track, 0f, 360f, false, style = Stroke(sw), topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw))
            val pct = if (goal > 0) (done.toFloat() / goal).coerceAtMost(1f) else 0f
            drawArc(accent, -90f, 360f * pct, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("de ${if (goal > 0) goal else "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The month as on the web: totals, then a calendar whose days wear the cover read most. */
@Composable
private fun MonthCard(data: StatsData, ym: YearMonth, zone: ZoneId, onOpen: (Book) -> Unit, onMonth: (YearMonth) -> Unit) {
    val now = YearMonth.now(zone)
    val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val ms = data.sits.filter { it.start in from until to }
    val done = data.finishedAt.values.count { it in from until to }
    val pages = ms.sumOf { data.sitPages(it) }
    val elapsed = if (ym == now) LocalDate.now(zone).dayOfMonth else ym.lengthOfMonth()
    val byDay = ms.groupBy { dayOf(it.start, zone).dayOfMonth }
    var dayOpen by remember { mutableStateOf<Int?>(null) }
    Card {
        // The web's drawing of the month, with its name over it.
        val ctx = LocalContext.current
        val ink = MONTH_INK[ym.monthValue - 1]
        Box(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 0.dp).background(Color.Transparent, RoundedCornerShape(16.dp))) {
            coil3.compose.AsyncImage(
                model = remember(ym.monthValue) {
                    coil3.request.ImageRequest.Builder(ctx).data("file:///android_asset/months/m${ym.monthValue - 1}.svg")
                        .decoderFactory(coil3.svg.SvgDecoder.Factory()).build()
                },
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().androidxClip(),
            )
            Column(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = ink)
                Text("${ym.year}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ink)
            }
            Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { RoundArrow("‹") { onMonth(ym.minusMonths(1)) } }
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { RoundArrow("›", enabled = ym < now) { onMonth(ym.plusMonths(1)) } }
        }
        Tiles(listOf("$done" to "libros", fmtNum(pages) to "páginas", "${if (elapsed > 0) (pages / elapsed).toInt() else 0}" to "páginas/día"))
        Row(Modifier.fillMaxWidth()) {
            listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val lead = ym.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..ym.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val list = byDay[day].orEmpty()
                            val top = list.groupBy { it.key }.maxByOrNull { e -> e.value.sumOf { it.seconds } }?.key?.let { data.byKey[it] }
                            val today = ym == now && day == LocalDate.now(zone).dayOfMonth
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                                    .clickable { dayOpen = day },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (top != null) BookCover(top, Modifier.fillMaxSize())
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (top != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = if (top != null) Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(horizontal = 6.dp) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    var adding by remember { mutableStateOf<LocalDate?>(null) }
    androidx.compose.material3.OutlinedButton(onClick = { adding = LocalDate.now(zone) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("＋ Agregar sesión", fontWeight = FontWeight.SemiBold)
    }
    adding?.let { d -> AddSessionDialog(data.books, d, zone) { adding = null } }
    dayOpen?.let { day ->
        val list = byDay[day].orEmpty().sortedBy { it.start }
        AlertDialog(
            onDismissRequest = { dayOpen = null },
            title = { Text(ym.atDay(day).let { "${it.dayOfWeek.getDisplayName(TextStyle.FULL, ES)}, $day de ${ym.month.getDisplayName(TextStyle.FULL, ES)}" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (list.isEmpty()) Text("No leíste este día.")
                    list.forEach { s ->
                        val b = data.byKey[s.key]
                        val t = Instant.ofEpochMilli(s.start).atZone(zone).toLocalTime()
                        RowItem(listOfNotNull(b), b?.title ?: "Libro", fmtMin(s.seconds / 60), "%02d:%02d".format(t.hour, t.minute) + (data.sitPages(s).takeIf { it >= 1 }?.let { " · ${it.toInt()} págs." } ?: ""), onOpen)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dayOpen = null }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { adding = ym.atDay(day); dayOpen = null }) { Text("＋ Agregar sesión este día") } },
        )
    }
}

/** «Agregar sesión»: what was read away from the app (paper, another reader). */
@Composable
private fun AddSessionDialog(books: List<Book>, day: LocalDate, zone: ZoneId, onDone: () -> Unit) {
    val context = LocalContext.current
    val choices = remember(books) {
        books.sortedWith(compareByDescending<Book> { it.lastOpenedAt ?: 0L }.thenBy { it.title.lowercase() })
    }
    var book by remember { mutableStateOf(choices.firstOrNull()) }
    var date by remember { mutableStateOf(day.toString()) }
    var time by remember { mutableStateOf(java.time.LocalTime.now().withSecond(0).withNano(0).toString().take(5)) }
    var minutes by remember { mutableStateOf("30") }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Agregar sesión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Para lo que leíste fuera de la app (en papel, en otro lector…). Las sesiones dentro de la app se registran solas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(book?.title ?: "Libro", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.DropdownMenu(open, { open = false }) {
                        choices.take(80).forEach { b -> androidx.compose.material3.DropdownMenuItem(text = { Text(b.title, maxLines = 1) }, onClick = { book = b; open = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Día (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(time, { time = it }, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(0.6f))
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutos") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = book ?: return@TextButton
                val min = minutes.toIntOrNull()?.takeIf { it > 0 } ?: return@TextButton
                val start = runCatching { LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli() }.getOrNull() ?: return@TextButton
                val end = start + min * 60_000L
                context.container.applicationScopeLaunch {
                    context.container.database.readingSessionDao().insert(
                        ReadingSession(bookUrl = b.url, startedAt = start, endedAt = end, lastCheckpointAt = end, durationMs = min * 60_000L),
                    )
                    context.container.driveStateSync.syncNow(force = true)
                }
                onDone()
            }) { Text("Guardar sesión") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}

// --- Logros, as achieve.js counts them -----------------------------------------------------

private class Achv(val id: String, val name: String, val glyph: String, val tiers: List<Int>, val desc: (Int) -> String)

private val ACHIEVEMENTS = listOf(
    Achv("books", "Lector", "📚", listOf(1, 10, 25, 50, 100)) { "Termina $it ${if (it == 1) "libro" else "libros"}" },
    Achv("pages", "Pasapáginas", "📄", listOf(1000, 5000, 10000, 25000, 50000)) { "Lee ${fmtNum(it)} páginas" },
    Achv("hours", "Horas de vuelo", "⏱", listOf(10, 50, 100, 250, 500)) { "Acumula $it horas de lectura" },
    Achv("streak", "Constancia", "🔥", listOf(7, 30, 100, 365)) { "Lee $it días seguidos" },
    Achv("en", "Bilingüe", "🌍", listOf(1, 5, 15, 30)) { "Termina $it ${if (it == 1) "libro" else "libros"} en inglés" },
    Achv("hl", "Subrayador", "🖍", listOf(10, 100, 500)) { "Haz $it subrayados" },
    Achv("words", "Coleccionista de palabras", "🔤", listOf(10, 100, 500)) { "Guarda $it palabras en tu vocabulario" },
    Achv("classics", "Clásico", "🏛", listOf(1, 5, 15)) { "Termina $it de tu estantería Clásicos" },
    Achv("series", "Sagas", "📚", listOf(3, 10, 25)) { "Termina $it libros que formen parte de una serie" },
    Achv("rated", "Crítico", "⭐", listOf(10, 50)) { "Califica $it libros terminados" },
    Achv("fan", "Fiel", "❤", listOf(3)) { "Termina 3 libros del mismo autor" },
    Achv("owl", "Búho", "🌙", listOf(1)) { "Lee pasada la medianoche" },
    Achv("lark", "Alondra", "☀", listOf(1)) { "Lee antes de las 7 de la mañana" },
    Achv("marathon", "Maratón", "⏳", listOf(1)) { "Lee 2 horas en una sola sesión" },
    Achv("brick", "Tocho", "⛰", listOf(1)) { "Termina un libro de más de 600 páginas" },
    Achv("fast", "Rayo", "⚡", listOf(1)) { "Termina un libro en 3 días o menos" },
    Achv("old", "Arqueólogo", "📜", listOf(1)) { "Termina un libro publicado antes de 1900" },
    Achv("perfect", "Mes perfecto", "📅", listOf(1)) { "Lee todos los días de un mes" },
    Achv("devices", "Nómada", "📱", listOf(3)) { "Lee en 3 dispositivos distintos" },
    Achv("retos", "Retador", "🚩", listOf(1, 6, 12)) { "Completa $it ${if (it == 1) "reto" else "retos"}" },
)
private val TIER_NAMES = listOf("Bronce", "Plata", "Oro", "Rubí", "Diamante")
private val TIER_COLORS = listOf(Color(0xFFD98B5B), Color(0xFF9FB4C7), Color(0xFFE8B83A), Color(0xFFC8455F), Color(0xFF6C8FE0))

@Composable
private fun Achievements(data: StatsData, zone: ZoneId) {
    val context = LocalContext.current
    val highlights by remember { context.container.database.annotationDao().observeAllMarks() }.collectAsState(emptyList())
    val values = remember(data, highlights.size) {
        val fin = data.finishedAt.keys
        val pagesByBook = HashMap<String, Double>()
        data.sits.forEach { pagesByBook[it.key] = (pagesByBook[it.key] ?: 0.0) + data.sitPages(it) }
        fin.forEach { pagesByBook[it] = maxOf(pagesByBook[it] ?: 0.0, data.pagesOf(it)) }
        val days = data.sits.map { dayOf(it.start, zone) }.toSet()
        val perMonth = days.groupBy { YearMonth.from(it) }
        val now = YearMonth.now(zone)
        val byAuthor = fin.mapNotNull { data.byKey[it]?.author }.groupingBy { it }.eachCount()
        val hours = data.sits.map { Instant.ofEpochMilli(it.start).atZone(zone).hour }
        val state = runCatching { JSONObject(File(context.filesDir, DriveStateSync.STATE_CACHE).readText()) }.getOrNull()
        val shelves = state?.optJSONObject("shelves")
        val classic = shelves?.keys()?.asSequence()?.filter { Regex("cl[aá]sic", RegexOption.IGNORE_CASE).containsMatchIn(shelves.optJSONObject(it)?.optString("name").orEmpty()) }?.toSet().orEmpty()
        val lang = { k: String -> data.peek(k)?.optString("lang")?.lowercase() }
        val webAchv = state?.optJSONObject("achv")
        // What the web already counted (words, challenges…) is never lost here.
        fun web(id: String) = webAchv?.optJSONObject(id)?.optInt("tier")?.takeIf { it > 0 }?.let { t -> ACHIEVEMENTS.first { it.id == id }.tiers.getOrNull(t - 1)?.toDouble() } ?: 0.0
        val local = mapOf(
            "books" to fin.size.toDouble(),
            "pages" to pagesByBook.values.sum(),
            "hours" to data.sits.sumOf { it.seconds } / 3600,
            "streak" to streaks(days, LocalDate.now(zone)).second.toDouble(),
            "hl" to highlights.size.toDouble(),
            "classics" to fin.count { k -> data.webBooks.optJSONObject(k)?.optJSONObject("meta")?.optJSONArray("shelves")?.let { a -> (0 until a.length()).any { a.optString(it) in classic } } == true }.toDouble(),
            "series" to fin.count { data.series(it) != null }.toDouble(),
            "rated" to fin.count { it in data.rating }.toDouble(),
            "fan" to (byAuthor.values.maxOrNull() ?: 0).toDouble(),
            "owl" to if (hours.any { it < 5 }) 1.0 else 0.0,
            "lark" to if (hours.any { it in 5..6 }) 1.0 else 0.0,
            "marathon" to if (data.sits.any { it.seconds >= 7200 }) 1.0 else 0.0,
            "brick" to if (fin.any { data.pagesOf(it) >= 600 }) 1.0 else 0.0,
            "fast" to if (fin.any { k -> data.startedAt[k]?.let { s -> data.finishedAt.getValue(k) - s <= 3 * 86_400_000L } == true }) 1.0 else 0.0,
            "old" to if (fin.any { k -> data.year(k)?.let { it < 1900 } == true }) 1.0 else 0.0,
            "perfect" to if (perMonth.any { (m, d) -> m != now && d.size >= m.lengthOfMonth() }) 1.0 else 0.0,
            "en" to fin.count { lang(it)?.startsWith("en") == true }.toDouble(),
            "words" to com.chmouel.liseur.data.gdrive.LiburuShared.vocab(context).size.toDouble(),
            "devices" to data.sits.map { it.device }.filter { it.isNotEmpty() }.toSet().size.toDouble(),
            "retos" to 0.0,
        )
        ACHIEVEMENTS.associate { a -> a.id to maxOf(local[a.id] ?: 0.0, web(a.id)) }
    }
    val earned = ACHIEVEMENTS.sumOf { a -> a.tiers.count { (values[a.id] ?: 0.0) >= it } }
    val total = ACHIEVEMENTS.sumOf { it.tiers.size }
    var open by remember { mutableStateOf<Achv?>(null) }
    Card("Logros", "$earned de $total") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(ACHIEVEMENTS, key = { it.id }) { a ->
                val v = values[a.id] ?: 0.0
                val tier = a.tiers.count { v >= it }
                val color = if (tier == 0) MaterialTheme.colorScheme.surfaceContainerHighest else if (a.tiers.size == 1) Gold else TIER_COLORS[tier - 1]
                Column(Modifier.width(84.dp).clickable { open = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        Text(a.glyph, fontSize = 28.sp, modifier = Modifier.then(if (tier == 0) Modifier.background(Color.Transparent) else Modifier), color = Color.Unspecified)
                        if (tier == 0) Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), CircleShape))
                    }
                    Text(a.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                    Text(if (tier == 0) "Por conseguir" else if (a.tiers.size == 1) "Conseguido" else TIER_NAMES[tier - 1], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    open?.let { a ->
        val v = values[a.id] ?: 0.0
        val tier = a.tiers.count { v >= it }
        val next = a.tiers.getOrNull(tier)
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("${a.glyph}  ${a.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.tiers.forEachIndexed { i, t ->
                        Text((if (v >= t) "✓ " else "○ ") + (if (a.tiers.size > 1) "${TIER_NAMES[i]}: " else "") + a.desc(t), color = if (v >= t) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    next?.let { Text("Llevas ${fmtNum(v)} de ${fmtNum(it)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Cerrar") } },
        )
    }
}

// --- Resúmenes, as stories ------------------------------------------------------------------

@Composable
private fun RecapChips(data: StatsData, zone: ZoneId) {
    val now = YearMonth.now(zone)
    val chips = buildList {
        add(Triple(now.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() }, "en curso", now as Any))
        add(Triple("${now.year}", "tu año", now.year as Any))
        (1..5).forEach { k -> val m = now.minusMonths(k.toLong()); add(Triple(m.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() }, "${m.year}", m as Any)) }
    }
    var open by remember { mutableStateOf<Any?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Resúmenes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Como historias, para ver y compartir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chips, key = { it.third.toString() }) { (t, sub, p) ->
                Surface(onClick = { open = p }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    open?.let { p -> RecapStory(data, zone, p) { open = null } }
}

private class Slide(val kicker: String, val big: String, val text: String, val books: List<Book> = emptyList())

@Composable
private fun RecapStory(data: StatsData, zone: ZoneId, period: Any, onClose: () -> Unit) {
    val context = LocalContext.current
    val from: Long
    val to: Long
    val label: String
    val what: String
    if (period is YearMonth) {
        val n = period.month.getDisplayName(TextStyle.FULL_STANDALONE, ES)
        from = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        to = period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        label = "${n.replaceFirstChar { it.uppercase() }} ${period.year}"; what = "Tu $n"
    } else {
        val y = period as Int
        from = LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        to = LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        label = "$y"; what = "Tu $y"
    }
    val ses = data.sits.filter { it.start in from until to }
    val minutes = ses.sumOf { it.seconds } / 60
    val days = ses.map { dayOf(it.start, zone) }.toSet()
    val fin = data.finishedAt.filter { it.value in from until to }.entries.sortedBy { it.value }.mapNotNull { data.byKey[it.key] }
    val perBook = ses.groupBy { it.key }.mapValues { e -> e.value.sumOf { it.seconds } / 60 }.entries.sortedByDescending { it.value }
    val hours = DoubleArray(24); ses.forEach { hours[Instant.ofEpochMilli(it.start).atZone(zone).hour] += it.seconds / 60 }
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val topAuthor = (perBook.mapNotNull { e -> data.byKey[e.key]?.author?.let { it to e.value } } + fin.mapNotNull { b -> b.author?.let { it to 30.0 } })
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.maxByOrNull { it.value }?.key
    val fav = fin.filter { data.rating[keyOfBook(it)] != null }.maxByOrNull { data.rating[keyOfBook(it)]!! } ?: perBook.firstOrNull()?.let { data.byKey[it.key] }
    val slides = buildList {
        add(Slide("Resumen de lectura", "$what en libros", "Desliza para ver más"))
        if (minutes < 1 && fin.isEmpty()) add(Slide(label, "Aún sin lecturas", "Abre un libro y la app irá contando el tiempo sola."))
        else {
            add(Slide("Tiempo de lectura", fmtMin(minutes), "en ${days.size} ${if (days.size == 1) "día" else "días"} y ${ses.size} ${if (ses.size == 1) "sesión" else "sesiones"}"))
            add(Slide("Libros terminados", "${fin.size}", if (fin.isEmpty()) "Ninguno terminado todavía" else fin.joinToString(", ") { it.title }.take(140), fin.take(6)))
            perBook.take(3).mapNotNull { data.byKey[it.key] }.takeIf { it.isNotEmpty() }?.let { add(Slide("Lo que más leíste", it.first().title, it.first().author.orEmpty(), it)) }
            if (minutes >= 5) add(Slide("Tu hora", when { peak in 5..11 -> "Alondra"; peak in 12..19 -> "Lector de tarde"; else -> "Búho" }, "Sueles leer entre las $peak y las ${(peak + 1) % 24} h"))
            topAuthor?.let { add(Slide("Tu autor", it, "Quien más te ha acompañado")) }
            fav?.let { add(Slide("Tu favorito", it.title, it.author.orEmpty(), listOf(it))) }
        }
    }
    var index by remember { mutableIntStateOf(0) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val s = slides[index]
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF1B1A17))))
                .clickable { if (index < slides.size - 1) index++ else onClose() },
        ) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i -> Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = if (i <= index) 0.9f else 0.3f), RoundedCornerShape(2.dp))) }
            }
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(s.kicker.uppercase(ES), color = Color.White.copy(alpha = 0.8f), letterSpacing = 2.sp, style = MaterialTheme.typography.labelLarge)
                Text(s.big, color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(s.text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (s.books.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.books.take(4).forEach { BookCover(it, Modifier.width(70.dp).aspectRatio(2f / 3f)) }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { shareSlide(context, label, s) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B1A17))) { Text("Compartir") }
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)) { Text("Cerrar") }
            }
        }
    }
}

private fun keyOfBook(b: Book) = keyOf(b.url)

/** The slide as a 1080×1920 image in the reader's accent, for stories. */
private fun shareSlide(context: Context, label: String, s: Slide) {
    val w = 1080; val h = 1920
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val accent = com.chmouel.liseur.ui.theme.LiburuAppearance.accentColor(com.chmouel.liseur.ui.theme.LiburuAppearance.look.value)
    val argb = android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())
    c.drawPaint(android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), argb, 0xFF1B1A17.toInt(), android.graphics.Shader.TileMode.CLAMP) })
    val serif = runCatching { android.graphics.Typeface.createFromAsset(context.assets, "fonts/Fraunces.ttf") }.getOrNull()
    fun text(t: String, y: Float, size: Float, face: android.graphics.Typeface?, alpha: Int) {
        val p = android.text.TextPaint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = size; typeface = face }
        val layout = android.text.StaticLayout.Builder.obtain(t, 0, t.length, p, w - 160).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
        c.save(); c.translate(80f, y); layout.draw(c); c.restore()
    }
    text(s.kicker.uppercase(ES), 620f, 44f, null, 210)
    text(s.big, 720f, 120f, serif, 255)
    text(s.text, 1180f, 48f, null, 220)
    text("Liburuville · $label", 1760f, 40f, null, 180)
    val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
    val f = File(dir, "resumen.png")
    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".quotes", f)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, "Compartir resumen"))
}

private val MONTH_INK = listOf("#fff", "#fff", "#21412b", "#4a1420", "#3b2a05", "#06324a", "#fff", "#fff", "#3a1430", "#fff", "#fff", "#fff")
    .map { Color(android.graphics.Color.parseColor(if (it.length == 4) "#" + it.drop(1).map { c -> "$c$c" }.joinToString("") else it)) }

private fun Modifier.androidxClip() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

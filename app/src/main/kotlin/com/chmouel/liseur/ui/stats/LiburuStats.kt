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
private data class Sit(val key: String, val start: Long, val seconds: Double, val from: Double?, val to: Double?, val pages: Double?)

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
                o.optDouble("fromFrac").takeIf { !it.isNaN() }, o.optDouble("toFrac").takeIf { !it.isNaN() }, o.optDouble("pages").takeIf { !it.isNaN() })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundArrow("‹") { onMonth(ym.minusMonths(1)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("${ym.year}", style = MaterialTheme.typography.titleLarge)
            }
            RoundArrow("›", enabled = ym < now) { onMonth(ym.plusMonths(1)) }
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
        )
    }
}

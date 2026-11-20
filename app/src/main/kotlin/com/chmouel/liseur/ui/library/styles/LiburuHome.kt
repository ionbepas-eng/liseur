package com.chmouel.liseur.ui.library.styles

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chmouel.liseur.LiseurApplication
import com.chmouel.liseur.R
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.ReadingSession
import com.chmouel.liseur.data.quotes.HomeQuote
import com.chmouel.liseur.ui.library.BookCover
import com.chmouel.liseur.ui.library.LibraryUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.map

/** Liburuville's goals and profile, shared with the web app through Drive. */
object LiburuPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("liburu", Context.MODE_PRIVATE)
    fun dailyGoal(context: Context) = prefs(context).getInt("dailyGoalMin", 30)
    fun setDailyGoal(context: Context, minutes: Int, at: Long = System.currentTimeMillis()) =
        prefs(context).edit().putInt("dailyGoalMin", minutes).putLong("goalsT", at).apply()
    fun yearGoal(context: Context) = prefs(context).getInt("yearGoal", 24)
    fun setYearGoal(context: Context, books: Int, at: Long = System.currentTimeMillis()) =
        prefs(context).edit().putInt("yearGoal", books).putLong("goalsT", at).apply()
    fun goalsChangedAt(context: Context) = prefs(context).getLong("goalsT", 0L)
    fun name(context: Context) = prefs(context).getString("name", null)
    fun setName(context: Context, name: String?, at: Long = System.currentTimeMillis()) =
        prefs(context).edit().putString("name", name).putLong("profileT", at).apply()
    fun profileChangedAt(context: Context) = prefs(context).getLong("profileT", 0L)
    fun avatarFile(context: Context) = java.io.File(context.filesDir, "liburu-avatar.jpg")

    /** Bumped whenever the photo changes, so Compose reloads it. */
    val avatarVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Square-crops and shrinks a picked photo to 320 px, as the web app does. */
    fun setAvatar(context: Context, source: android.net.Uri) {
        val bitmap = context.contentResolver.openInputStream(source)?.use(android.graphics.BitmapFactory::decodeStream) ?: return
        val side = minOf(bitmap.width, bitmap.height)
        val square = android.graphics.Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val small = android.graphics.Bitmap.createScaledBitmap(square, 320, 320, true)
        avatarFile(context).outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
        prefs(context).edit().putLong("profileT", System.currentTimeMillis()).apply()
        avatarVersion.value++
    }

    fun avatarDataUrl(context: Context): String? = avatarFile(context).takeIf { it.exists() }?.readBytes()?.let {
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
    }

    fun adoptAvatarDataUrl(context: Context, dataUrl: String?) {
        if (dataUrl.isNullOrBlank()) { avatarFile(context).delete(); avatarVersion.value++; return }
        val bytes = runCatching { android.util.Base64.decode(dataUrl.substringAfter(","), android.util.Base64.DEFAULT) }.getOrNull() ?: return
        avatarFile(context).writeBytes(bytes)
        avatarVersion.value++
    }
}

/** What the sessions say about this week and the run of days read. */
private data class Rhythm(val minutesToday: Int, val week: List<Boolean>, val streak: Int, val record: Int)

private fun rhythmOf(sessions: List<ReadingSession>, today: LocalDate, zone: ZoneId): Rhythm {
    // Same rule as the statistics: a sitting counts on the day it ended.
    val byDay = sessions
        .filter { it.durationMs > 0 }
        .groupBy { Instant.ofEpochMilli(it.endedAt ?: it.lastCheckpointAt).atZone(zone).toLocalDate() }
        .mapValues { (_, day) -> day.sumOf { it.durationMs } }
    val read = byDay.keys
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = (0..6).map { read.contains(monday.plusDays(it.toLong())) }
    var streak = 0
    var day = if (read.contains(today)) today else today.minusDays(1)
    while (read.contains(day)) { streak++; day = day.minusDays(1) }
    var record = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in read.sorted()) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        record = maxOf(record, run)
        prev = d
    }
    return Rhythm(((byDay[today] ?: 0L) / 60_000).toInt(), week, streak, maxOf(record, streak))
}

/** The next unread books worth starting: the next volume of a series under way first. */
private fun nextReads(books: List<Book>, progressions: Map<String, Double>): List<Book> {
    val open = books.filter { !it.finished && it.url !in progressions && !it.archived }
    val seriesNext = books.filter { it.seriesName != null }
        .groupBy { it.seriesName }
        .mapNotNull { (_, vols) ->
            if (vols.none { it.finished || it.url in progressions }) return@mapNotNull null
            vols.filter { it in open }.minByOrNull { it.seriesIndex ?: Double.MAX_VALUE }
        }
    return (seriesNext + open.sortedByDescending { it.addedAt }).distinctBy { it.url }.take(12)
}

@Composable
internal fun LiburuHome(
    state: LibraryUiState,
    listState: LazyListState,
    quote: HomeQuote?,
    onOpenQuote: (HomeQuote) -> Unit,
    onAnotherQuote: () -> Unit,
    onBookSelected: (Book) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onBrowseAll: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LiseurApplication).container
    val zone = remember { ZoneId.systemDefault() }
    val rhythmFlow = remember {
        container.database.readingSessionDao().observeAll().map { rhythmOf(it, LocalDate.now(zone), zone) }
    }
    val rhythm by rhythmFlow.collectAsState(initial = Rhythm(0, List(7) { false }, 0, 0))
    val allSessions by remember { container.database.readingSessionDao().observeAll() }.collectAsState(initial = emptyList())
    val progress by androidx.compose.runtime.produceState<LiburuProgress.Summary?>(null, allSessions, state.books) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { LiburuProgress.compute(context, allSessions, state.books, zone) }.getOrNull()
        }
    }
    val goal = LiburuPrefs.dailyGoal(context)
    val name by androidx.compose.runtime.produceState(LiburuPrefs.name(context)) {
        if (value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.chmouel.liseur.data.gdrive.DriveAuth.fetchNameBlocking()
            }
        }
    }
    val locale = Locale.getDefault()
    val today = LocalDate.now(zone)

    val reading = remember(state.books, state.progressions) {
        state.books.filter { it.url in state.progressions && !it.finished }
            .sortedByDescending { it.lastOpenedAt ?: 0L }
    }
    val upNext = remember(state.books, state.progressions) { nextReads(state.books, state.progressions) }
    val yearStart = remember { today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val readThisYear = remember(state.books) {
        state.books.filter { (it.finishedAt ?: 0L) >= yearStart }.sortedByDescending { it.finishedAt }
    }
    val gutter = Modifier.padding(horizontal = 20.dp)

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "lh:hello") {
            Row(gutter.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val date = today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale))
                        .replaceFirstChar { it.titlecase(locale) }
                    Text(date, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        name.let { n ->
                            if (n.isNullOrBlank()) stringResource(R.string.liburu_hello_plain)
                            else stringResource(R.string.liburu_hello, n)
                        },
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                GoalRing(rhythm.minutesToday, goal, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Avatar(Modifier.size(64.dp))
            }
        }
        if (quote != null) {
            item(key = "lh:quote") {
                QuoteCard(quote, { onOpenQuote(quote) }, onAnotherQuote, gutter.fillMaxWidth())
            }
        }
        if (reading.isNotEmpty()) {
            item(key = "lh:continue") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.liburu_continue), gutter)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(reading, key = { it.url }) { book ->
                            ContinueCard(book, state.progressions[book.url] ?: 0.0, onBookSelected, onBookLongPress)
                        }
                    }
                }
            }
        }
        if (upNext.isNotEmpty()) {
            item(key = "lh:next") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.liburu_next) + " ›",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = gutter.clickable(onClick = onBrowseAll),
                    )
                    Text(
                        stringResource(R.string.liburu_next_sub),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = gutter,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(upNext, key = { "n:" + it.url }) { book -> NextBook(book, onBookSelected) }
                    }
                }
            }
        }
        item(key = "lh:goals") {
            GoalsCard(
                rhythm = rhythm,
                goal = goal,
                current = reading.firstOrNull(),
                locale = locale,
                onContinue = onBookSelected,
                modifier = gutter.fillMaxWidth(),
            )
        }
        progress?.let { p ->
            item(key = "lh:level") { LevelCard(p.level, gutter.fillMaxWidth()) }
            if (p.retos.isNotEmpty()) {
                item(key = "lh:retos") {
                    Column(gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Retos de ${p.monthName}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        p.retos.forEach { RetoCard(it) }
                    }
                }
            }
        }
        if (readThisYear.isNotEmpty()) {
            item(key = "lh:year") {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.liburu_read_this_year, readThisYear.size, LiburuPrefs.yearGoal(context)),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    readThisYear.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { book ->
                                BookCover(
                                    book,
                                    Modifier.weight(1f).aspectRatio(2f / 3f).clickable { onBookSelected(book) },
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = modifier)
}

@Composable
private fun GoalRing(minutes: Int, goal: Int, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sweep = 360f * (minutes.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
            drawArc(accent, -90f, sweep.coerceAtLeast(2f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$minutes", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), lineHeight = 22.sp)
            Text("$goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

@Composable
private fun ContinueCard(book: Book, progression: Double, onOpen: (Book) -> Unit, onLong: (Book) -> Unit) {
    Surface(
        color = Color(0xFF2B2A27),
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.width(320.dp).clickable { onOpen(book) },
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(78.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = .85f)) }
                Text(
                    stringResource(R.string.liburu_book_percent, (progression * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .8f),
                )
            }
        }
    }
}

@Composable
private fun NextBook(book: Book, onOpen: (Book) -> Unit) {
    Column(Modifier.width(140.dp).clickable { onOpen(book) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = book.seriesName?.let { s -> book.seriesIndex?.let { "$s · ${stringResourceBook(it)}" } ?: s } ?: book.author
        line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}

@Composable
private fun stringResourceBook(index: Double): String =
    stringResource(R.string.liburu_series_volume, if (index % 1.0 == 0.0) index.toInt().toString() else index.toString())

@Composable
private fun GoalsCard(
    rhythm: Rhythm,
    goal: Int,
    current: Book?,
    locale: Locale,
    onContinue: (Book) -> Unit,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = modifier) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.liburu_goals),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.liburu_goals_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(2f), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
                    val stroke = 14.dp.toPx()
                    val d = size.width - stroke
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(track, 180f, 180f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                    val sweep = 180f * (rhythm.minutesToday.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                    if (sweep > 0f) drawArc(accent, 180f, sweep, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.liburu_today), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${rhythm.minutesToday}", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold))
                        Text(" min", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(stringResource(R.string.liburu_of_goal, goal), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (current != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onContinue(current) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.liburu_keep_reading), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(current.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            val today = LocalDate.now().dayOfWeek.value - 1
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEachIndexed { i, dow ->
                    val done = rhythm.week[i]
                    val label = dow.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(if (done) accent else track, CircleShape)
                            .then(if (i == today && !done) Modifier.border(2.dp, accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.liburu_streak, rhythm.streak, rhythm.streak),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.liburu_record, rhythm.record, rhythm.record),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Avatar(modifier: Modifier) {
    val context = LocalContext.current
    val version by LiburuPrefs.avatarVersion.collectAsState()
    val image = remember(version) {
        LiburuPrefs.avatarFile(context).takeIf { it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmapCompat() }
    }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) LiburuPrefs.setAvatar(context, uri) }
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                pick.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            androidx.compose.foundation.Image(
                image,
                contentDescription = stringResource(R.string.liburu_profile_photo),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSizeCompat(),
            )
        } else {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.AddAPhoto,
                contentDescription = stringResource(R.string.liburu_profile_photo),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun android.graphics.Bitmap.asImageBitmapCompat() = asImageBitmap()

@Composable
private fun LevelCard(level: LiburuProgress.Level, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = modifier) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(92.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NIVEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFF1B1A17) else Color(0xFFF3EEE6),
                    )
                    Text(
                        "${level.n}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFF1B1A17) else Color(0xFFF3EEE6),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(level.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                ProgressBar(level.pct, MaterialTheme.colorScheme.primary)
                Text(
                    if (level.to != null) "${level.xp} / ${level.to} puntos · siguiente: ${level.nextName}" else "${level.xp} puntos · nivel máximo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RetoCard(reto: LiburuProgress.Reto) {
    val done = reto.done
    val green = Color(0xFF5B9A55)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reto.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (done) {
                    Box(Modifier.size(30.dp).background(green, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Text(
                        "${reto.value.toInt().coerceAtMost(reto.target.toInt())} / ${reto.target.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ProgressBar((reto.value / reto.target).toFloat(), if (done) green else MaterialTheme.colorScheme.primary)
            Text(
                (if (reto.auto) "Reto del mes" else "Tu reto") + " · " +
                    if (done) "¡Conseguido!" else if (reto.daysLeft == 1) "queda 1 día" else "quedan ${reto.daysLeft} días",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressBar(pct: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
    }
}

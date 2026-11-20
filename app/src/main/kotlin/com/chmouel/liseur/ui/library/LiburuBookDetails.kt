package com.chmouel.liseur.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.R
import com.chmouel.liseur.container
import com.chmouel.liseur.data.calibre.DownloadProgress
import com.chmouel.liseur.data.db.BookAnnotation
import com.chmouel.liseur.data.db.DownloadState
import com.chmouel.liseur.data.db.ReadingSession
import com.chmouel.liseur.domain.FinishedOverride
import com.chmouel.liseur.domain.LibraryFilterOption
import com.chmouel.liseur.domain.PersonalEvaluation
import com.chmouel.liseur.domain.ReadingStatusChoice
import com.chmouel.liseur.domain.displayTitle
import com.chmouel.liseur.domain.seriesIndexLabel
import com.chmouel.liseur.domain.shelfStateFor
import com.chmouel.liseur.domain.validSeriesPosition
import com.chmouel.liseur.ui.BusyIndicator
import com.chmouel.liseur.ui.stats.readingDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Gold = Color(0xFFE8B83A)

/** The book page the way the web draws it: cover on its own colour, then the book, then tabs. */
@Composable
internal fun LiburuBookDetails(
    original: BookDetails,
    etaSeconds: Double?,
    canDownload: Boolean,
    download: DownloadProgress?,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onOpenStats: () -> Unit,
    onSetReadingChoice: (ReadingStatusChoice) -> Unit,
    onSetEvaluation: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val editsVersion by LiburuEdits.version.collectAsState()
    val edits = remember(original.book.url, editsVersion) { LiburuEdits.get(context, original.book.url) }
    val netVersion by LiburuEnrich.version.collectAsState()
    val net = remember(original.book.url, netVersion) { LiburuEnrich.get(context, original.book.url) }
    val details = remember(original, edits, net) { LiburuEdits.apply(original, edits, net) }
    var fetching by remember { mutableStateOf(false) }
    var fetchFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        if (!fetching) scope.launch {
            fetching = true
            val b = LiburuEdits.apply(original, edits)
            fetchFailed = !LiburuEnrich.refresh(
                context, original.book.url, b.book.displayTitle, b.book.author,
                b.metadata.identifiers.firstOrNull { it.scheme == "isbn" }?.value, b.metadata.languages.firstOrNull(),
            )
            fetching = false
        }
    }
    // Like the web: fetched on the first visit and again after a month.
    LaunchedEffect(original.book.url) {
        if (net == null || System.currentTimeMillis() - net.optLong("t") > LiburuEnrich.TTL) refresh()
    }
    var editing by remember { mutableStateOf(false) }
    val book = details.book
    val db = context.container.database
    val sessions by remember(book.url) { db.readingSessionDao().observeFor(book.url) }.collectAsState(emptyList())
    val marks by remember(book.url) { db.annotationDao().observe(book.url) }.collectAsState(emptyList())
    val heroColor by produceState<Color?>(null, book.coverPath, book.coverUrl) {
        value = withContext(Dispatchers.IO) { coverColor(book.coverPath) }
    }
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    var tab by rememberSaveable(book.url) { mutableStateOf(0) }
    val pad = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "hero") { Hero(details, heroColor) }
            item(key = "head") { Head(details, pad.padding(top = 22.dp)) }
            item(key = "actions") {
                Actions(details, etaSeconds, canDownload, download, onRead, onDownload, onSetReadingChoice, onSetEvaluation, pad.padding(top = 18.dp))
            }
            item(key = "tabs") {
                Column(pad.padding(top = 20.dp)) {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = tab,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                    ) {
                        listOf(R.string.liburu_bd_tab_about, R.string.liburu_bd_tab_notes, R.string.liburu_bd_tab_sessions)
                            .forEachIndexed { i, label ->
                                Tab(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    text = {
                                        Text(
                                            stringResource(label),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }
            when (tab) {
                0 -> {
                    details.metadata.description?.let { text ->
                        item(key = "synopsis") {
                            Column(pad.padding(top = 18.dp)) {
                                Text(
                                    stringResource(R.string.liburu_bd_synopsis, "EPUB"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Description(book.url, text, Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    item(key = "facts") { Facts(details, sessions, etaSeconds, pad) }
                    item(key = "biblio") { Biblio(details, net, pad) }
                    item(key = "file") { FileInfo(details, pad) }
                    item(key = "edit") {
                        Column(pad.padding(top = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        fetching -> stringResource(R.string.liburu_bd_fetching)
                                        fetchFailed -> stringResource(R.string.liburu_bd_fetch_failed)
                                        net != null -> stringResource(R.string.liburu_bd_updated, day(net.optLong("t"), LocalLocale.current.platformLocale))
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (fetching) BusyIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else TextButton(onClick = refresh) { Text(stringResource(R.string.liburu_bd_refresh), fontWeight = FontWeight.SemiBold) }
                            }
                            TextButton(onClick = { editing = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.liburu_bd_edit), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                1 -> {
                    item(key = "review") { PersonalEvaluationCard(details, onSetEvaluation, pad.padding(top = 18.dp)) }
                    val shown = marks.filter { it.kind != "BOOKMARK" }
                    if (shown.isEmpty()) {
                        item(key = "nonotes") { Empty(R.string.liburu_bd_no_notes, pad) }
                    } else {
                        items(shown, key = { "n:" + it.id }) { Mark(it, pad.padding(top = 14.dp)) }
                    }
                }
                else -> {
                    if (sessions.isEmpty()) {
                        item(key = "nosessions") { Empty(R.string.liburu_bd_no_sessions, pad) }
                    } else {
                        items(sessions, key = { "s:" + it.id }) { SessionRow(it, pad) }
                    }
                }
            }
        }
        TopButtons(details, scrolled, onBack, onOpenStats, onEdit = { editing = true })
        if (editing) EditDialog(original, edits) { editing = false }
    }
}

@Composable
private fun Hero(details: BookDetails, color: Color?) {
    val bg = color ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding().padding(top = 64.dp, bottom = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        BookCover(
            details.book,
            Modifier
                .width(170.dp)
                .aspectRatio(2f / 3f)
                .shadow(18.dp, RoundedCornerShape(6.dp)),
            shape = RoundedCornerShape(6.dp),
        )
    }
}

@Composable
private fun TopButtons(details: BookDetails, scrolled: Boolean, onBack: () -> Unit, onOpenStats: () -> Unit, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val bar = if (scrolled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f) else Color.Transparent
    val chip = if (scrolled) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 1f) else Color.White.copy(alpha = 0.2f)
    val ink = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White
    Row(
        Modifier.fillMaxWidth().background(bar).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(chip, onBack) { Icon(Icons.Outlined.Close, stringResource(R.string.back), tint = ink) }
        Text(
            if (scrolled) details.book.displayTitle else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Box {
            RoundButton(chip, { menu = true }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.liburu_bd_more), tint = ink) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.liburu_bd_edit)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.reading_stats)) }, onClick = { menu = false; onOpenStats() })
            }
        }
    }
}

@Composable
private fun RoundButton(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = color, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Head(details: BookDetails, modifier: Modifier) {
    val book = details.book
    val meta = details.metadata
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        book.seriesName?.takeIf { it.isNotBlank() }?.let { series ->
            val index = seriesIndexLabel(validSeriesPosition(book.seriesIndex))
            Text(
                if (index == null) series else stringResource(R.string.series_position, series, index),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(book.displayTitle, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        meta.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val author = book.author?.takeIf { it.isNotBlank() }
        val line = listOfNotNull(author, meta.published?.take(4)).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (meta.subjects.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meta.subjects.forEach { s ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(subjectLabel(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
        // Shelves, shared with the web through Drive.
        val version by LiburuShelves.version.collectAsState()
        val (shelves, membership) = remember(version) { LiburuShelves.load(context) }
        val mine = membership[LiburuShelves.keyOf(book.url)].orEmpty()
        var picking by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shelves.filter { it.id in mine }.forEach { shelf ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    onClick = { picking = true },
                ) {
                    Text(shelf.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            val dash = MaterialTheme.colorScheme.outline
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .drawBehind {
                        drawRoundRect(
                            dash,
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                        )
                    }
                    .clickable { picking = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.liburu_bd_shelves), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (picking) ShelfPicker(book.url, shelves, mine) { picking = false }
    }
}

@Composable
private fun ShelfPicker(bookUrl: String, shelves: List<LiburuShelves.Shelf>, mine: Set<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(mine) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.liburu_bd_shelves)) },
        text = {
            Column {
                shelves.sortedBy { it.name.lowercase() }.forEach { shelf ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = if (shelf.id in chosen) chosen - shelf.id else chosen + shelf.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(shelf.id in chosen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(shelf.name)
                    }
                }
                OutlinedTextField(
                    newName,
                    { newName = it },
                    label = { Text(stringResource(R.string.liburu_bd_new_shelf)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var ids = chosen
                newName.trim().takeIf { it.isNotEmpty() }?.let { ids = ids + LiburuShelves.saveShelf(context, null, it) }
                if (ids != mine) LiburuShelves.setBookShelves(context, bookUrl, ids)
                LiburuShelves.version.value++
                context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                onDone()
            }) { Text(stringResource(R.string.liburu_bd_save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** What the web calls the book's state, from the shelf bucket and the reader's own choice. */
private enum class Status(val label: Int, val choice: ReadingStatusChoice) {
    NONE(R.string.liburu_st_none, ReadingStatusChoice.UNREAD),
    WANT(R.string.liburu_st_want, ReadingStatusChoice.WANT_TO_READ),
    READING(R.string.liburu_st_reading, ReadingStatusChoice.READING),
    PAUSED(R.string.liburu_st_paused, ReadingStatusChoice.PAUSED),
    FINISHED(R.string.liburu_st_finished, ReadingStatusChoice.FINISHED),
    DROPPED(R.string.liburu_st_dropped, ReadingStatusChoice.DID_NOT_FINISH),
}

private fun statusOf(details: BookDetails): Status = when (details.override) {
    FinishedOverride.FINISHED -> Status.FINISHED
    FinishedOverride.WANT_TO_READ -> Status.WANT
    FinishedOverride.PAUSED -> Status.PAUSED
    FinishedOverride.DID_NOT_FINISH -> Status.DROPPED
    FinishedOverride.READING -> Status.READING
    FinishedOverride.UNREAD -> Status.NONE
    FinishedOverride.NONE -> when {
        details.book.finished || details.book.finishedAt != null -> Status.FINISHED
        shelfStateFor(details.book, details.progression, details.override) == LibraryFilterOption.IN_PROGRESS -> Status.READING
        else -> Status.NONE
    }
}

@Composable
private fun Actions(
    details: BookDetails,
    etaSeconds: Double?,
    canDownload: Boolean,
    download: DownloadProgress?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onSetReadingChoice: (ReadingStatusChoice) -> Unit,
    onSetEvaluation: (String, String?) -> Unit,
    modifier: Modifier,
) {
    val book = details.book
    val status = statusOf(details)
    val fraction = if (status == Status.FINISHED) 1.0 else (details.progression ?: 0.0).coerceIn(0.0, 1.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(status.label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        val state = book.downloadState
        val big = Modifier.fillMaxWidth().heightIn(min = 58.dp)
        val colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFF111111) else Color.White,
        )
        val label = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        when {
            book.openableUrl != null -> Button(onRead, big, colors = colors) {
                Text(
                    stringResource(
                        when (status) {
                            Status.READING, Status.PAUSED -> R.string.liburu_bd_continue
                            Status.FINISHED -> R.string.liburu_bd_reread
                            else -> R.string.liburu_bd_start
                        },
                    ),
                    style = label,
                )
            }
            download != null || state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING ->
                Button({}, big, enabled = false) {
                    BusyIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.download_in_progress), style = label)
                }
            canDownload && book.downloadHref != null -> Button(onDownload, big, colors = colors) {
                Text(stringResource(if (state == DownloadState.FAILED) R.string.book_details_retry_download else R.string.liburu_bd_start), style = label)
            }
            else -> Button({}, big, enabled = false) { Text(stringResource(R.string.endpaper_unavailable), style = label) }
        }
        var choosing by remember { mutableStateOf(false) }
        Box {
            Surface(
                onClick = { choosing = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(min = 220.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(status.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false).padding(end = 24.dp))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }
            DropdownMenu(choosing, { choosing = false }) {
                Status.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(stringResource(s.label)) }, onClick = { choosing = false; onSetReadingChoice(s.choice) })
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reading_status_automatic)) },
                    onClick = { choosing = false; onSetReadingChoice(ReadingStatusChoice.AUTOMATIC) },
                )
            }
        }
        val rating = details.evaluations.find { it.field == PersonalEvaluation.RATING }?.value?.toIntOrNull()
        val stars = rating?.let { (it + 1) / 2 } ?: 0
        Row {
            (1..5).forEach { n ->
                IconButton(onClick = {
                    onSetEvaluation(PersonalEvaluation.RATING, if (stars == n) null else (n * 2).toString())
                }) {
                    Icon(
                        if (n <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "$n",
                        tint = if (n <= stars) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Facts(details: BookDetails, sessions: List<ReadingSession>, etaSeconds: Double?, modifier: Modifier) {
    val locale = LocalLocale.current.platformLocale
    val total = sessions.sumOf { it.durationMs }
    val counted = sessions.filter { it.durationMs > 0 }
    val started = sessions.minOfOrNull { it.startedAt }
    val zone = ZoneId.systemDefault()
    // The end, at the pace of the days this book was read.
    val eta = etaSeconds?.takeIf { statusOf(details) == Status.READING && it > 0 }?.let { left ->
        val days = counted.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.distinct().size
        if (days == 0 || total <= 0) null
        else {
            val perDay = total.toDouble() / days
            LocalDate.now(zone).plusDays(kotlin.math.ceil(left * 1000 / perDay).toLong().coerceIn(1, 3650))
        }
    }
    val short = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    Column(modifier.padding(top = 8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        val cells = listOf(
            stringResource(R.string.liburu_bd_time_read) to (if (total > 0) readingDuration(total) else "—"),
            stringResource(R.string.liburu_bd_tab_sessions) to (if (counted.isNotEmpty()) counted.size.toString() else "—"),
            stringResource(R.string.liburu_bd_started) to (started?.let { day(it, locale) } ?: "—"),
            stringResource(R.string.liburu_bd_eta) to (eta?.format(short) ?: "—"),
        ) + listOfNotNull(
            details.metadata.publishers.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.book_details_publisher) to it.joinToString(", ") },
        )
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Mark(mark: BookAnnotation, modifier: Modifier) {
    val tint = runCatching { Color(android.graphics.Color.parseColor(mark.tint ?: "")) }.getOrNull() ?: MaterialTheme.colorScheme.primary
    Row(modifier) {
        Box(Modifier.width(4.dp).heightIn(min = 40.dp).background(tint, RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            mark.chapter?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            mark.text?.takeIf { it.isNotBlank() }?.let {
                Text("«${it.trim()}»", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            }
            mark.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SessionRow(s: ReadingSession, modifier: Modifier) {
    val locale = LocalLocale.current.platformLocale
    val at = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    Column(modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(at.format(time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(readingDuration(s.durationMs), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        val from = s.startProgression
        val to = s.endProgression
        if (from != null && to != null) {
            Text(
                "${(from * 100).toInt()} % → ${(to * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Empty(text: Int, modifier: Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 24.dp),
    )
}

/** The cover's average colour, a little darker, like the web's backdrop. */
private fun coverColor(path: String?): Color? {
    val file = path?.let(::File)?.takeIf { it.exists() } ?: return null
    val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
    val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
    val one = Bitmap.createScaledBitmap(bmp, 1, 1, true)
    val c = Color(one.getPixel(0, 0))
    return lerp(c, Color.Black, 0.25f)
}

/** The reader's own version of the book's data; an empty field goes back to the file's. */
@Composable
private fun EditDialog(original: BookDetails, edits: Map<String, String>, onDone: () -> Unit) {
    val context = LocalContext.current
    val m = original.metadata
    val base = mapOf(
        "title" to original.book.displayTitle,
        "author" to original.book.author.orEmpty(),
        "year" to m.published.orEmpty(),
        "genres" to m.subjects.joinToString(", "),
        "publisher" to m.publishers.joinToString(", "),
        "isbn" to m.identifiers.firstOrNull { it.scheme == "isbn" }?.value.orEmpty(),
        "pages" to m.pageCount?.toString().orEmpty(),
        "language" to m.languages.firstOrNull().orEmpty(),
        "synopsis" to m.description.orEmpty(),
    )
    var values by remember { mutableStateOf(LiburuEdits.FIELDS.associateWith { edits[it] ?: base[it].orEmpty() }) }
    val labels = mapOf(
        "title" to R.string.liburu_ed_title, "author" to R.string.liburu_ed_author, "year" to R.string.liburu_ed_year,
        "genres" to R.string.liburu_ed_genres, "publisher" to R.string.liburu_ed_publisher, "isbn" to R.string.liburu_ed_isbn,
        "pages" to R.string.liburu_ed_pages, "language" to R.string.liburu_ed_language, "synopsis" to R.string.liburu_ed_synopsis,
    )
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.liburu_bd_edit)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LiburuEdits.FIELDS) { k ->
                    OutlinedTextField(
                        values[k].orEmpty(),
                        { v -> values = values + (k to v) },
                        label = { Text(stringResource(labels.getValue(k))) },
                        singleLine = k != "synopsis",
                        minLines = if (k == "synopsis") 4 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Only what differs from the file is kept as the reader's.
                LiburuEdits.save(context, original.book.url, values.filter { (k, v) -> v.trim() != base[k].orEmpty().trim() })
                context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                onDone()
            }) { Text(stringResource(R.string.liburu_bd_save)) }
        },
        dismissButton = {
            Row {
                if (edits.isNotEmpty()) {
                    TextButton(onClick = {
                        LiburuEdits.save(context, original.book.url, emptyMap())
                        context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                        onDone()
                    }) {
                        Text(stringResource(R.string.liburu_ed_reset))
                    }
                }
                TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}

/** A section title as on the web: Fraunces, with an accent rule before it. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Biblio(details: BookDetails, net: JSONObject?, modifier: Modifier) {
    val locale = LocalLocale.current.platformLocale
    val m = details.metadata
    val cats = net?.optJSONObject("gb")?.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val rows = listOfNotNull(
        details.book.author?.takeIf { it.isNotBlank() }?.let { R.string.liburu_ed_author to it },
        m.published?.let { R.string.book_details_published to publishedShort(it, locale) },
        m.publishers.takeIf { it.isNotEmpty() }?.let { R.string.book_details_publisher to it.joinToString(", ") },
        m.identifiers.firstOrNull { it.scheme == "isbn" }?.let { R.string.liburu_bd_isbn to it.value },
        m.pageCount?.let { R.string.book_details_pages to it.toString() },
        m.languages.firstOrNull()?.let { R.string.book_details_language to languageName(it, locale) },
        (cats.ifEmpty { m.subjects }).takeIf { it.isNotEmpty() }?.let { R.string.liburu_bd_genres to it.joinToString(", ") },
    )
    if (rows.isEmpty()) return
    Column(modifier) {
        SectionTitle(stringResource(R.string.liburu_bd_biblio))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.38f))
                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.62f))
            }
        }
    }
}

private fun publishedShort(iso: String, locale: java.util.Locale): String =
    runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)) }
        .getOrDefault(iso)

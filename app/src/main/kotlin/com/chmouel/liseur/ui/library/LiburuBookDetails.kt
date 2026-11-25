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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.fillMaxHeight
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
    onSetSeries: (String?, Double?) -> Unit = { _, _ -> },
    onResetSeries: () -> Unit = {},
    onHide: () -> Unit = {},
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
        if (net == null || System.currentTimeMillis() - net.optLong("t") > LiburuEnrich.TTL || (net.optJSONObject("hc")?.optInt("v") ?: 0) < 4) refresh()
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

    // Liburuville: as in Fable, the cover stays put behind and the card slides up over it.
    val sheetColor = MaterialTheme.colorScheme.background.takeIf { it.alpha > 0.99f } ?: MaterialTheme.colorScheme.surfaceContainerLow
    val density = androidx.compose.ui.platform.LocalDensity.current
    val overlap = with(density) { 28.dp.toPx() }
    var heroPx by remember { mutableStateOf(with(density) { 400.dp.toPx() }.toInt()) }
    val scrollPx by remember { derivedStateOf { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE } }
    val sheetTop by remember {
        derivedStateOf {
            val info = list.layoutInfo.visibleItemsInfo
            info.firstOrNull { it.key == "head" }?.offset?.toFloat()
                ?: if (list.firstVisibleItemIndex >= 1) -overlap else (heroPx - overlap)
        }
    }
    Box(Modifier.fillMaxSize().background(sheetColor)) {
        Box(
            Modifier.fillMaxWidth()
                .onSizeChanged { heroPx = it.height }
                .graphicsLayer {
                    val sp = scrollPx.coerceAtMost(heroPx.toFloat())
                    translationY = -sp * 0.45f
                    alpha = (1f - sp / heroPx.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val k = 1f - 0.08f * (sp / heroPx.coerceAtLeast(1))
                    scaleX = k; scaleY = k
                },
        ) { Hero(details, heroColor) }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = 28.dp.toPx()
            drawRoundRect(
                sheetColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, sheetTop),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - sheetTop + r * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "hero") { Spacer(Modifier.fillMaxWidth().height(with(density) { (heroPx - overlap).coerceAtLeast(0f).toDp() })) }
            item(key = "head") { Head(details, pad.padding(top = 30.dp)) }
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
                        listOf(stringResource(R.string.liburu_bd_tab_about), "Comunidad", stringResource(R.string.liburu_bd_tab_notes), stringResource(R.string.liburu_bd_tab_sessions))
                            .forEachIndexed { i, label ->
                                Tab(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    text = {
                                        Text(
                                            label,
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
                    item(key = "synopsis") {
                        val epub = original.metadata.description
                        val gbDesc = net?.optJSONObject("gb")?.optString("desc")?.takeIf { it.isNotBlank() && it != "null" }
                        val text = edits["synopsis"] ?: epub ?: gbDesc
                        val sourceName = when {
                            edits["synopsis"] != null -> stringResource(R.string.liburu_bd_src_you)
                            epub != null -> if (LiburuEnrich.isComic(original.book.url)) "ComicInfo" else "EPUB"
                            else -> net?.optJSONObject("gb")?.optString("src")?.takeIf { it.isNotBlank() } ?: "Google Books"
                        }
                        Column(pad.padding(top = 18.dp)) {
                            if (text != null) {
                                Text(
                                    stringResource(R.string.liburu_bd_synopsis, sourceName),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Description(book.url, text, Modifier.padding(top = 6.dp))
                            } else {
                                val comic = LiburuEnrich.isComic(original.book.url)
                                Text(
                                    if (fetching) "Buscando en internet…" else if (fetchFailed) "No lo he encontrado en internet." else if (comic) "Este cómic no trae sinopsis." else stringResource(R.string.liburu_bd_no_synopsis),
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // As legible as the link chips: ink on the page, a spinner while it looks.
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = refresh, enabled = !fetching,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        ),
                                    ) {
                                        if (fetching) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        if (fetching) Spacer(Modifier.width(8.dp))
                                        Text(if (comic) "Buscar datos del cómic" else stringResource(R.string.liburu_bd_find_synopsis), fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    TextButton(onClick = { editing = true }) {
                                        Text(stringResource(R.string.liburu_bd_write_synopsis), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "facts") { Facts(details, sessions, etaSeconds, pad) }
                    item(key = "biblio") { Biblio(details, net, pad) }
                    net?.optJSONArray("byAuthor")?.takeIf { it.length() > 0 }?.let { arr ->
                        item(key = "byauthor") { ByAuthor(details.book.author.orEmpty(), arr, pad) }
                    }
                    item(key = "where") { WhereToFind(details, pad) }
                    item(key = "file") { DownloadLine(details, canDownload, download, onDownload, pad) }
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
                    val hc = net?.optJSONObject("hc")?.takeIf { it.optBoolean("found") }
                    if (hc != null) item(key = "community") { Community(hc, pad.padding(top = 18.dp)) }
                    else item(key = "nocommunity") {
                        Column(pad.padding(top = 24.dp)) {
                            Text(
                                if (fetching) "Buscando en Hardcover…" else "No he encontrado este libro en Hardcover.",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!fetching) TextButton(onClick = refresh, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.liburu_bd_refresh), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                2 -> {
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
        var assigning by remember { mutableStateOf(false) }
        TopButtons(
            details, scrolled, onBack,
            actions = listOf(
                R.string.liburu_bd_find_synopsis_net to refresh,
                R.string.liburu_bd_write_synopsis_long to { editing = true },
                R.string.liburu_bd_edit to { editing = true },
                R.string.liburu_bd_assign_series to { assigning = true },
                R.string.reading_stats to onOpenStats,
                R.string.liburu_bd_hide to onHide,
            ),
        )
        if (assigning) SeriesDialog(details, onSetSeries, onResetSeries) { assigning = false }
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
                .shadow(18.dp, LiburuCoverShape),
        )
    }
}

@Composable
private fun TopButtons(details: BookDetails, scrolled: Boolean, onBack: () -> Unit, actions: List<Pair<Int, () -> Unit>>) {
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
            if (menu) ActionSheet(details.book.displayTitle, actions) { menu = false }
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
        val tags = remember(meta.subjects) {
            meta.subjects.map { it.split("/", "--", " — ").last().trim() }.filter { it.isNotEmpty() && it.length <= 32 }
                .distinctBy { it.lowercase() }.take(3)
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tags.joinToString(", ")).forEach { s ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
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
        // Side by side when they fit, as on the web; the stars wrap below on a narrow phone.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        var choosing by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { choosing = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.widthIn(min = 180.dp),
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
                        modifier = Modifier.size(30.dp),
                    )
                }
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
internal fun coverColor(path: String?): Color? {
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
        m.identifiers.firstOrNull { it.scheme == "isbn" }?.let {
            // A comic's magazine barcode (977…, with its add-on) is not an ISBN; say what it is.
            val d = it.value.filter(Char::isDigit)
            if (d.startsWith("978") || d.startsWith("979") || d.length == 10) R.string.liburu_bd_isbn to it.value else R.string.liburu_bd_barcode to it.value
        },
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

/** The web's ⋯ sheet: the book's title, one wide button per action, and Close. */
@Composable
private fun ActionSheet(title: String, actions: List<Pair<Int, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { (label, run) ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onDismiss(); run() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
            ) { Text(stringResource(R.string.liburu_bd_close), style = MaterialTheme.typography.titleMedium) }
        },
    )
}

/** Puts the book in a series (or takes it out); Restore goes back to the catalogue's. */
@Composable
private fun SeriesDialog(details: BookDetails, onSet: (String?, Double?) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    var name by remember { mutableStateOf(details.book.seriesName.orEmpty()) }
    var number by remember {
        mutableStateOf(details.book.seriesIndex?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.liburu_bd_assign_series)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.liburu_bd_series_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.liburu_bd_series_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim().takeIf { it.isNotEmpty() }
                onSet(n, if (n == null) null else number.replace(',', '.').toDoubleOrNull())
                onDone()
            }) { Text(stringResource(R.string.liburu_bd_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onReset(); onDone() }) { Text(stringResource(R.string.liburu_ed_reset)) }
                TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun Stars(value: Double, size: androidx.compose.ui.unit.Dp) {
    Row {
        (1..5).forEach { n ->
            Icon(
                if (value >= n - 0.25) Icons.Filled.Star else Icons.Outlined.StarOutline,
                null,
                tint = if (value >= n - 0.25) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** «Opiniones de la comunidad», from Hardcover through the user's Worker. */
@Composable
private fun Community(hc: JSONObject, modifier: Modifier) {
    val context = LocalContext.current
    val rating = hc.optDouble("rating", 0.0)
    val count = hc.optInt("ratingsCount")
    val readers = hc.optInt("readers")
    Column(modifier) {
        SectionTitle(stringResource(R.string.liburu_bd_community))
        hc.optString("original").takeIf { it.isNotBlank() }?.let {
            Text("De la edición original, «$it»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (rating > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(java.util.Locale.getDefault(), if (rating % 1.0 == 0.0) "%.0f" else "%.1f", rating), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                Text("/5", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Stars(rating, 22.dp)
        }
        Text(
            stringResource(R.string.liburu_bd_ratings_readers, count, readers),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        hc.optJSONArray("dist")?.let { d ->
            val counts = (0 until d.length()).map { d.optInt(it) }
            val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                counts.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${5 - i} ★", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(c.toFloat() / counts.sum().coerceAtLeast(1)).height(8.dp).background(Gold))
                        }
                        Text("${Math.round(c * 100f / counts.sum().coerceAtLeast(1))} %", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).width(44.dp))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.liburu_bd_source_hc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        // Fable-style: six review cards side by side and a «+» that opens them all in a pop-up.
        val reviews = hc.optJSONArray("reviews")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.orEmpty()
        var all by remember { mutableStateOf(false) }
        if (reviews.isNotEmpty()) {
            Text("Reseñas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reviews.take(6).size) { i -> ReviewCard(reviews[i], Modifier.width(280.dp).height(230.dp)) { all = true } }
                item {
                    Surface(
                        onClick = { all = true }, shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(110.dp).height(230.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(52.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.surface) }
                            }
                            Text("Ver todas", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        } else {
            Text("Todavía nadie ha escrito una reseña de este libro en Hardcover.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
        }
        if (all) AllReviewsDialog(reviews, hc.optInt("ratingsCount")) { all = false }
        hc.optString("link").takeIf { it.isNotEmpty() }?.let { link ->
            TextButton(onClick = { openUrl(context, link) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.liburu_bd_all_reviews), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Opens the platform's own app when one is installed and takes the link
 * (Goodreads, Spotify, Amazon, Kobo…), the browser otherwise. Android 12+
 * sends unverified links to the browser unless asked for a non-browser app.
 */
private val APP_PKGS = listOf(
    "goodreads.com" to "com.goodreads", "babelio.com" to "com.babelio.babelio", "fable.co" to "co.fable.fable",
    "hardcover.app" to "app.hardcover", "storytel.com" to "grit.storytel.app", "kobo.com" to "com.kobobooks.android",
    "amazon.es" to "com.amazon.kindle", "play.google.com" to "com.android.vending", "open.spotify.com" to "com.spotify.music",
    "ivoox.com" to "com.ivoox.app", "youtube.com" to "com.google.android.youtube",
)

internal fun openUrl(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(url)
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    // Apps that do not claim their own links (Babelio, Fable…) are asked by name, as the web does.
    val host = uri.host.orEmpty().lowercase()
    APP_PKGS.firstOrNull { (h, _) -> host == h || host.endsWith(".$h") }?.second?.let { pkg ->
        if (runCatching { context.startActivity(android.content.Intent(view).setPackage(pkg)) }.isSuccess) return
    }
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        val app = android.content.Intent(view).addFlags(android.content.Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        if (runCatching { context.startActivity(app) }.isSuccess) return
    }
    runCatching { context.startActivity(view) }
}

/** «Otros libros de …», with what the library already holds marked. */
@Composable
private fun ByAuthor(author: String, arr: org.json.JSONArray, modifier: Modifier) {
    val context = LocalContext.current
    val books = (0 until arr.length()).map { arr.getJSONObject(it) }
    val owned by produceState(emptySet<String>(), author) {
        value = withContext(Dispatchers.IO) {
            context.container.database.bookDao().allOnce().filter { it.author == author }.map { it.displayTitle.lowercase().take(14) }.toSet()
        }
    }
    Column(modifier) {
        SectionTitle(stringResource(R.string.liburu_bd_by_author, author))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(books.size) { i ->
                val b = books[i]
                Column(Modifier.width(120.dp).clickable { b.optString("link").takeIf { it.isNotEmpty() }?.let { openUrl(context, it) } }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(5.dp, LiburuCoverShape).clip(LiburuCoverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(b.optString("title"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
                        b.optString("cover").takeIf { it.isNotEmpty() }?.let {
                            coil3.compose.AsyncImage(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    if (b.optString("title").lowercase().take(14) in owned) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), modifier = Modifier.padding(top = 6.dp)) {
                            Text(stringResource(R.string.liburu_dc_in_library), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Text(b.optString("title"), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(listOf(b.optString("author"), b.optString("year")).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** «Dónde encontrarlo»: the web's searches, by kind. */
@Composable
private fun WhereToFind(details: BookDetails, modifier: Modifier) {
    val context = LocalContext.current
    val t = details.book.displayTitle
    val a = details.book.author.orEmpty()
    val enc = { x: String -> java.net.URLEncoder.encode(x, "UTF-8") }
    val q = enc("$t $a".trim())
    val isbn = details.metadata.identifiers.firstOrNull { it.scheme == "isbn" }?.value?.filter { it.isDigit() }?.takeIf { it.length == 13 }
    val ivoox = { x: String ->
        val slug = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
        "https://www.ivoox.com/${slug}_sb.html?sb=${enc(x.trim())}"
    }
    // Liburuville: the shops are gone; communities, podcasts and videos as cards, «Plataforma · Libro/Autor».
    val defaults = listOf(
        R.string.liburu_wf_communities to listOf(
            "Babelio" to "https://www.babelio.com/es/resrecherche.php?Recherche=$q",
            "Margins" to "https://margins.app/search?q=$q",
            "Fable" to "https://fable.co/search?q=$q",
            "Goodreads" to (isbn?.let { "https://www.goodreads.com/book/isbn/$it" } ?: "https://www.goodreads.com/search?q=$q"),
            "Hardcover" to "https://hardcover.app/search?q=$q",
            "Lecturalia" to "https://www.lecturalia.com/buscar?q=$q",
            "Qué libro leo" to "https://quelibroleo.com/buscar?q=$q",
        ),
        R.string.liburu_wf_podcasts to (
            listOf("Spotify · Libro" to "https://open.spotify.com/search/${enc("$t $a".trim())}/episodes", "iVoox · Libro" to ivoox(t)) +
                (if (a.isNotBlank()) listOf("Spotify · Autor" to "https://open.spotify.com/search/${enc(a)}/episodes", "iVoox · Autor" to ivoox(a)) else emptyList())
            ),
        R.string.liburu_wf_videos to (
            listOf("YouTube · Libro" to "https://www.youtube.com/results?search_query=${enc("$t $a reseña".trim())}") +
                (if (a.isNotBlank()) listOf("YouTube · Autor" to "https://www.youtube.com/results?search_query=${enc("$a entrevista")}") else emptyList())
            ),
    )
    val linksVersion by WhereLinks.version.collectAsState()
    val fill = { tpl: String ->
        tpl.replace("{q}", q).replace("{t}", enc(t)).replace("{a}", enc(a)).replace("{isbn}", isbn.orEmpty())
    }
    val groups = remember(linksVersion, t, a) {
        defaults.map { (label, links) ->
            val key = WhereLinks.keyOf(label)
            val hidden = WhereLinks.hidden(context, key)
            label to (links.filter { it.first !in hidden } + WhereLinks.custom(context, key).map { it.first to fill(it.second) })
        }.filter { it.second.isNotEmpty() }
    }
    var editingGroup by remember { mutableStateOf<Int?>(null) }
    Column(modifier) {
        SectionTitle(stringResource(R.string.liburu_bd_where))
        groups.forEach { (label, links) ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.liburu_wf_edit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { editingGroup = label },
                )
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(links.size) { i -> val (name, url) = links[i]; WhereCard(name, url) { openUrl(context, url) } }
            }
        }
    }
    editingGroup?.let { label ->
        WhereLinksDialog(label, defaults.first { it.first == label }.second.map { it.first }) { editingGroup = null }
    }
}

/** The reader's changes to «Dónde encontrarlo»: links hidden and links added, per group. */
object WhereLinks {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("liburu", android.content.Context.MODE_PRIVATE)
    private fun all(c: android.content.Context) = runCatching { JSONObject(prefs(c).getString("whereLinks", "{}")!!) }.getOrDefault(JSONObject())
    fun keyOf(label: Int) = when (label) {
        R.string.liburu_wf_buy -> "buy"; R.string.liburu_wf_audio -> "audio"; R.string.liburu_wf_free -> "free"
        R.string.liburu_wf_communities -> "communities"; R.string.liburu_wf_reviews -> "reviews"; R.string.liburu_wf_podcasts -> "podcasts"
        else -> "videos"
    }
    fun hidden(c: android.content.Context, key: String): Set<String> =
        all(c).optJSONObject(key)?.optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
    fun custom(c: android.content.Context, key: String): List<Pair<String, String>> =
        all(c).optJSONObject(key)?.optJSONArray("custom")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("n") to o.optString("u") } } }.orEmpty()
    fun save(c: android.content.Context, key: String, hidden: Set<String>, custom: List<Pair<String, String>>) {
        val root = all(c).put(key, JSONObject()
            .put("hidden", org.json.JSONArray(hidden.toList()))
            .put("custom", org.json.JSONArray(custom.map { JSONObject().put("n", it.first).put("u", it.second) })))
        prefs(c).edit().putString("whereLinks", root.toString()).putLong("whereLinksT", System.currentTimeMillis()).apply()
        version.value++
    }
}

@Composable
private fun WhereLinksDialog(label: Int, defaultNames: List<String>, onDone: () -> Unit) {
    val context = LocalContext.current
    val key = WhereLinks.keyOf(label)
    var hidden by remember { mutableStateOf(WhereLinks.hidden(context, key)) }
    var custom by remember { mutableStateOf(WhereLinks.custom(context, key)) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(label)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(defaultNames) { n ->
                    Row(Modifier.fillMaxWidth().clickable { hidden = if (n in hidden) hidden - n else hidden + n }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(n !in hidden, null)
                        Text(n, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                items(custom) { (n, u) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(n)
                            Text(u, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { custom = custom - (n to u) }) { Icon(Icons.Outlined.Close, stringResource(R.string.liburu_wf_remove)) }
                    }
                }
                item {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.liburu_wf_add), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.liburu_wf_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(newUrl, { newUrl = it }, label = { Text(stringResource(R.string.liburu_wf_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.liburu_wf_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            enabled = newName.isNotBlank() && newUrl.startsWith("http"),
                            onClick = { custom = custom + (newName.trim() to newUrl.trim()); newName = ""; newUrl = "" },
                        ) { Text(stringResource(R.string.liburu_wf_add_btn)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                WhereLinks.save(context, key, hidden, custom)
                context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                onDone()
            }) { Text(stringResource(R.string.liburu_bd_save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** The web's line about the file: on this device or not, and the one action that goes with it. */
@Composable
private fun DownloadLine(details: BookDetails, canDownload: Boolean, download: DownloadProgress?, onDownload: () -> Unit, modifier: Modifier) {
    val book = details.book
    val onDevice = book.openableUrl != null
    val busy = download != null || book.downloadState == DownloadState.QUEUED || book.downloadState == DownloadState.DOWNLOADING
    Column(modifier) {
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    when {
                        book.downloadState == DownloadState.QUEUED && download == null -> R.string.liburu_bd_queued
                        busy -> R.string.download_in_progress
                        onDevice -> R.string.liburu_bd_on_device
                        else -> R.string.liburu_bd_not_on_device
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            if (!onDevice && (book.downloadState == DownloadState.QUEUED || book.downloadState == DownloadState.FAILED) && download == null && canDownload) {
                TextButton(onClick = {
                    context.getSharedPreferences("liburu", 0).edit().remove("dlErr:${book.url}").apply()
                    scope.launch { context.container.bookDownloads.retry(book) }
                }) { Text("Reintentar", fontWeight = FontWeight.SemiBold) }
            }
            if (onDevice && book.downloadState == DownloadState.DOWNLOADED && book.remoteUuid != null) {
                var confirm by remember { mutableStateOf(false) }
                TextButton(onClick = { confirm = true }) { Text("Quitar descarga", fontWeight = FontWeight.SemiBold) }
                if (confirm) androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("¿Quitar la descarga?") },
                    text = { Text("El archivo se borra de este dispositivo para liberar espacio. El libro sigue en tu biblioteca y en Drive, con tu progreso y tus notas.") },
                    confirmButton = { TextButton(onClick = { confirm = false; scope.launch { context.container.bookDownloads.removeDownload(book) } }) { Text("Quitar") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
                )
            }
            if (!onDevice && !busy && canDownload && book.downloadHref != null) {
                TextButton(onClick = onDownload) { Text(stringResource(R.string.liburu_bd_download), fontWeight = FontWeight.SemiBold) }
            }
        }
        LocalContext.current.getSharedPreferences("liburu", 0).getString("dlErr:${book.url}", null)?.takeIf { !onDevice }?.let {
            Text("Último error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}


@Composable
private fun ReviewHeader(r: JSONObject) {
    val user = r.optString("user").ifBlank { "Lector" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(user.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                r.optString("avatar").takeIf { it.startsWith("http") }?.let { url ->
                    coil3.compose.AsyncImage(model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
            }
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(user, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.optString("date").take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewCard(r: JSONObject, modifier: Modifier, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewHeader(r)
            r.optDouble("rating", 0.0).takeIf { it > 0 }?.let { Stars(it, 16.dp) }
            Text(r.optString("text"), style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AllReviewsDialog(reviews: List<JSONObject>, total: Int, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).fillMaxHeight(0.88f),
        ) {
            Column {
                Row(Modifier.padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${reviews.size} reseñas" + if (total > 0) " · $total valoraciones" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                }
                LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(reviews.size) { i ->
                        val r = reviews[i]
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReviewHeader(r)
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    r.optDouble("rating", 0.0).takeIf { it > 0 }?.let { Stars(it, 18.dp) }
                                    var open by remember { mutableStateOf(false) }
                                    Text(r.optString("text"), style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 7, overflow = TextOverflow.Ellipsis)
                                    if (!open && r.optString("text").length > 320) Text("Leer más", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { open = true })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Liburuville: a place to go as a card — the site's own icon, drawn in two tones of the reading
 * theme (its accent for the ink, the card for the paper) so every logo sits in whatever background
 * is chosen, and the platform's name with «Libro» or «Autor» when the link is about one of them.
 */
@Composable
private fun WhereCard(name: String, url: String, onClick: () -> Unit) {
    val platform = name.substringBefore(" · ")
    val about = name.substringAfter(" · ", "").takeIf { it.isNotBlank() }
    val host = runCatching { java.net.URI(url).host?.removePrefix("www.")?.removePrefix("open.") }.getOrNull().orEmpty()
    val card = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f)
    val ink = MaterialTheme.colorScheme.primary
    val duotone = remember(card, ink) {
        val l = floatArrayOf(0.299f, 0.587f, 0.114f)
        fun row(c: Float, i: Float) = floatArrayOf((c - i) * l[0], (c - i) * l[1], (c - i) * l[2], 0f, i * 255f)
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                row(card.red, ink.red) + row(card.green, ink.green) + row(card.blue, ink.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f),
            ),
        )
    }
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), color = card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(112.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Text(platform.take(1), style = MaterialTheme.typography.titleLarge, color = ink)
                if (host.isNotBlank()) coil3.compose.AsyncImage(
                    model = "https://www.google.com/s2/favicons?domain=$host&sz=128",
                    contentDescription = null,
                    colorFilter = duotone,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(platform, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (about != null) {
                Surface(shape = RoundedCornerShape(50), color = ink.copy(alpha = 0.12f)) {
                    Text(about, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            } else {
                Text("↗", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

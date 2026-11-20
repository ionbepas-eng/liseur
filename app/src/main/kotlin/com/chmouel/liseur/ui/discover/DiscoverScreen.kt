package com.chmouel.liseur.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chmouel.liseur.R
import com.chmouel.liseur.container
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.opds.FreeCatalog
import com.chmouel.liseur.data.remote.BrowseOwnedState
import com.chmouel.liseur.data.remote.RemoteBook
import com.chmouel.liseur.domain.displayTitle
import com.chmouel.liseur.ui.BusyIndicator
import com.chmouel.liseur.ui.library.BookCover
import com.chmouel.liseur.ui.library.LiburuShelves
import com.chmouel.liseur.ui.library.styles.Avatar
import com.chmouel.liseur.ui.library.styles.LiburuNewsRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** A book suggested from the reader's own library, with why. */
private data class Pick(val book: Book, val why: String)

/**
 * Liburuville's Descubrir: what to read next from the library, the next
 * book of each series, free classics to add, and literary news.
 */
@Composable
fun DiscoverScreen(
    books: List<Book>,
    progressions: Map<String, Double>,
    onOpenBook: (Book) -> Unit,
    bottomBarSpace: Dp,
) {
    val context = LocalContext.current
    var openList by rememberSaveable { mutableStateOf<String?>(null) }
    val rated by remember { context.container.database.bookEvaluationDao().observeRatedBooks() }.collectAsState(emptyList())
    val strNext = stringResource(R.string.liburu_dc_why_next)
    val strAuthor = stringResource(R.string.liburu_dc_why_author)
    val strShelf = stringResource(R.string.liburu_dc_why_shelf)
    val strBook = stringResource(R.string.liburu_dc_book_n)
    val (picks, series) = remember(books, progressions, rated) {
        suggestions(context, books, progressions, rated.toSet(), strNext, strAuthor, strShelf, strBook)
    }
    val netRows by produceState<List<LiburuDiscoverNet.Row>?>(null, books.size, rated.size) {
        value = runCatching { LiburuDiscoverNet.rows(context, books, rated.toSet()) }.getOrDefault(emptyList())
    }
    val wishVersion by com.chmouel.liseur.data.gdrive.LiburuShared.version.collectAsState()
    val wish = remember(wishVersion) { com.chmouel.liseur.data.gdrive.LiburuShared.wishlist(context) }
    var ext by remember { mutableStateOf<LiburuDiscoverNet.Ext?>(null) }
    var wishOpen by rememberSaveable { mutableStateOf(false) }
    ext?.let { e -> ExtSheet(e) { ext = null } }
    if (wishOpen) {
        BackHandler { wishOpen = false }
        WishlistScreen(wish, bottomBarSpace, { ext = it }) { wishOpen = false }
        return
    }
    openList?.let { name ->
        val source = LiburuClassics.byKey(name)
        BackHandler { openList = null }
        ClassicsList(source, bottomBarSpace) { openList = null }
        return
    }
    // Its own page colour (clear over a wallpaper), or the window shows through dark.
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = bottomBarSpace + 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.liburu_dc_kicker), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.liburu_dc_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
                Avatar(Modifier.size(56.dp))
            }
        }
        if (picks.isNotEmpty()) {
            item { Section(R.string.liburu_dc_next, R.string.liburu_dc_next_sub) }
            item { LibraryRow(picks, onOpenBook) }
        }
        if (series.isNotEmpty()) {
            item { Section(R.string.liburu_dc_series, R.string.liburu_dc_series_sub) }
            item { LibraryRow(series, onOpenBook) }
        }
        netRows?.forEach { row ->
            item(key = "net:" + row.title) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 14.dp)) {
                    Text(row.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(row.sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item(key = "netrow:" + row.title) { ExtRow(row.items) { ext = it } }
        }
        if (netRows == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { BusyIndicator() } }
        if (wish.isNotEmpty()) {
            item(key = "wish") {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 14.dp).clickable { wishOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Me interesa ›", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text("Tu lista de deseos", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "wishrow") { ExtRow(wish.map { w -> wishToExt(w) }) { ext = it } }
        }
        item { Section(R.string.liburu_dc_classics, R.string.liburu_dc_classics_sub) }
        LiburuClassics.SOURCES.forEach { source ->
            item(key = "src:" + source.key) {
                ClassicsRow(source) { openList = source.key }
            }
        }
        item { LiburuNewsRow(Modifier.padding(top = 24.dp)) }
    }
}

private fun suggestions(
    context: android.content.Context,
    books: List<Book>,
    progressions: Map<String, Double>,
    rated: Set<String>,
    strNext: String,
    strAuthor: String,
    strShelf: String,
    strBook: String,
): Pair<List<Pick>, List<Pick>> {
    val live = books.filter { !it.archived && !it.hidden }
    val open = live.filter { !it.finished && it.url !in progressions }
    val liked = live.filter { it.finished || it.url in rated }
    val series = live.filter { !it.seriesName.isNullOrBlank() }.groupBy { it.seriesName!! }
        .mapNotNull { (name, vols) ->
            val done = vols.filter { it.finished || it.url in progressions }
            if (done.isEmpty()) return@mapNotNull null
            val after = done.maxOf { it.seriesIndex ?: 0.0 }
            vols.filter { it in open && (it.seriesIndex ?: 0.0) > after }.minByOrNull { it.seriesIndex ?: Double.MAX_VALUE }
                ?.let { b -> Pick(b, b.seriesIndex?.let { i -> "$name · " + strBook.format(if (i % 1.0 == 0.0) i.toInt().toString() else i.toString()) } ?: name) }
        }
    val picks = LinkedHashMap<String, Pick>()
    series.forEach { picks[it.book.url] = Pick(it.book, strNext.format(it.book.seriesName)) }
    val authors = liked.mapNotNull { it.author?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
    open.filter { it.author in authors }.sortedByDescending { authors[it.author] }.forEach {
        picks.putIfAbsent(it.url, Pick(it, strAuthor.format(it.author)))
    }
    val (shelves, membership) = LiburuShelves.load(context)
    val names = shelves.associate { it.id to it.name }
    open.forEach { b ->
        membership[LiburuShelves.keyOf(b.url)]?.firstNotNullOfOrNull { names[it] }?.let { picks.putIfAbsent(b.url, Pick(b, strShelf.format(it))) }
    }
    return picks.values.take(15) to series
}

@Composable
private fun Section(title: Int, sub: Int?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 14.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        sub?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun LibraryRow(picks: List<Pick>, onOpen: (Book) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(picks, key = { it.book.url }) { p ->
            Column(Modifier.width(132.dp).clickable { onOpen(p.book) }) {
                BookCover(p.book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(p.book.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(p.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ClassicsRow(source: LiburuClassics.Source, onMore: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableStateOf(0) }
    val books by produceState<List<RemoteBook>?>(null, source, attempt) {
        value = null
        value = LiburuClassics.books(context, source, fresh = attempt > 0)
    }
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(
            source.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
        )
        when (val list = books) {
            null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { BusyIndicator() }
            else -> if (list.isEmpty()) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.liburu_dc_unreachable), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.liburu_dc_retry)) }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(list.take(5), key = { it.remoteId }) { b ->
                        Column(Modifier.width(120.dp).clickable(onClick = onMore)) {
                            RemoteCover(source, b, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                            Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            b.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    item(key = "more") {
                        Surface(
                            onClick = onMore,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 40.dp).size(60.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, stringResource(R.string.liburu_dc_more), tint = onInk())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun onInk(): Color = if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFF111111) else Color.White

@Composable
private fun RemoteCover(source: LiburuClassics.Source, book: RemoteBook, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.shadow(6.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(book.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp), maxLines = 4)
        LiburuClassics.coverUrl(source.catalog, book)?.let {
            AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The ~15 books of one source as the web lists them, each with its «Añadir». */
@Composable
private fun ClassicsList(source: LiburuClassics.Source, bottomBarSpace: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by produceState<List<RemoteBook>?>(null, source) { value = LiburuClassics.books(context, source) }
    val serverId by produceState<Long?>(null, source) { value = LiburuClassics.serverId(context, source.catalog) }
    val owned by remember(serverId) {
        serverId?.let { context.container.browseCatalog.observeOwned(it) } ?: flowOf(emptyMap())
    }.collectAsState(emptyMap())
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomBarSpace + 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Text(source.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                stringResource(R.string.liburu_dc_classics_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            )
        }
        when (val list = books) {
            null -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { BusyIndicator() } }
            else -> items(list, key = { it.remoteId }) { b ->
                val state = owned[b.remoteId]?.state
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Row(Modifier.padding(18.dp)) {
                        RemoteCover(source, b, Modifier.width(92.dp).aspectRatio(2f / 3f))
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            val year = b.metadata?.published?.take(4)
                            Text(
                                listOfNotNull(b.author, year).joinToString(" · "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            b.metadata?.description?.let { d ->
                                Text(
                                    d.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { serverId?.let { id -> scope.launch { context.container.browseCatalog.add(id, listOf(b)) } } },
                                enabled = state == null || state == BrowseOwnedState.FAILED,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = onInk()),
                            ) {
                                when (state) {
                                    BrowseOwnedState.IN_LIBRARY -> { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.liburu_dc_in_library)) }
                                    BrowseOwnedState.QUEUED, BrowseOwnedState.DOWNLOADING -> { BusyIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.download_in_progress)) }
                                    else -> { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.liburu_dc_add)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun wishToExt(w: org.json.JSONObject) = LiburuDiscoverNet.Ext(
    w.optString("key"), w.optString("title"), w.optString("author"), w.optString("year"), w.optString("cover"),
    w.optString("link"), w.optString("desc"), w.optString("author"),
)

@Composable
private fun ExtCover(e: LiburuDiscoverNet.Ext, modifier: Modifier) {
    val shape = com.chmouel.liseur.ui.library.LiburuCoverShape
    Box(modifier.shadow(6.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(e.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp), maxLines = 4)
        if (e.cover.isNotEmpty()) AsyncImage(e.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ExtRow(list: List<LiburuDiscoverNet.Ext>, onOpen: (LiburuDiscoverNet.Ext) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(list, key = { it.key }) { e ->
            Column(Modifier.width(132.dp).clickable { onOpen(e) }) {
                ExtCover(e, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(e.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A book from outside the library: what it is, and «Lo quiero leer». */
@Composable
private fun ExtSheet(e: LiburuDiscoverNet.Ext, onDone: () -> Unit) {
    val context = LocalContext.current
    val v by com.chmouel.liseur.data.gdrive.LiburuShared.version.collectAsState()
    val wished = remember(v, e.key) { com.chmouel.liseur.data.gdrive.LiburuShared.isWished(context, e.key) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    ExtCover(e, Modifier.width(96.dp).aspectRatio(2f / 3f))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(listOf(e.author, e.year).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (e.desc.isNotBlank()) Text(e.desc, style = MaterialTheme.typography.bodyMedium, maxLines = 10, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    com.chmouel.liseur.data.gdrive.LiburuShared.toggleWish(context, e.json(), !wished)
                    context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = onInk()),
            ) { Text(if (wished) "✓ En tu lista" else "Lo quiero leer") }
        },
        dismissButton = {
            if (e.link.isNotEmpty()) androidx.compose.material3.TextButton(onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.link))) }
            }) { Text("Ver en Google Books") }
        },
    )
}

/** «Lista de deseos», as the web's page. */
@Composable
private fun WishlistScreen(list: List<org.json.JSONObject>, bottomBarSpace: Dp, onOpen: (LiburuDiscoverNet.Ext) -> Unit, onBack: () -> Unit) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomBarSpace + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Text("Lista de deseos", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("${list.size} ${if (list.size == 1) "libro" else "libros"} que quieres leer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list.size) { i ->
            val e = wishToExt(list[i])
            Column(Modifier.clickable { onOpen(e) }) {
                ExtCover(e, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(e.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(e.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

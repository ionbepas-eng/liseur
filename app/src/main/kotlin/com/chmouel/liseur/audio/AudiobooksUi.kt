package com.chmouel.liseur.audio

import android.content.ComponentName
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.chmouel.liseur.data.gdrive.DriveAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** One controller for the app's audiobook player, kept while the app lives. */
object AudioController {
    private var controller: MediaController? = null
    var sleepJob: kotlinx.coroutines.Job? = null
    suspend fun get(context: Context): MediaController = controller?.takeIf { it.isConnected } ?: suspendCancellableCoroutine { cont ->
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, AudiobookService::class.java))).buildAsync()
        future.addListener({ runCatching { future.get() }.onSuccess { controller = it; cont.resumeWith(Result.success(it)) }.onFailure { cont.resumeWith(Result.failure(it)) } },
            androidx.core.content.ContextCompat.getMainExecutor(app))
    }
    fun current() = controller

    suspend fun open(context: Context, book: LiburuAudiobooks.Audiobook) {
        val c = get(context)
        val playingThis = c.mediaItemCount == book.tracks.size && c.mediaItemCount > 0 && c.getMediaItemAt(0).mediaId == book.tracks[0].id
        if (!playingThis) {
            val items = book.tracks.mapIndexed { i, t ->
                MediaItem.Builder().setMediaId(t.id).setUri("${DriveAuth.BASE_URL}/files/${t.id}?alt=media")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(book.title).setArtist(book.author).setAlbumTitle(t.name.substringAfterLast('/').substringBeforeLast('.'))
                        .setTrackNumber(i + 1).setTotalTrackCount(book.tracks.size).apply { book.cover?.let { setArtworkUri(android.net.Uri.parse(it)) } }.build())
                    .build()
            }
            val (track, ms) = LiburuAudiobooks.position(context, book.id)
            c.setMediaItems(items, track.coerceIn(0, items.size - 1), ms)
            c.prepare()
        }
        c.setPlaybackSpeed(LiburuAudiobooks.rate(context, book.id))
        c.play()
    }
}

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = s % 3600 / 60; val x = s % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x) }

@Composable
private fun Cover(book: LiburuAudiobooks.Audiobook, modifier: Modifier) {
    Box(modifier.shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3530)), contentAlignment = Alignment.Center) {
        Text(book.title, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp), maxLines = 4)
        book.cover?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
            Text("🎧", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Biblioteca › Audiolibros: square covers, as the web shows them. */
@Composable
fun AudiobooksOverview(bottomInset: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LiburuAudiobooks.init(context)
    val books by LiburuAudiobooks.list.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<LiburuAudiobooks.Audiobook?>(null) }
    val scope = rememberCoroutineScope()
    val error by LiburuAudiobooks.lastError.collectAsState()
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        scanning = true
        runCatching { LiburuAudiobooks.scan(context) }.onFailure { LiburuAudiobooks.lastError.value = it.message ?: it.javaClass.simpleName }
        scanning = false
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp), modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomInset + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column {
            Text(
                if (books.isEmpty()) (if (scanning) "Buscando la carpeta «Audiolibros» en tu Drive…" else "No hay audiolibros. Crea en Drive una carpeta «Audiolibros» con una subcarpeta por libro (sus pistas en orden y, si quieres, la portada).")
                else "${books.size} ${if (books.size == 1) "audiolibro" else "audiolibros"} · se escuchan desde Drive" + if (scanning) " · actualizando…" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.takeIf { !scanning }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("No he podido leer tu Drive: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { attempt++ }) { Text("Reintentar") }
                }
            }
        } }
        items(books, key = { it.id }) { b ->
            val f = LiburuAudiobooks.fraction(context, b.id)
            Column(Modifier.clickable { open = b; scope.launch { runCatching { AudioController.open(context, b) } } }) {
                Cover(b, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(if (f > 0.005f) "${(f * 100).toInt()} %" else "${b.tracks.size} ${if (b.tracks.size == 1) "pista" else "pistas"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    open?.let { b -> AudiobookPlayer(b) { open = null } }
}

/** The web's full-screen player: cover, chapter, the bar, ±15/30 s, speed, sleep timer and the track list. */
@Composable
fun AudiobookPlayer(book: LiburuAudiobooks.Audiobook, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var rate by remember { mutableFloatStateOf(LiburuAudiobooks.rate(context, book.id)) }
    var showTracks by remember { mutableStateOf(false) }
    var sleepLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(book.id) { c = runCatching { AudioController.get(context) }.getOrNull() }
    fun save() {
        val ctl = c ?: return
        if (ctl.mediaItemCount == 0 || ctl.getMediaItemAt(0).mediaId != book.tracks[0].id) return
        val sizes = book.tracks.map { it.size.coerceAtLeast(1) }
        val done = sizes.take(ctl.currentMediaItemIndex).sum() + (if (ctl.duration > 0) sizes[ctl.currentMediaItemIndex] * ctl.currentPosition / ctl.duration else 0)
        LiburuAudiobooks.savePosition(context, book.id, ctl.currentMediaItemIndex, ctl.currentPosition, (done.toFloat() / sizes.sum()).coerceIn(0f, 1f))
    }
    LaunchedEffect(c) {
        val ctl = c ?: return@LaunchedEffect
        var n = 0
        while (true) {
            playing = ctl.isPlaying; index = ctl.currentMediaItemIndex; pos = ctl.currentPosition; dur = ctl.duration.coerceAtLeast(0)
            if (++n % 10 == 0) save()
            delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { save() } }
    BackHandler(onBack = onClose)
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1E1B18)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("‹ Biblioteca", color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    Text("AUDIOLIBRO", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showTracks = !showTracks }) { Text(if (showTracks) "Portada" else "Pistas", color = Color.White) }
                }
                if (showTracks) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(book.tracks) { i, t ->
                            Text(t.name.substringBeforeLast('.'), color = if (i == index) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth().clickable { c?.seekTo(i, 0); c?.play(); showTracks = false }.padding(vertical = 12.dp))
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Cover(book, Modifier.widthIn(max = 420.dp).fillMaxWidth(0.82f).aspectRatio(1f))
                    }
                }
                Text(book.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2)
                if (book.author.isNotEmpty()) Text(book.author, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
                Text(book.tracks.getOrNull(index)?.name?.substringAfterLast('/')?.substringBeforeLast('.') ?: "", color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
                Slider(value = if (dur > 0) pos.toFloat() / dur else 0f, onValueChange = { v -> c?.seekTo((v * dur).toLong()) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(pos), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("Pista ${index + 1} de ${book.tracks.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("-" + fmt(dur - pos), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundBtn("⏮", 52.dp) { c?.seekToPreviousMediaItem() }
                    RoundBtn("−15", 60.dp) { c?.seekBack() }
                    RoundBtn(if (playing) "❚❚" else "▶", 80.dp, filled = true) { c?.let { if (it.isPlaying) { it.pause(); save() } else it.play() } }
                    RoundBtn("+30", 60.dp) { c?.seekForward() }
                    RoundBtn("⏭", 52.dp) { c?.seekToNextMediaItem() }
                }
                Row(Modifier.padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("${"%.2f".format(rate).trimEnd('0').trimEnd(',', '.')}×") {
                        val steps = listOf(0.8f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
                        rate = steps[(steps.indexOfFirst { it >= rate - 0.001f } + 1) % steps.size]
                        c?.setPlaybackSpeed(rate); LiburuAudiobooks.setRate(context, book.id, rate)
                    }
                    Pill(sleepLabel ?: "Temporizador") {
                        val next = when (sleepLabel) { null -> 15; "15 min" -> 30; "30 min" -> 60; else -> 0 }
                        AudioController.sleepJob?.cancel()
                        if (next == 0) sleepLabel = null else {
                            sleepLabel = "$next min"
                            AudioController.sleepJob = context.applicationContext.let { app ->
                                kotlinx.coroutines.MainScope().launch { delay(next * 60_000L); AudioController.current()?.pause(); sleepLabel = null }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundBtn(label: String, size: Dp, filled: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (filled) Color.White else Color.White.copy(alpha = 0.12f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = if (filled) Color(0xFF1E1B18) else Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

package com.chmouel.liseur.comic

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.chmouel.liseur.LiseurApplication
import com.chmouel.liseur.data.db.ReadingSession
import com.chmouel.liseur.data.library.ReadingPositionBackupRepository
import com.chmouel.liseur.domain.BackedUpReadingPosition
import com.chmouel.liseur.ui.theme.LiseurTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liburuville's comic reader for CBZ: whole pages with smooth zoom, two-page
 * spreads on a wide screen, left-to-right or manga order, and the place
 * saved like a book's so Home and the other devices know it.
 */
class ComicReaderActivity : ComponentActivity() {
    private var archive: ComicArchive? = null
    private lateinit var bookId: String
    private var title: String = ""
    private var page by mutableStateOf(0)
    private var total = 0
    private var startedAt = 0L
    private var startPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.getStringExtra(EXTRA_FILE)?.toUri() ?: return finish()
        bookId = intent.getStringExtra(EXTRA_ID) ?: uri.toString()
        com.chmouel.liseur.ui.library.LiburuComics.mark(bookId)
        val prefs = getSharedPreferences("comics", Context.MODE_PRIVATE)
        page = prefs.getInt("page:$bookId", 0)
        var loaded by mutableStateOf(false)
        lifecycleScope.launch {
            val container = (application as LiseurApplication).container
            title = withContext(Dispatchers.IO) { container.database.bookDao().getByUrl(bookId)?.title }.orEmpty()
            archive = withContext(Dispatchers.IO) { ComicArchive.localFile(this@ComicReaderActivity, uri)?.let(::ComicArchive) }
            total = archive?.pages?.size ?: 0
            if (total == 0) { finish(); return@launch }
            // The place another device left, when the book's progression says so.
            withContext(Dispatchers.IO) { container.database.readingProgressDao().get(bookId)?.totalProgression }
                ?.let { p -> page = (kotlin.math.round(p * total).toInt() - 1) }
            page = page.coerceIn(0, total - 1)
            loaded = true
        }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            LiseurTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!loaded) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        ComicPager(
                            archive = archive!!,
                            title = title,
                            start = page,
                            onPage = { page = it; prefs.edit().putInt("page:$bookId", it).apply() },
                            onBack = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startedAt = System.currentTimeMillis()
        startPage = page
    }

    override fun onPause() {
        super.onPause()
        if (total == 0) return
        val now = System.currentTimeMillis()
        val from = (startPage + 1).toDouble() / total
        val to = (page + 1).toDouble() / total
        val container = (application as LiseurApplication).container
        val begun = startedAt
        container.applicationScopeLaunch {
            // The place, as a book's progression, so Home and Drive sync see it.
            runCatching {
                ReadingPositionBackupRepository(
                    container.database.readingProgressDao(),
                    container.database.bookDao(),
                    container::requestBookSync,
                    finishedState = container.finishedState,
                ).restore(listOf(BackedUpReadingPosition(bookId, title, null, "{}", to.coerceIn(0.0, 1.0), now)))
            }
            if (now - begun > 15_000) {
                container.database.readingSessionDao().insert(
                    ReadingSession(
                        bookUrl = bookId, startedAt = begun, endedAt = now, lastCheckpointAt = now,
                        durationMs = now - begun, startProgression = from, endProgression = to,
                    ),
                )
            }
        }
    }

    override fun onDestroy() {
        archive?.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_ID = "id"
        fun intent(context: Context, file: String, id: String) =
            Intent(context, ComicReaderActivity::class.java).putExtra(EXTRA_FILE, file).putExtra(EXTRA_ID, id)
    }
}

@Composable
private fun ComicPager(
    archive: ComicArchive,
    title: String,
    start: Int,
    onPage: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("comics", Context.MODE_PRIVATE) }
    var manga by remember { mutableStateOf(prefs.getBoolean("manga", false)) }
    var spreadsOn by remember { mutableStateOf(prefs.getBoolean("spreads", true)) }
    var chrome by remember { mutableStateOf(false) }
    val config = LocalConfiguration.current
    val wide = config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 600
    val spreads = wide && spreadsOn
    val total = archive.pages.size
    // Spreads: the cover alone, then pairs.
    val groups = remember(spreads, total) {
        if (!spreads) (0 until total).map { listOf(it) }
        else listOf(listOf(0)) + (1 until total step 2).map { i -> listOfNotNull(i, (i + 1).takeIf { it < total }) }
    }
    val startGroup = groups.indexOfFirst { start in it }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startGroup) { groups.size }
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, groups) {
        snapshotFlow { pager.currentPage }.collect { onPage(groups[it].first()) }
    }
    val maxSide = (maxOf(config.screenWidthDp, config.screenHeightDp) * context.resources.displayMetrics.density * 1.5f).toInt()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            reverseLayout = manga,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            ZoomableSpread(
                archive = archive,
                pages = groups[index].let { if (manga) it.reversed() else it },
                maxSide = maxSide,
                onZoomChange = { zoomed = it },
                onTap = { x, width ->
                    when {
                        x < width * 0.3f -> scope.launch { pager.animateScrollToPage((pager.currentPage + if (manga) 1 else -1).coerceIn(0, groups.size - 1)) }
                        x > width * 0.7f -> scope.launch { pager.animateScrollToPage((pager.currentPage + if (manga) -1 else 1).coerceIn(0, groups.size - 1)) }
                        else -> chrome = !chrome
                    }
                },
            )
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                FilterChip(selected = manga, onClick = { manga = !manga; prefs.edit().putBoolean("manga", manga).apply() }, label = { Text("Manga") })
                if (wide) {
                    FilterChip(
                        selected = spreadsOn,
                        onClick = { spreadsOn = !spreadsOn; prefs.edit().putBoolean("spreads", spreadsOn).apply() },
                        label = { Text("Doble página") },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                    )
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val current = groups.getOrNull(pager.currentPage)?.joinToString("–") { (it + 1).toString() } ?: ""
                Text("Página $current de $total", color = Color.White, style = MaterialTheme.typography.labelLarge)
                var slider by remember(pager.currentPage) { mutableFloatStateOf(pager.currentPage.toFloat()) }
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { scope.launch { pager.scrollToPage(slider.toInt()) } },
                    valueRange = 0f..(groups.size - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.graphicsLayer { scaleX = if (manga) -1f else 1f },
                )
            }
        }
    }
}

@Composable
private fun ZoomableSpread(
    archive: ComicArchive,
    pages: List<Int>,
    maxSide: Int,
    onZoomChange: (Boolean) -> Unit,
    onTap: (Float, Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero; onZoomChange(false) }
                        else {
                            scale = 2.5f
                            offset = Offset((size.width / 2f - at.x) * 1.5f, (size.height / 2f - at.y) * 1.5f)
                            onZoomChange(true)
                        }
                    },
                    onTap = { at -> if (scale <= 1.01f) onTap(at.x, size.width.toFloat()) },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    val maxX = (size.width * (next - 1)) / 2f
                    val maxY = (size.height * (next - 1)) / 2f
                    offset = if (next <= 1.01f) Offset.Zero else Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                    onZoomChange(next > 1.01f)
                }
            },
    ) {
        Row(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { index ->
                val bitmap by produceState<android.graphics.Bitmap?>(null, index) {
                    value = withContext(Dispatchers.IO) { runCatching { archive.page(index, maxSide) }.getOrNull() }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = if (pages.size == 2) (if (index == pages.first()) Alignment.CenterEnd else Alignment.CenterStart) else Alignment.Center) {
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alignment = if (pages.size == 2) (if (index == pages.first()) Alignment.CenterEnd else Alignment.CenterStart) else Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

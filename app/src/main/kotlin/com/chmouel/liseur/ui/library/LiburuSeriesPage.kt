package com.chmouel.liseur.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.domain.SeriesShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The web's series page: a coloured hero with the fanned covers and what to read next, then the books in order. */
@Composable
fun LiburuSeriesPage(
    shelf: SeriesShelf,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
    onDetails: (Book) -> Unit,
    onMore: (Book) -> Unit,
    onSetSeries: (Book, String?, Double?) -> Unit,
    onResetSeries: (Book) -> Unit,
) {
    BackHandler(onBack = onBack)
    val vols = shelf.volumes
    val read = vols.count { it.finished }
    val next = vols.firstOrNull { !it.finished && (it.progression ?: 0.0) > 0.004 } ?: vols.firstOrNull { !it.finished }
    val lead = (next ?: vols.first()).book
    val authors = vols.mapNotNull { it.book.author?.takeIf(String::isNotBlank) }.distinct()
    val col by produceState<Color?>(null, lead.coverPath) { value = withContext(Dispatchers.IO) { coverColor(lead.coverPath) } }
    val base = col ?: Color(0xFF5B4A3A)
    val nf = next?.progression ?: 0.0
    val cta = if (next == null) "Leer de nuevo" else if (nf > 0.004) "Continuar leyendo" else "Empezar a leer"
    var editing by remember { mutableStateOf(false) }
    val fan = (listOf(lead) + vols.map { it.book }.filter { it.url != lead.url }).take(5)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(base, androidx.compose.ui.graphics.lerp(base, Color.Black, 0.35f))))) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
                        }
                        Box(Modifier.weight(1f))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), onClick = { editing = true }) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.width(16.dp))
                                Text("  Editar", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                        val order = listOf(3, 1, 0, 2, 4).filter { it < fan.size }
                        order.forEach { i ->
                            val side = when (i) { 0 -> 0; 1 -> -1; 2 -> 1; 3 -> -2; else -> 2 }
                            BookCover(fan[i], Modifier.width(if (side == 0) 112.dp else 96.dp).aspectRatio(2f / 3f).graphicsLayer {
                                translationX = side * 46.dp.toPx(); rotationZ = side * 5f; translationY = kotlin.math.abs(side) * 8.dp.toPx()
                            })
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(shelf.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(if (authors.size == 1) authors[0] else if (authors.isNotEmpty()) "Varios autores" else "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
                        Text("Serie · ${vols.size} ${if (vols.size == 1) "libro" else "libros"}" + if (read > 0) " · $read leído${if (read == 1) "" else "s"}" else "",
                            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        if (read > 0) LinearProgressIndicator(progress = { read.toFloat() / vols.size }, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {})
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, onClick = { onRead(lead) }, modifier = Modifier.padding(top = 12.dp)) {
                            Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cta, color = Color(0xFF1E1A16), fontWeight = FontWeight.SemiBold)
                                Text(lead.title, color = Color(0xFF1E1A16).copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item(key = "h") {
            Text("Libros", style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp))
        }
        items(vols, key = { it.book.url }) { v ->
            val b = v.book
            val f = v.progression ?: 0.0
            val st = when {
                v.finished -> "Terminado"
                f > 0.004 -> "${Math.round(f * 100)} %"
                System.currentTimeMillis() - b.addedAt < 14 * 86_400_000L -> "Nuevo"
                else -> ""
            }
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().combinedClickable(onClick = { onDetails(b) }, onLongClick = { LiburuQuickOpen.open?.invoke(b) ?: onRead(b) }).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(b, Modifier.width(58.dp).aspectRatio(2f / 3f))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    v.index?.let { Text("Libro " + (if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                    Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    b.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    if (st.isNotEmpty()) Text(st, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMore(b) }) { Icon(Icons.Outlined.MoreVert, "Opciones de ${b.title}") }
            }
        }
    }
    if (editing) SeriesEditDialog(shelf, emptyList(), onSetSeries, onResetSeries) { editing = false }
}

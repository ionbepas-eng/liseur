package com.chmouel.liseur.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow

/** «Seleccionar», as on the web: several books at once, and what to do with them. */
object LiburuSelection {
    val active = MutableStateFlow(false)
    val urls = MutableStateFlow<Set<String>>(emptySet())
    fun start() { urls.value = emptySet(); active.value = true }
    fun stop() { active.value = false; urls.value = emptySet() }
    fun toggle(url: String) { urls.value = if (url in urls.value) urls.value - url else urls.value + url }
}

/** The tick in the corner of a cover while choosing. */
@Composable
internal fun LiburuSelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f), CircleShape)
            .then(if (selected) Modifier else Modifier.background(Color.Transparent)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        else Box(Modifier.size(22.dp).background(Color.Transparent).then(Modifier.padding(1.dp)))
    }
}

/** The bar over the tab bar: how many are chosen and the actions for them. */
@Composable
internal fun SelectionBar(
    books: List<Book>,
    onSetFinished: (Book, Boolean) -> Unit,
    onRemoveDownload: (Book) -> Unit,
    onHide: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chosen by LiburuSelection.urls.collectAsState()
    val picked = books.filter { it.url in chosen }
    var shelfPicker by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (picked.isEmpty()) "Toca los libros para elegirlos" else "${picked.size} ${if (picked.size == 1) "libro elegido" else "libros elegidos"}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { LiburuSelection.urls.value = books.map { it.url }.toSet() }) { Text("Todos") }
                TextButton(onClick = { LiburuSelection.stop() }) { Text("Hecho") }
            }
            if (picked.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("✓ Leídos") { picked.forEach { onSetFinished(it, true) }; LiburuSelection.stop() }
                Chip("Sin leer") { picked.forEach { onSetFinished(it, false) }; LiburuSelection.stop() }
                Chip("Estantería…") { shelfPicker = true }
                val local = picked.filter { it.downloadState == DownloadState.DOWNLOADED }
                if (local.isNotEmpty()) Chip("Quitar descarga (${local.size})") {
                    local.forEach(onRemoveDownload)
                    android.widget.Toast.makeText(context, "Descarga quitada de ${local.size} ${if (local.size == 1) "libro" else "libros"}; siguen en tu biblioteca", android.widget.Toast.LENGTH_SHORT).show()
                    LiburuSelection.stop()
                }
                Chip("Ocultar") { confirmHide = true }
            }
        }
    }
    if (shelfPicker) {
        val (shelves, membership) = remember { LiburuShelves.load(context) }
        AlertDialog(
            onDismissRequest = { shelfPicker = false },
            title = { Text("Añadir a una estantería") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shelves.sortedBy { it.name.lowercase() }, key = { it.id }) { s ->
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable {
                            picked.forEach { b ->
                                val cur = membership[LiburuShelves.keyOf(b.url)] ?: emptySet()
                                LiburuShelves.setBookShelves(context, b.url, cur + s.id)
                            }
                            android.widget.Toast.makeText(context, "${picked.size} en «${s.name}»", android.widget.Toast.LENGTH_SHORT).show()
                            shelfPicker = false; LiburuSelection.stop()
                        }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { shelfPicker = false }) { Text("Cancelar") } },
        )
    }
    if (confirmHide) AlertDialog(
        onDismissRequest = { confirmHide = false },
        title = { Text("¿Ocultar ${picked.size} ${if (picked.size == 1) "libro" else "libros"}?") },
        text = { Text("No se borran de Drive: dejan de verse en la biblioteca.") },
        confirmButton = { TextButton(onClick = { picked.forEach(onHide); confirmHide = false; LiburuSelection.stop() }) { Text("Ocultar") } },
        dismissButton = { TextButton(onClick = { confirmHide = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}


/** Liburuville: a tap on a book shows its card; a long press opens the book itself. Set by the Library screen. */
internal object LiburuQuickOpen {
    @Volatile var open: ((com.chmouel.liseur.data.db.Book) -> Unit)? = null
}

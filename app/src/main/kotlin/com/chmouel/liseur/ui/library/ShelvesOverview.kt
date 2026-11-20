package com.chmouel.liseur.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.data.db.Book

/** Liburuville's shelves: the list, one shelf's books, and adding books to it. */
@Composable
internal fun ShelvesOverview(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version by LiburuShelves.version.collectAsState()
    val (shelves, membership) = remember(version) { LiburuShelves.load(context) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var picking by remember { mutableStateOf(false) }
    val booksOn = { id: String -> books.filter { id in (membership[LiburuShelves.keyOf(it.url)] ?: emptySet()) } }

    val open = shelves.firstOrNull { it.id == openId }
    if (open == null) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FilledTonalButton(onClick = { naming = null to "" }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva estantería")
                }
            }
            if (shelves.isEmpty()) {
                item {
                    Text(
                        "Crea estanterías para agrupar tus libros (por ejemplo «Clásicos» o «Cine»). Son las mismas que en Liburuville web.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(shelves, key = { it.id }) { shelf ->
                val on = booksOn(shelf.id)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().clickable { openId = shelf.id },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shelf.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            Text("${on.size} ${if (on.size == 1) "libro" else "libros"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                on.take(5).forEach { BookCover(it, Modifier.width(52.dp).aspectRatio(2f / 3f), RoundedCornerShape(6.dp)) }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val on = booksOn(open.id)
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                Text(open.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { naming = open.id to open.name }) { Icon(Icons.Outlined.Edit, "Renombrar") }
                IconButton(onClick = {
                    LiburuShelves.saveShelf(context, open.id, open.name, deleted = true)
                    openId = null
                }) { Icon(Icons.Outlined.Delete, "Borrar estantería") }
            }
            FilledTonalButton(onClick = { picking = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Añadir o quitar libros")
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(on, key = { it.url }) { book ->
                    Column(Modifier.clickable { onOpenBook(book) }) {
                        BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        Text(book.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (picking) {
            var query by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { picking = false },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("Listo") } },
                title = { Text(open.name) },
                text = {
                    Column {
                        OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val list = books.filter { query.isBlank() || it.title.contains(query, true) || (it.author?.contains(query, true) == true) }
                            .sortedBy { it.title.lowercase() }
                        LazyColumn(Modifier.height(380.dp)) {
                            items(list, key = { it.url }) { book ->
                                val key = LiburuShelves.keyOf(book.url)
                                val current = membership[key] ?: emptySet()
                                val checked = open.id in current
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        LiburuShelves.setBookShelves(context, book.url, if (checked) current - open.id else current + open.id)
                                    },
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
    naming?.let { (id, initial) ->
        var name by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (id == null) "Nueva estantería" else "Renombrar estantería") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val saved = LiburuShelves.saveShelf(context, id, name.trim())
                    naming = null
                    openId = saved
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancelar") } },
        )
    }
}

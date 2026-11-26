package com.chmouel.liseur.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.container
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer

private const val NONE = "none"

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
    val valid = shelves.map { it.id }.toSet()
    val booksOn = { id: String ->
        if (id == NONE) books.filter { (membership[LiburuShelves.keyOf(it.url)] ?: emptySet()).none { s -> s in valid } }
        else books.filter { id in (membership[LiburuShelves.keyOf(it.url)] ?: emptySet()) }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val open = shelves.firstOrNull { it.id == openId } ?: if (openId == NONE) LiburuShelves.Shelf(NONE, "Sin estantería asignada") else null
    if (open == null) {
        var organizing by remember { mutableStateOf(false) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = modifier,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomInset + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = { organizing = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text("✨ " + androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_organize), fontWeight = FontWeight.SemiBold) }
                    androidx.compose.material3.OutlinedButton(onClick = { naming = null to "" }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_new), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (shelves.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(shelves.sortedBy { it.name.lowercase() }, key = { it.id }) { shelf ->
                val on = booksOn(shelf.id)
                FanCard(
                    books = on,
                    title = shelf.name,
                    subtitle = androidx.compose.ui.res.pluralStringResource(com.chmouel.liseur.R.plurals.liburu_n_books, on.size, on.size),
                    onClick = { openId = shelf.id },
                )
            }
            if (shelves.isNotEmpty()) item(key = NONE) {
                val on = booksOn(NONE)
                FanCard(on, "Sin estantería", androidx.compose.ui.res.pluralStringResource(com.chmouel.liseur.R.plurals.liburu_n_books, on.size, on.size), onClick = { openId = NONE })
            }
        }
        if (organizing) OrganizeDialog(books) { organizing = false }
    } else {
        val on = booksOn(open.id).sortedBy { it.title.lowercase() }
        val real = open.id != NONE
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                Text("Estantería", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (real) {
                    IconButton(onClick = { naming = open.id to open.name }) { Icon(Icons.Outlined.Edit, "Cambiar nombre") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Borrar estantería") }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(open.name, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(androidx.compose.ui.res.pluralStringResource(com.chmouel.liseur.R.plurals.liburu_n_books, on.size, on.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (real) androidx.compose.material3.OutlinedButton(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir libros")
                }
            }
            if (on.isEmpty()) Text("No hay libros aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(on, key = { it.url }) { book ->
                    Column(Modifier.combinedClickable(onClick = { onOpenBook(book) }, onLongClick = { LiburuQuickOpen.open?.invoke(book) })) {
                        BookCover(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
    if (confirmDelete && open != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Borrar «${open.name}»?") },
        text = { Text("Borrar la estantería no borra los libros.") },
        confirmButton = { TextButton(onClick = { LiburuShelves.saveShelf(context, open.id, open.name, deleted = true); confirmDelete = false; openId = null }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
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

/** A card as on the web: up to three covers fanned out on a grey band, then the name. */
@Composable
internal fun FanCard(books: List<Book>, title: String, subtitle: String, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        // As on the web: a translucent card on the page, the covers on near-white.
        color = com.chmouel.liseur.ui.theme.liburuGlass(0.42f),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.chmouel.liseur.ui.theme.liburuGlass(0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxWidth().background(com.chmouel.liseur.ui.theme.liburuGlass(0.72f)).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                val w = maxWidth
                Box(Modifier.fillMaxWidth().height(w * 0.82f), contentAlignment = Alignment.Center) {
                    val shown = books.take(3)
                    val order = when (shown.size) { 3 -> listOf(0, 2, 1); else -> shown.indices.toList() }
                    order.forEach { i ->
                        val side = when {
                            shown.size == 1 -> 0
                            shown.size == 2 -> if (i == 0) -1 else 1
                            else -> i - 1
                        }
                        BookCover(
                            shown[i],
                            Modifier
                                .width(if (side == 0) w * 0.44f else w * 0.40f)
                                .aspectRatio(2f / 3f)
                                .graphicsLayer {
                                    translationX = side * (w * 0.30f).toPx()
                                    rotationZ = side * 5f
                                    translationY = if (side == 0) 0f else (w * 0.03f).toPx()
                                },
                        )
                    }
                }
            }
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                onEdit?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Edit, androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_wf_edit)) } }
            }
        }
    }
}

/** «Organizar automáticamente»: the web's proposal, shelf by shelf, then applied. */
@Composable
private fun OrganizeDialog(books: List<Book>, onDone: () -> Unit) {
    val context = LocalContext.current
    val proposal by androidx.compose.runtime.produceState<Map<String, List<String>>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LiburuAutoShelves.propose(context, books) }
    }
    var chosen by remember { mutableStateOf(LiburuAutoShelves.autoNames(context)) }
    var result by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_organize)) },
        text = {
            val p = proposal
            when {
                result != null -> Text(androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_done, result!!))
                p == null -> com.chmouel.liseur.ui.BusyIndicator()
                else -> Column {
                    Text(
                        androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.height(360.dp).padding(top = 8.dp)) {
                        items(p.entries.filter { it.value.isNotEmpty() }.toList(), key = { it.key }) { (name, urls) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { chosen = if (name in chosen) chosen - name else chosen + name },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(name in chosen, null)
                                Text(name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                                Text("${urls.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                TextButton(enabled = proposal != null && chosen.isNotEmpty(), onClick = {
                    val p = proposal ?: return@TextButton
                    result = LiburuAutoShelves.apply(context, p, chosen)
                    context.container.applicationScopeLaunch { context.container.driveStateSync.syncNow(force = true) }
                }) { Text(androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_sh_apply)) }
            } else {
                TextButton(onClick = onDone) { Text(androidx.compose.ui.res.stringResource(com.chmouel.liseur.R.string.liburu_bd_close)) }
            }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDone) { Text(androidx.compose.ui.res.stringResource(android.R.string.cancel)) } },
    )
}

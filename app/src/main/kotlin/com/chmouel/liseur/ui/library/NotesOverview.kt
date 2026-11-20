package com.chmouel.liseur.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import com.chmouel.liseur.LiseurApplication
import com.chmouel.liseur.R
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.BookAnnotation
import kotlinx.coroutines.flow.combine

/** Liburuville: the sections across the top of the library, a pill as on the web. */
@Composable
internal fun LibrarySections(
    selected: Int,
    seriesAvailable: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    comicsAvailable: Boolean = false,
) {
    val labels = buildList {
        add(0 to R.string.library_section_books)
        if (comicsAvailable) add(4 to R.string.liburu_lib_comics)
        if (seriesAvailable) add(1 to R.string.library_section_series)
        add(3 to R.string.library_section_shelves)
        add(2 to R.string.library_section_notes)
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = if (ink.luminance() > 0.5f) Color(0xFF111111) else Color.White
    // Centred on the screen whatever its width, as Ion asked.
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
        ) {
            labels.forEach { (id, label) ->
                val on = id == selected
                androidx.compose.material3.Surface(
                    onClick = { onSelect(id) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                    color = if (on) ink else Color.Transparent,
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (on) paper else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
}

private enum class NotesFilter { ALL, HIGHLIGHTS, NOTES }

/** Every highlight and note in the library, grouped by book. */
@Composable
internal fun NotesOverview(
    onOpenBook: (Book) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LiseurApplication).container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val flow = remember {
        combine(
            container.database.annotationDao().observeAllMarks(),
            container.database.bookDao().observeAll(),
        ) { marks, books -> marks to books.associateBy { it.url } }
    }
    val data by flow.collectAsState(initial = null)
    var section by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var tint by rememberSaveable { mutableStateOf<String?>(null) }
    var bookFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<BookAnnotation?>(null) }
    val (marks, books) = data ?: (emptyList<BookAnnotation>() to emptyMap())
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = if (ink.luminance() > 0.5f) Color(0xFF111111) else Color.White

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomInset + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Subrayados y notas · Vocabulario, as on the web.
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(5.dp)) {
                    listOf("Subrayados y notas", "Vocabulario").forEachIndexed { i, label ->
                        androidx.compose.material3.Surface(
                            onClick = { section = i },
                            shape = RoundedCornerShape(24.dp),
                            color = if (section == i) ink else Color.Transparent,
                        ) {
                            Text(
                                label,
                                color = if (section == i) paper else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
        if (section == 1) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vocabulario", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        "Aquí aparecerán las palabras que guardes mientras lees, con su definición. Llega con la próxima versión del lector.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }
        val withNote = marks.count { !it.note.isNullOrBlank() }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Subrayados y notas", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        "${marks.size} ${if (marks.size == 1) "subrayado" else "subrayados"} · $withNote con nota · ${marks.map { it.bookId }.distinct().size} ${if (marks.map { it.bookId }.distinct().size == 1) "libro" else "libros"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.OutlinedButton(onClick = { exportAll(context, marks, books) }, enabled = marks.isNotEmpty()) {
                    Text("Exportar todo", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Buscar en tus subrayados y notas") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val tints = marks.mapNotNull { it.tint?.lowercase() }.distinct()
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotePill("Todos", tint == null, ink, paper, null) { tint = null }
                tints.forEach { t -> NotePill(tintName(t), tint == t, ink, paper, tintColor(t)) { tint = if (tint == t) null else t } }
                var open by remember { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.Surface(
                        onClick = { open = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(bookFilter?.let { books[it]?.title } ?: "Todos los libros", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                            Text("  ⌄")
                        }
                    }
                    androidx.compose.material3.DropdownMenu(open, { open = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Todos los libros") }, onClick = { bookFilter = null; open = false })
                        marks.map { it.bookId }.distinct().forEach { id ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(books[id]?.title ?: id) }, onClick = { bookFilter = id; open = false })
                        }
                    }
                }
            }
        }
        val shown = marks.filter { m ->
            (tint == null || m.tint?.lowercase() == tint) &&
                (bookFilter == null || m.bookId == bookFilter) &&
                (query.isBlank() || (m.text.orEmpty() + " " + m.note.orEmpty()).contains(query, true))
        }
        if (shown.isEmpty()) {
            item {
                Text(stringResource(R.string.notes_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            }
        }
        shown.groupBy { it.bookId }.forEach { (bookId, bookMarks) ->
            val book = books[bookId]
            item(key = "h:$bookId") {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = book != null) { book?.let(onOpenBook) }.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    book?.let { BookCover(it, Modifier.width(44.dp).aspectRatio(2f / 3f)) }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(book?.title ?: bookId, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(book?.author, "${bookMarks.size}").joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(bookMarks, key = { it.id }) { mark ->
                MarkCard(
                    mark,
                    onClick = { book?.let(onOpenBook) },
                    onShare = {
                        book?.let { b ->
                            com.chmouel.liseur.ui.library.styles.QuoteShare.share(
                                context,
                                com.chmouel.liseur.data.quotes.HomeQuote(b, mark.text.orEmpty().trim(), mark.chapter, mark.locatorJson, true),
                            )
                        }
                    },
                    onEdit = { editing = mark },
                    onCopy = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("cita", "«${mark.text.orEmpty().trim()}» — ${book?.title.orEmpty()}"))
                    },
                )
            }
        }
    }
    editing?.let { mark ->
        var note by remember { mutableStateOf(mark.note.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Tu nota") },
            text = { androidx.compose.material3.OutlinedTextField(note, { note = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    scope.launch {
                        container.database.annotationDao().update(mark.id) {
                            it.copy(note = note.trim().ifEmpty { null }, noteUpdatedAt = now, noteCreatedAt = it.noteCreatedAt ?: now, updatedAt = now)
                        }
                    }
                    editing = null
                }) { Text("Guardar") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun NotePill(label: String, on: Boolean, ink: Color, paper: Color, dot: Color?, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (on) ink else Color.Transparent,
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            dot?.let { Box(Modifier.size(12.dp).background(it, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp)) }
            Text(label, color = if (on) paper else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun tintName(t: String) = when (t) {
    "yellow" -> "Amarillo"; "green" -> "Verde"; "blue" -> "Azul"; "pink" -> "Rosa"; "red" -> "Rojo"; "purple" -> "Morado"; "orange" -> "Naranja"
    else -> t.replaceFirstChar { it.uppercase() }
}

private fun exportAll(context: android.content.Context, marks: List<BookAnnotation>, books: Map<String, Book>) {
    val text = buildString {
        marks.groupBy { it.bookId }.forEach { (id, list) ->
            val b = books[id]
            append("# ").append(b?.title ?: id).append('\n')
            b?.author?.let { append(it).append('\n') }
            append('\n')
            list.forEach { m ->
                m.text?.takeIf { it.isNotBlank() }?.let { append("> ").append(it.trim()).append('\n') }
                m.note?.takeIf { it.isNotBlank() }?.let { append('\n').append(it.trim()).append('\n') }
                m.chapter?.takeIf { it.isNotBlank() }?.let { append("— ").append(it).append('\n') }
                append('\n')
            }
        }
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Subrayados y notas")
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(send, "Exportar subrayados"))
}

@Composable
private fun MarkCard(mark: BookAnnotation, onClick: () -> Unit, onShare: () -> Unit, onEdit: () -> Unit, onCopy: () -> Unit) {
    val color = tintColor(mark.tint)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mark.text?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("«")
                            withStyle(androidx.compose.ui.text.SpanStyle(background = color.copy(alpha = 0.35f))) { append(it.trim()) }
                            append("»")
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                mark.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it.trim(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val day = java.time.Instant.ofEpochMilli(mark.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("es", "ES")))
                    Text(
                        listOfNotNull(mark.chapter?.takeIf { it.isNotBlank() }, mark.position?.let { "p. $it" }, day).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.IconButton(onClick = onShare) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Share, "Compartir") }
                    androidx.compose.material3.IconButton(onClick = onEdit) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, "Editar nota") }
                    androidx.compose.material3.IconButton(onClick = onCopy) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.ContentCopy, "Copiar") }
                }
            }
        }
    }
}

@Composable
private fun tintColor(tint: String?): Color = when (tint?.lowercase()) {
    "yellow" -> Color(0xFFE8B83A)
    "green" -> Color(0xFF7FB069)
    "blue" -> Color(0xFF5B8DEF)
    "pink", "red" -> Color(0xFFB23A6E)
    "purple" -> Color(0xFF8E6BBF)
    else -> MaterialTheme.colorScheme.primary
}

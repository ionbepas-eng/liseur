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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Delete
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
    // All five fit on any phone, each slot as wide as its word needs, as on the web.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 420.dp
        val names = labels.map { (id, label) -> id to stringResource(label) }
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.padding(5.dp)) {
                names.forEach { (id, name) ->
                    val on = id == selected
                    androidx.compose.material3.Surface(
                        onClick = { onSelect(id) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                        color = if (on) ink else Color.Transparent,
                        modifier = Modifier.weight(name.length + 4f),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (narrow) 13.sp else 16.sp, letterSpacing = 0.sp),
                            fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (on) paper else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, softWrap = false,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = if (narrow) 11.dp else 13.dp),
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
    val (localMarks, books) = data ?: (emptyList<BookAnnotation>() to emptyMap())
    // The web's highlights (Play Libros imports included), read from the shared state.
    val sharedVersion by com.chmouel.liseur.data.gdrive.LiburuShared.version.collectAsState()
    val webMarks = remember(sharedVersion, books.size) { webAnnotations(context, books) }
    val marks = remember(localMarks, webMarks) {
        val seen = localMarks.mapNotNull { it.text?.trim() }.toSet()
        (localMarks + webMarks.filter { it.text?.trim() !in seen }).sortedByDescending { it.createdAt }
    }
    var importing by remember { mutableStateOf(false) }
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
            item { VocabSection() }
            return@LazyColumn
        }
        val withNote = marks.count { !it.note.isNullOrBlank() }
        item {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("Subrayados y notas", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        "${marks.size} ${if (marks.size == 1) "subrayado" else "subrayados"} · $withNote con nota · ${marks.map { it.bookId }.distinct().size} ${if (marks.map { it.bookId }.distinct().size == 1) "libro" else "libros"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                androidx.compose.material3.OutlinedButton(onClick = { importing = true }) {
                    Text("↓ Importar de Play Libros", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.OutlinedButton(onClick = { exportAll(context, marks, books) }, enabled = marks.isNotEmpty()) {
                    Text("Exportar todo", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
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
                                com.chmouel.liseur.data.quotes.HomeQuote(b, mark.text.orEmpty().trim(), mark.chapter, mark.locatorJson.takeUnless { it.startsWith("source:") }, true),
                            )
                        }
                    },
                    onEdit = if (mark.id.startsWith("web:")) null else ({ editing = mark }),
                    onCopy = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("cita", "«${mark.text.orEmpty().trim()}» — ${book?.title.orEmpty()}"))
                    },
                )
            }
        }
    }
    if (importing) PlayImportDialog(books.values.filter { !it.hidden }.toList()) { importing = false }
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
private fun MarkCard(mark: BookAnnotation, onClick: () -> Unit, onShare: () -> Unit, onEdit: (() -> Unit)?, onCopy: () -> Unit) {
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
                        listOfNotNull(mark.chapter?.takeIf { it.isNotBlank() }, mark.position?.let { "p. $it" }, day, mark.locatorJson.takeIf { it.startsWith("source:") }?.removePrefix("source:")).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.IconButton(onClick = onShare) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Share, "Compartir") }
                    onEdit?.let { androidx.compose.material3.IconButton(onClick = it) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, "Editar nota") } }
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

/** The web's highlights for the books here, as annotations to show (never written back). */
private fun webAnnotations(context: android.content.Context, books: Map<String, Book>): List<BookAnnotation> {
    val state = runCatching { org.json.JSONObject(java.io.File(context.filesDir, com.chmouel.liseur.data.gdrive.DriveStateSync.STATE_CACHE).readText()) }.getOrNull()
    val webBooks = state?.optJSONObject("books") ?: org.json.JSONObject()
    val pending = com.chmouel.liseur.data.gdrive.LiburuShared.pendingAnnotations(context)
    val out = ArrayList<BookAnnotation>()
    val byKey = books.values.associateBy { LiburuShelves.keyOf(it.url) }
    fun take(key: String, ann: org.json.JSONObject?) {
        val book = byKey[key] ?: return
        ann ?: return
        ann.keys().forEach { id ->
            val a = ann.optJSONObject(id) ?: return@forEach
            if (a.optBoolean("deleted") || a.optString("text").isBlank()) return@forEach
            val color = a.optString("color").lowercase()
            val tint = when {
                color.startsWith("#f6d") || color.contains("yellow") -> "yellow"
                color.startsWith("#f2a") || color.contains("pink") -> "pink"
                color.startsWith("#a8d") || color.contains("green") -> "green"
                color.startsWith("#9ec") || color.contains("blue") -> "blue"
                else -> "yellow"
            }
            out += BookAnnotation(
                id = "web:$id", bookId = book.url, kind = "HIGHLIGHT",
                locatorJson = "source:" + if (a.optString("imported") == "play") "Play Libros" else "Web",
                text = a.optString("text"), note = a.optString("note").takeIf { it.isNotBlank() },
                tint = tint, chapter = a.optString("label").takeIf { it.isNotBlank() },
                position = a.optInt("page").takeIf { it > 0 },
                createdAt = a.optLong("created", a.optLong("t")),
            )
        }
    }
    webBooks.keys().forEach { k -> take(k, webBooks.optJSONObject(k)?.optJSONObject("ann")) }
    pending.keys().forEach { k -> take(k, pending.optJSONObject(k)) }
    return out.distinctBy { it.id }
}

/** «Importar de Play Libros»: each notes document, matched to a book you can change. */
@Composable
private fun PlayImportDialog(books: List<Book>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val docs by androidx.compose.runtime.produceState<Result<List<com.chmouel.liseur.data.gdrive.LiburuPlayNotes.Doc>>?>(null) {
        value = runCatching { com.chmouel.liseur.data.gdrive.LiburuPlayNotes.findDocs() }
    }
    var chosen by remember { mutableStateOf<Map<String, Book?>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }
    val sorted = remember(books) { books.sortedBy { it.title.lowercase() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Importar de Play Libros") },
        text = {
            val r = docs
            when {
                status != null -> Text(status!!)
                r == null -> com.chmouel.liseur.ui.BusyIndicator()
                r.isFailure -> Text("No se pudo leer Drive. Comprueba la conexión con Google en Ajustes.")
                r.getOrThrow().isEmpty() -> Text("No he encontrado documentos «Notas de…» de Play Libros en tu Drive.")
                else -> {
                    val list = r.getOrThrow()
                    LaunchedEffect(list) { chosen = list.associate { it.id to com.chmouel.liseur.data.gdrive.LiburuPlayNotes.match(books, it.title) } }
                    LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("He encontrado ${list.size} ${if (list.size == 1) "documento" else "documentos"}. Revisa a qué libro corresponde cada uno.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(list, key = { it.id }) { d ->
                            var open by remember { mutableStateOf(false) }
                            Column {
                                Text(d.title, style = MaterialTheme.typography.titleSmall)
                                Box {
                                    androidx.compose.material3.TextButton(onClick = { open = true }) {
                                        Text(chosen[d.id]?.title ?: "No importar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    androidx.compose.material3.DropdownMenu(open, { open = false }) {
                                        androidx.compose.material3.DropdownMenuItem(text = { Text("No importar") }, onClick = { chosen = chosen + (d.id to null); open = false })
                                        sorted.forEach { b -> androidx.compose.material3.DropdownMenuItem(text = { Text(b.title, maxLines = 1) }, onClick = { chosen = chosen + (d.id to b); open = false }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (status == null) {
                androidx.compose.material3.TextButton(enabled = docs?.getOrNull()?.isNotEmpty() == true, onClick = {
                    val list = docs?.getOrNull().orEmpty()
                    status = "Importando…"
                    scope.launch {
                        var added = 0
                        var booksN = 0
                        var fail = 0
                        val state = runCatching { org.json.JSONObject(java.io.File(context.filesDir, com.chmouel.liseur.data.gdrive.DriveStateSync.STATE_CACHE).readText()) }.getOrNull()
                        for (d in list) {
                            val b = chosen[d.id] ?: continue
                            try {
                                val items = com.chmouel.liseur.data.gdrive.LiburuPlayNotes.parse(com.chmouel.liseur.data.gdrive.LiburuPlayNotes.export(d.id))
                                val key = LiburuShelves.keyOf(b.url)
                                val existing = state?.optJSONObject("books")?.optJSONObject(key)?.optJSONObject("ann")
                                val now = System.currentTimeMillis()
                                val anns = items.mapNotNull { it ->
                                    val id = com.chmouel.liseur.data.gdrive.LiburuPlayNotes.noteId(key, it.text)
                                    if (existing?.has(id) == true) null
                                    else org.json.JSONObject().put("id", id).put("kind", "highlight").put("text", it.text).put("note", it.note)
                                        .put("color", it.color).put("label", it.chapter).put("page", it.page ?: org.json.JSONObject.NULL)
                                        .put("cfi", org.json.JSONObject.NULL).put("fraction", org.json.JSONObject.NULL).put("imported", "play")
                                        .put("created", it.created ?: now).put("t", now)
                                }
                                if (anns.isNotEmpty()) { com.chmouel.liseur.data.gdrive.LiburuShared.addAnnotations(context, key, anns); added += anns.size; booksN++ }
                            } catch (e: Exception) { fail++ }
                        }
                        val c = (context.applicationContext as LiseurApplication).container
                        c.applicationScopeLaunch { c.driveStateSync.syncNow(force = true) }
                        status = "$added ${if (added == 1) "subrayado importado" else "subrayados importados"} en $booksN ${if (booksN == 1) "libro" else "libros"}" + if (fail > 0) " · $fail con error" else ""
                    }
                }) { Text("Importar") }
            } else {
                androidx.compose.material3.TextButton(onClick = onDone) { Text("Cerrar") }
            }
        },
        dismissButton = { if (status == null) androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}

/** The words saved while reading, shared with the web. */
@Composable
private fun VocabSection() {
    val context = LocalContext.current
    val v by com.chmouel.liseur.data.gdrive.LiburuShared.version.collectAsState()
    val words = remember(v) { com.chmouel.liseur.data.gdrive.LiburuShared.vocab(context) }
    var query by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    if (review && words.isNotEmpty()) { VocabReview(words) { review = false }; return }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vocabulario", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (words.isNotEmpty()) androidx.compose.material3.Button(onClick = { review = true }) { Text("Repasar") }
        }
        Text("${words.size} ${if (words.size == 1) "palabra" else "palabras"} · las guardas desde el diccionario del lector", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (words.isNotEmpty()) androidx.compose.material3.OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar palabra") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        words.filter { query.isBlank() || it.optString("word").contains(query, true) || it.optString("def").contains(query, true) }.forEach { w ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(w.optString("word"), style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text("  " + w.optString("lang").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        w.optString("def").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        w.optString("book").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    androidx.compose.material3.IconButton(onClick = { com.chmouel.liseur.data.gdrive.LiburuShared.deleteWord(context, w.optString("id")) }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Delete, "Quitar")
                    }
                }
            }
        }
    }
}


/** The web's flashcards: least-reviewed first, tap to turn, «Otra vez» / «La sabía». */
@Composable
private fun VocabReview(words: List<org.json.JSONObject>, onDone: () -> Unit) {
    val context = LocalContext.current
    val order = remember { words.sortedWith(compareBy({ it.optInt("reviews") }, { it.optLong("lastReview") })) }
    var i by remember { mutableStateOf(0) }
    var show by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onDone)
    val w = order[i % order.size]
    fun str(k: String) = w.optString(k).takeIf { it.isNotBlank() && it != "null" }
    val tts = remember { arrayOfNulls<android.speech.tts.TextToSpeech>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        tts[0] = android.speech.tts.TextToSpeech(context) {}
        onDispose { tts[0]?.shutdown() }
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onDone) { Text("✕ Terminar") }
            Spacer(Modifier.weight(1f))
            Text("${i + 1} / ${order.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).clickable { show = !show },
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Text(w.optString("word"), style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                str("phonetic")?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                str("context")?.let { Text("«$it»", style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                if (show) {
                    Text(str("tr") ?: "—", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    str("def")?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                } else Text("Toca para ver la traducción", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                tts[0]?.let { t -> runCatching { t.language = java.util.Locale.forLanguageTag(w.optString("lang")) }; t.speak(str("lemma") ?: w.optString("word"), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "v") }
            }) { Text("🔊 Escuchar") }
            if (show) {
                fun next(ok: Boolean) {
                    com.chmouel.liseur.data.gdrive.LiburuShared.reviewWord(context, w.optString("id"), ok)
                    show = false
                    if (i + 1 >= order.size) { android.widget.Toast.makeText(context, "¡Repaso terminado!", android.widget.Toast.LENGTH_SHORT).show(); onDone() } else i++
                }
                androidx.compose.material3.OutlinedButton(modifier = Modifier.weight(1f), onClick = { next(false) }) { Text("Otra vez") }
                androidx.compose.material3.Button(modifier = Modifier.weight(1f), onClick = { next(true) }) { Text("La sabía") }
            }
        }
    }
}

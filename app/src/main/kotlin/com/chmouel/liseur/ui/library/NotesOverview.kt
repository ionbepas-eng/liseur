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
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
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

private enum class NotesFilter { ALL, HIGHLIGHTS, NOTES }

/** Every highlight and note in the library, grouped by book. */
@Composable
internal fun NotesOverview(
    onOpenBook: (Book) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as LiseurApplication).container
    val flow = remember {
        combine(
            container.database.annotationDao().observeAllMarks(),
            container.database.bookDao().observeAll(),
        ) { marks, books -> marks to books.associateBy { it.url } }
    }
    val data by flow.collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf(NotesFilter.ALL) }

    Column(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NotesFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    NotesFilter.ALL -> R.string.notes_filter_all
                                    NotesFilter.HIGHLIGHTS -> R.string.notes_filter_highlights
                                    NotesFilter.NOTES -> R.string.notes_filter_notes
                                },
                            ),
                        )
                    },
                )
            }
        }
        val (marks, books) = data ?: return@Column
        val shown = marks.filter { mark ->
            when (filter) {
                NotesFilter.ALL -> true
                NotesFilter.HIGHLIGHTS -> mark.note.isNullOrBlank()
                NotesFilter.NOTES -> !mark.note.isNullOrBlank()
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.notes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val grouped = shown.groupBy { it.bookId }.toList()
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            grouped.forEach { (bookId, bookMarks) ->
                val book = books[bookId]
                item(key = "h:$bookId") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = book != null) { book?.let(onOpenBook) }
                            .padding(top = 14.dp, bottom = 2.dp),
                    ) {
                        Text(
                            book?.title ?: bookId,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        book?.author?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(bookMarks, key = { it.id }) { mark -> MarkCard(mark) { book?.let(onOpenBook) } }
            }
        }
    }
}

@Composable
private fun MarkCard(mark: BookAnnotation, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(tintColor(mark.tint)),
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mark.text?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "“${it.trim()}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                mark.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it.trim(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                mark.chapter?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

package com.chmouel.liseur.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.R
import com.chmouel.liseur.domain.FilterGroup
import com.chmouel.liseur.domain.LibraryFilterOption
import com.chmouel.liseur.domain.LibrarySort

/** What the library page's own buttons do; provided by the library screen. */
internal class LiburuLibActions(
    val onSearch: () -> Unit,
    val onRefresh: () -> Unit,
    val onAddBook: () -> Unit,
    val onAddFolder: () -> Unit,
    val onConnect: () -> Unit,
)

internal val LocalLiburuLibActions = compositionLocalOf<LiburuLibActions?> { null }

@Composable
private fun paperOn(ink: Color) = if (ink.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** The web's library controls: search and order, state pills, the count and the page's buttons. */
@Composable
internal fun LiburuControls(
    state: LibraryUiState,
    actions: LiburuLibActions,
    onSetSort: (LibrarySort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onSetReadingFilter: ((Set<LibraryFilterOption>) -> Unit)?,
    onSetGroupBySeries: (Boolean) -> Unit,
    filterMenu: @Composable () -> Unit,
    onChooseStyle: (() -> Unit)?,
    extra: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = paperOn(ink)
    val field = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = actions.onSearch,
                shape = RoundedCornerShape(22.dp),
                color = field,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Text(stringResource(R.string.liburu_lib_search), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { sortOpen = true },
                    shape = RoundedCornerShape(22.dp),
                    color = field,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.heightIn(min = 46.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.sort.label(), maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                    }
                }
                DropdownMenu(sortOpen, { sortOpen = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label(), fontWeight = if (option == state.sort) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { sortOpen = false; if (option == state.sort) onToggleSortDirection() else onSetSort(option) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(if (state.sortReversed) R.string.sort_reversed else R.string.sort_normal)) },
                        onClick = { sortOpen = false; onToggleSortDirection() },
                    )
                }
            }
        }
        if (onSetReadingFilter != null && !state.filters.archived) {
            val reading = state.filters.options.filterTo(mutableSetOf()) { it.group == FilterGroup.READING }
            val chips = listOf(
                R.string.liburu_st_all to ALL_READING_STATES,
                R.string.liburu_st_reading to setOf(LibraryFilterOption.IN_PROGRESS),
                R.string.liburu_lib_to_read to setOf(LibraryFilterOption.UNREAD),
                R.string.liburu_lib_finished to setOf(LibraryFilterOption.FINISHED),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (label, options) ->
                    val on = reading == options || (options == ALL_READING_STATES && reading.isEmpty())
                    Pill(stringResource(label), on, ink, paper) {
                        onSetReadingFilter(if (options == ALL_READING_STATES) options else if (on) ALL_READING_STATES else options)
                    }
                }
                val grouped = state.filters.groupBySeries
                Pill((if (grouped) "✓ " else "") + stringResource(R.string.liburu_lib_group_series), grouped, ink, paper) {
                    onSetGroupBySeries(!grouped)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val total = state.quickCounts.all
            val shown = state.books.size
            Text(
                if (total > shown && total > 0) stringResource(R.string.liburu_lib_count_of, shown, total) else if (shown == 1) "1 libro" else stringResource(R.string.liburu_lib_count, shown),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            extra?.invoke()

        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePill("Seleccionar", Icons.Outlined.Check) { LiburuSelection.start() }
            OutlinePill(stringResource(R.string.liburu_lib_refresh), Icons.Outlined.Refresh, actions.onRefresh)
            var addOpen by remember { mutableStateOf(false) }
            Box {
                OutlinePill(stringResource(R.string.liburu_lib_add), Icons.Outlined.Add) { addOpen = true }
                DropdownMenu(addOpen, { addOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_book)) }, onClick = { addOpen = false; actions.onAddBook() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_folder)) }, onClick = { addOpen = false; actions.onAddFolder() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.connect_server_short)) }, onClick = { addOpen = false; actions.onConnect() })
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, ink: Color, paper: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (on) ink else Color.Transparent,
        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (on) paper else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OutlinePill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

package com.chmouel.liseur.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chmouel.liseur.R
import com.chmouel.liseur.ui.library.styles.LiburuPrefs
import com.chmouel.liseur.ui.theme.LiburuAppearance

/** Liburuville's own settings, at the top of the screen as on the web: appearance, goals, name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiburuSettings() {
    val context = LocalContext.current
    LiburuAppearance.init(context)
    val look by LiburuAppearance.look.collectAsState()

    SettingsGroup(stringResource(R.string.liburu_appearance)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.liburu_background), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Swatch(
                    color = null,
                    label = stringResource(R.string.liburu_bg_auto),
                    selected = look.background == null,
                    onClick = { LiburuAppearance.setBackground(null) },
                )
                LiburuAppearance.backgrounds.forEach { (key, color) ->
                    Swatch(color, swatchName(key), look.background == key) { LiburuAppearance.setBackground(key) }
                }
            }
            Text(
                stringResource(R.string.liburu_accent),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LiburuAppearance.accents.forEach { (key, color) ->
                    Swatch(color, swatchName(key), look.accent == key) { LiburuAppearance.setAccent(key) }
                }
            }
        }
    }

    SettingsGroup(stringResource(R.string.liburu_goals_settings)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.liburu_goal_minutes), LiburuPrefs.dailyGoal(context)) {
                LiburuPrefs.setDailyGoal(context, it)
            }
            NumberField(stringResource(R.string.liburu_goal_books), LiburuPrefs.yearGoal(context)) {
                LiburuPrefs.setYearGoal(context, it)
            }
            var name by remember { mutableStateOf(LiburuPrefs.name(context).orEmpty()) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    LiburuPrefs.setName(context, it.trim().ifEmpty { null })
                },
                label = { Text(stringResource(R.string.liburu_your_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumberField(label: String, initial: Int, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it > 0 }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(if (selected) 5.dp else 1.dp)
                .background(color ?: MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun swatchName(key: String): String = stringResource(
    when (key) {
        "crema" -> R.string.liburu_c_crema
        "papel" -> R.string.liburu_c_papel
        "blanco" -> R.string.liburu_c_blanco
        "arena" -> R.string.liburu_c_arena
        "salvia" -> R.string.liburu_c_salvia
        "rosa" -> R.string.liburu_c_rosa
        "niebla" -> R.string.liburu_c_niebla
        "grafito" -> R.string.liburu_c_grafito
        "noche" -> R.string.liburu_c_noche
        "terracota" -> R.string.liburu_c_terracota
        "azul" -> R.string.liburu_c_azul
        "verde" -> R.string.liburu_c_verde
        "morado" -> R.string.liburu_c_morado
        else -> R.string.liburu_c_tinta
    },
)

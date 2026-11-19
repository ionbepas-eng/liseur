package com.chmouel.liseur.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Liburuville's appearance choices, as in the web app's Settings → Appearance. */
data class LiburuLook(val background: String? = null, val accent: String = "rosa")

object LiburuAppearance {
    val backgrounds: List<Pair<String, Color>> = listOf(
        "crema" to Color(0xFFF5F1E6),
        "papel" to Color(0xFFF8F7F2),
        "blanco" to Color(0xFFFFFFFF),
        "arena" to Color(0xFFE8DFCC),
        "salvia" to Color(0xFFDCE5D6),
        "rosa" to Color(0xFFF3E1E3),
        "niebla" to Color(0xFFE1E5EA),
        "grafito" to Color(0xFF2B2A28),
        "noche" to Color(0xFF121417),
    )
    val accents: List<Pair<String, Color>> = listOf(
        "rosa" to Color(0xFFB23A6E),
        "terracota" to Color(0xFFA6491F),
        "azul" to Color(0xFF2E5E9E),
        "verde" to Color(0xFF2E6B47),
        "morado" to Color(0xFF6B4FA0),
        "tinta" to Color(0xFF1C1A17),
    )

    private val _look = MutableStateFlow(LiburuLook())
    val look: StateFlow<LiburuLook> = _look
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("liburu", Context.MODE_PRIVATE).also {
            _look.value = LiburuLook(it.getString("bg", null), it.getString("accent", "rosa") ?: "rosa")
        }
    }

    fun setBackground(key: String?) = update(_look.value.copy(background = key))
    fun setAccent(key: String) = update(_look.value.copy(accent = key))

    private fun update(look: LiburuLook) {
        _look.value = look
        prefs?.edit()?.putString("bg", look.background)?.putString("accent", look.accent)?.apply()
    }

    fun backgroundColor(look: LiburuLook): Color? = backgrounds.firstOrNull { it.first == look.background }?.second
    fun accentColor(look: LiburuLook): Color = accents.firstOrNull { it.first == look.accent }?.second ?: accents[0].second

    /** Whether the chosen background asks for a dark scheme; null leaves it to the theme mode. */
    fun forcesDark(look: LiburuLook): Boolean? = backgroundColor(look)?.let { it.luminance() < 0.3f }
}

/** The base scheme recoloured with the chosen background and accent. */
internal fun ColorScheme.liburu(look: LiburuLook, dark: Boolean): ColorScheme {
    val accent = LiburuAppearance.accentColor(look)
    val bg = LiburuAppearance.backgroundColor(look)
    val ink = if (dark) Color(0xFFEDE7D8) else Color(0xFF1B1A17)
    val primary = if (dark) lerp(accent, Color.White, if (look.accent == "tinta") 0.85f else 0.35f) else accent
    var scheme = copy(
        primary = primary,
        onPrimary = if (primary.luminance() > 0.5f) Color(0xFF1B1A17) else Color.White,
        primaryContainer = lerp(accent, bg ?: background, if (dark) 0.6f else 0.82f),
        onPrimaryContainer = if (dark) Color.White else lerp(accent, Color.Black, 0.45f),
        secondary = primary,
        secondaryContainer = lerp(accent, bg ?: background, if (dark) 0.7f else 0.86f),
        onSecondaryContainer = if (dark) Color.White else lerp(accent, Color.Black, 0.5f),
        inversePrimary = lerp(accent, Color.White, 0.4f),
    )
    if (bg != null) {
        val toward = if (dark) Color.White else ink
        scheme = scheme.copy(
            background = bg,
            surface = bg,
            surfaceBright = bg,
            surfaceContainerLowest = bg,
            surfaceContainerLow = lerp(bg, toward, 0.03f),
            surfaceContainer = lerp(bg, toward, 0.05f),
            surfaceContainerHigh = lerp(bg, toward, 0.08f),
            surfaceContainerHighest = lerp(bg, toward, 0.11f),
            surfaceVariant = lerp(bg, toward, 0.07f),
            surfaceDim = lerp(bg, toward, 0.12f),
        )
    }
    return scheme
}

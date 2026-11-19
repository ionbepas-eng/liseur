package com.chmouel.liseur.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Liburuville's appearance choices, as in the web app's Settings → Appearance. */
data class LiburuLook(val background: String? = null, val accent: String = "rosa", val wallpaper: String? = null)

/** A gradient behind every screen, as the web's "Fondo de pantalla". */
data class LiburuWallpaper(val key: String, val colors: List<Color>, val dark: Boolean)

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

    val wallpapers: List<LiburuWallpaper> = listOf(
        LiburuWallpaper("atardecer", listOf(Color(0xFFF6C9B8), Color(0xFFEDA9BE), Color(0xFFC9A0C9)), false),
        LiburuWallpaper("lavanda", listOf(Color(0xFFF1EDF9), Color(0xFFDCD2F2), Color(0xFFC2B5E8)), false),
        LiburuWallpaper("oceano", listOf(Color(0xFFE2F1F5), Color(0xFFB9D7E3), Color(0xFF8DB8CE)), false),
        LiburuWallpaper("pradera", listOf(Color(0xFFF0F2DF), Color(0xFFD5E3BE), Color(0xFFA9CC9C)), false),
        LiburuWallpaper("bosque", listOf(Color(0xFF34503F), Color(0xFF23382B), Color(0xFF16261D)), true),
        LiburuWallpaper("medianoche", listOf(Color(0xFF3E3266), Color(0xFF241D42), Color(0xFF120F22)), true),
    )

    private val _look = MutableStateFlow(LiburuLook())
    val look: StateFlow<LiburuLook> = _look
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("liburu", Context.MODE_PRIVATE).also {
            _look.value = LiburuLook(
                it.getString("bg", null),
                it.getString("accent", "rosa") ?: "rosa",
                it.getString("wallpaper", null),
            )
        }
    }

    fun setBackground(key: String?) = update(_look.value.copy(background = key))
    fun setAccent(key: String) = update(_look.value.copy(accent = key))
    fun setWallpaper(key: String?) = update(_look.value.copy(wallpaper = key))
    fun wallpaper(look: LiburuLook): LiburuWallpaper? = wallpapers.firstOrNull { it.key == look.wallpaper }

    private fun update(look: LiburuLook) {
        _look.value = look
        prefs?.edit()?.putString("bg", look.background)?.putString("accent", look.accent)
            ?.putString("wallpaper", look.wallpaper)?.apply()
    }

    fun backgroundColor(look: LiburuLook): Color? = backgrounds.firstOrNull { it.first == look.background }?.second
    fun accentColor(look: LiburuLook): Color = accents.firstOrNull { it.first == look.accent }?.second ?: accents[0].second

    /** Whether the chosen background asks for a dark scheme; null leaves it to the theme mode. */
    fun forcesDark(look: LiburuLook): Boolean? =
        wallpaper(look)?.dark ?: backgroundColor(look)?.let { it.luminance() < 0.3f }
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
    LiburuAppearance.wallpaper(look)?.let { wall ->
        // The gradient is drawn behind the screens; the page itself turns clear
        // and the cards become a frosted version of the chosen paper.
        val paper = bg ?: if (dark) Color(0xFF1A1917) else Color(0xFFFBF8F1)
        scheme = scheme.copy(
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceContainerLowest = paper.copy(alpha = 0.55f),
            surfaceContainerLow = paper.copy(alpha = 0.62f),
            surfaceContainer = paper.copy(alpha = 0.7f),
            surfaceContainerHigh = paper.copy(alpha = 0.92f),
            surfaceContainerHighest = paper.copy(alpha = 0.96f),
            surfaceVariant = paper.copy(alpha = 0.6f),
            surfaceBright = paper,
            surfaceDim = wall.colors.last(),
        )
    }
    return scheme
}

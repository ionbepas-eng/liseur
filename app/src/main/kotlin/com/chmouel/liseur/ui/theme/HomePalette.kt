package com.chmouel.liseur.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.chmouel.liseur.ui.LocalEInk

/** One shelf tile's colours: the card, the lighter sweep across it, and its icon and count. */
@Immutable
data class TileTint(val container: Color, val glow: Color, val content: Color)

/**
 * The fixed colours of the Home tab and the tab bar, in a light and a dark
 * cut. Kept apart from the Material scheme on purpose: the shelf tiles keep
 * their own hues whatever the wallpaper is, and everything else on the
 * screen still follows the theme.
 */
@Immutable
data class HomePalette(
    val accent: Color,
    val onAccent: Color,
    val progress: Color,
    val track: Color,
    val pill: Color,
    val onPill: Color,
    val card: Color,
    val cardBorder: Color,
    val heroWash: Color,
    val reading: TileTint,
    val toRead: TileTint,
    val series: TileTint,
    val finished: TileTint,
    val tabBar: Color,
    val tabBarBorder: Color,
    val tabSelected: Color,
    val onTabSelected: Color,
    val onTab: Color,
)

val LightHomePalette = HomePalette(
    accent = Color(0xFFB23A6E),
    onAccent = Color.White,
    progress = Color(0xFFB23A6E),
    track = Color(0xFFE3D9C6),
    pill = Color(0xFFF6D5E2),
    onPill = Color(0xFF7E2A52),
    card = Color(0xFFFBF8F1),
    cardBorder = Color(0xFFD9D1BD),
    heroWash = Color(0xFFF2E3E8),
    reading = TileTint(Color(0xFFF8E1EA), Color(0xFFF2C9D9), Color(0xFFB23A6E)),
    toRead = TileTint(Color(0xFFEDE7D8), Color(0xFFE3D9C6), Color(0xFF5E5A50)),
    series = TileTint(Color(0xFFF6EBCB), Color(0xFFEEDB9F), Color(0xFF8A6A12)),
    finished = TileTint(Color(0xFFE4ECDD), Color(0xFFCFDDC3), Color(0xFF4E6B3A)),
    tabBar = Color(0xFF111111),
    tabBarBorder = Color(0xFF2E2C28),
    tabSelected = Color(0xFFB23A6E),
    onTabSelected = Color.White,
    onTab = Color(0xFFA8A39A),
)

val DarkHomePalette = HomePalette(
    accent = Color(0xFFB23A6E),
    onAccent = Color.White,
    progress = Color(0xFFF2A7C3),
    track = Color(0xFF2E2C28),
    pill = Color(0xFF3A2230),
    onPill = Color(0xFFF2A7C3),
    card = Color(0xFF1A1917),
    cardBorder = Color(0xFF2E2C28),
    heroWash = Color(0xFF2A1A22),
    reading = TileTint(Color(0xFF2E1B25), Color(0xFF4A2638), Color(0xFFF2A7C3)),
    toRead = TileTint(Color(0xFF22211F), Color(0xFF2E2C28), Color(0xFFEDE7D8)),
    series = TileTint(Color(0xFF2A2412), Color(0xFF453A16), Color(0xFFE8B83A)),
    finished = TileTint(Color(0xFF1E261A), Color(0xFF2F3D27), Color(0xFFB7D19F)),
    tabBar = Color(0xFF0B0B0B),
    tabBarBorder = Color(0xFF2E2C28),
    tabSelected = Color(0xFFB23A6E),
    onTabSelected = Color.White,
    onTab = Color(0xFFA8A39A),
)

/**
 * The cut that suits the theme in force, read from the surface rather than
 * from the system's night setting, which the app's own may overrule. Null on
 * e-ink, where tints dither into grey and the screens keep their outlines.
 */
@Composable
fun homePalette(): HomePalette? {
    if (LocalEInk.current) return null
    val base = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkHomePalette else LightHomePalette
    // Liburuville: the chosen accent leads the tab bar and the progress bars.
    val primary = MaterialTheme.colorScheme.primary
    val accent = com.chmouel.liseur.ui.theme.LiburuAppearance.accentColor(
        com.chmouel.liseur.ui.theme.LiburuAppearance.look.value,
    )
    return base.copy(
        accent = accent,
        progress = primary,
        tabSelected = accent,
        onTabSelected = if (accent.luminance() > 0.5f) Color(0xFF1B1A17) else Color.White,
        reading = base.reading.copy(content = primary),
    )
}

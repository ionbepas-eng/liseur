package com.chmouel.liseur.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The bundled Literata, the same face the reader offers, so the chrome
 * around a book is set in the type of one. The platform serif it
 * replaces looked like a fallback because it was one.
 */
fun literataFamily(assets: AssetManager): FontFamily = FontFamily(
    Font("fonts/Literata.ttf", assets),
    Font("fonts/Literata-Italic.ttf", assets, style = FontStyle.Italic),
)

/**
 * Fraunces, soft and upright, as Liburuville sets its titles. One variable
 * file carries every weight; the instances below pin the axes.
 */
fun frauncesFamily(assets: AssetManager): FontFamily {
    fun cut(w: Int) = Font(
        "fonts/Fraunces.ttf", assets, FontWeight(w),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(w),
            FontVariation.Setting("SOFT", 100f),
            FontVariation.Setting("WONK", 0f),
            FontVariation.Setting("opsz", 72f),
        ),
    )
    return FontFamily(cut(400), cut(500), cut(600), cut(700))
}

/** Inter, the web's text face, from one variable file. */
fun interFamily(assets: AssetManager): FontFamily {
    fun cut(w: Int) = Font(
        "fonts/Inter.ttf", assets, FontWeight(w),
        variationSettings = FontVariation.Settings(FontVariation.weight(w)),
    )
    return FontFamily(cut(400), cut(500), cut(600), cut(700))
}

/**
 * Sizes as the web sets them (body 15px, small 13px, page titles 34px,
 * card titles 26px…), with no extra tracking: Android's defaults are a size
 * larger and spaced out, and a phone screen showed half what the web did.
 */
fun liseurTypography(serif: FontFamily, sans: FontFamily? = null): Typography = Typography().run {
    val s = sans ?: FontFamily.Default
    fun sansStyle(base: androidx.compose.ui.text.TextStyle, size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
        base.copy(fontFamily = s, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp, fontWeight = w)
    fun serifStyle(base: androidx.compose.ui.text.TextStyle, size: Int, line: Int, track: Double, w: FontWeight = FontWeight.SemiBold) =
        base.copy(fontFamily = serif, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp, fontWeight = w)
    copy(
        displayLarge = serifStyle(displayLarge, 42, 46, -1.2),
        displayMedium = serifStyle(displayMedium, 36, 40, -1.2),
        displaySmall = serifStyle(displaySmall, 30, 34, -1.0),
        headlineLarge = serifStyle(headlineLarge, 27, 31, -0.8),
        headlineMedium = serifStyle(headlineMedium, 23, 28, -0.6),
        headlineSmall = serifStyle(headlineSmall, 19, 24, -0.4),
        titleLarge = serifStyle(titleLarge, 17, 22, -0.2),
        titleMedium = sansStyle(titleMedium, 14, 19, FontWeight.SemiBold),
        titleSmall = sansStyle(titleSmall, 12, 16, FontWeight.SemiBold),
        bodyLarge = sansStyle(bodyLarge, 14, 20),
        bodyMedium = sansStyle(bodyMedium, 13, 18),
        bodySmall = sansStyle(bodySmall, 12, 16),
        labelLarge = sansStyle(labelLarge, 13, 17, FontWeight.Medium),
        labelMedium = sansStyle(labelMedium, 11, 15, FontWeight.Medium),
        labelSmall = sansStyle(labelSmall, 10, 13, FontWeight.Medium),
    )
}

/** The platform-serif fallback, for previews and tests without assets. */
val LiseurTypography = liseurTypography(FontFamily.Serif)

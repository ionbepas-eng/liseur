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

fun liseurTypography(serif: FontFamily, sans: FontFamily? = null): Typography = Typography().run {
    val t = if (sans == null) this else copy(
        bodyLarge = bodyLarge.copy(fontFamily = sans),
        bodyMedium = bodyMedium.copy(fontFamily = sans),
        bodySmall = bodySmall.copy(fontFamily = sans),
        labelLarge = labelLarge.copy(fontFamily = sans),
        labelMedium = labelMedium.copy(fontFamily = sans),
        labelSmall = labelSmall.copy(fontFamily = sans),
        titleMedium = titleMedium.copy(fontFamily = sans),
        titleSmall = titleSmall.copy(fontFamily = sans),
    )
    t.copy(
        displayLarge = displayLarge.copy(fontFamily = serif),
        displayMedium = displayMedium.copy(fontFamily = serif),
        displaySmall = displaySmall.copy(fontFamily = serif),
        headlineLarge = headlineLarge.copy(fontFamily = serif),
        headlineMedium = headlineMedium.copy(fontFamily = serif),
        headlineSmall = headlineSmall.copy(fontFamily = serif),
        titleLarge = titleLarge.copy(
            fontFamily = serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
        ),
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        titleSmall = titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
    )
}

/** The platform-serif fallback, for previews and tests without assets. */
val LiseurTypography = liseurTypography(FontFamily.Serif)

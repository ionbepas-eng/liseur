package com.chmouel.liseur.ui.library.styles

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.chmouel.liseur.R
import com.chmouel.liseur.data.quotes.HomeQuote
import com.chmouel.liseur.ui.theme.LiburuAppearance
import java.io.File

/**
 * Shares a quote as a picture for stories and posts (1080×1350), in the
 * colours chosen for the app, with the text as well for apps that take only words.
 */
object QuoteShare {
    fun share(context: Context, quote: HomeQuote) {
        val text = quote.text.trim().trim('"', '“', '”', '«', '»')
        val credit = listOfNotNull(quote.book.title, quote.book.author).joinToString(" · ")
        val file = runCatching { render(context, text, credit) }.getOrNull()
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "«$text»\n— $credit")
            if (file != null) {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".quotes", file)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.liburu_share_quote)))
    }

    private fun render(context: Context, text: String, credit: String): File {
        val w = 1080
        val h = 1350
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        LiburuAppearance.init(context)
        val look = LiburuAppearance.look.value
        val wall = LiburuAppearance.wallpaper(look)
        val colors = wall?.colors?.map { it.toArgb() }?.toIntArray()
            ?: (LiburuAppearance.backgroundColor(look) ?: androidx.compose.ui.graphics.Color(0xFFF5F1E6)).toArgb()
                .let { intArrayOf(it, it) }
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), colors, null, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val dark = wall?.dark ?: (LiburuAppearance.forcesDark(look) == true)
        val ink = if (dark) 0xFFF3EEE6.toInt() else 0xFF1B1A17.toInt()
        val accent = LiburuAppearance.accentColor(look).toArgb()

        val serif = runCatching { Typeface.createFromAsset(context.assets, "fonts/Fraunces.ttf") }.getOrDefault(Typeface.SERIF)
        val margin = 110
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create(serif, Typeface.ITALIC)
            textSize = when {
                text.length < 120 -> 64f
                text.length < 260 -> 52f
                else -> 42f
            }
        }
        val body = StaticLayout.Builder.obtain("«$text»", 0, text.length + 2, quotePaint, w - margin * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setMaxLines(14)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val creditPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.takeIf { !dark } ?: ink
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 36f
        }
        val creditLayout = StaticLayout.Builder.obtain("— $credit", 0, credit.length + 2, creditPaint, w - margin * 2).build()
        val total = body.height + 60 + creditLayout.height
        var y = ((h - total) / 2f).coerceAtLeast(120f)
        canvas.save(); canvas.translate(margin.toFloat(), y); body.draw(canvas); canvas.restore()
        y += body.height + 60
        canvas.save(); canvas.translate(margin.toFloat(), y); creditLayout.draw(canvas); canvas.restore()
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; alpha = 140; typeface = serif; textSize = 34f
        }
        canvas.drawText("Liburuville", margin.toFloat(), h - 80f, brand)

        val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
        return File(dir, "cita.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}

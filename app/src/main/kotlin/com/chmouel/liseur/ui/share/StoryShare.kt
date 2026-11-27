package com.chmouel.liseur.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.chmouel.liseur.ui.theme.LiburuAppearance
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Liburuville: what a story card says. Everything optional but the kicker and the title. */
data class StoryContent(
    val kicker: String,
    val title: String,
    val sub: String? = null,
    val line: String? = null,
    /** A big number (racha, libros del mes…) drawn above the title. */
    val big: String? = null,
    val coverPaths: List<String> = emptyList(),
    val progress: Float? = null,
    /** An asset path (badges/…webp) drawn as the hero. */
    val badgeAsset: String? = null,
    /** A quote: the card is set as a page of text. */
    val quote: String? = null,
    val text: String = "",
)

/** Liburuville: story cards for Instagram — paper colours, Margins-like doodles, or a transparent sticker. */
object StoryShare {
    val request = MutableStateFlow<StoryContent?>(null)
    fun open(content: StoryContent) { request.value = content }

    val palettes: List<Pair<String, Color?>> = listOf(
        "Del tema" to null,
        "Papel" to Color(0xFFF3E9D8),
        "Rosa" to Color(0xFFF6E7EA),
        "Salvia" to Color(0xFFE8EEDC),
        "Cielo" to Color(0xFFE2F1F5),
        "Vino" to Color(0xFF72243E),
        "Bosque" to Color(0xFF085041),
        "Tinta" to Color(0xFF2C2C2A),
    )

    private fun themeColor(): Color {
        val look = LiburuAppearance.look.value
        val wall = LiburuAppearance.wallpaper(look)
        return wall?.colors?.first() ?: LiburuAppearance.backgroundColor(look) ?: Color(0xFFF5F1E6)
    }

    fun render(context: Context, c: StoryContent, paper: Color?, doodles: Boolean, sticker: Boolean): Bitmap {
        val bg = paper ?: themeColor()
        val dark = bg.luminance() < 0.35f
        val ink = if (dark) 0xFFF6F0E6.toInt() else 0xFF2A2724.toInt()
        val accentC = LiburuAppearance.accentColor(LiburuAppearance.look.value)
        val accent = (if (dark) androidx.compose.ui.graphics.lerp(accentC, Color.White, 0.55f) else accentC).toArgb()
        val serif = runCatching { Typeface.createFromAsset(context.assets, "fonts/Fraunces.ttf") }.getOrDefault(Typeface.SERIF)
        val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val w = 1080
        val h = if (sticker) 1200 else 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val seed = (c.title.hashCode() and 0x7fffffff)

        if (sticker) {
            // A card that floats on whatever Instagram puts behind it.
            val card = RectF(60f, 60f, w - 60f, h - 60f)
            cv.drawRoundRect(RectF(card.left + 10, card.top + 18, card.right + 10, card.bottom + 18), 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
            cv.drawRoundRect(card, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toArgb() })
        } else {
            cv.drawColor(bg.toArgb())
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = ink; alpha = 200
        }
        // Quotes stand alone: colour and words, no doodles.
        if (doodles && c.quote == null) drawDoodles(cv, w, h, stroke, accent, seed, sticker)

        fun text(t: String, size: Float, face: Typeface, color: Int, y: Float, max: Int = 3, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, margin: Int = 120, alpha: Int = 255): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color; this.alpha = alpha }
            val l = StaticLayout.Builder.obtain(t, 0, t.length, p, w - margin * 2).setAlignment(align)
                .setLineSpacing(0f, 1.15f).setMaxLines(max).setEllipsize(TextUtils.TruncateAt.END).build()
            cv.save(); cv.translate(margin.toFloat(), y); l.draw(cv); cv.restore()
            return y + l.height
        }

        val top = if (sticker) 150f else 230f
        text(c.kicker.lowercase() + "…", 46f, Typeface.create(serif, Typeface.ITALIC), ink, top, 1)

        if (c.quote != null) {
            val q = "«" + c.quote.trim().trim('«', '»', '"', '“', '”') + "»"
            val size = when { q.length < 120 -> 64f; q.length < 260 -> 52f; else -> 42f }
            val qp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.create(serif, Typeface.ITALIC); color = ink }
            val l = StaticLayout.Builder.obtain(q, 0, q.length, qp, w - 260).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f).setMaxLines(if (sticker) 9 else 14).setEllipsize(TextUtils.TruncateAt.END).build()
            val y0 = ((h - l.height) / 2f - 60f).coerceAtLeast(top + 120f)
            cv.save(); cv.translate(130f, y0); l.draw(cv); cv.restore()
            var y = y0 + l.height + 50f
            underline(cv, 130f, y, 420f, accent)
            y += 40f
            text("— " + c.title + (c.sub?.let { " · $it" } ?: ""), 38f, sans, accent, y, 2, Layout.Alignment.ALIGN_NORMAL, 130)
        } else {
            var y = top + 120f
            when {
                c.badgeAsset != null -> {
                    val b = runCatching { context.assets.open(c.badgeAsset).use(BitmapFactory::decodeStream) }.getOrNull()
                    if (b != null) {
                        val s = if (sticker) 460 else 560
                        cv.drawBitmap(b, null, RectF((w - s) / 2f, y, (w + s) / 2f, y + s * b.height / b.width), Paint(Paint.FILTER_BITMAP_FLAG))
                        y += s * b.height / b.width + 50f
                    }
                }
                c.coverPaths.size > 1 -> {
                    val covers = c.coverPaths.take(4).mapNotNull { cover(it, 360) }
                    val cw = if (sticker) 220f else 250f
                    val gap = 30f
                    val total = covers.size * cw + (covers.size - 1) * gap
                    covers.forEachIndexed { i, b ->
                        val x = (w - total) / 2f + i * (cw + gap)
                        val r = RectF(x, y + (if (i % 2 == 1) 40f else 0f), x + cw, y + cw * 1.5f + (if (i % 2 == 1) 40f else 0f))
                        drawCover(cv, b, r, if (i % 2 == 0) -3f else 3f)
                    }
                    y += cw * 1.5f + 110f
                }
                c.coverPaths.size == 1 -> cover(c.coverPaths[0], 700)?.let { b ->
                    val cw = if (sticker) 400f else 520f
                    val r = RectF((w - cw) / 2f, y, (w + cw) / 2f, y + cw * 1.5f)
                    drawCover(cv, b, r, -3f)
                    y += cw * 1.5f + 80f
                }
            }
            if (c.big != null) {
                y = text(c.big, if (sticker) 220f else 300f, serif, ink, y - 20f, 1) + 10f
            }
            y = text(c.title, if (c.title.length > 26) 66f else 80f, serif, ink, y, 2) + 14f
            if (c.sub != null) y = text(c.sub, 42f, sans, ink, y, 2, alpha = 200) + 10f
            underline(cv, w / 2f - 220f, y + 20f, 440f, accent)
            y += 60f
            if (c.progress != null) {
                val bw = 560f; val x = (w - bw) / 2f
                val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 50 }
                cv.drawRoundRect(RectF(x, y, x + bw, y + 18f), 9f, 9f, track)
                cv.drawRoundRect(RectF(x, y, x + bw * c.progress.coerceIn(0.02f, 1f), y + 18f), 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                y += 50f
            }
            if (c.line != null) text(c.line, 40f, sans, ink, y, 2, alpha = 210)
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = serif; color = ink; alpha = 150; textAlign = Paint.Align.CENTER }
        cv.drawText("liburuville", w / 2f, h - (if (sticker) 110f else 130f), brand)
        return bmp
    }

    private fun cover(path: String, max: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= max) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()

    private fun drawCover(cv: Canvas, b: Bitmap, r: RectF, rot: Float) {
        cv.save(); cv.rotate(rot, r.centerX(), r.centerY())
        cv.drawRoundRect(RectF(r.left + 14, r.top + 18, r.right + 14, r.bottom + 18), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30000000 })
        val p = Path().apply { addRoundRect(r, 14f, 14f, Path.Direction.CW) }
        cv.clipPath(p)
        cv.drawBitmap(b, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        cv.restore()
    }

    private fun underline(cv: Canvas, x: Float, y: Float, len: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; this.color = color }
        val path = Path().apply {
            moveTo(x, y)
            val seg = len / 4
            for (i in 0 until 4) rQuadTo(seg / 2, if (i % 2 == 0) -16f else 16f, seg, 0f)
        }
        cv.drawPath(path, p)
    }

    /** Hand-drawn margins: stars, waves, hearts, sprigs and loops, placed along the edges. */
    private fun drawDoodles(cv: Canvas, w: Int, h: Int, p: Paint, accent: Int, seed: Int, sticker: Boolean) {
        val r = java.util.Random(seed.toLong())
        val inset = if (sticker) 110f else 70f
        val spots = listOf(
            inset + 40f to inset + 60f, w - inset - 60f to inset + 50f,
            inset + 30f to h * 0.45f, w - inset - 40f to h * 0.55f,
            inset + 60f to h - inset - 230f, w - inset - 80f to h - inset - 260f,
        )
        spots.forEachIndexed { i, (x, y) ->
            val kind = (i + r.nextInt(5)) % 5
            val paint = Paint(p).apply { if (i % 3 == 1) color = accent }
            val s = 1f + r.nextFloat() * 0.4f
            cv.save(); cv.translate(x, y); cv.scale(s, s); cv.rotate(r.nextFloat() * 30 - 15)
            val path = Path()
            when (kind) {
                0 -> { // star
                    for (k in 0..10) {
                        val a = Math.PI / 5 * k - Math.PI / 2
                        val rad = if (k % 2 == 0) 34.0 else 15.0
                        val px = (rad * cos(a)).toFloat(); val py = (rad * sin(a)).toFloat()
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                }
                1 -> { path.moveTo(-40f, 0f); path.rQuadTo(10f, -16f, 20f, 0f); path.rQuadTo(10f, 16f, 20f, 0f); path.rQuadTo(10f, -16f, 20f, 0f); path.rQuadTo(10f, 16f, 20f, 0f) }
                2 -> { // heart
                    path.moveTo(0f, 22f); path.cubicTo(-38f, -4f, -22f, -34f, 0f, -14f); path.cubicTo(22f, -34f, 38f, -4f, 0f, 22f)
                }
                3 -> { // sprig
                    path.moveTo(0f, 40f); path.quadTo(4f, 0f, 0f, -40f)
                    for (k in 0..2) { val yy = 20f - k * 22f; path.moveTo(0f, yy); path.quadTo(-22f, yy - 8f, -26f, yy - 22f); path.moveTo(0f, yy - 4f); path.quadTo(22f, yy - 12f, 26f, yy - 26f) }
                }
                else -> { // loop
                    path.moveTo(-40f, 10f); path.cubicTo(-20f, -30f, 10f, -30f, 0f, 0f); path.cubicTo(-10f, 30f, 30f, 20f, 40f, -10f)
                }
            }
            cv.drawPath(path, paint)
            cv.restore()
        }
    }

    private fun save(context: Context, bmp: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "quotes").apply { mkdirs() }
        val f = File(dir, "historia-${System.currentTimeMillis() % 100000}.png")
        dir.listFiles()?.filter { it.name.startsWith("historia-") }?.forEach { it.delete() }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, context.packageName + ".quotes", f)
    }

    /** Straight into Instagram's story composer; the Android share sheet when Instagram is not there. */
    fun toInstagram(context: Context, bmp: Bitmap, c: StoryContent, sticker: Boolean, paper: Color?) {
        val uri = save(context, bmp)
        val ig = "com.instagram.android"
        val story = Intent("com.instagram.share.ADD_TO_STORY").apply {
            if (sticker) {
                type = "image/png"
                putExtra("interactive_asset_uri", uri)
                val bg = String.format("#%06X", 0xFFFFFF and (paper ?: Color(0xFFF3E9D8)).toArgb())
                putExtra("top_background_color", bg); putExtra("bottom_background_color", bg)
            } else {
                setDataAndType(uri, "image/png")
            }
            setPackage(ig)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.grantUriPermission(ig, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context.packageManager.resolveActivity(story, 0) != null && runCatching { context.startActivity(story) }.isSuccess) return
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).setPackage(ig)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(send) }.isSuccess) return
        toOthers(context, bmp, c)
    }

    fun toOthers(context: Context, bmp: Bitmap, c: StoryContent) {
        val uri = save(context, bmp)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .apply { if (c.text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, c.text) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Placed once at the root: shows the share sheet whenever a screen asks for one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShareHost() {
    val req by StoryShare.request.collectAsState()
    val content = req ?: return
    val context = LocalContext.current
    var paperIx by remember(content) { mutableStateOf(0) }
    var doodles by remember(content) { mutableStateOf(true) }
    var sticker by remember(content) { mutableStateOf(false) }
    val paper = StoryShare.palettes[paperIx].second
    val bmp by produceState<Bitmap?>(null, content, paperIx, doodles, sticker) {
        value = withContext(Dispatchers.Default) { runCatching { StoryShare.render(context, content, paper, doodles, sticker) }.getOrNull() }
    }
    ModalBottomSheet(
        onDismissRequest = { StoryShare.request.value = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Compartir", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.fillMaxWidth().heightIn(max = 380.dp), contentAlignment = Alignment.Center) {
                bmp?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.aspectRatio(it.width.toFloat() / it.height).clip(RoundedCornerShape(16.dp))
                            .then(if (sticker) Modifier.background(Color(0xFFDDDDDD)) else Modifier),
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(StoryShare.palettes.size) { i ->
                    val (name, c) = StoryShare.palettes[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { paperIx = i }) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(c ?: LiburuAppearance.accentColor(LiburuAppearance.look.value))
                                .border(if (paperIx == i) 3.dp else 1.dp, if (paperIx == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (content.quote == null) FilterChip(selected = doodles, onClick = { doodles = !doodles }, label = { Text("Garabatos") })
                Text("Sin fondo (pegatina)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Switch(checked = sticker, onCheckedChange = { sticker = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { bmp?.let { StoryShare.toInstagram(context, it, content, sticker, paper); StoryShare.request.value = null } },
                    modifier = Modifier.weight(1f),
                ) { Text("Historia de Instagram") }
                OutlinedButton(
                    onClick = { bmp?.let { StoryShare.toOthers(context, it, content); StoryShare.request.value = null } },
                    modifier = Modifier.weight(1f),
                ) { Text("Otras apps") }
            }
        }
    }
}

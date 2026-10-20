package com.doz.minihome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.widget.TextView

// Design is laid out on a 1920x1080 canvas (same numbers as the HTML mockup); k scales to the screen.
class Ui(private val ctx: Context) {
    val sw = ctx.resources.displayMetrics.widthPixels
    val sh = ctx.resources.displayMetrics.heightPixels
    val k = sw / 1920f
    fun px(v: Float) = (v * k).toInt()
    fun pxf(v: Float) = v * k

    private val fonts = HashMap<Int, Typeface>()
    fun font(weight: Int): Typeface = fonts.getOrPut(weight) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Typeface.Builder(ctx.assets, "fonts/JosefinSans.ttf")
                    .setFontVariationSettings("'wght' $weight").build()
            } else Typeface.createFromAsset(ctx.assets, "fonts/JosefinSans.ttf")
        } catch (e: Exception) { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    }

    fun text(view: TextView, sizePx: Float, weight: Int, spacingPx: Float = 0f) = view.apply {
        typeface = font(weight)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k)
        letterSpacing = spacingPx / sizePx
        setTextColor(WHITE)
        includeFontPadding = false
    }

    companion object {
        const val WHITE = 0xFFF4F1EA.toInt()
        const val GOLD = 0xFFC9A24B.toInt()
        const val NEON = 0xFF6FD6FF.toInt()        // Skyfall Shanghai neon
        const val NEON_DEEP = 0xFF1E7BFF.toInt()
    }
}

/** Separable box blur, 3 passes (≈ gaussian). Only used on tiny (160x90) bitmaps. */
fun blurSmall(src: Bitmap, radius: Int = 3): Bitmap {
    val w = src.width; val h = src.height
    val a = IntArray(w * h); src.getPixels(a, 0, w, 0, 0, w, h)
    val b = IntArray(w * h)
    repeat(3) {
        boxPass(a, b, w, h, radius, true)
        boxPass(b, a, w, h, radius, false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val outer = if (horizontal) h else w
    val inner = if (horizontal) w else h
    val div = 2 * r + 1
    for (o in 0 until outer) {
        var sr = 0; var sg = 0; var sb = 0
        fun idx(i: Int): Int { val c = i.coerceIn(0, inner - 1); return if (horizontal) o * w + c else c * w + o }
        for (i in -r..r) { val p = src[idx(i)]; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
        for (i in 0 until inner) {
            dst[idx(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = src[idx(i + r + 1)]; val rem = src[idx(i - r)]
            sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
            sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
            sb += (add and 255) - (rem and 255)
        }
    }
}

/** Thin line icons from the mockup, drawn on a 48x48 grid. */
class LineIcon(private val kind: Kind, private val strokePx: Float) : Drawable() {
    enum class Kind { HDMI, GRID, GEAR }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Ui.WHITE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 48f
        c.save()
        c.translate(b.left + (b.width() - 48 * s) / 2, b.top + (b.height() - 48 * s) / 2)
        c.scale(s, s)
        p.strokeWidth = strokePx / s
        when (kind) {
            Kind.HDMI -> {
                c.drawPath(Path().apply {
                    moveTo(6f, 16f); lineTo(42f, 16f); lineTo(42f, 26f); lineTo(37f, 32f)
                    lineTo(11f, 32f); lineTo(6f, 26f); close()
                }, p)
                c.drawLine(13f, 21f, 35f, 21f, p)
            }
            Kind.GRID -> for ((x, y) in listOf(9f to 9f, 28f to 9f, 9f to 28f, 28f to 28f))
                c.drawRoundRect(RectF(x, y, x + 11, y + 11), 3f, 3f, p)
            Kind.GEAR -> {
                c.drawCircle(24f, 24f, 6f, p)
                val l = floatArrayOf(
                    24f, 6f, 24f, 12f, 24f, 36f, 24f, 42f, 6f, 24f, 12f, 24f, 36f, 24f, 42f, 24f,
                    11.3f, 11.3f, 15.5f, 15.5f, 32.5f, 32.5f, 36.7f, 36.7f,
                    11.3f, 36.7f, 15.5f, 32.5f, 32.5f, 15.5f, 36.7f, 11.3f
                )
                c.drawLines(l, p)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

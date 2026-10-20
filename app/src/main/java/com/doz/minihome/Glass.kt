package com.doz.minihome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout

/**
 * The frosted dock. Android 11 has no live backdrop blur, so it draws the matching
 * region of a pre-blurred copy of the current wallpaper behind its children.
 */
class GlassDock(ctx: Context, private val ui: Ui) : LinearLayout(ctx) {
    private val radius = ui.pxf(40f)
    private var cur: Bitmap? = null
    private var prev: Bitmap? = null
    private var mix = 1f
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x29FFFFFF
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; color = 0x47FFFFFF }
    private val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val clip = Path()
    private val rect = RectF()
    private val loc = IntArray(2)

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        // No elevation: the dock is translucent, so a shadow would show through it.
    }

    fun setBlur(b: Bitmap?) {
        prev = cur; cur = b
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            addUpdateListener { mix = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        tint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x30FFFFFF, 0x16FFFFFF, Shader.TileMode.CLAMP)
        val m = ui.pxf(60f)
        hairline.shader = LinearGradient(m, 0f, w - m, 0f,
            intArrayOf(0x00C9A24B, 0xB3C9A24B.toInt(), 0x00C9A24B), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        getLocationOnScreen(loc)
        c.save()
        c.clipPath(clip)
        drawBlur(c, prev, 255)
        drawBlur(c, cur, (mix * 255).toInt())
        if (cur == null && prev == null) c.drawColor(0x33202428)
        c.drawRect(rect, tint)
        c.restore()
        c.drawRoundRect(rect.left + .5f, rect.top + .5f, rect.right - .5f, rect.bottom - .5f, radius, radius, border)
        c.drawLine(radius, 1.5f, width - radius, 1.5f, highlight)
        val m = ui.pxf(60f)
        c.drawLine(m, height - 1f, width - m, height - 1f, hairline)
    }

    private fun drawBlur(c: Canvas, b: Bitmap?, alpha: Int) {
        if (b == null || alpha <= 0) return
        val sx = b.width / ui.sw.toFloat(); val sy = b.height / ui.sh.toFloat()
        val src = Rect((loc[0] * sx).toInt(), (loc[1] * sy).toInt(),
            ((loc[0] + width) * sx).toInt(), ((loc[1] + height) * sy).toInt())
        bmpPaint.alpha = alpha
        c.drawBitmap(b, src, rect, bmpPaint)
    }
}

/**
 * Cold neon ring + glow around a focused tile. Fire OS's GPU renderer ignores BlurMaskFilter,
 * so the glow is rendered once into a bitmap with a software Canvas and then just drawn.
 */
class NeonGlow(ctx: Context, private val ui: Ui, private val pad: Int, private val corner: Float) : View(ctx) {
    private var art: Bitmap? = null

    init { alpha = 0f }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        art?.recycle()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = ui.pxf(2.5f).coerceAtLeast(2f)
        // ring sits just outside the tile edge so the tile never covers it
        val o = stroke / 2 + 1f
        val r = RectF(pad - o, pad - o, w - pad + o, h - pad + o)
        val e = ui.pxf(8f)
        c.drawRoundRect(r.left - e, r.top - e, r.right + e, r.bottom + e, corner + e, corner + e,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x801E7BFF.toInt(); maskFilter = BlurMaskFilter(ui.pxf(40f), BlurMaskFilter.Blur.OUTER)
            })
        c.drawRoundRect(r, corner + o, corner + o, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC6FD6FF.toInt(); maskFilter = BlurMaskFilter(ui.pxf(14f), BlurMaskFilter.Blur.OUTER)
        })
        c.drawRoundRect(r, corner + o, corner + o, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; color = Ui.NEON
        })
        art = b
    }

    override fun onDraw(c: Canvas) { art?.let { c.drawBitmap(it, 0f, 0f, null) } }
}

package com.doz.minihome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Intro: a camera aperture of 8 curved steel blades that spins open in one move,
 * then removes itself. Same geometry as the HTML mockup.
 */
class IrisView(ctx: Context, private val ui: Ui) : View(ctx) {
    private val n = 8
    private val tanHalf = tan(Math.PI / n).toFloat()
    private val far = maxOf(ui.sw, ui.sh) * 1.7f
    private val paths = List(n) { Path() }
    private var r = 0f
    private var twist = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x8CC9A24B.toInt()
        strokeWidth = ui.pxf(2f).coerceAtLeast(1f)
        strokeJoin = Paint.Join.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF34373D.toInt(), 0xFF1C1E22.toInt(), 0xFF0D0E10.toInt()), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val a = r * tanHalf
        val big = r * 1.25f
        val sag = if (r > 0) big - sqrt(big * big - a * a) else 0f
        for (k in 0 until n) {
            val th = Math.toRadians((k * 360.0 / n) + twist)
            val co = cos(th).toFloat(); val si = sin(th).toFloat()
            fun x(px: Float, py: Float) = cx + px * co - py * si
            fun y(px: Float, py: Float) = cy + px * si + py * co
            val p = paths[k]; p.reset()
            p.moveTo(x(-a, -r), y(-a, -r))
            // curved inner edge (bulges outward, like a real iris leaf)
            p.quadTo(x(0f, -r - 2 * sag), y(0f, -r - 2 * sag), x(a, -r), y(a, -r))
            p.lineTo(x(far, -r), y(far, -r))
            p.lineTo(x(far, -r - far), y(far, -r - far))
            p.lineTo(x(-a - far * 0.9f, -r - far), y(-a - far * 0.9f, -r - far))
            // curved seam over the previous blade
            p.quadTo(x(-a, -r - far * 0.45f), y(-a, -r - far * 0.45f), x(-a, -r), y(-a, -r))
            p.close()
            c.drawPath(p, fill)
            c.drawPath(p, edge)
        }
    }

    fun play(onDone: () -> Unit) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            interpolator = PathInterpolator(0.65f, 0f, 0.35f, 1f)   // ease-in-out
            addUpdateListener {
                val p = it.animatedValue as Float
                r = ui.pxf(1250f) * p
                twist = 100f * p
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (parent as? ViewGroup)?.removeView(this@IrisView)
                    onDone()
                }
            })
            start()
        }
    }
}

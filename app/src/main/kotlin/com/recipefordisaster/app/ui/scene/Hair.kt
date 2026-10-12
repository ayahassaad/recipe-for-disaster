package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Hair, for guests, waiters and the player. Each style is drawn in "hair units": a tenth of a scene
 * unit, measured from the middle of the head, which is a circle of radius 34. [hx], [hy] is the
 * middle of the head in scene units.
 */
internal class HairPen(private val pen: Pen, private val hx: Float, private val hy: Float) {
    private fun sx(x: Float) = hx + x / 10f
    private fun sy(y: Float) = hy + y / 10f

    fun Pen.Shape.m(x: Float, y: Float) = moveTo(sx(x), sy(y))
    fun Pen.Shape.l(x: Float, y: Float) = lineTo(sx(x), sy(y))
    fun Pen.Shape.q(cx: Float, cy: Float, x: Float, y: Float) = quadTo(sx(cx), sy(cy), sx(x), sy(y))
    fun Pen.Shape.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(sx(x1), sy(y1), sx(x2), sy(y2), sx(x), sy(y))

    /** A filled shape. */
    fun fill(color: Color, build: Pen.Shape.() -> Unit) = pen.shape(color, build = build)

    /** A line drawn along a path, [width] in hair units: a strand, a curl, a shine. */
    fun strand(color: Color, width: Float, build: Pen.Shape.() -> Unit) = pen.shape(color, stroke = width / 10f, build = build)

    fun blob(x: Float, y: Float, r: Float, color: Color) = pen.dot(sx(x), sy(y), r / 10f, color)

    fun box(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) = pen.box(sx(x), sy(y), w / 10f, h / 10f, color, radius = r / 10f)
}

/** Hair that hangs down behind the head (long hair), drawn before the face. */
internal fun Pen.drawHairBehind(style: Int, hx: Float, hy: Float, hair: Color) {
    if (style == 2) box(hx - 3.8f, hy - 2.4f, 7.6f, 6.2f, hair, radius = 2.4f)
}

/** Hair on top of the head, seen from the front, in one of the [style]s. */
internal fun Pen.drawHair(style: Int, hx: Float, hy: Float, hair: Color) {
    when (style) {
        0, 2 -> shape(hair) { // side parting
            moveTo(hx - 3.4f, hy - 0.2f)
            quadTo(hx - 3.2f, hy - 4.2f, hx + 0.6f, hy - 3.6f)
            quadTo(hx + 3.6f, hy - 3.4f, hx + 3.4f, hy - 0.2f)
            quadTo(hx + 1f, hy - 2.2f, hx - 3.4f, hy - 0.2f)
        }
        1 -> for (k in -2..2) dot(hx + k * 1.4f, hy - 2.8f + abs(k) * 0.5f, 1.2f, hair) // curls
        3 -> { // bun
            dot(hx, hy - 3.8f, 1.5f, hair)
            shape(hair) {
                moveTo(hx - 3.3f, hy - 0.6f)
                quadTo(hx, hy - 4.6f, hx + 3.3f, hy - 0.6f)
                quadTo(hx, hy - 2.4f, hx - 3.3f, hy - 0.6f)
            }
        }
        else -> { // short crop with a little quiff
            shape(hair) {
                moveTo(hx - 3.2f, hy - 1f)
                quadTo(hx, hy - 4.6f, hx + 3.2f, hy - 1f)
                quadTo(hx, hy - 2.6f, hx - 3.2f, hy - 1f)
            }
            dot(hx + 1.2f, hy - 3.4f, 0.9f, hair)
        }
    }
}

/** The back of someone's head, for a guest walking out with their back to us. */
internal fun Pen.drawHairFromBehind(style: Int, hx: Float, hy: Float, hair: Color) {
    dot(hx, hy, 3.4f, hair)
    if (style == 3) dot(hx, hy - 3f, 1.4f, hair)
}

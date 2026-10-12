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

/** The darker shade of a hair colour, for the parts in shadow and the lines between strands. */
internal fun shadeOf(hair: Color) = Color(hair.red * 0.75f, hair.green * 0.75f, hair.blue * 0.75f, hair.alpha)

/** The lighter shine of a hair colour, where the light catches it. */
internal fun shineOf(hair: Color) = Color(hair.red + (1f - hair.red) * 0.3f, hair.green + (1f - hair.green) * 0.3f, hair.blue + (1f - hair.blue) * 0.3f, hair.alpha)

/** Hair that hangs down behind the head (long hair), drawn before the face. */
internal fun Pen.drawHairBehind(style: Int, hx: Float, hy: Float, hair: Color) {
    if (style == 2) box(hx - 3.8f, hy - 2.4f, 7.6f, 6.2f, hair, radius = 2.4f)
}

/** Hair on top of the head, seen from the front, in one of the [style]s. */
internal fun Pen.drawHair(style: Int, hx: Float, hy: Float, hair: Color) {
    val h = HairPen(this, hx, hy)
    when (style) {
        0 -> h.sideParting(hair)
        2 -> shape(hair) { // side parting
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

/** Swept over to one side from a parting, over the ears, with a few strands and a shine on top. */
private fun HairPen.sideParting(hair: Color) {
    val shade = shadeOf(hair)
    val shine = shineOf(hair)
    fill(hair) {
        m(-37f, 8f); c(-41f, -30f, -18f, -46f, 3f, -45f); c(28f, -44f, 41f, -28f, 37f, 8f)
        c(34f, 0f, 32f, -10f, 29f, -15f); c(16f, -22f, -4f, -25f, -23f, -17f); c(-29f, -12f, -33f, -3f, -37f, 8f); close()
    }
    // The fringe, swept across from the parting.
    fill(shade) {
        m(-13f, -42f); c(6f, -38f, 24f, -26f, 31f, -11f); c(20f, -18f, 6f, -21f, -9f, -19f); c(-15f, -26f, -16f, -34f, -13f, -42f); close()
    }
    strand(shine, 2f) { m(-12f, -41f); c(-12f, -33f, -11f, -27f, -8f, -20f) } // the parting
    strand(hair, 1.8f) { m(-4f, -36f); c(8f, -32f, 18f, -24f, 24f, -15f) }
    strand(shade, 2f) { m(-24f, -30f); c(-28f, -22f, -31f, -12f, -33f, -2f) }
    strand(shine, 2.4f) { m(-25f, -32f); c(-19f, -37f, -11f, -39f, -3f, -39f) }
}

/** The back of someone's head, for a guest walking out with their back to us. */
internal fun Pen.drawHairFromBehind(style: Int, hx: Float, hy: Float, hair: Color) {
    dot(hx, hy, 3.4f, hair)
    if (style == 3) dot(hx, hy - 3f, 1.4f, hair)
}

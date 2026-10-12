package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.graphics.Color

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
    if (style == 2) HairPen(this, hx, hy).longHairBehind(hair)
}

/** Hair on top of the head, seen from the front, in one of the [style]s. */
internal fun Pen.drawHair(style: Int, hx: Float, hy: Float, hair: Color) {
    val h = HairPen(this, hx, hy)
    when (style) {
        0 -> h.sideParting(hair)
        2 -> h.longHair(hair)
        1 -> h.curls(hair)
        3 -> h.bun(hair)
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

/** Where each curl sits on a curly head, and how big it is: all round the top and down the sides. */
private val CURLS = listOf(
    Triple(-30f, -4f, 10f), Triple(-34f, -16f, 10f), Triple(-30f, -29f, 11f), Triple(-19f, -38f, 11f), Triple(-6f, -43f, 11f),
    Triple(7f, -43f, 11f), Triple(20f, -38f, 11f), Triple(30f, -29f, 11f), Triple(34f, -16f, 10f), Triple(30f, -4f, 10f),
    Triple(-12f, -32f, 9f), Triple(12f, -32f, 9f), Triple(0f, -36f, 9f), Triple(-22f, -24f, 8f), Triple(22f, -24f, 8f),
)

/** A full head of curls, each with a little curl mark, and the odd glint of shine. */
private fun HairPen.curls(hair: Color) {
    val shade = shadeOf(hair)
    val shine = shineOf(hair)
    CURLS.forEach { (x, y, r) -> blob(x, y, r, hair) }
    CURLS.filterIndexed { i, _ -> i % 2 == 0 }.forEach { (x, y, _) ->
        strand(shade, 1.8f) { m(x - 4f, y + 2f); q(x - 4f, y - 4f, x + 3f, y - 3f) }
    }
    CURLS.filterIndexed { i, _ -> i % 3 == 1 }.forEach { (x, y, _) -> blob(x - 3f, y - 4f, 2.2f, shine) }
}

/** Long hair falling down behind the shoulders, in shadow, with a couple of strands. */
private fun HairPen.longHairBehind(hair: Color) {
    fill(shadeOf(hair)) {
        m(-37f, -10f); c(-46f, 18f, -46f, 48f, -38f, 66f); c(-30f, 62f, -22f, 64f, -14f, 68f); l(14f, 68f)
        c(22f, 64f, 30f, 62f, 38f, 66f); c(46f, 48f, 46f, 18f, 37f, -10f); close()
    }
    strand(hair, 2.4f) { m(-36f, 20f); c(-38f, 38f, -36f, 52f, -32f, 62f) }
    strand(hair, 2.4f) { m(36f, 20f); c(38f, 38f, 36f, 52f, 32f, 62f) }
}

/** Parted in the middle and framing the face, with a shine down one side. */
private fun HairPen.longHair(hair: Color) {
    val shade = shadeOf(hair)
    fill(hair) {
        m(0f, -45f); c(-27f, -45f, -41f, -26f, -39f, 4f); c(-38f, 18f, -36f, 28f, -31f, 36f); c(-31f, 16f, -28f, -5f, -18f, -18f)
        c(-10f, -27f, -4f, -31f, 0f, -35f); c(4f, -31f, 10f, -27f, 18f, -18f); c(28f, -5f, 31f, 16f, 31f, 36f)
        c(36f, 28f, 38f, 18f, 39f, 4f); c(41f, -26f, 27f, -45f, 0f, -45f); close()
    }
    strand(shade, 2f) { m(0f, -44f); l(0f, -34f) } // the parting
    strand(shineOf(hair), 2.2f) { m(-6f, -40f); c(-16f, -36f, -26f, -24f, -31f, -6f) }
    strand(shade, 1.8f) { m(6f, -40f); c(16f, -36f, 26f, -24f, 31f, -6f) }
    strand(shade, 1.8f) { m(-34f, 0f); c(-34f, 14f, -33f, 24f, -31f, 32f) }
}

/** The pink of the band holding a bun in place. */
private val HAIR_TIE = Color(0xFFE85D75)

/** Pulled back smoothly into a bun on top, held with a hair tie, the comb lines running up to it. */
private fun HairPen.bun(hair: Color) {
    val shade = shadeOf(hair)
    val shine = shineOf(hair)
    blob(0f, -49f, 14f, hair)
    strand(shade, 2f) { m(-8f, -52f); c(-4f, -60f, 6f, -58f, 7f, -50f); c(8f, -43f, -2f, -41f, -5f, -46f) } // wound round
    blob(-4f, -55f, 2.6f, shine)
    fill(hair) {
        m(-35f, 4f); c(-38f, -32f, -18f, -44f, 0f, -44f); c(18f, -44f, 38f, -32f, 35f, 4f)
        c(31f, -12f, 18f, -21f, 0f, -22f); c(-18f, -21f, -31f, -12f, -35f, 4f); close()
    }
    box(-9f, -39f, 18f, 5f, 2.5f, HAIR_TIE)
    strand(shade, 1.8f) { m(-28f, -12f); c(-22f, -26f, -12f, -34f, -4f, -38f) }
    strand(shade, 1.8f) { m(28f, -12f); c(22f, -26f, 12f, -34f, 4f, -38f) }
    strand(shine, 2f) { m(-16f, -24f); c(-12f, -30f, -8f, -34f, -3f, -37f) }
}

/** The back of someone's head, for a guest walking out with their back to us. */
internal fun Pen.drawHairFromBehind(style: Int, hx: Float, hy: Float, hair: Color) {
    dot(hx, hy, 3.4f, hair)
    if (style == 3) dot(hx, hy - 3f, 1.4f, hair)
}

package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.app.ui.scene.SceneLayout.Rect
import com.recipefordisaster.domain.employee.Role
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.recipefordisaster.domain.service.ServiceFloor
import com.recipefordisaster.domain.service.TableLayout

/**
 * Everything drawn in the restaurant scene, as flat shapes with enough
 * detail to read as real things: chairs with backrests and cushions,
 * dressed tables, people with hair, arms and outfits. [Pen] maps scene
 * units (see [SceneLayout]) to pixels for the current screen, so every
 * function here works in the same units as the floor plan.
 */
internal class Pen(private val scope: DrawScope, val unit: Float, private val origin: Offset) : DrawScope by scope {
    fun p(point: Point) = Offset(origin.x + point.x * unit, origin.y + point.y * unit)
    fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    fun u(units: Float) = units * unit

    /**
     * A pen that draws everything [s] times smaller, shrinking towards [center]: for the tables (and the
     * people at them) in a room packed with more than six. Draw with the usual full-size offsets from
     * [center] and they come out scaled.
     */
    fun around(center: Point, s: Float): Pen =
        if (s == 1f) this else Pen(scope, unit * s, Offset(origin.x + center.x * unit * (1 - s), origin.y + center.y * unit * (1 - s)))

    fun box(rect: Rect, color: Color, radius: Float = 1.5f) =
        drawRoundRect(color, topLeft = p(rect.left, rect.top), size = Size(u(rect.width), u(rect.height)), cornerRadius = CornerRadius(u(radius)))

    fun box(left: Float, top: Float, width: Float, height: Float, color: Color, radius: Float = 0.6f) =
        drawRoundRect(color, topLeft = p(left, top), size = Size(u(width), u(height)), cornerRadius = CornerRadius(u(radius)))

    fun outline(rect: Rect, color: Color, radius: Float = 1.5f, width: Float = 0.6f) =
        drawRoundRect(color, topLeft = p(rect.left, rect.top), size = Size(u(rect.width), u(rect.height)), cornerRadius = CornerRadius(u(radius)), style = Stroke(u(width)))

    fun dot(center: Point, radius: Float, color: Color) = drawCircle(color, radius = u(radius), center = p(center))
    fun dot(x: Float, y: Float, radius: Float, color: Color) = drawCircle(color, radius = u(radius), center = p(x, y))
    fun ring(x: Float, y: Float, radius: Float, color: Color, width: Float = 0.4f) =
        drawCircle(color, radius = u(radius), center = p(x, y), style = Stroke(u(width)))
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float = 0.4f) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = u(width), cap = StrokeCap.Round)
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
        drawOval(color, topLeft = p(cx - rx, cy - ry), size = Size(u(rx * 2), u(ry * 2)))

    /** A path in scene units: build it with [moveTo] / [lineTo] / [quadTo] on the [Shape]. */
    fun shape(color: Color, stroke: Float? = null, build: Shape.() -> Unit) {
        val shape = Shape(this).apply(build)
        if (stroke == null) drawPath(shape.path, color) else drawPath(shape.path, color, style = Stroke(u(stroke), cap = StrokeCap.Round))
    }

    class Shape(private val pen: Pen) {
        val path = Path()
        fun moveTo(x: Float, y: Float) { val o = pen.p(x, y); path.moveTo(o.x, o.y) }
        fun lineTo(x: Float, y: Float) { val o = pen.p(x, y); path.lineTo(o.x, o.y) }
        fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { val c = pen.p(cx, cy); val o = pen.p(x, y); path.quadraticTo(c.x, c.y, o.x, o.y) }
        fun close() = path.close()
    }
}

internal object Palette {
    val wall = Color(0xFFE9DCC9)
    val kitchenTileA = Color(0xFFDDE1E0)
    val kitchenTileB = Color(0xFFCDD2D1)
    val grout = Color(0x22000000)
    val plankA = Color(0xFFD9B98C)
    val plankB = Color(0xFFCDA97A)
    val plankC = Color(0xFFE2C59B)
    val plankLine = Color(0x33704A25)
    val wood = Color(0xFF8A5A35)
    val woodLight = Color(0xFFB07A4C)
    val woodDark = Color(0xFF5A3620)
    val steel = Color(0xFFB4BCBF)
    val steelMid = Color(0xFF8E979A)
    val steelDark = Color(0xFF5E676A)
    val ovenBody = Color(0xFF3D3B3A)
    val cloth = Color(0xFFFFFFFF)
    val clothCheck = Color(0xFFD94B3B)
    val chairSeat = Color(0xFF9A643B)
    val chairDark = Color(0xFF6E4429)
    val cushion = Color(0xFFC0392B)
    val ink = Color(0xFF2B2622)
    val face = Color(0xFFF7E3C4)
    val blush = Color(0x55E8736B)
    val chefWhite = Color(0xFFFDFDFB)
    val serverRed = Color(0xFFC0392B)
    val washerBlue = Color(0xFF3B78A8)
    val smoke = Color(0xFF8E8E8E)
    val flame = Color(0xFFF08A24)
    val dirt = Color(0x668A6A3A)
    val rat = Color(0xFF6B625B)
    val gold = Color(0xFFE9B527)
    val alert = Color(0xFFD7322B)
    val leaf = Color(0xFF4E8F4A)
    val leafDark = Color(0xFF3A6E37)
    val pot = Color(0xFFB5603C)
    val shadow = Color(0x33000000)
    val guestColors = listOf(
        Color(0xFF6C8EBF), Color(0xFF9C6FB6), Color(0xFF4FA38A), Color(0xFFE08E45),
        Color(0xFFCF6F8E), Color(0xFF8C9A3B), Color(0xFF5B6FB0), Color(0xFFB5764E),
    )
    val hairColors = listOf(
        Color(0xFF3B2A20), Color(0xFF7A4A2A), Color(0xFFE2B866), Color(0xFF1F1B1A),
        Color(0xFFA8432A), Color(0xFF9A9A9A), Color(0xFF5C3B28),
    )
}

// ---------------------------------------------------------------- the room

/**
 * Fills whatever screen space the room doesn't cover on a tall phone: the
 * kitchen's back wall carries on above it and the dark front wall below,
 * so the restaurant reads as filling the screen rather than floating in it.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurround(origin: androidx.compose.ui.geometry.Offset, unit: Float) {
    val top = origin.y
    val bottom = origin.y + unit * SceneLayout.HEIGHT
    if (top > 0f) clipRect(0f, 0f, size.width, top) {
        // The kitchen's tiled wall carries on up to the top of the screen, in the same checkerboard
        // (clipped, so it never spills over the bar above).
        val tile = 6f * unit
        var row = 1
        while (top - row * tile > -tile) {
            val y = top - row * tile
            var col = kotlin.math.floor(-origin.x / tile).toInt()
            while (origin.x + col * tile < size.width) {
                val x = origin.x + col * tile
                val even = (col + row) % 2 == 0
                drawRect(if (even) Palette.kitchenTileB else Palette.kitchenTileA, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = Size(tile + 1f, tile + 1f))
                col++
            }
            drawRect(Palette.grout, topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = Size(size.width, unit * 0.15f))
            row++
        }
        var c = kotlin.math.floor(-origin.x / tile).toInt()
        while (origin.x + c * tile < size.width) {
            drawRect(Palette.grout, topLeft = androidx.compose.ui.geometry.Offset(origin.x + c * tile, 0f), size = Size(unit * 0.15f, top))
            c++
        }
    }
    if (bottom < size.height) drawRect(Palette.woodDark, topLeft = androidx.compose.ui.geometry.Offset(0f, bottom - 1f), size = Size(size.width, size.height - bottom + 1f))
}

/** The room. [doorOpen] is how far the front doors are open: 0 shut, 1 wide open. */
internal fun Pen.drawRoom(cleanliness: Int, doorOpen: Float, time: Float, name: String = "", text: TextMeasurer? = null) {
    drawRect(Palette.wall, topLeft = p(0f, 0f), size = Size(u(SceneLayout.WIDTH), u(SceneLayout.HEIGHT)))

    // Kitchen: square tiles with grout lines, and a tiled splashback along the back wall.
    val tile = 6f
    var y = 0f
    while (y < SceneLayout.KITCHEN_BOTTOM) {
        var x = 0f
        while (x < SceneLayout.WIDTH) {
            val even = ((x / tile).toInt() + (y / tile).toInt()) % 2 == 0
            drawRect(if (even) Palette.kitchenTileA else Palette.kitchenTileB, topLeft = p(x, y), size = Size(u(tile) + 1, u(tile) + 1))
            x += tile
        }
        y += tile
    }
    for (gx in 0..(SceneLayout.WIDTH / tile).toInt()) line(gx * tile, 0f, gx * tile, SceneLayout.KITCHEN_BOTTOM, Palette.grout, 0.15f)
    for (gy in 0..(SceneLayout.KITCHEN_BOTTOM / tile).toInt()) line(0f, gy * tile, SceneLayout.WIDTH, gy * tile, Palette.grout, 0.15f)
    box(0f, 0f, SceneLayout.WIDTH, 4f, Color(0xFF9FB7B4), radius = 0f)
    for (i in 0..20) line(i * 5f, 0f, i * 5f, 4f, Color(0x33FFFFFF), 0.2f)

    // Dining room: wooden planks, staggered like a real floor.
    val plankH = 4f
    y = 44f
    var row = 0
    while (y < 140f) {
        var x = if (row % 2 == 0) 0f else -9f
        var i = row
        while (x < SceneLayout.WIDTH) {
            val color = when (i % 3) { 0 -> Palette.plankA; 1 -> Palette.plankB; else -> Palette.plankC }
            drawRect(color, topLeft = p(x, y), size = Size(u(18f) + 1, u(plankH) + 1))
            line(x, y, x, y + plankH, Palette.plankLine, 0.2f)
            x += 18f
            i++
        }
        line(0f, y, SceneLayout.WIDTH, y, Palette.plankLine, 0.2f)
        y += plankH
        row++
    }
    // A runner rug down the centre aisle.
    box(44f, 46f, 12f, 92f, Color(0xFF9E3B2F), radius = 0.8f)
    box(45f, 47f, 10f, 90f, Color(0xFFB8493A), radius = 0.6f)
    for (k in 0..8) line(46f, 50f + k * 10f, 54f, 50f + k * 10f, Color(0x55F2D29B), 0.3f)

    // Dirt: more stains the dirtier it gets, always in the same places so they don't jump around.
    val stains = ((100 - cleanliness) / 12).coerceIn(0, STAIN_SPOTS.size)
    STAIN_SPOTS.take(stains).forEachIndexed { i, spot ->
        // Grey scuffs and crumbs ground into the boards — nothing like a fresh, shiny spill.
        val scuff = Color(0x335A4A3A)
        line(spot.x - 2.6f, spot.y, spot.x + 2.2f, spot.y - 0.8f, scuff, 0.7f)
        line(spot.x - 1.4f, spot.y + 1f, spot.x + 2.8f, spot.y + 0.4f, scuff, 0.5f)
        for (k in 0..2) dot(spot.x - 1.5f + k * 1.6f, spot.y + 1.8f - (k % 2) * 0.6f, 0.25f, Color(0x555A4A3A))
    }
    if (cleanliness < 45) drawRat(Point(88f, 132f), time)

    drawCounter()
    drawPlant(Point(5f, 50f), time)
    drawPlant(Point(95f, 50f), time)
    drawPlant(Point(95f, 131f), time)

    // Front wall, double door and doormat.
    drawRect(Palette.woodDark, topLeft = p(0f, 140f), size = Size(u(SceneLayout.WIDTH), u(10f)))
    for (k in 0..9) line(k * 10f, 140f, k * 10f, 150f, Color(0x22000000), 0.2f)
    val door = SceneLayout.door
    box(door.left - 3f, door.top - 4.5f, door.width + 6f, 4f, Color(0xFF7D5A3B), radius = 0.6f)
    if (name.isNotBlank() && text != null) {
        // The restaurant's name painted on a sign over the door.
        val sign = Rect(door.left - 9f, door.top - 5.2f, door.right + 9f, door.top - 0.4f)
        box(sign, Color(0xFF2F3B2F), radius = 0.8f)
        outline(sign, Palette.gold, radius = 0.8f, width = 0.35f)
        centeredText(text, name, sign.center, size = if (name.length > 14) 2.1f else 2.6f, color = Color(0xFFF2E9DC), bold = true)
    } else {
        for (k in 0..5) line(door.left - 2f + k * 4.6f, door.top - 4f, door.left - 2f + k * 4.6f, door.top - 1f, Color(0x55000000), 0.25f)
    }
    if (doorOpen > 0.02f) {
        drawRect(Palette.plankC, topLeft = p(door.left, door.top), size = Size(u(door.width), u(door.height)))
        // The two door leaves, hinged at the outside edges, swinging out: seen from above, each one
        // gets narrower as it opens, until it lies flat against the wall.
        val open = doorOpen.coerceIn(0f, 1f)
        val leaf = (door.width / 2) * (1f - open) + 1.2f * open
        box(door.left, door.top, leaf, door.height, Palette.woodLight, radius = 0.2f)
        box(door.right - leaf, door.top, leaf, door.height, Palette.woodLight, radius = 0.2f)
        if (open < 0.9f) {
            line(door.left + leaf, door.top, door.left + leaf, door.bottom, Palette.woodDark, 0.3f)
            line(door.right - leaf, door.top, door.right - leaf, door.bottom, Palette.woodDark, 0.3f)
        }
    } else {
        box(door, Palette.woodLight, radius = 0.5f)
        line(door.center.x, door.top, door.center.x, door.bottom, Palette.woodDark, 0.4f)
        box(door.left + 1.5f, door.top + 1.5f, door.width / 2 - 3f, door.height - 3f, Color(0x22000000), radius = 0.4f)
        box(door.center.x + 1.5f, door.top + 1.5f, door.width / 2 - 3f, door.height - 3f, Color(0x22000000), radius = 0.4f)
        dot(door.center.x - 1.4f, door.center.y, 0.6f, Palette.gold)
        dot(door.center.x + 1.4f, door.center.y, 0.6f, Palette.gold)
    }
}

private val STAIN_SPOTS = listOf(
    Point(12f, 78f), Point(36f, 104f), Point(84f, 80f), Point(62f, 130f),
    Point(18f, 106f), Point(66f, 52f), Point(30f, 134f), Point(88f, 110f),
)

private fun Pen.drawCounter() {
    val c = SceneLayout.counter
    box(c, Palette.wood, radius = 1f)
    box(c.left, c.top, c.width, 2.2f, Palette.woodLight, radius = 1f)
    for (k in 1..8) line(c.left + k * 11f, c.top + 2.4f, c.left + k * 11f + 3f, c.bottom - 0.6f, Color(0x22000000), 0.25f)
    // Service bell.
    oval(70f, c.top + 1.6f, 2.2f, 0.7f, Palette.woodDark)
    shape(Palette.gold) {
        moveTo(68f, c.top + 1.4f)
        quadTo(70f, c.top - 2.2f, 72f, c.top + 1.4f)
        close()
    }
    dot(70f, c.top - 1.1f, 0.35f, Palette.gold)
    // The ticket rail runs along the top of the kitchen wall, above the oven and stove, so the
    // orders hanging on it during service never cover the chef.
    line(6f, TICKET_RAIL_Y, 46f, TICKET_RAIL_Y, Palette.steelDark, 0.4f)
    for (x in listOf(6f, 46f)) dot(x, TICKET_RAIL_Y, 0.5f, Palette.steelDark)
}

/** The dish station at the right end of the counter: a deep basin with suds, a drying rack, and a sign. */
internal fun Pen.drawDishStation(text: TextMeasurer, sign: String) {
    val c = SceneLayout.counter
    val x = 80f
    box(x, c.top - 1f, 14f, 6f, Palette.steel, radius = 0.6f)
    box(x + 1f, c.top - 0.2f, 7f, 4.4f, Palette.steelDark, radius = 0.6f)
    for (k in 0..2) dot(x + 2.6f + k * 1.6f, c.top + 1.6f + (k % 2) * 0.6f, 0.6f, Color(0xCCFFFFFF)) // suds
    for (k in 0..3) line(x + 9.2f + k * 1.2f, c.top - 0.4f, x + 9.2f + k * 1.2f, c.top + 4.6f, Palette.steelMid, 0.3f) // drying rack
    box(x + 2f, c.top - 6.6f, 10f, 4f, Color(0xFF2F6188), radius = 0.5f)
    centeredText(text, sign, Point(x + 7f, c.top - 4.6f), size = 2.3f, color = Color.White, bold = true)
}

/** A potted plant, its leaves swaying gently (each plant in its own rhythm). */
/** Where the order tickets hang: high on the kitchen wall. */
internal const val TICKET_RAIL_Y = 1.2f

private fun Pen.drawPlant(at: Point, time: Float = 0f) {
    val sway = sin(time * 1.1f + at.x * 0.37f + at.y * 0.11f) * 0.09f
    oval(at.x, at.y + 3.4f, 3.6f, 1.2f, Palette.shadow)
    box(at.x - 2.8f, at.y + 0.5f, 5.6f, 4f, Palette.pot, radius = 0.8f)
    box(at.x - 3.2f, at.y, 6.4f, 1.2f, Color(0xFF9A4F30), radius = 0.4f)
    for (k in 0 until 7) {
        // Tips move more than the base, so it bends rather than spins.
        val a = (k / 7f) * 2f * PI.toFloat() + sway * (1f + (k % 3) * 0.4f)
        val tipX = at.x + cos(a) * 4.2f
        val tipY = at.y - 1.5f + sin(a) * 3.2f
        shape(if (k % 2 == 0) Palette.leaf else Palette.leafDark) {
            moveTo(at.x, at.y)
            quadTo(at.x + cos(a + 0.5f) * 3f, at.y - 1.5f + sin(a + 0.5f) * 2.4f, tipX, tipY)
            quadTo(at.x + cos(a - 0.5f) * 3f, at.y - 1.5f + sin(a - 0.5f) * 2.4f, at.x, at.y)
        }
    }
}

internal fun Pen.drawRat(at: Point, time: Float) {
    val wiggle = sin(time * 9f) * 0.8f
    oval(at.x, at.y + 0.6f, 3.4f, 1.0f, Palette.shadow)
    oval(at.x, at.y, 3f, 1.6f, Palette.rat)
    dot(at.x - 3.4f, at.y, 1.3f, Palette.rat)
    dot(at.x - 3f, at.y - 1.2f, 0.6f, Color(0xFFD9A4A0))
    dot(at.x - 4f, at.y - 0.4f, 0.35f, Palette.ink)
    dot(at.x - 4.7f, at.y, 0.25f, Color(0xFFE38D8D))
    shape(Palette.rat, stroke = 0.5f) {
        moveTo(at.x + 3f, at.y)
        quadTo(at.x + 5f, at.y - 2f + wiggle, at.x + 7f, at.y + wiggle)
    }
}

// ---------------------------------------------------------------- the kitchen

/**
 * The oven, by model: the ancient one is a battered black box; the sturdy
 * one is brushed steel with red knobs; the convection oven is sleek black
 * glass with a fan and a glowing blue display.
 */
internal fun Pen.drawOven(condition: Int, time: Float, onFire: Boolean, level: Int = 1) {
    val o = SceneLayout.oven
    oval(o.center.x, o.bottom + 0.6f, o.width / 2, 1f, Palette.shadow)
    val (frame, body, panel, knob) = when {
        level >= 3 -> listOf(Color(0xFF1B1D22), Color(0xFF2B2F38), Color(0xFF111317), Color(0xFF9AA3AD))
        level == 2 -> listOf(Palette.steelMid, Color(0xFFB9C0C6), Color(0xFF8D959C), Color(0xFFC0392B))
        else -> listOf(Palette.steelMid, Palette.ovenBody, Color(0xFF2A2827), Palette.steel)
    }
    box(o, frame)
    box(o.left + 0.6f, o.top + 0.6f, o.width - 1.2f, o.height - 1.2f, body, radius = 1.2f)
    if (level == 2) for (k in 0..5) line(o.left + 1f, o.top + 6f + k * 1.6f, o.right - 1f, o.top + 6f + k * 1.6f, Color(0x22FFFFFF), 0.2f) // brushed steel
    // Control panel with knobs and a little clock.
    box(o.left + 1.2f, o.top + 1.2f, o.width - 2.4f, 4f, panel, radius = 0.6f)
    if (level >= 3) {
        // A touch panel instead of knobs, with a bright display.
        box(o.left + 2.4f, o.top + 2.2f, 7f, 2f, Color(0xFF0E2F4A), radius = 0.4f)
        line(o.left + 3f, o.top + 3.2f, o.left + 8.6f, o.top + 3.2f, Color(0xFF6FD3FF), 0.5f)
        for (i in 0..2) dot(o.left + 12f + i * 2.4f, o.top + 3.2f, 0.5f, Color(0xFF6FD3FF))
    } else {
        for (i in 0..3) {
            val kx = o.left + 3.4f + i * 3.4f
            dot(kx, o.top + 3.2f, 1f, knob)
            line(kx, o.top + 3.2f, kx, o.top + 2.4f, Palette.ink, 0.25f)
        }
    }
    box(o.right - 5f, o.top + 2.2f, 3.2f, 2f, if (level >= 3) Color(0xFF0E2F4A) else Color(0xFF1A3A2A), radius = 0.3f)
    // Door handle and glass window — warm when it's working, dark and cracked when broken.
    box(o.left + 3f, o.top + 6.2f, o.width - 6f, 0.9f, Palette.steel, radius = 0.4f)
    val window = Rect(o.left + 3f, o.top + 8f, o.right - 3f, o.bottom - 2.5f)
    box(window, Color(0xFF1E1D1C), radius = 1f)
    if (condition > 0) {
        box(window.left + 0.6f, window.top + 0.6f, window.width - 1.2f, window.height - 1.2f, Color(0xFF8A5A2E), radius = 0.8f)
        line(window.left + 1f, window.center.y, window.right - 1f, window.center.y, Color(0x66000000), 0.3f)
        oval(window.center.x, window.center.y + 0.8f, 3f, 1f, Color(0xFFC77A3A)) // something baking
        line(window.left + 1.4f, window.top + 1.2f, window.left + 4f, window.top + 1.2f, Color(0x55FFFFFF), 0.3f)
    } else {
        line(window.left + 2f, window.top + 1f, window.center.x, window.bottom - 1f, Color(0x88FFFFFF), 0.2f)
        line(window.center.x, window.bottom - 1f, window.right - 2f, window.top + 2f, Color(0x88FFFFFF), 0.2f)
    }
    if (level >= 3 && condition > 0) {
        // The convection fan, turning.
        val spin = time * 6f
        for (k in 0..2) {
            val a = spin + k * 2.094f
            line(window.center.x, window.center.y, window.center.x + kotlin.math.cos(a) * 1.8f, window.center.y + kotlin.math.sin(a) * 1.8f, Color(0x99FFFFFF), 0.4f)
        }
    }
    if (condition in 1..34) dot(window.center, 2.6f, Palette.flame.copy(alpha = 0.45f))
    if (condition <= 0 || onFire) drawSmoke(Point(o.center.x, o.top), time)
    if (onFire) drawFlames(Point(o.center.x, o.top + 4f), time)
}

internal fun Pen.drawSmoke(from: Point, time: Float) {
    for (i in 0..3) {
        val phase = ((time * 0.6f + i * 0.25f) % 1f)
        val drift = sin((time + i) * 2f) * 2f
        dot(Point(from.x + drift + i - 1.5f, from.y - phase * 10f), 2f + phase * 2.5f, Palette.smoke.copy(alpha = (1f - phase) * 0.6f))
    }
}

internal fun Pen.drawFlames(at: Point, time: Float) {
    for (i in -1..1) {
        val flicker = sin(time * 14f + i * 2f) * 0.8f
        dot(Point(at.x + i * 3f, at.y - 1f + flicker), 2.4f, Palette.flame)
        dot(Point(at.x + i * 3f, at.y - 1.6f + flicker), 1.2f, Color(0xFFFFD44D))
    }
}

internal fun Pen.drawStove(busy: Boolean, time: Float) {
    val s = SceneLayout.stove
    oval(s.center.x, s.bottom + 0.6f, s.width / 2, 1f, Palette.shadow)
    box(s, Palette.steel)
    box(s.left + 0.8f, s.top + 0.8f, s.width - 1.6f, s.height - 1.6f, Color(0xFF2E2E2E), radius = 1f)
    val burners = listOf(Point(s.left + 5f, s.top + 5f), Point(s.right - 5f, s.top + 5f), Point(s.left + 5f, s.bottom - 5f), Point(s.right - 5f, s.bottom - 5f))
    burners.forEachIndexed { i, b ->
        ring(b.x, b.y, 3.2f, Palette.steelDark, 0.5f)
        ring(b.x, b.y, 1.8f, Palette.steelDark, 0.4f)
        // Cast-iron grate.
        line(b.x - 3.4f, b.y, b.x + 3.4f, b.y, Palette.steelMid, 0.4f)
        line(b.x, b.y - 3.4f, b.x, b.y + 3.4f, Palette.steelMid, 0.4f)
        if (busy && i != 1) {
            val glow = 0.55f + 0.45f * sin(time * 10f + i)
            ring(b.x, b.y, 2.5f, Palette.flame.copy(alpha = glow), 0.6f)
            ring(b.x, b.y, 1.4f, Color(0xFF4FA3E0).copy(alpha = glow), 0.4f)
        }
    }
    // A pot with a lid and handles on the front-left burner…
    val pot = burners[2]
    line(pot.x - 4f, pot.y, pot.x + 4f, pot.y, Palette.steelDark, 0.8f)
    dot(pot, 3.1f, Palette.steelDark)
    dot(pot, 2.6f, Palette.steel)
    ring(pot.x, pot.y, 1.6f, Palette.steelMid, 0.3f)
    dot(pot, 0.6f, Palette.ink)
    if (busy) drawSmoke(Point(pot.x, pot.y - 2f), time * 1.4f)
    // …and a frying pan with an egg on the back-left.
    val pan = burners[0]
    line(pan.x - 2.8f, pan.y - 2.8f, pan.x - 6f, pan.y - 4.6f, Palette.ink, 0.8f)
    dot(pan, 2.9f, Color(0xFF242424))
    oval(pan.x + 0.3f, pan.y + 0.2f, 1.7f, 1.4f, Color.White)
    dot(pan.x + 0.3f, pan.y + 0.2f, 0.7f, Color(0xFFF2B32F))
}

/**
 * The fridge: a tall two-door fridge (freezer on top) with handles and a little status light, green
 * when it's fine, amber when it's worn, red and dark when it's broken. [struggling] makes it judder and
 * the light flicker, just before it gives up. The better models look the part: white enamel for the
 * sturdy one, black steel with a display for the best.
 */
internal fun Pen.drawFridge(condition: Int, broken: Boolean, struggling: Boolean, level: Int, time: Float) {
    val shake = if (struggling) sin(time * 40f) * 0.25f else 0f
    val f = Rect(SceneLayout.fridge.left + shake, SceneLayout.fridge.top, SceneLayout.fridge.right + shake, SceneLayout.fridge.bottom)
    val (body, door, handle) = when {
        level >= 3 -> Triple(Color(0xFF26292F), Color(0xFF353A42), Color(0xFF9AA3AD))
        level == 2 -> Triple(Color(0xFFE7E4DC), Color(0xFFF7F5EF), Color(0xFF9AA3AD))
        else -> Triple(Palette.steelMid, Palette.steel, Palette.steelDark)
    }
    oval(f.center.x, f.bottom + 0.6f, f.width / 2, 0.9f, Palette.shadow)
    box(f, body, radius = 1.2f)
    // Freezer door on top, fridge door below, with a gap between.
    val split = f.top + f.height * 0.36f
    box(f.left + 0.6f, f.top + 0.6f, f.width - 1.2f, split - f.top - 1f, door, radius = 0.8f)
    box(f.left + 0.6f, split + 0.4f, f.width - 1.2f, f.bottom - split - 1f, door, radius = 0.8f)
    // Long handles down the right-hand edge.
    box(f.right - 2.4f, f.top + 2f, 0.8f, split - f.top - 4f, handle, radius = 0.4f)
    box(f.right - 2.4f, split + 2f, 0.8f, (f.bottom - split) * 0.5f, handle, radius = 0.4f)
    // A magnet and a shopping note on the door, because every restaurant fridge has one.
    if (!broken) {
        box(f.left + 2f, split + 3f, 4.2f, 4.6f, Color(0xFFFFFCF2), radius = 0.2f)
        line(f.left + 2.6f, split + 4.6f, f.left + 5.4f, split + 4.6f, Color(0x55000000), 0.2f)
        line(f.left + 2.6f, split + 5.8f, f.left + 4.8f, split + 5.8f, Color(0x55000000), 0.2f)
        dot(f.left + 4.1f, split + 3f, 0.6f, Palette.serverRed)
    }
    if (level >= 3) box(f.left + 2f, f.top + 2f, 4f, 2f, Color(0xFF0E2F4A), radius = 0.3f)
    // Status light: green, amber when worn, flickering just before it goes, red when broken.
    val flicker = struggling && sin(time * 30f) > 0f
    val light = when {
        broken -> Palette.alert
        flicker -> Color(0xFF3A2A10)
        struggling || condition < com.recipefordisaster.domain.equipment.Fridge.WORN -> Color(0xFFE0A030)
        else -> Color(0xFF4CAF50)
    }
    dot(f.left + 7.6f, f.top + 2.6f, 0.75f, light)
    if (broken) {
        // Door hanging open a crack, a puddle where it's dripping, and no hum.
        line(f.left + 0.6f, split + 0.6f, f.left + 0.6f, f.bottom - 0.8f, Color(0xFF1A1A1A), 0.5f)
        oval(f.center.x, f.bottom + 1.2f, 3f, 0.8f, Color(0x998FC6EA))
        val drip = (time * 1.2f) % 1f
        dot(f.center.x - 1f, f.bottom - 0.5f + drip * 1.6f, 0.35f, Color(0xFF8FC6EA))
    }
}

/** Shelves of lidded, labelled jars plus flour sacks; [fullness] 0-1 decides how much is stocked. */
/**
 * The pantry shelves: one jar per ingredient, filled to how much is left,
 * so an empty pantry looks empty. [jars] is each jar's level (0-1).
 */
internal fun Pen.drawPantry(fullness: Float, jars: List<Float> = emptyList()) {
    val shelf = SceneLayout.pantry
    oval(shelf.center.x, shelf.bottom + 0.6f, shelf.width / 2, 1f, Palette.shadow)
    box(shelf, Palette.wood, radius = 0.8f)
    box(shelf.left + 0.6f, shelf.top + 0.6f, shelf.width - 1.2f, shelf.height - 1.2f, Color(0xFF6E4429), radius = 0.6f)
    val rows = 3
    val perRow = 4
    val levels = jars.ifEmpty { List(rows * perRow) { if (it < fullness * rows * perRow) 1f else 0f } }
    val jarColors = listOf(Color(0xFFE5B85C), Color(0xFF7FB069), Color(0xFFC0392B), Color(0xFFF2E9DC), Color(0xFFB5651D), Color(0xFF8E6C8A), Color(0xFFD9C27E), Color(0xFF6FA8C9))
    for (r in 0 until rows) {
        val rowTop = shelf.top + 1.5f + r * 7.8f
        box(shelf.left + 0.6f, rowTop + 6.2f, shelf.width - 1.2f, 0.9f, Palette.woodLight, radius = 0.2f)
        for (c in 0 until perRow) {
            val i = r * perRow + c
            val jx = shelf.left + 1.6f + c * 6.1f
            val level = levels.getOrNull(i)
            if (level != null) {
                box(jx, rowTop + 1.6f, 4.6f, 4.6f, Color(0x99FFFFFF), radius = 0.9f) // glass
                if (level > 0f) {
                    // Contents fill the jar from the bottom, as high as there's food left.
                    val h = 3.6f * level.coerceIn(0.15f, 1f)
                    box(jx + 0.5f, rowTop + 5.8f - h, 3.6f, h, jarColors[i % jarColors.size], radius = 0.6f)
                }
                if (level < 0.25f) dot(jx + 2.3f, rowTop + 4f, 0.7f, if (level <= 0f) Color(0xFFC0392B) else Color(0xFFE0A030)) // nearly or completely out
                box(jx - 0.1f, rowTop + 0.9f, 4.8f, 1.2f, Color(0xFF8C8C8C), radius = 0.3f) // lid
            } else if (jars.isEmpty()) {
                box(jx, rowTop + 1.6f, 4.6f, 4.6f, Color(0x33FFFFFF), radius = 0.9f)
                outline(Rect(jx, rowTop + 1.6f, jx + 4.6f, rowTop + 6.2f), Color(0x66FFFFFF), radius = 0.9f, width = 0.25f)
            }
        }
    }
    // Flour sacks slumped beside the shelves when there's stock.
    if (fullness > 0.3f) {
        oval(shelf.left - 3f, shelf.bottom + 2f, 3f, 2.4f, Color(0xFFE8DCC0))
        line(shelf.left - 4.4f, shelf.bottom + 0.6f, shelf.left - 1.6f, shelf.bottom + 0.6f, Color(0xFFA08F6B), 0.4f)
    }
}

// ---------------------------------------------------------------- the dining room

internal fun Pen.drawTables(layout: TableLayout = ServiceFloor.layout(ServiceFloor.TABLE_COUNT)) {
    layout.tables.forEach { at ->
        val table = at.toPoint()
        with(around(table, layout.scale)) {
            drawChair(Point(table.x - 12f, table.y), facingRight = true)
            drawChair(Point(table.x + 12f, table.y), facingRight = false)
            drawTable(table)
        }
    }
}

/** A wooden chair from above: seat, curved backrest on the far side, spindles, and a cushion. */
private fun Pen.drawChair(at: Point, facingRight: Boolean) {
    val back = if (facingRight) -1f else 1f // the backrest is on the side away from the table
    oval(at.x, at.y + 3.6f, 3.8f, 1f, Palette.shadow)
    // Legs peeking out at the corners.
    for (dx in listOf(-2.8f, 2.8f)) for (dy in listOf(-2.8f, 2.8f)) dot(at.x + dx, at.y + dy, 0.55f, Palette.chairDark)
    box(at.x - 3.2f, at.y - 3.2f, 6.4f, 6.4f, Palette.chairSeat, radius = 1.4f)
    box(at.x - 2.4f, at.y - 2.4f, 4.8f, 4.8f, Palette.cushion, radius = 1.2f)
    line(at.x - 1.6f, at.y, at.x + 1.6f, at.y, Color(0x33000000), 0.2f) // cushion seam
    // Backrest: a curved top rail with spindles down to the seat.
    val railX = at.x + back * 4.2f
    shape(Palette.chairDark, stroke = 1.1f) {
        moveTo(railX - back * 0.6f, at.y - 3.8f)
        quadTo(railX + back * 0.8f, at.y, railX - back * 0.6f, at.y + 3.8f)
    }
    for (dy in listOf(-2f, 0f, 2f)) line(at.x + back * 3.2f, at.y + dy, railX, at.y + dy, Palette.chairDark, 0.35f)
}

/** A round table with a gingham cloth, two place settings and a small vase. */
private fun Pen.drawTable(table: Point) {
    oval(table.x, table.y + 1.6f, 8.4f, 7.6f, Palette.shadow)
    dot(table, 7.8f, Color(0xFFE9E3D6))
    // Scalloped hem.
    for (k in 0 until 16) {
        val a = k / 16f * 2f * PI.toFloat()
        dot(table.x + cos(a) * 7.6f, table.y + sin(a) * 7.6f, 0.9f, Color(0xFFE9E3D6))
    }
    dot(table, 7.2f, Palette.cloth)
    // Gingham: a few red bands each way, kept inside the cloth.
    for (k in -2..2) {
        val off = k * 2.6f
        val half = sqrt((7.2f * 7.2f - off * off).coerceAtLeast(0f)) - 0.4f
        line(table.x + off, table.y - half, table.x + off, table.y + half, Palette.clothCheck.copy(alpha = 0.25f), 1.1f)
        line(table.x - half, table.y + off, table.x + half, table.y + off, Palette.clothCheck.copy(alpha = 0.25f), 1.1f)
    }
    // Place settings on the chair sides: plate, fork, knife.
    for (side in listOf(-1f, 1f)) {
        val px = table.x + side * 4.2f
        dot(px, table.y, 1.9f, Color.White)
        ring(px, table.y, 1.9f, Color(0x33000000), 0.2f)
        ring(px, table.y, 1.2f, Color(0x22000000), 0.15f)
        line(px - 0.4f, table.y - 2.8f, px - 0.4f, table.y - 2.2f, Palette.steelDark, 0.25f)
        line(px + 0.4f, table.y + 2.2f, px + 0.4f, table.y + 2.9f, Palette.steelDark, 0.25f)
    }
    // A little vase with a flower in the middle.
    dot(table.x, table.y, 1.2f, Color(0xFF7FA7C9))
    dot(table.x, table.y - 0.6f, 0.9f, Color(0xFFE85D75))
    dot(table.x, table.y - 0.6f, 0.35f, Color(0xFFF7D560))
}

internal fun Pen.drawMenuBoard(text: TextMeasurer, label: String) {
    val board = SceneLayout.menuBoard
    // A-frame legs.
    line(board.left + 1f, board.bottom, board.left - 0.6f, board.bottom + 1.2f, Palette.woodDark, 0.6f)
    line(board.right - 1f, board.bottom, board.right + 0.6f, board.bottom + 1.2f, Palette.woodDark, 0.6f)
    box(Rect(board.left - 1f, board.top - 1f, board.right + 1f, board.bottom + 1f), Palette.wood, radius = 1f)
    box(board, Color(0xFF2E3A33), radius = 0.6f)
    centeredText(text, label, Point(board.center.x, board.top + 3f), size = 3f, color = Color(0xFFF1EEE4), bold = true)
    for (i in 0..1) line(board.left + 2f, board.top + 6.2f + i * 1.6f, board.right - 2f - i * 2f, board.top + 6.2f + i * 1.6f, Color(0x99F1EEE4), 0.3f)
}

internal fun Pen.drawMopBucket(withMop: Boolean = true) {
    val b = SceneLayout.mopBucket
    oval(b.center.x, b.bottom + 0.4f, 4.4f, 1f, Palette.shadow)
    // Bucket with a rim, a handle and soapy water.
    shape(Color(0xFFF2C230)) {
        moveTo(b.left + 0.6f, b.top + 4f)
        lineTo(b.right - 0.6f, b.top + 4f)
        lineTo(b.right - 1.6f, b.bottom)
        lineTo(b.left + 1.6f, b.bottom)
        close()
    }
    oval(b.center.x, b.top + 4f, b.width / 2 - 0.6f, 1.2f, Color(0xFFD9A821))
    oval(b.center.x, b.top + 4.1f, b.width / 2 - 1.4f, 0.8f, Color(0xFF8FB6D9))
    dot(b.center.x - 1.2f, b.top + 3.8f, 0.4f, Color.White)
    dot(b.center.x + 0.8f, b.top + 4.2f, 0.3f, Color.White)
    shape(Palette.steelDark, stroke = 0.3f) {
        moveTo(b.left + 1f, b.top + 4f)
        quadTo(b.center.x, b.top, b.right - 1f, b.top + 4f)
    }
    // Mop leaning in it: wooden handle and a stringy head.
    if (!withMop) return
    line(b.center.x + 0.6f, b.top + 4.6f, b.right + 3f, b.top - 6f, Palette.woodLight, 0.8f)
    for (k in -2..2) line(b.center.x + 0.6f, b.top + 4.4f, b.center.x + 0.6f + k * 0.7f, b.top + 6.4f, Color(0xFFE8E2D4), 0.5f)
}

internal fun Pen.drawHiringSign(text: TextMeasurer, label: String) {
    val s = SceneLayout.hiringSign
    line(s.left + 3f, s.bottom, s.left + 1.6f, s.bottom + 1.6f, Palette.woodDark, 0.6f)
    line(s.right - 3f, s.bottom, s.right - 1.6f, s.bottom + 1.6f, Palette.woodDark, 0.6f)
    box(s, Color(0xFFF7F1E3), radius = 0.8f)
    outline(s, Palette.woodDark, radius = 0.8f, width = 0.5f)
    dot(s.center.x, s.top + 0.6f, 0.5f, Palette.alert) // drawing pin
    // "COOK WANTED" etc. — two short lines fit the little sign better than one long one.
    val words = label.split(' ')
    if (words.size >= 2) {
        centeredText(text, words.dropLast(1).joinToString(" "), Point(s.center.x, s.center.y - 1.8f), size = 2.5f, color = Palette.serverRed, bold = true)
        centeredText(text, words.last(), Point(s.center.x, s.center.y + 2f), size = 2.5f, color = Palette.serverRed, bold = true)
    } else {
        centeredText(text, label, s.center, size = 2.5f, color = Palette.serverRed, bold = true)
    }
}

// ---------------------------------------------------------------- people

internal enum class Outfit { CHEF, SERVER, WASHER, HOST, GUEST }

internal fun outfitFor(role: Role) = when (role) {
    Role.COOK -> Outfit.CHEF
    Role.SERVER, Role.MANAGER, Role.BUSSER -> Outfit.SERVER
    Role.DISHWASHER -> Outfit.WASHER
    Role.HOST -> Outfit.HOST
}

/**
 * A little person seen from slightly above: feet (stepping when [walkPhase]
 * is given), a body in their outfit with arms and hands, and a head with
 * hair, eyes, cheeks and a mouth that follows [mood]. [variant] picks a
 * guest's hairstyle, hair colour and accessories so a room full of guests
 * isn't a room of twins. [sweat] adds a bead of sweat, [angry] red cheeks,
 * cross brows and steam, [backTurned] shows the back of the head (someone
 * leaving in disgust), [slumped] someone exhausted.
 */
internal fun Pen.drawPerson(
    at: Point,
    outfit: Outfit,
    mood: Int,
    bodyColor: Color = Palette.guestColors[0],
    bob: Float = 0f,
    sweat: Boolean = false,
    angry: Boolean = false,
    backTurned: Boolean = false,
    slumped: Boolean = false,
    variant: Int = 0,
    walkPhase: Float? = null,
    /** Apron (servers) or overalls (dishwashers) colour; null for the usual red or blue. */
    apron: Color? = null,
    /** Leave the arms off: the caller draws them doing something (holding a plate to wash, say). */
    armsBusy: Boolean = false,
) {
    val y = at.y - bob + if (slumped) 0.8f else 0f
    oval(at.x, at.y + 3.6f, 4.2f, 1.3f, Palette.shadow)

    // Feet, stepping in turn while walking.
    val step = walkPhase?.let { sin(it * 2f * PI.toFloat()) } ?: 0f
    val shoe = if (outfit == Outfit.CHEF) Color(0xFF2B2B2B) else Color(0xFF3A2A20)
    oval(at.x - 1.6f, at.y + 3.2f - step, 1.1f, 0.8f, shoe)
    oval(at.x + 1.6f, at.y + 3.2f + step, 1.1f, 0.8f, shoe)

    // Body colours.
    val top = when (outfit) {
        Outfit.CHEF -> Palette.chefWhite
        Outfit.SERVER -> Color(0xFFFAFAF7)
        Outfit.WASHER -> Palette.washerBlue
        Outfit.HOST -> Color(0xFF2C3A50) // a dark suit
        Outfit.GUEST -> bodyColor
    }
    val shade = top.copy(red = top.red * 0.82f, green = top.green * 0.82f, blue = top.blue * 0.82f)
    // Waiting staff have black sleeves to match the waistcoat; everyone else's match their top.
    val sleeve = if (outfit == Outfit.SERVER) Color(0xFF2B2B2B) else shade
    // Arms and hands first, so the body overlaps the tops of the sleeves.
    val armSwing = step * 0.6f
    val hand = if (outfit == Outfit.WASHER) Color(0xFFF2C230) else Palette.face
    if (!armsBusy) {
        box(at.x - 5.2f, y - 0.4f + armSwing, 1.8f, 4.4f, sleeve, radius = 0.9f)
        box(at.x + 3.4f, y - 0.4f - armSwing, 1.8f, 4.4f, sleeve, radius = 0.9f)
        dot(at.x - 4.3f, y + 4.2f + armSwing, 0.9f, hand)
        dot(at.x + 4.3f, y + 4.2f - armSwing, 0.9f, hand)
    }
    box(at.x - 4f, y - 1.2f, 8f, 6.4f, top, radius = 2.4f)
    box(at.x - 4f, y + 2.6f, 8f, 2.6f, shade.copy(alpha = 0.35f), radius = 1.6f)

    when (outfit) {
        Outfit.CHEF -> {
            // Double-breasted jacket: two rows of buttons, and a red neckerchief.
            for (k in 0..2) {
                dot(at.x - 1.3f, y + 0.6f + k * 1.4f, 0.32f, Color(0xFF9A9A9A))
                dot(at.x + 1.3f, y + 0.6f + k * 1.4f, 0.32f, Color(0xFF9A9A9A))
            }
            shape(Palette.serverRed) {
                moveTo(at.x - 2f, y - 1.2f)
                lineTo(at.x + 2f, y - 1.2f)
                lineTo(at.x, y + 0.8f)
                close()
            }
        }
        Outfit.SERVER -> {
            // Waistcoat, apron and a bow tie in the same colour.
            box(at.x - 4f, y - 1.2f, 2.4f, 5.6f, Color(0xFF2B2B2B), radius = 1.2f)
            box(at.x + 1.6f, y - 1.2f, 2.4f, 5.6f, Color(0xFF2B2B2B), radius = 1.2f)
            box(at.x - 2.6f, y + 1.8f, 5.2f, 3.4f, apron ?: Palette.serverRed, radius = 0.6f)
            line(at.x - 2.6f, y + 1.9f, at.x + 2.6f, y + 1.9f, Color(0x55000000), 0.3f)
            shape(apron ?: Palette.ink) {
                moveTo(at.x, y - 0.6f); lineTo(at.x - 1.3f, y - 1.3f); lineTo(at.x - 1.3f, y + 0.1f); close()
                moveTo(at.x, y - 0.6f); lineTo(at.x + 1.3f, y - 1.3f); lineTo(at.x + 1.3f, y + 0.1f); close()
            }
        }
        Outfit.WASHER -> {
            // Overalls bib with two buttons.
            box(at.x - 2f, y - 0.6f, 4f, 3f, apron ?: Color(0xFF2F6188), radius = 0.5f)
            dot(at.x - 1.3f, y - 0.2f, 0.3f, Palette.gold)
            dot(at.x + 1.3f, y - 0.2f, 0.3f, Palette.gold)
        }
        Outfit.HOST -> {
            // A smart jacket with lapels, a white shirt and a tie.
            shape(Color(0xFFF7F4EC)) {
                moveTo(at.x - 1.8f, y - 1.2f); lineTo(at.x, y + 2.6f); lineTo(at.x + 1.8f, y - 1.2f); close()
            }
            shape(Color(0xFF1F2A3A)) {
                moveTo(at.x - 1.8f, y - 1.2f); lineTo(at.x - 0.4f, y + 2.6f); lineTo(at.x - 3f, y + 1f); close()
                moveTo(at.x + 1.8f, y - 1.2f); lineTo(at.x + 0.4f, y + 2.6f); lineTo(at.x + 3f, y + 1f); close()
            }
            box(at.x - 0.45f, y - 0.8f, 0.9f, 3.2f, apron ?: Palette.serverRed, radius = 0.3f)
        }
        Outfit.GUEST -> {
            // A collar, and on some guests a necklace or a tie.
            shape(Color(0xFFFAFAF7)) {
                moveTo(at.x - 1.8f, y - 1.2f); lineTo(at.x, y + 0.8f); lineTo(at.x + 1.8f, y - 1.2f); close()
            }
            when (variant % 3) {
                0 -> line(at.x - 2.4f, y - 0.6f, at.x + 2.4f, y - 0.6f, Color(0xFFF2D06B), 0.4f)
                1 -> box(at.x - 0.5f, y - 0.6f, 1f, 3.4f, Color(0xFF2B2B2B), radius = 0.3f)
                else -> {}
            }
        }
    }

    // Head.
    val hx = at.x
    val hy = y - 4f + if (slumped) 1f else 0f
    val hair = Palette.hairColors[variant % Palette.hairColors.size]
    val style = if (outfit == Outfit.GUEST) variant % 5 else 0
    if (style == 2) box(hx - 3.8f, hy - 2.4f, 7.6f, 6.2f, hair, radius = 2.4f) // long hair behind the head
    dot(hx, hy, 3.4f, if (angry) Color(0xFFF2B19C) else Palette.face)
    if (backTurned) {
        dot(hx, hy, 3.4f, hair)
        if (style == 3) dot(hx, hy - 3f, 1.4f, hair)
    } else {
        // Hair on top, in one of a few styles (staff keep theirs under a hat or cap).
        if (outfit == Outfit.GUEST || outfit == Outfit.SERVER) {
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
        // Face: eyes with a highlight, rosy cheeks, mouth.
        val eyeY = hy + 0.2f
        for (side in listOf(-1f, 1f)) {
            dot(hx + side * 1.25f, eyeY, 0.5f, Palette.ink)
            dot(hx + side * 1.25f + 0.18f, eyeY - 0.18f, 0.16f, Color.White)
            dot(hx + side * 2.1f, hy + 1.3f, 0.65f, if (angry) Color(0x99E04A3A) else Palette.blush)
        }
        val curve = when {
            mood >= 66 -> 1.1f
            mood >= 33 -> 0f
            else -> -0.9f
        }
        shape(Palette.ink, stroke = 0.35f) {
            moveTo(hx - 1.2f, hy + 1.8f)
            quadTo(hx, hy + 1.8f + curve, hx + 1.2f, hy + 1.8f)
        }
        if (angry) {
            line(hx - 2f, eyeY - 1.3f, hx - 0.5f, eyeY - 0.7f, Palette.ink, 0.35f)
            line(hx + 2f, eyeY - 1.3f, hx + 0.5f, eyeY - 0.7f, Palette.ink, 0.35f)
        }
    }

    if (outfit == Outfit.CHEF) {
        // Toque: a tall pleated hat with a band.
        box(hx - 2.6f, hy - 4.6f, 5.2f, 2f, Palette.chefWhite, radius = 0.4f)
        dot(hx - 1.6f, hy - 5.6f, 1.9f, Palette.chefWhite)
        dot(hx + 1.6f, hy - 5.6f, 1.9f, Palette.chefWhite)
        dot(hx, hy - 6.4f, 2.1f, Palette.chefWhite)
        for (k in -1..1) line(hx + k * 1.2f, hy - 6.6f, hx + k * 1.2f, hy - 3.2f, Color(0xFFDADAD5), 0.25f)
        line(hx - 2.6f, hy - 2.8f, hx + 2.6f, hy - 2.8f, Color(0xFFDADAD5), 0.35f)
    }
    if (outfit == Outfit.WASHER) {
        box(hx - 3.4f, hy - 3.6f, 6.8f, 2.2f, Color(0xFF2F6188), radius = 1.1f) // cap
        box(hx - 1f, hy - 2f, 5f, 0.9f, Color(0xFF2F6188), radius = 0.45f) // brim
    }
    if (sweat) oval(hx + 3.4f, hy - 1.4f, 0.6f, 0.9f, Color(0xFF5FA8D3))
    if (angry) {
        for (side in listOf(-1f, 1f)) line(hx + side * 2.6f, hy - 3.8f, hx + side * 3.6f, hy - 5.6f, Palette.alert, 0.5f)
    }
}

/** A served plate: food and a garnish. */
internal fun Pen.drawPlate(at: Point) {
    dot(at, 2.3f, Color.White)
    ring(at.x, at.y, 2.3f, Color(0x33000000), 0.2f)
    ring(at.x, at.y, 1.6f, Color(0x22000000), 0.15f)
    oval(at.x - 0.3f, at.y, 1.3f, 0.9f, Color(0xFFC8762F))
    oval(at.x - 0.3f, at.y - 0.3f, 1.1f, 0.4f, Color(0xFFE09A4F))
    dot(at.x + 0.9f, at.y + 0.5f, 0.5f, Color(0xFF6DA34D))
    dot(at.x + 1.2f, at.y - 0.3f, 0.35f, Color(0xFFD94B3B))
}

internal fun Pen.drawCoins(text: TextMeasurer, at: Point, progress: Float, amount: Long) {
    val alpha = (1f - (progress - 0.6f) / 0.4f).coerceIn(0f, 1f) // stays solid, then fades at the end
    val y = at.y - 5f - progress * 8f
    // Pops in big, then settles.
    val pop = if (progress < 0.25f) 1f + 0.5f * kotlin.math.sin(progress / 0.25f * PI.toFloat()) else 1f
    dot(Point(at.x, y), 2f * pop, Color(0xFFB8860B).copy(alpha = alpha))
    dot(Point(at.x, y), 1.6f * pop, Palette.gold.copy(alpha = alpha))
    dot(Point(at.x - 0.4f, y - 0.4f), 0.6f * pop, Color(0xFFFFF0A8).copy(alpha = alpha))
    // Sparkles bursting off it.
    if (progress < 0.5f) {
        for (k in 0..5) {
            val a = k * PI.toFloat() / 3f
            val r = 2.5f + progress * 9f
            dot(Point(at.x + kotlin.math.cos(a) * r, y + kotlin.math.sin(a) * r), 0.45f * (1f - progress * 2f), Color(0xFFFFE27A))
        }
    }
    centeredText(text, "+$amount", Point(at.x + 6f, y), size = 3.2f * pop, color = Color(0xFF8A6A0A).copy(alpha = alpha), bold = true)
}

/** A red "!" that pulses — "this needs you". */
internal fun Pen.drawAlert(text: TextMeasurer, at: Point, time: Float) {
    val pulse = 1f + 0.12f * sin(time * 2f * PI.toFloat() * 1.2f)
    dot(at, 3.1f * pulse, Color.White)
    dot(at, 2.6f * pulse, Palette.alert)
    centeredText(text, "!", at, size = 4f, color = Color.White, bold = true)
}

internal fun Pen.centeredText(text: TextMeasurer, value: String, at: Point, size: Float, color: Color, bold: Boolean = false) {
    val layout = text.measure(
        value,
        TextStyle(fontSize = (u(size) / density / fontScale).sp, fontFamily = FontFamily.Serif, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color),
    )
    val c = p(at)
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
}

package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.sin

/**
 * Everything drawn in the restaurant scene, as plain shapes. [Pen] maps
 * scene units (see [SceneLayout]) to pixels for the current screen, so
 * every function here works in the same units as the floor plan.
 */
internal class Pen(private val scope: DrawScope, val unit: Float, private val origin: Offset) : DrawScope by scope {
    fun p(point: Point) = Offset(origin.x + point.x * unit, origin.y + point.y * unit)
    fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    fun u(units: Float) = units * unit

    fun box(rect: Rect, color: Color, radius: Float = 1.5f) =
        drawRoundRect(color, topLeft = p(rect.left, rect.top), size = Size(u(rect.width), u(rect.height)), cornerRadius = CornerRadius(u(radius)))

    fun outline(rect: Rect, color: Color, radius: Float = 1.5f, width: Float = 0.6f) =
        drawRoundRect(color, topLeft = p(rect.left, rect.top), size = Size(u(rect.width), u(rect.height)), cornerRadius = CornerRadius(u(radius)), style = Stroke(u(width)))

    fun dot(center: Point, radius: Float, color: Color) = drawCircle(color, radius = u(radius), center = p(center))
}

internal object Palette {
    val wall = Color(0xFFE9DCC9)
    val kitchenTileA = Color(0xFFD9DDDC)
    val kitchenTileB = Color(0xFFCBD0CF)
    val floorA = Color(0xFFF3E6CF)
    val floorB = Color(0xFFE2CFAE)
    val wood = Color(0xFF8A5A35)
    val woodLight = Color(0xFFB07A4C)
    val woodDark = Color(0xFF5A3620)
    val steel = Color(0xFF9AA3A6)
    val steelDark = Color(0xFF5E676A)
    val ovenBody = Color(0xFF3D3B3A)
    val cloth = Color(0xFFFFFFFF)
    val clothCheck = Color(0xFFD94B3B)
    val chair = Color(0xFF6E4429)
    val ink = Color(0xFF2B2622)
    val face = Color(0xFFF7E3C4)
    val chefWhite = Color(0xFFFDFDFB)
    val serverRed = Color(0xFFC0392B)
    val washerBlue = Color(0xFF3B78A8)
    val smoke = Color(0xFF8E8E8E)
    val flame = Color(0xFFF08A24)
    val dirt = Color(0x668A6A3A)
    val rat = Color(0xFF6B625B)
    val gold = Color(0xFFE9B527)
    val alert = Color(0xFFD7322B)
    val happy = Color(0xFF3E8E41)
    val unhappy = Color(0xFFD7322B)
    val so_so = Color(0xFFD69A1E)
    val guestColors = listOf(
        Color(0xFF6C8EBF), Color(0xFF9C6FB6), Color(0xFF4FA38A), Color(0xFFE08E45),
        Color(0xFFCF6F8E), Color(0xFF8C9A3B), Color(0xFF5B6FB0), Color(0xFFB5764E),
    )
}

// ---------------------------------------------------------------- the room

internal fun Pen.drawRoom(cleanliness: Int, doorOpen: Boolean, time: Float) {
    drawRect(Palette.wall, topLeft = p(0f, 0f), size = Size(u(SceneLayout.WIDTH), u(SceneLayout.HEIGHT)))

    // Kitchen tiles and dining-room checkerboard.
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
    val big = 10f
    y = 44f
    while (y < 140f) {
        var x = 0f
        while (x < SceneLayout.WIDTH) {
            val even = ((x / big).toInt() + (y / big).toInt()) % 2 == 0
            drawRect(if (even) Palette.floorA else Palette.floorB, topLeft = p(x, y), size = Size(u(big) + 1, u(big) + 1))
            x += big
        }
        y += big
    }

    // Dirt: more stains the dirtier it gets, always in the same places so they don't jump around.
    val stains = ((100 - cleanliness) / 12).coerceIn(0, STAIN_SPOTS.size)
    STAIN_SPOTS.take(stains).forEachIndexed { i, spot ->
        drawOval(Palette.dirt, topLeft = p(spot.x, spot.y), size = Size(u(5f + i % 3), u(3f + i % 2)))
    }
    if (cleanliness < 45) drawRat(Point(88f, 132f), time)

    // The pass counter.
    box(SceneLayout.counter, Palette.wood, radius = 1f)
    box(Rect(SceneLayout.counter.left, SceneLayout.counter.top, SceneLayout.counter.right, SceneLayout.counter.top + 2f), Palette.woodLight, radius = 1f)

    // Front wall and door.
    drawRect(Palette.woodDark, topLeft = p(0f, 140f), size = Size(u(SceneLayout.WIDTH), u(10f)))
    val door = SceneLayout.door
    if (doorOpen) {
        drawRect(Palette.floorA, topLeft = p(door.left, door.top), size = Size(u(door.width), u(door.height)))
    } else {
        box(door, Palette.woodLight, radius = 0.5f)
        dot(Point(door.right - 3f, door.center.y), 0.8f, Palette.gold)
    }
}

private val STAIN_SPOTS = listOf(
    Point(12f, 78f), Point(48f, 104f), Point(84f, 80f), Point(56f, 130f),
    Point(18f, 106f), Point(66f, 52f), Point(36f, 134f), Point(90f, 110f),
)

internal fun Pen.drawRat(at: Point, time: Float) {
    val wiggle = sin(time * 9f) * 0.8f
    drawOval(Palette.rat, topLeft = p(at.x - 3f, at.y - 1.6f), size = Size(u(6f), u(3.2f)))
    dot(Point(at.x - 3.4f, at.y), 1.3f, Palette.rat)
    dot(Point(at.x - 4f, at.y - 0.4f), 0.35f, Palette.ink)
    val tail = Path().apply {
        moveTo(p(at.x + 3f, at.y).x, p(at.x + 3f, at.y).y)
        quadraticTo(p(at.x + 5f, at.y - 2f + wiggle).x, p(at.x + 5f, at.y - 2f + wiggle).y, p(at.x + 7f, at.y + wiggle).x, p(at.x + 7f, at.y + wiggle).y)
    }
    drawPath(tail, Palette.rat, style = Stroke(u(0.5f)))
}

// ---------------------------------------------------------------- the kitchen

internal fun Pen.drawOven(condition: Int, time: Float, onFire: Boolean) {
    val oven = SceneLayout.oven
    box(oven, Palette.ovenBody)
    box(Rect(oven.left + 3f, oven.top + 6f, oven.right - 3f, oven.bottom - 3f), if (condition <= 0) Color(0xFF1E1D1C) else Color(0xFF6B4A2E), radius = 1f)
    if (condition in 1..34) dot(Point(oven.center.x, oven.center.y + 2f), 2.5f, Palette.flame.copy(alpha = 0.5f))
    // Knobs.
    for (i in 0..2) dot(Point(oven.left + 5f + i * 5f, oven.top + 3f), 0.9f, Palette.steel)
    if (condition <= 0 || onFire) drawSmoke(Point(oven.center.x, oven.top), time)
    if (onFire) drawFlames(Point(oven.center.x, oven.top + 4f), time)
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
    val stove = SceneLayout.stove
    box(stove, Palette.steel)
    val burners = listOf(Point(stove.left + 5f, stove.top + 5f), Point(stove.right - 5f, stove.top + 5f), Point(stove.left + 5f, stove.bottom - 5f), Point(stove.right - 5f, stove.bottom - 5f))
    burners.forEachIndexed { i, b ->
        drawCircle(Palette.steelDark, radius = u(3.2f), center = p(b), style = Stroke(u(0.8f)))
        if (busy && i % 2 == 0) {
            val glow = 0.6f + 0.4f * sin(time * 10f + i)
            drawCircle(Palette.flame.copy(alpha = glow), radius = u(2.6f), center = p(b), style = Stroke(u(0.6f)))
        }
    }
    // A pot on the front burner.
    dot(burners[2], 2.8f, Palette.steelDark)
    dot(burners[2], 2.0f, Color(0xFFB5482E))
    if (busy) drawSmoke(burners[2], time * 1.4f)
}

internal fun Pen.drawSink() {
    val sink = SceneLayout.sink
    box(sink, Palette.steel)
    box(Rect(sink.left + 1.5f, sink.top + 2f, sink.right - 1.5f, sink.bottom - 1.5f), Palette.steelDark, radius = 1f)
}

/** Shelves of jars; [fullness] 0-1 decides how many jars are filled. */
internal fun Pen.drawPantry(fullness: Float) {
    val shelf = SceneLayout.pantry
    box(shelf, Palette.wood, radius = 0.8f)
    val rows = 3
    val perRow = 4
    val total = rows * perRow
    val filled = (fullness.coerceIn(0f, 1f) * total).toInt()
    val jarColors = listOf(Color(0xFFE5B85C), Color(0xFF7FB069), Color(0xFFC0392B), Color(0xFFF2E9DC))
    for (r in 0 until rows) {
        val rowTop = shelf.top + 1.5f + r * 7.8f
        drawRect(Palette.woodDark, topLeft = p(shelf.left, rowTop + 6.2f), size = Size(u(shelf.width), u(0.8f)))
        for (c in 0 until perRow) {
            val i = r * perRow + c
            val jar = Rect(shelf.left + 1.5f + c * 6.2f, rowTop + 1f, shelf.left + 6f + c * 6.2f, rowTop + 6.2f)
            if (i < filled) {
                box(jar, jarColors[i % jarColors.size], radius = 0.8f)
            } else {
                outline(jar, Color(0x66FFFFFF), radius = 0.8f, width = 0.4f)
            }
        }
    }
}

// ---------------------------------------------------------------- the dining room

internal fun Pen.drawTables() {
    SceneLayout.seats.forEach { seat -> box(Rect(seat.x - 3.4f, seat.y - 3.4f, seat.x + 3.4f, seat.y + 3.4f), Palette.chair, radius = 1.2f) }
    SceneLayout.tables.forEach { table ->
        dot(table, 7.6f, Palette.woodDark)
        dot(table, 7f, Palette.cloth)
        drawCircle(Palette.clothCheck.copy(alpha = 0.35f), radius = u(5.2f), center = p(table), style = Stroke(u(0.8f)))
        dot(Point(table.x, table.y), 1f, Palette.clothCheck)
    }
}

internal fun Pen.drawMenuBoard(text: TextMeasurer, label: String) {
    val board = SceneLayout.menuBoard
    box(Rect(board.left - 1f, board.top - 1f, board.right + 1f, board.bottom + 1f), Palette.wood, radius = 1f)
    box(board, Color(0xFF2E3A33), radius = 0.6f)
    centeredText(text, label, board.center, size = 3.4f, color = Color(0xFFF1EEE4), bold = true)
}

internal fun Pen.drawMopBucket() {
    val b = SceneLayout.mopBucket
    box(Rect(b.left + 1f, b.top + 4f, b.right - 1f, b.bottom), Color(0xFF4F86C6), radius = 1f)
    drawLine(Palette.woodLight, p(b.center.x, b.top + 6f), p(b.right + 2f, b.top - 4f), strokeWidth = u(0.9f))
    dot(Point(b.right + 2.4f, b.top - 4.6f), 1.6f, Color(0xFFDDD6C8))
}

internal fun Pen.drawHiringSign(text: TextMeasurer, label: String) {
    val s = SceneLayout.hiringSign
    box(s, Color(0xFFF7F1E3), radius = 0.8f)
    outline(s, Palette.woodDark, radius = 0.8f, width = 0.5f)
    // "COOK WANTED" etc. — two short lines fit the little sign better than one long one.
    val words = label.split(' ')
    if (words.size >= 2) {
        centeredText(text, words.dropLast(1).joinToString(" "), Point(s.center.x, s.center.y - 2f), size = 2.6f, color = Palette.serverRed, bold = true)
        centeredText(text, words.last(), Point(s.center.x, s.center.y + 2f), size = 2.6f, color = Palette.serverRed, bold = true)
    } else {
        centeredText(text, label, s.center, size = 2.6f, color = Palette.serverRed, bold = true)
    }
}

// ---------------------------------------------------------------- people

internal enum class Outfit { CHEF, SERVER, WASHER, GUEST }

internal fun outfitFor(role: Role) = when (role) {
    Role.COOK -> Outfit.CHEF
    Role.SERVER, Role.MANAGER -> Outfit.SERVER
    Role.DISHWASHER -> Outfit.WASHER
}

/**
 * A little person seen from slightly above: a body, a head with a face
 * that follows [mood], and an outfit. [bob] lifts them a touch (walking,
 * chopping, eating); [sweat] adds a bead of sweat; [angry] adds steam;
 * [backTurned] hides the face (someone leaving in disgust).
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
) {
    val y = at.y - bob - if (slumped) -1f else 0f
    val body = when (outfit) {
        Outfit.CHEF -> Palette.chefWhite
        Outfit.SERVER -> Color(0xFF2B2B2B)
        Outfit.WASHER -> Palette.washerBlue
        Outfit.GUEST -> bodyColor
    }
    // Shadow, body.
    drawOval(Color(0x33000000), topLeft = p(at.x - 4f, at.y + 2.2f), size = Size(u(8f), u(2.6f)))
    drawOval(body, topLeft = p(at.x - 4.2f, y - 1f), size = Size(u(8.4f), u(6.4f)))
    if (outfit == Outfit.CHEF) drawOval(Color(0xFFE2E2DE), topLeft = p(at.x - 4.2f, y - 1f), size = Size(u(8.4f), u(6.4f)), style = Stroke(u(0.4f)))
    if (outfit == Outfit.SERVER) drawRect(Palette.serverRed, topLeft = p(at.x - 2.4f, y + 1f), size = Size(u(4.8f), u(3.6f)))

    // Head.
    val head = Point(at.x, y - 3.6f + if (slumped) 1.2f else 0f)
    val faceColor = if (angry) Color(0xFFF2A28C) else Palette.face
    dot(head, 3.4f, faceColor)
    if (backTurned) {
        dot(head, 3.4f, Color(0xFF5A4232))
    } else {
        val eyeY = head.y - 0.6f
        dot(Point(head.x - 1.2f, eyeY), 0.42f, Palette.ink)
        dot(Point(head.x + 1.2f, eyeY), 0.42f, Palette.ink)
        val curve = when {
            mood >= 66 -> 1.1f
            mood >= 33 -> 0f
            else -> -0.9f
        }
        val mouthY = head.y + 1.2f
        val mouth = Path().apply {
            moveTo(p(head.x - 1.4f, mouthY).x, p(head.x - 1.4f, mouthY).y)
            quadraticTo(p(head.x, mouthY + curve).x, p(head.x, mouthY + curve).y, p(head.x + 1.4f, mouthY).x, p(head.x + 1.4f, mouthY).y)
        }
        drawPath(mouth, Palette.ink, style = Stroke(u(0.35f)))
        if (angry) {
            drawLine(Palette.ink, p(head.x - 2f, eyeY - 1.4f), p(head.x - 0.5f, eyeY - 0.8f), strokeWidth = u(0.35f))
            drawLine(Palette.ink, p(head.x + 2f, eyeY - 1.4f), p(head.x + 0.5f, eyeY - 0.8f), strokeWidth = u(0.35f))
        }
    }
    if (outfit == Outfit.CHEF) {
        dot(Point(head.x, head.y - 3.6f), 2.6f, Palette.chefWhite)
        dot(Point(head.x - 1.6f, head.y - 3.2f), 1.8f, Palette.chefWhite)
        dot(Point(head.x + 1.6f, head.y - 3.2f), 1.8f, Palette.chefWhite)
    }
    if (sweat) {
        val drop = Point(head.x + 3.2f, head.y - 1.6f)
        drawOval(Color(0xFF5FA8D3), topLeft = p(drop.x - 0.6f, drop.y - 0.9f), size = Size(u(1.2f), u(1.8f)))
    }
    if (angry) {
        for (i in -1..1 step 2) {
            drawLine(Palette.alert, p(head.x + i * 2.6f, head.y - 3.6f), p(head.x + i * 3.6f, head.y - 5.4f), strokeWidth = u(0.5f))
        }
    }
}

internal fun Pen.drawPlate(at: Point) {
    dot(at, 2.2f, Color(0xFFFFFFFF))
    drawCircle(Color(0xFFCCCCCC), radius = u(2.2f), center = p(at), style = Stroke(u(0.3f)))
    dot(Point(at.x - 0.4f, at.y - 0.2f), 1.1f, Color(0xFFC8762F))
    dot(Point(at.x + 0.8f, at.y + 0.4f), 0.6f, Color(0xFF6DA34D))
}

internal fun Pen.drawCoins(text: TextMeasurer, at: Point, progress: Float, amount: Long) {
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val y = at.y - 5f - progress * 8f
    dot(Point(at.x, y), 2f, Palette.gold.copy(alpha = alpha))
    dot(Point(at.x, y), 1.2f, Color(0xFFF7D560).copy(alpha = alpha))
    centeredText(text, "+$amount", Point(at.x + 6f, y), size = 3.2f, color = Color(0xFF8A6A0A).copy(alpha = alpha), bold = true)
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

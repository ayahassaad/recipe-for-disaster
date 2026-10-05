package com.recipefordisaster.app.ui.scene

import com.recipefordisaster.domain.service.FloorPoint
import com.recipefordisaster.domain.service.ServiceFloor

/**
 * The restaurant's floor plan, in "scene units": the room is [WIDTH] wide
 * and [HEIGHT] tall, and the drawing scales that to whatever space the
 * screen has. Keeping positions in one place (and in plain numbers, not
 * pixels) means the drawing, the tap targets and the guest animation can't
 * disagree about where the oven is.
 *
 * Seen from above, roughly: kitchen along the top behind the pass counter,
 * dining room in the middle, front door at the bottom.
 */
fun FloorPoint.toPoint() = SceneLayout.Point(x, y)

object SceneLayout {
    const val WIDTH = 100f
    const val HEIGHT = 150f

    data class Point(val x: Float, val y: Float) {
        fun lerp(to: Point, t: Float): Point {
            val f = t.coerceIn(0f, 1f)
            return Point(x + (to.x - x) * f, y + (to.y - y) * f)
        }
    }

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val center get() = Point((left + right) / 2, (top + bottom) / 2)
    }

    // Kitchen
    const val KITCHEN_BOTTOM = 38f
    val oven = Rect(6f, 8f, 26f, 26f)
    val stove = Rect(30f, 8f, 50f, 26f)
    val sink = Rect(54f, 8f, 66f, 22f)
    val pantry = Rect(70f, 5f, 96f, 30f)

    // The pass: the counter between kitchen and dining room, where plates are handed over.
    val counter = Rect(4f, KITCHEN_BOTTOM, 96f, 44f)
    val pickup = ServiceFloor.pass.toPoint()

    // Dining room: the same six numbered tables the game logic plays service on (see ServiceFloor).
    val tables: List<Point> = ServiceFloor.tables.map { it.toPoint() }
    val seats: List<Point> = (0 until ServiceFloor.TABLE_COUNT).flatMap { t -> ServiceFloor.seats(t).map { it.toPoint() } }

    /** A sidewalk-style menu board by the front door, out of everyone's way. */
    val menuBoard = Rect(18f, 140.5f, 34f, 149.5f)

    val mopBucket = Rect(4f, 132f, 14f, 144f)
    val door = Rect(40f, 141f, 60f, 150f)
    val doorway = Point(50f, 146f)
    val outside = Point(50f, 153f)
    val hiringSign = Rect(68f, 138f, 90f, 148f)

    /** Where each kind of worker stands in the morning (they spread out if there are several). */
    fun cookSpot(index: Int) = Point(16f + index * 18f, 32f)
    fun dishwasherSpot(index: Int) = Point(60f + index * 8f, 30f)
    fun serverSpot(index: Int) = Point(34f + index * 16f, 49f)
    fun busserSpot(index: Int) = Point(22f - index * 8f, 49f)

    /** The host greets people just inside the door, beside where they queue. */
    fun hostSpot(index: Int) = Point(66f + index * 8f, 134f)
}

package com.recipefordisaster.domain.service

import kotlin.math.abs
import kotlin.math.hypot

/** A spot on the restaurant floor, in floor units (the room is [ServiceFloor.WIDTH] x [ServiceFloor.HEIGHT]). */
data class FloorPoint(val x: Float, val y: Float) {
    fun lerp(to: FloorPoint, t: Float): FloorPoint {
        val f = t.coerceIn(0f, 1f)
        return FloorPoint(x + (to.x - x) * f, y + (to.y - y) * f)
    }

    fun distanceTo(other: FloorPoint): Float = hypot(other.x - x, other.y - y)
}

/**
 * The floor plan service is played on: six numbered tables, the pass
 * (where tickets go in and plates come out), the door, and the aisles
 * people walk along. It lives in `:domain` because walking takes time and
 * time is gameplay — how far table 5 is from the kitchen decides how long
 * its food takes. The app draws the room from these same numbers.
 */
object ServiceFloor {
    const val WIDTH = 100f
    const val HEIGHT = 150f
    const val TABLE_COUNT = 6

    /** Table centres; table 1 is index 0 (top left), table 6 is index 5 (bottom right). */
    val tables: List<FloorPoint> = listOf(
        FloorPoint(27f, 64f), FloorPoint(73f, 64f),
        FloorPoint(27f, 92f), FloorPoint(73f, 92f),
        FloorPoint(27f, 120f), FloorPoint(73f, 120f),
    )

    /** The two chairs at a table: left, then right. */
    fun seats(table: Int): List<FloorPoint> = tables[table].let { listOf(FloorPoint(it.x - 12f, it.y), FloorPoint(it.x + 12f, it.y)) }

    /** Where a waiter stands to talk to (or serve) a table: in the aisle just below it. */
    fun stand(table: Int): FloorPoint = tables[table].let { FloorPoint(it.x, it.y + 10f) }

    /** Where tickets are handed in and plates picked up. */
    val pass = FloorPoint(50f, 49f)

    /** The dish station at the right end of the counter, where dirty plates are washed. */
    val dishStation = FloorPoint(86f, 49f)
    val door = FloorPoint(50f, 146f)
    const val AISLE_X = 50f

    /** Where parties wait inside the door when every table is taken. */
    fun queueSpot(index: Int): FloorPoint = FloorPoint(38f + (index % 4) * 8f, 134f - (index / 4) * 6f)

    /** Walking from one spot to another along the aisles: across to the centre aisle, along it, then across. */
    fun route(from: FloorPoint, to: FloorPoint): List<FloorPoint> =
        if (abs(from.y - to.y) < 0.5f) listOf(from, to) else listOf(from, FloorPoint(AISLE_X, from.y), FloorPoint(AISLE_X, to.y), to)

    fun length(route: List<FloorPoint>): Float = route.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    /** Position [t] (0-1) of the way along a route, at an even pace. */
    fun along(route: List<FloorPoint>, t: Float): FloorPoint {
        if (route.size == 1) return route.first()
        val lengths = route.zipWithNext { a, b -> a.distanceTo(b) }
        var remaining = t.coerceIn(0f, 1f) * lengths.sum()
        for ((i, length) in lengths.withIndex()) {
            if (remaining <= length || i == lengths.lastIndex) return route[i].lerp(route[i + 1], if (length == 0f) 1f else remaining / length)
            remaining -= length
        }
        return route.last()
    }
}

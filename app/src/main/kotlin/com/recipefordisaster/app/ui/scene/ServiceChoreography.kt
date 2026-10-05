package com.recipefordisaster.app.ui.scene

import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.domain.simulation.GuestOutcome
import com.recipefordisaster.domain.simulation.GuestVisit
import kotlin.math.max
import kotlin.math.min

/**
 * Turns the day's real guest list ([GuestVisit]s from the simulation) into
 * an animation timeline: who walks in when, which seat they take, which
 * server brings their plate and when, when they pay, and how they leave.
 * Pure arithmetic, so it's unit-tested on the JVM — the drawing code only
 * asks "where is guest 7 (or server 2) at time t, and how do they look".
 *
 * Nothing here decides an outcome. A guest who storms out hungry does so
 * because the simulation said the kitchen was full; this only stages it.
 * Servers carry one plate at a time, in turn, so a thin floor team visibly
 * makes guests wait longer for their food.
 */
class ServiceChoreography(guests: List<GuestVisit>, serverCount: Int = 1) {

    enum class Look { WALKING_IN, WAITING, EATING, HAPPY_LEAVING, ANGRY, STORMING_OUT, TURNING_AWAY }

    data class GuestFrame(
        val position: Point,
        val look: Look,
        /** 0-100, for the face: rises when fed, sinks while waiting for food that never comes. */
        val mood: Int,
        /** A plate on its way to (or sitting in front of) this guest. */
        val plate: Point?,
        /** Coins floating up after paying: where, and how far through the float (0-1). */
        val coins: Pair<Point, Float>?,
    )

    data class Track(
        val visit: GuestVisit,
        val start: Float,
        val seat: Point,
        val colorIndex: Int,
        /** For fed guests: which server brings the plate, and when they set off from the pass with it. */
        val server: Int = -1,
        val serveAt: Float = 0f,
        /** How long the walk from the pass to this table takes — further tables take longer. */
        val carry: Float = 0f,
    ) {
        val fed get() = visit.outcome == GuestOutcome.FED
        val eatUntil get() = serveAt + carry + EAT
        val leftAt get() = when {
            visit.outcome == GuestOutcome.WALKED_OUT -> start + 2.2f
            fed -> eatUntil + LEAVE
            else -> start + STORM_AT + STORM
        }
    }

    private val servers = serverCount.coerceAtLeast(1)

    val tracks: List<Track>

    /**
     * How much faster than walking pace the night plays. A busy night with
     * a thin team would otherwise run for a minute; instead the whole thing
     * speeds up evenly, so nobody ever jumps.
     */
    private val compression: Float

    /** Seconds (on screen) from the first guest arriving until the last one has left. */
    val duration: Float

    init {
        val interval = if (guests.isEmpty()) 0f else max(MIN_INTERVAL, min(MAX_INTERVAL, MAX_ARRIVALS_SPAN / guests.size))
        val serverFreeAt = FloatArray(servers)
        val seatFreeAt = FloatArray(SceneLayout.seats.size)
        var nextServer = 0
        tracks = guests.mapIndexed { i, visit ->
            val start = i * interval
            // The seat that's been free longest (or will be soonest).
            val seatIndex = seatFreeAt.indices.minBy { seatFreeAt[it] }
            val base = Track(visit = visit, start = start, seat = SceneLayout.seats[seatIndex], colorIndex = i)
            val track = if (visit.outcome == GuestOutcome.FED) {
                val server = nextServer
                nextServer = (nextServer + 1) % servers
                // The plate leaves the pass once the guest is seated and the server is back from their last run.
                val carry = walkTime(SceneLayout.routeFromPass(tableSpot(base.seat), base.seat))
                val serveAt = max(start + SEATED_AND_ORDERED, serverFreeAt[server] + FETCH)
                serverFreeAt[server] = serveAt + carry + carry * RETURN_FACTOR
                base.copy(server = server, serveAt = serveAt, carry = carry)
            } else {
                base
            }
            if (visit.outcome != GuestOutcome.WALKED_OUT) seatFreeAt[seatIndex] = track.leftAt
            track
        }
        val natural = (tracks.maxOfOrNull { it.leftAt } ?: 0f) + 0.5f
        compression = max(1f, natural / TARGET_DURATION)
        duration = natural / compression
    }

    /** How many coins have been paid so far tonight, for the running total on screen. */
    fun coinsPaidBy(screenTime: Float): Long {
        val time = screenTime * compression
        return tracks.filter { it.fed && time >= it.eatUntil }.sumOf { it.visit.paid }
    }

    fun frame(track: Track, screenTime: Float): GuestFrame? {
        val time = screenTime * compression
        val t = time - track.start
        if (t < 0f || time >= track.leftAt) return null
        val visit = track.visit
        val door = SceneLayout.doorway

        if (visit.outcome == GuestOutcome.WALKED_OUT) {
            // Steps inside, reads the menu board, shakes their head, leaves.
            val inside = Point(door.x + ((track.colorIndex % 3) - 1) * 6f, door.y - 14f)
            return when {
                t < 0.7f -> GuestFrame(door.lerp(inside, t / 0.7f), Look.WALKING_IN, 50, null, null)
                t < 1.4f -> GuestFrame(inside, Look.TURNING_AWAY, 35, null, null)
                else -> GuestFrame(inside.lerp(SceneLayout.outside, (t - 1.4f) / 0.8f), Look.TURNING_AWAY, 35, null, null)
            }
        }

        val seat = track.seat
        val routeIn = SceneLayout.routeToSeat(seat)
        val routeOut = routeIn.reversed() + SceneLayout.outside
        if (t < WALK) return GuestFrame(SceneLayout.along(routeIn, t / WALK), Look.WALKING_IN, 60, null, null)

        if (track.fed) {
            return when {
                time < track.serveAt -> GuestFrame(seat, Look.WAITING, waitingMood(time - track.start - WALK), null, null)
                time < track.serveAt + track.carry -> GuestFrame(seat, Look.WAITING, 55, carried(track, time), null)
                time < track.eatUntil -> GuestFrame(seat, Look.EATING, visit.satisfaction, tableSpot(seat), null)
                else -> {
                    val leaving = (time - track.eatUntil) / LEAVE
                    val coinFloat = ((time - track.eatUntil) / 1.0f).coerceAtMost(1f)
                    GuestFrame(SceneLayout.along(routeOut, leaving), Look.HAPPY_LEAVING, visit.satisfaction, null, seat to coinFloat)
                }
            }
        }

        // Hungry: they wait, get crosser, and storm out without paying.
        return when {
            t < ANGRY_AT -> GuestFrame(seat, Look.WAITING, waitingMood(t - WALK), null, null)
            t < STORM_AT -> GuestFrame(seat, Look.ANGRY, 5, null, null)
            else -> GuestFrame(SceneLayout.along(routeOut, (t - STORM_AT) / STORM), Look.STORMING_OUT, 5, null, null)
        }
    }

    /** True while any plate is being cooked or carried — the cook works and the stove glows. */
    fun kitchenBusy(screenTime: Float): Boolean {
        val time = screenTime * compression
        return tracks.any { it.fed && time in (it.serveAt - COOK_LEAD)..(it.serveAt + it.carry) }
    }

    /**
     * Where server [index] is at [time]: walking to the pass, carrying a
     * plate along the aisle to a table, walking back, or waiting at
     * [restSpot]. Every move is a walk from where they just were, so they
     * never jump across the room.
     */
    fun serverPosition(index: Int, screenTime: Float, restSpot: Point): Point {
        val time = screenTime * compression
        val mine = tracks.filter { it.fed && it.server == index }
        val run = mine.firstOrNull { time >= it.serveAt - FETCH && time < it.serveAt + it.carry * (1 + RETURN_FACTOR) } ?: return restSpot
        val route = SceneLayout.routeFromPass(tableSpot(run.seat), run.seat)
        val p = when {
            time < run.serveAt -> restSpot.lerp(SceneLayout.pickup, 1f - (run.serveAt - time) / FETCH)
            time < run.serveAt + run.carry -> SceneLayout.along(route, (time - run.serveAt) / run.carry)
            else -> SceneLayout.along(route.reversed() + restSpot, (time - run.serveAt - run.carry) / (run.carry * RETURN_FACTOR))
        }
        return Point(p.x, p.y - 3f)
    }

    private fun carried(track: Track, time: Float): Point {
        val p = SceneLayout.along(SceneLayout.routeFromPass(tableSpot(track.seat), track.seat), (time - track.serveAt) / track.carry)
        return Point(p.x, p.y - 3f)
    }

    private fun walkTime(route: List<Point>): Float = route.zipWithNext { a, b -> a.distanceTo(b) }.sum() / WALK_SPEED

    private fun waitingMood(waited: Float) = (62 - waited * 12).toInt().coerceIn(20, 62)

    private fun tableSpot(seat: Point): Point {
        val table = SceneLayout.tables.minBy { (it.x - seat.x) * (it.x - seat.x) + (it.y - seat.y) * (it.y - seat.y) }
        return Point((table.x + seat.x) / 2, table.y)
    }

    companion object {
        private const val WALK = 1.0f
        private const val SEATED_AND_ORDERED = 1.4f
        private const val COOK_LEAD = 0.8f
        private const val FETCH = 0.25f

        /** Scene units per second — a brisk restaurant walk. */
        private const val WALK_SPEED = 75f

        /** Walking back empty-handed is a bit quicker than carrying a plate. */
        private const val RETURN_FACTOR = 0.8f

        /** The longest a night plays on screen before it's sped up to fit. */
        private const val TARGET_DURATION = 22f
        private const val EAT = 1.2f
        private const val LEAVE = 1.0f
        private const val ANGRY_AT = 2.8f
        private const val STORM_AT = 3.5f
        private const val STORM = 0.7f
        private const val MIN_INTERVAL = 0.35f
        private const val MAX_INTERVAL = 0.8f
        private const val MAX_ARRIVALS_SPAN = 12f
    }
}

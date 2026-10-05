package com.recipefordisaster.app.ui.scene

import com.recipefordisaster.domain.simulation.GuestOutcome
import com.recipefordisaster.domain.simulation.GuestVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceChoreographyTest {

    private fun guests(n: Int, outcome: (Int) -> GuestOutcome = { GuestOutcome.FED }) = (0 until n).map { i ->
        val o = outcome(i)
        GuestVisit(o, dishName = if (o == GuestOutcome.FED) "Burger" else null, paid = if (o == GuestOutcome.FED) 18 else 0, satisfaction = 70)
    }

    @Test
    fun `everyone has left by the end of the night`() {
        val show = ServiceChoreography(guests(40))
        show.tracks.forEach { assertNull(show.frame(it, show.duration)) }
    }

    @Test
    fun `a busy night still fits in about twenty seconds`() {
        assertTrue(ServiceChoreography(guests(40)).duration <= 23f)
    }

    @Test
    fun `coins paid by the end match what fed guests paid`() {
        val visits = guests(12) { if (it % 3 == 0) GuestOutcome.HUNGRY_KITCHEN_FULL else GuestOutcome.FED }
        val show = ServiceChoreography(visits)
        assertEquals(visits.sumOf { it.paid }, show.coinsPaidBy(show.duration))
    }

    @Test
    fun `guests who walk out never reach a seat`() {
        val show = ServiceChoreography(guests(6) { GuestOutcome.WALKED_OUT })
        show.tracks.forEach { track ->
            var t = track.start
            while (t < show.duration) {
                show.frame(track, t)?.let { assertTrue("at $t", it.position != track.seat) }
                t += 0.05f
            }
        }
    }

    @Test
    fun `servers walk smoothly and never jump across the room`() {
        for (servers in 1..3) {
            val show = ServiceChoreography(guests(30), serverCount = servers)
            for (index in 0 until servers) {
                val rest = SceneLayout.serverSpot(index)
                var previous = show.serverPosition(index, 0f, rest)
                var t = 0.02f
                while (t < show.duration) {
                    val now = show.serverPosition(index, t, rest)
                    assertTrue("server $index of $servers jumped at $t", previous.distanceTo(now) < 12f)
                    previous = now
                    t += 0.02f
                }
            }
        }
    }

    @Test
    fun `a second server gets food to tables sooner`() {
        val one = ServiceChoreography(guests(20), serverCount = 1)
        val two = ServiceChoreography(guests(20), serverCount = 2)
        assertTrue(two.tracks.last().serveAt < one.tracks.last().serveAt)
    }

    @Test
    fun `each server carries one plate at a time`() {
        val show = ServiceChoreography(guests(25), serverCount = 2)
        for (server in 0..1) {
            val carries = show.tracks.filter { it.fed && it.server == server }
            carries.zipWithNext().forEach { (a, b) -> assertTrue(b.serveAt - a.serveAt >= a.carry) }
        }
    }
}

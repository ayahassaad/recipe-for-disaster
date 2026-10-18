package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GentleStartTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start = NewGameFactory.create(seed = 4L)

    @Test
    fun `the first night is the gentlest, and it's gone after the first week`() {
        assertEquals(1f, GentleStart.left(start.copy(day = 1)), 0f)
        val left = (1..7).map { GentleStart.left(start.copy(day = it)) }
        assertTrue(left.zipWithNext().all { (a, b) -> b < a || (a == 0f && b == 0f) })
        assertEquals(0f, GentleStart.left(start.copy(day = GentleStart.LAST_DAY + 1)), 0f)
        assertEquals(0f, GentleStart.left(start.copy(day = 30)), 0f)
    }

    @Test
    fun `on the first night guests arrive twice as far apart and are more patient`() {
        assertEquals(GentleStart.FIRST_NIGHT_GAP, GentleStart.arrivalGapFactor(start), 0.001f)
        assertEquals(GentleStart.FIRST_NIGHT_PATIENCE, GentleStart.patienceFactor(start), 0.001f)
        assertEquals(1f, GentleStart.arrivalGapFactor(start.copy(day = 10)), 0f)
        assertEquals(1f, GentleStart.patienceFactor(start.copy(day = 10)), 0f)
    }

    @Test
    fun `it can be turned off with everything from the start`() {
        val everything = start.copy(everythingFromTheStart = true)
        assertEquals(1f, GentleStart.arrivalGapFactor(everything), 0f)
        assertEquals(1f, GentleStart.patienceFactor(everything), 0f)
    }

    private fun averageGap(day: Int): Float {
        val state = start.copy(day = day, restaurant = start.restaurant.copy(reputation = 80))
        val setup = engine.openService(state, PlayerDecisions(), SeededRandomSource(1L))
        val times = ServiceNight.open(setup, SeededRandomSource(2L)).parties.map { it.arriveAt }.sorted()
        return (times.last() - times.first()) / (times.size - 1)
    }

    @Test
    fun `a first night really is spread out compared with a later one`() {
        assertTrue("night 1 ${averageGap(1)} vs night 7 ${averageGap(7)}", averageGap(1) > averageGap(7) * 1.6f)
    }
}

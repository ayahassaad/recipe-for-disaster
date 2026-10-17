package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.OutlookCalculator
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RushDemandTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val seed = 12L
    private val base = NewGameFactory.create(seed = seed).let { it.copy(restaurant = it.restaurant.copy(reputation = 60, tables = 6)) }
    private val rushDay = (RushNights.FROM_DAY..400).first { RushNights.isRush(seed, it) }
    private val usualDay = (RushNights.FROM_DAY..400).first { !RushNights.isRush(seed, it) && !RushNights.isRush(seed, it + 1) }

    private fun guests(day: Int) = engine.openService(base.copy(day = day), PlayerDecisions(), SeededRandomSource(1L)).arrivals.size

    @Test
    fun `a rush night brings more guests than the same restaurant on a usual night`() {
        assertTrue("rush ${guests(rushDay)} vs usual ${guests(usualDay)}", guests(rushDay) > guests(usualDay))
    }

    @Test
    fun `and the morning's forecast already says so`() {
        val rush = OutlookCalculator.forTonight(base.copy(day = rushDay)).expectedCustomers
        val usual = OutlookCalculator.forTonight(base.copy(day = usualDay)).expectedCustomers
        assertTrue("rush $rush vs usual $usual", rush > usual)
    }

    @Test
    fun `a rush can bring more guests than there are seats, who then wait at the door`() {
        val full = base.copy(day = rushDay, restaurant = base.restaurant.copy(reputation = 100))
        val capacity = full.restaurant.guestCapacity
        val arrivals = engine.openService(full, PlayerDecisions(), SeededRandomSource(1L)).arrivals.size
        assertTrue("$arrivals vs capacity $capacity", arrivals > capacity)
        assertTrue(arrivals <= capacity * (100 + RushNights.OVER_CAPACITY_PERCENT) / 100)
        // An ordinary night is still capped at the room.
        val ordinary = engine.openService(full.copy(day = usualDay), PlayerDecisions(), SeededRandomSource(1L)).arrivals.size
        assertEquals(true, ordinary <= capacity)
    }
}

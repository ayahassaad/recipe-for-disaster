package com.recipefordisaster.domain.restaurant

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableGrowthTest {

    private val start = NewGameFactory.create(seed = 3L)

    @Test
    fun `a new restaurant opens with two tables and gets one more each day until six`() {
        assertEquals(2, start.restaurant.tables)
        val engine = DefaultDayTickEngine(EventEngine(emptyList()))
        var state = start
        val counts = (1..6).map {
            state = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(it.toLong())).newState
            state.restaurant.tables
        }
        assertEquals(listOf(3, 4, 5, 6, 6, 6), counts)
    }

    @Test
    fun `more tables bring more guests`() {
        val two = start.restaurant
        val six = two.copy(tables = 6)
        assertTrue(six.guestCapacity >= two.guestCapacity * 3 - 1)
    }

    @Test
    fun `tables after the sixth cost coins, each more than the last, up to twelve`() {
        assertNull(TableGrowth.nextTablePrice(5))
        assertEquals(300L, TableGrowth.nextTablePrice(6))
        assertEquals(400L, TableGrowth.nextTablePrice(7))
        assertNull(TableGrowth.nextTablePrice(12))
    }

    @Test
    fun `buying a table adds one and charges for it`() {
        val six = start.copy(restaurant = start.restaurant.copy(tables = 6))
        val applied = DecisionApplier.apply(six, PlayerDecisions(buyTable = true))
        assertEquals(7, applied.state.restaurant.tables)
        assertEquals(300L, applied.spending.upgrades)
    }

    @Test
    fun `you can't buy a table while free ones are still arriving, or past twelve, or without the money`() {
        assertEquals(2, DecisionApplier.apply(start, PlayerDecisions(buyTable = true)).state.restaurant.tables)
        val full = start.copy(restaurant = start.restaurant.copy(tables = 12))
        assertEquals(12, DecisionApplier.apply(full, PlayerDecisions(buyTable = true)).state.restaurant.tables)
        val broke = start.copy(restaurant = start.restaurant.copy(tables = 6, cash = 100))
        assertEquals(6, DecisionApplier.apply(broke, PlayerDecisions(buyTable = true)).state.restaurant.tables)
    }

    @Test
    fun `rent is for the tables you have, so a small new restaurant pays less`() {
        assertEquals(20L, start.restaurant.costsToday.rentPerDay)
        assertEquals(60L, start.restaurant.copy(tables = 6).costsToday.rentPerDay)
        assertEquals(120L, start.restaurant.copy(tables = 12).costsToday.rentPerDay)
    }

    @Test
    fun `a bad night costs only a little reputation in the first week, and in full after`() {
        assertEquals(-2, ReputationModel.dailyReputationDelta(averageSatisfaction = 5, cleanliness = 80, day = 2))
        assertTrue(ReputationModel.dailyReputationDelta(averageSatisfaction = 5, cleanliness = 80, day = 10) < -2)
        // Good nights count in full from the start.
        assertEquals(ReputationModel.dailyReputationDelta(95, 80, day = 10), ReputationModel.dailyReputationDelta(95, 80, day = 1))
    }

    @Test
    fun `no guest is too poor for the cheapest dish on the starting menu`() {
        val cheapest = start.menu.minOf { it.sellingPrice }
        val rng = SeededRandomSource(9L)
        repeat(500) { assertTrue(com.recipefordisaster.domain.customer.CustomerFlow.generateCustomer(rng, it).budget >= cheapest) }
    }
}

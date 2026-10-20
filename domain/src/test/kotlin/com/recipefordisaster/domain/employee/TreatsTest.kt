package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Test

class TreatsTest {

    private val start = NewGameFactory.create(seed = 4L).let { game ->
        game.copy(employees = game.employees.map { it.copy(morale = 50, stress = 50) })
    }

    @Test
    fun `a treat comes out of the jar and cheers the whole team`() {
        val state = start.copy(tipJar = 100)
        val after = DecisionApplier.apply(state, PlayerDecisions(treats = setOf(Treat.PIZZA_FOR_THE_TEAM))).state
        assertEquals(70L, after.tipJar)
        assertTrue(after.employees.all { it.morale == 56 && it.stress == 44 })
        assertEquals(state.restaurant.cash, after.restaurant.cash)
    }

    @Test
    fun `an empty jar buys nothing`() {
        val state = start.copy(tipJar = 10)
        val result = DecisionApplier.apply(state, PlayerDecisions(treats = setOf(Treat.COFFEE_AND_PASTRIES)))
        assertEquals(10L, result.state.tipJar)
        assertTrue(result.state.employees.all { it.morale == 50 })
    }

    @Test
    fun `moods stay between nothing and a hundred`() {
        val joyful = Treat.AN_EVENING_OUT.cheer(start.employees.first().copy(morale = 98, stress = 3))
        assertEquals(100, joyful.morale)
        assertEquals(0, joyful.stress)
    }

    @Test
    fun `treats are paid in the order of their price while the jar lasts`() {
        val state = start.copy(tipJar = 50)
        val after = DecisionApplier.apply(state, PlayerDecisions(treats = setOf(Treat.AN_EVENING_OUT, Treat.COFFEE_AND_PASTRIES, Treat.PIZZA_FOR_THE_TEAM))).state
        // Coffee (15) and pizza (30) fit; the evening out (70) doesn't.
        assertEquals(5L, after.tipJar)
    }

    @Test
    fun `a night's tips go into the jar`() {
        val engine = DefaultDayTickEngine(EventEngine(emptyList()))
        val state = start.copy(tipJar = 40)
        val setup = engine.openService(state, PlayerDecisions(), SeededRandomSource(2L))
        val result = engine.closeService(setup, com.recipefordisaster.domain.simulation.ServiceSimulator.ServiceResult(
            outcomes = emptyList(), dishesSold = emptyMap(), staffingRatio = 1.0, tips = 12,
        ), SeededRandomSource(3L))
        assertEquals(52L, result.newState.tipJar)
    }
}

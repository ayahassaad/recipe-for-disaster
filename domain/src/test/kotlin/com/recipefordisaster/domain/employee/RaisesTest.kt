package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaisesTest {

    private val start = NewGameFactory.create(seed = 3L).let { it.copy(day = 30) }
    private val cook = start.employees.first()

    @Test
    fun `a raise is a tenth more, at least two coins`() {
        assertEquals(6L, Raises.amount(cook.copy(salaryPerDay = 60)))
        assertEquals(2L, Raises.amount(cook.copy(salaryPerDay = 10)))
    }

    @Test
    fun `a raise pays more a day and lifts their mood`() {
        val raised = DecisionApplier.apply(start, PlayerDecisions(raises = setOf(cook.id))).state.employees.first { it.id == cook.id }
        assertEquals(cook.salaryPerDay + Raises.amount(cook), raised.salaryPerDay)
        assertEquals((cook.morale + Raises.MORALE_BOOST).coerceAtMost(100), raised.morale)
        assertEquals(30, raised.lastRaiseDay)
    }

    @Test
    fun `nobody gets another straight away`() {
        val state = start.copy(employees = start.employees.map { if (it.id == cook.id) it.copy(lastRaiseDay = 25) else it })
        assertFalse(Raises.canRaise(state.employees.first { it.id == cook.id }, state.day))
        assertEquals(5, Raises.daysToWait(state.employees.first { it.id == cook.id }, state.day))
        val after = DecisionApplier.apply(state, PlayerDecisions(raises = setOf(cook.id))).state.employees.first { it.id == cook.id }
        assertEquals(cook.salaryPerDay, after.salaryPerDay)
        assertTrue(Raises.canRaise(cook.copy(lastRaiseDay = 20), 30))
    }
}

package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTest {

    private val start = NewGameFactory.create(seed = 3L)
    private val cook = start.employees.first()

    @Test
    fun `days of the week count from the first day, and repeat`() {
        assertEquals(0, Roster.weekday(1))
        assertEquals(6, Roster.weekday(7))
        assertEquals(0, Roster.weekday(8))
        assertEquals(3, Roster.weekday(25))
    }

    @Test
    fun `a day can be put on and taken off the roster, up to a limit`() {
        var e = cook
        e = Roster.toggle(e, 2)
        assertEquals(setOf(2), e.offDays)
        e = Roster.toggle(e, 2)
        assertTrue(e.offDays.isEmpty())
        (0 until 6).forEach { e = Roster.toggle(e, it) }
        assertEquals(Roster.MAX_DAYS_OFF, e.offDays.size)
        // A weekday that isn't one is ignored.
        assertEquals(e, Roster.toggle(e, 9))
    }

    @Test
    fun `someone with today off by the roster is resting today`() {
        val state = start.copy(day = 10, employees = start.employees.map { it.copy(offDays = setOf(Roster.weekday(10))) })
        val morning = DecisionApplier.apply(state, PlayerDecisions()).state

        assertTrue(morning.employees.all { it.status == EmployeeStatus.ON_BREAK })
    }

    @Test
    fun `and not on the other days of the week`() {
        val state = start.copy(day = 11, employees = start.employees.map { it.copy(offDays = setOf(Roster.weekday(10))) })
        val morning = DecisionApplier.apply(state, PlayerDecisions()).state
        assertTrue(morning.employees.all { it.status == EmployeeStatus.ACTIVE })
    }

    @Test
    fun `giving a rostered day off's worker today off puts them to work instead`() {
        val state = start.copy(day = 10, employees = start.employees.map { it.copy(offDays = setOf(Roster.weekday(10))) })
        val morning = DecisionApplier.apply(state, PlayerDecisions(restDays = setOf(cook.id))).state

        assertEquals(EmployeeStatus.ACTIVE, morning.employees.first { it.id == cook.id }.status)
    }

    @Test
    fun `an ordinary day off still works as before`() {
        val morning = DecisionApplier.apply(start, PlayerDecisions(restDays = setOf(cook.id))).state
        assertEquals(EmployeeStatus.ON_BREAK, morning.employees.first { it.id == cook.id }.status)
    }

    @Test
    fun `days off are unpaid, so the roster saves wages`() {
        assertEquals(cook.salaryPerDay * 7, Roster.weeklyWages(cook))
        assertEquals(cook.salaryPerDay * 5, Roster.weeklyWages(cook.copy(offDays = setOf(5, 6))))
        val resting = DecisionApplier.apply(start.copy(day = 10, employees = start.employees.map { it.copy(offDays = setOf(Roster.weekday(10))) }), PlayerDecisions()).state
        assertFalse(DailyCosts.of(resting).wages > 0)
    }
}

package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffSlipsTest {

    private val start = NewGameFactory.create(seed = 9L)

    private fun server(experience: Int, reliability: Int = 90, stress: Int = 0, skill: Int = 60): Employee =
        start.applicants.first { it.role == Role.SERVER }
            .copy(status = EmployeeStatus.ACTIVE, experienceDays = experience, reliability = reliability, stress = stress, skill = skill)

    private fun withServer(server: Employee) = start.copy(employees = start.employees + server)

    @Test
    fun `a brand new server always trips on their first shift`() {
        val server = server(experience = 0)
        val plan = StaffSlips.planFor(withServer(server))

        assertTrue(server.id.value in plan)
        assertTrue(plan.getValue(server.id.value) in 25f..170f)
    }

    @Test
    fun `an old hand who's rested and reliable almost never trips`() {
        val steady = server(experience = 30, reliability = 100, stress = 0)
        assertEquals(0f, StaffSlips.chance(steady), 0f)
        assertTrue(StaffSlips.planFor(withServer(steady)).isEmpty())
        // The founding cook starts with no experience too, but stays in the kitchen.
        assertTrue(StaffSlips.planFor(start).isEmpty())
    }

    @Test
    fun `an old hand who's worn out or unreliable can still trip`() {
        val steady = server(experience = 30, reliability = 90, stress = 10)
        val worn = server(experience = 30, reliability = 40, stress = 90)
        assertTrue(StaffSlips.chance(worn) > StaffSlips.chance(steady))
        assertTrue(StaffSlips.chance(worn) > 0.5f)
        // Across a few days, the worn-out one trips on some of them.
        val tripDays = (1..30).count { day -> StaffSlips.planFor(withServer(worn).copy(day = day)).containsKey(worn.id.value) }
        assertTrue("tripped on $tripDays of 30 days", tripDays in 8..30)
    }

    @Test
    fun `being new, tired and unreliable add up`() {
        val rested = server(experience = 1, reliability = 90, stress = 0)
        val tired = rested.copy(stress = 80)
        assertTrue(StaffSlips.chance(tired) > StaffSlips.chance(rested))
        assertTrue(StaffSlips.chance(rested) > StaffSlips.newHireChance(1) - 0.001f)
    }

    @Test
    fun `the plan is the same every time for the same day`() {
        val state = withServer(server(experience = 1))
        assertEquals(StaffSlips.planFor(state), StaffSlips.planFor(state))
    }

    @Test
    fun `being new alone makes tripping rarer with each shift`() {
        assertTrue(StaffSlips.newHireChance(0) > StaffSlips.newHireChance(1))
        assertTrue(StaffSlips.newHireChance(1) > StaffSlips.newHireChance(2))
        assertEquals(0f, StaffSlips.newHireChance(3), 0f)
    }

    @Test
    fun `how often someone slips is put into a word`() {
        assertEquals(StaffSlips.Sloppiness.RARELY, StaffSlips.sloppiness(server(experience = 30, reliability = 100, stress = 0)))
        assertEquals(StaffSlips.Sloppiness.OFTEN, StaffSlips.sloppiness(server(experience = 0)))
    }
}

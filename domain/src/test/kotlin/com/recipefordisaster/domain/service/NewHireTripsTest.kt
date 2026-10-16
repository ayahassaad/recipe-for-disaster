package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewHireTripsTest {

    private val start = NewGameFactory.create(seed = 9L)

    private fun withServer(experience: Int) = start.copy(
        employees = start.employees + start.applicants.first { it.role == Role.SERVER }.copy(status = EmployeeStatus.ACTIVE, experienceDays = experience),
    )

    @Test
    fun `a brand new server always trips on their first shift`() {
        val state = withServer(experience = 0)
        val server = state.employees.first { it.role == Role.SERVER }
        val plan = NewHireTrips.planFor(state)

        assertTrue(server.id.value in plan)
        assertTrue(plan.getValue(server.id.value) in 25f..170f)
    }

    @Test
    fun `an old hand never trips, and the cook never does`() {
        val plan = NewHireTrips.planFor(withServer(experience = NewHireTrips.NEW_SHIFTS))
        assertTrue(plan.isEmpty())
        // The founding cook starts with no experience too, but stays in the kitchen.
        assertTrue(NewHireTrips.planFor(start).isEmpty())
    }

    @Test
    fun `the plan is the same every time for the same day`() {
        val state = withServer(experience = 1)
        assertEquals(NewHireTrips.planFor(state), NewHireTrips.planFor(state))
    }

    @Test
    fun `tripping gets rarer with each shift`() {
        assertTrue(NewHireTrips.chance(0) > NewHireTrips.chance(1))
        assertTrue(NewHireTrips.chance(1) > NewHireTrips.chance(2))
        assertEquals(0f, NewHireTrips.chance(3), 0f)
    }
}

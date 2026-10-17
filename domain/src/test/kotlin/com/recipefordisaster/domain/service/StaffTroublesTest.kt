package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTroublesTest {

    private val base = NewGameFactory.create(seed = 4L)

    private fun worker(role: Role, id: String, stress: Int = 20, morale: Int = 70): Employee =
        base.applicants.first().copy(id = com.recipefordisaster.domain.employee.EmployeeId(id), role = role, status = EmployeeStatus.ACTIVE, stress = stress, morale = morale, experienceDays = 20)

    private fun staffed(day: Int, vararg staff: Employee) = base.copy(day = day, employees = base.employees + staff)

    @Test
    fun `nobody has a bad night before the day it starts`() {
        assertTrue(StaffTroubles.planFor(staffed(day = 10, worker(Role.SERVER, "s", stress = 90))).isEmpty())
    }

    @Test
    fun `nobody has one with nobody on the floor, and the cook never does`() {
        assertTrue(StaffTroubles.planFor(staffed(day = 25)).isEmpty())
    }

    @Test
    fun `the first night it can happen, it does`() {
        val plan = StaffTroubles.planFor(staffed(day = 19, worker(Role.SERVER, "s")))
        assertEquals(setOf("s"), plan.keys)
    }

    @Test
    fun `never more than one person a night`() {
        val crew = arrayOf(worker(Role.SERVER, "a", stress = 95, morale = 10), worker(Role.DISHWASHER, "b", stress = 95, morale = 10), worker(Role.BARTENDER, "c", stress = 95, morale = 10))
        (19..60).forEach { day -> assertTrue(StaffTroubles.planFor(staffed(day, *crew)).size <= 1) }
    }

    @Test
    fun `worn-out, unhappy staff have bad nights far more often`() {
        fun nights(stress: Int, morale: Int) = (20..120).count { day -> StaffTroubles.planFor(staffed(day, worker(Role.SERVER, "s", stress, morale))).isNotEmpty() }
        assertTrue(nights(stress = 95, morale = 10) > nights(stress = 10, morale = 90) + 15)
    }

    @Test
    fun `a break ends, the others don't`() {
        val plans = (19..200).flatMap { day -> StaffTroubles.planFor(staffed(day, worker(Role.SERVER, "s", stress = 80, morale = 30))).values }
        assertTrue(plans.any { it.kind == TroubleKind.BREAK })
        plans.forEach { plan ->
            if (plan.kind == TroubleKind.BREAK) assertEquals(plan.at + StaffTroubles.BREAK_LENGTH, plan.backAt!!, 0.001f) else assertNull(plan.backAt)
            assertTrue(plan.at in 40f..140f)
        }
        assertNotNull(plans.firstOrNull { it.kind == TroubleKind.SICK })
    }

    @Test
    fun `the plan is the same every time for the same day`() {
        val state = staffed(day = 30, worker(Role.SERVER, "s", stress = 80))
        assertEquals(StaffTroubles.planFor(state), StaffTroubles.planFor(state))
    }
}

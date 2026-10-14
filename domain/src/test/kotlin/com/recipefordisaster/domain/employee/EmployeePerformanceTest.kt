package com.recipefordisaster.domain.employee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeePerformanceTest {

    private fun cook(
        skill: Int = 70,
        speed: Int = 70,
        morale: Int = 70,
        stress: Int = 20,
        reliability: Int = 70,
        status: EmployeeStatus = EmployeeStatus.ACTIVE,
    ) = Employee(
        id = EmployeeId("e1"),
        name = "Test Cook",
        role = Role.COOK,
        skill = skill,
        speed = speed,
        reliability = reliability,
        morale = morale,
        stress = stress,
        salaryPerDay = 1000,
        experienceDays = 30,
        personalityTraits = emptySet(),
        relationships = emptyMap(),
        status = status,
    )

    @Test
    fun `low morale and high stress reduce effective speed relative to a healthy employee`() {
        val healthy = cook(morale = 90, stress = 5)
        val burnedOut = cook(morale = 10, stress = 90)

        assertTrue(EmployeePerformance.effectiveServiceSpeed(burnedOut) < EmployeePerformance.effectiveServiceSpeed(healthy))
    }

    @Test
    fun `an inactive employee contributes no speed`() {
        val quit = cook(status = EmployeeStatus.QUIT)

        assertEquals(0.0, EmployeePerformance.effectiveServiceSpeed(quit), 0.0)
    }

    @Test
    fun `low reliability and high stress raise error probability`() {
        val reliable = cook(reliability = 95, stress = 5)
        val unreliable = cook(reliability = 20, stress = 80)

        assertTrue(EmployeePerformance.errorProbability(unreliable) > EmployeePerformance.errorProbability(reliable))
    }

    @Test
    fun `a busy understaffed day raises stress`() {
        val before = cook(stress = 20)
        val after = EmployeePerformance.applyEndOfDayStress(before, staffingRatio = 10.0)

        assertTrue(after.stress > before.stress)
    }

    @Test
    fun `even a quiet day is a little tiring, a busy one far more, and only a day off clears it`() {
        val before = cook(stress = 50)
        val quiet = EmployeePerformance.applyEndOfDayStress(before, staffingRatio = 2.0)
        val busy = EmployeePerformance.applyEndOfDayStress(before, staffingRatio = 14.0)

        assertTrue(quiet.stress in (before.stress + 1)..(before.stress + 3))
        assertTrue(busy.stress > quiet.stress + 10)
        assertTrue(EmployeePerformance.returnFromDayOff(busy.copy(status = EmployeeStatus.ON_BREAK)).stress < before.stress)
    }

    @Test
    fun `a day off returns the employee to work with less stress`() {
        val resting = cook(stress = 80, status = EmployeeStatus.ON_BREAK)
        val after = EmployeePerformance.returnFromDayOff(resting)

        assertEquals(EmployeeStatus.ACTIVE, after.status)
        assertTrue(after.stress < resting.stress)
    }

    @Test
    fun `a sick employee comes back once their sick days run out`() {
        val sick = cook(status = EmployeeStatus.SICK).copy(sickDaysRemaining = 2)

        val dayOne = EmployeePerformance.advanceSickness(sick)
        val dayTwo = EmployeePerformance.advanceSickness(dayOne)

        assertEquals(EmployeeStatus.SICK, dayOne.status)
        assertEquals(EmployeeStatus.ACTIVE, dayTwo.status)
    }

    @Test
    fun `every ten days on the job they get better and ask for a raise`() {
        var worker = cook(stress = 0)
        val wage = worker.salaryPerDay
        repeat(10) { worker = EmployeePerformance.applyEndOfDayStress(worker.copy(stress = 0), staffingRatio = 2.0) }
        assertEquals(wage + EmployeePerformance.RAISE_PER_STEP, worker.salaryPerDay)
    }
}

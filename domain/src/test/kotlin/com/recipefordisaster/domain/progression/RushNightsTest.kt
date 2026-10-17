package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RushNightsTest {

    private val seed = 12L
    private val start = NewGameFactory.create(seed = seed)

    @Test
    fun `no rush before the first week is over`() {
        assertTrue((1 until RushNights.FROM_DAY).none { RushNights.isRush(seed, it) })
    }

    @Test
    fun `about one night in five is a rush, never two in a row`() {
        val days = (RushNights.FROM_DAY..400).toList()
        val rushes = days.filter { RushNights.isRush(seed, it) }
        assertTrue("rushes: ${rushes.size} of ${days.size}", rushes.size in 40..110)
        assertTrue(rushes.zipWithNext().none { (a, b) -> b - a == 1 })
    }

    @Test
    fun `the warning and the night agree`() {
        (1..80).forEach { day ->
            val today = start.copy(day = day)
            assertEquals(RushNights.isRush(seed, day + 1), RushNights.tomorrowIsRush(today))
        }
    }

    @Test
    fun `a rush brings more guests the more staff there are, up to a limit`() {
        val day = (RushNights.FROM_DAY..400).first { RushNights.isRush(seed, it) }
        val rushDay = start.copy(day = day)
        val crew = (1..10).map { n -> start.applicants.first().copy(id = com.recipefordisaster.domain.employee.EmployeeId("c$n"), role = Role.SERVER, status = EmployeeStatus.ACTIVE) }

        val alone = RushNights.demandPercent(rushDay)
        val withTwo = RushNights.demandPercent(rushDay.copy(employees = rushDay.employees + crew.take(2)))
        val withTen = RushNights.demandPercent(rushDay.copy(employees = rushDay.employees + crew))
        assertTrue(alone >= RushNights.BASE_PERCENT)
        assertTrue(withTwo > alone)
        assertEquals(RushNights.MAX_PERCENT, withTen)
    }

    @Test
    fun `an ordinary night has no extra guests`() {
        val day = (RushNights.FROM_DAY..400).first { !RushNights.isRush(seed, it) }
        assertEquals(0, RushNights.demandPercent(start.copy(day = day)))
        assertEquals(0, RushNights.overCapacityPercent(start.copy(day = day)))
        assertFalse(RushNights.isRush(start.copy(day = day)))
    }
}

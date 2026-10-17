package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.StaffTroubleOutcome
import com.recipefordisaster.domain.service.TroubleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadNightAftermathTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start = NewGameFactory.create(seed = 6L).let { it.copy(day = 25, restaurant = it.restaurant.copy(tables = 6)) }
    private val worker = start.employees.first()

    private fun closeWith(kind: TroubleKind, morale: Int = 70): GameState {
        val state = start.copy(employees = start.employees.map { if (it.id == worker.id) it.copy(morale = morale) else it })
        val setup = engine.openService(state, PlayerDecisions(), SeededRandomSource(1L))
        val result = ServiceSimulator.ServiceResult(
            outcomes = emptyList(), dishesSold = emptyMap(), staffingRatio = 8.0,
            staffTrouble = listOf(StaffTroubleOutcome(worker.id.value, kind)),
        )
        return engine.closeService(setup, result, SeededRandomSource(2L)).newState
    }

    @Test
    fun `someone who went home ill is off sick for a couple of days`() {
        val after = closeWith(TroubleKind.SICK).employees.first { it.id == worker.id }
        assertEquals(EmployeeStatus.SICK, after.status)
        assertEquals(EmployeePerformance.SICK_DAYS_AFTER_GOING_HOME, after.sickDaysRemaining)
    }

    @Test
    fun `someone who stormed out is in a worse mood`() {
        val after = closeWith(TroubleKind.ARGUMENT, morale = 80).employees.first { it.id == worker.id }
        assertEquals(EmployeeStatus.ACTIVE, after.status)
        assertTrue(after.morale <= 80 - EmployeePerformance.ROW_MORALE_HIT + 2)
    }

    @Test
    fun `someone already fed up who storms out quits for good`() {
        val after = closeWith(TroubleKind.ARGUMENT, morale = 25).employees.first { it.id == worker.id }
        assertEquals(EmployeeStatus.QUIT, after.status)
    }

    @Test
    fun `a long break leaves nothing behind`() {
        val after = closeWith(TroubleKind.BREAK).employees.first { it.id == worker.id }
        assertEquals(EmployeeStatus.ACTIVE, after.status)
    }
}

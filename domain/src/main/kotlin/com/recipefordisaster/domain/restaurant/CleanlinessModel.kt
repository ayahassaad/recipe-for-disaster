package com.recipefordisaster.domain.restaurant

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role

/**
 * Cleanliness used to be a constant 80 for the whole run (Phase 5). Phase 6
 * makes it move: every service makes a mess in proportion to how busy it
 * was, and only dishwashers (or a paid deep clean, see
 * [com.recipefordisaster.domain.decision.DecisionApplier]) push it back up.
 * Low cleanliness already costs reputation via [ReputationModel] and now
 * also feeds the rat / health-inspection events.
 */
object CleanlinessModel {

    const val DEEP_CLEAN_BOOST = 35

    fun endOfDayCleanliness(current: Int, customersServed: Int, employees: List<Employee>): Int {
        val mess = 3 + customersServed / 6
        val dishwashers = employees.count { it.status == EmployeeStatus.ACTIVE && it.role == Role.DISHWASHER }
        // Everyone wipes a table now and then; a dishwasher actually keeps up.
        val cleaning = 2 + dishwashers * 8
        return (current - mess + cleaning).coerceIn(0, 100)
    }
}

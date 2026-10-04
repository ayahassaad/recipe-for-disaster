package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.restaurant.OperatingCosts

/**
 * Rolls up a single day's revenue and every expense category from the
 * product brief's economy model (section 3) into one [DailyFinancials]
 * entry. Deliberately just arithmetic over inputs the rest of the engine
 * already produced — no new game rules invented here.
 */
object DailyFinancialsCalculator {

    fun calculate(
        day: Int,
        dishesSold: Map<DishId, Int>,
        menu: List<Dish>,
        employees: List<Employee>,
        ingredientCosts: Long,
        operatingCosts: OperatingCosts,
        equipment: List<Equipment>,
        miscellaneous: Long = 0,
        upgrades: Long = 0,
    ): DailyFinancials {
        val menuById = menu.associateBy { it.id }
        val revenue = dishesSold.entries.sumOf { (dishId, count) ->
            (menuById[dishId]?.sellingPrice ?: 0) * count
        }

        val wages = employees
            .filter { it.status == EmployeeStatus.ACTIVE }
            .sumOf { it.salaryPerDay }

        val maintenance = equipment.sumOf { it.maintenanceCostPerDay }

        return DailyFinancials(
            day = day,
            revenue = revenue,
            wages = wages,
            ingredientCosts = ingredientCosts,
            rent = operatingCosts.rentPerDay,
            utilities = operatingCosts.utilitiesPerDay,
            maintenance = maintenance,
            upgrades = upgrades, // repairs the player paid for this morning (Phase 6)
            miscellaneous = operatingCosts.miscPerDay + miscellaneous,
        )
    }
}

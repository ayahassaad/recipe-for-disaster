package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.violates

/**
 * This is where the product brief's central chain actually happens:
 * understaffing + tired employees -> slower service -> customers wait past
 * their patience -> satisfaction drops. Nothing here is scripted; it falls
 * out of ordinary arithmetic over whatever [Employee] and [Customer] state
 * the rest of the engine produced.
 */
object ServiceSimulator {

    data class CustomerServiceOutcome(
        val customer: Customer,
        val dish: Dish?,
        val satisfaction: Int,
        val waitMinutes: Double,
    )

    data class ServiceResult(
        val outcomes: List<CustomerServiceOutcome>,
        val dishesSold: Map<DishId, Int>,
        val staffingRatio: Double,
    )

    fun simulate(
        arrivals: List<Customer>,
        employees: List<Employee>,
        menu: List<Dish>,
    ): ServiceResult {
        val activeEmployees = employees.filter { it.status == EmployeeStatus.ACTIVE }
        val averageServiceSpeed = if (activeEmployees.isEmpty()) {
            0.0
        } else {
            activeEmployees.sumOf { EmployeePerformance.effectiveServiceSpeed(it) } / activeEmployees.size
        }

        // Customers per active employee. Empty restaurant floor with
        // customers waiting is the worst case, modeled as effectively
        // infinite ratio rather than dividing by zero.
        val staffingRatio = if (activeEmployees.isEmpty()) {
            if (arrivals.isEmpty()) 0.0 else Double.MAX_VALUE
        } else {
            arrivals.size.toDouble() / activeEmployees.size
        }

        val availableDishes = menu.filter { it.available }
        val dishesSold = mutableMapOf<DishId, Int>()

        val outcomes = arrivals.map { customer ->
            val dish = pickDish(customer, availableDishes)
            val waitMinutes = estimateWaitMinutes(staffingRatio, averageServiceSpeed)
            val satisfaction = resolveSatisfaction(customer, dish, waitMinutes)
            if (dish != null) {
                dishesSold[dish.id] = (dishesSold[dish.id] ?: 0) + 1
            }
            CustomerServiceOutcome(customer, dish, satisfaction, waitMinutes)
        }

        return ServiceResult(outcomes, dishesSold, staffingRatio)
    }

    private fun pickDish(customer: Customer, dishes: List<Dish>): Dish? {
        val eligible = dishes.filter { dish ->
            dish.sellingPrice <= customer.budget &&
                customer.dietaryRequirements.none { requirement -> dish.violates(requirement) }
        }
        // Simple popularity-greedy choice for MVP; richer preference
        // matching (cuisine style, repeat-visit favorites) is a later
        // refinement once there's a UI to expose those choices through.
        return eligible.maxByOrNull { it.popularity }
    }

    private fun estimateWaitMinutes(staffingRatio: Double, averageServiceSpeed: Double): Double {
        if (averageServiceSpeed <= 0.0) return 90.0 // no one working the floor: an effectively unbounded wait
        val speedFactor = 100.0 / averageServiceSpeed.coerceAtLeast(1.0)
        return (staffingRatio.coerceAtMost(20.0) * speedFactor).coerceIn(1.0, 90.0)
    }

    private fun resolveSatisfaction(customer: Customer, dish: Dish?, waitMinutes: Double): Int {
        if (dish == null) return 0 // nothing on the menu they could eat, or couldn't afford it — walked away

        val patienceDeficit = (waitMinutes - customer.patience).coerceAtLeast(0.0)
        val waitPenalty = (patienceDeficit * 2).toInt()
        val qualityEffect = dish.quality - 50 // quality is 0-100; 50 is "unremarkable," neither bonus nor penalty

        return (customer.satisfaction + qualityEffect - waitPenalty).coerceIn(0, 100)
    }
}

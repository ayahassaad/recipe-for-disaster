package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.violates

/**
 * This is where the product brief's central chain actually happens:
 * understaffing + tired employees -> slower service -> customers wait past
 * their patience -> satisfaction drops. Nothing here is scripted; it falls
 * out of ordinary arithmetic over whatever [Employee] and [Customer] state
 * the rest of the engine produced.
 *
 * Phase 6 adds the constraints player decisions push against: the kitchen
 * can only cook so many meals ([kitchenCapacity], set by who's cooking and
 * what's broken), every meal has to come out of real stock ([inventory]),
 * and price relative to [Dish.referencePrice] changes both what customers
 * order and how they feel about it. Each of those is optional, so a caller
 * that doesn't model them (most unit tests) gets the Phase 3 behavior.
 */
object ServiceSimulator {

    enum class MissedMealReason {
        /** Nothing on the menu they could eat or afford. */
        NOTHING_SUITABLE,

        /** Something suitable was on the menu, but the ingredients had run out. */
        OUT_OF_STOCK,

        /** The kitchen had already cooked as much as it could today. */
        KITCHEN_OVERWHELMED,

        /** Waited too long — for a table, to order, or for their food — and left. */
        TIRED_OF_WAITING,
    }

    data class CustomerServiceOutcome(
        val customer: Customer,
        val dish: Dish?,
        val satisfaction: Int,
        val waitMinutes: Double,
        val missedReason: MissedMealReason? = null,
    )

    data class ServiceResult(
        val outcomes: List<CustomerServiceOutcome>,
        val dishesSold: Map<DishId, Int>,
        val staffingRatio: Double,
        /** Stock left after every meal served today was cooked, or null if stock wasn't modeled. */
        val inventoryAfter: InventoryState? = null,
    ) {
        fun missedCount(reason: MissedMealReason): Int = outcomes.count { it.missedReason == reason }
    }

    /**
     * @param inventory stock to cook from; `null` means stock isn't modeled and every dish is always makeable.
     * @param kitchenCapacity the most meals the kitchen can turn out today; `null` means unlimited.
     * @param kitchenQualityBonus added to every dish's quality (skilled cooks up, broken oven down).
     * @param rng when given, customers choose dishes by weighted chance instead of always picking the favorite.
     */
    fun simulate(
        arrivals: List<Customer>,
        employees: List<Employee>,
        menu: List<Dish>,
        inventory: InventoryState? = null,
        kitchenCapacity: Int? = null,
        kitchenQualityBonus: Int = 0,
        rng: RandomSource? = null,
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
        var stock = inventory
        var mealsCooked = 0

        val outcomes = arrivals.map { customer ->
            val waitMinutes = estimateWaitMinutes(staffingRatio, averageServiceSpeed)
            val suitable = availableDishes.filter { dish ->
                dish.sellingPrice <= customer.budget &&
                    customer.dietaryRequirements.none { requirement -> dish.violates(requirement) }
            }
            val makeable = suitable.filter { dish -> stock?.let { InventoryOperations.canFulfill(it, dish.recipe) } ?: true }

            val missedReason = when {
                suitable.isEmpty() -> MissedMealReason.NOTHING_SUITABLE
                makeable.isEmpty() -> MissedMealReason.OUT_OF_STOCK
                kitchenCapacity != null && mealsCooked >= kitchenCapacity -> MissedMealReason.KITCHEN_OVERWHELMED
                else -> null
            }

            if (missedReason != null) {
                CustomerServiceOutcome(customer, dish = null, satisfaction = missedMealSatisfaction(missedReason), waitMinutes, missedReason)
            } else {
                val dish = pickDish(makeable, rng)
                stock = stock?.let { InventoryOperations.consume(it, dish.recipe) }
                mealsCooked++
                dishesSold[dish.id] = (dishesSold[dish.id] ?: 0) + 1
                val satisfaction = resolveSatisfaction(customer, dish, waitMinutes, kitchenQualityBonus)
                CustomerServiceOutcome(customer, dish, satisfaction, waitMinutes)
            }
        }

        return ServiceResult(outcomes, dishesSold, staffingRatio, inventoryAfter = stock)
    }

    /**
     * How strongly price nudges both choice and satisfaction: a dish priced
     * at its reference is 1.0; double the reference is 0.5; half is 2.0.
     */
    fun valueFactor(dish: Dish): Double {
        if (dish.sellingPrice <= 0) return 2.0
        return (dish.referencePrice.toDouble() / dish.sellingPrice).coerceIn(0.3, 2.0)
    }

    private fun pickDish(dishes: List<Dish>, rng: RandomSource?): Dish {
        val weights = dishes.map { dish -> dish.popularity.coerceAtLeast(1) * valueFactor(dish) * valueFactor(dish) }
        // Without an rng (Phase 3 callers and most unit tests), fall back to
        // the deterministic "everyone orders the favorite" choice.
        if (rng == null) return dishes[weights.indices.maxBy { weights[it] }]

        val roll = rng.nextFloat() * weights.sum()
        var cumulative = 0.0
        for ((dish, weight) in dishes.zip(weights)) {
            cumulative += weight
            if (roll <= cumulative) return dish
        }
        return dishes.last()
    }

    fun missedMealSatisfaction(reason: MissedMealReason): Int = when (reason) {
        // Walked in, saw nothing for them, walked out — no meal, no goodwill.
        MissedMealReason.NOTHING_SUITABLE -> 0
        // Sat down, ordered, and were told there's no food. Barely better.
        MissedMealReason.OUT_OF_STOCK, MissedMealReason.KITCHEN_OVERWHELMED, MissedMealReason.TIRED_OF_WAITING -> 10
    }

    private fun estimateWaitMinutes(staffingRatio: Double, averageServiceSpeed: Double): Double {
        if (averageServiceSpeed <= 0.0) return 90.0 // no one working the floor: an effectively unbounded wait
        val speedFactor = 100.0 / averageServiceSpeed.coerceAtLeast(1.0)
        return (staffingRatio.coerceAtMost(20.0) * speedFactor).coerceIn(1.0, 90.0)
    }

    fun resolveSatisfaction(customer: Customer, dish: Dish, waitMinutes: Double, kitchenQualityBonus: Int): Int {
        val patienceDeficit = (waitMinutes - customer.patience).coerceAtLeast(0.0)
        val waitPenalty = (patienceDeficit * 2).toInt()
        // quality is 0-100; 50 is "unremarkable," neither bonus nor penalty
        val qualityEffect = (dish.quality + kitchenQualityBonus).coerceIn(0, 100) - 50
        // Feeling ripped off stings more than a bargain pleases.
        val valueEffect = if (dish.referencePrice > 0) {
            ((dish.referencePrice - dish.sellingPrice) * 30 / dish.referencePrice).toInt().coerceIn(-25, 10)
        } else {
            0
        }

        return (customer.satisfaction + qualityEffect + valueEffect - waitPenalty).coerceIn(0, 100)
    }
}

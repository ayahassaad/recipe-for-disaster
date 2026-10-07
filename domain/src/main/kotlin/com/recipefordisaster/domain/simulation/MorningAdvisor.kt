package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.menu.Dish
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * What the restaurant costs to keep open each day, whether or not anyone
 * walks in: everyone's wages, rent, utilities, upkeep. Hiring someone adds
 * their salary here for every day they stay, which is the commitment the
 * player needs to see before they say yes.
 */
data class DailyCosts(
    val wages: Long,
    val premises: Long,
) {
    val total: Long get() = wages + premises

    companion object {
        fun of(state: GameState): DailyCosts {
            val staff = state.employees.filter { it.status != EmployeeStatus.QUIT && it.status != EmployeeStatus.FIRED }
            val costs = state.restaurant.costsToday
            return DailyCosts(
                wages = staff.sumOf { it.salaryPerDay },
                premises = costs.rentPerDay + costs.utilitiesPerDay + costs.miscPerDay + state.equipment.sumOf { it.maintenanceCostPerDay },
            )
        }
    }
}

/** A rough "what will tonight earn and cost" so the player can tell a losing day before it happens. */
data class MoneyForecast(
    val expectedIncome: Long,
    val dailyCosts: DailyCosts,
    val expectedIngredientUse: Long,
) {
    val expectedProfit: Long get() = expectedIncome - dailyCosts.total - expectedIngredientUse
}

/** One thing worth the player's attention this morning, with what it would take to fix it. */
sealed interface Advice {
    /** True when ignoring it will very likely cost you tonight. */
    val urgent: Boolean

    /** Not enough of an ingredient for tonight (urgent) or tomorrow. [suggestedQuantity] covers about two nights. */
    data class Restock(val ingredient: Ingredient, val suggestedQuantity: Double, val cost: Long, override val urgent: Boolean) : Advice

    /** More people are coming than the cooks can feed. */
    data class KitchenTooSmall(val expectedCustomers: Int, val kitchenCapacity: Int) : Advice {
        override val urgent = true
    }

    /** The day will probably lose money as things stand. */
    data class LosingMoney(val forecast: MoneyForecast) : Advice {
        override val urgent = true
    }

    data class EquipmentBroken(val equipment: Equipment, val repairCost: Long) : Advice {
        override val urgent = true
    }

    /** Badly worn and likely to break (or catch fire) soon. */
    data class EquipmentWorn(val equipment: Equipment, val repairCost: Long) : Advice {
        override val urgent = false
    }

    data class Dirty(val cleanliness: Int, val deepCleanCost: Long) : Advice {
        override val urgent: Boolean get() = cleanliness < 45
    }

    data class StaffExhausted(val employee: Employee) : Advice {
        override val urgent: Boolean get() = employee.stress >= 85
    }

    data class DishUnmakeable(val dish: Dish) : Advice {
        override val urgent = true
    }
}

/**
 * Turns the morning's state into a short to-do list (Phase 6 UI rework).
 * Pure and deterministic like everything in `:domain`. The UI feeds it the
 * state *after* the player's choices so far, so fixing something makes its
 * item disappear straight away.
 */
object MorningAdvisor {

    private const val RESTOCK_NIGHTS = 2

    /**
     * Everything [adviceFor] suggests buying, as one shopping list — what a
     * single "restock everything" button buys.
     */
    fun restockList(state: GameState): Map<IngredientId, Double> =
        adviceFor(state).filterIsInstance<Advice.Restock>().associate { it.ingredient.id to it.suggestedQuantity }

    /**
     * Roughly how many nights the current stock of an ingredient lasts at
     * tonight's expected demand. `null` if tonight's menu doesn't use it.
     */
    fun nightsOfStock(state: GameState, ingredient: Ingredient): Double? {
        val serving = state.menu.filter { it.available }
        if (serving.isEmpty()) return null
        val perCustomer = serving.sumOf { it.recipe.ingredientRequirements[ingredient.id] ?: 0.0 } / serving.size
        if (perCustomer <= 0.0) return null
        val perNight = perCustomer * OutlookCalculator.forTonight(state).expectedCustomers.coerceAtLeast(1)
        return ingredient.quantityOnHand / perNight
    }

    /** What the restaurant is short of, if anything — so the hiring sign can say what kind of help is wanted. */
    enum class Need { COOK, SERVER, DISHWASHER }

    /** Stress at which a worker is flagged as needing a day off. */
    const val TIRED_STRESS = 60

    /** Stress at which someone is about to collapse: one more hard night and they'll go off sick. */
    const val COLLAPSE_STRESS = 85

    /** Whether the only cook on shift is about to collapse, so a second cook is needed before the kitchen stops. */
    fun onlyCookExhausted(state: GameState): Boolean {
        val cooks = state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.COOK }
        return cooks.size == 1 && cooks.first().stress >= COLLAPSE_STRESS
    }

    fun staffNeed(state: GameState): Need? {
        val working = state.employees.filter { it.status == EmployeeStatus.ACTIVE }
        val outlook = OutlookCalculator.forTonight(state)
        val floorStaff = working.count { it.role == Role.SERVER || it.role == Role.MANAGER }
        return when {
            outlook.kitchenTooSmall -> Need.COOK
            onlyCookExhausted(state) -> Need.COOK
            // The player waits tables too, so a server is only needed once the room is too busy for them.
            outlook.expectedCustomers > (floorStaff + 1) * 12 -> Need.SERVER
            state.restaurant.cleanliness < 55 && working.none { it.role == Role.DISHWASHER } -> Need.DISHWASHER
            else -> null
        }
    }

    fun forecast(state: GameState): MoneyForecast {
        val outlook = OutlookCalculator.forTonight(state)
        val serving = state.menu.filter { it.available && it.id !in outlook.unmakeableDishes }
        val customersFed = minOf(outlook.expectedCustomers, outlook.kitchenCapacity)
        val income: Long
        val ingredients: Long
        if (serving.isEmpty() || customersFed == 0) {
            income = 0
            ingredients = 0
        } else {
            // Customers lean toward popular dishes, so weight the average the same way.
            val totalPopularity = serving.sumOf { it.popularity.coerceAtLeast(1) }.toDouble()
            val averagePrice = serving.sumOf { it.sellingPrice * it.popularity.coerceAtLeast(1).toDouble() } / totalPopularity
            val averagePlateCost = serving.sumOf { OutlookCalculator.plateCost(it, state.inventory) * it.popularity.coerceAtLeast(1) } / totalPopularity
            income = (averagePrice * customersFed).roundToLong()
            ingredients = (averagePlateCost * customersFed).roundToLong()
        }
        return MoneyForecast(expectedIncome = income, dailyCosts = DailyCosts.of(state), expectedIngredientUse = ingredients)
    }

    fun adviceFor(state: GameState): List<Advice> {
        val outlook = OutlookCalculator.forTonight(state)
        val advice = mutableListOf<Advice>()

        state.menu.filter { it.id in outlook.unmakeableDishes }.forEach { advice += Advice.DishUnmakeable(it) }

        val serving = state.menu.filter { it.available }
        state.inventory.ingredients.values.forEach { ingredient ->
            if (serving.isEmpty()) return@forEach
            val perCustomer = serving.sumOf { it.recipe.ingredientRequirements[ingredient.id] ?: 0.0 } / serving.size
            if (perCustomer <= 0.0) return@forEach
            val tonight = perCustomer * outlook.expectedCustomers
            val target = tonight * RESTOCK_NIGHTS
            if (ingredient.quantityOnHand < target) {
                val quantity = ceil(target - ingredient.quantityOnHand).coerceAtLeast(1.0)
                advice += Advice.Restock(
                    ingredient = ingredient,
                    suggestedQuantity = quantity,
                    cost = DecisionApplier.purchaseCost(ingredient.purchasePricePerUnit, quantity),
                    urgent = ingredient.quantityOnHand < tonight,
                )
            }
        }

        if (outlook.kitchenTooSmall) advice += Advice.KitchenTooSmall(outlook.expectedCustomers, outlook.kitchenCapacity)

        state.equipment.forEach { equipment ->
            val cost = EquipmentOperations.repairCost(equipment)
            when {
                EquipmentOperations.isBroken(equipment) -> advice += Advice.EquipmentBroken(equipment, cost)
                // A worn fridge is the one that can break, so flag it as soon as it's worn.
                equipment.condition < (if (com.recipefordisaster.domain.equipment.Fridge.isFridge(equipment)) com.recipefordisaster.domain.equipment.Fridge.WORN else 35) ->
                    advice += Advice.EquipmentWorn(equipment, cost)
            }
        }

        if (state.restaurant.cleanliness < 55) advice += Advice.Dirty(state.restaurant.cleanliness, DecisionApplier.DEEP_CLEAN_COST)

        // Flag tiredness early, while a day off still fixes it, not once they're about to collapse.
        state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.stress >= TIRED_STRESS }.forEach { advice += Advice.StaffExhausted(it) }

        val forecast = forecast(state)
        if (forecast.expectedProfit < 0) advice += Advice.LosingMoney(forecast)

        // Urgent things first; within that, keep the order above (food, then kitchen, then the rest).
        return advice.sortedByDescending { it.urgent }
    }
}

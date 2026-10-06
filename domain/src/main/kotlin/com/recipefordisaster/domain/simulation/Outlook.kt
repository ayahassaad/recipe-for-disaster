package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import kotlin.math.roundToInt

/**
 * A forecast of tonight's service, so the UI can warn before the doors open
 * ("not enough cooks", "running low on lettuce") without reimplementing
 * any rules itself. Everything here is an estimate from the same models
 * the day-tick uses; tonight's actual numbers still come from the RNG.
 */
data class Outlook(
    val expectedCustomers: Int,
    val kitchenCapacity: Int,
    val demandModifierPercent: Int,
    /** Ingredients the current menu uses that probably won't last the night. */
    val lowStock: List<Ingredient>,
    /** Dishes on the menu that can't be made even once with current stock. */
    val unmakeableDishes: Set<DishId>,
) {
    val kitchenTooSmall: Boolean get() = kitchenCapacity < expectedCustomers
}

object OutlookCalculator {

    /** What one plate of [dish] costs in ingredients at today's supplier prices. */
    fun plateCost(dish: Dish, inventory: InventoryState): Double =
        dish.recipe.ingredientRequirements.entries.sumOf { (id, amount) ->
            amount * (inventory.ingredients[id]?.purchasePricePerUnit ?: 0L)
        }

    fun forTonight(state: GameState): Outlook {
        val restaurant = state.restaurant
        val modifier = (100 + state.pendingDemandModifierPercent).coerceAtLeast(0) / 100.0
        val expected = (restaurant.capacity * restaurant.reputation.coerceIn(0, 100) / 100.0 * modifier)
            .roundToInt()
            .coerceIn(0, restaurant.capacity)

        val serving = state.menu.filter { it.available }
        val lowStock = state.inventory.ingredients.values.filter { ingredient ->
            if (serving.isEmpty()) return@filter false
            // Assume orders spread evenly across tonight's menu.
            val perCustomer = serving.sumOf { it.recipe.ingredientRequirements[ingredient.id] ?: 0.0 } / serving.size
            perCustomer > 0.0 && ingredient.quantityOnHand < perCustomer * expected
        }
        val unmakeable = serving.filter { !InventoryOperations.canFulfill(state.inventory, it.recipe) }.map { it.id }.toSet()

        return Outlook(
            expectedCustomers = expected,
            // The night is played live, so what counts is how fast the kitchen cooks while guests keep coming.
            kitchenCapacity = com.recipefordisaster.domain.service.ServiceNight.mealsPerNight(state, expected),
            demandModifierPercent = state.pendingDemandModifierPercent,
            lowStock = lowStock,
            unmakeableDishes = unmakeable,
        )
    }
}

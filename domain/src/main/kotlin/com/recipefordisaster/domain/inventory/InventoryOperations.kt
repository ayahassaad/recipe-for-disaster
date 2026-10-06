package com.recipefordisaster.domain.inventory

import com.recipefordisaster.domain.menu.Recipe

/**
 * Pure functions over [InventoryState] — consumption, spoilage, and the
 * "can we even make this?" check the service simulator needs before it can
 * sell a dish. Kept separate from the [Ingredient]/[InventoryState] data
 * classes themselves so those stay plain data.
 */
object InventoryOperations {

    fun canFulfill(inventory: InventoryState, recipe: Recipe, servings: Int = 1): Boolean =
        recipe.ingredientRequirements.all { (ingredientId, amountPerServing) ->
            val onHand = inventory.ingredients[ingredientId]?.quantityOnHand ?: 0.0
            onHand >= amountPerServing * servings
        }

    fun consume(inventory: InventoryState, recipe: Recipe, servings: Int = 1): InventoryState {
        val updated = inventory.ingredients.toMutableMap()
        for ((ingredientId, amountPerServing) in recipe.ingredientRequirements) {
            val ingredient = updated[ingredientId] ?: continue
            val consumed = amountPerServing * servings
            updated[ingredientId] = ingredient.copy(
                quantityOnHand = (ingredient.quantityOnHand - consumed).coerceAtLeast(0.0),
            )
        }
        return inventory.copy(ingredients = updated)
    }

    /**
     * Applies one day's spoilage to every ingredient. Spoilage is modeled
     * as a flat daily percentage of what's on hand (`spoilageRatePerDay`)
     * rather than a shelf-life countdown — simpler to reason about for
     * MVP, and still enough to create real pressure against over-ordering
     * perishables.
     */
    fun applySpoilage(inventory: InventoryState, coldSpoilsFaster: Boolean = false): InventoryState {
        val updated = inventory.ingredients.mapValues { (_, ingredient) ->
            // With the fridge broken, the cold stuff goes off three times as fast.
            val rate = if (coldSpoilsFaster && com.recipefordisaster.domain.equipment.Fridge.isCold(ingredient)) (ingredient.spoilageRatePerDay * 3).coerceAtMost(1.0) else ingredient.spoilageRatePerDay
            val spoiled = ingredient.quantityOnHand * rate
            ingredient.copy(quantityOnHand = (ingredient.quantityOnHand - spoiled).coerceAtLeast(0.0))
        }
        return inventory.copy(ingredients = updated)
    }

    fun totalStorageUsed(inventory: InventoryState): Double =
        inventory.ingredients.values.sumOf { it.quantityOnHand * it.storageSpaceRequired }

    fun isOverCapacity(inventory: InventoryState): Boolean =
        totalStorageUsed(inventory) > inventory.storageCapacity
}

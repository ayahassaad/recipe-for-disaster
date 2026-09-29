package com.recipefordisaster.domain.inventory

import com.recipefordisaster.domain.menu.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryOperationsTest {

    private val flour = IngredientId("flour")
    private val cheese = IngredientId("cheese")

    private fun stateWith(flourQty: Double, cheeseQty: Double) = InventoryState(
        ingredients = mapOf(
            flour to Ingredient(flour, "Flour", flourQty, "kg", purchasePricePerUnit = 20, spoilageRatePerDay = 0.01, storageSpaceRequired = 1.0),
            cheese to Ingredient(cheese, "Cheese", cheeseQty, "kg", purchasePricePerUnit = 80, spoilageRatePerDay = 0.10, storageSpaceRequired = 1.0),
        ),
        storageCapacity = 1000.0,
        suppliers = emptyList(),
    )

    private val pizzaRecipe = Recipe(
        ingredientRequirements = mapOf(flour to 0.3, cheese to 0.2),
        preparationTimeMinutes = 12,
    )

    @Test
    fun `can fulfill when enough of every ingredient is on hand`() {
        val state = stateWith(flourQty = 5.0, cheeseQty = 5.0)
        assertTrue(InventoryOperations.canFulfill(state, pizzaRecipe, servings = 3))
    }

    @Test
    fun `cannot fulfill when one ingredient is short`() {
        val state = stateWith(flourQty = 5.0, cheeseQty = 0.1)
        assertFalse(InventoryOperations.canFulfill(state, pizzaRecipe, servings = 1))
    }

    @Test
    fun `consuming a recipe reduces exactly the required ingredients`() {
        val state = stateWith(flourQty = 5.0, cheeseQty = 5.0)
        val after = InventoryOperations.consume(state, pizzaRecipe, servings = 2)

        assertEquals(5.0 - 0.6, after.ingredients.getValue(flour).quantityOnHand, 0.0001)
        assertEquals(5.0 - 0.4, after.ingredients.getValue(cheese).quantityOnHand, 0.0001)
    }

    @Test
    fun `spoilage never drives quantity negative`() {
        val state = stateWith(flourQty = 0.001, cheeseQty = 0.0)
        val after = InventoryOperations.applySpoilage(state)

        assertTrue(after.ingredients.values.all { it.quantityOnHand >= 0.0 })
    }

    @Test
    fun `cheese spoils faster than flour given equal starting quantity`() {
        val state = stateWith(flourQty = 10.0, cheeseQty = 10.0)
        val after = InventoryOperations.applySpoilage(state)

        val flourRemaining = after.ingredients.getValue(flour).quantityOnHand
        val cheeseRemaining = after.ingredients.getValue(cheese).quantityOnHand
        assertTrue(cheeseRemaining < flourRemaining)
    }
}

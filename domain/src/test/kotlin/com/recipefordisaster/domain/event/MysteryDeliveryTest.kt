package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.MorningAdvisor
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MysteryDeliveryTest {

    private val start = NewGameFactory.create(seed = 3L)

    private fun withStock(vararg stock: Pair<com.recipefordisaster.domain.inventory.IngredientId, Double>) = start.copy(
        inventory = start.inventory.copy(
            ingredients = start.inventory.ingredients.mapValues { (id, ingredient) ->
                ingredient.copy(quantityOnHand = stock.toMap()[id] ?: 0.5)
            },
            storageCapacity = 1_000.0,
        ),
    )

    @Test
    fun `the crate is the thing the kitchen is shortest of, and covers about two nights`() {
        val state = withStock(RecipeBook.FLOUR to 0.0, RecipeBook.CHEESE to 500.0)
        val before = state.inventory.ingredients.getValue(RecipeBook.FLOUR)
        val nightly = MorningAdvisor.nightlyUse(state, before)!!

        val outcome = SupplyEvents.mysteryDelivery.resolve(state, SeededRandomSource(1L))
        val after = outcome.resultingState.inventory.ingredients

        // Flour is the thing used that's at nothing, so it's the one that arrives.
        val delivered = after.getValue(RecipeBook.FLOUR).quantityOnHand
        assertTrue("delivered $delivered", delivered >= 3.0)
        assertTrue(delivered >= minOf(nightly * 2, 25.0) - 1.0)
        assertEquals(500.0, after.getValue(RecipeBook.CHEESE).quantityOnHand, 0.0)
        assertTrue(outcome.description.contains("two nights"))
    }

    @Test
    fun `a crate never brings more than the pantry has room for`() {
        val state = withStock(RecipeBook.FLOUR to 0.0).let { it.copy(inventory = it.inventory.copy(storageCapacity = 20.0)) }
        val outcome = SupplyEvents.mysteryDelivery.resolve(state, SeededRandomSource(1L))
        val after = outcome.resultingState.inventory
        val usedAfter = after.ingredients.values.sumOf { it.quantityOnHand * it.storageSpaceRequired }

        assertTrue("used $usedAfter of 20", usedAfter <= 20.0 + 1e-6)
    }
}

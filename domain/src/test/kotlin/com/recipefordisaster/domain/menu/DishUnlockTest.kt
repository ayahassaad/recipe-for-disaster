package com.recipefordisaster.domain.menu

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DishUnlockTest {

    private val start = NewGameFactory.create(seed = 3L)
    private val lasagna = RecipeBook.find(RecipeBook.LASAGNA_DISH)!!

    @Test
    fun `new dishes unlock a few days apart`() {
        assertEquals(listOf(4, 8, 12, 14), RecipeBook.dishes.map { RecipeBook.unlockDay(it) }.sorted())
    }

    @Test
    fun `a dish isn't available before its day, and is from then on`() {
        assertFalse(RecipeBook.isUnlocked(start.copy(day = 11), lasagna))
        assertTrue(RecipeBook.isUnlocked(start.copy(day = 12), lasagna))
        assertTrue(RecipeBook.isUnlocked(start.copy(day = 40), lasagna))
    }

    @Test
    fun `trying to add a dish too early does nothing and costs nothing`() {
        val early = start.copy(day = 5)
        val result = DecisionApplier.apply(early, PlayerDecisions(dishesToAdd = setOf(lasagna.id)))

        assertTrue(result.state.menu.none { it.id == lasagna.id })
        assertEquals(early.restaurant.cash, result.state.restaurant.cash)
    }

    @Test
    fun `adding a dish once it's unlocked works`() {
        val later = start.copy(day = 12)
        val result = DecisionApplier.apply(later, PlayerDecisions(dishesToAdd = setOf(lasagna.id)))
        assertTrue(result.state.menu.any { it.id == lasagna.id })
    }
}

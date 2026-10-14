package com.recipefordisaster.domain.restaurant

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationTest {

    private val start = NewGameFactory.create(seed = 6L)

    @Test
    fun `a decoration is bought once and kept`() {
        val rich = start.copy(restaurant = start.restaurant.copy(cash = 5_000))
        val after = DecisionApplier.apply(rich, PlayerDecisions(decorationsToBuy = setOf(Decoration.PAINTINGS)))
        assertEquals(setOf(Decoration.PAINTINGS), after.state.decorations)
        assertEquals(Decoration.PAINTINGS.cost, after.spending.upgrades)
        // Buying it again does nothing.
        assertEquals(0L, DecisionApplier.apply(after.state, PlayerDecisions(decorationsToBuy = setOf(Decoration.PAINTINGS))).spending.upgrades)
    }

    @Test
    fun `nothing is bought without the money, and dearer ones are nicer`() {
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 100))
        assertTrue(DecisionApplier.apply(broke, PlayerDecisions(decorationsToBuy = setOf(Decoration.PIANO))).state.decorations.isEmpty())
        assertTrue(Decoration.PIANO.joy > Decoration.PAINTINGS.joy)
        assertEquals(9, Decoration.joyOf(Decoration.entries.toSet()))
    }
}

package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EverythingFromTheStartTest {

    private val day1 = NewGameFactory.create(seed = 3L)
    private val everything = day1.copy(everythingFromTheStart = true)

    @Test
    fun `normally almost nothing is there on day one`() {
        assertFalse(Progression.has(day1, Feature.DRINKS))
        assertFalse(Progression.has(day1, Feature.POWER_CUT))
    }

    @Test
    fun `with everything from the start, every feature is there, in full`() {
        Feature.entries.forEach { feature ->
            assertTrue("$feature", Progression.has(everything, feature))
            assertEquals("$feature", 1f, Progression.strength(everything, feature), 0f)
            assertFalse("$feature", Progression.isNew(everything, feature))
            assertFalse("$feature", Progression.debutsToday(everything, feature))
        }
        assertTrue(Progression.arrivingOn(everything, 6).isEmpty())
    }

    @Test
    fun `and every dish can be added`() {
        assertTrue(RecipeBook.dishes.none { RecipeBook.isUnlocked(day1, it) })
        assertTrue(RecipeBook.dishes.all { RecipeBook.isUnlocked(everything, it) })
    }
}

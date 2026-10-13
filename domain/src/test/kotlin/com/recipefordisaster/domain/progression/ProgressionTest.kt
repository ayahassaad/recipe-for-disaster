package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.GameStateJson
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val start = NewGameFactory.create(seed = 3L)

    @Test
    fun `a new game meets each feature on its day, one at a time`() {
        assertFalse(Progression.has(start.copy(day = 5), Feature.DRINKS))
        assertTrue(Progression.has(start.copy(day = 6), Feature.DRINKS))
        assertFalse(Progression.has(start.copy(day = 14), Feature.RAT))
        assertTrue(Progression.has(start.copy(day = 15), Feature.RAT))
        // Never two new things on the same day.
        assertEquals(Feature.entries.size, Feature.entries.map { it.day }.toSet().size)
    }

    @Test
    fun `a new feature starts rare and settles in over a few days`() {
        assertEquals(0f, Progression.strength(start.copy(day = 5), Feature.DRINKS), 0.001f)
        val first = Progression.strength(start.copy(day = 6), Feature.DRINKS)
        assertTrue(first >= 0.4f && first < 0.5f)
        assertTrue(Progression.strength(start.copy(day = 8), Feature.DRINKS) > first)
        assertEquals(1f, Progression.strength(start.copy(day = 6 + Progression.RAMP_DAYS - 1), Feature.DRINKS), 0.001f)
    }

    @Test
    fun `a game saved before the schedule keeps everything it had already met`() {
        // An old save, at day 7: it had drinks, the cat and disasters already.
        val json = GameStateJson.instance.encodeToString(GameState.serializer(), start.copy(day = 7, featuresMetEarly = null))
        val old = GameStateJson.instance.decodeFromString(GameState.serializer(), json)
        val loaded = Progression.ensure(old)
        listOf(Feature.DRINKS, Feature.CAT, Feature.RAT, Feature.FRIDGE_BREAKS, Feature.CHEF_MOODS).forEach {
            assertTrue(it.name, Progression.has(loaded, it))
            assertEquals(1f, Progression.strength(loaded, it), 0.001f)
        }
        // Two disasters in a night is new, so it still waits for its day.
        assertFalse(Progression.has(loaded, Feature.DOUBLE_TROUBLE))
        // And what it had is fixed when it's loaded: it doesn't grow with the days.
        assertEquals(loaded.featuresMetEarly, Progression.ensure(loaded.copy(day = 30)).featuresMetEarly)
    }

    @Test
    fun `a new game has met nothing early`() {
        assertEquals(emptySet<Feature>(), start.featuresMetEarly)
        assertEquals(listOf(Feature.DRINKS), Progression.arrivingOn(start, 6))
    }

    @Test
    fun `a feature counts as new for its first few nights`() {
        assertFalse(Progression.isNew(start.copy(day = 5), Feature.DRINKS))
        assertTrue(Progression.isNew(start.copy(day = 6), Feature.DRINKS))
        assertTrue(Progression.isNew(start.copy(day = 8), Feature.DRINKS))
        assertFalse(Progression.isNew(start.copy(day = 9), Feature.DRINKS))
    }

    @Test
    fun `a feature's first day is the day it was promised for`() {
        assertTrue(Progression.debutsToday(start.copy(day = 8), Feature.CAT))
        assertFalse(Progression.debutsToday(start.copy(day = 9), Feature.CAT))
        // A game that already had the cat before doesn't have a first day for it.
        assertFalse(Progression.debutsToday(start.copy(day = 8, featuresMetEarly = setOf(Feature.CAT)), Feature.CAT))
    }
}

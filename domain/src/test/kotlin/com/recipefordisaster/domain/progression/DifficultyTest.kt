package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    private val start = NewGameFactory.create(seed = 3L)
    private fun on(day: Int) = Difficulty.dials(start.copy(day = day))

    @Test
    fun `every dial gets a little harder each day, never easier`() {
        (1 until 50).forEach { day ->
            val today = on(day)
            val tomorrow = on(day + 1)
            assertTrue(tomorrow.patience <= today.patience)
            assertTrue(tomorrow.arrivalGap <= today.arrivalGap)
            assertTrue(tomorrow.chaosChance >= today.chaosChance)
            assertTrue(tomorrow.maxSpills >= today.maxSpills)
            assertTrue(tomorrow.pickiness >= today.pickiness)
        }
    }

    @Test
    fun `no single day is a big jump`() {
        (1 until 50).forEach { day ->
            assertTrue(on(day).patience - on(day + 1).patience < 0.02f)
            assertTrue(on(day).arrivalGap - on(day + 1).arrivalGap < 0.02f)
        }
    }

    @Test
    fun `the first nights are forgiving, and it's as hard as it gets by day forty`() {
        assertTrue(on(1).patience > 1.2f)
        assertTrue(on(1).arrivalGap > 1.2f)
        assertEquals(0, on(1).pickiness)
        assertEquals(on(40), on(60))
        assertTrue(on(40).patience < 0.9f)
    }
}

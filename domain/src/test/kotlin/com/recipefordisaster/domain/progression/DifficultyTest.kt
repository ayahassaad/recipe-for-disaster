package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    private val start = NewGameFactory.create(seed = 3L)
    private fun on(day: Int) = Difficulty.dials(start.copy(day = day))

    @Test
    fun `every dial gets a little harder each day, never easier, apart from the quieter nights`() {
        (1 until 50).filter { !Difficulty.breather(start.copy(day = it)) && !Difficulty.breather(start.copy(day = it + 1)) }.forEach { day ->
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
        (1 until 50).filter { !Difficulty.breather(start.copy(day = it)) && !Difficulty.breather(start.copy(day = it + 1)) }.forEach { day ->
            assertTrue(on(day).patience - on(day + 1).patience < 0.02f)
            assertTrue(on(day).arrivalGap - on(day + 1).arrivalGap < 0.02f)
        }
    }

    @Test
    fun `the first nights are forgiving, and it's as hard as it gets by day forty`() {
        assertTrue(on(1).patience > 1.2f)
        assertTrue(on(1).arrivalGap > 1.2f)
        assertEquals(0, on(1).pickiness)
        assertEquals(on(41), on(61))
        assertTrue(on(40).patience < 0.9f)
    }

    @Test
    fun `every seventh night is a bit quieter than the nights either side`() {
        listOf(14, 21, 28, 35).forEach { day ->
            assertTrue(on(day).patience > on(day - 1).patience)
            assertTrue(on(day).patience > on(day + 1).patience)
            assertTrue(on(day).chaosChance < on(day - 1).chaosChance)
        }
    }
}

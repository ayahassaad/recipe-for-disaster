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

    private fun withNights(profits: List<Long>, satisfaction: Int) = start.copy(
        day = 20,
        recentSatisfaction = satisfaction,
        ledger = start.ledger.copy(
            history = profits.mapIndexed { i, profit ->
                com.recipefordisaster.domain.economy.DailyFinancials(
                    day = 17 + i, revenue = 200 + profit, wages = 200, ingredientCosts = 0, rent = 0, utilities = 0, maintenance = 0, upgrades = 0, miscellaneous = 0,
                )
            },
        ),
    )

    @Test
    fun `a restaurant having a hard time gets a little breathing room, and one cruising along a little more to do`() {
        val fine = Difficulty.dials(withNights(listOf(10, -5, 10), satisfaction = 60))
        val struggling = Difficulty.dials(withNights(listOf(-20, -30, 10), satisfaction = 60))
        val sinking = Difficulty.dials(withNights(listOf(-20, -30, -10), satisfaction = 40))
        val cruising = Difficulty.dials(withNights(listOf(40, 50, 60), satisfaction = 85))
        assertTrue(struggling.patience > fine.patience)
        assertTrue(sinking.patience > struggling.patience)
        assertTrue(cruising.patience < fine.patience)
        // Unhappy guests count too, even when the money's fine.
        assertTrue(Difficulty.dials(withNights(listOf(10, 10, 10), satisfaction = 30)).patience > fine.patience)
    }

    @Test
    fun `a restaurant with more stars draws pickier guests`() {
        fun picky(reputation: Int) = Difficulty.dials(start.copy(day = 20, restaurant = start.restaurant.copy(reputation = reputation))).pickiness
        assertEquals(picky(50), picky(70))
        assertTrue(picky(80) > picky(70))
        assertTrue(picky(95) > picky(80))
    }

    @Test
    fun `small overnight troubles can happen any time, bigger ones ease in later`() {
        fun share(day: Int, severity: com.recipefordisaster.domain.event.Severity) = Difficulty.overnightShare(start.copy(day = day), severity)
        assertEquals(1f, share(3, com.recipefordisaster.domain.event.Severity.MINOR), 0.001f)
        assertEquals(0f, share(4, com.recipefordisaster.domain.event.Severity.MODERATE), 0.001f)
        assertTrue(share(5, com.recipefordisaster.domain.event.Severity.MODERATE) in 0.01f..0.5f)
        assertEquals(0f, share(8, com.recipefordisaster.domain.event.Severity.SEVERE), 0.001f)
        assertEquals(1f, share(20, com.recipefordisaster.domain.event.Severity.SEVERE), 0.001f)
    }
}

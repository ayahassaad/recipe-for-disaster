package com.recipefordisaster.domain.restaurant

import com.recipefordisaster.domain.customer.CustomerFlow
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.CustomerServiceOutcome
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostAndFoundTest {

    private val base = NewGameFactory.create(seed = 8L).copy(day = 10)
    private val dish = base.menu.first()
    private fun guests(count: Int) = (0 until count).map { CustomerFlow.generateCustomer(SeededRandomSource(it + 1L), it) }
    private fun happy(count: Int) = guests(count).map { CustomerServiceOutcome(it, dish, 80, 1.0) }

    @Test
    fun `now and then a guest who enjoyed themselves leaves something behind`() {
        val lost = (1L..40L).sumOf { seed -> LostAndFound.afterNight(base.copy(seed = seed), happy(10)).found.size }
        // About one guest in twelve of 400.
        assertTrue("found $lost", lost in 10..70)
    }

    @Test
    fun `an unhappy guest or one who never ate leaves nothing`() {
        val unhappy = guests(200).map { CustomerServiceOutcome(it, dish, 30, 1.0) }
        val hungry = guests(200).map { CustomerServiceOutcome(it, null, 10, 0.0, MissedMealReason.TIRED_OF_WAITING) }
        assertTrue(LostAndFound.afterNight(base, unhappy).found.isEmpty())
        assertTrue(LostAndFound.afterNight(base, hungry).found.isEmpty())
    }

    @Test
    fun `the same night always loses the same things`() {
        assertEquals(LostAndFound.afterNight(base, happy(100)), LostAndFound.afterNight(base, happy(100)))
    }

    @Test
    fun `the box holds only so much, and the oldest things go first`() {
        val full = (0 until LostAndFound.MAX_ITEMS).map { LostItem(it, "scarf", "Someone", day = 1) }
        val update = LostAndFound.afterNight(base.copy(lostAndFound = full), happy(300))
        assertEquals(LostAndFound.MAX_ITEMS, update.items.size)
        assertTrue(update.thrownOut.isNotEmpty())
        assertTrue(update.thrownOut.containsAll(full))
        assertTrue(update.items.containsAll(update.found.takeLast(LostAndFound.MAX_ITEMS)))
    }

    @Test
    fun `giving something back pleases the owner`() {
        val item = LostItem(1001, "umbrella", "Petunia Fenwick", day = 9)
        val other = LostItem(1002, "scarf", "Cornelius Snoot", day = 9)
        val state = base.copy(lostAndFound = listOf(item, other), tipJar = 10, restaurant = base.restaurant.copy(reputation = 50))
        val result = DecisionApplier.apply(state, PlayerDecisions(givenBack = setOf(1001)))
        assertEquals(listOf(other), result.state.lostAndFound)
        assertEquals(10 + LostAndFound.THANK_YOU_TIP, result.state.tipJar)
        assertEquals(51, result.state.restaurant.reputation)
        assertEquals(state.restaurant.cash, result.state.restaurant.cash)
        assertTrue(result.log.any { "Petunia Fenwick" in it.message })
    }

    @Test
    fun `asking to give back something that isn't there does nothing`() {
        val result = DecisionApplier.apply(base, PlayerDecisions(givenBack = setOf(5)))
        assertEquals(base.tipJar, result.state.tipJar)
    }
}

package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineTest {

    private fun baseState(eventCooldowns: Map<String, Int> = emptyMap(), firedUniqueEventIds: Set<String> = emptySet()) = GameState(
        seed = 1,
        day = 1,
        restaurant = Restaurant(
            cash = 5_000, reputation = 50, cleanliness = 80, capacity = 20, level = 1,
            operatingCosts = OperatingCosts(100, 20, 10), currentDay = 1, status = RestaurantStatus.OPEN,
        ),
        employees = emptyList(),
        customersPresent = emptyList(),
        inventory = InventoryState(emptyMap(), 100.0, emptyList()),
        menu = emptyList(),
        equipment = emptyList(),
        ledger = Ledger(emptyList()),
        log = emptyList(),
        eventCooldowns = eventCooldowns,
        firedUniqueEventIds = firedUniqueEventIds,
    )

    private fun simpleRule(id: String, cooldownDays: Int = 3, unique: Boolean = false, weight: Float = 1f) = EventRule(
        id = id,
        severity = Severity.MINOR,
        cooldownDays = cooldownDays,
        unique = unique,
        prerequisite = { true },
        weight = { weight },
        resolve = { state, _ -> EventOutcome(ruleId = id, description = "test event $id", resultingState = state) },
    )

    @Test
    fun `returns null when there are no rules`() {
        val engine = EventEngine(emptyList())
        assertNull(engine.selectNext(baseState(), SeededRandomSource(1)))
    }

    @Test
    fun `a rule on cooldown is not eligible`() {
        val engine = EventEngine(listOf(simpleRule("A")))
        val state = baseState(eventCooldowns = mapOf("A" to 2))

        assertTrue(engine.availableRules(state).isEmpty())
    }

    @Test
    fun `a unique rule that already fired is not eligible again`() {
        val engine = EventEngine(listOf(simpleRule("A", unique = true)))
        val state = baseState(firedUniqueEventIds = setOf("A"))

        assertTrue(engine.availableRules(state).isEmpty())
    }

    @Test
    fun `firing a rule stamps its cooldown onto the resulting state`() {
        val engine = EventEngine(listOf(simpleRule("A", cooldownDays = 5)))
        val outcome = engine.selectNext(baseState(), SeededRandomSource(1))

        assertEquals(5, outcome?.resultingState?.eventCooldowns?.get("A"))
    }

    @Test
    fun `firing a unique rule records it as fired`() {
        val engine = EventEngine(listOf(simpleRule("A", unique = true)))
        val outcome = engine.selectNext(baseState(), SeededRandomSource(1))

        assertTrue("A" in (outcome?.resultingState?.firedUniqueEventIds ?: emptySet()))
    }

    @Test
    fun `a heavily weighted rule fires far more often than a lightly weighted one`() {
        val engine = EventEngine(listOf(simpleRule("heavy", cooldownDays = 0, weight = 99f), simpleRule("light", cooldownDays = 0, weight = 1f)))
        val rng = SeededRandomSource(seed = 123)

        val picks = (1..200).map { engine.selectNext(baseState(), rng)?.ruleId }
        val heavyCount = picks.count { it == "heavy" }
        val lightCount = picks.count { it == "light" }

        assertTrue(heavyCount > lightCount)
    }

    @Test
    fun `decayCooldowns counts down and drops entries that reach zero`() {
        val decayed = EventEngine.decayCooldowns(mapOf("A" to 1, "B" to 3))

        assertTrue("A" !in decayed)
        assertEquals(2, decayed["B"])
    }
}

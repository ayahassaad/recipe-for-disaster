package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.employee.PersonalityTrait
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.GameStateValidator
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLibraryTest {

    private val fresh: GameState = NewGameFactory.create(seed = 7L).copy(day = 20)

    /** A restaurant where nearly everything has gone wrong, so most rules' prerequisites hold at once. */
    private val troubled: GameState = fresh.copy(
        restaurant = fresh.restaurant.copy(cleanliness = 15, reputation = 65, cash = 300),
        employees = fresh.employees.map {
            it.copy(stress = 95, morale = 10, personalityTraits = setOf(PersonalityTrait.HOTHEADED, PersonalityTrait.GOSSIP, PersonalityTrait.PERFECTIONIST))
        },
        equipment = fresh.equipment.map { it.copy(condition = 20) },
        recentSatisfaction = 30,
    )

    /** A restaurant where everything is going well. */
    private val thriving: GameState = fresh.copy(
        restaurant = fresh.restaurant.copy(cleanliness = 90, reputation = 85),
        employees = fresh.employees.map { it.copy(stress = 10, morale = 90) },
        recentSatisfaction = 85,
    )

    @Test
    fun `the library has between 15 and 25 events with unique IDs`() {
        val ids = EventLibrary.rules.map { it.id }

        assertTrue("${ids.size} rules", ids.size in 15..25)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every eligible rule resolves to a valid state, in good times and bad`() {
        for (state in listOf(fresh, troubled, thriving)) {
            for (rule in EventLibrary.rules.filter { it.prerequisite(state) }) {
                repeat(10) { attempt ->
                    val outcome = rule.resolve(state, SeededRandomSource(seed = attempt.toLong()))
                    assertEquals("rule ${rule.id}", rule.id, outcome.ruleId)
                    assertEquals("rule ${rule.id}", emptyList<String>(), GameStateValidator.validate(outcome.resultingState))
                    assertTrue("rule ${rule.id} has no description", outcome.description.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `weights are never negative`() {
        for (state in listOf(fresh, troubled, thriving)) {
            for (rule in EventLibrary.rules.filter { it.prerequisite(state) }) {
                assertTrue("rule ${rule.id}", rule.weight(state) >= 0f)
            }
        }
    }

    @Test
    fun `a troubled restaurant has far more events in play than a thriving one`() {
        val engine = EventLibrary.engine()
        val troubledWeight = engine.availableRules(troubled).sumOf { it.weight(troubled).toDouble() }
        val thrivingWeight = engine.availableRules(thriving).sumOf { it.weight(thriving).toDouble() }

        assertTrue("troubled $troubledWeight vs thriving $thrivingWeight", troubledWeight > thrivingWeight * 1.5)
    }

    @Test
    fun `follow-up-only rules can't fire by chance`() {
        assertEquals(0f, KitchenEvents.fireInspection.weight(troubled))
    }

    @Test
    fun `a kitchen fire breaks the worst machine and schedules a fire inspection`() {
        val outcome = KitchenEvents.kitchenFire.resolve(troubled, SeededRandomSource(1))

        assertEquals(0, outcome.resultingState.equipment.minOf { it.condition })
        assertTrue("fire_inspection" in outcome.followUpRuleIds)
    }

    @Test
    fun `the health inspector fines a filthy kitchen and rewards a spotless one`() {
        val filthy = KitchenEvents.healthInspection.resolve(troubled, SeededRandomSource(1))
        val spotless = KitchenEvents.healthInspection.resolve(thriving, SeededRandomSource(1))

        assertTrue(filthy.resultingState.restaurant.cash < troubled.restaurant.cash)
        assertTrue(spotless.resultingState.restaurant.reputation > thriving.restaurant.reputation)
    }

    @Test
    fun `loyal staff never quit, however unhappy`() {
        val loyalButMiserable = troubled.copy(employees = troubled.employees.map { it.copy(personalityTraits = setOf(PersonalityTrait.LOYAL)) })

        assertTrue(!StaffEvents.staffQuits.prerequisite(loyalButMiserable))
    }

    @Test
    fun `event money is booked into the ledger so lifetime totals match cash`() {
        val withHistory = troubled.copy(
            ledger = troubled.ledger.copy(
                history = listOf(
                    com.recipefordisaster.domain.economy.DailyFinancials(
                        day = 19, revenue = 0, wages = 0, ingredientCosts = 0, rent = 0, utilities = 0, maintenance = 0, upgrades = 0, miscellaneous = 0,
                    ),
                ),
            ),
        )

        val fined = KitchenEvents.healthInspection.resolve(withHistory, SeededRandomSource(1)).resultingState

        val cashChange = fined.restaurant.cash - withHistory.restaurant.cash
        assertEquals(cashChange, fined.ledger.history.last().eventCashDelta)
    }
}

package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.event.EventLibrary
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-run checks over many seeds, standing in for playtesting until there
 * is some. The point of Phase 6's decisions is that they matter, so the
 * core assertion is simple: a player who restocks, hires and maintains
 * outlasts one who just keeps pressing "Start service."
 *
 * Set the `balanceReport` system property (e.g. via
 * `./gradlew :domain:test -DbalanceReport=true` with the test task
 * forwarding it) to print per-policy survival stats when tuning numbers.
 */
class BalanceSimulationTest {

    private val engine = DefaultDayTickEngine(EventLibrary.engine())
    private val seeds = (1L..40L).toList()
    private val maxDays = System.getProperty("balanceMaxDays")?.toInt() ?: 120

    private data class RunResult(val daysSurvived: Int, val finalStatus: RestaurantStatus, val firedRuleIds: Set<String>)

    private fun play(seed: Long, policy: (GameState) -> PlayerDecisions): RunResult {
        var state = NewGameFactory.create(seed)
        val fired = mutableSetOf<String>()
        while (state.day <= maxDays && state.restaurant.status == RestaurantStatus.OPEN) {
            val rng = SeededRandomSource(state.seed * 6_364_136_223_846_793_005L + state.day)
            val result = engine.advanceDay(state, policy(state), rng)
            assertEquals("seed $seed day ${state.day} produced an invalid state", emptyList<String>(), GameStateValidator.validate(result.newState))
            result.event?.let { fired += it.ruleId }
            state = result.newState
        }
        return RunResult(daysSurvived = state.day - 1, finalStatus = state.restaurant.status, firedRuleIds = fired)
    }

    private fun report(name: String, results: List<RunResult>) {
        if (System.getProperty("balanceReport") == null) return
        val days = results.map { it.daysSurvived }.sorted()
        val statuses = results.groupingBy { it.finalStatus }.eachCount()
        println("[$name] median ${days[days.size / 2]}, min ${days.first()}, max ${days.last()}, outcomes $statuses")
    }

    @Test
    fun `a sensible player survives much longer than one who never makes a decision`() {
        val passive = seeds.map { play(it, BalancePolicies.doNothing) }
        val sensible = seeds.map { play(it, BalancePolicies.sensiblePlayer) }
        report("do nothing", passive)
        report("sensible", sensible)
        report("careless", seeds.map { play(it, BalancePolicies.carelessOwner) })

        val passiveMedian = passive.map { it.daysSurvived }.sorted()[seeds.size / 2]
        val sensibleMedian = sensible.map { it.daysSurvived }.sorted()[seeds.size / 2]
        assertTrue("passive median $passiveMedian vs sensible median $sensibleMedian", sensibleMedian >= passiveMedian * 2)
        // Doing nothing at all should not be a viable strategy...
        assertTrue("a passive run survived $maxDays days", passive.none { it.daysSurvived >= maxDays })
        // ...but sensible play should usually make it a good long way.
        assertTrue("sensible median only $sensibleMedian days", sensibleMedian >= 60)
    }

    @Test
    fun `every event in the library fires at least once across many runs`() {
        val policies = listOf(BalancePolicies.sensiblePlayer, BalancePolicies.carelessOwner, BalancePolicies.doNothing)
        val fired = seeds.flatMap { seed -> policies.flatMap { play(seed, it).firedRuleIds } }.toSet()
        val neverFired = EventLibrary.rules.map { it.id }.toSet() - fired
        if (System.getProperty("balanceReport") != null) println("never fired: $neverFired")
        assertTrue("these events never fired: $neverFired", neverFired.isEmpty())
    }
}

/** Scripted stand-ins for a player, shared by the balance tests. */
internal object BalancePolicies {
    val doNothing: (GameState) -> PlayerDecisions = { PlayerDecisions() }

    /** Keeps the shelves stocked and nothing else: never hires, rests anyone, cleans or repairs. */
    val carelessOwner: (GameState) -> PlayerDecisions = { state -> PlayerDecisions(purchases = restockToPar(state)) }

    private fun restockToPar(state: GameState, parLevel: Double = 10.0): Map<IngredientId, Double> {
        val usedIngredients = state.menu.filter { it.available }.flatMap { it.recipe.ingredientRequirements.keys }.toSet()
        return usedIngredients.mapNotNull { id ->
            val onHand = state.inventory.ingredients.getValue(id).quantityOnHand
            if (onHand < parLevel) id to parLevel - onHand else null
        }.toMap()
    }

    /** A reasonable, not optimal, player: keeps stock topped up, staffs to demand, and does basic upkeep. */
    val sensiblePlayer: (GameState) -> PlayerDecisions = { state ->
        val purchases = restockToPar(state)

        val working = state.employees.filter { it.status != EmployeeStatus.SICK }
        val expectedCustomers = state.restaurant.capacity * state.restaurant.reputation / 100
        val budget = state.restaurant.cash - 300
        val hires = mutableSetOf<EmployeeId>()
        val roomToGrow = state.employees.size < 6 && state.restaurant.cash > 600
        val wantCook = roomToGrow && KitchenModel.mealCapacity(working, state.equipment) < expectedCustomers
        val wantServer = roomToGrow && working.size * 12 < expectedCustomers
        val wantDishwasher = state.restaurant.cleanliness < 55 && state.employees.none { it.role == Role.DISHWASHER }
        state.applicants
            .filter { (wantCook && it.role == Role.COOK) || (wantServer && it.role == Role.SERVER) || (wantDishwasher && it.role == Role.DISHWASHER) }
            .maxByOrNull { it.skill }
            ?.takeIf { StaffingMarket.hiringFee(it) < budget }
            ?.let { hires += it.id }

        val rest = state.employees
            .filter { it.status == EmployeeStatus.ACTIVE && it.stress >= 70 }
            .filter { candidate -> candidate.role != Role.COOK || state.employees.count { it.role == Role.COOK && it.status == EmployeeStatus.ACTIVE } > 1 }
            .take(1)
            .map { it.id }
            .toSet()

        val repairs = state.equipment
            .filter { it.condition < 40 && EquipmentOperations.repairCost(it) < budget }
            .map { it.id }
            .toSet()

        PlayerDecisions(
            purchases = purchases,
            hires = hires,
            restDays = rest,
            repairs = repairs,
            deepClean = state.restaurant.cleanliness < 45 && budget > DecisionApplier.DEEP_CLEAN_COST,
        )
    }
}

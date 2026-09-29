package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.restaurant.Restaurant

/**
 * A complete, immutable snapshot of the run at a single point in time.
 * There is deliberately no mutable shared state anywhere in the simulation
 * engine: every day-tick takes a [GameState] in and produces a brand new
 * one out. That immutability is what makes seeded determinism (section 9
 * of the project brief) actually true rather than just aspirational.
 */
data class GameState(
    val seed: Long,
    val day: Int,
    val restaurant: Restaurant,
    val employees: List<Employee>,
    val customersPresent: List<Customer>,
    val inventory: InventoryState,
    val menu: List<Dish>,
    val equipment: List<Equipment>,
    val ledger: Ledger,
    val log: List<SimulationLogEntry>,
)

data class SimulationLogEntry(
    val day: Int,
    val message: String,
)

/**
 * Choices the player made before/during a day — what to buy, who to
 * schedule, prices to set, which event options to pick, etc. Left empty for
 * now; Phase 5 (Core UI) is what will grow this out to match whatever
 * decisions the UI actually exposes, so it isn't invented speculatively here.
 */
data class PlayerDecisions(
    val placeholder: Unit = Unit,
)

data class DayResult(
    val newState: GameState,
    val log: List<SimulationLogEntry>,
)

/**
 * The core simulation loop: advances one in-game day. This is the single
 * function the entire game is built around — everything else (UI, save
 * data, event engine) exists to feed it inputs or read its outputs.
 *
 * TODO(Phase 3): implement the actual day-advancement rules (revenue,
 * customer flow, employee behavior, inventory consumption/spoilage,
 * equipment wear, event resolution). Phase 2 only establishes the shape of
 * the function and its inputs/outputs.
 */
fun interface DayTickEngine {
    fun advanceDay(state: GameState, decisions: PlayerDecisions, rng: RandomSource): DayResult
}

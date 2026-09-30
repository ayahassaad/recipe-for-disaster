package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.restaurant.Restaurant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A complete, immutable snapshot of the run at a single point in time.
 * There is deliberately no mutable shared state anywhere in the simulation
 * engine: every day-tick takes a [GameState] in and produces a brand new
 * one out. That immutability is what makes seeded determinism (section 9
 * of the project brief) actually true rather than just aspirational.
 *
 * `@Serializable` is what lets [GameState] be persisted directly as JSON
 * (Phase 4) without a parallel set of hand-written DTO/entity classes in
 * `:data` — see [GameStateJson] for the shared serialization config, and
 * [com.recipefordisaster.domain.simulation.GameStateValidator] for the
 * sanity checks a deserialized save is put through before it's trusted.
 *
 * Every field added since Phase 2 (`eventCooldowns`, `firedUniqueEventIds`,
 * `dishSalesTotals`) carries a default value specifically so an older saved
 * JSON blob missing that key still decodes cleanly — the intended way this
 * schema evolves over time, in place of a traditional SQL migration.
 */
@Serializable
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
    /** Rule ID -> days remaining before that event can be considered again. */
    val eventCooldowns: Map<String, Int> = emptyMap(),
    /** Rule IDs already fired for rules marked `unique`, so they never fire twice in a run. */
    val firedUniqueEventIds: Set<String> = emptySet(),
    /** Running lifetime count of each dish sold, for the eventual end-of-run "best-selling dish" stat. */
    val dishSalesTotals: Map<String, Int> = emptyMap(),
)

@Serializable
data class SimulationLogEntry(
    val day: Int,
    val message: String,
)

/**
 * The single shared JSON configuration for reading/writing a [GameState].
 * `allowStructuredMapKeys` is needed because several domain maps are keyed
 * by a value class (e.g. `Map<EmployeeId, RelationshipScore>`) rather than
 * a plain String — with it enabled, kotlinx.serialization encodes those as
 * a flat array of [key, value, key, value, ...] instead of a JSON object,
 * which is a bit less pretty to eyeball by hand but requires no parallel
 * DTO layer just to flatten IDs into strings. `ignoreUnknownKeys` is the
 * other half of the forward-compatibility story: a future field added to
 * some nested type won't break loading an older save, it'll just be absent
 * (and use its default, if it has one) going the other direction.
 */
object GameStateJson {
    val instance: Json = Json {
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }
}

/**
 * Choices the player made before/during a day — what to buy, who to
 * schedule, prices to set, which event options to pick, etc. Left empty for
 * now; Phase 5 (Core UI) is what will grow this out to match whatever
 * decisions the UI actually exposes, so it isn't invented speculatively here.
 * Not persisted (it's a per-tick input, not part of the snapshot), so it's
 * deliberately not `@Serializable`.
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
 * See [DefaultDayTickEngine] for the real implementation.
 */
fun interface DayTickEngine {
    fun advanceDay(state: GameState, decisions: PlayerDecisions, rng: RandomSource): DayResult
}

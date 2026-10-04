package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.event.Severity
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
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
    /** People currently asking for a job (Phase 6). Refreshed every few days; hiring one removes them. */
    val applicants: List<Employee> = emptyList(),
    /** Yesterday's average customer satisfaction (0-100), so events can react to how service has been going. */
    val recentSatisfaction: Int = 50,
    /** One-day demand swing set by events (festival, storm, tour bus...), applied to the next day and then cleared. */
    val pendingDemandModifierPercent: Int = 0,
    /** Follow-up rule IDs an earlier event scheduled; [com.recipefordisaster.domain.event.EventEngine] fires these first. */
    val scheduledFollowUps: Set<String> = emptySet(),
)

@Serializable
data class SimulationLogEntry(
    val day: Int,
    val message: String,
    val tone: LogTone = LogTone.NEUTRAL,
)

/** Whether a log line is good news, bad news, or neither — lets the UI color it without parsing text. */
@Serializable
enum class LogTone {
    GOOD,
    NEUTRAL,
    BAD,
}

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
 * Everything the player decided before opening for the day (Phase 6):
 * staffing, pricing, purchasing, menu changes and upkeep. Applied by
 * [com.recipefordisaster.domain.decision.DecisionApplier] at the start of
 * the day-tick, in a fixed order, skipping anything that can't be afforded
 * or no longer makes sense (e.g. hiring an applicant who has since left).
 * Every field defaults to "change nothing," so `PlayerDecisions()` is a
 * valid "just open as-is" day.
 *
 * Not persisted (it's a per-tick input, not part of the snapshot), so it's
 * deliberately not `@Serializable`.
 */
data class PlayerDecisions(
    /** Ingredient -> extra quantity to buy, in that ingredient's own unit. */
    val purchases: Map<IngredientId, Double> = emptyMap(),
    /** Dish -> new selling price. Clamped to [com.recipefordisaster.domain.decision.PriceRules]. */
    val priceChanges: Map<DishId, Long> = emptyMap(),
    /** Dish -> whether it's on tonight's menu. */
    val menuAvailability: Map<DishId, Boolean> = emptyMap(),
    /** Recipes from [com.recipefordisaster.domain.menu.RecipeBook] to add to the menu. */
    val dishesToAdd: Set<DishId> = emptySet(),
    /** Applicant IDs (from [GameState.applicants]) to hire. */
    val hires: Set<EmployeeId> = emptySet(),
    val fires: Set<EmployeeId> = emptySet(),
    /** Employees given today off: unpaid, not working, and they come back much less stressed. */
    val restDays: Set<EmployeeId> = emptySet(),
    val repairs: Set<EquipmentId> = emptySet(),
    val deepClean: Boolean = false,
)

data class DayResult(
    val newState: GameState,
    val log: List<SimulationLogEntry>,
    /** The event that fired at the end of this day, if any — surfaced separately so the UI can headline it. */
    val event: FiredEvent? = null,
)

data class FiredEvent(
    val ruleId: String,
    val title: String,
    val description: String,
    val severity: Severity,
    val tone: LogTone,
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

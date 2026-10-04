package com.recipefordisaster.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.SaveLoadResult
import com.recipefordisaster.domain.decision.AppliedDecisions
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.decision.DecisionSpending
import com.recipefordisaster.domain.decision.PriceRules
import com.recipefordisaster.domain.economy.DailyFinancials
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.simulation.DaySummary
import com.recipefordisaster.domain.simulation.DayTickEngine
import com.recipefordisaster.domain.simulation.FiredEvent
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.MorningAdvisor
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.SimulationLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * UI state for the main play screen.
 *
 * A day has two phases. In the **morning** the player hires, buys, repairs
 * and so on; each choice shows up straight away (the new cook is in the
 * team, the cash has gone down) and can be undone by tapping it again until
 * service starts. Under the hood those choices are a [plan] over the
 * morning's starting [state], and [preview] is the domain's own answer to
 * "what does the restaurant look like with that plan" — computed by the
 * same [DecisionApplier] the day-tick uses, so what the screen shows is
 * exactly what happens. Screens should read [morning] (the restaurant as
 * the player has arranged it) and [cashNow], not [state].
 *
 * When service ends, [report] holds the results until the player moves on
 * to the next morning.
 */
sealed interface GameUiState {
    data object Loading : GameUiState
    data class Playing(
        val state: GameState,
        val dayLog: List<SimulationLogEntry>,
        val lastEvent: FiredEvent? = null,
        val plan: PlayerDecisions = PlayerDecisions(),
        val preview: AppliedDecisions = DecisionApplier.apply(state, plan),
        val report: DayReport? = null,
        /** True on a brand-new game until the player has read "How to play". */
        val showIntro: Boolean = false,
    ) : GameUiState {
        /** The restaurant as the player has set it up this morning. */
        val morning: GameState get() = preview.state

        /** Cash after everything chosen so far this morning. */
        val cashNow: Long get() = state.restaurant.cash - preview.spending.total
    }
    data class Error(val message: String) : GameUiState
}

/**
 * Everything the end-of-day results screen shows. [morningSpending] is
 * kept so the screen can separate the player's one-off choices (hiring,
 * cleaning, menu) from the restaurant's fixed daily costs, which the books
 * file together.
 */
data class DayReport(
    val summary: DaySummary,
    val books: DailyFinancials,
    val event: FiredEvent?,
    val morningSpending: DecisionSpending,
)

/**
 * Drives the core loop: start or resume a run, set up the morning
 * (Phase 6 decisions), start service, read the results, next morning. No simulation logic lives
 * here — every rule about what a decision or a day *does* stays in
 * `:domain` ([DecisionApplier], [DayTickEngine]); this class only edits
 * the draft plan, sequences calls, and turns results into UI state.
 */
class GameViewModel(
    private val gameRepository: GameRepository,
    private val dayTickEngine: DayTickEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun startNewGame() {
        viewModelScope.launch {
            _uiState.value = GameUiState.Loading
            // Choosing the seed for a brand-new run is a one-time, non-
            // simulation decision — see the doc comment on NewGameFactory
            // for why this doesn't conflict with RandomSource being the
            // sole entry point for randomness inside a day-tick.
            val seed = Random.nextLong()
            val newState = NewGameFactory.create(seed)
            gameRepository.save(newState)
            _uiState.value = GameUiState.Playing(newState, dayLog = emptyList(), showIntro = true)
        }
    }

    fun continueGame() {
        viewModelScope.launch {
            _uiState.value = GameUiState.Loading
            when (val result = gameRepository.load()) {
                is SaveLoadResult.Success -> _uiState.value = GameUiState.Playing(result.state, dayLog = emptyList())
                is SaveLoadResult.NoSaveFound -> _uiState.value = GameUiState.Error("There's no saved game to continue.")
                is SaveLoadResult.Corrupted -> _uiState.value = GameUiState.Error("Your save couldn't be read (${result.reason}). Start a new game instead.")
            }
        }
    }

    /** Runs tonight's service with everything chosen this morning, then shows the results. */
    fun startService() {
        val current = _uiState.value
        if (current !is GameUiState.Playing || current.report != null) return

        viewModelScope.launch {
            // Per-day deterministic seed, derived from the run's base seed
            // and the day number, rather than one continuous RNG stream
            // kept alive in memory for the whole run. This makes any single
            // day's outcome reproducible from the state it started from
            // (base seed + day number), which is what matters for testing
            // and debugging. It does NOT currently guarantee that replaying
            // an entire run from day 1 after an app restart mid-run
            // produces byte-identical results to an uninterrupted run,
            // since we don't persist RNG stream position — only the base
            // seed. That would need an explicit "RNG position" field on
            // GameState; flagging it as a known gap rather than pretending
            // full-run replay determinism is already solved.
            val dailySeed = current.state.seed * 6_364_136_223_846_793_005L + current.state.day
            val rng = SeededRandomSource(dailySeed)

            val result = dayTickEngine.advanceDay(current.state, current.plan, rng)
            gameRepository.save(result.newState)
            val report = result.summary?.let { summary ->
                result.newState.ledger.history.lastOrNull()?.let { books -> DayReport(summary, books, result.event, current.preview.spending) }
            }
            _uiState.value = GameUiState.Playing(result.newState, dayLog = result.log, lastEvent = result.event, report = report)
        }
    }

    fun dismissIntro() {
        _uiState.update { current -> if (current is GameUiState.Playing) current.copy(showIntro = false) else current }
    }

    /** Leaves the results screen for the next morning. */
    fun nextMorning() {
        _uiState.update { current -> if (current is GameUiState.Playing) current.copy(report = null) else current }
    }

    // --- The morning. Each of these edits the plan, so the change shows straight away and tapping again undoes it. ---

    /** Adds [delta] (may be negative) to the planned purchase of an ingredient, never below zero. */
    fun adjustPurchase(id: IngredientId, delta: Double) = editPlan { plan ->
        val updated = ((plan.purchases[id] ?: 0.0) + delta).coerceAtLeast(0.0)
        plan.copy(purchases = if (updated > 0.0) plan.purchases + (id to updated) else plan.purchases - id)
    }

    fun clearPurchases() = editPlan { it.copy(purchases = emptyMap()) }

    /** Buys everything the morning advice says is running low, in one go. */
    fun restockAll() {
        val current = _uiState.value as? GameUiState.Playing ?: return
        val list = MorningAdvisor.restockList(current.morning)
        if (list.isEmpty()) return
        editPlan { plan ->
            val merged = plan.purchases.toMutableMap()
            list.forEach { (id, quantity) -> merged.merge(id, quantity, Double::plus) }
            plan.copy(purchases = merged)
        }
    }

    fun adjustPrice(id: DishId, delta: Long) = editPlan { plan ->
        val dish = currentState()?.menu?.firstOrNull { it.id == id } ?: return@editPlan plan
        val current = plan.priceChanges[id] ?: dish.sellingPrice
        val updated = PriceRules.clamp(dish, current + delta)
        plan.copy(priceChanges = if (updated == dish.sellingPrice) plan.priceChanges - id else plan.priceChanges + (id to updated))
    }

    fun setDishAvailable(id: DishId, available: Boolean) = editPlan { plan ->
        val dish = currentState()?.menu?.firstOrNull { it.id == id } ?: return@editPlan plan
        plan.copy(menuAvailability = if (available == dish.available) plan.menuAvailability - id else plan.menuAvailability + (id to available))
    }

    fun toggleAddDish(id: DishId) = editPlan { it.copy(dishesToAdd = it.dishesToAdd.toggle(id)) }

    fun toggleHire(id: EmployeeId) = editPlan { it.copy(hires = it.hires.toggle(id)) }

    /** Firing and resting the same person are mutually exclusive, so choosing one clears the other. */
    fun toggleFire(id: EmployeeId) = editPlan { it.copy(fires = it.fires.toggle(id), restDays = it.restDays - id) }

    fun toggleRestDay(id: EmployeeId) = editPlan { it.copy(restDays = it.restDays.toggle(id), fires = it.fires - id) }

    fun toggleRepair(id: EquipmentId) = editPlan { it.copy(repairs = it.repairs.toggle(id)) }

    fun toggleDeepClean() = editPlan { it.copy(deepClean = !it.deepClean) }

    private fun currentState(): GameState? = (_uiState.value as? GameUiState.Playing)?.state

    private fun editPlan(transform: (PlayerDecisions) -> PlayerDecisions) {
        _uiState.update { current ->
            if (current !is GameUiState.Playing || current.report != null) return@update current
            val plan = transform(current.plan)
            current.copy(plan = plan, preview = DecisionApplier.apply(current.state, plan))
        }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}

class GameViewModelFactory(
    private val gameRepository: GameRepository,
    private val dayTickEngine: DayTickEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
            "GameViewModelFactory can only create GameViewModel, got $modelClass"
        }
        return GameViewModel(gameRepository, dayTickEngine) as T
    }
}

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
import com.recipefordisaster.domain.service.NightInProgress
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.ServiceSetup
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
        /** Tonight's service while it's being played; null in the morning and on the results screen. */
        val night: NightSession? = null,
    ) : GameUiState {
        /** The restaurant as the player has set it up this morning. */
        val morning: GameState get() = preview.state

        /** Cash after everything chosen so far this morning. */
        val cashNow: Long get() = state.restaurant.cash - preview.spending.total
    }
    data class Error(val message: String) : GameUiState
}

/**
 * A night of service in progress. [opening] is the night as the doors open;
 * the screen plays it forward (time, taps) and hands the finished night
 * back to [GameViewModel.finishService].
 */
data class NightSession(
    val setup: ServiceSetup,
    val opening: ServiceNight,
    val morningSpending: DecisionSpending,
    val dailySeed: Long,
)

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
    /** The restaurant as it was when the doors opened — what the service animation shows. */
    val startOfService: GameState,
    /** Of the guests who gave up waiting, how many were waiting for a table, to order, or for food. */
    val gaveUp: Map<ServiceNight.WaitedFor, Int> = emptyMap(),
    /** How tonight's special guests found it. */
    val specials: List<com.recipefordisaster.domain.service.SpecialVisit> = emptyList(),
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
    private val dayTickEngine: DefaultDayTickEngine,
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
            gameRepository.clearNight()
            _uiState.value = GameUiState.Playing(newState, dayLog = emptyList(), showIntro = true)
        }
    }

    fun continueGame() {
        viewModelScope.launch {
            _uiState.value = GameUiState.Loading
            when (val result = gameRepository.load()) {
                is SaveLoadResult.Success -> {
                    // Saves from before the fridge existed get one.
                    val state = com.recipefordisaster.domain.equipment.Fridge.ensure(result.state).let { s ->
                        // Older saves may be missing a kind of applicant: there's always one of each to hire now.
                        s.copy(
                            applicants = com.recipefordisaster.domain.employee.StaffingMarket.withEveryBasicRole(s.applicants, com.recipefordisaster.domain.simulation.SeededRandomSource(s.seed + s.day), s.day),
                            // The room now holds at most nine tables.
                            restaurant = s.restaurant.copy(tables = s.restaurant.tables.coerceAtMost(com.recipefordisaster.domain.restaurant.TableGrowth.MAX)),
                        )
                    }
                    // If the app was closed during service, pick the night back up where it was left.
                    val night = gameRepository.loadNight()?.takeIf { it.setup.original == result.state }
                    _uiState.value = GameUiState.Playing(
                        if (night != null) result.state else state,
                        dayLog = emptyList(),
                        night = night?.let { NightSession(it.setup, it.night, it.morningSpending, it.dailySeed) },
                    )
                }
                is SaveLoadResult.NoSaveFound -> _uiState.value = GameUiState.Error("There's no saved game to continue.")
                is SaveLoadResult.Corrupted -> _uiState.value = GameUiState.Error("Your save couldn't be read (${result.reason}). Start a new game instead.")
            }
        }
    }

    /**
     * Opens the doors: carries out the morning and starts tonight's service
     * for the player to play. The night is saved as it goes (see
     * [saveNightProgress]); the day itself is saved once the night is over.
     */
    fun startService() {
        val current = _uiState.value
        if (current !is GameUiState.Playing || current.report != null || current.night != null) return
        // Per-day seed from the run's seed and the day number, so any day can be reproduced from where it started.
        val dailySeed = current.state.seed * 6_364_136_223_846_793_005L + current.state.day
        val setup = dayTickEngine.openService(current.state, current.plan, SeededRandomSource(dailySeed))
        val night = ServiceNight.open(setup, SeededRandomSource(dailySeed + 1))
        val session = NightSession(setup, night, current.preview.spending, dailySeed)
        _uiState.value = current.copy(night = session)
        viewModelScope.launch { gameRepository.saveNight(NightInProgress(setup, night, session.morningSpending, dailySeed)) }
    }

    /** Saves how far the night has got, so closing the app mid-service doesn't lose it. */
    fun saveNightProgress(night: ServiceNight) {
        val session = (_uiState.value as? GameUiState.Playing)?.night ?: return
        if (night.finished) return
        // Not cancellable: this also runs as the player leaves the game, when the screen is being torn down.
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            gameRepository.saveNight(NightInProgress(session.setup, night, session.morningSpending, session.dailySeed))
        }
    }

    /** The last guest has gone: close the books on the night the player played, save, and show the results. */
    fun finishService(finalNight: ServiceNight) {
        val current = _uiState.value
        if (current !is GameUiState.Playing) return
        val session = current.night ?: return
        viewModelScope.launch {
            val result = dayTickEngine.closeService(session.setup, finalNight.result(), SeededRandomSource(session.dailySeed + 2))
            val newState = com.recipefordisaster.domain.equipment.Fridge.ensure(result.newState)
            gameRepository.save(newState)
            gameRepository.clearNight()
            // Days this restaurant has made it through so far, towards the best run.
            gameRepository.recordRun(com.recipefordisaster.domain.simulation.BestRun(days = newState.day - 1, name = newState.restaurant.displayName))
            val report = result.summary?.let { summary ->
                result.newState.ledger.history.lastOrNull()?.let { books -> DayReport(summary, books, result.event, session.morningSpending, session.setup.morning.state, finalNight.gaveUpCounts(), finalNight.specialVisits) }
            }
            _uiState.value = GameUiState.Playing(newState, dayLog = result.log, lastEvent = result.event, report = report)
        }
    }

    /** Leaves "How to play" with the name the player gave their restaurant, and saves it. */
    fun dismissIntro(name: String) {
        val current = _uiState.value as? GameUiState.Playing ?: return
        val clean = name.trim().take(com.recipefordisaster.domain.restaurant.MAX_NAME_LENGTH)
        val named = current.state.copy(restaurant = current.state.restaurant.copy(name = clean))
        _uiState.value = current.copy(state = named, preview = DecisionApplier.apply(named, current.plan), showIntro = false)
        viewModelScope.launch { gameRepository.save(named) }
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
    fun toggleUpgrade(id: EquipmentId) = editPlan { it.copy(upgrades = it.upgrades.toggle(id)) }

    fun toggleDeepClean() = editPlan { it.copy(deepClean = !it.deepClean) }
    fun toggleBuyTable() = editPlan { it.copy(buyTable = !it.buyTable) }

    private fun currentState(): GameState? = (_uiState.value as? GameUiState.Playing)?.state

    private fun editPlan(transform: (PlayerDecisions) -> PlayerDecisions) {
        _uiState.update { current ->
            if (current !is GameUiState.Playing || current.report != null || current.night != null) return@update current
            val plan = transform(current.plan)
            current.copy(plan = plan, preview = DecisionApplier.apply(current.state, plan))
        }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}

class GameViewModelFactory(
    private val gameRepository: GameRepository,
    private val dayTickEngine: DefaultDayTickEngine,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
            "GameViewModelFactory can only create GameViewModel, got $modelClass"
        }
        return GameViewModel(gameRepository, dayTickEngine) as T
    }
}

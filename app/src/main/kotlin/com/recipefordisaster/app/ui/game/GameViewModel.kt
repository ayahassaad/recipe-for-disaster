package com.recipefordisaster.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.SaveLoadResult
import com.recipefordisaster.domain.simulation.DayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.SimulationLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * UI state for the main play screen. A [GameState] carries everything the
 * dashboard needs to render; [dayLog] is just the entries produced by the
 * *most recent* day, kept separate so the UI can highlight "what just
 * happened" distinctly from the full running history in [GameState.log].
 */
sealed interface GameUiState {
    data object Loading : GameUiState
    data class Playing(val state: GameState, val dayLog: List<SimulationLogEntry>) : GameUiState
    data class Error(val message: String) : GameUiState
}

/**
 * Drives the single-action core loop approved for Phase 5: start or resume
 * a run, then repeatedly call "Open for the day." No simulation logic
 * lives here — every rule about what a day *does* stays in `:domain`
 * ([DayTickEngine]); this class only sequences calls to it and to
 * [GameRepository], and turns the result into UI state.
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
            _uiState.value = GameUiState.Playing(newState, dayLog = emptyList())
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

    fun openForTheDay() {
        val current = _uiState.value
        if (current !is GameUiState.Playing) return

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

            val result = dayTickEngine.advanceDay(current.state, PlayerDecisions(), rng)
            gameRepository.save(result.newState)
            _uiState.value = GameUiState.Playing(result.newState, dayLog = result.log)
        }
    }
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

package com.recipefordisaster.app.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipefordisaster.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for [com.recipefordisaster.app.ui.start.StartScreen]: just enough
 * to know whether "Continue" should be enabled. No simulation logic lives
 * here — that's [GameRepository]'s and `:domain`'s job.
 */
data class StartUiState(
    val isLoading: Boolean = true,
    val hasExistingSave: Boolean = false,
)

class StartViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState: StateFlow<StartUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-checks for an existing save. Called on init, and again when returning here after a game ends. */
    fun refresh() {
        viewModelScope.launch {
            val hasSave = gameRepository.hasExistingSave()
            _uiState.update { it.copy(isLoading = false, hasExistingSave = hasSave) }
        }
    }
}

class StartViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StartViewModel::class.java)) {
            "StartViewModelFactory can only create StartViewModel, got $modelClass"
        }
        return StartViewModel(gameRepository) as T
    }
}

package com.recipefordisaster.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.recipefordisaster.app.AppContainer
import com.recipefordisaster.app.ui.game.GameScreen
import com.recipefordisaster.app.ui.game.GameViewModel
import com.recipefordisaster.app.ui.game.GameViewModelFactory
import com.recipefordisaster.app.ui.game.actions
import com.recipefordisaster.app.ui.start.StartScreen
import com.recipefordisaster.app.ui.start.StartViewModel
import com.recipefordisaster.app.ui.start.StartViewModelFactory

private const val ROUTE_START = "start"
private const val ROUTE_GAME = "game?isNewGame={isNewGame}"
private const val ARG_IS_NEW_GAME = "isNewGame"

/**
 * The whole Phase 5 navigation graph: Start -> Game. "Game" covers both the
 * day-to-day dashboard and the game-over summary — [GameScreen] decides
 * which to render from the loaded [com.recipefordisaster.domain.simulation.GameState],
 * so there's no separate route (and no separate back-stack entry, avoiding
 * a "back" press from game-over returning to a mid-run dashboard) for it.
 */
@Composable
fun RecipeForDisasterNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_START) {
        composable(ROUTE_START) {
            val viewModel: StartViewModel = viewModel(
                factory = StartViewModelFactory(appContainer.gameRepository),
            )
            // The Start destination's ViewModel survives while this entry
            // stays on the back stack (e.g. while a game is in progress on
            // top of it), so re-check for a save each time this screen is
            // (re)entered — otherwise "Continue" would stay stuck showing
            // whatever was true before that game started.
            LaunchedEffect(Unit) { viewModel.refresh() }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            StartScreen(
                uiState = uiState,
                onNewGame = { navController.navigate("game?isNewGame=true") },
                onContinueGame = { navController.navigate("game?isNewGame=false") },
            )
        }

        composable(
            route = ROUTE_GAME,
            arguments = listOf(navArgument(ARG_IS_NEW_GAME) { type = NavType.BoolType; defaultValue = false }),
        ) { backStackEntry ->
            val isNewGame = backStackEntry.arguments?.getBoolean(ARG_IS_NEW_GAME) ?: false
            val viewModel: GameViewModel = viewModel(
                factory = GameViewModelFactory(appContainer.gameRepository, appContainer.dayTickEngine),
            )

            LaunchedEffect(Unit) {
                if (isNewGame) viewModel.startNewGame() else viewModel.continueGame()
            }

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            GameScreen(
                uiState = uiState,
                actions = viewModel.actions(),
                onBackToStart = {
                    navController.popBackStack(ROUTE_START, inclusive = false)
                },
            )
        }
    }
}

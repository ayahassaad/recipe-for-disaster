package com.recipefordisaster.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.restaurant.RestaurantStatus

/**
 * Everything the player can do on the game screen, bundled so the screen
 * (and its tests/previews) take one parameter instead of a dozen lambdas.
 * [GameViewModel.actions] wires these to the ViewModel.
 */
data class GameActions(
    val onStartService: () -> Unit = {},
    val onNextMorning: () -> Unit = {},
    val onDismissIntro: () -> Unit = {},
    val onRestockAll: () -> Unit = {},
    val onAdjustPurchase: (IngredientId, Double) -> Unit = { _, _ -> },
    val onClearPurchases: () -> Unit = {},
    val onAdjustPrice: (DishId, Long) -> Unit = { _, _ -> },
    val onSetDishAvailable: (DishId, Boolean) -> Unit = { _, _ -> },
    val onToggleAddDish: (DishId) -> Unit = {},
    val onToggleHire: (EmployeeId) -> Unit = {},
    val onToggleFire: (EmployeeId) -> Unit = {},
    val onToggleRestDay: (EmployeeId) -> Unit = {},
    val onToggleRepair: (EquipmentId) -> Unit = {},
    val onToggleDeepClean: () -> Unit = {},
)

fun GameViewModel.actions(): GameActions = GameActions(
    onStartService = ::startService,
    onNextMorning = ::nextMorning,
    onDismissIntro = ::dismissIntro,
    onRestockAll = ::restockAll,
    onAdjustPurchase = ::adjustPurchase,
    onClearPurchases = ::clearPurchases,
    onAdjustPrice = ::adjustPrice,
    onSetDishAvailable = ::setDishAvailable,
    onToggleAddDish = ::toggleAddDish,
    onToggleHire = ::toggleHire,
    onToggleFire = ::toggleFire,
    onToggleRestDay = ::toggleRestDay,
    onToggleRepair = ::toggleRepair,
    onToggleDeepClean = ::toggleDeepClean,
)

/** The three places off the main screen. */
internal enum class Place { STAFF, FOOD, KITCHEN }

/**
 * The play screen. One thing at a time: "How to play" on a brand-new
 * game, then each day's morning (one screen, plus Staff / Food / Kitchen
 * one tap away), the bill after service, and the final bill when the run
 * ends.
 */
@Composable
fun GameScreen(
    uiState: GameUiState,
    actions: GameActions,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is GameUiState.Loading -> Scaffold(modifier = modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        }
        is GameUiState.Error -> Scaffold(modifier = modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = uiState.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackToStart) { Text(stringResource(R.string.game_back_to_start)) }
            }
        }
        is GameUiState.Playing -> {
            val status = uiState.state.restaurant.status
            val gameOver = status == RestaurantStatus.BANKRUPT || status == RestaurantStatus.CONDEMNED
            var place by rememberSaveable { mutableStateOf<Place?>(null) }
            when {
                uiState.showIntro -> IntroScreen(onStart = actions.onDismissIntro, modifier = modifier)
                uiState.report != null -> BillScreen(
                    report = uiState.report,
                    gameOver = gameOver,
                    onContinue = {
                        place = null
                        actions.onNextMorning()
                    },
                    modifier = modifier,
                )
                gameOver -> FinalBillScreen(state = uiState.state, onBackToStart = onBackToStart, modifier = modifier)
                else -> {
                    BackHandler(enabled = place != null) { place = null }
                    when (place) {
                        null -> MorningScreen(uiState = uiState, actions = actions, onGoTo = { place = it }, modifier = modifier)
                        Place.STAFF -> StaffScreen(uiState, actions, onBack = { place = null }, modifier = modifier)
                        Place.FOOD -> FoodScreen(uiState, actions, onBack = { place = null }, modifier = modifier)
                        Place.KITCHEN -> KitchenScreen(uiState, actions, onBack = { place = null }, modifier = modifier)
                    }
                }
            }
        }
    }
}

/**
 * Shared frame for Staff, Food and Kitchen: a slim awning, the page's name,
 * the money left after this morning's choices, and a way back.
 */
@Composable
internal fun PlaceScaffold(
    title: String,
    cashNow: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Awning(height = 22.dp, stripes = 16)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                        Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Text(text = coins(cashNow), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

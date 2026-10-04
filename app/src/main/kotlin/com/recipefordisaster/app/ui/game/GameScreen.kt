package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState

/**
 * Everything the player can do on the game screen, bundled so the screen
 * (and its tests/previews) take one parameter instead of a dozen lambdas.
 * [GameViewModel.actions] wires these to the ViewModel.
 */
data class GameActions(
    val onOpenForTheDay: () -> Unit = {},
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
    onOpenForTheDay = ::openForTheDay,
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

private enum class GameTab(val labelRes: Int) {
    TODAY(R.string.tab_today),
    STAFF(R.string.tab_staff),
    MENU(R.string.tab_menu),
    PANTRY(R.string.tab_pantry),
    KITCHEN(R.string.tab_kitchen),
}

/**
 * The main play screen. Renders one of three things depending on
 * [GameUiState]: a loading spinner, the run-ending summary (once
 * [GameState.restaurant]'s status is [RestaurantStatus.BANKRUPT] or
 * [RestaurantStatus.CONDEMNED]), or the day-to-day dashboard: five tabs for
 * planning the day, and a bottom bar showing what the plan costs next to
 * the "Open for the day" button that commits it.
 */
@Composable
fun GameScreen(
    uiState: GameUiState,
    actions: GameActions,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is GameUiState.Loading -> Scaffold(modifier = modifier.fillMaxSize()) { padding -> LoadingContent(Modifier.padding(padding)) }
        is GameUiState.Error -> Scaffold(modifier = modifier.fillMaxSize()) { padding ->
            ErrorContent(message = uiState.message, onBackToStart = onBackToStart, modifier = Modifier.padding(padding))
        }
        is GameUiState.Playing -> {
            val status = uiState.state.restaurant.status
            if (status == RestaurantStatus.BANKRUPT || status == RestaurantStatus.CONDEMNED) {
                Scaffold(modifier = modifier.fillMaxSize()) { padding ->
                    GameOverContent(state = uiState.state, onBackToStart = onBackToStart, modifier = Modifier.padding(padding).padding(16.dp))
                }
            } else {
                DashboardContent(uiState = uiState, actions = actions, modifier = modifier)
            }
        }
    }
}

@Composable
private fun DashboardContent(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(GameTab.TODAY.ordinal) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DashboardHeader(state = uiState.state, selectedTab = selectedTab, onSelectTab = { selectedTab = it }) },
        bottomBar = { PlanBar(uiState = uiState, onOpenForTheDay = actions.onOpenForTheDay) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (GameTab.entries[selectedTab]) {
                GameTab.TODAY -> TodayTab(uiState)
                GameTab.STAFF -> StaffTab(uiState, actions)
                GameTab.MENU -> MenuTab(uiState, actions)
                GameTab.PANTRY -> PantryTab(uiState, actions)
                GameTab.KITCHEN -> KitchenTab(uiState, actions)
            }
        }
    }
}

@Composable
private fun DashboardHeader(state: GameState, selectedTab: Int, onSelectTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_day, state.day),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = stringResource(R.string.header_cash), style = MaterialTheme.typography.labelMedium)
                    Text(text = coins(state.restaurant.cash), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                edgePadding = 8.dp,
            ) {
                GameTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab.ordinal,
                        onClick = { onSelectTab(tab.ordinal) },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanBar(uiState: GameUiState.Playing, onOpenForTheDay: () -> Unit) {
    val spend = uiState.preview.spending.total
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (spend > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.plan_spend, coins(spend)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.plan_cash_after, coins(uiState.state.restaurant.cash - spend)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(text = stringResource(R.string.plan_nothing), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenForTheDay, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(text = "🍳", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.dashboard_open_for_the_day), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, onBackToStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToStart) {
            Text(stringResource(R.string.game_back_to_start))
        }
    }
}

@Composable
private fun GameOverContent(state: GameState, onBackToStart: () -> Unit, modifier: Modifier = Modifier) {
    val cause = when (state.restaurant.status) {
        RestaurantStatus.BANKRUPT -> stringResource(R.string.game_over_cause_bankrupt)
        RestaurantStatus.CONDEMNED -> stringResource(R.string.game_over_cause_condemned)
        else -> stringResource(R.string.game_over_cause_unknown)
    }
    val bestSellingDish = state.dishSalesTotals.maxByOrNull { it.value }
    val bestSellingName = bestSellingDish?.let { (id, _) -> state.menu.firstOrNull { it.id.value == id }?.name ?: id }
    val profit = state.ledger.totalProfitOrLoss

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = stringResource(R.string.game_over_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = cause, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        GameCard {
            // The run ended during the day shown, so the last full day survived is the one before.
            StatRow(label = stringResource(R.string.game_over_days_survived), value = (state.day - 1).coerceAtLeast(0).toString())
            StatRow(label = stringResource(R.string.game_over_total_revenue), value = coins(state.ledger.totalRevenue))
            StatRow(label = stringResource(R.string.game_over_total_profit_or_loss), value = coins(profit), valueColor = moneyColor(profit))
            StatRow(label = stringResource(R.string.game_over_final_reputation), value = state.restaurant.reputation.toString())
            if (bestSellingDish != null && bestSellingName != null) {
                StatRow(label = stringResource(R.string.game_over_best_seller), value = "$bestSellingName (${bestSellingDish.value})")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBackToStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.game_back_to_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

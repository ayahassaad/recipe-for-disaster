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
import androidx.compose.runtime.remember
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
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.OutlookCalculator

/**
 * Everything the player can do on the game screen, bundled so the screen
 * (and its tests/previews) take one parameter instead of a dozen lambdas.
 * [GameViewModel.actions] wires these to the ViewModel.
 */
data class GameActions(
    val onStartService: () -> Unit = {},
    val onNextMorning: () -> Unit = {},
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

internal enum class GameTab(val labelRes: Int) {
    TODAY(R.string.tab_today),
    STAFF(R.string.tab_staff),
    MENU(R.string.tab_menu),
    PANTRY(R.string.tab_pantry),
    KITCHEN(R.string.tab_kitchen),
}

/**
 * The main play screen. Depending on [GameUiState] it shows a loading
 * spinner, the morning (five tabs to get ready, and "Start service"), the
 * end-of-day results, or — once [GameState.restaurant]'s status is
 * [RestaurantStatus.BANKRUPT] or [RestaurantStatus.CONDEMNED] and the
 * player has seen that last day's results — the run-ending summary.
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
            val gameOver = status == RestaurantStatus.BANKRUPT || status == RestaurantStatus.CONDEMNED
            when {
                uiState.report != null -> DayReportScreen(
                    report = uiState.report,
                    nextDay = uiState.state.day,
                    gameOver = gameOver,
                    onContinue = actions.onNextMorning,
                    modifier = modifier,
                )
                gameOver -> Scaffold(modifier = modifier.fillMaxSize()) { padding ->
                    GameOverContent(state = uiState.state, onBackToStart = onBackToStart, modifier = Modifier.padding(padding).padding(16.dp))
                }
                else -> MorningContent(uiState = uiState, actions = actions, modifier = modifier)
            }
        }
    }
}

@Composable
private fun MorningContent(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(GameTab.TODAY.ordinal) }
    val goTo: (GameTab) -> Unit = { selectedTab = it.ordinal }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { MorningHeader(uiState = uiState, selectedTab = selectedTab, onSelectTab = { selectedTab = it }) },
        bottomBar = { ServiceBar(uiState = uiState, onStartService = actions.onStartService) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (GameTab.entries[selectedTab]) {
                GameTab.TODAY -> TodayTab(uiState, actions, onGoTo = goTo)
                GameTab.STAFF -> StaffTab(uiState, actions)
                GameTab.MENU -> MenuTab(uiState, actions)
                GameTab.PANTRY -> PantryTab(uiState, actions)
                GameTab.KITCHEN -> KitchenTab(uiState, actions)
            }
        }
    }
}

@Composable
private fun MorningHeader(uiState: GameUiState.Playing, selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val dailyCosts = remember(uiState.morning) { DailyCosts.of(uiState.morning) }
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_day, uiState.state.day),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = stringResource(R.string.phase_morning), style = MaterialTheme.typography.labelLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = stringResource(R.string.header_cash), style = MaterialTheme.typography.labelMedium)
                    Text(text = coins(uiState.cashNow), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.header_daily_costs, coins(dailyCosts.total)),
                        style = MaterialTheme.typography.labelMedium,
                    )
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
private fun ServiceBar(uiState: GameUiState.Playing, onStartService: () -> Unit) {
    val outlook = remember(uiState.morning) { OutlookCalculator.forTonight(uiState.morning) }
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.start_service_hint, outlook.expectedCustomers),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onStartService, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(text = stringResource(R.string.start_service), style = MaterialTheme.typography.titleMedium)
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

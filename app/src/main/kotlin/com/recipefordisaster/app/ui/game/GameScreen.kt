package com.recipefordisaster.app.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.scene.RestaurantScene
import com.recipefordisaster.app.ui.scene.SceneLabels
import com.recipefordisaster.app.ui.scene.SceneModel
import com.recipefordisaster.app.ui.scene.SceneTarget
import com.recipefordisaster.app.ui.scene.ServiceChoreography
import com.recipefordisaster.app.ui.scene.StaffFigure
import com.recipefordisaster.app.ui.theme.ReceiptInk
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.Advice
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.MorningAdvisor

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

/**
 * The play screen. The restaurant is always on screen: in the morning you
 * tap things in it to sort them out, then open the doors and watch the
 * night play out, then see what you made. "How to play" comes first on a
 * new game; the final bill ends the run.
 */
@Composable
fun GameScreen(
    uiState: GameUiState,
    actions: GameActions,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is GameUiState.Loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
            when {
                uiState.showIntro -> IntroScreen(onStart = actions.onDismissIntro, modifier = modifier)
                uiState.report != null -> ServicePlay(report = uiState.report, gameOver = gameOver, onContinue = actions.onNextMorning, modifier = modifier)
                gameOver -> FinalBillScreen(state = uiState.state, onBackToStart = onBackToStart, modifier = modifier)
                else -> MorningPlay(uiState = uiState, actions = actions, modifier = modifier)
            }
        }
    }
}

// ---------------------------------------------------------------- morning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningPlay(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    val morning = uiState.morning
    val advice = remember(morning) { MorningAdvisor.adviceFor(morning) }
    val model = remember(morning, advice) { sceneModelFor(morning, advice, hiringOpen = uiState.state.applicants.any { it.id !in uiState.plan.hires }) }
    val costs = remember(morning) { DailyCosts.of(morning) }
    var open by remember { mutableStateOf<SceneTarget?>(null) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Hud(day = uiState.state.day, cash = uiState.cashNow, reputation = morning.restaurant.reputation, subtitle = stringResource(R.string.daily_costs, coins(costs.total)))
        val need = remember(morning) { MorningAdvisor.staffNeed(morning) }
        RestaurantScene(model = model, labels = sceneLabels(need), onTap = { open = it }, modifier = Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(if (model.alerts.isEmpty()) R.string.hint_ready else R.string.hint_alerts),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SignButton(text = stringResource(R.string.open_doors), onClick = actions.onStartService)
        }
    }

    open?.let { target ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { SheetContent(target, uiState, actions) }
        }
    }
}

/** Day, money and stars along the top, under the awning. */
@Composable
private fun Hud(day: Int, cash: Long, reputation: Int, subtitle: String) {
    Column {
        Awning(height = 20.dp, stripes = 16)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.day, day), style = MaterialTheme.typography.headlineMedium)
                StarRating(reputation)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(coins(cash), style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Builds what the scene shows from the restaurant as the player has set it up. */
internal fun sceneModelFor(state: GameState, advice: List<Advice>, hiringOpen: Boolean): SceneModel {
    val working = state.employees.filter { it.status == EmployeeStatus.ACTIVE }
    val used = state.inventory.ingredients.values.mapNotNull { MorningAdvisor.nightsOfStock(state, it) }
    val fullness = if (used.isEmpty()) 1f else used.map { (it / 2.0).coerceIn(0.0, 1.0) }.average().toFloat()
    val alerts = advice.mapNotNull { item ->
        when (item) {
            is Advice.Restock, is Advice.DishUnmakeable -> SceneTarget.Pantry
            is Advice.KitchenTooSmall -> if (hiringOpen) SceneTarget.HiringSign else null
            is Advice.EquipmentBroken, is Advice.EquipmentWorn -> SceneTarget.Oven
            is Advice.Dirty -> SceneTarget.Mop
            is Advice.StaffExhausted -> SceneTarget.Staff(item.employee.id)
            is Advice.LosingMoney -> null // shown as red money in the bar instead
        }
    }.toSet()
    return SceneModel(
        staff = working.map { StaffFigure(it.id, it.name, it.role, it.morale, it.stress) },
        ovenCondition = state.equipment.firstOrNull()?.condition ?: 100,
        pantryFullness = fullness,
        cleanliness = state.restaurant.cleanliness,
        hiring = hiringOpen,
        alerts = alerts,
    )
}

@Composable
private fun sceneLabels(need: MorningAdvisor.Need? = null): SceneLabels {
    val staffLabel = stringResource(R.string.scene_staff)
    val roles = mapOf(
        com.recipefordisaster.domain.employee.Role.COOK to stringResource(R.string.role_cook),
        com.recipefordisaster.domain.employee.Role.SERVER to stringResource(R.string.role_server),
        com.recipefordisaster.domain.employee.Role.DISHWASHER to stringResource(R.string.role_dishwasher),
        com.recipefordisaster.domain.employee.Role.MANAGER to stringResource(R.string.role_manager),
    )
    return SceneLabels(
        menu = stringResource(R.string.scene_menu_sign),
        hiring = stringResource(
            when (need) {
                MorningAdvisor.Need.COOK -> R.string.scene_hiring_sign_cook
                MorningAdvisor.Need.SERVER -> R.string.scene_hiring_sign_server
                MorningAdvisor.Need.DISHWASHER -> R.string.scene_hiring_sign_dishwasher
                null -> R.string.scene_hiring_sign
            },
        ),
        oven = stringResource(R.string.scene_oven),
        pantry = stringResource(R.string.scene_pantry),
        mop = stringResource(R.string.scene_mop),
        hiringSign = stringResource(R.string.scene_hiring),
        staff = { figure -> String.format(staffLabel, figure.name, roles[figure.role] ?: "") },
    )
}

// ---------------------------------------------------------------- service and the end of the night

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServicePlay(report: DayReport, gameOver: Boolean, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val serverCount = report.startOfService.employees.count {
        it.status == EmployeeStatus.ACTIVE && (it.role == com.recipefordisaster.domain.employee.Role.SERVER || it.role == com.recipefordisaster.domain.employee.Role.MANAGER)
    }
    val choreography = remember(report) { ServiceChoreography(report.summary.guests, serverCount) }
    var finished by rememberSaveable(report.summary.day) { mutableStateOf(false) }
    var fast by rememberSaveable { mutableStateOf(false) }
    var tonight by remember(report) { mutableLongStateOf(0L) }
    var showBill by remember { mutableStateOf(false) }

    val event = report.event
    val baseModel = remember(report) { sceneModelFor(report.startOfService, emptyList(), hiringOpen = false) }
    // Overnight, some events show up in the room itself.
    val model = if (finished && event != null) {
        baseModel.copy(
            cleanliness = if (event.ruleId == "rat_sighting") minOf(baseModel.cleanliness, 40) else baseModel.cleanliness,
            ovenOnFire = event.ruleId == "kitchen_fire",
        )
    } else {
        baseModel
    }
    val profit = report.books.profitOrLoss

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Hud(
            day = report.summary.day,
            cash = if (finished) report.summary.cashAfter else report.summary.cashBefore - report.morningSpending.total + tonight,
            reputation = if (finished) report.summary.reputationAfter else report.summary.reputationBefore,
            // Takings while guests pay; once the night's over the receipt shows the profit, so don't compete with it.
            subtitle = if (finished) "" else stringResource(R.string.tonight_takings, signedCoins(tonight)),
        )
        Box(modifier = Modifier.weight(1f)) {
            RestaurantScene(
                model = model,
                labels = sceneLabels(),
                onTap = {},
                service = if (finished) null else choreography,
                speed = if (fast) 3f else 1f,
                onServiceFinished = { finished = true },
                onCoinsSoFar = { tonight = it },
            )
            EndOfNightPanel(
                visible = finished,
                report = report,
                onShowBill = { showBill = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (finished) {
                SignButton(text = stringResource(if (gameOver) R.string.bill_game_over else R.string.bill_next), onClick = onContinue)
            } else {
                OutlinedButton(onClick = { fast = !fast }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(if (fast) R.string.speed_normal else R.string.speed_fast), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(onClick = { finished = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.skip), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showBill) {
        ModalBottomSheet(onDismissRequest = { showBill = false }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { BillReceipt(report) }
        }
    }
}

/** The receipt that slides up over the restaurant once the last guest has left. */
@Composable
private fun EndOfNightPanel(visible: Boolean, report: DayReport, onShowBill: () -> Unit, modifier: Modifier = Modifier) {
    val event = report.event
    val profit = report.books.profitOrLoss
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        modifier = modifier,
    ) {
        Receipt(modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp)) {
            Text(
                stringResource(if (profit >= 0) R.string.bill_made else R.string.bill_lost),
                style = MaterialTheme.typography.titleMedium,
                color = ReceiptInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                signedCoins(profit),
                style = MaterialTheme.typography.displaySmall,
                color = moneyColor(profit),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.end_fed, report.summary.customersFed, report.summary.customersArrived),
                style = MaterialTheme.typography.bodyLarge,
                color = ReceiptInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            event?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it.title, style = MaterialTheme.typography.titleMedium, color = toneColor(it.tone), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(it.description, style = MaterialTheme.typography.bodyMedium, color = ReceiptInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onShowBill, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.bill_details_show), color = ReceiptInk)
            }
        }
    }
}

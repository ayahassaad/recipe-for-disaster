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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
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
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.app.ui.scene.NightScene
import com.recipefordisaster.app.ui.scene.NightLabels
import com.recipefordisaster.app.ui.scene.RestaurantScene
import com.recipefordisaster.app.ui.scene.SceneLabels
import com.recipefordisaster.app.ui.scene.SceneModel
import com.recipefordisaster.app.ui.scene.SceneTarget
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
    val onFinishService: (ServiceNight) -> Unit = {},
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
    onFinishService = ::finishService,
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
                uiState.night != null -> NightPlay(session = uiState.night, onFinished = actions.onFinishService, modifier = modifier)
                uiState.report != null -> ResultsPlay(report = uiState.report, gameOver = gameOver, onContinue = actions.onNextMorning, modifier = modifier)
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

/**
 * Tonight's service, played. The clock runs while the screen is up; taps
 * on tables and the counter go straight to the night as commands. When the
 * last guest has left, the finished night goes back to the ViewModel to
 * close the day.
 */
@Composable
private fun NightPlay(session: NightSession, onFinished: (ServiceNight) -> Unit, modifier: Modifier = Modifier) {
    var night by remember(session) { mutableStateOf(session.opening) }
    var clock by remember { mutableFloatStateOf(0f) }
    val model = remember(session) { sceneModelFor(session.setup.morning.state, emptyList(), hiringOpen = false) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(session) {
        var last = withFrameNanos { it }
        var reported = false
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
            clock += dt
            if (!night.finished) {
                night = night.advance(dt)
            } else if (!reported) {
                reported = true
                currentOnFinished(night)
            }
        }
    }

    val labels = nightLabels()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Hud(
            day = session.setup.original.day,
            cash = session.setup.original.restaurant.cash - session.morningSpending.total + night.takings,
            reputation = session.setup.morning.state.restaurant.reputation,
            subtitle = stringResource(R.string.tonight_takings, signedCoins(night.takings)),
        )
        NightScene(
            night = night,
            model = model,
            labels = labels,
            clock = clock,
            onTapTable = { night = night.tapTable(it) },
            onTapCounter = { night = night.tapPass() },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = nightHint(night),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** One line telling the player the most useful thing to do right now. */
@Composable
private fun nightHint(night: ServiceNight): String {
    val me = night.player
    val mine = night.parties.filter { it.table in me.tables }
    fun number(table: Int?) = (table ?: 0) + 1
    val carrying = me.plates.firstOrNull()?.let { id -> night.parties.firstOrNull { it.id == id } }
    val ready = mine.filter { it.stage == ServiceNight.Stage.READY_AT_PASS }.minByOrNull { it.stageSince }
    val ordering = mine.filter { it.stage == ServiceNight.Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }
    return when {
        carrying != null -> stringResource(R.string.hint_serve, number(carrying.table))
        me.tickets.isNotEmpty() -> stringResource(R.string.hint_hand_in)
        ready != null -> stringResource(R.string.hint_pick_up, number(ready.table))
        ordering != null -> stringResource(R.string.hint_take_order, number(ordering.table))
        night.parties.any { it.stage == ServiceNight.Stage.NOT_YET_ARRIVED || it.stage == ServiceNight.Stage.QUEUEING } -> stringResource(R.string.hint_waiting)
        else -> stringResource(R.string.hint_cooking)
    }
}

@Composable
private fun nightLabels(): NightLabels {
    val table = stringResource(R.string.night_table)
    return NightLabels(
        table = { number, state -> String.format(table, number, state) },
        counter = stringResource(R.string.night_counter),
        wantsToOrder = stringResource(R.string.night_wants_to_order),
        waitingForFood = stringResource(R.string.night_waiting_food),
        foodReady = stringResource(R.string.night_food_ready),
        eating = stringResource(R.string.night_eating),
        empty = stringResource(R.string.night_empty),
        you = stringResource(R.string.night_you),
        menu = stringResource(R.string.scene_menu_sign),
    )
}

/** After the night: the restaurant quiet again, the receipt over it, and on to the next day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsPlay(report: DayReport, gameOver: Boolean, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var showBill by remember { mutableStateOf(false) }
    val event = report.event
    val baseModel = remember(report) { sceneModelFor(report.startOfService, emptyList(), hiringOpen = false) }
    // Overnight, some events show up in the room itself.
    val model = if (event != null) {
        baseModel.copy(
            cleanliness = if (event.ruleId == "rat_sighting") minOf(baseModel.cleanliness, 40) else baseModel.cleanliness,
            ovenOnFire = event.ruleId == "kitchen_fire",
        )
    } else {
        baseModel
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Hud(day = report.summary.day, cash = report.summary.cashAfter, reputation = report.summary.reputationAfter, subtitle = "")
        Box(modifier = Modifier.weight(1f)) {
            RestaurantScene(model = model, labels = sceneLabels(), onTap = {})
            EndOfNightPanel(visible = true, report = report, onShowBill = { showBill = true }, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            SignButton(text = stringResource(if (gameOver) R.string.bill_game_over else R.string.bill_next), onClick = onContinue)
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

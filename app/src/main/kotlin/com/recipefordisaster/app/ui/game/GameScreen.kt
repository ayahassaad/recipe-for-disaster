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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.LeafGreen
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
import androidx.compose.material3.AlertDialog
import com.recipefordisaster.domain.simulation.PlayerDecisions
import androidx.compose.foundation.clickable

/**
 * Everything the player can do on the game screen, bundled so the screen
 * (and its tests/previews) take one parameter instead of a dozen lambdas.
 * [GameViewModel.actions] wires these to the ViewModel.
 */
data class GameActions(
    val onStartService: () -> Unit = {},
    val onFinishService: (ServiceNight) -> Unit = {},
    val onNightProgress: (ServiceNight) -> Unit = {},
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
    val onToggleUpgrade: (EquipmentId) -> Unit = {},
    val onToggleDeepClean: () -> Unit = {},
    val onToggleBuyTable: () -> Unit = {},
)

fun GameViewModel.actions(): GameActions = GameActions(
    onStartService = ::startService,
    onFinishService = ::finishService,
    onNightProgress = ::saveNightProgress,
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
    onToggleUpgrade = ::toggleUpgrade,
    onToggleDeepClean = ::toggleDeepClean,
    onToggleBuyTable = ::toggleBuyTable,
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
            // Leaving goes back to the start screen. The game is saved (a night in progress too),
            // so Continue picks up from here; only this morning's unfinished choices are dropped.
            var askLeave by remember { mutableStateOf(false) }
            val onMenu = { askLeave = true }
            androidx.activity.compose.BackHandler(enabled = !gameOver) { askLeave = !askLeave }
            if (askLeave) {
                AlertDialog(
                    onDismissRequest = { askLeave = false },
                    title = { Text(stringResource(R.string.leave_title)) },
                    text = {
                        Text(
                            stringResource(
                                when {
                                    uiState.night != null -> R.string.leave_body_night
                                    uiState.report == null && uiState.plan != PlayerDecisions() -> R.string.leave_body_morning
                                    else -> R.string.leave_body
                                },
                            ),
                        )
                    },
                    confirmButton = { TextButton(onClick = { askLeave = false; onBackToStart() }) { Text(stringResource(R.string.leave_confirm)) } },
                    dismissButton = { TextButton(onClick = { askLeave = false }) { Text(stringResource(R.string.leave_stay)) } },
                )
            }
            when {
                uiState.showIntro -> IntroScreen(onStart = actions.onDismissIntro, modifier = modifier)
                uiState.night != null -> NightPlay(session = uiState.night, onFinished = actions.onFinishService, onProgress = actions.onNightProgress, onMenu = onMenu, modifier = modifier)
                uiState.report != null -> ResultsPlay(
                    report = uiState.report,
                    gameOver = gameOver,
                    newTable = uiState.state.restaurant.tables > uiState.report.startOfService.restaurant.tables,
                    onContinue = actions.onNextMorning,
                    onMenu = onMenu,
                    modifier = modifier,
                )
                gameOver -> FinalBillScreen(state = uiState.state, onBackToStart = onBackToStart, modifier = modifier)
                else -> MorningPlay(uiState = uiState, actions = actions, onMenu = onMenu, modifier = modifier)
            }
        }
    }
}

// ---------------------------------------------------------------- morning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningPlay(uiState: GameUiState.Playing, actions: GameActions, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val morning = uiState.morning
    val advice = remember(morning) { MorningAdvisor.adviceFor(morning) }
    // The sign only goes up when the restaurant is actually short of someone, and says who.
    val staffNeed = remember(morning) { MorningAdvisor.staffNeed(morning) }
    val model = remember(morning, advice, staffNeed) { sceneModelFor(morning, advice, hiringOpen = staffNeed != null) }
    val costs = remember(morning) { DailyCosts.of(morning) }
    var open by remember { mutableStateOf<SceneTarget?>(null) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Hud(day = uiState.state.day, cash = uiState.cashNow, reputation = morning.restaurant.reputation, subtitle = stringResource(R.string.daily_costs, coins(costs.total)), onMenu = onMenu)
        RestaurantScene(model = model, labels = sceneLabels(staffNeed), onTap = { open = it }, modifier = Modifier.weight(1f))
        // Opening with nothing the kitchen can cook means every guest walks straight back out.
        val noFood = remember(morning) {
            morning.menu.filter { it.available }.none { com.recipefordisaster.domain.inventory.InventoryOperations.canFulfill(morning.inventory, it.recipe) }
        }
        // Someone worn out gets named, so the player knows exactly who needs a day off.
        val tired = advice.filterIsInstance<Advice.StaffExhausted>().maxByOrNull { it.employee.stress }?.employee
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = when {
                    noFood -> stringResource(R.string.hint_no_food)
                    tired != null -> stringResource(R.string.hint_tired, tired.name)
                    model.alerts.isEmpty() -> stringResource(R.string.hint_ready)
                    else -> stringResource(R.string.hint_alerts)
                },
                color = if (noFood) DisasterRed else Color.Unspecified,
                fontWeight = if (noFood) FontWeight.SemiBold else null,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                // Always two lines tall, so a longer hint never pushes the restaurant up.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SignButton(text = stringResource(R.string.open_doors), onClick = actions.onStartService)
        }
    }

    open?.let { target ->
        // No half-open state: Back (or a tap outside) closes the sheet in one go, instead of first
        // shrinking it and leaving the player's next tap to land on the dimmed background.
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = sheetState) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { SheetContent(target, uiState, actions) }
        }
    }
}

/** Day, money and stars along the top, under the awning. */
@Composable
private fun Hud(day: Int, cash: Long, reputation: Int, subtitle: String, onMenu: () -> Unit, onPause: (() -> Unit)? = null) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Back to the start screen (asks first), and during service a pause button under it.
            Column {
                TextButton(onClick = onMenu) { Text(stringResource(R.string.menu_button), style = MaterialTheme.typography.titleMedium) }
                if (onPause != null) {
                    TextButton(onClick = onPause) {
                        Text(stringResource(R.string.pause_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
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

/** The oven (the cooking kit, as opposed to the fridge). */
internal fun oven(state: GameState) = com.recipefordisaster.domain.equipment.Fridge.cookingKit(state.equipment).firstOrNull()

/** Builds what the scene shows from the restaurant as the player has set it up. */
internal fun sceneModelFor(state: GameState, advice: List<Advice>, hiringOpen: Boolean): SceneModel {
    val working = state.employees.filter { it.status == EmployeeStatus.ACTIVE }
    val used = state.inventory.ingredients.values.mapNotNull { MorningAdvisor.nightsOfStock(state, it) }
    val fullness = if (used.isEmpty()) 1f else used.map { (it / 2.0).coerceIn(0.0, 1.0) }.average().toFloat()
    // One jar per ingredient tonight's menu uses, filled to how long it'll last:
    // full is two nights or more, empty is none left. Ingredients nobody needs stay off the shelf.
    val jars = state.inventory.ingredients.values.sortedBy { it.name }.mapNotNull { ingredient ->
        MorningAdvisor.nightsOfStock(state, ingredient)?.let { (it / 2.0).coerceIn(0.0, 1.0).toFloat() }
    }
    val alerts = advice.mapNotNull { item ->
        when (item) {
            is Advice.Restock, is Advice.DishUnmakeable -> SceneTarget.Pantry
            is Advice.KitchenTooSmall -> if (hiringOpen) SceneTarget.HiringSign else null
            is Advice.EquipmentBroken -> if (com.recipefordisaster.domain.equipment.Fridge.isFridge(item.equipment)) SceneTarget.Fridge else SceneTarget.Oven
            is Advice.EquipmentWorn -> if (com.recipefordisaster.domain.equipment.Fridge.isFridge(item.equipment)) SceneTarget.Fridge else SceneTarget.Oven
            is Advice.Dirty -> SceneTarget.Mop
            is Advice.StaffExhausted -> SceneTarget.Staff(item.employee.id)
            is Advice.LosingMoney -> null // shown as red money in the bar instead
        }
    }.toSet()
    return SceneModel(
        staff = working.map { StaffFigure(it.id, it.name, it.role, it.morale, it.stress) },
        ovenCondition = oven(state)?.condition ?: 100,
        ovenLevel = oven(state)?.upgradeLevel ?: 1,
        fridgeCondition = com.recipefordisaster.domain.equipment.Fridge.of(state)?.condition ?: 100,
        fridgeLevel = com.recipefordisaster.domain.equipment.Fridge.of(state)?.upgradeLevel ?: 1,
        tableCount = state.restaurant.tables,
        pantryFullness = fullness,
        pantryJars = jars,
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
        com.recipefordisaster.domain.employee.Role.HOST to stringResource(R.string.role_host),
        com.recipefordisaster.domain.employee.Role.BUSSER to stringResource(R.string.role_busser),
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
        fridge = stringResource(R.string.scene_fridge),
        pantry = stringResource(R.string.scene_pantry),
        mop = stringResource(R.string.scene_mop),
        hiringSign = stringResource(R.string.scene_hiring),
        dishSign = stringResource(R.string.night_dish_sign),
        tables = stringResource(R.string.scene_tables),
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
private fun NightPlay(session: NightSession, onFinished: (ServiceNight) -> Unit, onProgress: (ServiceNight) -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    var night by remember(session) { mutableStateOf(session.opening) }
    var clock by remember { mutableFloatStateOf(0f) }
    // Paused: time stands still, nothing can be tapped, and a sign says how to carry on.
    var paused by remember(session) { mutableStateOf(false) }
    val model = remember(session) { sceneModelFor(session.setup.morning.state, emptyList(), hiringOpen = false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnProgress by rememberUpdatedState(onProgress)

    // Save the night every few seconds, and whenever the app goes into the background,
    // so closing the app mid-service picks up at the same moment next time.
    LaunchedEffect(session) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            currentOnProgress(night)
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Leaving the app pauses the night, so coming back doesn't drop you into a rush.
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                paused = true
                currentOnProgress(night)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the screen (back to the start) saves exactly where the night was.
            currentOnProgress(night)
        }
    }

    LaunchedEffect(session) {
        var last = withFrameNanos { it }
        var reported = false
        var closedAt = -1f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
            if (paused) continue
            clock += dt
            if (!night.finished) {
                night = night.advance(dt)
            } else if (!reported) {
                // The last guest has gone: a moment of "Closing time!" before the bill.
                if (closedAt < 0f) closedAt = clock
                if (clock - closedAt >= CLOSING_PAUSE) {
                    reported = true
                    currentOnFinished(night)
                }
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
            // The menu pauses the night too while you decide whether to leave.
            onMenu = { paused = true; onMenu() },
            onPause = if (night.finished) null else ({ paused = true }),
        )
        Box(modifier = Modifier.weight(1f)) {
            NightScene(
                night = night,
                model = model,
                labels = labels,
                clock = clock,
                onTapTable = { night = night.tapTable(it) },
                onTapCounter = { night = night.tapChef() },
                onTapPlate = { id -> night = night.tapPlate(id) },
                onTapDishStation = { night = night.tapDishStation() },
                onTapMopBucket = { night = night.tapMopBucket() },
                onTapMess = { id -> night = night.tapMess(id) },
                onTapFridge = { night = night.tapFridge() },
                modifier = Modifier.fillMaxSize(),
                // For the first few nights, point at what the hint is talking about.
                focus = if (session.setup.original.day <= GUIDED_DAYS) NightHint.of(night).focus(night) else null,
            )
            if (paused) {
                // Covers the restaurant, so taps while paused don't send you anywhere.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(onClickLabel = stringResource(R.string.paused_resume)) { paused = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Chalkboard(modifier = Modifier.padding(horizontal = 40.dp)) {
                        Text(stringResource(R.string.paused_title), style = MaterialTheme.typography.headlineMedium, color = ChalkWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.paused_resume), style = MaterialTheme.typography.bodyLarge, color = ChalkWhite.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = night.finished,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            ) {
                val result = night.result()
                Chalkboard {
                    Text(stringResource(R.string.closing_time), style = MaterialTheme.typography.headlineMedium, color = ChalkWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.closing_time_fed, result.outcomes.count { it.dish != null }, result.outcomes.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChalkWhite.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            text = nightHint(night),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Always two lines tall, so the restaurant doesn't jump when a hint wraps.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** For this many days, the night scene points at what to tap next. */
private const val GUIDED_DAYS = 3

/** Seconds the "Closing time!" sign stays up before the bill. */
private const val CLOSING_PAUSE = 2.5f

/** One line telling the player the most useful thing to do right now. */
@Composable
private fun nightHint(night: ServiceNight): String = when (val hint = NightHint.of(night)) {
    NightHint.Washing -> stringResource(R.string.hint_washing)
    NightHint.Mopping -> stringResource(R.string.hint_mopping)
    is NightHint.Serve -> stringResource(R.string.hint_serve, hint.table + 1)
    NightHint.HandIn -> stringResource(R.string.hint_hand_in)
    is NightHint.PickUp -> stringResource(R.string.hint_pick_up, hint.table + 1)
    is NightHint.DoorWaiting -> stringResource(R.string.hint_door_waiting, hint.table + 1)
    is NightHint.TakeOrder -> stringResource(R.string.hint_take_order, hint.table + 1)
    NightHint.Wash -> stringResource(R.string.hint_wash)
    is NightHint.Clear -> stringResource(if (night.guestsGone) R.string.hint_last_clear else R.string.hint_clear, hint.table + 1)
    NightHint.OutOfFood -> stringResource(R.string.hint_out_of_food)
    NightHint.MopSpill -> stringResource(R.string.hint_mop_spill)
    NightHint.GetMop -> stringResource(R.string.hint_get_mop)
    NightHint.PutMopBack -> stringResource(R.string.hint_put_mop_back)
    NightHint.Waiting -> stringResource(R.string.hint_waiting)
    NightHint.TidyingUp -> stringResource(R.string.hint_tidying_up)
    NightHint.FixFridge -> stringResource(R.string.hint_fix_fridge)
    NightHint.FixingFridge -> stringResource(R.string.hint_fixing_fridge)
    NightHint.FridgeStruggling -> stringResource(R.string.hint_fridge_struggling)
    NightHint.Cooking -> stringResource(R.string.hint_cooking)
    is NightHint.CookingFor -> stringResource(R.string.hint_cooking_for, hint.table + 1)
    NightHint.Eating -> stringResource(R.string.hint_eating)
    is NightHint.Deciding -> stringResource(R.string.hint_deciding, hint.table + 1)
}

@Composable
private fun nightLabels(): NightLabels {
    val plateLabel = stringResource(R.string.night_plate)
    val table = stringResource(R.string.night_table)
    return NightLabels(
        table = { number, state -> String.format(table, number, state) },
        counter = stringResource(R.string.night_counter),
        plate = { number -> String.format(plateLabel, number) },
        wantsToOrder = stringResource(R.string.night_wants_to_order),
        deciding = stringResource(R.string.night_deciding),
        waitingForFood = stringResource(R.string.night_waiting_food),
        foodReady = stringResource(R.string.night_food_ready),
        eating = stringResource(R.string.night_eating),
        empty = stringResource(R.string.night_empty),
        needsClearing = stringResource(R.string.night_needs_clearing),
        dishStation = stringResource(R.string.night_dish_station),
        dishSign = stringResource(R.string.night_dish_sign),
        mopBucket = stringResource(R.string.night_mop_bucket),
        fridge = stringResource(R.string.night_fridge),
        spill = stringResource(R.string.night_spill),
        you = stringResource(R.string.night_you),
        menu = stringResource(R.string.scene_menu_sign),
    )
}

/** After the night: the restaurant quiet again, the receipt over it, and on to the next day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsPlay(report: DayReport, gameOver: Boolean, newTable: Boolean, onContinue: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
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
        Hud(day = report.summary.day, cash = report.summary.cashAfter, reputation = report.summary.reputationAfter, subtitle = "", onMenu = onMenu)
        Box(modifier = Modifier.weight(1f)) {
            RestaurantScene(model = model, labels = sceneLabels(), onTap = {})
            EndOfNightPanel(
                visible = true,
                report = report,
                onShowBill = { showBill = true },
                newTable = newTable,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            SignButton(text = stringResource(if (gameOver) R.string.bill_game_over else R.string.bill_next), onClick = onContinue)
        }
    }

    if (showBill) {
        ModalBottomSheet(onDismissRequest = { showBill = false }, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { BillReceipt(report) }
        }
    }
}

/** The biggest reason guests went without tonight, and what to do about it; null if everyone was fed. */
@Composable
private fun lostTip(report: DayReport): String? {
    val s = report.summary
    val reasons = buildMap {
        report.gaveUp[ServiceNight.WaitedFor.TABLE]?.let { put(R.string.tip_table, it) }
        report.gaveUp[ServiceNight.WaitedFor.ORDER]?.let { put(R.string.tip_order, it) }
        report.gaveUp[ServiceNight.WaitedFor.FOOD]?.let { put(R.string.tip_food, it) }
        if (report.gaveUp.isEmpty() && s.gaveUpWaiting > 0) put(R.string.tip_order, s.gaveUpWaiting)
        if (s.unfedOutOfStock > 0) put(R.string.tip_stock, s.unfedOutOfStock)
        if (s.walkedOut > 0) put(R.string.tip_menu, s.walkedOut)
        if (s.unfedKitchenFull > 0) put(R.string.tip_cooks, s.unfedKitchenFull)
    }
    val (tip, count) = reasons.maxByOrNull { it.value } ?: return null
    return stringResource(tip, count)
}

/** The receipt that slides up over the restaurant once the last guest has left. */
@Composable
private fun EndOfNightPanel(visible: Boolean, report: DayReport, onShowBill: () -> Unit, newTable: Boolean = false, modifier: Modifier = Modifier) {
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
            if (newTable) {
                Text(stringResource(R.string.new_table_tomorrow), style = MaterialTheme.typography.titleMedium, color = LeafGreen, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            // The biggest reason guests went without, with what to do about it.
            lostTip(report)?.let { tip ->
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = DisasterRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
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

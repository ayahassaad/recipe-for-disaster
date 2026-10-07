package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.scene.SceneTarget
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.LeafGreen
import com.recipefordisaster.app.ui.theme.MustardAmber
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentCatalog
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.menu.violates
import com.recipefordisaster.domain.restaurant.TableGrowth
import com.recipefordisaster.domain.simulation.MorningAdvisor

private const val BUY_STEP = 5.0

/**
 * What pops up when the player taps something in the restaurant: a short
 * title, how it's doing, and one or two buttons. Choices show in the scene
 * straight away (the oven stops smoking, the shelves fill up) and can be
 * undone until service starts.
 */
@Composable
internal fun SheetContent(target: SceneTarget, uiState: GameUiState.Playing, actions: GameActions) {
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (target) {
            SceneTarget.Oven -> oven(uiState.state)?.let { EquipmentSheet(it, uiState, actions) }
            SceneTarget.Fridge -> com.recipefordisaster.domain.equipment.Fridge.of(uiState.state)?.let { EquipmentSheet(it, uiState, actions) }
            SceneTarget.Pantry -> PantrySheet(uiState, actions)
            SceneTarget.MenuBoard -> MenuSheet(uiState, actions)
            SceneTarget.Mop -> CleaningSheet(uiState, actions)
            SceneTarget.Tables -> TablesSheet(uiState, actions)
            is SceneTarget.Decorate -> DecorSheet(uiState, actions)
            SceneTarget.HiringSign -> HiringSheet(uiState, actions)
            is SceneTarget.Staff -> {
                val person = (uiState.state.employees + uiState.state.applicants).firstOrNull { it.id == target.id }
                if (person != null) PersonSheet(person, uiState, actions)
            }
        }
    }
}

@Composable
private fun EquipmentSheet(equipment: com.recipefordisaster.domain.equipment.Equipment, uiState: GameUiState.Playing, actions: GameActions) {
    val isFridge = com.recipefordisaster.domain.equipment.Fridge.isFridge(equipment)
    val upgrading = equipment.id in uiState.plan.upgrades
    val repairing = equipment.id in uiState.plan.repairs && !upgrading
    val cost = EquipmentOperations.repairCost(equipment)
    val next = EquipmentCatalog.nextModel(equipment)
    SectionTitle(if (upgrading && next != null) next.name else equipment.name)
    Meter(stringResource(R.string.condition), if (repairing || upgrading) 100 else equipment.condition)
    if (EquipmentOperations.isBroken(equipment) && !repairing && !upgrading) {
        Text(stringResource(if (isFridge) R.string.fridge_broken else R.string.broken), style = MaterialTheme.typography.bodyLarge, color = DisasterRed)
    } else if (isFridge && com.recipefordisaster.domain.equipment.Fridge.isWorn(equipment) && !repairing && !upgrading) {
        Text(stringResource(R.string.fridge_worn), style = MaterialTheme.typography.bodyLarge, color = MustardAmber)
    }
    when {
        upgrading -> {}
        repairing -> OutlinedButton(onClick = { actions.onToggleRepair(equipment.id) }) { Text(stringResource(R.string.undo)) }
        cost > 0 -> BigAction(stringResource(R.string.fix, coins(cost)), enabled = uiState.cashNow >= cost) { actions.onToggleRepair(equipment.id) }
        else -> Text(stringResource(R.string.fine), style = MaterialTheme.typography.bodyLarge)
    }

    // Buying something better.
    SectionTitle(stringResource(R.string.upgrade_title))
    when {
        upgrading -> {
            Text(stringResource(R.string.upgrade_ordered, next?.name ?: ""), style = MaterialTheme.typography.bodyLarge, color = LeafGreen)
            OutlinedButton(onClick = { actions.onToggleUpgrade(equipment.id) }) { Text(stringResource(R.string.undo)) }
        }
        next == null -> Text(stringResource(if (isFridge) R.string.upgrade_best_fridge else R.string.upgrade_best), style = MaterialTheme.typography.bodyLarge)
        else -> {
            Text(stringResource(if (isFridge) R.string.upgrade_pitch_fridge else R.string.upgrade_pitch, next.name), style = MaterialTheme.typography.bodyLarge)
            val affordable = uiState.cashNow >= next.price
            BigAction(stringResource(R.string.upgrade_buy, next.name, coins(next.price)), enabled = affordable) {
                if (repairing) actions.onToggleRepair(equipment.id) // no need to fix the old one too
                actions.onToggleUpgrade(equipment.id)
            }
        }
    }
}

@Composable
private fun CleaningSheet(uiState: GameUiState.Playing, actions: GameActions) {
    SectionTitle(stringResource(R.string.cleanliness))
    Meter(stringResource(R.string.cleanliness), uiState.morning.restaurant.cleanliness)
    Text(stringResource(R.string.cleaning_hint), style = MaterialTheme.typography.bodyLarge)
    when {
        uiState.plan.deepClean -> OutlinedButton(onClick = actions.onToggleDeepClean) { Text(stringResource(R.string.undo)) }
        uiState.state.restaurant.cleanliness < 100 -> BigAction(
            stringResource(R.string.clean, coins(DecisionApplier.DEEP_CLEAN_COST)),
            enabled = uiState.cashNow >= DecisionApplier.DEEP_CLEAN_COST,
            onClick = actions.onToggleDeepClean,
        )
    }
}

@Composable
private fun PantrySheet(uiState: GameUiState.Playing, actions: GameActions) {
    val morning = uiState.morning
    val restock = MorningAdvisor.restockList(morning)
    val restockCost = restock.entries.sumOf { (id, quantity) ->
        DecisionApplier.purchaseCost(morning.inventory.ingredients[id]?.purchasePricePerUnit ?: 0, quantity)
    }
    SectionTitle(stringResource(R.string.pantry_title))
    if (restock.isEmpty()) {
        Text(stringResource(R.string.food_stocked), style = MaterialTheme.typography.titleMedium, color = LeafGreen)
    } else {
        BigAction(stringResource(R.string.food_restock_all, coins(restockCost)), enabled = uiState.cashNow >= restockCost, onClick = actions.onRestockAll)
        // Say what that buys, so a small price doesn't look like a mistake.
        Text(
            stringResource(
                R.string.food_restock_what,
                restock.entries.joinToString(", ") { (id, quantity) ->
                    val ingredient = morning.inventory.ingredients.getValue(id)
                    "${formatQuantity(quantity)} ${ingredient.unit} ${ingredient.name.lowercase()}"
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
    if (uiState.plan.purchases.isNotEmpty()) {
        TextButton(onClick = actions.onClearPurchases) { Text(stringResource(R.string.food_undo)) }
    }
    morning.inventory.ingredients.values
        .sortedWith(compareBy({ MorningAdvisor.nightsOfStock(morning, it) == null }, { it.name }))
        .forEach { ingredient ->
            PantryRow(
                ingredient = ingredient,
                nights = MorningAdvisor.nightsOfStock(morning, ingredient),
                bought = uiState.plan.purchases[ingredient.id] ?: 0.0,
                canAfford = uiState.cashNow >= DecisionApplier.purchaseCost(ingredient.purchasePricePerUnit, BUY_STEP),
                onBuy = { actions.onAdjustPurchase(ingredient.id, BUY_STEP) },
            )
        }
}

@Composable
private fun PantryRow(ingredient: Ingredient, nights: Double?, bought: Double, canAfford: Boolean, onBuy: () -> Unit) {
    val (label, color) = when {
        nights == null -> stringResource(R.string.stock_unused) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        nights < 0.1 -> stringResource(R.string.stock_out) to DisasterRed
        nights < 2.0 -> stringResource(R.string.stock_low) to MustardAmber // same two-night target the restock list uses
        else -> stringResource(R.string.stock_ok) to LeafGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ingredient.name, style = MaterialTheme.typography.titleMedium)
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                Text(
                    "  " + stringResource(R.string.stock_amount, formatQuantity(ingredient.quantityOnHand), ingredient.unit) +
                        if (bought > 0) "  " + stringResource(R.string.stock_bought, formatQuantity(bought)) else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val description = stringResource(R.string.buy_more_label, ingredient.name)
        // The price is on the button, so buying never costs a surprise.
        FilledTonalButton(onClick = onBuy, enabled = canAfford, modifier = Modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.buy_more_price, coins(DecisionApplier.purchaseCost(ingredient.purchasePricePerUnit, BUY_STEP))))
        }
    }
}

@Composable
private fun MenuSheet(uiState: GameUiState.Playing, actions: GameActions) {
    val morning = uiState.morning
    val existingIds = uiState.state.menu.map { it.id }.toSet()
    SectionTitle(stringResource(R.string.menu_title))
    // Diets nothing on tonight's menu suits: guests with them read the menu at the door and leave.
    val serving = morning.menu.filter { it.available }
    val leftOut = DIETS_SHOWN.filter { diet -> serving.isNotEmpty() && serving.all { it.violates(diet) } }
    if (leftOut.isNotEmpty()) {
        Text(
            stringResource(R.string.menu_nothing_for, leftOut.map { dietLabel(it) }.joinToString(", ")),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = DisasterRed,
        )
    }
    morning.menu.filter { it.id in existingIds }.forEach { dish ->
        DishRow(
            dish,
            plateCost = com.recipefordisaster.domain.simulation.OutlookCalculator.plateCost(dish, morning.inventory),
            onDown = { actions.onAdjustPrice(dish.id, -1) },
            onUp = { actions.onAdjustPrice(dish.id, 1) },
            onServing = { actions.onSetDishAvailable(dish.id, it) },
        )
    }
    val newDishes = RecipeBook.dishes.filter { it.id !in existingIds }
    if (newDishes.isNotEmpty()) {
        SectionTitle(stringResource(R.string.new_dishes_title))
        newDishes.forEach { dish ->
            val adding = dish.id in uiState.plan.dishesToAdd
            val needs = dish.recipe.ingredientRequirements.keys.joinToString { id -> morning.inventory.ingredients[id]?.name?.lowercase() ?: id.value }
            val suits = DIETS_SHOWN.filter { diet -> leftOut.contains(diet) && !dish.violates(diet) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dish.name, style = MaterialTheme.typography.titleMedium)
                    Text(coins(dish.sellingPrice) + " · " + stringResource(R.string.new_dish_needs, needs), style = MaterialTheme.typography.bodyMedium)
                    if (suits.isNotEmpty()) {
                        Text(stringResource(R.string.menu_suits, suits.map { dietLabel(it) }.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = LeafGreen)
                    }
                }
                if (adding) {
                    OutlinedButton(onClick = { actions.onToggleAddDish(dish.id) }) { Text(stringResource(R.string.undo)) }
                } else {
                    FilledTonalButton(onClick = { actions.onToggleAddDish(dish.id) }, enabled = uiState.cashNow >= RecipeBook.ADD_DISH_COST) {
                        Text(stringResource(R.string.new_dish_add, coins(RecipeBook.ADD_DISH_COST)))
                    }
                }
            }
        }
    }
}

@Composable
private fun DishRow(dish: Dish, plateCost: Double, onDown: () -> Unit, onUp: () -> Unit, onServing: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dish.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = dish.available, onCheckedChange = onServing, modifier = Modifier.semantics { contentDescription = dish.name })
        }
        val downLabel = stringResource(R.string.menu_price_down, dish.name)
        val upLabel = stringResource(R.string.menu_price_up, dish.name)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDown, modifier = Modifier.semantics { contentDescription = downLabel }) { Text("−") }
            Text(
                coins(dish.sellingPrice),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = when {
                    dish.sellingPrice > dish.referencePrice -> DisasterRed
                    dish.sellingPrice < dish.referencePrice -> LeafGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.widthIn(min = 84.dp),
            )
            OutlinedButton(onClick = onUp, modifier = Modifier.semantics { contentDescription = upLabel }) { Text("+") }
        }
        Text(stringResource(R.string.menu_fair, coins(dish.referencePrice)), style = MaterialTheme.typography.bodySmall)
        // What each plate costs in ingredients, and who can't eat it.
        val cost = kotlin.math.ceil(plateCost).toLong()
        Text(stringResource(R.string.menu_plate_cost, coins(cost), coins(dish.sellingPrice - cost)), style = MaterialTheme.typography.bodySmall)
        val cantEat = DIETS_SHOWN.filter { dish.violates(it) }
        if (cantEat.isNotEmpty()) {
            Text(stringResource(R.string.menu_not_for, cantEat.map { dietLabel(it) }.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The diets the game can actually check a dish against (vegetarian and vegan aren't modelled yet). */
private val DIETS_SHOWN = listOf(
    com.recipefordisaster.domain.customer.DietaryRequirement.GLUTEN_FREE,
    com.recipefordisaster.domain.customer.DietaryRequirement.LACTOSE_INTOLERANT,
    com.recipefordisaster.domain.customer.DietaryRequirement.NUT_ALLERGY,
    com.recipefordisaster.domain.customer.DietaryRequirement.SHELLFISH_ALLERGY,
)

@Composable
private fun dietLabel(diet: com.recipefordisaster.domain.customer.DietaryRequirement): String = stringResource(
    when (diet) {
        com.recipefordisaster.domain.customer.DietaryRequirement.GLUTEN_FREE -> R.string.diet_gluten_free
        com.recipefordisaster.domain.customer.DietaryRequirement.LACTOSE_INTOLERANT -> R.string.diet_no_dairy
        com.recipefordisaster.domain.customer.DietaryRequirement.NUT_ALLERGY -> R.string.diet_nut_allergy
        com.recipefordisaster.domain.customer.DietaryRequirement.SHELLFISH_ALLERGY -> R.string.diet_shellfish_allergy
        com.recipefordisaster.domain.customer.DietaryRequirement.VEGETARIAN -> R.string.diet_vegetarian
        com.recipefordisaster.domain.customer.DietaryRequirement.VEGAN -> R.string.diet_vegan
    },
)

@Composable
private fun HiringSheet(uiState: GameUiState.Playing, actions: GameActions) {
    val need = MorningAdvisor.staffNeed(uiState.morning)
    val outlook = com.recipefordisaster.domain.simulation.OutlookCalculator.forTonight(uiState.morning)
    SectionTitle(stringResource(R.string.hiring_title))
    Text(
        text = when (need) {
            MorningAdvisor.Need.COOK -> stringResource(R.string.need_cook, outlook.kitchenCapacity, outlook.expectedCustomers)
            MorningAdvisor.Need.SERVER -> stringResource(R.string.need_server)
            MorningAdvisor.Need.DISHWASHER -> stringResource(R.string.need_dishwasher)
            null -> stringResource(R.string.need_none)
        },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (need != null) FontWeight.SemiBold else FontWeight.Normal,
        color = if (need != null) DisasterRed else MaterialTheme.colorScheme.onSurface,
    )
    val hiredToday = uiState.state.applicants.filter { it.id in uiState.plan.hires }
    hiredToday.forEach { person ->
        PersonLine(person) { Text(stringResource(R.string.staff_new), style = MaterialTheme.typography.bodyMedium, color = LeafGreen) }
        OutlinedButton(onClick = { actions.onToggleHire(person.id) }) { Text(stringResource(R.string.undo)) }
    }
    val applying = uiState.state.applicants.filter { it.id !in uiState.plan.hires }
    // Hosts and bussers only apply once there's money to pay them.
    if (uiState.state.restaurant.cash < StaffingMarket.LUXURY_STAFF_CASH) {
        Text(stringResource(R.string.hiring_luxury_locked, coins(StaffingMarket.LUXURY_STAFF_CASH)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
    if (applying.isEmpty() && hiredToday.isEmpty()) Text(stringResource(R.string.hiring_empty), style = MaterialTheme.typography.bodyLarge)
    // People with the job you need first.
    applying.sortedByDescending { need != null && roleMatches(it.role, need) }.forEach { applicant ->
        val fee = StaffingMarket.hiringFee(applicant)
        ApplicantLine(applicant, recommended = need != null && roleMatches(applicant.role, need)) {
            StarRating((applicant.skill + applicant.speed + applicant.reliability) / 3)
            if (applicant.personalityTraits.isNotEmpty()) {
                Text(applicant.personalityTraits.map { traitLabel(it) }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FilledTonalButton(onClick = { actions.onToggleHire(applicant.id) }, enabled = uiState.cashNow >= fee) {
            Text(if (uiState.cashNow >= fee) stringResource(R.string.hire, coins(fee)) else stringResource(R.string.cant_afford))
        }
    }
}

private fun roleMatches(role: com.recipefordisaster.domain.employee.Role, need: MorningAdvisor.Need) = when (need) {
    MorningAdvisor.Need.COOK -> role == com.recipefordisaster.domain.employee.Role.COOK
    MorningAdvisor.Need.SERVER -> role == com.recipefordisaster.domain.employee.Role.SERVER || role == com.recipefordisaster.domain.employee.Role.MANAGER
    MorningAdvisor.Need.DISHWASHER -> role == com.recipefordisaster.domain.employee.Role.DISHWASHER
}

/** An applicant: their job in big letters (that's the first thing you need to know), then who they are. */
@Composable
private fun ApplicantLine(person: Employee, recommended: Boolean, extra: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(roleLabel(person.role), style = MaterialTheme.typography.headlineSmall)
            if (recommended) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.recommended),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(LeafGreen, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        // What the job actually does, so it's clear who to hire.
        Text(roleDescription(person.role), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Spacer(modifier = Modifier.height(4.dp))
        PersonLine(person, showRole = false) {
            Text(
                stringResource(R.string.applicant_wage, coins(StaffingMarket.hiringFee(person)), perDay(person.salaryPerDay)),
                style = MaterialTheme.typography.bodyMedium,
            )
            extra()
        }
    }
}

@Composable
private fun PersonSheet(person: Employee, uiState: GameUiState.Playing, actions: GameActions) {
    val plan = uiState.plan
    val isNew = person.id in plan.hires
    val leaving = person.id in plan.fires
    val resting = person.id in plan.restDays
    PersonLine(person) {
        val status = when {
            isNew -> stringResource(R.string.staff_new)
            leaving -> stringResource(R.string.staff_leaving)
            resting -> stringResource(R.string.staff_resting)
            person.status == EmployeeStatus.SICK -> stringResource(R.string.staff_sick)
            person.stress >= 70 -> stringResource(R.string.staff_tired)
            else -> null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    // How they're doing. Energy is stress turned round, so every bar reads "full is good".
    Meter(stringResource(R.string.staff_mood), person.morale)
    Meter(stringResource(R.string.staff_energy), 100 - person.stress)
    Meter(stringResource(R.string.staff_skill), person.skill)
    // The last cook on shift: without them nobody cooks tonight, so say so before it happens.
    val cooksOnShift = uiState.morning.employees.count { it.status == EmployeeStatus.ACTIVE && it.role == com.recipefordisaster.domain.employee.Role.COOK }
    val onlyCook = person.role == com.recipefordisaster.domain.employee.Role.COOK && person.status == EmployeeStatus.ACTIVE && cooksOnShift <= 1
    var confirm by remember { mutableStateOf<StaffAction?>(null) }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (action) {
                        StaffAction.DAY_OFF -> stringResource(R.string.confirm_day_off_title, person.name)
                        StaffAction.LET_GO -> stringResource(R.string.confirm_let_go_title, person.name)
                    },
                )
            },
            text = {
                val lines = buildList {
                    if (action == StaffAction.LET_GO) add(stringResource(R.string.confirm_let_go_body, coins(StaffingMarket.severance(person))))
                    if (onlyCook) add(stringResource(R.string.confirm_only_cook))
                }
                Text(lines.joinToString("\n\n"))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        StaffAction.DAY_OFF -> actions.onToggleRestDay(person.id)
                        StaffAction.LET_GO -> actions.onToggleFire(person.id)
                    }
                }) {
                    Text(
                        when (action) {
                            StaffAction.DAY_OFF -> stringResource(R.string.staff_day_off)
                            StaffAction.LET_GO -> stringResource(R.string.staff_fire)
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            isNew -> OutlinedButton(onClick = { actions.onToggleHire(person.id) }) { Text(stringResource(R.string.undo)) }
            leaving -> OutlinedButton(onClick = { actions.onToggleFire(person.id) }) { Text(stringResource(R.string.undo)) }
            resting -> OutlinedButton(onClick = { actions.onToggleRestDay(person.id) }) { Text(stringResource(R.string.undo)) }
            else -> {
                if (person.status == EmployeeStatus.ACTIVE) {
                    // A day off is easy to undo, so it only asks when it would leave the kitchen empty.
                    FilledTonalButton(onClick = { if (onlyCook) confirm = StaffAction.DAY_OFF else actions.onToggleRestDay(person.id) }) {
                        Text(stringResource(R.string.staff_day_off))
                    }
                }
                OutlinedButton(onClick = { confirm = StaffAction.LET_GO }, enabled = uiState.cashNow >= StaffingMarket.severance(person)) {
                    Text(stringResource(R.string.staff_fire))
                }
            }
        }
    }
}

@Composable
private fun PersonLine(person: Employee, showRole: Boolean = true, extra: @Composable () -> Unit) {
    val mood = stringResource(
        when {
            person.morale >= 66 -> R.string.mood_happy
            person.morale >= 33 -> R.string.mood_ok
            else -> R.string.mood_unhappy
        },
        person.name,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        MoodFace(morale = person.morale, stress = person.stress, description = mood, size = 52.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            if (showRole) {
                Text(stringResource(R.string.staff_role_wage, roleLabel(person.role), perDay(person.salaryPerDay)), style = MaterialTheme.typography.bodyMedium)
            }
            extra()
        }
    }
}

@Composable
private fun BigAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    if (enabled) {
        SignButton(text = text, onClick = onClick)
    } else {
        // Still show what it is and what it costs, so the player knows how much to save up.
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(stringResource(R.string.cant_afford), style = MaterialTheme.typography.bodyMedium, color = DisasterRed)
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

private enum class StaffAction { DAY_OFF, LET_GO }

@Composable
private fun TablesSheet(uiState: GameUiState.Playing, actions: GameActions) {
    val have = uiState.state.restaurant.tables
    val buying = uiState.plan.buyTable
    SectionTitle(stringResource(R.string.tables_title))
    Text(stringResource(R.string.tables_have, if (buying) have + 1 else have), style = MaterialTheme.typography.bodyLarge)
    val price = TableGrowth.nextTablePrice(have)
    when {
        buying -> {
            Text(stringResource(R.string.tables_bought, have + 1), style = MaterialTheme.typography.bodyLarge, color = LeafGreen)
            OutlinedButton(onClick = actions.onToggleBuyTable) { Text(stringResource(R.string.undo)) }
        }
        TableGrowth.growsFree(have) -> Text(stringResource(R.string.tables_free_coming, TableGrowth.FREE_UP_TO), style = MaterialTheme.typography.bodyLarge)
        price == null -> Text(stringResource(R.string.tables_full), style = MaterialTheme.typography.bodyLarge)
        else -> {
            Text(stringResource(R.string.tables_pitch), style = MaterialTheme.typography.bodyLarge)
            BigAction(stringResource(R.string.tables_buy, have + 1, coins(price)), enabled = uiState.cashNow >= price, onClick = actions.onToggleBuyTable)
        }
    }
}

@Composable
private fun roleDescription(role: com.recipefordisaster.domain.employee.Role): String = stringResource(
    when (role) {
        com.recipefordisaster.domain.employee.Role.COOK -> R.string.job_cook
        com.recipefordisaster.domain.employee.Role.SERVER -> R.string.job_server
        com.recipefordisaster.domain.employee.Role.DISHWASHER -> R.string.job_dishwasher
        com.recipefordisaster.domain.employee.Role.MANAGER -> R.string.job_manager
        com.recipefordisaster.domain.employee.Role.HOST -> R.string.job_host
        com.recipefordisaster.domain.employee.Role.BUSSER -> R.string.job_busser
    },
)

@Composable
private fun DecorSheet(uiState: GameUiState.Playing, actions: GameActions) {
    SectionTitle(stringResource(R.string.decor_title))
    Text(stringResource(R.string.decor_intro), style = MaterialTheme.typography.bodyLarge)
    com.recipefordisaster.domain.restaurant.Decor.entries.forEach { item ->
        val owned = item in uiState.state.restaurant.decor
        val buying = item in uiState.plan.buyDecor
        val (name, effect) = when (item) {
            com.recipefordisaster.domain.restaurant.Decor.PLANTS -> R.string.decor_plants to R.string.decor_plants_effect
            com.recipefordisaster.domain.restaurant.Decor.TABLECLOTHS -> R.string.decor_tablecloths to R.string.decor_tablecloths_effect
            com.recipefordisaster.domain.restaurant.Decor.FISH_TANK -> R.string.decor_fish_tank to R.string.decor_fish_tank_effect
            com.recipefordisaster.domain.restaurant.Decor.PAINTING -> R.string.decor_painting to R.string.decor_painting_effect
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(effect), style = MaterialTheme.typography.bodyMedium)
            }
            when {
                owned -> Text(stringResource(R.string.decor_owned), style = MaterialTheme.typography.bodyMedium, color = LeafGreen)
                buying -> OutlinedButton(onClick = { actions.onToggleDecor(item) }) { Text(stringResource(R.string.undo)) }
                else -> FilledTonalButton(onClick = { actions.onToggleDecor(item) }, enabled = uiState.cashNow >= item.price) {
                    Text(coins(item.price))
                }
            }
        }
    }
}

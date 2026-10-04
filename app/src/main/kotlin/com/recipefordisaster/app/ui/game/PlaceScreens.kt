package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.LeafGreen
import com.recipefordisaster.app.ui.theme.MustardAmber
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.MorningAdvisor

private const val BUY_STEP = 5.0

// ---------------------------------------------------------------- Staff

/**
 * The team: a face for each person showing how they're doing, what they
 * cost a day, and at most two buttons. People hired this morning are
 * already in the team with an Undo; applicants are below with a star
 * rating instead of a column of stats.
 */
@Composable
internal fun StaffScreen(uiState: GameUiState.Playing, actions: GameActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val plan = uiState.plan
    val costs = remember(uiState.morning) { DailyCosts.of(uiState.morning) }
    val hiredToday = uiState.state.applicants.filter { it.id in plan.hires }
    val team = uiState.state.employees + hiredToday
    val applying = uiState.state.applicants.filter { it.id !in plan.hires }

    PlaceScaffold(stringResource(R.string.staff_title), uiState.cashNow, onBack, modifier) {
        item { Text(stringResource(R.string.staff_costs, coins(costs.wages)), style = MaterialTheme.typography.titleMedium) }
        items(team, key = { it.id.value }) { person ->
            val isNew = person.id in plan.hires
            val leaving = person.id in plan.fires
            val resting = person.id in plan.restDays
            GameCard(modifier = Modifier.alpha(if (leaving) 0.55f else 1f)) {
                PersonRow(person) {
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
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isNew -> OutlinedButton(onClick = { actions.onToggleHire(person.id) }) { Text(stringResource(R.string.undo)) }
                        leaving -> OutlinedButton(onClick = { actions.onToggleFire(person.id) }) { Text(stringResource(R.string.undo)) }
                        resting -> OutlinedButton(onClick = { actions.onToggleRestDay(person.id) }) { Text(stringResource(R.string.undo)) }
                        else -> {
                            if (person.status == EmployeeStatus.ACTIVE) {
                                FilledTonalButton(onClick = { actions.onToggleRestDay(person.id) }) { Text(stringResource(R.string.staff_day_off)) }
                            }
                            OutlinedButton(
                                onClick = { actions.onToggleFire(person.id) },
                                enabled = uiState.cashNow >= StaffingMarket.severance(person),
                            ) { Text(stringResource(R.string.staff_fire)) }
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.hiring_title)) }
        if (applying.isEmpty()) {
            item { Text(stringResource(R.string.hiring_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(applying, key = { "applicant-" + it.id.value }) { applicant ->
            val fee = StaffingMarket.hiringFee(applicant)
            GameCard {
                PersonRow(applicant) {
                    StarRating((applicant.skill + applicant.speed + applicant.reliability) / 3)
                    if (applicant.personalityTraits.isNotEmpty()) {
                        Text(applicant.personalityTraits.map { traitLabel(it) }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(onClick = { actions.onToggleHire(applicant.id) }, enabled = uiState.cashNow >= fee) {
                    Text(if (uiState.cashNow >= fee) stringResource(R.string.hire, coins(fee)) else stringResource(R.string.cant_afford))
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: Employee, extra: @Composable () -> Unit) {
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
            Text(stringResource(R.string.staff_role_wage, roleLabel(person.role), perDay(person.salaryPerDay)), style = MaterialTheme.typography.bodyMedium)
            extra()
        }
    }
}

// ---------------------------------------------------------------- Food

/**
 * Food in one place: a single "restock everything" button, the pantry as
 * simple Out / Low / Enough labels, then the menu (prices and what's on)
 * and new dishes.
 */
@Composable
internal fun FoodScreen(uiState: GameUiState.Playing, actions: GameActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val morning = uiState.morning
    val restock = remember(morning) { MorningAdvisor.restockList(morning) }
    val restockCost = restock.entries.sumOf { (id, quantity) ->
        DecisionApplier.purchaseCost(morning.inventory.ingredients[id]?.purchasePricePerUnit ?: 0, quantity)
    }
    val existingIds = uiState.state.menu.map { it.id }.toSet()
    val pantry = morning.inventory.ingredients.values.sortedWith(compareBy({ MorningAdvisor.nightsOfStock(morning, it) == null }, { it.name }))

    PlaceScaffold(stringResource(R.string.food_title), uiState.cashNow, onBack, modifier) {
        item {
            GameCard {
                if (restock.isEmpty()) {
                    Text(stringResource(R.string.food_stocked), style = MaterialTheme.typography.titleMedium)
                } else {
                    SignButton(
                        text = if (uiState.cashNow >= restockCost) stringResource(R.string.food_restock_all, coins(restockCost)) else stringResource(R.string.cant_afford),
                        onClick = actions.onRestockAll,
                    )
                }
                if (uiState.plan.purchases.isNotEmpty()) {
                    TextButton(onClick = actions.onClearPurchases) { Text(stringResource(R.string.food_undo)) }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.pantry_title)) }
        items(pantry, key = { it.id.value }) { ingredient ->
            PantryRow(
                ingredient = ingredient,
                nights = MorningAdvisor.nightsOfStock(morning, ingredient),
                bought = uiState.plan.purchases[ingredient.id] ?: 0.0,
                canAfford = uiState.cashNow >= DecisionApplier.purchaseCost(ingredient.purchasePricePerUnit, BUY_STEP),
                onBuy = { actions.onAdjustPurchase(ingredient.id, BUY_STEP) },
            )
        }

        item { SectionTitle(stringResource(R.string.menu_title)) }
        items(morning.menu.filter { it.id in existingIds }, key = { "dish-" + it.id.value }) { dish ->
            DishRow(dish, onDown = { actions.onAdjustPrice(dish.id, -1) }, onUp = { actions.onAdjustPrice(dish.id, 1) }, onServing = { actions.onSetDishAvailable(dish.id, it) })
        }

        val newDishes = RecipeBook.dishes.filter { it.id !in existingIds }
        if (newDishes.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.new_dishes_title)) }
            items(newDishes, key = { "new-" + it.id.value }) { dish ->
                val adding = dish.id in uiState.plan.dishesToAdd
                val needs = dish.recipe.ingredientRequirements.keys.joinToString { id -> morning.inventory.ingredients[id]?.name?.lowercase() ?: id.value }
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dish.name, style = MaterialTheme.typography.titleMedium)
                            Text(coins(dish.sellingPrice) + " · " + stringResource(R.string.new_dish_needs, needs), style = MaterialTheme.typography.bodyMedium)
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
    GameCard {
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
            FilledTonalButton(onClick = onBuy, enabled = canAfford, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.buy_more))
            }
        }
    }
}

@Composable
private fun DishRow(dish: Dish, onDown: () -> Unit, onUp: () -> Unit, onServing: (Boolean) -> Unit) {
    GameCard {
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
        Text(stringResource(R.string.menu_fair, coins(dish.referencePrice)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- Kitchen

/** Cleanliness and equipment: one bar and at most one button each. */
@Composable
internal fun KitchenScreen(uiState: GameUiState.Playing, actions: GameActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state = uiState.state
    val cleaning = uiState.plan.deepClean

    PlaceScaffold(stringResource(R.string.kitchen_title), uiState.cashNow, onBack, modifier) {
        item {
            GameCard {
                Meter(stringResource(R.string.cleanliness), uiState.morning.restaurant.cleanliness)
                Spacer(modifier = Modifier.height(10.dp))
                if (cleaning) {
                    OutlinedButton(onClick = actions.onToggleDeepClean) { Text(stringResource(R.string.undo)) }
                } else if (state.restaurant.cleanliness < 100) {
                    FilledTonalButton(onClick = actions.onToggleDeepClean, enabled = uiState.cashNow >= DecisionApplier.DEEP_CLEAN_COST) {
                        Text(stringResource(R.string.clean, coins(DecisionApplier.DEEP_CLEAN_COST)))
                    }
                }
            }
        }
        items(state.equipment, key = { it.id.value }) { equipment ->
            val repairing = equipment.id in uiState.plan.repairs
            val cost = EquipmentOperations.repairCost(equipment)
            GameCard {
                Text(equipment.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Meter(stringResource(R.string.condition), if (repairing) 100 else equipment.condition)
                if (EquipmentOperations.isBroken(equipment) && !repairing) {
                    Text(stringResource(R.string.broken), style = MaterialTheme.typography.bodyMedium, color = DisasterRed, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                when {
                    repairing -> OutlinedButton(onClick = { actions.onToggleRepair(equipment.id) }) { Text(stringResource(R.string.undo)) }
                    cost > 0 -> FilledTonalButton(onClick = { actions.onToggleRepair(equipment.id) }, enabled = uiState.cashNow >= cost) {
                        Text(stringResource(R.string.fix, coins(cost)))
                    }
                    else -> Text(stringResource(R.string.fine), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

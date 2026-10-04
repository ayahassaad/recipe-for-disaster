package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.event.Severity
import com.recipefordisaster.domain.simulation.Advice
import com.recipefordisaster.domain.simulation.FiredEvent
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.MorningAdvisor
import com.recipefordisaster.domain.simulation.OutlookCalculator
import com.recipefordisaster.domain.simulation.SimulationLogEntry

private const val LOG_ENTRIES_SHOWN = 40

/**
 * The morning at a glance: what happened overnight, a to-do list of things
 * that will hurt tonight if ignored (each with a one-tap fix), what tonight
 * should look like, and what's already been done this morning.
 */
@Composable
internal fun TodayTab(uiState: GameUiState.Playing, actions: GameActions, onGoTo: (GameTab) -> Unit, modifier: Modifier = Modifier) {
    val morning = uiState.morning
    // Advice reads the restaurant as the player has set it up, so fixing something removes it from the list.
    val advice = remember(morning) { MorningAdvisor.adviceFor(morning) }
    val forecast = remember(morning) { MorningAdvisor.forecast(morning) }
    val outlook = remember(morning) { OutlookCalculator.forTonight(morning) }
    val recentLog = remember(uiState.state.log) { uiState.state.log.asReversed().take(LOG_ENTRIES_SHOWN) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.lastEvent?.let { event -> item { EventCard(event) } }

        item { SectionHeader(stringResource(R.string.todo_heading)) }
        if (advice.isEmpty()) {
            item {
                GameCard { Text(stringResource(R.string.todo_all_good), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
            }
        }
        items(advice) { item -> AdviceCard(item, uiState.cashNow, actions, onGoTo) }

        item {
            GameCard {
                SectionHeader(stringResource(R.string.tonight_heading))
                Text(pluralStringResource(R.plurals.tonight_guests, outlook.expectedCustomers, outlook.expectedCustomers), style = MaterialTheme.typography.bodyLarge)
                Text(pluralStringResource(R.plurals.tonight_meals, outlook.kitchenCapacity, outlook.kitchenCapacity), style = MaterialTheme.typography.bodyLarge)
                if (outlook.demandModifierPercent > 0) ToneLine(stringResource(R.string.tonight_demand_up), LogTone.NEUTRAL)
                if (outlook.demandModifierPercent < 0) ToneLine(stringResource(R.string.tonight_demand_down), LogTone.NEUTRAL)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tonight_money, coins(forecast.expectedIncome), coins(forecast.dailyCosts.total + forecast.expectedIngredientUse)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatRow(
                    label = stringResource(R.string.tonight_profit),
                    value = (if (forecast.expectedProfit > 0) "+" else "") + coins(forecast.expectedProfit),
                    valueColor = moneyColor(forecast.expectedProfit),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    emoji = "⭐",
                    label = stringResource(R.string.dashboard_reputation),
                    value = morning.restaurant.reputation.toString(),
                    meterValue = morning.restaurant.reputation,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    emoji = "🧽",
                    label = stringResource(R.string.dashboard_cleanliness),
                    value = morning.restaurant.cleanliness.toString(),
                    meterValue = morning.restaurant.cleanliness,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (uiState.preview.log.isNotEmpty()) {
            item {
                GameCard {
                    SectionHeader(stringResource(R.string.done_heading))
                    uiState.preview.log.forEach { entry -> ToneLine("• " + entry.message, entry.tone) }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.done_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.dashboard_log_heading)) }
        items(recentLog) { entry -> LogRow(entry) }
    }
}

@Composable
private fun AdviceCard(advice: Advice, cash: Long, actions: GameActions, onGoTo: (GameTab) -> Unit) {
    val accent = if (advice.urgent) toneColor(LogTone.BAD) else MaterialTheme.colorScheme.secondary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (advice.urgent) 2.dp else 1.dp, accent),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (advice) {
                is Advice.Restock -> {
                    val unit = advice.ingredient.unit
                    val quantity = formatQuantity(advice.suggestedQuantity)
                    AdviceText(
                        title = stringResource(if (advice.urgent) R.string.todo_restock_urgent else R.string.todo_restock, advice.ingredient.name),
                        detail = stringResource(R.string.todo_restock_detail, formatQuantity(advice.ingredient.quantityOnHand), unit, quantity),
                    )
                    AdviceButton(
                        label = stringResource(R.string.todo_restock_action, quantity, unit, coins(advice.cost)),
                        enabled = cash >= advice.cost,
                        onClick = { actions.onAdjustPurchase(advice.ingredient.id, advice.suggestedQuantity) },
                    )
                }
                is Advice.KitchenTooSmall -> {
                    AdviceText(stringResource(R.string.todo_kitchen), stringResource(R.string.todo_kitchen_detail, advice.expectedCustomers, advice.kitchenCapacity))
                    AdviceButton(stringResource(R.string.todo_kitchen_action), onClick = { onGoTo(GameTab.STAFF) })
                }
                is Advice.LosingMoney -> {
                    AdviceText(
                        stringResource(R.string.todo_losing),
                        stringResource(
                            R.string.todo_losing_detail,
                            coins(advice.forecast.expectedIncome),
                            coins(advice.forecast.dailyCosts.total),
                            coins(advice.forecast.expectedIngredientUse),
                        ),
                    )
                    AdviceButton(stringResource(R.string.todo_losing_action), onClick = { onGoTo(GameTab.STAFF) })
                }
                is Advice.EquipmentBroken -> {
                    AdviceText(stringResource(R.string.todo_broken, advice.equipment.name), stringResource(R.string.todo_broken_detail))
                    AdviceButton(
                        stringResource(R.string.todo_repair_action, coins(advice.repairCost)),
                        enabled = cash >= advice.repairCost,
                        onClick = { actions.onToggleRepair(advice.equipment.id) },
                    )
                }
                is Advice.EquipmentWorn -> {
                    AdviceText(stringResource(R.string.todo_worn, advice.equipment.name), stringResource(R.string.todo_worn_detail, advice.equipment.condition))
                    AdviceButton(
                        stringResource(R.string.todo_repair_action, coins(advice.repairCost)),
                        enabled = cash >= advice.repairCost,
                        onClick = { actions.onToggleRepair(advice.equipment.id) },
                    )
                }
                is Advice.Dirty -> {
                    AdviceText(stringResource(R.string.todo_dirty), stringResource(R.string.todo_dirty_detail, advice.cleanliness))
                    AdviceButton(
                        stringResource(R.string.todo_clean_action, coins(advice.deepCleanCost)),
                        enabled = cash >= advice.deepCleanCost,
                        onClick = actions.onToggleDeepClean,
                    )
                }
                is Advice.StaffExhausted -> {
                    AdviceText(stringResource(R.string.todo_exhausted, advice.employee.name), stringResource(R.string.todo_exhausted_detail, advice.employee.stress))
                    AdviceButton(stringResource(R.string.todo_rest_action), onClick = { actions.onToggleRestDay(advice.employee.id) })
                }
                is Advice.DishUnmakeable -> {
                    AdviceText(stringResource(R.string.todo_unmakeable, advice.dish.name), stringResource(R.string.todo_unmakeable_detail))
                    AdviceButton(stringResource(R.string.todo_pantry_action), onClick = { onGoTo(GameTab.PANTRY) })
                }
            }
        }
    }
}

@Composable
private fun AdviceText(title: String, detail: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(text = detail, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun AdviceButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Spacer(modifier = Modifier.height(8.dp))
    FilledTonalButton(onClick = onClick, enabled = enabled) {
        Text(if (enabled) label else stringResource(R.string.cant_afford))
    }
}

@Composable
internal fun EventCard(event: FiredEvent, modifier: Modifier = Modifier) {
    val accent = toneColor(event.tone)
    val emoji = when {
        event.tone == LogTone.GOOD -> "🎉"
        event.severity >= Severity.SEVERE -> "🚨"
        event.tone == LogTone.BAD -> "😬"
        else -> "📣"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, accent),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = stringResource(R.string.overnight_heading), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(text = event.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ToneLine(text: String, tone: LogTone) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = toneColor(tone),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LogRow(entry: SimulationLogEntry, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        DayBadge(entry.day)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = toneColor(entry.tone),
            modifier = Modifier.weight(1f),
        )
    }
}

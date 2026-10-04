package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.recipefordisaster.domain.simulation.FiredEvent
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.OutlookCalculator
import com.recipefordisaster.domain.simulation.SimulationLogEntry

private const val LOG_ENTRIES_SHOWN = 40

@Composable
internal fun TodayTab(uiState: GameUiState.Playing, modifier: Modifier = Modifier) {
    val state = uiState.state
    // The outlook reflects the plan as it stands, so hiring a cook clears the "not enough cooks" warning straight away.
    val outlook = remember(uiState.preview.state) { OutlookCalculator.forTonight(uiState.preview.state) }
    val recentLog = remember(state.log) { state.log.asReversed().take(LOG_ENTRIES_SHOWN) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.lastEvent?.let { event -> item { EventCard(event) } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    emoji = "⭐",
                    label = stringResource(R.string.dashboard_reputation),
                    value = state.restaurant.reputation.toString(),
                    meterValue = state.restaurant.reputation,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    emoji = "🧽",
                    label = stringResource(R.string.dashboard_cleanliness),
                    value = state.restaurant.cleanliness.toString(),
                    meterValue = state.restaurant.cleanliness,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.ledger.history.isNotEmpty()) {
            item {
                StatCard(
                    emoji = "😋",
                    label = stringResource(R.string.dashboard_satisfaction),
                    value = state.recentSatisfaction.toString(),
                    meterValue = state.recentSatisfaction,
                )
            }
        }

        item {
            GameCard {
                SectionHeader(stringResource(R.string.outlook_heading))
                Text(pluralStringResource(R.plurals.outlook_expected, outlook.expectedCustomers, outlook.expectedCustomers), style = MaterialTheme.typography.bodyLarge)
                Text(pluralStringResource(R.plurals.outlook_kitchen, outlook.kitchenCapacity, outlook.kitchenCapacity), style = MaterialTheme.typography.bodyLarge)
                if (outlook.demandModifierPercent > 0) WarningLine(stringResource(R.string.outlook_demand_up), LogTone.NEUTRAL)
                if (outlook.demandModifierPercent < 0) WarningLine(stringResource(R.string.outlook_demand_down), LogTone.NEUTRAL)
                if (outlook.kitchenTooSmall) WarningLine(stringResource(R.string.outlook_kitchen_short), LogTone.BAD)
                if (outlook.lowStock.isNotEmpty()) {
                    WarningLine(stringResource(R.string.outlook_low_stock, outlook.lowStock.joinToString { it.name }), LogTone.BAD)
                }
            }
        }

        state.ledger.history.lastOrNull()?.let { books ->
            item {
                GameCard {
                    SectionHeader(stringResource(R.string.yesterday_heading))
                    StatRow(stringResource(R.string.yesterday_revenue), coins(books.revenue))
                    StatRow(stringResource(R.string.yesterday_expenses), coins(-books.expenses))
                    if (books.eventCashDelta != 0L) {
                        StatRow(stringResource(R.string.yesterday_events), coins(books.eventCashDelta), valueColor = moneyColor(books.eventCashDelta))
                    }
                    StatRow(stringResource(R.string.yesterday_profit), coins(books.profitOrLoss), valueColor = moneyColor(books.profitOrLoss))
                }
            }
        }

        item {
            GameCard {
                SectionHeader(stringResource(R.string.plan_heading))
                if (uiState.preview.log.isEmpty()) {
                    Text(stringResource(R.string.plan_empty_hint), style = MaterialTheme.typography.bodyMedium)
                } else {
                    uiState.preview.log.forEach { entry -> WarningLine(entry.message, entry.tone) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.dashboard_log_heading)) }
        items(recentLog) { entry -> LogRow(entry) }
    }
}

@Composable
private fun EventCard(event: FiredEvent, modifier: Modifier = Modifier) {
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
private fun WarningLine(text: String, tone: LogTone) {
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

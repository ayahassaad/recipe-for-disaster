package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SimulationLogEntry

/**
 * The main play screen. Renders one of three things depending on
 * [GameUiState]: a loading spinner, the run-ending summary (once
 * [GameState.restaurant]'s status is [RestaurantStatus.BANKRUPT] or
 * [RestaurantStatus.CONDEMNED]), or the day-to-day dashboard with its
 * single approved action for this phase: "Open for the day."
 */
@Composable
fun GameScreen(
    uiState: GameUiState,
    onOpenForTheDay: () -> Unit,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            when (uiState) {
                is GameUiState.Loading -> LoadingContent()
                is GameUiState.Error -> ErrorContent(message = uiState.message, onBackToStart = onBackToStart)
                is GameUiState.Playing -> {
                    if (uiState.state.restaurant.status == RestaurantStatus.BANKRUPT ||
                        uiState.state.restaurant.status == RestaurantStatus.CONDEMNED
                    ) {
                        GameOverContent(state = uiState.state, onBackToStart = onBackToStart)
                    } else {
                        DashboardContent(state = uiState.state, onOpenForTheDay = onOpenForTheDay)
                    }
                }
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
        modifier = modifier.fillMaxSize(),
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
private fun DashboardContent(state: GameState, onOpenForTheDay: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.dashboard_day, state.day),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        StatRow(label = stringResource(R.string.dashboard_cash), value = state.restaurant.cash.toString())
        StatRow(label = stringResource(R.string.dashboard_reputation), value = state.restaurant.reputation.toString())
        StatRow(label = stringResource(R.string.dashboard_cleanliness), value = state.restaurant.cleanliness.toString())

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.dashboard_staff_heading), style = MaterialTheme.typography.titleMedium)
        state.employees.forEach { employee -> EmployeeRow(employee) }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.dashboard_log_heading), style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.log.asReversed()) { entry -> LogRow(entry) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenForTheDay, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dashboard_open_for_the_day))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmployeeRow(employee: Employee, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "${employee.name} (${employee.role})", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.dashboard_employee_morale, employee.morale),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LogRow(entry: SimulationLogEntry, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.dashboard_log_entry, entry.day, entry.message),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun GameOverContent(state: GameState, onBackToStart: () -> Unit, modifier: Modifier = Modifier) {
    val cause = when (state.restaurant.status) {
        RestaurantStatus.BANKRUPT -> stringResource(R.string.game_over_cause_bankrupt)
        RestaurantStatus.CONDEMNED -> stringResource(R.string.game_over_cause_condemned)
        else -> stringResource(R.string.game_over_cause_unknown)
    }
    val bestSellingDish = state.dishSalesTotals.maxByOrNull { it.value }

    Column(modifier = modifier.fillMaxSize()) {
        Text(text = stringResource(R.string.game_over_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = cause, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        StatRow(label = stringResource(R.string.game_over_days_survived), value = state.day.toString())
        StatRow(label = stringResource(R.string.game_over_total_revenue), value = state.ledger.totalRevenue.toString())
        StatRow(label = stringResource(R.string.game_over_total_profit_or_loss), value = state.ledger.totalProfitOrLoss.toString())
        StatRow(label = stringResource(R.string.game_over_final_reputation), value = state.restaurant.reputation.toString())
        if (bestSellingDish != null) {
            StatRow(
                label = stringResource(R.string.game_over_best_seller),
                value = "${bestSellingDish.key} (${bestSellingDish.value})",
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBackToStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.game_back_to_start))
        }
    }
}

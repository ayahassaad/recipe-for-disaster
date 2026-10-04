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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.ChalkDim
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.theme.WoodBrown
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.Advice
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.FiredEvent
import com.recipefordisaster.domain.simulation.MorningAdvisor
import com.recipefordisaster.domain.simulation.OutlookCalculator

private const val MAX_JOBS = 3

/** One line on the chalkboard: what's wrong, and the one button that deals with it. */
private data class Job(val text: String, val action: String, val enabled: Boolean, val onClick: () -> Unit)

/**
 * The main screen: the shop front, today's jobs on the chalkboard (at most
 * three, most urgent first, each with one button), a one-line forecast,
 * and the big "Open the doors" sign. Everything else is one tap away in
 * Staff, Food or Kitchen.
 */
@Composable
internal fun MorningScreen(
    uiState: GameUiState.Playing,
    actions: GameActions,
    onGoTo: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val morning = uiState.morning
    val advice = remember(morning) { MorningAdvisor.adviceFor(morning) }
    val forecast = remember(morning) { MorningAdvisor.forecast(morning) }
    val outlook = remember(morning) { OutlookCalculator.forTonight(morning) }
    val costs = remember(morning) { DailyCosts.of(morning) }
    val jobs = jobsFrom(advice, uiState.cashNow, actions, onGoTo)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ShopFront(day = uiState.state.day, cash = uiState.cashNow, dailyCosts = costs.total) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SignButton(text = stringResource(R.string.open_doors), onClick = actions.onStartService)
                    Spacer(modifier = Modifier.height(10.dp))
                    PlaceButtons(onGoTo)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRating(morning.restaurant.reputation)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        when {
                            uiState.state.recentSatisfaction >= 66 -> R.string.guests_happy
                            uiState.state.recentSatisfaction >= 40 -> R.string.guests_ok
                            else -> R.string.guests_unhappy
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            uiState.lastEvent?.let { OvernightNote(it) }

            Chalkboard {
                Text(text = stringResource(R.string.jobs_title), style = MaterialTheme.typography.headlineSmall, color = ChalkWhite)
                Spacer(modifier = Modifier.height(10.dp))
                if (jobs.isEmpty()) {
                    Text(text = stringResource(R.string.jobs_done), style = MaterialTheme.typography.bodyLarge, color = ChalkWhite)
                }
                jobs.take(MAX_JOBS).forEachIndexed { index, job ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = job.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChalkWhite,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        ChalkButton(
                            text = if (job.enabled) job.action else stringResource(R.string.cant_afford),
                            onClick = job.onClick,
                            enabled = job.enabled,
                        )
                    }
                }
                if (jobs.size > MAX_JOBS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.jobs_more), style = MaterialTheme.typography.bodyMedium, color = ChalkDim)
                }
            }

            Text(
                text = stringResource(R.string.tonight, outlook.expectedCustomers, signedCoins(forecast.expectedProfit)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun jobsFrom(advice: List<Advice>, cash: Long, actions: GameActions, onGoTo: (Place) -> Unit): List<Job> {
    val jobs = mutableListOf<Job>()
    // All the "running low" items become one job with one button.
    val restocks = advice.filterIsInstance<Advice.Restock>()
    if (restocks.isNotEmpty()) {
        val cost = restocks.sumOf { it.cost }
        jobs += Job(
            text = stringResource(R.string.job_restock, restocks.take(2).joinToString { it.ingredient.name.lowercase() } + if (restocks.size > 2) "…" else ""),
            action = stringResource(R.string.job_restock_action, coins(cost)),
            enabled = cash >= cost,
            onClick = actions.onRestockAll,
        )
    }
    for (item in advice) {
        jobs += when (item) {
            is Advice.Restock, is Advice.DishUnmakeable -> continue
            is Advice.KitchenTooSmall -> Job(stringResource(R.string.job_cooks), stringResource(R.string.job_cooks_action), true) { onGoTo(Place.STAFF) }
            is Advice.LosingMoney -> Job(stringResource(R.string.job_losing), stringResource(R.string.job_losing_action), true) { onGoTo(Place.STAFF) }
            is Advice.EquipmentBroken -> Job(
                stringResource(R.string.job_broken, item.equipment.name.lowercase()),
                stringResource(R.string.job_fix_action, coins(item.repairCost)),
                cash >= item.repairCost,
            ) { actions.onToggleRepair(item.equipment.id) }
            is Advice.EquipmentWorn -> Job(
                stringResource(R.string.job_worn, item.equipment.name.lowercase()),
                stringResource(R.string.job_fix_action, coins(item.repairCost)),
                cash >= item.repairCost,
            ) { actions.onToggleRepair(item.equipment.id) }
            is Advice.Dirty -> Job(
                stringResource(R.string.job_dirty),
                stringResource(R.string.job_clean_action, coins(DecisionApplier.DEEP_CLEAN_COST)),
                cash >= DecisionApplier.DEEP_CLEAN_COST,
                actions.onToggleDeepClean,
            )
            is Advice.StaffExhausted -> Job(
                stringResource(R.string.job_tired, item.employee.name),
                stringResource(R.string.job_tired_action),
                true,
            ) { actions.onToggleRestDay(item.employee.id) }
        }
    }
    return jobs
}

/** The shop front: awning, the day, and the money in the till. */
@Composable
private fun ShopFront(day: Int, cash: Long, dailyCosts: Long) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Awning()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.day, day), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = coins(cash), style = MaterialTheme.typography.titleLarge)
                    Text(text = stringResource(R.string.daily_costs, coins(dailyCosts)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** What happened overnight, as a note pinned up by the door. */
@Composable
private fun OvernightNote(event: FiredEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, toneColor(event.tone)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = stringResource(R.string.overnight) + ": " + event.title, style = MaterialTheme.typography.titleMedium, color = toneColor(event.tone))
            Text(text = event.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Staff, Food and Kitchen — the three doors off the dining room. */
@Composable
private fun PlaceButtons(onGoTo: (Place) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Place.STAFF to R.string.nav_staff, Place.FOOD to R.string.nav_food, Place.KITCHEN to R.string.nav_kitchen).forEach { (place, label) ->
            OutlinedButton(
                onClick = { onGoTo(place) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(2.dp, WoodBrown),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

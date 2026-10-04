package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.simulation.LogTone

@Composable
internal fun KitchenTab(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    val state = uiState.state
    // Before the deep clean (if any), so the chip still makes sense once it's been tapped.
    val cleanliness = state.restaurant.cleanliness
    val cleanedTo = uiState.morning.restaurant.cleanliness

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GameCard {
                SectionHeader(stringResource(R.string.kitchen_cleanliness_heading))
                Meter(stringResource(R.string.dashboard_cleanliness), cleanedTo, statColorFor(cleanedTo))
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.kitchen_cleanliness_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChip(
                    selected = uiState.plan.deepClean,
                    onClick = actions.onToggleDeepClean,
                    enabled = uiState.plan.deepClean || (cleanliness < 100 && uiState.cashNow >= DecisionApplier.DEEP_CLEAN_COST),
                    label = {
                        Text(
                            if (uiState.plan.deepClean) {
                                stringResource(R.string.kitchen_cleaned)
                            } else {
                                stringResource(R.string.kitchen_deep_clean, coins(DecisionApplier.DEEP_CLEAN_COST))
                            },
                        )
                    },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.kitchen_equipment_heading)) }
        items(state.equipment, key = { it.id.value }) { equipment ->
            val repairing = equipment.id in uiState.plan.repairs
            EquipmentCard(
                equipment = equipment,
                shownCondition = if (repairing) 100 else equipment.condition,
                repairing = repairing,
                canAfford = uiState.cashNow >= EquipmentOperations.repairCost(equipment),
                onToggleRepair = { actions.onToggleRepair(equipment.id) },
            )
        }
    }
}

@Composable
private fun EquipmentCard(
    equipment: Equipment,
    shownCondition: Int,
    repairing: Boolean,
    canAfford: Boolean,
    onToggleRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cost = EquipmentOperations.repairCost(equipment)
    GameCard(modifier = modifier) {
        Text(text = equipment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Meter(stringResource(R.string.kitchen_condition), shownCondition, statColorFor(shownCondition))
        if (EquipmentOperations.isBroken(equipment) && !repairing) {
            Text(
                text = stringResource(R.string.kitchen_broken),
                style = MaterialTheme.typography.bodyMedium,
                color = toneColor(LogTone.BAD),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (cost > 0) {
            FilterChip(
                selected = repairing,
                onClick = onToggleRepair,
                enabled = repairing || canAfford,
                label = {
                    Text(if (repairing) stringResource(R.string.kitchen_repaired) else stringResource(R.string.kitchen_repair, coins(cost)))
                },
            )
        } else {
            Text(stringResource(R.string.kitchen_good_shape), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

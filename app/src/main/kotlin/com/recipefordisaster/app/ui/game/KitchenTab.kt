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
    val cleanliness = state.restaurant.cleanliness

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GameCard {
                SectionHeader(stringResource(R.string.kitchen_cleanliness_heading))
                Meter(stringResource(R.string.dashboard_cleanliness), cleanliness, statColorFor(cleanliness))
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.kitchen_cleanliness_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                FilterChip(
                    selected = uiState.plan.deepClean,
                    onClick = actions.onToggleDeepClean,
                    enabled = cleanliness < 100,
                    label = { Text("🧼 " + stringResource(R.string.kitchen_deep_clean, coins(DecisionApplier.DEEP_CLEAN_COST))) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.kitchen_equipment_heading)) }
        items(state.equipment, key = { it.id.value }) { equipment ->
            EquipmentCard(
                equipment = equipment,
                repairing = equipment.id in uiState.plan.repairs,
                onToggleRepair = { actions.onToggleRepair(equipment.id) },
            )
        }
    }
}

@Composable
private fun EquipmentCard(equipment: Equipment, repairing: Boolean, onToggleRepair: () -> Unit, modifier: Modifier = Modifier) {
    val cost = EquipmentOperations.repairCost(equipment)
    GameCard(modifier = modifier) {
        Text(text = equipment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Meter(stringResource(R.string.kitchen_condition), equipment.condition, statColorFor(equipment.condition))
        if (EquipmentOperations.isBroken(equipment)) {
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
                label = { Text("🔧 " + stringResource(R.string.kitchen_repair, coins(cost))) },
            )
        } else {
            Text(stringResource(R.string.kitchen_good_shape), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

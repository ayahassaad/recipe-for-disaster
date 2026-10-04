package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.OutlookCalculator
import java.util.Locale

private const val BUY_STEP = 5.0

@Composable
internal fun PantryTab(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    val state = uiState.state
    val planned = uiState.preview.state
    val outlook = remember(planned) { OutlookCalculator.forTonight(planned) }
    val lowStockIds = outlook.lowStock.map { it.id }.toSet()
    val usedIds = remember(planned.menu) {
        planned.menu.filter { it.available }.flatMap { it.recipe.ingredientRequirements.keys }.toSet()
    }
    // What's on tonight's menu first, then the rest alphabetically.
    val ingredients = state.inventory.ingredients.values.sortedWith(compareBy({ it.id !in usedIds }, { it.name }))
    val storageUsed = InventoryOperations.totalStorageUsed(planned.inventory).toInt()
    val storageCapacity = state.inventory.storageCapacity.toInt()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GameCard {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.pantry_heading), Modifier.weight(1f))
                    if (uiState.plan.purchases.isNotEmpty()) {
                        TextButton(onClick = actions.onClearPurchases) { Text(stringResource(R.string.pantry_clear)) }
                    }
                }
                val percentFull = if (storageCapacity > 0) storageUsed * 100 / storageCapacity else 0
                Meter(
                    label = stringResource(R.string.pantry_storage, storageUsed, storageCapacity),
                    value = percentFull,
                    color = inverseStatColorFor(percentFull),
                    showValue = false,
                )
                Text(stringResource(R.string.pantry_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(ingredients, key = { it.id.value }) { ingredient ->
            IngredientCard(
                ingredient = ingredient,
                buying = uiState.plan.purchases[ingredient.id] ?: 0.0,
                low = ingredient.id in lowStockIds,
                unused = ingredient.id !in usedIds,
                onLess = { actions.onAdjustPurchase(ingredient.id, -BUY_STEP) },
                onMore = { actions.onAdjustPurchase(ingredient.id, BUY_STEP) },
            )
        }
    }
}

@Composable
private fun IngredientCard(
    ingredient: Ingredient,
    buying: Double,
    low: Boolean,
    unused: Boolean,
    onLess: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (if (low) "⚠️ " else "") + ingredient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (low) toneColor(LogTone.BAD) else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.pantry_on_hand,
                        formatQuantity(ingredient.quantityOnHand),
                        ingredient.unit,
                        coins(ingredient.purchasePricePerUnit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (unused) Text(stringResource(R.string.pantry_unused), style = MaterialTheme.typography.bodySmall)
                if (buying > 0.0) {
                    Text(
                        text = stringResource(R.string.pantry_buying, formatQuantity(buying), ingredient.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = toneColor(LogTone.GOOD),
                    )
                }
            }
            val lessLabel = stringResource(R.string.pantry_buy_less, ingredient.name)
            val moreLabel = stringResource(R.string.pantry_buy_more, ingredient.name)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onLess, enabled = buying > 0.0, modifier = Modifier.semantics { contentDescription = lessLabel }) {
                    Text("−5")
                }
                FilledTonalButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = moreLabel }) {
                    Text("+5")
                }
            }
        }
    }
}

private fun formatQuantity(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else String.format(Locale.getDefault(), "%.1f", quantity)

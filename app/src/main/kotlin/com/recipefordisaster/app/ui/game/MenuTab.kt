package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.OutlookCalculator
import kotlin.math.roundToLong

@Composable
internal fun MenuTab(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    // Show the menu as the plan would leave it: new prices, availability, and any dishes being added.
    val planned = uiState.preview.state
    val outlook = remember(planned) { OutlookCalculator.forTonight(planned) }
    val existingIds = remember(uiState.state.menu) { uiState.state.menu.map { it.id }.toSet() }
    val currentMenu = planned.menu.filter { it.id in existingIds }
    val book = RecipeBook.dishes.filter { it.id !in existingIds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.menu_heading)) }
        items(currentMenu, key = { it.id.value }) { dish ->
            DishCard(
                dish = dish,
                state = planned,
                sold = uiState.state.dishSalesTotals[dish.id.value] ?: 0,
                cantMake = dish.id in outlook.unmakeableDishes,
                onPriceDown = { actions.onAdjustPrice(dish.id, -1) },
                onPriceUp = { actions.onAdjustPrice(dish.id, 1) },
                onAvailableChange = { actions.onSetDishAvailable(dish.id, it) },
            )
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.recipe_book_heading))
                Text(stringResource(R.string.recipe_book_hint, coins(RecipeBook.ADD_DISH_COST)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (book.isEmpty()) {
            item { Text(stringResource(R.string.recipe_book_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(book, key = { "book-" + it.id.value }) { dish ->
            RecipeCard(
                dish = dish,
                state = uiState.state,
                adding = dish.id in uiState.plan.dishesToAdd,
                onToggle = { actions.onToggleAddDish(dish.id) },
            )
        }
    }
}

@Composable
private fun DishCard(
    dish: Dish,
    state: GameState,
    sold: Int,
    cantMake: Boolean,
    onPriceDown: () -> Unit,
    onPriceUp: () -> Unit,
    onAvailableChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plateCost = OutlookCalculator.plateCost(dish, state.inventory).roundToLong()
    GameCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = stringResource(R.string.menu_available), style = MaterialTheme.typography.labelMedium)
            Switch(checked = dish.available, onCheckedChange = onAvailableChange, modifier = Modifier.semantics { contentDescription = dish.name })
        }
        Text(
            text = stringResource(R.string.menu_fair_price, coins(dish.referencePrice), coins(plateCost)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.menu_quality_popularity, dish.quality, dish.popularity, sold),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val priceDownLabel = stringResource(R.string.menu_price_down, dish.name)
        val priceUpLabel = stringResource(R.string.menu_price_up, dish.name)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPriceDown, modifier = Modifier.semantics { contentDescription = priceDownLabel }) { Text("−") }
            Text(
                text = coins(dish.sellingPrice),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = priceColor(dish),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            FilledTonalButton(onClick = onPriceUp, modifier = Modifier.semantics { contentDescription = priceUpLabel }) { Text("+") }
        }
        if (cantMake && dish.available) {
            Text(text = stringResource(R.string.menu_out_of_stock), style = MaterialTheme.typography.bodyMedium, color = toneColor(LogTone.BAD))
        }
    }
}

/** Priced above fair reads as a warning, below as a bargain — the number itself is always shown too. */
@Composable
private fun priceColor(dish: Dish) = when {
    dish.sellingPrice > dish.referencePrice -> toneColor(LogTone.BAD)
    dish.sellingPrice < dish.referencePrice -> toneColor(LogTone.GOOD)
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun RecipeCard(dish: Dish, state: GameState, adding: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val needs = dish.recipe.ingredientRequirements.keys.joinToString { id -> state.inventory.ingredients[id]?.name ?: id.value }
    GameCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = coins(dish.sellingPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(text = stringResource(R.string.recipe_quality_popularity, dish.quality, dish.popularity), style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(R.string.recipe_needs, needs), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        FilterChip(selected = adding, onClick = onToggle, label = { Text("📖 " + stringResource(R.string.recipe_add)) })
    }
}

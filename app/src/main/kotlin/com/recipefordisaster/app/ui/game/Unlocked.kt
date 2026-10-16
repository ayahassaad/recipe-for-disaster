package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.progression.Progression
import com.recipefordisaster.domain.simulation.GameState

/** Everything the restaurant has met so far, explained again, and what's still to come. */
@Composable
internal fun UnlockedDialog(state: GameState, onClose: () -> Unit) {
    val met = Feature.entries.filter { Progression.has(state, it) }.sortedBy { it.day }
    val nextDay = Feature.entries.filter { !Progression.has(state, it) }.minOfOrNull { it.day }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.unlocked_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (met.isEmpty()) Text(stringResource(R.string.unlocked_nothing), style = MaterialTheme.typography.bodyLarge)
                met.forEach { feature ->
                    val text = introductionText(feature)
                    Text(stringResource(text.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(text.body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                nextDay?.let {
                    Text(stringResource(R.string.unlocked_next, it), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(stringResource(R.string.unlocked_dishes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                RecipeBook.dishes.sortedBy { RecipeBook.unlockDay(it) }.forEach { dish ->
                    Text(
                        if (RecipeBook.isUnlocked(state, dish)) dish.name else stringResource(R.string.unlocked_dish_locked, dish.name, RecipeBook.unlockDay(dish)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.unlocked_close)) } },
    )
}

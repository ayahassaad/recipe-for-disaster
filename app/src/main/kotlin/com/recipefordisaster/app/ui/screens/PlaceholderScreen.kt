package com.recipefordisaster.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme

/**
 * Stands in for the real management dashboard, which is Phase 5 (Core UI)
 * work. Exists in Phase 2 only to prove the module wiring (:app depending
 * on :domain and :data, Compose rendering, resource strings) end to end.
 *
 * Text comes from string resources rather than being hardcoded (section 15:
 * internationalization) even though there's only one string here — the
 * habit matters more once there are hundreds.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.placeholder_screen_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.placeholder_screen_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = 0x20) // Configuration.UI_MODE_NIGHT_YES
@Composable
private fun PlaceholderScreenPreview() {
    RecipeForDisasterTheme {
        PlaceholderScreen()
    }
}

package com.recipefordisaster.app.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme

/**
 * The very first screen: New Game, or Continue if a save exists. Continue
 * is only enabled once we actually know a save is there (section 13: never
 * imply a save is available, or safe to load, when it isn't).
 */
@Composable
fun StartScreen(
    uiState: StartUiState,
    onNewGame: () -> Unit,
    onContinueGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = stringResource(R.string.start_logo),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.start_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text(stringResource(R.string.start_new_game), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onContinueGame,
                    enabled = uiState.hasExistingSave,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Text(stringResource(R.string.start_continue_game), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    RecipeForDisasterTheme {
        StartScreen(
            uiState = StartUiState(isLoading = false, hasExistingSave = true),
            onNewGame = {},
            onContinueGame = {},
        )
    }
}

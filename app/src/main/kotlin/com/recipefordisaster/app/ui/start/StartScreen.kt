package com.recipefordisaster.app.ui.start

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.game.Awning
import com.recipefordisaster.app.ui.game.Chalkboard
import com.recipefordisaster.app.ui.game.SignButton
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme
import com.recipefordisaster.app.ui.theme.WoodBrown

/**
 * The very first screen: the shop front, with New game, or Continue if a
 * save exists. Continue is only enabled once we actually know a save is
 * there (section 13: never imply a save is available, or safe to load,
 * when it isn't).
 */
@Composable
fun StartScreen(
    uiState: StartUiState,
    onNewGame: () -> Unit,
    onContinueGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Awning(height = 56.dp, stripes = 8)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The logo: the chef's head, the same picture as the app icon.
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(240.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Chalkboard {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = ChalkWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.start_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ChalkWhite.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(40.dp))

            // Starting over throws the current restaurant away, so ask first if there is one.
            var confirmNewGame by remember { mutableStateOf(false) }
            if (confirmNewGame) {
                AlertDialog(
                    onDismissRequest = { confirmNewGame = false },
                    title = { Text(stringResource(R.string.start_over_title)) },
                    text = { Text(stringResource(R.string.start_over_body)) },
                    confirmButton = {
                        TextButton(onClick = { confirmNewGame = false; onNewGame() }) { Text(stringResource(R.string.start_over_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmNewGame = false }) { Text(stringResource(R.string.start_over_cancel)) }
                    },
                )
            }
            // The record to beat.
            uiState.best?.let { best ->
                Text(
                    stringResource(R.string.best_run, best.days, best.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = WoodBrown,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                SignButton(text = stringResource(R.string.start_new_game), onClick = {
                    // Only ask when there's a restaurant still going to lose; one that's already closed down is just replaced.
                    if (uiState.hasExistingSave && !uiState.savedGameIsOver) confirmNewGame = true else onNewGame()
                })
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onContinueGame,
                    enabled = uiState.hasExistingSave,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, if (uiState.hasExistingSave) WoodBrown else WoodBrown.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.start_continue_game), style = MaterialTheme.typography.titleLarge)
                        // Which restaurant you'd be going back to.
                        if (uiState.hasExistingSave && uiState.savedName != null && !uiState.savedGameIsOver) {
                            Text(uiState.savedName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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

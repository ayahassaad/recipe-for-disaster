package com.recipefordisaster.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recipefordisaster.app.ui.screens.PlaceholderScreen
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Compose UI test infrastructure is wired up end to end. Real
 * critical-flow tests (new game, start day, resolve event, save/reload,
 * game-over — per section 18) get built alongside the corresponding screens
 * in Phase 5 onward.
 */
@RunWith(AndroidJUnit4::class)
class PlaceholderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun placeholderScreenShowsTitle() {
        composeTestRule.setContent {
            RecipeForDisasterTheme {
                PlaceholderScreen()
            }
        }

        composeTestRule.onNodeWithText("Recipe for Disaster").assertExists()
    }
}

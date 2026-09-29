package com.recipefordisaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.recipefordisaster.app.ui.screens.PlaceholderScreen
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecipeForDisasterTheme {
                PlaceholderScreen()
            }
        }
    }
}

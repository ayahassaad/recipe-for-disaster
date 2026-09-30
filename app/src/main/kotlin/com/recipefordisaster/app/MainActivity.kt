package com.recipefordisaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.recipefordisaster.app.navigation.RecipeForDisasterNavHost
import com.recipefordisaster.app.ui.theme.RecipeForDisasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RecipeForDisasterApplication).container
        setContent {
            RecipeForDisasterTheme {
                RecipeForDisasterNavHost(appContainer = container)
            }
        }
    }
}

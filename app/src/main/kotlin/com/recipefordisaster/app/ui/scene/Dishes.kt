package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.graphics.Color
import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.domain.menu.DishId

/**
 * The food on a plate, drawn as the dish it is, so you can tell plates apart on the counter and on
 * the tables. [left] is how much is still there, 1 to start with: it goes down bite by bite.
 */
internal fun Pen.drawDish(dish: DishId?, at: Point, left: Float = 1f) {
    val s = (0.3f + 0.7f * left).coerceIn(0.3f, 1f)
    when (dish?.value) {
        else -> drawSomeFood(at, left, s)
    }
}

/** A helping of something golden with a bit of salad: for any dish without a look of its own. */
private fun Pen.drawSomeFood(at: Point, left: Float, s: Float) {
    oval(at.x - 0.3f, at.y, 1.3f * s, 0.9f * s, Color(0xFFC8762F))
    oval(at.x - 0.3f, at.y - 0.3f * s, 1.1f * s, 0.4f * s, Color(0xFFE09A4F))
    if (left > 0.4f) dot(at.x + 0.9f, at.y + 0.5f, 0.5f, Color(0xFF6DA34D))
    if (left > 0.7f) dot(at.x + 1.2f, at.y - 0.3f, 0.35f, Color(0xFFD94B3B))
}

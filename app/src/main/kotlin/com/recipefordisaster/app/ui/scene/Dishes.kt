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
        "the_regular_burger" -> drawBurger(at, s)
        "mystery_soup" -> drawSoup(at, s)
        "suspicious_salad" -> drawSalad(at, s)
        "fries_of_destiny" -> drawFries(at, s)
        else -> drawSomeFood(at, left, s)
    }
}

/** A burger: sesame bun, a slice of cheese, lettuce peeking out and the patty. */
private fun Pen.drawBurger(at: Point, s: Float) {
    oval(at.x, at.y + 0.5f * s, 1.4f * s, 0.55f * s, Color(0xFFD9A35A)) // bottom bun
    oval(at.x, at.y + 0.15f * s, 1.5f * s, 0.4f * s, Color(0xFF6DA34D)) // lettuce
    oval(at.x, at.y - 0.05f * s, 1.35f * s, 0.4f * s, Color(0xFF6B3A22)) // patty
    oval(at.x + 0.3f * s, at.y - 0.2f * s, 1.1f * s, 0.25f * s, Color(0xFFF2C230)) // cheese
    oval(at.x, at.y - 0.55f * s, 1.4f * s, 0.75f * s, Color(0xFFE0A860)) // top bun
    for (k in -1..1) dot(at.x + k * 0.55f * s, at.y - 0.8f * s + kotlin.math.abs(k) * 0.15f * s, 0.12f * s, Color(0xFFFFF4DC))
}

/** A bowl of soup, murky as its name, with a spoon in it. */
private fun Pen.drawSoup(at: Point, s: Float) {
    dot(at, 1.6f, Color(0xFFF7F1E3)) // the bowl
    ring(at.x, at.y, 1.6f, Color(0x33000000), 0.15f)
    dot(at, 1.25f * s, Color(0xFF9A6B3F)) // the soup, going down as it's eaten
    dot(at.x - 0.4f * s, at.y - 0.3f * s, 0.3f * s, Color(0xFFB98C5A))
    dot(at.x + 0.5f * s, at.y + 0.2f * s, 0.2f * s, Color(0xFF6DA34D))
    line(at.x + 0.6f, at.y - 0.4f, at.x + 1.9f, at.y - 1.3f, Palette.steelDark, 0.3f) // spoon handle
}

/** A heap of leaves with a tomato and crumbs of cheese. */
private fun Pen.drawSalad(at: Point, s: Float) {
    val leaf = Color(0xFF6DA34D)
    val pale = Color(0xFF9CCB6B)
    for ((dx, dy) in listOf(-0.7f to 0.2f, 0.6f to 0.3f, 0f to -0.4f, -0.2f to 0.5f, 0.5f to -0.3f)) oval(at.x + dx * s, at.y + dy * s, 0.7f * s, 0.5f * s, leaf)
    oval(at.x - 0.3f * s, at.y - 0.1f * s, 0.5f * s, 0.35f * s, pale)
    dot(at.x + 0.6f * s, at.y - 0.1f * s, 0.38f * s, Color(0xFFD94B3B)) // tomato
    for ((dx, dy) in listOf(-0.6f to -0.3f, 0.1f to 0.4f)) dot(at.x + dx * s, at.y + dy * s, 0.17f * s, Color(0xFFFFF6D8)) // cheese
}

/** Fries standing up in a little red carton. */
private fun Pen.drawFries(at: Point, s: Float) {
    val gold = Color(0xFFF2C230)
    for (k in 0 until 5) {
        val x = at.x - 0.8f * s + k * 0.4f * s
        val top = at.y - (0.9f + (k % 2) * 0.3f) * s
        line(x, at.y, x + (k - 2) * 0.08f, top, gold, 0.28f * s)
    }
    shape(Color(0xFFD94B3B)) {
        moveTo(at.x - 1f * s, at.y - 0.2f * s); lineTo(at.x + 1f * s, at.y - 0.2f * s)
        lineTo(at.x + 0.75f * s, at.y + 0.9f * s); lineTo(at.x - 0.75f * s, at.y + 0.9f * s); close()
    }
    dot(at.x, at.y + 0.35f * s, 0.22f * s, Color(0xFFFFF4DC)) // the logo
}

/** A helping of something golden with a bit of salad: for any dish without a look of its own. */
private fun Pen.drawSomeFood(at: Point, left: Float, s: Float) {
    oval(at.x - 0.3f, at.y, 1.3f * s, 0.9f * s, Color(0xFFC8762F))
    oval(at.x - 0.3f, at.y - 0.3f * s, 1.1f * s, 0.4f * s, Color(0xFFE09A4F))
    if (left > 0.4f) dot(at.x + 0.9f, at.y + 0.5f, 0.5f, Color(0xFF6DA34D))
    if (left > 0.7f) dot(at.x + 1.2f, at.y - 0.3f, 0.35f, Color(0xFFD94B3B))
}

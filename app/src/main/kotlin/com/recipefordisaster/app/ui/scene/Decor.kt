package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.graphics.Color
import com.recipefordisaster.app.ui.scene.SceneLayout.Rect
import com.recipefordisaster.domain.restaurant.Decoration

/** Where each decoration goes: the paintings either side of the hiring sign on the front wall. */
private val PAINTINGS = listOf(Rect(61.5f, 141.6f, 66.5f, 147f), Rect(91f, 141.2f, 97.5f, 147.4f))

/** The decorations the player has bought, in their places around the dining room. */
internal fun Pen.drawDecorations(decorations: Set<Decoration>, time: Float) {
    if (Decoration.PAINTINGS in decorations) drawPaintings()
}

/** Two little framed pictures: a sunset over hills, and a bowl of fruit. */
private fun Pen.drawPaintings() {
    PAINTINGS.forEachIndexed { k, r ->
        box(r, Palette.gold, radius = 0.3f)
        val inside = Rect(r.left + 0.6f, r.top + 0.6f, r.right - 0.6f, r.bottom - 0.6f)
        if (k == 0) {
            box(inside, Color(0xFFF2A65A), radius = 0.1f)
            dot(inside.center.x + 0.6f, inside.top + 1.6f, 0.9f, Color(0xFFFFE08A))
            oval(inside.center.x - 0.8f, inside.bottom - 0.3f, 2.6f, 1.3f, Color(0xFF6DA34D))
            oval(inside.center.x + 1.4f, inside.bottom - 0.2f, 2f, 0.9f, Color(0xFF4E8A3A))
        } else {
            box(inside, Color(0xFF2E3A5C), radius = 0.1f)
            oval(inside.center.x, inside.bottom - 1f, 2.2f, 0.8f, Color(0xFFB98C5A))
            dot(inside.center.x - 0.9f, inside.bottom - 1.8f, 0.8f, Color(0xFFD94B3B))
            dot(inside.center.x + 0.6f, inside.bottom - 1.9f, 0.75f, Color(0xFFF2C230))
            dot(inside.center.x, inside.bottom - 2.6f, 0.7f, Color(0xFF6DA34D))
        }
    }
}

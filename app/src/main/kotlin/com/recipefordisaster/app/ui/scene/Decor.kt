package com.recipefordisaster.app.ui.scene

import androidx.compose.ui.graphics.Color
import com.recipefordisaster.app.ui.scene.SceneLayout.Rect
import com.recipefordisaster.domain.restaurant.Decoration

/** Where each decoration goes: the paintings either side of the hiring sign on the front wall. */
private val PAINTINGS = listOf(Rect(61.5f, 141.6f, 66.5f, 147f), Rect(91f, 141.2f, 97.5f, 147.4f))

/** The decorations the player has bought, in their places around the dining room. */
internal fun Pen.drawDecorations(decorations: Set<Decoration>, time: Float) {
    if (Decoration.PAINTINGS in decorations) drawPaintings()
    if (Decoration.FAIRY_LIGHTS in decorations) drawFairyLights(time)
    if (Decoration.PIANO in decorations) drawPiano(time)
}

/** Where the piano stands: in the corner by the kitchen, where a plant used to be. */
internal val PIANO = Rect(1f, 45f, 12.5f, 53.5f)

/** An upright piano seen from above, lid and keys, with a note or two floating up from it. */
private fun Pen.drawPiano(time: Float) {
    val p = PIANO
    oval(p.center.x, p.bottom + 0.4f, p.width / 2, 0.8f, Palette.shadow)
    box(p, Color(0xFF2B1D16), radius = 0.8f)
    box(p.left + 0.6f, p.top + 0.6f, p.width - 1.2f, p.height * 0.55f, Color(0xFF3E2A1F), radius = 0.5f) // the lid
    // The keys, along the side facing the room.
    val keysTop = p.bottom - 2.4f
    box(p.left + 0.5f, keysTop, p.width - 1f, 1.9f, Color(0xFFF7F1E3), radius = 0.2f)
    var x = p.left + 1.2f
    var k = 0
    while (x < p.right - 1f) {
        if (k % 7 != 2 && k % 7 != 6) box(x - 0.2f, keysTop, 0.45f, 1.1f, Palette.ink, radius = 0.05f)
        x += 0.85f
        k++
    }
    dot(p.left + 2f, p.top + 1.6f, 0.5f, Palette.gold) // a candlestick on top
    // A note drifting up now and then.
    val t = (time * 0.4f) % 1f
    if (t < 0.7f) {
        val nx = p.right - 2f + t * 2.5f
        val ny = p.top + 1f - t * 5f
        val ink = Palette.ink.copy(alpha = 1f - t / 0.7f)
        dot(nx, ny, 0.45f, ink)
        line(nx + 0.4f, ny, nx + 0.4f, ny - 1.6f, ink, 0.18f)
    }
}

/** The bulbs' colours, in turn along the string. */
private val BULBS = listOf(Color(0xFFFFE27A), Color(0xFFFF9A8B), Color(0xFF9AD8FF), Color(0xFFB5F2A0))

/**
 * A string of little coloured bulbs along the top of the front wall, swagging between hooks and
 * twinkling. (Not across the sign over the door.)
 */
private fun Pen.drawFairyLights(time: Float) {
    val hooks = listOf(0f, 10f, 20f, 30f) to listOf(70f, 80f, 90f, 100f)
    var bulb = 0
    for (run in listOf(hooks.first, hooks.second)) {
        run.zipWithNext().forEach { (a, b) ->
            shape(Color(0xFF3A3A3A), stroke = 0.15f) { moveTo(a, 139.6f); quadTo((a + b) / 2, 141.8f, b, 139.6f) }
            for (k in 1..4) {
                val t = k / 5f
                val x = a + (b - a) * t
                val y = 139.6f + 4f * t * (1f - t) * 1.1f
                val twinkle = 0.6f + 0.4f * kotlin.math.sin(time * 3f + bulb * 1.7f)
                val colour = BULBS[bulb % BULBS.size]
                dot(x, y + 0.5f, 1.1f, colour.copy(alpha = 0.25f * twinkle))
                dot(x, y + 0.5f, 0.45f, colour.copy(alpha = 0.6f + 0.4f * twinkle))
                bulb++
            }
        }
    }
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

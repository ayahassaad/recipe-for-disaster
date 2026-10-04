package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.theme.ChalkboardGreen
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.KitchenCharcoal
import com.recipefordisaster.app.ui.theme.MustardAmber
import com.recipefordisaster.app.ui.theme.PlateCream
import com.recipefordisaster.app.ui.theme.ReceiptPaper
import com.recipefordisaster.app.ui.theme.SweatBlue
import com.recipefordisaster.app.ui.theme.WoodBrown
import com.recipefordisaster.app.ui.theme.WoodDark

/**
 * The drawn pieces that make the screens look like a restaurant rather than
 * a spreadsheet: a shop-front awning, a chalkboard, receipt paper, and staff
 * faces. Everything is drawn with Compose shapes — no image assets and no
 * emoji, which render differently (or not at all) across Android versions.
 */

/** Red-and-cream striped awning with a scalloped edge, like over a shop front. */
@Composable
fun Awning(modifier: Modifier = Modifier, height: Dp = 34.dp, stripes: Int = 12) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val stripeWidth = size.width / stripes
        val scallopRadius = stripeWidth / 2
        val bodyHeight = size.height - scallopRadius
        for (i in 0 until stripes) {
            // White, not cream: cream stripes vanish against the cream wall behind them.
            val color = if (i % 2 == 0) DisasterRed else Color.White
            drawRect(color, topLeft = Offset(i * stripeWidth, 0f), size = Size(stripeWidth + 1f, bodyHeight))
            drawCircle(color, radius = scallopRadius, center = Offset(i * stripeWidth + scallopRadius, bodyHeight))
        }
        // The rail the awning hangs from.
        drawRect(WoodDark, topLeft = Offset(0f, 0f), size = Size(size.width, 4.dp.toPx()))
    }
}

/** A chalkboard in a wooden frame — where the day's jobs are written up. */
@Composable
fun Chalkboard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WoodBrown, RoundedCornerShape(10.dp))
            .border(2.dp, WoodDark, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkboardGreen, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

/** Outlined chalk-coloured button that sits on the chalkboard. */
@Composable
fun ChalkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.5.dp, if (enabled) ChalkWhite else ChalkWhite.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChalkWhite, disabledContentColor = ChalkWhite.copy(alpha = 0.45f)),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/** A strip of till-receipt paper with a torn, zig-zag bottom edge. */
@Composable
fun Receipt(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().background(ReceiptPaper).padding(horizontal = 20.dp, vertical = 18.dp),
            content = content,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp).clipToBounds()) {
            val tooth = 12.dp.toPx()
            val path = Path().apply {
                moveTo(0f, 0f)
                var x = 0f
                while (x < size.width) {
                    lineTo(x + tooth / 2, size.height)
                    lineTo(x + tooth, 0f)
                    x += tooth
                }
                close()
            }
            drawPath(path, ReceiptPaper)
        }
    }
}

/**
 * A simple drawn face whose expression follows morale: a smile when happy,
 * a flat mouth when so-so, a frown when miserable — plus a bead of sweat
 * when stressed. The rim colour repeats the same reading for quick
 * scanning; [description] carries it for screen readers.
 */
@Composable
fun MoodFace(morale: Int, stress: Int, description: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val rim = statColorFor(morale)
    Canvas(modifier = modifier.size(size).semantics { contentDescription = description }) {
        val r = this.size.minDimension / 2
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(PlateCream, radius = r, center = c)
        drawCircle(rim, radius = r - 1.5.dp.toPx(), center = c, style = Stroke(width = 3.dp.toPx()))

        val eyeY = c.y - r * 0.2f
        drawCircle(KitchenCharcoal, radius = r * 0.09f, center = Offset(c.x - r * 0.32f, eyeY))
        drawCircle(KitchenCharcoal, radius = r * 0.09f, center = Offset(c.x + r * 0.32f, eyeY))

        val mouthY = c.y + r * 0.32f
        val curve = when {
            morale >= 66 -> r * 0.28f // smile
            morale >= 33 -> 0f // flat
            else -> -r * 0.24f // frown
        }
        val mouth = Path().apply {
            moveTo(c.x - r * 0.36f, mouthY)
            quadraticTo(c.x, mouthY + curve, c.x + r * 0.36f, mouthY)
        }
        drawPath(mouth, KitchenCharcoal, style = Stroke(width = 2.5.dp.toPx()))

        if (stress >= 70) {
            val dropTop = Offset(c.x + r * 0.62f, c.y - r * 0.55f)
            val drop = Path().apply {
                moveTo(dropTop.x, dropTop.y)
                quadraticTo(dropTop.x + r * 0.2f, dropTop.y + r * 0.3f, dropTop.x, dropTop.y + r * 0.36f)
                quadraticTo(dropTop.x - r * 0.2f, dropTop.y + r * 0.3f, dropTop.x, dropTop.y)
            }
            drawPath(drop, SweatBlue)
        }
    }
}

/** Five-star rating from a 0-100 score, drawn with the plain (non-emoji) star characters. */
@Composable
fun StarRating(score: Int, modifier: Modifier = Modifier) {
    val stars = ((score.coerceIn(0, 100) + 10) / 20).coerceIn(0, 5)
    Text(
        text = "★".repeat(stars) + "☆".repeat(5 - stars),
        color = MustardAmber,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

/** The big red "Open the doors" sign-style button. */
@Composable
fun SignButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DisasterRed, contentColor = PlateCream),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

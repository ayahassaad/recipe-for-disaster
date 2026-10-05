package com.recipefordisaster.app.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.app.ui.scene.SceneLayout.Rect
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.Role as StaffRole

/** Something in the restaurant the player can tap. */
sealed interface SceneTarget {
    data object Oven : SceneTarget
    data object Pantry : SceneTarget
    data object MenuBoard : SceneTarget
    data object Mop : SceneTarget
    data object HiringSign : SceneTarget
    data class Staff(val id: EmployeeId) : SceneTarget
}

/** One worker as the scene shows them. */
data class StaffFigure(
    val id: EmployeeId,
    val name: String,
    val role: StaffRole,
    val morale: Int,
    val stress: Int,
)

/**
 * What the scene needs to know about the restaurant — built from the
 * morning's state by the screen, so this file never touches game rules.
 */
data class SceneModel(
    val staff: List<StaffFigure>,
    val ovenCondition: Int,
    val pantryFullness: Float,
    val cleanliness: Int,
    val hiring: Boolean,
    /** Things that need the player — each gets a pulsing red "!". */
    val alerts: Set<SceneTarget>,
    val ovenOnFire: Boolean = false,
)

data class SceneLabels(
    val menu: String,
    val hiring: String,
    val oven: String,
    val pantry: String,
    val mop: String,
    val hiringSign: String,
    val staff: (StaffFigure) -> String,
)

/**
 * The restaurant, drawn from above and animated. In the morning it idles
 * (steam, smoke from a broken oven, staff breathing) and everything in
 * [SceneModel.alerts] pulses; tapping an object calls [onTap].
 */
@Composable
fun RestaurantScene(
    model: SceneModel,
    labels: SceneLabels,
    onTap: (SceneTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableFloatStateOf(0f) }

    // Idle animation only (steam, smoke, staff breathing); service itself is played in NightScene.
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
        }
    }

    val text = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val unit = minOf(widthPx / SceneLayout.WIDTH, heightPx / SceneLayout.HEIGHT)
        val origin = Offset((widthPx - unit * SceneLayout.WIDTH) / 2f, (heightPx - unit * SceneLayout.HEIGHT) / 2f)
        val staffSpots = staffPositions(model.staff)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Nobody gets drawn outside the room, even on their way out of the door.
            clipRect(origin.x, origin.y, origin.x + unit * SceneLayout.WIDTH, origin.y + unit * SceneLayout.HEIGHT) {
            val pen = Pen(this, unit, origin)
            val time = clock
            with(pen) {
                drawRoom(model.cleanliness, doorOpen = false, time = time)
                drawOven(model.ovenCondition, time, model.ovenOnFire)
                drawStove(false, time)
                drawSink()
                drawPantry(model.pantryFullness)
                drawTables()
                drawMenuBoard(text, labels.menu)
                drawMopBucket()
                if (model.hiring) drawHiringSign(text, labels.hiring)

                // Staff at their morning spots.
                staffSpots.forEach { (figure, spot) ->
                    drawPerson(
                        at = spot,
                        outfit = outfitFor(figure.role),
                        mood = figure.morale,
                        bob = sin(time * 1.6f + figure.id.hashCode()) * 0.25f,
                        sweat = figure.stress >= 70,
                        slumped = figure.stress >= 85,
                        variant = figure.name.hashCode().mod(5),
                    )
                }

                // "This needs you" markers.
                model.alerts.forEach { target ->
                    val rect = rectFor(target, staffSpots) ?: return@forEach
                    drawAlert(text, Point(rect.right - 1f, rect.top + 1f), time)
                }
            }
            }
        }

        // Invisible tap areas over each object; they also give screen readers something to announce.
        run {
            val targets = listOf(
                SceneTarget.Oven to labels.oven,
                SceneTarget.Pantry to labels.pantry,
                SceneTarget.MenuBoard to labels.menu,
                SceneTarget.Mop to labels.mop,
            ) + (if (model.hiring) listOf(SceneTarget.HiringSign to labels.hiringSign) else emptyList()) +
                staffSpots.map { (figure, _) -> SceneTarget.Staff(figure.id) to labels.staff(figure) }
            targets.forEach { (target, label) ->
                val rect = rectFor(target, staffSpots) ?: return@forEach
                val x = with(density) { (origin.x + rect.left * unit).toDp() }
                val y = with(density) { (origin.y + rect.top * unit).toDp() }
                val w = with(density) { (rect.width * unit).toDp() }
                val h = with(density) { (rect.height * unit).toDp() }
                Box(
                    modifier = Modifier
                        .offset(x, y)
                        .size(w.coerceAtLeast(44.dp), h.coerceAtLeast(44.dp))
                        .semantics { contentDescription = label }
                        .clickable(role = Role.Button) { onTap(target) },
                )
            }
        }
    }
}

/** Morning spots for everyone working today, grouped by job. */
internal fun staffPositions(staff: List<StaffFigure>): List<Pair<StaffFigure, Point>> {
    val cooks = staff.filter { it.role == StaffRole.COOK }
    val washers = staff.filter { it.role == StaffRole.DISHWASHER }
    val servers = staff.filter { it.role == StaffRole.SERVER || it.role == StaffRole.MANAGER }
    return cooks.mapIndexed { i, f -> f to SceneLayout.cookSpot(i) } +
        washers.mapIndexed { i, f -> f to SceneLayout.dishwasherSpot(i) } +
        servers.mapIndexed { i, f -> f to SceneLayout.serverSpot(i) }
}

private fun rectFor(target: SceneTarget, staffSpots: List<Pair<StaffFigure, Point>>): Rect? = when (target) {
    SceneTarget.Oven -> SceneLayout.oven
    SceneTarget.Pantry -> SceneLayout.pantry
    SceneTarget.MenuBoard -> SceneLayout.menuBoard
    SceneTarget.Mop -> Rect(SceneLayout.mopBucket.left, SceneLayout.mopBucket.top - 6f, SceneLayout.mopBucket.right + 4f, SceneLayout.mopBucket.bottom)
    SceneTarget.HiringSign -> SceneLayout.hiringSign
    is SceneTarget.Staff -> staffSpots.firstOrNull { it.first.id == target.id }?.second?.let { Rect(it.x - 5f, it.y - 9f, it.x + 5f, it.y + 4f) }
}

private fun sin(x: Float): Float = kotlin.math.sin(x)

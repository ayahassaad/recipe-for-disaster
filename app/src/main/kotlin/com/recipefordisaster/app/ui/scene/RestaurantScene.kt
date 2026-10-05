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
 * [SceneModel.alerts] pulses; tapping an object calls [onTap]. When
 * [service] is given, it plays that night: guests come in, get fed (or
 * don't), pay and leave, and [onServiceFinished] fires at the end.
 */
@Composable
fun RestaurantScene(
    model: SceneModel,
    labels: SceneLabels,
    onTap: (SceneTarget) -> Unit,
    modifier: Modifier = Modifier,
    service: ServiceChoreography? = null,
    speed: Float = 1f,
    onServiceFinished: () -> Unit = {},
    onCoinsSoFar: (Long) -> Unit = {},
) {
    var clock by remember { mutableFloatStateOf(0f) }
    var serviceTime by remember(service) { mutableFloatStateOf(-0.6f) }
    val currentSpeed by androidx.compose.runtime.rememberUpdatedState(speed)

    LaunchedEffect(service) {
        var last = withFrameNanos { it }
        var finished = false
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
            clock += dt
            if (service != null && !finished) {
                serviceTime += dt * currentSpeed
                onCoinsSoFar(service.coinsPaidBy(serviceTime))
                if (serviceTime >= service.duration) {
                    finished = true
                    onServiceFinished()
                }
            }
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
            val busy = service?.kitchenBusy(serviceTime) ?: false
            with(pen) {
                drawRoom(model.cleanliness, doorOpen = service != null, time = time)
                drawOven(model.ovenCondition, time, model.ovenOnFire)
                drawStove(busy, time)
                drawSink()
                drawPantry(model.pantryFullness)
                drawTables()
                drawMenuBoard(text, labels.menu)
                drawMopBucket()
                if (model.hiring && service == null) drawHiringSign(text, labels.hiring)

                // Staff.
                val servers = staffSpots.filter { it.first.role == StaffRole.SERVER || it.first.role == StaffRole.MANAGER }
                staffSpots.forEach { (figure, spot) ->
                    val serverIndex = servers.indexOfFirst { it.first.id == figure.id }
                    val position = if (service != null && serverIndex >= 0) {
                        service.serverPosition(serverIndex, serviceTime, spot)
                    } else {
                        spot
                    }
                    val working = service != null && (figure.role == StaffRole.COOK && busy)
                    val bob = if (working) (sin(time * 12f) * 0.6f) else (sin(time * 1.6f + figure.id.hashCode()) * 0.25f)
                    drawPerson(
                        at = position,
                        outfit = outfitFor(figure.role),
                        mood = figure.morale,
                        bob = bob,
                        sweat = figure.stress >= 70,
                        slumped = figure.stress >= 85,
                    )
                }

                // Guests.
                service?.tracks?.forEach { track ->
                    val frame = service.frame(track, serviceTime) ?: return@forEach
                    frame.plate?.let { drawPlate(it) }
                    val walking = frame.look == ServiceChoreography.Look.WALKING_IN ||
                        frame.look == ServiceChoreography.Look.HAPPY_LEAVING ||
                        frame.look == ServiceChoreography.Look.STORMING_OUT
                    drawPerson(
                        at = frame.position,
                        outfit = Outfit.GUEST,
                        mood = frame.mood,
                        bodyColor = Palette.guestColors[track.colorIndex % Palette.guestColors.size],
                        bob = when {
                            walking -> kotlin.math.abs(sin((serviceTime - track.start) * 14f)) * 0.8f
                            frame.look == ServiceChoreography.Look.EATING -> kotlin.math.abs(sin(serviceTime * 6f + track.colorIndex)) * 0.4f
                            else -> 0f
                        },
                        angry = frame.look == ServiceChoreography.Look.ANGRY || frame.look == ServiceChoreography.Look.STORMING_OUT,
                        backTurned = frame.look == ServiceChoreography.Look.TURNING_AWAY && serviceTime - track.start > 1.1f,
                    )
                    frame.coins?.let { (where, progress) -> if (progress < 1f) drawCoins(text, where, progress, track.visit.paid) }
                }

                // "This needs you" markers — only in the morning.
                if (service == null) {
                    model.alerts.forEach { target ->
                        val rect = rectFor(target, staffSpots) ?: return@forEach
                        drawAlert(text, Point(rect.right - 1f, rect.top + 1f), time)
                    }
                }
            }
            }
        }

        // Invisible tap areas over each object; they also give screen readers something to announce.
        if (service == null) {
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

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
import androidx.compose.ui.graphics.Color
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
import com.recipefordisaster.domain.service.ServiceFloor
import com.recipefordisaster.domain.employee.Role as StaffRole

/** Something in the restaurant the player can tap. */
sealed interface SceneTarget {
    data object Oven : SceneTarget
    data object Fridge : SceneTarget
    data object Pantry : SceneTarget
    data object MenuBoard : SceneTarget
    data object Mop : SceneTarget
    data object HiringSign : SceneTarget
    /** Any table: opens the "more tables" popup. */
    data object Tables : SceneTarget
    /** The jar of tips on the counter: spend it on treats for the team. */
    data object TipJar : SceneTarget
    /** The box of things guests left behind. */
    data object LostAndFound : SceneTarget
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
    /** How full each ingredient's jar is (0 = none left, 1 = plenty), in a fixed order. */
    val pantryJars: List<Float> = emptyList(),
    val cleanliness: Int,
    val hiring: Boolean,
    /** Things that need the player — each gets a pulsing red "!". */
    val alerts: Set<SceneTarget>,
    val ovenOnFire: Boolean = false,
    /** Which model of oven: 1 is the ancient one, higher is better (see EquipmentCatalog). */
    val ovenLevel: Int = 1,
    /** How many tables are in the dining room. */
    val tableCount: Int = 6,
    /** Coins in the tip jar. */
    val tipJar: Long = 0,
    /** How many things are in the lost and found (the box only shows when there are some). */
    val lostItems: Int = 0,
    val fridgeCondition: Int = 100,
    val fridgeLevel: Int = 1,
    /** The restaurant's name, for the sign over the door. */
    val name: String = "",
    /** What the player has bought to make the dining room nicer. */
    val decorations: Set<com.recipefordisaster.domain.restaurant.Decoration> = emptySet(),
    /** Whether the bar is open (drinks have arrived): before then it isn't there at all. */
    val barOpen: Boolean = true,
    /** Whether the fuse box is on the wall: only once the power can go out. */
    val fuseBox: Boolean = true,
    /** Whether the mop bucket is out: from when spills start (or earlier if the floor needs a clean). */
    val mopOut: Boolean = true,
    /** How the cooks are feeling: it shows over their heads, so you can see a grumpy night coming. */
    val chefMood: com.recipefordisaster.domain.service.ChefMood = com.recipefordisaster.domain.service.ChefMood.NORMAL,
)

data class SceneLabels(
    val menu: String,
    val hiring: String,
    val oven: String,
    val fridge: String = "",
    val pantry: String,
    val mop: String,
    val hiringSign: String,
    val dishSign: String,
    val tables: String = "",
    val tipJar: String = "",
    val lostAndFound: String = "",
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
    /** The player's look, to show them in the room too; null leaves them out. */
    playerLook: com.recipefordisaster.app.ui.player.PlayerLook? = null,
    /** What the player is doing while they stand about. */
    playerPose: PlayerPose = PlayerPose.WAVING,
    /** How dark it is: 0 in the morning, 1 once service is over and it's late evening. */
    evening: Float = 0f,
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
        val helperColor = remember(model.staff) { helperColors(model.staff) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSurround(origin, unit)
            // Nobody gets drawn outside the room, even on their way out of the door.
            clipRect(origin.x, origin.y, origin.x + unit * SceneLayout.WIDTH, origin.y + unit * SceneLayout.HEIGHT) {
            val pen = Pen(this, unit, origin)
            val time = clock
            with(pen) {
                drawRoom(model.cleanliness, doorOpen = 0f, time = time, name = model.name, text = text, aisles = ServiceFloor.layout(model.tableCount).aisles, bar = model.barOpen, decorations = model.decorations)
                drawOven(model.ovenCondition, time, model.ovenOnFire, model.ovenLevel)
                drawStove(false, time)
                drawFridge(model.fridgeCondition, broken = model.fridgeCondition <= 0, struggling = false, level = model.fridgeLevel, time = time)
                drawDishStation(text, labels.dishSign)
                drawPantry(model.pantryFullness, model.pantryJars)
                drawTables(ServiceFloor.layout(model.tableCount))
                // Room for one more: a faint outline where the next table will go.
                if (model.tableCount < ServiceFloor.TABLE_COUNT) drawTableGhost(ServiceFloor.tables[model.tableCount])
                drawIdleTipJar(text, model.tipJar)
                if (model.lostItems > 0) drawLostAndFound(text, model.lostItems)
                drawLampGlow(evening, ServiceFloor.layout(model.tableCount))
                // The candles stand on the tables, so people go in front of them.
                drawLamps(evening, ServiceFloor.layout(model.tableCount), time)
                drawMenuBoard(text, labels.menu)
                if (model.mopOut) drawMopBucket()
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
                        apron = helperColor[figure.id.value],
                    )
                    if (figure.role == StaffRole.COOK) drawChefMood(text, spot, model.chefMood, time + figure.id.hashCode() % 5)
                }

                // You: ready for the day, or after service, pleased or fed up with how it went.
                playerLook?.let { drawIdlePlayer(playerIdleSpot(ServiceFloor.layout(model.tableCount)), it, playerPose, time) }

                // In the morning the sun streams in; after closing it's late: the evening light, the candles still lit.
                if (evening <= 0f) drawMorningSun(time)
                if (evening > 0f) {
                    drawEveningLight(evening)
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
                SceneTarget.Fridge to labels.fridge,
                SceneTarget.Pantry to labels.pantry,
                SceneTarget.MenuBoard to labels.menu,
            ) +
                // One tap area per table, all opening the same popup.
                ServiceFloor.layout(model.tableCount).let { layout ->
                    layout.tables.map { SceneTarget.Tables to labels.tables }
                } + listOf(SceneTarget.TipJar to labels.tipJar) + (if (model.lostItems > 0) listOf(SceneTarget.LostAndFound to labels.lostAndFound) else emptyList()) + (if (model.hiring) listOf(SceneTarget.HiringSign to labels.hiringSign) else emptyList()) +
                staffSpots.map { (figure, _) -> SceneTarget.Staff(figure.id) to labels.staff(figure) } +
                // The mop bucket last, so its tap sits on top of the nearest table's.
                (if (model.mopOut) listOf(SceneTarget.Mop to labels.mop) else emptyList())
            val tableRects = ServiceFloor.layout(model.tableCount).let { layout ->
                layout.tables.map { Rect(it.x - 17f * layout.scale, it.y - 10f * layout.scale, it.x + 17f * layout.scale, it.y + 12f * layout.scale) }
            }.iterator()
            targets.forEach { (target, label) ->
                val rect = (if (target == SceneTarget.Tables) tableRects.next() else rectFor(target, staffSpots)) ?: return@forEach
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
    val bussers = staff.filter { it.role == StaffRole.BUSSER }
    val hosts = staff.filter { it.role == StaffRole.HOST }
    val bartenders = staff.filter { it.role == StaffRole.BARTENDER }
    return cooks.mapIndexed { i, f -> f to SceneLayout.cookSpot(i) } +
        washers.mapIndexed { i, f -> f to SceneLayout.dishwasherSpot(i) } +
        servers.mapIndexed { i, f -> f to SceneLayout.serverSpot(i) } +
        bussers.mapIndexed { i, f -> f to SceneLayout.busserSpot(i) } +
        hosts.mapIndexed { i, f -> f to SceneLayout.hostSpot(i) } +
        bartenders.mapIndexed { i, f -> f to SceneLayout.bartenderSpot(i) }
}

/** The mop bucket's tap area: the whole corner around it, mop handle and all, so it's easy to hit. */
internal val MOP_TAP = Rect(0f, SceneLayout.mopBucket.top - 9f, SceneLayout.mopBucket.right + 6f, SceneLayout.HEIGHT)

/** The tip jar's tap area: a bit bigger than the jar itself. */
private val TIP_JAR_TAP = Rect(SceneLayout.TIP_JAR.x - 5f, SceneLayout.TIP_JAR.y - 5f, SceneLayout.TIP_JAR.x + 5f, SceneLayout.TIP_JAR.y + 4f)

/** Where the lost and found box sits, on the floor by the front door. */
private val LOST_BOX = Point(68f, 144f)
private val LOST_BOX_TAP = Rect(LOST_BOX.x - 7f, LOST_BOX.y - 5f, LOST_BOX.x + 7f, LOST_BOX.y + 5f)

/** A cardboard box with a scarf and an umbrella sticking out, and the number of things in it on a label. */
private fun Pen.drawLostAndFound(text: androidx.compose.ui.text.TextMeasurer, items: Int) {
    val b = LOST_BOX
    oval(b.x, b.y + 4.4f, 6.6f, 1.2f, Color(0x33000000))
    // The umbrella's crook and the scarf poking out of the top.
    line(b.x - 1.5f, b.y - 1f, b.x - 2.5f, b.y - 5.5f, Color(0xFF3A4A6B), 0.7f)
    line(b.x - 2.5f, b.y - 5.5f, b.x - 4.2f, b.y - 5.2f, Color(0xFF3A4A6B), 0.7f)
    box(b.x + 1f, b.y - 4f, 2.2f, 4f, Color(0xFFC0392B), radius = 0.8f)
    box(b.x - 5.6f, b.y - 1.4f, 11.2f, 6.4f, Color(0xFFB8895A), radius = 0.5f)
    box(b.x - 5.6f, b.y - 1.4f, 11.2f, 1.1f, Color(0xFF9A7144), radius = 0.4f)
    box(b.x - 3.2f, b.y + 0.9f, 6.4f, 2.6f, Color(0xFFF4EBD6), radius = 0.3f)
    centeredText(text, "LOST & FOUND", Point(b.x, b.y + 2.2f), size = 1.1f, color = Palette.ink, bold = true)
    dot(b.x + 5.4f, b.y - 1.4f, 2f, Color(0xFFC0392B))
    centeredText(text, items.toString(), Point(b.x + 5.4f, b.y - 1.4f), size = 2.2f, color = Color.White, bold = true)
}

/** The tip jar on the counter in the morning, fuller the more tips are in it (full at about 100). */
private fun Pen.drawIdleTipJar(text: androidx.compose.ui.text.TextMeasurer, coins: Long) {
    val j = SceneLayout.TIP_JAR
    oval(j.x, j.y + 2.6f, 2.4f, 0.6f, Color(0x33000000))
    val fill = (coins / 100f).coerceIn(0f, 1f)
    if (fill > 0f) box(j.x - 1.8f, j.y + 2.4f - 4f * fill, 3.6f, 4f * fill, Palette.gold, radius = 0.6f)
    box(j.x - 2.2f, j.y - 2f, 4.4f, 4.6f, Color(0x55DDEEF7), radius = 1.2f)
    box(j.x - 2.4f, j.y - 2.6f, 4.8f, 1f, Color(0xCCB0BEC5), radius = 0.4f)
    line(j.x - 1.4f, j.y - 1f, j.x - 1.4f, j.y + 1.6f, Color(0x99FFFFFF), 0.3f)
    centeredText(text, "TIPS", Point(j.x, j.y + 0.6f), size = 1.4f, color = Palette.ink, bold = true)
}

private fun rectFor(target: SceneTarget, staffSpots: List<Pair<StaffFigure, Point>>): Rect? = when (target) {
    SceneTarget.Oven -> SceneLayout.oven
    SceneTarget.Fridge -> SceneLayout.fridge
    SceneTarget.Pantry -> SceneLayout.pantry
    SceneTarget.MenuBoard -> SceneLayout.menuBoard
    SceneTarget.Mop -> MOP_TAP
    SceneTarget.HiringSign -> SceneLayout.hiringSign
    SceneTarget.Tables -> null
    SceneTarget.TipJar -> TIP_JAR_TAP
    SceneTarget.LostAndFound -> LOST_BOX_TAP
    is SceneTarget.Staff -> staffSpots.firstOrNull { it.first.id == target.id }?.second?.let { Rect(it.x - 5f, it.y - 9f, it.x + 5f, it.y + 4f) }
}

private fun sin(x: Float): Float = kotlin.math.sin(x)

package com.recipefordisaster.app.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.recipefordisaster.app.ui.scene.SceneLayout.Point
import com.recipefordisaster.app.ui.scene.SceneLayout.Rect
import com.recipefordisaster.domain.employee.Role as StaffRole
import com.recipefordisaster.domain.service.FloorPoint
import com.recipefordisaster.domain.service.ServiceFloor
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.service.ServiceNight.Stage
import kotlin.math.abs
import kotlin.math.sin

/** Labels the night scene needs, for screen readers. */
data class NightLabels(
    val table: (number: Int, state: String) -> String,
    val counter: String,
    val wantsToOrder: String,
    val waitingForFood: String,
    val foodReady: String,
    val eating: String,
    val empty: String,
    val you: String,
    val menu: String,
)

/** Colours that mark whose table is whose: yours, and each hired server's. */
internal val PlayerColor = Color(0xFF3E8E41)
internal val HelperColors = listOf(Color(0xFF3B78A8), Color(0xFF9C6FB6))

/**
 * Service, played. The same restaurant as the morning, with guests in it:
 * a bubble with "?" over a table means they're ready to order, a plate
 * bubble means they're waiting for food, and a bar under it shows how much
 * patience they have left. Tickets hang on the rail by table number while
 * they cook, and finished plates wait on the counter with their table's
 * number. Tap a table to go to it; tap the counter to hand in tickets and
 * pick up food. Green table numbers are yours; blue and purple belong to
 * your hired servers, who look after them on their own.
 */
@Composable
fun NightScene(
    night: ServiceNight,
    model: SceneModel,
    labels: NightLabels,
    clock: Float,
    onTapTable: (Int) -> Unit,
    onTapCounter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val unit = minOf(widthPx / SceneLayout.WIDTH, heightPx / SceneLayout.HEIGHT)
        val origin = Offset((widthPx - unit * SceneLayout.WIDTH) / 2f, (heightPx - unit * SceneLayout.HEIGHT) / 2f)
        val ownerColor = ownerColors(night)

        Canvas(modifier = Modifier.fillMaxSize()) {
            clipRect(origin.x, origin.y, origin.x + unit * SceneLayout.WIDTH, origin.y + unit * SceneLayout.HEIGHT) {
                val pen = Pen(this, unit, origin)
                with(pen) {
                    val time = night.time
                    val cooking = night.parties.any { it.stage == Stage.COOKING }
                    drawRoom(model.cleanliness, doorOpen = true, time = clock)
                    drawOven(model.ovenCondition, clock, onFire = false)
                    drawStove(cooking, clock)
                    drawSink()
                    drawPantry(model.pantryFullness)
                    drawTables()
                    drawMenuBoard(text, labels.menu)
                    drawMopBucket()

                    // Kitchen staff at their stations, working when there's cooking.
                    staffPositions(model.staff).filter { it.first.role != StaffRole.SERVER && it.first.role != StaffRole.MANAGER }
                        .forEach { (figure, spot) ->
                            drawPerson(spot, outfitFor(figure.role), figure.morale, bob = if (cooking) sin(clock * 12f) * 0.6f else 0f, sweat = figure.stress >= 70, variant = figure.name.hashCode().mod(5))
                        }

                    drawTickets(text, night)
                    drawReadyPlates(text, night)

                    // Table number cards, in the colour of whoever looks after the table.
                    SceneLayout.tables.forEachIndexed { t, table -> drawTableNumber(text, table, t + 1, ownerColor[t] ?: PlayerColor) }

                    // Guests.
                    var queueIndex = 0
                    night.parties.forEach { party ->
                        when (party.stage) {
                            Stage.NOT_YET_ARRIVED, Stage.DONE -> {}
                            Stage.QUEUEING -> {
                                party.guests.forEachIndexed { g, guest ->
                                    val spot = ServiceFloor.queueSpot(queueIndex++).toPoint()
                                    val mood = (70 - (time - party.stageSince) / (party.patience * 0.8f) * 60).toInt()
                                    drawGuest(spot, guest, mood, angry = mood < 25, walkPhase = null, g)
                                }
                            }
                            Stage.WALKING_TO_TABLE -> {
                                val table = party.table ?: return@forEach
                                val progress = 1f - (party.until - time) / (party.until - party.stageSince).coerceAtLeast(0.01f)
                                party.guests.forEachIndexed { g, guest ->
                                    val seat = ServiceFloor.seats(table)[g]
                                    val route = ServiceFloor.route(ServiceFloor.door, ServiceFloor.stand(table)) + seat
                                    drawGuest(ServiceFloor.along(route, progress).toPoint(), guest, 65, angry = false, walkPhase = time * 2.2f + g, g)
                                }
                            }
                            Stage.LEAVING_HAPPY, Stage.LEAVING_ANGRY -> {
                                val table = party.table ?: return@forEach
                                val progress = (time - party.stageSince) / (party.until - party.stageSince).coerceAtLeast(0.01f)
                                val angry = party.stage == Stage.LEAVING_ANGRY
                                party.guests.forEachIndexed { g, guest ->
                                    val seat = ServiceFloor.seats(table)[g]
                                    val route = listOf(seat) + ServiceFloor.route(ServiceFloor.stand(table), ServiceFloor.door) + FloorPoint(50f, 156f)
                                    val mood = if (angry) 5 else night.results[guest]?.satisfaction ?: 70
                                    drawGuest(ServiceFloor.along(route, progress).toPoint(), guest, mood, angry = angry, walkPhase = time * 2.6f + g, g)
                                }
                                if (!angry) drawCoins(text, SceneLayout.tables[table], ((time - party.stageSince) / 1.2f).coerceAtMost(1f), party.guests.sumOf { night.results[it]?.dish?.sellingPrice ?: 0 })
                            }
                            else -> {
                                val table = party.table ?: return@forEach
                                val waitedFraction = when (party.stage) {
                                    Stage.READY_TO_ORDER -> (time - party.stageSince) / party.patience
                                    Stage.EATING -> 0f
                                    else -> (time - party.orderedAt) / (party.patience * 1.8f)
                                }.coerceIn(0f, 1f)
                                party.guests.forEachIndexed { g, guest ->
                                    val seat = ServiceFloor.seats(table)[g]
                                    val eating = party.stage == Stage.EATING
                                    val mood = if (eating) 80 else (75 - waitedFraction * 70).toInt()
                                    if (eating && party.orders.getOrNull(g) != null) drawPlate(Point((SceneLayout.tables[table].x + seat.x) / 2, seat.y))
                                    drawGuest(seat.toPoint(), guest, mood, angry = waitedFraction > 0.75f, walkPhase = null, g, bob = if (eating) abs(sin(clock * 6f + guest)) * 0.4f else 0f)
                                }
                                drawTableBubble(text, table, party.stage, waitedFraction, clock)
                            }
                        }
                    }

                    // Waiters: hired servers in red aprons, you in green with a marker overhead.
                    night.waiters.forEach { waiter ->
                        val at = waiter.position(time).toPoint().let { Point(it.x, it.y - 2f) }
                        val walking = waiter.walking
                        val bob = if (walking) abs(sin(time * 12f)) * 0.5f else sin(clock * 1.6f) * 0.2f
                        val figure = model.staff.firstOrNull { it.id.value == waiter.id }
                        drawPerson(
                            at = at,
                            outfit = Outfit.SERVER,
                            mood = figure?.morale ?: 80,
                            bob = bob,
                            sweat = (figure?.stress ?: 0) >= 70,
                            variant = (figure?.name ?: "you").hashCode().mod(5),
                            walkPhase = if (walking) time * 2.4f else null,
                            apron = if (waiter.isPlayer) PlayerColor else Palette.serverRed,
                        )
                        // What they're carrying: plates in hand, a notepad for tickets not yet handed in.
                        waiter.plates.forEachIndexed { k, _ -> drawPlate(Point(at.x - 4.4f + k * 8.8f, at.y + 2.6f)) }
                        if (waiter.tickets.isNotEmpty()) {
                            box(at.x + 3.6f, at.y - 1f, 3f, 3.8f, Color(0xFFFFFCF2), radius = 0.3f)
                            for (k in 0..2) line(at.x + 4.1f, at.y + 0.1f + k * 0.9f, at.x + 6f, at.y + 0.1f + k * 0.9f, Color(0x88000000), 0.2f)
                        }
                        if (waiter.isPlayer) drawYouMarker(text, labels.you, Point(at.x, at.y - 12f), clock)
                        else {
                            val c = ownerColor[waiter.tables.firstOrNull() ?: -1] ?: HelperColors[0]
                            dot(at.x, at.y - 11.4f, 1.1f, c)
                        }
                    }
                }
            }
        }

        // Tap areas: each table (with its chairs), and the counter / kitchen.
        val tableRects = SceneLayout.tables.map { Rect(it.x - 17f, it.y - 10f, it.x + 17f, it.y + 12f) }
        tableRects.forEachIndexed { t, rect ->
            val party = night.partyAt(t)
            val state = when (party?.stage) {
                Stage.READY_TO_ORDER -> labels.wantsToOrder
                Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.CARRIED -> labels.waitingForFood
                Stage.READY_AT_PASS -> labels.foodReady
                Stage.EATING -> labels.eating
                else -> labels.empty
            }
            TapArea(rect, unit, origin, labels.table(t + 1, state)) { onTapTable(t) }
        }
        TapArea(Rect(0f, 0f, SceneLayout.WIDTH, SceneLayout.counter.bottom + 2f), unit, origin, labels.counter, onTapCounter)
    }
}

@Composable
private fun TapArea(rect: Rect, unit: Float, origin: Offset, label: String, onTap: () -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(with(density) { (origin.x + rect.left * unit).toDp() }, with(density) { (origin.y + rect.top * unit).toDp() })
            .size(with(density) { (rect.width * unit).toDp() }, with(density) { (rect.height * unit).toDp() })
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onTap),
    )
}

/** Table index -> the colour of whoever looks after it. */
private fun ownerColors(night: ServiceNight): Map<Int, Color> {
    val map = mutableMapOf<Int, Color>()
    var helper = 0
    night.waiters.forEach { waiter ->
        val color = if (waiter.isPlayer) PlayerColor else HelperColors[helper++ % HelperColors.size]
        waiter.tables.forEach { map[it] = color }
    }
    return map
}

private fun Pen.drawGuest(at: Point, guest: Int, mood: Int, angry: Boolean, walkPhase: Float?, seatIndex: Int, bob: Float = 0f) {
    drawPerson(
        at = at,
        outfit = Outfit.GUEST,
        mood = mood,
        bodyColor = Palette.guestColors[guest % Palette.guestColors.size],
        bob = bob,
        angry = angry,
        variant = guest,
        walkPhase = walkPhase,
    )
}

/** A small white card standing on the table with its number, edged in its waiter's colour. */
private fun Pen.drawTableNumber(text: TextMeasurer, table: Point, number: Int, color: Color) {
    val card = Rect(table.x - 2.4f, table.y - 6.6f, table.x + 2.4f, table.y - 2.4f)
    box(card, color, radius = 0.6f)
    box(Rect(card.left + 0.5f, card.top + 0.5f, card.right - 0.5f, card.bottom - 0.5f), Color.White, radius = 0.4f)
    centeredText(text, number.toString(), card.center, size = 3.2f, color = color, bold = true)
}

/** The speech bubble over a table: "?" to order, a plate while they wait for food, with a patience bar underneath. */
private fun Pen.drawTableBubble(text: TextMeasurer, tableIndex: Int, stage: Stage, waited: Float, clock: Float) {
    if (stage == Stage.EATING) return
    val table = SceneLayout.tables[tableIndex]
    val c = Point(table.x, table.y - 13.5f)
    val wobble = if (stage == Stage.READY_TO_ORDER) sin(clock * 5f) * 0.4f else 0f
    // Bubble with a little tail.
    shape(Color(0x33000000)) { moveTo(c.x - 1.4f, c.y + 3.2f); lineTo(c.x + 1.4f, c.y + 3.2f); lineTo(c.x, c.y + 6f); close() }
    dot(c.x, c.y + wobble + 0.3f, 4.6f, Color(0x33000000))
    dot(c.x, c.y + wobble, 4.4f, Color.White)
    shape(Color.White) { moveTo(c.x - 1.2f, c.y + 3.6f); lineTo(c.x + 1.2f, c.y + 3.6f); lineTo(c.x, c.y + 5.6f); close() }
    when (stage) {
        Stage.READY_TO_ORDER -> centeredText(text, "?", Point(c.x, c.y + wobble), size = 5.4f, color = Palette.alert, bold = true)
        Stage.READY_AT_PASS -> {
            drawPlate(Point(c.x, c.y))
            dot(c.x + 3.2f, c.y - 3.2f, 1.3f, Palette.gold) // a little "ding"
        }
        else -> {
            // Knife and fork: waiting for food.
            line(c.x - 1.2f, c.y - 2.4f, c.x - 1.2f, c.y + 2.4f, Palette.steelDark, 0.5f)
            for (k in -1..1) line(c.x - 1.2f + k * 0.5f, c.y - 2.4f, c.x - 1.2f + k * 0.5f, c.y - 1.2f, Palette.steelDark, 0.25f)
            line(c.x + 1.2f, c.y - 2.4f, c.x + 1.2f, c.y + 2.4f, Palette.steelDark, 0.5f)
            box(c.x + 0.9f, c.y - 2.4f, 0.9f, 2.2f, Palette.steelDark, radius = 0.4f)
        }
    }
    // Patience bar: full and green when they've just sat down, red and short when they're about to leave.
    val barY = c.y - 6.6f
    box(c.x - 5f, barY, 10f, 1.4f, Color(0x55000000), radius = 0.7f)
    val left = (1f - waited).coerceIn(0f, 1f)
    val barColor = when {
        left > 0.5f -> Color(0xFF3E8E41)
        left > 0.25f -> Color(0xFFD69A1E)
        else -> Palette.alert
    }
    if (left > 0f) box(c.x - 5f, barY, 10f * left, 1.4f, barColor, radius = 0.7f)
}

/** Tickets clipped to the rail above the pass: table number, and a progress strip while it cooks. */
private fun Pen.drawTickets(text: TextMeasurer, night: ServiceNight) {
    val inKitchen = night.parties.filter { it.stage == Stage.IN_KITCHEN || it.stage == Stage.COOKING }.sortedBy { it.stageSince }
    val top = SceneLayout.counter.top - 7f
    inKitchen.take(6).forEachIndexed { i, party ->
        val x = 8f + i * 6.2f
        box(x, top, 5.2f, 6.4f, Color(0xFFFFFCF2), radius = 0.3f)
        dot(x + 2.6f, top + 0.4f, 0.4f, Palette.steelDark) // clip
        centeredText(text, ((party.table ?: 0) + 1).toString(), Point(x + 2.6f, top + 3f), size = 3f, color = Palette.ink, bold = true)
        if (party.stage == Stage.COOKING) {
            val progress = (1f - (party.until - night.time) / (party.until - party.stageSince).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            box(x + 0.6f, top + 5f, 4f, 0.8f, Color(0x33000000), radius = 0.4f)
            box(x + 0.6f, top + 5f, 4f * progress, 0.8f, Palette.flame, radius = 0.4f)
        }
    }
}

/** Finished plates on the counter, each with a flag showing which table it's for. */
private fun Pen.drawReadyPlates(text: TextMeasurer, night: ServiceNight) {
    val ready = night.parties.filter { it.stage == Stage.READY_AT_PASS }.sortedBy { it.stageSince }
    ready.take(5).forEachIndexed { i, party ->
        val x = 56f + i * 7.4f
        val y = SceneLayout.counter.top + 3f
        drawPlate(Point(x, y))
        line(x + 2f, y - 1f, x + 2f, y - 5.4f, Palette.ink, 0.25f)
        box(x + 2f, y - 5.4f, 3.4f, 2.6f, Palette.alert, radius = 0.3f)
        centeredText(text, ((party.table ?: 0) + 1).toString(), Point(x + 3.7f, y - 4.1f), size = 2.2f, color = Color.White, bold = true)
    }
}

/** A bouncing green arrow with "YOU" over the player's waiter. */
private fun Pen.drawYouMarker(text: TextMeasurer, label: String, at: Point, clock: Float) {
    val bounce = sin(clock * 4f) * 0.6f
    val y = at.y + bounce
    box(at.x - 4.4f, y - 2.2f, 8.8f, 3.6f, PlayerColor, radius = 1.6f)
    centeredText(text, label, Point(at.x, y - 0.4f), size = 2.4f, color = Color.White, bold = true)
    shape(PlayerColor) { moveTo(at.x - 1.2f, y + 1.4f); lineTo(at.x + 1.2f, y + 1.4f); lineTo(at.x, y + 3f); close() }
}

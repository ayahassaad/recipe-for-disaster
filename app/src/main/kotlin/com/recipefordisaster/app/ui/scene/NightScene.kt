package com.recipefordisaster.app.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.recipefordisaster.domain.service.ChaosKind
import com.recipefordisaster.domain.service.ChefMood
import com.recipefordisaster.domain.service.ServiceFloor
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.service.ServiceNight.Stage
import kotlin.math.abs
import kotlin.math.sin

/** Labels the night scene needs, for screen readers. */
data class NightLabels(
    val table: (number: Int, state: String) -> String,
    /** The chef / kitchen area, where orders are handed in. */
    val counter: String,
    /** A plate on the counter: "Food for table N". */
    val plate: (table: Int) -> String = { "" },
    val wantsToOrder: String,
    val waitingForFood: String,
    /** The bar, where drinks are poured, and the table states around drinks. */
    val bar: String = "",
    val wantsDrinks: String = "",
    val waitingForDrinks: String = "",
    val drinking: String = "",
    val deciding: String = "",
    val foodReady: String,
    val eating: String,
    val empty: String,
    val needsClearing: String,
    val dishStation: String,
    val dishSign: String,
    val mopBucket: String,
    val fridge: String = "",
    val chaos: String = "",
    val spill: String,
    val you: String,
    val menu: String,
)

/** Colours that mark whose table is whose: yours, and each hired server's. */
internal val PlayerColor = Color(0xFF3E8E41)
internal val HelperColors = listOf(
    Color(0xFF3B78A8), Color(0xFF9C6FB6), Color(0xFFE08A2E), Color(0xFF2E9C8F), Color(0xFFD46A8C), Color(0xFF8C8C2E), Color(0xFF6B4F3A),
)

/**
 * Each hired helper's colour, by id: servers, dishwashers and bussers (two
 * of each), then the host — the same people, in the same order, that work the floor at night —
 * so someone wears the same colour in the morning as during service.
 */
internal fun helperColors(staff: List<StaffFigure>): Map<String, Color> {
    val helpers = staff.filter { it.role == StaffRole.SERVER || it.role == StaffRole.MANAGER }.take(2) +
        staff.filter { it.role == StaffRole.DISHWASHER }.take(2) +
        staff.filter { it.role == StaffRole.BUSSER }.take(2) +
        staff.filter { it.role == StaffRole.HOST }.take(1)
    return helpers.mapIndexed { k, figure -> figure.id.value to HelperColors[k % HelperColors.size] }.toMap()
}

/**
 * Service, played. The same restaurant as the morning, with guests in it:
 * a bubble with "?" over a table means they're ready to order, a plate
 * bubble means they're waiting for food, and a bar under it shows how much
 * patience they have left. Tickets hang on the rail by table number while
 * they cook, and finished plates wait on the counter with their table's
 * number. Tap a table to go to it, the chef to hand in orders, and a plate
 * to pick it up. Every table is yours; hired servers run plates out when
 * you leave them waiting on the counter.
 */
@Composable
fun NightScene(
    night: ServiceNight,
    model: SceneModel,
    labels: NightLabels,
    clock: Float,
    onTapTable: (Int) -> Unit,
    /** The chef area: hand in the orders you're carrying. */
    onTapCounter: () -> Unit,
    /** A plate on the counter, by party id: pick it up. */
    onTapPlate: (Int) -> Unit = {},
    onTapDishStation: () -> Unit,
    onTapMopBucket: () -> Unit,
    onTapMess: (Int) -> Unit,
    onTapFridge: () -> Unit = {},
    onTapChaos: () -> Unit = {},
    /** The bar: pour the drinks you've taken orders for. */
    onTapBar: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Something to point at with a pulsing ring, for players still learning what to tap. */
    focus: NightFocus? = null,
    /** How the player has chosen to look. */
    playerLook: com.recipefordisaster.app.ui.player.PlayerLook = com.recipefordisaster.app.ui.player.PlayerLook(),
) {
    val text = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val unit = minOf(widthPx / SceneLayout.WIDTH, heightPx / SceneLayout.HEIGHT)
        val origin = Offset((widthPx - unit * SceneLayout.WIDTH) / 2f, (heightPx - unit * SceneLayout.HEIGHT) / 2f)
        val ownerColor = ownerColors(night)
        // Tonight's tables, and how small they're drawn (full size up to six, smaller when packed).
        val layout = night.layout
        val tablePoints = layout.tables.map { it.toPoint() }
        val scale = layout.scale
        val helperColor = remember(model.staff) { helperColors(model.staff) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSurround(origin, unit)
            clipRect(origin.x, origin.y, origin.x + unit * SceneLayout.WIDTH, origin.y + unit * SceneLayout.HEIGHT) {
                val pen = Pen(this, unit, origin)
                with(pen) {
                    val time = night.time
                    val cooking = night.parties.any { it.stage == Stage.COOKING }
                    drawRoom(model.cleanliness, doorOpen = doorOpenness(night), time = clock, name = model.name, text = text, aisles = layout.aisles)
                    drawOven(model.ovenCondition, clock, onFire = false, level = model.ovenLevel)
                    drawStove(cooking, clock)
                    drawFridge(model.fridgeCondition, broken = night.fridgeBroken, struggling = night.fridgeStruggling, level = model.fridgeLevel, time = clock)
                    if (night.fridgeBroken) drawAlert(text, Point(SceneLayout.fridge.right - 1f, SceneLayout.fridge.top + 1f), clock)
                    drawPantry(model.pantryFullness, model.pantryJars)
                    drawTables(layout)
                    drawMenuBoard(text, labels.menu)
                    // The mop stays in the bucket unless someone's carrying it.
                    drawMopBucket(withMop = night.waiters.none { it.holdingMop })

                    // Kitchen staff at their stations, working when there's cooking; the host by the door.
                    staffPositions(model.staff).filter { it.first.role == StaffRole.COOK || it.first.role == StaffRole.HOST }
                        .forEach { (figure, spot) ->
                            val bob = if (figure.role == StaffRole.COOK && cooking) sin(clock * 12f) * 0.6f else sin(clock * 1.6f) * 0.2f
                            val cook = figure.role == StaffRole.COOK
                            // The cooks wear tonight's mood on their faces: scowling, or beaming.
                            val mood = when {
                                !cook -> figure.morale
                                night.chefMood == ChefMood.GRUMPY -> 10
                                night.chefMood == ChefMood.HAPPY -> 95
                                else -> figure.morale
                            }
                            drawPerson(spot, outfitFor(figure.role), mood, bob = bob, sweat = figure.stress >= 70, variant = figure.name.hashCode().mod(5), apron = helperColor[figure.id.value],
                                angry = cook && night.chefMood == ChefMood.GRUMPY)
                            helperColor[figure.id.value]?.let { dot(spot.x, spot.y - 11.4f, 1.1f, it) }
                            if (cook && cooking) drawChefAtWork(text, Point(spot.x, spot.y + bob), clock + figure.id.hashCode() % 7, grumpy = night.chefMood == ChefMood.GRUMPY)
                            if (cook) drawChefMood(text, Point(spot.x, spot.y), night.chefMood, clock + figure.id.hashCode() % 5)
                        }

                    drawBurnt(text, time - night.lastBurnAt, clock)
                    drawTipJar(text, night, clock)
                    drawTickets(text, night)
                    drawReadyPlates(text, night, clock)
                    drawDishStation(text, labels.dishSign, night, clock)

                    // Tables guests have left: dirty plates, crumbs and a crumpled napkin until someone clears them.
                    night.dirtyTables.forEach { t -> around(tablePoints[t], scale).drawDirtyTable(tablePoints[t]) }

                    // Spills on the floor, and a ring filling up while someone mops one.
                    night.messesOnFloor.forEach { mess ->
                        if (mess.crumbs) drawDroppedFood(mess.at.toPoint(), mess.id, clock) else drawSpill(mess.at.toPoint(), mess.id, clock, sinceAppeared = night.time - mess.appearsAt)
                    }
                    night.waiters.forEach { w ->
                        val mopping = w.errand as? ServiceNight.Errand.Mopping ?: return@forEach
                        val at = night.messes.first { it.id == mopping.messId }.at
                        val progress = ((night.time - w.routeStart) / (w.routeEnd - w.routeStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                        drawProgressRing(Point(at.x, at.y - 13f), progress)
                    }

                    // Table number cards, in the colour of whoever looks after the table.
                    tablePoints.forEachIndexed { t, table -> around(table, scale).drawTableNumber(text, table, t + 1, ownerColor[t] ?: PlayerColor) }

                    // Guests.
                    var queueIndex = 0
                    night.parties.forEach { party ->
                        when (party.stage) {
                            Stage.NOT_YET_ARRIVED, Stage.DONE -> {}
                            Stage.QUEUEING -> {
                                val waited = night.impatience(party)
                                val first = ServiceFloor.queueSpot(queueIndex).toPoint()
                                party.guests.forEachIndexed { g, guest ->
                                    val spot = ServiceFloor.queueSpot(queueIndex++).toPoint()
                                    val mood = (70 - waited * 60).toInt()
                                    val child = party.family && g == 1
                                    drawGuest(spot, guest, mood, angry = mood < 25, walkPhase = null, g, child = child)
                                    if (child) drawBalloon(spot, guest, clock)
                                    if (g == 0) party.special?.let { drawSpecialLook(spot, it, clock) }
                                }
                                // A "we need a table" bubble with their patience, so a crowd at the door is hard to miss.
                                drawDoorBubble(first, waited, clock)
                            }
                            Stage.WALKING_TO_TABLE -> {
                                val table = party.table ?: return@forEach
                                val progress = 1f - (party.until - time) / (party.until - party.stageSince).coerceAtLeast(0.01f)
                                party.guests.forEachIndexed { g, guest ->
                                    val seat = layout.seats(table)[g]
                                    val route = ServiceFloor.route(ServiceFloor.door, layout.stand(table), layout.aisles) + seat
                                    // Side by side all the way in, then each steps into their own seat.
                                    val apart = if (party.guests.size > 1) (1f - ((progress - 0.8f) / 0.2f)).coerceIn(0f, 1f) else 0f
                                    val at = sideBySide(route, progress, if (g == 0) -1f else 1f, apart)
                                    val child = party.family && g == 1
                                    around(at, scale).drawGuest(at, guest, 65, angry = false, walkPhase = time * 2.2f + g, g, child = child)
                                    if (child) around(at, scale).drawBalloon(at, guest, clock)
                                    if (g == 0) party.special?.let { around(at, scale).drawSpecialLook(at, it, clock) }
                                }
                            }
                            Stage.LEAVING_HAPPY, Stage.LEAVING_ANGRY -> {
                                val table = party.table ?: return@forEach
                                val progress = (time - party.stageSince) / (party.until - party.stageSince).coerceAtLeast(0.01f)
                                val angry = party.stage == Stage.LEAVING_ANGRY
                                party.guests.forEachIndexed { g, guest ->
                                    val seat = layout.seats(table)[g]
                                    val route = listOf(seat) + ServiceFloor.route(layout.stand(table), ServiceFloor.door, layout.aisles) + FloorPoint(50f, 156f)
                                    val mood = if (angry) 5 else night.results[guest]?.satisfaction ?: 70
                                    // Up from their seats, then out together side by side.
                                    val apart = if (party.guests.size > 1) (progress / 0.2f).coerceIn(0f, 1f) else 0f
                                    val at = sideBySide(route, progress, if (g == 0) -1f else 1f, apart)
                                    // Angry guests stomp out: a faster, bouncier walk, and a cloud of cross words overhead.
                                    val stomp = if (angry) abs(sin(time * 9f + g)) * 0.9f else 0f
                                    val child = party.family && g == 1
                                    around(at, scale).drawGuest(Point(at.x, at.y - stomp), guest, mood, angry = angry, walkPhase = time * (if (angry) 4.2f else 2.6f) + g, g, child = child)
                                    if (child) around(at, scale).drawBalloon(at, guest, clock)
                                    if (g == 0) party.special?.let { around(at, scale).drawSpecialLook(Point(at.x, at.y - stomp), it, clock) }
                                    if (angry && g == 0) drawGrumble(text, Point(at.x, at.y - 14f), clock)
                                }
                                if (!angry) {
                                    val progress = ((time - party.stageSince) / 1.2f).coerceAtMost(1f)
                                    drawCoins(text, tablePoints[table], progress, party.guests.sumOf { night.results[it]?.dish?.sellingPrice ?: 0 })
                                    // A tip for quick service gets its own little pop, just under the bill.
                                    if (party.tip > 0) {
                                        val alpha = (1f - (progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                                        centeredText(text, "+${party.tip} tip!", Point(tablePoints[table].x + 6f, tablePoints[table].y - 0.5f - progress * 8f), size = 2.6f, color = Color(0xFF3E8E41).copy(alpha = alpha), bold = true)
                                    }
                                }
                            }
                            else -> {
                                val table = party.table ?: return@forEach
                                val waitedFraction = night.impatience(party)
                                // Drawn at full size around the table's centre, then shrunk to fit if the room is packed.
                                val c = tablePoints[table]
                                with(around(c, scale)) {
                                    party.guests.forEachIndexed { g, guest ->
                                        val seat = Point(c.x + (if (g == 0) -12f else 12f), c.y)
                                        val eating = party.stage == Stage.EATING
                                        val mood = if (eating) 80 else (75 - waitedFraction * 70).toInt()
                                        // The food goes on the plate already laid at their place, not on a second plate.
                                        if (eating && party.orders.getOrNull(g) != null) drawFood(Point(c.x + (if (g == 0) -4.2f else 4.2f), c.y))
                                        if (eating && party.chefsSpecial && party.orders.getOrNull(g) != null) drawSpecialStar(Point(c.x + (if (g == 0) -4.2f else 4.2f), c.y))
                                        val eatingPlate = Point(c.x + (if (g == 0) -4.2f else 4.2f), c.y)
                                        // A fresh plate steams for the first few seconds.
                                        if (eating && party.orders.getOrNull(g) != null && time - party.stageSince < 3.5f) drawSteam(eatingPlate, clock, guest)
                                        val guestBob = if (eating) abs(sin(clock * 6f + guest)) * 0.4f else 0f
                                        // A child sits up on a booster cushion, so they can see over the table.
                                        val child = party.family && g == 1
                                        if (child) box(seat.x - 3f, seat.y + 0.6f, 6f, 2.4f, Color(0xFFE85D75), radius = 1f)
                                        drawGuest(if (child) Point(seat.x, seat.y - 1.2f) else seat, guest, mood, angry = waitedFraction > 0.75f, walkPhase = null, g, bob = guestBob, child = child)
                                        if (g == 0) party.special?.let { drawSpecialLook(seat, it, clock, guestBob) }
                                        // Reading the menu while deciding; now and then someone checks their phone while the food comes.
                                        // Once they've waited a good while, they keep looking at their watch instead.
                                        val waitingStage = party.stage == Stage.READY_TO_ORDER || party.stage in Stage.ORDER_TAKEN..Stage.CARRIED
                                        when {
                                            waitingStage && waitedFraction >= WATCH_FROM && ((clock * 0.35f + guest * 0.5f) % 1f) < 0.45f -> drawWatchCheck(seat, clock, Palette.guestColors[guest % Palette.guestColors.size])
                                            party.stage == Stage.DECIDING || party.stage == Stage.READY_TO_ORDER -> drawMenuCard(seat, clock + guest)
                                            party.stage in Stage.ORDER_TAKEN..Stage.CARRIED && waitedFraction < 0.6f &&
                                                ((clock * 0.2f + guest * 0.37f) % 1f) < 0.4f -> drawPhone(seat, clock)
                                        }
                                    }
                                    // Two at a table chat while they wait and eat (unless they're getting cross).
                                    if (party.guests.size == 2 && waitedFraction < 0.6f && party.stage in Stage.ORDER_TAKEN..Stage.EATING) {
                                        drawChatter(text, c, party.id, clock)
                                    }
                                    drawTableBubble(text, c, party.stage, waitedFraction, clock)
                                }
                            }
                        }
                    }

                    // Lights out: everything goes dark except a little glow around the fuse box.
                    if (night.activeChaos?.kind == ChaosKind.POWER_CUT) {
                        drawRect(Color(0xCC0B0E1A), topLeft = p(0f, 0f), size = androidx.compose.ui.geometry.Size(u(SceneLayout.WIDTH), u(SceneLayout.HEIGHT)))
                        dot(ServiceFloor.fuseBox.x - 4f, ServiceFloor.fuseBox.y - 4f, 9f, Color(0x33F2C230))
                        drawFuseBox(true)
                        drawAlert(text, Point(ServiceFloor.fuseBox.x - 1f, ServiceFloor.fuseBox.y - 12f), clock)
                    }
                    // "Tap here": a pulsing ring around whatever the hint is talking about.
                    focus?.let { f ->
                        val (center, radius) = when (f) {
                            is NightFocus.Table -> tablePoints[f.table] to 16f * scale
                            NightFocus.Chef -> SceneLayout.cookSpot(0).let { Point(it.x, it.y - 3f) } to 9f
                            is NightFocus.Plate -> (readyPlates(night).indexOfFirst { it.id == f.partyId }.takeIf { it >= 0 }?.let { plateSpot(it) } ?: return@let) to 4.5f
                            NightFocus.DishStation -> Point(87f, SceneLayout.counter.top + 1f) to 9f
                            NightFocus.Fridge -> SceneLayout.fridge.center to 10f
                            NightFocus.Chaos -> (if (night.activeChaos?.kind == ChaosKind.PAN_FIRE) SceneLayout.stove.center else night.chaosSpot()?.toPoint() ?: return@let) to 9f
                            NightFocus.MopBucket -> SceneLayout.mopBucket.center to 9f
                            is NightFocus.Spill -> (night.messes.firstOrNull { it.id == f.messId }?.at?.toPoint() ?: return@let) to 7f
                        }
                        val pulse = (clock * 1.4f) % 1f
                        ring(center.x, center.y, radius + pulse * 3f, PlayerColor.copy(alpha = 0.9f * (1f - pulse)), 0.9f)
                        ring(center.x, center.y, radius, PlayerColor.copy(alpha = 0.55f), 0.5f)
                    }

                    // Where you're heading, and the stops you've queued after it.
                    val me = night.player
                    (listOfNotNull(me.errand) + me.queue).forEachIndexed { k, errand ->
                        val spot = when (errand) {
                            is ServiceNight.Errand.VisitTable -> layout.stand(errand.table)
                            ServiceNight.Errand.VisitPass -> ServiceFloor.pass
                            ServiceNight.Errand.HandIn -> ServiceFloor.chef
                            is ServiceNight.Errand.PickUp -> night.platesOnCounter.indexOfFirst { it.id == errand.partyId }.takeIf { it >= 0 }?.let { ServiceFloor.plateStand(it) }
                            ServiceNight.Errand.VisitDishStation -> ServiceFloor.dishStation
                            ServiceNight.Errand.VisitFridge -> ServiceFloor.fridge
                            ServiceNight.Errand.VisitMopBucket -> ServiceFloor.mopBucket
                            is ServiceNight.Errand.CleanMess -> night.messes.firstOrNull { it.id == errand.messId }?.at
                            else -> null
                        } ?: return@forEachIndexed
                        val at = Point(spot.x, spot.y + 3.5f)
                        if (k == 0) {
                            ring(at.x, at.y, 2.2f + 0.3f * sin(clock * 6f), PlayerColor, 0.5f)
                        } else {
                            dot(at, 2.2f, PlayerColor)
                            centeredText(text, k.toString(), at, size = 2.6f, color = Color.White, bold = true)
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
                            outfit = if (waiter.kind == ServiceNight.Kind.DISHWASHER) Outfit.WASHER else Outfit.SERVER,
                            mood = figure?.morale ?: 80,
                            bob = bob,
                            sweat = (figure?.stress ?: 0) >= 70,
                            variant = (figure?.name ?: "you").hashCode().mod(5),
                            walkPhase = if (walking && waiter.errand != ServiceNight.Errand.Wash) time * 2.4f else null,
                            apron = if (waiter.isPlayer) playerLook.apronColor else helperColor[waiter.id],
                            look = if (waiter.isPlayer) playerLook else null,
                            // While washing up, the arms are drawn holding the plate instead.
                            armsBusy = waiter.errand == ServiceNight.Errand.Wash || waiter.errand == ServiceNight.Errand.HandleChaos,
                        )
                        // What's in each hand (left, then right), and a notepad for tickets not yet handed in.
                        waiter.hands.forEachIndexed { k, item ->
                            val hand = Point(at.x + (if (k == 0) -4.6f else 4.6f), at.y + 2.6f)
                            when (item) {
                                is ServiceNight.HandItem.DirtyDishes -> drawDirtyStack(hand)
                                is ServiceNight.HandItem.Drinks -> {}
                                ServiceNight.HandItem.Mop -> drawMop(hand, if (k == 0) -1f else 1f, mopping = waiter.errand is ServiceNight.Errand.Mopping, clock = clock)
                                is ServiceNight.HandItem.Plate -> {
                                    // A little hop as it's picked up.
                                    val since = night.parties.firstOrNull { it.id == item.partyId }?.let { time - it.stageSince } ?: 1f
                                    val hop = if (since < 0.35f) kotlin.math.sin(since / 0.35f * PI_F) * 2.2f else 0f
                                    val plateAt = Point(hand.x, hand.y - hop)
                                    drawPlate(plateAt)
                                    drawSteam(plateAt, clock, item.partyId)
                                    if (night.parties.firstOrNull { it.id == item.partyId }?.chefsSpecial == true) drawSpecialStar(plateAt)
                                    // The table number travels with the plate, so you always know where it's going.
                                    night.parties.firstOrNull { it.id == item.partyId }?.table?.let { table -> drawNumberFlag(text, plateAt, table + 1) }
                                }
                            }
                        }
                        // One order note per ticket not yet handed in, fanned out, each with its table number.
                        waiter.tickets.forEachIndexed { k, partyId ->
                            val nx = at.x + 3.4f + k * 2.6f
                            val ny = at.y - 2.4f - k * 1.2f
                            box(nx, ny, 3.4f, 4.4f, Color(0xFFFFFCF2), radius = 0.3f)
                            line(nx + 0.5f, ny + 3.2f, nx + 2.9f, ny + 3.2f, Color(0x88000000), 0.2f)
                            line(nx + 0.5f, ny + 3.9f, nx + 2.4f, ny + 3.9f, Color(0x88000000), 0.2f)
                            night.parties.firstOrNull { it.id == partyId }?.table?.let { table ->
                                centeredText(text, (table + 1).toString(), Point(nx + 1.7f, ny + 1.4f), size = 2.2f, color = Palette.ink, bold = true)
                            }
                        }
                        if (waiter.isPlayer) {
                            // Standing at the chef, the marker would sit on the chef's face: put it beside you instead.
                            // Same at the dish station, where it would cover the DISHES sign: put it on the left.
                            val here = waiter.position(time)
                            val atChef = here.distanceTo(ServiceFloor.chef) < 4f
                            val atDishes = here.distanceTo(ServiceFloor.dishStation) < 4f
                            val marker = when {
                                atChef -> Point(at.x + 10f, at.y - 6f)
                                atDishes -> Point(at.x - 10f, at.y - 6f)
                                else -> Point(at.x, at.y - 12f)
                            }
                            drawYouMarker(text, labels.you, marker, clock)
                        }
                        else {
                            helperColor[waiter.id]?.let { dot(at.x, at.y - 11.4f, 1.1f, it) }
                        }
                    }

                    // Tonight's chaos: a rat, a pan fire, a begging dog, or the lights going out (dark overlay drawn last).
                    night.activeChaos?.let { c ->
                        val spot = night.chaosSpot()!!.toPoint()
                        when (c.kind) {
                            ChaosKind.RAT -> drawRat(spot, clock)
                            ChaosKind.DOG -> drawDog(spot, clock)
                            ChaosKind.PAN_FIRE -> {
                                // Flames leaping out of the frying pan, kept on the stove, dying down as they're put out.
                                val dousing = night.waiters.firstOrNull { it.errand == ServiceNight.Errand.HandleChaos }
                                    ?.let { ((night.time - it.routeStart) / (it.routeEnd - it.routeStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f) } ?: 0f
                                drawFlames(FIRE_AT, clock, size = 0.62f * (1f - dousing))
                                drawSmoke(Point(FIRE_AT.x + 1f, SceneLayout.stove.top + 1f), clock)
                            }
                            ChaosKind.POWER_CUT -> {}
                        }
                        val alertAt = when (c.kind) {
                            ChaosKind.PAN_FIRE -> Point(SceneLayout.stove.right - 1f, SceneLayout.stove.top + 1f)
                            ChaosKind.POWER_CUT -> Point(spot.x + 3f, spot.y - 6f)
                            else -> Point(spot.x + 3f, spot.y - 5f)
                        }
                        if (!night.chaosBeingHandled) drawAlert(text, alertAt, clock)
                    }
                    drawFuseBox(night.activeChaos?.kind == ChaosKind.POWER_CUT)

                    // The restaurant cat, and the plate it just knocked off the counter.
                    night.catPose()?.let { pose -> drawCat(pose.at.toPoint(), pose.walking, pose.facingRight, clock) }
                    val sinceKnock = time - night.catKnockAt
                    if (sinceKnock in 0f..1.2f) drawFallingPlate(text, Point(78f, SceneLayout.counter.top + 2f), sinceKnock)

                    // Anyone dealing with the chaos, doing the right thing for it.
                    night.activeChaos?.let { c ->
                        night.waiters.filter { it.errand == ServiceNight.Errand.HandleChaos }.forEach { w ->
                            val progress = ((night.time - w.routeStart) / (w.routeEnd - w.routeStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                            drawHandlingChaos(w.position(night.time).toPoint().let { Point(it.x, it.y - 2f) }, c.kind, night.chaosSpot()!!.toPoint(), progress, clock)
                        }
                    }

                    // Anyone fixing the fridge: banging away at it with a spanner.
                    night.waiters.filter { it.errand == ServiceNight.Errand.FixFridge }.forEach { w ->
                        val progress = ((night.time - w.routeStart) / (w.routeEnd - w.routeStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                        drawFixingFridge(w.position(night.time).toPoint().let { Point(it.x, it.y - 2f) }, progress, clock)
                    }

                    // Anyone washing up, scrubbing away at the sink (drawn over them, since their hands are in it).
                    night.waiters.filter { it.errand == ServiceNight.Errand.Wash }.forEach { w ->
                        val progress = ((night.time - w.routeStart) / (w.routeEnd - w.routeStart).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                        // Sleeves match the outfit: the black waistcoat for you and servers, blue overalls for a dishwasher.
                        val sleeve = if (w.kind == ServiceNight.Kind.DISHWASHER) Palette.washerBlue else Color(0xFF2B2B2B)
                        drawWashingUp(w.position(night.time).toPoint().let { Point(it.x, it.y - 2f) }, progress, clock, sleeve)
                    }

                    // The evening drawing in as the night goes on (not while the lights are out: that's dark enough).
                    if (night.activeChaos?.kind != ChaosKind.POWER_CUT) {
                        val evening = eveningOf(time, night.parties.maxOfOrNull { it.arriveAt } ?: 0f)
                        drawEveningLight(evening)
                        drawLamps(evening, layout, clock)
                    }
                }
            }
        }

        // Tap areas: each table (with its chairs), and the counter / kitchen.
        val tableRects = tablePoints.map { Rect(it.x - 17f * scale, it.y - 10f * scale, it.x + 17f * scale, it.y + 12f * scale) }
        tableRects.forEachIndexed { t, rect ->
            val party = night.partyAt(t)
            val state = when (party?.stage) {
                Stage.READY_TO_ORDER -> labels.wantsToOrder
                Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.CARRIED -> labels.waitingForFood
                Stage.READY_AT_PASS -> labels.foodReady
                Stage.EATING -> labels.eating
                Stage.DECIDING -> labels.deciding
                Stage.WANTS_DRINKS -> labels.wantsDrinks
                Stage.DRINKS_ORDERED -> labels.waitingForDrinks
                Stage.DRINKING -> labels.drinking
                else -> if (t in night.dirtyTables) labels.needsClearing else labels.empty
            }
            TapArea(rect, unit, origin, labels.table(t + 1, state)) { onTapTable(t) }
        }
        // The chef's end of the kitchen hands in orders; each plate on the counter is its own tap.
        TapArea(Rect(0f, 0f, 54f, SceneLayout.counter.bottom + 2f), unit, origin, labels.counter, onTapCounter)
        TapArea(Rect(78f, 0f, SceneLayout.WIDTH, SceneLayout.counter.bottom + 2f), unit, origin, labels.dishStation, onTapDishStation)
        // The bar sits in the chef's end of the counter, so its tap goes on top.
        TapArea(Rect(SceneLayout.bar.left, SceneLayout.bar.top, SceneLayout.bar.right, SceneLayout.counter.bottom + 2f), unit, origin, labels.bar, onTapBar)
        readyPlates(night).forEachIndexed { i, party ->
            val at = plateSpot(i)
            TapArea(Rect(at.x - 3.4f, at.y - 6f, at.x + 4f, at.y + 3f), unit, origin, labels.plate((party.table ?: 0) + 1)) { onTapPlate(party.id) }
        }
        // Tonight's chaos, wherever it is: tap it to deal with it.
        night.activeChaos?.let { c ->
            val rect = when (c.kind) {
                ChaosKind.PAN_FIRE -> SceneLayout.stove
                else -> night.chaosSpot()!!.let { Rect(it.x - 7f, it.y - 7f, it.x + 7f, it.y + 5f) }
            }
            TapArea(rect, unit, origin, labels.chaos, onTapChaos)
        }
        // The fridge, over the chef's end of the kitchen so it gets the tap.
        TapArea(SceneLayout.fridge, unit, origin, labels.fridge, onTapFridge)
        TapArea(Rect(SceneLayout.mopBucket.left - 2f, SceneLayout.mopBucket.top - 8f, SceneLayout.mopBucket.right + 6f, SceneLayout.mopBucket.bottom), unit, origin, labels.mopBucket, onTapMopBucket)
        // Spills last, so they sit on top of the tables' tap areas.
        night.messesOnFloor.forEach { mess ->
            TapArea(Rect(mess.at.x - 6f, mess.at.y - 4f, mess.at.x + 6f, mess.at.y + 4f), unit, origin, labels.spill) { onTapMess(mess.id) }
        }
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

/** Table index -> colour for its number card: every table is the player's. */
private fun ownerColors(night: ServiceNight): Map<Int, Color> = night.player.tables.associateWith { PlayerColor }

private fun Pen.drawGuest(at: Point, guest: Int, mood: Int, angry: Boolean, walkPhase: Float?, seatIndex: Int, bob: Float = 0f, child: Boolean = false) {
    // A child is a smaller person, standing on the same spot of floor, in a bright top.
    if (child) {
        around(Point(at.x, at.y + 3.6f), CHILD_SIZE).drawPerson(
            at = at,
            outfit = Outfit.GUEST,
            mood = mood,
            bodyColor = CHILD_COLOURS[guest % CHILD_COLOURS.size],
            bob = bob,
            angry = angry,
            variant = guest + 1,
            walkPhase = walkPhase,
        )
        return
    }
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

private const val CHILD_SIZE = 0.68f
private val CHILD_COLOURS = listOf(Color(0xFFF2C230), Color(0xFFE85D75), Color(0xFF4FB3D9), Color(0xFF7BC67B))

/** A balloon on a string, bobbing above a child walking in or out. */
private fun Pen.drawBalloon(child: Point, guest: Int, clock: Float) {
    val sway = sin(clock * 1.7f + guest) * 0.8f
    val hand = Point(child.x + 2.6f, child.y + 1.4f)
    val b = Point(child.x + 4.2f + sway, child.y - 9.5f)
    line(hand.x, hand.y, b.x, b.y + 2.2f, Color(0x99FFFFFF), 0.2f)
    oval(b.x, b.y, 1.8f, 2.2f, CHILD_COLOURS[(guest + 1) % CHILD_COLOURS.size])
    dot(b.x - 0.6f, b.y - 0.8f, 0.45f, Color(0x88FFFFFF))
}

/** A small white card standing on the table with its number, edged in its waiter's colour. */
private fun Pen.drawTableNumber(text: TextMeasurer, table: Point, number: Int, color: Color) {
    val card = Rect(table.x - 2.4f, table.y - 6.6f, table.x + 2.4f, table.y - 2.4f)
    box(card, color, radius = 0.6f)
    box(Rect(card.left + 0.5f, card.top + 0.5f, card.right - 0.5f, card.bottom - 0.5f), Color.White, radius = 0.4f)
    centeredText(text, number.toString(), card.center, size = 3.2f, color = color, bold = true)
}

/** A wine glass for a speech bubble, empty or with something red in it. */
private fun Pen.drawBubbleGlass(at: Point, full: Boolean) {
    val bowl = Color(0xFF7A8C96)
    if (full) {
        shape(Color(0xFFB0303C)) {
            moveTo(at.x - 1.3f, at.y - 1.2f)
            quadTo(at.x, at.y + 1.6f, at.x + 1.3f, at.y - 1.2f)
            close()
        }
    }
    shape(bowl, stroke = 0.35f) {
        moveTo(at.x - 1.6f, at.y - 2.6f)
        quadTo(at.x - 1.8f, at.y + 1.2f, at.x, at.y + 1.2f)
        quadTo(at.x + 1.8f, at.y + 1.2f, at.x + 1.6f, at.y - 2.6f)
    }
    line(at.x, at.y + 1.2f, at.x, at.y + 2.8f, bowl, 0.35f)
    line(at.x - 1.1f, at.y + 2.9f, at.x + 1.1f, at.y + 2.9f, bowl, 0.4f)
}

/** The speech bubble over a table: "?" to order, a plate while they wait for food, with a patience bar underneath. */
private fun Pen.drawTableBubble(text: TextMeasurer, table: Point, stage: Stage, waited: Float, clock: Float) {
    // No bubble while they eat, while they're still choosing, or while they sip their drinks.
    if (stage == Stage.EATING || stage == Stage.DECIDING || stage == Stage.DRINKING) return
    val c = Point(table.x, table.y - 13.5f)
    val wobble = if (stage == Stage.READY_TO_ORDER || stage == Stage.WANTS_DRINKS) sin(clock * 5f) * 0.4f else 0f
    // Bubble with a little tail.
    shape(Color(0x33000000)) { moveTo(c.x - 1.4f, c.y + 3.2f); lineTo(c.x + 1.4f, c.y + 3.2f); lineTo(c.x, c.y + 6f); close() }
    dot(c.x, c.y + wobble + 0.3f, 4.6f, Color(0x33000000))
    dot(c.x, c.y + wobble, 4.4f, Color.White)
    shape(Color.White) { moveTo(c.x - 1.2f, c.y + 3.6f); lineTo(c.x + 1.2f, c.y + 3.6f); lineTo(c.x, c.y + 5.6f); close() }
    when (stage) {
        Stage.READY_TO_ORDER -> centeredText(text, "?", Point(c.x, c.y + wobble), size = 5.4f, color = Palette.alert, bold = true)
        // A glass and a "?": they'd like to order drinks.
        Stage.WANTS_DRINKS -> {
            drawBubbleGlass(Point(c.x - 1.4f, c.y + wobble), full = false)
            centeredText(text, "?", Point(c.x + 2f, c.y + wobble - 0.2f), size = 4f, color = Palette.alert, bold = true)
        }
        // A full glass: waiting for the drinks to come.
        Stage.DRINKS_ORDERED -> drawBubbleGlass(Point(c.x, c.y), full = true)
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
    val top = TICKET_RAIL_Y + 0.2f
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
/** Plates waiting on the counter, oldest first (as many as fit). */
private fun readyPlates(night: ServiceNight) = night.platesOnCounter

/** Where the [i]th waiting plate sits on the counter (the same spot the player walks to). */
private fun plateSpot(i: Int) = ServiceFloor.plate(i).toPoint()

private fun Pen.drawReadyPlates(text: TextMeasurer, night: ServiceNight, clock: Float) {
    readyPlates(night).forEachIndexed { i, party ->
        val at = plateSpot(i)
        drawPlate(at)
        drawSteam(at, clock, party.id)
        if (party.chefsSpecial) drawSpecialStar(at)
        drawNumberFlag(text, at, (party.table ?: 0) + 1)
    }
}

/** A little red flag on a cocktail stick in a plate, showing the table number. */
private fun Pen.drawNumberFlag(text: TextMeasurer, plate: Point, number: Int) {
    line(plate.x + 2f, plate.y - 1f, plate.x + 2f, plate.y - 5.4f, Palette.ink, 0.25f)
    box(plate.x + 2f, plate.y - 5.4f, 3.4f, 2.6f, Palette.alert, radius = 0.3f)
    centeredText(text, number.toString(), Point(plate.x + 3.7f, plate.y - 4.1f), size = 2.2f, color = Color.White, bold = true)
}

/** A bouncing green arrow with "YOU" over the player's waiter. */
private fun Pen.drawYouMarker(text: TextMeasurer, label: String, at: Point, clock: Float) {
    val bounce = sin(clock * 4f) * 0.6f
    val y = at.y + bounce
    box(at.x - 4.4f, y - 2.2f, 8.8f, 3.6f, PlayerColor, radius = 1.6f)
    centeredText(text, label, Point(at.x, y - 0.4f), size = 2.4f, color = Color.White, bold = true)
    shape(PlayerColor) { moveTo(at.x - 1.2f, y + 1.4f); lineTo(at.x + 1.2f, y + 1.4f); lineTo(at.x, y + 3f); close() }
}

/** Dirty plates left on a table: smeared, with crumbs and a crumpled napkin. */
private fun Pen.drawDirtyTable(table: Point) {
    for (side in listOf(-1f, 1f)) {
        val px = table.x + side * 4.2f
        dot(px, table.y, 1.9f, Color(0xFFF4EFE6))
        oval(px + 0.3f, table.y - 0.2f, 1.1f, 0.6f, Color(0x99A0612E)) // smear of sauce
        dot(px - 0.7f, table.y + 0.6f, 0.25f, Color(0xFF8A5A2E))
        dot(px + 0.9f, table.y + 0.8f, 0.2f, Color(0xFF8A5A2E))
        line(px - 1.2f, table.y - 1.6f, px + 1.4f, table.y + 1.2f, Palette.steelDark, 0.3f) // dropped fork
    }
    // Crumpled napkin.
    dot(table.x + 1.2f, table.y + 2.6f, 1.1f, Color.White)
    dot(table.x + 1.9f, table.y + 2.1f, 0.7f, Color(0xFFEDEDED))
    // Crumbs on the cloth.
    for (k in 0..4) dot(table.x - 2f + k * 1.1f, table.y - 3f + (k % 2) * 0.8f, 0.2f, Color(0xFF9A6A3A))
}

/** A stack of dirty plates, as carried in one hand. */
private fun Pen.drawDirtyStack(at: Point) {
    for (k in 0..2) {
        oval(at.x, at.y - k * 0.6f, 2.2f, 1.1f, Color(0xFFF4EFE6))
        ring(at.x, at.y - k * 0.6f, 1.2f, Color(0x33000000), 0.15f)
    }
    oval(at.x + 0.4f, at.y - 1.4f, 0.9f, 0.4f, Color(0x99A0612E))
}

/** The dish station at the right end of the counter: a deep basin, a drying rack, and a sign. */
/** The dish station; anyone washing up there is drawn by [drawWashingUp]. */
@Suppress("UNUSED_PARAMETER")
private fun Pen.drawDishStation(text: TextMeasurer, sign: String, night: ServiceNight, clock: Float) = drawDishStation(text, sign)

/**
 * Someone washing up, facing you: a plate held in front of them in both hands, a yellow sponge
 * scrubbing round on it, and soap bubbles drifting up and popping. Clean plates stack up on the
 * drying rack as the job gets done, and a little ring beside them shows how long is left.
 */
private fun Pen.drawWashingUp(at: Point, progress: Float, clock: Float, sleeve: Color) {
    // The plate, held at chest height and tilting a little as it's scrubbed.
    val tilt = sin(clock * 9f) * 0.6f
    val plate = Point(at.x, at.y + 3.2f + tilt * 0.3f)
    oval(plate.x, plate.y + 0.4f, 3.1f, 1.4f, Color(0x22000000))
    oval(plate.x, plate.y, 3f, 1.3f, Color(0xFFF7FBFF))
    ring(plate.x, plate.y, 1.6f, Color(0x2A2F6188), 0.2f)
    // Arms from the shoulders, bent forward to hold it at the rim.
    for (side in listOf(-1f, 1f)) {
        box(at.x + side * 4.3f - 0.9f, at.y - 0.4f, 1.8f, 3.4f, sleeve, radius = 0.9f)
        line(at.x + side * 4.3f, at.y + 2.6f, plate.x + side * 3.1f, plate.y + 0.1f, sleeve, 1.6f)
        dot(plate.x + side * 3.1f, plate.y + 0.1f, 0.9f, Palette.face)
    }
    // The sponge going round and round on it, with a little foam.
    val sponge = Point(plate.x + kotlin.math.cos(clock * 11f) * 1.2f, plate.y - 0.2f + kotlin.math.sin(clock * 11f) * 0.4f)
    dot(sponge.x - 0.6f, sponge.y - 0.6f, 0.7f, Color.White)
    box(sponge.x - 1f, sponge.y - 0.6f, 2f, 1.2f, Color(0xFFF2C230), radius = 0.4f)
    // Soap bubbles floating up and away, fading as they go.
    for (k in 0..6) {
        val phase = (clock * 0.7f + k * 0.143f) % 1f
        val x = plate.x - 3f + (k % 4) * 2f + sin(clock * 2.5f + k) * 1.2f
        val y = plate.y - 1f - phase * 9f
        val r = 0.7f + 0.8f * phase
        val alpha = (1f - phase).coerceAtMost(0.95f)
        dot(x, y, r, Color(0xFFDDF1FF).copy(alpha = alpha * 0.7f))
        ring(x, y, r, Color(0xFF4F9FD6).copy(alpha = alpha), 0.28f)
        dot(x - r * 0.35f, y - r * 0.35f, r * 0.25f, Color.White.copy(alpha = alpha))
    }
    // Clean plates piling up on the drying rack as the job gets done.
    val done = (progress * 4).toInt()
    for (k in 0 until done) oval(91f, SceneLayout.counter.top + 3.6f - k * 0.9f, 1.9f, 0.7f, Color.White)
    // How long is left: a ring beside them, on the other side from the "YOU" marker.
    val ringAt = Point(at.x + 8f, at.y + 1f)
    dot(ringAt, 2.8f, Color(0xEEFFFFFF))
    ring(ringAt.x, ringAt.y, 2.2f, Color(0x332F6188), 0.7f)
    drawArc(
        Palette.washerBlue,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = p(ringAt.x - 2.2f, ringAt.y - 2.2f),
        size = androidx.compose.ui.geometry.Size(u(4.4f), u(4.4f)),
        style = androidx.compose.ui.graphics.drawscope.Stroke(u(0.7f)),
    )
    for (k in 0..2) dot(ringAt.x - 0.8f + k * 0.8f, ringAt.y + 0.1f, 0.3f, Palette.washerBlue)
}

/** A spill: a puddle with splashes and a shine, plus a wobbling warning so it catches the eye. */
/** What a child dropped: a squashed bread roll, a few peas, crumbs everywhere, and a wet-floor sign all the same. */
private fun Pen.drawDroppedFood(at: Point, seed: Int, clock: Float) {
    oval(at.x - 1f, at.y + 0.2f, 2f, 1.2f, Color(0xFFD9A55B)) // the roll
    oval(at.x - 1.3f, at.y - 0.2f, 1.1f, 0.5f, Color(0xFFE9C083))
    for (k in 0..4) dot(at.x + 1.6f + (k % 3) * 1.1f, at.y - 0.8f + (k / 3) * 1.4f + (k % 2) * 0.3f, 0.45f, Color(0xFF6DAA45)) // peas
    for (k in 0..7) {
        val a = k * 0.8f + seed
        dot(at.x + kotlin.math.cos(a) * (2.6f + k % 3), at.y + kotlin.math.sin(a) * 1.6f, 0.22f, Color(0xFFC08A4A))
    }
    val bob = sin(clock * 4f + seed) * 0.4f
    shape(Color(0xFFF2C230)) {
        moveTo(at.x, at.y - 6.6f + bob)
        lineTo(at.x + 2.2f, at.y - 3f + bob)
        lineTo(at.x - 2.2f, at.y - 3f + bob)
        close()
    }
    box(at.x - 0.25f, at.y - 5.6f + bob, 0.5f, 1.4f, Color(0xFF3A2A10))
    dot(at.x, at.y - 3.7f + bob, 0.3f, Color(0xFF3A2A10))
}

private fun Pen.drawSpill(at: Point, seed: Int, clock: Float, sinceAppeared: Float) {
    val grow = (sinceAppeared / 0.4f).coerceIn(0.2f, 1f)
    val colour = if (seed % 2 == 0) Color(0xCC8B4A22) else Color(0xCC9A2E2E) // gravy, or red wine
    oval(at.x, at.y, 4.2f * grow, 2.4f * grow, colour)
    oval(at.x + 2.6f * grow, at.y + 1f * grow, 1.8f * grow, 1.2f * grow, colour)
    dot(at.x - 3.8f * grow, at.y - 1.6f * grow, 0.7f * grow, colour)
    dot(at.x + 4.6f * grow, at.y - 1.4f * grow, 0.5f * grow, colour)
    oval(at.x - 1.2f, at.y - 0.8f, 1.2f * grow, 0.4f * grow, Color(0x66FFFFFF))
    // A little yellow wet-floor triangle bobbing above it.
    val bob = sin(clock * 4f + seed) * 0.4f
    shape(Color(0xFFF2C230)) {
        moveTo(at.x, at.y - 6.6f + bob)
        lineTo(at.x + 2.2f, at.y - 3f + bob)
        lineTo(at.x - 2.2f, at.y - 3f + bob)
        close()
    }
    box(at.x - 0.25f, at.y - 5.6f + bob, 0.5f, 1.4f, Color(0xFF3A2A10))
    dot(at.x, at.y - 3.7f + bob, 0.3f, Color(0xFF3A2A10))
}

/** The mop, held upright in one hand; it swishes while mopping. */
private fun Pen.drawMop(hand: Point, side: Float, mopping: Boolean, clock: Float) {
    val swish = if (mopping) sin(clock * 14f) * 2f else 0f
    val head = Point(hand.x + side * 1.5f + swish, hand.y + 4.5f)
    line(hand.x + side * 0.4f, hand.y - 7f, head.x, head.y, Palette.woodLight, 0.7f)
    for (k in -2..2) line(head.x, head.y, head.x + k * 0.7f, head.y + 2f, Color(0xFFE8E2D4), 0.5f)
}

private fun Pen.drawProgressRing(center: Point, progress: Float) {
    ring(center.x, center.y, 3f, Color(0x33000000), 0.8f)
    drawArc(
        PlayerColor,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = p(center.x - 3f, center.y - 3f),
        size = androidx.compose.ui.geometry.Size(u(6f), u(6f)),
        style = androidx.compose.ui.graphics.drawscope.Stroke(u(0.8f)),
    )
}

/** Over a party waiting at the door: a bubble with an empty table in it, and a bar of how long they'll wait. */
private fun Pen.drawDoorBubble(at: Point, waited: Float, clock: Float) {
    val c = Point(at.x + 3f, at.y - 13f + sin(clock * 4f) * 0.3f)
    dot(c.x, c.y + 0.3f, 4.2f, Color(0x33000000))
    dot(c.x, c.y, 4f, Color.White)
    shape(Color.White) { moveTo(c.x - 1.2f, c.y + 3.2f); lineTo(c.x + 1.2f, c.y + 3.2f); lineTo(c.x - 1.6f, c.y + 5.4f); close() }
    // A little table with two chairs.
    dot(c.x, c.y, 1.6f, Color(0xFFE9A5A5))
    box(c.x - 3.2f, c.y - 0.8f, 1.2f, 1.6f, Palette.serverRed, radius = 0.3f)
    box(c.x + 2f, c.y - 0.8f, 1.2f, 1.6f, Palette.serverRed, radius = 0.3f)
    // Patience: green, going amber, then red as they're about to walk out.
    val colour = when {
        waited > 0.75f -> Palette.alert
        waited > 0.45f -> Color(0xFFE0A030)
        else -> PlayerColor
    }
    box(c.x - 4f, c.y + 5.6f, 8f, 1f, Color(0x33000000), radius = 0.5f)
    box(c.x - 4f, c.y + 5.6f, 8f * (1f - waited), 1f, colour, radius = 0.5f)
}

/** What the night scene can point at to show a new player where to tap. Tables are 0-based. */
sealed interface NightFocus {
    data class Table(val table: Int) : NightFocus
    data object Chef : NightFocus
    data class Plate(val partyId: Int) : NightFocus
    data object DishStation : NightFocus
    data object Fridge : NightFocus
    data object Chaos : NightFocus
    data object MopBucket : NightFocus
    data class Spill(val messId: Int) : NightFocus
}

/**
 * Someone fixing the fridge: reaching up with a spanner and giving it a few good whacks (with sparks),
 * a little ring beside them filling up to show how long is left.
 */
private fun Pen.drawFixingFridge(at: Point, progress: Float, clock: Float) {
    val swing = sin(clock * 10f)
    val hand = Point(at.x + 3.2f, at.y - 4.4f + swing * 1.2f)
    line(at.x + 2.8f, at.y + 0.2f, hand.x, hand.y, Color(0xFF2B2B2B), 1.4f)
    dot(hand, 0.9f, Palette.face)
    // The spanner: a handle and an open jaw, swinging at the fridge.
    val tip = Point(hand.x + 1.6f, hand.y - 3f - swing * 0.6f)
    line(hand.x, hand.y, tip.x, tip.y, Palette.steelDark, 0.7f)
    ring(tip.x, tip.y, 0.9f, Palette.steelDark, 0.5f)
    if (swing > 0.8f) {
        for (k in 0..3) {
            val a = k * 1.57f + clock * 3f
            line(tip.x + kotlin.math.cos(a) * 1.2f, tip.y + kotlin.math.sin(a) * 1.2f, tip.x + kotlin.math.cos(a) * 2.4f, tip.y + kotlin.math.sin(a) * 2.4f, Color(0xFFF2C230), 0.3f)
        }
    }
    val ringAt = Point(at.x - 9f, at.y - 3f)
    dot(ringAt, 2.8f, Color(0xEEFFFFFF))
    ring(ringAt.x, ringAt.y, 2.2f, Color(0x33000000), 0.7f)
    drawArc(
        Color(0xFFE0A030),
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = p(ringAt.x - 2.2f, ringAt.y - 2.2f),
        size = androidx.compose.ui.geometry.Size(u(4.4f), u(4.4f)),
        style = androidx.compose.ui.graphics.drawscope.Stroke(u(0.7f)),
    )
}

/**
 * Hot food: three wavy squiggles of steam rising off it, wiggling as they go and fading out at the
 * top, the way steam is drawn in cartoons. [seed] keeps plates out of step with each other.
 */
private fun Pen.drawSteam(plate: Point, clock: Float, seed: Int) {
    for (k in 0..2) {
        val cycle = (clock * 0.45f + k * 0.33f + (seed % 5) * 0.17f) % 1f
        val fade = sin(cycle * PI_F) // each squiggle drifts up, fading in then out
        val baseX = plate.x - 1.4f + k * 1.4f
        val baseY = plate.y - 1.2f - cycle * 2.5f
        val height = 4.5f
        val steps = 10
        for (n in 0 until steps) {
            val y0 = baseY - height * n / steps
            val y1 = baseY - height * (n + 1) / steps
            val x0 = baseX + sin(n * 0.9f + clock * 3.5f + k * 2f + seed) * 0.6f
            val x1 = baseX + sin((n + 1) * 0.9f + clock * 3.5f + k * 2f + seed) * 0.6f
            // Strongest in the middle, thinning out at both ends.
            val along = (n + 0.5f) / steps
            val alpha = (fade * sin(along * PI_F)).coerceIn(0f, 1f)
            line(x0, y0, x1, y1, Color(0xFF7D838B).copy(alpha = alpha * 0.35f), 0.75f)
            line(x0, y0, x1, y1, Color.White.copy(alpha = alpha * 0.95f), 0.45f)
        }
    }
}

/**
 * A pair chatting at their table: a little speech bubble that hops between the two of them, with
 * "..." most of the time and the odd laugh. Each table keeps its own rhythm, and goes quiet now and then.
 */
private fun Pen.drawChatter(text: TextMeasurer, table: Point, partyId: Int, clock: Float) {
    val t = clock + partyId * 1.7f
    val turn = (t / 2.6f).toInt()
    val inTurn = (t / 2.6f) % 1f
    if (inTurn > 0.75f || turn % 4 == 3) return // a pause between turns, and a quiet spell every so often
    val left = turn % 2 == 0
    val at = Point(table.x + (if (left) -12f else 12f) + (if (left) -3.4f else 3.4f), table.y - 10.5f)
    val pop = (inTurn / 0.12f).coerceAtMost(1f) // pops in
    val w = 4.6f * pop
    box(at.x - w / 2, at.y - 1.6f * pop, w, 3.2f * pop, Color(0xF2FFFFFF), radius = 1.4f * pop)
    shape(Color(0xF2FFFFFF)) {
        moveTo(at.x - 0.6f, at.y + 1.4f * pop)
        lineTo(at.x + 0.6f, at.y + 1.4f * pop)
        lineTo(at.x + (if (left) 1.6f else -1.6f), at.y + 2.6f * pop)
        close()
    }
    if (pop < 1f) return
    if ((turn + partyId) % 5 == 2) {
        centeredText(text, "ha!", at, size = 2f, color = Palette.ink, bold = true)
    } else {
        // Dots that appear one by one, like someone mid-sentence.
        val shown = 1 + ((inTurn * 6).toInt() % 3)
        for (k in 0 until shown) dot(at.x - 1.2f + k * 1.2f, at.y, 0.38f, Palette.ink)
    }
}

/**
 * The chef while there's cooking: stirring a bowl with a wooden spoon, steam rising off it, and every
 * few seconds a taste from the spoon with a pleased "mm!".
 */
/** A storm cloud over a grumpy chef, or music notes floating up from a happy one. */
internal fun Pen.drawChefMood(text: TextMeasurer, chef: Point, mood: ChefMood, clock: Float) {
    when (mood) {
        ChefMood.GRUMPY -> {
            val c = Point(chef.x + sin(clock * 0.7f) * 0.8f, chef.y - 17f)
            val cloud = Color(0xFF5E6470)
            dot(c.x - 2.2f, c.y + 0.4f, 1.8f, cloud)
            dot(c.x, c.y - 0.6f, 2.3f, cloud)
            dot(c.x + 2.3f, c.y + 0.3f, 1.7f, cloud)
            box(c.x - 3.6f, c.y, 7.4f, 2f, cloud, radius = 1f)
            // Now and then a little flash of lightning.
            if ((clock % 3f) < 0.25f) {
                shape(Palette.gold) {
                    moveTo(c.x + 0.4f, c.y + 1.8f); lineTo(c.x - 0.8f, c.y + 3.8f); lineTo(c.x + 0.2f, c.y + 3.8f)
                    lineTo(c.x - 0.6f, c.y + 5.6f); lineTo(c.x + 1.2f, c.y + 3.2f); lineTo(c.x + 0.2f, c.y + 3.2f); close()
                }
            }
        }
        ChefMood.HAPPY -> {
            for (k in 0..1) {
                val rise = ((clock * 0.4f + k * 0.5f) % 1f)
                val alpha = (1f - rise).coerceIn(0f, 1f)
                centeredText(text, if (k == 0) "♪" else "♫", Point(chef.x + 4f + k * 2f + sin(clock * 2f + k) * 1f, chef.y - 12f - rise * 7f), size = 3f, color = Color(0xFF3E8E41).copy(alpha = alpha), bold = true)
            }
        }
        ChefMood.NORMAL -> {}
    }
}

/** Black smoke pouring off the stove, and a cross "Burnt!", for a couple of seconds after the chef burns something. */
private fun Pen.drawBurnt(text: TextMeasurer, since: Float, clock: Float) {
    if (since !in 0f..BURNT_SHOWS) return
    val fade = 1f - since / BURNT_SHOWS
    val stove = SceneLayout.stove
    for (k in 0..3) {
        val rise = ((clock * 0.9f + k * 0.25f) % 1f)
        dot(stove.center.x - 3f + k * 2f + sin(clock * 3f + k) * 0.8f, stove.top + 4f - rise * 10f, 1.4f + rise * 1.6f, Color(0xFF2B2B2B).copy(alpha = 0.55f * fade * (1f - rise)))
    }
    centeredText(text, "Burnt!", Point(stove.center.x, stove.top - 1f), size = 2.8f, color = Palette.alert.copy(alpha = fade), bold = true)
}

private const val BURNT_SHOWS = 2.5f

/** A little gold star stuck on a chef's special. */
private fun Pen.drawSpecialStar(plate: Point) {
    val c = Point(plate.x - 2.4f, plate.y - 2.2f)
    shape(Palette.gold) {
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) 1.3f else 0.55f
            val a = -kotlin.math.PI.toFloat() / 2 + k * kotlin.math.PI.toFloat() / 5
            val x = c.x + kotlin.math.cos(a) * r
            val y = c.y + kotlin.math.sin(a) * r
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun Pen.drawChefAtWork(text: TextMeasurer, chef: Point, clock: Float, grumpy: Boolean = false) {
    val bowl = Point(chef.x + 5.6f, chef.y + 3.4f)
    oval(bowl.x, bowl.y + 1f, 2.8f, 0.7f, Color(0x33000000))
    shape(Color(0xFFB0573A)) {
        moveTo(bowl.x - 2.8f, bowl.y - 0.6f)
        quadTo(bowl.x, bowl.y + 3f, bowl.x + 2.8f, bowl.y - 0.6f)
        close()
    }
    oval(bowl.x, bowl.y - 0.6f, 2.8f, 0.9f, Color(0xFFD27A4B))
    oval(bowl.x, bowl.y - 0.5f, 2.2f, 0.6f, Color(0xFFE8B04F))
    drawSteam(Point(bowl.x, bowl.y - 0.4f), clock, 3)
    val cycle = clock % 5f
    val tasting = cycle > 4f
    val (handle, end) = if (tasting) {
        // Spoon up to the mouth for a taste.
        Point(chef.x + 2.6f, chef.y - 0.6f) to Point(chef.x + 0.6f, chef.y - 3.6f)
    } else {
        // Round and round in the bowl.
        val a = clock * 7f
        Point(bowl.x + kotlin.math.cos(a) * 1.2f, bowl.y - 0.6f + kotlin.math.sin(a) * 0.35f).let { tip -> Point(tip.x + 2.2f, tip.y - 3.4f) to tip }
    }
    line(chef.x + 3.6f, chef.y + 1.4f, handle.x, handle.y, Palette.chefWhite, 1.2f) // sleeve
    dot(handle, 0.85f, Palette.face)
    line(handle.x, handle.y, end.x, end.y, Color(0xFF9A6A3A), 0.5f)
    oval(end.x, end.y, 0.7f, 0.45f, Color(0xFF9A6A3A))
    if (tasting) centeredText(text, if (grumpy) "ugh." else "mm!", Point(chef.x + 6f, chef.y - 8f), size = 2.2f, color = Palette.ink, bold = true)
}

private const val PI_F = 3.1415927f

/** How far open the front doors are: they swing open as guests come in or go out, and close behind them. */
private fun doorOpenness(night: ServiceNight): Float {
    var open = 0f
    for (party in night.parties) {
        val span = (party.until - party.stageSince).coerceAtLeast(0.01f)
        val progress = (night.time - party.stageSince) / span
        val near = when (party.stage) {
            Stage.WALKING_TO_TABLE -> 1f - progress / 0.3f // just through the door
            Stage.LEAVING_HAPPY, Stage.LEAVING_ANGRY -> (progress - 0.6f) / 0.3f // nearly out
            Stage.QUEUEING -> 1f - (night.time - party.stageSince) / 0.8f // just stepped in
            else -> 0f
        }
        open = maxOf(open, near.coerceIn(0f, 1f))
    }
    return open
}

/** A menu card held open in front of a guest, tilting a little as they read. */
private fun Pen.drawMenuCard(seat: Point, clock: Float) {
    val tilt = sin(clock * 0.9f) * 0.3f
    val c = Point(seat.x, seat.y + 3.2f + tilt)
    box(c.x - 2.4f, c.y - 1.6f, 4.8f, 3.2f, Color(0xFF7A2E2A), radius = 0.4f)
    line(c.x, c.y - 1.5f, c.x, c.y + 1.5f, Color(0xFF4F1C19), 0.25f) // the fold
    for (k in 0..1) {
        line(c.x - 1.9f, c.y - 0.6f + k * 1f, c.x - 0.5f, c.y - 0.6f + k * 1f, Palette.gold, 0.2f)
        line(c.x + 0.5f, c.y - 0.6f + k * 1f, c.x + 1.9f, c.y - 0.6f + k * 1f, Palette.gold, 0.2f)
    }
    for (side in listOf(-1f, 1f)) dot(c.x + side * 2.5f, c.y + 0.6f, 0.75f, Palette.face)
}

/** How long a party has to have waited (as a share of their patience) before they start checking their watch. */
private const val WATCH_FROM = 0.45f

/** A guest lifting their wrist to look at their watch, with a little ticking clock over their head. */
private fun Pen.drawWatchCheck(seat: Point, clock: Float, sleeve: Color) {
    // Forearm raised across the chest, the watch face towards them.
    val wrist = Point(seat.x + 1.6f, seat.y + 0.4f)
    line(seat.x - 3.4f, seat.y + 2.6f, wrist.x, wrist.y, sleeve.copy(red = sleeve.red * 0.82f, green = sleeve.green * 0.82f, blue = sleeve.blue * 0.82f), 1.9f)
    dot(wrist.x + 0.9f, wrist.y - 0.2f, 0.85f, Palette.face)
    dot(wrist.x, wrist.y, 0.75f, Palette.gold)
    dot(wrist.x, wrist.y, 0.5f, Color.White)
    // The clock bubble.
    val c = Point(seat.x + 3.6f, seat.y - 10.5f)
    dot(c, 1.9f, Color.White)
    ring(c.x, c.y, 1.9f, Palette.ink, 0.3f)
    val angle = clock * 6f
    line(c.x, c.y, c.x + kotlin.math.sin(angle) * 1.3f, c.y - kotlin.math.cos(angle) * 1.3f, Palette.alert, 0.3f)
    line(c.x, c.y, c.x, c.y - 0.9f, Palette.ink, 0.3f)
}

/** A phone in a guest's hand, screen glowing, a thumb scrolling. */
private fun Pen.drawPhone(seat: Point, clock: Float) {
    val c = Point(seat.x + 1.4f, seat.y + 3f)
    dot(c.x, c.y, 2.6f, Color(0x226FD3FF)) // glow
    box(c.x - 0.9f, c.y - 1.5f, 1.8f, 3f, Color(0xFF1E1E22), radius = 0.35f)
    box(c.x - 0.7f, c.y - 1.25f, 1.4f, 2.4f, Color(0xFF8FD3F4), radius = 0.2f)
    val scroll = (clock * 0.8f) % 1f
    for (k in 0..2) {
        val y = c.y - 1f + ((k * 0.33f + scroll) % 1f) * 2f
        line(c.x - 0.5f, y, c.x + 0.4f, y, Color(0xFF3B78A8), 0.18f)
    }
    dot(c.x + 0.4f, c.y + 1.2f, 0.65f, Palette.face) // thumb
}

/**
 * A tip jar on the counter, by the chef. When a table pays, a coin arcs over from their table and
 * drops in with a little jiggle, and the jar fills up as the night's takings grow.
 */
private fun Pen.drawTipJar(text: TextMeasurer, night: ServiceNight, clock: Float) {
    val jar = Point(38f, SceneLayout.counter.top + 0.6f)
    // Coins in flight from tables that have just paid.
    var landed = 0f
    val layout = night.layout
    for (party in night.parties) {
        if (party.stage != Stage.LEAVING_HAPPY) continue
        val table = party.table ?: continue
        val t = ((night.time - party.stageSince) / 1.1f).coerceIn(0f, 1f)
        if (t >= 1f) continue
        landed = maxOf(landed, if (t > 0.85f) 1f - (t - 0.85f) / 0.15f else 0f)
        val from = layout.tables[table].toPoint().let { Point(it.x, it.y - 6f) }
        val x = from.x + (jar.x - from.x) * t
        val y = from.y + (jar.y - 3f - from.y) * t - kotlin.math.sin(t * PI_F) * 14f
        dot(x, y, 1.3f, Color(0xFFB8860B))
        dot(x, y, 0.95f, Palette.gold)
        dot(x - 0.35f, y - 0.35f, 0.3f, Color(0xFFFFF3B0))
    }
    val jiggle = sin(clock * 30f) * 0.4f * landed
    val j = Point(jar.x + jiggle, jar.y)
    oval(j.x, j.y + 2.6f, 2.4f, 0.6f, Color(0x33000000))
    // Coins inside, piling up with the takings (full at about 300).
    val fill = (night.takings / 300f).coerceIn(0f, 1f)
    if (fill > 0f) box(j.x - 1.8f, j.y + 2.4f - 4f * fill, 3.6f, 4f * fill, Palette.gold, radius = 0.6f)
    // The glass: a rounded jar with a rim and a shine.
    box(j.x - 2.2f, j.y - 2f, 4.4f, 4.6f, Color(0x55DDEEF7), radius = 1.2f)
    box(j.x - 2.4f, j.y - 2.6f, 4.8f, 1f, Color(0xCCB0BEC5), radius = 0.4f)
    line(j.x - 1.4f, j.y - 1f, j.x - 1.4f, j.y + 1.6f, Color(0x99FFFFFF), 0.3f)
    centeredText(text, "TIPS", Point(j.x, j.y + 0.6f), size = 1.4f, color = Palette.ink, bold = true)
}

/**
 * Where a guest is along [route] at [progress], stepped [side] (-1 left, +1 right) of the path by
 * [apart] (0-1) of a shoulder's width, so a pair walks next to each other instead of on top of each other.
 */
private fun sideBySide(route: List<FloorPoint>, progress: Float, side: Float, apart: Float): SceneLayout.Point {
    val here = ServiceFloor.along(route, progress)
    if (apart <= 0f) return here.toPoint()
    // The way they're heading, from a step ahead (or behind, right at the end).
    val ahead = ServiceFloor.along(route, (progress + 0.02f).coerceAtMost(1f))
    val behind = ServiceFloor.along(route, (progress - 0.02f).coerceAtLeast(0f))
    val dx = ahead.x - behind.x
    val dy = ahead.y - behind.y
    val length = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
    // At right angles to that, so they're shoulder to shoulder.
    val offset = 4.4f * side * apart
    return SceneLayout.Point(here.x + -dy / length * offset, here.y + dx / length * offset)
}

/** A little storm cloud of cross words over a guest storming out. */
private fun Pen.drawGrumble(text: TextMeasurer, at: Point, clock: Float) {
    val shake = sin(clock * 30f) * 0.3f
    val c = Point(at.x + shake, at.y)
    for ((dx, r) in listOf(-2.2f to 2f, 0f to 2.6f, 2.2f to 2f)) dot(c.x + dx, c.y, r, Color(0xFF4A4A52))
    centeredText(text, "#@!", Point(c.x, c.y), size = 2.3f, color = Color(0xFFF2C230), bold = true)
}

/**
 * What makes a special guest stand out: the critic's dark glasses and notepad, the celebrity's shades and
 * a twinkling gold star overhead, the inspector's brown hat and clipboard. Drawn over the guest at [at].
 */
private fun Pen.drawSpecialLook(at: Point, special: com.recipefordisaster.domain.service.SpecialGuest, clock: Float, bob: Float = 0f) {
    val hy = at.y - bob - 4f
    fun glasses(frame: Color, lens: Color) {
        for (side in listOf(-1f, 1f)) oval(at.x + side * 1.3f, hy + 0.2f, 1.05f, 0.75f, lens)
        line(at.x - 0.4f, hy + 0.1f, at.x + 0.4f, hy + 0.1f, frame, 0.3f)
        for (side in listOf(-1f, 1f)) line(at.x + side * 2.3f, hy, at.x + side * 3.2f, hy - 0.4f, frame, 0.25f)
    }
    fun clipboard(paper: Color, board: Color) {
        val c = Point(at.x + 4.4f, at.y + 2.2f)
        box(c.x - 1.4f, c.y - 1.8f, 2.8f, 3.6f, board, radius = 0.3f)
        box(c.x - 1.1f, c.y - 1.3f, 2.2f, 2.8f, paper, radius = 0.2f)
        for (k in 0..2) line(c.x - 0.8f, c.y - 0.7f + k * 0.8f, c.x + 0.8f, c.y - 0.7f + k * 0.8f, Color(0x88000000), 0.15f)
        box(c.x - 0.5f, c.y - 2.1f, 1f, 0.6f, Palette.steelDark, radius = 0.2f)
    }
    when (special) {
        com.recipefordisaster.domain.service.SpecialGuest.CRITIC -> {
            glasses(Color(0xFF111111), Color(0xFF1E1E22))
            clipboard(Color.White, Color(0xFF7A2E2A))
            // A beret, for that critic look.
            oval(at.x - 0.4f, hy - 3f, 3f, 1.2f, Color(0xFF2B2B2B))
            dot(at.x - 0.2f, hy - 4f, 0.35f, Color(0xFF2B2B2B))
        }
        com.recipefordisaster.domain.service.SpecialGuest.CELEBRITY -> {
            glasses(Color(0xFFE5B85C), Color(0xFF6A3E8C))
            // A gold star twinkling over their head.
            val twinkle = 1f + 0.15f * sin(clock * 5f)
            val c = Point(at.x, hy - 7f)
            shape(Palette.gold) {
                for (k in 0 until 10) {
                    val r = (if (k % 2 == 0) 2f else 0.85f) * twinkle
                    val a = -PI_F / 2 + k * PI_F / 5
                    val x = c.x + kotlin.math.cos(a) * r
                    val y = c.y + kotlin.math.sin(a) * r
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            if (sin(clock * 3f) > 0.6f) dot(c.x + 2.4f, c.y - 1.2f, 0.35f, Color.White)
        }
        com.recipefordisaster.domain.service.SpecialGuest.INSPECTOR -> {
            // Brown hat with a band, and a clipboard for the checklist.
            oval(at.x, hy - 2.6f, 4.3f, 1f, Color(0xFF6B4A2E))
            box(at.x - 2.5f, hy - 5.4f, 5f, 2.9f, Color(0xFF7D5A3B), radius = 1f)
            box(at.x - 2.5f, hy - 3.4f, 5f, 0.7f, Color(0xFF3A2A1A), radius = 0.2f)
            clipboard(Color.White, Color(0xFF9A6A3A))
        }
    }
}

/** A scruffy little dog, tail wagging, looking up hopefully for scraps. */
private fun Pen.drawDog(at: Point, clock: Float) {
    val wag = sin(clock * 14f) * 1.4f
    oval(at.x, at.y + 1.6f, 4.4f, 1.1f, Palette.shadow)
    line(at.x + 3.6f, at.y - 0.4f, at.x + 5.6f, at.y - 2.4f + wag, Color(0xFFB07A45), 0.8f) // tail
    oval(at.x, at.y, 4f, 2.2f, Color(0xFFC48A52)) // body
    oval(at.x + 0.6f, at.y + 0.2f, 2f, 1.2f, Color(0xFFE0B482)) // patch
    for (k in listOf(-2.6f, -1f, 1.4f, 2.8f)) box(at.x + k - 0.35f, at.y + 1f, 0.7f, 1.4f, Color(0xFFA8703F), radius = 0.3f) // legs
    dot(at.x - 4.2f, at.y - 1.2f, 1.9f, Color(0xFFC48A52)) // head
    oval(at.x - 4.6f, at.y - 2.8f, 0.7f, 1.2f, Color(0xFF8A5A2E)) // ear
    oval(at.x - 3.2f, at.y - 2.8f, 0.7f, 1.2f, Color(0xFF8A5A2E))
    dot(at.x - 4.8f, at.y - 1.4f, 0.3f, Palette.ink)
    dot(at.x - 3.6f, at.y - 1.4f, 0.3f, Palette.ink)
    dot(at.x - 4.2f, at.y - 0.5f, 0.45f, Color(0xFF2B2B2B)) // nose
}

/** The fuse box on the wall by the door; its little lever down (and a red light) when the power's out. */
private fun Pen.drawFuseBox(out: Boolean) {
    val c = ServiceFloor.fuseBox
    box(c.x - 6f, c.y - 9f, 4.4f, 5.6f, Color(0xFF8D959C), radius = 0.4f)
    box(c.x - 5.4f, c.y - 8.4f, 3.2f, 4.4f, Color(0xFF5E666C), radius = 0.3f)
    val lever = if (out) c.y - 5.6f else c.y - 7.6f
    box(c.x - 4.3f, lever, 1f, 1.6f, Color(0xFFE0E0E0), radius = 0.2f)
    dot(c.x - 2.8f, c.y - 8f, 0.35f, if (out) Palette.alert else Color(0xFF4CAF50))
}

/**
 * The restaurant cat: a brown-grey tabby with dark stripes everywhere, a white chest and chin like a
 * scarf, white paws like mittens and boots, yellow-green eyes and a pink nose. Walking, its legs move
 * and the striped tail sways behind; sitting, it sits up tall with its white front showing.
 * [facingRight] is the way it's heading.
 */
private fun Pen.drawCat(at: Point, walking: Boolean, facingRight: Boolean, clock: Float) {
    val dir = if (facingRight) 1f else -1f
    val fur = Color(0xFF8B7B66)     // brown-grey tabby
    val stripe = Color(0xFF3E342B)  // dark tabby stripes
    val white = Color(0xFFF7F4EE)
    oval(at.x, at.y + 1.6f, 3.6f, 0.9f, Palette.shadow)
    // Long striped tail.
    val swish = sin(clock * (if (walking) 6f else 2.2f)) * 1.2f
    val tailEnd = Point(at.x - dir * (4.4f + swish * 0.4f), at.y - 3.6f + swish * 0.3f)
    shape(fur, stroke = 0.9f) {
        moveTo(at.x - dir * 2.6f, at.y)
        quadTo(at.x - dir * 4.8f, at.y - 0.8f, tailEnd.x, tailEnd.y)
    }
    // Dark rings along it.
    val tailStart = Point(at.x - dir * 2.6f, at.y)
    for (k in 1..3) dot(tailStart.lerp(tailEnd, k / 4f), 0.32f, stripe)
    dot(tailEnd, 0.5f, stripe)
    if (walking) {
        // Four legs stepping, each ending in a white paw.
        val step = sin(clock * 12f) * 0.6f
        for ((k, x) in listOf(-1.8f, -0.8f, 0.9f, 1.9f).withIndex()) {
            val lift = if (k % 2 == 0) step else -step
            box(at.x + dir * x - 0.35f, at.y + 0.4f + lift, 0.7f, 1.2f, fur, radius = 0.3f)
            dot(at.x + dir * x, at.y + 1.6f + lift, 0.42f, white)
        }
        oval(at.x, at.y - 0.2f, 3.1f, 1.4f, fur)
        // Tabby stripes across the back.
        for (k in -2..2) line(at.x + k * 0.95f, at.y - 1.5f, at.x + k * 0.95f + dir * 0.4f, at.y + 0.3f, stripe, 0.32f)
        // White chest showing at the front.
        oval(at.x + dir * 2.2f, at.y + 0.2f, 0.9f, 0.9f, white)
    } else {
        // Sitting up tall: striped sides, a white bib down the front, and white front paws.
        oval(at.x, at.y - 0.4f, 2.5f, 1.9f, fur)
        for (k in -1..1) line(at.x + k * 1.6f - 0.3f, at.y - 1.8f, at.x + k * 1.6f, at.y + 0.6f, stripe, 0.3f)
        shape(white) {
            moveTo(at.x - 1.1f, at.y - 2f)
            quadTo(at.x, at.y + 1.6f, at.x + 1.1f, at.y - 2f)
            close()
        }
        for (side in listOf(-0.7f, 0.7f)) oval(at.x + side, at.y + 1.3f, 0.6f, 0.4f, white)
    }
    // Head: tabby with an "M" on the forehead, white chin and muzzle, big ears.
    val head = Point(at.x + dir * (if (walking) 2.7f else 0f), at.y - (if (walking) 1.3f else 2.7f))
    for (side in listOf(-1f, 1f)) {
        shape(fur) {
            moveTo(head.x + side * 1.5f, head.y - 0.5f)
            lineTo(head.x + side * 1.05f, head.y - 2.6f)
            lineTo(head.x + side * 0.2f, head.y - 1.2f)
            close()
        }
        shape(Color(0xFFD9A79A)) { // pink inside the ears
            moveTo(head.x + side * 1.2f, head.y - 0.9f)
            lineTo(head.x + side * 1.0f, head.y - 2.0f)
            lineTo(head.x + side * 0.5f, head.y - 1.2f)
            close()
        }
    }
    dot(head, 1.6f, fur)
    oval(head.x, head.y + 0.75f, 0.95f, 0.7f, white) // white muzzle and chin
    // The tabby "M" and a couple of cheek stripes.
    line(head.x - 0.7f, head.y - 1.3f, head.x - 0.35f, head.y - 0.7f, stripe, 0.22f)
    line(head.x - 0.35f, head.y - 0.7f, head.x, head.y - 1.2f, stripe, 0.22f)
    line(head.x, head.y - 1.2f, head.x + 0.35f, head.y - 0.7f, stripe, 0.22f)
    line(head.x + 0.35f, head.y - 0.7f, head.x + 0.7f, head.y - 1.3f, stripe, 0.22f)
    for (side in listOf(-1f, 1f)) line(head.x + side * 1.6f, head.y + 0.1f, head.x + side * 1.1f, head.y + 0.2f, stripe, 0.2f)
    // Big yellow-green eyes (with the odd blink), pink nose, whiskers.
    val blink = (clock % 4f) > 3.85f
    for (side in listOf(-1f, 1f)) {
        val e = Point(head.x + side * 0.65f, head.y - 0.15f)
        if (blink) line(e.x - 0.3f, e.y, e.x + 0.3f, e.y, stripe, 0.2f)
        else {
            dot(e, 0.42f, Color(0xFFB7B33A))
            dot(e, 0.16f, Color(0xFF1E1A14))
            dot(e.x - 0.12f, e.y - 0.14f, 0.08f, Color.White)
        }
        line(head.x + side * 0.4f, head.y + 0.7f, head.x + side * 2.3f, head.y + 0.4f, Color(0xBBFFFFFF), 0.1f)
        line(head.x + side * 0.4f, head.y + 0.85f, head.x + side * 2.2f, head.y + 1.0f, Color(0xBBFFFFFF), 0.1f)
    }
    dot(head.x, head.y + 0.45f, 0.22f, Color(0xFFD9867A))
}

/** A plate tumbling off the counter and smashing, with a "crash!". */
private fun Pen.drawFallingPlate(text: TextMeasurer, from: Point, t: Float) {
    if (t < 0.5f) {
        val y = from.y + t / 0.5f * 7f
        dot(from.x, y, 2f, Color.White)
        ring(from.x, y, 2f, Color(0x33000000), 0.2f)
    } else {
        val spread = (t - 0.5f) * 6f
        for (k in 0..5) {
            val a = k * 1.05f
            dot(from.x + kotlin.math.cos(a) * spread, from.y + 7.5f + kotlin.math.sin(a) * spread * 0.4f, 0.6f, Color.White)
        }
        centeredText(text, "crash!", Point(from.x, from.y + 3f - spread), size = 2.4f, color = Palette.alert, bold = true)
    }
}

/** Where a pan fire burns: in the frying pan on the back-left burner, well inside the stove. */
private val FIRE_AT = Point(SceneLayout.stove.left + 5f, SceneLayout.stove.top + 6.5f)

/**
 * Someone dealing with tonight's chaos: spraying the pan fire with an extinguisher, sweeping the rat
 * out with a broom, leading the dog away on a lead, or reaching up to flip the fuse box. A ring beside
 * them fills up to show how long is left.
 */
private fun Pen.drawHandlingChaos(at: Point, kind: ChaosKind, target: Point, progress: Float, clock: Float) {
    val sleeve = Color(0xFF2B2B2B)
    when (kind) {
        ChaosKind.PAN_FIRE -> {
            // A red extinguisher held up, with a jet of white foam arcing up to the pan.
            val hand = Point(at.x - 3.2f, at.y - 2f)
            line(at.x - 3f, at.y + 0.5f, hand.x, hand.y, sleeve, 1.3f)
            box(hand.x - 1.1f, hand.y - 0.8f, 2.2f, 4.2f, Color(0xFFD7322B), radius = 0.8f)
            box(hand.x - 0.5f, hand.y - 1.6f, 1f, 0.9f, Palette.steelDark, radius = 0.2f)
            val nozzle = Point(hand.x, hand.y - 2f)
            val fire = Point(FIRE_AT.x, FIRE_AT.y - 1.5f)
            for (k in 0..9) {
                val t = ((k / 10f) + clock * 2.2f) % 1f
                val x = nozzle.x + (fire.x - nozzle.x) * t + sin(clock * 20f + k) * 0.4f
                val y = nozzle.y + (fire.y - nozzle.y) * t - kotlin.math.sin(t * PI_F) * 4f
                dot(x, y, 0.5f + t * 0.9f, Color.White.copy(alpha = 0.9f - t * 0.3f))
            }
            // Foam settling over the pan.
            for (k in -2..2) dot(FIRE_AT.x + k * 1.1f, FIRE_AT.y - 0.5f, 0.9f * progress, Color.White)
        }
        ChaosKind.RAT -> {
            // A broom swept back and forth towards the rat.
            val swing = sin(clock * 9f)
            val hand = Point(at.x + 3f, at.y + 0.5f)
            line(at.x + 3.2f, at.y - 0.5f, hand.x, hand.y, sleeve, 1.3f)
            val head = Point(hand.x + 4f + swing * 2f, hand.y + 3.5f)
            line(hand.x, hand.y - 3f, head.x, head.y, Palette.woodLight, 0.6f)
            box(head.x - 1.8f, head.y - 0.3f, 3.6f, 1.4f, Color(0xFFD9A821), radius = 0.3f)
            for (k in -3..3) line(head.x + k * 0.5f, head.y + 1f, head.x + k * 0.6f, head.y + 2f, Color(0xFFB8860B), 0.2f)
            // Motion lines.
            for (k in 0..2) line(head.x - 3.6f, head.y - 1f + k, head.x - 2.4f, head.y - 1f + k, Color(0x66000000), 0.2f)
        }
        ChaosKind.DOG -> {
            // A lead from your hand to the dog's collar, and a little wave of "this way".
            val hand = Point(at.x + 3.6f, at.y + 1f)
            line(at.x + 3.2f, at.y - 0.5f, hand.x, hand.y, sleeve, 1.3f)
            dot(hand, 0.9f, Palette.face)
            val collar = Point(target.x - 4.2f, target.y - 0.5f)
            shape(Color(0xFFC0392B), stroke = 0.35f) {
                moveTo(hand.x, hand.y)
                quadTo((hand.x + collar.x) / 2, maxOf(hand.y, collar.y) + 2.5f, collar.x, collar.y)
            }
            dot(collar, 0.6f, Color(0xFFC0392B))
            val wave = sin(clock * 8f) * 1.2f
            line(at.x - 3.2f, at.y - 0.5f, at.x - 4.6f, at.y - 3.6f + wave, sleeve, 1.3f)
            dot(at.x - 4.6f, at.y - 3.6f + wave, 0.9f, Palette.face)
        }
        ChaosKind.POWER_CUT -> {
            // Reaching up to the fuse box and flipping the switch, with a torch glow.
            val reach = Point(ServiceFloor.fuseBox.x - 3.8f, ServiceFloor.fuseBox.y - 6f)
            line(at.x - 3f, at.y - 0.5f, reach.x, reach.y, sleeve, 1.3f)
            dot(reach, 0.9f, Palette.face)
            dot(reach.x, reach.y, 3.5f, Color(0x33FFE27A))
            if (sin(clock * 12f) > 0.3f) for (k in 0..3) {
                val a = k * 1.57f + 0.4f
                line(reach.x + kotlin.math.cos(a) * 1.4f, reach.y + kotlin.math.sin(a) * 1.4f, reach.x + kotlin.math.cos(a) * 2.4f, reach.y + kotlin.math.sin(a) * 2.4f, Color(0xFFFFE27A), 0.25f)
            }
        }
    }
    // How long is left.
    val ringAt = Point(at.x + 8f, at.y + 1f)
    dot(ringAt, 2.8f, Color(0xEEFFFFFF))
    ring(ringAt.x, ringAt.y, 2.2f, Color(0x33000000), 0.7f)
    drawArc(
        Color(0xFFE0A030),
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = p(ringAt.x - 2.2f, ringAt.y - 2.2f),
        size = androidx.compose.ui.geometry.Size(u(4.4f), u(4.4f)),
        style = androidx.compose.ui.graphics.drawscope.Stroke(u(0.7f)),
    )
}

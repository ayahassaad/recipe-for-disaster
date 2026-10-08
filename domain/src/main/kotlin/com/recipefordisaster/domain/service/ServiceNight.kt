package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.EquipmentCatalog
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.equipment.Fridge
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.violates
import com.recipefordisaster.domain.simulation.KitchenModel
import com.recipefordisaster.domain.simulation.RandomSource
import com.recipefordisaster.domain.simulation.ServiceSetup
import com.recipefordisaster.domain.simulation.ServiceSimulator
import com.recipefordisaster.domain.simulation.ServiceSimulator.CustomerServiceOutcome
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import kotlinx.serialization.Serializable

/**
 * One night of service, played live. Guests arrive in parties, get seated
 * at numbered tables, wait to order, wait for food, eat, pay and leave —
 * or give up and storm out if they wait too long. The player is one of the
 * waiters: they take orders at every table, hand the tickets in at the
 * pass, and carry plates back out. Hired servers work as food runners: if
 * a plate has been waiting on the pass for a few seconds, one of them
 * takes it out so the player can keep taking orders.
 *
 * Immutable like the rest of `:domain`: [advance] moves time on and the
 * player's commands ([tapTable], [tapPass]) each return a new night. All
 * randomness (who orders what, party sizes, arrival times) is rolled once
 * in [open], so the night itself only depends on what the player does.
 * When [finished], [result] hands back the same [ServiceSimulator.ServiceResult]
 * the automatic night produces, and the day closes as normal.
 */
@Serializable
data class ServiceNight(
    val time: Float,
    val parties: List<Party>,
    val waiters: List<Waiter>,
    val inventory: InventoryState,
    /** How many dishes the kitchen can cook at once (one per cook). */
    val kitchenSlots: Int,
    /** Seconds per dish, for this kitchen tonight. */
    val secondsPerDish: Float,
    val menu: List<Dish>,
    val kitchenQualityBonus: Int,
    val staffingRatio: Double,
    /** Everyone who came in, in the order they arrived — the order results are reported in. */
    val arrivalOrder: List<Customer>,
    val results: Map<Int, CustomerServiceOutcome> = emptyMap(),
    /** Tables guests have left dirty plates on; nobody can sit there until they're cleared. */
    val dirtyTables: Set<Int> = emptySet(),
    /** When each dirty table was left, so helpers can step in on ones that have sat a while. */
    val dirtiedAt: Map<Int, Float> = emptyMap(),
    /** Spills, rolled when the doors opened: each shows up at its time and stays until someone mops it. */
    val messes: List<Mess> = emptyList(),
    /** Whether a host is on the door tonight: guests wait longer, and one more party fits inside. */
    val hosted: Boolean = false,
    /** How many tables the restaurant has tonight. */
    val tableCount: Int = ServiceFloor.TABLE_COUNT,
    /** For guests who gave up: at which step (the door, waiting to order, or waiting for food). */
    val gaveUpAt: Map<Int, WaitedFor> = emptyMap(),
    /** Whether the restaurant cat is about tonight. */
    val cat: Boolean = false,
    /** Tables the cat has sat by tonight: guests there enjoy it. */
    val catVisited: Set<Int> = emptySet(),
    /** When the cat last knocked a plate off the counter, and whose it was. */
    val catKnockAt: Float = -100f,
    val catKnockTable: Int? = null,
    /** Tonight's chaotic moment, if there is one (rolled when the doors open). */
    val chaos: Chaos? = null,
    /** How tonight's special guests found it, as they leave. */
    val specialVisits: List<SpecialVisit> = emptyList(),
    /** When a worn fridge is going to give up tonight, if it is (rolled when the doors open). */
    val fridgeBreaksAt: Float? = null,
    /** The fridge isn't working: dishes needing cold ingredients can't be made until it's fixed. */
    val fridgeBroken: Boolean = false,
    /** The fridge broke during service tonight (as opposed to being broken already). */
    val fridgeBrokeTonight: Boolean = false,
    /** When the cold food last went off a bit while the fridge was broken. */
    val fridgeLastSpoil: Float = 0f,
) {

    /** What a guest was waiting for when they gave up. */
    @Serializable
    enum class WaitedFor { TABLE, ORDER, FOOD }

    /** A spill on the floor. Guests who finish their meal while it's there notice it. */
    @Serializable
    data class Mess(val id: Int, val at: FloorPoint, val appearsAt: Float, val cleaned: Boolean = false)

    /** Spills on the floor right now. */
    val messesOnFloor: List<Mess> get() = messes.filter { !it.cleaned && it.appearsAt <= time }

    enum class Stage {
        NOT_YET_ARRIVED,
        QUEUEING,
        WALKING_TO_TABLE,
        /** Sat down and reading the menu; ready to order in a few seconds. */
        DECIDING,
        READY_TO_ORDER,
        ORDER_TAKEN,
        IN_KITCHEN,
        COOKING,
        READY_AT_PASS,
        CARRIED,
        EATING,
        LEAVING_HAPPY,
        LEAVING_ANGRY,
        DONE,
    }

    @Serializable
    data class Party(
        val id: Int,
        /** Indices into [arrivalOrder]. */
        val guests: List<Int>,
        /** Each guest's dishes in order of preference, rolled when the night opened. */
        val preferences: List<List<DishId>>,
        val arriveAt: Float,
        /** Seconds this party will wait at each step before giving up. */
        val patience: Float,
        val stage: Stage = Stage.NOT_YET_ARRIVED,
        val stageSince: Float = 0f,
        val table: Int? = null,
        /** For WALKING_TO_TABLE, EATING, COOKING and the LEAVING stages: when that step ends. */
        val until: Float = 0f,
        val seatedAt: Float = 0f,
        val orderedAt: Float = 0f,
        /** What each guest ordered (null: nothing they could have). */
        val orders: List<DishId?> = emptyList(),
        /** Which waiter is holding the ticket or the plates, while they're being carried. */
        val heldBy: String? = null,
        /** What they left as a tip when they paid. */
        val tip: Long = 0,
        /** A critic, celebrity or inspector in the party, if any. */
        val special: SpecialGuest? = null,
    ) {
        val seated: Boolean get() = stage in Stage.DECIDING..Stage.LEAVING_ANGRY
        val occupiesTable: Boolean get() = table != null && stage in Stage.WALKING_TO_TABLE..Stage.LEAVING_ANGRY
    }

    /** Something a waiter can hold in one hand. */
    @Serializable
    sealed interface HandItem {
        /** A party's food (one hand holds a whole table's order). */
        @Serializable
        data class Plate(val partyId: Int) : HandItem

        /** The dirty plates cleared from a table, on their way to the dish station. */
        @Serializable
        data class DirtyDishes(val table: Int) : HandItem

        /** The mop, from the bucket by the door. */
        @Serializable
        data object Mop : HandItem
    }

    /**
     * Who a waiter is: the player, a hired server running food, a hired
     * dishwasher (clears tables, washes up and mops), or a busser (clears
     * tables and washes up, but doesn't mop).
     */
    enum class Kind { PLAYER, RUNNER, DISHWASHER, BUSSER }

    @Serializable
    sealed interface Errand {
        /** Go to a table and do whatever it needs: serve it if you're carrying its food, otherwise take its order. */
        @Serializable
        data class VisitTable(val table: Int) : Errand
        /** Hired help's trip to the pass: hand in any tickets and take whatever has waited too long. */
        @Serializable
        data object VisitPass : Errand

        /** The player hands their orders to the chef (and does nothing else there). */
        @Serializable
        data object HandIn : Errand

        /** The player picks up one particular plate from the counter. */
        @Serializable
        data class PickUp(val partyId: Int) : Errand
        @Serializable
        data class Serve(val table: Int) : Errand

        /** Take dirty dishes to the dish station and wash them. */
        @Serializable
        data object VisitDishStation : Errand

        /** Standing at the dish station washing up. */
        @Serializable
        data object Wash : Errand

        /** Go to the bucket: grab the mop, or put it back if you're holding it. */
        @Serializable
        data object VisitMopBucket : Errand

        /** Go to a spill and mop it up (the player needs the mop in hand; dishwashers bring their own). */
        @Serializable
        data class CleanMess(val messId: Int) : Errand

        /** Standing at a spill mopping it; it's gone when this ends. */
        @Serializable
        data class Mopping(val messId: Int) : Errand
        @Serializable
        data object Rest : Errand

        /** Go and deal with tonight's chaos (chase the rat, put out the fire, lead the dog out, flip the fuses). */
        @Serializable
        data object VisitChaos : Errand

        /** Dealing with it; it's sorted when this ends. */
        @Serializable
        data object HandleChaos : Errand

        /** Go to the fridge to fix it. */
        @Serializable
        data object VisitFridge : Errand

        /** Banging the fridge back into life; it works again when this ends. */
        @Serializable
        data object FixFridge : Errand
    }

    @Serializable
    data class Waiter(
        val id: String,
        val isPlayer: Boolean,
        /** Tables this waiter looks after (0-based). */
        val tables: Set<Int>,
        val speed: Float,
        val restSpot: FloorPoint,
        val route: List<FloorPoint>,
        val routeStart: Float,
        val routeEnd: Float,
        val errand: Errand?,
        /** Party ids whose order this waiter has taken but not yet handed in — notes on a notepad, not in a hand. */
        val tickets: List<Int> = emptyList(),
        /** What's in this waiter's two hands, left then right. */
        val hands: List<HandItem> = emptyList(),
        /** Where the player has asked to go next, after the current errand — taps queue up rather than interrupt. */
        val queue: List<Errand> = emptyList(),
        val kind: Kind = if (isPlayer) Kind.PLAYER else Kind.RUNNER,
    ) {
        val dirtyDishes: List<Int> get() = hands.filterIsInstance<HandItem.DirtyDishes>().map { it.table }
        /** Party ids whose plates this waiter is carrying. */
        val plates: List<Int> get() = hands.filterIsInstance<HandItem.Plate>().map { it.partyId }
        val freeHands: Int get() = (HANDS - hands.size).coerceAtLeast(0)
        val holdingMop: Boolean get() = HandItem.Mop in hands
        fun position(time: Float): FloorPoint = when {
            time >= routeEnd || routeEnd <= routeStart -> route.last()
            else -> ServiceFloor.along(route, (time - routeStart) / (routeEnd - routeStart))
        }
        val walking: Boolean get() = errand != null
    }

    /** Every guest has been and gone. */
    val guestsGone: Boolean get() = parties.all { it.stage == Stage.DONE }

    /** No dirty tables left, and no dirty plates in anyone's hands or in the sink. */
    val tidy: Boolean get() = dirtyTables.isEmpty() && waiters.none { it.dirtyDishes.isNotEmpty() || it.errand == Errand.Wash }

    /**
     * The night is over once the last guest has left and the place is cleared up. If nobody clears
     * up, it closes anyway [TIDY_UP_LIMIT] seconds after the last guest goes, so it can't hang.
     */
    val finished: Boolean get() = guestsGone && (tidy || time - (parties.maxOfOrNull { it.stageSince } ?: 0f) > TIDY_UP_LIMIT)

    /** Plates waiting on the counter, oldest first, as many as fit. */
    val platesOnCounter: List<Party> get() = parties.filter { it.stage == Stage.READY_AT_PASS }.sortedBy { it.stageSince }.take(ServiceFloor.PLATES_ON_COUNTER)

    /** Where tonight's tables are. */
    val layout: TableLayout get() = ServiceFloor.layout(tableCount)

    val player: Waiter get() = waiters.first { it.isPlayer }

    /** Coins taken so far tonight. */
    val takings: Long get() = results.values.sumOf { it.dish?.sellingPrice ?: 0 } + tips

    /** Tips left so far tonight. */
    val tips: Long get() = parties.sumOf { it.tip }

    fun partyAt(table: Int): Party? = parties.firstOrNull { it.table == table && it.occupiesTable }

    /** Parties standing inside the door, waiting for a table. */
    val waitingAtDoor: List<Party> get() = parties.filter { it.stage == Stage.QUEUEING }

    /** Whether there's a clean, empty table for the next party to sit at. */
    val hasFreeTable: Boolean get() = (0 until tableCount).any { t -> t !in dirtyTables && partyAt(t) == null }

    /** How long this party will wait at each step before giving up (longer with a host on the door). */
    fun patienceOf(party: Party): Float = party.patience * (if (hosted) HOST_PATIENCE else 1f)

    /** How close a party is to giving up, 0 (just started waiting) to 1 (about to leave). */
    fun impatience(party: Party): Float = when (party.stage) {
        Stage.QUEUEING -> (time - party.stageSince) / (patienceOf(party) * 0.8f)
        Stage.READY_TO_ORDER -> (time - party.stageSince) / patienceOf(party)
        Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.READY_AT_PASS, Stage.CARRIED -> (time - party.orderedAt) / (patienceOf(party) * FOOD_PATIENCE)
        else -> 0f
    }.coerceIn(0f, 1f)

    // ------------------------------------------------------------ the player's commands

    /** Go to a table: take their order if they're ready, or serve them if you're carrying their food. */
    fun tapTable(table: Int): ServiceNight = enqueue(Errand.VisitTable(table))

    /** Go to the pass: hand in any tickets you're holding and pick up plates that are ready. */
    fun tapPass(): ServiceNight = enqueue(Errand.VisitPass)

    /** Go to the chef and hand in the orders you've taken. */
    fun tapChef(): ServiceNight = enqueue(Errand.HandIn)

    /** Go and pick up one plate from the counter (a party's food that's ready). */
    fun tapPlate(partyId: Int): ServiceNight = enqueue(Errand.PickUp(partyId))

    /** Go to the dish station and wash whatever dirty plates you're carrying. */
    fun tapDishStation(): ServiceNight = enqueue(Errand.VisitDishStation)

    /** Go to the mop bucket: pick the mop up, or put it back. */
    fun tapMopBucket(): ServiceNight = enqueue(Errand.VisitMopBucket)

    /** Go and mop up a spill (you need the mop in hand when you get there). */
    fun tapMess(messId: Int): ServiceNight = enqueue(Errand.CleanMess(messId))

    /** Go and deal with whatever's going wrong right now. */
    fun tapChaos(): ServiceNight = enqueue(Errand.VisitChaos)

    /** The chaos happening right now, if any. */
    val activeChaos: Chaos? get() = chaos?.takeIf { !it.resolved && time >= it.startsAt }

    /** Where the chaos is right now (the rat and the dog move about). */
    fun chaosSpot(): FloorPoint? = activeChaos?.let { c ->
        val t = time - c.startsAt
        when (c.kind) {
            ChaosKind.RAT -> FloorPoint(c.at.x + kotlin.math.sin(t * 1.3f) * 14f, c.at.y + kotlin.math.sin(t * 2.1f) * 5f)
            ChaosKind.DOG -> FloorPoint(c.at.x + kotlin.math.sin(t * 0.6f) * 6f, c.at.y)
            else -> c.at
        }
    }

    /** Someone is on their way to deal with the chaos, or dealing with it. */
    val chaosBeingHandled: Boolean get() = waiters.any { w -> w.errand == Errand.VisitChaos || w.errand == Errand.HandleChaos || Errand.VisitChaos in w.queue }

    /** Go and fix the fridge (only does anything while it's broken). */
    fun tapFridge(): ServiceNight = enqueue(Errand.VisitFridge)

    /** The fridge is about to give up: its light is flickering. */
    val fridgeStruggling: Boolean get() = !fridgeBroken && fridgeBreaksAt != null && time >= fridgeBreaksAt - FRIDGE_WARNING && time < fridgeBreaksAt

    /** Someone is on their way to fix the fridge, or fixing it now. */
    val fridgeBeingFixed: Boolean get() = waiters.any { w -> w.errand == Errand.FixFridge || w.errand == Errand.VisitFridge || Errand.VisitFridge in w.queue }

    /**
     * Taps queue up: if the player is already on their way somewhere, the
     * new stop goes on the end of the list instead of cutting the current
     * errand short. Tapping a stop that's already planned does nothing.
     */
    private fun enqueue(errand: Errand): ServiceNight {
        val me = player
        // An empty, clean table has nothing to do: ignore the tap rather than send the player on a wasted trip.
        if (errand is Errand.VisitTable && partyAt(errand.table) == null && errand.table !in dirtyTables) return this
        // A working fridge doesn't need fixing.
        if (errand == Errand.VisitFridge && !fridgeBroken) return this
        if (errand == Errand.VisitChaos && activeChaos == null) return this
        if (me.errand == null) return send(me.id, errand)
        if (errand == me.errand || errand in me.queue || me.queue.size >= MAX_QUEUED) return this
        return copy(waiters = waiters.map { if (it.isPlayer) it.copy(queue = it.queue + errand) else it })
    }

    private fun partyTable(partyId: Int): Int? = parties.firstOrNull { it.id == partyId }?.table

    private fun send(waiterId: String, errand: Errand): ServiceNight {
        val waiter = waiters.first { it.id == waiterId }
        val here = waiter.position(time)
        val destination = when (errand) {
            is Errand.VisitTable -> layout.stand(errand.table)
            is Errand.Serve -> layout.stand(errand.table)
            Errand.VisitPass -> ServiceFloor.pass
            // Walk right up to the chef to hand orders in, and to the plate itself to pick it up.
            Errand.HandIn -> ServiceFloor.chef
            is Errand.PickUp -> platesOnCounter.indexOfFirst { it.id == errand.partyId }.takeIf { it >= 0 }?.let { ServiceFloor.plateStand(it) } ?: ServiceFloor.pass
            Errand.VisitDishStation -> ServiceFloor.dishStation
            Errand.Wash, is Errand.Mopping, Errand.FixFridge, Errand.HandleChaos -> here
            Errand.VisitChaos -> chaosSpot()?.let { spot ->
                // Fire and fuses are dealt with from in front of the counter / by the wall; rat and dog where they are.
                when (activeChaos?.kind) {
                    ChaosKind.PAN_FIRE -> FloorPoint(40f, ServiceFloor.pass.y)
                    else -> spot
                }
            } ?: here
            Errand.VisitFridge -> ServiceFloor.fridge
            Errand.VisitMopBucket -> ServiceFloor.mopBucket
            is Errand.CleanMess -> messes.firstOrNull { it.id == errand.messId }?.at ?: here
            Errand.Rest -> waiter.restSpot
        }
        val route = ServiceFloor.route(here, destination, layout.aisles)
        val duration = ServiceFloor.length(route) / waiter.speed
        val moved = waiter.copy(route = route, routeStart = time, routeEnd = time + duration, errand = errand)
        return copy(waiters = waiters.map { if (it.id == waiterId) moved else it })
    }

    // ------------------------------------------------------------ time passing

    fun advance(dt: Float): ServiceNight {
        if (finished) return this
        var night = copy(time = time + dt)
        night = night.fridgeStep()
        night = night.chaosStep()
        night = night.catStep()
        night = night.arrivals()
        night = night.seatParties()
        night = night.checkPatience()
        night = night.runKitchen(dt)
        night = night.finishMeals()
        night = night.moveWaiters()
        night = night.directStaff()
        if (night.time > HARD_STOP) night = night.closeUp()
        return night
    }

    /**
     * Where the cat is and what it's doing: it pads between a few favourite spots, sitting at each for a
     * moment, and one of the spots is up on the counter by the plates.
     */
    fun catPose(): CatPose? {
        if (!cat) return null
        val leg = (time / CAT_LEG).toInt()
        val inLeg = (time % CAT_LEG) / CAT_LEG
        val from = CAT_SPOTS[leg % CAT_SPOTS.size]
        val to = CAT_SPOTS[(leg + 1) % CAT_SPOTS.size]
        val walking = inLeg < CAT_WALKING
        val at = if (walking) from.lerp(to, inLeg / CAT_WALKING) else to
        return CatPose(at, walking = walking, onCounter = !walking && to == CAT_COUNTER_SPOT, facingRight = to.x >= from.x)
    }

    /** The cat's comings and goings: making friends at tables, and knocking forgotten plates off the counter. */
    private fun catStep(): ServiceNight {
        val pose = catPose() ?: return this
        var night = this
        if (!pose.walking) {
            // Sitting by a table: whoever's there now enjoys it.
            layout.tables.indices.firstOrNull { t -> layout.stand(t).distanceTo(pose.at) < 12f && partyAt(t) != null }?.let { t ->
                if (t !in catVisited) night = night.copy(catVisited = catVisited + t)
            }
            // Up on the counter next to a plate that's been sitting there a while: whoops.
            if (pose.onCounter && time - catKnockAt > CAT_KNOCK_EVERY) {
                val forgotten = platesOnCounter.firstOrNull { time - it.stageSince > CAT_KNOCK_AFTER }
                if (forgotten != null) {
                    night = night.copy(catKnockAt = time, catKnockTable = forgotten.table)
                        .updateParty(forgotten.id) { it.copy(stage = Stage.IN_KITCHEN, stageSince = time) }
                }
            }
        }
        return night
    }

    /** Chaos that sorts itself out after a while (the lights come back, the rat wanders off). */
    private fun chaosStep(): ServiceNight {
        val c = activeChaos ?: return this
        val limit = c.lastsAtMost ?: return this
        return if (time - c.startsAt >= limit) copy(chaos = c.copy(resolved = true)) else this
    }

    /** A worn fridge gives up at its time; while it's broken, the cold food slowly goes off. */
    private fun fridgeStep(): ServiceNight {
        var night = this
        if (!fridgeBroken && fridgeBreaksAt != null && time >= fridgeBreaksAt && !fridgeBrokeTonight) {
            night = night.copy(fridgeBroken = true, fridgeBrokeTonight = true, fridgeLastSpoil = time)
        }
        if (night.fridgeBroken && time - night.fridgeLastSpoil >= FRIDGE_SPOIL_EVERY) {
            val spoiled = night.inventory.ingredients.mapValues { (_, ingredient) ->
                if (Fridge.isCold(ingredient)) ingredient.copy(quantityOnHand = ingredient.quantityOnHand * (1 - FRIDGE_SPOIL)) else ingredient
            }
            night = night.copy(inventory = night.inventory.copy(ingredients = spoiled), fridgeLastSpoil = time)
        }
        return night
    }

    private fun updateParty(id: Int, transform: (Party) -> Party) = copy(parties = parties.map { if (it.id == id) transform(it) else it })

    private fun arrivals(): ServiceNight {
        var night = this
        for (party in parties.filter { it.stage == Stage.NOT_YET_ARRIVED && it.arriveAt <= time }.sortedBy { it.arriveAt }) {
            // No more than a couple of parties crowd the door. Others hold off — and if they'd have to
            // hold off too long, they go somewhere else tonight.
            if (night.parties.count { it.stage == Stage.QUEUEING } >= MAX_QUEUE + (if (hosted) 1 else 0)) {
                if (time - party.arriveAt > GIVE_UP_COMING) night = night.stayAway(party)
                continue
            }
            // Anyone with nothing on the menu they can eat or afford reads it at the door and leaves —
            // and so does anyone whose dishes have all sold out (the kitchen has run out of ingredients).
            // (A broken fridge doesn't send people away at the door: it might be fixed by the time they order.)
            val (staying, leaving) = party.guests.zip(party.preferences).partition { (_, prefs) -> prefs.any { night.canMake(it, night.inventory, fridgeMatters = false) } }
            var results = night.results
            leaving.forEach { (guest, prefs) ->
                results = results + (guest to missed(guest, if (prefs.isEmpty()) MissedMealReason.NOTHING_SUITABLE else MissedMealReason.OUT_OF_STOCK))
            }
            night = night.copy(results = results).updateParty(party.id) {
                if (staying.isEmpty()) {
                    it.copy(stage = Stage.DONE, stageSince = time)
                } else {
                    it.copy(guests = staying.map { g -> g.first }, preferences = staying.map { g -> g.second }, stage = Stage.QUEUEING, stageSince = time)
                }
            }
        }
        return night
    }

    /** A party that couldn't even get through the door: they never came in, so they count as having given up. */
    private fun stayAway(party: Party): ServiceNight {
        var results = results
        val gaveUpAt = gaveUpAt + party.guests.filter { it !in results }.associateWith { WaitedFor.TABLE }
        party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, MissedMealReason.TIRED_OF_WAITING)) }
        return copy(results = results, gaveUpAt = gaveUpAt).updateParty(party.id) { it.copy(stage = Stage.DONE, stageSince = time) }
    }

    /** How many guests gave up at each step tonight. */
    fun gaveUpCounts(): Map<WaitedFor, Int> = gaveUpAt.values.groupingBy { it }.eachCount()

    private fun seatParties(): ServiceNight {
        var night = this
        val queue = night.parties.filter { it.stage == Stage.QUEUEING }.sortedBy { it.arriveAt }
        for (party in queue) {
            val free = (0 until night.tableCount).firstOrNull { t ->
                t !in night.dirtyTables && night.parties.none { it.table == t && it.occupiesTable }
            } ?: break
            val walk = ServiceFloor.length(ServiceFloor.route(ServiceFloor.door, night.layout.stand(free), night.layout.aisles)) / GUEST_SPEED
            night = night.updateParty(party.id) { it.copy(stage = Stage.WALKING_TO_TABLE, stageSince = time, table = free, until = time + walk) }
        }
        // Parties that have reached their table are ready to order.
        // Parties that have reached their table read the menu for a few seconds first...
        night.parties.filter { it.stage == Stage.WALKING_TO_TABLE && time >= it.until }.forEach { party ->
            val reading = DECIDE_MIN + (party.id * 0.618f % 1f) * (DECIDE_MAX - DECIDE_MIN)
            night = night.updateParty(party.id) { it.copy(stage = Stage.DECIDING, stageSince = time, seatedAt = time, until = time + reading) }
        }
        // ...and then they're ready to order.
        night.parties.filter { it.stage == Stage.DECIDING && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = Stage.READY_TO_ORDER, stageSince = time) }
        }
        return night
    }

    private fun checkPatience(): ServiceNight {
        var night = this
        for (party in parties) {
            // A host chatting to people, handing out menus and topping up water buys you time.
            val givesUp = when (party.stage) {
                Stage.QUEUEING, Stage.READY_TO_ORDER, Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.READY_AT_PASS, Stage.CARRIED ->
                    impatience(party) >= 1f
                else -> false
            }
            if (givesUp) {
                val waitedFor = when (party.stage) {
                    Stage.QUEUEING -> WaitedFor.TABLE
                    Stage.READY_TO_ORDER -> WaitedFor.ORDER
                    else -> WaitedFor.FOOD
                }
                night = night.copy(gaveUpAt = night.gaveUpAt + party.guests.filter { it !in night.results }.associateWith { waitedFor })
                    .stormOut(party.id, MissedMealReason.TIRED_OF_WAITING)
            }
        }
        return night
    }

    /** A party gives up: everyone without a result leaves hungry, and any ticket or plate of theirs disappears. */
    private fun stormOut(partyId: Int, reason: MissedMealReason): ServiceNight {
        val party = parties.first { it.id == partyId }
        var results = results
        val specialVisits = specialVisits + listOfNotNull(party.special?.let { SpecialVisit(it, pleased = false) })
        party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, reason)) }
        val inLine = party.stage == Stage.QUEUEING
        return copy(
            specialVisits = specialVisits,
            results = results,
            waiters = waiters.map { it.copy(tickets = it.tickets - partyId, hands = it.hands - HandItem.Plate(partyId)) },
        ).updateParty(partyId) {
            if (inLine) it.copy(stage = Stage.DONE, stageSince = time) else it.copy(stage = Stage.LEAVING_ANGRY, stageSince = time, until = time + LEAVE, heldBy = null)
        }
    }

    /**
     * Whether the kitchen could cook this dish from [stock]: it's on tonight's menu, the ingredients are
     * there, and (while the fridge is broken) it doesn't need anything cold.
     */
    private fun canMake(dishId: DishId, stock: InventoryState, fridgeMatters: Boolean = true): Boolean {
        val dish = menu.firstOrNull { it.id == dishId } ?: return false
        if (fridgeMatters && fridgeBroken && needsCold(dish)) return false
        return dish.available && InventoryOperations.canFulfill(stock, dish.recipe)
    }

    /** Whether a dish uses anything that has to be kept in the fridge. */
    fun needsCold(dish: Dish): Boolean = dish.recipe.ingredientRequirements.keys.any { id -> inventory.ingredients[id]?.let { Fridge.isCold(it) } == true }

    /** Whether anything on the menu can still be cooked tonight (a broken fridge can be fixed, so it doesn't count). */
    val kitchenHasFood: Boolean get() = menu.any { canMake(it.id, inventory, fridgeMatters = false) }

    private fun runKitchen(dt: Float): ServiceNight {
        var night = this
        // A pan fire or a power cut stops the kitchen: whatever's cooking just waits.
        if (night.activeChaos?.stopsKitchen == true) {
            return night.copy(parties = night.parties.map { if (it.stage == Stage.COOKING) it.copy(until = it.until + dt) else it })
        }
        // Finished cooking: plates go up on the pass.
        night.parties.filter { it.stage == Stage.COOKING && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = Stage.READY_AT_PASS, stageSince = time) }
        }
        // Free cooks pick up the oldest tickets.
        val busy = night.parties.count { it.stage == Stage.COOKING }
        val waiting = night.parties.filter { it.stage == Stage.IN_KITCHEN }.sortedBy { it.stageSince }
        for (party in waiting.take((night.kitchenSlots - busy).coerceAtLeast(0))) {
            // The ingredients were set aside when the order was taken, so whatever was ordered gets cooked.
            val cooked = party.orders
            night = if (cooked.all { it == null }) {
                night.updateParty(party.id) { it.copy(stage = Stage.LEAVING_ANGRY, stageSince = time, until = time + LEAVE) }
            } else {
                val cookTime = night.secondsPerDish * cooked.count { it != null } + COOK_BASE
                night.updateParty(party.id) { it.copy(stage = Stage.COOKING, stageSince = time, until = time + cookTime, orders = cooked) }
            }
        }
        return night
    }

    private fun finishMeals(): ServiceNight {
        var night = this
        for (party in parties) {
            when {
                party.stage == Stage.EATING && time >= party.until -> {
                    // They pay; how happy they are depends on the food, the price, and how long it all took.
                    var results = night.results
                    val waitedMinutes = ((time - EAT - party.seatedAt - EXPECTED_SERVICE).coerceAtLeast(0f) * MINUTES_PER_SECOND).toDouble()
                    party.guests.zip(party.orders).forEach { (guest, dishId) ->
                        if (guest in results) return@forEach
                        val dish = night.menu.first { it.id == dishId }
                        val customer = arrivalOrder[guest]
                        // A dirty floor puts people off, however good the food was.
                    // A rat or a dog about puts people off as much as a spill does.
                    val bother = if (night.activeChaos?.botherGuests == true) 1 else 0
                    val mess = (night.messesOnFloor.size + bother).coerceAtMost(MAX_MESS_PENALTIES) * MESS_PENALTY -
                        // ...while a visit from the cat makes their evening.
                        (if (party.table in night.catVisited) CAT_JOY else 0)
                    val satisfaction = (ServiceSimulator.resolveSatisfaction(customer, dish, waitedMinutes, kitchenQualityBonus) - mess).coerceIn(0, 100)
                        results = results + (guest to CustomerServiceOutcome(customer, dish, satisfaction, waitedMinutes))
                    }
                    // They leave their dirty plates behind; the table needs clearing before anyone else can sit there.
                    night = night.copy(
                        results = results,
                        dirtyTables = night.dirtyTables + listOfNotNull(party.table),
                        dirtiedAt = night.dirtiedAt + listOfNotNull(party.table?.let { it to time }),
                    )
                        .updateParty(party.id) { it.copy(stage = Stage.LEAVING_HAPPY, stageSince = time, until = time + LEAVE, tip = tipFor(party, results)) }
                    party.special?.let { special ->
                        // The inspector judges the room, not the meal: any spill or other dirty table counts against you.
                        val pleased = when (special) {
                            SpecialGuest.INSPECTOR -> night.messesOnFloor.isEmpty() && night.dirtyTables.none { it != party.table }
                            else -> party.guests.all { (results[it]?.satisfaction ?: 0) >= SPECIAL_PLEASED }
                        }
                        night = night.copy(specialVisits = night.specialVisits + SpecialVisit(special, pleased))
                    }
                }
                (party.stage == Stage.LEAVING_HAPPY || party.stage == Stage.LEAVING_ANGRY) && time >= party.until ->
                    night = night.updateParty(party.id) { it.copy(stage = Stage.DONE, stageSince = time) }
            }
        }
        return night
    }

    private fun moveWaiters(): ServiceNight {
        var night = this
        for (waiter in waiters) {
            if (waiter.errand == null || time < waiter.routeEnd) continue
            night = night.arrive(waiter.id)
            // Then on to the next stop the player queued up, if any (unless they've just started washing up).
            val after = night.waiters.first { it.id == waiter.id }
            val next = after.queue.firstOrNull()
            if (next != null && after.errand == null) {
                night = night.copy(waiters = night.waiters.map { if (it.id == waiter.id) it.copy(queue = it.queue.drop(1)) else it })
                    .send(waiter.id, next)
            }
        }
        return night
    }

    /** A waiter has reached where they were going: do the thing they went there for. */
    private fun arrive(waiterId: String): ServiceNight {
        val waiter = waiters.first { it.id == waiterId }
        var night = copy(waiters = waiters.map { if (it.id == waiterId) it.copy(errand = null) else it })
        fun updateWaiter(transform: (Waiter) -> Waiter) {
            night = night.copy(waiters = night.waiters.map { if (it.id == waiterId) transform(it) else it })
        }
        when (val errand = waiter.errand) {
            is Errand.VisitTable -> {
                val party = night.partyAt(errand.table)
                if (party != null && (party.stage == Stage.WALKING_TO_TABLE || party.stage == Stage.DECIDING) && waiter.kind != Kind.DISHWASHER && waiter.kind != Kind.BUSSER) {
                    // They're still sitting down or deciding: wait at the table and take their order as soon as they're ready.
                    updateWaiter { it.copy(route = listOf(it.position(time)), routeStart = time, routeEnd = party.until + 0.01f, errand = errand) }
                } else if (party != null && party.id in waiter.plates) {
                    night = night.updateParty(party.id) { it.copy(stage = Stage.EATING, stageSince = time, until = time + EAT, heldBy = null) }
                    updateWaiter { it.copy(hands = it.hands - HandItem.Plate(party.id)) }
                } else if (party == null && errand.table in night.dirtyTables && waiter.freeHands > 0) {
                    // Clear the table: its dirty plates go in one hand.
                    night = night.copy(dirtyTables = night.dirtyTables - errand.table, dirtiedAt = night.dirtiedAt - errand.table)
                    updateWaiter { it.copy(hands = it.hands + HandItem.DirtyDishes(errand.table)) }
                } else if ((waiter.kind == Kind.PLAYER || waiter.kind == Kind.RUNNER) && party != null && party.stage == Stage.READY_TO_ORDER && errand.table in waiter.tables) {
                    // Each guest has the first thing they like that the kitchen can still make, and its
                    // ingredients are set aside now — so nobody is told later that their food has run out.
                    var stock = night.inventory
                    val orders = party.preferences.map { prefs ->
                        prefs.firstOrNull { night.canMake(it, stock) }?.also { id -> stock = InventoryOperations.consume(stock, night.menu.first { it.id == id }.recipe) }
                    }
                    // With the fridge broken, anyone who'd want something cold can't order yet: they wait for it to be fixed.
                    val waitingForFridge = night.fridgeBroken && party.preferences.zip(orders).any { (prefs, order) ->
                        order == null && prefs.any { night.canMake(it, night.inventory, fridgeMatters = false) }
                    }
                    if (waitingForFridge) return night
                    var results = night.results
                    party.guests.zip(orders).forEach { (guest, dish) -> if (dish == null && guest !in results) results = results + (guest to missed(guest, MissedMealReason.OUT_OF_STOCK)) }
                    night = night.copy(inventory = stock, results = results)
                    night = if (orders.all { it == null }) {
                        // Everything they wanted sold out while they sat down; they leave disappointed.
                        night.updateParty(party.id) { it.copy(stage = Stage.LEAVING_ANGRY, stageSince = time, until = time + LEAVE) }
                    } else {
                        updateWaiter { it.copy(tickets = it.tickets + party.id) }
                        night.updateParty(party.id) { it.copy(stage = Stage.ORDER_TAKEN, stageSince = time, orderedAt = time, orders = orders, heldBy = waiterId) }
                    }
                }
            }
            is Errand.Serve -> {
                val party = night.partyAt(errand.table)
                if (party != null && party.id in waiter.plates) {
                    night = night.updateParty(party.id) { it.copy(stage = Stage.EATING, stageSince = time, until = time + EAT, heldBy = null) }
                    updateWaiter { it.copy(hands = it.hands - HandItem.Plate(party.id)) }
                }
            }
            Errand.VisitPass -> {
                // Hand in tickets…
                waiter.tickets.forEach { id -> night = night.updateParty(id) { it.copy(stage = Stage.IN_KITCHEN, stageSince = time, heldBy = null) } }
                updateWaiter { it.copy(tickets = emptyList()) }
                // …and pick up whatever's ready, as much as two hands can carry. Runners only take plates
                // the player has left waiting a while (and hasn't said they're coming for), so they help
                // out rather than race the player.
                val claimed = night.player.let { listOfNotNull(it.errand) + it.queue }.filterIsInstance<Errand.PickUp>().map { it.partyId }.toSet()
                val ready = night.parties
                    .filter { it.stage == Stage.READY_AT_PASS && (waiter.isPlayer || (time - it.stageSince >= RUNNER_DELAY && it.id !in claimed)) }
                    .sortedBy { it.stageSince }
                    .take(waiter.freeHands)
                ready.forEach { party -> night = night.updateParty(party.id) { it.copy(stage = Stage.CARRIED, stageSince = time, heldBy = waiterId) } }
                updateWaiter { it.copy(hands = it.hands + ready.map { p -> HandItem.Plate(p.id) }) }
            }
            Errand.HandIn -> {
                waiter.tickets.forEach { id -> night = night.updateParty(id) { it.copy(stage = Stage.IN_KITCHEN, stageSince = time, heldBy = null) } }
                updateWaiter { it.copy(tickets = emptyList()) }
            }
            is Errand.PickUp -> {
                val party = night.parties.firstOrNull { it.id == errand.partyId }
                if (party != null && party.stage == Stage.READY_AT_PASS && waiter.freeHands > 0) {
                    night = night.updateParty(party.id) { it.copy(stage = Stage.CARRIED, stageSince = time, heldBy = waiterId) }
                    updateWaiter { it.copy(hands = it.hands + HandItem.Plate(party.id)) }
                }
            }
            Errand.VisitDishStation -> {
                val stacks = waiter.dirtyDishes.size
                if (stacks > 0) {
                    // Washing up takes a moment per table's worth, standing at the station.
                    updateWaiter {
                        val here = it.position(time)
                        it.copy(hands = it.hands.filterNot { item -> item is HandItem.DirtyDishes }, route = listOf(here), routeStart = time, routeEnd = time + WASH * stacks, errand = Errand.Wash)
                    }
                }
            }
            Errand.VisitMopBucket -> {
                if (waiter.holdingMop) {
                    updateWaiter { it.copy(hands = it.hands - HandItem.Mop) }
                } else if (waiter.freeHands > 0) {
                    updateWaiter { it.copy(hands = it.hands + HandItem.Mop) }
                }
            }
            is Errand.CleanMess -> {
                val mess = night.messesOnFloor.firstOrNull { it.id == errand.messId }
                val canMop = waiter.holdingMop || waiter.kind == Kind.DISHWASHER
                val alreadyOnIt = night.waiters.any { it.id != waiterId && it.errand == Errand.Mopping(errand.messId) }
                if (mess != null && canMop && !alreadyOnIt) {
                    updateWaiter {
                        val here = it.position(time)
                        it.copy(route = listOf(here), routeStart = time, routeEnd = time + MOP, errand = Errand.Mopping(mess.id))
                    }
                }
            }
            is Errand.Mopping -> night = night.copy(messes = night.messes.map { if (it.id == errand.messId) it.copy(cleaned = true) else it })
            Errand.VisitFridge -> {
                val someoneOnIt = night.waiters.any { it.id != waiterId && it.errand == Errand.FixFridge }
                if (night.fridgeBroken && !someoneOnIt) {
                    updateWaiter { it.copy(route = listOf(it.position(time)), routeStart = time, routeEnd = time + FIX_FRIDGE, errand = Errand.FixFridge) }
                }
            }
            // Patched up: it works for the rest of the night (a proper repair is a morning job).
            Errand.FixFridge -> night = night.copy(fridgeBroken = false)
            Errand.VisitChaos -> {
                if (night.activeChaos != null && night.waiters.none { it.id != waiterId && it.errand == Errand.HandleChaos }) {
                    updateWaiter { it.copy(route = listOf(it.position(time)), routeStart = time, routeEnd = time + HANDLE_CHAOS, errand = Errand.HandleChaos) }
                }
            }
            Errand.HandleChaos -> night = night.copy(chaos = night.chaos?.copy(resolved = true))
            Errand.Wash, Errand.Rest, null -> {}
        }
        return night
    }

    /**
     * Hired staff, whenever they're free. Food runners deliver what they're
     * carrying or fetch plates that have waited too long; dishwashers clear
     * dirty tables and wash up.
     */
    private fun directStaff(): ServiceNight {
        var night = this
        for (waiter in waiters.filter { !it.isPlayer && it.errand == null }) {
            val next: Errand? = when (waiter.kind) {
                Kind.DISHWASHER, Kind.BUSSER -> {
                    // A table (or a spill) nobody else is already on their way to.
                    val plans = night.waiters.flatMap { listOfNotNull(it.errand) + it.queue }
                    val claimed = plans.filterIsInstance<Errand.VisitTable>().map { it.table }.toSet()
                    val claimedMesses = plans.mapNotNull { (it as? Errand.CleanMess)?.messId ?: (it as? Errand.Mopping)?.messId }.toSet()
                    val table = night.dirtyTables.firstOrNull { it !in claimed }
                    // Only dishwashers bring a mop; bussers stick to tables and dishes.
                    val mess = if (waiter.kind == Kind.DISHWASHER) night.messesOnFloor.firstOrNull { it.id !in claimedMesses } else null
                    when {
                        // A dishwasher sees to a broken fridge before anything else.
                        waiter.kind == Kind.DISHWASHER && night.fridgeBroken && !night.fridgeBeingFixed -> Errand.VisitFridge
                        waiter.dirtyDishes.isNotEmpty() && (waiter.freeHands == 0 || table == null) -> Errand.VisitDishStation
                        mess != null -> Errand.CleanMess(mess.id)
                        table != null && waiter.freeHands > 0 -> Errand.VisitTable(table)
                        else -> null
                    }
                }
                else -> {
                    // Food first; when there's none to run, servers clear tables that have sat dirty a while.
                    val claimed = night.waiters.flatMap { listOfNotNull(it.errand) + it.queue }.filterIsInstance<Errand.VisitTable>().map { it.table }.toSet()
                    val stale = night.dirtyTables.firstOrNull { it !in claimed && time - (night.dirtiedAt[it] ?: time) >= RUNNER_CLEAR_DELAY }
                    when {
                        waiter.plates.isNotEmpty() -> night.parties.first { it.id == waiter.plates.first() }.table?.let { Errand.Serve(it) }
                        night.parties.any { it.stage == Stage.READY_AT_PASS && time - it.stageSince >= RUNNER_DELAY } && waiter.freeHands > 0 -> Errand.VisitPass
                        waiter.dirtyDishes.isNotEmpty() -> Errand.VisitDishStation
                        stale != null && waiter.freeHands > 0 -> Errand.VisitTable(stale)
                        else -> null
                    }
                }
            }
            val here = waiter.position(time)
            night = when {
                next != null -> night.send(waiter.id, next)
                here.distanceTo(waiter.restSpot) > 0.5f -> night.send(waiter.id, Errand.Rest)
                else -> night
            }
        }
        return night
    }

    /** Closing time: anyone still waiting gives up. */
    private fun closeUp(): ServiceNight {
        var night = this
        parties.filter { it.stage != Stage.DONE }.forEach { party ->
            var results = night.results
            party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, MissedMealReason.TIRED_OF_WAITING)) }
            night = night.copy(results = results).updateParty(party.id) { it.copy(stage = Stage.DONE, stageSince = time) }
        }
        return night
    }

    /**
     * Quick service earns a tip: from sitting down to the food arriving, the faster the better,
     * for each guest who enjoyed it. Nothing for a slow or a disappointing meal.
     */
    private fun tipFor(party: Party, results: Map<Int, CustomerServiceOutcome>): Long {
        val served = time - EAT - party.seatedAt
        val perGuest = when {
            served <= TIP_FAST -> 3L
            served <= TIP_OK -> 1L
            else -> 0L
        }
        return party.guests.sumOf { guest -> if ((results[guest]?.satisfaction ?: 0) >= 55) perGuest else 0L }
    }

    private fun missed(guest: Int, reason: MissedMealReason) =
        CustomerServiceOutcome(arrivalOrder[guest], dish = null, satisfaction = ServiceSimulator.missedMealSatisfaction(reason), waitMinutes = 0.0, missedReason = reason)

    /** The night's results in the same shape the automatic night produces, so the day closes the same way. */
    fun result(): ServiceSimulator.ServiceResult {
        val outcomes = arrivalOrder.indices.map { results[it] ?: missed(it, MissedMealReason.TIRED_OF_WAITING) }
        val sold = outcomes.mapNotNull { it.dish?.id }.groupingBy { it }.eachCount()
        return ServiceSimulator.ServiceResult(
            specialVisits = specialVisits,
            outcomes = outcomes,
            dishesSold = sold,
            staffingRatio = staffingRatio,
            inventoryAfter = inventory,
            fridgeBrokeTonight = fridgeBrokeTonight,
            fridgeLeftBroken = fridgeBroken,
            tips = tips,
        )
    }

    companion object {
        const val PLAYER_ID = "you"
        /** Everyone has two hands: two plates, or a plate and a stack of dirty dishes, and so on. */
        const val HANDS = 2

        /** Seconds between parties arriving, plus up to [ARRIVAL_JITTER] more so it doesn't feel like clockwork. */
        private const val ARRIVAL_GAP = 6f
        private const val ARRIVAL_JITTER = 3f

        /** Parties that can wait by the door at once; more than that hold off coming in. */
        private const val MAX_QUEUE = 2

        /** How much longer guests will wait with a host looking after them. */
        private const val HOST_PATIENCE = 1.4f

        /** How long a party will hold off coming in before going somewhere else. */
        private const val GIVE_UP_COMING = 15f

        /** How many stops the player can line up ahead. */
        const val MAX_QUEUED = 5

        /** How long a plate sits on the pass before a runner takes it out instead of waiting for the player. */
        private const val RUNNER_DELAY = 3f

        /** The cat: comes from this day, walks between spots (each leg this long, the first part walking). */
        private const val CAT_FROM_DAY = 3
        private const val CAT_LEG = 9f
        private const val CAT_WALKING = 0.5f
        private const val CAT_JOY = 6
        /** A plate left on the counter this long tempts the cat; it won't knock more than one this often. */
        private const val CAT_KNOCK_AFTER = 7f
        private const val CAT_KNOCK_EVERY = 25f
        private val CAT_COUNTER_SPOT = FloorPoint(78f, 39.5f)
        private val CAT_SPOTS = listOf(
            FloorPoint(14f, 80f), FloorPoint(40f, 108f), FloorPoint(86f, 80f), CAT_COUNTER_SPOT,
            FloorPoint(62f, 110f), FloorPoint(14f, 132f), FloorPoint(86f, 132f), FloorPoint(38f, 80f),
        )

        /** Seconds to chase out the rat, put out the fire, lead the dog out or flip the fuses. */
        private const val HANDLE_CHAOS = 3f

        /** Chaos can happen from this day on, on about this share of nights. */
        private const val CHAOS_FROM_DAY = 4
        private const val CHAOS_CHANCE = 0.45f

        /** A special guest counts as pleased at this satisfaction or more. */
        private const val SPECIAL_PLEASED = 65

        /** Special guests start turning up from this day, on about this share of nights. */
        private const val SPECIALS_FROM_DAY = 3
        private const val SPECIAL_CHANCE = 0.4f

        /** Food on the table within this many seconds of sitting down earns the full tip; within [TIP_OK], a small one. */
        private const val TIP_FAST = 24f
        private const val TIP_OK = 34f

        /** How long guests read the menu after sitting down before they're ready to order. */
        private const val DECIDE_MIN = 2.5f
        private const val DECIDE_MAX = 4.5f

        /** Guests' patience on the very first night, while the player learns. */
        private const val FIRST_NIGHT_PATIENCE = 1.3f

        /** No spills for the first few nights: there's enough to learn already. */
        private const val QUIET_FLOOR_DAYS = 2

        /** How long a table sits dirty before a server with nothing else to do clears it. */
        private const val RUNNER_CLEAR_DELAY = 6f
        private const val GUEST_SPEED = 32f
        private const val PLAYER_SPEED = 60f
        private const val STAFF_SPEED = 42f
        private const val EAT = 6f

        /** Seconds to wash one table's dirty plates. */
        private const val WASH = 1.5f

        /** Seconds to mop up a spill. */
        private const val MOP = 1.5f

        /** How long the fridge's light flickers before it gives up. */
        const val FRIDGE_WARNING = 6f

        /** Seconds to get a broken fridge going again. */
        private const val FIX_FRIDGE = 4f

        /** While the fridge is broken, the cold food loses [FRIDGE_SPOIL] of itself every this many seconds. */
        private const val FRIDGE_SPOIL_EVERY = 10f
        private const val FRIDGE_SPOIL = 0.1

        /** A worn fridge can break during service from this day on. */
        private const val FRIDGE_BREAKS_FROM_DAY = 4

        /** Satisfaction each guest loses per spill on the floor when they finish eating, counting up to [MAX_MESS_PENALTIES]. */
        private const val MESS_PENALTY = 6
        private const val MAX_MESS_PENALTIES = 3
        private const val LEAVE = 1.6f
        private const val COOK_BASE = 2f
        private const val FOOD_PATIENCE = 1.8f
        /** A sensible minimum from sitting down to being served; only waiting beyond it counts against you. */
        private const val EXPECTED_SERVICE = 10f
        private const val MINUTES_PER_SECOND = 1.2f
        private const val HARD_STOP = 260f

        /** How long after the last guest leaves the night waits for the clearing up before closing anyway. */
        private const val TIDY_UP_LIMIT = 45f

        /**
         * Opens the doors: sorts tonight's guests into parties with arrival
         * times and pre-rolled orders, puts any hired servers on as food
         * runners, and sets the kitchen's pace from the cooks on shift.
         */
        fun open(setup: ServiceSetup, rng: RandomSource): ServiceNight {
            val state = setup.morning.state
            val customers = setup.arrivals
            val menu = state.menu.filter { it.available }

            // Parties of one or two (mostly two). They arrive roughly every seven or eight seconds in a
            // six-table room; a smaller room gets them more slowly, a bigger one faster, so every table gets used.
            val groups = mutableListOf<List<Int>>()
            var i = 0
            while (i < customers.size) {
                val size = if (i + 1 < customers.size && rng.nextInt(3) != 0) 2 else 1
                groups += (i until i + size).toList()
                i += size
            }
            val tableCount = state.restaurant.tables.coerceIn(1, ServiceFloor.MAX_TABLES)
            val pace = kotlin.math.sqrt(ServiceFloor.TABLE_COUNT.toFloat() / tableCount)
            var nextArrival = 1.5f
            val parties = groups.mapIndexed { index, guests ->
                val arriveAt = nextArrival
                nextArrival += (ARRIVAL_GAP + rng.nextFloat() * ARRIVAL_JITTER) * pace
                Party(
                    id = index,
                    guests = guests,
                    preferences = guests.map { g -> rankDishes(customers[g], menu, rng) },
                    arriveAt = arriveAt,
                    // The very first night is gentler: guests are a bit more forgiving while you learn the ropes.
                    patience = (guests.map { customers[it].patience }.average().toFloat() / MINUTES_PER_SECOND + 12f) * (if (state.day <= 1) FIRST_NIGHT_PATIENCE else 1f),
                )
            }

            // Spills: about one for every six parties, one more in a grubby restaurant. None while the
            // first guests are settling in: each happens a little after one of the later parties arrives,
            // at one of the open spots between tables.
            val later = parties.drop(parties.size / 4)
            val spillCount = if (later.isEmpty() || state.day <= QUIET_FLOOR_DAYS) 0 else (parties.size / 6 + (if (state.restaurant.cleanliness < 50) 1 else 0)).coerceAtMost(ServiceFloor.spillSpots.size)
            val spots = ServiceFloor.spillSpots.shuffledWith(rng)
            val messes = (0 until spillCount).map { k ->
                val after = later[rng.nextInt(later.size)].arriveAt
                Mess(id = k, at = spots[k], appearsAt = after + 12f + rng.nextFloat() * 10f)
            }

            // Every table is the player's. Up to two hired servers help out as food runners.
            val servers = state.employees.filter { it.status == EmployeeStatus.ACTIVE && (it.role == Role.SERVER || it.role == Role.MANAGER) }
            val helpers = servers.take(2)
            val allTables = (0 until tableCount).toSet()
            val waiters = listOf(
                Waiter(PLAYER_ID, isPlayer = true, tables = allTables, speed = PLAYER_SPEED, restSpot = ServiceFloor.pass,
                    route = listOf(FloorPoint(ServiceFloor.pass.x, ServiceFloor.pass.y + 3f)), routeStart = 0f, routeEnd = 0f, errand = null),
            ) + helpers.mapIndexed { k, employee ->
                // Out at the ends of the counter, so they never stand in front of a table's speech bubble.
                val rest = FloorPoint(if (k == 0) 10f else 90f, ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null)
            } + state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.DISHWASHER }.take(2).mapIndexed { k, employee ->
                // Dishwashers wait by the dish station and come out to clear tables.
                val rest = FloorPoint(78f - k * 8f, ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null, kind = Kind.DISHWASHER)
            } + state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.BUSSER }.take(2).mapIndexed { k, employee ->
                // Bussers wait at the left end of the counter, quick on their feet.
                val rest = FloorPoint(30f - k * 8f, ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * 1.15f * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null, kind = Kind.BUSSER)
            }
            val (slots, secondsPerDish) = kitchenPace(state)

            val active = state.employees.count { it.status == EmployeeStatus.ACTIVE }
            // A worn fridge might give up tonight (the more worn, the likelier), some time after things get going.
            // One that's already broken stays broken until someone fixes it.
            val fridge = Fridge.of(state)
            val fridgeBrokenAtOpen = fridge != null && EquipmentOperations.isBroken(fridge)
            val fridgeBreaksAt = if (fridge != null && !fridgeBrokenAtOpen && Fridge.isWorn(fridge) && state.day >= FRIDGE_BREAKS_FROM_DAY) {
                val chance = 0.25f + 0.5f * (Fridge.WORN - fridge.condition) / Fridge.WORN.toFloat()
                if (rng.nextFloat() < chance) 35f + rng.nextFloat() * 60f else null
            } else {
                null
            }

            // Now and then a special guest comes in (rolled last, so it doesn't change anything else about the night).
            val specialParties = if (state.day >= SPECIALS_FROM_DAY && parties.size > 3 && rng.nextFloat() < SPECIAL_CHANCE) {
                val who = SpecialGuest.entries[rng.nextInt(SpecialGuest.entries.size)]
                val index = 2 + rng.nextInt(parties.size - 2)
                parties.mapIndexed { k, p -> if (k == index) p.copy(special = who) else p }
            } else {
                parties
            }

            // Some nights, something goes wrong (rolled last, after everything else about the night).
            val chaos = if (state.day >= CHAOS_FROM_DAY && rng.nextFloat() < CHAOS_CHANCE) {
                val kind = ChaosKind.entries[rng.nextInt(ChaosKind.entries.size)]
                val startsAt = 30f + rng.nextFloat() * 50f
                val at = when (kind) {
                    ChaosKind.RAT -> FloorPoint(50f, 106f)
                    ChaosKind.DOG -> ServiceFloor.layout(tableCount).stand(rng.nextInt(tableCount)).let { FloorPoint(it.x, it.y - 2f) }
                    ChaosKind.PAN_FIRE -> FloorPoint(40f, 17f)
                    ChaosKind.POWER_CUT -> ServiceFloor.fuseBox
                }
                Chaos(kind, at, startsAt)
            } else {
                null
            }

            return ServiceNight(
                time = 0f,
                parties = specialParties,
                waiters = waiters,
                inventory = state.inventory,
                kitchenSlots = slots,
                secondsPerDish = secondsPerDish,
                menu = state.menu,
                kitchenQualityBonus = KitchenModel.qualityBonus(state.employees, state.equipment, state.restaurant.cleanliness),
                // Guests per person working, counting the player, who's on the floor all night.
                staffingRatio = customers.size.toDouble() / (active + 1),
                arrivalOrder = customers,
                messes = messes,
                tableCount = tableCount,
                fridgeBreaksAt = fridgeBreaksAt,
                chaos = chaos,
                cat = state.day >= CAT_FROM_DAY,
                fridgeBroken = fridgeBrokenAtOpen,
                hosted = state.employees.any { it.status == EmployeeStatus.ACTIVE && it.role == Role.HOST },
            )
        }

        private fun <T> List<T>.shuffledWith(rng: RandomSource): List<T> {
            val list = toMutableList()
            for (i in list.lastIndex downTo 1) {
                val j = rng.nextInt(i + 1)
                list[i] = list[j].also { list[j] = list[i] }
            }
            return list
        }

        /**
         * Kitchen pace: how many dishes can cook at once (one per cook) and
         * how long each takes — quicker with better cooks and a better oven,
         * slower with broken kit.
         */
        fun kitchenPace(state: com.recipefordisaster.domain.simulation.GameState): Pair<Int, Float> {
            val cooks = state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.COOK }
            val slots = cooks.size.coerceAtLeast(1)
            val skill = if (cooks.isEmpty()) 0.35f else (cooks.map { EmployeePerformance.effectiveServiceSpeed(it) }.average() / 45.0).toFloat().coerceIn(0.5f, 1.6f)
            val broken = Fridge.cookingKit(state.equipment).count { EquipmentOperations.isBroken(it) }
            return slots to 3.2f / skill * (if (broken > 0) 1.8f else 1f) / EquipmentCatalog.cookingSpeed(state.equipment)
        }

        /**
         * Roughly how many meals the kitchen can get out during a night of
         * [expectedGuests]: cooks work while guests keep arriving, plus a
         * little after the last one comes in.
         */
        fun mealsPerNight(state: com.recipefordisaster.domain.simulation.GameState, expectedGuests: Int): Int {
            val (slots, secondsPerDish) = kitchenPace(state)
            val parties = expectedGuests / AVERAGE_PARTY
            val window = parties * (ARRIVAL_GAP + ARRIVAL_JITTER / 2) + 30f
            // A party's plates cook together, so the fixed start-up time is shared between them.
            return (slots * window / (secondsPerDish + COOK_BASE / AVERAGE_PARTY)).toInt()
        }

        private const val AVERAGE_PARTY = 1.67f

        /** What a guest would order, best first: dishes they can eat and afford, ranked by popularity and value with a roll of the dice. */
        private fun rankDishes(customer: Customer, menu: List<Dish>, rng: RandomSource): List<DishId> {
            val suitable = menu.filter { dish -> dish.sellingPrice <= customer.budget && customer.dietaryRequirements.none { dish.violates(it) } }
            return suitable
                .map { dish -> dish.id to dish.popularity.coerceAtLeast(1) * ServiceSimulator.valueFactor(dish) * ServiceSimulator.valueFactor(dish) * (0.5 + rng.nextFloat()) }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }
}

/** Where the cat is: walking between spots, or sitting (maybe up on the counter). */
data class CatPose(val at: FloorPoint, val walking: Boolean, val onCounter: Boolean, val facingRight: Boolean)

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
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.menu.violates
import com.recipefordisaster.domain.progression.Difficulty
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.progression.Progression
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
 * Drinks come first: once seated, a party orders drinks, which the
 * player pours at the bar and carries over. They sip them while they read
 * the menu, and only then are they ready to order food.
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
    /** How much more every guest enjoys their evening for the decorations in the room. */
    val decorJoy: Int = 0,
    /** Tables the cat has sat by tonight: guests there enjoy it. */
    val catVisited: Set<Int> = emptySet(),
    /** When the cat last knocked a plate off the counter, and whose it was. */
    val catKnockAt: Float = -100f,
    val catKnockTable: Int? = null,
    /** How settled in guests' requests are tonight (0 before they arrive, 1 once normal). */
    val requestStrength: Float = 0f,
    /** Guests' requests the player saw to tonight, and ones left unseen. */
    val requestsFixed: Int = 0,
    val requestsMissed: Int = 0,
    /** The first night guests can ask for things, the first meal served is sure to bring a request. */
    val requestDebutPending: Boolean = false,
    /** When each new hire (by id) will trip and drop their tray tonight, if they will (see [StaffSlips]). */
    val tripAt: Map<String, Float> = emptyMap(),
    /** The new hires who've already tripped tonight, and when it last happened. */
    val tripped: Set<String> = emptySet(),
    val lastTripAt: Float = -100f,
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
    /** How the cooks are feeling tonight. */
    val chefMood: ChefMood = ChefMood.NORMAL,
    /** Rolled when the doors open: picks which plates the chef's mood touches, so the night stays repeatable. */
    val chefLuck: Float = 0f,
    /** When the chef last burnt something (for the puff of smoke). */
    val lastBurnAt: Float = -100f,
    /** Coins taken for drinks so far tonight (paid as they're served). */
    val drinkTakings: Long = 0,
    /** Points off every guest's enjoyment tonight: guests expect more as the run goes on. */
    val pickiness: Int = 0,
    /** A second thing going wrong later tonight, once the first is sorted (rolled when the doors open). */
    val laterChaos: Chaos? = null,
    /** Whether the trouble going on now is tonight's second lot. */
    val secondTrouble: Boolean = false,
    /** Features the player has had explained so far (this game's, plus any explained tonight). */
    val introduced: Set<Feature> = emptySet(),
) {

    /** What a guest was waiting for when they gave up. */
    @Serializable
    enum class WaitedFor { TABLE, ORDER, FOOD, DRINKS }

    /** What a guest can ask for, once they've been served: only the player can sort it out. */
    enum class RequestKind { WRONG_DISH, COLD, BILL }

    /** A guest asking for something, since [since]: go to their table to see to it. */
    @Serializable
    data class Request(val kind: RequestKind, val since: Float)

    /** A spill on the floor. Guests who finish their meal while it's there notice it. */
    @Serializable
    data class Mess(
        val id: Int,
        val at: FloorPoint,
        val appearsAt: Float,
        val cleaned: Boolean = false,
        /** Food a child dropped, rather than a spilt drink or sauce. */
        val crumbs: Boolean = false,
    )

    /** Spills on the floor right now. */
    val messesOnFloor: List<Mess> get() = messes.filter { !it.cleaned && it.appearsAt <= time }

    enum class Stage {
        NOT_YET_ARRIVED,
        QUEUEING,
        WALKING_TO_TABLE,
        /** Sat down and looking at what there is to drink; ready to order drinks in a moment. */
        DECIDING,
        /** Ready to order drinks. */
        WANTS_DRINKS,
        /** Drinks ordered: being poured at the bar, or on their way over. */
        DRINKS_ORDERED,
        /** Sipping their drinks and reading the menu; ready to order food in a few seconds. */
        DRINKING,
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
        /** A grown-up and a child out together: the second guest is the child. */
        val family: Boolean = false,
        /** A grumpy chef burnt this table's food and had to start again. */
        val burnt: Boolean = false,
        /** A happy chef made this table's food into a chef's special: they love it, and tip more. */
        val chefsSpecial: Boolean = false,
        /** When they ordered their drinks. */
        val drinksOrderedAt: Float = 0f,
        /**
         * When their drinks were poured and put on the bar (null: not waiting there). The tray sits with their
         * table number on it until the player or a server carries it over.
         */
        val trayAt: Float? = null,
        /** When their food went up on the pass, so we know how long it sat there (and went cold). */
        val readyAt: Float = 0f,
        /** Something they're asking for while they eat (see [Request]); null once it's seen to, or if they never asked. */
        val request: Request? = null,
        /** The player saw to what they asked for: they were pleased. */
        val requestFixed: Boolean = false,
        /** How long they sat sipping their drinks: time spent enjoying a drink isn't time spent waiting. */
        val sippedFor: Float = 0f,
        /** Whether they'll have a drink before their food (before the bar opens, nobody does). */
        val wantsDrinks: Boolean = true,
        /** Each guest's favourite dish, by their index in [arrivalOrder] (not everyone has one). */
        val favourites: Map<Int, DishId> = emptyMap(),
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

        /** A party's drinks, poured at the bar (one hand holds a whole table's round). */
        @Serializable
        data class Drinks(val partyId: Int) : HandItem
    }

    /**
     * Who a waiter is: the player, a hired server running food, a hired
     * dishwasher or busser (clears tables and washes up), or a bartender (drinks).
     */
    enum class Kind { PLAYER, RUNNER, DISHWASHER, BUSSER, BARTENDER }

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

        /** Go to the bar and pour the drinks you've taken orders for. */
        @Serializable
        data object VisitBar : Errand

        /** Go to the bar and pick up the tray of drinks poured for one party, to carry to their table. */
        @Serializable
        data class PickUpDrinks(val partyId: Int) : Errand

        /** Standing at the bar pouring these parties' drinks; they're in your hands when this ends. */
        @Serializable
        data class Pour(val partyIds: List<Int>) : Errand

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
        /** Party ids whose drinks this waiter has been asked for but not poured yet. */
        val drinkOrders: List<Int> = emptyList(),
    ) {
        /** Party ids whose drinks this waiter is carrying. */
        val drinks: List<Int> get() = hands.filterIsInstance<HandItem.Drinks>().map { it.partyId }
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

    private val trayCount: Int get() = parties.count { it.stage == Stage.DRINKS_ORDERED && it.trayAt != null }

    /** Drink trays waiting on the bar, oldest first (as many as fit), each marked with its table's number. */
    val traysOnBar: List<Party> get() = parties.filter { it.stage == Stage.DRINKS_ORDERED && it.trayAt != null }.sortedBy { it.trayAt }.take(ServiceFloor.TRAYS_ON_BAR)

    /** Plates waiting on the counter, oldest first, as many as fit. */
    val platesOnCounter: List<Party> get() = parties.filter { it.stage == Stage.READY_AT_PASS }.sortedBy { it.stageSince }.take(ServiceFloor.PLATES_ON_COUNTER)

    /** Where tonight's tables are. */
    val layout: TableLayout get() = ServiceFloor.layout(tableCount)

    val player: Waiter get() = waiters.first { it.isPlayer }

    /** Whether the player's list of stops is full, so one more tap would be ignored. */
    val queueFull: Boolean get() = player.queue.size >= MAX_QUEUED

    /** Coins taken so far tonight. */
    val takings: Long get() = results.values.sumOf { it.dish?.sellingPrice ?: 0 } + tips + drinkTakings

    /** What a party's round of drinks costs: a child's lemonade is cheaper. */
    fun drinksPrice(party: Party): Long = party.guests.indices.sumOf { g -> if (party.family && g == 1) CHILD_DRINK_PRICE else DRINK_PRICE }

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
        Stage.READY_TO_ORDER, Stage.WANTS_DRINKS -> (time - party.stageSince) / patienceOf(party)
        Stage.DRINKS_ORDERED -> (time - party.drinksOrderedAt) / (patienceOf(party) * DRINK_PATIENCE)
        Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.READY_AT_PASS, Stage.CARRIED -> (time - party.orderedAt) / (patienceOf(party) * FOOD_PATIENCE)
        else -> 0f
    }.coerceIn(0f, 1f)

    /**
     * Something turning up tonight for the very first time, that the player hasn't had explained yet:
     * the screen pauses and explains it. Null when there's nothing new to explain.
     */
    fun introductionDue(): Feature? {
        fun arriving(party: Party) = party.stage in Stage.QUEUEING..Stage.DECIDING
        val now = buildList {
            if (secondTrouble && activeChaos != null) add(Feature.DOUBLE_TROUBLE)
            activeChaos?.let { add(featureOf(it.kind)) }
            if (fridgeStruggling || (fridgeBrokeTonight && fridgeBroken)) add(Feature.FRIDGE_BREAKS)
            if (messesOnFloor.isNotEmpty()) add(Feature.SPILLS)
            parties.filter { arriving(it) }.forEach { party -> party.special?.let { add(featureOf(it)) } }
            if (parties.any { it.family && arriving(it) }) add(Feature.FAMILIES)
            if (parties.any { it.request != null }) add(Feature.GUEST_REQUESTS)
            if (parties.any { it.stage == Stage.WANTS_DRINKS }) add(Feature.DRINKS)
            if (cat && time >= CAT_INTRODUCED_AFTER) add(Feature.CAT)
            if (chefMood != ChefMood.NORMAL && parties.any { it.stage == Stage.COOKING }) add(Feature.CHEF_MOODS)
        }
        return now.firstOrNull { it !in introduced }
    }

    /** The player has read about [feature]: it won't be explained again. */
    fun introduce(feature: Feature): ServiceNight = copy(introduced = introduced + feature)

    // ------------------------------------------------------------ the player's commands

    /** Go to a table: take their order if they're ready, or serve them if you're carrying their food. */
    fun tapTable(table: Int): ServiceNight = enqueue(Errand.VisitTable(table))

    /** Go to the pass: hand in any tickets you're holding and pick up plates that are ready. */
    fun tapPass(): ServiceNight = enqueue(Errand.VisitPass)

    /** Go to the chef and hand in the orders you've taken. */
    fun tapChef(): ServiceNight = enqueue(Errand.HandIn)

    /** Go and pick up one plate from the counter (a party's food that's ready). */
    fun tapPlate(partyId: Int): ServiceNight = enqueue(Errand.PickUp(partyId))

    /** Go to the bar and pour the drinks you've taken orders for. */
    fun tapBar(): ServiceNight = enqueue(Errand.VisitBar)

    /** Go and pick up the tray of drinks waiting on the bar for this party. */
    fun tapTray(partyId: Int): ServiceNight = enqueue(Errand.PickUpDrinks(partyId))

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

    /** Whether there's a stop lined up, or a walk still on its way, for [undoLastTap] to take back. */
    val canUndo: Boolean get() = player.queue.isNotEmpty() || player.errand?.isWalk == true

    /**
     * Take back the last thing you tapped: the last stop lined up, or, with none lined up, the walk you're
     * on (you stop where you are). Jobs already under way, like washing up or mopping, aren't interrupted.
     */
    fun undoLastTap(): ServiceNight {
        val me = player
        return when {
            me.queue.isNotEmpty() -> copy(waiters = waiters.map { if (it.isPlayer) it.copy(queue = it.queue.dropLast(1)) else it })
            me.errand?.isWalk == true -> copy(waiters = waiters.map {
                if (it.isPlayer) it.copy(errand = null, route = listOf(it.position(time)), routeStart = time, routeEnd = time) else it
            })
            else -> this
        }
    }

    /** A trip to somewhere, as opposed to a job done once there. */
    private val Errand.isWalk: Boolean
        get() = when (this) {
            is Errand.VisitTable, Errand.VisitPass, Errand.HandIn, is Errand.PickUp, is Errand.Serve, Errand.VisitDishStation,
            Errand.VisitMopBucket, is Errand.CleanMess, Errand.VisitChaos, Errand.VisitBar, is Errand.PickUpDrinks, Errand.VisitFridge -> true
            Errand.Wash, is Errand.Mopping, Errand.Rest, Errand.HandleChaos, is Errand.Pour, Errand.FixFridge -> false
        }

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
        // Only a tray that's actually waiting on the bar can be picked up.
        if (errand is Errand.PickUpDrinks && traysOnBar.none { it.id == errand.partyId }) return this
        if (errand == Errand.VisitChaos && activeChaos == null) return this
        if (me.errand == null) return send(me.id, errand)
        if (errand == me.errand || errand in me.queue || queueFull) return this
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
            Errand.Wash, is Errand.Mopping, Errand.FixFridge, Errand.HandleChaos, is Errand.Pour -> here
            Errand.VisitBar -> ServiceFloor.bar
            is Errand.PickUpDrinks -> traysOnBar.indexOfFirst { it.id == errand.partyId }.takeIf { it >= 0 }?.let { ServiceFloor.trayStand(it) } ?: ServiceFloor.bar
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
        night = night.tripStep()
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
        // Like everyone else, the cat keeps to the floor: along the gaps between the rows of tables and
        // up and down the aisles, never across a table. (From the end of an aisle it hops up onto the counter.)
        val route = ServiceFloor.route(from, to, layout.aisles)
        val t = (inLeg / CAT_WALKING).coerceIn(0f, 1f)
        val at = if (walking) ServiceFloor.along(route, t) else to
        val ahead = ServiceFloor.along(route, (t + 0.03f).coerceAtMost(1f))
        val facingRight = if (walking && kotlin.math.abs(ahead.x - at.x) > 0.01f) ahead.x > at.x else to.x >= from.x
        return CatPose(at, walking = walking, onCounter = !walking && to == CAT_COUNTER_SPOT, facingRight = facingRight)
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

    /** New hires who are due a trip drop their tray the next time they're out carrying something. */
    private fun tripStep(): ServiceNight {
        var night = this
        for ((id, at) in tripAt) {
            if (id in night.tripped || time < at) continue
            val waiter = night.waiters.firstOrNull { it.id == id } ?: continue
            if (waiter.hands.none { it != HandItem.Mop } || waiter.errand?.isWalk != true) continue
            night = night.trip(waiter)
        }
        return night
    }

    /**
     * [waiter] trips and drops everything they carry: food goes back to the kitchen to be made again, drinks
     * are asked for again, dirty plates are broken, and there's a mess on the floor for someone to mop.
     */
    private fun trip(waiter: Waiter): ServiceNight {
        val here = waiter.position(time)
        var night = this
        waiter.hands.forEach { item ->
            when (item) {
                is HandItem.Plate -> night = night.updateParty(item.partyId) { it.copy(stage = Stage.IN_KITCHEN, stageSince = time, heldBy = null) }
                is HandItem.Drinks -> night = night.updateParty(item.partyId) { it.copy(stage = Stage.WANTS_DRINKS, stageSince = time) }
                is HandItem.DirtyDishes, HandItem.Mop -> {}
            }
        }
        val spot = ServiceFloor.spillSpots
            .filter { spot -> messesOnFloor.none { it.at.distanceTo(spot) < 1f } }
            .minByOrNull { it.distanceTo(here) } ?: here
        val mess = Mess(id = (messes.maxOfOrNull { it.id } ?: -1) + 1, at = spot, appearsAt = time)
        return night.copy(
            waiters = night.waiters.map {
                if (it.id == waiter.id) it.copy(hands = it.hands.filter { item -> item == HandItem.Mop }, errand = null, route = listOf(here), routeStart = time, routeEnd = time) else it
            },
            messes = night.messes + mess,
            tripped = night.tripped + waiter.id,
            lastTripAt = time,
        )
    }

    /** Chaos that sorts itself out after a while (the lights come back, the rat wanders off), and a second lot later on. */
    private fun chaosStep(): ServiceNight {
        // Once the first trouble is over, the second one (if there is one) is next.
        val next = laterChaos
        if (next != null && chaos?.resolved == true && time >= next.startsAt) return copy(chaos = next, laterChaos = null, secondTrouble = true)
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
            // A quick look at the drinks, or (if they're not having one) a proper read of the menu.
            val reading = if (party.wantsDrinks) {
                DRINKS_LIST_MIN + (party.id * 0.618f % 1f) * (DRINKS_LIST_MAX - DRINKS_LIST_MIN)
            } else {
                DECIDE_MIN + (party.id * 0.618f % 1f) * (DECIDE_MAX - DECIDE_MIN)
            }
            night = night.updateParty(party.id) { it.copy(stage = Stage.DECIDING, stageSince = time, seatedAt = time, until = time + reading) }
        }
        // ...and then they'd like a drink (or, if they're not having one, they're ready to order food)...
        night.parties.filter { it.stage == Stage.DECIDING && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = if (party.wantsDrinks) Stage.WANTS_DRINKS else Stage.READY_TO_ORDER, stageSince = time) }
        }
        // ...and once they've had a good sip and read the menu, they're ready to order food.
        night.parties.filter { it.stage == Stage.DRINKING && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = Stage.READY_TO_ORDER, stageSince = time) }
        }
        return night
    }

    private fun checkPatience(): ServiceNight {
        var night = this
        for (party in parties) {
            // A host chatting to people, handing out menus and topping up water buys you time.
            val givesUp = when (party.stage) {
                Stage.QUEUEING, Stage.WANTS_DRINKS, Stage.DRINKS_ORDERED, Stage.READY_TO_ORDER, Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.READY_AT_PASS, Stage.CARRIED ->
                    impatience(party) >= 1f
                else -> false
            }
            if (givesUp) {
                val waitedFor = when (party.stage) {
                    Stage.QUEUEING -> WaitedFor.TABLE
                    Stage.READY_TO_ORDER -> WaitedFor.ORDER
                    Stage.WANTS_DRINKS, Stage.DRINKS_ORDERED -> WaitedFor.DRINKS
                    else -> WaitedFor.FOOD
                }
                night = night.copy(gaveUpAt = night.gaveUpAt + party.guests.filter { it !in night.results }.associateWith { waitedFor })
                    .stormOut(party.id, MissedMealReason.TIRED_OF_WAITING)
            }
        }
        return night
    }

    /**
     * What's left in someone's hands when a party walks out: their food or drinks can't be served any more,
     * so it becomes a stack of dirty dishes to take to the dish station (one stack, even if it was both).
     */
    private fun List<HandItem>.withoutOrderOf(partyId: Int, table: Int?): List<HandItem> {
        var stacked = table != null && HandItem.DirtyDishes(table) in this
        return mapNotNull { item ->
            when {
                item != HandItem.Plate(partyId) && item != HandItem.Drinks(partyId) -> item
                table == null || stacked -> null
                else -> HandItem.DirtyDishes(table).also { stacked = true }
            }
        }
    }

    /** A party gives up: everyone without a result leaves hungry, and any ticket of theirs disappears; food in hand goes to be washed. */
    private fun stormOut(partyId: Int, reason: MissedMealReason): ServiceNight {
        val party = parties.first { it.id == partyId }
        var results = results
        val specialVisits = specialVisits + listOfNotNull(party.special?.let { SpecialVisit(it, pleased = false) })
        party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, reason)) }
        val inLine = party.stage == Stage.QUEUEING
        return copy(
            specialVisits = specialVisits,
            results = results,
            waiters = waiters.map {
                it.copy(tickets = it.tickets - partyId, drinkOrders = it.drinkOrders - partyId, hands = it.hands.withoutOrderOf(partyId, party.table))
            },
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

    /** Whether guests can ask for things tonight. */
    val requestsOn: Boolean get() = requestStrength > 0f

    /** Whether anything on the menu can still be cooked tonight (a broken fridge can be fixed, so it doesn't count). */
    val kitchenHasFood: Boolean get() = menu.any { canMake(it.id, inventory, fridgeMatters = false) }

    private fun runKitchen(dt: Float): ServiceNight {
        var night = this
        // A pan fire or a power cut stops the kitchen: whatever's cooking just waits.
        if (night.activeChaos?.stopsKitchen == true) {
            return night.copy(parties = night.parties.map { if (it.stage == Stage.COOKING) it.copy(until = it.until + dt) else it })
        }
        // Finished cooking: plates go up on the pass — unless a grumpy chef has burnt them, and starts again.
        night.parties.filter { it.stage == Stage.COOKING && time >= it.until }.forEach { party ->
            night = if (night.burns(party)) {
                night.copy(lastBurnAt = time).updateParty(party.id) { it.copy(burnt = true, until = time + night.cookTime(party.orders)) }
            } else {
                val special = night.chefMood == ChefMood.HAPPY && night.touchedByMood(party, CHEFS_SPECIAL_CHANCE)
                night.updateParty(party.id) { it.copy(stage = Stage.READY_AT_PASS, stageSince = time, readyAt = time, chefsSpecial = special) }
            }
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
                night.updateParty(party.id) { it.copy(stage = Stage.COOKING, stageSince = time, until = time + night.cookTime(cooked), orders = cooked) }
            }
        }
        return night
    }

    /** How long the kitchen takes to cook a table's order: quick dishes come out sooner than slow ones. */
    internal fun cookTime(orders: List<DishId?>): Float =
        secondsPerDish * orders.filterNotNull().sumOf { id -> dishPace(menu.firstOrNull { it.id == id }).toDouble() }.toFloat() + COOK_BASE

    /** Which of tonight's plates the chef's mood touches: the same ones every time the night is played. */
    private fun touchedByMood(party: Party, chance: Float): Boolean = (party.id * 0.618f + chefLuck) % 1f < chance

    /** A grumpy chef burns some plates (each one only once), and the slow, fiddly dishes twice as often. */
    private fun burns(party: Party): Boolean {
        val fiddly = party.orders.filterNotNull().any { id -> dishPace(menu.firstOrNull { it.id == id }) >= FIDDLY_PACE }
        return chefMood == ChefMood.GRUMPY && !party.burnt && touchedByMood(party, BURN_CHANCE * (if (fiddly) 2f else 1f))
    }

    /** How many tables' food got burnt tonight. */
    val burntPlates: Int get() = parties.count { it.burnt }

    /** How many guests got their favourite dish tonight. */
    val favouritesServed: Int get() = parties.sumOf { party ->
        party.guests.zip(party.orders).count { (guest, dish) -> dish != null && party.favourites[guest] == dish && results[guest]?.dish?.id == dish }
    }

    /** How many tables got a chef's special tonight. */
    val chefsSpecials: Int get() = parties.count { it.chefsSpecial }

    private fun finishMeals(): ServiceNight {
        var night = this
        for (party in parties) {
            when {
                party.stage == Stage.EATING && time >= party.until -> {
                    // They pay; how happy they are depends on the food, the price, and how long it all took.
                    var results = night.results
                    val waitedMinutes = ((time - EAT - party.seatedAt - party.sippedFor - EXPECTED_SERVICE - DRINKS_ALLOWANCE).coerceAtLeast(0f) * MINUTES_PER_SECOND).toDouble()
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
                    // Seeing to what they asked for pleases them; being ignored does the opposite.
                    val requestPenalty = when (party.request?.kind) {
                        RequestKind.COLD -> COLD_PENALTY
                        RequestKind.WRONG_DISH -> WRONG_DISH_PENALTY
                        RequestKind.BILL -> BILL_PENALTY
                        null -> 0
                    }
                    val treat = night.decorJoy + (if (party.chefsSpecial) CHEFS_SPECIAL_JOY else 0) +
                        // Getting their favourite dish makes anyone's night.
                        (if (party.favourites[guest] == dishId) FAVOURITE_JOY else 0) +
                        (if (party.requestFixed) REQUEST_FIXED_JOY else 0) - requestPenalty
                    val satisfaction = (ServiceSimulator.resolveSatisfaction(customer, dish, waitedMinutes, kitchenQualityBonus) - mess + treat - night.pickiness).coerceIn(0, 100)
                        results = results + (guest to CustomerServiceOutcome(customer, dish, satisfaction, waitedMinutes))
                    }
                    // They leave their dirty plates behind; the table needs clearing before anyone else can sit there.
                    night = night.copy(
                        results = results,
                        requestsMissed = night.requestsMissed + (if (party.request != null) 1 else 0),
                        dirtyTables = night.dirtyTables + listOfNotNull(party.table),
                        dirtiedAt = night.dirtiedAt + listOfNotNull(party.table?.let { it to time }),
                    )
                        .updateParty(party.id) { it.copy(stage = Stage.LEAVING_HAPPY, stageSince = time, until = time + LEAVE, tip = tipFor(party, results)) }
                    // Children drop food: a family leaves a mess on the floor by their table.
                    if (party.family) night = night.familyMess(party)
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

    /**
     * [party]'s food reaches their table: they start eating. Now and then they have something to say about
     * it: cold if it sat on the pass too long, otherwise (from the day guests start asking) the wrong dish
     * or the bill, now and then. Staff can't help with that: it's for the player to sort out before they leave.
     */
    private fun served(party: Party, waiterId: String): ServiceNight {
        val kind = requestKindFor(party)
        val request = kind?.let { Request(it, time) }
        val sure = requestDebutPending && request != null
        return copy(
            waiters = waiters.map { if (it.id == waiterId) it.copy(hands = it.hands - HandItem.Plate(party.id)) else it },
            requestDebutPending = requestDebutPending && !sure,
        ).updateParty(party.id) {
            it.copy(stage = Stage.EATING, stageSince = time, until = time + EAT + (if (request != null) REQUEST_WAIT else 0f), heldBy = null, request = request)
        }
    }

    /** What, if anything, [party] asks for when their food arrives (the same every time the night is played). */
    private fun requestKindFor(party: Party): RequestKind? {
        if (!requestsOn) return null
        if (time - party.readyAt >= COLD_AFTER) return RequestKind.COLD
        val roll = (party.id * 0.618f + chefLuck + 0.37f) % 1f
        return when {
            requestDebutPending -> RequestKind.BILL
            roll < WRONG_DISH_CHANCE * requestStrength -> RequestKind.WRONG_DISH
            roll < (WRONG_DISH_CHANCE + BILL_CHANCE) * requestStrength -> RequestKind.BILL
            else -> null
        }
    }

    /** A table gets its drinks: they sip them while they read the menu. */
    private fun serveDrinks(partyId: Int, waiterId: String): ServiceNight {
        val party = parties.first { it.id == partyId }
        val sip = SIP_MIN + (party.id * 0.618f % 1f) * (SIP_MAX - SIP_MIN)
        return copy(waiters = waiters.map { if (it.id == waiterId) it.copy(hands = it.hands - HandItem.Drinks(partyId)) else it }, drinkTakings = drinkTakings + drinksPrice(party))
            .updateParty(partyId) { it.copy(stage = Stage.DRINKING, stageSince = time, until = time + sip, sippedFor = sip, heldBy = null) }
    }

    /** The mess a family leaves behind, on the nearest bit of open floor to their table that's still clean. */
    private fun familyMess(party: Party): ServiceNight {
        val table = party.table ?: return this
        val near = layout.stand(table)
        val spot = ServiceFloor.spillSpots
            .filter { spot -> messesOnFloor.none { it.at.distanceTo(spot) < 1f } }
            .minByOrNull { it.distanceTo(near) } ?: return this
        val id = (messes.maxOfOrNull { it.id } ?: -1) + 1
        return copy(messes = messes + Mess(id = id, at = spot, appearsAt = time, crumbs = true))
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
                val notReadyYet = party != null && (party.stage == Stage.WALKING_TO_TABLE || party.stage == Stage.DECIDING || (party.stage == Stage.DRINKING && party.id !in waiter.drinks))
                if (notReadyYet && waiter.kind != Kind.DISHWASHER && waiter.kind != Kind.BUSSER) {
                    // They're still sitting down, deciding, or finishing a sip: wait at the table and take their order as soon as they're ready.
                    val readyAt = party!!.until
                    updateWaiter { it.copy(route = listOf(it.position(time)), routeStart = time, routeEnd = readyAt + 0.01f, errand = errand) }
                } else if (party != null && party.id in waiter.plates) {
                    night = night.served(party, waiterId)
                } else if (party != null && party.id in waiter.drinks) {
                    night = night.serveDrinks(party.id, waiterId)
                } else if ((waiter.kind == Kind.PLAYER || (waiter.kind == Kind.BARTENDER && party?.special == null)) && party != null && party.stage == Stage.WANTS_DRINKS) {
                    // Their drinks order goes on the notepad; pour it at the bar.
                    updateWaiter { it.copy(drinkOrders = it.drinkOrders + party.id) }
                    night = night.updateParty(party.id) { it.copy(stage = Stage.DRINKS_ORDERED, stageSince = time, drinksOrderedAt = time, heldBy = waiterId) }
                } else if (waiter.kind == Kind.PLAYER && party != null && party.request != null) {
                    // They asked for something: it's you they want, and you're here.
                    night = night.copy(requestsFixed = night.requestsFixed + 1)
                        .updateParty(party.id) { it.copy(request = null, requestFixed = true, until = minOf(it.until, time + EAT)) }
                } else if (party == null && errand.table in night.dirtyTables && waiter.freeHands > 0) {
                    // Clear the table: its dirty plates go in one hand.
                    night = night.copy(dirtyTables = night.dirtyTables - errand.table, dirtiedAt = night.dirtiedAt - errand.table)
                    updateWaiter { it.copy(hands = it.hands + HandItem.DirtyDishes(errand.table)) }
                } else if (waiter.kind == Kind.PLAYER && party != null && party.stage == Stage.READY_TO_ORDER && errand.table in waiter.tables) {
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
                    night = night.served(party, waiterId)
                } else if (party != null && party.id in waiter.drinks) {
                    night = night.serveDrinks(party.id, waiterId)
                }
            }
            is Errand.PickUpDrinks -> {
                val party = night.parties.firstOrNull { it.id == errand.partyId }
                if (party != null && party.stage == Stage.DRINKS_ORDERED && party.trayAt != null && waiter.freeHands > 0) {
                    night = night.updateParty(party.id) { it.copy(trayAt = null, heldBy = waiterId) }
                    updateWaiter { it.copy(hands = it.hands + HandItem.Drinks(party.id)) }
                }
            }
            Errand.VisitBar -> {
                // Pour as many rounds as there are free hands for (a bartender: as many as fit on the bar), a moment each.
                val room = if (waiter.kind == Kind.BARTENDER) ServiceFloor.TRAYS_ON_BAR - night.trayCount else waiter.freeHands
                val rounds = waiter.drinkOrders.take(minOf(waiter.freeHands, room))
                if (rounds.isNotEmpty()) {
                    updateWaiter { it.copy(route = listOf(it.position(time)), routeStart = time, routeEnd = time + POUR * rounds.size, errand = Errand.Pour(rounds)) }
                }
            }
            is Errand.Pour -> if (waiter.kind == Kind.BARTENDER) {
                // A bartender stays behind the bar: each round goes on a tray, with the table's number on it, for someone to carry over.
                updateWaiter { it.copy(drinkOrders = it.drinkOrders - errand.partyIds.toSet()) }
                errand.partyIds.forEach { id -> night = night.updateParty(id) { it.copy(trayAt = time, heldBy = null) } }
            } else {
                updateWaiter { it.copy(drinkOrders = it.drinkOrders - errand.partyIds.toSet(), hands = it.hands + errand.partyIds.map { id -> HandItem.Drinks(id) }) }
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
                    .filter { it.stage == Stage.READY_AT_PASS && (waiter.isPlayer || (it.special == null && time - it.stageSince >= RUNNER_DELAY && it.id !in claimed)) }
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
                val canMop = waiter.holdingMop
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
     * carrying, fetch plates that have waited too long, and see to tables
     * left waiting for drinks; dishwashers clear dirty tables and wash up.
     */
    private fun directStaff(): ServiceNight {
        var night = this
        for (waiter in waiters.filter { !it.isPlayer && it.errand == null }) {
            val next: Errand? = when (waiter.kind) {
                Kind.DISHWASHER, Kind.BUSSER -> {
                    // Clearing is the whole job: a table nobody else is already on their way to, and the dishes to the sink.
                    // (Spills and the fridge are left to you.)
                    val claimed = night.waiters.flatMap { listOfNotNull(it.errand) + it.queue }.filterIsInstance<Errand.VisitTable>().map { it.table }.toSet()
                    val table = night.dirtyTables.firstOrNull { it !in claimed }
                    when {
                        waiter.dirtyDishes.isNotEmpty() && (waiter.freeHands == 0 || table == null) -> Errand.VisitDishStation
                        table != null && waiter.freeHands > 0 -> Errand.VisitTable(table)
                        else -> null
                    }
                }
                Kind.BARTENDER -> {
                    // The bartender sees to every table that wants a drink and that you're not already on your way to.
                    val claimed = night.waiters.flatMap { listOfNotNull(it.errand) + it.queue }.filterIsInstance<Errand.VisitTable>().map { it.table }.toSet()
                    // (Special guests only want you.)
                    val thirsty = night.parties.filter { it.special == null && it.stage == Stage.WANTS_DRINKS && it.table !in claimed }.minByOrNull { it.stageSince }?.table
                    // They take the orders and pour; carrying the trays over is for you and the servers.
                    val barHasRoom = night.trayCount < ServiceFloor.TRAYS_ON_BAR
                    when {
                        waiter.drinkOrders.isNotEmpty() && barHasRoom && thirsty == null -> Errand.VisitBar
                        thirsty != null && waiter.freeHands > 0 -> Errand.VisitTable(thirsty)
                        else -> null
                    }
                }
                else -> {
                    // Servers only carry: food and drink trays out to the tables. Taking orders is left to you (and a bartender), and clearing to dishwashers.
                    // A tray is left for you for a few seconds, and isn't taken if you've said you're coming for it.
                    val claimedTrays = night.waiters.flatMap { listOfNotNull(it.errand) + it.queue }.filterIsInstance<Errand.PickUpDrinks>().map { it.partyId }.toSet()
                    val tray = night.traysOnBar.firstOrNull { it.special == null && it.id !in claimedTrays && time - (it.trayAt ?: time) >= RUNNER_DELAY }
                    when {
                        waiter.plates.isNotEmpty() -> night.parties.first { it.id == waiter.plates.first() }.table?.let { Errand.Serve(it) }
                        // (Anything to do with drinks a server already had in hand from before is still seen through.)
                        waiter.drinks.isNotEmpty() -> night.parties.first { it.id == waiter.drinks.first() }.table?.let { Errand.Serve(it) }
                        tray != null && waiter.freeHands > 0 -> Errand.PickUpDrinks(tray.id)
                        waiter.drinkOrders.isNotEmpty() && waiter.freeHands > 0 -> Errand.VisitBar
                        night.parties.any { it.special == null && it.stage == Stage.READY_AT_PASS && time - it.stageSince >= RUNNER_DELAY } && waiter.freeHands > 0 -> Errand.VisitPass
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
        // Someone whose request was ignored leaves nothing.
        if (party.request != null) return 0L
        // Time spent sipping drinks doesn't count, and nor does a quick round of drinks.
        val served = time - EAT - party.seatedAt - party.sippedFor - DRINKS_ALLOWANCE
        val perGuest = when {
            served <= TIP_FAST -> 3L
            served <= TIP_OK -> 1L
            else -> 0L
        }
        // A chef's special earns a little extra on top, however long it took.
        val extra = if (party.chefsSpecial) CHEFS_SPECIAL_TIP else 0L
        // So does serving someone their favourite dish.
        fun favourite(guest: Int) = if (party.favourites[guest] != null && party.favourites[guest] == party.orders.getOrNull(party.guests.indexOf(guest))) FAVOURITE_TIP else 0L
        return party.guests.sumOf { guest -> if ((results[guest]?.satisfaction ?: 0) >= 55) perGuest + extra + favourite(guest) else 0L }
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
            drinks = drinkTakings,
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
        private const val CAT_LEG = 9f
        private const val CAT_WALKING = 0.5f
        private const val CAT_JOY = 6

        /** How long a food order can sit on the pass before it arrives cold. */
        private const val COLD_AFTER = 20f

        /** Chances (before the first nights settle in) a served table asks about the wrong dish, or the bill. */
        private const val WRONG_DISH_CHANCE = 0.07f
        private const val BILL_CHANCE = 0.18f

        /** How much longer a table that's asked for something waits, for you to come, before they carry on eating anyway. */
        private const val REQUEST_WAIT = 10f

        /** How much it costs their opinion of you to be ignored, by what they asked for; and the bit of joy when it's seen to. */
        private const val COLD_PENALTY = 20
        private const val WRONG_DISH_PENALTY = 30
        private const val BILL_PENALTY = 5
        private const val REQUEST_FIXED_JOY = 4
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

        /** How long into the night the cat has to be about before it's introduced (so it's on screen by then). */
        private const val CAT_INTRODUCED_AFTER = 3f

        /** Once two disasters a night are possible, about this share of nights with one get a second. */
        private const val DOUBLE_TROUBLE_CHANCE = 0.35f

        /** When each kind of trouble first turns up: the rat, then the dog, the pan fire and the power cut. */
        private fun featureOf(kind: ChaosKind) = when (kind) {
            ChaosKind.RAT -> Feature.RAT
            ChaosKind.DOG -> Feature.DOG
            ChaosKind.PAN_FIRE -> Feature.PAN_FIRE
            ChaosKind.POWER_CUT -> Feature.POWER_CUT
        }

        /** A special guest counts as pleased at this satisfaction or more. */
        private const val SPECIAL_PLEASED = 65

        /** Once special guests are coming, one turns up on about this share of nights. */
        private const val SPECIAL_CHANCE = 0.4f

        /** When each kind of special guest starts coming: the celebrity (the easy one) first. */
        private fun featureOf(guest: SpecialGuest) = when (guest) {
            SpecialGuest.CELEBRITY -> Feature.CELEBRITY
            SpecialGuest.CRITIC -> Feature.CRITIC
            SpecialGuest.INSPECTOR -> Feature.INSPECTOR
        }

        /** Once families are coming in, this share of the parties of two are one. */
        private const val FAMILY_CHANCE = 0.25f

        /** The share of plates a grumpy chef burns. */
        private const val BURN_CHANCE = 0.3f

        /** Dishes this slow or slower (the lasagna) are the ones a grumpy chef burns most. */
        private const val FIDDLY_PACE = 1.4f

        /** The share of plates a happy chef makes special, how much more guests enjoy one, and the extra tip each leaves. */
        private const val CHEFS_SPECIAL_CHANCE = 0.3f
        private const val CHEFS_SPECIAL_JOY = 8
        private const val CHEFS_SPECIAL_TIP = 2L

        /** Food on the table within this many seconds of sitting down earns the full tip; within [TIP_OK], a small one. */
        private const val TIP_FAST = 24f
        private const val TIP_OK = 34f

        /** How long guests read the menu after sitting down before they're ready to order. */
        private const val DECIDE_MIN = 2.5f
        private const val DECIDE_MAX = 4.5f

        /** How long a party looks at the drinks before they're ready to order them. */
        private const val DRINKS_LIST_MIN = 1.5f
        private const val DRINKS_LIST_MAX = 2.5f

        /** How long a party sips their drinks (and reads the menu) before they're ready to order food. */
        private const val SIP_MIN = DECIDE_MIN + 1.5f
        private const val SIP_MAX = DECIDE_MAX + 1.5f

        /** What a drink costs, and a child's. */
        private const val DRINK_PRICE = 3L
        private const val CHILD_DRINK_PRICE = 2L

        /** Seconds to pour one table's round at the bar. */
        private const val POUR = 1.5f

        /** How much longer than [patience] a party will wait for drinks they've ordered. */
        private const val DRINK_PATIENCE = 1.3f

        /** A quick round of drinks takes about this long, so it doesn't count against the food being slow. */
        private const val DRINKS_ALLOWANCE = 8f

        /** How long a table sits dirty before a server with nothing else to do clears it. */
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


        /** Satisfaction each guest loses per spill on the floor when they finish eating, counting up to [MAX_MESS_PENALTIES]. */
        private const val MESS_PENALTY = 6
        private const val MAX_MESS_PENALTIES = 3
        private const val LEAVE = 1.6f
        private const val COOK_BASE = 2f

        /** A dish that takes this many minutes to prepare cooks at the kitchen's usual pace. */
        private const val USUAL_PREP_MINUTES = 8f

        /**
         * How long [dish] takes compared with the usual: a salad is quick, a lasagna slow. Only half of
         * it depends on the dish, so a slow dish is a wait but never a disaster.
         */
        fun dishPace(dish: Dish?): Float = if (dish == null) 1f else 0.5f + 0.5f * dish.recipe.preparationTimeMinutes / USUAL_PREP_MINUTES
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
            // How hard tonight is: guests a little less patient, and coming a little faster, as the run goes on.
            val dials = Difficulty.dials(state)

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
            // Once the bar opens, a few parties have a drink first; within a few nights, everyone does.
            val drinksShare = Progression.strength(state, Feature.DRINKS)
            val parties = groups.mapIndexed { index, guests ->
                val arriveAt = nextArrival
                nextArrival += (ARRIVAL_GAP + rng.nextFloat() * ARRIVAL_JITTER) * pace * dials.arrivalGap
                Party(
                    id = index,
                    guests = guests,
                    preferences = guests.map { g -> rankDishes(customers[g], menu, rng) },
                    arriveAt = arriveAt,
                    // The first nights are gentler: guests are more forgiving while you learn the ropes.
                    patience = (guests.map { customers[it].patience }.average().toFloat() / MINUTES_PER_SECOND + 12f) * dials.patience,
                    wantsDrinks = (index * 0.618f + 0.3f) % 1f < drinksShare,
                )
            }

            // Spills: about one for every six parties, one more in a grubby restaurant. None while the
            // first guests are settling in: each happens a little after one of the later parties arrives,
            // at one of the open spots between tables.
            val later = parties.drop(parties.size / 4)
            // Spills start a few nights in, rare at first; there can be a few more of them as the run goes on.
            val spillsSettled = Progression.strength(state, Feature.SPILLS)
            val spillCount = if (later.isEmpty() || spillsSettled <= 0f) 0 else {
                val usual = (parties.size / 6 + (if (state.restaurant.cleanliness < 50) 1 else 0)).coerceAtMost(dials.maxSpills)
                // (On the first night of spills there's always one, as promised.)
                val first = if (Progression.debutsToday(state, Feature.SPILLS)) 1 else 0
                kotlin.math.ceil(usual * spillsSettled).toInt().coerceAtLeast(first).coerceAtMost(ServiceFloor.spillSpots.size)
            }
            val spots = ServiceFloor.spillSpots.shuffledWith(rng)
            val messes = (0 until spillCount).map { k ->
                val after = later[rng.nextInt(later.size)].arriveAt
                Mess(id = k, at = spots[k], appearsAt = after + 12f + rng.nextFloat() * 10f)
            }

            // Every table is the player's. Everyone hired works: servers run food, dishwashers and bussers clear up.
            val helpers = state.employees.filter { it.status == EmployeeStatus.ACTIVE && (it.role == Role.SERVER || it.role == Role.MANAGER) }
            val allTables = (0 until tableCount).toSet()
            val waiters = listOf(
                Waiter(PLAYER_ID, isPlayer = true, tables = allTables, speed = PLAYER_SPEED, restSpot = ServiceFloor.pass,
                    route = listOf(FloorPoint(ServiceFloor.pass.x, ServiceFloor.pass.y + 3f)), routeStart = 0f, routeEnd = 0f, errand = null),
            ) + helpers.mapIndexed { k, employee ->
                // Out at the ends of the counter, so they never stand in front of a table's speech bubble.
                val rest = FloorPoint(restSpot(SERVER_REST, k), ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null)
            } + state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.DISHWASHER }.mapIndexed { k, employee ->
                // Dishwashers wait by the dish station and come out to clear tables.
                val rest = FloorPoint(restSpot(DISHWASHER_REST, k), ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null, kind = Kind.DISHWASHER)
            } + state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.BARTENDER }.mapIndexed { k, employee ->
                // Bartenders wait by the bar for tables that want a drink.
                val rest = FloorPoint(restSpot(BARTENDER_REST, k), ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null, kind = Kind.BARTENDER)
            } + state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.BUSSER }.mapIndexed { k, employee ->
                // Bussers wait at the left end of the counter, quick on their feet.
                val rest = FloorPoint(restSpot(BUSSER_REST, k), ServiceFloor.pass.y + 1f)
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
            // (A fridge can only give up mid-service from day seventeen, and rarely on the first few of those nights.)
            val fridgeBreaksAt = if (fridge != null && !fridgeBrokenAtOpen && Fridge.isWorn(fridge) && Progression.has(state, Feature.FRIDGE_BREAKS)) {
                val chance = (0.25f + 0.5f * (Fridge.WORN - fridge.condition) / Fridge.WORN.toFloat()) * Progression.strength(state, Feature.FRIDGE_BREAKS)
                if (rng.nextFloat() < chance) 35f + rng.nextFloat() * 60f else null
            } else {
                null
            }

            // Now and then a special guest comes in (rolled last, so it doesn't change anything else about the night):
            // the celebrity from day twelve, the critic from fourteen, the inspector from twenty-three, rarely at first.
            val specialKinds = SpecialGuest.entries.filter { Progression.has(state, featureOf(it)) }
            val specialChance = SPECIAL_CHANCE * Progression.strength(state, Feature.CELEBRITY)
            // On the day a kind of special guest is first expected, they're sure to come.
            val debutGuest = specialKinds.firstOrNull { Progression.debutsToday(state, featureOf(it)) }
            val specialRoll = rng.nextFloat()
            val specialParties = if (specialKinds.isNotEmpty() && parties.size > 3 && (specialRoll < specialChance || debutGuest != null)) {
                val who = debutGuest ?: specialKinds[rng.nextInt(specialKinds.size)]
                val index = 2 + rng.nextInt(parties.size - 2)
                parties.mapIndexed { k, p -> if (k == index) p.copy(special = who) else p }
            } else {
                parties
            }

            // Some nights, something goes wrong (rolled last, after everything else about the night).
            // Disasters start on day fifteen, one kind at a time, and get a little more likely as the run goes on.
            val chaosKinds = ChaosKind.entries.filter { Progression.has(state, featureOf(it)) }
            val chaosChance = dials.chaosChance
            fun chaosAt(kind: ChaosKind) = when (kind) {
                ChaosKind.RAT -> FloorPoint(50f, 106f)
                ChaosKind.DOG -> ServiceFloor.layout(tableCount).stand(rng.nextInt(tableCount)).let { FloorPoint(it.x, it.y - 2f) }
                ChaosKind.PAN_FIRE -> FloorPoint(40f, 17f)
                ChaosKind.POWER_CUT -> ServiceFloor.fuseBox
            }
            // The day each kind of trouble is first expected, it's sure to happen (the receipt warned you).
            // So is the first night with two lots of trouble.
            val doubleDebut = Progression.debutsToday(state, Feature.DOUBLE_TROUBLE)
            val debutTrouble = chaosKinds.firstOrNull { Progression.debutsToday(state, featureOf(it)) }
            val chaosRoll = rng.nextFloat()
            val chaos = if (chaosKinds.isNotEmpty() && (chaosRoll < chaosChance || debutTrouble != null || doubleDebut)) {
                val kind = debutTrouble ?: chaosKinds[rng.nextInt(chaosKinds.size)]
                val startsAt = 30f + rng.nextFloat() * 50f
                Chaos(kind, chaosAt(kind), startsAt)
            } else {
                null
            }

            // From day ten, some parties of two are a grown-up out with a child: only the odd one at first
            // (rolled after everything else).
            val familyChance = FAMILY_CHANCE * Progression.strength(state, Feature.FAMILIES)
            val rolledFamilies = if (familyChance > 0f) {
                specialParties.map { p -> if (p.guests.size == 2 && p.special == null && rng.nextFloat() < familyChance) p.copy(family = true) else p }
            } else {
                specialParties
            }
            // On the first night families come, as promised, at least one does.
            val firstFamily = rolledFamilies.indexOfFirst { it.guests.size == 2 && it.special == null }
            val withFamilies = if (Progression.debutsToday(state, Feature.FAMILIES) && rolledFamilies.none { it.family } && firstFamily >= 0) {
                rolledFamilies.mapIndexed { k, p -> if (k == firstFamily) p.copy(family = true) else p }
            } else {
                rolledFamilies
            }

            // Which plates a grumpy chef burns or a happy one makes special (rolled last of all).
            val chefLuck = rng.nextFloat()
            // The cat moves in on day eight: at first it only drops by now and then, and soon it's there every night.
            // (On its very first day, as promised, it's sure to come.)
            val catTonight = rng.nextFloat() < Progression.strength(state, Feature.CAT) || Progression.debutsToday(state, Feature.CAT)
            // From day twenty-six, now and then a second thing goes wrong later in the night (rolled last of all).
            val otherKinds = chaosKinds.filter { it != chaos?.kind }
            val doubleRoll = rng.nextFloat()
            val laterChaos = if (chaos != null && otherKinds.isNotEmpty() && (doubleRoll < DOUBLE_TROUBLE_CHANCE * Progression.strength(state, Feature.DOUBLE_TROUBLE) || doubleDebut)) {
                val kind = otherKinds[rng.nextInt(otherKinds.size)]
                Chaos(kind, chaosAt(kind), chaos.startsAt + 40f + rng.nextFloat() * 30f)
            } else {
                null
            }

            // Who has a favourite dish (rolled after everything else, so nothing above changes).
            val withFavourites = withFamilies.map { it.withFavourites((state.menu + RecipeBook.dishes).distinctBy { d -> d.id }, rng) }

            return ServiceNight(
                time = 0f,
                parties = withFavourites,
                chefMood = ChefMood.of(state),
                pickiness = dials.pickiness,
                chefLuck = chefLuck,
                waiters = waiters,
                tripAt = StaffSlips.planFor(state),
                requestStrength = Progression.strength(state, Feature.GUEST_REQUESTS),
                requestDebutPending = Progression.debutsToday(state, Feature.GUEST_REQUESTS),
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
                cat = catTonight,
                decorJoy = com.recipefordisaster.domain.restaurant.Decoration.joyOf(state.decorations),
                laterChaos = laterChaos,
                introduced = state.introductionsSeen,
                fridgeBroken = fridgeBrokenAtOpen,
                hosted = state.employees.any { it.status == EmployeeStatus.ACTIVE && it.role == Role.HOST },
            )
        }

        /** Where each kind of helper waits along the counter when there's nothing to do, the first ones first. */
        private val SERVER_REST = listOf(10f, 90f, 18f, 82f)
        private val DISHWASHER_REST = listOf(78f, 70f, 62f)
        private val BUSSER_REST = listOf(30f, 22f, 38f)
        private val BARTENDER_REST = listOf(36f, 42f)

        /** The [k]th helper's waiting spot: more helpers than spots share them, a step apart. */
        private fun restSpot(spots: List<Float>, k: Int): Float = spots[k % spots.size] + (k / spots.size) * 3f

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

        /** How much happier a guest is with their favourite dish, and the extra tip they leave for it. */
        private const val FAVOURITE_JOY = 10
        private const val FAVOURITE_TIP = 2L

        /** About this share of ordinary guests have a favourite dish. */
        private const val FAVOURITE_SHARE = 0.6f

        /**
         * Gives each guest in the party a favourite, out of every dish there is (on the menu or not):
         * children love fries, the critic wants the fish, the celebrity the lasagna, and most other
         * guests have one of their own. If their favourite is on tonight's menu, it's what they'll order.
         */
        private fun Party.withFavourites(dishes: List<Dish>, rng: RandomSource): Party {
            fun known(id: DishId) = id.takeIf { wanted -> dishes.any { it.id == wanted } }
            val favourites = guests.mapIndexedNotNull { seat, guest ->
                val favourite = when {
                    seat == 0 && special == SpecialGuest.CRITIC -> known(RecipeBook.FISH_DISH)
                    seat == 0 && special == SpecialGuest.CELEBRITY -> known(RecipeBook.LASAGNA_DISH)
                    seat == 1 && family -> known(RecipeBook.FRIES_DISH)
                    else -> null
                } ?: dishes.takeIf { it.isNotEmpty() && rng.nextFloat() < FAVOURITE_SHARE }?.let { it[rng.nextInt(it.size)].id }
                favourite?.let { guest to it }
            }.toMap()
            val ordered = guests.zip(preferences).map { (guest, prefs) ->
                val favourite = favourites[guest]
                if (favourite != null && favourite in prefs) listOf(favourite) + (prefs - favourite) else prefs
            }
            return copy(favourites = favourites, preferences = ordered)
        }

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

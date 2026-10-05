package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.EquipmentOperations
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
) {

    enum class Stage {
        NOT_YET_ARRIVED,
        QUEUEING,
        WALKING_TO_TABLE,
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
    ) {
        val seated: Boolean get() = stage in Stage.READY_TO_ORDER..Stage.LEAVING_ANGRY
        val occupiesTable: Boolean get() = table != null && stage in Stage.WALKING_TO_TABLE..Stage.LEAVING_ANGRY
    }

    /** Something a waiter can hold in one hand. */
    sealed interface HandItem {
        /** A party's food (one hand holds a whole table's order). */
        data class Plate(val partyId: Int) : HandItem
    }

    sealed interface Errand {
        data class TakeOrder(val table: Int) : Errand
        data object VisitPass : Errand
        data class Serve(val table: Int) : Errand
        data object Rest : Errand
    }

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
    ) {
        /** Party ids whose plates this waiter is carrying. */
        val plates: List<Int> get() = hands.filterIsInstance<HandItem.Plate>().map { it.partyId }
        val freeHands: Int get() = (HANDS - hands.size).coerceAtLeast(0)
        fun position(time: Float): FloorPoint = when {
            time >= routeEnd || routeEnd <= routeStart -> route.last()
            else -> ServiceFloor.along(route, (time - routeStart) / (routeEnd - routeStart))
        }
        val walking: Boolean get() = errand != null
    }

    val finished: Boolean get() = parties.all { it.stage == Stage.DONE }

    val player: Waiter get() = waiters.first { it.isPlayer }

    /** Coins taken so far tonight. */
    val takings: Long get() = results.values.sumOf { it.dish?.sellingPrice ?: 0 }

    fun partyAt(table: Int): Party? = parties.firstOrNull { it.table == table && it.occupiesTable }

    // ------------------------------------------------------------ the player's commands

    /** Walk to a table: take their order if they're ready, or serve them if you're carrying their food. */
    fun tapTable(table: Int): ServiceNight = send(player.id, Errand.TakeOrder(table).takeUnless { player.plates.any { partyTable(it) == table } } ?: Errand.Serve(table))

    /** Walk to the pass: hand in any tickets you're holding and pick up plates that are ready for your tables. */
    fun tapPass(): ServiceNight = send(player.id, Errand.VisitPass)

    private fun partyTable(partyId: Int): Int? = parties.firstOrNull { it.id == partyId }?.table

    private fun send(waiterId: String, errand: Errand): ServiceNight {
        val waiter = waiters.first { it.id == waiterId }
        val here = waiter.position(time)
        val destination = when (errand) {
            is Errand.TakeOrder -> ServiceFloor.stand(errand.table)
            is Errand.Serve -> ServiceFloor.stand(errand.table)
            Errand.VisitPass -> ServiceFloor.pass
            Errand.Rest -> waiter.restSpot
        }
        val route = ServiceFloor.route(here, destination)
        val duration = ServiceFloor.length(route) / waiter.speed
        val moved = waiter.copy(route = route, routeStart = time, routeEnd = time + duration, errand = errand)
        return copy(waiters = waiters.map { if (it.id == waiterId) moved else it })
    }

    // ------------------------------------------------------------ time passing

    fun advance(dt: Float): ServiceNight {
        if (finished) return this
        var night = copy(time = time + dt)
        night = night.arrivals()
        night = night.seatParties()
        night = night.checkPatience()
        night = night.runKitchen()
        night = night.finishMeals()
        night = night.moveWaiters()
        night = night.directStaff()
        if (night.time > HARD_STOP) night = night.closeUp()
        return night
    }

    private fun updateParty(id: Int, transform: (Party) -> Party) = copy(parties = parties.map { if (it.id == id) transform(it) else it })

    private fun arrivals(): ServiceNight {
        var night = this
        for (party in parties.filter { it.stage == Stage.NOT_YET_ARRIVED && it.arriveAt <= time }.sortedBy { it.arriveAt }) {
            // No more than a couple of parties crowd the door. Others hold off — and if they'd have to
            // hold off too long, they go somewhere else tonight.
            if (night.parties.count { it.stage == Stage.QUEUEING } >= MAX_QUEUE) {
                if (time - party.arriveAt > GIVE_UP_COMING) night = night.stayAway(party)
                continue
            }
            // Anyone with nothing on the menu they can eat or afford reads it at the door and leaves.
            val (staying, leaving) = party.guests.zip(party.preferences).partition { (_, prefs) -> prefs.isNotEmpty() }
            var results = night.results
            leaving.forEach { (guest, _) -> results = results + (guest to missed(guest, MissedMealReason.NOTHING_SUITABLE)) }
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
        party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, MissedMealReason.TIRED_OF_WAITING)) }
        return copy(results = results).updateParty(party.id) { it.copy(stage = Stage.DONE, stageSince = time) }
    }

    private fun seatParties(): ServiceNight {
        var night = this
        val queue = night.parties.filter { it.stage == Stage.QUEUEING }.sortedBy { it.arriveAt }
        for (party in queue) {
            val free = (0 until ServiceFloor.TABLE_COUNT).firstOrNull { t -> night.parties.none { it.table == t && it.occupiesTable } } ?: break
            val walk = ServiceFloor.length(ServiceFloor.route(ServiceFloor.door, ServiceFloor.stand(free))) / GUEST_SPEED
            night = night.updateParty(party.id) { it.copy(stage = Stage.WALKING_TO_TABLE, stageSince = time, table = free, until = time + walk) }
        }
        // Parties that have reached their table are ready to order.
        night.parties.filter { it.stage == Stage.WALKING_TO_TABLE && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = Stage.READY_TO_ORDER, stageSince = time, seatedAt = time) }
        }
        return night
    }

    private fun checkPatience(): ServiceNight {
        var night = this
        for (party in parties) {
            val waited = time - party.stageSince
            val givesUp = when (party.stage) {
                Stage.QUEUEING -> waited > party.patience * 0.8f
                Stage.READY_TO_ORDER -> waited > party.patience
                Stage.ORDER_TAKEN, Stage.IN_KITCHEN, Stage.COOKING, Stage.READY_AT_PASS, Stage.CARRIED -> time - party.orderedAt > party.patience * FOOD_PATIENCE
                else -> false
            }
            if (givesUp) night = night.stormOut(party.id, MissedMealReason.TIRED_OF_WAITING)
        }
        return night
    }

    /** A party gives up: everyone without a result leaves hungry, and any ticket or plate of theirs disappears. */
    private fun stormOut(partyId: Int, reason: MissedMealReason): ServiceNight {
        val party = parties.first { it.id == partyId }
        var results = results
        party.guests.forEach { guest -> if (guest !in results) results = results + (guest to missed(guest, reason)) }
        val inLine = party.stage == Stage.QUEUEING
        return copy(
            results = results,
            waiters = waiters.map { it.copy(tickets = it.tickets - partyId, hands = it.hands - HandItem.Plate(partyId)) },
        ).updateParty(partyId) {
            if (inLine) it.copy(stage = Stage.DONE, stageSince = time) else it.copy(stage = Stage.LEAVING_ANGRY, stageSince = time, until = time + LEAVE, heldBy = null)
        }
    }

    private fun runKitchen(): ServiceNight {
        var night = this
        // Finished cooking: plates go up on the pass.
        night.parties.filter { it.stage == Stage.COOKING && time >= it.until }.forEach { party ->
            night = night.updateParty(party.id) { it.copy(stage = Stage.READY_AT_PASS, stageSince = time) }
        }
        // Free cooks pick up the oldest tickets.
        val busy = night.parties.count { it.stage == Stage.COOKING }
        val waiting = night.parties.filter { it.stage == Stage.IN_KITCHEN }.sortedBy { it.stageSince }
        for (party in waiting.take((night.kitchenSlots - busy).coerceAtLeast(0))) {
            // Only now do we find out whether there's stock for each dish.
            var stock = night.inventory
            val cooked = party.orders.map { dishId ->
                val dish = dishId?.let { id -> night.menu.firstOrNull { it.id == id } }
                if (dish != null && InventoryOperations.canFulfill(stock, dish.recipe)) {
                    stock = InventoryOperations.consume(stock, dish.recipe)
                    dishId
                } else {
                    null
                }
            }
            var results = night.results
            party.guests.zip(cooked).forEach { (guest, dish) -> if (dish == null && guest !in results) results = results + (guest to missed(guest, MissedMealReason.OUT_OF_STOCK)) }
            night = night.copy(inventory = stock, results = results)
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
                        val satisfaction = ServiceSimulator.resolveSatisfaction(customer, dish, waitedMinutes, kitchenQualityBonus)
                        results = results + (guest to CustomerServiceOutcome(customer, dish, satisfaction, waitedMinutes))
                    }
                    night = night.copy(results = results).updateParty(party.id) { it.copy(stage = Stage.LEAVING_HAPPY, stageSince = time, until = time + LEAVE) }
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
            is Errand.TakeOrder -> {
                val party = night.partyAt(errand.table)
                if (party != null && party.stage == Stage.READY_TO_ORDER && errand.table in waiter.tables) {
                    val orders = party.preferences.map { prefs -> prefs.firstOrNull { id -> night.menu.any { it.id == id && it.available } } }
                    night = night.updateParty(party.id) { it.copy(stage = Stage.ORDER_TAKEN, stageSince = time, orderedAt = time, orders = orders, heldBy = waiterId) }
                    updateWaiter { it.copy(tickets = it.tickets + party.id) }
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
                // the player has left waiting a while, so they help out rather than race the player.
                val ready = night.parties
                    .filter { it.stage == Stage.READY_AT_PASS && (waiter.isPlayer || time - it.stageSince >= RUNNER_DELAY) }
                    .sortedBy { it.stageSince }
                    .take(waiter.freeHands)
                ready.forEach { party -> night = night.updateParty(party.id) { it.copy(stage = Stage.CARRIED, stageSince = time, heldBy = waiterId) } }
                updateWaiter { it.copy(hands = it.hands + ready.map { p -> HandItem.Plate(p.id) }) }
            }
            Errand.Rest, null -> {}
        }
        return night
    }

    /** Food runners, whenever they're free: deliver what they're carrying, or fetch plates that have been waiting too long. */
    private fun directStaff(): ServiceNight {
        var night = this
        for (waiter in waiters.filter { !it.isPlayer && it.errand == null }) {
            val next: Errand? = when {
                waiter.plates.isNotEmpty() -> night.parties.first { it.id == waiter.plates.first() }.table?.let { Errand.Serve(it) }
                night.parties.any { it.stage == Stage.READY_AT_PASS && time - it.stageSince >= RUNNER_DELAY } -> Errand.VisitPass
                else -> null
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

    private fun missed(guest: Int, reason: MissedMealReason) =
        CustomerServiceOutcome(arrivalOrder[guest], dish = null, satisfaction = ServiceSimulator.missedMealSatisfaction(reason), waitMinutes = 0.0, missedReason = reason)

    /** The night's results in the same shape the automatic night produces, so the day closes the same way. */
    fun result(): ServiceSimulator.ServiceResult {
        val outcomes = arrivalOrder.indices.map { results[it] ?: missed(it, MissedMealReason.TIRED_OF_WAITING) }
        val sold = outcomes.mapNotNull { it.dish?.id }.groupingBy { it }.eachCount()
        return ServiceSimulator.ServiceResult(outcomes = outcomes, dishesSold = sold, staffingRatio = staffingRatio, inventoryAfter = inventory)
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

        /** How long a party will hold off coming in before going somewhere else. */
        private const val GIVE_UP_COMING = 15f

        /** How long a plate sits on the pass before a runner takes it out instead of waiting for the player. */
        private const val RUNNER_DELAY = 3f
        private const val GUEST_SPEED = 32f
        private const val PLAYER_SPEED = 60f
        private const val STAFF_SPEED = 42f
        private const val EAT = 6f
        private const val LEAVE = 1.6f
        private const val COOK_BASE = 2f
        private const val FOOD_PATIENCE = 1.8f
        /** A sensible minimum from sitting down to being served; only waiting beyond it counts against you. */
        private const val EXPECTED_SERVICE = 10f
        private const val MINUTES_PER_SECOND = 1.2f
        private const val HARD_STOP = 260f

        /**
         * Opens the doors: sorts tonight's guests into parties with arrival
         * times and pre-rolled orders, puts any hired servers on as food
         * runners, and sets the kitchen's pace from the cooks on shift.
         */
        fun open(setup: ServiceSetup, rng: RandomSource): ServiceNight {
            val state = setup.morning.state
            val customers = setup.arrivals
            val menu = state.menu.filter { it.available }

            // Parties of one or two (mostly two), a new one roughly every seven or eight seconds.
            val groups = mutableListOf<List<Int>>()
            var i = 0
            while (i < customers.size) {
                val size = if (i + 1 < customers.size && rng.nextInt(3) != 0) 2 else 1
                groups += (i until i + size).toList()
                i += size
            }
            var nextArrival = 1.5f
            val parties = groups.mapIndexed { index, guests ->
                val arriveAt = nextArrival
                nextArrival += ARRIVAL_GAP + rng.nextFloat() * ARRIVAL_JITTER
                Party(
                    id = index,
                    guests = guests,
                    preferences = guests.map { g -> rankDishes(customers[g], menu, rng) },
                    arriveAt = arriveAt,
                    patience = guests.map { customers[it].patience }.average().toFloat() / MINUTES_PER_SECOND + 12f,
                )
            }

            // Every table is the player's. Up to two hired servers help out as food runners.
            val servers = state.employees.filter { it.status == EmployeeStatus.ACTIVE && (it.role == Role.SERVER || it.role == Role.MANAGER) }
            val helpers = servers.take(2)
            val allTables = (0 until ServiceFloor.TABLE_COUNT).toSet()
            val waiters = listOf(
                Waiter(PLAYER_ID, isPlayer = true, tables = allTables, speed = PLAYER_SPEED, restSpot = ServiceFloor.pass,
                    route = listOf(FloorPoint(ServiceFloor.pass.x, ServiceFloor.pass.y + 3f)), routeStart = 0f, routeEnd = 0f, errand = null),
            ) + helpers.mapIndexed { k, employee ->
                // Out at the ends of the counter, so they never stand in front of a table's speech bubble.
                val rest = FloorPoint(if (k == 0) 10f else 90f, ServiceFloor.pass.y + 1f)
                val pace = (EmployeePerformance.effectiveServiceSpeed(employee) / 50.0).toFloat().coerceIn(0.6f, 1.3f)
                Waiter(employee.id.value, isPlayer = false, tables = allTables, speed = STAFF_SPEED * pace, restSpot = rest,
                    route = listOf(rest), routeStart = 0f, routeEnd = 0f, errand = null)
            }
            // Kitchen pace: one dish at a time per cook, quicker with better cooks, slower with broken kit.
            val cooks = state.employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.COOK }
            val slots = cooks.size.coerceAtLeast(1)
            val skill = if (cooks.isEmpty()) 0.35f else (cooks.map { EmployeePerformance.effectiveServiceSpeed(it) }.average() / 45.0).toFloat().coerceIn(0.5f, 1.6f)
            val broken = state.equipment.count { EquipmentOperations.isBroken(it) }
            val secondsPerDish = 3.2f / skill * (if (broken > 0) 1.8f else 1f)

            val active = state.employees.count { it.status == EmployeeStatus.ACTIVE }
            return ServiceNight(
                time = 0f,
                parties = parties,
                waiters = waiters,
                inventory = state.inventory,
                kitchenSlots = slots,
                secondsPerDish = secondsPerDish,
                menu = state.menu,
                kitchenQualityBonus = KitchenModel.qualityBonus(state.employees, state.equipment, state.restaurant.cleanliness),
                staffingRatio = if (active == 0) (if (customers.isEmpty()) 0.0 else Double.MAX_VALUE) else customers.size.toDouble() / active,
                arrivalOrder = customers,
            )
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

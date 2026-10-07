package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.GameStateValidator
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceNightTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    /** A hired server (a new restaurant doesn't start with one). */
    private val aServer get() = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.SERVER)

    /** A restaurant with the full six tables (a brand-new one starts with only two). */
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    /** Lets the clock run in small steps, calling [player] each step so a test can play. */
    private fun play(night: ServiceNight, player: (ServiceNight) -> ServiceNight = { it }): ServiceNight {
        var n = night
        var steps = 0
        while (!n.finished && steps < 20_000) {
            n = player(n).advance(0.05f)
            steps++
        }
        return n
    }

    /** A decent player: serves plates first, hands in tickets, then takes the longest-waiting order, and clears up. */
    private fun busyPlayer(n: ServiceNight): ServiceNight {
        val me = n.player
        if (me.walking) return n
        if (me.dirtyDishes.isNotEmpty() && (me.freeHands == 0 || n.dirtyTables.isEmpty())) return n.tapDishStation()
        me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        if (me.tickets.isNotEmpty()) return n.tapPass()
        if (n.parties.any { it.stage == Stage.READY_AT_PASS && it.table in me.tables }) return n.tapPass()
        n.parties.filter { it.stage == Stage.READY_TO_ORDER && it.table in me.tables }.minByOrNull { it.stageSince }?.table?.let { return n.tapTable(it) }
        if (me.freeHands > 0) n.dirtyTables.firstOrNull()?.let { return n.tapTable(it) }
        return n
    }

    @Test
    fun `every guest gets exactly one result by the end of the night`() {
        val night = play(open(), ::busyPlayer)
        val result = night.result()
        assertEquals(night.arrivalOrder.size, result.outcomes.size)
        assertTrue(night.finished)
    }

    @Test
    fun `a player who does nothing feeds nobody at their own tables`() {
        val idle = play(open())
        val busy = play(open(), ::busyPlayer)
        assertTrue(busy.result().outcomes.count { it.dish != null } > idle.result().outcomes.count { it.dish != null })
    }

    @Test
    fun `ignored guests give up waiting rather than staying forever`() {
        val idle = play(open())
        assertTrue(idle.result().outcomes.any { it.missedReason == MissedMealReason.TIRED_OF_WAITING })
    }

    @Test
    fun `the player has two hands, and can carry two tables' food at once`() {
        // No food runners, so nobody else picks the plates up first.
        var n = open(start.copy(employees = start.employees.filter { it.role != com.recipefordisaster.domain.employee.Role.SERVER }), seed = 3)
        fun waitUntil(condition: (ServiceNight) -> Boolean) { var guard = 0; while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f) }
        fun walk() = waitUntil { !it.player.walking }

        // Take two tables' orders…
        repeat(2) {
            waitUntil { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
            n = n.tapTable(n.parties.first { it.stage == Stage.READY_TO_ORDER }.table!!)
            walk()
        }
        assertEquals(2, n.player.tickets.size)
        // …hand both in, wait for both plates, and collect them in one trip.
        n = n.tapPass(); walk()
        waitUntil { night -> night.parties.count { it.stage == Stage.READY_AT_PASS } >= 2 }
        n = n.tapPass(); walk()
        assertEquals(ServiceNight.HANDS, n.player.plates.size)
    }

    @Test
    fun `taking an order needs the player to actually walk to the table`() {
        var n = open()
        while (n.parties.none { it.stage == Stage.READY_TO_ORDER && it.table in n.player.tables }) n = n.advance(0.05f)
        val table = n.parties.first { it.stage == Stage.READY_TO_ORDER && it.table in n.player.tables }.table!!
        n = n.tapTable(table)
        assertTrue(n.player.walking)
        assertTrue(n.player.tickets.isEmpty())
        while (n.player.walking) n = n.advance(0.05f)
        assertEquals(1, n.player.tickets.size)
    }

    @Test
    fun `the player looks after every table`() {
        assertEquals((0 until ServiceFloor.TABLE_COUNT).toSet(), open().player.tables)
    }

    @Test
    fun `hired servers run plates out when the player leaves them on the pass`() {
        val withRunner = start.copy(employees = start.employees + listOf(aServer))
        // A player who takes orders and hands them in, but never collects food.
        val orderTaker: (ServiceNight) -> ServiceNight = { n ->
            val me = n.player
            when {
                me.walking -> n
                me.tickets.isNotEmpty() -> n.tapChef()
                else -> n.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { n.tapTable(it) } ?: n
            }
        }
        val alone = play(open(start.copy(employees = start.employees.filter { it.role != com.recipefordisaster.domain.employee.Role.SERVER })), orderTaker)
        val helped = play(open(withRunner), orderTaker)
        assertEquals(0, alone.result().outcomes.count { it.dish != null })
        assertTrue(helped.result().outcomes.count { it.dish != null } > 0)
    }

    @Test
    fun `a played night closes the day into a valid state with books that match`() {
        val setup = engine.openService(start, PlayerDecisions(), SeededRandomSource(3))
        val night = play(ServiceNight.open(setup, SeededRandomSource(4)), ::busyPlayer)
        val day = engine.closeService(setup, night.result(), SeededRandomSource(5))

        assertEquals(emptyList<String>(), GameStateValidator.validate(day.newState))
        assertEquals(night.takings, day.newState.ledger.history.last().revenue)
        assertEquals(start.day + 1, day.newState.day)
    }

    @Test
    fun `a quick player makes more money than a slow one`() {
        val quick = play(open(seed = 7), ::busyPlayer)
        var slowSteps = 0
        val slow = play(open(seed = 7)) { n -> if (slowSteps++ % 120 == 0) busyPlayer(n) else n }
        assertTrue("quick ${quick.takings} vs slow ${slow.takings}", quick.takings >= slow.takings)
    }

    @Test
    fun `no more than two parties ever wait by the door`() {
        var n = open()
        var worst = 0
        while (!n.finished) {
            worst = maxOf(worst, n.parties.count { it.stage == Stage.QUEUEING })
            n = n.advance(0.05f)
        }
        assertTrue("$worst parties queued at once", worst <= 2)
    }

    @Test
    fun `parties arrive several seconds apart`() {
        val arrivals = open().parties.map { it.arriveAt }
        arrivals.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 6f) }
    }

    @Test
    fun `a second tap queues up instead of interrupting`() {
        var n = open()
        n = n.tapDishStation()
        val firstDestination = n.player.route.last()
        n = n.tapPass()
        assertEquals(firstDestination, n.player.route.last())
        assertEquals(listOf<ServiceNight.Errand>(ServiceNight.Errand.VisitPass), n.player.queue)

        // Once the first stop is reached, the queued one starts.
        while (n.player.errand == ServiceNight.Errand.VisitDishStation) n = n.advance(0.05f)
        assertEquals(ServiceNight.Errand.VisitPass, n.player.errand)
        assertTrue(n.player.queue.isEmpty())
    }

    @Test
    fun `tapping the same stop twice doesn't queue it twice`() {
        var n = open().tapDishStation().tapPass().tapPass().tapDishStation()
        assertEquals(listOf<ServiceNight.Errand>(ServiceNight.Errand.VisitPass), n.player.queue)
    }

    @Test
    fun `guests leave dirty plates, and nobody sits at a dirty table`() {
        var n = open(start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK }))
        // Serve but never clear, with nobody to help: tables fill up with dirty plates and stay empty.
        n = play(n) { current ->
            val me = current.player
            when {
                me.walking -> current
                me.plates.isNotEmpty() -> current.parties.first { it.id == me.plates.first() }.table?.let { current.tapTable(it) } ?: current
                me.tickets.isNotEmpty() || current.parties.any { it.stage == Stage.READY_AT_PASS } -> current.tapPass()
                else -> current.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { current.tapTable(it) } ?: current
            }
        }
        assertTrue(n.dirtyTables.isNotEmpty())
    }

    @Test
    fun `clearing and washing up frees the table again`() {
        var n = open(start.copy(employees = start.employees.filter { it.role != com.recipefordisaster.domain.employee.Role.SERVER }))
        n = play(n, ::busyPlayer)
        assertTrue("dirty tables left: ${n.dirtyTables}", n.dirtyTables.size <= 1)
    }

    @Test
    fun `a hired dishwasher clears tables on their own`() {
        val washer = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.DISHWASHER)
        val withWasher = start.copy(employees = start.employees + washer)
        val night = open(withWasher)
        assertTrue(night.waiters.any { it.kind == ServiceNight.Kind.DISHWASHER })
        // The player serves but never clears; the dishwasher should keep tables clean.
        val served = play(night) { current ->
            val me = current.player
            when {
                me.walking -> current
                me.plates.isNotEmpty() -> current.parties.first { it.id == me.plates.first() }.table?.let { current.tapTable(it) } ?: current
                me.tickets.isNotEmpty() || current.parties.any { it.stage == Stage.READY_AT_PASS } -> current.tapPass()
                else -> current.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { current.tapTable(it) } ?: current
            }
        }
        assertTrue("dirty tables left: ${served.dirtyTables}", served.dirtyTables.size <= 1)
    }

    /** A few nights in (when spills start), with no dishwasher to mop them. */
    private val noWashers get() = start.copy(day = 4, employees = start.employees.filter { it.role != com.recipefordisaster.domain.employee.Role.DISHWASHER })

    @Test
    fun `spills happen during the night and stay until someone mops them`() {
        val night = open(noWashers)
        assertTrue(night.messes.isNotEmpty())
        assertTrue(night.messesOnFloor.isEmpty())
        assertTrue(play(night).messesOnFloor.isNotEmpty())
    }

    @Test
    fun `mopping a spill needs the mop from the bucket`() {
        var n = open(noWashers)
        while (n.messesOnFloor.isEmpty()) n = n.advance(0.05f)
        fun walk() { while (n.player.walking) n = n.advance(0.05f) }
        val mess = n.messesOnFloor.first()

        // Empty-handed, walking over does nothing.
        n = n.tapMess(mess.id); walk()
        assertTrue(n.messesOnFloor.any { it.id == mess.id })

        // With the mop (which takes a hand) it gets cleaned up…
        n = n.tapMopBucket(); walk()
        assertTrue(n.player.holdingMop)
        assertEquals(ServiceNight.HANDS - 1, n.player.freeHands)
        n = n.tapMess(mess.id); walk()
        assertTrue(n.messesOnFloor.none { it.id == mess.id })

        // …and tapping the bucket again puts the mop back.
        n = n.tapMopBucket(); walk()
        assertTrue(!n.player.holdingMop)
    }

    @Test
    fun `a hired dishwasher mops spills on their own`() {
        val washer = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.DISHWASHER)
        val night = play(open(start.copy(day = 4, employees = start.employees + washer)), ::busyPlayer)
        assertTrue(night.messes.isNotEmpty())
        assertTrue("spills left: ${night.messesOnFloor}", night.messesOnFloor.isEmpty())
    }

    @Test
    fun `guests are less happy when the floor is dirty`() {
        val night = open(noWashers)
        val clean = play(night.copy(messes = emptyList()), ::busyPlayer)
        val filthy = play(night.copy(messes = ServiceFloor.spillSpots.take(3).mapIndexed { k, at -> ServiceNight.Mess(k, at, appearsAt = 0f) }), ::busyPlayer)
        fun happiness(n: ServiceNight) = n.result().outcomes.filter { it.dish != null }.map { it.satisfaction }.average()
        assertTrue(happiness(filthy) < happiness(clean))
    }

    @Test
    fun `a host keeps guests waiting patiently for longer`() {
        val host = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.HOST)
        val hostedNight = open(start.copy(employees = start.employees + host))
        assertTrue(hostedNight.hosted)
        fun stillWaiting(night: ServiceNight): Boolean {
            var n = night
            while (n.parties.none { it.stage == Stage.READY_TO_ORDER }) n = n.advance(0.05f)
            val party = n.parties.first { it.stage == Stage.READY_TO_ORDER }
            val until = n.time + party.patience * 1.2f
            while (n.time < until) n = n.advance(0.05f)
            return n.parties.first { it.id == party.id }.stage == Stage.READY_TO_ORDER
        }
        assertTrue(stillWaiting(hostedNight))
        assertTrue(!stillWaiting(hostedNight.copy(hosted = false)))
    }

    @Test
    fun `a busser clears and washes but leaves spills alone`() {
        val busser = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.BUSSER)
        val night = open(noWashers.copy(employees = noWashers.employees + busser))
        assertTrue(night.waiters.any { it.kind == ServiceNight.Kind.BUSSER })
        val served = play(night) { current ->
            val me = current.player
            when {
                me.walking -> current
                me.plates.isNotEmpty() -> current.parties.first { it.id == me.plates.first() }.table?.let { current.tapTable(it) } ?: current
                me.tickets.isNotEmpty() || current.parties.any { it.stage == Stage.READY_AT_PASS } -> current.tapPass()
                else -> current.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { current.tapTable(it) } ?: current
            }
        }
        assertTrue("dirty tables left: ${served.dirtyTables}", served.dirtyTables.size <= 1)
        assertTrue(served.messesOnFloor.isNotEmpty())
    }

    @Test
    fun `with an empty pantry guests leave at the door instead of storming out after ordering`() {
        val empty = start.copy(inventory = start.inventory.copy(ingredients = start.inventory.ingredients.mapValues { it.value.copy(quantityOnHand = 0.0) }))
        var n = open(empty)
        assertTrue(!n.kitchenHasFood)
        var everSeated = false
        while (!n.finished) {
            n = busyPlayer(n).advance(0.05f)
            if (n.parties.any { it.table != null }) everSeated = true
        }
        assertTrue(!everSeated)
        assertTrue(n.result().outcomes.all { it.missedReason == MissedMealReason.OUT_OF_STOCK || it.missedReason == MissedMealReason.NOTHING_SUITABLE })
    }

    @Test
    fun `once an order is taken its food is never found missing in the kitchen`() {
        // Barely any stock: a few dishes' worth at most.
        val scarce = start.copy(inventory = start.inventory.copy(ingredients = start.inventory.ingredients.mapValues { it.value.copy(quantityOnHand = it.value.quantityOnHand.coerceAtMost(0.6)) }))
        var n = open(scarce)
        val ordered = mutableSetOf<Int>()
        while (!n.finished) {
            n = busyPlayer(n).advance(0.05f)
            n.parties.filter { it.stage >= Stage.ORDER_TAKEN && it.orders.any { o -> o != null } }.forEach { ordered += it.id }
        }
        val fedOrTired = n.parties.filter { it.id in ordered }.flatMap { it.guests.zip(it.orders) }.filter { it.second != null }
        assertTrue(fedOrTired.isNotEmpty())
        assertTrue(fedOrTired.none { (guest, _) -> n.results.getValue(guest).missedReason == MissedMealReason.OUT_OF_STOCK })
    }

    @Test
    fun `guests who give up waiting are reported as giving up, not as a kitchen with too few cooks`() {
        val setup = engine.openService(start, PlayerDecisions(), SeededRandomSource(1L))
        val night = play(ServiceNight.open(setup, SeededRandomSource(2L)))
        val summary = engine.closeService(setup, night.result(), SeededRandomSource(3L)).summary!!
        assertTrue(summary.gaveUpWaiting > 0)
        assertEquals(0, summary.unfedKitchenFull)
    }

    @Test
    fun `a night half-way through saves and loads back exactly, and carries on the same`() {
        val setup = engine.openService(start, PlayerDecisions(), SeededRandomSource(1L))
        var n = ServiceNight.open(setup, SeededRandomSource(2L))
        repeat(900) { n = busyPlayer(n).advance(0.05f) } // 45 seconds in: guests seated, food cooking, plates in hand
        val saved = NightInProgress(setup, n, com.recipefordisaster.domain.decision.DecisionSpending(), 7L)
        val json = com.recipefordisaster.domain.simulation.GameStateJson.instance
        val loaded = json.decodeFromString(NightInProgress.serializer(), json.encodeToString(NightInProgress.serializer(), saved))
        assertEquals(saved, loaded)
        assertEquals(play(n, ::busyPlayer).result(), play(loaded.night, ::busyPlayer).result())
    }

    @Test
    fun `tapping an empty table does nothing`() {
        val n = open()
        assertEquals(n, n.tapTable(3))
    }

    @Test
    fun `tapping a table while its guests are still sitting down takes their order once they're seated`() {
        var n = open()
        while (n.parties.none { it.stage == Stage.WALKING_TO_TABLE }) n = n.advance(0.05f)
        val party = n.parties.first { it.stage == Stage.WALKING_TO_TABLE }
        n = n.tapTable(party.table!!)
        while (n.player.walking) n = n.advance(0.05f)
        assertEquals(listOf(party.id), n.player.tickets)
    }

    @Test
    fun `with every table dirty, guests end up waiting at the door with nowhere to sit`() {
        var n = open(start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK }))
        var seen = false
        // Serve but never clear.
        while (!n.finished) {
            val me = n.player
            n = when {
                me.walking -> n
                me.plates.isNotEmpty() -> n.parties.first { it.id == me.plates.first() }.table?.let { n.tapTable(it) } ?: n
                me.tickets.isNotEmpty() || n.parties.any { it.stage == Stage.READY_AT_PASS } -> n.tapPass()
                else -> n.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { n.tapTable(it) } ?: n
            }.advance(0.05f)
            if (n.waitingAtDoor.isNotEmpty() && !n.hasFreeTable && n.dirtyTables.isNotEmpty()) seen = true
        }
        assertTrue(seen)
    }

    @Test
    fun `spills are few, and never while the first guests are arriving`() {
        (1L..20L).forEach { seed ->
            val n = open(noWashers, seed)
            assertTrue(n.messes.size <= n.parties.size / 6 + 1)
            val firstQuarter = n.parties[(n.parties.size / 4).coerceAtMost(n.parties.lastIndex)].arriveAt
            assertTrue(n.messes.all { it.appearsAt > firstQuarter })
        }
    }

    @Test
    fun `a server with no food to run clears tables that have sat dirty a while`() {
        val noHelpers = start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK })
        val withServer = start.copy(employees = start.employees + listOf(aServer))
        val servesOnly: (ServiceNight) -> ServiceNight = { current ->
            val me = current.player
            when {
                me.walking -> current
                me.plates.isNotEmpty() -> current.parties.first { it.id == me.plates.first() }.table?.let { current.tapTable(it) } ?: current
                me.tickets.isNotEmpty() || current.parties.any { it.stage == Stage.READY_AT_PASS } -> current.tapPass()
                else -> current.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { current.tapTable(it) } ?: current
            }
        }
        val alone = play(open(noHelpers), servesOnly)
        val helped = play(open(withServer), servesOnly)
        assertTrue(helped.result().outcomes.count { it.dish != null } > alone.result().outcomes.count { it.dish != null })
        assertTrue(helped.dirtyTables.size < alone.dirtyTables.size)
    }

    @Test
    fun `the first nights have no spills, and first-night guests are more patient`() {
        assertTrue(open(start).messes.isEmpty())
        assertTrue(open(start.copy(day = 2)).messes.isEmpty())
        val firstNight = open(start).parties.first().patience
        val later = open(start.copy(day = 4)).parties.first().patience
        assertTrue(firstNight > later)
    }

    @Test
    fun `everyone who gave up is recorded with what they were waiting for`() {
        val n = play(open())
        val gaveUp = n.result().outcomes.count { it.missedReason == MissedMealReason.TIRED_OF_WAITING }
        assertTrue(gaveUp > 0)
        assertEquals(gaveUp, n.gaveUpCounts().values.sum())
    }

    @Test
    fun `guests only ever sit at the tables the restaurant has`() {
        val small = start.copy(restaurant = start.restaurant.copy(tables = 2))
        var n = open(small)
        while (!n.finished) {
            n = busyPlayer(n).advance(0.05f)
            assertTrue(n.parties.all { it.table == null || it.table!! < 2 })
        }
        assertEquals(setOf(0, 1), n.player.tables)
    }

    @Test
    fun `a packed room of twelve tables plays a full night`() {
        val big = start.copy(restaurant = start.restaurant.copy(tables = 12, reputation = 80))
        val n = play(open(big), ::busyPlayer)
        assertTrue(n.finished)
        assertTrue(n.parties.mapNotNull { it.table }.any { it >= 6 })
        assertTrue(n.result().outcomes.count { it.dish != null } > 20)
    }

    private fun waitFor(night: ServiceNight, condition: (ServiceNight) -> Boolean): ServiceNight {
        var n = night
        var guard = 0
        while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f)
        return n
    }

    @Test
    fun `tapping the chef only hands orders in, and tapping a plate only picks that plate up`() {
        val noRunners = start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK })
        var n = waitFor(open(noRunners)) { night -> night.parties.count { it.stage == Stage.READY_TO_ORDER } >= 2 }
        val (first, second) = n.parties.filter { it.stage == Stage.READY_TO_ORDER }.take(2)
        n = n.tapTable(first.table!!).tapTable(second.table!!)
        n = waitFor(n) { it.player.tickets.size == 2 && !it.player.walking }

        // The chef takes the orders; nothing is picked up.
        n = waitFor(n.tapChef()) { !it.player.walking }
        assertTrue(n.player.tickets.isEmpty())
        n = waitFor(n) { night -> night.parties.count { it.stage == Stage.READY_AT_PASS } >= 2 }

        // Handing in again with food waiting picks nothing up.
        n = waitFor(n.tapChef()) { !it.player.walking }
        assertTrue(n.player.plates.isEmpty())

        // Tapping one plate takes just that one.
        n = waitFor(n.tapPlate(second.id)) { !it.player.walking }
        assertEquals(listOf(second.id), n.player.plates)
        n = waitFor(n.tapPlate(first.id)) { !it.player.walking }
        assertEquals(setOf(first.id, second.id), n.player.plates.toSet())
    }

    @Test
    fun `a runner leaves alone a plate the player is on their way to collect`() {
        val withRunner = start.copy(employees = start.employees + listOf(aServer))
        var n = waitFor(open(withRunner)) { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
        val party = n.parties.first { it.stage == Stage.READY_TO_ORDER }
        n = waitFor(n.tapTable(party.table!!)) { !it.player.walking }
        n = waitFor(n.tapChef()) { !it.player.walking }
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage == Stage.READY_AT_PASS }
        // Let it sit a while, then go to the dish station and back: by the time the player is back,
        // the plate has waited long enough that a runner would otherwise have taken it.
        val readyAt = n.time
        n = waitFor(n) { it.time >= readyAt + 2.5f }
        n = n.tapDishStation().tapPlate(party.id)
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage != Stage.READY_AT_PASS }
        assertEquals(ServiceNight.PLAYER_ID, n.parties.first { it.id == party.id }.heldBy)
    }

    @Test
    fun `handing in walks to the chef, and picking up walks to the plate`() {
        val noRunners = start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK })
        var n = waitFor(open(noRunners)) { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
        val party = n.parties.first { it.stage == Stage.READY_TO_ORDER }
        n = waitFor(n.tapTable(party.table!!)) { !it.player.walking }
        n = n.tapChef()
        assertEquals(ServiceFloor.chef, n.player.route.last())
        n = waitFor(n) { !it.player.walking }
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage == Stage.READY_AT_PASS }
        n = n.tapPlate(party.id)
        assertEquals(ServiceFloor.plateStand(0), n.player.route.last())
    }

    @Test
    fun `the night only closes once the last table is cleared and washed up`() {
        val noHelpers = start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK })
        var n = open(noHelpers)
        // Serve everyone but never clear: the guests all go, yet the night stays open for a while.
        while (!n.guestsGone) {
            val me = n.player
            n = when {
                me.walking -> n
                me.plates.isNotEmpty() -> n.parties.first { it.id == me.plates.first() }.table?.let { n.tapTable(it) } ?: n
                me.tickets.isNotEmpty() -> n.tapChef()
                n.parties.any { it.stage == Stage.READY_AT_PASS } && me.freeHands > 0 -> n.tapPlate(n.parties.first { it.stage == Stage.READY_AT_PASS }.id)
                else -> n.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { n.tapTable(it) } ?: n
            }.advance(0.05f)
        }
        assertTrue(n.dirtyTables.isNotEmpty())
        assertTrue(!n.finished)
        // Now clear up: once it's all washed, it closes.
        n = play(n, ::busyPlayer)
        assertTrue(n.finished)
        assertTrue(n.tidy)
    }

    @Test
    fun `a night nobody clears up still closes in the end`() {
        val noHelpers = start.copy(employees = start.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK })
        val n = play(open(noHelpers))
        assertTrue(n.finished)
    }

    @Test
    fun `guests read the menu for a few seconds after sitting down before they're ready to order`() {
        var n = open()
        n = waitFor(n) { night -> night.parties.any { it.stage == Stage.DECIDING } }
        val party = n.parties.first { it.stage == Stage.DECIDING }
        val satAt = n.time
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage == Stage.READY_TO_ORDER }
        assertTrue(n.time - satAt >= 2.4f)
    }
}

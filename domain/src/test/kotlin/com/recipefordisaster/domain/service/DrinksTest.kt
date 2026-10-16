package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrinksTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    /** A restaurant a while in, once everybody has a drink first. */
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(day = 11, restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    private fun waitFor(night: ServiceNight, condition: (ServiceNight) -> Boolean): ServiceNight {
        var n = night
        var guard = 0
        while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f)
        return n
    }

    private fun walk(night: ServiceNight) = waitFor(night) { !it.player.walking }

    @Test
    fun `once seated, guests want a drink before they order any food`() {
        val n = waitFor(open()) { night -> night.parties.any { it.stage == Stage.WANTS_DRINKS } }
        assertTrue(n.parties.none { it.stage == Stage.READY_TO_ORDER })
        // Left alone, they never get as far as ordering food.
        val later = waitFor(n) { it.time > n.time + 20f }
        assertTrue(later.parties.none { it.stage == Stage.READY_TO_ORDER })
    }

    @Test
    fun `take the drinks order, pour it at the bar, carry it over, and they order food after a sip`() {
        var n = waitFor(open()) { night -> night.parties.any { it.stage == Stage.WANTS_DRINKS } }
        val party = n.parties.first { it.stage == Stage.WANTS_DRINKS }
        val table = party.table!!

        n = walk(n.tapTable(table))
        assertEquals(Stage.DRINKS_ORDERED, n.parties.first { it.id == party.id }.stage)
        assertEquals(listOf(party.id), n.player.drinkOrders)

        // Pouring takes a moment at the bar; then the drinks are in your hand.
        n = n.tapBar()
        n = waitFor(n) { it.player.errand is ServiceNight.Errand.Pour }
        assertTrue(n.player.drinks.isEmpty())
        n = walk(n)
        assertEquals(listOf(party.id), n.player.drinks)
        assertTrue(n.player.drinkOrders.isEmpty())

        n = walk(n.tapTable(table))
        assertEquals(Stage.DRINKING, n.parties.first { it.id == party.id }.stage)
        assertTrue(n.player.drinks.isEmpty())

        // They sip and read the menu for a few seconds before they're ready to order food.
        val servedAt = n.time
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage != Stage.DRINKING }
        assertEquals(Stage.READY_TO_ORDER, n.parties.first { it.id == party.id }.stage)
        assertTrue(n.time - servedAt >= 3.9f)
    }

    @Test
    fun `tapping the bar with no drinks to pour does nothing there`() {
        var n = walk(open().tapBar())
        assertTrue(n.player.drinks.isEmpty())
        n = n.advance(1f)
        assertTrue(n.player.errand == null)
    }

    @Test
    fun `two hands carry two tables' drinks at once`() {
        var n = waitFor(open()) { night -> night.parties.count { it.stage == Stage.WANTS_DRINKS } >= 2 }
        val tables = n.parties.filter { it.stage == Stage.WANTS_DRINKS }.take(2).map { it.table!! }
        n = n.tapTable(tables[0]).tapTable(tables[1]).tapBar()
        n = waitFor(n) { it.player.drinks.size == 2 || it.time > 200f }
        assertEquals(2, n.player.drinks.size)
    }

    @Test
    fun `guests who never get their drinks give up and go`() {
        val n = waitFor(open()) { night -> night.gaveUpAt.values.any { it == ServiceNight.WaitedFor.DRINKS } }
        assertTrue(n.gaveUpAt.values.any { it == ServiceNight.WaitedFor.DRINKS })
    }

    @Test
    fun `time spent sipping a drink doesn't make the food seem slow`() {
        // Serve drinks and then food as quickly as possible: guests should be happy and tip.
        var n = open()
        var guard = 0
        while (!n.finished && guard++ < 20_000) {
            n = DrinksPlayer.play(n).advance(0.05f)
        }
        val fed = n.result().outcomes.filter { it.dish != null }
        assertTrue(fed.isNotEmpty())
        assertTrue(fed.map { it.satisfaction }.average() >= 55)
        assertTrue(n.tips > 0)
    }
    @Test
    fun `guests pay for their drinks when they're served, and it reaches the day's books`() {
        val setup = engine.openService(start, PlayerDecisions(), SeededRandomSource(1L))
        var n = ServiceNight.open(setup, SeededRandomSource(2L))
        var guard = 0
        while (!n.finished && guard++ < 20_000) n = DrinksPlayer.play(n).advance(0.05f)
        assertTrue(n.drinkTakings > 0)
        // Everyone who got as far as drinking paid for a drink.
        val served = n.parties.filter { it.sippedFor > 0f }
        assertEquals(served.sumOf { n.drinksPrice(it) }, n.drinkTakings)
        val books = engine.closeService(setup, n.result(), SeededRandomSource(3L)).newState.ledger.history.last()
        assertEquals(n.drinkTakings, books.drinks)
    }

    @Test
    fun `a child's drink costs less`() {
        val party = ServiceNight.Party(id = 0, guests = listOf(0, 1), preferences = listOf(emptyList(), emptyList()), arriveAt = 0f, patience = 10f)
        val n = open()
        assertTrue(n.drinksPrice(party.copy(family = true)) < n.drinksPrice(party))
    }

    @Test
    fun `servers leave orders and drinks to you`() {
        val server = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.SERVER)
        var n = open(start.copy(employees = start.employees + server))
        repeat(1_500) {
            n = n.advance(0.05f)
            assertTrue(n.parties.none { it.stage == Stage.DRINKING || it.stage == Stage.ORDER_TAKEN })
        }
    }

    @Test
    fun `nobody has a drink until the bar opens on day six, then a few do, then everyone`() {
        fun share(day: Int) = open(start.copy(day = day)).parties.let { ps -> ps.count { it.wantsDrinks }.toFloat() / ps.size }
        assertEquals(0f, share(5), 0.001f)
        assertTrue(share(6) > 0f && share(6) < 0.7f)
        assertTrue(share(8) > share(6))
        assertEquals(1f, share(10), 0.001f)
    }

    @Test
    fun `before the bar opens, guests go straight to ordering food`() {
        val n = waitFor(open(start.copy(day = 3))) { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
        assertTrue(n.parties.none { it.stage == Stage.WANTS_DRINKS })
    }

    @Test
    fun `a bartender takes the drinks orders and pours them onto trays, but doesn't carry them over`() {
        val bartender = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.BARTENDER)
        var n = open(start.copy(employees = start.employees + bartender))
        n = waitFor(n) { night -> night.traysOnBar.isNotEmpty() }
        assertTrue(n.traysOnBar.isNotEmpty())
        // The tray stays on the bar, with its table's number, until someone carries it.
        val tray = n.traysOnBar.first()
        assertTrue(tray.table != null)
        val later = waitFor(n) { it.time > n.time + 3f }
        assertTrue(later.waiters.filter { it.kind == ServiceNight.Kind.BARTENDER }.all { it.drinks.isEmpty() })
        // You never had to lift a finger to take or pour them.
        assertTrue(n.player.drinkOrders.isEmpty() && n.player.drinks.isEmpty())
        assertEquals(1, n.waiters.count { it.kind == ServiceNight.Kind.BARTENDER })
    }

    @Test
    fun `a server carries a poured tray to the right table`() {
        val bartender = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.BARTENDER)
        val server = start.applicants.first { it.role == com.recipefordisaster.domain.employee.Role.SERVER }.copy(status = com.recipefordisaster.domain.employee.EmployeeStatus.ACTIVE)
        var n = open(start.copy(employees = start.employees + bartender + server))
        n = waitFor(n) { night -> night.traysOnBar.isNotEmpty() }
        val tray = n.traysOnBar.first()

        // Nobody but the server does anything about it.
        n = waitFor(n) { night -> night.parties.first { it.id == tray.id }.stage == Stage.DRINKING }

        assertEquals(Stage.DRINKING, n.parties.first { it.id == tray.id }.stage)
        assertTrue(n.player.drinks.isEmpty())
    }
}

/** A quick player who looks after the drinks as well as the food. */
internal object DrinksPlayer {
    fun play(n: ServiceNight): ServiceNight {
        val me = n.player
        if (me.walking) return n
        me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        me.drinks.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        if (me.drinkOrders.isNotEmpty() && me.freeHands > 0) return n.tapBar()
        if (me.tickets.isNotEmpty()) return n.tapPass()
        if (n.parties.any { it.stage == Stage.READY_AT_PASS }) return n.tapPass()
        n.parties.filter { it.stage == Stage.WANTS_DRINKS || it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { return n.tapTable(it) }
        if (me.dirtyDishes.isNotEmpty() && (me.freeHands == 0 || n.dirtyTables.isEmpty())) return n.tapDishStation()
        if (me.freeHands > 0) n.dirtyTables.firstOrNull()?.let { return n.tapTable(it) }
        return n
    }
}

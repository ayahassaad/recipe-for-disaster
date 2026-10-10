package com.recipefordisaster.app.ui.game

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceFloor
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightHintTest {

    private val start = NewGameFactory.create(seed = 11L).let { s -> s.copy(employees = s.employees.filter { it.role == com.recipefordisaster.domain.employee.Role.COOK }) }
    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val night = ServiceNight.open(engine.openService(start, PlayerDecisions(), SeededRandomSource(1)), SeededRandomSource(2))

    private fun withParty(stage: Stage, table: Int) = night.copy(
        parties = night.parties.mapIndexed { i, p -> if (i == 0) p.copy(stage = stage, table = table, stageSince = 0f, orderedAt = 0f) else p },
    )

    private val spill = listOf(ServiceNight.Mess(0, ServiceFloor.spillSpots.first(), appearsAt = 0f))

    @Test
    fun `taking an order comes before mopping a spill`() {
        assertEquals(NightHint.TakeOrder(2), NightHint.of(withParty(Stage.READY_TO_ORDER, 2).copy(messes = spill)))
    }

    @Test
    fun `food waiting on the counter comes before a spill`() {
        val night = withParty(Stage.READY_AT_PASS, 4).copy(messes = spill)
        assertEquals(NightHint.PickUp(4, night.parties.first().id), NightHint.of(night))
    }

    @Test
    fun `clearing a table comes before mopping`() {
        assertEquals(NightHint.Clear(1), NightHint.of(night.copy(dirtyTables = setOf(1), messes = spill)))
    }

    @Test
    fun `a spill is pointed out when nothing more urgent needs doing`() {
        assertEquals(NightHint.GetMop, NightHint.of(night.copy(messes = spill)))
    }

    @Test
    fun `a plate you're already on your way to collect isn't pointed at again`() {
        val waiting = withParty(Stage.READY_AT_PASS, 4)
        val id = waiting.parties.first().id
        assertEquals(NightHint.PickUp(4, id), NightHint.of(waiting))
        assertTrue(NightHint.of(waiting.tapPlate(id)) !is NightHint.PickUp)
    }

    @Test
    fun `once everyone has gone, a dirty table is the last job and the hint says so`() {
        val gone = night.copy(parties = night.parties.map { it.copy(stage = Stage.DONE, table = 0) }, dirtyTables = setOf(0))
        assertEquals(NightHint.Clear(0), NightHint.of(gone))
        assertEquals(NightHint.TidyingUp, NightHint.of(gone.copy(dirtyTables = emptySet())))
    }

    @Test
    fun `a broken fridge is pointed out before taking orders, and a struggling one is warned about`() {
        val broken = withParty(Stage.READY_TO_ORDER, 2).copy(fridgeBroken = true)
        assertEquals(NightHint.FixFridge, NightHint.of(broken))
        assertEquals(com.recipefordisaster.app.ui.scene.NightFocus.Fridge, NightHint.of(broken).focus(broken))
        val struggling = night.copy(fridgeBreaksAt = night.time + 2f)
        assertEquals(NightHint.FridgeStruggling, NightHint.of(struggling))
    }

    @Test
    fun `while the food cooks, the hint says whose it is instead of waiting for guests`() {
        assertEquals(NightHint.CookingFor(3), NightHint.of(withParty(Stage.COOKING, 3)))
        assertEquals(NightHint.Deciding(1), NightHint.of(withParty(Stage.DECIDING, 1)))
    }

    @Test
    fun `a table that would like drinks gets a hint to take their order`() {
        assertEquals(NightHint.TakeDrinks(3), NightHint.of(withParty(Stage.WANTS_DRINKS, 3)))
        assertEquals(com.recipefordisaster.app.ui.scene.NightFocus.Table(3), NightHint.TakeDrinks(3).focus(night))
    }

    @Test
    fun `with drinks orders on the notepad, the hint says to pour them at the bar`() {
        val ordered = withParty(Stage.DRINKS_ORDERED, 2)
        val withOrder = ordered.copy(waiters = ordered.waiters.map { if (it.isPlayer) it.copy(drinkOrders = listOf(ordered.parties.first().id)) else it })
        assertEquals(NightHint.PourDrinks, NightHint.of(withOrder))
        assertEquals(com.recipefordisaster.app.ui.scene.NightFocus.Bar, NightHint.PourDrinks.focus(withOrder))
    }

    @Test
    fun `carrying drinks, the hint says which table they're for`() {
        val ordered = withParty(Stage.DRINKS_ORDERED, 5)
        val carrying = ordered.copy(waiters = ordered.waiters.map { if (it.isPlayer) it.copy(hands = listOf(ServiceNight.HandItem.Drinks(ordered.parties.first().id))) else it })
        assertEquals(NightHint.ServeDrinks(5), NightHint.of(carrying))
    }
}

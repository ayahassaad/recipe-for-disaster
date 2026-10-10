package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChefMoodTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(day = 5, restaurant = it.restaurant.copy(tables = 6)) }

    private fun withCooks(morale: Int, stress: Int) =
        start.copy(employees = start.employees.map { if (it.role == Role.COOK) it.copy(morale = morale, stress = stress) else it })

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    @Test
    fun `the kitchen's mood follows the cooks' morale and stress`() {
        assertEquals(ChefMood.NORMAL, open(withCooks(morale = 70, stress = 20)).chefMood)
        assertEquals(ChefMood.GRUMPY, open(withCooks(morale = 30, stress = 20)).chefMood)
        assertEquals(ChefMood.GRUMPY, open(withCooks(morale = 80, stress = 80)).chefMood)
        assertEquals(ChefMood.HAPPY, open(withCooks(morale = 85, stress = 20)).chefMood)
        assertEquals(ChefMood.NORMAL, ChefMood.of(emptyList()))
    }

    /** Plays the night with a quick player who serves everyone as fast as they can. */
    private fun play(night: ServiceNight): ServiceNight {
        var n = night
        var guard = 0
        while (!n.finished && guard++ < 20_000) {
            val me = n.player
            if (!me.walking) {
                n = me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { n.tapTable(it) } }
                    ?: (if (me.tickets.isNotEmpty() || n.parties.any { it.stage == ServiceNight.Stage.READY_AT_PASS }) n.tapPass() else null)
                    ?: n.parties.filter { it.stage == ServiceNight.Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { n.tapTable(it) }
                    ?: (if (me.dirtyDishes.isNotEmpty()) n.tapDishStation() else n.dirtyTables.firstOrNull()?.let { n.tapTable(it) })
                    ?: n
            }
            n = n.advance(0.05f).drinksServed()
        }
        return n
    }

    @Test
    fun `a grumpy chef burns some plates and cooks them again, a normal one never does`() {
        val grumpy = play(open(withCooks(morale = 30, stress = 20)))
        assertTrue(grumpy.burntPlates > 0)
        assertTrue(grumpy.burntPlates < grumpy.parties.size)
        assertEquals(0, play(open(withCooks(morale = 70, stress = 20))).burntPlates)
    }

    @Test
    fun `a happy chef makes some plates a chef's special, which guests love and tip more for`() {
        val happy = play(open(withCooks(morale = 85, stress = 20)))
        val normal = play(open(withCooks(morale = 70, stress = 20)))
        assertTrue(happy.chefsSpecials > 0)
        assertEquals(0, normal.chefsSpecials)
        assertEquals(0, happy.burntPlates)
        // The same guests, served the same way: the special tables are happier and tip more.
        val special = happy.parties.filter { it.chefsSpecial }
        val sameInNormal = normal.parties.filter { p -> p.id in special.map { it.id } }
        assertTrue(special.sumOf { it.tip } > sameInNormal.sumOf { it.tip })
    }
}

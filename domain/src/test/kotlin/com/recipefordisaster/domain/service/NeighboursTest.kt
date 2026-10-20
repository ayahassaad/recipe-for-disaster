package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighboursTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(day: Int = 12): ServiceNight {
        val base = NewGameFactory.create(seed = 5L)
        val state = base.copy(day = day, restaurant = base.restaurant.copy(tables = 6))
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
    }

    /** Parties 0 and 1 are put at the given tables and stages; everyone else is kept away. */
    private fun seat(night: ServiceNight, tableA: Int, stageA: Stage, tableB: Int, stageB: Stage): ServiceNight {
        fun placed(p: ServiceNight.Party, table: Int, stage: Stage) = p.copy(
            stage = stage, table = table, stageSince = 0f, seatedAt = 0f, until = 0f,
            orders = p.preferences.map { it.first() },
        )
        return night.copy(
            parties = night.parties.mapIndexed { i, p ->
                when (i) {
                    0 -> placed(p, tableA, stageA)
                    1 -> placed(p, tableB, stageB)
                    else -> p.copy(arriveAt = 1_000f)
                }
            },
        )
    }

    @Test
    fun `tables next to each other are neighbours, but not across the room`() {
        val night = open()
        // Table 1 (top left) has the one beside it and the one in front, not the diagonal.
        assertEquals(listOf(1, 2), night.neighbours(0))
        assertEquals(listOf(0, 3, 4), night.neighbours(2))
        assertFalse(5 in night.neighbours(0))
    }

    @Test
    fun `a table that had a wonderful time cheers up the one next to it`() {
        // Table 1 finishes a meal (paying), table 2 beside it is still deciding.
        // (A room so lovely that the meal can't help being wonderful.)
        var night = seat(open(), 0, Stage.EATING, 1, Stage.READY_TO_ORDER).copy(decorJoy = 60)
        night = night.advance(0.05f)
        assertTrue(night.parties[0].guests.all { (night.results[it]?.satisfaction ?: 0) >= 85 })
        assertTrue(night.parties[1].neighbourMood > 0)
        assertTrue(night.reactions.any { it.from == 0 && it.to == 1 && it.cheer })
    }

    @Test
    fun `a wonderful evening next door makes a mealtime go better than a plain one`() {
        val plain = seat(open(), 0, Stage.EATING, 1, Stage.EATING).advance(0.05f)
        val cheered = seat(open(), 0, Stage.EATING, 1, Stage.EATING).let { n ->
            n.copy(parties = n.parties.map { if (it.id == n.parties[1].id) it.copy(neighbourMood = 8) else it })
        }.advance(0.05f)
        val plainScore = plain.results[plain.parties[1].guests.first()]!!.satisfaction
        val cheeredScore = cheered.results[cheered.parties[1].guests.first()]!!.satisfaction
        assertTrue("$cheeredScore should beat $plainScore", cheeredScore > plainScore || plainScore == 100)
    }

    @Test
    fun `a table that storms out sours the ones beside it`() {
        var night = seat(open(), 0, Stage.READY_TO_ORDER, 1, Stage.DRINKING)
        // Make the first table give up waiting.
        night = night.copy(parties = night.parties.map { if (it.id == night.parties[0].id) it.copy(patience = 0.01f) else it })
        var steps = 0
        while (night.parties[0].stage != Stage.LEAVING_ANGRY && steps++ < 200) night = night.advance(0.05f)
        assertEquals(Stage.LEAVING_ANGRY, night.parties[0].stage)
        assertTrue(night.parties[1].neighbourMood < 0)
        assertTrue(night.reactions.any { it.from == 0 && it.to == 1 && !it.cheer })
    }

    @Test
    fun `nothing spreads on the very first nights`() {
        var night = seat(open(day = 2), 0, Stage.READY_TO_ORDER, 1, Stage.DRINKING)
        night = night.copy(parties = night.parties.map { if (it.id == night.parties[0].id) it.copy(patience = 0.01f) else it })
        var steps = 0
        while (night.parties[0].stage != Stage.LEAVING_ANGRY && steps++ < 200) night = night.advance(0.05f)
        assertEquals(0, night.parties[1].neighbourMood)
        assertTrue(night.reactions.isEmpty())
    }
}

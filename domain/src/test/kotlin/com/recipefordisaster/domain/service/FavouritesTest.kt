package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    @Test
    fun `many guests have a favourite, and order it when it's on the menu`() {
        val n = open()
        val favourites = n.parties.flatMap { p -> p.favourites.entries.map { (guest, dish) -> Triple(p, guest, dish) } }
        assertTrue(favourites.size > n.arrivalOrder.size / 3)
        favourites.forEach { (party, guest, dish) ->
            val prefs = party.preferences[party.guests.indexOf(guest)]
            if (dish in prefs) assertEquals(dish, prefs.first())
        }
    }

    @Test
    fun `the critic loves the fish and children love fries`() {
        (1L..10L).forEach { seed ->
            val n = open(start.copy(day = 14), seed)
            n.parties.filter { it.special == SpecialGuest.CRITIC }.forEach { assertEquals(RecipeBook.FISH_DISH, it.favourites[it.guests[0]]) }
            n.parties.filter { it.family }.forEach { assertEquals(RecipeBook.FRIES_DISH, it.favourites[it.guests[1]]) }
        }
    }
}

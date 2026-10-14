package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertTrue
import org.junit.Test

class DishQuirksTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    @Test
    fun `a grumpy chef burns the lasagna more than other dishes`() {
        val start = NewGameFactory.create(seed = 11L)
        val night = ServiceNight.open(engine.openService(start, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
            .copy(chefMood = ChefMood.GRUMPY, menu = start.menu + RecipeBook.dishes)
        val burger = start.menu.first().id
        fun burnt(dish: com.recipefordisaster.domain.menu.DishId) = (0 until 200).count { id ->
            val party = night.parties.first().copy(id = id, stage = ServiceNight.Stage.COOKING, until = 0f, orders = listOf(dish))
            night.copy(time = 1f, parties = listOf(party)).advance(0.01f).parties.first().burnt
        }
        assertTrue(burnt(RecipeBook.LASAGNA_DISH) > burnt(burger) * 3 / 2)
    }
}

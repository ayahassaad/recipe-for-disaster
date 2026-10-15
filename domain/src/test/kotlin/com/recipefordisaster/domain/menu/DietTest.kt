package com.recipefordisaster.domain.menu

import com.recipefordisaster.domain.customer.DietaryRequirement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DietTest {

    private fun dish(id: String) = RecipeBook.dishes.first { it.id.value == id }

    @Test
    fun `meat and fish are not vegetarian, but fries and omelettes are`() {
        assertTrue(RecipeBook.find(RecipeBook.LASAGNA_DISH)!!.violates(DietaryRequirement.VEGETARIAN))
        assertTrue(RecipeBook.find(RecipeBook.FISH_DISH)!!.violates(DietaryRequirement.VEGETARIAN))
        assertFalse(RecipeBook.find(RecipeBook.FRIES_DISH)!!.violates(DietaryRequirement.VEGETARIAN))
        assertFalse(dish("existential_omelette").violates(DietaryRequirement.VEGETARIAN))
    }

    @Test
    fun `eggs and cheese are not vegan, but fries are`() {
        assertTrue(dish("existential_omelette").violates(DietaryRequirement.VEGAN))
        assertTrue(RecipeBook.find(RecipeBook.LASAGNA_DISH)!!.violates(DietaryRequirement.VEGAN))
        assertFalse(RecipeBook.find(RecipeBook.FRIES_DISH)!!.violates(DietaryRequirement.VEGAN))
    }
}

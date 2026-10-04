package com.recipefordisaster.domain.menu

import com.recipefordisaster.domain.inventory.IngredientId

/**
 * Dishes the player can add to the menu (Phase 6 "menu changes"). Each one
 * only uses ingredients that exist in every new game's pantry — some start
 * at zero stock, so adding a dish usually also means buying for it.
 * [com.recipefordisaster.domain.simulation.NewGameFactory] owns which
 * ingredients exist; this file just references them by ID.
 */
object RecipeBook {

    /** One-off cost of reprinting the menu to add a dish. */
    const val ADD_DISH_COST = 40L

    val FLOUR = IngredientId("flour")
    val MYSTERY_MEAT = IngredientId("ground_mystery_meat")
    val LETTUCE = IngredientId("lettuce")
    val BROTH = IngredientId("ominous_broth")
    val CHEESE = IngredientId("cheese")
    val EGGS = IngredientId("eggs")
    val FISH = IngredientId("questionable_fish")
    val POTATOES = IngredientId("potatoes")

    val dishes: List<Dish> = listOf(
        Dish(
            id = DishId("fries_of_destiny"),
            name = "Fries of Destiny",
            recipe = Recipe(ingredientRequirements = mapOf(POTATOES to 0.3), preparationTimeMinutes = 6),
            sellingPrice = 9,
            popularity = 75,
            quality = 50,
            available = true,
            allergens = emptySet(),
        ),
        Dish(
            id = DishId("existential_omelette"),
            name = "Existential Omelette",
            recipe = Recipe(ingredientRequirements = mapOf(EGGS to 0.3, CHEESE to 0.05), preparationTimeMinutes = 7),
            sellingPrice = 15,
            popularity = 55,
            quality = 60,
            available = true,
            allergens = setOf(Allergen.EGGS, Allergen.DAIRY),
        ),
        Dish(
            id = DishId("lasagna_situation"),
            name = "The Lasagna Situation",
            recipe = Recipe(
                ingredientRequirements = mapOf(FLOUR to 0.15, MYSTERY_MEAT to 0.15, CHEESE to 0.1),
                preparationTimeMinutes = 15,
            ),
            sellingPrice = 22,
            popularity = 65,
            quality = 70,
            available = true,
            allergens = setOf(Allergen.GLUTEN, Allergen.DAIRY),
        ),
        Dish(
            id = DishId("fish_of_uncertain_origin"),
            name = "Fish of Uncertain Origin",
            recipe = Recipe(ingredientRequirements = mapOf(FISH to 0.25, LETTUCE to 0.05), preparationTimeMinutes = 12),
            sellingPrice = 25,
            popularity = 50,
            quality = 72,
            available = true,
            allergens = emptySet(),
        ),
    )

    fun find(id: DishId): Dish? = dishes.firstOrNull { it.id == id }

    /** Recipes not yet on the given menu. */
    fun notYetOn(menu: List<Dish>): List<Dish> {
        val onMenu = menu.map { it.id }.toSet()
        return dishes.filter { it.id !in onMenu }
    }
}

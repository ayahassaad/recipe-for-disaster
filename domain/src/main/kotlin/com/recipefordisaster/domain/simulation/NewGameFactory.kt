package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Allergen
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus

/**
 * Builds the starting [GameState] for a brand-new run (Phase 5). This is
 * the one place in the game that decides what "day one" looks like: how
 * much starting cash, who the founding staff are, what's on the menu, and
 * what's in the pantry.
 *
 * A note on the `seed` parameter and the "single entry point for
 * randomness" rule in [RandomSource]'s docs: that rule is about the
 * *simulation* — every day-tick must be reproducible from (state, seed).
 * Picking *which* seed a brand-new run starts with is a different concern,
 * closer to "which shuffled deck did we open" than to simulation math, so
 * it's legitimate for the caller (the UI layer, in practice) to hand in an
 * arbitrary fresh `Long` — e.g. from `kotlin.random.Random` or the clock —
 * without that violating the determinism guarantee. Once a seed is chosen
 * here, everything downstream of it is fully deterministic.
 *
 * The starting roster, menu and pantry below are deliberately small and a
 * little absurd (per the product brief's tone) rather than a "balanced"
 * simulation-tuned start — actual balance is something Phase 6+ playtesting
 * will need to revisit; this exists to make the game playable end to end,
 * not to be the final word on difficulty.
 */
object NewGameFactory {

    fun create(seed: Long): GameState {
        val flourId = IngredientId("flour")
        val groundMysteryMeatId = IngredientId("ground_mystery_meat")
        val lettuceId = IngredientId("lettuce")
        val brothId = IngredientId("ominous_broth")
        val cheeseId = IngredientId("cheese")

        val cookId = EmployeeId("founding_cook")
        val serverId = EmployeeId("founding_server")

        val burgerDishId = DishId("the_regular_burger")
        val soupDishId = DishId("mystery_soup")
        val saladDishId = DishId("suspicious_salad")

        return GameState(
            seed = seed,
            day = 1,
            restaurant = Restaurant(
                cash = 5_000,
                reputation = 50,
                cleanliness = 80,
                capacity = 20,
                level = 1,
                operatingCosts = OperatingCosts(
                    rentPerDay = 200,
                    utilitiesPerDay = 40,
                    miscPerDay = 10,
                ),
                currentDay = 1,
                status = RestaurantStatus.OPEN,
            ),
            employees = listOf(
                Employee(
                    id = cookId,
                    name = "Chef Marguerite",
                    role = Role.COOK,
                    skill = 55,
                    speed = 50,
                    reliability = 65,
                    morale = 70,
                    stress = 20,
                    salaryPerDay = 120,
                    experienceDays = 0,
                    personalityTraits = emptySet(),
                    relationships = emptyMap(),
                    status = EmployeeStatus.ACTIVE,
                ),
                Employee(
                    id = serverId,
                    name = "Bartholomew",
                    role = Role.SERVER,
                    skill = 50,
                    speed = 55,
                    reliability = 60,
                    morale = 70,
                    stress = 20,
                    salaryPerDay = 90,
                    experienceDays = 0,
                    personalityTraits = emptySet(),
                    relationships = emptyMap(),
                    status = EmployeeStatus.ACTIVE,
                ),
            ),
            customersPresent = emptyList(),
            inventory = InventoryState(
                ingredients = mapOf(
                    flourId to Ingredient(flourId, "Flour", quantityOnHand = 20.0, unit = "kg", purchasePricePerUnit = 2, spoilageRatePerDay = 0.01, storageSpaceRequired = 1.0),
                    groundMysteryMeatId to Ingredient(groundMysteryMeatId, "Ground Mystery Meat", quantityOnHand = 15.0, unit = "kg", purchasePricePerUnit = 6, spoilageRatePerDay = 0.08, storageSpaceRequired = 1.0),
                    lettuceId to Ingredient(lettuceId, "Lettuce", quantityOnHand = 10.0, unit = "kg", purchasePricePerUnit = 3, spoilageRatePerDay = 0.12, storageSpaceRequired = 1.0),
                    brothId to Ingredient(brothId, "Ominous Broth", quantityOnHand = 12.0, unit = "L", purchasePricePerUnit = 4, spoilageRatePerDay = 0.05, storageSpaceRequired = 1.0),
                    cheeseId to Ingredient(cheeseId, "Cheese", quantityOnHand = 8.0, unit = "kg", purchasePricePerUnit = 5, spoilageRatePerDay = 0.06, storageSpaceRequired = 1.0),
                ),
                storageCapacity = 200.0,
                suppliers = emptyList(),
            ),
            menu = listOf(
                Dish(
                    id = burgerDishId,
                    name = "The Regular Burger",
                    recipe = Recipe(
                        ingredientRequirements = mapOf(groundMysteryMeatId to 0.2, flourId to 0.1, cheeseId to 0.05),
                        preparationTimeMinutes = 10,
                    ),
                    sellingPrice = 12,
                    popularity = 70,
                    quality = 60,
                    available = true,
                    allergens = setOf(Allergen.GLUTEN, Allergen.DAIRY),
                ),
                Dish(
                    id = soupDishId,
                    name = "Mystery Soup",
                    recipe = Recipe(
                        ingredientRequirements = mapOf(brothId to 0.4, lettuceId to 0.1),
                        preparationTimeMinutes = 8,
                    ),
                    sellingPrice = 8,
                    popularity = 55,
                    quality = 50,
                    available = true,
                    allergens = emptySet(),
                ),
                Dish(
                    id = saladDishId,
                    name = "Suspicious Salad",
                    recipe = Recipe(
                        ingredientRequirements = mapOf(lettuceId to 0.3, cheeseId to 0.05),
                        preparationTimeMinutes = 5,
                    ),
                    sellingPrice = 9,
                    popularity = 45,
                    quality = 55,
                    available = true,
                    allergens = setOf(Allergen.DAIRY),
                ),
            ),
            equipment = listOf(
                Equipment(
                    id = EquipmentId("ancient_oven"),
                    name = "Ancient Oven",
                    purchaseCost = 1_500,
                    condition = 70,
                    capacityEffect = 0,
                    maintenanceCostPerDay = 5,
                    failureProbabilityBase = 0.02,
                    upgradeLevel = 1,
                ),
            ),
            ledger = Ledger(history = emptyList()),
            log = listOf(SimulationLogEntry(day = 0, message = "You just inherited a restaurant. Nobody can explain why. Good luck.")),
        )
    }
}

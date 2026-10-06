package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Allergen
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import com.recipefordisaster.domain.menu.RecipeBook
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
 *
 * Phase 6 rebalanced the numbers so decisions can actually change the
 * outcome: before it, fixed costs (~465/day) exceeded the most the
 * restaurant could possibly earn, so every run went bankrupt on schedule.
 * Now a fresh restaurant roughly breaks even at its starting reputation,
 * and growing past that means hiring — one cook can only feed ~20 people.
 * Starting prices are each dish's fair (reference) price.
 */
object NewGameFactory {

    /** Mixed into the run seed for the starting applicant pool, so it isn't correlated with day one's RNG stream. */
    private const val APPLICANT_SEED_SALT = 0x5EED_A991_1CA7L

    fun create(seed: Long): GameState {
        val flourId = RecipeBook.FLOUR
        val groundMysteryMeatId = RecipeBook.MYSTERY_MEAT
        val lettuceId = RecipeBook.LETTUCE
        val brothId = RecipeBook.BROTH
        val cheeseId = RecipeBook.CHEESE

        val cookId = EmployeeId("founding_cook")

        val burgerDishId = DishId("the_regular_burger")
        val soupDishId = DishId("mystery_soup")
        val saladDishId = DishId("suspicious_salad")

        return GameState(
            seed = seed,
            day = 1,
            restaurant = Restaurant(
                cash = 1_500,
                reputation = 50,
                cleanliness = 80,
                capacity = 40,
                tables = com.recipefordisaster.domain.restaurant.TableGrowth.STARTING,
                level = 1,
                operatingCosts = OperatingCosts(
                    rentPerDay = 60,
                    utilitiesPerDay = 20,
                    miscPerDay = 5,
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
                    salaryPerDay = 60,
                    experienceDays = 0,
                    personalityTraits = emptySet(),
                    relationships = emptyMap(),
                    status = EmployeeStatus.ACTIVE,
                ),
            ),
            customersPresent = emptyList(),
            inventory = InventoryState(
                ingredients = listOf(
                    Ingredient(flourId, "Flour", quantityOnHand = 15.0, unit = "kg", purchasePricePerUnit = 2, spoilageRatePerDay = 0.01, storageSpaceRequired = 1.0),
                    Ingredient(groundMysteryMeatId, "Ground Mystery Meat", quantityOnHand = 10.0, unit = "kg", purchasePricePerUnit = 6, spoilageRatePerDay = 0.08, storageSpaceRequired = 1.0),
                    Ingredient(lettuceId, "Lettuce", quantityOnHand = 8.0, unit = "kg", purchasePricePerUnit = 3, spoilageRatePerDay = 0.12, storageSpaceRequired = 1.0),
                    Ingredient(brothId, "Ominous Broth", quantityOnHand = 10.0, unit = "L", purchasePricePerUnit = 4, spoilageRatePerDay = 0.05, storageSpaceRequired = 1.0),
                    Ingredient(cheeseId, "Cheese", quantityOnHand = 5.0, unit = "kg", purchasePricePerUnit = 5, spoilageRatePerDay = 0.06, storageSpaceRequired = 1.0),
                    // Not used by the starting menu — only by RecipeBook dishes
                    // the player can add — so they start empty.
                    Ingredient(RecipeBook.EGGS, "Eggs", quantityOnHand = 0.0, unit = "kg", purchasePricePerUnit = 3, spoilageRatePerDay = 0.06, storageSpaceRequired = 1.0),
                    Ingredient(RecipeBook.FISH, "Questionable Fish", quantityOnHand = 0.0, unit = "kg", purchasePricePerUnit = 8, spoilageRatePerDay = 0.15, storageSpaceRequired = 1.0),
                    Ingredient(RecipeBook.POTATOES, "Potatoes", quantityOnHand = 0.0, unit = "kg", purchasePricePerUnit = 1, spoilageRatePerDay = 0.02, storageSpaceRequired = 1.0),
                ).associateBy { it.id },
                storageCapacity = 120.0,
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
                    sellingPrice = 18,
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
                    sellingPrice = 13,
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
                    sellingPrice = 14,
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
                    purchaseCost = 600,
                    condition = 85,
                    capacityEffect = 0,
                    maintenanceCostPerDay = 5,
                    failureProbabilityBase = 0.02,
                    upgradeLevel = 1,
                ),
            ),
            ledger = Ledger(history = emptyList()),
            log = listOf(SimulationLogEntry(day = 0, message = "You just inherited a restaurant. Nobody can explain why. Good luck.")),
            applicants = StaffingMarket.generateApplicants(SeededRandomSource(seed xor APPLICANT_SEED_SALT), day = 1),
        )
    }
}

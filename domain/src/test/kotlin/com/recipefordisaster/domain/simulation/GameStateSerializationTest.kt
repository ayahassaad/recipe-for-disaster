package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.customer.CustomerId
import com.recipefordisaster.domain.economy.DailyFinancials
import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.PersonalityTrait
import com.recipefordisaster.domain.employee.RelationshipScore
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.inventory.IngredientId
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.inventory.Supplier
import com.recipefordisaster.domain.inventory.SupplierId
import com.recipefordisaster.domain.menu.Allergen
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole point of Phase 4's save format: a [GameState], however deeply
 * nested and full of value-class IDs and enums, must survive a round trip
 * through JSON byte-for-byte equal to what went in. This test deliberately
 * exercises every domain area at once rather than testing each type's
 * serializer in isolation, since a real save has all of them together.
 */
class GameStateSerializationTest {

    private fun sampleState(): GameState {
        val flourId = IngredientId("flour")
        val cheeseId = IngredientId("cheese")
        val employeeA = EmployeeId("emp-a")
        val employeeB = EmployeeId("emp-b")

        return GameState(
            seed = 12345L,
            day = 7,
            restaurant = Restaurant(
                cash = 8_500,
                reputation = 62,
                cleanliness = 71,
                capacity = 25,
                level = 2,
                operatingCosts = OperatingCosts(rentPerDay = 300, utilitiesPerDay = 60, miscPerDay = 15),
                currentDay = 7,
                status = RestaurantStatus.OPEN,
            ),
            employees = listOf(
                Employee(
                    id = employeeA,
                    name = "Marguerite Fenwick",
                    role = Role.COOK,
                    skill = 72,
                    speed = 65,
                    reliability = 80,
                    morale = 55,
                    stress = 30,
                    salaryPerDay = 900,
                    experienceDays = 120,
                    personalityTraits = setOf(PersonalityTrait.PERFECTIONIST, PersonalityTrait.ANXIOUS),
                    relationships = mapOf(employeeB to RelationshipScore(15)),
                    status = EmployeeStatus.ACTIVE,
                ),
            ),
            customersPresent = listOf(
                Customer(
                    id = CustomerId("cust-1"),
                    name = "Bartholomew Snoot",
                    patience = 25,
                    budget = 2_000,
                    preferences = emptySet(),
                    dietaryRequirements = emptySet(),
                    satisfaction = 70,
                    likelihoodOfReturning = 50,
                    complaintTendency = 20,
                    reviewInfluence = 40,
                ),
            ),
            inventory = InventoryState(
                ingredients = mapOf(
                    flourId to Ingredient(flourId, "Flour", 12.5, "kg", 20, 0.01, 1.0),
                    cheeseId to Ingredient(cheeseId, "Cheese", 4.0, "kg", 80, 0.1, 1.0),
                ),
                storageCapacity = 500.0,
                suppliers = listOf(
                    Supplier(SupplierId("sup-1"), "Big Cheese Co", reliability = 80, pricingMultiplier = 1.1, availableIngredientIds = setOf(cheeseId)),
                ),
            ),
            menu = listOf(
                Dish(
                    id = DishId("pizza"),
                    name = "Pizza",
                    recipe = Recipe(mapOf(flourId to 0.3, cheeseId to 0.2), preparationTimeMinutes = 12),
                    sellingPrice = 1_400,
                    popularity = 90,
                    quality = 70,
                    available = true,
                    allergens = setOf(Allergen.GLUTEN, Allergen.DAIRY),
                ),
            ),
            equipment = listOf(
                Equipment(EquipmentId("oven"), "Oven", purchaseCost = 5_000, condition = 80, capacityEffect = 15, maintenanceCostPerDay = 20, failureProbabilityBase = 0.02, upgradeLevel = 1),
            ),
            ledger = Ledger(
                history = listOf(
                    DailyFinancials(day = 6, revenue = 3_000, wages = 900, ingredientCosts = 400, rent = 300, utilities = 60, maintenance = 20, upgrades = 0, miscellaneous = 15),
                ),
            ),
            log = listOf(SimulationLogEntry(day = 6, message = "A quiet Tuesday.")),
            eventCooldowns = mapOf("inspection" to 3),
            firedUniqueEventIds = setOf("celebrity_visit"),
            dishSalesTotals = mapOf("pizza" to 42),
        )
    }

    @Test
    fun `a full game state survives a JSON round trip unchanged`() {
        val original = sampleState()

        val json = GameStateJson.instance.encodeToString(GameState.serializer(), original)
        val decoded = GameStateJson.instance.decodeFromString(GameState.serializer(), json)

        assertEquals(original, decoded)
    }

    @Test
    fun `a JSON blob missing newer optional fields still decodes using their defaults`() {
        // Simulates loading a save written before eventCooldowns/firedUniqueEventIds/
        // dishSalesTotals existed — the whole reason those fields have defaults.
        val original = sampleState()
        val fullJson = GameStateJson.instance.encodeToString(GameState.serializer(), original)

        val jsonWithoutNewFields = fullJson
            .replace(Regex(""","eventCooldowns":\{[^}]*\}"""), "")
            .replace(Regex(""","firedUniqueEventIds":\[[^]]*]"""), "")
            .replace(Regex(""","dishSalesTotals":\{[^}]*\}"""), "")

        val decoded = GameStateJson.instance.decodeFromString(GameState.serializer(), jsonWithoutNewFields)

        assertEquals(emptyMap<String, Int>(), decoded.eventCooldowns)
        assertEquals(emptySet<String>(), decoded.firedUniqueEventIds)
        assertEquals(emptyMap<String, Int>(), decoded.dishSalesTotals)
        // Everything else should be untouched.
        assertEquals(original.restaurant, decoded.restaurant)
        assertEquals(original.employees, decoded.employees)
    }
}

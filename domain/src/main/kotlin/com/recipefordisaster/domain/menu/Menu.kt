package com.recipefordisaster.domain.menu

import com.recipefordisaster.domain.customer.DietaryRequirement
import com.recipefordisaster.domain.inventory.IngredientId
import kotlinx.serialization.Serializable

@Serializable
data class Dish(
    val id: DishId,
    val name: String,
    val recipe: Recipe,
    val sellingPrice: Long,
    val popularity: Int,
    val quality: Int,
    val available: Boolean,
    val allergens: Set<Allergen>,
    /**
     * What customers consider a fair price for this dish (Phase 6). Pricing
     * above it makes the dish less attractive and less satisfying; pricing
     * below it does the opposite. Defaults to the selling price, so a dish
     * from a pre-Phase-6 save is treated as fairly priced.
     */
    val referencePrice: Long = sellingPrice,
)

@Serializable
@JvmInline
value class DishId(val value: String)

@Serializable
data class Recipe(
    val ingredientRequirements: Map<IngredientId, Double>,
    val preparationTimeMinutes: Int,
)

/**
 * Allergens are modeled as an explicit, checkable set on every dish — never
 * inferred from an icon or a color alone (section 3 / accessibility
 * requirements both depend on this being real data).
 */
@Serializable
enum class Allergen {
    GLUTEN,
    DAIRY,
    NUTS,
    SHELLFISH,
    EGGS,
    SOY,
}

fun Dish.violates(requirement: DietaryRequirement): Boolean = when (requirement) {
    DietaryRequirement.GLUTEN_FREE -> Allergen.GLUTEN in allergens
    DietaryRequirement.NUT_ALLERGY -> Allergen.NUTS in allergens
    DietaryRequirement.SHELLFISH_ALLERGY -> Allergen.SHELLFISH in allergens
    DietaryRequirement.LACTOSE_INTOLERANT -> Allergen.DAIRY in allergens
    DietaryRequirement.VEGETARIAN, DietaryRequirement.VEGAN -> false // TODO(Phase 3): needs an explicit ingredient-level meat/animal-product model, not just allergens.
}

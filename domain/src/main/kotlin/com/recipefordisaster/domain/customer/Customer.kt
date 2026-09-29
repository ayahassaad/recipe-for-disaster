package com.recipefordisaster.domain.customer

/**
 * All customers are generated fictional identities — nothing here is or
 * ever should be derived from a real person. See section 12 of the project
 * brief (data & privacy) for why that constraint is non-negotiable.
 */
data class Customer(
    val id: CustomerId,
    val name: String,
    val patience: Int,
    val budget: Long,
    val preferences: Set<String>,
    val dietaryRequirements: Set<DietaryRequirement>,
    val satisfaction: Int,
    val likelihoodOfReturning: Int,
    val complaintTendency: Int,
    val reviewInfluence: Int,
)

@JvmInline
value class CustomerId(val value: String)

/**
 * Modeled explicitly rather than left implicit, per section 3: a dietary
 * requirement must be checkable in code (e.g. "does this dish's allergen
 * set intersect this customer's requirements?"), never inferred from a UI
 * color or icon alone.
 */
enum class DietaryRequirement {
    VEGETARIAN,
    VEGAN,
    GLUTEN_FREE,
    NUT_ALLERGY,
    SHELLFISH_ALLERGY,
    LACTOSE_INTOLERANT,
}

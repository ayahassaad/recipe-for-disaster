package com.recipefordisaster.domain.restaurant

import kotlinx.serialization.Serializable

/**
 * The restaurant's own vital signs — the numbers a player is ultimately
 * trying to keep out of the danger zone. Deliberately just data: any rule
 * about how these numbers change lives in the simulation layer (Phase 3),
 * not here, so this type stays trivially testable and serializable.
 */
@Serializable
data class Restaurant(
    val cash: Long,
    val reputation: Int,
    val cleanliness: Int,
    val capacity: Int,
    val level: Int,
    val operatingCosts: OperatingCosts,
    val currentDay: Int,
    val status: RestaurantStatus,
)

@Serializable
data class OperatingCosts(
    val rentPerDay: Long,
    val utilitiesPerDay: Long,
    val miscPerDay: Long,
)

@Serializable
enum class RestaurantStatus {
    CLOSED,
    OPEN,
    BANKRUPT,
    CONDEMNED,
}

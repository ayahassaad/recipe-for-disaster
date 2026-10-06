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
    /** The most guests a night can bring when every table is out (scales with [tables]; see [guestCapacity]). */
    val capacity: Int,
    val level: Int,
    val operatingCosts: OperatingCosts,
    val currentDay: Int,
    val status: RestaurantStatus,
    /** Tables in the dining room. A new restaurant starts with a couple and grows (see TableGrowth). */
    val tables: Int = 6,
) {
    /** The most guests tonight can bring: [capacity] is for a six-table room, and more tables mean more guests. */
    val guestCapacity: Int get() = capacity * tables / 6
}

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

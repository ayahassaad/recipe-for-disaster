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
    /** What the player called the place. Blank for saves from before it could be named. */
    val name: String = "",
    /** Decorations bought so far. */
    val decor: Set<Decor> = emptySet(),
) {
    /** The name to show: the player's, or a stand-in for older saves. */
    val displayName: String get() = name.ifBlank { DEFAULT_NAME }

    /** The most guests tonight can bring: [capacity] is for a six-table room, and more tables mean more guests. */
    val guestCapacity: Int get() = capacity * tables / 6 * (if (Decor.FISH_TANK in decor) 11 else 10) / 10

    /**
     * What the restaurant pays each day. Rent in [operatingCosts] is for a full six-table room; a
     * smaller room pays for what it uses (so a new restaurant isn't sunk by rent on empty floor),
     * and every extra table bought adds to it.
     */
    val costsToday: OperatingCosts get() = operatingCosts.copy(rentPerDay = operatingCosts.rentPerDay * tables / 6)
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

/** What a restaurant is called if the player never named it. */
const val DEFAULT_NAME = "The Leaky Ladle"

/** The longest name that fits on the sign over the door. */
const val MAX_NAME_LENGTH = 20

package com.recipefordisaster.domain.service

import kotlinx.serialization.Serializable

/**
 * Guests who matter more than most, and look the part so the player can spot them:
 * a food critic, a celebrity, and a health inspector.
 */
@Serializable
enum class SpecialGuest {
    /** Dark glasses and a notepad. Their review moves the stars a lot, either way. */
    CRITIC,

    /** Shades and a gold star. Keep them happy and tomorrow is busier. */
    CELEBRITY,

    /** A hat and a clipboard. Checks the floor and tables are clean when they pay. */
    INSPECTOR,
}

/** How a special guest's visit went: [pleased] is a happy meal (or, for the inspector, a clean room). */
@Serializable
data class SpecialVisit(val guest: SpecialGuest, val pleased: Boolean)

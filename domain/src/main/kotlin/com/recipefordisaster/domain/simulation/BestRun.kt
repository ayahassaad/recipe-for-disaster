package com.recipefordisaster.domain.simulation

import kotlinx.serialization.Serializable

/** The longest any restaurant has lasted: how many days, and what it was called. Kept across games. */
@Serializable
data class BestRun(val days: Int, val name: String)

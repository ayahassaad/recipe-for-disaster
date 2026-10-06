package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.decision.DecisionSpending
import com.recipefordisaster.domain.simulation.ServiceSetup
import kotlinx.serialization.Serializable

/**
 * A night of service that has started but not finished, as it's saved
 * while the player plays: if the app is closed mid-service, the player
 * comes back to the same night at the same moment rather than to the
 * morning (which would let a bad night be quietly replayed).
 */
@Serializable
data class NightInProgress(
    val setup: ServiceSetup,
    val night: ServiceNight,
    val morningSpending: DecisionSpending,
    val dailySeed: Long,
)

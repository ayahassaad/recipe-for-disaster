package com.recipefordisaster.domain.restaurant

/**
 * Converts a day's average customer satisfaction (and cleanliness) into a
 * reputation delta. Reputation feeding back into tomorrow's customer
 * demand ([com.recipefordisaster.domain.customer.CustomerFlow]) is what
 * closes the feedback loop from the product brief's emergent-gameplay
 * example: bad service today measurably starves traffic days from now.
 */
object ReputationModel {

    fun dailyReputationDelta(averageSatisfaction: Int, cleanliness: Int): Int {
        // Centered on 50 "neutral" satisfaction: above it nudges reputation
        // up, below it nudges it down, scaled gently so no single day can
        // swing reputation wildly on its own.
        val satisfactionEffect = (averageSatisfaction - 50) / 10

        val cleanlinessEffect = when {
            cleanliness < 30 -> -3
            cleanliness < 50 -> -1
            else -> 0
        }

        return satisfactionEffect + cleanlinessEffect
    }
}

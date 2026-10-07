package com.recipefordisaster.domain.restaurant

/**
 * Converts a day's average customer satisfaction (and cleanliness) into a
 * reputation delta. Reputation feeding back into tomorrow's customer
 * demand ([com.recipefordisaster.domain.customer.CustomerFlow]) is what
 * closes the feedback loop from the product brief's emergent-gameplay
 * example: bad service today measurably starves traffic days from now.
 */
object ReputationModel {

    /** For this many days, a bad night costs at most [EARLY_WORST] stars-worth, while the player learns. */
    const val EARLY_DAYS = 5
    private const val EARLY_WORST = -2

    fun dailyReputationDelta(averageSatisfaction: Int, cleanliness: Int, day: Int = EARLY_DAYS + 1): Int {
        // Centered on 55 "neutral" satisfaction: above it nudges reputation
        // up, below it nudges it down, capped so no single day can swing
        // reputation wildly on its own. (Phase 6: was (avg - 50) / 10,
        // which meant even consistently happy customers only moved
        // reputation +1 or +2 a day — too slow for good play to show.)
        val satisfactionEffect = ((averageSatisfaction - 55) / 6).coerceIn(-6, 5)

        val cleanlinessEffect = when {
            cleanliness < 30 -> -3
            cleanliness < 50 -> -1
            else -> 0
        }

        val delta = satisfactionEffect + cleanlinessEffect
        // A gentler first week: good nights still count in full, bad ones only nudge.
        return if (day <= EARLY_DAYS) delta.coerceAtLeast(EARLY_WORST) else delta
    }
}

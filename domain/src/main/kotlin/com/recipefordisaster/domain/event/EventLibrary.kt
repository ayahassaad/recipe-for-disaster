package com.recipefordisaster.domain.event

/**
 * The MVP event rule library (Phase 6) — 25 rules, the top of the product
 * brief's 15-25. Grouped by what drives them:
 *
 * - [StaffEvents]: stress, morale and personality traits.
 * - [CustomerEvents]: reputation, reviews, and tomorrow's demand.
 * - [KitchenEvents]: equipment wear and cleanliness, including the two
 *   built-in chains (fire -> fire inspection, rat -> health inspection).
 * - [SupplyEvents]: ingredient prices, free stock, rent creep, and one lifeline.
 *
 * None of these are scripted to a day number. Every rule's prerequisite and
 * weight read the live state, so which events a run sees falls out of how
 * it's being played.
 */
object EventLibrary {

    val rules: List<EventRule> = StaffEvents.all + CustomerEvents.all + KitchenEvents.all + SupplyEvents.all

    /**
     * Weight of "nothing happens today," relative to the rules' own weights
     * (most sit between 0.2 and 1.5). A calm restaurant has a handful of
     * low-weight rules eligible and gets an event roughly every other day;
     * a struggling one has several heavy rules eligible at once and gets
     * them most days. Tuned with the balance test in `:domain`'s tests.
     */
    const val QUIET_DAY_WEIGHT = 6f

    fun engine(): EventEngine = EventEngine(rules, quietDayWeight = QUIET_DAY_WEIGHT)
}

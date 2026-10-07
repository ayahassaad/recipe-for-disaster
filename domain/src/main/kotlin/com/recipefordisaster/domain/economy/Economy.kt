package com.recipefordisaster.domain.economy

import kotlinx.serialization.Serializable

@Serializable
data class DailyFinancials(
    val day: Int,
    val revenue: Long,
    val wages: Long,
    val ingredientCosts: Long,
    val rent: Long,
    val utilities: Long,
    val maintenance: Long,
    val upgrades: Long,
    val miscellaneous: Long,
    /**
     * Money gained (+) or lost (-) to events after the day closed — a fine,
     * a windfall (Phase 6). Kept apart from operating expenses so the
     * day's "how did service go" numbers stay readable. Defaulted so
     * pre-Phase-6 saves still load.
     */
    val eventCashDelta: Long = 0,
    /** Tips guests left for quick service (already included in [revenue]). */
    val tips: Long = 0,
) {
    val expenses: Long
        get() = wages + ingredientCosts + rent + utilities + maintenance + upgrades + miscellaneous

    val profitOrLoss: Long
        get() = revenue - expenses + eventCashDelta
}

@Serializable
data class Ledger(
    val history: List<DailyFinancials>,
) {
    val totalRevenue: Long get() = history.sumOf { it.revenue }
    val totalProfitOrLoss: Long get() = history.sumOf { it.profitOrLoss }
}

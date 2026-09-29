package com.recipefordisaster.domain.economy

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
) {
    val expenses: Long
        get() = wages + ingredientCosts + rent + utilities + maintenance + upgrades + miscellaneous

    val profitOrLoss: Long
        get() = revenue - expenses
}

data class Ledger(
    val history: List<DailyFinancials>,
) {
    val totalRevenue: Long get() = history.sumOf { it.revenue }
    val totalProfitOrLoss: Long get() = history.sumOf { it.profitOrLoss }
}

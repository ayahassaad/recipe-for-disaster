package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState

/** One night's result. */
data class NightResult(val day: Int, val profit: Long)

/** What took most of the money on a bad night. */
enum class CostCategory { WAGES, GROCERIES, RUNNING_COSTS, UPGRADES, FINES_AND_OTHER, INTEREST }

/** What went wrong, in the end. */
sealed interface Mistake {
    /** A loan left unpaid for so long that the bank took the restaurant. */
    data object BankTookOver : Mistake

    /** The night that lost the most money, and what most of the money went on. */
    data class CostlyNight(val day: Int, val loss: Long, val mostlyOn: CostCategory) : Mistake
}

/** How a run went, for the game-over screen. */
data class RunSummary(
    val daysSurvived: Int,
    val revenue: Long,
    val profit: Long,
    val bestNight: NightResult?,
    val favouriteDish: Pair<String, Int>?,
    /** Everything guests left as tips over the whole run. */
    val tips: Long,
    val regulars: Int,
    val stars: Int,
    val mistake: Mistake?,
)

object RunSummaries {

    fun of(state: GameState): RunSummary {
        val history = state.ledger.history
        val best = history.maxByOrNull { it.profitOrLoss }?.let { NightResult(it.day, it.profitOrLoss) }
        val favourite = state.dishSalesTotals.maxByOrNull { it.value }?.let { (id, count) ->
            (state.menu.firstOrNull { it.id.value == id }?.name ?: id) to count
        }
        return RunSummary(
            daysSurvived = (state.day - 1).coerceAtLeast(0),
            revenue = state.ledger.totalRevenue,
            profit = state.ledger.totalProfitOrLoss,
            bestNight = best?.takeIf { it.profit > 0 },
            favouriteDish = favourite,
            tips = history.sumOf { it.tips },
            regulars = state.regulars.size,
            stars = starsFor(state.restaurant.reputation),
            mistake = mistakeOf(state),
        )
    }

    /** Reputation out of a hundred, as one to five stars. */
    fun starsFor(reputation: Int): Int = (reputation / 20).coerceIn(1, 5)

    private fun mistakeOf(state: GameState): Mistake? {
        if (Loans.foreclosed(state) && state.restaurant.status == RestaurantStatus.BANKRUPT) return Mistake.BankTookOver
        val worst = state.ledger.history.minByOrNull { it.profitOrLoss }?.takeIf { it.profitOrLoss < 0 } ?: return null
        val costs = mapOf(
            CostCategory.WAGES to worst.wages,
            CostCategory.GROCERIES to worst.ingredientCosts,
            CostCategory.RUNNING_COSTS to worst.rent + worst.utilities + worst.maintenance,
            CostCategory.UPGRADES to worst.upgrades,
            CostCategory.FINES_AND_OTHER to worst.miscellaneous,
            CostCategory.INTEREST to worst.interest,
        )
        return Mistake.CostlyNight(worst.day, -worst.profitOrLoss, costs.maxBy { it.value }.key)
    }
}

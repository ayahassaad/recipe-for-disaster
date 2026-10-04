package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.RandomSource

/**
 * Small, composable state changes the event rules are written in terms of.
 * Every one clamps to the same ranges [com.recipefordisaster.domain.simulation.GameStateValidator]
 * enforces, so no event can produce a save that later loads as corrupted.
 */

internal fun GameState.adjustReputation(delta: Int): GameState =
    copy(restaurant = restaurant.copy(reputation = (restaurant.reputation + delta).coerceIn(0, 100)))

internal fun GameState.adjustCleanliness(delta: Int): GameState =
    copy(restaurant = restaurant.copy(cleanliness = (restaurant.cleanliness + delta).coerceIn(0, 100)))

/**
 * Event money is booked onto the day that just closed (its
 * [com.recipefordisaster.domain.economy.DailyFinancials.eventCashDelta]),
 * so the ledger's lifetime totals still add up to the cash actually on hand.
 */
internal fun GameState.adjustCash(delta: Long): GameState {
    val history = ledger.history
    val updatedHistory = if (history.isEmpty()) {
        history
    } else {
        history.dropLast(1) + history.last().let { it.copy(eventCashDelta = it.eventCashDelta + delta) }
    }
    return copy(
        restaurant = restaurant.copy(cash = restaurant.cash + delta),
        ledger = ledger.copy(history = updatedHistory),
    )
}

/** Percent swing to tomorrow's customer demand. Stacks with anything already pending, within sane bounds. */
internal fun GameState.adjustTomorrowsDemand(percent: Int): GameState =
    copy(pendingDemandModifierPercent = (pendingDemandModifierPercent + percent).coerceIn(-80, 150))

internal fun GameState.updateEmployee(id: EmployeeId, transform: (Employee) -> Employee): GameState =
    copy(employees = employees.map { if (it.id == id) transform(it) else it })

internal fun GameState.updateAllEmployees(transform: (Employee) -> Employee): GameState =
    copy(employees = employees.map(transform))

internal fun Employee.adjustMorale(delta: Int): Employee = copy(morale = (morale + delta).coerceIn(0, 100))

internal fun Employee.adjustStress(delta: Int): Employee = copy(stress = (stress + delta).coerceIn(0, 100))

internal fun Employee.adjustSkill(delta: Int): Employee = copy(skill = (skill + delta).coerceIn(0, 100))

internal fun Employee.fallSick(days: Int): Employee =
    copy(status = EmployeeStatus.SICK, sickDaysRemaining = days.coerceAtLeast(1))

internal val GameState.activeEmployees: List<Employee>
    get() = employees.filter { it.status == EmployeeStatus.ACTIVE }

internal fun <T> RandomSource.pick(items: List<T>): T = items[nextInt(items.size)]

/** Weighted pick, so e.g. the most stressed employee is the most likely — but not certain — to be the one who gets sick. */
internal fun <T> RandomSource.pickWeighted(items: List<T>, weight: (T) -> Double): T {
    val weights = items.map { weight(it).coerceAtLeast(0.0) }
    val total = weights.sum()
    if (total <= 0.0) return pick(items)
    val roll = nextFloat() * total
    var cumulative = 0.0
    for ((item, w) in items.zip(weights)) {
        cumulative += w
        if (roll <= cumulative) return item
    }
    return items.last()
}

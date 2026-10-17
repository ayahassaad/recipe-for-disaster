package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource

/**
 * Now and then a night is a rush: far more guests than usual, arriving close together. The receipt the night
 * before warns you, so you can stock up and have staff ready. The more staff you have, the bigger the crowd,
 * so a rush is always more than your team can handle alone. Which nights are rushes depends only on the
 * game's seed and the day, so the warning and the night always agree.
 */
object RushNights {

    /** Nights before this are never a rush. */
    const val FROM_DAY = 8

    /** About one night in five is a rush, never two nights running. */
    private const val CHANCE = 0.2f

    /** How many more guests than usual on a rush night, before counting staff, and for each worker on the floor. */
    const val BASE_PERCENT = 40
    const val PER_STAFF_PERCENT = 8
    const val MAX_PERCENT = 100

    /** A rush night can fill the room a bit beyond what it holds, and the extras wait at the door. */
    const val OVER_CAPACITY_PERCENT = 30

    /** How much closer together parties arrive on a rush night (1 is the usual gap). */
    const val ARRIVAL_GAP_FACTOR = 0.6f

    private val onTheFloor = setOf(Role.SERVER, Role.MANAGER, Role.DISHWASHER, Role.BUSSER, Role.BARTENDER)

    private fun rolls(seed: Long, day: Int): Boolean =
        day >= FROM_DAY && SeededRandomSource(seed * 911 + day * 37L).nextFloat() < CHANCE

    /** Whether [day] is a rush night in a game with this [seed]. */
    fun isRush(seed: Long, day: Int): Boolean = rolls(seed, day) && !rolls(seed, day - 1)

    fun isRush(state: GameState): Boolean = isRush(state.seed, state.day)

    /** Whether tomorrow night is a rush, for the receipt to warn about. */
    fun tomorrowIsRush(state: GameState): Boolean = isRush(state.seed, state.day + 1)

    /** How many percent more guests tonight than usual: nothing on an ordinary night. */
    fun demandPercent(state: GameState): Int {
        if (!isRush(state)) return 0
        val staff = state.employees.count { it.status == EmployeeStatus.ACTIVE && it.role in onTheFloor }
        return (BASE_PERCENT + PER_STAFF_PERCENT * staff).coerceAtMost(MAX_PERCENT)
    }

    /** How far past the room's capacity tonight's demand can go, in percent. */
    fun overCapacityPercent(state: GameState): Int = if (isRush(state)) OVER_CAPACITY_PERCENT else 0
}

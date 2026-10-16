package com.recipefordisaster.app.ui.game

import androidx.annotation.StringRes
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.service.ServiceNight.WaitedFor

/** A note for the bill: [text] says what went wrong for [count] guests and what to do about it. */
internal data class LostTip(@StringRes val text: Int, val count: Int)

/**
 * The biggest reason guests went without tonight, so the bill can say what to do about it; null if
 * everyone was fed. [staff] is who was working: when the cause is something a missing worker would
 * have handled (drinks with no bartender, say), the note says so instead of just what to tap.
 */
internal fun biggestLoss(
    gaveUp: Map<WaitedFor, Int>,
    gaveUpWaiting: Int,
    unfedOutOfStock: Int,
    walkedOut: Int,
    unfedKitchenFull: Int,
    staff: Set<Role>,
): LostTip? {
    val reasons = buildMap {
        gaveUp[WaitedFor.TABLE]?.let {
            // Dishwashers and bussers clear tables.
            put(if (staff.none { r -> r == Role.DISHWASHER || r == Role.BUSSER }) R.string.tip_table_no_help else R.string.tip_table, it)
        }
        gaveUp[WaitedFor.DRINKS]?.let { put(if (Role.BARTENDER !in staff) R.string.tip_drinks_no_bartender else R.string.tip_drinks, it) }
        gaveUp[WaitedFor.ORDER]?.let { put(R.string.tip_order, it) }
        gaveUp[WaitedFor.FOOD]?.let { put(if (Role.SERVER !in staff) R.string.tip_food_no_server else R.string.tip_food, it) }
        if (gaveUp.isEmpty() && gaveUpWaiting > 0) put(R.string.tip_order, gaveUpWaiting)
        if (unfedOutOfStock > 0) put(R.string.tip_stock, unfedOutOfStock)
        if (walkedOut > 0) put(R.string.tip_menu, walkedOut)
        if (unfedKitchenFull > 0) put(R.string.tip_cooks, unfedKitchenFull)
    }
    return reasons.maxByOrNull { it.value }?.let { (text, count) -> LostTip(text, count) }
}

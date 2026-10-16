package com.recipefordisaster.app.ui.game

import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.service.ServiceNight.WaitedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostTipsTest {

    private val everyone = setOf(Role.COOK, Role.SERVER, Role.DISHWASHER, Role.BARTENDER)

    private fun tip(gaveUp: Map<WaitedFor, Int>, staff: Set<Role>) = biggestLoss(gaveUp, 0, 0, 0, 0, staff)

    @Test
    fun `nobody lost means no note`() {
        assertNull(tip(emptyMap(), everyone))
    }

    @Test
    fun `drinks lost with no bartender says to hire one`() {
        val lost = mapOf(WaitedFor.DRINKS to 18)
        assertEquals(LostTip(R.string.tip_drinks_no_bartender, 18), tip(lost, everyone - Role.BARTENDER))
        assertEquals(LostTip(R.string.tip_drinks, 18), tip(lost, everyone))
    }

    @Test
    fun `food lost with no server says to hire one`() {
        val lost = mapOf(WaitedFor.FOOD to 6)
        assertEquals(LostTip(R.string.tip_food_no_server, 6), tip(lost, everyone - Role.SERVER))
        assertEquals(LostTip(R.string.tip_food, 6), tip(lost, everyone))
    }

    @Test
    fun `no table lost with nobody clearing says to hire a dishwasher, though a busser will do`() {
        val lost = mapOf(WaitedFor.TABLE to 4)
        assertEquals(LostTip(R.string.tip_table_no_help, 4), tip(lost, setOf(Role.COOK)))
        // A server only carries food: they don't clear.
        assertEquals(LostTip(R.string.tip_table_no_help, 4), tip(lost, setOf(Role.COOK, Role.SERVER)))
        assertEquals(LostTip(R.string.tip_table, 4), tip(lost, setOf(Role.COOK, Role.BUSSER)))
    }

    @Test
    fun `the biggest cause wins`() {
        val lost = mapOf(WaitedFor.DRINKS to 3, WaitedFor.ORDER to 9)
        assertEquals(LostTip(R.string.tip_order, 9), tip(lost, everyone))
    }
}

package com.recipefordisaster.app.ui.game

import com.recipefordisaster.app.ui.scene.NightFocus
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.service.ServiceNight.Errand
import com.recipefordisaster.domain.service.ServiceNight.Kind
import com.recipefordisaster.domain.service.ServiceNight.Stage

/**
 * The one most useful thing for the player to do right now, shown under
 * the restaurant during service. Tables are 0-based here.
 *
 * Order matters: food in hand and food waiting come first (guests are
 * hungry), then getting people seated and ordering, then clearing tables,
 * and only then the floor — a spill puts guests off, but an empty table
 * or a cold plate loses them.
 */
sealed interface NightHint {
    data object Washing : NightHint
    data object Mopping : NightHint
    data class Serve(val table: Int) : NightHint
    data object HandIn : NightHint
    data class PickUp(val table: Int, val partyId: Int) : NightHint
    data class DoorWaiting(val table: Int) : NightHint
    data class TakeOrder(val table: Int) : NightHint
    data object Wash : NightHint
    data class Clear(val table: Int) : NightHint
    data object OutOfFood : NightHint
    data object MopSpill : NightHint
    data object GetMop : NightHint
    data object PutMopBack : NightHint
    data object Waiting : NightHint
    data object Cooking : NightHint

    /** Where in the restaurant this hint means, for the "tap here" ring; null if it isn't about one place. */
    fun focus(night: ServiceNight): NightFocus? = when (this) {
        is Serve -> NightFocus.Table(table)
        is TakeOrder -> NightFocus.Table(table)
        is Clear -> NightFocus.Table(table)
        is DoorWaiting -> NightFocus.Table(table)
        HandIn -> NightFocus.Chef
        is PickUp -> NightFocus.Plate(partyId)
        Wash -> NightFocus.DishStation
        GetMop, PutMopBack -> NightFocus.MopBucket
        MopSpill -> night.messesOnFloor.firstOrNull()?.let { NightFocus.Spill(it.id) }
        Washing, Mopping, OutOfFood, Waiting, Cooking -> null
    }

    companion object {
        fun of(night: ServiceNight): NightHint {
            val me = night.player
            val plans = listOfNotNull(me.errand) + me.queue
            val carrying = me.plates.firstOrNull()?.let { id -> night.parties.firstOrNull { it.id == id } }
            val ordering = night.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }
            val dirty = night.dirtyTables.firstOrNull { Errand.VisitTable(it) !in plans }
            val noWasher = night.waiters.none { it.kind == Kind.DISHWASHER }
            val nobodyClears = noWasher && night.waiters.none { it.kind == Kind.BUSSER }
            val spill = night.messesOnFloor.isNotEmpty() && noWasher
            val handingIn = Errand.HandIn in plans || Errand.VisitPass in plans
            val collecting = plans.filterIsInstance<Errand.PickUp>().map { it.partyId }.toSet()
            val toCollect = night.parties.filter { it.stage == Stage.READY_AT_PASS && it.id !in collecting }.minByOrNull { it.stageSince }
            return when {
                me.errand == Errand.Wash -> Washing
                me.errand is Errand.Mopping -> Mopping
                carrying?.table != null -> Serve(carrying.table!!)
                me.tickets.isNotEmpty() && !handingIn -> HandIn
                toCollect?.table != null && me.freeHands - collecting.size > 0 -> PickUp(toCollect.table!!, toCollect.id)
                // Guests at the door with nowhere to sit, because a table needs clearing: they'll walk out soon.
                night.waitingAtDoor.isNotEmpty() && !night.hasFreeTable && dirty != null && nobodyClears && me.freeHands > 0 -> DoorWaiting(dirty)
                ordering?.table != null && Errand.VisitTable(ordering.table!!) !in plans -> TakeOrder(ordering.table!!)
                me.dirtyDishes.isNotEmpty() && Errand.VisitDishStation !in plans && (me.freeHands == 0 || dirty == null) -> Wash
                dirty != null && nobodyClears && me.freeHands > 0 -> Clear(dirty)
                me.dirtyDishes.isNotEmpty() && Errand.VisitDishStation !in plans -> Wash
                !night.kitchenHasFood -> OutOfFood
                spill && me.holdingMop && plans.none { it is Errand.CleanMess } -> MopSpill
                spill && !me.holdingMop && Errand.VisitMopBucket !in plans && me.freeHands > 0 -> GetMop
                me.holdingMop && night.messesOnFloor.isEmpty() && Errand.VisitMopBucket !in plans -> PutMopBack
                night.parties.any { it.stage == Stage.NOT_YET_ARRIVED || it.stage == Stage.QUEUEING } -> Waiting
                else -> Cooking
            }
        }
    }
}

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
    /** A table would like drinks: take their order. */
    data class TakeDrinks(val table: Int) : NightHint
    /** You've taken drinks orders: pour them at the bar. */
    data object PourDrinks : NightHint
    /** Pouring at the bar right now. */
    data object Pouring : NightHint
    /** A bartender has poured a table's drinks: they're on the bar, waiting for someone to carry them. */
    data class PickUpDrinks(val table: Int, val partyId: Int) : NightHint
    /** You're carrying a table's drinks. */
    data class ServeDrinks(val table: Int) : NightHint
    /** A table is having a drink and reading the menu; they'll order food soon. */
    data class Drinking(val table: Int) : NightHint
    data object Wash : NightHint
    data class Clear(val table: Int) : NightHint
    data object OutOfFood : NightHint
    data object MopSpill : NightHint
    data object GetMop : NightHint
    data object PutMopBack : NightHint
    data object Waiting : NightHint
    /** Everyone's gone; staff are clearing up before closing. */
    data object TidyingUp : NightHint
    data object FixFridge : NightHint
    data object FixingFridge : NightHint
    data object FridgeStruggling : NightHint
    /** Something's going wrong: deal with it. */
    data class Chaos(val kind: com.recipefordisaster.domain.service.ChaosKind) : NightHint
    /** Dealing with it right now. */
    data object HandlingChaos : NightHint
    /** The cat just knocked a table's food off the counter. */
    data class CatKnocked(val table: Int) : NightHint
    /** A new hire has just tripped and dropped their tray. */
    data object Tripped : NightHint
    /** A special guest has just come in. */
    data class SpecialArrived(val guest: com.recipefordisaster.domain.service.SpecialGuest, val table: Int?) : NightHint
    data object Cooking : NightHint
    /** The chef is cooking a particular table's food. */
    data class CookingFor(val table: Int) : NightHint
    /** Guests are eating; nothing to do until they're done. */
    data object Eating : NightHint
    /** Someone has just sat down and is choosing. */
    data class Deciding(val table: Int) : NightHint

    /** The newer part of the game this hint is about, if any, so the ring can point at it while it's new. */
    fun feature(): com.recipefordisaster.domain.progression.Feature? = when (this) {
        is TakeDrinks, PourDrinks, Pouring, is PickUpDrinks, is ServeDrinks, is Drinking -> com.recipefordisaster.domain.progression.Feature.DRINKS
        MopSpill, GetMop, PutMopBack, Mopping -> com.recipefordisaster.domain.progression.Feature.SPILLS
        FixFridge, FixingFridge, FridgeStruggling -> com.recipefordisaster.domain.progression.Feature.FRIDGE_BREAKS
        is CatKnocked -> com.recipefordisaster.domain.progression.Feature.CAT
        is Chaos -> when (kind) {
            com.recipefordisaster.domain.service.ChaosKind.RAT -> com.recipefordisaster.domain.progression.Feature.RAT
            com.recipefordisaster.domain.service.ChaosKind.DOG -> com.recipefordisaster.domain.progression.Feature.DOG
            com.recipefordisaster.domain.service.ChaosKind.PAN_FIRE -> com.recipefordisaster.domain.progression.Feature.PAN_FIRE
            com.recipefordisaster.domain.service.ChaosKind.POWER_CUT -> com.recipefordisaster.domain.progression.Feature.POWER_CUT
        }
        else -> null
    }

    /** Where in the restaurant this hint means, for the "tap here" ring; null if it isn't about one place. */
    fun focus(night: ServiceNight): NightFocus? = when (this) {
        is Serve -> NightFocus.Table(table)
        is TakeOrder -> NightFocus.Table(table)
        is TakeDrinks -> NightFocus.Table(table)
        is ServeDrinks -> NightFocus.Table(table)
        PourDrinks -> NightFocus.Bar
        is PickUpDrinks -> NightFocus.Tray(partyId)
        is Clear -> NightFocus.Table(table)
        is DoorWaiting -> NightFocus.Table(table)
        HandIn -> NightFocus.Chef
        is PickUp -> NightFocus.Plate(partyId)
        Wash -> NightFocus.DishStation
        GetMop, PutMopBack -> NightFocus.MopBucket
        MopSpill -> night.messesOnFloor.firstOrNull()?.let { NightFocus.Spill(it.id) }
        FixFridge, FridgeStruggling -> NightFocus.Fridge
        is Chaos -> NightFocus.Chaos
        HandlingChaos, is CatKnocked -> null
        Tripped -> night.messesOnFloor.lastOrNull()?.let { NightFocus.Spill(it.id) }
        is SpecialArrived -> table?.let { NightFocus.Table(it) }
        Washing, Mopping, OutOfFood, Waiting, Cooking, TidyingUp, FixingFridge, is CookingFor, Eating, is Deciding, Pouring, is Drinking -> null
    }

    companion object {
        fun of(night: ServiceNight): NightHint {
            val me = night.player
            val plans = listOfNotNull(me.errand) + me.queue
            val carrying = me.plates.firstOrNull()?.let { id -> night.parties.firstOrNull { it.id == id } }
            // Whoever has waited longest to order, food or drinks.
            val ordering = night.parties.filter { it.stage == Stage.READY_TO_ORDER || it.stage == Stage.WANTS_DRINKS }.minByOrNull { it.stageSince }
            val carryingDrinks = me.drinks.firstOrNull()?.let { id -> night.parties.firstOrNull { it.id == id } }
            val dirty = night.dirtyTables.firstOrNull { Errand.VisitTable(it) !in plans }
            // Only dishwashers and bussers clear tables; nobody but you mops.
            val nobodyClears = night.waiters.none { it.kind == Kind.DISHWASHER || it.kind == Kind.BUSSER }
            val spill = night.messesOnFloor.isNotEmpty()
            val newcomer = night.parties.firstOrNull { it.special != null && it.stage in Stage.QUEUEING..Stage.DECIDING }
            val handingIn = Errand.HandIn in plans || Errand.VisitPass in plans
            val collecting = plans.filterIsInstance<Errand.PickUp>().map { it.partyId }.toSet()
            val toCollect = night.parties.filter { it.stage == Stage.READY_AT_PASS && it.id !in collecting }.minByOrNull { it.stageSince }
            // Drinks a bartender has poured, on the bar, that nobody (you included) is on the way to.
            val collectingTrays = plans.filterIsInstance<Errand.PickUpDrinks>().map { it.partyId }.toSet()
            val trayToCollect = night.traysOnBar.firstOrNull { it.id !in collectingTrays }
            return when {
                me.errand == Errand.Wash -> Washing
                me.errand is Errand.Mopping -> Mopping
                me.errand == Errand.FixFridge -> FixingFridge
                me.errand == Errand.HandleChaos -> HandlingChaos
                me.errand is Errand.Pour -> Pouring
                carrying?.table != null -> Serve(carrying.table!!)
                carryingDrinks?.table != null -> ServeDrinks(carryingDrinks.table!!)
                // A broken fridge stops orders, so fixing it comes before almost anything else.
                night.fridgeBroken && !night.fridgeBeingFixed -> FixFridge
                night.activeChaos != null && !night.chaosBeingHandled -> Chaos(night.activeChaos!!.kind)
                night.time - night.catKnockAt < 4f && night.catKnockTable != null -> CatKnocked(night.catKnockTable!!)
                night.time - night.lastTripAt < 4f -> Tripped
                night.fridgeStruggling -> FridgeStruggling
                me.tickets.isNotEmpty() && !handingIn -> HandIn
                me.drinkOrders.isNotEmpty() && Errand.VisitBar !in plans && me.freeHands > 0 -> PourDrinks
                toCollect?.table != null && me.freeHands - collecting.size > 0 -> PickUp(toCollect.table!!, toCollect.id)
                trayToCollect?.table != null && me.freeHands - collecting.size - collectingTrays.size > 0 -> PickUpDrinks(trayToCollect.table!!, trayToCollect.id)
                // A special guest has just walked in: worth knowing before anything routine.
                newcomer != null -> SpecialArrived(newcomer.special!!, newcomer.table)
                // Guests at the door with nowhere to sit, because a table needs clearing: they'll walk out soon.
                night.waitingAtDoor.isNotEmpty() && !night.hasFreeTable && dirty != null && nobodyClears && me.freeHands > 0 -> DoorWaiting(dirty)
                ordering?.table != null && Errand.VisitTable(ordering.table!!) !in plans ->
                    if (ordering.stage == Stage.WANTS_DRINKS) TakeDrinks(ordering.table!!) else TakeOrder(ordering.table!!)
                me.dirtyDishes.isNotEmpty() && Errand.VisitDishStation !in plans && (me.freeHands == 0 || dirty == null) -> Wash
                dirty != null && nobodyClears && me.freeHands > 0 -> Clear(dirty)
                me.dirtyDishes.isNotEmpty() && Errand.VisitDishStation !in plans -> Wash
                !night.kitchenHasFood -> OutOfFood
                spill && me.holdingMop && plans.none { it is Errand.CleanMess } -> MopSpill
                spill && !me.holdingMop && Errand.VisitMopBucket !in plans && me.freeHands > 0 -> GetMop
                me.holdingMop && night.messesOnFloor.isEmpty() && Errand.VisitMopBucket !in plans -> PutMopBack
                night.guestsGone -> TidyingUp
                // Nothing for you to do right now: say what's actually going on, the most useful first.
                else -> {
                    val cooking = night.parties.filter { it.stage == Stage.IN_KITCHEN || it.stage == Stage.COOKING }.minByOrNull { it.stageSince }
                    val deciding = night.parties.firstOrNull { it.stage == Stage.DECIDING }
                    val drinking = night.parties.firstOrNull { it.stage == Stage.DRINKING }
                    when {
                        cooking?.table != null -> CookingFor(cooking.table!!)
                        drinking?.table != null -> Drinking(drinking.table!!)
                        deciding?.table != null -> Deciding(deciding.table!!)
                        night.parties.any { it.stage == Stage.EATING } -> Eating
                        night.parties.any { it.stage == Stage.NOT_YET_ARRIVED || it.stage == Stage.QUEUEING || it.stage == Stage.WALKING_TO_TABLE } -> Waiting
                        else -> Cooking
                    }
                }
            }
        }
    }
}

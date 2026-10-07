package com.recipefordisaster.domain.service

import kotlinx.serialization.Serializable

/** Something going wrong mid-service that the player has to deal with. */
@Serializable
enum class ChaosKind {
    /** A rat scurrying about: guests are put off until it's chased out. */
    RAT,

    /** A pan on fire on the stove: nothing cooks until it's put out. */
    PAN_FIRE,

    /** A dog wandered in and is begging at the tables: guests are put off until it's led out. */
    DOG,

    /** The lights went out: the kitchen stops until someone flips the fuse box (or it comes back by itself). */
    POWER_CUT,
}

/**
 * One chaotic moment, rolled when the doors open: it starts at [startsAt] and lasts until the player
 * deals with it ([resolved]) or it sorts itself out. [at] is where the player goes to deal with it.
 */
@Serializable
data class Chaos(
    val kind: ChaosKind,
    val at: FloorPoint,
    val startsAt: Float,
    val resolved: Boolean = false,
) {
    /** Puts guests off while it's happening. */
    val botherGuests: Boolean get() = kind == ChaosKind.RAT || kind == ChaosKind.DOG

    /** Stops the kitchen while it's happening. */
    val stopsKitchen: Boolean get() = kind == ChaosKind.PAN_FIRE || kind == ChaosKind.POWER_CUT

    /** Seconds after which it sorts itself out if nobody deals with it (null: it never does). */
    val lastsAtMost: Float? get() = when (kind) {
        ChaosKind.RAT, ChaosKind.DOG -> 30f
        ChaosKind.POWER_CUT -> 14f
        ChaosKind.PAN_FIRE -> null
    }
}

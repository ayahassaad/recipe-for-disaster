package com.recipefordisaster.app.ui.scene

import com.recipefordisaster.domain.service.ServiceNight
import kotlin.math.PI
import kotlin.math.sin

/**
 * How a trip looks, second by second since [ServiceNight.TripScene.since]: a lurch forward, a fall flat on the
 * floor while what they carried flies off and lands (that's when the spill appears), a moment lying there
 * seeing stars, then getting back up. Pure functions of time, so they're easy to check.
 */
internal object TripAnimation {

    /** The lurch (arms flailing) before they go over. */
    const val LURCH = 0.3f

    /** When they're flat on the floor, and when they start to get up. */
    const val FLAT_AT = ServiceNight.TRIP_LAND
    const val GETTING_UP = 1.6f

    /** How far over they tip when the lurch is at its worst, and when they're flat (degrees). */
    private const val LEAN = 30f
    private const val FLAT = 88f

    /** How far a tripping worker is tipped over at [t] seconds in (degrees, always 0 or more): multiply by the side they fell. */
    fun tilt(t: Float): Float = when {
        t <= 0f || t >= ServiceNight.TRIP_TOTAL -> 0f
        t < LURCH -> LEAN * (t / LURCH)
        t < FLAT_AT -> LEAN + (FLAT - LEAN) * ((t - LURCH) / (FLAT_AT - LURCH))
        t < GETTING_UP -> FLAT
        else -> FLAT * (1f - (t - GETTING_UP) / (ServiceNight.TRIP_TOTAL - GETTING_UP))
    }

    /** Whether the worker is down on the floor at [t], so the stars go round their head. */
    fun seeingStars(t: Float): Boolean = t in FLAT_AT..GETTING_UP

    /** How far through its flight what they dropped is at [t]: 0 as it leaves their hands, 1 as it lands; null if it isn't in the air. */
    fun flight(t: Float): Float? = if (t in 0f..FLAT_AT) t / FLAT_AT else null

    /** How high the flying thing is above the straight line from hand to landing, at [flight] (0 to 1): an arc. */
    fun arc(flight: Float, height: Float = 9f): Float = sin(flight * PI.toFloat()) * height

    /** How far it has spun (degrees) at [flight], tumbling the way they fell. */
    fun spin(flight: Float): Float = flight * 540f

    /** How far the splash has spread after landing at [t], from 0 to 1, or null if it hasn't landed or has faded. */
    fun splash(t: Float): Float? = if (t in FLAT_AT..(FLAT_AT + SPLASH_LENGTH)) (t - FLAT_AT) / SPLASH_LENGTH else null

    private const val SPLASH_LENGTH = 0.5f
}

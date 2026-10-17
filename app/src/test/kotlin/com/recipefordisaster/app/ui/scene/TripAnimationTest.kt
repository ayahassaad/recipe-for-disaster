package com.recipefordisaster.app.ui.scene

import com.recipefordisaster.domain.service.ServiceNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAnimationTest {

    @Test
    fun `they start upright, tip over, lie flat, then get back up`() {
        assertEquals(0f, TripAnimation.tilt(0f), 0f)
        assertTrue(TripAnimation.tilt(TripAnimation.LURCH) in 20f..40f)
        assertTrue(TripAnimation.tilt(TripAnimation.FLAT_AT) > 80f)
        assertTrue(TripAnimation.tilt(1.2f) > 80f)
        assertTrue(TripAnimation.tilt(TripAnimation.GETTING_UP + 0.2f) < TripAnimation.tilt(TripAnimation.GETTING_UP))
        assertEquals(0f, TripAnimation.tilt(ServiceNight.TRIP_TOTAL), 0f)
        assertEquals(0f, TripAnimation.tilt(ServiceNight.TRIP_TOTAL + 5f), 0f)
    }

    @Test
    fun `the fall never jumps, because the tilt only ever changes smoothly`() {
        var last = 0f
        var t = 0f
        while (t <= ServiceNight.TRIP_TOTAL) {
            val now = TripAnimation.tilt(t)
            assertTrue("jump at $t: $last -> $now", kotlin.math.abs(now - last) < 12f)
            last = now
            t += 0.02f
        }
    }

    @Test
    fun `what they dropped is in the air until it lands, with the spill appearing then`() {
        assertNotNull(TripAnimation.flight(0.4f))
        assertEquals(1f, TripAnimation.flight(ServiceNight.TRIP_LAND)!!, 0.001f)
        assertNull(TripAnimation.flight(ServiceNight.TRIP_LAND + 0.01f))
        assertEquals(0f, TripAnimation.arc(0f), 0.001f)
        assertTrue(TripAnimation.arc(0.5f) > 8f)
        assertEquals(0f, TripAnimation.arc(1f), 0.01f)
    }

    @Test
    fun `stars go round while they're down, and the splash fades quickly`() {
        assertFalse(TripAnimation.seeingStars(0.3f))
        assertTrue(TripAnimation.seeingStars(1.2f))
        assertFalse(TripAnimation.seeingStars(2.0f))
        assertNull(TripAnimation.splash(0.5f))
        assertNotNull(TripAnimation.splash(ServiceNight.TRIP_LAND + 0.1f))
        assertNull(TripAnimation.splash(ServiceNight.TRIP_LAND + 1f))
    }
}

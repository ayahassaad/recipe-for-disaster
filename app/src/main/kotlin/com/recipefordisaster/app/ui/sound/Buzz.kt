package com.recipefordisaster.app.ui.sound

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The kinds of buzz the game makes: a quick tap, and a longer double buzz when something goes wrong. */
enum class Buzzes(val timings: LongArray, val amplitudes: IntArray) {
    TAP(longArrayOf(0, 25), intArrayOf(0, 90)),
    TROUBLE(longArrayOf(0, 120, 80, 160), intArrayOf(0, 255, 0, 255)),
}

/**
 * Makes the phone vibrate, and remembers whether the player has turned it off. One instance for the
 * whole app (see [Buzz.get]).
 */
class Buzz private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var on: Boolean = prefs.getBoolean("vibration", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("vibration", value).apply()
        }

    fun buzz(kind: Buzzes) {
        val v = vibrator ?: return
        if (!on || !v.hasVibrator()) return
        val amplitudes = if (v.hasAmplitudeControl()) kind.amplitudes else kind.amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 }.toIntArray()
        v.vibrate(VibrationEffect.createWaveform(kind.timings, amplitudes, -1))
    }

    companion object {
        @Volatile private var instance: Buzz? = null
        fun get(context: Context): Buzz = instance ?: synchronized(this) {
            instance ?: Buzz(context.applicationContext).also { instance = it }
        }
    }
}

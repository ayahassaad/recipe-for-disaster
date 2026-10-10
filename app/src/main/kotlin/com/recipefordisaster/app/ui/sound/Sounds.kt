package com.recipefordisaster.app.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.recipefordisaster.app.R

/** The game's sound effects. */
enum class Sfx(val res: Int) {
    BELL(R.raw.sfx_bell),
    COINS(R.raw.sfx_coins),
    CLINK(R.raw.sfx_clink),
    PAPER(R.raw.sfx_paper),
    DOOR(R.raw.sfx_door),
    GRUMBLE(R.raw.sfx_grumble),
    SPLASH(R.raw.sfx_splash),
    ALARM(R.raw.sfx_alarm),
    CLANK(R.raw.sfx_clank),
    FANFARE(R.raw.sfx_fanfare),
    MEOW(R.raw.sfx_meow),
    POUR(R.raw.sfx_pour),
}

/**
 * Plays the sound effects and the background music, and remembers whether the player has turned
 * either off. One instance for the whole app (see [Sounds.get]).
 */
class Sounds private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    private var music: MediaPlayer? = null
    private var musicWanted = false

    var effectsOn: Boolean = prefs.getBoolean("effects", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("effects", value).apply()
        }

    var musicOn: Boolean = prefs.getBoolean("music", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("music", value).apply()
            if (value && musicWanted) startMusic() else if (!value) stopMusicNow()
        }

    fun play(sfx: Sfx, volume: Float = 0.8f) {
        if (!effectsOn) return
        loaded[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    /** The game screen is showing: keep the music going (if it's on). */
    fun musicPlaying(wanted: Boolean) {
        musicWanted = wanted
        if (wanted && musicOn) startMusic() else stopMusicNow()
    }

    private fun startMusic() {
        val player = music ?: MediaPlayer.create(context, R.raw.music_bistro)?.apply {
            isLooping = true
            setVolume(0.35f, 0.35f)
        }?.also { music = it } ?: return
        if (!player.isPlaying) player.start()
    }

    private fun stopMusicNow() {
        music?.let { if (it.isPlaying) it.pause() }
    }

    companion object {
        @Volatile private var instance: Sounds? = null
        fun get(context: Context): Sounds = instance ?: synchronized(this) {
            instance ?: Sounds(context.applicationContext).also { instance = it }
        }
    }
}

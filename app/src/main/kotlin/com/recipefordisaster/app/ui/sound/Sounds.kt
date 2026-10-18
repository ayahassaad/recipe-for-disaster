package com.recipefordisaster.app.ui.sound

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.recipefordisaster.app.R

/** The game's sound effects, each with how loud it plays (some recordings are louder than others). */
enum class Sfx(val res: Int, val loudness: Float = 0.8f) {
    BELL(R.raw.sfx_bell),
    COINS(R.raw.sfx_coins),
    CLINK(R.raw.sfx_clink),
    PAPER(R.raw.sfx_paper),
    DOOR(R.raw.sfx_door, loudness = 0.3f),
    GRUMBLE(R.raw.sfx_grumble),
    SPLASH(R.raw.sfx_splash),
    ALARM(R.raw.sfx_alarm),
    CLANK(R.raw.sfx_clank),
    FANFARE(R.raw.sfx_fanfare),
    MEOW(R.raw.sfx_meow),
    POUR(R.raw.sfx_pour),
    /** The sad trombone: for a night that lost money, or the end of the run. */
    TROMBONE(R.raw.sfx_trombone, loudness = 0.9f),
}

/**
 * Plays the sound effects and the background music, and remembers whether the player has turned
 * either off. One instance for the whole app (see [Sounds.get]), holding on to the app itself rather
 * than any one screen, so no screen is kept around after it closes.
 */
class Sounds private constructor(private val context: Application) {

    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    private var music: MediaPlayer? = null
    private var musicWanted = false
    private var theme: MediaPlayer? = null
    private var themeWanted = false

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
            if (value && themeWanted) startTheme() else if (!value) stopThemeNow()
        }

    fun play(sfx: Sfx, volume: Float = sfx.loudness) {
        if (!effectsOn) return
        loaded[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    /** The game screen is showing: keep the music going (if it's on). */
    fun musicPlaying(wanted: Boolean) {
        musicWanted = wanted
        if (wanted && musicOn) startMusic() else stopMusicNow()
    }

    /** The title screen is showing: play the theme song (if music is on); it stops as soon as you leave. */
    fun themePlaying(wanted: Boolean) {
        themeWanted = wanted
        if (wanted && musicOn) startTheme() else stopThemeNow()
    }

    private fun startTheme() {
        val player = theme ?: MediaPlayer.create(context, R.raw.music_theme)?.apply {
            isLooping = true
            setVolume(0.45f, 0.45f)
        }?.also { theme = it } ?: return
        if (!player.isPlaying) player.start()
    }

    private fun stopThemeNow() {
        theme?.let { if (it.isPlaying) it.pause() }
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
            instance ?: Sounds(context.applicationContext as Application).also { instance = it }
        }
    }
}

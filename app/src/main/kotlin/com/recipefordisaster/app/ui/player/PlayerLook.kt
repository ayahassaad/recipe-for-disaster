package com.recipefordisaster.app.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * How the player's waiter looks: skin, hair colour, hairstyle and apron. Each is an index into the
 * lists below, so a saved look stays valid if the lists grow. The defaults are the look the player
 * had before they could choose.
 */
data class PlayerLook(
    val skin: Int = 0,
    val hair: Int = 4,
    val hairStyle: Int = 0,
    val apron: Int = 0,
) {
    val skinColor: Color get() = SKINS[skin.mod(SKINS.size)]
    val hairColor: Color get() = HAIRS[hair.mod(HAIRS.size)]
    val apronColor: Color get() = APRONS[apron.mod(APRONS.size)]
    val style: Int get() = hairStyle.mod(STYLES)

    companion object {
        val SKINS = listOf(
            Color(0xFFF7E3C4), Color(0xFFF1CFA8), Color(0xFFDDAA7C),
            Color(0xFFB98057), Color(0xFF8D5A3B), Color(0xFF5E3B26),
        )
        val HAIRS = listOf(
            Color(0xFF3B2A20), Color(0xFF7A4A2A), Color(0xFFE2B866), Color(0xFF1F1B1A),
            Color(0xFFA8432A), Color(0xFF9A9A9A), Color(0xFF5C3B28), Color(0xFFD9708F),
        )
        /** Side parting, curls, long, bun, short crop. */
        const val STYLES = 5
        val APRONS = listOf(
            Color(0xFF3E8E41), Color(0xFF3B78A8), Color(0xFF9C6FB6),
            Color(0xFFE08E45), Color(0xFFCF6F8E), Color(0xFF2B2B2B),
        )
    }
}

/** Remembers the player's look on this phone, across games. One instance for the whole app (see [get]). */
class PlayerLooks private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("player_look", Context.MODE_PRIVATE)

    /** The current look; reading it from a composable redraws that composable when it changes. */
    var look: PlayerLook by mutableStateOf(
        PlayerLook(
            skin = prefs.getInt("skin", 0),
            hair = prefs.getInt("hair", 4),
            hairStyle = prefs.getInt("style", 0),
            apron = prefs.getInt("apron", 0),
        ),
    )
        private set

    fun update(look: PlayerLook) {
        this.look = look
        prefs.edit()
            .putInt("skin", look.skin)
            .putInt("hair", look.hair)
            .putInt("style", look.hairStyle)
            .putInt("apron", look.apron)
            .apply()
    }

    companion object {
        @Volatile private var instance: PlayerLooks? = null
        fun get(context: Context): PlayerLooks = instance ?: synchronized(this) {
            instance ?: PlayerLooks(context.applicationContext).also { instance = it }
        }
    }
}

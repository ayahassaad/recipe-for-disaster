package com.recipefordisaster.domain.restaurant

import kotlinx.serialization.Serializable

/**
 * Things to make the restaurant nicer. Each is bought once, shows in the room, and helps a little.
 */
@Serializable
enum class Decor(val price: Long) {
    /** More potted plants about the place: guests are calmer and wait a little longer. */
    PLANTS(120),

    /** Crisp white tablecloths with a gold trim: guests enjoy their meal a bit more. */
    TABLECLOTHS(150),

    /** A fish tank by the tables: people come in to look at it, so a few more guests a night. */
    FISH_TANK(250),

    /** A painting on the wall: guests enjoy their meal a bit more. */
    PAINTING(180);

    companion object {
        /** How much happier guests leave, thanks to the decor. */
        fun satisfactionBonus(decor: Set<Decor>): Int = (if (TABLECLOTHS in decor) 4 else 0) + (if (PAINTING in decor) 3 else 0)

        /** How much longer guests will wait, thanks to the decor. */
        fun patienceFactor(decor: Set<Decor>): Float = if (PLANTS in decor) 1.15f else 1f
    }
}

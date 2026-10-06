package com.recipefordisaster.domain.restaurant

/**
 * How the dining room grows. A new restaurant opens with [STARTING] tables,
 * so the first night is small and gentle; a new table arrives free each
 * morning until there are [FREE_UP_TO]. After that more tables have to be
 * bought, each dearer than the last, up to [MAX].
 */
object TableGrowth {
    const val STARTING = 2
    const val FREE_UP_TO = 6
    const val MAX = 12

    /** Whether tomorrow brings a free table. */
    fun growsFree(tables: Int): Boolean = tables < FREE_UP_TO

    /** What the next table costs, or null if it's free or the room is full. */
    fun nextTablePrice(tables: Int): Long? = when {
        tables < FREE_UP_TO -> null
        tables >= MAX -> null
        // Table 7 costs 300; each one after that 100 more.
        else -> 300L + 100L * (tables - FREE_UP_TO)
    }
}

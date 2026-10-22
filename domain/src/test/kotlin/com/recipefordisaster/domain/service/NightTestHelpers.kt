package com.recipefordisaster.domain.service

/**
 * Whether the night would be over if the player had mopped every spill. A night only ends once spills are
 * mopped, so tests whose scripted player never mops use this to know when everything else is done.
 */
internal fun ServiceNight.doneIgnoringSpills(): Boolean = copy(messes = messes.map { it.copy(cleaned = true) }).finished

package com.recipefordisaster.app.ui.game

import androidx.annotation.StringRes
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.progression.Feature

/** One line about what a new feature means for the player, for the "Tomorrow:" and "New tonight:" notes. */
@StringRes
internal fun featureLine(feature: Feature): Int = when (feature) {
    Feature.SPILLS -> R.string.feature_spills
    Feature.DRINKS -> R.string.feature_drinks
    Feature.TAKEAWAY -> R.string.feature_takeaway
    Feature.CAT -> R.string.feature_cat
    Feature.FAMILIES -> R.string.feature_families
    Feature.CELEBRITY -> R.string.feature_celebrity
    Feature.CRITIC -> R.string.feature_critic
    Feature.RAT -> R.string.feature_rat
    Feature.GUEST_REQUESTS -> R.string.feature_guest_requests
    Feature.FRIDGE_BREAKS -> R.string.feature_fridge
    Feature.DOG -> R.string.feature_dog
    Feature.STAFF_TROUBLE -> R.string.feature_staff_trouble
    Feature.CHEF_MOODS -> R.string.feature_chef_moods
    Feature.PAN_FIRE -> R.string.feature_pan_fire
    Feature.INSPECTOR -> R.string.feature_inspector
    Feature.POWER_CUT -> R.string.feature_power_cut
    Feature.DOUBLE_TROUBLE -> R.string.feature_double_trouble
}

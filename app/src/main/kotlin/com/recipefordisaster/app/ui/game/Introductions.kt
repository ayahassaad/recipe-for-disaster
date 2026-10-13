package com.recipefordisaster.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.scene.SceneLayout
import com.recipefordisaster.app.ui.scene.toPoint
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.service.ServiceNight

/** The title and the explanation for the pop-up the first time [feature] turns up. */
internal data class IntroductionText(@StringRes val title: Int, @StringRes val body: Int)

internal fun introductionText(feature: Feature): IntroductionText = when (feature) {
    Feature.SPILLS -> IntroductionText(R.string.intro_spills_title, R.string.intro_spills)
    Feature.DRINKS -> IntroductionText(R.string.intro_drinks_title, R.string.intro_drinks)
    Feature.CAT -> IntroductionText(R.string.intro_cat_title, R.string.intro_cat)
    Feature.FAMILIES -> IntroductionText(R.string.intro_families_title, R.string.intro_families)
    Feature.CELEBRITY -> IntroductionText(R.string.intro_celebrity_title, R.string.intro_celebrity)
    Feature.CRITIC -> IntroductionText(R.string.intro_critic_title, R.string.intro_critic)
    Feature.RAT -> IntroductionText(R.string.intro_rat_title, R.string.intro_rat)
    Feature.FRIDGE_BREAKS -> IntroductionText(R.string.intro_fridge_title, R.string.intro_fridge)
    Feature.DOG -> IntroductionText(R.string.intro_dog_title, R.string.intro_dog)
    Feature.CHEF_MOODS -> IntroductionText(R.string.intro_chef_moods_title, R.string.intro_chef_moods)
    Feature.PAN_FIRE -> IntroductionText(R.string.intro_fire_title, R.string.intro_fire)
    Feature.INSPECTOR -> IntroductionText(R.string.intro_inspector_title, R.string.intro_inspector)
    Feature.POWER_CUT -> IntroductionText(R.string.intro_power_title, R.string.intro_power)
    Feature.DOUBLE_TROUBLE -> IntroductionText(R.string.intro_double_title, R.string.intro_double)
}

/** Where in the restaurant [feature] is right now, to zoom in on while it's explained. */
internal fun introductionSpot(night: ServiceNight, feature: Feature): SceneLayout.Point {
    fun tableOf(party: ServiceNight.Party?) = party?.table?.let { night.layout.tables[it].toPoint() }
    val door = SceneLayout.Point(50f, 132f)
    return when (feature) {
        Feature.SPILLS -> night.messesOnFloor.firstOrNull()?.at?.toPoint()
        Feature.DRINKS -> tableOf(night.parties.firstOrNull { it.stage == ServiceNight.Stage.WANTS_DRINKS })
        Feature.CAT -> night.catPose()?.at?.toPoint()
        Feature.FAMILIES -> tableOf(night.parties.firstOrNull { it.family && it.stage >= ServiceNight.Stage.DECIDING }) ?: door
        Feature.CELEBRITY, Feature.CRITIC, Feature.INSPECTOR -> tableOf(night.parties.firstOrNull { it.special != null && it.stage >= ServiceNight.Stage.DECIDING }) ?: door
        Feature.PAN_FIRE -> SceneLayout.stove.center
        Feature.POWER_CUT -> SceneLayout.fuseBox.center
        Feature.RAT, Feature.DOG, Feature.DOUBLE_TROUBLE -> night.chaosSpot()?.toPoint()
        Feature.FRIDGE_BREAKS -> SceneLayout.fridge.center
        Feature.CHEF_MOODS -> SceneLayout.cookSpot(0)
    } ?: SceneLayout.Point(SceneLayout.WIDTH / 2, SceneLayout.HEIGHT / 2)
}

/**
 * The game stops and explains something the first time it turns up: what it is and what to do
 * about it. Covers the restaurant so nothing gets tapped by accident; [onGotIt] carries on.
 */
@Composable
internal fun IntroductionCard(feature: Feature, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    val text = introductionText(feature)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            // Taps on the restaurant behind don't go through.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Chalkboard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.intro_new, stringResource(text.title)), style = MaterialTheme.typography.headlineSmall, color = ChalkWhite, textAlign = TextAlign.Center)
                Text(stringResource(text.body), style = MaterialTheme.typography.bodyLarge, color = ChalkWhite.copy(alpha = 0.9f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                SignButton(text = stringResource(R.string.intro_got_it), onClick = onGotIt)
            }
        }
    }
}

package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.LeafGreen
import com.recipefordisaster.domain.goal.GoalKind
import com.recipefordisaster.domain.goal.WeeklyGoal

/** This week's goal in one line on the morning screen, with how far along it is and the days left. */
@Composable
internal fun WeeklyGoalLine(goal: WeeklyGoal, day: Int, modifier: Modifier = Modifier) {
    val daysLeft = (goal.lastDay - day + 1).coerceAtLeast(1)
    val what = when (goal.kind) {
        GoalKind.TAKINGS -> stringResource(R.string.goal_takings, coins(goal.target))
        GoalKind.GUESTS_FED -> stringResource(R.string.goal_guests, goal.target.toInt())
        GoalKind.STARS -> stringResource(R.string.goal_stars, stars(goal.target))
    }
    val sofar = when (goal.kind) {
        GoalKind.TAKINGS -> stringResource(R.string.goal_so_far, coins(goal.progress))
        GoalKind.GUESTS_FED -> stringResource(R.string.goal_so_far, goal.progress.toString())
        GoalKind.STARS -> stringResource(R.string.goal_now, stars(goal.progress))
    }
    val left = if (daysLeft == 1) stringResource(R.string.goal_last_day) else pluralStringResource(R.plurals.goal_days_left, daysLeft, daysLeft)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.goal_line, what, coins(goal.reward)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        // How far along it is.
        val fraction = if (goal.target <= 0) 1f else (goal.progress.toFloat() / goal.target).coerceIn(0f, 1f)
        Box(
            modifier = Modifier.padding(top = 3.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(LeafGreen))
        }
        Text(
            stringResource(R.string.goal_progress, sofar, left),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** A reputation as stars out of five, to the half: 90 is "4.5". */
private fun stars(reputation: Long): String {
    val halves = (reputation / 10).toInt()
    return if (halves % 2 == 0) "${halves / 2}" else "${halves / 2}.5"
}

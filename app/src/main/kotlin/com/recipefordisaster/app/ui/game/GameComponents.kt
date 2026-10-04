package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.LeafGreen
import com.recipefordisaster.app.ui.theme.MustardAmber
import com.recipefordisaster.domain.employee.PersonalityTrait
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.LogTone
import java.text.NumberFormat

/** Green at 66+, amber from 33, red below — for "higher is better" stats like reputation, cleanliness, morale. */
fun statColorFor(value: Int): Color = when {
    value >= 66 -> LeafGreen
    value >= 33 -> MustardAmber
    else -> DisasterRed
}

/** The same scale flipped, for "higher is worse" stats like stress. */
fun inverseStatColorFor(value: Int): Color = statColorFor(100 - value)

@Composable
fun toneColor(tone: LogTone): Color = when (tone) {
    LogTone.GOOD -> LeafGreen
    LogTone.BAD -> DisasterRed
    LogTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

fun moneyColor(amount: Long): Color = if (amount >= 0) LeafGreen else DisasterRed

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

/** "1,500 🪙" — grouping follows the device locale. */
@Composable
fun coins(amount: Long): String = stringResource(R.string.coins, numberFormat.format(amount))

@Composable
fun GameCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * A labelled 0-100 meter. The number is shown next to the bar (or is part
 * of the label, with [showValue] off), so the reading never depends on
 * telling green from red.
 */
@Composable
fun Meter(label: String, value: Int, color: Color, modifier: Modifier = Modifier, showValue: Boolean = true) {
    val clamped = value.coerceIn(0, 100)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = "$label $clamped of 100" }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            if (showValue) {
                Text(text = stringResource(R.string.percent_value, clamped), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** A big headline number with an emoji, optionally with a meter underneath. */
@Composable
fun StatCard(
    emoji: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    meterValue: Int? = null,
) {
    GameCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
            }
        }
        if (meterValue != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val color = statColorFor(meterValue)
            LinearProgressIndicator(
                progress = { meterValue.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/** A colored circle with someone's initial; the color tracks a 0-100 stat (morale, for staff). */
@Composable
fun Avatar(name: String, colorValue: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(statColorFor(colorValue)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
fun DayBadge(day: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.log_day_badge, day),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A label/value row; the value can be tinted (green profit, red loss). */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.COOK -> R.string.role_cook
        Role.SERVER -> R.string.role_server
        Role.DISHWASHER -> R.string.role_dishwasher
        Role.MANAGER -> R.string.role_manager
    },
)

@Composable
fun traitLabel(trait: PersonalityTrait): String = stringResource(
    when (trait) {
        PersonalityTrait.PERFECTIONIST -> R.string.trait_perfectionist
        PersonalityTrait.SLACKER -> R.string.trait_slacker
        PersonalityTrait.GOSSIP -> R.string.trait_gossip
        PersonalityTrait.LOYAL -> R.string.trait_loyal
        PersonalityTrait.HOTHEADED -> R.string.trait_hotheaded
        PersonalityTrait.ANXIOUS -> R.string.trait_anxious
    },
)

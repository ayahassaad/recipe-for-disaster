package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.Locale

/** Green at 66+, amber from 33, red below — for "higher is better" stats like reputation, cleanliness, morale. */
fun statColorFor(value: Int): Color = when {
    value >= 66 -> LeafGreen
    value >= 33 -> MustardAmber
    else -> DisasterRed
}

@Composable
fun toneColor(tone: LogTone): Color = when (tone) {
    LogTone.GOOD -> LeafGreen
    LogTone.BAD -> DisasterRed
    LogTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

fun moneyColor(amount: Long): Color = if (amount >= 0) LeafGreen else DisasterRed

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

/** "1,500 coins" — grouping follows the device locale. */
@Composable
fun coins(amount: Long): String = stringResource(R.string.coins, numberFormat.format(amount))

/** "+79 coins" / "-120 coins". */
@Composable
fun signedCoins(amount: Long): String = (if (amount > 0) "+" else "") + coins(amount)

/** "78 a day" — for anything paid every day, like wages. */
@Composable
fun perDay(amount: Long): String = stringResource(R.string.coins_per_day, numberFormat.format(amount))

/** "5" for whole amounts, "4.3" otherwise — in the device's own decimal format. */
internal fun formatQuantity(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else String.format(Locale.getDefault(), "%.1f", quantity)

/** A plain cream card — the "paper" everything off the chalkboard sits on. */
@Composable
fun GameCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** A 0-100 bar with its label. The number is spoken to screen readers; on screen the bar and colour carry it. */
@Composable
fun Meter(label: String, value: Int, modifier: Modifier = Modifier) {
    val clamped = value.coerceIn(0, 100)
    val color = statColorFor(clamped)
    val description = stringResource(R.string.meter_value, label, clamped)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { clamped / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** A label/value row; the value can be tinted (green profit, red loss). */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
        Role.HOST -> R.string.role_host
        Role.BUSSER -> R.string.role_busser
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

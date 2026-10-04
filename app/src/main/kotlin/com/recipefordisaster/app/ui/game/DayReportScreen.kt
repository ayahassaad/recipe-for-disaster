package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.simulation.LogTone

/**
 * The end-of-day results: who got fed, where the money went, and what
 * happened overnight. This is the moment the game explains itself — every
 * number that changed since the morning is accounted for here, so nothing
 * moves "silently" between days.
 */
@Composable
internal fun DayReportScreen(
    report: DayReport,
    nextDay: Int,
    gameOver: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = report.summary
    val books = report.books

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Text(
                    text = stringResource(R.string.report_title, summary.day),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) {
                    Text(
                        text = if (gameOver) stringResource(R.string.report_game_over) else stringResource(R.string.report_next, nextDay),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            report.event?.let { event -> item { EventCard(event) } }

            item {
                GameCard {
                    SectionHeader(stringResource(R.string.report_guests_heading))
                    Text(
                        text = stringResource(R.string.report_fed, summary.customersFed, summary.customersArrived),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (summary.unfedKitchenFull > 0) ReportLine(stringResource(R.string.report_unfed_kitchen, summary.unfedKitchenFull), LogTone.BAD)
                    if (summary.unfedOutOfStock > 0) ReportLine(stringResource(R.string.report_unfed_stock, summary.unfedOutOfStock), LogTone.BAD)
                    if (summary.walkedOut > 0) ReportLine(stringResource(R.string.report_walked_out, summary.walkedOut), LogTone.NEUTRAL)
                    Spacer(modifier = Modifier.height(10.dp))
                    Meter(stringResource(R.string.report_mood), summary.averageSatisfaction, statColorFor(summary.averageSatisfaction))
                    val reputationChange = summary.reputationAfter - summary.reputationBefore
                    ReportLine(
                        text = stringResource(R.string.report_reputation, summary.reputationBefore, summary.reputationAfter) +
                            " (" + (if (reputationChange >= 0) "+" else "") + reputationChange + ")",
                        tone = when {
                            reputationChange > 0 -> LogTone.GOOD
                            reputationChange < 0 -> LogTone.BAD
                            else -> LogTone.NEUTRAL
                        },
                    )
                }
            }

            item {
                GameCard {
                    SectionHeader(stringResource(R.string.report_money_heading))
                    StatRow(stringResource(R.string.report_earned), "+" + coins(books.revenue), valueColor = moneyColor(books.revenue))
                    MoneyRow(stringResource(R.string.report_wages), books.wages)
                    val oneOff = report.morningSpending.staffing + report.morningSpending.cleaning + report.morningSpending.menu
                    MoneyRow(stringResource(R.string.report_premises), books.rent + books.utilities + books.maintenance + books.miscellaneous - oneOff)
                    MoneyRow(stringResource(R.string.report_groceries), books.ingredientCosts)
                    MoneyRow(stringResource(R.string.report_repairs), books.upgrades)
                    MoneyRow(stringResource(R.string.report_other), oneOff)
                    if (books.eventCashDelta != 0L) {
                        StatRow(stringResource(R.string.report_events), signedCoins(books.eventCashDelta), valueColor = moneyColor(books.eventCashDelta))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    StatRow(stringResource(R.string.report_total), signedCoins(books.profitOrLoss), valueColor = moneyColor(books.profitOrLoss))
                    Text(
                        text = stringResource(R.string.report_cash, coins(summary.cashBefore), coins(summary.cashAfter)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyRow(label: String, amount: Long) {
    if (amount == 0L) return
    StatRow(label, "−" + coins(amount), valueColor = moneyColor(-amount))
}

@Composable
private fun signedCoins(amount: Long): String = (if (amount > 0) "+" else "") + coins(amount)

@Composable
private fun ReportLine(text: String, tone: LogTone) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = toneColor(tone), modifier = Modifier.padding(top = 4.dp))
}

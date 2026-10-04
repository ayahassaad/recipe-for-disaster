package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recipefordisaster.app.R
import com.recipefordisaster.app.ui.theme.ChalkWhite
import com.recipefordisaster.app.ui.theme.DisasterRed
import com.recipefordisaster.app.ui.theme.ReceiptFont
import com.recipefordisaster.app.ui.theme.ReceiptInk
import com.recipefordisaster.app.ui.theme.WoodBrown
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState

private val receiptText = TextStyle(fontFamily = ReceiptFont, fontSize = 15.sp, lineHeight = 22.sp, color = ReceiptInk)

/**
 * The end of a day, printed like a till receipt: the profit big at the
 * top, three short lines (guests, reputation, anything that happened
 * overnight), and the itemised bill one tap away for anyone who wants it.
 */
@Composable
internal fun BillScreen(report: DayReport, gameOver: Boolean, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val summary = report.summary
    val books = report.books
    val profit = books.profitOrLoss
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().background(WoodBrown).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Receipt {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = receiptText.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.bill_title, summary.day),
                    style = receiptText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Dashes()
                Text(
                    text = stringResource(if (profit >= 0) R.string.bill_made else R.string.bill_lost),
                    style = receiptText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = signedCoins(profit),
                    style = receiptText.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = moneyColor(profit)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Dashes()
                ReceiptLine(stringResource(R.string.bill_fed), stringResource(R.string.bill_fed_value, summary.customersFed, summary.customersArrived))
                if (summary.unfedKitchenFull > 0) ReceiptNote(stringResource(R.string.bill_hungry_kitchen, summary.unfedKitchenFull))
                if (summary.unfedOutOfStock > 0) ReceiptNote(stringResource(R.string.bill_hungry_stock, summary.unfedOutOfStock))
                if (summary.walkedOut > 0) ReceiptNote(stringResource(R.string.bill_walked_out, summary.walkedOut))
                val change = summary.reputationAfter - summary.reputationBefore
                ReceiptLine(stringResource(R.string.bill_reputation), (if (change > 0) "+" else "") + change)
                report.event?.let { event ->
                    Dashes()
                    Text(text = event.title.uppercase(), style = receiptText.copy(fontWeight = FontWeight.Bold))
                    Text(text = event.description, style = receiptText)
                }

                if (showDetails) {
                    Dashes()
                    val oneOff = report.morningSpending.staffing + report.morningSpending.cleaning + report.morningSpending.menu
                    ReceiptLine(stringResource(R.string.bill_earned), signedCoins(books.revenue))
                    ReceiptLine(stringResource(R.string.bill_wages), signedCoins(-books.wages))
                    ReceiptLine(stringResource(R.string.bill_premises), signedCoins(-(books.rent + books.utilities + books.maintenance + books.miscellaneous - oneOff)))
                    if (books.ingredientCosts > 0) ReceiptLine(stringResource(R.string.bill_groceries), signedCoins(-books.ingredientCosts))
                    if (books.upgrades > 0) ReceiptLine(stringResource(R.string.bill_repairs), signedCoins(-books.upgrades))
                    if (oneOff > 0) ReceiptLine(stringResource(R.string.bill_other), signedCoins(-oneOff))
                    if (books.eventCashDelta != 0L) ReceiptLine(stringResource(R.string.bill_events), signedCoins(books.eventCashDelta))
                    Dashes()
                    ReceiptLine(stringResource(R.string.bill_total), signedCoins(profit), bold = true)
                    ReceiptLine(stringResource(R.string.bill_cash), coins(summary.cashAfter))
                }
                TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        text = stringResource(if (showDetails) R.string.bill_details_hide else R.string.bill_details_show),
                        color = ReceiptInk,
                    )
                }
            }
        }
        SignButton(
            text = stringResource(if (gameOver) R.string.bill_game_over else R.string.bill_next),
            onClick = onContinue,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The run is over: the last bill, with the totals for the whole run. */
@Composable
internal fun FinalBillScreen(state: GameState, onBackToStart: () -> Unit, modifier: Modifier = Modifier) {
    val cause = stringResource(
        when (state.restaurant.status) {
            RestaurantStatus.BANKRUPT -> R.string.final_bankrupt
            RestaurantStatus.CONDEMNED -> R.string.final_condemned
            else -> R.string.final_closed
        },
    )
    val best = state.dishSalesTotals.maxByOrNull { it.value }
    val bestName = best?.let { (id, _) -> state.menu.firstOrNull { it.id.value == id }?.name ?: id }
    val profit = state.ledger.totalProfitOrLoss

    Column(modifier = modifier.fillMaxSize().background(WoodBrown).statusBarsPadding().navigationBarsPadding()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Receipt {
                Text(
                    text = stringResource(R.string.final_title).uppercase(),
                    style = receiptText.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = cause, style = receiptText.copy(color = DisasterRed), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Dashes()
                // The run ended during the day shown, so the last full day open is the one before.
                ReceiptLine(stringResource(R.string.final_days), (state.day - 1).coerceAtLeast(0).toString())
                ReceiptLine(stringResource(R.string.final_revenue), coins(state.ledger.totalRevenue))
                ReceiptLine(stringResource(R.string.final_profit), signedCoins(profit), bold = true)
                if (best != null && bestName != null) ReceiptLine(stringResource(R.string.final_best), bestName)
            }
        }
        SignButton(text = stringResource(R.string.game_back_to_start), onClick = onBackToStart, modifier = Modifier.padding(16.dp))
    }
}

/** "How to play", laid out like a restaurant menu: three courses and a house rule. */
@Composable
internal fun IntroScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Awning()
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Chalkboard {
                Text(
                    text = stringResource(R.string.intro_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChalkWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Course(1, stringResource(R.string.intro_step1_title), stringResource(R.string.intro_step1))
                Course(2, stringResource(R.string.intro_step2_title), stringResource(R.string.intro_step2))
                Course(3, stringResource(R.string.intro_step3_title), stringResource(R.string.intro_step3))
                HorizontalDivider(color = ChalkWhite.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.intro_goal),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ChalkWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SignButton(text = stringResource(R.string.intro_start), onClick = onStart, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Course(number: Int, title: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(text = "$number.", style = MaterialTheme.typography.headlineSmall, color = ChalkWhite, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = ChalkWhite)
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = ChalkWhite.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String, bold: Boolean = false) {
    val style = if (bold) receiptText.copy(fontWeight = FontWeight.Bold) else receiptText
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = style, modifier = Modifier.weight(1f))
        Text(text = value, style = style)
    }
}

@Composable
private fun ReceiptNote(text: String) {
    Text(text = text, style = receiptText.copy(fontSize = 13.sp, color = DisasterRed), modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun Dashes() {
    Text(
        text = "- ".repeat(40),
        style = receiptText.copy(color = ReceiptInk.copy(alpha = 0.4f)),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

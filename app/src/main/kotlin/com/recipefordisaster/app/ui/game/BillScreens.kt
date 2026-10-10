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
import androidx.compose.runtime.Composable
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
import com.recipefordisaster.domain.service.ServiceNight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField

private val receiptText = TextStyle(fontFamily = ReceiptFont, fontSize = 15.sp, lineHeight = 22.sp, color = ReceiptInk)

/**
 * The day's full bill, printed like a till receipt: profit, guests fed (and
 * why any weren't), reputation, the overnight event, and every line of
 * money in and out. Opened from the end-of-night panel for anyone who wants
 * the detail; the scene itself already showed what happened.
 */
@Composable
internal fun BillReceipt(report: DayReport, modifier: Modifier = Modifier) {
    val summary = report.summary
    val books = report.books
    val profit = books.profitOrLoss
    Receipt(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = report.startOfService.restaurant.displayName.uppercase(),
            style = receiptText.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = stringResource(R.string.bill_title, summary.day), style = receiptText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Dashes()
        ReceiptLine(stringResource(R.string.bill_fed), stringResource(R.string.bill_fed_value, summary.customersFed, summary.customersArrived))
        if (summary.unfedKitchenFull > 0) ReceiptNote(stringResource(R.string.bill_hungry_kitchen, summary.unfedKitchenFull))
        if (summary.unfedOutOfStock > 0) ReceiptNote(stringResource(R.string.bill_hungry_stock, summary.unfedOutOfStock))
        if (summary.walkedOut > 0) ReceiptNote(stringResource(R.string.bill_walked_out, summary.walkedOut))
        if (summary.gaveUpWaiting > 0) {
            // Split by what they were waiting for, when the night recorded it.
            val split = report.gaveUp
            if (split.isEmpty()) {
                ReceiptNote(stringResource(R.string.bill_gave_up, summary.gaveUpWaiting))
            } else {
                split[ServiceNight.WaitedFor.TABLE]?.let { ReceiptNote(stringResource(R.string.bill_gave_up_table, it)) }
                split[ServiceNight.WaitedFor.DRINKS]?.let { ReceiptNote(stringResource(R.string.bill_gave_up_drinks, it)) }
                split[ServiceNight.WaitedFor.ORDER]?.let { ReceiptNote(stringResource(R.string.bill_gave_up_order, it)) }
                split[ServiceNight.WaitedFor.FOOD]?.let { ReceiptNote(stringResource(R.string.bill_gave_up_food, it)) }
            }
        }
        val change = summary.reputationAfter - summary.reputationBefore
        ReceiptLine(stringResource(R.string.bill_reputation), (if (change > 0) "+" else "") + change)
        Dashes()
        val oneOff = report.morningSpending.staffing + report.morningSpending.cleaning + report.morningSpending.menu
        ReceiptLine(stringResource(R.string.bill_earned), signedCoins(books.revenue - books.tips - books.drinks))
        if (books.drinks > 0) ReceiptLine(stringResource(R.string.bill_drinks), signedCoins(books.drinks))
        if (books.tips > 0) ReceiptLine(stringResource(R.string.bill_tips), signedCoins(books.tips))
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
internal fun IntroScreen(onStart: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Naming the place: it goes on the sign over the door and on every bill.
            Text(stringResource(R.string.name_prompt), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(com.recipefordisaster.domain.restaurant.MAX_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text(com.recipefordisaster.domain.restaurant.DEFAULT_NAME) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
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
        SignButton(text = stringResource(R.string.intro_start), onClick = { onStart(name) }, modifier = Modifier.padding(16.dp))
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

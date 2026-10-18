package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.economy.Loans

/** The bank: what you owe, what it costs a day, and a stepper to borrow more or pay some back this morning. */
@Composable
internal fun BankDialog(uiState: GameUiState.Playing, actions: GameActions, onClose: () -> Unit) {
    val state = uiState.state
    val change = uiState.plan.loanChange
    val owed = state.loan + change
    val canLess = Loans.clamp(state, change - Loans.STEP) != change
    val canMore = Loans.clamp(state, change + Loans.STEP) != change
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.bank_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (owed > 0) stringResource(R.string.bank_owe, owed) else stringResource(R.string.bank_owe_nothing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (owed > 0) {
                    val late = Loans.overdueDays(state) > 0
                    val percent = if (late) Loans.OVERDUE_INTEREST_PERCENT else Loans.DAILY_INTEREST_PERCENT
                    Text(stringResource(R.string.bank_interest, Loans.interest(owed, percent), percent), style = MaterialTheme.typography.bodyLarge)
                    // When it has to be paid back by, or how late it is and how long the bank will wait.
                    // (Counting what's planned this morning: borrowing today starts the clock today.)
                    val after = Loans.applied(state, change)
                    val left = Loans.daysUntilDue(after)
                    when {
                        late -> Text(
                            stringResource(R.string.bank_late, Loans.overdueDays(state), Loans.daysBeforeForeclosure(state) ?: 0),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        after.loanDueDay != null && left != null -> Text(stringResource(R.string.bank_due, after.loanDueDay!!, left), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val less = stringResource(R.string.bank_less)
                    val more = stringResource(R.string.bank_more)
                    OutlinedButton(onClick = { actions.onAdjustLoan(-Loans.STEP) }, enabled = canLess, modifier = Modifier.semantics { contentDescription = less }) { Text("−${Loans.STEP}") }
                    Text(
                        when {
                            change > 0 -> stringResource(R.string.bank_borrow, change)
                            change < 0 -> stringResource(R.string.bank_repay, -change)
                            else -> stringResource(R.string.bank_nochange)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { actions.onAdjustLoan(Loans.STEP) }, enabled = canMore, modifier = Modifier.semantics { contentDescription = more }) { Text("+${Loans.STEP}") }
                }
                // Why you can't borrow, if you can't.
                when (Loans.refusal(state)) {
                    Loans.Refusal.LATE -> Text(stringResource(R.string.bank_no_loans_late), style = MaterialTheme.typography.bodyMedium)
                    Loans.Refusal.JUST_PAID_OFF -> Text(stringResource(R.string.bank_no_loans_yet, state.noLoanUntilDay), style = MaterialTheme.typography.bodyMedium)
                    null -> {}
                }
                Text(stringResource(R.string.bank_limit, Loans.LIMIT, Loans.TERM_DAYS), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.bank_close)) } },
    )
}

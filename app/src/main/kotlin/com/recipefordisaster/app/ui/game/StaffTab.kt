package com.recipefordisaster.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.simulation.DailyCosts
import com.recipefordisaster.domain.simulation.LogTone

/**
 * The team, as it stands this morning: people hired today are already in
 * it (marked "new"), people let go are shown on their way out, and either
 * can be undone until service starts. The wage bill at the top is the
 * number that matters most when hiring, because it's paid every day.
 */
@Composable
internal fun StaffTab(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    val plan = uiState.plan
    val costs = remember(uiState.morning) { DailyCosts.of(uiState.morning) }
    val hiredToday = uiState.state.applicants.filter { it.id in plan.hires }
    // Everyone who started the day here (including anyone being let go, so it can be undone), then today's hires.
    val team = uiState.state.employees + hiredToday
    val stillApplying = uiState.state.applicants.filter { it.id !in plan.hires }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GameCard {
                SectionHeader(stringResource(R.string.staff_costs_heading))
                Text(
                    text = coinsPerDay(costs.total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.staff_costs_detail, coins(costs.wages), coins(costs.premises)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.staff_heading)) }
        if (team.isEmpty()) {
            item { Text(stringResource(R.string.staff_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(team, key = { it.id.value }) { employee ->
            val isNew = employee.id in plan.hires
            EmployeeCard(
                employee = employee,
                isNew = isNew,
                leaving = employee.id in plan.fires,
                resting = employee.id in plan.restDays,
                canAffordSeverance = uiState.cashNow >= StaffingMarket.severance(employee),
                onUndoHire = { actions.onToggleHire(employee.id) },
                onToggleRest = { actions.onToggleRestDay(employee.id) },
                onToggleFire = { actions.onToggleFire(employee.id) },
            )
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.applicants_heading))
                Text(stringResource(R.string.applicants_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (stillApplying.isEmpty()) {
            item { Text(stringResource(R.string.applicants_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(stillApplying, key = { "applicant-" + it.id.value }) { applicant ->
            ApplicantCard(
                applicant = applicant,
                canAfford = uiState.cashNow >= StaffingMarket.hiringFee(applicant),
                onHire = { actions.onToggleHire(applicant.id) },
            )
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: Employee,
    isNew: Boolean,
    leaving: Boolean,
    resting: Boolean,
    canAffordSeverance: Boolean,
    onUndoHire: () -> Unit,
    onToggleRest: () -> Unit,
    onToggleFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(modifier = modifier.alpha(if (leaving) 0.6f else 1f)) {
        PersonHeader(employee)
        when {
            isNew -> StatusLine(stringResource(R.string.staff_new), LogTone.GOOD)
            leaving -> StatusLine(stringResource(R.string.staff_leaving), LogTone.BAD)
            resting -> StatusLine(stringResource(R.string.staff_resting), LogTone.NEUTRAL)
            employee.status == EmployeeStatus.SICK -> StatusLine(
                if (employee.sickDaysRemaining <= 1) {
                    stringResource(R.string.staff_sick_one)
                } else {
                    pluralStringResource(R.plurals.staff_sick, employee.sickDaysRemaining, employee.sickDaysRemaining)
                },
                LogTone.BAD,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Meter(stringResource(R.string.staff_morale), employee.morale, statColorFor(employee.morale), Modifier.weight(1f))
            Meter(stringResource(R.string.staff_stress), employee.stress, inverseStatColorFor(employee.stress), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                isNew -> OutlinedButton(onClick = onUndoHire) { Text(stringResource(R.string.undo)) }
                leaving -> OutlinedButton(onClick = onToggleFire) { Text(stringResource(R.string.undo)) }
                else -> {
                    if (employee.status == EmployeeStatus.ACTIVE || resting) {
                        FilterChip(selected = resting, onClick = onToggleRest, label = { Text(stringResource(R.string.staff_day_off)) })
                    }
                    FilterChip(
                        selected = false,
                        onClick = onToggleFire,
                        enabled = canAffordSeverance,
                        label = { Text(stringResource(R.string.staff_fire, coins(StaffingMarket.severance(employee)))) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicantCard(applicant: Employee, canAfford: Boolean, onHire: () -> Unit, modifier: Modifier = Modifier) {
    GameCard(modifier = modifier) {
        PersonHeader(applicant)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.applicant_stats, applicant.skill, applicant.speed, applicant.reliability),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (applicant.personalityTraits.isNotEmpty()) {
            Text(
                text = applicant.personalityTraits.map { traitLabel(it) }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(onClick = onHire, enabled = canAfford) {
            Text(
                if (canAfford) {
                    stringResource(
                        R.string.applicant_hire,
                        coins(StaffingMarket.hiringFee(applicant)),
                        coinsPerDay(applicant.salaryPerDay),
                    )
                } else {
                    stringResource(R.string.cant_afford)
                },
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, tone: LogTone) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = toneColor(tone))
}

@Composable
private fun PersonHeader(person: Employee) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = person.name, colorValue = person.morale)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.staff_role_salary, roleLabel(person.role), coinsPerDay(person.salaryPerDay)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket

@Composable
internal fun StaffTab(uiState: GameUiState.Playing, actions: GameActions, modifier: Modifier = Modifier) {
    val state = uiState.state
    val plan = uiState.plan

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.staff_heading)) }
        if (state.employees.isEmpty()) {
            item { Text(stringResource(R.string.staff_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(state.employees, key = { it.id.value }) { employee ->
            EmployeeCard(
                employee = employee,
                restingToday = employee.id in plan.restDays,
                beingFired = employee.id in plan.fires,
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
        if (state.applicants.isEmpty()) {
            item { Text(stringResource(R.string.applicants_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(state.applicants, key = { it.id.value }) { applicant ->
            ApplicantCard(
                applicant = applicant,
                hiring = applicant.id in plan.hires,
                onToggleHire = { actions.onToggleHire(applicant.id) },
            )
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: Employee,
    restingToday: Boolean,
    beingFired: Boolean,
    onToggleRest: () -> Unit,
    onToggleFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameCard(modifier = modifier) {
        PersonHeader(employee)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Meter(stringResource(R.string.staff_morale), employee.morale, statColorFor(employee.morale), Modifier.weight(1f))
            Meter(stringResource(R.string.staff_stress), employee.stress, inverseStatColorFor(employee.stress), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (employee.status == EmployeeStatus.SICK) {
            Text(
                text = if (employee.sickDaysRemaining <= 1) {
                    stringResource(R.string.staff_sick_one)
                } else {
                    pluralStringResource(R.plurals.staff_sick, employee.sickDaysRemaining, employee.sickDaysRemaining)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (employee.status == EmployeeStatus.ACTIVE) {
                FilterChip(selected = restingToday, onClick = onToggleRest, label = { Text("😴 " + stringResource(R.string.staff_day_off)) })
            }
            FilterChip(
                selected = beingFired,
                onClick = onToggleFire,
                label = { Text("👋 " + stringResource(R.string.staff_fire, coins(StaffingMarket.severance(employee)))) },
            )
        }
    }
}

@Composable
private fun ApplicantCard(applicant: Employee, hiring: Boolean, onToggleHire: () -> Unit, modifier: Modifier = Modifier) {
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
        Spacer(modifier = Modifier.height(6.dp))
        FilterChip(
            selected = hiring,
            onClick = onToggleHire,
            label = { Text("🤝 " + stringResource(R.string.applicant_hire, coins(StaffingMarket.hiringFee(applicant)))) },
        )
    }
}

@Composable
private fun PersonHeader(person: Employee) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = person.name, colorValue = person.morale)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.staff_role_salary, roleLabel(person.role), coins(person.salaryPerDay)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

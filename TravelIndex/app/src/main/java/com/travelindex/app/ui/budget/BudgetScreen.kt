package com.travelindex.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelindex.app.R
import com.travelindex.app.domain.BudgetCalculator
import com.travelindex.app.domain.ExpenseCategory
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.ui.components.EmptyState
import com.travelindex.app.ui.components.LoadingBox
import com.travelindex.app.ui.components.SectionCard
import com.travelindex.app.ui.components.SectionTitle
import com.travelindex.app.ui.modules.Choices
import com.travelindex.app.ui.modules.ModuleItemCard
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.modules.TripBundleViewModel
import com.travelindex.app.ui.theme.CenteredContent
import com.travelindex.app.ui.theme.Spacing
import com.travelindex.app.ui.theme.StatusDanger
import java.time.LocalDate

private val categoryColors = mapOf(
    ExpenseCategory.FLIGHTS to Color(0xFF1FB5A8),
    ExpenseCategory.LODGING to Color(0xFF3B82C4),
    ExpenseCategory.FOOD to Color(0xFFF5A524),
    ExpenseCategory.TRANSPORT to Color(0xFF8B6FD6),
    ExpenseCategory.ACTIVITIES to Color(0xFFE5736F),
    ExpenseCategory.SHOPPING to Color(0xFF5BBF7A),
    ExpenseCategory.OTHER to Color(0xFF8A97AB),
)

/** Presupuesto y gastos de un viaje. Nunca se suman monedas distintas: cada moneda tiene su propio total y su propio gráfico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: TripBundleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bundle = state.bundle
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_budget)) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_expense)) },
            )
        },
    ) { pad ->
        if (bundle == null) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        CenteredContent(Modifier.padding(pad)) { Content(bundle, onOpen) }
    }
}

@Composable
private fun Content(b: TripBundle, onOpen: (Long) -> Unit) {
    val trip = b.trip
    val summary = remember(b.expenses, trip.budgetMinor, trip.currency) {
        BudgetCalculator.summarize(trip.budgetMinor, trip.currency, b.expenses)
    }
    val spec = ModuleSpecs.of(ModuleType.EXPENSE)
    val expenseItems = remember(b.expenses) {
        ModuleItems.of(ModuleType.EXPENSE, b).sortedByDescending { it.values["date"].orEmpty() }
    }
    val today = LocalDate.now()

    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            SectionCard(stringResource(R.string.budget_summary)) {
                val total = trip.budgetMinor
                if (total != null) {
                    Text(stringResource(R.string.budget_of, Money.format(total, trip.currency)), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(stringResource(R.string.budget_not_set), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.spentByCurrency.isEmpty()) {
                    Text(stringResource(R.string.budget_no_expenses), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                summary.spentByCurrency.entries.sortedBy { it.key }.forEach { (cur, minor) ->
                    Text(stringResource(R.string.budget_spent_in, Money.format(minor, cur)), style = MaterialTheme.typography.bodyLarge)
                }
                summary.consumedFraction?.let { fr ->
                    LinearProgressIndicator(
                        progress = { fr.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (fr > 1f) StatusDanger else MaterialTheme.colorScheme.secondary,
                    )
                }
                summary.remainingMinor?.let { rem ->
                    val text = if (rem >= 0) stringResource(R.string.budget_remaining, Money.format(rem, trip.currency))
                    else stringResource(R.string.budget_over, Money.format(-rem, trip.currency))
                    Text(text, style = MaterialTheme.typography.bodyMedium,
                        color = if (rem < 0) StatusDanger else MaterialTheme.colorScheme.onSurface)
                }
                if (summary.hasOtherCurrencies) {
                    Text(stringResource(R.string.budget_other_currencies), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Un gráfico por moneda, nunca mezclados.
        summary.spentByCurrency.keys.sorted().forEach { cur ->
            val rows = summary.byCategory.mapNotNull { (cat, byCur) -> byCur[cur]?.let { cat to it } }
                .filter { it.second > 0 }.sortedByDescending { it.second }
            if (rows.isNotEmpty()) item(key = "chart-$cur") {
                SectionCard(stringResource(R.string.budget_by_category, cur)) {
                    val max = rows.maxOf { it.second }.toFloat()
                    rows.forEach { (cat, minor) ->
                        BarRow(
                            label = Choices.label(Choices.expenseCategory, cat.name)?.let { stringResource(it) } ?: cat.name,
                            amount = Money.format(minor, cur), fraction = minor / max, color = categoryColors.getValue(cat),
                        )
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.budget_expenses_title)) }
        if (expenseItems.isEmpty()) {
            item {
                EmptyState(
                    stringResource(spec.emptyTitleRes), stringResource(spec.emptyBodyRes), icon = Icons.Outlined.Payments,
                )
            }
        } else {
            items(expenseItems, key = { it.id }) { exp ->
                ModuleItemCard(spec, exp, today, trip.endDate, onClick = { onOpen(exp.id) })
            }
        }
    }
}

@Composable
private fun BarRow(label: String, amount: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Text(amount, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

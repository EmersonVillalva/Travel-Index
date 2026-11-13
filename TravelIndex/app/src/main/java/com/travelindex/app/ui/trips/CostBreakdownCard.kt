package com.travelindex.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.domain.CostBreakdown
import com.travelindex.app.domain.CostCategory
import com.travelindex.app.domain.CostLine
import com.travelindex.app.domain.Money
import com.travelindex.app.ui.theme.Spacing

@Composable
private fun categoryName(c: CostCategory): String = stringResource(
    when (c) {
        CostCategory.FLIGHTS -> R.string.cost_flights
        CostCategory.LODGING -> R.string.cost_lodging
        CostCategory.ACTIVITIES -> R.string.cost_activities
        CostCategory.TRANSPORT -> R.string.cost_transport
        CostCategory.RESTAURANTS -> R.string.cost_restaurants
        CostCategory.OTHER -> R.string.cost_other
    }
)

private fun amounts(byCurrency: Map<String, Long>): String =
    byCurrency.entries.joinToString(" · ") { (cur, minor) -> Money.format(minor, cur) }

/** Lo que se pagó en un viaje, separado por vuelos, alojamiento y el resto. Las monedas distintas nunca se suman entre sí. */
@Composable
fun CostBreakdownCard(lines: List<CostLine>, modifier: Modifier = Modifier) {
    val totals = CostBreakdown.totals(lines)
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.cost_title), style = MaterialTheme.typography.titleMedium)
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(categoryName(l.category), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(amounts(l.byCurrency), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
                }
            }
            if (lines.size > 1) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.cost_total), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(amounts(totals), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
                }
            }
            if (totals.size > 1) Text(
                stringResource(R.string.cost_currencies_note), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

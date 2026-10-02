package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.ui.components.*

internal fun List<RecollectionCurrency>.moneyLabel(bucket: RecollectionBucket? = null): String =
    if (isEmpty()) "—" else joinToString("\n") { ledger ->
        ledger.amount(bucket)?.let { Money.format(it, ledger.currency) } ?: "Importe no disponible (${ledger.currency})"
    }

internal fun List<RecollectionCurrency>.costKgLabel(): String =
    if (isEmpty()) "—" else joinToString("\n") { ledger ->
        ledger.costPerKgMinor?.let { "${Money.format(it, ledger.currency)}/kg" } ?: "— (${ledger.currency})"
    }

@Composable
internal fun RecollectionTotalCards(ledger: List<RecollectionCurrency>, day: Boolean, weighed: String?) {
    MoKpiMetric(if (day) "Coste del día" else "Total recogida", ledger.moneyLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost" else "dashboard-cost"),
        icon = MoIcons.Euro, kind = MoKpiKind.TOTAL,
        supportingText = "Solo gastos confirmados · pagar no cambia el coste")
    MoKpiMetric("Coste/kg", ledger.costKgLabel(),
        Modifier.fillMaxWidth().testTag(if (day) "day-cost-per-kg" else "dashboard-cost-per-kg"),
        icon = MoIcons.Percent, kind = MoKpiKind.TOTAL,
        supportingText = weighed?.let { "Sobre $it pesados" } ?: "Sin kilos pesados")
}

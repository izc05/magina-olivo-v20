package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency
import com.isivoltpro.maginaolivo.domain.labour.LabourByWorker
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary

internal const val CAMPAIGN_NO_LABOUR = "Sin jornales registrados"
internal const val CAMPAIGN_NO_ATTENDANCE = "Sin personas anotadas"

/**
 * #365: the campaign's Jornales in one line, from the labour lines and the Expense ledger:
 * «1 persona · 1 jornada · 65,00 €». Named people are counted by person; lines kept only as a
 * count (historical) are said apart and never counted as known people. Money is only the
 * confirmed (posted) labour cost; nothing is computed from rates here.
 */
internal fun campaignLabourLine(entries: List<LabourEntry>, ledger: List<RecollectionCurrency>): String {
    val cost = ledger.takeIf { currencies -> currencies.any { it.hasPosted(RecollectionBucket.LABOUR) } }
        ?.moneyLabel(RecollectionBucket.LABOUR)?.replace("\n", " · ")
    // Codex #382: a confirmed labour cost kept only in the ledger (a day's «Jornales/servicio»)
    // is still shown, saying that nobody's attendance was noted.
    if (entries.isEmpty()) return cost?.let { "$CAMPAIGN_NO_ATTENDANCE · $it" } ?: CAMPAIGN_NO_LABOUR
    val people = LabourByWorker.of(entries).size
    val unnamed = LabourByWorker.unnamed(entries)
    return listOfNotNull(
        people.takeIf { it > 0 }?.let { if (it == 1) "1 persona" else "$it personas" },
        LabourSummary.of(entries).label().takeIf { it.isNotBlank() },
        cost,
        unnamed.people.takeIf { it > 0 }?.let { "$it sin nombre (histórico)" },
    ).joinToString(" · ")
}

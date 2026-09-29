package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary

/**
 * CR-011 §9/§23 — the one line a día de recolección is recognised by in a list:
 * «8.750 kg · 3 pesadas · 5 jornadas · 1 tractor». Each part is read from its own ledger (the
 * kilos from the Pesadas, labour from Labour, machinery from Equipment) and a part with
 * nothing recorded is simply left out, never shown as zero. Labour keeps its units (full days,
 * half days, hours) through [LabourSummary], exactly as the rest of the app shows it.
 */
internal fun dayRowLine(
    harvest: Harvest,
    pesadas: Int,
    labour: List<LabourEntry>,
    equipment: List<EquipmentLine>,
): String {
    val labourSummary = LabourSummary.of(labour)
    return listOfNotNull(
        if (harvest.awaitingPesadas) PENDING_KILOS else Weight.format(harvest.totalGrams),
        pesadas.takeIf { it > 0 }?.let { if (it == 1) "1 pesada" else "$it pesadas" },
        labourSummary.takeUnless { it.isEmpty }?.label(),
        equipment.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.text() },
    ).joinToString(" · ")
}

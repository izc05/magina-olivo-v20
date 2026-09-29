package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry

/**
 * CR-011 §9/§23 — the one line a día de recolección is recognised by in a list:
 * «8.750 kg · 3 pesadas · 5 jornales · 1 tractor». Each part is read from its own ledger (the
 * kilos from the Pesadas, jornales from Labour, machinery from Equipment) and a part with
 * nothing recorded is simply left out, never shown as zero.
 */
internal fun dayRowLine(
    harvest: Harvest,
    pesadas: Int,
    labour: List<LabourEntry>,
    equipment: List<EquipmentLine>,
): String {
    val people = labour.sumOf { it.quantity }
    return listOfNotNull(
        if (harvest.awaitingPesadas) PENDING_KILOS else Weight.format(harvest.totalGrams),
        pesadas.takeIf { it > 0 }?.let { if (it == 1) "1 pesada" else "$it pesadas" },
        people.takeIf { it > 0 }?.let { if (it == 1) "1 jornal" else "$it jornales" },
        equipment.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.text() },
    ).joinToString(" · ")
}

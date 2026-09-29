package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** CR-011 §9/§23: a día de recolección reads «8.750 kg · 3 pesadas · 5 jornadas · 1 tractor». */
class DayRowLineTest {
    private val dayId = UUID.randomUUID()

    @Test fun aFullDayShowsKilosPesadasJornalesAndMachinery() {
        val line = dayRowLine(
            day(8_750_000),
            pesadas = 3,
            labour = listOf(labour(3), labour(2)),
            equipment = listOf(EquipmentLine(UUID.randomUUID(), dayId, EquipmentType.TRACTOR, null, 1, null, 1)),
        )
        assertEquals("${Weight.format(8_750_000)} · 3 pesadas · 5 jornadas · 1 tractor", line)
    }

    @Test fun nothingRecordedIsLeftOutNotShownAsZero() {
        assertEquals("${Weight.format(1_200_000)} · 1 pesada", dayRowLine(day(1_200_000), 1, emptyList(), emptyList()))
    }

    @Test fun aDayWithOnlyJornalesSaysItsKilosAreStillPending() {
        assertEquals("$PENDING_KILOS · 1 jornada", dayRowLine(day(0), 0, listOf(labour(1)), emptyList()))
    }

    /** Codex #303: half days and hours keep their unit; they are never counted as full days. */
    @Test fun labourKeepsItsUnits() {
        val line = dayRowLine(
            day(1_000_000), 0,
            listOf(labour(1), labour(2, LabourUnit.HALF_DAY), labour(1, LabourUnit.HOURS, minutes = 90)),
            emptyList(),
        )
        assertEquals("${Weight.format(1_000_000)} · 1 jornada · 2 medias · 1 h 30 min", line)
    }

    private fun day(grams: Long) = Harvest(
        id = dayId, workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 12, 12), totalGrams = grams, shares = emptyList(),
        collectionMethod = null, workerCount = null, machineryText = null, notes = null, version = 1,
    )

    private fun labour(people: Int, unit: LabourUnit = LabourUnit.FULL_DAY, minutes: Int? = null) = LabourEntry(
        id = UUID.randomUUID(), harvestId = dayId, workerId = null, workerName = null,
        quantity = people, unit = unit, minutes = minutes, version = 1,
    )
}

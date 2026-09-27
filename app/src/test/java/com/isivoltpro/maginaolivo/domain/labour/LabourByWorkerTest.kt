package com.isivoltpro.maginaolivo.domain.labour

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** 254-D: labour per person by stable id; bare counts stay apart and are never shared out. */
class LabourByWorkerTest {
    private val juan = UUID.randomUUID()
    private val maria = UUID.randomUUID()
    private val day1 = UUID.randomUUID()
    private val day2 = UUID.randomUUID()

    @Test fun eachPersonAddsUpTheirOwnJornalesAcrossJornadas() {
        val entries = listOf(
            line(day1, juan, "Juan Pérez", LabourUnit.FULL_DAY),
            line(day2, juan, "Juan Pérez Gómez", LabourUnit.FULL_DAY), // renamed later: still one person
            line(day1, maria, "María López", LabourUnit.HALF_DAY),
            line(day2, null, null, LabourUnit.FULL_DAY, quantity = 3),
        )
        val byWorker = LabourByWorker.of(entries)

        assertEquals(listOf("Juan Pérez Gómez", "María López"), byWorker.map { it.name })
        assertEquals(2, byWorker[0].summary.fullDays)
        assertEquals(2, byWorker[0].jornadas)
        assertEquals(1, byWorker[1].summary.halfDays)
        assertEquals(3, LabourByWorker.unnamed(entries).fullDays)
    }

    private fun line(harvest: UUID, worker: UUID?, name: String?, unit: LabourUnit, quantity: Int = 1) =
        LabourEntry(UUID.randomUUID(), harvest, worker, name, quantity, unit, null, 1)
}

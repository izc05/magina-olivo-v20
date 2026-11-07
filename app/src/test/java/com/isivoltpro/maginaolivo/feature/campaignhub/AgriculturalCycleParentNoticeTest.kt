package com.isivoltpro.maginaolivo.feature.campaignhub

import com.isivoltpro.maginaolivo.domain.cycle.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AgriculturalCycleParentNoticeTest {
    private val farm=UUID.randomUUID()
    private val workspace=UUID.randomUUID()
    private val campaign=UUID.randomUUID()
    private val cycleId=UUID.randomUUID()
    private val date=LocalDate.parse("2026-12-01")
    private fun cycle(closed: Boolean)=AgriculturalCycle(cycleId,workspace,farm,"2026/27",LocalDate.parse("2026-02-01"),
        if(closed) LocalDate.parse("2027-01-25") else null,if(closed) Instant.parse("2027-02-01T12:00:00Z") else null,false,1)
    private fun recollection(linked: UUID?)=CycleRecord(campaign,"RECOLLECTION","Recogida",date,date,linked,"ACTIVE",1)
    private fun snapshot(cycles: List<AgriculturalCycle>, records: List<CycleRecord>)=
        CycleSnapshot(CycleScope(workspace,farm,null,1),cycles,records)

    @Test fun farmWithoutCyclesShowsNoNotice() {
        assertNull(parentNoticeMessage(snapshot(emptyList(),listOf(recollection(null))),campaign))
    }

    @Test fun unlinkedRecollectionAsksToLinkItFirst() {
        val message=parentNoticeMessage(snapshot(listOf(cycle(false)),listOf(recollection(null))),campaign)
        assertTrue(message!!.contains("Vincula primero la recolección"))
    }

    @Test fun recollectionLinkedToAClosedCycleSaysItIsClosed() {
        val message=parentNoticeMessage(snapshot(listOf(cycle(true)),listOf(recollection(cycleId))),campaign)
        assertTrue(message!!.contains("está cerrada"))
        assertTrue(message.contains("2026/27"))
    }

    @Test fun recollectionLinkedToTheOpenCycleShowsNoNotice() {
        assertNull(parentNoticeMessage(snapshot(listOf(cycle(false)),listOf(recollection(cycleId))),campaign))
    }
}

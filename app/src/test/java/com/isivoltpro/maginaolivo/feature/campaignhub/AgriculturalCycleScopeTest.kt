package com.isivoltpro.maginaolivo.feature.campaignhub

import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.notebook.FarmNotebook
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AgriculturalCycleScopeTest {
    private val farm=UUID.randomUUID()
    private val workspace=UUID.randomUUID()
    private val cycle=UUID.randomUUID()
    private val date=LocalDate.parse("2027-05-04")
    private fun expense(id: UUID)=Expense(id,workspace,date,"Factura QA",ExpenseCategory.OTHER,1234,"EUR",
        ExpenseStatus.POSTED,ExpenseOrigin.MANUAL,farmId=farm)
    private fun state(expenses: List<Expense>)=NotebookUiState(isLoading=false,
        farmNotebook=FarmNotebook(farm,emptyList(),emptyList(),emptyList(),expenses))
    private fun snapshot(records: List<CycleRecord>)=CycleSnapshot(CycleScope(workspace,farm,null,1),listOf(
        AgriculturalCycle(cycle,workspace,farm,"Nombre libre",LocalDate.parse("2026-02-01"),
            LocalDate.parse("2027-01-25"),java.time.Instant.parse("2027-05-01T12:00:00Z"),false,2)),records)

    @Test fun explicitlyLinkedLateInvoiceIsIncludedAndSameDateUnassignedExpenseIsNotInferred() {
        val included=expense(UUID.randomUUID())
        val unassigned=expense(UUID.randomUUID())
        val original=state(listOf(included,unassigned))
        val scoped=original.forAgriculturalCycle(snapshot(listOf(
            CycleRecord(included.id,"EXPENSE",included.concept,date,date,cycle,"POSTED",1))),cycle)
        assertEquals(listOf(included),scoped.farmNotebook!!.expenses)
        assertEquals(2,original.farmNotebook!!.expenses.size)
    }

    @Test fun browsingAnEmptyHistoricalCycleNeverFallsBackToTheCurrentRecollection() {
        val original=state(listOf(expense(UUID.randomUUID()))).copy(selectedCampaignId=UUID.randomUUID())
        val scoped=original.forAgriculturalCycle(snapshot(emptyList()),cycle)
        assertNull(scoped.selectedCampaignId)
        assertNull(scoped.notebook)
        assertTrue(scoped.farmNotebook!!.expenses.isEmpty())
        assertNotNull(original.selectedCampaignId)
    }

    @Test fun aDifferentFarmsSnapshotCannotRevealOrAttributeTheNotebook() {
        val original=state(listOf(expense(UUID.randomUUID())))
        val foreign=snapshot(emptyList()).let {it.copy(scope=it.scope.copy(farmId=UUID.randomUUID()))}
        val scoped=original.forAgriculturalCycle(foreign,cycle)
        assertNotNull(scoped.error)
        assertNull(scoped.farmNotebook)
    }
}

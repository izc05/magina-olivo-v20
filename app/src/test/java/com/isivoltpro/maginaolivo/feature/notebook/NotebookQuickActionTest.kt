package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** CR-011 §5 (was QuickAddActionTest): the one set of daily actions, one canonical Pesada. */
class NotebookQuickActionTest {
    @Test
    fun sixActionsWithOneCanonicalWeighing() {
        assertEquals(
            listOf("Trabajo", "Riego", "Tratamiento", "Pesada", "Jornal", "Gasto"),
            NotebookQuickAction.entries.map { it.label },
        )
        val labels = NotebookQuickAction.entries.map { it.label }
        // Harvest and delivery are one Pesada; documents and machinery live inside their record.
        listOf("Cosecha", "Entrega", "Documento", "Maquinaria", "Registrar hoy").forEach { assertFalse(it in labels) }
    }

    @Test
    fun notebookFarmResolutionRejectsGhostContextAndKeepsValidIntent() {
        val farmA = UUID.fromString("20000000-0000-0000-0000-000000000427")
        val farmB = UUID.fromString("20000000-0000-0000-0000-000000000428")
        val archived = UUID.fromString("20000000-0000-0000-0000-000000000429")
        val active = listOf(farmA, farmB)

        // A valid navigation context wins over the previous Cuaderno choice.
        assertEquals(farmB, resolveNotebookFarmId(active, requested = farmB, stored = farmA))
        // An archived/foreign request is never persisted; the last valid choice survives.
        assertEquals(farmA, resolveNotebookFarmId(active, requested = null, stored = farmA))
        // A stale stored id heals to the first active Farm instead of being retried next boot.
        assertEquals(farmA, resolveNotebookFarmId(active, requested = null, stored = archived))
        // With no active Farms there is no phantom context at all.
        assertNull(resolveNotebookFarmId(emptyList(), requested = null, stored = archived))
    }

    /** CR-011 §20/§21: each action has its own colour, and no two actions share one. */
    @Test
    fun everyActionHasItsOwnSectionTone() {
        assertEquals(
            listOf(MoIconTone.GROVE, MoIconTone.WATER, MoIconTone.TREATMENT, MoIconTone.VALUE, MoIconTone.LABOUR, MoIconTone.MONEY),
            NotebookQuickAction.entries.map { it.tone() },
        )
        assertEquals(NotebookQuickAction.entries.size, NotebookQuickAction.entries.map { it.tone() }.toSet().size)
    }
}

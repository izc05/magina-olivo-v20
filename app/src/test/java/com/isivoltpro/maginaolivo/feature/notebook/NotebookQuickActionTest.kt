package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

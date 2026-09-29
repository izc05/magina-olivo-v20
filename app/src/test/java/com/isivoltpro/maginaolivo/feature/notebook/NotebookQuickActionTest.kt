package com.isivoltpro.maginaolivo.feature.notebook

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
}

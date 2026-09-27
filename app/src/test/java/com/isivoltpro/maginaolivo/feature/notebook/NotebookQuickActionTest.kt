package com.isivoltpro.maginaolivo.feature.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookQuickActionTest {
    @Test
    fun collectionUsesOneCanonicalWeighingAction() {
        val actions = NotebookQuickAction.entries

        assertEquals(1, actions.count { it.name == "WEIGHING" })
        assertTrue(actions.any { it.label == "Pesada" })
        assertFalse(actions.any { it.label == "Cosecha" })
        assertFalse(actions.any { it.label == "Entrega" })
    }
}

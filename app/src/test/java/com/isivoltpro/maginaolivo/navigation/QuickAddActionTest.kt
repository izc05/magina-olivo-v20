package com.isivoltpro.maginaolivo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuickAddActionTest {
    @Test
    fun collectionUsesOneCanonicalWeighingAction() {
        val collectionActions = QuickAddAction.entries.filter {
            it.name in setOf("HARVEST", "DELIVERY", "WEIGHING")
        }

        assertEquals(listOf("WEIGHING"), collectionActions.map { it.name })
        assertEquals("Nueva pesada", collectionActions.single().title)
        assertFalse(QuickAddAction.entries.any { it.title == "Registrar cosecha" })
        assertFalse(QuickAddAction.entries.any { it.title == "Registrar entrega" })
    }
}

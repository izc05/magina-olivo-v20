package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** #521: a broad catalogue category never becomes a specific, priced equipment type. */
class MachineEquipmentTypeTest {
    @Test
    fun onlyUnambiguousCategoriesGetASpecificType() {
        assertEquals(EquipmentType.TRACTOR, MachineCategory.TRACTOR.toEquipment())
        assertEquals(EquipmentType.TRAILER, MachineCategory.TRAILER.toEquipment())
        MachineCategory.entries.filter { it != MachineCategory.TRACTOR && it != MachineCategory.TRAILER }.forEach {
            assertEquals("$it", EquipmentType.OTHER, it.toEquipment())
        }
    }
}

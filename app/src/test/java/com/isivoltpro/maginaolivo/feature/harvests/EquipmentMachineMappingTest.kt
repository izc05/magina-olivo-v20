package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentMachineMappingTest {
    @Test
    fun onlyUnambiguousMachineCategoriesBecomeSpecificRecollectionTypes() {
        assertEquals(EquipmentType.TRACTOR, MachineCategory.TRACTOR.toEquipment())
        assertEquals(EquipmentType.TRAILER, MachineCategory.TRAILER.toEquipment())

        assertEquals(EquipmentType.OTHER, MachineCategory.HARVEST.toEquipment())
        assertEquals(EquipmentType.OTHER, MachineCategory.TOOL.toEquipment())
        assertEquals(EquipmentType.OTHER, MachineCategory.OTHER.toEquipment())
    }
}

package com.isivoltpro.maginaolivo.domain.equipment

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquipmentTest {
    private val harvest = UUID.randomUUID()

    @Test
    fun twoShakersACombAndATractorReadAsOneDay() {
        val lines = listOf(
            line(EquipmentType.SHAKER, 2), line(EquipmentType.COMB, 1), line(EquipmentType.TRACTOR, 1),
            line(EquipmentType.OTHER, 1, "Paraguas"),
        )
        val summary = EquipmentSummary.of(lines)
        assertEquals("1 tractor · 2 vibradoras · 1 peine eléctrico · 1 paraguas", summary.label())
        assertEquals(summary, EquipmentSummary.of(lines.reversed()))
        // Two Jornadas add up per kind: equipment-days.
        assertEquals("2 tractores · 5 vibradoras", EquipmentSummary.of(listOf(line(EquipmentType.TRACTOR, 1), line(EquipmentType.TRACTOR, 1), line(EquipmentType.SHAKER, 2), line(EquipmentType.SHAKER, 3))).label())
    }

    @Test
    fun rulesKeepOneLinePerKindAndOneMachinePerRegisteredLine() {
        assertNull(EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2), EquipmentDraftLine(EquipmentType.TRACTOR, 1))))
        assertEquals("duplicate", EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.SHAKER, 2), EquipmentDraftLine(EquipmentType.SHAKER, 1)))!!.code)
        assertEquals("out_of_range", EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.SHAKER, 0)))!!.code)
        assertEquals("required", EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.OTHER, 1)))!!.code)
        assertEquals("one_machine", EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 2, machineId = UUID.randomUUID())))!!.code)
        // A registered tractor and "1 tractor" by type are different lines.
        assertNull(EquipmentRules.validate(listOf(EquipmentDraftLine(EquipmentType.TRACTOR, 1, machineId = UUID.randomUUID()), EquipmentDraftLine(EquipmentType.TRACTOR, 1))))
    }

    private fun line(type: EquipmentType, quantity: Int, label: String? = null) =
        EquipmentLine(UUID.randomUUID(), harvest, type, label, quantity, null, 1)
}

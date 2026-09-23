package com.isivoltpro.maginaolivo.domain.machinery

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MachineRulesTest {
    private val tractor = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val atomizer = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    @Test
    fun aNameIsAllAMachineNeeds() {
        assertNull(MachineRules.validate(MachineDraft("Tractor")))
        assertEquals(MachineProblem("name", "blank"), MachineRules.validate(MachineDraft("  ")))
        assertEquals(MachineProblem("currentHours", "negative"), MachineRules.validate(MachineDraft("Tractor", currentHours = -1.0)))
    }

    @Test
    fun machinesOnAnActivityAreOptionalAndHoursAreOptionalToo() {
        assertNull(MachineRules.validateUses(emptyList()))
        assertNull(MachineRules.validateUses(listOf(MachineUseInput(tractor))))
        assertNull(MachineRules.validateUses(listOf(MachineUseInput(tractor, usageHours = 3.5))))
        assertNull(MachineRules.validateUses(listOf(MachineUseInput(tractor, startHours = 1200.0, endHours = 1203.5, usageHours = 3.5))))
    }

    @Test
    fun hourMeterReadingsMustMakeSense() {
        assertEquals(
            MachineProblem("machines", "end_before_start"),
            MachineRules.validateUses(listOf(MachineUseInput(tractor, startHours = 1200.0, endHours = 1100.0))),
        )
        assertEquals(
            MachineProblem("machines", "hours_mismatch"),
            MachineRules.validateUses(listOf(MachineUseInput(tractor, startHours = 1200.0, endHours = 1203.5, usageHours = 2.0))),
        )
        assertEquals(MachineProblem("machines", "negative"), MachineRules.validateUses(listOf(MachineUseInput(tractor, usageHours = -1.0))))
        assertEquals(
            MachineProblem("machines", "duplicate"),
            MachineRules.validateUses(listOf(MachineUseInput(tractor), MachineUseInput(tractor))),
        )
        assertNull(MachineRules.validateUses(listOf(MachineUseInput(tractor), MachineUseInput(atomizer))))
    }

    @Test
    fun hoursUsedPrefersWhatWasTypedThenTheMeterDifference() {
        assertEquals(3.5, ActivityMachine(tractor, "T", MachineCategory.TRACTOR, null, null, 3.5).hoursUsed!!, 0.0)
        assertEquals(2.0, ActivityMachine(tractor, "T", MachineCategory.TRACTOR, 10.0, 12.0, null).hoursUsed!!, 0.0)
        assertNull(ActivityMachine(tractor, "T", MachineCategory.TRACTOR, 10.0, null, null).hoursUsed)
    }
}

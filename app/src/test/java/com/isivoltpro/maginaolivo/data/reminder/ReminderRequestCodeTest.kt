package com.isivoltpro.maginaolivo.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class ReminderRequestCodeTest {
    @Test fun keepsTheUuidHashWhenItIsFree() {
        val id = UUID.fromString("c35a71f8-e218-4d0f-a706-a4771265c4b9")
        assertEquals(id.hashCode(), allocateReminderRequestCode(id, emptySet()))
    }

    @Test fun collidingUuidsReceiveDifferentSlots() {
        // UUID.hashCode folds the four 32-bit words with XOR: these two both hash to zero.
        val first = UUID(0L, 0L)
        val second = UUID(0L, 0x0000000100000001L)
        assertEquals(first.hashCode(), second.hashCode())

        val firstCode = allocateReminderRequestCode(first, emptySet())
        val secondCode = allocateReminderRequestCode(second, setOf(firstCode))
        assertNotEquals(firstCode, secondCode)
        assertEquals(firstCode + 1, secondCode)
    }

    @Test fun probingSkipsEveryOccupiedSlot() {
        val id = UUID(0L, 0L)
        assertEquals(2, allocateReminderRequestCode(id, setOf(0, 1)))
    }

    @Test fun legacyRepairPreservesFirstOwnerAndMovesOnlyCollisions() {
        val first = UUID(0L, 0L)
        val second = UUID(0L, 0x0000000100000001L)
        val independent = UUID.fromString("c35a71f8-e218-4d0f-a706-a4771265c4b9")
        val repaired = repairReminderRequestCodes(
            listOf(
                first to 0,
                second to 0,
                independent to 42,
            ),
        )

        assertEquals(0, repaired[first])
        assertEquals(1, repaired[second])
        assertEquals(42, repaired[independent])
    }
}

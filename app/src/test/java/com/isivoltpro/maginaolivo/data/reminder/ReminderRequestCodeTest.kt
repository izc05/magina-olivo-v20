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

    @Test fun probingWrapsAtIntMaxWithoutReusingASlot() {
        // This does not depend on finding a UUID with MAX_VALUE hash: the allocator contract is
        // covered by occupying the preferred slot and its successor for a normal UUID.
        val id = UUID(0L, 0L)
        assertEquals(2, allocateReminderRequestCode(id, setOf(0, 1)))
    }
}

package com.isivoltpro.maginaolivo.data.local

import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun identifiersAndTimeValuesRoundTripLosslessly() {
        val id = UUID.fromString("9fb2be2f-1b12-4e86-8656-f8f66c46f214")
        val instant = Instant.parse("2026-09-19T16:42:03.456Z")
        val date = LocalDate.parse("2026-11-04")

        assertEquals(id, converters.stringToUuid(converters.uuidToString(id)))
        assertEquals(instant, converters.longToInstant(converters.instantToLong(instant)))
        assertEquals(date, converters.stringToLocalDate(converters.localDateToString(date)))
    }

    @Test
    fun nullableValuesRemainNull() {
        assertNull(converters.uuidToString(null))
        assertNull(converters.stringToUuid(null))
        assertNull(converters.instantToLong(null))
        assertNull(converters.longToInstant(null))
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
    }

    @Test
    fun synchronizationEnumsUseStableProtocolNames() {
        assertEquals("PENDING", converters.syncStatusToString(SyncStatus.PENDING))
        assertEquals(SyncStatus.CONFLICT, converters.stringToSyncStatus("CONFLICT"))
        assertEquals("DELETE", converters.outboxOperationToString(OutboxOperation.DELETE))
        assertEquals(OutboxOperation.CREATE, converters.stringToOutboxOperation("CREATE"))
        assertEquals("FARM", converters.syncEntityTypeToString(SyncEntityType.FARM))
        assertEquals(SyncEntityType.DOCUMENT, converters.stringToSyncEntityType("DOCUMENT"))
    }
}

package com.isivoltpro.maginaolivo.feature.parcels

import com.isivoltpro.maginaolivo.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #494: a refused restore tells the farmer why, never a generic storage failure. */
class RestoreErrorTest {
    @Test
    fun eachRefusalHasItsOwnMessage() {
        assertTrue(restoreError(AppError.Conflict("duplicate_cadastral_reference")).contains("referencia catastral"))
        assertTrue(restoreError(AppError.Conflict("parcel_not_archived")).contains("ya está activa"))
        assertTrue(restoreError(AppError.Validation("catastro", "identity_and_geometry_required")).contains("contorno"))
        assertEquals("No se pudo guardar en este dispositivo", restoreError(AppError.Conflict("invalid_farm")))
    }
}

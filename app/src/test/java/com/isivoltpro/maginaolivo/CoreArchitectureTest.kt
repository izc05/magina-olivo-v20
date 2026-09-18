package com.isivoltpro.maginaolivo

import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.AppEnvironment
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppPreconditions
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.regional.AreaUnit
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.regional.UnitPreferences
import com.isivoltpro.maginaolivo.core.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreArchitectureTest {
    @Test
    fun appResultMapsSuccessWithoutLosingFailureContract() {
        val success: AppResult<Int> = AppResult.Success(21)
        val mapped = success.map { it * 2 }

        assertEquals(AppResult.Success(42), mapped)

        val failure: AppResult<Int> = AppResult.Failure(
            AppError.Validation(field = "name", code = "required"),
        )
        assertTrue(failure.map { it * 2 } is AppResult.Failure)
    }

    @Test
    fun preconditionsNormalizeRequiredText() {
        assertEquals(AppResult.Success("Los Llanos"), AppPreconditions.nonBlank("  Los Llanos  ", "name"))
        assertTrue(AppPreconditions.nonBlank("   ", "name") is AppResult.Failure)
    }

    @Test
    fun clockCanBeDeterministicInTests() {
        val instant = Instant.parse("2026-09-18T08:00:00Z")
        val zone = ZoneId.of("Europe/Madrid")
        val fixedClock = object : AppClock {
            override fun nowInstant(): Instant = instant
            override fun today(zoneId: ZoneId): LocalDate = LocalDate.of(2026, 9, 18)
        }

        assertEquals(instant, fixedClock.nowInstant())
        assertEquals(LocalDate.of(2026, 9, 18), fixedClock.today(zone))
    }

    @Test
    fun idGeneratorCanBeDeterministicInTests() {
        val expected = UUID.fromString("7d52967b-91d4-4e7e-9641-a4df6de4d6f6")
        val generator = object : IdGenerator {
            override fun newId(): UUID = expected
        }

        assertEquals(expected, generator.newId())
    }

    @Test
    fun regionalDefaultsAreExplicitNotDomainIdentity() {
        val context = RegionalContext.spainDefault()
        val units = UnitPreferences()

        assertEquals("ES", context.countryCode)
        assertEquals("EUR", context.currency.currencyCode)
        assertEquals("Europe/Madrid", context.zoneId.id)
        assertEquals(AreaUnit.HECTARE, units.area)
    }

    @Test
    fun compositionRootIsConstructibleForDev() {
        val root = AppCompositionRoot.createDefault("DEV")

        assertEquals(AppEnvironment.DEV, root.environment)
        assertEquals("ES", root.regionalContext.countryCode)
    }
}

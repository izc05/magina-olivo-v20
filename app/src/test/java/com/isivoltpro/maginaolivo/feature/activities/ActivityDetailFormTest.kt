package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the typed block into a detail is a pure function, so the rules that decide
 * what gets written are provable on the JVM, without an emulator.
 */
class ActivityDetailFormTest {
    @Test
    fun `date intervals stay limited to agronomic operations that need them`() {
        assertTrue(ActivityType.PHYTOSANITARY.needsDateInterval())
        assertTrue(ActivityType.FERTILIZATION.needsDateInterval())
        assertTrue(ActivityType.IRRIGATION.needsDateInterval())
        assertFalse(ActivityType.PRUNING.needsDateInterval())
        assertFalse(ActivityType.MAINTENANCE.needsDateInterval())
        assertFalse(ActivityType.HARVEST_DAY.needsDateInterval())
    }

    @Test
    fun `affected surface stays out of unrelated work types`() {
        assertTrue(ActivityType.PHYTOSANITARY.needsAffectedArea())
        assertTrue(ActivityType.FERTILIZATION.needsAffectedArea())
        assertTrue(ActivityType.IRRIGATION.needsAffectedArea())
        assertFalse(ActivityType.PRUNING.needsAffectedArea())
        assertFalse(ActivityType.MAINTENANCE.needsAffectedArea())
        assertFalse(ActivityType.OBSERVATION.needsAffectedArea())
    }

    @Test
    fun `observation and other carry no typed detail`() {
        assertFalse(ActivityType.OBSERVATION.hasTypedDetail())
        assertFalse(ActivityType.OTHER.hasTypedDetail())
        assertNull(buildActivityDetail(ActivityType.OBSERVATION, mapOf("workerCount" to "4")))
        assertNull(buildActivityDetail(ActivityType.OTHER, mapOf("volumeM3" to "10")))
    }

    @Test
    fun `an untouched block writes no detail row`() {
        assertNull(buildActivityDetail(ActivityType.PRUNING, emptyMap()))
        assertNull(buildActivityDetail(ActivityType.IRRIGATION, mapOf(ActivityDetailFields.SECTOR_TEXT to "   ")))
    }

    @Test
    fun `a partially filled block is still kept`() {
        val detail = buildActivityDetail(ActivityType.PRUNING, mapOf(ActivityDetailFields.HOURS to "7,5"))
        assertEquals(ActivityDetail.Pruning(hours = 7.5), detail)
    }

    @Test
    fun `the detail always belongs to the type that was showing`() {
        val fields = mapOf(
            ActivityDetailFields.PRODUCT_NAME to "NPK",
            ActivityDetailFields.VOLUME_M3 to "240",
            ActivityDetailFields.WORKER_COUNT to "4",
        )
        assertTrue(buildActivityDetail(ActivityType.FERTILIZATION, fields) is ActivityDetail.Fertilization)
        assertTrue(buildActivityDetail(ActivityType.IRRIGATION, fields) is ActivityDetail.Irrigation)
        assertTrue(buildActivityDetail(ActivityType.PRUNING, fields) is ActivityDetail.Pruning)
    }

    @Test
    fun `a comma is a decimal separator, as the Spanish keyboard offers it`() {
        val detail = buildActivityDetail(
            ActivityType.IRRIGATION,
            mapOf(ActivityDetailFields.VOLUME_M3 to "12,5"),
        ) as ActivityDetail.Irrigation
        assertEquals(12.5, detail.volumeM3!!, 0.0001)
    }

    @Test
    fun `the tariff snapshot only exists once a basis is chosen`() {
        // A price with no basis says nothing, so the snapshot is simply not created —
        // the irrigation itself is still saved, because its own fields were filled in.
        val withoutBasis = buildActivityDetail(
            ActivityType.IRRIGATION,
            mapOf(
                ActivityDetailFields.VOLUME_M3 to "240",
                ActivityDetailFields.UNIT_PRICE to "0,12",
            ),
        ) as ActivityDetail.Irrigation
        assertNull(withoutBasis.price)
        assertEquals(240.0, withoutBasis.volumeM3!!, 0.0001)

        // With nothing but a price and no basis there is no irrigation to save at all.
        assertNull(
            buildActivityDetail(
                ActivityType.IRRIGATION,
                mapOf(ActivityDetailFields.UNIT_PRICE to "0,12"),
            ),
        )

        val withBasis = buildActivityDetail(
            ActivityType.IRRIGATION,
            mapOf(
                ActivityDetailFields.PRICE_BASIS to IrrigationPricingBasis.PER_M3.name,
                ActivityDetailFields.UNIT_PRICE to "0,12",
                ActivityDetailFields.PRICED_QUANTITY to "240",
                ActivityDetailFields.PRICE_DATE to "2026-03-01",
            ),
        ) as ActivityDetail.Irrigation
        val price = withBasis.price!!
        assertEquals(IrrigationPricingBasis.PER_M3, price.basis)
        assertEquals(12L, price.unitPriceMinor)
        assertEquals(LocalDate.parse("2026-03-01"), price.priceDate)
        // The estimate is derived, never a second number to keep in step by hand.
        assertEquals(2880L, price.estimatedAmountMinor)
        assertNull("the expense link belongs to the expense phase", price.linkedExpenseId)
    }

    @Test
    fun `an incident always records its state`() {
        val detail = buildActivityDetail(
            ActivityType.INCIDENT,
            mapOf(ActivityDetailFields.SEVERITY to IncidentSeverity.HIGH.name),
        ) as ActivityDetail.Incident
        assertEquals(IncidentSeverity.HIGH, detail.severity)
        assertEquals(IncidentState.OPEN, detail.state)
    }

    @Test
    fun `an unreadable value is an error next to its field, never dropped`() {
        // #473: «cuatro» is not «no data»; the form refuses to save until it is corrected.
        val errors = detailFieldErrors(
            ActivityType.PRUNING,
            mapOf(
                ActivityDetailFields.WORKER_COUNT to "cuatro",
                ActivityDetailFields.HOURS to "abc",
                ActivityDetailFields.PRUNING_TYPE to "Formación",
            ),
        )
        assertEquals(setOf(ActivityDetailFields.WORKER_COUNT, ActivityDetailFields.HOURS), errors.keys)
        assertEquals("Escribe las horas como 7,5", errors[ActivityDetailFields.HOURS])
    }

    @Test
    fun `readable and empty values raise no error`() {
        assertTrue(detailFieldErrors(ActivityType.PRUNING, mapOf(ActivityDetailFields.HOURS to "7,5")).isEmpty())
        assertTrue(detailFieldErrors(ActivityType.IRRIGATION, mapOf(ActivityDetailFields.VOLUME_M3 to "12,5")).isEmpty())
        // An optional field left empty is still a valid «no data».
        assertTrue(detailFieldErrors(ActivityType.IRRIGATION, mapOf(ActivityDetailFields.VOLUME_M3 to "  ")).isEmpty())
        assertTrue(detailFieldErrors(ActivityType.OBSERVATION, mapOf(ActivityDetailFields.HOURS to "abc")).isEmpty())
    }

    @Test
    fun `every typed number and the tariff date are checked`() {
        assertEquals(
            setOf(ActivityDetailFields.VOLUME_M3, ActivityDetailFields.DURATION_MINUTES, ActivityDetailFields.UNIT_PRICE,
                ActivityDetailFields.PRICED_QUANTITY, ActivityDetailFields.PRICE_DATE),
            detailFieldErrors(
                ActivityType.IRRIGATION,
                mapOf(
                    // A basis is chosen, so only the unreadable values are reported here.
                    ActivityDetailFields.PRICE_BASIS to IrrigationPricingBasis.PER_M3.name,
                    ActivityDetailFields.VOLUME_M3 to "doce",
                    ActivityDetailFields.DURATION_MINUTES to "hora y media",
                    ActivityDetailFields.UNIT_PRICE to "barato",
                    ActivityDetailFields.PRICED_QUANTITY to "mucho",
                    ActivityDetailFields.PRICE_DATE to "31/02/2026",
                ),
            ).keys,
        )
        assertEquals(
            setOf(ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.TOTAL_QUANTITY),
            detailFieldErrors(
                ActivityType.PHYTOSANITARY,
                mapOf(ActivityDetailFields.DOSE_VALUE to "dos", ActivityDetailFields.TOTAL_QUANTITY to "x"),
            ).keys,
        )
    }

    @Test
    fun `a tariff value without its basis asks for the basis instead of vanishing`() {
        listOf(
            ActivityDetailFields.UNIT_PRICE to "0,12",
            ActivityDetailFields.PRICED_QUANTITY to "240",
            ActivityDetailFields.PRICE_DATE to "2026-03-01",
        ).forEach { written ->
            val errors = detailFieldErrors(ActivityType.IRRIGATION, mapOf(written, ActivityDetailFields.VOLUME_M3 to "12"))
            assertEquals(mapOf(ActivityDetailFields.PRICE_BASIS to "Elige cómo se aplica esta tarifa."), errors)
        }
        // With its basis the snapshot is kept; with every tariff field empty there is nothing to ask.
        assertTrue(
            detailFieldErrors(
                ActivityType.IRRIGATION,
                mapOf(ActivityDetailFields.PRICE_BASIS to IrrigationPricingBasis.PER_M3.name, ActivityDetailFields.UNIT_PRICE to "0,12"),
            ).isEmpty(),
        )
        assertTrue(detailFieldErrors(ActivityType.IRRIGATION, mapOf(ActivityDetailFields.UNIT_PRICE to " ")).isEmpty())
    }

    @Test
    fun `a tariff without its own date takes the date of the work, never today`() {
        val detail = buildActivityDetail(
            ActivityType.IRRIGATION,
            mapOf(
                ActivityDetailFields.PRICE_BASIS to IrrigationPricingBasis.PER_M3.name,
                ActivityDetailFields.UNIT_PRICE to "0,12",
            ),
            activityDate = LocalDate.parse("2025-07-10"),
        ) as ActivityDetail.Irrigation
        assertEquals(LocalDate.parse("2025-07-10"), detail.price!!.priceDate)
    }

    @Test
    fun `editing starts from what was already saved`() {
        val original = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%",
            activeSubstance = "Oxicloruro de cobre",
            totalQuantity = 12.0,
            unit = "l",
            doseValue = 2.0,
            doseUnit = "kg/ha",
            reason = "Repilo",
            equipmentText = "Atomizador",
        )
        assertEquals(original, buildActivityDetail(ActivityType.PHYTOSANITARY, original.toFields()))
    }

    @Test
    fun `an irrigation round trips through the form with its tariff`() {
        val original = buildActivityDetail(
            ActivityType.IRRIGATION,
            mapOf(
                ActivityDetailFields.DURATION_MINUTES to "180",
                ActivityDetailFields.VOLUME_M3 to "240",
                ActivityDetailFields.SECTOR_TEXT to "Sector 3",
                ActivityDetailFields.SYSTEM_TEXT to "Goteo",
                ActivityDetailFields.PRICE_BASIS to IrrigationPricingBasis.PER_M3.name,
                ActivityDetailFields.UNIT_PRICE to "0.12",
                ActivityDetailFields.PRICED_QUANTITY to "240",
                ActivityDetailFields.PRICE_DATE to "2026-03-01",
            ),
        )!!
        assertEquals(original, buildActivityDetail(ActivityType.IRRIGATION, original.toFields()))
    }
}

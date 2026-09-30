package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LabourPricingTest {
    private val date = LocalDate.of(2026, 9, 30)
    private val day = UUID.randomUUID()
    private val worker = UUID.randomUUID()

    @Test fun threeIdentifiedFullDaysCost180Euros() {
        val entries = List(3) {
            LabourPricing.capture(entry(), RecollectionRates(fullDayMinor = 6_000), date)
        }
        assertEquals(18_000L, entries.sumOf { LabourPricing.amountMinor(it)!! })
        assertTrue(entries.all { LabourPricing.validateNewRecollection(it) == null })
    }

    @Test fun threeHoursAtTenEurosCost30Euros() {
        val priced = LabourPricing.capture(entry(LabourUnit.HOURS, 180), RecollectionRates(hourlyMinor = 1_000), date)
        assertEquals(3_000L, LabourPricing.amountMinor(priced))
    }

    @Test fun changingUsualRateDoesNotRewriteTheAppliedPriceOrDate() {
        val original = LabourPricing.capture(entry(), RecollectionRates(fullDayMinor = 6_000), date)
        val after = LabourPricing.capture(original, RecollectionRates(fullDayMinor = 6_500), date.plusDays(1))
        assertEquals(original, after)
        assertEquals(6_000L, LabourPricing.amountMinor(after))
        assertEquals(date, after.appliedRate!!.priceDate)
        assertEquals(6_500L, LabourPricing.amountMinor(
            LabourPricing.capture(entry(), RecollectionRates(fullDayMinor = 6_500), date.plusDays(1)),
        ))
    }

    @Test fun explicitPerPersonRateTakesPrecedenceOverUsualRate() {
        val override = entry().copy(appliedRate = LabourRateSnapshot(7_000, "EUR", date, LabourRateBasis.DAY))
        assertEquals(7_000L, LabourPricing.amountMinor(
            LabourPricing.capture(override, RecollectionRates(fullDayMinor = 6_000), date),
        ))
    }

    @Test fun missingPriceRemainsUnknownButExplicitZeroIsKnown() {
        assertNull(LabourPricing.amountMinor(LabourPricing.capture(entry(), RecollectionRates(), date)))
        assertEquals(0L, LabourPricing.amountMinor(
            LabourPricing.capture(entry(), RecollectionRates(fullDayMinor = 0), date),
        ))
    }

    @Test fun historicalHalfDaysAndHourlyFractionsRoundHalfUpPerLine() {
        val half = LabourPricing.capture(entry(LabourUnit.HALF_DAY), RecollectionRates(fullDayMinor = 7_001), date)
        assertEquals(3_501L, LabourPricing.amountMinor(half))
        val hour = LabourPricing.capture(entry(LabourUnit.HOURS, 1), RecollectionRates(hourlyMinor = 90), date)
        assertEquals(2L, LabourPricing.amountMinor(hour))
    }

    @Test fun newRecollectionRequiresIdentityAndOnePersonPerLine() {
        assertEquals("required", LabourPricing.validateNewRecollection(entry().copy(workerId = null))!!.code)
        assertEquals("one_per_person", LabourPricing.validateNewRecollection(entry().copy(quantity = 2))!!.code)
        assertEquals("required", LabourPricing.validateNewRecollection(entry(LabourUnit.HOURS))!!.code)
        assertEquals("too_long", LabourPricing.validateNewRecollection(entry(LabourUnit.HOURS, 1441))!!.code)
    }

    @Test fun oldAnonymousCountsStayReadableWithoutInventingWorkers() {
        val anonymous = entry().copy(workerId = null, workerName = null, quantity = 5)
        assertNull(LabourRules.validate(anonymous.quantity, anonymous.unit, anonymous.minutes))
        assertEquals(5, LabourByWorker.unnamed(listOf(anonymous)).fullDays)
        assertTrue(LabourByWorker.of(listOf(anonymous)).isEmpty())
        assertNull(anonymous.appliedRate)
        assertNull(LabourPricing.amountMinor(anonymous))
        val historical = LabourPricing.capture(anonymous, RecollectionRates(fullDayMinor = 6_000), date)
        assertEquals(30_000L, LabourPricing.amountMinor(historical))
        assertNull(historical.workerId)
    }

    @Test fun negativePricesAndInvalidCurrencyAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { LabourRateSnapshot(-1, "EUR", date, LabourRateBasis.DAY) }
        assertThrows(IllegalArgumentException::class.java) { LabourRateSnapshot(100, "not-a-currency", date, LabourRateBasis.DAY) }
    }

    @Test fun currencyIsHistoricalAndNotForcedToEuros() {
        val priced = LabourPricing.capture(entry(), RecollectionRates(fullDayMinor = 6_000, currency = "USD"), date)
        assertEquals("USD", priced.appliedRate!!.currency)
        assertEquals("USD", LabourPricing.capture(priced, RecollectionRates(currency = "EUR"), date).appliedRate!!.currency)
    }

    @Test fun arithmeticDoesNotOverflowBeforeRounding() {
        val half = entry(LabourUnit.HALF_DAY).copy(appliedRate = LabourRateSnapshot(Long.MAX_VALUE, "EUR", date, LabourRateBasis.DAY))
        assertEquals(4_611_686_018_427_387_904L, LabourPricing.amountMinor(half))
        assertThrows(ArithmeticException::class.java) { LabourPricing.amountMinor(half.copy(quantity = 3)) }
    }

    @Test fun changingDayToHoursRequiresAnExplicitHourlyAgreement() {
        val rates = RecollectionRates(fullDayMinor = 6_000, hourlyMinor = 1_000)
        val daily = LabourPricing.capture(entry(), rates, date)
        assertEquals(LabourRateBasis.DAY, daily.appliedRate!!.basis)
        val changed = daily.copy(unit = LabourUnit.HOURS, minutes = 180)
        assertThrows(IllegalArgumentException::class.java) { LabourPricing.amountMinor(changed) }
        assertThrows(IllegalArgumentException::class.java) { LabourPricing.capture(changed, rates, date) }
        val newlyAgreed = LabourPricing.capture(changed.copy(appliedRate = null), rates, date)
        assertEquals(LabourRateBasis.HOUR, newlyAgreed.appliedRate!!.basis)
        assertEquals(3_000L, LabourPricing.amountMinor(newlyAgreed))
        assertEquals(6_000L, LabourPricing.amountMinor(daily))
    }

    @Test fun changingHoursToDayCannotReuseAnHourlyPrice() {
        val rates = RecollectionRates(fullDayMinor = 6_000, hourlyMinor = 1_000)
        val hourly = LabourPricing.capture(entry(LabourUnit.HOURS, 180), rates, date)
        val changed = hourly.copy(unit = LabourUnit.FULL_DAY, minutes = null)
        assertThrows(IllegalArgumentException::class.java) { LabourPricing.amountMinor(changed) }
        assertThrows(IllegalArgumentException::class.java) { LabourPricing.capture(changed, rates, date) }
        assertEquals(6_000L, LabourPricing.amountMinor(
            LabourPricing.capture(changed.copy(appliedRate = null), rates, date),
        ))
        val half = LabourPricing.capture(entry(), rates, date).copy(unit = LabourUnit.HALF_DAY)
        assertEquals(3_000L, LabourPricing.amountMinor(LabourPricing.capture(half, rates.copy(fullDayMinor = 6_500), date.plusDays(1))))
    }

    private fun entry(unit: LabourUnit = LabourUnit.FULL_DAY, minutes: Int? = null) =
        LabourEntry(UUID.randomUUID(), day, worker, "Juan García López", 1, unit, minutes, 1)
}

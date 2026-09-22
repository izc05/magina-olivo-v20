package com.isivoltpro.maginaolivo.domain.activity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Typed agronomic detail of an Activity.
 *
 * An Activity keeps one common header — date, description, notes, targeted Parcels — and
 * at most one typed detail that matches its type. The detail is an aggregate child, never
 * a second Activity: it is written in the same transaction as its header, shares the
 * Activity version and queues no synchronization intent of its own
 * (`RC1-NORMATIVE-ADDENDUM` D5 and D10).
 *
 * Types that the contract gives no structured fields — `OBSERVATION` and `OTHER` — carry
 * no detail at all; the common header already says everything they record.
 */
sealed interface ActivityDetail {
    /** The Activity type this detail belongs to. A detail never travels to another type. */
    val type: ActivityType

    data class Pruning(
        val pruningType: String? = null,
        val workerCount: Int? = null,
        val hours: Double? = null,
        val residueManagement: String? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.PRUNING
    }

    /**
     * `productName` is the persisted historical text. RC1 has no Products module, so a
     * fertilisation is always recordable without one and nothing here depends on a table
     * that does not exist (`RC1-NORMATIVE-ADDENDUM` D8).
     */
    data class Fertilization(
        val productName: String? = null,
        val totalQuantity: Double? = null,
        val unit: String? = null,
        val doseValue: Double? = null,
        val doseUnit: String? = null,
        val applicationMethod: String? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.FERTILIZATION
    }

    data class Phytosanitary(
        val productName: String? = null,
        val activeSubstance: String? = null,
        val totalQuantity: Double? = null,
        val unit: String? = null,
        val doseValue: Double? = null,
        val doseUnit: String? = null,
        val reason: String? = null,
        val equipmentText: String? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.PHYTOSANITARY
    }

    data class SoilWork(
        val workType: String? = null,
        val method: String? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.SOIL_WORK
    }

    /**
     * `price` is an optional historical tariff snapshot, never a financial total: the
     * Expense ledger stays the only authoritative source of money
     * (`RC1.2-PRODUCT-LOCK` §8, `RC1-NORMATIVE-ADDENDUM` D2).
     */
    data class Irrigation(
        val durationMinutes: Int? = null,
        val volumeM3: Double? = null,
        val sectorText: String? = null,
        val systemText: String? = null,
        val price: IrrigationPrice? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.IRRIGATION
    }

    data class Maintenance(
        val maintenanceType: String? = null,
        val assetText: String? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.MAINTENANCE
    }

    data class Incident(
        val category: String? = null,
        val severity: IncidentSeverity? = null,
        val state: IncidentState = IncidentState.OPEN,
        val actionTaken: String? = null,
        val resolvedAt: Instant? = null,
    ) : ActivityDetail {
        override val type: ActivityType get() = ActivityType.INCIDENT
    }
}

/** The only typed-detail enum the contract spells out. */
enum class IncidentSeverity { LOW, MEDIUM, HIGH, CRITICAL }

enum class IncidentState { OPEN, MONITORING, RESOLVED }

enum class IrrigationPricingBasis { PER_M3, PER_HOUR, PER_EVENT, PER_HECTARE, INVOICE_TOTAL, OTHER }

/**
 * Historical irrigation tariff snapshot.
 *
 * Changing today's tariff never rewrites what past irrigations cost, which is why the
 * basis, the price and the date are stored together with the Activity that used them.
 * `estimatedAmountMinor` is an estimate for the farmer's own reading; it is never summed
 * into a financial report and never competes with an Expense. `linkedExpenseId` is
 * reserved for the Expense phase and stays null until then.
 */
data class IrrigationPrice(
    val basis: IrrigationPricingBasis,
    val priceDate: LocalDate,
    val unitPriceMinor: Long? = null,
    val quantity: Double? = null,
    val estimatedAmountMinor: Long? = null,
    val currency: String = "EUR",
    val linkedExpenseId: UUID? = null,
    val notes: String? = null,
)

package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * The editor keeps the typed block as plain text keyed by field name.
 *
 * One flat map, not seven sets of state: the form shows only the block that belongs to
 * the chosen type, and switching type has to leave the rest of the screen alone. Turning
 * that map into a typed detail is a pure function, which is what makes the rule "the
 * detail always matches the type" testable without a device.
 */
object ActivityDetailFields {
    const val PRUNING_TYPE = "pruningType"
    const val WORKER_COUNT = "workerCount"
    const val HOURS = "hours"
    const val RESIDUE_MANAGEMENT = "residueManagement"
    const val PRODUCT_NAME = "productName"
    const val ACTIVE_SUBSTANCE = "activeSubstance"
    const val TOTAL_QUANTITY = "totalQuantity"
    const val UNIT = "unit"
    const val DOSE_VALUE = "doseValue"
    const val DOSE_UNIT = "doseUnit"
    const val APPLICATION_METHOD = "applicationMethod"
    const val REASON = "reason"
    const val EQUIPMENT_TEXT = "equipmentText"
    const val WORK_TYPE = "workType"
    const val METHOD = "method"
    const val DURATION_MINUTES = "durationMinutes"
    const val VOLUME_M3 = "volumeM3"
    const val SECTOR_TEXT = "sectorText"
    const val SYSTEM_TEXT = "systemText"
    const val PRICE_BASIS = "priceBasis"
    const val UNIT_PRICE = "unitPrice"
    const val PRICED_QUANTITY = "pricedQuantity"
    const val PRICE_DATE = "priceDate"
    const val MAINTENANCE_TYPE = "maintenanceType"
    const val ASSET_TEXT = "assetText"
    const val CATEGORY = "category"
    const val SEVERITY = "severity"
    const val INCIDENT_STATE = "incidentState"
    const val ACTION_TAKEN = "actionTaken"
}

/** Types that carry no structured fields: their common header already says everything. */
fun ActivityType.hasTypedDetail(): Boolean =
    this != ActivityType.OBSERVATION && this != ActivityType.OTHER && this != ActivityType.HARVEST_DAY

/**
 * Builds the typed detail for [type] from what the form holds.
 *
 * Returns null when the type has no detail table or when the block was left completely
 * empty, so an untouched form never writes a row of nulls. Anything the farmer did type
 * is kept, even partially: a pruning with only the hours filled in is still a pruning.
 */
fun buildActivityDetail(type: ActivityType, fields: Map<String, String>): ActivityDetail? {
    if (!type.hasTypedDetail()) return null
    val detail = when (type) {
        ActivityType.PRUNING -> ActivityDetail.Pruning(
            pruningType = fields.text(ActivityDetailFields.PRUNING_TYPE),
            workerCount = fields.int(ActivityDetailFields.WORKER_COUNT),
            hours = fields.decimal(ActivityDetailFields.HOURS),
            residueManagement = fields.text(ActivityDetailFields.RESIDUE_MANAGEMENT),
        )
        ActivityType.FERTILIZATION -> ActivityDetail.Fertilization(
            productName = fields.text(ActivityDetailFields.PRODUCT_NAME),
            totalQuantity = fields.decimal(ActivityDetailFields.TOTAL_QUANTITY),
            unit = fields.text(ActivityDetailFields.UNIT),
            doseValue = fields.decimal(ActivityDetailFields.DOSE_VALUE),
            doseUnit = fields.text(ActivityDetailFields.DOSE_UNIT),
            applicationMethod = fields.text(ActivityDetailFields.APPLICATION_METHOD),
        )
        ActivityType.PHYTOSANITARY -> ActivityDetail.Phytosanitary(
            productName = fields.text(ActivityDetailFields.PRODUCT_NAME),
            activeSubstance = fields.text(ActivityDetailFields.ACTIVE_SUBSTANCE),
            totalQuantity = fields.decimal(ActivityDetailFields.TOTAL_QUANTITY),
            unit = fields.text(ActivityDetailFields.UNIT),
            doseValue = fields.decimal(ActivityDetailFields.DOSE_VALUE),
            doseUnit = fields.text(ActivityDetailFields.DOSE_UNIT),
            reason = fields.text(ActivityDetailFields.REASON),
            equipmentText = fields.text(ActivityDetailFields.EQUIPMENT_TEXT),
        )
        ActivityType.SOIL_WORK -> ActivityDetail.SoilWork(
            workType = fields.text(ActivityDetailFields.WORK_TYPE),
            method = fields.text(ActivityDetailFields.METHOD),
        )
        ActivityType.IRRIGATION -> ActivityDetail.Irrigation(
            durationMinutes = fields.int(ActivityDetailFields.DURATION_MINUTES),
            volumeM3 = fields.decimal(ActivityDetailFields.VOLUME_M3),
            sectorText = fields.text(ActivityDetailFields.SECTOR_TEXT),
            systemText = fields.text(ActivityDetailFields.SYSTEM_TEXT),
            price = buildIrrigationPrice(fields),
        )
        ActivityType.MAINTENANCE -> ActivityDetail.Maintenance(
            maintenanceType = fields.text(ActivityDetailFields.MAINTENANCE_TYPE),
            assetText = fields.text(ActivityDetailFields.ASSET_TEXT),
        )
        ActivityType.INCIDENT -> ActivityDetail.Incident(
            category = fields.text(ActivityDetailFields.CATEGORY),
            severity = fields.enumOrNull(ActivityDetailFields.SEVERITY, IncidentSeverity::valueOf),
            state = fields.enumOrNull(ActivityDetailFields.INCIDENT_STATE, IncidentState::valueOf)
                ?: IncidentState.OPEN,
            actionTaken = fields.text(ActivityDetailFields.ACTION_TAKEN),
        )
        ActivityType.OBSERVATION, ActivityType.OTHER, ActivityType.HARVEST_DAY -> null
    }
    return detail?.takeUnless { it.isEmpty() }
}

/** Fills the form from a detail that already exists, so editing starts where it left off. */
fun ActivityDetail?.toFields(): Map<String, String> {
    val fields = mutableMapOf<String, String>()
    when (this) {
        null -> Unit
        is ActivityDetail.Pruning -> {
            fields.put(ActivityDetailFields.PRUNING_TYPE, pruningType)
            fields.put(ActivityDetailFields.WORKER_COUNT, workerCount?.toString())
            fields.put(ActivityDetailFields.HOURS, hours.plain())
            fields.put(ActivityDetailFields.RESIDUE_MANAGEMENT, residueManagement)
        }
        is ActivityDetail.Fertilization -> {
            fields.put(ActivityDetailFields.PRODUCT_NAME, productName)
            fields.put(ActivityDetailFields.TOTAL_QUANTITY, totalQuantity.plain())
            fields.put(ActivityDetailFields.UNIT, unit)
            fields.put(ActivityDetailFields.DOSE_VALUE, doseValue.plain())
            fields.put(ActivityDetailFields.DOSE_UNIT, doseUnit)
            fields.put(ActivityDetailFields.APPLICATION_METHOD, applicationMethod)
        }
        is ActivityDetail.Phytosanitary -> {
            fields.put(ActivityDetailFields.PRODUCT_NAME, productName)
            fields.put(ActivityDetailFields.ACTIVE_SUBSTANCE, activeSubstance)
            fields.put(ActivityDetailFields.TOTAL_QUANTITY, totalQuantity.plain())
            fields.put(ActivityDetailFields.UNIT, unit)
            fields.put(ActivityDetailFields.DOSE_VALUE, doseValue.plain())
            fields.put(ActivityDetailFields.DOSE_UNIT, doseUnit)
            fields.put(ActivityDetailFields.REASON, reason)
            fields.put(ActivityDetailFields.EQUIPMENT_TEXT, equipmentText)
        }
        is ActivityDetail.SoilWork -> {
            fields.put(ActivityDetailFields.WORK_TYPE, workType)
            fields.put(ActivityDetailFields.METHOD, method)
        }
        is ActivityDetail.Irrigation -> {
            fields.put(ActivityDetailFields.DURATION_MINUTES, durationMinutes?.toString())
            fields.put(ActivityDetailFields.VOLUME_M3, volumeM3.plain())
            fields.put(ActivityDetailFields.SECTOR_TEXT, sectorText)
            fields.put(ActivityDetailFields.SYSTEM_TEXT, systemText)
            price?.let { snapshot ->
                fields.put(ActivityDetailFields.PRICE_BASIS, snapshot.basis.name)
                fields.put(ActivityDetailFields.UNIT_PRICE, snapshot.unitPriceMinor.majorUnits())
                fields.put(ActivityDetailFields.PRICED_QUANTITY, snapshot.quantity.plain())
                fields.put(ActivityDetailFields.PRICE_DATE, snapshot.priceDate.toString())
            }
        }
        is ActivityDetail.Maintenance -> {
            fields.put(ActivityDetailFields.MAINTENANCE_TYPE, maintenanceType)
            fields.put(ActivityDetailFields.ASSET_TEXT, assetText)
        }
        is ActivityDetail.Incident -> {
            fields.put(ActivityDetailFields.CATEGORY, category)
            fields.put(ActivityDetailFields.SEVERITY, severity?.name)
            fields.put(ActivityDetailFields.INCIDENT_STATE, state.name)
            fields.put(ActivityDetailFields.ACTION_TAKEN, actionTaken)
        }
    }
    return fields
}

/**
 * The tariff snapshot is optional and only exists once a basis is chosen.
 *
 * `estimatedAmountMinor` is computed from what the farmer entered, never stored as an
 * independent number they have to keep in step: it is an estimate for their own reading
 * and the Expense ledger remains the authoritative cost.
 */
private fun buildIrrigationPrice(fields: Map<String, String>): IrrigationPrice? {
    val basis = fields.enumOrNull(ActivityDetailFields.PRICE_BASIS, IrrigationPricingBasis::valueOf)
        ?: return null
    val unitPrice = fields.decimal(ActivityDetailFields.UNIT_PRICE)
    val quantity = fields.decimal(ActivityDetailFields.PRICED_QUANTITY)
    val priceDate = fields.text(ActivityDetailFields.PRICE_DATE)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()
    val unitPriceMinor = unitPrice?.toMinorUnits()
    val estimated = when {
        basis == IrrigationPricingBasis.INVOICE_TOTAL -> unitPriceMinor
        unitPriceMinor != null && quantity != null ->
            BigDecimal(unitPriceMinor).multiply(BigDecimal(quantity.toString()))
                .setScale(0, RoundingMode.HALF_UP).toLong()
        else -> null
    }
    return IrrigationPrice(
        basis = basis,
        priceDate = priceDate,
        unitPriceMinor = unitPriceMinor,
        quantity = quantity,
        estimatedAmountMinor = estimated,
    )
}

private fun ActivityDetail.isEmpty(): Boolean = when (this) {
    is ActivityDetail.Pruning ->
        pruningType == null && workerCount == null && hours == null && residueManagement == null
    is ActivityDetail.Fertilization ->
        productName == null && totalQuantity == null && unit == null &&
            doseValue == null && doseUnit == null && applicationMethod == null
    is ActivityDetail.Phytosanitary ->
        productName == null && activeSubstance == null && totalQuantity == null && unit == null &&
            doseValue == null && doseUnit == null && reason == null && equipmentText == null
    is ActivityDetail.SoilWork -> workType == null && method == null
    is ActivityDetail.Irrigation ->
        durationMinutes == null && volumeM3 == null && sectorText == null &&
            systemText == null && price == null
    is ActivityDetail.Maintenance -> maintenanceType == null && assetText == null
    // An incident always states something: its state is a real answer even on its own.
    is ActivityDetail.Incident -> false
}

private fun MutableMap<String, String>.put(key: String, value: String?) {
    if (!value.isNullOrBlank()) this[key] = value
}

private fun Map<String, String>.text(key: String): String? = this[key]?.trim()?.ifEmpty { null }

private fun Map<String, String>.int(key: String): Int? = text(key)?.toIntOrNull()

/** Accepts the comma the Spanish keyboard offers for decimals. */
private fun Map<String, String>.decimal(key: String): Double? =
    text(key)?.replace(',', '.')?.toDoubleOrNull()

private fun <T> Map<String, String>.enumOrNull(key: String, parse: (String) -> T): T? =
    text(key)?.let { runCatching { parse(it) }.getOrNull() }

private fun Double?.plain(): String? = this?.let {
    BigDecimal(it.toString()).stripTrailingZeros().toPlainString()
}

private fun Double.toMinorUnits(): Long =
    BigDecimal(this.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

private fun Long?.majorUnits(): String? = this?.let {
    BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString()
}

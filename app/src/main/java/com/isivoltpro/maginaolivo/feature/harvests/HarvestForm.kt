package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocationMode
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestProblem
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRules
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.time.LocalDate
import java.util.UUID

/**
 * What the Harvest form holds while a person is typing. `splitKnown` is the explicit
 * choice between "No conozco el reparto exacto" (the default: only the total) and
 * "Conozco los kilos de cada parcela", where a Parcel left blank stays unknown.
 */
data class HarvestForm(
    val farmId: UUID? = null,
    val date: String = "",
    val total: String = "",
    val parcelIds: List<UUID> = emptyList(),
    val splitKnown: Boolean = false,
    val weights: Map<UUID, String> = emptyMap(),
    val collectionMethod: CollectionMethod? = null,
    val workers: String = "",
    val machinery: String = "",
    val notes: String = "",
)

data class HarvestFormErrors(
    val farm: String? = null,
    val date: String? = null,
    val total: String? = null,
    val parcels: String? = null,
    val workers: String? = null,
) {
    val isEmpty: Boolean get() = listOf(farm, date, total, parcels, workers).all { it == null }
}

/** The kilos the form already places on Parcels, for the live "sin asignar" line. */
data class AllocationPreview(val totalGrams: Long?, val allocatedGrams: Long, val unknownParcels: Int)

internal fun HarvestForm.preview(): AllocationPreview {
    val totalGrams = Weight.parseGrams(total)
    return when {
        parcelIds.size == 1 -> AllocationPreview(totalGrams, totalGrams ?: 0, 0)
        !splitKnown -> AllocationPreview(totalGrams, 0, parcelIds.size)
        else -> {
            val known = parcelIds.mapNotNull { Weight.parseGrams(weights[it]) }
            AllocationPreview(totalGrams, known.sum(), parcelIds.size - known.size)
        }
    }
}

/**
 * Turns the form into a [HarvestDraft], or explains in the farmer's words what is wrong.
 * A single origin Parcel carries the whole total — that is a fact, not a split. With
 * several, nothing is attributed unless the person typed it.
 */
internal fun HarvestForm.toDraft(today: LocalDate): Pair<HarvestDraft?, HarvestFormErrors> {
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val totalGrams = Weight.parseGrams(total)
    val badWeight = splitKnown && parcelIds.size > 1 &&
        parcelIds.any { id -> weights[id].orEmpty().isNotBlank() && Weight.parseGrams(weights[id]) == null }
    val workerCount = workers.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val errors = HarvestFormErrors(
        farm = if (farmId == null) "Elige la finca" else null,
        date = if (parsedDate == null) "Elige una fecha" else null,
        total = when {
            total.isBlank() -> "Escribe los kilos recogidos"
            totalGrams == null -> "Escribe los kilos como 2850 o 2.850,5"
            else -> null
        },
        parcels = if (badWeight) "Revisa los kilos de las parcelas: escribe como 1200 o 1.200,5" else null,
        workers = if (workers.isNotBlank() && workerCount == null) "Escribe un número de personas" else null,
    )
    if (!errors.isEmpty) return null to errors
    val shares = when {
        parcelIds.size == 1 -> listOf(HarvestShareInput(parcelIds.single(), totalGrams))
        splitKnown -> parcelIds.map { HarvestShareInput(it, Weight.parseGrams(weights[it])) }
        else -> parcelIds.map { HarvestShareInput(it, null) }
    }
    val draft = HarvestDraft(
        farmId = farmId!!,
        harvestDate = parsedDate!!,
        totalGrams = totalGrams,
        shares = shares,
        collectionMethod = collectionMethod,
        workerCount = workerCount,
        machineryText = machinery.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
    )
    HarvestRules.validate(draft, today)?.let { problem -> return null to problem.toFormErrors() }
    return draft to errors
}

internal fun HarvestProblem.toFormErrors(): HarvestFormErrors = when (field) {
    "harvestDate" -> HarvestFormErrors(date = harvestProblemMessage(this))
    "totalGrams" -> HarvestFormErrors(total = harvestProblemMessage(this))
    "workerCount" -> HarvestFormErrors(workers = harvestProblemMessage(this))
    "farmId" -> HarvestFormErrors(farm = harvestProblemMessage(this))
    else -> HarvestFormErrors(parcels = harvestProblemMessage(this))
}

internal fun harvestProblemMessage(problem: HarvestProblem): String = when (problem.code) {
    "required" -> "Escribe los kilos recogidos"
    "not_positive" -> if (problem.field == "totalGrams") {
        "Los kilos deben ser más que cero"
    } else {
        "Cada parcela con kilos debe tener más de cero"
    }
    "future" -> "La fecha no puede ser posterior a hoy"
    "before_campaign" -> "La fecha es anterior al inicio de la campaña"
    "empty" -> "Elige al menos una parcela de origen"
    "duplicate" -> "Cada parcela solo puede aparecer una vez"
    "exceeds_total" -> "Las parcelas suman más kilos que el total"
    "does_not_reconcile" ->
        "Los kilos de las parcelas deben sumar el total exacto. Deja en blanco las que no conozcas."
    "nothing_left_unallocated" ->
        "Ya has repartido todo el total: escribe también los kilos de las demás parcelas o quítalas"
    "parcel_not_in_campaign" -> "Esa parcela no forma parte de la campaña"
    "negative" -> "El número de personas no puede ser negativo"
    "cannot_change" -> "Una cosecha no puede cambiar de finca"
    "exact_with_pesadas" -> "Con pesadas enlazadas, los kilos se cuentan en total: no se reparten por parcela"
    "after_pesadas" -> "La jornada no puede ser posterior a sus pesadas"
    else -> "Revisa los datos de la cosecha"
}

internal fun Harvest.toForm(): HarvestForm {
    val exact = shares.filter { it.allocation == HarvestAllocation.EXACT }
    return HarvestForm(
        farmId = farmId,
        date = harvestDate.toString(),
        total = Weight.editable(totalGrams),
        parcelIds = shares.map { it.parcelId },
        splitKnown = shares.size > 1 && exact.isNotEmpty(),
        weights = if (shares.size > 1) exact.associate { it.parcelId to Weight.editable(it.weightGrams) } else emptyMap(),
        collectionMethod = collectionMethod,
        workers = workerCount?.toString().orEmpty(),
        machinery = machineryText.orEmpty(),
        notes = notes.orEmpty(),
    )
}

internal fun CollectionMethod.label(): String = when (this) {
    CollectionMethod.MANUAL -> "A mano o vareo"
    CollectionMethod.TRUNK_SHAKER -> "Vibrador de tronco"
    CollectionMethod.UMBRELLA_SHAKER -> "Vibrador con paraguas"
    CollectionMethod.STRADDLE_HARVESTER -> "Cosechadora cabalgante"
    CollectionMethod.OTHER -> "Otro"
}

internal fun HarvestAllocationMode.label(): String = when (this) {
    HarvestAllocationMode.EXACT -> "Reparto exacto por parcela"
    HarvestAllocationMode.PARTIAL -> "Reparto parcial"
    HarvestAllocationMode.UNALLOCATED -> "Sin reparto por parcela"
}

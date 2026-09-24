package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryProblem
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRules
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.delivery.YieldRules
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.ocr.DeliveryTicketProposal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * What the Delivery form holds while a person types. As for a Harvest, several origin
 * Parcels default to "No conozco el reparto exacto" (a mixed load), and a Parcel left
 * blank in the known split stays unknown.
 */
data class DeliveryForm(
    val farmId: UUID? = null,
    val date: String = "",
    val destinationOrganizationId: UUID? = null,
    val destinationText: String = "",
    val net: String = "",
    val gross: String = "",
    val tare: String = "",
    val deliveryNumber: String = "",
    val ticketNumber: String = "",
    val parcelIds: List<UUID> = emptyList(),
    val splitKnown: Boolean = false,
    val weights: Map<UUID, String> = emptyMap(),
    val notes: String = "",
    /** Phase 19B: the hour on the ticket, "9:30". Optional. */
    val time: String = "",
    /** Phase 19B: the Jornada this Pesada joins, chosen explicitly; never guessed. */
    val harvestId: UUID? = null,
    val newJornada: Boolean = false,
)

/**
 * "Guardar y añadir otra": the next Pesada of the same day keeps the Farm, date, Jornada,
 * cooperative and origin Parcels; its own weighing (kilos, ticket, hour) starts empty.
 */
internal fun DeliveryForm.nextPesada(harvestId: UUID?): DeliveryForm = copy(
    net = "",
    gross = "",
    tare = "",
    deliveryNumber = "",
    ticketNumber = "",
    notes = "",
    time = "",
    weights = emptyMap(),
    splitKnown = false,
    harvestId = harvestId,
    newJornada = false,
)

private val TIME = Regex("""^(\d{1,2})[:.h](\d{2})$""")

/** "9:30", "09.30" or "9h30" → 09:30; anything else is not an hour. */
internal fun parseHour(text: String): LocalTime? {
    val match = TIME.matchEntire(text.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}

data class DeliveryFormErrors(
    val farm: String? = null,
    val date: String? = null,
    val destination: String? = null,
    val net: String? = null,
    val gross: String? = null,
    val parcels: String? = null,
    val time: String? = null,
    val jornada: String? = null,
) {
    val isEmpty: Boolean get() = listOf(farm, date, destination, net, gross, parcels, time, jornada).all { it == null }
}

internal fun DeliveryForm.toDraft(today: LocalDate): Pair<DeliveryDraft?, DeliveryFormErrors> {
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val netGrams = Weight.parseGrams(net)
    val grossGrams = Weight.parseGrams(gross)
    val tareGrams = Weight.parseGrams(tare)
    val badWeight = splitKnown && parcelIds.size > 1 &&
        parcelIds.any { id -> weights[id].orEmpty().isNotBlank() && Weight.parseGrams(weights[id]) == null }
    val errors = DeliveryFormErrors(
        farm = if (farmId == null) "Elige la finca" else null,
        date = if (parsedDate == null) "Elige una fecha" else null,
        destination = if (destinationOrganizationId == null && destinationText.isBlank()) {
            "Elige la cooperativa o almazara, o escribe su nombre"
        } else {
            null
        },
        net = when {
            net.isBlank() -> "Escribe los kilos netos entregados"
            netGrams == null -> "Escribe los kilos como 2850 o 2.850"
            else -> null
        },
        gross = when {
            gross.isNotBlank() && grossGrams == null -> "Revisa el peso bruto"
            tare.isNotBlank() && tareGrams == null -> "Revisa la tara"
            else -> null
        },
        parcels = if (badWeight) "Revisa los kilos de las parcelas: escribe como 1200 o 1.200,5" else null,
        time = if (time.isNotBlank() && parseHour(time) == null) "Escribe la hora como 9:30" else null,
    )
    if (!errors.isEmpty) return null to errors
    val shares = when {
        parcelIds.size == 1 -> listOf(DeliveryShareInput(parcelIds.single(), netGrams))
        splitKnown -> parcelIds.map { DeliveryShareInput(it, Weight.parseGrams(weights[it])) }
        else -> parcelIds.map { DeliveryShareInput(it, null) }
    }
    val draft = DeliveryDraft(
        farmId = farmId!!,
        deliveryDate = parsedDate!!,
        destinationOrganizationId = destinationOrganizationId,
        destinationName = destinationText.trim().ifEmpty { null },
        netGrams = netGrams,
        shares = shares,
        grossGrams = grossGrams,
        tareGrams = tareGrams,
        deliveryNumber = deliveryNumber.trim().ifEmpty { null },
        ticketNumber = ticketNumber.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        harvestId = harvestId.takeUnless { newJornada },
        deliveryTime = parseHour(time),
        newJornada = newJornada,
    )
    DeliveryRules.validate(draft, today)?.let { return null to it.toFormErrors() }
    return draft to errors
}

internal fun DeliveryProblem.toFormErrors(): DeliveryFormErrors = when (field) {
    "deliveryDate" -> DeliveryFormErrors(date = deliveryProblemMessage(this))
    "netGrams" -> DeliveryFormErrors(net = deliveryProblemMessage(this))
    "grossGrams" -> DeliveryFormErrors(gross = deliveryProblemMessage(this))
    "destination" -> DeliveryFormErrors(destination = deliveryProblemMessage(this))
    "farmId" -> DeliveryFormErrors(farm = deliveryProblemMessage(this))
    "harvestId" -> DeliveryFormErrors(jornada = deliveryProblemMessage(this))
    else -> DeliveryFormErrors(parcels = deliveryProblemMessage(this))
}

internal fun deliveryProblemMessage(problem: DeliveryProblem): String = when (problem.code) {
    "required" -> when (problem.field) {
        "destination" -> "Elige la cooperativa o almazara, o escribe su nombre"
        "yield" -> "Escribe al menos un rendimiento"
        else -> "Escribe los kilos netos entregados"
    }
    "not_positive" -> when (problem.field) {
        "netGrams" -> "Los kilos deben ser más que cero"
        "grossGrams" -> "Revisa el peso bruto y la tara"
        else -> "Cada parcela con kilos debe tener más de cero"
    }
    "gross_tare_mismatch" -> "Bruto menos tara no da el neto. Revisa los tres pesos del vale."
    "future" -> "La fecha no puede ser posterior a hoy"
    "before_campaign" -> "La fecha es anterior al inicio de la campaña"
    "before_delivery" -> "El análisis no puede ser anterior a la entrega"
    "empty" -> "Elige al menos una parcela de origen"
    "duplicate" -> "Cada parcela solo puede aparecer una vez"
    "exceeds_total" -> "Las parcelas suman más kilos que la entrega"
    "does_not_reconcile" -> "Los kilos de las parcelas deben sumar el neto exacto. Deja en blanco las que no conozcas."
    "nothing_left_unallocated" ->
        "Ya has repartido todo el neto: escribe también los kilos de las demás parcelas o quítalas"
    "parcel_not_in_campaign" -> "Esa parcela no forma parte de la campaña"
    "not_found" -> when (problem.field) {
        "destination" -> "Esa cooperativa ya no está guardada"
        "harvestId" -> "Esa jornada ya no está en este dispositivo"
        else -> "La finca no está en este dispositivo"
    }
    "cannot_change" -> "Una entrega no puede cambiar de finca"
    "out_of_range" -> "El rendimiento debe estar entre 0 y 100 %"
    "other_campaign" -> "Esa jornada es de otra finca o campaña"
    "before_jornada" -> "La pesada no puede ser anterior a su jornada"
    "exact_split" -> "Esa jornada tiene kilos repartidos por parcela. Quita el reparto exacto para enlazarle pesadas."
    "no_parcels" -> "Esta campaña no tiene parcelas para abrir la jornada"
    "ambiguous" -> "Elige una jornada o crea una nueva, no las dos"
    else -> "Revisa los datos de la entrega"
}

internal fun Delivery.toForm(): DeliveryForm {
    val exact = shares.filter { it.allocation == HarvestAllocation.EXACT }
    return DeliveryForm(
        farmId = farmId,
        date = deliveryDate.toString(),
        destinationOrganizationId = destinationOrganizationId,
        destinationText = if (destinationOrganizationId == null) destinationName else "",
        net = Weight.editable(netGrams),
        gross = Weight.editable(grossGrams),
        tare = Weight.editable(tareGrams),
        deliveryNumber = deliveryNumber.orEmpty(),
        ticketNumber = ticketNumber.orEmpty(),
        parcelIds = shares.map { it.parcelId },
        splitKnown = shares.size > 1 && exact.isNotEmpty(),
        weights = if (shares.size > 1) exact.associate { it.parcelId to Weight.editable(it.weightGrams) } else emptyMap(),
        notes = notes.orEmpty(),
        time = deliveryTime?.toString().orEmpty(),
        harvestId = harvestId,
    )
}

/**
 * A ticket's proposal as a starting form. Only what the ticket said is filled in; the
 * farmer still chooses the Farm, the origin Parcels and confirms every figure.
 */
internal fun DeliveryTicketProposal?.toForm(farmId: UUID?, today: LocalDate): DeliveryForm = DeliveryForm(
    farmId = farmId,
    date = (this?.deliveryDate ?: today).toString(),
    destinationText = this?.organizationName.orEmpty(),
    net = Weight.editable(this?.netGrams),
    gross = Weight.editable(this?.grossGrams),
    tare = Weight.editable(this?.tareGrams),
    ticketNumber = this?.ticketNumber.orEmpty(),
)

data class YieldForm(
    val date: String = "",
    val fat: String = "",
    val industrial: String = "",
    val notes: String = "",
)

data class YieldFormErrors(val date: String? = null, val fat: String? = null, val industrial: String? = null) {
    val isEmpty: Boolean get() = date == null && fat == null && industrial == null
}

internal fun YieldForm.toDraft(today: LocalDate): Pair<YieldDraft?, YieldFormErrors> {
    val parsedDate = date.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val fatValue = Percent.parseHundredths(fat)
    val industrialValue = Percent.parseHundredths(industrial)
    val errors = YieldFormErrors(
        date = if (date.isNotBlank() && parsedDate == null) "Elige una fecha" else null,
        fat = if (fat.isNotBlank() && fatValue == null) "Escribe el rendimiento como 21,5" else null,
        industrial = if (industrial.isNotBlank() && industrialValue == null) "Escribe el rendimiento como 18,2" else null,
    )
    if (!errors.isEmpty) return null to errors
    val draft = YieldDraft(parsedDate, fatValue, industrialValue, notes.trim().ifEmpty { null })
    YieldRules.validate(draft, today)?.let { problem ->
        val message = deliveryProblemMessage(problem)
        return null to if (problem.field == "analysisDate") YieldFormErrors(date = message) else YieldFormErrors(fat = message)
    }
    return draft to errors
}

internal fun YieldAnalysis?.toForm(): YieldForm = YieldForm(
    date = this?.analysisDate?.toString().orEmpty(),
    fat = Percent.editable(this?.fatYieldHundredths),
    industrial = Percent.editable(this?.industrialYieldHundredths),
    notes = this?.notes.orEmpty(),
)

package com.isivoltpro.maginaolivo.domain.equipment

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Phase 19E: the presets of recollection equipment (CR-005 §9), plus any other kind. */
enum class EquipmentType(val singular: String, val plural: String) {
    TRACTOR("tractor", "tractores"),
    SHAKER("vibradora", "vibradoras"),
    COMB("peine eléctrico", "peines eléctricos"),
    TRAILER("remolque", "remolques"),
    BLOWER("sopladora", "sopladoras"),
    OTHER("otra", "otras"),
}

/**
 * One equipment line of a Jornada: a type and a quantity, or one registered Machine
 * (`machineId`, quantity 1) when the farmer wants that machine's history.
 */
data class EquipmentLine(
    val id: UUID,
    val harvestId: UUID,
    val type: EquipmentType,
    val label: String?,
    val quantity: Int,
    val machineId: UUID?,
    val version: Long,
) {
    fun text(): String = when {
        machineId != null -> label ?: type.singular
        type == EquipmentType.OTHER -> "$quantity ${label ?: "otra"}"
        else -> "$quantity ${if (quantity == 1) type.singular else type.plural}"
    }
}

/** What the farmer leaves on the sheet; saved all together, replacing the Jornada's lines. */
data class EquipmentDraftLine(
    val type: EquipmentType,
    val quantity: Int,
    val label: String? = null,
    val machineId: UUID? = null,
)

data class EquipmentProblem(val field: String, val code: String)

object EquipmentRules {
    const val MAX_QUANTITY = 50

    fun validate(lines: List<EquipmentDraftLine>): EquipmentProblem? {
        lines.forEach { line ->
            if (line.quantity !in 1..MAX_QUANTITY) return EquipmentProblem("quantity", "out_of_range")
            if (line.machineId != null && line.quantity != 1) return EquipmentProblem("quantity", "one_machine")
            if (line.machineId == null && line.type == EquipmentType.OTHER && line.label.isNullOrBlank()) {
                return EquipmentProblem("label", "required")
            }
        }
        val keys = lines.map { key(it) }
        if (keys.toSet().size != keys.size) return EquipmentProblem("lines", "duplicate")
        return null
    }

    /** One line per registered machine, per preset type, or per named "other". */
    fun key(line: EquipmentDraftLine): String =
        line.machineId?.let { "machine:$it" }
            ?: if (line.type == EquipmentType.OTHER) "other:${line.label!!.trim().lowercase()}" else "type:${line.type.name}"
}

/** Totals by type: "2 vibradoras · 1 peine eléctrico · 1 tractor". Order-independent. */
data class EquipmentSummary(val byType: Map<EquipmentType, Int>, val others: Map<String, Int>) {
    val isEmpty: Boolean get() = byType.isEmpty() && others.isEmpty()

    fun label(): String = (
        EquipmentType.entries.filter { it != EquipmentType.OTHER }.mapNotNull { type ->
            byType[type]?.let { n -> "$n ${if (n == 1) type.singular else type.plural}" }
        } + others.entries.sortedBy { it.key }.map { (name, n) -> "$n $name" }
        ).joinToString(" · ")

    companion object {
        fun of(lines: List<EquipmentLine>): EquipmentSummary {
            val (other, typed) = lines.partition { it.type == EquipmentType.OTHER }
            return EquipmentSummary(
                byType = typed.groupBy { it.type }.mapValues { (_, group) -> group.sumOf { it.quantity } },
                others = other.groupBy { (it.label ?: "otra").trim().lowercase() }.mapValues { (_, group) -> group.sumOf { it.quantity } },
            )
        }
    }
}

interface EquipmentRepository {
    fun observeForHarvest(harvestId: UUID): Flow<List<EquipmentLine>>

    fun observeForCampaign(campaignId: UUID): Flow<List<EquipmentLine>>

    /** Saves the whole sheet at once: new lines are added, changed ones updated, missing ones removed. */
    suspend fun replaceForHarvest(harvestId: UUID, lines: List<EquipmentDraftLine>): AppResult<Unit>
}

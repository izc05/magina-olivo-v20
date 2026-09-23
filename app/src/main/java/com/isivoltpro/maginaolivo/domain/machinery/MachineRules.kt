package com.isivoltpro.maginaolivo.domain.machinery

import java.util.UUID

/** `DATA-MODEL-RC1.1-ADDENDUM` §5. */
enum class MachineCategory { TRACTOR, ATOMIZER, MOWER, PRUNER, HARVEST, TRAILER, TOOL, OTHER }

/**
 * A machine as a lightweight operational resource (`RC1.1-PRODUCT-LOCK` §8): a name is
 * enough; everything else is optional and can grow later without changing the core.
 */
data class Machine(
    val id: UUID,
    val name: String,
    val category: MachineCategory,
    val make: String? = null,
    val model: String? = null,
    val registrationOrSerial: String? = null,
    val currentHours: Double? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    val version: Long = 1,
)

data class MachineDraft(
    val name: String,
    val category: MachineCategory = MachineCategory.OTHER,
    val make: String? = null,
    val model: String? = null,
    val registrationOrSerial: String? = null,
    val currentHours: Double? = null,
    val notes: String? = null,
)

data class MachineOption(val id: UUID, val name: String, val category: MachineCategory)

/**
 * One machine on one Activity. Every figure is optional: an Activity may say only which
 * machine was used. When hour-meter readings are given they must make sense together.
 */
data class MachineUseInput(
    val machineId: UUID,
    val startHours: Double? = null,
    val endHours: Double? = null,
    val usageHours: Double? = null,
)

data class ActivityMachine(
    val machineId: UUID,
    val name: String,
    val category: MachineCategory,
    val startHours: Double?,
    val endHours: Double?,
    val usageHours: Double?,
    val archived: Boolean = false,
) {
    /** What was typed, or the hour-meter difference when only the readings were given. */
    val hoursUsed: Double? get() = usageHours ?: if (startHours != null && endHours != null) endHours - startHours else null
}

/** An Activity that used a machine, for the machine's detail. */
data class MachineUse(
    val activityId: UUID,
    val activityDate: java.time.LocalDate,
    val description: String,
    val hoursUsed: Double?,
)

data class MachineProblem(val field: String, val code: String)

object MachineRules {
    private const val TOLERANCE = 0.001

    fun validate(draft: MachineDraft): MachineProblem? = when {
        draft.name.isBlank() -> MachineProblem("name", "blank")
        (draft.currentHours ?: 0.0) < 0 -> MachineProblem("currentHours", "negative")
        else -> null
    }

    fun validateUses(uses: List<MachineUseInput>): MachineProblem? {
        if (uses.map { it.machineId }.toSet().size != uses.size) return MachineProblem("machines", "duplicate")
        uses.forEach { use ->
            if (listOfNotNull(use.startHours, use.endHours, use.usageHours).any { it < 0 }) {
                return MachineProblem("machines", "negative")
            }
            if (use.startHours != null && use.endHours != null) {
                if (use.endHours < use.startHours) return MachineProblem("machines", "end_before_start")
                if (use.usageHours != null && kotlin.math.abs(use.endHours - use.startHours - use.usageHours) > TOLERANCE) {
                    return MachineProblem("machines", "hours_mismatch")
                }
            }
        }
        return null
    }
}

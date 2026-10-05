package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import java.util.UUID

/**
 * #449: how far the posted recollection money can be trusted as the whole cost. Derived from the
 * same records the screens list, never stored, and never a second amount: the money stays the
 * posted Expense ledger; this only says what is still unknown.
 */
data class RecollectionCostCompleteness(val reasons: Set<Reason>) {
    val complete: Boolean get() = reasons.isEmpty()

    enum class Reason {
        /** A jornal recorded without a confirmed price. */
        LABOUR_UNPRICED,
        /** A machine use recorded without a confirmed price. */
        EQUIPMENT_UNPRICED,
        /** Priced jornales or machinery whose cost is not yet posted for that day. */
        UNPOSTED_RESOURCE_COST,
        /** A cost still waiting for confirmation that nothing posted stands for. */
        DRAFT_COSTS,
    }

    companion object {
        fun of(labour: List<LabourEntry>, equipment: List<EquipmentLine>, expenses: List<Expense>): RecollectionCostCompleteness {
            val reasons = mutableSetOf<Reason>()
            if (labour.any { it.appliedRate == null }) reasons += Reason.LABOUR_UNPRICED
            if (equipment.any { it.appliedPrice == null }) reasons += Reason.EQUIPMENT_UNPRICED
            val posted = expenses.filter { it.status == ExpenseStatus.POSTED }
            fun postedFor(bucket: RecollectionBucket, day: UUID, currency: String) =
                posted.any { it.harvestId == day && it.currency == currency && RecollectionBucket.of(it) == bucket }
            val unposted = labour.any { entry -> entry.appliedRate?.let { !postedFor(RecollectionBucket.LABOUR, entry.harvestId, it.currency) } ?: false } ||
                equipment.any { line -> line.appliedPrice?.let { !postedFor(RecollectionBucket.EQUIPMENT, line.harvestId, it.currency) } ?: false }
            if (unposted) reasons += Reason.UNPOSTED_RESOURCE_COST
            // A draft calculation kept aside because a hand-typed replacement counts instead (#475),
            // or the other way round, is superseded: its day's cost of that kind is posted.
            val pendingDraft = expenses.any { draft ->
                draft.status == ExpenseStatus.DRAFT && run {
                    val bucket = RecollectionBucket.of(draft)
                    val day = draft.harvestId
                    bucket == RecollectionBucket.OTHER || day == null ||
                        posted.none { it.harvestId == day && RecollectionBucket.of(it) == bucket }
                }
            }
            if (pendingDraft) reasons += Reason.DRAFT_COSTS
            return RecollectionCostCompleteness(reasons)
        }
    }
}

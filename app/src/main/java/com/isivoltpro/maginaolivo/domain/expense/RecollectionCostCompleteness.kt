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
            // The day's cost of a kind is posted when its calculation is posted in the resource's
            // currency, or when an explicit replacement (#475) stands for it, in whatever currency.
            fun postedFor(kind: DayCostKind, day: UUID, currency: String) = posted.any {
                it.harvestId == day && (
                    (it.origin == kind.origin && it.currency == currency) ||
                        (it.origin == ExpenseOrigin.DAY_REPLACEMENT && DayCostKind.of(it.category) == kind)
                    )
            }
            val unposted = labour.any { entry -> entry.appliedRate?.let { !postedFor(DayCostKind.LABOUR, entry.harvestId, it.currency) } ?: false } ||
                equipment.any { line -> line.appliedPrice?.let { !postedFor(DayCostKind.EQUIPMENT, line.harvestId, it.currency) } ?: false }
            if (unposted) reasons += Reason.UNPOSTED_RESOURCE_COST
            if (expenses.any { isPendingDraft(it, posted) }) reasons += Reason.DRAFT_COSTS
            return RecollectionCostCompleteness(reasons)
        }

        /**
         * A draft waiting for confirmation, unless it is one side of the day's explicit pair (#475):
         * a calculation set aside because a replacement of its kind is posted, or a replacement set
         * aside because the calculation of its kind is posted. Any other draft is pending.
         */
        fun isPendingDraft(draft: Expense, posted: List<Expense>): Boolean {
            if (draft.status != ExpenseStatus.DRAFT) return false
            val day = draft.harvestId ?: return true
            val sameDay = posted.filter { it.status == ExpenseStatus.POSTED && it.harvestId == day }
            val calculated = DayCostKind.entries.firstOrNull { it.origin == draft.origin }
            if (calculated != null) {
                return sameDay.none { it.origin == ExpenseOrigin.DAY_REPLACEMENT && DayCostKind.of(it.category) == calculated }
            }
            if (draft.origin == ExpenseOrigin.DAY_REPLACEMENT) {
                val kind = DayCostKind.of(draft.category) ?: return true
                return sameDay.none { it.origin == kind.origin }
            }
            return true
        }
    }
}

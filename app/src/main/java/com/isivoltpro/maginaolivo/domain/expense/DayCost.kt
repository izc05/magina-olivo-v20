package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * CR-010 §8–9: the Farm's usual recollection prices, all optional. A missing price is unknown,
 * never zero: what it would price is left out of the calculation and said so.
 */
data class RecollectionRates(
    val fullDayMinor: Long? = null,
    val hourlyMinor: Long? = null,
    val equipmentDayMinor: Map<EquipmentType, Long> = emptyMap(),
    val currency: String = "EUR",
) {
    val isEmpty: Boolean get() = fullDayMinor == null && hourlyMinor == null && equipmentDayMinor.isEmpty()
}

/**
 * A cost worked out from one day's attendance or equipment. [detail] is the snapshot stored on
 * the posted Expense («5 jornadas × 70,00 €»), so a later price change never rewrites history;
 * [unpriced] names what had no price («6 h sin precio por hora»).
 */
data class CalculatedCost(val amountMinor: Long, val detail: String, val unpriced: String?) {
    /** The text kept on the ledger entry. */
    val note: String get() = listOfNotNull(detail.ifEmpty { null }, unpriced).joinToString(" · ")
}

object DayCostCalculator {
    /** Half a day is half the day price, rounded half up to the cent. */
    fun halfDayMinor(fullDayMinor: Long): Long = (fullDayMinor + 1) / 2

    fun labour(entries: List<LabourEntry>, rates: RecollectionRates): CalculatedCost? {
        val summary = LabourSummary.of(entries)
        if (summary.isEmpty) return null
        val money = { minor: Long -> Money.format(minor, rates.currency) }
        val parts = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var amount = 0L
        if (summary.fullDays > 0) {
            val count = if (summary.fullDays == 1) "1 jornada" else "${summary.fullDays} jornadas"
            rates.fullDayMinor?.let { price ->
                amount += summary.fullDays * price
                parts += "$count × ${money(price)}"
            } ?: missing.add(count)
        }
        if (summary.halfDays > 0) {
            val count = if (summary.halfDays == 1) "1 media" else "${summary.halfDays} medias"
            rates.fullDayMinor?.let { price ->
                val half = halfDayMinor(price)
                amount += summary.halfDays * half
                parts += "$count × ${money(half)}"
            } ?: missing.add(count)
        }
        if (summary.minutes > 0) {
            val hours = LabourSummary.hours(summary.minutes)
            rates.hourlyMinor?.let { price ->
                amount += (summary.minutes * price + 30) / 60
                parts += "$hours × ${money(price)}/h"
            } ?: missing.add(hours)
        }
        return CalculatedCost(amount, parts.joinToString(" + "), missing.takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ")} sin precio" })
    }

    fun equipment(lines: List<EquipmentLine>): CalculatedCost? {
        if (lines.isEmpty()) return null
        val parts = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var amount = 0L
        lines.sortedBy { it.type }.forEach { line ->
            val quantity = line.quantity
            val name = "$quantity ${if (quantity == 1) line.type.singular else line.type.plural}"
            line.appliedPrice?.let { snapshot ->
                amount = Math.addExact(amount, Math.multiplyExact(quantity.toLong(), snapshot.unitPriceMinor))
                parts += "$name × ${Money.format(snapshot.unitPriceMinor, snapshot.currency)}"
            } ?: missing.add(name)
        }
        return CalculatedCost(amount, parts.joinToString(" + "), missing.takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ")} sin precio" })
    }
}

/** CR-010 A3: which calculated cost of a day. */
enum class DayCostKind(val origin: ExpenseOrigin, val category: ExpenseCategory, val concept: String) {
    LABOUR(ExpenseOrigin.DAY_LABOUR, ExpenseCategory.LABOR, "Jornales (calculado)"),
    EQUIPMENT(ExpenseOrigin.DAY_EQUIPMENT, ExpenseCategory.MACHINERY, "Maquinaria (calculada)"),
    ;

    /**
     * A3: whether a hand-typed Expense of the day stands for this calculated cost (and so
     * replaces it) rather than adding to it, as oil for the machines does.
     */
    fun isReplacedBy(category: ExpenseCategory, concept: String): Boolean =
        category == this.category &&
            JornadaExpenseKind.entries.none { it.additive && it.category == category && it.names(concept) }
}

/**
 * A3: posted, hand-typed costs of the day's Farm and date that are linked to **no** day, while
 * the day has a calculated cost they could stand for. They are ambiguous: the app shows them with
 * a warning and the farmer links one to the day or keeps it apart; it never merges or drops them.
 */
object UnlinkedDayCosts {
    fun of(dayId: UUID, farmId: UUID?, date: LocalDate, expenses: List<Expense>): List<Expense> {
        if (farmId == null) return emptyList()
        val calculated = expenses.filter { it.harvestId == dayId }
            .mapNotNull { expense -> DayCostKind.entries.firstOrNull { it.origin == expense.origin } }
            .toSet()
        if (calculated.isEmpty()) return emptyList()
        return expenses.filter { expense ->
            expense.harvestId == null && expense.farmId == farmId && expense.expenseDate == date &&
                expense.status == ExpenseStatus.POSTED && expense.origin in LINKABLE &&
                calculated.any { it.isReplacedBy(expense.category, expense.concept) }
        }
    }

    /**
     * Only costs the farmer owns can be linked to a day: hand-typed ones and reviewed documents.
     * An Activity's cost belongs to its Activity (its next edit would drop the link) and a
     * calculated one to its day.
     */
    val LINKABLE = setOf(ExpenseOrigin.MANUAL, ExpenseOrigin.DOCUMENT_OCR)
}

interface DayCostRepository {
    fun observeRates(farmId: UUID): Flow<RecollectionRates>

    /** Saving usual prices does not change confirmed historical equipment or labour snapshots. */
    suspend fun saveRates(farmId: UUID, rates: RecollectionRates): AppResult<Unit>

    /**
     * A3 collision: a hand-typed cost of the same kind on the same day stands until the farmer
     * picks the calculation; then that hand-typed cost goes back to draft (kept, never summed).
     */
    suspend fun preferCalculated(harvestId: UUID, kind: DayCostKind): AppResult<Unit>

    /**
     * A3: the farmer says an unlinked hand-typed cost of the same Farm and date belongs to this
     * day. It is linked (nothing else changes) and the collision rule then applies to the day.
     */
    suspend fun linkToDay(expenseId: UUID, harvestId: UUID): AppResult<Unit>
}

package com.isivoltpro.maginaolivo.domain.expense

/**
 * Phase 19F (CR-005 §10): the quick recollection-cost buttons. Each is only a shortcut to an
 * ordinary Expense in the existing ledger (category + default concept); no new money type.
 */
enum class JornadaExpenseKind(val label: String, val category: ExpenseCategory) {
    LABOUR("Jornales/servicio", ExpenseCategory.LABOR),
    DIESEL("Gasoil", ExpenseCategory.FUEL),
    PETROL("Gasolina", ExpenseCategory.FUEL),
    LUBRICANT("Aceite/lubricante", ExpenseCategory.MACHINERY),
    RENTAL("Maquinaria/alquiler", ExpenseCategory.MACHINERY),
    TRANSPORT("Transporte", ExpenseCategory.TRANSPORT),
    OTHER("Otro", ExpenseCategory.HARVEST),
}

/**
 * The cost of one Jornada, read from the ledger itself: the posted Expenses linked to it.
 * Drafts are listed and never summed; nothing is copied, so it always equals the ledger.
 */
data class JornadaCost(val summary: ExpenseSummary, val expenses: List<Expense>) {
    val postedMinor: Long get() = summary.totalMinor
    val draftCount: Int get() = summary.draftCount

    companion object {
        fun of(expenses: List<Expense>): JornadaCost = JornadaCost(ExpenseSummary.of(expenses), expenses)
    }
}

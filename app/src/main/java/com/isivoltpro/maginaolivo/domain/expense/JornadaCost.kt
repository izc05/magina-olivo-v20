package com.isivoltpro.maginaolivo.domain.expense

/**
 * Phase 19F (CR-005 §10): the quick recollection-cost buttons. Each is only a shortcut to an
 * ordinary Expense in the existing ledger (category + default concept); no new money type.
 *
 * CR-010 A3: an [additive] kind is spent on top of the day's calculated cost (oil for the
 * machines is not the machines' day), so it never replaces it. Its concept always starts with
 * its [label]; a concept typed in Gastos is recognised by its [keywords] («Aceite hidráulico»).
 */
enum class JornadaExpenseKind(
    val label: String,
    val category: ExpenseCategory,
    val additive: Boolean = false,
    val keywords: List<String> = emptyList(),
) {
    LABOUR("Jornales/servicio", ExpenseCategory.LABOR),
    DIESEL("Gasoil", ExpenseCategory.FUEL),
    PETROL("Gasolina", ExpenseCategory.FUEL),
    LUBRICANT("Aceite/lubricante", ExpenseCategory.MACHINERY, additive = true, keywords = listOf("aceite", "lubricante", "grasa")),
    RENTAL("Maquinaria/alquiler", ExpenseCategory.MACHINERY),
    TRANSPORT("Transporte", ExpenseCategory.TRANSPORT),
    REPAIR("Reparaciones", ExpenseCategory.REPAIR),
    OTHER("Otro", ExpenseCategory.HARVEST),
    ;

    /** Whether [concept] names this kind: its label in front, or one of its [keywords] as a word. */
    fun names(concept: String): Boolean {
        if (concept.trim().startsWith(label, ignoreCase = true)) return true
        val words = java.text.Normalizer.normalize(concept.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9ñ]+"))
        return keywords.any { it in words }
    }

    /** The concept saved for this kind: an additive kind keeps its label in front of any text. */
    fun concept(text: String?): String = when {
        text.isNullOrBlank() -> label
        additive && !text.startsWith(label, ignoreCase = true) -> "$label · $text"
        else -> text
    }
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

package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.labour.LabourPayment
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.harvests.labourAccounts
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton

@Composable
internal fun LabourCampaignSummary(notebook: CampaignNotebook, payments: List<LabourPayment>, onOpen: () -> Unit) {
    val accounts = runCatching { labourAccounts(notebook.campaign.id, notebook.labour, notebook.expenses, payments) }.getOrNull()
    val ledger = notebook.expenses.filter { it.category == ExpenseCategory.LABOR && it.status == ExpenseStatus.POSTED }
    ledger.groupBy { it.currency }.forEach { (currency, expenses) ->
        val total = runCatching { expenses.fold(0L) { amount, expense -> Math.addExact(amount, expense.amountMinor) } }.getOrNull()
        Text("Generado: ${total?.let { Money.format(it, currency) } ?: "No disponible"}", color = MoColors.current.labourText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("campaign-labour-generated"))
    }
    if (ledger.isEmpty() || accounts == null || accounts.any { it.unconfirmed } || !notebook.unnamedLabour.isEmpty) {
        Text("Hay costes sin atribuir a personas. Consulta el detalle para confirmar los precios.", color = MoColors.current.secondaryText, modifier = Modifier.testTag("campaign-labour-unconfirmed"))
    }
    accounts?.flatMap { it.balances }?.groupBy { it.currency }?.forEach { (currency, balances) ->
        val paid = runCatching { balances.fold(0L) { total, balance -> Math.addExact(total, balance.paidMinor) } }.getOrNull()
        val pending = runCatching { balances.fold(0L) { total, balance -> Math.addExact(total, balance.pendingMinor) } }.getOrNull()
        Text("Personas con coste confirmado: ${paid?.let { Money.format(it, currency) } ?: "—"} pagados · ${pending?.let { Money.format(it, currency) } ?: "—"} pendientes", style = MaterialTheme.typography.bodyMedium, color = MoColors.current.secondaryText)
    }
    MoSecondaryButton("Ver jornales y pagos", onOpen, Modifier.fillMaxWidth().testTag("notebook-open-labour"))
}

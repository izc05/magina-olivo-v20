package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.labour.*
import java.util.UUID

internal data class LabourAccount(val person: WorkerLabour, val balances: List<LabourSettlement>, val unconfirmed: Boolean, val costs: List<ConfirmedLabourCost>)

internal fun labourAccounts(campaignId: UUID, entries: List<LabourEntry>, expenses: List<Expense>, payments: List<LabourPayment>): List<LabourAccount> {
    val costs = entries.groupBy { it.harvestId }.flatMap { (day, rows) ->
        val ledgers = expenses.filter { it.harvestId == day && it.campaignId == campaignId && it.origin == ExpenseOrigin.DAY_LABOUR && it.status == ExpenseStatus.POSTED }
        if (ledgers.size != 1) emptyList() else LabourLedgerAllocation.of(ledgers.single(), rows).orEmpty()
    }
    val allocated = costs.map { it.labourEntryId }.toSet()
    return LabourByWorker.of(entries).map { person ->
        val currencies = (costs.filter { it.workerId == person.workerId }.map { it.currency } + payments.filter { it.workerId == person.workerId }.map { it.currency }).distinct().sorted()
        LabourAccount(person, currencies.map { LabourSettlement.of(person.workerId, campaignId, it, costs, payments) }, entries.any { it.workerId == person.workerId && it.id !in allocated }, costs.filter { it.workerId == person.workerId })
    }
}

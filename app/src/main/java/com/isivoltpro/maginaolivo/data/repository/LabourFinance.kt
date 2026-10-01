package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.data.local.entity.LabourPaymentEntity
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRateSnapshot
import com.isivoltpro.maginaolivo.domain.labour.LabourRateBasis
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.LabourPayment
import com.isivoltpro.maginaolivo.domain.labour.ConfirmedLabourCost
import com.isivoltpro.maginaolivo.domain.labour.LabourLedgerAllocation
import java.util.UUID

/** Throw inside the transaction; translate only after Room has rolled back all rows/outbox. */
internal class LabourFinanceInvalid(val field: String, val code: String) : RuntimeException(code)

internal fun HarvestLabourEntity.toLabourEntry() = LabourEntry(
    id, harvestId, workerId, workerName, quantity, LabourUnit.valueOf(unit), minutes, metadata.version,
    if (appliedPriceMinor != null && appliedCurrency != null && appliedPriceDate != null && appliedBasis != null)
        LabourRateSnapshot(appliedPriceMinor, appliedCurrency, appliedPriceDate, LabourRateBasis.valueOf(appliedBasis)) else null,
)

internal fun HarvestLabourEntity.withRate(rate: LabourRateSnapshot?) = copy(
    appliedPriceMinor = rate?.unitPriceMinor, appliedCurrency = rate?.currency,
    appliedPriceDate = rate?.priceDate, appliedBasis = rate?.basis?.name,
)

internal fun LabourPaymentEntity.toPayment() = LabourPayment(id, workerId, campaignId, paymentDate, amountMinor, currency, note)

/** Rebuild only ephemeral allocations of the authoritative Expense ledger, never a second ledger. */
internal class LabourFinance(private val database: MaginaOlivoDatabase) {
    suspend fun costs(campaignId: UUID): List<ConfirmedLabourCost> {
        val campaign = database.campaignDao().findById(campaignId) ?: return emptyList()
        return database.harvestDao().listLiveForCampaign(campaignId).flatMap { day ->
            if (day.workspaceId != campaign.workspaceId) return@flatMap emptyList()
            val entries = database.labourDao().listForHarvest(day.id)
            if (entries.any { it.workspaceId != campaign.workspaceId }) return@flatMap emptyList()
            val expenses = database.expenseDao().listForHarvest(day.id).filter {
                it.campaignId == campaignId && it.workspaceId == campaign.workspaceId &&
                    it.origin == "DAY_LABOUR" && it.status == "POSTED"
            }
            // Ambiguous duplicate ledgers cannot be used to manufacture a payable balance.
            if (expenses.size != 1) emptyList() else
                LabourLedgerAllocation.of(expenses.single().toDomain(), entries.map { it.toLabourEntry() }).orEmpty()
        }
    }

    suspend fun verifyCampaign(campaignId: UUID?) {
        if (campaignId == null) return
        val payments = database.labourPaymentDao().listForCampaign(campaignId)
        if (payments.isEmpty()) return
        val costs = costs(campaignId)
        payments.groupBy { it.workerId to it.currency }.forEach { (key, rows) ->
            val paid = rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
            val generated = costs.filter { it.workerId == key.first && it.currency == key.second }
                .fold(0L) { total, cost -> Math.addExact(total, cost.amountMinor) }
            if (paid > generated) throw LabourFinanceInvalid("amount", "below_paid")
        }
    }
}

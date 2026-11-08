package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.cycle.CycleRecordKind
import com.isivoltpro.maginaolivo.domain.cycle.CycleSnapshot

/** An agricultural cycle and the works explicitly linked to it. A date never makes a work belong. */
data class FarmCycleView(val id: UUID, val label: String, val closed: Boolean, val activityIds: Set<UUID>)

internal fun CycleSnapshot.farmCycles(): List<FarmCycleView> = cycles.map { cycle ->
    FarmCycleView(cycle.id, cycle.label, cycle.effectiveEndDate != null,
        records.filter { it.kind == CycleRecordKind.ACTIVITY.name && it.cycleId == cycle.id }.map { it.id }.toSet())
}

/** The last treatment as it was recorded. A value not recorded stays null. */
data class LastTreatment(
    val date: LocalDate,
    val product: String?,
    val parcels: List<String>,
    val areaM2: Double?,
    val quantity: Double?,
    val unit: String?,
)

data class RecentWork(val type: ActivityType, val date: LocalDate, val parcels: List<String>, val done: Boolean)

/** Posted money of one currency. A category that overflows is null, never a wrapped total. */
data class FarmMoney(
    val currency: String,
    val totalMinor: Long?,
    val labourMinor: Long?,
    val machineryMinor: Long?,
    val productsMinor: Long?,
    /** Fuel, irrigation, services...: so the parts always add up to the total. */
    val otherMinor: Long?,
)

/** Read-only summary of records that already exist; nothing here is stored or estimated. */
data class FarmDashboard(
    val campaignName: String?,
    /** The open agricultural cycle, not the harvest; null when the farm has none. */
    val openCycleLabel: String?,
    val treatmentsInOpenCycle: Int,
    val lastTreatment: LastTreatment?,
    val incompleteTreatments: Int,
    val recentWorks: List<RecentWork>,
    val pendingWorks: Int,
    val doneWorks: Int,
    val harvestDays: Int,
    val harvestedGrams: Long?,
    val deliveryCount: Int,
    val deliveredGrams: Long?,
    val destinations: List<String>,
    val latestFatYieldHundredths: Int?,
    val money: List<FarmMoney>,
    val draftExpenses: Int,
    val documentCount: Int,
    val latestDocuments: List<String> = emptyList(),
    val lastIrrigation: LocalDate? = null,
    val irrigationCount: Int = 0,
) {
    companion object {
        private const val RECENT = 3

        private fun checkedSum(values: List<Long>): Long? =
            runCatching { values.fold(0L) { total, value -> Math.addExact(total, value) } }.getOrNull()

        private fun Activity.isIncompleteTreatment(): Boolean {
            val detail = detail as? ActivityDetail.Phytosanitary ?: return true
            return detail.productName.isNullOrBlank() || detail.productRegistrationNumber.isNullOrBlank() ||
                detail.totalQuantity == null || detail.unit.isNullOrBlank() || detail.reason.isNullOrBlank() && detail.pestProblemCode.isNullOrBlank() ||
                targets.isEmpty()
        }

        /** Every list must already belong to this farm; each Activity counts once however many parcels it covers. */
        fun of(
            campaignId: UUID?,
            campaignName: String?,
            activities: List<Activity>,
            harvests: List<Harvest>,
            deliveries: List<Delivery>,
            expenses: List<Expense>,
            documentCount: Int,
            latestDocuments: List<String> = emptyList(),
            openCycle: FarmCycleView? = null,
        ): FarmDashboard {
            val done = activities.filter { it.status == ActivityStatus.COMPLETED }
            val treatments = done.filter { it.type == ActivityType.PHYTOSANITARY }.sortedByDescending { it.activityDate }
            val campaignHarvests = harvests.filter { campaignId != null && it.campaignId == campaignId }
            val campaignDeliveries = deliveries.filter { campaignId != null && it.campaignId == campaignId }
            val scopedExpenses = if (campaignId != null) expenses.filter { it.campaignId == campaignId } else expenses
            val posted = scopedExpenses.filter { it.status == ExpenseStatus.POSTED }
            val last = treatments.firstOrNull()
            val detail = last?.detail as? ActivityDetail.Phytosanitary
            return FarmDashboard(
                campaignName = campaignName,
                openCycleLabel = openCycle?.label,
                treatmentsInOpenCycle = openCycle?.let { cycle -> treatments.count { it.id in cycle.activityIds } } ?: 0,
                lastTreatment = last?.let { activity ->
                    LastTreatment(
                        date = activity.activityDate,
                        product = detail?.productName,
                        parcels = activity.targets.map { it.parcelName },
                        // Only when every parcel has its area: a partial sum would read as the whole.
                        areaM2 = activity.targets.takeIf { it.isNotEmpty() && it.all { t -> t.areaAffectedM2 != null } }?.sumOf { it.areaAffectedM2!! },
                        quantity = detail?.totalQuantity,
                        unit = detail?.unit,
                    )
                },
                incompleteTreatments = treatments.count { it.isIncompleteTreatment() },
                recentWorks = activities.filter { it.status == ActivityStatus.COMPLETED || it.status == ActivityStatus.PLANNED }
                    .sortedByDescending { it.activityDate }.take(RECENT)
                    .map { activity -> RecentWork(activity.type, activity.activityDate, activity.targets.map { it.parcelName }, activity.status == ActivityStatus.COMPLETED) },
                pendingWorks = activities.count { it.status == ActivityStatus.PLANNED },
                doneWorks = done.size,
                harvestDays = campaignHarvests.size,
                harvestedGrams = checkedSum(campaignHarvests.map { it.totalGrams }),
                deliveryCount = campaignDeliveries.size,
                deliveredGrams = checkedSum(campaignDeliveries.map { it.netGrams }),
                destinations = campaignDeliveries.map { it.destinationName }.filter(String::isNotBlank).distinct(),
                latestFatYieldHundredths = campaignDeliveries.filter { it.analysis?.fatYieldHundredths != null }
                    .maxByOrNull { it.deliveryDate }?.analysis?.fatYieldHundredths,
                money = posted.groupBy { it.currency }.toSortedMap().map { (currency, rows) ->
                    fun sum(category: ExpenseCategory) = checkedSum(rows.filter { it.category == category }.map { it.amountMinor })
                    val listed = setOf(ExpenseCategory.LABOR, ExpenseCategory.MACHINERY, ExpenseCategory.PRODUCTS)
                    FarmMoney(currency, checkedSum(rows.map { it.amountMinor }), sum(ExpenseCategory.LABOR), sum(ExpenseCategory.MACHINERY), sum(ExpenseCategory.PRODUCTS),
                        checkedSum(rows.filter { it.category !in listed }.map { it.amountMinor }))
                },
                draftExpenses = scopedExpenses.count { it.status == ExpenseStatus.DRAFT },
                documentCount = documentCount,
                latestDocuments = latestDocuments,
                lastIrrigation = done.filter { it.type == ActivityType.IRRIGATION }.maxOfOrNull { it.activityDate },
                irrigationCount = done.count { it.type == ActivityType.IRRIGATION },
            )
        }
    }
}

internal fun observeFarmCycles(persistence: LocalPersistence, farmId: UUID): Flow<List<FarmCycleView>> =
    persistence.agriculturalCycles?.observe(farmId)?.map { result ->
        (result as? AppResult.Success)?.value?.farmCycles().orEmpty()
    } ?: flowOf(emptyList())

/** Reuses the existing repositories; the farm filter is applied here, before anything is summed. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun farmDashboardFlow(persistence: LocalPersistence, farmId: UUID): Flow<FarmDashboard> =
    persistence.campaignRepository.observeForFarm(farmId).flatMapLatest { campaigns ->
        val campaign = campaigns.filter { it.status == CampaignStatus.ACTIVE || it.status == CampaignStatus.HARVEST }
            .maxByOrNull { it.startDate }
        val harvests = campaign?.let { persistence.harvestRepository.observeForCampaign(it.id) } ?: flowOf(emptyList())
        val deliveries = campaign?.let { persistence.deliveryRepository.observeForCampaign(it.id) } ?: flowOf(emptyList())
        combine(
            persistence.activityRepository.observeForFarm(farmId),
            combine(harvests, deliveries, ::Pair),
            persistence.expenseRepository.observeAll(),
            persistence.attachmentRepository.observeForOwner(AttachmentOwner(AttachmentOwnerType.FARM, farmId)),
            observeFarmCycles(persistence, farmId),
        ) { activities, (harvestRows, deliveryRows), expenses, documents, cycles ->
            FarmDashboard.of(
                campaign?.id, campaign?.name, activities,
                harvestRows.filter { it.farmId == farmId }, deliveryRows.filter { it.farmId == farmId },
                expenses.filter { it.farmId == farmId }, documents.size,
                documents.sortedByDescending { it.createdAt }.take(2).map { it.displayName },
                cycles.firstOrNull { !it.closed },
            )
        }
    }

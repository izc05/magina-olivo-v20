package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.*
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.harvest.*
import com.isivoltpro.maginaolivo.domain.labour.*
import java.time.LocalDate
import java.util.UUID

/** Called inside the caller's Room transaction: day creation and crew recording commit together. */
internal suspend fun saveCampaignQuickLabour(
    farmId: UUID, campaignId: UUID, date: LocalDate, draft: CrewDraft,
    contexts: List<HarvestContext>,
    openDay: suspend (UUID, LocalDate) -> AppResult<UUID>,
    readDay: suspend (UUID) -> Harvest?,
    record: suspend (CrewDraft) -> AppResult<Int>,
): AppResult<Int> {
    val context = contexts.filter { it.farmId == farmId && it.campaignStatus.isRunning }.singleOrNull()
    if (context?.campaignId != campaignId) return AppResult.Failure(AppError.Validation("campaignId", "changed_or_ambiguous"))
    LabourRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
    if (date.isBefore(context.campaignStart)) return AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
    val dayId = when (val opened = openDay(farmId, date)) {
        is AppResult.Success -> opened.value
        is AppResult.Failure -> return opened
    }
    val day = readDay(dayId)
    if (day?.farmId != farmId || day.campaignId != campaignId || day.harvestDate != date || !day.editable) {
        return AppResult.Failure(AppError.Validation("campaignId", "changed_or_ambiguous"))
    }
    return record(draft.copy(harvestId = dayId))
}

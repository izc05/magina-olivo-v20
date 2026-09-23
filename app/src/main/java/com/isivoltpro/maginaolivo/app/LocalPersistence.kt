package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmCoverRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository

data class LocalPersistence(
    val database: MaginaOlivoDatabase,
    val farmRepository: FarmRepository,
    val farmCoverRepository: FarmCoverRepository,
    val parcelRepository: ParcelRepository,
    val campaignRepository: CampaignRepository,
    val activityRepository: ActivityRepository,
    val attachmentRepository: AttachmentRepository,
    val organizationRepository: OrganizationRepository,
    val expenseRepository: ExpenseRepository,
    val documentOcrRepository: DocumentOcrRepository,
    val workspaceRepository: WorkspaceRepository,
)

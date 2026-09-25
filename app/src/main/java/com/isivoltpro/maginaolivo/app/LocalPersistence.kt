package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmCoverRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.machinery.MachineRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.domain.agenda.ReminderReconciler
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed

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
    val harvestRepository: HarvestRepository,
    val deliveryRepository: DeliveryRepository,
    val machineRepository: MachineRepository,
    /** Phase 19D: jornales of each Jornada. */
    val labourRepository: LabourRepository,
    /** Phase 19E: equipment used on each Jornada. */
    val equipmentRepository: EquipmentRepository,
    val workspaceRepository: WorkspaceRepository,
    /** Rebuilds planned-work alarms (Phase 16); run at start. Null where alarms do not exist. */
    val reminders: ReminderReconciler? = null,
    /** Phase 20A: Inicio's weather, cache first. Null where no feed exists (tests). */
    val weatherFeed: WeatherFeed? = null,
)

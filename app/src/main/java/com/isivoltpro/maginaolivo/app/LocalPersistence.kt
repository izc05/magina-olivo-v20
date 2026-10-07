package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.domain.expense.DayCostRepository
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
import com.isivoltpro.maginaolivo.domain.weather.RadarSource
import com.isivoltpro.maginaolivo.domain.market.OilMarketFeed
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryResourceRepository
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
    /** CR-010 A3: recollection prices and calculated day costs. Null where not wired (tests). */
    val dayCostRepository: DayCostRepository? = null,
    /** Rebuilds planned-work alarms (Phase 16); run at start. Null where alarms do not exist. */
    val reminders: ReminderReconciler? = null,
    /** Phase 20A: Inicio's weather, cache first. Null where no feed exists (tests). */
    val weatherFeed: WeatherFeed? = null,
    /** Phase 20B-radar: live radar pictures, never cached. Null where not configured. */
    val radarSource: RadarSource? = null,
    /** Phase 20D: the official weekly oil series, cache first. Null where no feed exists (tests). */
    val oilMarketFeed: OilMarketFeed? = null,
    /** Phase 21A: «Mi perfil» — municipality and preferred cooperative. Null where not wired (tests). */
    val profileRepository: ProfileRepository? = null,
    /** CUE v24: reusable applicators/advisors and regulatory Machine resources. */
    val phytosanitaryResourceRepository: PhytosanitaryResourceRepository? = null,
)

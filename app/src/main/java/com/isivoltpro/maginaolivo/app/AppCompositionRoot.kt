package com.isivoltpro.maginaolivo.app

import android.content.Context
import com.isivoltpro.maginaolivo.BuildConfig
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.logging.AppLogger
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.regional.UnitPreferences
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.remote.weather.EdgeRadarSource
import com.isivoltpro.maginaolivo.data.remote.market.EdgeOilMarketSource
import com.isivoltpro.maginaolivo.data.remote.weather.EdgeWeatherSource
import com.isivoltpro.maginaolivo.data.repository.CachedOilMarketFeed
import com.isivoltpro.maginaolivo.data.repository.CachedWeatherFeed
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.AndroidAttachmentFileStore
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstAttachmentRepository
import com.isivoltpro.maginaolivo.data.repository.JsonProposalCodec
import com.isivoltpro.maginaolivo.data.repository.MlKitOcrEngine
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDocumentOcrRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDayCostRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstEquipmentRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstLabourRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstMachineRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstOrganizationRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstProfileRepository
import com.isivoltpro.maginaolivo.data.repository.RoomReminderPreferences
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmCoverRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.reminder.AndroidReminderScheduler
import com.isivoltpro.maginaolivo.data.reminder.ReminderCoordinator

data class AppCompositionRoot(
    val environment: AppEnvironment,
    val clock: AppClock,
    val idGenerator: IdGenerator,
    val dispatchers: AppDispatchers,
    val logger: AppLogger,
    val regionalContext: RegionalContext,
    val unitPreferences: UnitPreferences,
    val onboardingStateStore: OnboardingStateStore,
    val localPersistence: LocalPersistence?,
    /** UX-C: the Farm Mi Cuaderno opens on (device preference). */
    val activeFarmStore: ActiveFarmStore = InMemoryActiveFarmStore(),
) {
    companion object {
        fun createDefault(environmentValue: String): AppCompositionRoot =
            AppCompositionRoot(
                environment = AppEnvironment.fromBuildConfig(environmentValue),
                clock = SystemAppClock(),
                idGenerator = UuidGenerator(),
                dispatchers = DefaultAppDispatchers(),
                logger = AndroidAppLogger(),
                regionalContext = RegionalContext.spainDefault(),
                unitPreferences = UnitPreferences(),
                onboardingStateStore = InMemoryOnboardingStateStore(),
                localPersistence = null,
            )

        fun createAndroid(
            context: Context,
            environmentValue: String,
        ): AppCompositionRoot {
            val applicationContext = context.applicationContext
            val defaults = createDefault(environmentValue)
            val database = MaginaOlivoDatabase.getInstance(applicationContext)
            val farmRepository = OfflineFirstFarmRepository(
                database = database,
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
            )
            val workspaceRepository = LocalWorkspaceRepository(
                database = database,
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
                regionalContext = defaults.regionalContext,
            )
            val attachmentFileStore = AndroidAttachmentFileStore(applicationContext)
            val farmCoverRepository = OfflineFirstFarmCoverRepository(
                database = database,
                fileStore = attachmentFileStore,
                workspaceRepository = workspaceRepository,
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
            )
            val parcelRepository = OfflineFirstParcelRepository(
                database = database,
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
            )
            val campaignRepository = OfflineFirstCampaignRepository(database, defaults.clock, defaults.idGenerator, defaults.dispatchers)
            // Phase 21B: Perfil → Avisos (on/off, day-before hour) drives the alarms.
            val reminderPreferences = RoomReminderPreferences(database)
            val reminders = ReminderCoordinator(
                database, AndroidReminderScheduler(applicationContext), defaults.clock, reminderPreferences,
            )
            val activityRepository = OfflineFirstActivityRepository(
                database, defaults.clock, defaults.idGenerator, defaults.dispatchers, reminders, reminderPreferences,
            )
            val attachmentRepository = OfflineFirstAttachmentRepository(
                database = database,
                fileStore = attachmentFileStore,
                workspaceRepository = workspaceRepository,
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
            )
            val organizationRepository = OfflineFirstOrganizationRepository(
                database, workspaceRepository, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val expenseRepository = OfflineFirstExpenseRepository(
                database, workspaceRepository, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val documentOcrRepository = OfflineFirstDocumentOcrRepository(
                database = database,
                attachments = attachmentRepository,
                workspaceRepository = workspaceRepository,
                engine = MlKitOcrEngine(applicationContext),
                proposalCodec = JsonProposalCodec(),
                clock = defaults.clock,
                idGenerator = defaults.idGenerator,
                dispatchers = defaults.dispatchers,
            )
            val harvestRepository = OfflineFirstHarvestRepository(
                database, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val deliveryRepository = OfflineFirstDeliveryRepository(
                database, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val machineRepository = OfflineFirstMachineRepository(
                database, workspaceRepository, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val labourRepository = OfflineFirstLabourRepository(
                database, workspaceRepository, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val equipmentRepository = OfflineFirstEquipmentRepository(
                database, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            val dayCostRepository = OfflineFirstDayCostRepository(
                database, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            // Phase 20B (CR-006): AEMET -> MET Norway through the weather Edge Function. Without
            // the public anon key in this build the card says the weather is not configured.
            val weatherSource = BuildConfig.WEATHER_ANON_KEY.takeIf { it.isNotBlank() }?.let { key ->
                EdgeWeatherSource(BuildConfig.WEATHER_FUNCTIONS_URL, key)
            }
            // Phase 20B-radar: the same public key and functions; no key, no radar entry.
            val radarSource = BuildConfig.WEATHER_ANON_KEY.takeIf { it.isNotBlank() }?.let { key ->
                EdgeRadarSource(BuildConfig.WEATHER_FUNCTIONS_URL, key)
            }
            val weatherFeed = CachedWeatherFeed(
                database, source = weatherSource, workspaces = workspaceRepository, clock = defaults.clock, dispatchers = defaults.dispatchers,
            )
            // Phase 20D: the official Junta series comes from the `oil-market` Edge Function
            // (deployed and validated live, deploy run #3, 2026-09-28). Without the public key the
            // source stays off and the card says so.
            val oilSource = BuildConfig.WEATHER_ANON_KEY.takeIf { it.isNotBlank() && OIL_MARKET_FUNCTION_DEPLOYED }?.let { key ->
                EdgeOilMarketSource(BuildConfig.WEATHER_FUNCTIONS_URL, key)
            }
            val oilMarketFeed = CachedOilMarketFeed(
                database, source = oilSource, workspaces = workspaceRepository, clock = defaults.clock, dispatchers = defaults.dispatchers,
            )
            val profileRepository = OfflineFirstProfileRepository(
                database, workspaceRepository, organizationRepository, defaults.clock, defaults.idGenerator, defaults.dispatchers,
            )
            return defaults.copy(
                onboardingStateStore = AndroidOnboardingStateStore(applicationContext),
                activeFarmStore = AndroidActiveFarmStore(applicationContext),
                localPersistence = LocalPersistence(
                    database = database,
                    farmRepository = farmRepository,
                    farmCoverRepository = farmCoverRepository,
                    parcelRepository = parcelRepository,
                    campaignRepository = campaignRepository,
                    activityRepository = activityRepository,
                    attachmentRepository = attachmentRepository,
                    organizationRepository = organizationRepository,
                    expenseRepository = expenseRepository,
                    documentOcrRepository = documentOcrRepository,
                    harvestRepository = harvestRepository,
                    deliveryRepository = deliveryRepository,
                    machineRepository = machineRepository,
                    labourRepository = labourRepository,
                    equipmentRepository = equipmentRepository,
                    dayCostRepository = dayCostRepository,
                    workspaceRepository = workspaceRepository,
                    reminders = reminders,
                    weatherFeed = weatherFeed,
                    radarSource = radarSource,
                    oilMarketFeed = oilMarketFeed,
                    profileRepository = profileRepository,
                ),
            )
        }
    }
}

/** Phase 20D-3: the `oil-market` Edge Function is deployed and answered 200 from eu-west-3. */
private const val OIL_MARKET_FUNCTION_DEPLOYED = true

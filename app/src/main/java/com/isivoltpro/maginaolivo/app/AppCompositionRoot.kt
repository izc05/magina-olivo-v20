package com.isivoltpro.maginaolivo.app

import android.content.Context
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
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository

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
            return defaults.copy(
                onboardingStateStore = AndroidOnboardingStateStore(applicationContext),
                localPersistence = LocalPersistence(database, farmRepository),
            )
        }
    }
}

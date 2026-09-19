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

data class AppCompositionRoot(
    val environment: AppEnvironment,
    val clock: AppClock,
    val idGenerator: IdGenerator,
    val dispatchers: AppDispatchers,
    val logger: AppLogger,
    val regionalContext: RegionalContext,
    val unitPreferences: UnitPreferences,
    val onboardingStateStore: OnboardingStateStore,
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
            )

        fun createAndroid(
            context: Context,
            environmentValue: String,
        ): AppCompositionRoot =
            createDefault(environmentValue).copy(
                onboardingStateStore = AndroidOnboardingStateStore(context.applicationContext),
            )
    }
}

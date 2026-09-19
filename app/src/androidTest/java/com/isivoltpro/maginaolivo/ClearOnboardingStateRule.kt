package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Clears first-run state before ActivityScenario launches the activity under test. */
class ClearOnboardingStateRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context
                    .getSharedPreferences(
                        AndroidOnboardingStateStore.PREFERENCES_NAME,
                        Context.MODE_PRIVATE,
                    ).edit()
                    .clear()
                    .commit()
                base.evaluate()
            }
        }
}

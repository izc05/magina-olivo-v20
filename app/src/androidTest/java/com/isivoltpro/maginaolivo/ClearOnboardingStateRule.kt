package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Clears durable app state before ActivityScenario launches the activity under test. */
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
                runBlocking {
                    withContext(Dispatchers.IO) {
                        MaginaOlivoDatabase.getInstance(context).clearAllTables()
                    }
                }
                base.evaluate()
            }
        }
}

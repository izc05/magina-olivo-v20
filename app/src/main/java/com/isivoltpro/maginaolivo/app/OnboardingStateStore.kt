package com.isivoltpro.maginaolivo.app

import android.content.Context
import androidx.core.content.edit

interface OnboardingStateStore {
    fun isCompleted(): Boolean

    fun markCompleted()
}

class InMemoryOnboardingStateStore(
    completed: Boolean = false,
) : OnboardingStateStore {
    private var completed = completed

    override fun isCompleted(): Boolean = completed

    override fun markCompleted() {
        completed = true
    }
}

class AndroidOnboardingStateStore(
    context: Context,
) : OnboardingStateStore {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    override fun markCompleted() {
        preferences.edit {
            putBoolean(KEY_COMPLETED, true)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "magina_olivo_app_state"
        const val KEY_COMPLETED = "onboarding_completed"
    }
}

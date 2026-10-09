package com.isivoltpro.maginaolivo.app

import android.content.Context

class AndroidAppearanceStore(context: Context) : AppearanceStore by PersistentAppearanceStore(
    object : AppearanceStorage {
        private val preferences by lazy {
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }
        override fun read(): String? = preferences.getString("mode", null)
        override fun write(value: String): Boolean = preferences.edit().putString("mode", value).commit()
    },
) {
    companion object {
        const val PREFERENCES_NAME = "magina_ui_appearance"
    }
}

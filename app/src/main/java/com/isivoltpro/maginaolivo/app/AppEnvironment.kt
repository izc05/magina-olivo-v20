package com.isivoltpro.maginaolivo.app

enum class AppEnvironment {
    DEV,
    STAGING,
    PRODUCTION;

    companion object {
        fun fromBuildConfig(value: String): AppEnvironment =
            entries.firstOrNull { it.name == value } ?: DEV
    }
}

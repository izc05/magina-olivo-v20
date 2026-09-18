package com.isivoltpro.maginaolivo.core.regional

import java.time.ZoneId
import java.util.Currency
import java.util.Locale

data class RegionalContext(
    val countryCode: String,
    val locale: Locale,
    val zoneId: ZoneId,
    val currency: Currency,
) {
    companion object {
        fun spainDefault(): RegionalContext = RegionalContext(
            countryCode = "ES",
            locale = Locale.forLanguageTag("es-ES"),
            zoneId = ZoneId.of("Europe/Madrid"),
            currency = Currency.getInstance("EUR"),
        )
    }
}

data class UnitPreferences(
    val area: AreaUnit = AreaUnit.HECTARE,
    val volume: VolumeUnit = VolumeUnit.LITER,
    val mass: MassUnit = MassUnit.KILOGRAM,
)

enum class AreaUnit {
    HECTARE,
    ACRE,
}

enum class VolumeUnit {
    LITER,
    CUBIC_METER,
}

enum class MassUnit {
    KILOGRAM,
    TONNE,
}

package com.isivoltpro.maginaolivo.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface AppClock {
    fun nowInstant(): Instant

    fun today(zoneId: ZoneId): LocalDate
}

class SystemAppClock : AppClock {
    override fun nowInstant(): Instant = Instant.now()

    override fun today(zoneId: ZoneId): LocalDate = LocalDate.now(zoneId)
}

package com.isivoltpro.maginaolivo.domain.weather

import java.time.Instant

/** One radar picture: the time it shows and where its map tiles are ({z}/{x}/{y}). */
data class RadarFrame(val time: Instant, val tileUrlTemplate: String)

/**
 * Phase 20B-radar — the recent radar pictures, as the provider gave them. Radar is live-only
 * (spec §10): it is never cached and shown later as if it were current.
 */
data class RadarFrames(
    val provider: String,
    val attribution: String,
    val updatedAt: Instant?,
    /** Oldest first. Never empty. */
    val frames: List<RadarFrame>,
) {
    init {
        require(frames.isNotEmpty()) { "radar without frames" }
    }

    val latest: RadarFrame get() = frames.last()
}

/** Where the radar comes from; the UI never calls the network itself. */
interface RadarSource {
    suspend fun frames(): RadarFrames
}

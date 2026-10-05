package com.isivoltpro.maginaolivo.data.reminder

import java.util.UUID

/**
 * #573 — Android PendingIntent/notification ids are device-local slots, not domain identity.
 *
 * UUID.hashCode() is still the preferred first slot to keep existing installs stable, but a
 * collision is resolved by deterministic linear probing against every slot already persisted.
 */
internal fun allocateReminderRequestCode(reminderId: UUID, used: Set<Int>): Int {
    var candidate = reminderId.hashCode()
    repeat(MAX_PROBES) {
        if (candidate !in used) return candidate
        candidate = if (candidate == Int.MAX_VALUE) Int.MIN_VALUE else candidate + 1
    }
    error("reminder_request_code_exhausted")
}

private const val MAX_PROBES = 1_000_000

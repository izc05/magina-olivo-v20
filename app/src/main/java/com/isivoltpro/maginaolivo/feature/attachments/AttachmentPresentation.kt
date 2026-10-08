package com.isivoltpro.maginaolivo.feature.attachments

import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val captureName = Regex("captura-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg", RegexOption.IGNORE_CASE)
private val photoDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))

/** Presentation only: never replaces stored names, attachment identity or file references. */
internal fun attachmentLabels(attachments: List<Attachment>, zone: ZoneId): Map<UUID, String> {
    val labels = attachments.associate { it.id to it.displayName }.toMutableMap()
    attachments.filter { it.kind == AttachmentKind.PHOTO && captureName.matches(it.displayName) }
        .groupBy { it.owner to it.createdAt.atZone(zone).toLocalDate() }
        .forEach { (group, photos) ->
            val title = when (group.first.type) {
                AttachmentOwnerType.DELIVERY -> "Foto del vale"
                AttachmentOwnerType.EXPENSE -> "Foto de factura/ticket"
                else -> "Foto"
            }
            photos.sortedWith(compareBy<Attachment> { it.createdAt }.thenBy { it.id.toString() })
                .forEachIndexed { index, attachment ->
                    val ordinal = if (photos.size > 1) " ${index + 1}" else ""
                    labels[attachment.id] = "$title$ordinal · ${photoDate.format(group.second)}"
                }
        }
    return labels
}

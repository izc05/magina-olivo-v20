package com.isivoltpro.maginaolivo.domain.profile

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Phase 21A — «Mi perfil»: the farmer's municipality and preferred cooperative, stored on the
 * phone first (Room v19) and representable for a later sync. The cooperative is a reference to
 * an existing Organization, never a copied name: a rename shows at once, and an archived one (or
 * one that is no longer a cooperative or mill) is simply no longer the preferred cooperative.
 */
data class ProfileSettings(
    val municipality: String? = null,
    val province: String? = null,
    val preferredCooperative: Organization? = null,
    /** Phase 21B: Perfil → Avisos. */
    val reminders: ReminderPreferences = ReminderPreferences(),
) {
    /** The place Inicio may ask the weather about when the farms give none; null when unset. */
    val location: FeedLocation? get() = FeedLocation.common(listOf(municipality to province))
}

data class ProfileDraft(
    val municipality: String?,
    val province: String?,
    val preferredOrganizationId: UUID?,
)

/** Roles a preferred cooperative may play: where the farmer takes the olives. */
val PREFERRED_COOPERATIVE_ROLES: Set<OrganizationRole> = setOf(OrganizationRole.COOPERATIVE, OrganizationRole.MILL)

interface ProfileRepository {
    fun observe(): Flow<ProfileSettings>

    suspend fun save(draft: ProfileDraft): AppResult<Unit>

    /** Phase 21B: stores the reminder preferences; the caller then rebuilds the alarms. */
    suspend fun saveReminders(preferences: ReminderPreferences): AppResult<Unit>
}

package com.isivoltpro.maginaolivo.domain.territorial

/**
 * Modelo de dominio para un municipio del catálogo territorial público.
 */
data class TerritorialMunicipality(
    val slug: String,
    val name: String,
    val province: String = "Jaén",
    val ineCode: String?,
    val aemetCode: String,
    val comarca: String = "Sierra Mágina",
    val officialUrl: String? = null,
    val electronicSeatUrl: String? = null,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val sourceUrl: String,
    val localities: List<TerritorialLocality> = emptyList(),
)

/**
 * Modelo de dominio para una localidad, pedanía o entidad local dentro de un municipio.
 * Ejemplo: Garcíez como entidad local dependiente de Bedmar y Garcíez.
 */
data class TerritorialLocality(
    val slug: String,
    val name: String,
    val municipalitySlug: String,
    val isPedaniaOrLocalEntity: Boolean = false,
    val postalCode: String? = null,
)

/**
 * Cooperativa o almazara verificada del catálogo público comarcal.
 */
data class TerritorialCooperativeInfo(
    val id: String,
    val officialName: String,
    val brandName: String,
    val municipalitySlug: String,
    val localitySlug: String,
    val entityType: String = "cooperative_sca", // "cooperative_sca", "sat", "almazara"
    val dopSierraMagina: Boolean = true,
    val mainBrands: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val websiteUrl: String? = null,
    val verificationStatus: String = "verified",
    val sourceUrl: String,
)

/**
 * Comunidad de regantes verificada del catálogo público.
 */
data class TerritorialIrrigationCommunityInfo(
    val id: String,
    val officialName: String,
    val shortName: String,
    val primaryMunicipalitySlug: String,
    val entityType: String = "community_of_irrigation", // "community_of_irrigation", "sat_irrigation"
    val bulletinReference: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val websiteUrl: String? = null,
    val electronicSeatUrl: String? = null,
    val verificationStatus: String = "verified",
    val notes: String? = null,
)

/**
 * Aviso comunitario público de turnos o cortes de riego.
 */
data class WaterNotice(
    val id: String,
    val communityId: String,
    val sectorCode: String? = null,
    val noticeType: String = "shift_schedule", // "shift_schedule", "water_cut", "schedule_change", "general_notice"
    val title: String,
    val body: String,
    val startsAtEpochMs: Long,
    val endsAtEpochMs: Long,
    val sourceType: String = "public_bulletin",
    val sourceUrl: String? = null,
    val status: String = "published", // "published", "revoked", "expired"
    val publishedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
)

/**
 * Elemento de la agenda personal offline de riego del agricultor.
 * No genera anotación en actividades CUE hasta confirmación del usuario.
 */
data class PersonalIrrigationPlanItem(
    val id: String,
    val workspaceId: String,
    val plotId: String,
    val sectorCode: String? = null,
    val scheduledAtEpochMs: Long,
    val durationMinutes: Int,
    val linkedNoticeId: String? = null,
    val status: String = "planned", // "planned", "reprogrammed", "cancelled", "completed"
    val reminderMinutesBefore: Int = 30,
)

/**
 * Contrato de noticias, bandos y avisos municipales (#532).
 */
data class MunicipalPublication(
    val id: String,
    val municipalitySlug: String,
    val category: String, // "bando", "anuncio", "aviso_agrario", "convocatoria"
    val title: String,
    val summary: String,
    val sourceUrl: String,
    val publishedAtEpochMs: Long,
    val isOfficial: Boolean = true,
)

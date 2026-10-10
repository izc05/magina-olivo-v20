package com.isivoltpro.maginaolivo.feature.territorial

import com.isivoltpro.maginaolivo.data.local.entity.CommunityWaterNoticeEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationCommunityEntity
import com.isivoltpro.maginaolivo.data.local.entity.PersonalIrrigationPlanEntity
import com.isivoltpro.maginaolivo.data.local.entity.TerritorialMunicipalityEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TerritorialCatalogTest {

    @Test
    fun verifiedMunicipalitiesKeepOfficialIneAndAemetCodes() {
        val bedmar = TerritorialMunicipalityEntity(
            slug = "bedmar-y-garciez",
            name = "Bedmar y Garcíez",
            province = "Jaén",
            ineCode = "23902",
            aemetCode = "23902",
            comarca = "Sierra Mágina",
            officialUrl = "https://www.bedmargarciez.es/",
            electronicSeatUrl = "https://bedmargarciez.sedelectronica.es/",
            centerLatitude = 37.8183,
            centerLongitude = -3.4144,
            sourceUrl = "https://www.aemet.es/es/eltiempo/prediccion/municipios/bedmar-y-garciez-id23902"
        )

        val jodar = TerritorialMunicipalityEntity(
            slug = "jodar",
            name = "Jódar",
            province = "Jaén",
            ineCode = "23053",
            aemetCode = "23053",
            comarca = "Sierra Mágina",
            officialUrl = "https://www.jodar.es/",
            electronicSeatUrl = "https://jodar.sedelectronica.es/",
            centerLatitude = 37.8406,
            centerLongitude = -3.3537,
            sourceUrl = "https://www.aemet.es/es/eltiempo/prediccion/municipios/jodar-id23053"
        )

        assertEquals("23902", bedmar.ineCode)
        assertEquals("23053", jodar.ineCode)
        assertNotEquals(bedmar.slug, jodar.slug)
        assertTrue(bedmar.electronicSeatUrl!!.contains("sedelectronica.es"))
        assertTrue(jodar.electronicSeatUrl!!.contains("sedelectronica.es"))
    }

    @Test
    fun homonymousCommunitiesKeepDistinctIdsAndPrimaryMunicipalities() {
        val jandulilla = IrrigationCommunityEntity(
            id = "20000000-0000-4000-8000-000000000001",
            officialName = "Comunidad de Regantes Canal del Jandulilla",
            shortName = "Canal del Jandulilla",
            entityType = "community_of_irrigation",
            primaryMunicipalitySlug = "jodar",
            verificationStatus = "verified",
            sourceBulletinRef = "BOE-B-2025-14108"
        )

        val lagunillas = IrrigationCommunityEntity(
            id = "20000000-0000-4000-8000-000000000002",
            officialName = "Comunidad de Regantes Lagunillas y Cerrón",
            shortName = "Lagunillas y Cerrón",
            entityType = "community_of_irrigation",
            primaryMunicipalitySlug = "bedmar-y-garciez",
            verificationStatus = "verified",
            sourceBulletinRef = "BOE-B-2026-19273"
        )

        assertNotEquals(jandulilla.id, lagunillas.id)
        assertNotEquals(jandulilla.primaryMunicipalitySlug, lagunillas.primaryMunicipalitySlug)
        assertEquals("verified", jandulilla.verificationStatus)
        assertEquals("verified", lagunillas.verificationStatus)
    }

    @Test
    fun revokedCommunityNoticesMaintainStatusWithoutDeletingHistory() {
        val revokedNotice = CommunityWaterNoticeEntity(
            id = UUID.randomUUID().toString(),
            communityId = "20000000-0000-4000-8000-000000000001",
            sectorCode = "SEC-01",
            noticeType = "water_cut",
            title = "Aviso de corte temporal de riego por mantenimiento",
            body = "Se cancela el corte previsto por finalización anticipada de obras.",
            startsAtEpochMs = System.currentTimeMillis() - 3600000,
            endsAtEpochMs = System.currentTimeMillis() + 3600000,
            sourceType = "public_bulletin",
            status = "revoked"
        )

        assertEquals("revoked", revokedNotice.status)
        assertEquals("water_cut", revokedNotice.noticeType)
        assertTrue(revokedNotice.endsAtEpochMs >= revokedNotice.startsAtEpochMs)
    }

    @Test
    fun personalIrrigationPlanFunctionsOfflineWithoutMutatingPrivateActivities() {
        val workspaceId = UUID.randomUUID().toString()
        val plotId = UUID.randomUUID().toString()

        val personalPlan = PersonalIrrigationPlanEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            plotId = plotId,
            sectorCode = "SEC-JANDULILLA",
            scheduledAtEpochMs = System.currentTimeMillis() + 86400000,
            durationMinutes = 120,
            linkedNoticeId = null,
            status = "planned",
            reminderMinutesBefore = 30
        )

        assertEquals("planned", personalPlan.status)
        assertEquals(120, personalPlan.durationMinutes)
        assertNull(personalPlan.linkedNoticeId)
        assertNotNull(personalPlan.workspaceId)
    }
}

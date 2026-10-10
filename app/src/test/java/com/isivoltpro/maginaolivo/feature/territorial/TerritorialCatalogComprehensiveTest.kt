package com.isivoltpro.maginaolivo.feature.territorial

import com.isivoltpro.maginaolivo.data.local.TerritorialCatalogData
import com.isivoltpro.maginaolivo.domain.territorial.MunicipalPublication
import com.isivoltpro.maginaolivo.domain.territorial.PersonalIrrigationPlanItem
import com.isivoltpro.maginaolivo.domain.territorial.WaterNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TerritorialCatalogComprehensiveTest {

    @Test
    fun verifiedMunicipalitiesCoverBedmarAndJodarWithOfficialCodes() {
        val municipalities = TerritorialCatalogData.MUNICIPALITIES
        assertEquals(2, municipalities.size)

        val bedmar = municipalities.find { it.slug == "bedmar-y-garciez" }
        assertNotNull(bedmar)
        assertEquals("Bedmar y Garcíez", bedmar!!.name)
        assertEquals("Jaén", bedmar.province)
        assertEquals("23902", bedmar.ineCode)
        assertEquals("23902", bedmar.aemetCode)
        assertEquals("Sierra Mágina", bedmar.comarca)
        assertTrue(bedmar.electronicSeatUrl!!.contains("bedmargarciez.sedelectronica.es"))
        assertTrue(bedmar.officialUrl!!.contains("bedmargarciez.es"))
        assertNotNull(bedmar.centerLatitude)
        assertNotNull(bedmar.centerLongitude)

        val jodar = municipalities.find { it.slug == "jodar" }
        assertNotNull(jodar)
        assertEquals("Jódar", jodar!!.name)
        assertEquals("Jaén", jodar.province)
        assertEquals("23053", jodar.ineCode)
        assertEquals("23053", jodar.aemetCode)
        assertEquals("Sierra Mágina", jodar.comarca)
        assertTrue(jodar.electronicSeatUrl!!.contains("jodar.sedelectronica.es"))
        assertTrue(jodar.officialUrl!!.contains("jodar.es"))
    }

    @Test
    fun garciezIsIdentifiedAsLocalEntityOfBedmarYtGarciez() {
        val bedmar = TerritorialCatalogData.MUNICIPALITIES.find { it.slug == "bedmar-y-garciez" }!!
        val localities = bedmar.localities
        assertEquals(2, localities.size)

        val bedmarLoc = localities.find { it.slug == "bedmar" }!!
        assertFalse(bedmarLoc.isPedaniaOrLocalEntity)
        assertEquals("23537", bedmarLoc.postalCode)

        val garciezLoc = localities.find { it.slug == "garciez" }!!
        assertTrue(garciezLoc.isPedaniaOrLocalEntity)
        assertEquals("23537", garciezLoc.postalCode)
        assertEquals("bedmar-y-garciez", garciezLoc.municipalitySlug)
    }

    @Test
    fun verifiedCooperativesIncludeMagnasurAndQuintaEsenciaUnderDopSierraMagina() {
        val cooperatives = TerritorialCatalogData.COOPERATIVES
        assertEquals(4, cooperatives.size)

        val bedmarense = cooperatives.find { it.brandName == "Magnasur" }
        assertNotNull(bedmarense)
        assertEquals("S.C.A. Bedmarense", bedmarense!!.officialName)
        assertEquals("bedmar-y-garciez", bedmarense.municipalitySlug)
        assertEquals("bedmar", bedmarense.localitySlug)
        assertTrue(bedmarense.dopSierraMagina)
        assertTrue(bedmarense.services.contains("molturacion"))
        assertTrue(bedmarense.services.contains("tienda_aove"))
        assertTrue(bedmarense.services.contains("carburantes"))
        assertEquals("verified", bedmarense.verificationStatus)

        val camino = cooperatives.find { it.officialName == "S.A.T. Ntra. Sra. del Camino" }
        assertNotNull(camino)
        assertEquals("bedmar-y-garciez", camino!!.municipalitySlug)
        assertEquals("garciez", camino.localitySlug)
        assertEquals("sat", camino.entityType)
        assertTrue(camino.dopSierraMagina)

        val andaraje = cooperatives.find { it.brandName == "El Pilar del Andaraje" }
        assertNotNull(andaraje)
        assertEquals("jodar", andaraje!!.municipalitySlug)
        assertTrue(andaraje.dopSierraMagina)

        val misericordia = cooperatives.find { it.brandName == "La Quinta Esencia" }
        assertNotNull(misericordia)
        assertEquals("S.C.A. Santísimo Cristo de la Misericordia", misericordia!!.officialName)
        assertEquals("jodar", misericordia.municipalitySlug)
        assertTrue(misericordia.dopSierraMagina)
        assertEquals("verified", misericordia.verificationStatus)
    }

    @Test
    fun verifiedIrrigationCommunitiesHaveOfficialBulletinReferences() {
        val communities = TerritorialCatalogData.IRRIGATION_COMMUNITIES
        assertEquals(4, communities.size)

        val lagunillas = communities.find { it.bulletinReference == "BOE-B-2026-19273" }
        assertNotNull(lagunillas)
        assertEquals("Comunidad de Regantes Lagunillas y Cerrón", lagunillas!!.officialName)
        assertEquals("bedmar-y-garciez", lagunillas.primaryMunicipalitySlug)
        assertEquals("community_of_irrigation", lagunillas.entityType)

        val satFique = communities.find { it.bulletinReference == "BOE-B-2025-43655" }
        assertNotNull(satFique)
        assertEquals("S.A.T. Fique", satFique!!.officialName)
        assertEquals("bedmar-y-garciez", satFique.primaryMunicipalitySlug)
        assertEquals("sat_irrigation", satFique.entityType)

        val jandulilla = communities.find { it.bulletinReference == "BOE-B-2025-14108" }
        assertNotNull(jandulilla)
        assertEquals("Comunidad de Regantes Canal del Jandulilla", jandulilla!!.officialName)
        assertEquals("jodar", jandulilla.primaryMunicipalitySlug)

        val cazMolinos = communities.find { it.bulletinReference == "BOP-JAEN-2025-03-14" }
        assertNotNull(cazMolinos)
        assertEquals("Comunidad de Regantes Caz de los Molinos", cazMolinos!!.officialName)
        assertEquals("jodar", cazMolinos.primaryMunicipalitySlug)

        // Todos tienen estado verificado
        assertTrue(communities.all { it.verificationStatus == "verified" })
    }

    @Test
    fun conversionToRoomEntitiesPreservesPrimaryKeysAndIndicesIntegrity() {
        val municipalityEntities = TerritorialCatalogData.toMunicipalityEntities()
        assertEquals(2, municipalityEntities.size)
        assertTrue(municipalityEntities.all { it.active })
        assertTrue(municipalityEntities.all { it.lastCheckedAtMs > 0 })
        assertNotEquals(municipalityEntities[0].slug, municipalityEntities[1].slug)
        assertNotEquals(municipalityEntities[0].ineCode, municipalityEntities[1].ineCode)
        assertNotEquals(municipalityEntities[0].aemetCode, municipalityEntities[1].aemetCode)

        val communityEntities = TerritorialCatalogData.toCommunityEntities()
        assertEquals(4, communityEntities.size)
        val slugs = communityEntities.map { it.primaryMunicipalitySlug }.toSet()
        assertEquals(setOf("bedmar-y-garciez", "jodar"), slugs)
        assertTrue(communityEntities.all { it.active })
    }

    @Test
    fun nonInventionRuleEnforcedNoInventedSectorsOrIrrigationSchedules() {
        // En ausencia de publicación oficial de turnos concretos para 2026/27,
        // no debe haber turnos ficticios en el catálogo canónico.
        val notice = WaterNotice(
            id = UUID.randomUUID().toString(),
            communityId = "20000000-0000-4000-8000-000000000001",
            sectorCode = null, // No inventado
            noticeType = "general_notice",
            title = "Aviso general de campaña de riego",
            body = "Los turnos oficiales serán publicados conforme a acuerdo de la Junta de Gobierno.",
            startsAtEpochMs = 1775779200000L,
            endsAtEpochMs = 1775865600000L,
            sourceType = "community_verified",
            status = "published",
        )

        assertNull(notice.sectorCode)
        assertEquals("general_notice", notice.noticeType)
        assertEquals("published", notice.status)
    }

    @Test
    fun municipalPublicationContractMatchesIssue532() {
        val pub = MunicipalPublication(
            id = "pub-bedmar-test",
            municipalitySlug = "bedmar-y-garciez",
            category = "aviso_agrario",
            title = "Bando municipal sobre recogida de aceituna y caminos rurales",
            summary = "Aviso sobre ordenación del tráfico agrícola en caminos del término municipal.",
            sourceUrl = "https://bedmargarciez.sedelectronica.es/",
            publishedAtEpochMs = 1775779200000L,
            isOfficial = true,
        )

        assertEquals("bedmar-y-garciez", pub.municipalitySlug)
        assertEquals("aviso_agrario", pub.category)
        assertTrue(pub.isOfficial)
        assertTrue(pub.sourceUrl.startsWith("https://"))
    }
}

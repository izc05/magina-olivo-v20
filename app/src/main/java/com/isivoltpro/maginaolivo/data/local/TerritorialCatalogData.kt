package com.isivoltpro.maginaolivo.data.local

import com.isivoltpro.maginaolivo.data.local.entity.IrrigationCommunityEntity
import com.isivoltpro.maginaolivo.data.local.entity.TerritorialMunicipalityEntity
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialCooperativeInfo
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialIrrigationCommunityInfo
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialLocality
import com.isivoltpro.maginaolivo.domain.territorial.TerritorialMunicipality

/**
 * Catálogo territorial verificado con fuentes oficiales para la comarca de Sierra Mágina.
 * Piloto inicial: Bedmar, Garcíez y Jódar.
 *
 * Fuentes oficiales:
 * - Instituto Nacional de Estadística (INE)
 * - Agencia Estatal de Meteorología (AEMET)
 * - Boletín Oficial del Estado (BOE)
 * - Boletín Oficial de la Provincia de Jaén (BOP)
 * - Boletín Oficial de la Junta de Andalucía (BOJA)
 * - Registro de Sociedades Cooperativas Andaluzas
 * - Consejo Regulador D.O.P. Sierra Mágina
 */
object TerritorialCatalogData {

    val MUNICIPALITIES: List<TerritorialMunicipality> = listOf(
        TerritorialMunicipality(
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
            sourceUrl = "https://www.aemet.es/es/eltiempo/prediccion/municipios/bedmar-y-garciez-id23902",
            localities = listOf(
                TerritorialLocality(
                    slug = "bedmar",
                    name = "Bedmar",
                    municipalitySlug = "bedmar-y-garciez",
                    isPedaniaOrLocalEntity = false,
                    postalCode = "23537",
                ),
                TerritorialLocality(
                    slug = "garciez",
                    name = "Garcíez",
                    municipalitySlug = "bedmar-y-garciez",
                    isPedaniaOrLocalEntity = true,
                    postalCode = "23537",
                ),
            ),
        ),
        TerritorialMunicipality(
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
            sourceUrl = "https://www.aemet.es/es/eltiempo/prediccion/municipios/jodar-id23053",
            localities = listOf(
                TerritorialLocality(
                    slug = "jodar",
                    name = "Jódar",
                    municipalitySlug = "jodar",
                    isPedaniaOrLocalEntity = false,
                    postalCode = "23540",
                ),
            ),
        ),
    )

    val COOPERATIVES: List<TerritorialCooperativeInfo> = listOf(
        TerritorialCooperativeInfo(
            id = "10000000-0000-4000-8000-000000000001",
            officialName = "S.C.A. Bedmarense",
            brandName = "Magnasur",
            municipalitySlug = "bedmar-y-garciez",
            localitySlug = "bedmar",
            entityType = "cooperative_sca",
            dopSierraMagina = true,
            mainBrands = listOf("Magnasur", "Oro de Cánava Selección"),
            services = listOf(
                "molturacion",
                "bodega",
                "envasado",
                "tienda_aove",
                "suministros_agricolas",
                "carburantes",
                "seccion_credito",
            ),
            address = "Ctra. Garcíez, s/n, 23537 Bedmar, Jaén",
            phone = "+34953760011",
            email = "info@magnasur.es",
            websiteUrl = "https://www.magnasur.es/",
            verificationStatus = "verified",
            sourceUrl = "https://www.magnasur.es/",
        ),
        TerritorialCooperativeInfo(
            id = "10000000-0000-4000-8000-000000000002",
            officialName = "S.A.T. Ntra. Sra. del Camino",
            brandName = "Ntra. Sra. del Camino",
            municipalitySlug = "bedmar-y-garciez",
            localitySlug = "garciez",
            entityType = "sat",
            dopSierraMagina = true,
            mainBrands = listOf("Ntra. Sra. del Camino"),
            services = listOf(
                "molturacion",
                "almacenamiento",
                "venta_aove",
            ),
            address = "Garcíez, 23537 Bedmar y Garcíez, Jaén",
            phone = null,
            email = null,
            websiteUrl = null,
            verificationStatus = "verified",
            sourceUrl = "https://www.boe.es/diario_boe/xml.php?id=BOE-B-2025-43655",
        ),
        TerritorialCooperativeInfo(
            id = "10000000-0000-4000-8000-000000000003",
            officialName = "S.C.A. El Pilar del Andaraje",
            brandName = "El Pilar del Andaraje",
            municipalitySlug = "jodar",
            localitySlug = "jodar",
            entityType = "cooperative_sca",
            dopSierraMagina = true,
            mainBrands = listOf("El Pilar del Andaraje"),
            services = listOf(
                "molturacion",
                "bodega",
                "tienda_aove",
                "suministros",
            ),
            address = "Ctra. Cabra del Santo Cristo, km 1, 23540 Jódar, Jaén",
            phone = "+34953785106",
            email = "cooperativa@elpilar.es",
            websiteUrl = "https://elpilar.es/",
            verificationStatus = "verified",
            sourceUrl = "https://elpilar.es/",
        ),
        TerritorialCooperativeInfo(
            id = "10000000-0000-4000-8000-000000000004",
            officialName = "S.C.A. Santísimo Cristo de la Misericordia",
            brandName = "La Quinta Esencia",
            municipalitySlug = "jodar",
            localitySlug = "jodar",
            entityType = "cooperative_sca",
            dopSierraMagina = true,
            mainBrands = listOf("La Quinta Esencia"),
            services = listOf(
                "molturacion",
                "envasado_premium",
                "tienda_aove",
                "suministros_agricolas",
            ),
            address = "Calle Tras del Pilar, s/n, 23540 Jódar, Jaén",
            phone = "+34953785010",
            email = "info@laquintaesencia.com",
            websiteUrl = "https://laquintaesencia.com/",
            verificationStatus = "verified",
            sourceUrl = "https://laquintaesencia.com/",
        ),
    )

    val IRRIGATION_COMMUNITIES: List<TerritorialIrrigationCommunityInfo> = listOf(
        TerritorialIrrigationCommunityInfo(
            id = "20000000-0000-4000-8000-000000000001",
            officialName = "Comunidad de Regantes Canal del Jandulilla",
            shortName = "Canal del Jandulilla",
            primaryMunicipalitySlug = "jodar",
            entityType = "community_of_irrigation",
            bulletinReference = "BOE-B-2025-14108",
            address = "Jódar, Jaén",
            phone = null,
            email = null,
            websiteUrl = null,
            verificationStatus = "verified",
            notes = "Regadío olivar cuenca del río Jandulilla. Convocatorias oficiales verificadas en BOE.",
        ),
        TerritorialIrrigationCommunityInfo(
            id = "20000000-0000-4000-8000-000000000002",
            officialName = "Comunidad de Regantes Lagunillas y Cerrón",
            shortName = "Lagunillas y Cerrón",
            primaryMunicipalitySlug = "bedmar-y-garciez",
            entityType = "community_of_irrigation",
            bulletinReference = "BOE-B-2026-19273",
            address = "Bedmar, Jaén",
            phone = null,
            email = null,
            websiteUrl = null,
            verificationStatus = "verified",
            notes = "Zona regable parajes Lagunillas y Cerrón en Bedmar. Convocatorias verificadas en BOE.",
        ),
        TerritorialIrrigationCommunityInfo(
            id = "20000000-0000-4000-8000-000000000003",
            officialName = "S.A.T. Fique",
            shortName = "SAT Fique (Riego)",
            primaryMunicipalitySlug = "bedmar-y-garciez",
            entityType = "sat_irrigation",
            bulletinReference = "BOE-B-2025-43655",
            address = "Garcíez, Bedmar y Garcíez, Jaén",
            phone = null,
            email = null,
            websiteUrl = null,
            verificationStatus = "verified",
            notes = "Sociedad Agraria de Transformación para riego olivarero en término de Garcíez/Bedmar.",
        ),
        TerritorialIrrigationCommunityInfo(
            id = "20000000-0000-4000-8000-000000000004",
            officialName = "Comunidad de Regantes Caz de los Molinos",
            shortName = "Caz de los Molinos",
            primaryMunicipalitySlug = "jodar",
            entityType = "community_of_irrigation",
            bulletinReference = "BOP-JAEN-2025-03-14",
            address = "Jódar, Jaén",
            phone = null,
            email = null,
            websiteUrl = null,
            verificationStatus = "verified",
            notes = "Regadío tradicional histórico de Jódar documentado en Boletín Oficial de Jaén.",
        ),
    )

    fun toMunicipalityEntities(): List<TerritorialMunicipalityEntity> =
        MUNICIPALITIES.map { m ->
            TerritorialMunicipalityEntity(
                slug = m.slug,
                name = m.name,
                province = m.province,
                ineCode = m.ineCode,
                aemetCode = m.aemetCode,
                comarca = m.comarca,
                officialUrl = m.officialUrl,
                electronicSeatUrl = m.electronicSeatUrl,
                centerLatitude = m.centerLatitude,
                centerLongitude = m.centerLongitude,
                sourceUrl = m.sourceUrl,
                lastCheckedAtMs = System.currentTimeMillis(),
                active = true,
            )
        }

    fun toCommunityEntities(): List<IrrigationCommunityEntity> =
        IRRIGATION_COMMUNITIES.map { c ->
            IrrigationCommunityEntity(
                id = c.id,
                officialName = c.officialName,
                shortName = c.shortName,
                entityType = c.entityType,
                primaryMunicipalitySlug = c.primaryMunicipalitySlug,
                address = c.address,
                phone = c.phone,
                email = c.email,
                websiteUrl = c.websiteUrl,
                electronicSeatUrl = c.electronicSeatUrl,
                verificationStatus = c.verificationStatus,
                sourceBulletinRef = c.bulletinReference,
                lastCheckedAtMs = System.currentTimeMillis(),
                active = true,
            )
        }
}

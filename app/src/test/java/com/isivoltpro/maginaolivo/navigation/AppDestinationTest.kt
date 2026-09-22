package com.isivoltpro.maginaolivo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    @Test
    fun rootDestinationsFollowFrozenOrder() {
        assertEquals(
            listOf("Inicio", "Mi Olivar", "Registrar", "Calendario", "Perfil"),
            RootDestination.entries.map { it.label },
        )
    }

    @Test
    fun nestedRoutesKeepTheirOwningRootSelected() {
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("farm/demo-farm"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("parcel/demo-parcel"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("campaign/demo-campaign"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute(AppDestination.MapCatastro))
        assertEquals(RootDestination.Register, AppDestination.rootForRoute(AppDestination.OcrReview))
        assertEquals(RootDestination.Profile, AppDestination.rootForRoute(AppDestination.DeveloperGallery))
    }

    @Test
    fun unknownOrOnboardingRoutesDoNotSelectMainRoot() {
        assertNull(AppDestination.rootForRoute(AppDestination.Onboarding))
        assertNull(AppDestination.rootForRoute("unknown"))
        assertNull(AppDestination.rootForRoute(null))
    }

    @Test
    fun nestedRouteBuildersRejectBlankIdentifiers() {
        assertEquals("farm/farm-1", AppDestination.farm("farm-1"))
        assertEquals("parcel/parcel-1", AppDestination.parcel("parcel-1"))
        assertEquals("campaign/campaign-1", AppDestination.campaign("campaign-1"))

        listOf(
            { AppDestination.farm(" ") },
            { AppDestination.parcel("") },
            { AppDestination.campaign("\t") },
        ).forEach { builder ->
            runCatching(builder).onSuccess {
                throw AssertionError("Blank identifiers must not produce a route")
            }
        }
    }
}

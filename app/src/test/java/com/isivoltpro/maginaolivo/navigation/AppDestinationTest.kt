package com.isivoltpro.maginaolivo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDestinationTest {
    @Test
    fun rootDestinationsFollowFrozenOrder() {
        assertEquals(
            listOf("Inicio", "Mi Campo", "Cuaderno", "Avisos", "Perfil"),
            RootDestination.entries.map { it.label },
        )
    }

    @Test
    fun nestedRoutesKeepTheirOwningRootSelected() {
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("farm/demo-farm"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("parcel/demo-parcel"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute("campaign/demo-campaign"))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute(AppDestination.MapCatastro))
        // UX-B (Issue #246): registering lives under Cuaderno; the agenda under Avisos.
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.OcrReview))
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.Register))
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.Harvest))
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute("delivery/demo"))
        assertEquals(RootDestination.Alerts, AppDestination.rootForRoute(AppDestination.Calendar))
        // UX-D: the register flow with a preset type stays under Cuaderno.
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.RegisterPattern))
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.register("PHYTOSANITARY")))
        assertEquals(RootDestination.Notebook, RootDestination.entries.single { it.isPrimaryAction })
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

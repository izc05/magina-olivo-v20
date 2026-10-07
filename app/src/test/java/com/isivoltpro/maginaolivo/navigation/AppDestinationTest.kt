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
        // CR-011 §12: Avisos → «Planificar trabajo» reuses the flow in plan mode.
        assertEquals(AppDestination.PlanWork, AppDestination.planWork())
        assertEquals(RootDestination.Alerts, AppDestination.rootForRoute(AppDestination.PlanWork))
        assertEquals("register?type={type}", AppDestination.RegisterPattern)
        // CR-011: Cuaderno → Jornal opens today's day under Cuaderno, on its own route.
        assertEquals("harvest/today/{farmId}", AppDestination.TodayHarvestPattern)
        // CR-011 §14: Pesada and Gasto carry the Cuaderno's Parcel as an optional argument.
        assertEquals("deliveries/new/{farmId}?parcelId={parcelId}", AppDestination.NewPesadaPattern)
        assertEquals("expenses/farm/{farmId}?parcelId={parcelId}&campaignId={campaignId}&activityId={activityId}&quick={quick}", AppDestination.FarmExpensesPattern)
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.TodayHarvestPattern))
        assertEquals(RootDestination.Notebook, RootDestination.entries.single { it.isPrimaryAction })
        assertEquals(RootDestination.Profile, AppDestination.rootForRoute(AppDestination.DeveloperGallery))
        // Phase 20B-radar: the radar belongs to Inicio.
        assertEquals(RootDestination.Home, AppDestination.rootForRoute(AppDestination.Radar))
        assertEquals(RootDestination.Home, AppDestination.rootForRoute(AppDestination.OilMarket))
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
        assertEquals("campaign/campaign-1/harvests", AppDestination.campaignHarvests("campaign-1"))
        assertEquals("campaign/{campaignId}/harvests", AppDestination.CampaignHarvestsPattern)
        assertEquals("campaign/campaign-1/deliveries", AppDestination.campaignDeliveries("campaign-1"))
        assertEquals("campaign/{campaignId}/deliveries", AppDestination.CampaignDeliveriesPattern)
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute(AppDestination.campaignHarvests("campaign-1")))
        assertEquals(RootDestination.Olivar, AppDestination.rootForRoute(AppDestination.campaignDeliveries("campaign-1")))

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

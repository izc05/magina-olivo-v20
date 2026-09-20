package com.isivoltpro.maginaolivo.navigation

enum class RootDestination(
    val route: String,
    val label: String,
    val symbol: String,
    val isPrimaryAction: Boolean = false,
) {
    Home(
        route = "home",
        label = "Inicio",
        symbol = "⌂",
    ),
    Olivar(
        route = "olivar",
        label = "Mi Olivar",
        symbol = "♧",
    ),
    Register(
        route = "register",
        label = "Registrar",
        symbol = "+",
        isPrimaryAction = true,
    ),
    Calendar(
        route = "calendar",
        label = "Calendario",
        symbol = "▦",
    ),
    Profile(
        route = "profile",
        label = "Perfil",
        symbol = "○",
    ),
}

object AppDestination {
    const val Onboarding = "onboarding"
    const val MapCatastro = "map-catastro"
    const val Weather = "weather"
    const val Analytics = "analytics"
    const val OcrReview = "ocr-review"
    const val Harvest = "harvest"
    const val Expenses = "expenses"
    const val DeveloperGallery = "developer-gallery"

    const val FarmPattern = "farm/{farmId}"
    const val ParcelPattern = "parcel/{parcelId}/{farmId}"
    const val CampaignPattern = "campaign/{campaignId}"

    fun farm(farmId: String): String = nestedRoute("farm", farmId)

    fun parcel(parcelId: String, farmId: String): String =
        "${nestedRoute("parcel", parcelId)}/${safeIdentifier(farmId)}"

    fun campaign(campaignId: String): String = nestedRoute("campaign", campaignId)

    fun rootForRoute(route: String?): RootDestination? {
        val prefix = route?.substringBefore('/') ?: return null
        return when (prefix) {
            RootDestination.Home.route, Weather -> RootDestination.Home
            RootDestination.Olivar.route,
            "farm",
            "parcel",
            "campaign",
            MapCatastro,
            Analytics,
            -> RootDestination.Olivar
            RootDestination.Register.route, OcrReview, Harvest, Expenses -> RootDestination.Register
            RootDestination.Calendar.route -> RootDestination.Calendar
            RootDestination.Profile.route, DeveloperGallery -> RootDestination.Profile
            else -> null
        }
    }

    private fun nestedRoute(prefix: String, identifier: String): String {
        return "$prefix/${safeIdentifier(identifier)}"
    }

    private fun safeIdentifier(identifier: String): String = identifier.trim().also {
        require(it.isNotEmpty()) { "A navigation identifier cannot be blank" }
        require('/' !in it) { "A navigation identifier cannot contain '/'" }
    }
}

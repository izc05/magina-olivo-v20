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
    const val Organizations = "organizations"
    const val Deliveries = "deliveries"
    const val Machinery = "machinery"

    const val FarmPattern = "farm/{farmId}"
    const val ParcelPattern = "parcel/{parcelId}"
    const val CampaignPattern = "campaign/{campaignId}"
    const val ActivityPattern = "activity/{activityId}"
    const val ExpensePattern = "expense/{expenseId}"
    const val DocumentPattern = "document/{extractionId}"
    const val HarvestPattern = "harvest/{harvestId}"
    const val DeliveryPattern = "delivery/{deliveryId}"
    const val JornadaPesadaPattern = "deliveries/jornada/{harvestId}"
    const val TicketPattern = "delivery-ticket/{extractionId}"
    const val MachinePattern = "machine/{machineId}"
    const val FarmMapPattern = "farm-map/{farmId}"
    const val FarmMapLocatePattern = "farm-map/{farmId}/locate/{parcelId}"
    const val CatastroPattern = "map-catastro/{farmId}"

    fun farm(farmId: String): String = nestedRoute("farm", farmId)
    fun farmMap(farmId: String): String = nestedRoute("farm-map", farmId)
    fun farmMapLocate(farmId: String, parcelId: String): String = "${farmMap(farmId)}/locate/${android.net.Uri.encode(parcelId)}"

    /** Design v3: a Farm section on its own screen, e.g. `farm-parcels/{farmId}`. */
    fun farmSection(prefix: String, farmId: String): String = nestedRoute(prefix, farmId)

    fun farmSectionPattern(prefix: String): String = "$prefix/{farmId}"

    fun parcel(parcelId: String): String = nestedRoute("parcel", parcelId)

    fun campaign(campaignId: String): String = nestedRoute("campaign", campaignId)

    fun activity(activityId: String): String = nestedRoute("activity", activityId)

    fun expense(expenseId: String): String = nestedRoute("expense", expenseId)

    fun document(extractionId: String): String = nestedRoute("document", extractionId)

    fun harvest(harvestId: String): String = nestedRoute(Harvest, harvestId)

    fun delivery(deliveryId: String): String = nestedRoute("delivery", deliveryId)

    /** Phase 19B: the Pesada form opened on one Jornada. */
    fun jornadaPesada(harvestId: String): String = "$Deliveries/jornada/${android.net.Uri.encode(harvestId)}"

    fun ticket(extractionId: String): String = nestedRoute("delivery-ticket", extractionId)

    fun machine(machineId: String): String = nestedRoute("machine", machineId)

    fun catastro(farmId: String): String = nestedRoute(MapCatastro, farmId)

    fun rootForRoute(route: String?): RootDestination? {
        val prefix = route?.substringBefore('/') ?: return null
        return when (prefix) {
            RootDestination.Home.route, Weather -> RootDestination.Home
            RootDestination.Olivar.route,
            "farm",
            "farm-parcels",
            "farm-campaigns",
            "farm-notebook",
            "farm-activities",
            "farm-documents",
            "farm-map",
            "parcel",
            "campaign",
            "activity",
            MapCatastro,
            Analytics,
            Machinery,
            "machine",
            -> RootDestination.Olivar
            RootDestination.Register.route,
            OcrReview,
            Harvest,
            Expenses,
            Organizations,
            Deliveries,
            "delivery",
            "delivery-ticket",
            "expense",
            "document",
            -> RootDestination.Register
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

package com.isivoltpro.maginaolivo.navigation

/**
 * The five roots of the bottom bar. UX-B (Issue #246, CR-007): Inicio · Mi Campo · Cuaderno ·
 * Avisos · Perfil. Cuaderno is the centre (daily register); the old `register` and `calendar`
 * routes stay as nested routes under Cuaderno and Avisos, so nothing that opened them breaks.
 */
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
        label = "Mi Campo",
        symbol = "♧",
    ),
    Notebook(
        route = "cuaderno",
        label = "Cuaderno",
        symbol = "▤",
        isPrimaryAction = true,
    ),
    Alerts(
        route = "avisos",
        label = "Avisos",
        symbol = "!",
    ),
    Profile(
        route = "profile",
        label = "Perfil",
        symbol = "○",
    ),
}

object AppDestination {
    const val Onboarding = "onboarding"
    /** Activity register flow (was the `+` root; now opened from Cuaderno → Registrar hoy). */
    const val Register = "register"
    /** «Registrar trabajo»: the register flow with the type chosen in the Cuaderno (optional). */
    const val RegisterPattern = "register?type={type}"
    /**
     * CR-011 §12: Avisos → «Planificar trabajo». The same flow in plan mode (saved as planned),
     * on its own route so the bottom bar stays on Avisos.
     */
    const val PlanWork = "plan-work"
    /** The agenda (was the Calendario root; now under Avisos). */
    const val Calendar = "calendar"
    const val MapCatastro = "map-catastro"
    const val Weather = "weather"
    /** Phase 20B-radar: rain radar over the active Farm, nested under Inicio. */
    const val Radar = "radar"
    /** Phase 20D-3: the oil market (daily pulse + 12 official weeks), nested under Inicio. */
    const val OilMarket = "oil-market"
    const val Analytics = "analytics"
    const val OcrReview = "ocr-review"
    const val Harvest = "harvest"
    const val Expenses = "expenses"
    const val DeveloperGallery = "developer-gallery"
    /** Phase 21C: Perfil → Ayuda y privacidad («news», «privacy», «offline»). */
    const val HelpPattern = "help/{topic}"
    const val Organizations = "organizations"
    const val Deliveries = "deliveries"
    const val Machinery = "machinery"
    const val AgronomicPeople = "agronomic-people"
    const val AgronomicPersonPattern = "agronomic-person/{personId}"

    const val FarmPattern = "farm/{farmId}"
    const val ParcelPattern = "parcel/{parcelId}"
    const val CampaignPattern = "campaign/{campaignId}"
    /** #408: Días de recolección scoped to the campaign the user came from. */
    const val CampaignHarvestsPattern = "campaign/{campaignId}/harvests"
    /** #511: Pesadas scoped to the Campaign being consulted, including closed history. */
    const val CampaignDeliveriesPattern = "campaign/{campaignId}/deliveries"
    const val ActivityPattern = "activity/{activityId}"
    const val ExpensePattern = "expense/{expenseId}"
    /** CR-012 P1: a document taken on a Farm/Campaign screen is reviewed with that context. */
    const val DocumentPattern = "document/{extractionId}?farmId={farmId}&campaignId={campaignId}&recollection={recollection}"
    const val HarvestPattern = "harvest/{harvestId}"
    /** CR-011: Cuaderno → Jornal, today's día de recolección of a Farm (found or created). */
    const val TodayHarvestPattern = "harvest/today/{farmId}"
    const val DeliveryPattern = "delivery/{deliveryId}"
    const val JornadaPesadaPattern = "deliveries/jornada/{harvestId}"
    /** CR-011: «Cuaderno → Pesada», the form open on the Cuaderno's Farm. */
    const val NewPesadaPattern = "deliveries/new/{farmId}?parcelId={parcelId}"
    /** CR-011: «Cuaderno → Gasto», a new expense starting on the Cuaderno's Farm. */
    const val FarmExpensesPattern = "expenses/farm/{farmId}?parcelId={parcelId}&campaignId={campaignId}&activityId={activityId}&quick={quick}"
    /** #378: Cuaderno → Jornal outside a running campaign — the Farm's own labour. */
    const val FarmLabourPattern = "expenses/farm-labour/{farmId}?parcelId={parcelId}"
    const val PendingYieldsRoute = "deliveries/pending"
    const val DeliveryYieldPattern = "delivery/{deliveryId}/yield"
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

    /** #408: keep the chosen Campaign while browsing its recollection days. */
    fun campaignHarvests(campaignId: String): String = "${campaign(campaignId)}/harvests"

    /** #511: keep the chosen Campaign while browsing its Pesadas. */
    fun campaignDeliveries(campaignId: String): String = "${campaign(campaignId)}/deliveries"

    fun activity(activityId: String): String = nestedRoute("activity", activityId)

    fun expense(expenseId: String): String = nestedRoute("expense", expenseId)

    fun document(extractionId: String): String = nestedRoute("document", extractionId)

    /**
     * The review of a document just taken on an expense screen: its Farm, its Campaign
     * («Gastos de recogida») or, from Cuaderno → Gasto, the «Gasto de recogida» preselection.
     */
    fun documentInContext(extractionId: String, farmId: String?, campaignId: String?, preselectRecollection: Boolean): String {
        val query = listOfNotNull(
            farmId?.let { "farmId=${android.net.Uri.encode(it)}" },
            campaignId?.let { "campaignId=${android.net.Uri.encode(it)}" },
            "recollection=true".takeIf { preselectRecollection },
        )
        return document(extractionId) + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
    }

    fun harvest(harvestId: String): String = nestedRoute(Harvest, harvestId)

    fun todayHarvest(farmId: String): String = "$Harvest/today/${android.net.Uri.encode(farmId)}"

    fun delivery(deliveryId: String): String = nestedRoute("delivery", deliveryId)

    /** Phase 19C: a Pesada opened on its yield form. */
    fun deliveryYield(deliveryId: String): String = "${delivery(deliveryId)}/yield"

    /** Phase 19B: the Pesada form opened on one Jornada. */
    fun jornadaPesada(harvestId: String): String = "$Deliveries/jornada/${android.net.Uri.encode(harvestId)}"

    fun newPesada(farmId: String, parcelId: String? = null): String =
        "$Deliveries/new/${android.net.Uri.encode(farmId)}" + parcelQuery(parcelId)

    fun help(topic: String): String = nestedRoute("help", topic)

    fun farmExpenses(
        farmId: String,
        parcelId: String? = null,
        campaignId: String? = null,
        activityId: String? = null,
        /** #415: Cuaderno → Gasto opens the form at once and returns to the Cuaderno. */
        quick: Boolean = false,
    ): String =
        "$Expenses/farm/${android.net.Uri.encode(farmId)}" + listOfNotNull(
            parcelId?.takeIf { it.isNotBlank() }?.let { "parcelId=${android.net.Uri.encode(it)}" },
            campaignId?.takeIf { it.isNotBlank() }?.let { "campaignId=${android.net.Uri.encode(it)}" },
            // #416: «Añadir gasto relacionado» from a work.
            activityId?.takeIf { it.isNotBlank() }?.let { "activityId=${android.net.Uri.encode(it)}" },
            "quick=true".takeIf { quick },
        ).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }

    /** CR-011 §14: the Cuaderno's Parcel travels with Pesada and Gasto when there is one. */
    fun farmLabour(farmId: String, parcelId: String? = null): String =
        "$Expenses/farm-labour/${android.net.Uri.encode(farmId)}" + parcelQuery(parcelId)

    private fun parcelQuery(parcelId: String?): String =
        if (parcelId.isNullOrBlank()) "" else "?parcelId=${android.net.Uri.encode(parcelId)}"

    fun ticket(extractionId: String): String = nestedRoute("delivery-ticket", extractionId)

    fun machine(machineId: String): String = nestedRoute("machine", machineId)

    fun agronomicPerson(personId: String): String = nestedRoute("agronomic-person", personId)

    fun catastro(farmId: String): String = nestedRoute(MapCatastro, farmId)

    /** `register` or `register?type=IRRIGATION`. */
    fun register(type: String?): String = if (type.isNullOrBlank()) Register else "$Register?type=$type"

    /** CR-011 §12: Avisos → «Planificar trabajo». */
    fun planWork(): String = PlanWork

    fun rootForRoute(route: String?): RootDestination? {
        val prefix = route?.substringBefore('?')?.substringBefore('/') ?: return null
        return when (prefix) {
            RootDestination.Home.route, Weather, Radar, OilMarket -> RootDestination.Home
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
            RootDestination.Notebook.route,
            Register,
            OcrReview,
            Harvest,
            Expenses,
            Organizations,
            Deliveries,
            "delivery",
            "delivery-ticket",
            "expense",
            "document",
            -> RootDestination.Notebook
            RootDestination.Alerts.route, Calendar, PlanWork -> RootDestination.Alerts
            RootDestination.Profile.route, DeveloperGallery, "help", AgronomicPeople, "agronomic-person" -> RootDestination.Profile
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

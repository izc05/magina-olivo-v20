package com.isivoltpro.maginaolivo.feature.farms

/**
 * The parts of a Farm that open as their own screen from the Farm hub (design v3).
 * Phase 19A (CR-005): the hub shows Parcelas · Cuaderno · Campañas · Documentos. The work list
 * (`farm-activities`) keeps its route and is opened from the Cuaderno's Diario.
 *
 * CR-011 §3: [NOTEBOOK] is only the hub entry — it opens the one Cuaderno (the bottom-bar root)
 * on this Farm; there is no second, per-Farm Cuaderno screen.
 */
enum class FarmSection(val route: String, val title: String) {
    PARCELS("farm-parcels", "Parcelas"),
    NOTEBOOK("farm-notebook", "Cuaderno"),
    CAMPAIGNS("farm-campaigns", "Campañas"),
    ACTIVITIES("farm-activities", "Trabajos"),
    DOCUMENTS("farm-documents", "Documentos"),
}

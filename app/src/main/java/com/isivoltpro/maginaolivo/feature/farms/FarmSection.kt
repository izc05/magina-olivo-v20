package com.isivoltpro.maginaolivo.feature.farms

/** The parts of a Farm that open as their own screen from the Farm hub (design v3). */
enum class FarmSection(val route: String, val title: String) {
    PARCELS("farm-parcels", "Parcelas"),
    CAMPAIGNS("farm-campaigns", "Campañas"),
    ACTIVITIES("farm-activities", "Trabajos"),
    DOCUMENTS("farm-documents", "Documentos"),
}

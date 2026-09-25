package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoEarthText
import com.isivoltpro.maginaolivo.ui.theme.MoEarthTint
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoTint
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGoldText
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGoldTint
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText
import com.isivoltpro.maginaolivo.ui.theme.MoWarningTint

/**
 * UI polish v2 — one coherent family of outlined agricultural icons (DESIGN_SYSTEM §7),
 * drawn on a 24-unit grid with a medium round stroke. Coloured by [MoIconTone].
 */
object MoIcons {
    val Activity: ImageVector by lazy { line("activity", "M12 20v-7 M12 13C12 9 9.2 6.2 5 6.2c0 4 2.8 6.8 7 6.8z M12 15c0-3.6 2.6-6.2 6.8-6.2 0 3.6-2.6 6.2-6.8 6.2z M7 20h10") }
    val Harvest: ImageVector by lazy { line("harvest", "M4 10h16l-1.6 8.3A2 2 0 0 1 16.4 20H7.6a2 2 0 0 1-2-1.7z M8.5 10l3-5 M15.5 10l-3-5 M9.5 13.5v3 M14.5 13.5v3") }
    val Delivery: ImageVector by lazy { line("delivery", "M3 6h11v10H3z M14 9h4l3 3.5V16h-7 M7 19a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z M17 19a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z") }
    val Document: ImageVector by lazy { line("document", "M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M14 3v4h4 M9 12h6 M9 16h6") }
    val Calendar: ImageVector by lazy { line("calendar", "M5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-12A1.5 1.5 0 0 1 5.5 5z M4 10h16 M8 3v4 M16 3v4") }
    val ChevronLeft: ImageVector by lazy { line("chevron-left", "M15 5l-7 7 7 7") }
    val ChevronRight: ImageVector by lazy { line("chevron-right", "M9 5l7 7-7 7") }
    val Map: ImageVector by lazy { line("map", "M9 4L3 6.5V20l6-2.5 6 2.5 6-2.5V4l-6 2.5z M9 4v13.5 M15 6.5V20") }
    val Parcels: ImageVector by lazy { line("parcels", "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z") }
    val Campaign: ImageVector by lazy { line("campaign", "M5 21V4 M5 4h11l-2 4 2 4H5") }
    val Checklist: ImageVector by lazy { line("checklist", "M10 6h10 M10 12h10 M10 18h10 M4 6l1.2 1.2L7.5 5 M4 12l1.2 1.2L7.5 11 M4 18l1.2 1.2L7.5 17") }
    val Location: ImageVector by lazy { line("location", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z M12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z") }
    val Clock: ImageVector by lazy { line("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2") }
    val Bell: ImageVector by lazy { line("bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z M10 20.5a2 2 0 0 0 4 0") }
    val People: ImageVector by lazy { line("people", "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M3 20a6 6 0 0 1 12 0 M16 4.3a3.5 3.5 0 0 1 0 6.4 M18 14a6 6 0 0 1 3 6") }
    val Weight: ImageVector by lazy { line("weight", "M6 20h12l-1.6-11H7.6z M9.5 9a2.5 2.5 0 0 1 5 0") }
    val Euro: ImageVector by lazy { line("euro", "M17 6.5A6.5 6.5 0 1 0 17 17.5 M4 10.5h9 M4 13.5h9") }
    val Percent: ImageVector by lazy { line("percent", "M19 5L5 19 M7 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z") }
    val Area: ImageVector by lazy { line("area", "M4 8V4h4 M16 4h4v4 M20 16v4h-4 M8 20H4v-4 M8 8h8v8H8z") }
    val Home: ImageVector by lazy { line("home", "M4 11l8-7 8 7 M6 9.5V20h12V9.5 M10 20v-5h4v5") }
    val Person: ImageVector by lazy { line("person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 20a7.5 7.5 0 0 1 15 0") }
    val Plus: ImageVector by lazy { line("plus", "M12 5v14 M5 12h14") }
    // Design v3 (CR-004) additions, same grid and stroke.
    val Drop: ImageVector by lazy { line("drop", "M12 3.5s-6 6.6-6 10.9a6 6 0 0 0 12 0C18 10.1 12 3.5 12 3.5z M9.2 14.6a2.9 2.9 0 0 0 2.8 2.8") }
    val Leaf: ImageVector by lazy { line("leaf", "M5 19c0-8.5 5.5-14 15-14 0 9.5-5.5 15-14 15z M5 19l7.5-7.5") }
    val Olive: ImageVector by lazy { line("olive", "M9.5 20.5a4 5.2 20 1 1 0-.01z M15.5 16a3.6 4.6-20 1 1 0-.01z M11 11.5C11.5 7 13.5 4.5 17.5 3.5") }
    val History: ImageVector by lazy { line("history", "M4 12a8 8 0 1 0 2.4-5.7 M4 4.5v4h4 M12 8v4.5l3 1.8") }
    val Warning: ImageVector by lazy { line("warning", "M12 4l9 16H3z M12 10v4.5 M12 17.3v.2") }
    val Tree: ImageVector by lazy { line("tree", "M12 21v-6 M12 15c-4.4 0-7-2.4-7-5.5S8 4 12 4s7 2.4 7 5.5-2.6 5.5-7 5.5z M9 21h6") }
    val Tractor: ImageVector by lazy { line("tractor", "M7.5 20a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M4.5 14V6h5.5l1.5 6.5H19v4 M11 16.5h5 M16 12.5V9") }
    val Shears: ImageVector by lazy { line("shears", "M6.5 20.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M17.5 20.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M8.2 16.1L15.5 4 M15.8 16.1L8.5 4") }
    val Spray: ImageVector by lazy { line("spray", "M7 21h8v-9l-1.5-3h-5L7 12z M9 9V6h4v3 M13 7h3l1.5 1.5 M19.5 5v.1 M20.5 8.5v.1 M19.5 12v.1") }
    val Sack: ImageVector by lazy { line("sack", "M8.5 8c-2 3-3.5 6.5-3.5 9.5A3 3 0 0 0 8 20.5h8a3 3 0 0 0 3-3C19 14.5 17.5 11 15.5 8z M8.5 8L7.5 4.5h9L15.5 8 M9.5 14h5") }
    val Wrench: ImageVector by lazy { line("wrench", "M15.5 3.5a5 5 0 0 0-4.7 6.7L4 17a2.1 2.1 0 0 0 3 3l6.8-6.8a5 5 0 0 0 6.7-4.7l-3.2 1.2-2.5-2.5z") }
    val Weather: ImageVector by lazy { line("weather", "M8 3v1.5 M3.5 8H5 M4.8 4.8l1 1 M11.2 4.8l-1 1 M6.2 10.2A3 3 0 1 1 11 6.6 M8 20h9.5a3.5 3.5 0 0 0 .5-7 5 5 0 0 0-9.6-1A4 4 0 0 0 8 20z") }
    val Notebook: ImageVector by lazy { line("notebook", "M6 3h11a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M8 3v18 M11 8h4 M11 12h4") }

    private fun line(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = "mo-$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}

/**
 * Colour family of an icon, so the farmer recognises each kind of thing at a glance
 * (CR-004 addendum). Only palette tokens: the shape still carries the meaning and every
 * icon keeps its text label, so colour never communicates alone.
 */
enum class MoIconTone(val tint: Color, val container: Color) {
    /** Olivar and field work. */
    GROVE(MoOliveMid, MoOliveTint),
    /** Land and resources: parcels, surface, map, place, people, machinery. */
    LAND(MoEarthText, MoEarthTint),
    /** Water, planning and records: irrigation, calendar, reminders, documents. */
    WATER(MoInfoText, MoInfoTint),
    /** Value: harvest, deliveries, kg, yield, money. */
    VALUE(MoSoftGoldText, MoSoftGoldTint),
    /** Incidents and notices. */
    ALERT(MoWarningText, MoWarningTint),
    ;

    companion object {
        private val byName: Map<String, MoIconTone> by lazy {
            buildMap {
                listOf(MoIcons.Tree, MoIcons.Leaf, MoIcons.Olive, MoIcons.Activity, MoIcons.Checklist, MoIcons.Campaign, MoIcons.Home, MoIcons.Shears, MoIcons.Spray, MoIcons.Sack)
                    .forEach { put(it.name, GROVE) }
                listOf(MoIcons.Parcels, MoIcons.Area, MoIcons.Map, MoIcons.Location, MoIcons.People, MoIcons.Person, MoIcons.Tractor, MoIcons.Wrench)
                    .forEach { put(it.name, LAND) }
                listOf(MoIcons.Drop, MoIcons.Calendar, MoIcons.Clock, MoIcons.Bell, MoIcons.History, MoIcons.Document)
                    .forEach { put(it.name, WATER) }
                listOf(MoIcons.Harvest, MoIcons.Delivery, MoIcons.Weight, MoIcons.Percent, MoIcons.Euro)
                    .forEach { put(it.name, VALUE) }
                put(MoIcons.Warning.name, ALERT)
            }
        }

        /** Family of [icon]; icons outside the table (chevrons, plus) stay olive. */
        fun of(icon: ImageVector): MoIconTone = byName[icon.name] ?: GROVE
    }
}

package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #414: the quick forms show the essentials, hide the rest, and never lose an older record's data. */
class DetailLayoutTest {
    @Test fun aNewPodaAsksNoPeopleNorHours() {
        val poda = detailLayout(ActivityType.PRUNING)
        assertEquals(listOf(ActivityDetailFields.PRUNING_TYPE), poda.visible)
        assertFalse(ActivityDetailFields.WORKER_COUNT in poda.visible + poda.advanced)
        assertFalse(ActivityDetailFields.HOURS in poda.visible + poda.advanced)
    }

    @Test fun aNewTratamientoHasNoFreeEquipmentField() {
        val tratamiento = detailLayout(ActivityType.PHYTOSANITARY)
        assertEquals(
            listOf(ActivityDetailFields.PRODUCT_NAME, ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.DOSE_UNIT, ActivityDetailFields.REASON),
            tratamiento.visible,
        )
        assertFalse(ActivityDetailFields.EQUIPMENT_TEXT in tratamiento.visible + tratamiento.advanced)
    }

    @Test fun aNewRiegoKeepsTheTariffOutOfTheWay() {
        val riego = detailLayout(ActivityType.IRRIGATION)
        assertEquals(
            listOf(ActivityDetailFields.DURATION_MINUTES, ActivityDetailFields.VOLUME_M3, ActivityDetailFields.SECTOR_TEXT),
            riego.visible,
        )
        listOf(
            ActivityDetailFields.PRICE_BASIS, ActivityDetailFields.UNIT_PRICE,
            ActivityDetailFields.PRICED_QUANTITY, ActivityDetailFields.PRICE_DATE,
        ).forEach { assertTrue(it in riego.retired) }
    }

    /** Every field a typed record can hold has a place, so editing an older record loses nothing. */
    @Test fun everyStoredFieldStillHasAPlace() {
        val stored = mapOf(
            ActivityType.PRUNING to setOf(
                ActivityDetailFields.PRUNING_TYPE, ActivityDetailFields.WORKER_COUNT,
                ActivityDetailFields.HOURS, ActivityDetailFields.RESIDUE_MANAGEMENT,
            ),
            ActivityType.FERTILIZATION to setOf(
                ActivityDetailFields.PRODUCT_NAME, ActivityDetailFields.TOTAL_QUANTITY, ActivityDetailFields.UNIT,
                ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.DOSE_UNIT, ActivityDetailFields.APPLICATION_METHOD,
            ),
            ActivityType.PHYTOSANITARY to setOf(
                ActivityDetailFields.PRODUCT_NAME, ActivityDetailFields.ACTIVE_SUBSTANCE, ActivityDetailFields.TOTAL_QUANTITY,
                ActivityDetailFields.UNIT, ActivityDetailFields.DOSE_VALUE, ActivityDetailFields.DOSE_UNIT,
                ActivityDetailFields.REASON, ActivityDetailFields.EQUIPMENT_TEXT,
            ),
            ActivityType.SOIL_WORK to setOf(ActivityDetailFields.WORK_TYPE, ActivityDetailFields.METHOD),
            ActivityType.IRRIGATION to setOf(
                ActivityDetailFields.DURATION_MINUTES, ActivityDetailFields.VOLUME_M3, ActivityDetailFields.SECTOR_TEXT,
                ActivityDetailFields.SYSTEM_TEXT, ActivityDetailFields.PRICE_BASIS, ActivityDetailFields.UNIT_PRICE,
                ActivityDetailFields.PRICED_QUANTITY, ActivityDetailFields.PRICE_DATE,
            ),
            ActivityType.MAINTENANCE to setOf(ActivityDetailFields.MAINTENANCE_TYPE, ActivityDetailFields.ASSET_TEXT),
            ActivityType.INCIDENT to setOf(
                ActivityDetailFields.CATEGORY, ActivityDetailFields.SEVERITY,
                ActivityDetailFields.INCIDENT_STATE, ActivityDetailFields.ACTION_TAKEN,
            ),
        )
        stored.forEach { (type, keys) ->
            val layout = detailLayout(type)
            val placed = layout.visible + layout.advanced + layout.retired
            assertEquals("$type places each field once", placed.size, placed.toSet().size)
            assertEquals("$type keeps every stored field", keys, placed.toSet())
        }
    }
}

package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDetailDecimalsTest {
    private val detail = ActivityDetail.Phytosanitary(productName = "Cobre", totalQuantity = 12.5, unit = "L", doseValue = 0.5, doseUnit = "l/ha")

    @Test fun decimalsAreShownWithAComma() {
        val fields = detail.toFields()
        assertEquals("12,5", fields[ActivityDetailFields.TOTAL_QUANTITY])
        assertEquals("0,5", fields[ActivityDetailFields.DOSE_VALUE])
    }

    @Test fun theStoredNumberSurvivesTheComma() =
        assertEquals(detail, buildActivityDetail(ActivityType.PHYTOSANITARY, detail.toFields()))

    @Test fun aWholeNumberKeepsNoDecimals() =
        assertEquals("3", detail.copy(totalQuantity = 3.0).toFields()[ActivityDetailFields.TOTAL_QUANTITY])
}

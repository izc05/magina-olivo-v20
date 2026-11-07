package com.isivoltpro.maginaolivo.feature.farms

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FarmSetupScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun nameOnlyCreationAndThreeWaysToAddParcelsKeepFarmContext() {
        val workspace = UUID.randomUUID()
        val farmId = UUID.randomUUID()
        val parcelId = UUID.randomUUID()
        val farm = Farm(farmId, workspace, "Finca QA", null, null, null, null, null, 1, null, null, null, 1)
        val parcel = Parcel(parcelId, workspace, farmId, "Parcela QA", null, null, null, null, null,
            ParcelSource.MANUAL, null, null, null, null, null, 1)
        var createdName: String? = null
        var method: String? = null
        var chosenFarm: UUID? = null
        var chosenParcel: UUID? = null
        var later = false
        val state = androidx.compose.runtime.mutableStateOf(FarmSetupState(loading = false, workspaceId = workspace))
        rule.setContent {
            FarmSetupScreen(state.value,
                onCreate = { createdName = it; state.value = state.value.copy(farm = farm, parcels = listOf(parcel)) },
                onMap = { method = "map"; chosenFarm = it },
                onSearch = { method = "reference"; chosenFarm = it },
                onPolygonSearch = { method = "polygon"; chosenFarm = it },
                onGps = { method = "gps"; chosenFarm = it },
                onEditParcel = { chosenParcel = it },
                onLater = { later = true }, onFinish = {})
        }
        rule.onNodeWithTag("farm-setup-name").performTextInput("Finca QA")
        rule.onNodeWithTag("farm-setup-create").performClick()
        rule.runOnIdle { assertEquals("Finca QA", createdName) }
        rule.onNodeWithTag("farm-setup-summary").assertExists()
        rule.onNodeWithTag("farm-setup-map").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("map", method); assertEquals(farmId, chosenFarm) }
        rule.onNodeWithTag("farm-setup-search").performScrollTo().performClick()
        rule.onNodeWithTag("farm-setup-reference").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("reference", method); assertEquals(farmId, chosenFarm) }
        rule.onNodeWithTag("farm-setup-polygon").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("polygon", method); assertEquals(farmId, chosenFarm) }
        rule.onNodeWithTag("farm-setup-gps").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("gps", method); assertEquals(farmId, chosenFarm) }
        rule.onNodeWithTag("farm-setup-parcel-$parcelId").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(parcelId, chosenParcel) }
        rule.onNodeWithTag("farm-setup-later").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(later) }
    }
}

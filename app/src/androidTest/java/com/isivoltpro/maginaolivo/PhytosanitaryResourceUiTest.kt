package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPerson
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentInspection
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentProfile
import com.isivoltpro.maginaolivo.domain.phytosanitary.RegulatoryResourceSource
import com.isivoltpro.maginaolivo.feature.machinery.MachineDetailScreen
import com.isivoltpro.maginaolivo.feature.machinery.MachineDetailUiState
import com.isivoltpro.maginaolivo.feature.phytosanitary.AgronomicPeopleScreen
import com.isivoltpro.maginaolivo.feature.phytosanitary.AgronomicPeopleUiState
import com.isivoltpro.maginaolivo.feature.profile.ProfileScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhytosanitaryResourceUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun profileKeepsCueResourcesNestedUnderProfile() {
        var opened = false
        rule.setContent {
            MaginaOlivoTheme {
                ProfileScreen(
                    appVersion = "test",
                    notificationsOn = true,
                    onNotifications = {},
                    onMachinery = {},
                    onAgronomicPeople = { opened = true },
                )
            }
        }
        rule.onNodeWithTag("profile-agronomic-people").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test fun applicatorListMasksTheTaxId() {
        val person = AgronomicPerson(
            id = UUID.randomUUID(),
            displayName = "Juan Aplicador",
            taxId = "12345678Z",
            isAdvisor = true,
        )
        rule.setContent {
            MaginaOlivoTheme {
                AgronomicPeopleScreen(
                    state = AgronomicPeopleUiState(isLoading = false, active = listOf(person)),
                    onCreate = {},
                    onPersonSelected = {},
                )
            }
        }
        rule.onNodeWithTag("agronomic-person-row").assertIsDisplayed()
            .assertTextContains("Juan Aplicador", substring = true)
            .assertTextContains("••••678Z", substring = true)
    }

    @Test fun machineDetailShowsOfficialProfileAndInspectionHistoryWithoutAnotherMachine() {
        val machineId = UUID.randomUUID()
        val machine = Machine(machineId, "Atomizador principal", MachineCategory.ATOMIZER)
        val profile = PhytosanitaryEquipmentProfile(
            machineId = machineId,
            romaRegistration = "ROMA-JA-001",
            censusReference = "CENSO-42",
            acquisitionDate = LocalDate.parse("2024-02-01"),
            source = RegulatoryResourceSource.REAFA,
            externalId = "rea-machine-1",
            sourceVersion = "2026-10",
            fetchedAt = Instant.parse("2026-10-07T12:00:00Z"),
        )
        val inspection = PhytosanitaryEquipmentInspection(
            id = UUID.randomUUID(),
            machineId = machineId,
            inspectionDate = LocalDate.parse("2026-03-01"),
            resultCode = "PASS",
            certificateReference = "CERT-2026",
        )
        rule.setContent {
            MaginaOlivoTheme {
                MachineDetailScreen(
                    state = MachineDetailUiState(
                        isLoading = false,
                        machine = machine,
                        phytosanitaryProfile = profile,
                        phytosanitaryInspections = listOf(inspection),
                    ),
                    onUpdate = {},
                    onArchive = {},
                    onRestore = {},
                )
            }
        }
        rule.onNodeWithTag("machine-phyto-source").performScrollTo().assertTextContains("REAFA")
        rule.onNodeWithTag("machine-phyto-inspection").performScrollTo()
            .assertTextContains("CERT-2026", substring = true)
        rule.onNodeWithTag("machine-phyto-edit").assertDoesNotExist()
    }
}

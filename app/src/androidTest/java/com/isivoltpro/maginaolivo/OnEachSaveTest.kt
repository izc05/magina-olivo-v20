package com.isivoltpro.maginaolivo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #380: an editor closes after every save, not only after the first one with a given message. */
class OnEachSaveTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun everyRiseRunsOnceAndALowerCountOnlyRebases() {
        var saveCount by mutableIntStateOf(0)
        var closed = 0
        composeRule.setContent { OnEachSave(saveCount) { closed++ } }
        composeRule.waitForIdle()
        assertEquals("Opening the screen is not a save", 0, closed)

        saveCount = 1
        composeRule.waitForIdle()
        saveCount = 2
        composeRule.waitForIdle()
        assertEquals("A second save closes the editor again", 2, closed)

        // A new ViewModel (process death) starts from 0: the restored editor stays open.
        saveCount = 0
        composeRule.waitForIdle()
        assertEquals(2, closed)
        saveCount = 1
        composeRule.waitForIdle()
        assertEquals("The first save on the new ViewModel still closes it", 3, closed)
    }

    @Test
    fun restoredScreenDoesNotCloseForSavesItAlreadySaw() {
        var saveCount by mutableIntStateOf(3)
        var closed = 0
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { OnEachSave(saveCount) { closed++ } }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(0, closed)

        saveCount = 4
        composeRule.waitForIdle()
        assertEquals(1, closed)
    }
}

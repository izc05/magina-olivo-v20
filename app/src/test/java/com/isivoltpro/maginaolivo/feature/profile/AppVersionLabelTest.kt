package com.isivoltpro.maginaolivo.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every APK says which build it is, so a phone can be matched to its changelog entry. */
class AppVersionLabelTest {
    @Test fun aCiBuildShowsItsNumber() {
        assertEquals("0.2.0-dev · compilación 531", appVersionLabel("0.2.0-dev", 531))
    }

    @Test fun aLocalBuildSaysSo() {
        assertEquals("0.2.0-dev · compilación local", appVersionLabel("0.2.0-dev", 0))
    }
}

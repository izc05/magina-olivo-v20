package com.isivoltpro.maginaolivo.privacy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * #461: farm data stays on this phone until Mágina Olivo has its own account/sync. Android's
 * cloud backup and device-to-device transfer must not carry the Room database, attachments,
 * camera cache or preferences, on every supported Android version.
 */
class BackupPolicyTest {
    private val main = sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }

    @Test
    fun theApplicationOptsOutOfAndroidBackup() {
        val application = parse(file("AndroidManifest.xml")).getElementsByTagName("application").item(0) as Element

        // Android 11 and lower: no backup at all, adb included.
        assertEquals("false", application.getAttribute("android:allowBackup"))
        // Android 12+: allowBackup=false does not stop D2D transfer, so the rules must be explicit.
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeEveryDomain() {
        val rules = parse(file("res/xml/data_extraction_rules.xml")).documentElement

        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as? Element
            assertNotNull("Missing <$section>", element)
            assertEquals("<$section> must not include anything", 0, element!!.getElementsByTagName("include").length)
            val excludes = element.getElementsByTagName("exclude")
            val excluded = (0 until excludes.length).map { excludes.item(it) as Element }
                .onEach { assertEquals("Every domain is excluded whole", ".", it.getAttribute("path")) }
                .map { it.getAttribute("domain") }
                .toSet()
            assertEquals(setOf("root", "device_root", "external"), excluded)
        }
    }

    @Test
    fun theProfileCopyTellsTheRealBehaviour() {
        val help = file("java/com/isivoltpro/maginaolivo/feature/profile/HelpScreens.kt").readText()

        assertTrue(help.contains("la copia de seguridad de Android"))
        assertTrue(help.contains("o cambias de teléfono, se pierden"))
    }

    private fun file(path: String): File {
        assertNotNull("Could not locate src/main", main)
        return File(main, path).also { assertTrue("Missing $path", it.exists()) }
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
}

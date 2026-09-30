package com.isivoltpro.maginaolivo.feature.profile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 21C — «Qué hay de nuevo» shows the farmer-facing part of the bundled CHANGELOG. */
class ReleaseNotesTest {
    @Test fun theTeamNotesAboveTheFirstVersionAreLeftOutAndMarksRemoved() {
        val notes = releaseNotes(
            """
            # Mágina Olivo — registro de versiones

            Cada APK muestra su versión en **Perfil**.

            - **Versión**: se sube a mano.

            ## 0.7.0 — en curso (fase 21, Perfil)

            - **Mi perfil (21A).** En Perfil, «Tu municipio» y
              «Tu cooperativa» (`profile_settings`).
            - Sin cambios en los cálculos.

            ## 0.6.0 — en curso

            Texto suelto.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NoteBlock.Heading("0.7.0 — en curso (fase 21, Perfil)"),
                NoteBlock.Item("Mi perfil (21A). En Perfil, «Tu municipio» y «Tu cooperativa» (profile_settings)."),
                NoteBlock.Item("Sin cambios en los cálculos."),
                NoteBlock.Heading("0.6.0 — en curso"),
                NoteBlock.Paragraph("Texto suelto."),
            ),
            notes,
        )
    }

    @Test fun aChangelogWithoutVersionsShowsNothing() {
        assertTrue(releaseNotes("# Título\n\nSolo notas internas.").isEmpty())
    }

    @Test fun theRealChangelogHasFarmerFacingNotes() {
        val changelog = listOf(File("../docs/CHANGELOG-APP.md"), File("docs/CHANGELOG-APP.md")).first { it.exists() }
        val notes = releaseNotes(changelog.readText())
        assertTrue(notes.first() is NoteBlock.Heading)
        assertTrue(notes.any { it is NoteBlock.Item })
    }
}

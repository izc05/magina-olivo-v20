package com.isivoltpro.maginaolivo.feature.profile

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/** Phase 21C — Perfil → Ayuda y privacidad. Everything here is read from the phone. */
enum class HelpTopic(val route: String, val title: String) {
    NEWS("news", "Qué hay de nuevo"),
    PRIVACY("privacy", "Privacidad y datos"),
    OFFLINE("offline", "Usar la app sin cobertura"),
    ;

    companion object {
        fun of(route: String?): HelpTopic? = entries.firstOrNull { it.route == route }
    }
}

/** One piece of the release notes as the screen shows it. */
sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Item(val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

/**
 * The farmer-facing part of `docs/CHANGELOG-APP.md` (bundled at build time): from the first
 * version heading («## 0.x …») on, with Markdown marks removed. The notes on how versions are
 * numbered, above it, are for the team and are left out.
 */
fun releaseNotes(markdown: String): List<NoteBlock> {
    val lines = markdown.lines()
    val start = lines.indexOfFirst { VERSION_HEADING.matches(it) }
    if (start < 0) return emptyList()
    val blocks = mutableListOf<NoteBlock>()
    var item: StringBuilder? = null
    var paragraph: StringBuilder? = null
    fun flush() {
        item?.let { blocks += NoteBlock.Item(clean(it.toString())) }
        paragraph?.let { blocks += NoteBlock.Paragraph(clean(it.toString())) }
        item = null
        paragraph = null
    }
    for (line in lines.drop(start)) {
        when {
            line.startsWith("#") -> { flush(); blocks += NoteBlock.Heading(clean(line.trimStart('#'))) }
            line.startsWith("- ") -> { flush(); item = StringBuilder(line.removePrefix("- ")) }
            line.isBlank() -> flush()
            item != null -> item!!.append(' ').append(line.trim())
            paragraph != null -> paragraph!!.append(' ').append(line.trim())
            else -> paragraph = StringBuilder(line.trim())
        }
    }
    flush()
    return blocks
}

private val VERSION_HEADING = Regex("^##\\s+\\d+\\.\\d+.*")

private fun clean(text: String): String = text.replace("**", "").replace("`", "").replace(Regex("\\s+"), " ").trim()

@Composable
fun HelpRoute(topic: HelpTopic) {
    val context = LocalContext.current
    val notes by produceState<List<NoteBlock>?>(initialValue = null, topic) {
        value = if (topic != HelpTopic.NEWS) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                runCatching { context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() } }
                    .map(::releaseNotes)
                    .getOrDefault(emptyList())
            }
        }
    }
    HelpScreen(topic, notes)
}

@Composable
fun HelpScreen(topic: HelpTopic, notes: List<NoteBlock>? = null) {
    Column(
        Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen).testTag("help-${topic.route}"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Spacer(Modifier.height(MoSpacing.sm))
        Text(topic.title, style = MaterialTheme.typography.headlineMedium, color = MoColors.current.primaryText)
        when (topic) {
            HelpTopic.NEWS -> News(notes)
            HelpTopic.PRIVACY -> Sections(PRIVACY)
            HelpTopic.OFFLINE -> Sections(OFFLINE)
        }
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun News(notes: List<NoteBlock>?) {
    when {
        notes == null -> Body("Cargando…")
        notes.isEmpty() -> Body("Las novedades no están en esta instalación.")
        else -> notes.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> MoSectionHeader(block.text)
                is NoteBlock.Item -> Body("• ${block.text}", Modifier.testTag("help-news-item"))
                is NoteBlock.Paragraph -> Body(block.text)
            }
        }
    }
}

@Composable
private fun Sections(sections: List<Pair<String, String>>) {
    sections.forEach { (title, body) ->
        MoSectionHeader(title)
        Body(body)
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText, modifier = modifier)
}

private const val CHANGELOG_ASSET = "CHANGELOG-APP.md"

/** What stays on the phone and which outside services the app calls, and why. */
private val PRIVACY = listOf(
    "Lo que queda en tu teléfono" to
        "Fincas, parcelas, campañas, trabajos, pesadas, jornales, gastos, fotos y documentos se guardan en este " +
        "teléfono y solo en él. Hoy no hay cuenta ni copia en la nube, y la copia de seguridad de Android y el " +
        "paso a un teléfono nuevo no los llevan: si borras la app o sus datos, o cambias de teléfono, se pierden.",
    "Fotos de vales y facturas" to
        "Una foto de vale o factura se guarda como adjunto en el propio teléfono y no se envía a ningún " +
        "servicio. Las fotos que adjuntas no se leen: los kilos y los importes son los que escribes tú. Los " +
        "vales y facturas que ya tenías «por revisar» de versiones anteriores se pueden seguir leyendo, " +
        "solo en el propio teléfono, y nada leído se guarda sin tu confirmación.",
    "Servicios externos que se consultan" to
        "Tiempo: se envía el municipio a nuestro servidor, que pregunta a AEMET y, si falla, a MET Norway. " +
        "Radar: nuestro servidor da la lista de imágenes y el teléfono descarga de RainViewer las de la zona " +
        "que ves en el mapa. Mapas: el teléfono descarga la foto aérea y el mapa base del Instituto Geográfico " +
        "Nacional (IGN) y la capa de parcelas del Catastro para la zona que ves. Catastro: se envía la " +
        "referencia o el punto del mapa al servicio INSPIRE de la Dirección General del Catastro. Mercado del " +
        "aceite: se descargan los precios semanales publicados por la Junta de Andalucía, y el pulso diario se " +
        "muestra con el widget de AOVE.net. Estos servicios reciben solo el municipio o la zona del mapa que " +
        "consultas; nunca tus pesadas, tus gastos ni tus trabajos.",
    "Permisos" to
        "Cámara y archivos, solo cuando adjuntas una foto o un documento. Notificaciones, solo para los avisos " +
        "de trabajos que planificas. Internet, solo para los servicios de arriba.",
)

/** What works without signal and what needs it. */
private val OFFLINE = listOf(
    "Todo lo del campo funciona sin cobertura" to
        "Registrar trabajos, riegos, tratamientos, pesadas, jornales y gastos, hacer fotos de vales y consultar " +
        "el Cuaderno funciona en modo avión. Se guarda al momento en el teléfono.",
    "Lo que necesita internet" to
        "El tiempo, el radar, el mercado del aceite y la consulta al Catastro. Sin cobertura verás el último dato " +
        "guardado con su fecha, o un aviso si no hay ninguno; nunca un dato inventado.",
    "Los avisos suenan igual" to
        "Los avisos de trabajos planificados se programan en el teléfono y suenan aunque no haya cobertura.",
)

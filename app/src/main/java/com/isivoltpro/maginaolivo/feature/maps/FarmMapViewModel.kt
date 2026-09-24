package com.isivoltpro.maginaolivo.feature.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.CadastreClient
import com.isivoltpro.maginaolivo.feature.catastro.CadastreError
import com.isivoltpro.maginaolivo.feature.catastro.CadastreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * VIEW: the farm's own parcels. ADD: tap the map, pick several Catastro parcels, incorporate
 * them in one go. LOCATE: attach Catastro identity + boundary to one parcel made by hand.
 */
enum class FarmMapMode { VIEW, ADD, LOCATE }

data class FarmMapState(
    val farm: Farm? = null,
    val parcels: List<Parcel> = emptyList(),
    val mode: FarmMapMode = FarmMapMode.VIEW,
    val locateParcel: Parcel? = null,
    val candidates: List<CadastralCandidate> = emptyList(),
    /** References already saved anywhere in the olivar: shown, never offered again. */
    val taken: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val selectedSavedId: UUID? = null,
    val focus: MapFocus? = null,
    val searching: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Set once a LOCATE link is saved, so the screen can return to the parcel. */
    val linkedParcelId: UUID? = null,
) {
    val selectedCandidates: List<CadastralCandidate> get() = candidates.filter { it.reference in selected }
}

class FarmMapViewModel(
    private val farmId: UUID,
    private val farms: FarmRepository,
    private val parcels: ParcelRepository,
    private val client: CadastreClient,
    locateParcelId: UUID? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        FarmMapState(mode = if (locateParcelId != null) FarmMapMode.LOCATE else FarmMapMode.VIEW),
    )
    val state: StateFlow<FarmMapState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { farms.observeById(farmId).collect { farm -> mutableState.update { it.copy(farm = farm) } } }
        viewModelScope.launch { parcels.observeActive(farmId).collect { list -> mutableState.update { it.copy(parcels = list) } } }
        if (locateParcelId != null) {
            viewModelScope.launch {
                parcels.observeById(locateParcelId).collect { parcel -> mutableState.update { it.copy(locateParcel = parcel) } }
            }
        }
    }

    fun setMode(mode: FarmMapMode) {
        if (mutableState.value.mode == FarmMapMode.LOCATE || mode == FarmMapMode.LOCATE) return
        searchJob?.cancel()
        mutableState.update {
            it.copy(mode = mode, candidates = emptyList(), selected = emptySet(), selectedSavedId = null,
                searching = false, error = null, message = null)
        }
    }

    /** Coordinates typed or pasted by the farmer; in ADD/LOCATE the nearby parcels are fetched too. */
    fun searchCoordinates(text: String) {
        val point = parseCoordinates(text)
        if (point == null) {
            mutableState.update { it.copy(error = "Escribe coordenadas como 37.636, -3.480 o 37°38'09\"N 3°28'48\"W.") }
            return
        }
        goTo(point)
    }

    /** "Mi ubicación" and coordinate searches end here. */
    fun goTo(point: GeoPoint) {
        mutableState.update { it.copy(focus = MapFocus(point), error = null, message = null) }
        if (mutableState.value.mode != FarmMapMode.VIEW) findNear(point.latitude, point.longitude)
    }

    fun tapMap(latitude: Double, longitude: Double) {
        if (mutableState.value.mode == FarmMapMode.VIEW) {
            mutableState.update { it.copy(selectedSavedId = null) }
            return
        }
        findNear(latitude, longitude)
    }

    fun searchPolygonParcel(province: String, municipality: String, polygon: String, parcel: String) {
        if (mutableState.value.mode == FarmMapMode.VIEW) mutableState.update { it.copy(mode = FarmMapMode.ADD) }
        load(replace = true) { client.findByPolygonParcel(province, municipality, polygon, parcel) }
    }

    /** Tap on a drawn parcel: a saved one opens its card, a candidate is (de)selected. */
    fun tapParcel(id: String) {
        val current = mutableState.value
        val candidate = current.candidates.firstOrNull { it.reference == id }
        when {
            candidate == null -> mutableState.update {
                it.copy(selectedSavedId = runCatching { UUID.fromString(id) }.getOrNull())
            }
            candidate.reference in current.taken -> mutableState.update {
                it.copy(message = "La parcela ${candidate.reference} ya está en tu olivar.")
            }
            current.mode == FarmMapMode.LOCATE -> mutableState.update {
                it.copy(selected = if (candidate.reference in it.selected) emptySet() else setOf(candidate.reference))
            }
            else -> mutableState.update {
                it.copy(selected = if (candidate.reference in it.selected) it.selected - candidate.reference else it.selected + candidate.reference)
            }
        }
    }

    /** Incorporates every selected candidate into this farm; names come from the review sheet. */
    fun importSelected(names: Map<String, String>) {
        val current = mutableState.value
        if (current.saving || current.mode != FarmMapMode.ADD || current.selected.isEmpty()) return
        mutableState.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            var added = 0
            val failed = mutableListOf<String>()
            current.selectedCandidates.forEach { candidate ->
                val name = names[candidate.reference]?.trim().orEmpty().ifEmpty { defaultParcelName(candidate.reference) }
                val result = parcels.create(
                    NewParcel(
                        farmId = farmId,
                        displayName = name,
                        cadastralReference = candidate.reference,
                        cadastralPolygon = ruralPart(candidate.reference, 6, 9),
                        cadastralParcel = ruralPart(candidate.reference, 9, 14),
                        source = ParcelSource.CATASTRO,
                        sourceProvider = candidate.provider,
                        sourceImportedAt = candidate.importedAt,
                        geometryGeoJson = candidate.geometryGeoJson,
                        cadastralAreaM2 = candidate.areaM2,
                    ),
                )
                if (result is AppResult.Success) added++ else failed += candidate.reference
            }
            mutableState.update {
                it.copy(
                    saving = false,
                    mode = if (failed.isEmpty()) FarmMapMode.VIEW else it.mode,
                    candidates = if (failed.isEmpty()) emptyList() else it.candidates,
                    selected = failed.toSet(),
                    taken = it.taken + (it.selected - failed.toSet()),
                    message = when (added) {
                        0 -> null
                        1 -> "1 parcela incorporada a la finca."
                        else -> "$added parcelas incorporadas a la finca."
                    },
                    error = if (failed.isEmpty()) null else "No pudimos guardar ${failed.joinToString()}. Revisa si ya están en otra finca.",
                )
            }
        }
    }

    /** LOCATE: the chosen Catastro parcel becomes this parcel's identity and boundary. */
    fun linkSelected() {
        val current = mutableState.value
        val parcel = current.locateParcel ?: return
        val candidate = current.selectedCandidates.singleOrNull() ?: return
        if (current.saving || current.mode != FarmMapMode.LOCATE) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = parcels.linkToRegistry(
                parcel.id,
                RegistryLink(
                    cadastralReference = candidate.reference,
                    cadastralPolygon = ruralPart(candidate.reference, 6, 9),
                    cadastralParcel = ruralPart(candidate.reference, 9, 14),
                    geometryGeoJson = candidate.geometryGeoJson,
                    cadastralAreaM2 = candidate.areaM2,
                    sourceProvider = candidate.provider,
                    sourceImportedAt = candidate.importedAt,
                ),
            )
            mutableState.update {
                when (result) {
                    is AppResult.Success -> it.copy(saving = false, linkedParcelId = parcel.id)
                    is AppResult.Failure -> it.copy(
                        saving = false,
                        error = if (result.error is AppError.Conflict && result.error.resource == "duplicate_cadastral_reference") {
                            "Esa referencia ya pertenece a otra parcela de tu olivar."
                        } else {
                            "No hemos podido guardar la ubicación. Vuelve a intentarlo."
                        },
                    )
                }
            }
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null, error = null) }

    fun locationUnavailable() = mutableState.update {
        it.copy(error = "No hemos podido saber dónde estás. Activa la ubicación o escribe las coordenadas.")
    }

    private fun findNear(latitude: Double, longitude: Double) = load(replace = false) { client.findNear(latitude, longitude) }

    private fun load(replace: Boolean, query: suspend () -> List<CadastralCandidate>) {
        if (mutableState.value.saving) return
        searchJob?.cancel()
        mutableState.update { it.copy(searching = true, error = null, message = null) }
        searchJob = viewModelScope.launch {
            try {
                val found = query()
                val workspace = mutableState.value.farm?.workspaceId
                val taken = if (workspace == null) emptySet() else found.map { it.reference }
                    .filter { parcels.findActiveByCadastralReference(workspace, it) != null }.toSet()
                mutableState.update { state ->
                    // Taps add to what is already on the map, so several parcels can be picked.
                    val merged = if (replace) found else (state.candidates + found).distinctBy { it.reference }
                    state.copy(
                        searching = false,
                        candidates = merged.take(MAX_CANDIDATES),
                        taken = state.taken + taken,
                        focus = if (replace) found.firstOrNull()?.centroid()?.let(::MapFocus) ?: state.focus else state.focus,
                        error = if (found.isEmpty()) "Catastro no devolvió parcelas en ese punto." else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CadastreException) {
                mutableState.update { it.copy(searching = false, error = error.farmerMessage()) }
            } catch (error: Exception) {
                mutableState.update { it.copy(searching = false, error = "No hemos podido consultar Catastro ahora. Tus parcelas guardadas siguen disponibles.") }
            }
        }
    }

    private companion object {
        const val MAX_CANDIDATES = 300
    }
}

/** Default name for a new parcel: what the farmer reads on PAC/deed papers. */
fun defaultParcelName(reference: String): String {
    val polygon = ruralPart(reference, 6, 9)?.toIntOrNull()
    val parcel = ruralPart(reference, 9, 14)?.toIntOrNull()
    return if (polygon != null && parcel != null) "Pol. $polygon · Parc. $parcel" else "Parcela ${reference.takeLast(5)}"
}

/** The number drawn on a Catastro parcel in the map: its parcel number ("120"). */
fun parcelNumber(reference: String): String =
    ruralPart(reference, 9, 14)?.toIntOrNull()?.toString() ?: reference.takeLast(4)

private fun ruralPart(reference: String, from: Int, to: Int): String? =
    if (RURAL.matches(reference)) reference.substring(from, to) else null

private val RURAL = Regex("\\d{5}[A-Z]\\d{8}")

internal fun CadastralCandidate.centroid(): GeoPoint? {
    val ring = polygons.firstOrNull()?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    return GeoPoint(ring.map { it.second }.average(), ring.map { it.first }.average())
}

private fun CadastreException.farmerMessage(): String = when (kind) {
    CadastreError.INVALID_REFERENCE -> "Revisa provincia, municipio, polígono y parcela."
    CadastreError.NOT_FOUND -> "Catastro no encontró esa parcela. Revisa los datos."
    CadastreError.NETWORK, CadastreError.SERVICE -> "No hemos podido consultar Catastro ahora. Tus parcelas guardadas siguen disponibles."
    CadastreError.INVALID_GEOMETRY, CadastreError.RESPONSE -> "Catastro respondió, pero no pudimos verificar esa parcela."
}

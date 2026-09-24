package com.isivoltpro.maginaolivo.feature.catastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CadastreImportState(
    val farms: List<Farm> = emptyList(),
    val searching: Boolean = false,
    val saving: Boolean = false,
    val candidate: CadastralCandidate? = null,
    val candidates: List<CadastralCandidate> = emptyList(),
    val duplicateId: UUID? = null,
    val error: String? = null,
    val savedParcelId: UUID? = null,
)

class CadastreImportViewModel(
    private val persistence: LocalPersistence,
    private val client: CadastreClient,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CadastreImportState())
    val state: StateFlow<CadastreImportState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            when (val workspace = persistence.workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Success -> persistence.farmRepository.observeActive(workspace.value).collect { farms ->
                    mutableState.value = mutableState.value.copy(farms = farms)
                }
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    error = "No hemos podido cargar tus fincas en este dispositivo.",
                )
            }
        }
    }

    fun search(reference: String) {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            searching = true, candidate = null, candidates = emptyList(), error = null, savedParcelId = null, duplicateId = null,
        )
        searchJob = viewModelScope.launch {
            try {
                val candidate = client.findByReference(reference)
                mutableState.value = mutableState.value.copy(
                    searching = false, candidate = candidate, candidates = listOf(candidate),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CadastreException) {
                mutableState.value = mutableState.value.copy(searching = false, error = error.userMessage())
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    searching = false,
                    error = "No hemos podido consultar Catastro ahora. Tus parcelas guardadas siguen disponibles.",
                )
            }
        }
    }

    fun selectCandidate(reference: String) {
        mutableState.value.candidates.firstOrNull { it.reference == reference }?.let {
            mutableState.value = mutableState.value.copy(candidate = it, error = null, duplicateId = null)
        }
    }

    fun searchNear(latitude: Double, longitude: Double) = loadCandidates {
        client.findNear(latitude, longitude)
    }

    fun importFile(resolver: android.content.ContentResolver, uri: android.net.Uri) = loadCandidates {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 2_000_000) throw CadastreException(CadastreError.RESPONSE)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            } ?: throw CadastreException(CadastreError.RESPONSE)
            readGmlParcels(bytes)
        }
    }

    private fun loadCandidates(load: suspend () -> List<CadastralCandidate>) {
        if (mutableState.value.saving) return
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(searching = true, candidate = null,
            candidates = emptyList(), error = null, duplicateId = null)
        searchJob = viewModelScope.launch {
            try {
                val results = load()
                mutableState.value = mutableState.value.copy(searching = false, candidates = results,
                    candidate = results.singleOrNull(),
                    error = if (results.isEmpty()) "No encontramos parcelas en esa consulta." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = mutableState.value.copy(searching = false,
                    error = (error as? CadastreException)?.userMessage() ?: "No se pudo leer la parcela. Revisa el archivo o la conexión.")
            }
        }
    }

    fun import(farmId: UUID?, alias: String) {
        if (mutableState.value.searching) return
        val candidate = mutableState.value.candidate ?: return
        if (farmId == null || mutableState.value.farms.none { it.id == farmId }) {
            mutableState.value = mutableState.value.copy(error = "Elige la finca que recibirá la parcela.")
            return
        }
        if (alias.isBlank()) {
            mutableState.value = mutableState.value.copy(error = "Escribe un nombre para reconocer la parcela.")
            return
        }
        if (mutableState.value.saving) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null)
            val farm = mutableState.value.farms.first { it.id == farmId }
            val existing = persistence.parcelRepository.findActiveByCadastralReference(
                farm.workspaceId, candidate.reference,
            )
            if (existing != null) {
                mutableState.value = mutableState.value.copy(saving = false, duplicateId = existing,
                    error = "Esta referencia ya está guardada en tu olivar.")
                return@launch
            }
            val rural = Regex("\\d{5}[A-Z]\\d{8}").matches(candidate.reference)
            when (val result = persistence.parcelRepository.create(
                NewParcel(
                    farmId = farmId,
                    displayName = alias.trim(),
                    cadastralReference = candidate.reference,
                    cadastralPolygon = if (rural) candidate.reference.substring(6, 9) else null,
                    cadastralParcel = if (rural) candidate.reference.substring(9, 14) else null,
                    source = ParcelSource.CATASTRO,
                    sourceProvider = candidate.provider,
                    sourceImportedAt = candidate.importedAt,
                    geometryGeoJson = candidate.geometryGeoJson,
                    cadastralAreaM2 = candidate.areaM2,
                ),
            )) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(
                    saving = false, savedParcelId = result.value,
                )
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    saving = false,
                    error = if (result.error is AppError.Conflict &&
                        result.error.resource == "duplicate_cadastral_reference"
                    ) {
                        "Esta referencia ya está guardada en tu olivar. Abre la parcela existente desde su finca."
                    } else {
                        "No hemos podido guardar la parcela. Revisa la finca y vuelve a intentarlo."
                    },
                )
            }
        }
    }
}

private fun CadastreException.userMessage(): String = when (kind) {
    CadastreError.INVALID_REFERENCE -> "La referencia catastral debe tener 14 letras o números."
    CadastreError.NOT_FOUND -> "No encontramos una parcela con esa referencia. Revisa el dato."
    CadastreError.NETWORK, CadastreError.SERVICE ->
        "No hemos podido consultar Catastro ahora. Tus parcelas guardadas siguen disponibles."
    CadastreError.INVALID_GEOMETRY, CadastreError.RESPONSE ->
        "Catastro respondió, pero no pudimos verificar la geometría de esta parcela."
}

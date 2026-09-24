package com.isivoltpro.maginaolivo.feature.maps

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/** Saved parcels are the farmer's own; candidates are Catastro answers not yet incorporated. */
enum class MapParcelKind { SAVED, CANDIDATE }

data class MapParcel(
    val id: String,
    val name: String,
    val geometry: String,
    val kind: MapParcelKind = MapParcelKind.SAVED,
    /** Short text drawn on the parcel (a Catastro parcel number); null draws nothing. */
    val label: String? = null,
)

/** A camera request; a new [token] moves the map again to the same place. */
data class MapFocus(val point: GeoPoint, val zoom: Double = 16.5, val token: Long = System.nanoTime())

/**
 * What is drawn under the parcels. [MAP] is the light IGN base map (streets, paths, towns);
 * [AERIAL] is the PNOA photo, heavier on the phone; [NONE] draws only the saved boundaries and
 * is the one that works without connection.
 */
enum class MapBase(val label: String) { MAP("Mapa"), AERIAL("Foto aérea"), NONE("Solo parcelas") }

/** No remote style, sprites or fonts are needed for the authoritative local parcel layer. */
@Composable
fun ParcelMap(
    parcels: List<MapParcel>,
    modifier: Modifier = Modifier,
    imagery: Boolean = false,
    selectedId: String? = null,
    onSelected: (String) -> Unit = {},
    onTap: ((Double, Double) -> Unit)? = null,
    onReady: () -> Unit = {},
    onMapSnapshot: ((Bitmap) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    focus: MapFocus? = null,
    /** Overrides [imagery] when set. */
    base: MapBase? = null,
    /** Catastro's own boundary lines over the base (online, from zoom 15); off by default: they are slow. */
    cadastreLines: Boolean = false,
    /** Floating +, − and frame buttons; off for small embedded maps. */
    controls: Boolean = true,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val select by rememberUpdatedState(onSelected)
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val snapshot by rememberUpdatedState(onMapSnapshot)
    val effectiveBase = base ?: if (imagery) MapBase.AERIAL else MapBase.NONE
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    // Frame the farmer's own parcels when they change, not on every selection tap.
    var framedKey by remember { mutableStateOf<List<String>?>(null) }
    // Screen positions of the labels, refreshed when the camera stops (never while it moves).
    var labelSpots by remember { mutableStateOf<List<Pair<String, IntOffset>>>(emptyList()) }
    var cameraTick by remember { mutableStateOf(0) }
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(view, owner) {
        var started = false
        var resumed = false
        fun synchronize() {
            val state = owner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.STARTED) && !started) { view.onStart(); started = true }
            if (state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) { view.onResume(); resumed = true }
            if (!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) { view.onPause(); resumed = false }
            if (!state.isAtLeast(Lifecycle.State.STARTED) && started) { view.onStop(); started = false }
        }
        val observer = LifecycleEventObserver { _, _ -> synchronize() }
        owner.lifecycle.addObserver(observer)
        synchronize()
        view.getMapAsync { loaded ->
            map = loaded
            loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(37.73, -3.45), 12.0))
            loaded.addOnMapClickListener { point ->
                val hit = loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(point), "parcels-fill").firstOrNull()
                if (hit != null) select(hit.getStringProperty("id")) else tap?.invoke(point.latitude, point.longitude)
                true
            }
            loaded.addOnCameraMoveStartedListener { labelSpots = emptyList() }
            loaded.addOnCameraIdleListener { cameraTick++ }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, effectiveBase, cadastreLines) {
        styleReady = false
        map?.setStyle(Style.Builder().fromJson(parcelStyle(effectiveBase, cadastreLines))) { styleReady = true }
    }
    val selection = remember(selectedId, selectedIds) { selectedIds + listOfNotNull(selectedId) }
    val data = remember(parcels, selection) { mapFeatureCollection(parcels, selection) }
    DisposableEffect(map, styleReady, data, parcels.map { it.id }, snapshot != null) {
        val current = map
        if (!styleReady || current == null) return@DisposableEffect onDispose {}

        var delivered = false
        var listener: MapView.OnDidFinishRenderingMapListener? = null
        if (snapshot != null) {
            listener = MapView.OnDidFinishRenderingMapListener { fullyRendered ->
                if (fullyRendered && !delivered) {
                    delivered = true
                    listener?.let(view::removeOnDidFinishRenderingMapListener)
                    current.snapshot { bitmap ->
                        snapshot?.invoke(bitmap)
                        ready()
                    }
                }
            }
            view.addOnDidFinishRenderingMapListener(listener)
        }
        current.style?.getSourceAs<GeoJsonSource>("saved-parcels")?.setGeoJson(data)
        val saved = parcels.filter { it.kind == MapParcelKind.SAVED }
        val savedKey = saved.map { it.id }
        if (framedKey != savedKey && focus == null) {
            fitParcels(current, saved)
            framedKey = savedKey
        }
        cameraTick++
        if (snapshot == null) ready()

        onDispose { listener?.let(view::removeOnDidFinishRenderingMapListener) }
    }
    LaunchedEffect(map, focus) {
        val current = map ?: return@LaunchedEffect
        focus?.let { current.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.point.latitude, it.point.longitude), it.zoom)) }
    }
    val labelled = remember(parcels) { parcels.mapNotNull { p -> p.label?.let { label -> labelPoint(p.geometry)?.let { Triple(p.id, label, it) } } } }
    LaunchedEffect(map, cameraTick, labelled) {
        val current = map ?: return@LaunchedEffect
        // Numbers only once the parcels are big enough to tell apart, and never a crowd.
        labelSpots = if (current.cameraPosition.zoom < LABEL_MIN_ZOOM) {
            emptyList()
        } else {
            labelled.mapNotNull { (_, label, point) ->
                val spot = current.projection.toScreenLocation(point)
                if (spot.x < 0 || spot.y < 0 || spot.x > view.width || spot.y > view.height) null
                else label to IntOffset(spot.x.roundToInt(), spot.y.roundToInt())
            }.take(MAX_LABELS)
        }
    }
    val density = LocalDensity.current
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().testTag("parcel-map-view"))
        labelSpots.forEach { (label, spot) ->
            ParcelNumber(label, Modifier.offset { IntOffset(spot.x - with(density) { 14.dp.roundToPx() }, spot.y - with(density) { 11.dp.roundToPx() }) })
        }
        if (controls) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MapButton("+", "Acercar") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
                MapButton("−", "Alejar") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
                MapButton("⤢", "Encuadrar mis parcelas") {
                    map?.let { fitParcels(it, parcels.filter { p -> p.kind == MapParcelKind.SAVED }.ifEmpty { parcels }) }
                }
            }
        }
        Text(
            when (effectiveBase) {
                MapBase.AERIAL -> "© IGN · PNOA" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.MAP -> "© IGN · Mapa base" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.NONE -> "Límites guardados en el teléfono"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MoInk,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                .semantics { contentDescription = "Atribución del mapa" },
        )
    }
}

@Composable
private fun ParcelNumber(label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MoWarmWhite.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MoSoftGold),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MoOliveDark,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MapButton(symbol: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        color = MoWarmWhite.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MoOutline),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        }
    }
}

fun mapFeatureCollection(parcels: List<MapParcel>, selectedId: String?): String =
    mapFeatureCollection(parcels, setOfNotNull(selectedId))

fun mapFeatureCollection(parcels: List<MapParcel>, selectedIds: Set<String>): String {
    val features = JsonArray()
    parcels.forEach { parcel ->
        val geometry = runCatching { JsonParser.parseString(parcel.geometry).asJsonObject }.getOrNull() ?: return@forEach
        val properties = JsonObject().apply {
            addProperty("id", parcel.id)
            addProperty("selected", parcel.id in selectedIds)
            addProperty("kind", parcel.kind.name)
        }
        features.add(JsonObject().apply {
            addProperty("type", "Feature")
            add("geometry", geometry)
            add("properties", properties)
        })
    }
    return JsonObject().apply {
        addProperty("type", "FeatureCollection")
        add("features", features)
    }.toString()
}

/** Where a label goes: the average of the outer ring of the first polygon (inside for olive plots). */
internal fun labelPoint(geometry: String): LatLng? = runCatching {
    val root = JsonParser.parseString(geometry).asJsonObject
    val coordinates = root.getAsJsonArray("coordinates")
    val ring = if (root.get("type").asString == "MultiPolygon") coordinates[0].asJsonArray[0].asJsonArray else coordinates[0].asJsonArray
    val points = ring.map { it.asJsonArray }.dropLast(1).ifEmpty { ring.map { it.asJsonArray } }
    LatLng(points.map { it[1].asDouble }.average(), points.map { it[0].asDouble }.average())
}.getOrNull()

private fun fitParcels(map: MapLibreMap, parcels: List<MapParcel>) {
    val points = mutableListOf<LatLng>()
    fun collect(array: JsonArray) {
        if (array.size() >= 2 && array[0].isJsonPrimitive && array[0].asJsonPrimitive.isNumber) {
            points += LatLng(array[1].asDouble, array[0].asDouble)
        } else array.filter { it.isJsonArray }.forEach { collect(it.asJsonArray) }
    }
    parcels.forEach { runCatching { collect(JsonParser.parseString(it.geometry).asJsonObject.getAsJsonArray("coordinates")) } }
    if (points.size >= 3) map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 48))
}

internal fun parcelStyle(imagery: Boolean): String = parcelStyle(if (imagery) MapBase.AERIAL else MapBase.NONE, cadastreLines = false)

/**
 * Raster sources are declared at 512 px: the phone downloads and decodes about a quarter of
 * the tiles of a 256 px declaration, which is what keeps the aerial photo fluid.
 */
internal fun parcelStyle(base: MapBase, cadastreLines: Boolean): String {
    val sources = buildList {
        add(""""saved-parcels":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}""")
        when (base) {
            MapBase.AERIAL -> add(""""base":{"type":"raster","tileSize":512,"maxzoom":19,"tiles":["$PNOA"]}""")
            MapBase.MAP -> add(""""base":{"type":"raster","tileSize":512,"maxzoom":17,"tiles":["$IGN_BASE"]}""")
            MapBase.NONE -> Unit
        }
        if (cadastreLines && base != MapBase.NONE) {
            add(""""cadastre":{"type":"raster","tileSize":512,"tiles":["$CADASTRE_WMS"]}""")
        }
    }.joinToString(",")
    val layers = buildList {
        add("""{"id":"background","type":"background","paint":{"background-color":"#F3F1E6"}}""")
        if (base != MapBase.NONE) add("""{"id":"base","type":"raster","source":"base"}""")
        if (cadastreLines && base != MapBase.NONE) add("""{"id":"cadastre","type":"raster","source":"cadastre","minzoom":15}""")
        add("""{"id":"parcels-fill","type":"fill","source":"saved-parcels","paint":{"fill-color":["case",["get","selected"],"#CDA449",["==",["get","kind"],"CANDIDATE"],"#F4EAD0","#567342"],"fill-opacity":["case",["get","selected"],0.55,["==",["get","kind"],"CANDIDATE"],0.30,0.38]}}""")
        add("""{"id":"parcels-line","type":"line","source":"saved-parcels","paint":{"line-color":["case",["==",["get","kind"],"CANDIDATE"],"#8A6A1F","#25371C"],"line-width":["case",["get","selected"],4,["==",["get","kind"],"CANDIDATE"],2,3]}}""")
    }.joinToString(",")
    return """{"version":8,"sources":{$sources},"layers":[$layers]}"""
}

private const val LABEL_MIN_ZOOM = 15.5
private const val MAX_LABELS = 60
private const val PNOA = "https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=OI.OrthoimageCoverage&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
private const val IGN_BASE = "https://www.ign.es/wmts/ign-base?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=IGNBaseTodo&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
private const val CADASTRE_WMS = "https://ovc.catastro.meh.es/cartografia/INSPIRE/spadgcwms.aspx?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=CP.CadastralParcel&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE&SRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=512&HEIGHT=512"

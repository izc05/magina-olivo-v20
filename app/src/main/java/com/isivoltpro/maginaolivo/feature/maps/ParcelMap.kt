package com.isivoltpro.maginaolivo.feature.maps

import android.os.Bundle
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/** Saved parcels are the farmer's own; candidates are Catastro answers not yet incorporated. */
enum class MapParcelKind { SAVED, CANDIDATE }

data class MapParcel(
    val id: String,
    val name: String,
    val geometry: String,
    val kind: MapParcelKind = MapParcelKind.SAVED,
)

/** A camera request; a new [token] moves the map again to the same place. */
data class MapFocus(val point: GeoPoint, val zoom: Double = 16.5, val token: Long = System.nanoTime())

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
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val select by rememberUpdatedState(onSelected)
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val snapshot by rememberUpdatedState(onMapSnapshot)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    // Frame the farmer's own parcels when they change, not on every selection tap.
    var framedKey by remember { mutableStateOf<List<String>?>(null) }
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
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, imagery) {
        styleReady = false
        map?.setStyle(Style.Builder().fromJson(parcelStyle(imagery))) { styleReady = true }
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
        if (snapshot == null) ready()

        onDispose { listener?.let(view::removeOnDidFinishRenderingMapListener) }
    }
    LaunchedEffect(map, focus) {
        val current = map ?: return@LaunchedEffect
        focus?.let { current.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.point.latitude, it.point.longitude), it.zoom)) }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) { Text("Acercar +") }
            TextButton(onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) { Text("Alejar −") }
            TextButton(onClick = { map?.let { fitParcels(it, parcels.filter { p -> p.kind == MapParcelKind.SAVED }.ifEmpty { parcels }) } }) { Text("Encuadrar") }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().weight(1f).testTag("parcel-map-view"))
        Text(if (imagery) "© IGN / PNOA · © Dirección General del Catastro. Base con conexión."
            else "Límites guardados en el dispositivo · base sin conexión", Modifier.padding(6.dp))
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

internal fun parcelStyle(imagery: Boolean): String {
    val remoteSources = if (imagery) """,
        "ortho":{"type":"raster","tileSize":256,"tiles":["https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=OI.OrthoimageCoverage&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"]},
        "cadastre":{"type":"raster","tileSize":256,"tiles":["https://ovc.catastro.meh.es/cartografia/INSPIRE/spadgcwms.aspx?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=CP.CadastralParcel&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE&SRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256"]}
    """ else ""
    val remoteLayers = if (imagery) """{"id":"ortho","type":"raster","source":"ortho"},
        {"id":"cadastre","type":"raster","source":"cadastre","minzoom":15},""" else ""
    return """{"version":8,"sources":{"saved-parcels":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}$remoteSources},
        "layers":[{"id":"background","type":"background","paint":{"background-color":"#F3F1E6"}},$remoteLayers
        {"id":"parcels-fill","type":"fill","source":"saved-parcels","paint":{"fill-color":["case",["get","selected"],"#CDA449",["==",["get","kind"],"CANDIDATE"],"#F4EAD0","#567342"],"fill-opacity":["case",["get","selected"],0.55,["==",["get","kind"],"CANDIDATE"],0.30,0.38]}},
        {"id":"parcels-line","type":"line","source":"saved-parcels","paint":{"line-color":["case",["==",["get","kind"],"CANDIDATE"],"#8A6A1F","#25371C"],"line-width":["case",["get","selected"],4,["==",["get","kind"],"CANDIDATE"],2,3]}}]}"""
}

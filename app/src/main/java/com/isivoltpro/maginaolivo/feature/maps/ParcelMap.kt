package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

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
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.roundToInt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation

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

/** Pixels occupied by UI floating over the MapView. They are not part of the usable camera viewport. */
data class MapCameraInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
) {
    init {
        require(leftPx >= 0 && topPx >= 0 && rightPx >= 0 && bottomPx >= 0) { "negative_map_insets" }
    }

    internal fun withMargin(marginPx: Int): MapCameraInsets = MapCameraInsets(
        leftPx = leftPx + marginPx,
        topPx = topPx + marginPx,
        rightPx = rightPx + marginPx,
        bottomPx = bottomPx + marginPx,
    )
}

private data class MapFrameKey(
    val parcels: List<Pair<String, String>>,
    val padding: MapCameraInsets,
)

/** A projected parcel label before collision filtering. Pure data so priority can be unit-tested. */
internal data class MapLabelCandidate(
    val id: String,
    val label: String,
    val x: Int,
    val y: Int,
    val selected: Boolean,
)

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
    /** Phase 20B-radar: a live raster drawn over the base ({z}/{x}/{y} template), under the parcels. */
    overlayTiles: String? = null,
    /** Credit for [overlayTiles], added to the map's attribution line. */
    overlayAttribution: String? = null,
    /** #574: UI panels floating over the map; framing and labels stay inside the uncovered area. */
    cameraInsets: MapCameraInsets = MapCameraInsets(),
    /** #361: where «Mi ubicación» found the phone, drawn as a blue dot; never tracked or stored. */
    myLocation: GeoPoint? = null,
    /** Native remote tile failures; saved geometry is independent of these sources. */
    onTileError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val select by rememberUpdatedState(onSelected)
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val snapshot by rememberUpdatedState(onMapSnapshot)
    val tileError by rememberUpdatedState(onTileError)
    val density = LocalDensity.current
    val frameMarginPx = with(density) { MAP_FRAME_MARGIN.roundToPx() }
    val labelHorizontalSpacingPx = with(density) { LABEL_HORIZONTAL_SPACING.roundToPx() }
    val labelVerticalSpacingPx = with(density) { LABEL_VERTICAL_SPACING.roundToPx() }
    val framePadding = remember(cameraInsets, frameMarginPx) { cameraInsets.withMargin(frameMarginPx) }
    val effectiveBase = base ?: if (imagery) MapBase.AERIAL else MapBase.NONE
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    // Frame the farmer's own parcels when they change, not on every selection tap.
    var framedKey by remember { mutableStateOf<MapFrameKey?>(null) }
    // Screen positions of the labels, refreshed when the camera stops (never while it moves).
    var labelSpots by remember { mutableStateOf<List<Pair<String, IntOffset>>>(emptyList()) }
    var cameraTick by remember { mutableStateOf(0) }
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(view, owner) {
        var active = true
        val tileListener = MapView.OnTileActionListener { operation, _, _, _, _, _, source ->
            if (operation == TileOperation.Error) {
                view.post { if (active) tileError(source) }
            }
        }
        view.addOnTileActionListener(tileListener)
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
            active = false
            view.removeOnTileActionListener(tileListener)
            owner.lifecycle.removeObserver(observer)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
    val showsMyLocation = myLocation != null
    LaunchedEffect(map, effectiveBase, cadastreLines, overlayTiles, showsMyLocation, dark) {
        styleReady = false
        map?.setStyle(Style.Builder().fromJson(parcelStyle(effectiveBase, cadastreLines, overlayTiles, showsMyLocation, dark))) { styleReady = true }
    }
    LaunchedEffect(map, styleReady, myLocation) {
        val point = myLocation ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        map?.style?.getSourceAs<GeoJsonSource>("my-location")?.setGeoJson(myLocationFeature(point))
    }
    val selection = remember(selectedId, selectedIds) { selectedIds + listOfNotNull(selectedId) }
    val data = remember(parcels, selection) { mapFeatureCollection(parcels, selection) }
    DisposableEffect(map, styleReady, data, parcels.map { it.id to it.geometry }, framePadding, snapshot != null) {
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
        val savedKey = MapFrameKey(saved.map { it.id to it.geometry }, framePadding)
        if (framedKey != savedKey && focus == null) {
            fitParcels(current, saved, framePadding)
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
    LaunchedEffect(
        map,
        cameraTick,
        labelled,
        cameraInsets,
        selection,
        labelHorizontalSpacingPx,
        labelVerticalSpacingPx,
    ) {
        val current = map ?: return@LaunchedEffect
        // #574: selected parcel first; then labels nearest the usable viewport centre. A label
        // hidden by another is dropped instead of drawing two unreadable number pills.
        labelSpots = if (current.cameraPosition.zoom < LABEL_MIN_ZOOM) {
            emptyList()
        } else {
            val candidates = labelled.mapNotNull { (id, label, point) ->
                val spot = current.projection.toScreenLocation(point)
                if (!insideSafeViewport(spot.x, spot.y, view.width, view.height, cameraInsets)) {
                    null
                } else {
                    MapLabelCandidate(
                        id = id,
                        label = label,
                        x = spot.x.roundToInt(),
                        y = spot.y.roundToInt(),
                        selected = id in selection,
                    )
                }
            }
            resolveMapLabelCollisions(
                candidates = candidates,
                centerX = (cameraInsets.leftPx + (view.width - cameraInsets.rightPx)) / 2,
                centerY = (cameraInsets.topPx + (view.height - cameraInsets.bottomPx)) / 2,
                horizontalSpacingPx = labelHorizontalSpacingPx,
                verticalSpacingPx = labelVerticalSpacingPx,
                maxLabels = MAX_LABELS,
            ).map { it.label to IntOffset(it.x, it.y) }
        }
    }
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
                    map?.let { fitParcels(it, parcels.filter { p -> p.kind == MapParcelKind.SAVED }.ifEmpty { parcels }, framePadding) }
                }
            }
        }
        MapAttribution(
            when (effectiveBase) {
                MapBase.AERIAL -> "© IGN · PNOA" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.MAP -> "© IGN · Mapa base" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.NONE -> "Límites guardados en el teléfono"
            } + (overlayAttribution?.let { " · $it" } ?: ""),
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = with(density) { cameraInsets.bottomPx.toDp() } + 4.dp)
                .semantics { contentDescription = "Atribución del mapa" },
        )
    }
}

@Composable
internal fun MapAttribution(label: String, modifier: Modifier = Modifier) {
    // Raster imagery keeps its source colors; attribution needs its own readable backdrop.
    Surface(modifier = modifier, color = MoSurfaceTokens.cardSurface,
        shape = RoundedCornerShape(4.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MoColors.current.bodyText)
    }
}

@Composable
private fun ParcelNumber(label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MoSurfaceTokens.cardSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MoColors.current.goldAccent),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MoColors.current.primaryText,
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
        color = MoSurfaceTokens.cardSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
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

/** #574: a point guaranteed inside the dominant valid polygon component. */
internal fun labelPoint(geometry: String): LatLng? =
    parcelLabelPoint(geometry)?.let { LatLng(it.latitude, it.longitude) }

private fun fitParcels(map: MapLibreMap, parcels: List<MapParcel>, padding: MapCameraInsets) {
    val points = mutableListOf<LatLng>()
    fun collect(array: JsonArray) {
        if (array.size() >= 2 && array[0].isJsonPrimitive && array[0].asJsonPrimitive.isNumber) {
            points += LatLng(array[1].asDouble, array[0].asDouble)
        } else array.filter { it.isJsonArray }.forEach { collect(it.asJsonArray) }
    }
    parcels.forEach { runCatching { collect(JsonParser.parseString(it.geometry).asJsonObject.getAsJsonArray("coordinates")) } }
    if (points.size >= 3) {
        val bounds = LatLngBounds.Builder().includes(points).build()
        map.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                0.0,
                0.0,
                padding.leftPx,
                padding.topPx,
                padding.rightPx,
                padding.bottomPx,
            ),
        )
    }
}

/**
 * #574 collision policy for Compose labels. All selected labels survive; ordinary labels are
 * then admitted from the centre out until [maxLabels], skipping pills that would overlap one
 * already visible. This is deterministic and independent of parcel input order.
 */
internal fun resolveMapLabelCollisions(
    candidates: List<MapLabelCandidate>,
    centerX: Int,
    centerY: Int,
    horizontalSpacingPx: Int,
    verticalSpacingPx: Int,
    maxLabels: Int,
): List<MapLabelCandidate> {
    if (maxLabels <= 0 && candidates.none { it.selected }) return emptyList()

    fun distanceSquared(candidate: MapLabelCandidate): Long {
        val dx = candidate.x.toLong() - centerX
        val dy = candidate.y.toLong() - centerY
        return dx * dx + dy * dy
    }

    val selected = candidates.filter { it.selected }
        .sortedWith(compareBy<MapLabelCandidate>({ distanceSquared(it) }, { it.id }))
    val accepted = selected.toMutableList()
    val limit = maxOf(maxLabels, selected.size)

    candidates.asSequence()
        .filterNot { it.selected }
        .sortedWith(compareBy<MapLabelCandidate>({ distanceSquared(it) }, { it.id }))
        .forEach { candidate ->
            if (accepted.size >= limit) return@forEach
            val collides = accepted.any { visible ->
                kotlin.math.abs(candidate.x - visible.x) < horizontalSpacingPx &&
                    kotlin.math.abs(candidate.y - visible.y) < verticalSpacingPx
            }
            if (!collides) accepted += candidate
        }
    return accepted
}

internal fun insideSafeViewport(
    x: Float,
    y: Float,
    widthPx: Int,
    heightPx: Int,
    insets: MapCameraInsets,
): Boolean =
    x >= insets.leftPx &&
        x <= widthPx - insets.rightPx &&
        y >= insets.topPx &&
        y <= heightPx - insets.bottomPx

internal fun parcelStyle(imagery: Boolean): String = parcelStyle(if (imagery) MapBase.AERIAL else MapBase.NONE, cadastreLines = false)

/**
 * #361: IGN and PNOA serve 256 px tiles, so they are declared at 256 and drawn at their own
 * resolution. Declaring them at 512 (Phase 18, to fetch fewer tiles) stretched every picture
 * to twice its size: roads, olive rows and boundaries looked blurred on the phone. Catastro's
 * WMS is asked for real 512 px images and stays at 512.
 */
internal fun parcelStyle(base: MapBase, cadastreLines: Boolean, overlayTiles: String? = null, myLocation: Boolean = false, dark: Boolean = false): String {
    // Theme our own canvas only. Raster source pixels and raster paints stay unchanged.
    val background = if (dark) "#171914" else "#F3F1E6"
    val savedLine = if (dark && base == MapBase.NONE) "#B6D39E" else "#25371C"
    val candidateLine = if (dark && base == MapBase.NONE) "#E2CC90" else "#8A6A1F"
    val sources = buildList {
        add(""""saved-parcels":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}""")
        if (myLocation) add(""""my-location":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}""")
        when (base) {
            MapBase.AERIAL -> add(""""base":{"type":"raster","tileSize":256,"maxzoom":19,"tiles":["$PNOA"]}""")
            MapBase.MAP -> add(""""base":{"type":"raster","tileSize":256,"maxzoom":17,"tiles":["$IGN_BASE"]}""")
            MapBase.NONE -> Unit
        }
        if (cadastreLines && base != MapBase.NONE) {
            add(""""cadastre":{"type":"raster","tileSize":512,"tiles":["$CADASTRE_WMS"]}""")
        }
        // Radar pictures are published up to a low zoom; MapLibre enlarges them beyond it. #360: a
        // 512 px picture is declared at 512, so it is drawn at its own resolution, not stretched.
        overlayTiles?.let { add(""""overlay":{"type":"raster","tileSize":${overlayTileSize(it)},"maxzoom":$OVERLAY_MAX_ZOOM,"tiles":["$it"]}""") }
    }.joinToString(",")
    val layers = buildList {
        add("""{"id":"background","type":"background","paint":{"background-color":"$background"}}""")
        if (base != MapBase.NONE) add("""{"id":"base","type":"raster","source":"base"}""")
        if (cadastreLines && base != MapBase.NONE) add("""{"id":"cadastre","type":"raster","source":"cadastre","minzoom":15}""")
        if (overlayTiles != null) add("""{"id":"overlay","type":"raster","source":"overlay","paint":{"raster-opacity":0.7}}""")
        add("""{"id":"parcels-fill","type":"fill","source":"saved-parcels","paint":{"fill-color":["case",["get","selected"],"#CDA449",["==",["get","kind"],"CANDIDATE"],"#F4EAD0","#567342"],"fill-opacity":["case",["get","selected"],0.55,["==",["get","kind"],"CANDIDATE"],0.30,0.38]}}""")
        add("""{"id":"parcels-line","type":"line","source":"saved-parcels","paint":{"line-color":["case",["==",["get","kind"],"CANDIDATE"],"$candidateLine","$savedLine"],"line-width":["case",["get","selected"],4,["==",["get","kind"],"CANDIDATE"],2,3]}}""")
        // #361: «Mi ubicación» — a blue dot with a soft halo, above everything else.
        if (myLocation) {
            add("""{"id":"my-location-halo","type":"circle","source":"my-location","paint":{"circle-radius":18,"circle-color":"#1D6FD8","circle-opacity":0.18}}""")
            add("""{"id":"my-location","type":"circle","source":"my-location","paint":{"circle-radius":7,"circle-color":"#1D6FD8","circle-stroke-color":"#FFFFFF","circle-stroke-width":3}}""")
        }
    }.joinToString(",")
    return """{"version":8,"sources":{$sources},"layers":[$layers]}"""
}

/** The single point «Mi ubicación» draws. */
internal fun myLocationFeature(point: GeoPoint): String =
    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.longitude},${point.latitude}]},"properties":{}}"""

/** The pixel size an overlay template asks for: 512 when its path says so, else the usual 256. */
internal fun overlayTileSize(template: String): Int = if ("/512/" in template) 512 else 256

private val MAP_FRAME_MARGIN = 24.dp
private val LABEL_HORIZONTAL_SPACING = 36.dp
private val LABEL_VERTICAL_SPACING = 24.dp
private const val LABEL_MIN_ZOOM = 15.5
private const val OVERLAY_MAX_ZOOM = 7
private const val MAX_LABELS = 60
private const val PNOA = "https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=OI.OrthoimageCoverage&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
private const val IGN_BASE = "https://www.ign.es/wmts/ign-base?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=IGNBaseTodo&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
private const val CADASTRE_WMS = "https://ovc.catastro.meh.es/cartografia/INSPIRE/spadgcwms.aspx?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=CP.CadastralParcel&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE&SRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=512&HEIGHT=512"

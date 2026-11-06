package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
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

/** Fixed-size restoration identity: never copy potentially large GeoJSON into Activity state. */
internal fun savedParcelFrameFingerprint(parcels: List<MapParcel>): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    for (parcel in parcels.filter { it.kind == MapParcelKind.SAVED }) {
        for (value in listOf(parcel.id, parcel.geometry)) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

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
    /** Optional official SIGPAC recintos, a reference only; never imported as owned parcels. */
    sigpacLines: Boolean = false,
    /** Explicit retry reloads the current sources without touching local geometry or camera. */
    tileRetryToken: Long = 0L,
    cadastreOpacity: Float = 0.65f,
    sigpacOpacity: Float = 0.5f,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val select by rememberUpdatedState(onSelected)
    val currentParcels by rememberUpdatedState(parcels)
    val tap by rememberUpdatedState(onTap)
    val ready by rememberUpdatedState(onReady)
    val snapshot by rememberUpdatedState(onMapSnapshot)
    val tileError by rememberUpdatedState(onTileError)
    val density = LocalDensity.current
    var mapHeightPx by remember { mutableStateOf(0) }
    var mapWidthPx by remember { mutableStateOf(0) }
    var footerHeightPx by remember { mutableStateOf(0) }
    var scaleValue by remember { mutableStateOf<MapScaleValue?>(null) }
    var scaleAnchor by remember { mutableStateOf<Offset?>(null) }
    var mapCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lineCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cameraMoving by remember { mutableStateOf(false) }
    fun updateScaleAnchor() {
        val parent = mapCoordinates ?: return
        val line = lineCoordinates ?: return
        if (parent.isAttached && line.isAttached) {
            scaleAnchor = parent.localPositionOf(line, Offset(line.size.width.toFloat(), line.size.height / 2f))
        }
    }
    val frameMarginPx = with(density) { MAP_FRAME_MARGIN.roundToPx() }
    val footerGapPx = with(density) { 4.dp.roundToPx() }
    val labelHorizontalSpacingPx = with(density) { LABEL_HORIZONTAL_SPACING.roundToPx() }
    val labelVerticalSpacingPx = with(density) { LABEL_VERTICAL_SPACING.roundToPx() }
    val framePadding = remember(cameraInsets, frameMarginPx, footerHeightPx, footerGapPx, mapWidthPx, mapHeightPx) {
        val availableWidth = (mapWidthPx - cameraInsets.leftPx - cameraInsets.rightPx).coerceAtLeast(0)
        val availableHeight = (mapHeightPx - cameraInsets.topPx - cameraInsets.bottomPx - footerHeightPx - footerGapPx).coerceAtLeast(0)
        // A short usable viewport must still be frameable: reserve at most one quarter
        // of each dimension per side, keeping the normal 24 dp margin whenever it fits.
        val horizontalMargin = frameMarginPx.coerceAtMost(availableWidth / 4)
        val verticalMargin = frameMarginPx.coerceAtMost(availableHeight / 4)
        MapCameraInsets(
            leftPx = cameraInsets.leftPx + horizontalMargin,
            topPx = cameraInsets.topPx + verticalMargin,
            rightPx = cameraInsets.rightPx + horizontalMargin,
            bottomPx = cameraInsets.bottomPx + footerHeightPx + footerGapPx + verticalMargin,
        )
    }
    val effectiveBase = base ?: if (imagery) MapBase.AERIAL else MapBase.NONE
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    // Frame the farmer's own parcels when they change, not on every selection tap.
    var framedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val savedFrameKey = remember(parcels) { savedParcelFrameFingerprint(parcels) }
    var savedCamera by rememberSaveable { mutableStateOf<CameraPosition?>(null) }
    // Screen positions of the labels, refreshed when the camera stops (never while it moves).
    var labelSpots by remember { mutableStateOf<List<Pair<String, IntOffset>>>(emptyList()) }
    var cameraTick by remember { mutableStateOf(0) }
    var appliedFocusToken by rememberSaveable { mutableStateOf<Long?>(null) }
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
            loaded.moveCamera(savedCamera?.let(CameraUpdateFactory::newCameraPosition)
                ?: CameraUpdateFactory.newLatLngZoom(LatLng(37.73, -3.45), 12.0))
            loaded.addOnMapClickListener { point ->
                val hits = loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(point), "parcels-fill")
                    .map { it.getStringProperty("id") }
                val selected = uniqueParcelAtPoint(currentParcels, hits, point.longitude, point.latitude)
                if (selected != null) select(selected) else tap?.invoke(point.latitude, point.longitude)
                true
            }
            loaded.addOnCameraMoveStartedListener { labelSpots = emptyList(); cameraMoving = true }
            loaded.addOnCameraIdleListener { savedCamera = loaded.cameraPosition; cameraMoving = false; cameraTick++ }
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
    LaunchedEffect(map, effectiveBase, cadastreLines, sigpacLines, overlayTiles, showsMyLocation, dark, tileRetryToken) {
        loadedStyle = null
        map?.setStyle(Style.Builder().fromJson(parcelStyle(effectiveBase, cadastreLines, overlayTiles, showsMyLocation, dark, sigpacLines, tileRetryToken, cadastreOpacity, sigpacOpacity))) { loadedStyle = it }
    }
    LaunchedEffect(loadedStyle, cadastreOpacity, sigpacOpacity) {
        // Dragging intensity changes paint only; saved geometry, camera and source caches stay live.
        loadedStyle?.getLayerAs<RasterLayer>("cadastre")?.setProperties(rasterOpacity(cadastreOpacity))
        loadedStyle?.getLayerAs<RasterLayer>("sigpac")?.setProperties(rasterOpacity(sigpacOpacity))
    }
    LaunchedEffect(loadedStyle, myLocation) {
        val style = loadedStyle ?: return@LaunchedEffect
        val point = myLocation ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>("my-location")?.setGeoJson(myLocationFeature(point))
    }
    val selection = remember(selectedId, selectedIds) { selectedIds + listOfNotNull(selectedId) }
    val data = remember(parcels, selection) { mapFeatureCollection(parcels, selection) }
    DisposableEffect(map, loadedStyle, data, parcels.map { it.id to it.geometry }, framePadding, mapWidthPx, mapHeightPx, snapshot != null) {
        val current = map
        val style = loadedStyle
        if (style == null || current == null) return@DisposableEffect onDispose {}

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
        style.getSourceAs<GeoJsonSource>("saved-parcels")?.setGeoJson(data)
        val saved = parcels.filter { it.kind == MapParcelKind.SAVED }
        val savedKey = savedFrameKey
        if (framedKey != savedKey && focus == null && footerHeightPx > 0 &&
            mapWidthPx > framePadding.leftPx + framePadding.rightPx &&
            mapHeightPx > framePadding.topPx + framePadding.bottomPx) {
            fitParcels(current, saved, framePadding)
            framedKey = savedKey
        }
        cameraTick++
        if (snapshot == null) ready()

        onDispose { listener?.let(view::removeOnDidFinishRenderingMapListener) }
    }
    LaunchedEffect(map, focus, framePadding, mapWidthPx, mapHeightPx) {
        val current = map ?: return@LaunchedEffect
        val requested = focus
        if (mapWidthPx <= framePadding.leftPx + framePadding.rightPx ||
            mapHeightPx <= framePadding.topPx + framePadding.bottomPx) return@LaunchedEffect
        // Insets change with search/IME/panels. Apply them absolutely without replaying an
        // already consumed focus or auto-framing parcels over a later user pan/zoom.
        val camera = CameraPosition.Builder(current.cameraPosition).padding(
            framePadding.leftPx.toDouble(), framePadding.topPx.toDouble(),
            framePadding.rightPx.toDouble(), framePadding.bottomPx.toDouble(),
        )
        if (requested != null && appliedFocusToken != requested.token) {
            camera.target(LatLng(requested.point.latitude, requested.point.longitude)).zoom(requested.zoom)
        }
        current.moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()))
        if (requested != null) appliedFocusToken = requested.token
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
    BoxWithConstraints(modifier.onSizeChanged { mapWidthPx = it.width; mapHeightPx = it.height }
        .onGloballyPositioned { mapCoordinates = it; updateScaleAnchor() }) {
        val showsScale = controls && maxWidth - with(density) { (cameraInsets.leftPx + cameraInsets.rightPx).toDp() } >= 240.dp
        LaunchedEffect(map, cameraTick, scaleAnchor, showsScale, density.density) {
            val current = map
            val anchor = scaleAnchor
            scaleValue = if (showsScale && current != null && anchor != null) {
                val projection = current.projection
                val right = projection.fromScreenLocation(PointF(anchor.x, anchor.y))
                projectedMapScale(with(density) { 96.dp.toPx() }) { width ->
                    right.distanceTo(projection.fromScreenLocation(PointF(anchor.x - width, anchor.y)))
                }
            } else null
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().testTag("parcel-map-view"))
        labelSpots.forEach { (label, spot) ->
            ParcelNumber(label, Modifier.offset { IntOffset(spot.x - with(density) { 14.dp.roundToPx() }, spot.y - with(density) { 11.dp.roundToPx() }) })
        }
        val usableTop = cameraInsets.topPx.coerceAtMost(mapHeightPx)
        val usableBottom = (mapHeightPx - cameraInsets.bottomPx - footerHeightPx - footerGapPx).coerceAtLeast(usableTop)
        // Keep the existing actions reachable when landscape leaves room for one row,
        // while measured panels and credits still protect their touch area.
        val availableControlHeight = usableBottom - usableTop
        val verticalControlHeight = with(density) { (48.dp * 3 + 6.dp * 2).roundToPx() }
        val minimumControlHeight = with(density) { 48.dp.roundToPx() }
        val compactControls = availableControlHeight < verticalControlHeight
        var controlsHeightPx by remember(compactControls) { mutableStateOf(0) }
        val controlModifier = Modifier.align(Alignment.TopEnd)
            .offset { IntOffset(-cameraInsets.rightPx, usableTop + ((availableControlHeight - controlsHeightPx) / 2).coerceAtLeast(0)) }
            .onSizeChanged { controlsHeightPx = it.height }
            .padding(end = 8.dp)
        val enoughControlWidth = maxWidth - with(density) { (cameraInsets.leftPx + cameraInsets.rightPx).toDp() } >= 164.dp
        if (controls && availableControlHeight >= minimumControlHeight && (!compactControls || enoughControlWidth)) {
            if (compactControls) {
                Row(controlModifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapControlButtons(map, parcels, framePadding)
                }
            } else {
                Column(controlModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapControlButtons(map, parcels, framePadding)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(start = with(density) { cameraInsets.leftPx.toDp() } + 4.dp,
                end = with(density) { cameraInsets.rightPx.toDp() } + 4.dp,
                bottom = with(density) { cameraInsets.bottomPx.toDp() } + 4.dp)
            .onSizeChanged { footerHeightPx = it.height },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MapAttribution(
            when (effectiveBase) {
                MapBase.AERIAL -> "© IGN · PNOA" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.MAP -> "© IGN · Mapa base" + if (cadastreLines) " · © DG Catastro" else ""
                MapBase.NONE -> "Límites guardados en el teléfono"
            } + (if (sigpacLines && effectiveBase != MapBase.NONE) " · SIGPAC · FEGA/MAPA" else "")
                + (if (effectiveBase != MapBase.NONE && overlayTiles != null) overlayAttribution?.let { " · $it" } ?: "" else ""),
            modifier = Modifier.weight(1f)
                .semantics { contentDescription = "Atribución del mapa" },
        )
        if (showsScale) MapScaleBar(scaleValue, visible = !cameraMoving && usableBottom > usableTop) {
            lineCoordinates = it
            updateScaleAnchor()
        }
        }
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
private fun MapControlButtons(map: MapLibreMap?, parcels: List<MapParcel>, framePadding: MapCameraInsets) {
    MapButton("+", "Acercar") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
    MapButton("−", "Alejar") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
    MapButton("⤢", "Encuadrar mis parcelas") {
        map?.let { fitParcels(it, parcels.filter { p -> p.kind == MapParcelKind.SAVED }.ifEmpty { parcels }, framePadding) }
    }
}
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
internal fun parcelStyle(base: MapBase, cadastreLines: Boolean, overlayTiles: String? = null, myLocation: Boolean = false, dark: Boolean = false, sigpacLines: Boolean = false, tileRetryToken: Long = 0L, cadastreOpacity: Float = 0.65f, sigpacOpacity: Float = 0.5f): String {
    require(cadastreOpacity.isFinite() && cadastreOpacity in 0f..1f && sigpacOpacity.isFinite() && sigpacOpacity in 0f..1f)
    // Only an explicit retry bypasses a failed native resource/cache entry. Ordinary URLs and
    // the offline cache remain intact; these official KVP services accept additional parameters.
    fun officialTiles(template: String) = if (tileRetryToken == 0L) template else "$template&magina_retry=$tileRetryToken"
    // Theme our own canvas only; layer opacity mixes original official pixels with the photo.
    val background = if (dark) "#171914" else "#F3F1E6"
    val savedLine = if (dark && base == MapBase.NONE) "#B6D39E" else "#25371C"
    val candidateLine = if (dark && base == MapBase.NONE) "#E2CC90" else "#8A6A1F"
    val selectedLine = "#D45A12"
    val sources = buildList {
        add(""""saved-parcels":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}""")
        if (myLocation) add(""""my-location":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}""")
        when (base) {
            MapBase.AERIAL -> add(""""base":{"type":"raster","tileSize":256,"maxzoom":19,"tiles":["${officialTiles(PNOA)}"]}""")
            MapBase.MAP -> add(""""base":{"type":"raster","tileSize":256,"maxzoom":17,"tiles":["${officialTiles(IGN_BASE)}"]}""")
            MapBase.NONE -> Unit
        }
        if (cadastreLines && base != MapBase.NONE) {
            add(""""cadastre":{"type":"raster","tileSize":512,"tiles":["${officialTiles(CADASTRE_WMS)}"]}""")
        }
        if (sigpacLines && base != MapBase.NONE) {
            add(""""sigpac":{"type":"raster","tileSize":512,"tiles":["${officialTiles(SIGPAC_WMS)}"]}""")
        }
        // Radar pictures are published up to a low zoom; MapLibre enlarges them beyond it. #360: a
        // 512 px picture is declared at 512, so it is drawn at its own resolution, not stretched.
        if (base != MapBase.NONE) overlayTiles?.let { add(""""overlay":{"type":"raster","tileSize":${overlayTileSize(it)},"maxzoom":$OVERLAY_MAX_ZOOM,"tiles":["$it"]}""") }
    }.joinToString(",")
    val layers = buildList {
        add("""{"id":"background","type":"background","paint":{"background-color":"$background"}}""")
        if (base != MapBase.NONE) add("""{"id":"base","type":"raster","source":"base"}""")
        if (cadastreLines && base != MapBase.NONE) add("""{"id":"cadastre","type":"raster","source":"cadastre","minzoom":15,"paint":{"raster-opacity":$cadastreOpacity}}""")
        if (sigpacLines && base != MapBase.NONE) add("""{"id":"sigpac","type":"raster","source":"sigpac","minzoom":15,"paint":{"raster-opacity":$sigpacOpacity}}""")
        if (overlayTiles != null && base != MapBase.NONE) add("""{"id":"overlay","type":"raster","source":"overlay","paint":{"raster-opacity":0.7}}""")
        add("""{"id":"parcels-fill","type":"fill","source":"saved-parcels","paint":{"fill-color":["case",["get","selected"],"#F59E0B",["==",["get","kind"],"CANDIDATE"],"#F4EAD0","#567342"],"fill-opacity":["case",["get","selected"],0.38,["==",["get","kind"],"CANDIDATE"],0.20,0.25]}}""")
        add("""{"id":"parcels-selection-halo","type":"line","source":"saved-parcels","filter":["==",["get","selected"],true],"paint":{"line-color":"#FFFFFF","line-width":9}}""")
        add("""{"id":"parcels-line","type":"line","source":"saved-parcels","paint":{"line-color":["case",["get","selected"],"$selectedLine",["==",["get","kind"],"CANDIDATE"],"$candidateLine","$savedLine"],"line-width":["case",["get","selected"],5.5,["==",["get","kind"],"CANDIDATE"],2,2.5]}}""")
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
private const val SIGPAC_WMS = "https://sigpac-hubcloud.es/wms/ows?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=AU.Sigpac:recinto&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE&CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=512&HEIGHT=512"

package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilTrend
import com.isivoltpro.maginaolivo.domain.market.OilTrends
import com.isivoltpro.maginaolivo.domain.market.TrendDirection
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * «Mercado del aceite» preview on Inicio: only the official weekly trend from the Junta, compacted
 * to a graph so it is readable at a glance. The daily AOVE.net widget lives on the detail screen;
 * the two sources and cadences are never combined.
 */
@Composable
internal fun OilMarketCard(
    official: FeedState<OilMarketSeries>,
    /** Phase 20D-3: the market screen with the 12-week chart, once there are official weeks. */
    onOpen: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home-market"),
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(MoSpacing.sm), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoIconBadge(MoIcons.Euro)
                Text("Mercado del aceite", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
            }
            when (official) {
                is FeedState.Value -> {
                    OilMarketChart(official.value, compact = true)
                    val latest = OilTrends.all(official.value).maxByOrNull { it.latest.periodEnd }?.latest
                    Text(
                        "${latest?.let { "Semana ${OilTrends.week(it)} · " }.orEmpty()}${official.value.sourceName} · €/kg · precio en almazara",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("home-market-official-source"),
                    )
                    if (official.stale) MoStatusChip("Dato antiguo", tone = MoStatusTone.Warning, modifier = Modifier.testTag("home-market-official-stale"))
                }
                FeedState.NotConfigured -> Note(
                    "Sin precios oficiales guardados todavía.",
                    "home-market-official-not-configured",
                )
                FeedState.NoLocation, FeedState.Unavailable ->
                    Note("Aún sin datos oficiales. Se actualizará cuando haya conexión.", "home-market-official-unavailable")
            }
            if (onOpen != null) {
                MoSecondaryButton("Ver mercado", onOpen, Modifier.fillMaxWidth().testTag("home-market-open"))
            }
        }
    }
}

@Composable
internal fun OfficialTrend(official: FeedState.Value<OilMarketSeries>) {
    val series = official.value
    val trends = OilTrends.all(series)
    val latest = trends.maxByOrNull { it.latest.periodEnd }?.latest ?: return
    // A category the source has not published for the newest week keeps its own week on the row,
    // so an older price is never read under the newest week's footer.
    trends.forEach { trend -> TrendRow(trend, lagging = trend.latest.periodEnd.isBefore(latest.periodEnd)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Text(
            "Semana ${OilTrends.week(latest)} (${latest.periodStart.format(DAY)}–${latest.periodEnd.format(DAY)}) · " +
                "${series.sourceName} · precio en almazara",
            style = MaterialTheme.typography.bodySmall,
            color = MoSurfaceTokens.secondaryText,
            modifier = Modifier.weight(1f).testTag("home-market-official-source"),
        )
        if (official.stale) MoStatusChip("Dato antiguo", tone = MoStatusTone.Warning, modifier = Modifier.testTag("home-market-official-stale"))
    }
}

@Composable
private fun TrendRow(trend: OilTrend, lagging: Boolean) {
    val tint = when (trend.direction) {
        TrendDirection.UP -> MoColors.current.successText
        TrendDirection.DOWN -> MoColors.current.errorText
        else -> MoSurfaceTokens.secondaryText
    }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("home-market-trend"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(trend.category.label, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText, modifier = Modifier.weight(1f))
        Text(OilTrends.euros(trend.latest.valueEurPerKg), style = MaterialTheme.typography.titleSmall, color = MoColors.current.bodyText)
        val change = OilTrends.label(trend).removePrefix(trend.category.label).trim().takeIf { trend.direction != null }
        val ownWeek = "semana ${OilTrends.week(trend.latest)}"
        val text = when {
            !lagging -> change.orEmpty()
            change == null -> ownWeek
            else -> change.replace("esta semana", "· $ownWeek")
        }
        Text(
            "  $text",
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

@Composable
internal fun Note(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag(tag))
}

/**
 * #271 — «Pulso diario»: AOVE.net's free widget
 * (https://aove.net/insertar-widget-precio-aceite-oliva/), loaded as the publisher serves it: no
 * cache (never an old page passed off as today's), no file or app bridge, links open in the
 * browser.
 *
 * It is a **non-blocking** block: the official weekly prices of the Junta are already on screen
 * above it, read from this phone's cache. While the widget loads, the block is a small strip that
 * says so; if it fails, is empty or takes too long, the strip is replaced by one line with
 * «Reintentar» and «Ver en AOVE.net». It never reserves a tall empty area — the defect this fixes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AoveNetPulse() {
    val context = LocalContext.current
    // Recreating the WebView from scratch is what «Reintentar» does, so a half-loaded page is
    // never retried in place.
    var attempt by remember { mutableStateOf(0) }
    var online by remember { mutableStateOf(isOnline(context)) }
    var failed by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf<Int?>(null) }
    var elapsedMs by remember { mutableStateOf(0L) }

    // When a connection comes (back), the widget is tried again instead of staying "sin conexión".
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
            }

            override fun onLost(network: Network) {
                online = isOnline(context)
            }
        }
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess && connectivity != null
        onDispose { if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }

    // #355: on a short phone the widget must not push the official weekly prices off the screen.
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    // Waiting has an end: past the limit the block gives up instead of holding the space open.
    LaunchedEffect(attempt, online) {
        elapsedMs = 0L
        measured = null
        failed = false
        while (elapsedMs < MarketPulse.TIMEOUT_MS && measured == null && !failed) {
            delay(PULSE_TICK_MS)
            elapsedMs += PULSE_TICK_MS
        }
    }

    val outcome = MarketPulse.outcome(online, failed, measured, elapsedMs)
    PulseBlock(
        outcome = outcome,
        heightDp = MarketPulse.blockHeightDp(outcome, measured, screenHeightDp),
        onRetry = {
            online = isOnline(context)
            attempt += 1
        },
        onOpenSource = { openInBrowser(context, Uri.parse(AOVE_NET_DETAIL)) },
        web = if (outcome == PulseOutcome.UNAVAILABLE) {
            null
        } else {
            { modifier ->
                // A new attempt builds a new WebView; without this key Compose would reuse the
                // failed one and «Reintentar» would do nothing.
                key(attempt) {
                AndroidView(
                    modifier = modifier.testTag("home-market-pulse-web"),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    openInBrowser(viewContext, request.url)
                                    return true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    // The widget fills its prices in after load, sometimes slowly on a
                                    // rural connection: measure until the height holds still, within
                                    // the same time limit the block itself waits.
                                    var waited = 0L
                                    var last = -1
                                    var steady = 0
                                    val measure = object : Runnable {
                                        override fun run() {
                                            val height = view.contentHeight // CSS px = dp at default zoom
                                            steady = if (height > 0 && height == last) steady + 1 else 0
                                            last = height
                                            if (steady >= PULSE_STEADY_READINGS) {
                                                // Only a measurement big enough to be the three prices
                                                // becomes height; anything else is an empty page.
                                                if (MarketPulse.contentHeightDp(height) != null) measured = height else failed = true
                                                return
                                            }
                                            waited += PULSE_MEASURE_EVERY_MS
                                            if (waited < MarketPulse.TIMEOUT_MS) view.postDelayed(this, PULSE_MEASURE_EVERY_MS)
                                        }
                                    }
                                    view.postDelayed(measure, PULSE_MEASURE_EVERY_MS)
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) failed = true
                                }

                                // An HTTP error page loads "successfully": without this, a 404 or a
                                // 403 from the publisher looked like content and kept its space.
                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: android.webkit.WebResourceResponse,
                                ) {
                                    if (request.isForMainFrame) failed = true
                                }

                                /** A dead render process leaves a blank view; it must not take the app with it. */
                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    failed = true
                                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                                    view.destroy()
                                    return true
                                }
                            }
                            loadUrl(AOVE_NET_WIDGET)
                        }
                    },
                )
                }
            }
        },
    )
}

/**
 * The three states of the pulse block, with no WebView in them so they can be driven in a test.
 * [web] is the widget itself; it is null when there is nothing to show, and then the block keeps
 * no height at all.
 */
@Composable
internal fun PulseBlock(
    outcome: PulseOutcome,
    heightDp: Int,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit,
    web: (@Composable (Modifier) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs), modifier = Modifier.testTag("home-market-pulse")) {
        when (outcome) {
            PulseOutcome.UNAVAILABLE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    Text(
                        "Precio diario no disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("home-market-pulse-unavailable"),
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("home-market-pulse-retry")) { Text("Reintentar") }
                    TextButton(onClick = onOpenSource, modifier = Modifier.testTag("home-market-pulse-detail")) { Text("Ver en AOVE.net") }
                }
            }
            else -> {
                Box(Modifier.fillMaxWidth().height(heightDp.dp)) {
                    // The widget keeps a real size while it loads — a WebView with no height never
                    // paints — and is simply not visible until it has drawn its prices.
                    web?.invoke(Modifier.matchParentSize().alpha(if (outcome == PulseOutcome.SHOWN) 1f else 0f))
                    if (outcome == PulseOutcome.LOADING) {
                        Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Buscando el precio diario…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoSurfaceTokens.secondaryText,
                                modifier = Modifier.testTag("home-market-pulse-loading"),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Fuente: AOVE.net · referencia orientativa diaria, no es una cotización oficial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSource, modifier = Modifier.testTag("home-market-pulse-detail")) {
                        Text("Ver detalle")
                    }
                }
            }
        }
    }
}

private fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun openInBrowser(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone: the card keeps its own text.
    }
}

private const val PULSE_MEASURE_EVERY_MS = 400L
private const val PULSE_STEADY_READINGS = 3 // the height held still for more than a second
private const val PULSE_TICK_MS = 400L
private const val AOVE_NET_WIDGET = "https://aove.net/widget/precio-aceite-oliva-hoy/"
private const val AOVE_NET_DETAIL = "https://aove.net/"
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))

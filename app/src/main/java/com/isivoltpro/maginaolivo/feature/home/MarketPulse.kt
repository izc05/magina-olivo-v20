package com.isivoltpro.maginaolivo.feature.home

/** What «Pulso diario» is doing right now. */
enum class PulseOutcome {
    /** The publisher's widget is being fetched; the block stays small and says so. */
    LOADING,

    /** It drew real content: the block takes the height the page measured, bounded. */
    SHOWN,

    /** Offline, failed, empty or too slow: no widget area at all, only a small notice. */
    UNAVAILABLE,
}

/**
 * #271 — the rules of the daily pulse block, with no Android in them so they can be tested.
 *
 * The defect this fixes: the block reserved 320 dp before the page had drawn anything and could
 * grow to 560 dp, and only a main-frame network error counted as a failure. An HTTP error page, a
 * dead render process, an empty widget or a very slow connection therefore left a tall white
 * rectangle on «Mercado» for as long as the screen stayed open.
 *
 * Two rules follow from that, and both are checked here instead of in the UI:
 *
 * 1. **Nothing is ever reserved for content that is not there.** Only a measurement that is tall
 *    enough to be the widget turns into height; anything smaller is an empty page, not content.
 * 2. **Waiting has an end.** Past [TIMEOUT_MS] the block gives up and becomes a one-line notice
 *    with «Reintentar», instead of waiting forever with the space already taken.
 *
 * The official weekly prices of the Junta never depend on any of this: they are read from the
 * phone's own cache and drawn above, whatever the pulse does.
 */
object MarketPulse {
    /**
     * While it loads, the widget still needs a real size to render into — a WebView with no height
     * never paints — so the block keeps this much and shows what it is doing on top of it.
     */
    const val LOADING_HEIGHT_DP = 112

    /** Below this, the page drew a header or nothing: it is not the three prices. */
    const val MIN_CONTENT_HEIGHT_DP = 96

    /**
     * The widget of the publisher is three price cards plus its date and source lines; it measures
     * around 600 dp. The block follows that measurement so the last card is never cut in half, and
     * stops here, because past this height the page is something else and is not worth the screen.
     */
    const val MAX_CONTENT_HEIGHT_DP = 640

    /** A rural connection may be slow, but not endless. */
    const val TIMEOUT_MS = 12_000L

    /**
     * The height to give the widget for a page that measured [measuredCssPx] (CSS pixels, which are
     * dp at the default zoom), or null when that measurement is not usable content.
     */
    fun contentHeightDp(measuredCssPx: Int): Int? =
        if (measuredCssPx < MIN_CONTENT_HEIGHT_DP) null else measuredCssPx.coerceAtMost(MAX_CONTENT_HEIGHT_DP)

    /**
     * [measuredCssPx] is the last height the page reported, or null if it has reported none.
     * [failed] covers every way the page can be unusable: no network error, an HTTP error page, a
     * dead render process or an explicit load error.
     */
    fun outcome(online: Boolean, failed: Boolean, measuredCssPx: Int?, elapsedMs: Long): PulseOutcome = when {
        !online || failed -> PulseOutcome.UNAVAILABLE
        measuredCssPx != null && contentHeightDp(measuredCssPx) != null -> PulseOutcome.SHOWN
        elapsedMs >= TIMEOUT_MS -> PulseOutcome.UNAVAILABLE
        else -> PulseOutcome.LOADING
    }

    /** The height the block occupies for an [outcome]; the notice has no fixed height of its own. */
    fun blockHeightDp(outcome: PulseOutcome, measuredCssPx: Int?): Int = when (outcome) {
        PulseOutcome.SHOWN -> measuredCssPx?.let(::contentHeightDp) ?: LOADING_HEIGHT_DP
        PulseOutcome.LOADING -> LOADING_HEIGHT_DP
        PulseOutcome.UNAVAILABLE -> 0
    }
}

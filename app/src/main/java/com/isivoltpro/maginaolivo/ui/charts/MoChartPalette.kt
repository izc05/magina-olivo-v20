package com.isivoltpro.maginaolivo.ui.charts

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color
import com.isivoltpro.maginaolivo.domain.analytics.ExpenseSlice
import com.isivoltpro.maginaolivo.ui.theme.MoColors

/**
 * #355 Fase 2, bloque B — the colours of the charts.
 *
 * Nothing new is invented here: every colour is one the app already uses, taken from the palette so
 * that light and dark are decided in one place and the screens keep the cream and olive identity.
 * Two rules decide which one goes where:
 *
 * 1. **Meaning picks the colour.** Jornales take the colour the app already gives to labour,
 *    maquinaria the one it gives to treatments and machinery, and so on. The same thing is the
 *    same colour wherever the farmer meets it.
 * 2. **Neighbours differ in hue, not only in lightness**, so the ring still reads for someone who
 *    does not separate red from green, and in black and white.
 */
object MoChartPalette {
    /** Jornales, maquinaria, combustible and the rest — four distinct hues. */
    @Composable
    fun cost(slice: ExpenseSlice): Color = with(MoColors.current) {
        when (slice) {
            ExpenseSlice.LABOUR -> labourText      // terracotta: people
            ExpenseSlice.EQUIPMENT -> treatmentText // teal: machines and treatments
            ExpenseSlice.FUEL -> infoText          // blue-grey: fuel
            ExpenseSlice.OTHER -> moneyText        // muted gold: money with no other name
        }
    }

    /**
     * Parcels, in the order they are drawn. The sequence runs through the olive, the teal, the
     * gold and the earth before it repeats, so adjoining slices never share a hue on a farm with a
     * handful of parcels — and a farm with many is still read from the legend, not the ring.
     */
    @Composable
    fun parcel(index: Int): Color = with(MoColors.current) {
        val wheel = listOf(selectionText, treatmentText, goldAccent, earthText, infoText, labourText)
        wheel[index.mod(wheel.size)]
    }

    /**
     * The kilos that belong to no parcel. Deliberately the quietest colour of the ring: they are
     * not a parcel, and the eye should not read them as one more.
     */
    @Composable
    fun unassigned(): Color = MoColors.current.secondaryText
}

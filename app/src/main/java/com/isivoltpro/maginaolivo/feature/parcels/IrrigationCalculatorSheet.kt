package com.isivoltpro.maginaolivo.feature.parcels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationPlan
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationPlanInput
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationPlanner
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariff
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariffType
import com.isivoltpro.maginaolivo.ui.components.MoLabeledValue
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")

private fun number(value: Double, decimals: Int = 2): String =
    NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = decimals }.format(value)

internal fun decimalInput(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun durationLabel(minutes: Int): String = "${minutes / 60} h ${minutes % 60} min"

/** Euros typed by the farmer to euro cents, without binary rounding. */
internal fun eurosToMinor(text: String): Long? = runCatching {
    BigDecimal(text.trim().replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()?.takeIf { it >= 0 }

private fun euros(minor: Long): String = NumberFormat.getCurrencyInstance(SPANISH).format(minor / 100.0)

/** Everything typed here stays on this screen: the calculator neither reads nor writes records. */
@Composable
internal fun IrrigationCalculatorSheet(initialTrees: Int?) {
    var trees by rememberSaveable { mutableStateOf(initialTrees?.toString().orEmpty()) }
    var emitters by rememberSaveable { mutableStateOf("") }
    var flow by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var weekly by rememberSaveable { mutableStateOf("") }
    var tariffName by rememberSaveable { mutableStateOf<String?>(null) }
    var price by rememberSaveable { mutableStateOf("") }
    val tariffType = tariffName?.let(WaterTariffType::valueOf)
    val duration = (hours.ifBlank { "0" }.toIntOrNull() ?: -1).let { h ->
        (minutes.ifBlank { "0" }.toIntOrNull() ?: -1).let { m -> if (h < 0 || m !in 0..59 || h > 200) -1 else h * 60 + m }
    }
    val priceMinor = eurosToMinor(price)
    val plan = IrrigationPlanner.calculate(
        IrrigationPlanInput(
            trees = trees.toIntOrNull() ?: 0,
            emittersPerTree = emitters.toIntOrNull() ?: 0,
            emitterFlowLph = decimalInput(flow) ?: 0.0,
            durationMinutes = duration,
            targetLitersPerTree = target.takeIf { it.isNotBlank() }?.let { decimalInput(it) ?: -1.0 },
            irrigationsPerWeek = weekly.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() ?: -1 },
            tariff = when {
                tariffType == WaterTariffType.FREE -> WaterTariff(WaterTariffType.FREE)
                tariffType != null && priceMinor != null -> WaterTariff(tariffType, variablePricePerUnitMinor = priceMinor)
                else -> null
            },
        ),
    )
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(
        Modifier.padding(horizontal = MoSpacing.screen).padding(bottom = MoSpacing.lg).verticalScroll(rememberScrollState())
            .testTag("irrigation-calculator"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text("Calculadora de riego", style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
        Text("Cálculo teórico. No se guarda ni registra un riego.", style = MaterialTheme.typography.bodyMedium,
            color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("irrigation-calculator-theoretical"))
        MoTextField(trees, { trees = it.filter(Char::isDigit).take(7) }, "Olivos regados", Modifier.fillMaxWidth().testTag("calc-trees"), keyboardOptions = numeric)
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoTextField(emitters, { emitters = it.filter(Char::isDigit).take(3) }, "Goteros por olivo", Modifier.weight(1f).testTag("calc-emitters"), keyboardOptions = numeric)
            MoTextField(flow, { flow = it.take(8) }, "Caudal gotero (L/h)", Modifier.weight(1f).testTag("calc-flow"), keyboardOptions = decimal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoTextField(hours, { hours = it.filter(Char::isDigit).take(3) }, "Horas", Modifier.weight(1f).testTag("calc-hours"), keyboardOptions = numeric)
            MoTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, "Minutos", Modifier.weight(1f).testTag("calc-minutes"), keyboardOptions = numeric)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoTextField(target, { target = it.take(8) }, "Dosis objetivo (L/olivo)", Modifier.weight(1f).testTag("calc-target"), keyboardOptions = decimal)
            MoTextField(weekly, { weekly = it.filter(Char::isDigit).take(2) }, "Riegos por semana", Modifier.weight(1f).testTag("calc-weekly"), keyboardOptions = numeric)
        }
        Text("Tarifa", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            listOf(WaterTariffType.PER_M3 to "€/m³", WaterTariffType.PER_HOUR to "€/hora", WaterTariffType.FREE to "Sin coste").forEach { (type, label) ->
                FilterChip(selected = tariffType == type, onClick = { tariffName = type.name.takeIf { it != tariffName } }, label = { Text(label) },
                    modifier = Modifier.testTag("calc-tariff-${type.name}"))
            }
        }
        if (tariffType != null && tariffType != WaterTariffType.FREE) {
            MoTextField(price, { price = it.take(8) }, "Precio (€)", Modifier.fillMaxWidth().testTag("calc-price"), keyboardOptions = decimal)
        }
        if (plan == null) Text("Indica olivos, goteros y caudal para ver el cálculo.", style = MaterialTheme.typography.bodyMedium,
            color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("calc-incomplete"))
        else PlanResults(plan, duration)
    }
}

@Composable
private fun PlanResults(plan: IrrigationPlan, durationMinutes: Int) {
    Column(Modifier.testTag("calc-results"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Text("Calculado por Mágina Olivo", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
        MoLabeledValue("Caudal total", "${number(plan.totalFlowLph, 0)} L/h · ${number(plan.totalFlowM3PerHour)} m³/h")
        MoLabeledValue("Duración del riego", durationLabel(durationMinutes))
        MoLabeledValue("Litros por olivo", "${number(plan.dose.litersPerTree)} L")
        MoLabeledValue("Volumen por riego", "${number(plan.dose.theoreticalVolumeM3)} m³")
        plan.minutesForTarget?.let { MoLabeledValue("Duración para la dosis objetivo", durationLabel(it)) }
        plan.weeklyVolumeM3?.let { MoLabeledValue("Consumo previsto semanal", "${number(it)} m³") }
        plan.costPerIrrigationMinor?.let { MoLabeledValue("Coste estimado por riego", euros(it)) }
        plan.weeklyCostMinor?.let { MoLabeledValue("Coste estimado semanal", euros(it)) }
    }
}

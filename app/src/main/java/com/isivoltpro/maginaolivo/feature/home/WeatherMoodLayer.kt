package com.isivoltpro.maginaolivo.feature.home

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.weather.WeatherMood
import kotlin.math.PI
import kotlin.math.sin

/**
 * Phase 20C — a light layer over Inicio's photo that follows the current weather. Decorative
 * only: low opacity, under the header text, nothing when the weather is unknown or out of date,
 * and still (one frame) when the phone asks for reduced motion or is a low-memory device.
 */
@Composable
fun WeatherMoodLayer(mood: WeatherMood?, modifier: Modifier = Modifier, animate: Boolean = rememberMotionAllowed()) {
    if (mood == null) return
    val progress: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "weather-mood").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodMillis(mood), easing = LinearEasing)),
            label = "weather-mood-progress",
        )
    } else {
        remember { mutableFloatStateOf(STILL_FRAME) }
    }
    val motion = if (animate) "animated" else "static"
    // The progress is read while drawing only, so a frame redraws without recomposing Inicio.
    Canvas(modifier.testTag("home-weather-mood-${mood.name.lowercase()}-$motion")) {
        drawMood(mood, progress.value, animate)
    }
}

/** Animations off in the system settings, or a low-memory phone: the layer stays still. */
@Composable
fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        scale > 0f && !lowRam
    }
}

private fun periodMillis(mood: WeatherMood): Int = when (mood) {
    WeatherMood.RAIN, WeatherMood.STORM -> 1_400
    WeatherMood.WIND -> 2_600
    WeatherMood.CLEAR -> 6_000
    WeatherMood.CLOUDY, WeatherMood.FOG -> 18_000
}

private fun DrawScope.drawMood(mood: WeatherMood, t: Float, animate: Boolean) {
    when (mood) {
        WeatherMood.CLEAR -> sunGlow(t)
        WeatherMood.CLOUDY -> clouds(t)
        WeatherMood.RAIN -> rain(t, alpha = 0.30f)
        WeatherMood.WIND -> wind(t)
        WeatherMood.FOG -> fog(t)
        WeatherMood.STORM -> {
            drawRect(Color(0xFF1B2416).copy(alpha = 0.18f))
            rain(t, alpha = 0.34f)
            // A soft glow near the end of each cycle, never a strobe and never when still.
            if (animate && t in 0.90f..0.94f) drawRect(Color.White.copy(alpha = 0.10f))
        }
    }
}

private fun DrawScope.sunGlow(t: Float) {
    val pulse = 0.16f + 0.05f * sin(2 * PI * t).toFloat()
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFE3A3).copy(alpha = pulse), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.12f),
            radius = size.minDimension * 0.9f,
        ),
        radius = size.minDimension * 0.9f,
        center = Offset(size.width * 0.85f, size.height * 0.12f),
    )
}

private fun DrawScope.clouds(t: Float) {
    val white = Color.White.copy(alpha = 0.14f)
    listOf(0.10f to 0.18f, 0.55f to 0.08f, 0.30f to 0.30f).forEachIndexed { i, (x, y) ->
        val w = size.width * (0.55f - i * 0.08f)
        val h = size.height * 0.16f
        val left = ((x + t * (0.25f + i * 0.1f)) % 1.3f - 0.3f) * size.width
        drawOval(white, topLeft = Offset(left, size.height * y), size = Size(w, h))
    }
}

private fun DrawScope.rain(t: Float, alpha: Float) {
    val drop = Color.White.copy(alpha = alpha)
    val length = size.height * 0.06f
    for (i in 0 until DROPS) {
        // Fixed pseudo-random columns so the pattern is stable frame to frame.
        val x = ((i * 73 + 17) % 100) / 100f * size.width
        val phase = ((i * 37) % 100) / 100f
        val y = ((phase + t) % 1f) * (size.height + length) - length
        drawLine(drop, Offset(x, y), Offset(x - length * 0.3f, y + length), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.wind(t: Float) {
    val streak = Color.White.copy(alpha = 0.22f)
    for (i in 0 until 7) {
        val y = size.height * (0.12f + i * 0.1f)
        val span = size.width * (0.25f + (i % 3) * 0.08f)
        val x = ((((i * 29) % 100) / 100f + t) % 1.2f - 0.2f) * size.width
        drawLine(streak, Offset(x, y), Offset(x + span, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.fog(t: Float) {
    for (i in 0 until 3) {
        val top = size.height * (0.15f + i * 0.22f)
        val shift = (t + i / 3f) % 1f * size.width * 0.3f
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                startX = -size.width * 0.3f + shift,
                endX = size.width * 1.0f + shift,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, size.height * 0.14f),
        )
    }
}

private const val DROPS = 42
private const val STILL_FRAME = 0.35f

package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

/**
 * Design v3 (CR-004, owner mockups 2026-09-23): photographic header that bleeds to the top
 * edge with rounded lower corners, a dark fade and the title in the editorial serif.
 *
 * [imageModel] is the farmer's own photo when there is one; otherwise the bundled olive-grove
 * photograph, which is decorative and never presented as the farmer's land.
 */
@Composable
fun MoPhotoHeader(
    title: String,
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    location: String? = null,
    caption: String? = null,
    height: Dp = 232.dp,
    /** Share of the screen height; wins over [height] so the photo scales with the phone. */
    heightFraction: Float? = null,
    trailing: (@Composable () -> Unit)? = null,
    overlay: (@Composable ColumnScope.() -> Unit)? = null,
    top: (@Composable BoxScope.() -> Unit)? = null,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val resolved = heightFraction?.let { (screenHeight * it).dp.coerceIn(240.dp, 460.dp) } ?: height
    Box(
        modifier
            .fillMaxWidth()
            .height(resolved)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.onboarding_welcome_olive_grove),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(MoOliveDark.copy(alpha = 0.35f), Color.Transparent, MoOliveDark.copy(alpha = 0.82f)),
                ),
            ),
        )
        top?.invoke(this)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MoWarmWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // A status chip on a photo needs its own light backing to stay legible.
                trailing?.let { chip ->
                    Surface(shape = RoundedCornerShape(50), color = MoWarmWhite.copy(alpha = 0.92f)) {
                        Box(Modifier.padding(2.dp)) { chip() }
                    }
                }
            }
            location?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(MoIcons.Location, contentDescription = null, tint = MoWarmWhite, modifier = Modifier.size(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MoWarmWhite)
                }
            }
            caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MoWarmWhite) }
            overlay?.invoke(this)
        }
    }
}

/** One figure of a stat strip or tile. [value] is already formatted; "—" when unknown. */
data class MoStat(val label: String, val value: String, val icon: ImageVector)

/** A white strip of 2–4 figures with dividers, as under the headers of the mockups. */
@Composable
fun MoStatStrip(stats: List<MoStat>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(vertical = MoSpacing.sm, horizontal = MoSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(MoOutline))
                Row(
                    Modifier.weight(1f).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (stats.size <= 3) MoIconBadge(stat.icon, size = 36)
                    Column {
                        Text(stat.label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary, maxLines = 1)
                        Text(
                            stat.value,
                            style = MaterialTheme.typography.titleMedium,
                            color = MoInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Square figure tile with the icon on top (Parcel detail: Superficie · Olivos · Variedad · Riego). */
@Composable
fun MoStatTile(stat: MoStat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(
            Modifier.padding(vertical = MoSpacing.sm, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MoIconBadge(stat.icon, size = 34)
            Text(stat.label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary, maxLines = 1)
            Text(
                stat.value,
                style = MaterialTheme.typography.titleMedium,
                color = MoInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Label over value, "—" when the farmer has not told us. */
@Composable
fun MoLabeledValue(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
        Text(value ?: "—", style = MaterialTheme.typography.bodyLarge, color = MoInk)
    }
}

/** White card with an icon badge, a serif title and an optional trailing action. */
@Composable
fun MoSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    iconContainer: Color? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                val tone = MoIconTone.of(icon)
                MoIconBadge(icon, tint = iconTint ?: tone.tint, container = iconContainer ?: tone.container)
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MoOliveDark,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                action?.invoke()
            }
            content()
        }
    }
}

/** Brand line for photo headers: leaf mark and "Mágina Olivo" in the editorial serif, in white. */
@Composable
fun MoPhotoBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(MoIcons.Leaf, contentDescription = null, tint = MoWarmWhite, modifier = Modifier.size(26.dp))
        Text("Mágina Olivo", style = MaterialTheme.typography.headlineMedium, color = MoWarmWhite)
    }
}

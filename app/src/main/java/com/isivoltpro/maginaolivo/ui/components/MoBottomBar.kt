package com.isivoltpro.maginaolivo.ui.components

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing

data class MoBottomBarItem(
    val label: String,
    val symbol: String,
    val isPrimaryAction: Boolean = false,
    /** UI polish v2: a line icon replaces the text symbol when given. */
    val icon: ImageVector? = null,
)

@Composable
fun MoBottomBar(
    items: List<MoBottomBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** CR-014: separate Button, outside the selectable tab list. */
    onAddRecord: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MoSurfaceTokens.cardSurface,
        contentColor = MoColors.current.primaryText,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MoSpacing.xs, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (index == 2 && onAddRecord != null) {
                    Box(Modifier.weight(0.75f), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(52.dp).clickable(role = Role.Button, onClick = onAddRecord)
                                .semantics { contentDescription = "Añadir registro" }.testTag("bottom-add-record"),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(MoIcons.Plus, null, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
                val selected = index == selectedIndex
                val color = if (selected || item.isPrimaryAction) MoColors.current.selectionText else MoSurfaceTokens.secondaryText

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelected(index) },
                        )
                        .testTag("bottom-${item.label}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (item.isPrimaryAction) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MoColors.current.primaryButton,
                            contentColor = MoColors.current.onPrimaryButton,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (item.icon != null) {
                                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MoColors.current.onPrimaryButton)
                                } else {
                                    Text(
                                        text = item.symbol,
                                        modifier = Modifier.clearAndSetSemantics { },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    } else {
                        // The selected item gets a soft pill as well as colour and weight,
                        // so the active root never depends on colour alone.
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MoColors.current.actionTint else Color.Transparent,
                            contentColor = color,
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                                if (item.icon != null) {
                                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
                                } else {
                                    Text(
                                        text = item.symbol,
                                        modifier = Modifier.clearAndSetSemantics { },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = color,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

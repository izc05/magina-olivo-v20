package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors

private data class BottomItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MoBottomBarPreview(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomItem("Inicio", Icons.Outlined.Home),
        BottomItem("Mi Olivar", Icons.Outlined.List),
        BottomItem("Registrar", Icons.Filled.Add),
        BottomItem("Calendario", Icons.Outlined.CalendarMonth),
        BottomItem("Perfil", Icons.Outlined.Person),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isRegister = item.label == "Registrar"
            val isSelected = item.label == selected
            val contentColor = if (isSelected || isRegister) {
                OlivarColors.Olive700
            } else {
                OlivarColors.Charcoal500
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = item.label
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .clickable { onSelected(item.label) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isRegister) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                color = OlivarColors.Olive700,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = "Registrar",
                            tint = OlivarColors.White,
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

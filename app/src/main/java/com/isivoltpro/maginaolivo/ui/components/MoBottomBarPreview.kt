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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors

@Composable
fun MoBottomBarPreview(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf("Inicio", "Mi Olivar", "Registrar", "Calendario", "Perfil")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isRegister = item == "Registrar"
            val isSelected = item == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isRegister) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = OlivarColors.Olive700,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.headlineMedium,
                            color = OlivarColors.White,
                        )
                    }
                } else {
                    Text(
                        text = if (isSelected) "●" else "○",
                        color = if (isSelected) OlivarColors.Olive700 else OlivarColors.Charcoal500,
                    )
                }
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected || isRegister) {
                        OlivarColors.Olive700
                    } else {
                        OlivarColors.Charcoal500
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

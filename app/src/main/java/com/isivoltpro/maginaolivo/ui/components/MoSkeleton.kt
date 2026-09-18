package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun MoListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceSm),
    ) {
        repeat(rows) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = OlivarColors.Cream100,
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                    )
                    .padding(OlivarDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceXs),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .background(
                            color = OlivarColors.Line200,
                            shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                        ),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(20.dp)
                        .background(
                            color = OlivarColors.Line200,
                            shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
    }
}

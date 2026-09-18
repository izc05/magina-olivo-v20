package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun ReferenceOnboardingScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(OlivarDimens.SpaceLg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.brand_working_name),
                style = MaterialTheme.typography.labelLarge,
                color = OlivarColors.Olive700,
            )
            Text(
                text = stringResource(R.string.brand_working_tagline),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
            )
            Text(
                text = "Gestiona fincas, parcelas, campañas y trabajos desde un solo lugar.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = OlivarDimens.SpaceSm),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    color = OlivarColors.Sage200,
                    shape = MaterialTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(0.55f)
                        .background(
                            color = OlivarColors.Olive700,
                            shape = MaterialTheme.shapes.large,
                        ),
                )
                Text(
                    text = "Finca → Parcelas → Campaña",
                    style = MaterialTheme.typography.titleMedium,
                    color = OlivarColors.Olive900,
                    modifier = Modifier.padding(top = OlivarDimens.SpaceMd),
                )
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (index == 0) 28.dp else 8.dp)
                            .background(
                                color = if (index == 0) OlivarColors.Olive700 else OlivarColors.Line200,
                                shape = CircleShape,
                            ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(OlivarDimens.SpaceLg))
            MoPrimaryButton(
                text = "Siguiente",
                onClick = {},
            )
            Spacer(modifier = Modifier.height(OlivarDimens.SpaceXs))
            MoSecondaryButton(
                text = "Saltar",
                onClick = {},
            )
        }
    }
}

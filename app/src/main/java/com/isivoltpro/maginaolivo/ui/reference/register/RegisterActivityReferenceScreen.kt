package com.isivoltpro.maginaolivo.ui.reference.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoDateField
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

private enum class ActivityType(val label: String) {
    Fertilization("Abonado"),
    Treatment("Tratamiento"),
    Irrigation("Riego"),
    Pruning("Poda"),
    Soil("Suelo"),
    Observation("Observación"),
}

@Composable
fun RegisterActivityReferenceScreen(
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf(ActivityType.Treatment) }
    var notes by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("Cobre 50%") }
    var dose by remember { mutableStateOf("2,0 kg/ha") }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("register-reference-root"),
        containerColor = MoCream,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
        ) {
            Spacer(Modifier.height(MoSpacing.md))

            Text(
                text = "Registrar actuación",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Añade solo los datos que correspondan al trabajo realizado.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.lg))

            Text(
                text = "Tipo de actuación",
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
            Spacer(Modifier.height(MoSpacing.sm))

            ActivityTypeGrid(
                selected = selectedType,
                onSelected = { selectedType = it },
            )

            Spacer(Modifier.height(MoSpacing.lg))

            MoSelectField(
                label = "Finca",
                value = "La Solana",
                onClick = {},
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoSelectField(
                label = "Parcela",
                value = "Parcela Norte",
                onClick = {},
            )
            Spacer(Modifier.height(MoSpacing.sm))
            MoDateField(
                label = "Fecha",
                value = "18 septiembre 2026",
                onClick = {},
            )

            Spacer(Modifier.height(MoSpacing.lg))

            if (selectedType == ActivityType.Treatment) {
                MoTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = "Producto",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MoSpacing.sm))
                MoTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = "Dosis",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MoSpacing.sm))
            }

            if (selectedType == ActivityType.Irrigation) {
                MoTextField(
                    value = "4 h",
                    onValueChange = {},
                    label = "Duración",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MoSpacing.sm))
            }

            if (selectedType == ActivityType.Pruning) {
                MoSelectField(
                    label = "Tipo de poda",
                    value = "Mantenimiento",
                    onClick = {},
                )
                Spacer(Modifier.height(MoSpacing.sm))
            }

            MoTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notas",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.md))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
                border = BorderStroke(1.dp, MoOutline),
            ) {
                Column(
                    modifier = Modifier.padding(MoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    Text(
                        text = "Fotos y justificantes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Adjunta imágenes del trabajo o documentos relacionados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                    Text(
                        text = "+ Añadir archivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MoOlivePrimary,
                    )
                }
            }

            Spacer(Modifier.height(MoSpacing.lg))

            MoPrimaryButton(
                text = "Guardar actuación",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
}

@Composable
private fun ActivityTypeGrid(
    selected: ActivityType,
    onSelected: (ActivityType) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        ActivityType.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                row.forEach { type ->
                    val active = type == selected
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("activity-type-${type.name}")
                            .selectable(
                                selected = active,
                                role = Role.RadioButton,
                                onClick = { onSelected(type) },
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) {
                                MoOlivePrimary.copy(alpha = 0.12f)
                            } else {
                                MoWarmWhite
                            },
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (active) MoOlivePrimary else MoOutline,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MoSpacing.md, horizontal = MoSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = activitySymbol(type),
                                modifier = Modifier.clearAndSetSemantics { },
                                style = MaterialTheme.typography.titleLarge,
                                color = MoOlivePrimary,
                            )
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MoOliveDark,
                            )
                        }
                    }
                }

                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun activitySymbol(type: ActivityType): String = when (type) {
    ActivityType.Fertilization -> "◈"
    ActivityType.Treatment -> "✦"
    ActivityType.Irrigation -> "◉"
    ActivityType.Pruning -> "Y"
    ActivityType.Soil -> "≈"
    ActivityType.Observation -> "!"
}

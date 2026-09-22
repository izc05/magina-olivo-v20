package com.isivoltpro.maginaolivo.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

@Composable
fun NavigationPlaceholderScreen(
    title: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    developerGalleryEnabled: Boolean = false,
    onDeveloperGallery: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
            .statusBarsPadding()
            .padding(MoSpacing.screen),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(MoSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MoOliveDark,
        )
        Spacer(Modifier.height(MoSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MoTextSecondary,
        )
        if (developerGalleryEnabled) {
            Spacer(Modifier.height(MoSpacing.lg))
            MoSecondaryButton(
                text = "Catálogo de diseño (DEV)",
                onClick = onDeveloperGallery,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

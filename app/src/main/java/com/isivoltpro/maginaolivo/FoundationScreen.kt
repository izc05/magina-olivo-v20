package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun FoundationScreen(
    environment: String,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag("foundation-root"),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.foundation_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.foundation_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (environment != "PRODUCTION") {
                    Text(
                        text = environment,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

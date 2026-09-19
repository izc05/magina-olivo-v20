package com.isivoltpro.maginaolivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compositionRoot = AppCompositionRoot.createAndroid(
            context = applicationContext,
            environmentValue = BuildConfig.ENVIRONMENT,
        )

        setContent {
            AppRoot(compositionRoot = compositionRoot)
        }
    }
}

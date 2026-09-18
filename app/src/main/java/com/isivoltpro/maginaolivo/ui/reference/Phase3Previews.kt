package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.isivoltpro.maginaolivo.ui.theme.OlivarTheme

@Preview(
    name = "Inicio · 360dp",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun PreviewHome360() {
    OlivarTheme {
        ReferenceHomeScreen()
    }
}

@Preview(
    name = "Inicio · 412dp",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun PreviewHome412() {
    OlivarTheme {
        ReferenceHomeScreen()
    }
}

@Preview(
    name = "Mi Olivar · 360dp",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun PreviewOliveGrove360() {
    OlivarTheme {
        ReferenceOliveGroveScreen()
    }
}

@Preview(
    name = "Mi Olivar · 412dp",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun PreviewOliveGrove412() {
    OlivarTheme {
        ReferenceOliveGroveScreen()
    }
}

@Preview(
    name = "Producción · 360dp",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun PreviewProduction360() {
    OlivarTheme {
        ReferenceProductionScreen()
    }
}

@Preview(
    name = "Producción · 412dp",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun PreviewProduction412() {
    OlivarTheme {
        ReferenceProductionScreen()
    }
}

@Preview(
    name = "Entrega OCR · 360dp",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun PreviewOcr360() {
    OlivarTheme {
        ReferenceOcrScreen()
    }
}

@Preview(
    name = "Entrega OCR · 412dp",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun PreviewOcr412() {
    OlivarTheme {
        ReferenceOcrScreen()
    }
}

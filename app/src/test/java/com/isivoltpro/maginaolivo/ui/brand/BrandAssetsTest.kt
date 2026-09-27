package com.isivoltpro.maginaolivo.ui.brand

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetsTest {
    private val drawableRoot = sequenceOf(
        File("src/main/res/drawable-nodpi"),
        File("app/src/main/res/drawable-nodpi"),
    ).firstOrNull { it.parentFile?.exists() == true }

    @Test
    fun officialAssetsFitTheirUiRoles() {
        assertNotNull("Could not locate drawable-nodpi", drawableRoot)

        val wordmark = image("brand_official_wordmark.png")
        val mark = image("brand_official_mark.png")
        val appIcon = image("brand_official_app_icon.png")

        assertTrue("Horizontal wordmark must stay wide", wordmark.width > wordmark.height * 3)
        assertTrue("Compact mark must stay approximately square", mark.width in (mark.height * 3 / 4)..(mark.height * 5 / 4))
        assertTrue("Launcher artwork must be square", appIcon.width == appIcon.height)
        assertTrue("Official PNG assets must preserve transparency", wordmark.colorModel.hasAlpha())
        assertTrue("Official PNG assets must preserve transparency", mark.colorModel.hasAlpha())
        assertTrue("Official PNG assets must preserve transparency", appIcon.colorModel.hasAlpha())
    }

    @Test
    fun monochromeAssetIsAValidSystemSilhouette() {
        val monochrome = image("brand_official_monochrome.png")

        assertTrue("System brand silhouette must be square", monochrome.width == monochrome.height)
        val visiblePixels = (0 until monochrome.width).asSequence().flatMap { x ->
            (0 until monochrome.height).asSequence().map { y -> monochrome.getRGB(x, y) }
        }.filter { pixel -> pixel ushr 24 != 0 }.toList()
        assertTrue("System brand silhouette must contain visible pixels", visiblePixels.isNotEmpty())
        assertTrue("System brand silhouette must be white with alpha", visiblePixels.all { pixel -> pixel and 0x00FFFFFF == 0x00FFFFFF })
        assertTrue("System brand silhouette must not amplify low-alpha source artifacts", visiblePixels.all { pixel -> pixel ushr 24 == 0xFF })
    }

    @Test
    fun compactMarkHasTransparentBreathingRoom() {
        val mark = image("brand_official_mark.png")
        val border = 8

        val borderPixels = sequence {
            for (x in 0 until mark.width) {
                for (y in 0 until border) yield(mark.getRGB(x, y))
                for (y in mark.height - border until mark.height) yield(mark.getRGB(x, y))
            }
            for (y in border until mark.height - border) {
                for (x in 0 until border) yield(mark.getRGB(x, y))
                for (x in mark.width - border until mark.width) yield(mark.getRGB(x, y))
            }
        }
        assertTrue("Compact mark must not include clipped neighboring artwork", borderPixels.all { pixel -> pixel ushr 24 == 0 })
    }

    private fun image(name: String) = File(drawableRoot, name).let { file ->
        assertTrue("Missing official brand asset: $name", file.isFile)
        ImageIO.read(file).also { assertNotNull("Unreadable official brand asset: $name", it) }
    }
}

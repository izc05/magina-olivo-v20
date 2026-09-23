package com.isivoltpro.maginaolivo.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.ocr.OcrEngine
import com.isivoltpro.maginaolivo.domain.ocr.OcrText
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device text recognition with the bundled ML Kit Latin model: it works in a field with
 * no signal and never sends a document anywhere. PDFs are read page by page (up to
 * [MAX_PDF_PAGES]) from the app's own copy.
 */
class MlKitOcrEngine(
    private val context: Context,
) : OcrEngine {
    override val name: String = "mlkit-text-latin"

    override suspend fun recognize(localUri: String, mimeType: String): OcrText {
        val uri = Uri.parse(localUri)
        val text = if (AttachmentKind.fromMimeType(mimeType) == AttachmentKind.PDF) {
            val file = File(uri.path ?: error("pdf_without_path"))
            readPdf(file)
        } else {
            read(InputImage.fromFilePath(context, uri))
        }
        return OcrText(text = text, engine = name, engineVersion = ENGINE_VERSION)
    }

    private suspend fun read(image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
        } finally {
            recognizer.close()
        }
    }

    /** One page in memory at a time: a rendered A4 page is several megabytes. */
    private suspend fun readPdf(file: File): String {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                val pages = mutableListOf<String>()
                for (index in 0 until min(renderer.pageCount, MAX_PDF_PAGES)) {
                    val bitmap = renderPage(renderer, index)
                    try {
                        pages += read(InputImage.fromBitmap(bitmap, 0))
                    } finally {
                        bitmap.recycle()
                    }
                }
                return pages.joinToString("\n")
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            // About 200 dpi for an A4 page: enough for printed invoice text.
            val scale = PDF_TARGET_WIDTH_PX.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt(),
                (page.height * scale).roundToInt(),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return bitmap
        } finally {
            page.close()
        }
    }

    private companion object {
        const val ENGINE_VERSION = "16.0.1"
        const val MAX_PDF_PAGES = 3
        const val PDF_TARGET_WIDTH_PX = 1654
    }
}

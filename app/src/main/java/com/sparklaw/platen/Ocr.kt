package com.sparklaw.platen

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

private const val TAG = "Ocr"

data class OcrWord(val text: String, val box: Rect)

object Ocr {

    suspend fun recognize(bitmap: Bitmap): List<OcrWord> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val words = mutableListOf<OcrWord>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isBlank()) continue
                    val box = line.boundingBox ?: continue
                    words += OcrWord(text, box)
                }
            }
            words
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            emptyList()
        } finally {
            recognizer.close()
        }
    }
}

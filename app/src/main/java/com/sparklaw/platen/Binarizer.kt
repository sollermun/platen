package com.sparklaw.platen

import android.graphics.Bitmap

/**
 * Converts a captured page bitmap to either pure black/white (bitonal) or
 * smooth grayscale.
 */
object Binarizer {

    /**
     * Pure black-and-white via Bradley-Roth adaptive thresholding. Smallest
     * files, but "fax" look (no anti-aliasing). Tunables: t (lower keeps fainter
     * text), windowDiv (larger = more local).
     */
    fun toBitonal(src: Bitmap, t: Double = 0.15, windowDiv: Int = 8): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h

        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val gray = IntArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        val integral = LongArray(n)
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[y * w + x] =
                    if (y == 0) rowSum else integral[(y - 1) * w + x] + rowSum
            }
        }

        val s = maxOf(w, h) / windowDiv
        val half = s / 2
        val out = IntArray(n)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = maxOf(0, x - half)
                val x2 = minOf(w - 1, x + half)
                val y1 = maxOf(0, y - half)
                val y2 = minOf(h - 1, y + half)
                val count = (x2 - x1 + 1).toLong() * (y2 - y1 + 1).toLong()

                val a = if (x1 > 0 && y1 > 0) integral[(y1 - 1) * w + (x1 - 1)] else 0L
                val bb = if (y1 > 0) integral[(y1 - 1) * w + x2] else 0L
                val cc = if (x1 > 0) integral[y2 * w + (x1 - 1)] else 0L
                val dd = integral[y2 * w + x2]
                val regionSum = dd - bb - cc + a

                val pixel = gray[y * w + x].toLong()
                out[y * w + x] =
                    if (pixel * count <= regionSum * (1.0 - t)) 0xFF000000.toInt()
                    else 0xFFFFFFFF.toInt()
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Desaturate to grayscale (luminance). Smooth, anti-aliased, document-scan
     * look. Larger files than bitonal because there are real gray tones to
     * compress, but still far smaller than the original JPEG.
     */
    fun toGrayscale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = (r * 299 + g * 587 + b * 114) / 1000
            px[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}

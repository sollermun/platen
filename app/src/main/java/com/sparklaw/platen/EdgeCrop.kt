package com.sparklaw.platen

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

const val FILL_LUMA_MAX = 90
const val MAX_EDGE_CROP_FRACTION = 0.25f
const val MAX_FILL_FRACTION = 0.50f
const val DETECT_CEILING = 6000
const val MIN_DESKEW_DEG = 0.3f
const val MAX_DESKEW_DEG = 10f
private const val SEED_MARGIN = 3
private const val MAX_EDGE_DISAGREE_DEG = 2.0

private inline fun luma(pixel: Int): Int {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
}

/**
 * Builds a BooleanArray mask of all pixels reachable by iterative 4-connected
 * flood-fill from every border pixel whose luminance ≤ lumaMax.
 */
private fun borderFloodMask(pixels: IntArray, w: Int, h: Int, lumaMax: Int): BooleanArray {
    val visited = BooleanArray(w * h)
    val stack = ArrayDeque<Int>(w * 2 + h * 2)

    fun seed(idx: Int) {
        if (!visited[idx] && luma(pixels[idx]) <= lumaMax) {
            visited[idx] = true
            stack.addLast(idx)
        }
    }

    val sm = SEED_MARGIN.coerceAtMost(w / 2).coerceAtMost(h / 2)
    for (row in 0 until sm) for (x in 0 until w) { seed(row * w + x); seed((h - 1 - row) * w + x) }
    for (col in 0 until sm) for (y in sm until h - sm) { seed(y * w + col); seed(y * w + w - 1 - col) }

    while (stack.isNotEmpty()) {
        val idx = stack.removeLast()
        val x = idx % w
        val y = idx / w
        if (y > 0)     { val n = idx - w; if (!visited[n] && luma(pixels[n]) <= lumaMax) { visited[n] = true; stack.addLast(n) } }
        if (y < h - 1) { val n = idx + w; if (!visited[n] && luma(pixels[n]) <= lumaMax) { visited[n] = true; stack.addLast(n) } }
        if (x > 0)     { val n = idx - 1; if (!visited[n] && luma(pixels[n]) <= lumaMax) { visited[n] = true; stack.addLast(n) } }
        if (x < w - 1) { val n = idx + 1; if (!visited[n] && luma(pixels[n]) <= lumaMax) { visited[n] = true; stack.addLast(n) } }
    }
    return visited
}

/**
 * Least-squares linear regression over a list of (x, y) Double pairs.
 * Returns slope or null if degenerate (< 2 points or zero variance in x).
 */
private fun lsqSlope(pts: List<Pair<Double, Double>>): Double? {
    if (pts.size < 2) return null
    val n = pts.size.toDouble()
    val mx = pts.sumOf { it.first } / n
    val my = pts.sumOf { it.second } / n
    var sxx = 0.0; var sxy = 0.0
    for ((x, y) in pts) { sxx += (x - mx) * (x - mx); sxy += (x - mx) * (y - my) }
    if (sxx < 1e-9) return null
    return sxy / sxx
}

/**
 * Estimates the skew angle (degrees) of the document by fitting lines through
 * the fill/paper transitions on the top and bottom edges.
 * Returns null if estimation fails or the two edges disagree badly.
 */
fun estimateSkewDeg(mask: BooleanArray, w: Int, h: Int): Double? {
    val topPts = mutableListOf<Pair<Double, Double>>()
    val botPts = mutableListOf<Pair<Double, Double>>()
    for (col in 0 until w) {
        var topTransition = -1
        for (row in 0 until h) {
            if (mask[row * w + col]) continue
            topTransition = row; break
        }
        if (topTransition > 0) topPts.add(Pair(col.toDouble(), topTransition.toDouble()))

        var botTransition = -1
        for (row in h - 1 downTo 0) {
            if (mask[row * w + col]) continue
            botTransition = row; break
        }
        if (botTransition in 0 until h - 1) botPts.add(Pair(col.toDouble(), botTransition.toDouble()))
    }
    val topSlope = lsqSlope(topPts) ?: return null
    val botSlope = lsqSlope(botPts)
    val topDeg = Math.toDegrees(atan(topSlope))
    if (botSlope != null) {
        val botDeg = Math.toDegrees(atan(botSlope))
        if (abs(topDeg - botDeg) > MAX_EDGE_DISAGREE_DEG) return null
    }
    return topDeg
}

/**
 * Deskews a full-resolution bitmap by rotating it by the negated skew angle.
 * Skips rotation if estimation fails, or |angle| < MIN_DESKEW_DEG, or > MAX_DESKEW_DEG.
 * The rotated bitmap is expanded to fit the full rotated content (no clipping).
 */
fun deskewBitmap(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val mask = borderFloodMask(pixels, w, h, FILL_LUMA_MAX)
    val angle = estimateSkewDeg(mask, w, h) ?: return src
    if (abs(angle) < MIN_DESKEW_DEG || abs(angle) > MAX_DESKEW_DEG) return src
    val matrix = Matrix().apply { setRotate(-angle.toFloat(), w / 2f, h / 2f) }
    val rotated = Bitmap.createBitmap(src, 0, 0, w, h, matrix, true)
    return rotated
}

/**
 * Detects the true page boundary by flood-filling the off-page fill from each
 * image border, then crops to the rectangle just inside the deepest inward
 * extent of that fill on each edge.
 *
 * Safety: never crops more than MAX_EDGE_CROP_FRACTION on any single edge.
 * Returns src unchanged if no meaningful crop is found.
 */
fun detectAndCropPage(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    val mask = borderFloodMask(pixels, w, h, FILL_LUMA_MAX)

    val maxTopCrop    = (h * MAX_EDGE_CROP_FRACTION).toInt()
    val maxBottomCrop = (h * MAX_EDGE_CROP_FRACTION).toInt()
    val maxLeftCrop   = (w * MAX_EDGE_CROP_FRACTION).toInt()
    val maxRightCrop  = (w * MAX_EDGE_CROP_FRACTION).toInt()

    var cropTop = 0
    outer@ for (row in 0 until h) {
        for (x in 0 until w) if (mask[row * w + x]) { cropTop = row + 1; continue@outer }
        break
    }
    if (cropTop > maxTopCrop) cropTop = 0

    var cropBottom = h
    outer@ for (row in h - 1 downTo 0) {
        for (x in 0 until w) if (mask[row * w + x]) { cropBottom = row; continue@outer }
        break
    }
    if (h - cropBottom > maxBottomCrop) cropBottom = h

    var cropLeft = 0
    outer@ for (col in 0 until w) {
        for (y in 0 until h) if (mask[y * w + col]) { cropLeft = col + 1; continue@outer }
        break
    }
    if (cropLeft > maxLeftCrop) cropLeft = 0

    var cropRight = w
    outer@ for (col in w - 1 downTo 0) {
        for (y in 0 until h) if (mask[y * w + col]) { cropRight = col; continue@outer }
        break
    }
    if (w - cropRight > maxRightCrop) cropRight = w

    val cropW = cropRight - cropLeft
    val cropH = cropBottom - cropTop
    if (cropW <= 0 || cropH <= 0) return src
    if (cropTop == 0 && cropBottom == h && cropLeft == 0 && cropRight == w) return src

    return Bitmap.createBitmap(src, cropLeft, cropTop, cropW, cropH)
}

/**
 * After cropping, re-runs the border flood-fill on the (now-cropped) bitmap and
 * repaints any remaining border-connected near-black pixels white. Handles
 * slivers from slight rotation.
 *
 * Bails and returns src unchanged if the fill would cover > MAX_FILL_FRACTION
 * of total pixels (detection misfire guard).
 *
 * The input bitmap must be mutable; pass a copy if needed.
 */
fun whitenResidualFill(bmp: Bitmap): Bitmap {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    val mask = borderFloodMask(pixels, w, h, FILL_LUMA_MAX)

    val fillCount = mask.count { it }
    if (fillCount.toFloat() / (w * h) > MAX_FILL_FRACTION) return bmp

    if (fillCount == 0) return bmp

    val white = 0xFFFFFFFF.toInt()
    for (i in pixels.indices) if (mask[i]) pixels[i] = white

    val result = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

package com.sparklaw.platen

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "PdfExporter"

private const val RECEIPT_RATIO_THRESHOLD = 0.5f
private const val PAGE_MARGIN_PT = 0f

private const val LETTER_W_PT = 612f
private const val LETTER_H_PT = 792f
private const val LEGAL_W_PT  = 612f
private const val LEGAL_H_PT  = 1008f

private val LETTER_PORTRAIT_RATIO = LETTER_W_PT / LETTER_H_PT
private val LEGAL_PORTRAIT_RATIO  = LEGAL_W_PT  / LEGAL_H_PT

enum class PageSize { FIT, LETTER, LEGAL }

private data class DrawRect(
    val pageW: Float, val pageH: Float,
    val imgX: Float, val imgY: Float,
    val imgW: Float, val imgH: Float
)

sealed class SaveException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PermissionLostException(message: String) : SaveException(message)
class FolderMissingException(message: String) : SaveException(message)
class CreateFileException(val treeUri: Uri, message: String) : SaveException(message)
class WriteFailedException(val treeUri: Uri, message: String, cause: Throwable) : SaveException(message, cause)

internal fun isLocalProvider(uri: Uri): Boolean =
    uri.authority == "com.android.externalstorage.documents"

internal fun providerLabel(uri: Uri): String = when {
    uri.authority?.contains("nextcloud", ignoreCase = true) == true -> "Nextcloud"
    uri.authority?.contains("docs.storage", ignoreCase = true) == true -> "Google Drive"
    isLocalProvider(uri) -> "the selected folder"
    else -> "the sync app"
}

private val FMT_DATE     = DateTimeFormatter.ofPattern("yyyy-MM-dd",          Locale.US)
private val FMT_TIME     = DateTimeFormatter.ofPattern("HH-mm-ss",            Locale.US)
private val FMT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.US)
private val FMT_YEAR   = DateTimeFormatter.ofPattern("yyyy", Locale.US)
private val FMT_MONTH  = DateTimeFormatter.ofPattern("MM",   Locale.US)
private val FMT_DAY    = DateTimeFormatter.ofPattern("dd",   Locale.US)
private val FMT_HOUR   = DateTimeFormatter.ofPattern("HH",   Locale.US)
private val FMT_MINUTE = DateTimeFormatter.ofPattern("mm",   Locale.US)
private val FMT_SECOND = DateTimeFormatter.ofPattern("ss",   Locale.US)

internal fun sanitizeFilename(name: String): String {
    val cleaned = name
        .replace(Regex("[:/\\\\?*\"<>|]"), "_")
        .replace(Regex("[\\x00-\\x1F]"), "")
        .trim()
        .trim('.')
    return cleaned.ifBlank { "scan" }.take(120)
}

internal fun expandTokens(
    pattern: String,
    profileName: String,
    now: LocalDateTime
): String = pattern
    .ifBlank { "{datetime}_{profile}" }
    .replace("{datetime}", now.format(FMT_DATETIME))
    .replace("{date}",     now.format(FMT_DATE))
    .replace("{time}",     now.format(FMT_TIME))
    .replace("{year}",     now.format(FMT_YEAR))
    .replace("{month}",    now.format(FMT_MONTH))
    .replace("{day}",      now.format(FMT_DAY))
    .replace("{hour}",     now.format(FMT_HOUR))
    .replace("{minute}",   now.format(FMT_MINUTE))
    .replace("{second}",   now.format(FMT_SECOND))
    .replace("{profile}",  profileName)

private fun buildFilename(
    pattern: String,
    profileName: String,
    now: LocalDateTime,
    dir: androidx.documentfile.provider.DocumentFile
): String {
    val raw = expandTokens(pattern, profileName, now)
    val base = sanitizeFilename(raw.replace("{n}", ""))
    val candidate = "$base.pdf"
    if (dir.findFile(candidate) == null) return base
    for (n in 2..99) {
        val withN = sanitizeFilename(raw.replace("{n}", n.toString()))
        val withNCandidate = "$withN.pdf"
        val collisionName = "$base ($n).pdf"
        val chosen = if (raw.contains("{n}")) withNCandidate else collisionName
        val chosenBase = chosen.removeSuffix(".pdf")
        if (dir.findFile(chosen) == null) return chosenBase
    }
    return sanitizeFilename("$base ${now.format(FMT_TIME)}")
}

object PdfExporter {
    fun export(
        context: Context,
        treeUri: Uri,
        pages: List<Bitmap>,
        ocrWords: List<List<OcrWord>>? = null,
        dpi: Float = 300f,
        pageSize: PageSize = PageSize.FIT,
        autoDetect: Boolean = false,
        filenamePattern: String = "{datetime}_{profile}",
        profileName: String = "scan"
    ): Uri? {
        if (pages.isEmpty()) return null
        val doc = PDDocument()
        try {
            pages.forEachIndexed { idx, bmp ->
                // LosslessFactory accepts a normal ARGB_8888 bitmap and Flate-compresses it.
                // Works for both the bitonal and grayscale outputs from Binarizer.
                val img = LosslessFactory.createFromImage(doc, bmp)
                val dr = computeDrawRect(bmp, dpi, pageSize, autoDetect)
                val page = PDPage(PDRectangle(dr.pageW, dr.pageH))
                doc.addPage(page)

                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, dr.imgX, dr.imgY, dr.imgW, dr.imgH)
                }

                val words = ocrWords?.getOrNull(idx)
                if (!words.isNullOrEmpty()) {
                    val scaleX = dr.imgW / bmp.width
                    val scaleY = dr.imgH / bmp.height
                    PDPageContentStream(
                        doc, page,
                        PDPageContentStream.AppendMode.APPEND,
                        true
                    ).use { cs ->
                        cs.beginText()
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        val font = PDType1Font.HELVETICA
                        for (word in words) {
                            val sanitized = sanitizeForWinAnsi(word.text)
                            if (sanitized.isEmpty()) continue
                            val boxH = (word.box.bottom - word.box.top).coerceAtLeast(1)
                            val fontSize = (boxH * scaleY).coerceAtLeast(1f)
                            val x = dr.imgX + word.box.left * scaleX
                            val y = dr.imgY + (dr.imgH - word.box.bottom * scaleY)
                            try {
                                cs.setFont(font, fontSize)
                                cs.newLineAtOffset(x, y)
                                cs.showText(sanitized)
                                cs.newLineAtOffset(-x, -y)
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipped line '${word.text}': ${e.message}")
                            }
                        }
                        cs.endText()
                    }
                }
            }
            val hasWritePerm = context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == treeUri && perm.isWritePermission
            }
            if (!hasWritePerm)
                throw PermissionLostException("No persisted write permission for $treeUri")
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            if (dir == null || !dir.exists() || !dir.canWrite())
                throw FolderMissingException("Folder unavailable or unwritable: $treeUri")
            val now = LocalDateTime.now()
            val baseName = buildFilename(filenamePattern, profileName, now, dir)
            val file = dir.createFile("application/pdf", "$baseName.pdf")
                ?: throw CreateFileException(treeUri, "createFile returned null for $treeUri")
            val os = context.contentResolver.openOutputStream(file.uri)
                ?: throw WriteFailedException(treeUri, "openOutputStream returned null", java.io.IOException("null stream"))
            try {
                os.use { doc.save(it) }
            } catch (e: java.io.IOException) {
                throw WriteFailedException(treeUri, "IOException during doc.save: ${e.message}", e)
            }
            return file.uri
        } finally {
            doc.close()
        }
    }

    private fun computeDrawRect(bmp: Bitmap, dpi: Float, pageSize: PageSize, autoDetect: Boolean): DrawRect {
        val imgWPt = bmp.width / dpi * 72f
        val imgHPt = bmp.height / dpi * 72f

        val shortSide = min(bmp.width, bmp.height).toFloat()
        val longSide  = maxOf(bmp.width, bmp.height).toFloat()
        val ratio = shortSide / longSide

        if (ratio < RECEIPT_RATIO_THRESHOLD) {
            return DrawRect(imgWPt, imgHPt, 0f, 0f, imgWPt, imgHPt)
        }

        val resolvedSize: PageSize = when {
            autoDetect -> {
                val diffLetter = abs(ratio - LETTER_PORTRAIT_RATIO)
                val diffLegal  = abs(ratio - LEGAL_PORTRAIT_RATIO)
                if (diffLetter <= diffLegal) PageSize.LETTER else PageSize.LEGAL
            }
            else -> pageSize
        }

        if (resolvedSize == PageSize.FIT) {
            return DrawRect(imgWPt, imgHPt, 0f, 0f, imgWPt, imgHPt)
        }

        val landscape = bmp.width > bmp.height
        val (fixedW, fixedH) = when (resolvedSize) {
            PageSize.LETTER -> if (landscape) Pair(LETTER_H_PT, LETTER_W_PT) else Pair(LETTER_W_PT, LETTER_H_PT)
            PageSize.LEGAL  -> if (landscape) Pair(LEGAL_H_PT,  LEGAL_W_PT)  else Pair(LEGAL_W_PT,  LEGAL_H_PT)
            PageSize.FIT    -> return DrawRect(imgWPt, imgHPt, 0f, 0f, imgWPt, imgHPt)
        }

        val scale = min(fixedW / imgWPt, fixedH / imgHPt)
        val drawnW = imgWPt * scale
        val drawnH = imgHPt * scale
        val offsetX = (fixedW - drawnW) / 2f
        val offsetY = (fixedH - drawnH) / 2f

        return DrawRect(fixedW, fixedH, offsetX, offsetY, drawnW, drawnH)
    }

    private fun sanitizeForWinAnsi(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val cp = ch.code
            when {
                cp in 0x20..0x7E -> sb.append(ch)
                cp in 0xA0..0xFF -> sb.append(ch)
                ch == '\u2019' || ch == '\u2018' -> sb.append('\'')
                ch == '\u201C' || ch == '\u201D' -> sb.append('"')
                ch == '\u2013' || ch == '\u2014' -> sb.append('-')
                else -> { /* drop */ }
            }
        }
        return sb.toString()
    }
}

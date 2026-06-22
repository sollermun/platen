package com.sparklaw.platen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "platen"
private const val KEY_TREE = "output_tree_uri"
private const val KEY_GRAYSCALE = "grayscale_mode"
private const val KEY_OCR = "ocr_enabled"
private const val KEY_HIGH_QUALITY = "high_quality"
private const val KEY_PAGE_SIZE = "page_size"
private const val KEY_AUTO_DETECT = "auto_detect_page_size"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerScreen()
                }
            }
        }
    }
}

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var showSettings by remember { mutableStateOf(false) }
    var treeUri by remember {
        mutableStateOf(prefs.getString(KEY_TREE, null)?.let(Uri::parse))
    }
    var grayscale by remember { mutableStateOf(prefs.getBoolean(KEY_GRAYSCALE, false)) }
    var ocrEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_OCR, true)) }
    var highQuality by remember { mutableStateOf(prefs.getBoolean(KEY_HIGH_QUALITY, false)) }
    var pageSizeOrdinal by remember { mutableStateOf(prefs.getInt(KEY_PAGE_SIZE, PageSize.FIT.ordinal)) }
    var autoDetect by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_DETECT, false)) }
    var lastSaved by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    fun setGrayscale(value: Boolean) {
        grayscale = value
        prefs.edit().putBoolean(KEY_GRAYSCALE, value).apply()
    }

    fun setOcrEnabled(value: Boolean) {
        ocrEnabled = value
        prefs.edit().putBoolean(KEY_OCR, value).apply()
    }

    fun setHighQuality(value: Boolean) {
        highQuality = value
        prefs.edit().putBoolean(KEY_HIGH_QUALITY, value).apply()
    }

    fun setPageSize(value: PageSize) {
        pageSizeOrdinal = value.ordinal
        prefs.edit().putInt(KEY_PAGE_SIZE, value.ordinal).apply()
    }

    fun setAutoDetect(value: Boolean) {
        autoDetect = value
        prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()
    }

    if (showSettings) {
        SettingsScreen(
            grayscale = grayscale,
            onGrayscaleChange = { setGrayscale(it) },
            ocrEnabled = ocrEnabled,
            onOcrEnabledChange = { setOcrEnabled(it) },
            highQuality = highQuality,
            onHighQualityChange = { setHighQuality(it) },
            pageSize = PageSize.entries[pageSizeOrdinal],
            onPageSizeChange = { setPageSize(it) },
            autoDetect = autoDetect,
            onAutoDetectChange = { setAutoDetect(it) },
            onBack = { showSettings = false }
        )
        return
    }

    fun shareLast() {
        val uri = lastSaved ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share scan"))
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_TREE, uri.toString()).apply()
            treeUri = uri
            status = "Output folder set. Ready to scan."
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pageUris = data?.pages?.map { it.imageUri } ?: emptyList()
        val dest = treeUri
        if (pageUris.isEmpty() || dest == null) {
            status = "Scan cancelled or no folder set."
            return@rememberLauncherForActivityResult
        }
        status = "Processing…"
        val useGray = grayscale
        val useOcr = ocrEnabled
        val useHigh = highQuality
        val usePageSize = PageSize.entries[pageSizeOrdinal]
        val useAutoDetect = autoDetect
        scope.launch {
            val out: Uri? = try {
                withContext(Dispatchers.Default) {
                    val maxEdge = if (useHigh) 4000 else 3000
                    val dpi = if (useHigh) 400f else 300f
                    val processed = pageUris.mapNotNull { uri ->
                        decodeFullRes(context, uri)?.let { full ->
                            val cropped = detectAndCropPage(full)
                            val whitened = whitenResidualFill(
                                if (cropped.isMutable) cropped
                                else cropped.copy(Bitmap.Config.ARGB_8888, true)
                            )
                            val page = downsampleBitmap(whitened, maxEdge)
                            if (useGray) Binarizer.toGrayscale(page) else Binarizer.toBitonal(page)
                        }
                    }
                    val words: List<List<OcrWord>>? = if (useOcr) {
                        withContext(Dispatchers.Main) { status = "Recognizing text…" }
                        processed.map { bmp -> Ocr.recognize(bmp) }
                    } else null
                    PdfExporter.export(context, dest, processed, words, dpi, usePageSize, useAutoDetect)
                }
            } catch (e: Throwable) {
                status = "Error: ${e.message}"
                null
            }
            status = ""
            if (out != null) {
                lastSaved = out
                val filename = out.lastPathSegment?.substringAfterLast('/') ?: "scan"
                val result = snackbarHostState.showSnackbar(
                    message = "Saved $filename",
                    actionLabel = "Share",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) shareLast()
            }
        }
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as ComponentActivity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platen") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val folderCaption = when {
                treeUri == null -> "No folder selected"
                else -> "Saving to Scans"
            }
            Text(folderCaption, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { startScan() },
                enabled = treeUri != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan", style = MaterialTheme.typography.titleMedium)
            }

            FilledTonalButton(
                onClick = { folderPicker.launch(treeUri) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (treeUri == null) "Choose output folder" else "Change output folder")
            }

            OutlinedButton(
                onClick = { shareLast() },
                enabled = lastSaved != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share last scan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    ocrEnabled: Boolean,
    onOcrEnabledChange: (Boolean) -> Unit,
    highQuality: Boolean,
    onHighQualityChange: (Boolean) -> Unit,
    pageSize: PageSize,
    onPageSizeChange: (PageSize) -> Unit,
    autoDetect: Boolean,
    onAutoDetectChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !grayscale, onClick = { onGrayscaleChange(false) })
                Text("Black & white (small)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = grayscale, onClick = { onGrayscaleChange(true) })
                Text("Grayscale (smoother, larger)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Searchable PDF", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ocrEnabled, onCheckedChange = onOcrEnabledChange)
                Text("Make PDF searchable (OCR)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Quality", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !highQuality, onClick = { onHighQualityChange(false) })
                Text("Standard (300 DPI)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = highQuality, onClick = { onHighQualityChange(true) })
                Text("High (400 DPI)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Page size", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = pageSize == PageSize.FIT, onClick = { onPageSizeChange(PageSize.FIT) })
                Text("Fit to content")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = pageSize == PageSize.LETTER, onClick = { onPageSizeChange(PageSize.LETTER) })
                Text("Letter")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = pageSize == PageSize.LEGAL, onClick = { onPageSizeChange(PageSize.LEGAL) })
                Text("Legal")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoDetect, onCheckedChange = onAutoDetectChange)
                Text("Auto-detect page size")
            }
        }
    }
}

private fun decodeFullRes(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > DETECT_CEILING) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}

private fun downsampleBitmap(src: Bitmap, maxEdge: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxEdge) return src
    val scale = maxEdge.toFloat() / longest
    val dstW = (src.width * scale).toInt().coerceAtLeast(1)
    val dstH = (src.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, dstW, dstH, true)
}

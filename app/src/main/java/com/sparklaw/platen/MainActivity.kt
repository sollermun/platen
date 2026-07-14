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
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDateTime
import androidx.compose.ui.focus.onFocusChanged

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { ProfileStore(applicationContext).initialize() }
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PlatenTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileStore = remember { ProfileStore(context.applicationContext) }

    val profiles by profileStore.profiles.collectAsState(initial = emptyList())
    val activeProfileId by profileStore.activeProfileId.collectAsState(initial = ProfileStore.DEFAULT_PROFILE.id)
    val activeProfile = remember(profiles, activeProfileId) {
        profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
    }

    var settingsProfileId by remember { mutableStateOf<String?>(null) }
    var showProfileManager by remember { mutableStateOf(false) }
    var showShareScans by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var folderPickerProfileId by remember { mutableStateOf<String?>(null) }
    var lastSaved by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarIsError by remember { mutableStateOf(false) }

    fun folderUsable(uri: Uri?): Boolean {
        val u = uri ?: return false
        return try {
            DocumentFile.fromTreeUri(context, u)?.canWrite() == true
        } catch (e: Exception) {
            false
        }
    }

    BackHandler(enabled = settingsProfileId != null) { settingsProfileId = null }
    BackHandler(enabled = showProfileManager) { showProfileManager = false }
    BackHandler(enabled = showShareScans) { showShareScans = false }
    BackHandler(enabled = showAbout) { showAbout = false }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val id = folderPickerProfileId ?: return@rememberLauncherForActivityResult
        val target = profiles.find { it.id == id } ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch {
                profileStore.updateProfile(target.copy(folderUri = uri.toString()))
            }
        }
        folderPickerProfileId = null
    }

    if (showShareScans) {
        ShareScansScreen(
            folderUri = activeProfile?.folderUri,
            context = context,
            onBack = { showShareScans = false }
        )
        return
    }

    if (showProfileManager) {
        ProfileManagerScreen(
            profiles = profiles,
            activeProfileId = activeProfileId,
            profileStore = profileStore,
            onEditProfile = { profile ->
                settingsProfileId = profile.id
                showProfileManager = false
            },
            onNewProfileCreated = { profile ->
                settingsProfileId = profile.id
                showProfileManager = false
            },
            onBack = { showProfileManager = false }
        )
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    val editingProfile = settingsProfileId?.let { id -> profiles.find { it.id == id } }
    if (editingProfile != null) {
        SettingsScreen(
            profile = editingProfile,
            profileStore = profileStore,
            onChangeFolderClick = {
                folderPickerProfileId = editingProfile.id
                folderPicker.launch(editingProfile.folderUri?.let(Uri::parse))
            },
            onEditProfiles = {
                settingsProfileId = null
                showProfileManager = true
            },
            onBack = { settingsProfileId = null }
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
        try {
            context.startActivity(Intent.createChooser(send, "Share scan"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to share this file.", Toast.LENGTH_LONG).show()
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val profile = activeProfile ?: return@rememberLauncherForActivityResult
        val data = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pageUris = data?.pages?.map { it.imageUri } ?: emptyList()
        val dest = profile.folderUri?.let(Uri::parse)
        if (pageUris.isEmpty()) {
            status = "Scan cancelled."
            return@rememberLauncherForActivityResult
        }
        if (dest == null) {
            status = "No output folder set. Choose one in this profile's settings."
            return@rememberLauncherForActivityResult
        }
        if (!folderUsable(dest)) {
            status = "Output folder access was lost. Re-select it in this profile's settings."
            return@rememberLauncherForActivityResult
        }
        status = "Processing…"
        val useGray = profile.colorMode == ColorMode.GRAYSCALE
        val useOcr = profile.ocrEnabled
        val useHigh = profile.quality == Quality.HIGH
        val usePageSize = profile.pageSize
        val useAutoDetect = profile.autoDetect
        val useFilenamePattern = profile.filenamePattern
        val useProfileName = profile.name
        scope.launch {
            val out: Uri? = try {
                withContext(Dispatchers.Default) {
                    val maxEdge = if (useHigh) 4000 else 3000
                    val dpi = if (useHigh) 400f else 300f
                    val processed = pageUris.mapNotNull { uri ->
                        decodeFullRes(context, uri)?.let { full ->
                            val straight = deskewBitmap(full)
                            val cropped = detectAndCropPage(straight)
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
                    PdfExporter.export(context, dest, processed, words, dpi, usePageSize, useAutoDetect, useFilenamePattern, useProfileName)
                }
            } catch (e: Throwable) {
                android.util.Log.e("Platen", "save failed uri=$dest profile=${activeProfile?.name}", e)
                snackbarIsError = true
                val msg = when (e) {
                    is PermissionLostException ->
                        "Folder access was lost. Re-select the output folder in Settings."
                    is FolderMissingException ->
                        "The output folder is missing or moved. Re-select it in Settings."
                    is CreateFileException ->
                        "${providerLabel(e.treeUri)} wouldn't create the file. Check the folder, then try again."
                    is WriteFailedException ->
                        if (isLocalProvider(e.treeUri))
                            "Couldn't write the file to the selected folder."
                        else
                            "Saving to ${providerLabel(e.treeUri)} failed — it may be offline. Try again, or save to a local folder and let it sync."
                    is java.io.IOException ->
                        if (e.message?.contains("space", true) == true ||
                            e.message?.contains("ENOSPC", true) == true)
                            "Not enough storage to save the scan."
                        else
                            "Couldn't save — check your output folder in Settings."
                    else ->
                        "Couldn't save the scan. Please try again."
                }
                snackbarHostState.showSnackbar(msg)
                null
            }
            status = ""
            if (out != null) {
                snackbarIsError = false
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
                android.util.Log.e("Platen", "Scanner launch failed", e)
                Toast.makeText(
                    context,
                    "Document scanner unavailable. Make sure Google Play Services is up to date.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platen") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                    IconButton(
                        onClick = { settingsProfileId = activeProfile?.id },
                        enabled = activeProfile != null
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Profile settings")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (snackbarIsError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (snackbarIsError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = if (snackbarIsError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activeProfile == null) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Loading profiles…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.08f)
                )

                if (profiles.size <= 3) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            FilterChip(
                                selected = profile.id == activeProfileId,
                                onClick = { scope.launch { profileStore.setActiveProfile(profile.id) } },
                                label = { Text(profile.name) }
                            )
                        }
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = activeProfile.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        expanded = false
                                        scope.launch { profileStore.setActiveProfile(profile.id) }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { startScan() },
                    enabled = activeProfile.folderUri != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier
                            .size(ButtonDefaults.IconSize)
                            .padding(end = ButtonDefaults.IconSpacing)
                    )
                    Text("Scan", style = MaterialTheme.typography.headlineSmall)
                }

                if (activeProfile.folderUri == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Set an output folder in Settings to start scanning.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showShareScans = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Scans")
                }

                Spacer(Modifier.weight(1.5f))

                if (status.isNotEmpty()) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                val activeFolderName by produceState<String?>(
                    initialValue = null,
                    key1 = activeProfile.folderUri
                ) {
                    val uriStr = activeProfile.folderUri
                    value = if (uriStr == null) {
                        null
                    } else {
                        withContext(Dispatchers.IO) {
                            try {
                                DocumentFile.fromTreeUri(context, Uri.parse(uriStr))?.name
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }

                if (activeProfile.folderUri != null) {
                    Text(
                        "Saving to ${activeFolderName ?: "…"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: Profile,
    profileStore: ProfileStore,
    onChangeFolderClick: () -> Unit,
    onEditProfiles: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun update(block: Profile.() -> Profile) {
        scope.launch { profileStore.updateProfile(profile.block()) }
    }
    val folderLabel by produceState(
        initialValue = if (profile.folderUri == null) "No output folder" else "Loading…",
        key1 = profile.folderUri
    ) {
        val uriStr = profile.folderUri
        value = if (uriStr == null) {
            "No output folder"
        } else {
            withContext(Dispatchers.IO) {
                try {
                    DocumentFile.fromTreeUri(context, Uri.parse(uriStr))?.name ?: "Output folder"
                } catch (e: Exception) {
                    "Output folder"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEditProfiles) {
                        Text("Profiles")
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
            Text(
                profile.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                folderLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            FilledTonalButton(
                onClick = onChangeFolderClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (profile.folderUri == null) "Choose output folder" else "Change output folder")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.colorMode == ColorMode.BITONAL,
                    onClick = { update { copy(colorMode = ColorMode.BITONAL) } }
                )
                Text("Black & white (small)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.colorMode == ColorMode.GRAYSCALE,
                    onClick = { update { copy(colorMode = ColorMode.GRAYSCALE) } }
                )
                Text("Grayscale (smoother, larger)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Searchable PDF", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = profile.ocrEnabled,
                    onCheckedChange = { update { copy(ocrEnabled = !ocrEnabled) } }
                )
                Text("Make PDF searchable (OCR)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Quality", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.quality == Quality.STANDARD,
                    onClick = { update { copy(quality = Quality.STANDARD) } }
                )
                Text("Standard (300 DPI)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.quality == Quality.HIGH,
                    onClick = { update { copy(quality = Quality.HIGH) } }
                )
                Text("High (400 DPI)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Page size", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.pageSize == PageSize.FIT,
                    onClick = { update { copy(pageSize = PageSize.FIT) } }
                )
                Text("Fit to content")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.pageSize == PageSize.LETTER,
                    onClick = { update { copy(pageSize = PageSize.LETTER) } }
                )
                Text("Letter")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = profile.pageSize == PageSize.LEGAL,
                    onClick = { update { copy(pageSize = PageSize.LEGAL) } }
                )
                Text("Legal")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = profile.autoDetect,
                    onCheckedChange = { update { copy(autoDetect = !autoDetect) } }
                )
                Text("Auto-detect page size")
            }
            Text("Filename pattern", style = MaterialTheme.typography.labelLarge)
            var patternField by remember(profile.id) { mutableStateOf(TextFieldValue(profile.filenamePattern)) }
            val sampleTime = remember { LocalDateTime.of(2026, 1, 15, 9, 30, 0) }
            val previewName = remember(patternField.text, profile.name) {
                sanitizeFilename(expandTokens(patternField.text, profile.name, sampleTime)) + ".pdf"
            }
            OutlinedTextField(
                value = patternField,
                onValueChange = {
                    patternField = it
                    update { copy(filenamePattern = it.text) }
                },
                label = { Text("Pattern") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Preview: $previewName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Insert token",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            val tokens = remember { listOf("{datetime}", "{date}", "{time}", "{year}", "{month}", "{day}", "{hour}", "{minute}", "{second}", "{profile}", "{n}") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tokens.forEach { token ->
                    AssistChip(
                        onClick = {
                            val sel = patternField.selection
                            val cur = patternField.text
                            val start = sel.min
                            val end = sel.max
                            val newText = cur.substring(0, start) + token + cur.substring(end)
                            val newCursor = start + token.length
                            patternField = TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursor)
                            )
                            update { copy(filenamePattern = newText) }
                        },
                        label = { Text(token, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerScreen(
    profiles: List<Profile>,
    activeProfileId: String,
    profileStore: ProfileStore,
    onEditProfile: (Profile) -> Unit,
    onNewProfileCreated: (Profile) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameProfileId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteProfileId by remember { mutableStateOf<String?>(null) }

    fun promptAdd() { showAddDialog = true }
    fun promptRename(id: String) { renameProfileId = id; showRenameDialog = true }
    fun promptDelete(id: String) { deleteProfileId = id; showDeleteDialog = true }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New profile") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                val newProfile = profileStore.addProfile(trimmed)
                                onNewProfileCreated(newProfile)
                            }
                        }
                    },
                    enabled = name.trim().isNotEmpty()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        val profile = profiles.find { it.id == renameProfileId }
        var name by remember(renameProfileId) { mutableStateOf(profile?.name ?: "") }
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameProfileId = null
            },
            title = { Text("Rename profile") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = renameProfileId
                        showRenameDialog = false
                        renameProfileId = null
                        val trimmed = name.trim()
                        if (id != null && trimmed.isNotEmpty()) {
                            scope.launch { profileStore.renameProfile(id, trimmed) }
                        }
                    },
                    enabled = name.trim().isNotEmpty()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    renameProfileId = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        val profile = profiles.find { it.id == deleteProfileId }
        val canDelete = profiles.size > 1
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deleteProfileId = null
            },
            title = { Text("Delete ${profile?.name ?: "profile"}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = deleteProfileId
                        showDeleteDialog = false
                        deleteProfileId = null
                        if (id != null && canDelete) {
                            scope.launch { profileStore.deleteProfile(id) }
                        }
                    },
                    enabled = canDelete
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteProfileId = null
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { promptAdd() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add profile")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isActive = profile.id == activeProfileId
                val canDelete = profiles.size > 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditProfile(profile) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        if (isActive) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = { promptRename(profile.id) }) { Text("Rename") }
                    IconButton(
                        onClick = { promptDelete(profile.id) },
                        enabled = canDelete
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete profile")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
            Text(
                "Platen",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Version 1.1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Tips", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Cloud sync: To sync scans automatically, set a profile's output folder to a folder inside your cloud storage app (Nextcloud, Google Drive, Dropbox, and others). Platen saves there; your cloud app handles syncing.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Profiles: Create a profile for each type of document. Each profile remembers its own output folder and scan settings — for example, a Receipts profile and a Documents profile saving to different folders.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("About", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Platen is a private, on-device document scanner. No account, no servers, no data collection.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Open source under the Apache License 2.0.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "© 2026 Law Office of Samuel H. Park, APC",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://sparklawfirm.com/platen-privacy-policy.html")
                    )
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No browser available", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Text("Privacy Policy")
            }
        }
    }
}

data class PdfEntry(val uri: Uri, val name: String, val dateLabel: String, val modified: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScansScreen(
    folderUri: String?,
    context: Context,
    onBack: () -> Unit
) {
    val treeUri = folderUri?.let(Uri::parse)
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<PdfEntry>>(emptyList()) }
    val checked = remember { mutableStateListOf<Uri>() }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    suspend fun loadEntries() {
        loading = true
        val result = if (treeUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    DocumentFile.fromTreeUri(context, treeUri)
                        ?.listFiles()
                        ?.filter { it.isFile && it.name?.endsWith(".pdf", ignoreCase = true) == true }
                        ?.map { f ->
                            val mod = f.lastModified()
                            val label = if (mod > 0) dateFmt.format(Date(mod)) else ""
                            PdfEntry(f.uri, f.name ?: f.uri.lastPathSegment ?: "scan.pdf", label, mod)
                        }
                        ?.sortedByDescending { it.modified }
                        ?: emptyList()
                } catch (_: Exception) { emptyList() }
            }
        } else emptyList()
        entries = result
        loading = false
    }

    LaunchedEffect(treeUri) {
        entries = emptyList()
        checked.clear()
        loadEntries()
    }

    fun shareSelected() {
        val uris = ArrayList(checked)
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri("", uris.first()).also { cd ->
                uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share scans"))
    }

    if (showDeleteDialog) {
        val count = checked.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $count ${if (count == 1) "scan" else "scans"}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteDialog = false
                    val toDelete = ArrayList(checked)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            toDelete.forEach { uri ->
                                DocumentFile.fromSingleUri(context, uri)?.delete()
                            }
                        }
                        checked.clear()
                        loadEntries()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Scans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareSelected() },
                        enabled = checked.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Share selected")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = checked.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                treeUri == null -> {
                    Text(
                        "No output folder set. Go to this profile's settings to choose one.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                entries.isEmpty() -> {
                    Text(
                        "No scans yet.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.uri.toString() }) { entry ->
                            val isChecked = entry.uri in checked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { on ->
                                        if (on) checked.add(entry.uri)
                                        else checked.remove(entry.uri)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                    if (entry.dateLabel.isNotEmpty()) {
                                        Text(
                                            entry.dateLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
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

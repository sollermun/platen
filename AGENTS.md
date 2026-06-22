# AGENTS.md — Platen (bitonal document scanner for Android)

> Context for AI agents and human contributors working on this repo.
> This app targets **Google Play**. It is intentionally Google/ML Kit-based.
> The merged manifest includes `INTERNET` and `ACCESS_NETWORK_STATE` via Google's
> transitive dependencies — this is expected and acceptable. Read the Security &
> Privacy section before adding code or dependencies.

---

## 1. What this app is

A deliberately minimal Android app that scans paper documents (primarily
**receipts**) and saves them as **small black-and-white (or grayscale) PDFs**.
It exists because mainstream scanner apps embed high-resolution JPEGs that
produce multi-hundred-KB pages even in "black & white" mode. This app
straightens, crops, binarizes (or desaturates) each page and stores it
compactly, then writes the PDF to a user-chosen folder that an external sync
tool (e.g. FolderSync → Nextcloud) can pick up. It can also share the PDF
directly via the Android share sheet.

Design ethos: no frills, no accounts, no analytics we add, no tracking. It does
one thing. Keep it that way. Google/ML Kit dependencies are intentional.

App name: **Platen**. Package: `com.sparklaw.platen`.

---

## 2. Architecture / data flow

### 2a. Capture

Google ML Kit Document Scanner (`GmsDocumentScanner`) provides the entire
capture UX: camera, edge detection, perspective correction, cleanup. It returns
cropped page images as JPEG URIs. No `CAMERA` permission is needed by this
app — ML Kit runs inside Google Play services.
→ wired in `MainActivity.kt` (`startScan`, `scannerLauncher`).

### 2b. Per-page processing pipeline

Each captured JPEG URI passes through this sequence, all in
`withContext(Dispatchers.Default)`:

```
decodeFullRes(uri)
  → deskewBitmap(full)
  → detectAndCropPage(straight)
  → whitenResidualFill(cropped copy)
  → downsampleBitmap(whitened, maxEdge)
  → Binarizer.toBitonal(page)  OR  Binarizer.toGrayscale(page)
  → [optional] Ocr.recognize(page)
  → PdfExporter.export(...)
```

**Step 1 — `decodeFullRes` (`MainActivity.kt`):**
Decodes the JPEG at native resolution, capped at `DETECT_CEILING` = 6000 px on
the long edge via `BitmapFactory.inSampleSize`. Does **not** pre-downsample to
`maxEdge`; that happens after cleaning so the crop and deskew operate at the
highest available resolution.

**Step 2 — `deskewBitmap` (`EdgeCrop.kt`):**
ML Kit's perspective dewarp fills the off-document area with near-black. This
step estimates the residual rotation of the page boundary from that fill:
1. Runs `borderFloodMask` to get the off-page fill mask.
2. Per column, finds the first non-fill pixel from the top (top-edge transition)
   and from the bottom (bottom-edge transition). Collects only columns where
   fill was actually present on that edge.
3. Fits a least-squares line through each set of `(col, row)` transition points;
   computes `angle = atan(slope)` in degrees.
4. Cross-checks top vs bottom: if they disagree by more than
   `MAX_EDGE_DISAGREE_DEG` = 2.0°, estimation fails and the image passes
   through unrotated.
5. Skips rotation if `|angle| < MIN_DESKEW_DEG` (0.3° — not worth a resample)
   or `|angle| > MAX_DESKEW_DEG` (10° — implausible misdetection guard).
6. Otherwise rotates by `-angle` around the image centre using `Matrix.setRotate`
   + `Bitmap.createBitmap(..., matrix, true)` (bilinear filtering).

**Step 3 — `detectAndCropPage` (`EdgeCrop.kt`):**
Re-runs `borderFloodMask` on the (now-straight) bitmap and crops to the true
page boundary:
- For each edge (top/bottom: row scan; left/right: column scan), finds the
  deepest inward row/column that still has any fill pixel.
- Sets the crop boundary just past that row/column.
- Per-edge safety cap: if a proposed crop exceeds `MAX_EDGE_CROP_FRACTION`
  (0.25) of that dimension, detection is treated as failed for **that edge
  only** and the edge is left uncropped; other edges proceed normally.
- Returns `src` unchanged if no meaningful crop is found (clean capture with
  no fill).

**Step 4 — `whitenResidualFill` (`EdgeCrop.kt`):**
After the axis-aligned crop, slight rotation leaves triangular fill slivers at
corners. Re-runs `borderFloodMask` on the cropped bitmap and repaints all
border-connected near-black pixels white. Bails and returns the bitmap unchanged
if the fill count exceeds `MAX_FILL_FRACTION` (0.50) of total pixels (misfire
guard).

**Step 5 — `downsampleBitmap` (`MainActivity.kt`):**
Scales the cleaned page down to `maxEdge` on the long edge using
`Bitmap.createScaledBitmap` (bilinear). Standard: `maxEdge` = 3000. High:
`maxEdge` = 4000. If the cleaned page is already ≤ `maxEdge`, it is returned
unchanged (no upscaling).

**Step 6 — `Binarizer` (`Binarizer.kt`):**
- `toBitonal(src, t=0.15, windowDiv=8)` — pure black/white via **Bradley-Roth
  adaptive thresholding** (integral-image local mean). Smallest files, "fax"
  look. Robust to receipt lighting / thermal fade.
- `toGrayscale(src)` — luminance desaturation. Smooth/anti-aliased, larger
  files.
Both return an `ARGB_8888` bitmap.

**Step 7 — `Ocr.recognize` (`Ocr.kt`, optional):**
When "Make PDF searchable" is on, runs **ML Kit Text Recognition** with the
bundled Latin model. Iterates `Text → blocks → lines`; for each line emits an
`OcrWord(text = line.text.trim(), box = line.boundingBox)`. No model download;
entirely on-device.

**Step 8 — `PdfExporter.export` (`PdfExporter.kt`):**
Wraps each processed bitmap in a PDF page via **pdfbox-android**
(`LosslessFactory.createFromImage` → Flate compression; ~70 KB/page bitonal
observed). Page sizing via `computeDrawRect`:
- `PageSize.FIT` — page = image at 300 / 400 DPI (natural size); image
  fills the page exactly.
- `PageSize.LETTER` / `PageSize.LEGAL` — fixed page dimensions (Letter:
  612 × 792 pt; Legal: 612 × 1008 pt). Scale = `min(pageW/imgW, pageH/imgH)`
  using **full page dimensions** (`PAGE_MARGIN_PT` = 0). The binding dimension
  goes flush to the page edge; the other axis is centered with whatever small
  margin the aspect difference leaves.
- **Auto-detect:** if enabled, snaps to whichever fixed size has the nearest
  aspect ratio (`RECEIPT_RATIO_THRESHOLD` = 0.5 — images with short/long < 0.5
  are treated as receipts and use Fit regardless).
- **OCR text layer:** when OCR words are provided, a second content stream per
  page draws invisible text (`RenderingMode.NEITHER`) positioned using the
  `OcrWord.box` scaled from image pixels to PDF points via the actual `DrawRect`
  (`imgX, imgY, imgW, imgH`). Alignment holds across all page size modes.

Output filename: `yyyy.MM.dd.HH.mm.ss scan.pdf` written to the SAF folder.

> **Not implemented:** edge-trim / overscan removal as a separate step.
> Full-page placement (flush scaling, `PAGE_MARGIN_PT` = 0) made a dedicated
> overscan trim unnecessary; any residual fill is handled by `whitenResidualFill`.

### 2c. UI (`MainActivity.kt`, Jetpack Compose)

Two screens, both using Material3 `Scaffold` + `TopAppBar` (proper inset
handling; no manual status-bar padding):

**ScannerScreen** (main):
- TopAppBar title "Platen", gear `IconButton` opens Settings.
- Small caption: "No folder selected" / "Saving to Scans".
- Transient status text while processing/recognizing.
- **Scan** — primary filled `Button` (full width); disabled until folder set.
- **Change output folder** — `FilledTonalButton` (full width).
- **Share last scan** — `OutlinedButton` (full width); disabled until a scan exists.
- After save: `SnackbarHost` shows "Saved \<filename\>" with **Share** action.

**SettingsScreen**:
- TopAppBar title "Settings", back arrow as `navigationIcon`.
- Content in a `verticalScroll` column.
- **Mode** — radio: Black & white / Grayscale.
- **Searchable PDF** — checkbox: Make PDF searchable (OCR).
- **Quality** — radio: Standard (300 DPI, 3000 px) / High (400 DPI, 4000 px).
- **Page size** — radio: Fit to content / Letter / Legal.
- **Auto-detect page size** — checkbox.

All settings persisted in `SharedPreferences` (keys: `KEY_GRAYSCALE`,
`KEY_OCR`, `KEY_HIGH_QUALITY`, `KEY_PAGE_SIZE`, `KEY_AUTO_DETECT`, `KEY_TREE`).

---

## 3. Tech stack (pinned)

| Thing | Version | Notes |
|---|---|---|
| Language | Kotlin 2.0.21 | |
| UI | Jetpack Compose BOM 2024.12.01, Material3 | two screens |
| Android Gradle Plugin | 8.7.3 | needs Gradle ≥ 8.9 |
| Gradle | 8.11.1 | via wrapper |
| JDK (to build) | 17 | **not** the Ubuntu-default 21 JRE — see Gotchas |
| minSdk / compileSdk / targetSdk | 24 / 35 / 35 | |
| ML Kit doc scanner | play-services-mlkit-document-scanner 16.0.0-**beta1** | proprietary; track for stable release |
| PDF | com.tom-roush:pdfbox-android 2.0.27.0 | Apache-2.0 |
| OCR | com.google.mlkit:text-recognition 16.0.1 | bundled Latin model; on-device; no download |
| Coroutines/Tasks bridge | kotlinx-coroutines-play-services 1.7.3 | `Task.await()` for ML Kit |
| SAF | androidx.documentfile 1.0.1 | |
| core-ktx | androidx.core:core-ktx 1.15.0 | |
| activity-compose | androidx.activity:activity-compose 1.9.3 | |

Package: `com.sparklaw.platen`.

---

## 4. Project structure

```
docscanner/                         (Gradle root)
├── AGENTS.md                       (this file)
├── BACKLOG.md
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties               (android.useAndroidX=true, etc.)
├── local.properties                (sdk.dir — NOT committed)
├── gradlew / gradle/               (wrapper)
└── app/
    ├── build.gradle.kts            (all deps live here)
    └── src/main/
        ├── AndroidManifest.xml     (no camera/storage perms; INTERNET is transitive from ML Kit)
        ├── res/values/themes.xml
        └── java/com/sparklaw/platen/
            ├── MainActivity.kt     (Compose UI, orchestration, SAF folder, prefs, scan coroutine)
            ├── Binarizer.kt        (toBitonal / toGrayscale)
            ├── EdgeCrop.kt         (deskewBitmap, detectAndCropPage, whitenResidualFill + helpers)
            ├── Ocr.kt              (ML Kit Text Recognition; returns List<OcrWord> at line level)
            └── PdfExporter.kt      (bitmap → PDF, page sizing, invisible OCR text layer)
```

---

## 5. EdgeCrop.kt — tunable constants

| Constant | Value | Controls |
|---|---|---|
| `FILL_LUMA_MAX` | 90 | Luminance ceiling for a pixel to be considered off-page fill |
| `SEED_MARGIN` | 3 | Width (px) of the border strip seeded into the flood-fill; bridges JPEG artifact pixels at image edges |
| `MAX_EDGE_CROP_FRACTION` | 0.25 | Maximum fraction of a dimension cropped from any single edge; exceeded → that edge left uncropped |
| `MAX_FILL_FRACTION` | 0.50 | Whiten bail-out: if fill covers > 50 % of the cropped bitmap, whitenResidualFill returns unchanged |
| `DETECT_CEILING` | 6000 | Long-edge cap (px) for the full-res decode used by deskew and crop |
| `MIN_DESKEW_DEG` | 0.3 | Minimum |angle| to trigger a rotation; below this, not worth the resample |
| `MAX_DESKEW_DEG` | 10.0 | Maximum |angle| treated as plausible; above this, assumed misdetection |
| `MAX_EDGE_DISAGREE_DEG` | 2.0 | (private) Top-vs-bottom skew estimate disagreement threshold; exceeded → estimation fails |

---

## 6. Build / run / deploy

Built and run from **WSL2 (Ubuntu)** on a Windows host; tested on a physical
phone over **wireless adb** (no emulator — ML Kit needs Google Play services).
`adb` lives at `~/Android/Sdk/platform-tools/adb`; add to `PATH` or prefix
commands with the full path.

```bash
# one-time per shell: point at the real JDK 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # also in ~/.bashrc

# compile check only (no device needed) — ALWAYS run after editing source
./gradlew assembleDebug

# connect a phone (Wireless debugging on, same LAN; WSL2 must use mirrored networking)
~/Android/Sdk/platform-tools/adb connect <phone-ip>:<port>
~/Android/Sdk/platform-tools/adb devices
export ANDROID_SERIAL=<phone-ip>:<port>               # pin target if multiple/stale entries
./gradlew installDebug
```

Debugging a crash on device: `adb logcat -c`, reproduce, then
`adb logcat -d -b crash`.

---

## 7. Key design decisions (and why)

- **Full-res decode before crop/deskew, then downsample.** Crop and skew
  estimation operate on up to 6000 px; the quality downsampling to 3000/4000 px
  happens only after the page is clean. This gives the fill-boundary detection
  the most pixels to work with and avoids precision loss.
- **Flood-fill, not row/column threshold, for fill detection.** The off-page
  fill is connected to image borders; flood-fill naturally excludes dark content
  in the middle of the page (e.g. dark photos or logos). A simple dark-row scan
  would over-crop documents with dark content near an edge.
- **3-pixel seed margin.** JPEG compression introduces artifact pixels at block
  boundaries right at the image edge. Seeding only the outermost pixel row/col
  leaves the fill unreachable if a single artifact pixel breaks connectivity.
  Seeding a 3-pixel-deep strip reliably bridges these artifacts.
- **LosslessFactory, not CCITTFactory, for PDF encoding.** pdfbox-android's
  `CCITTFactory.createFromImage(Bitmap)` only accepts `ALPHA_8` and reads bits
  back via `getPixels()`, which is inconsistent across Android versions and can
  yield blank pages. `LosslessFactory` on `ARGB_8888` Flate-compresses well
  (~70 KB/page bitonal). Do not "optimize" back to CCITT without verifying the
  `ALPHA_8`/`getPixels` round-trip on devices.
- **Adaptive (Bradley-Roth) threshold, not global Otsu** — robust to receipt
  lighting/fade. Tunable via `t` (default 0.15) and `windowDiv` (default 8).
- **PAGE_MARGIN_PT = 0 for fixed-size pages.** Scale = `min(pageW/imgW,
  pageH/imgH)` against the full page. Binding dimension goes flush; the other
  is centered. This gives true copier fill for a Letter-aspect document and
  requires no separate overscan-trim step.
- **OCR at line level, not word level.** `line.text` + `line.boundingBox`
  avoids inter-word spacing issues in the invisible text layer; words within a
  line are space-separated naturally.
- **Storage Access Framework, not storage permissions** — user explicitly grants
  one folder; no broad storage access.
- **Sharing** is user-initiated `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`
  + `clipData` for the single saved PDF.
- **On-device OCR** — bundled Latin model; no server call for OCR.
- **No analytics we add** — no crash-reporting or analytics SDKs.

---

## 8. Memory / performance notes

Peak memory for a single page at High quality:
- Full-res ARGB_8888 bitmap (≤ 6000 px): up to ~144 MB
- `borderFloodMask` allocates an `IntArray(w*h)` + `BooleanArray(w*h)` on top
- `Binarizer.toBitonal` allocates a `LongArray(w*h)` integral image on the
  downsampled (≤ 4000 px) bitmap: ~128 MB worst case
- Total worst-case per-page working set: ~300–350 MB

Multi-page scans process pages sequentially (not in parallel). Standard quality
(3000 px) keeps the working set roughly half that. The full-res bitmap is
replaced by the cropped/downsampled page before binarizing, so both are not
live simultaneously for long.

---

## 9. Security & Privacy (READ BEFORE ADDING ANYTHING)

Preserve these invariants:

- **INTERNET in merged manifest is expected.** `INTERNET` and
  `ACCESS_NETWORK_STATE` appear via Google's transitive libraries (ML Kit/GMS).
  This app's own code makes no network calls. Do not add new network-using SDKs
  without deliberate review.
- **Minimal permissions.** No `CAMERA`, no `READ/WRITE_EXTERNAL_STORAGE`, no
  `MANAGE_EXTERNAL_STORAGE`. File access is SAF only. Sharing grants read
  access only to the single shared file, only to the chosen target.
- **No analytics / telemetry / crash-reporting SDKs we add.** Receipts are
  financial documents; nothing about document content leaves the device except
  via the user's explicit save or share action.
- **No data collection or sharing** (Play "Data Safety"). The app never
  transmits document content; user-initiated OS share is not "collection."
- **Exported surface.** Only `MainActivity` (launcher) is exported. No exported
  services, receivers, or content providers. No WebView.
- **Untrusted input.** `setGalleryImportAllowed(true)` lets users import
  arbitrary images; decoding goes through `BitmapFactory` with a bounds pass
  first.
- **Backup.** `android:allowBackup` is currently true; review for release
  (only a SAF URI + mode flags are stored in prefs).

### Release hardening (do before publishing)
- Enable **R8/minify + resource shrinking** (`isMinifyEnabled = true`); add keep
  rules for pdfbox-android / ML Kit if needed. Currently minify is OFF.
- **Signing:** use Play App Signing; keep keystore and passwords out of version
  control (inject via env/Gradle properties). Never commit a keystore.
- ML Kit doc scanner is still `-beta1` — watch for a stable release.

---

## 10. Release plan & requirements

### Google Play (primary target)
- Intentionally ML Kit / Google-based; Play is the target store.
- **New-personal-account testing gate** (closed test, ~12 testers × 14 days)
  applies — OR register as an **organization** (DUNS number) to be exempt.
- **Data Safety form:** declare *no data collected, no data shared*.
- targetSdk 35 currently meets Play requirements; re-check at submission time.
- Use Play App Signing.

### F-Droid (future / optional)
- Not currently targeted. The app uses ML Kit and Google Play services
  (proprietary) and is not F-Droid-eligible in its current form.
- A `foss` product flavor (CameraX + open-source OCR, no GMS) is tracked in
  BACKLOG as a low-priority future item.

---

## 11. Roadmap / open work

1. **Deskew + crop** — implemented (EdgeCrop.kt).
2. **OCR text layer** — implemented: ML Kit, line-level, invisible layer in PDF.
3. **Settings screen** — implemented: Mode / OCR / Quality / Page size /
   Auto-detect.
4. **Expose threshold `t` / DPI** in settings for power users — not yet done.
5. **`foss` flavor** — optional, low priority; see §10.
6. Release hardening per Section 9.

---

## 12. Conventions for agents

- **Always run `./gradlew assembleDebug` after editing source** to confirm it
  compiles before declaring done.
- **Editor paste hazard:** auto-close-brackets has repeatedly corrupted pasted
  Kotlin (doubled `}`). Prefer writing large edits as targeted find/replace
  blocks; verify brace balance and compile.
- **Library currency:** ML Kit doc scanner and pdfbox-android APIs may differ
  from model training data — consult current docs/source rather than assuming.
  (The CCITT decision above came from reading pdfbox-android source directly.)
- **Do not add** analytics, crash-reporting, or new broad storage/network
  permissions without flagging against Sections 9 and 10.
- Keep the app single-purpose and the dependency list small.

---

## 13. Gotchas log (things already hit — don't repeat)

- **JDK:** build with JDK **17** (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`).
  Ubuntu 24.04's default `java` is a **21 JRE with no `javac`** → Gradle fails
  with "does not provide the required capabilities: [JAVA_COMPILER]".
- **AndroidX:** `gradle.properties` must contain `android.useAndroidX=true`.
- **Gradle wrapper** was generated manually (the scaffold shipped without it).
- **WSL2 + adb:** default NAT networking can't reach the phone; set
  `networkingMode=mirrored` in `%UserProfile%\.wslconfig`, then `wsl --shutdown`.
- **adb wireless:** the *pair* port and the *connect* port differ; `adb pair`
  may print a scary "protocol fault … Success" yet still pair. `ANDROID_SERIAL`
  goes stale when the phone re-pairs (new port) — update or unset it.
- **adb not on PATH in WSL:** lives at `~/Android/Sdk/platform-tools/adb`.
- **CCITTFactory** needs ALPHA_8 bitmaps; we use LosslessFactory instead (see §7).
- **OOM risk** at High quality — see §8. Process pages sequentially.
- **Flood-fill left/right seeding:** a single-pixel seed column fails when JPEG
  artifacts sit right at the image edge (luma slightly above threshold). The
  3-pixel `SEED_MARGIN` was introduced to fix this asymmetry.

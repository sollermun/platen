# AGENTS.md — Platen (bitonal document scanner for Android)

> Context for AI agents (Devin) and human contributors working on this repo.
> This app targets **Google Play**. It is intentionally Google/ML Kit-based.
> The merged manifest includes `INTERNET` and `ACCESS_NETWORK_STATE` via Google's
> transitive dependencies — this is expected and acceptable. Read the Security &
> Privacy section before adding code or dependencies.

---

## 1. What this app is

A deliberately minimal Android app that scans paper documents (primarily
**receipts**) and saves them as **small black-and-white (or grayscale) PDFs**.
It exists because mainstream scanner apps embed high-resolution JPEGs that
produce multi-hundred-KB pages even in "black & white" mode. This app binarizes
(or desaturates) each page and stores it compactly, then writes the PDF to a
user-chosen folder that an external sync tool (e.g. FolderSync → Nextcloud) can
pick up. It can also share the PDF directly via the Android share sheet.

Design ethos: no frills, no accounts, no analytics we add, no tracking. It does
one thing. Keep it that way. Google/ML Kit dependencies are intentional.

App name: **Platen**. Package: `com.sparklaw.platen`.

---

## 2. Architecture / data flow

Three stages, each isolated in its own file:

1. **Capture** — Google ML Kit Document Scanner (`GmsDocumentScanner`) provides
   the entire capture UX: camera, edge detection, perspective correction,
   cleanup. It returns cropped page images as JPEG URIs. (No camera permission
   is needed by THIS app — ML Kit runs inside Google Play services.)
   → wired in `MainActivity.kt` (`startScan`, `scannerLauncher`).

2. **Process** — `Binarizer.kt`. Loads each page (downsampled, see below) and,
   depending on the user's mode selection:
   - `toBitonal()` — pure black/white via **Bradley-Roth adaptive thresholding**
     (integral-image local mean; robust to receipt lighting/thermal fade).
     Smallest files, "fax" look.
   - `toGrayscale()` — luminance desaturation. Smooth/anti-aliased, larger files.
   Both return an ARGB_8888 bitmap.

3. **OCR** (optional) — `Ocr.kt`. When the "Make PDF searchable" toggle is on,
   runs **ML Kit Text Recognition** (`com.google.mlkit:text-recognition`) with
   the bundled Latin model on each processed bitmap. Returns a `List<OcrWord>`
   (text + bounding box in image-pixel coordinates) by walking
   `Text → blocks → lines → elements` (elements are word-level). No model
   download needed; runs entirely on-device via ML Kit's Task executor.
   → wired in `MainActivity.kt` scan coroutine, called as a suspend function.

4. **Export** — `PdfExporter.kt`. Wraps each page as an image in a PDF via
   **pdfbox-android**. When OCR word lists are supplied, appends a second
   content stream per page with **invisible text** (PDF render mode 3 —
   `RenderingMode.NEITHER`): each word is positioned using the ML Kit
   bounding box scaled from image pixels to PDF points, so the PDF is
   visually identical but text-selectable and searchable. Writes the file
   into the user's chosen folder (Storage Access Framework).

UI (in `MainActivity.kt`, Compose): radio buttons select B&W vs grayscale;
 a checkbox toggles OCR (default on, persisted in prefs); a folder picker
(SAF) sets the destination; after a successful scan a **Share last scan**
button opens an `ACTION_SEND` chooser.

`loadBitmap` downsamples each captured page to ~3000px on the long edge before
processing — keeps memory bounded and files small. `PdfExporter` sizes pages at
300 DPI.

---

## 3. Tech stack (pinned)

| Thing | Version | Notes |
|---|---|---|
| Language | Kotlin 2.0.21 | |
| UI | Jetpack Compose (BOM 2024.12.01), Material3 | single screen |
| Android Gradle Plugin | 8.7.3 | needs Gradle ≥ 8.9 |
| Gradle | 8.11.1 | via wrapper |
| JDK (to build) | 17 | **not** the Ubuntu-default 21 JRE — see Gotchas |
| minSdk / compileSdk / targetSdk | 24 / 35 / 35 | |
| ML Kit doc scanner | play-services-mlkit-document-scanner 16.0.0-**beta1** | proprietary; track for a stable release |
| PDF | com.tom-roush:pdfbox-android 2.0.27.0 | Apache-2.0 |
| OCR | com.google.mlkit:text-recognition 16.0.1 | bundled Latin model; on-device; no download |
| Coroutines/Tasks bridge | kotlinx-coroutines-play-services 1.7.3 | `Task.await()` for ML Kit |
| SAF | androidx.documentfile 1.0.1 | |

Package: `com.sparklaw.platen`.

---

## 4. Project structure

```
docscanner/                         (Gradle root)
├── AGENTS.md                       (this file)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties               (android.useAndroidX=true, etc.)
├── local.properties                (sdk.dir — NOT committed)
├── gradlew / gradle/               (wrapper)
└── app/
    ├── build.gradle.kts            (deps live here)
    └── src/main/
        ├── AndroidManifest.xml     (no camera/storage permissions; INTERNET comes from ML Kit transitively)
        ├── res/values/themes.xml
        └── java/com/sparklaw/platen/
            ├── MainActivity.kt     (Compose UI, ML Kit launch, SAF folder, mode radios, OCR toggle, share, orchestration)
            ├── Binarizer.kt        (toBitonal / toGrayscale)
            ├── Ocr.kt              (ML Kit Text Recognition wrapper; returns List<OcrWord> with text + pixel bounding boxes)
            └── PdfExporter.kt      (bitmap -> PDF via pdfbox-android; invisible text layer when OCR words supplied)
```

---

## 5. Build / run / deploy

Built and run from **WSL2 (Ubuntu)** on a Windows host; tested on a physical
phone over **wireless adb** (no emulator — ML Kit needs Google Play services).

```bash
# one-time per shell: point at the real JDK 17 (Ubuntu default `java` is a 21 JRE without javac)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # also in ~/.bashrc

# compile check only (no device needed) — ALWAYS run this after editing source
./gradlew assembleDebug

# connect a phone (Wireless debugging on, same LAN; WSL2 must use mirrored networking)
adb connect <phone-ip>:<port>
adb devices
export ANDROID_SERIAL=<phone-ip>:<port>               # pin target if multiple/stale entries
./gradlew installDebug
```

Debugging a crash on device: `adb logcat -c`, reproduce, then
`adb logcat -d -b crash`.

---

## 6. Key design decisions (and why)

- **LosslessFactory, not CCITTFactory, for PDF encoding.** True 1-bit CCITT G4
  would be smallest, but pdfbox-android's `CCITTFactory.createFromImage(Bitmap)`
  only accepts `Bitmap.Config.ALPHA_8` and reads bits back via `getPixels()`,
  whose behavior on ALPHA_8 is inconsistent across Android versions and can
  silently yield blank pages. We use `LosslessFactory.createFromImage` on an
  ARGB_8888 bitmap; on a binarized page the flat areas Flate-compress well
  (~70 KB/page bitonal observed; grayscale a few hundred KB). Do not "optimize"
  back to CCITT without verifying the ALPHA_8/getPixels round-trip on devices.
- **Adaptive (Bradley-Roth) threshold, not global Otsu** — robust to receipt
  lighting/fade. Tunable via `t` (default 0.15) and `windowDiv` (default 8).
- **B&W vs grayscale is a user choice** (radio buttons, persisted). Resolution
  is 3000px long edge at 300 DPI — crisper than the original 2000px/150.
- **Downsample before processing** — bounds memory, keeps files small.
- **Storage Access Framework, not storage permissions** — see Security.
- **Sharing** is user-initiated `ACTION_SEND` of the just-saved PDF, with a
  scoped `FLAG_GRANT_READ_URI_PERMISSION` (+ clipData) so the chooser target can
  read that one file. No new manifest permissions; no broad access.
- **On-device OCR** — ML Kit text recognition uses the bundled Latin model;
  no server call is made for OCR. Capture-side ML Kit may use Play services
  for model updates (out of this app's control).
- **No analytics we add** — no crash-reporting or analytics SDKs in this app's
  own code.

---

## 7. Security & Privacy (READ BEFORE ADDING ANYTHING)

Preserve these invariants:

- **INTERNET in merged manifest is expected.** `INTERNET` and
  `ACCESS_NETWORK_STATE` appear in the merged manifest via Google's transitive
  libraries (ML Kit / GMS). This app's own code makes no network calls and adds
  no network permissions directly. Do not add new network-using SDKs without
  deliberate review.
- **Minimal permissions.** No `CAMERA` (ML Kit uses Play services' camera), no
  `READ/WRITE_EXTERNAL_STORAGE`, no `MANAGE_EXTERNAL_STORAGE`. File access is
  **SAF only**: the user explicitly grants one folder via `OpenDocumentTree`,
  and we persist that URI permission. Sharing grants read access to only the
  single shared file, only to the chosen target. Do not add broad storage perms.
- **No analytics / telemetry / crash-reporting SDKs we add.** Receipts are
  financial documents; nothing about document content leaves the device except
  via the user's explicit folder save or share action.
- **No data collection or sharing** (in the Play "Data Safety" sense). The app
  never transmits document content; user-initiated OS share is not "collection."
- **Exported surface.** Only `MainActivity` (the launcher) is exported. No
  exported services, receivers, or content providers. No WebView. Keep it so.
- **Untrusted input.** `setGalleryImportAllowed(true)` lets users import
  arbitrary images; decoding goes through `BitmapFactory` with a bounds pass
  first. Consider disabling gallery import if tighter scope is wanted.
- **Backup.** `android:allowBackup` is currently true; review for release
  (only a SAF URI + a mode flag are stored in prefs, but decide deliberately).

### Release hardening (do before publishing)
- Enable **R8/minify + resource shrinking** for the release build
  (`isMinifyEnabled = true`); add keep rules for pdfbox-android / ML Kit if
  stripping breaks them. Currently minify is OFF.
- **Signing:** use Play App Signing; keep the upload keystore and passwords
  **out of version control** (inject via env/Gradle properties). Never commit a
  keystore. Add keystore patterns to `.gitignore`.
- Pin and review all dependencies; ML Kit is a `-beta1` — watch for a stable
  release before a production launch.

---

## 8. Release plan & requirements

### Google Play (primary target)
- The app is intentionally ML Kit / Google-based; Play is the target store.
- **New-personal-account testing gate** (closed test, ~12 testers × 14 days)
  applies — OR register the developer account as an **organization** (a
  registered business entity with a DUNS number) to be **exempt** from that gate.
- **Data Safety form:** declare *no data collected, no data shared* (true as
  long as Section 7 invariants hold — no analytics SDKs added by us).
- targetSdk 35 currently meets Play requirements; re-check at submission time.
- Use Play App Signing.

### F-Droid (future / optional)
- Not currently targeted. The app uses ML Kit and Google Play services
  (proprietary), so it is **not F-Droid-eligible** in its current form.
- A `foss` product flavor (CameraX capture + open-source OCR, no GMS) would be
  required to pursue F-Droid. Tracked in BACKLOG as a low-priority future item.

---

## 9. Roadmap / open work

1. **Output quality — partly done.** Resolution bumped to 3000px/300 DPI and a
   B&W-vs-grayscale radio selector is implemented. Remaining: optionally expose
   the threshold (`t`) / DPI in a settings screen for power users.
2. **OCR text layer** — implemented: ML Kit Text Recognition, bundled Latin
   model, invisible text layer in PDF, toggleable. On-device only.
3. **Settings screen** (DPI, threshold, one-PDF-per-receipt vs per-session).
4. **`foss` flavor** for F-Droid (de-Googled capture + OCR path) — optional,
   low priority; see §8.
5. Release hardening per Section 7.

---

## 10. Conventions for agents

- **Always run `./gradlew assembleDebug` after editing source** to confirm it
  compiles before declaring done.
- **Editor paste hazard:** auto-close-brackets has repeatedly corrupted pasted
  Kotlin in this project (doubled `}`). When writing/replacing a whole file,
  prefer delivering it as a file (zip/base64) written from the terminal, and
  then **verify brace balance** (`grep -o '{' file | wc -l` vs `'}'`) and
  compile. Don't trust a large editor paste blindly.
- **Library currency:** ML Kit doc scanner and pdfbox-android APIs may differ
  from model training data — consult current docs/source rather than assuming.
  (The CCITT decision above came from reading pdfbox-android source directly.)
- **Do not add** analytics, crash-reporting, or new broad storage/network
  permissions without flagging it against Sections 7 and 8. Google/ML Kit deps
  are acceptable; third-party analytics are not.
- Keep the app single-purpose and the dependency list small.

---

## 11. Gotchas log (things already hit — don't repeat)

- **JDK:** build with JDK **17** (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`).
  Ubuntu 24.04's default `java` is a **21 JRE with no `javac`** → Gradle fails
  with "does not provide the required capabilities: [JAVA_COMPILER]".
- **AndroidX:** `gradle.properties` must contain `android.useAndroidX=true`
  (not auto-created by the hand-built scaffold).
- **Gradle wrapper** was generated manually (the scaffold shipped without it).
- **WSL2 + adb:** default NAT networking can't reach the phone; set
  `networkingMode=mirrored` in `%UserProfile%\.wslconfig`, then `wsl --shutdown`.
- **adb wireless:** the *pair* port and the *connect* port differ; `adb pair`
  may print a scary "protocol fault … Success" yet still pair. `ANDROID_SERIAL`
  goes stale when the phone re-pairs (new port) — update or unset it.
- **CCITTFactory** needs ALPHA_8 bitmaps; we use LosslessFactory instead (see §6).
- **OOM risk** if you process full-resolution captures — downsample first.

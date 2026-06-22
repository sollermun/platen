# Platen

A no-frills Android document scanner that outputs **compact black-and-white (or
grayscale) PDFs** — the encoding that makes scans tiny (full pages typically
well under 100 KB instead of the several-hundred-KB JPEG that general scanner
apps embed).

Pipeline:

1. **ML Kit Document Scanner** handles capture, edge detection, deskew, and
   cleanup, and returns cropped page images. (No camera permission needed — it
   runs inside Google Play services.)
2. **Binarizer.kt** converts each page to pure black/white via Bradley-Roth
   adaptive thresholding (robust to receipt shadows / thermal fade).
3. **PdfExporter.kt** wraps each bitonal page as a CCITT G4 image in a PDF via
   pdfbox-android and writes it to a folder you pick.

Point **FolderSync** at that same output folder to push the PDFs to Nextcloud.

---

## Heads-up: this is a starting scaffold

It was written without an Android SDK in the loop, so treat it as a working
skeleton to compile and shake out in your IDE — expect a couple of minor
fixups (dependency version bumps, an import) on first build. The three things
that actually carry the design — the ML Kit call, the adaptive binarizer, and
the CCITT export — are the parts to keep.

## Build & run (WSL2 + physical phone)

You don't need Android Studio or an emulator. ML Kit's scanner needs Google
Play services, so a physical phone is the right test target anyway.

1. Install the Android command-line tools + SDK in WSL2, and set
   `ANDROID_HOME` / `local.properties` (`sdk.dir=/path/to/Android/Sdk`).
   Install: `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
2. On the phone: enable Developer Options → **Wireless debugging**.
   Then from WSL2: `adb pair <ip>:<pair-port>` and `adb connect <ip>:<port>`.
   (Wireless avoids USB passthrough into WSL2.)
3. Build & install:
   ```
   ./gradlew installDebug
   ```
4. Launch the app, tap **Choose output folder**, pick a folder, then
   **Scan receipt**. Point FolderSync at that folder.

## Tuning

- **File size / sharpness**: `PdfExporter.export(..., dpi = 150f)`. Drop to
  120 or 100 for smaller files; raise for sharper text.
- **Faint vs. clean**: `Binarizer.toBitonal(src, t = 0.15)`. Lower `t` keeps
  fainter text (more black); raise it for a whiter, cleaner page. 0.10–0.18 is
  the useful range for receipts.

## Possible next steps

- Add an OCR text layer (ML Kit text recognition or tesseract4android) so the
  receipts are searchable.
- Let the user set default DPI / threshold in a small settings screen.
- Batch multiple receipts into one PDF vs. one-file-per-receipt toggle.

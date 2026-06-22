# BACKLOG.md — Platen

Prioritized work to take this from "works on one phone" to a publishable app.
Read **AGENTS.md** first for architecture, build/run, and the security/privacy
and release constraints — every task below inherits them.

## Global definition of done
- `./gradlew assembleDebug` compiles; change verified on a physical device.
- **No new runtime permissions** beyond what the merged manifest already has
  (INTERNET/ACCESS_NETWORK_STATE come from ML Kit — already expected). SAF-only
  file access preserved.
- **No analytics/telemetry/crash-reporting SDKs** we add.
- User-facing strings go in `strings.xml` (see P0-6), not hardcoded.
- New logic that can be unit-tested, is.

Priority tiers: **P0** = blocks any release · **P1** = needed for a credible
"complete" launch · **P2** = enhancements · **P3** = exploratory.

---

## P0 — Release blockers

### P0-1 — App icon
Replace the default launcher icon with a proper **adaptive icon** (foreground +
background layers) plus a **monochrome** layer for Android themed icons, at all
densities, and a Play Store 512×512.
**Done when:** custom icon shows on launcher, recents, settings, and renders
correctly as a themed icon.

### P0-2 — App identity (name + applicationId)
~~Pick a distinct app name~~ **Done**: app renamed to **Platen**,
`applicationId` = `com.sparklaw.platen`. Changing it again later is disruptive,
so this is now locked before first publish.
**Done when:** name + applicationId finalized; manifest `label` updated.

### P0-3 — License + third-party notices
Add a `LICENSE` file. F-Droid requires FOSS; GPLv3 is the conventional F-Droid
choice (Apache-2.0/MIT are more permissive). Add SPDX headers if desired.
Compile third-party notices (pdfbox-android = Apache-2.0; AndroidX = Apache-2.0;
ML Kit has its own terms) for the About screen (P1-5).
**Done when:** `LICENSE` present; notices list assembled.

### P0-4 — Privacy policy
Write and host a privacy policy (Play requires a URL even when nothing is
collected). Content is simple: no data collected, no data transmitted, files
stay in the user-chosen folder; shares are user-initiated.
**Done when:** policy hosted at a stable URL, ready for the store listing.

### P0-5 — Release build configuration
Enable `isMinifyEnabled = true` + resource shrinking for `release`; add keep
rules if R8 breaks pdfbox-android/ML Kit. Configure real signing (**Play App
Signing**; upload keystore + passwords injected via env/Gradle properties,
**never committed** — add keystore globs to `.gitignore`). Set a
`versionCode`/`versionName` scheme; confirm release is not debuggable.
**Done when:** a signed release AAB builds and installs; no secrets in VCS.

### P0-6 — Externalize strings
Move all hardcoded UI strings to `res/values/strings.xml` (prereq for
localization and F-Droid/Weblate translation).
**Done when:** no user-facing string literals remain in Kotlin.

### P0-7 — Graceful degradation / error handling
Handle, without crashing: Google Play services or the ML Kit scanner module
unavailable; SAF folder permission revoked between runs; low storage; very large
multi-page sessions; share target that can't open the file. Show a clear message
in each case.
**Done when:** each failure path shows a message, not a crash (verify via forced
conditions).

### P0-8 — `foss` product flavor (F-Droid only) — **deferred / low priority**
The app is now intentionally ML Kit / Google-based (Document Scanner + Text
Recognition). F-Droid is not currently targeted. A `foss` flavor would require
replacing both the ML Kit scanner (CameraX + manual crop) and the ML Kit OCR
(e.g. open-source Tesseract) with no GMS on the classpath. Large effort; pursue
only if F-Droid distribution becomes a goal. See AGENTS.md §8.
**Done when:** `foss` flavor builds and scans with zero GMS / ML Kit on its
classpath.

---

## P1 — Needed for a credible "complete" launch

### P1-1 — Page-size normalization
In `PdfExporter`, support placing each page image onto a fixed-size page,
scaled-to-fit and centered with a small margin (~18 pt). Sizes (PDF points):
Letter 612×792, Legal 612×1008, A4 595.28×841.89. Choose portrait/landscape from
image aspect. Add a **Page size** selector — *Fit to content* (default, best for
receipts) / Letter / A4 / Legal — persisted in prefs.
**Done when:** a multi-page scan in "Letter" yields uniformly-sized, printable
pages; "Fit to content" preserves today's behavior.

### P1-2 — Auto page-size detection (aspect ratio)
Add an **Auto** option to the P1-1 selector: measure the rectified crop's
aspect ratio and snap to the nearest standard (Letter ≈ 0.773, A4 ≈ 0.707,
Legal ≈ 0.607); fall back to *Fit to content* when far from any standard (e.g. a
long receipt). No extra sensors/deps — uses ML Kit's existing deskewed crop.
**Done when:** scanning a real Letter sheet auto-selects Letter; a receipt falls
back to fit-to-content.

### P1-3 — Settings screen
Move mode (B&W/grayscale) default, page size, and power-user knobs (threshold
`t`, DPI) into a small settings screen instead of cramming the main screen.
**Done when:** settings persist and the main screen stays uncluttered.

### P1-4 — Accessibility
Add content descriptions to buttons and radio options; verify TalkBack flow and
large-font/contrast support.
**Done when:** the screen is fully operable via TalkBack.

### P1-5 — About screen
Show app version, license, source-repo link, and third-party notices (from
P0-3).
**Done when:** reachable from the main UI and accurate.

### P1-6 — Store listing assets (Play)
Add the fastlane metadata structure (`fastlane/metadata/android/...`: title,
short/full description, changelogs, screenshots) for the Play listing.
F-Droid metadata depends on the `foss` flavor (P0-8, deferred).
**Done when:** Play listing metadata is complete and accurate.

### P1-7 — Tests
Unit-test `Binarizer` (`toBitonal`/`toGrayscale`) against known input bitmaps; a
basic instrumented smoke test that the app launches and the folder/scan controls
exist.
**Done when:** tests run green in `./gradlew test`.

### P1-8 — Store listing assets
Short + full descriptions, phone screenshots, Play feature graphic (1024×500),
category, content-rating questionnaire, and the **Data Safety** form (no data
collected/shared). For a new personal Play account, plan for the closed-testing
gate **or** register an organization account to be exempt (see AGENTS.md §8).
**Done when:** a complete Play listing draft + F-Droid descriptions exist.

---

## P2 — Enhancements

### P2-1 — OCR text layer (searchable PDFs) ✓ DONE
~~Add an optional on-device OCR pass~~ **Implemented**: `Ocr.kt` uses
**ML Kit Text Recognition** (`com.google.mlkit:text-recognition 16.0.1`,
bundled Latin model) to recognize words with pixel bounding boxes. `PdfExporter`
appends an invisible text content stream (`RenderingMode.NEITHER`) per page when
OCR results are supplied. Toggle persisted in prefs, default on. On-device only;
no model download required.
Stretch (date/total auto-naming): still open.

### P2-2 — PDF assembly options
Append a new scan to an existing PDF; choose combined-document vs
one-file-per-page for multi-page sessions.
**Done when:** both modes produce correct PDFs.

### P2-3 — Threshold/contrast control
Expose `Binarizer.t` (and optionally window size) as a slider for faded/thermal
receipts, with a live preview if feasible.
**Done when:** adjusting it visibly changes bitonal output and persists.

### P2-4 — File naming
Rename-before-save and/or filename templates (prefix, date, counter).
**Done when:** user can control output filenames.

### P2-5 — Auto-organization
Optionally write into year/month subfolders under the chosen SAF tree.
**Done when:** files land in the configured subfolder structure.

### P2-6 — Theming
Dark mode and Material You dynamic color.
**Done when:** UI follows system theme correctly.

### P2-7 — Receipt-width preset
A preset that fixes page width (e.g. captured width / thermal width) with
floating height, for tidy uniform receipt columns without full-page waste.
**Done when:** receipts export at a consistent width.

---

## P3 — Exploratory

### P3-1 — Physical-size estimation spike
Research-only: can real paper dimensions be estimated from Camera2 focus
distance + intrinsics, or ARCore Depth, accurately enough to be useful?
**Note:** ARCore is a proprietary Google dependency and would break the `foss`
flavor; the Pixel 7 Pro has no ToF/LiDAR; focus-distance readings are noisy.
P1-2 (aspect-ratio inference) is the cheaper win — only pursue this if P1-2
proves insufficient.
**Done when:** a written findings note + go/no-go recommendation.

# Platen

A minimal, privacy-focused document and receipt scanner for Android.

Platen captures documents with your phone's camera, deskews and cleans them,
and saves compact black-and-white or grayscale PDFs (optionally with an
on-device searchable-text layer) to a folder **you** choose. Everything happens
on the device — no account, no servers, no analytics, no data collection.

## Install

**Google Play** — coming soon (`com.sparklaw.platen`)

**APK (sideload):** Download `app-release.apk` from
[Releases](https://github.com/sollermun/platen/releases). Enable *Install
unknown apps* for your file manager, then open the APK.

> ⚠️ The Play Store version and the GitHub APK are signed with different keys
> (Google re-signs apps distributed via Play). You cannot upgrade from one
> install path to the other without uninstalling first.

**Requirements:** Android 7.0 (API 24) or higher.

## Features

- Document/receipt capture with automatic edge detection, deskew, and perspective
  correction (Google ML Kit Document Scanner)
- Black-and-white (bitonal) or grayscale output
- Optional searchable PDFs via on-device OCR (Google ML Kit Text Recognition)
- Page-size handling: fit-to-content, Letter, or Legal, with optional
  aspect-ratio auto-detection and a receipt guardrail
- Standard / High quality (resolution) setting
- Multi-select share and delete for saved scans
- Files are saved to a folder you choose via the Storage Access Framework — a
  normal location on your device that you can back up or sync however you like

## Privacy

Platen does not collect, transmit, or sell any data. Scans are processed on the
device and written only to the folder you select. The `INTERNET` permission is
present transitively via Google ML Kit; it is not used by this app to send your
documents or any personal data anywhere. See the [privacy policy](https://sparklawfirm.com/platen-privacy-policy.html) for details.

## Build

- Kotlin + Jetpack Compose (Material 3)
- minSdk 24, JDK 17
- `./gradlew assembleDebug` (set `JAVA_HOME` to a JDK 17)

## Contributions and support

This project is provided **as-is**, with no warranty and no support. It is
published for transparency and reference. **Issues and pull requests are not
being accepted** and may be left unanswered or closed. You are welcome to fork
the project and adapt it under the terms of the license.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
Third-party attributions are in [NOTICE](NOTICE).

Note: this app depends on Google ML Kit and Google Play services, which are
proprietary to Google and governed by Google's own terms — they are not covered
by this project's Apache-2.0 license.

Copyright 2026 Law Office of Samuel H. Park, APC

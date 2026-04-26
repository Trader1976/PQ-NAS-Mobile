# DNA-Nexus

DNA-Nexus is a private cloud and file access app for your own DNA-Nexus server.

This Android app lets you browse files, upload content, manage shares, preview images, edit text files, and monitor your storage usage from your phone.

## Screenshots

<p align="center">
  <img src="docs/images/nexusapp1.jpg" alt="DNA-Nexus app screenshot 1" width="300">
  <img src="docs/images/nexusapp2.jpg" alt="DNA-Nexus app screenshot 2" width="300">
</p>

## Features

- Browse files and folders
- Upload files from your phone
- Download files
- Create public share links
- Choose share expiry:
  - 1 hour
  - 1 day
  - 7 days
  - never
- Manage shares in the app
- Add and remove favorites
- Preview images
- Edit text files directly on the server
- View storage quota and usage
- Pair with a DNA-Nexus server using QR-based trusted-device login

## Project status

DNA-Nexus is under active development.

Current focus areas include:

- improving mobile UX
- expanding file management
- improving share handling
- polishing server and app integration
- hardening Android app security

## Tech

- Kotlin
- Jetpack Compose
- Retrofit
- OkHttp
- Moshi
- Android Studio

## Development

Open the project in Android Studio, or build from the command line.

### Debug build

For normal development, use the debug build:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Or both at once:

```bash
./gradlew :app:assembleDebug :app:installDebug
```

The debug build is faster for development and keeps debug-only logging enabled.

### Release-test build

To test the real release behavior locally, use:

```bash
./scripts/install-release-test.sh
```

This script builds the release APK, runs R8/minification, signs the APK with your local Android debug key, verifies the signature, and installs it with `adb`.

Use this when testing security-sensitive release behavior such as:

- `BuildConfig.DEBUG = false`
- release-only minification / obfuscation
- release logging disabled
- release network security settings
- QR pairing and file APIs after R8 shrinking

If Android reports a signature mismatch because another build is already installed, run:

```bash
./scripts/install-release-test.sh --uninstall
```

This uninstalls the existing app first, so app data is wiped and the device must be paired again.

The release-test APK is for local testing only. Do not publish an APK signed with the Android debug key.

## Useful checks

Check that no UI-thread `runBlocking` calls remain:

```bash
grep -rn "runBlocking" app/src/main/java --include="*.kt"
```

Check that release logging is guarded:

```bash
grep -rn "Log\.\|android.util.Log\|HttpLoggingInterceptor" app/src/main/java --include="*.kt"
```

Check that backup and network security rules are present:

```bash
grep -r "datastore\|pqnas_auth" app/src/main/res/xml/
grep -r "networkSecurityConfig\|cleartextTrafficPermitted" app/src/main/
```

Build both debug and release:

```bash
./gradlew clean assembleDebug assembleRelease
```
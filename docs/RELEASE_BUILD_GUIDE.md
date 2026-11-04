# Release Build & Packaging Guide

This guide details the procedure for compiling, verifying, and packaging **AETHER AI v1.0.0** APKs and source releases.

---

## 1. Prerequisites
- **JDK**: Java Development Kit 17 (Eclipse Temurin recommended)
- **Gradle**: 8.x+ (run using `gradle`, not `./gradlew`)
- **Android SDK**: Build Tools 35.0.0, Platform SDK 35 (Android 15)
- **NDK**: 26.1.10909125 (for C++ ARM NEON native libraries)

---

## 2. Compiling the Application

### Running Local JVM & Robolectric Test Suite
```bash
gradle :app:testDebugUnitTest
```
Ensure all tests in `AetherWorkstationTestSuite.kt` pass with 0 failures before building the APK.

### Assembling Release APK
```bash
gradle :app:assembleRelease
```
The resulting APK will be generated at:
`app/build/outputs/apk/release/app-release.apk` (or unsigned release APK).

### Assembling Debug APK (For immediate sideload testing)
```bash
gradle :app:assembleDebug
```
The debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Sideloading to Samsung Galaxy S22 Ultra
```bash
# Enable USB Debugging in Settings > Developer Options
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. GitHub Actions CI/CD Pipeline
Every push to `release/v1.0.0` or tag matching `v*` triggers `.github/workflows/android-release.yml`:
1. Executes unit tests with Robolectric.
2. Builds release APK.
3. Calculates SHA-256 checksums and stores them in `release_artifacts/`.
4. Uploads release artifacts and creates GitHub Release on tags.

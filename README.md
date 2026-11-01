# UK Prepper - offline Android app

Native Kotlin + Jetpack Compose. Air-gapped: the INTERNET permission is stripped at build time.
Target: Pixel 8 (minSdk 29, compileSdk/targetSdk 35 - runs fine on Android 17).

## Build on your laptop

Toolchain this was written for: Gradle 9.3.1, JDK 17, Android SDK platform 35.
AGP 9.0.1 compiles Kotlin itself (built-in Kotlin), so there is no kotlin-android plugin.

```bash
cd ukprepper

# 1. Point Gradle at your SDK (skip if ANDROID_HOME is already set)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties     # adjust the path

# 2. One-off: create the Gradle wrapper so builds are reproducible
gradle wrapper --gradle-version 9.3.1

# 3. Build (first run downloads AGP, build-tools 36 and libraries - needs internet ONCE)
./gradlew assembleDebug

# 4. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Check that the app really has no internet permission
```bash
$ANDROID_HOME/build-tools/36.0.0/aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
```
You should see CAMERA, POST_NOTIFICATIONS and WorkManager's permissions (wake lock, boot completed,
foreground service, network state). INTERNET should NOT be listed. ACCESS_NETWORK_STATE only lets
WorkManager read connectivity status; it cannot send any data.

## Files to and from the phone
PDFs and backups are written to `Download/UKPrepper/`:
```bash
adb pull /sdcard/Download/UKPrepper/ ./prepper-files/
adb push my-backup.json /sdcard/Download/        # then More > Print & backup > Restore
```

## What's where
| Path | Contents |
|---|---|
| `model/Models.kt` | Item, Product, Plan (all planning figures and defaults) |
| `logic/` | Pure Kotlin, no Android: readiness, meal planner, power budget, checklists |
| `data/Repo.kt` | SQLite storage (no Room/KSP, to keep the build simple) |
| `work/ExpiryCheck.kt` | The only background job: Saturday ~08:00, weekly |
| `export/Exporter.kt` | Double-sided A4 PDF, JSON backup/restore |
| `ui/` | Compose screens |
| `assets/reference/*.md` | Reference library text - edit freely, no code changes needed |

## Battery design
- No background services, no location, no network.
- One WorkManager job per week.
- The camera runs only while the scan or photo screen is open.
- Dark theme throughout (OLED).
- Database work is tiny and synchronous.

## If the build fails
Paste the first error block back to Claude. The likeliest early issues are dependency or plugin
version mismatches, which are quick to fix.

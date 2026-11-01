# Upgrading to 0.3.0 without losing your stock or photos

What changes in 0.3.0:
- New toilet reference pages, with five diagrams (pinch-to-zoom; also printed in the PDF):
  - Garden twin pit, built in the existing hole on the raised area by the wall.
  - Urine separation and ammonia control, with a self-emptying tank and soakaway trench.
  - Garage emergency toilet.
- Two new essentials checks: a urine container, and vinegar / citric acid.
- The stay-home checklist now includes the garden and garage toilet set-up.

The database format has NOT changed, so your data carries over unchanged.

Your data survives an update as long as you:
1. build on the SAME laptop as before (it signs with the same debug key), and
2. install with `adb install -r` - NEVER uninstall first.

## Step 1 - Back up (5 minutes, do not skip)
On the phone: More → Print & backup → **Create backup**. Then on the laptop:
```bash
mkdir -p ~/prepper-backups/$(date +%F) && cd ~/prepper-backups/$(date +%F)
adb pull /sdcard/Download/UKPrepper/ .
# full copy of the database and photos (works because it is a debug build)
adb exec-out run-as uk.prepper.app cat databases/prepper.db > prepper.db
adb exec-out run-as uk.prepper.app tar cf - files/photos > photos.tar
ls -la    # check prepper.db and photos.tar are not 0 bytes
```

## Step 2 - Unpack the new source next to the old one
```bash
cd ~                                   # wherever you keep projects
unzip ukprepper-0.3.0.zip -d ukprepper-0.3.0
cd ukprepper-0.3.0/ukprepper
cp ../../ukprepper/local.properties .  # adjust the path to your old project folder
gradle wrapper --gradle-version 9.3.1  # or copy gradlew + gradle/ from the old folder
```

## Step 3 - Build
```bash
./gradlew assembleDebug
```

## Step 4 - Install over the top (keeps your data)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
- `Success` → done.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a signature error → **STOP. Do not uninstall.** It means this build was signed with a different key from the one on the phone - for example a different laptop or user account. Build on the original machine, or send the error to Claude.

## Step 5 - Check
- Stock shows the same number of lines (94).
- Photos still appear on the items.
- More → Reference library shows "Garden compost toilet (twin pit in the raised area)", "Urine: keep it separate…" and "Garage emergency toilet", each with diagrams. Tap a diagram to zoom.
- The version shows in Android Settings → Apps → UK Prepper (0.3.0).

## If anything went wrong - restore
Option A (simple): More → Print & backup → **Restore…** and pick the backup JSON. This restores the stock, barcode memory, routine and settings, but not photos.

Option B (complete, debug builds only):
```bash
adb shell am force-stop uk.prepper.app
adb push prepper.db /data/local/tmp/prepper.db
adb push photos.tar /data/local/tmp/photos.tar
adb shell chmod 644 /data/local/tmp/prepper.db /data/local/tmp/photos.tar
adb shell "run-as uk.prepper.app sh -c 'cat /data/local/tmp/prepper.db > databases/prepper.db && rm -f databases/prepper.db-journal databases/prepper.db-wal databases/prepper.db-shm'"
adb shell "run-as uk.prepper.app sh -c 'tar xf /data/local/tmp/photos.tar'"
adb shell rm /data/local/tmp/prepper.db /data/local/tmp/photos.tar
```
Then open the app.

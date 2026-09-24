# Still

A simple Android weight journal inspired by DroidWeight, built with Kotlin and
Jetpack Compose.

## Features

- Daily weight entries with optional body fat, waist measurements, and notes.
- Kilograms or pounds, with editing and backdating.
- Weight charts, seven-day averages, and period statistics.
- Optional height-based BMI reference.
- System, light, and dark themes.
- Full backups, CSV import/export, and DroidWeight import.

Requires **Android 8.0 or later**. Measurements are stored locally in SQLite.
There are no accounts, ads, analytics, or internet permission.

## Using the app

Record a weight from **Today**, review or edit entries in **History**, and explore
charts in **Trends**. Each date can have one entry. Body fat, waist measurements,
and notes appear in entry details; charts show weight.

In **Settings**, choose your weight unit or enter your height for the optional
BMI reference in **Trends**.

**Settings > Theme** defaults to **System**, which follows the phone's appearance.
Choose **Light** or **Dark** to keep a fixed theme.

## Backups and phone transfer

### Full backup

Use a `.still` backup to save all measurements and settings, including your
weight unit, height, and theme.

1. Open **Settings > Back up everything** and save the file.
2. Copy it to the destination phone and install the same or a newer app version.
3. Open **Settings > Restore backup**, select the file, and review its contents.
4. Select **Replace journal** to confirm.

**Restoring replaces all existing entries and settings.** Back up the destination
journal first if you want to keep it. To add measurements without replacing
existing entries, use CSV import instead.

**Backup files are not encrypted.** Store them somewhere private. Full backups
are limited to 16 MiB, both compressed and expanded.

Full backups use format version 3. Earlier backup formats remain importable;
measurements, units, height, and appearance are preserved.

### Android setup transfer

On supported Android 9+ devices, phone-to-phone setup can transfer the journal
and settings directly. It does not require an exported `.still` file. Availability
depends on the devices, setup tool, and app installation/signing eligibility.
Android 8/8.1 requires a manual backup.

Keep a manual backup as a fallback. After transfer, open the app, verify your
history before wiping the old phone.

Cloud backup and ongoing multi-device synchronization are not supported.

## CSV import and export

Open **Settings > Data tools** to import or export measurements. CSV contains
measurements and notes, not app settings. Imports add missing dates and skip
dates already in the journal.

Exports use metric units regardless of the display setting:

```csv
date,weight_kg,body_fat_percent,waist_cm,note
2026-01-15,72.4,21.5,81.2,"Morning, after a walk"
```

Quoted commas, quotation marks, and multiline notes are supported. Import files
are limited to 10 MB. CSV files are unencrypted.

### Importing from DroidWeight

Choose a DroidWeight `data.csv` export through **Import measurements (CSV)**.
The supported pipe-separated header is:

```text
value|type|date|metric|id|comment
```

Measurements are grouped by date; when a date has multiple weights, the last
weight row in the file is used. Times are not retained. Height records are not
imported; enter your height in **Settings > Height**. A date with body fat
or waist measurements but no weight must be corrected before importing.

## Build and run

### Requirements

- Android SDK platform 37 and build tools.
- A JDK compatible with the bundled Gradle wrapper.
- An Android 8.0+ device or emulator.

Open the project in Android Studio, or build from PowerShell using Android
Studio's bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

The SDK path can also be configured in an untracked `local.properties` file.
The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

To install and launch it on a connected device with USB debugging enabled:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.vadimfrolov.still.debug/app.still.MainActivity
```

The release application ID is `com.vadimfrolov.still`. Debug builds use
`com.vadimfrolov.still.debug`, so they can be installed alongside a release.
Release builds require a signing configuration before distribution.

## Development

### Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Instrumented tests require an unlocked device or emulator. They use isolated
databases and in-memory UI states rather than the user's journal, and leave the
app installed after the run.

To generate store screenshots with sample data, run the instrumented suite with
`-Pandroid.testInstrumentationRunnerArguments.captureStoreListing=true`.
Images are saved in the debug app's external files directory under `store-listing`.

### Project structure

Application sources are under `app\src\main\java\app\still`.

| Component | Purpose |
| --- | --- |
| `data` | Models, SQLite storage, statistics, CSV and backup codecs |
| `TrackerViewModel` | Application state and persistence operations |
| `ui` | Compose screens, editors, charts, and themes |
| `app\src\test` | Unit tests |
| `app\src\androidTest` | Database and UI integration tests |

## License

MIT. See `LICENSE` for the full terms.

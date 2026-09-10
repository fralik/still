# Still

A small, local-first Android weight journal, inspired by DroidWeight's simplicity.
This is a new implementation, not a modification of the original app.

## What's here

- A calm, native Jetpack Compose interface with light and dark appearances.
- Daily weight check-ins, backdating, editing, and confirmed deletion.
- Optional body fat, waist measurements, and notes.
- Kilograms and pounds, with canonical kilogram storage and reversible conversions.
- Weight charts for one, three, and six months, or all history.
- Seven-day averages, period statistics, and optional goals for either gaining or losing weight.
- An optional BMI reference when height is supplied, without diagnostic labels.
- Opt-in daily notifications at a chosen local time, rescheduled after reboot.
- CSV backups through Android's document picker and migration from DroidWeight exports.
- SQLite persistence, no account, no analytics, no internet permission, no automatic cloud backup.

The app starts empty. It does not insert demonstration measurements or read another
app's private storage. Entries are deliberately limited to one check-in per calendar
day. Body fat and waist appear in entry details; the chart plots weight.

## Build and run

Requirements: an Android SDK with platform 37 and build tools, a JDK compatible
with Gradle 9.4.1 (JDK 17 or later), and Android 8.0 / API 26 or later on the device.
The project uses Android Gradle Plugin 9.2.1 and its built-in Kotlin support.

Open this folder in Android Studio, or on PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n app.still/.MainActivity
```

The Gradle wrapper is included. SDK location can also be set in an untracked
`local.properties` file. Debug APK: `app\build\outputs\apk\debug\app-debug.apk`.
Release builds require your own signing configuration before distribution.

## Backups and migration

Use **Settings > Export CSV backup** to select a destination. Backups are plain
text and contain personal measurements and notes; choose a private location.
Automatic Android backup is disabled. Export before uninstalling or changing phones.
Goal, height, and appearance preferences are not part of the measurement CSV.
Reminder preferences are not included either. Reminders are inexact, respect
Android notification permissions, and may be delayed by battery restrictions.

Still's canonical CSV uses this header and metric storage regardless of display unit:

```csv
date,weight_kg,body_fat_percent,waist_cm,note
2026-01-15,72.4,21.5,81.2,"Morning, after a walk"
```

Quoted commas, quotation marks, and multiline notes are supported.
**Settings > Import CSV** parses the entire file before requesting confirmation.
Imports are transactional and do not overwrite existing dates.

Original DroidWeight `data.csv` exports with the pipe-separated header
`value|type|date|metric|id|comment` are also supported. Measurements are grouped
by calendar date; if there are multiple weight measurements on one day, the last
weight row in the file wins. Legacy imperial values are converted using
DroidWeight's original factors (2.2 pounds/kg and 0.39 inches/cm) to recover the
stored metric measurements. Legacy times cannot be preserved in a daily journal.
Separate legacy height records are not imported as check-ins; set your height in
**Goal & height**. A date containing body fat or waist without a weight is rejected
instead of silently losing those measurements.

Malformed files, invalid numbers, future dates, and conflicting edited dates produce
visible errors. Imports are limited to 10 MB of text.

## Structure

- `data`: SQLite repository, unit conversions, validation, statistics, CSV codec.
- `TrackerViewModel`: serialized asynchronous persistence and import/export operations.
- `ui`: Compose screens, editors, theme, and an accessible time-scaled chart.
- `src/test`: pure domain and CSV regression tests.
- `src/androidTest`: isolated SQLite persistence tests and Compose interaction tests.

Run `.\gradlew.bat :app:connectedDebugAndroidTest` with an unlocked device for
the instrumented suite. Its databases are individually namespaced and removed
after each test; UI tests render in-memory sample states, not the user's journal.
The test runner is configured to leave the app installed, preserving its data.
Android Studio previews include empty, populated, dark, and large-text layouts.

No subscription or remote infrastructure is needed. Bluetooth scales and
Health Connect integration are not included in this version.

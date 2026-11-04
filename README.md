# A51 RAM Optimizer — starter project

Target: Samsung Galaxy A51 SM-A515F, Android 16 / API 36, One UI 8.5.

## Current scope
- `app`: read-only dashboard for `/proc/meminfo` and ZRAM sysfs statistics.
- `lsposed`: a deliberately inert LSPosed loading probe, scoped to the dashboard app only.
  It makes no memory, process, kernel, SystemUI, or ZRAM changes.

This is an initial compatibility/build scaffold, not an optimizer release. We will only add
behavior-changing hooks after measuring a specific problem and confirming the relevant API
on this ROM. The dashboard does not require LSPosed to function.

## Build requirements
- JDK 17 or 21
- Android SDK Platform 36 and Build Tools
- Gradle 8.11.1 or compatible with Android Gradle Plugin 8.9.1

From the project root:
    gradle wrapper --gradle-version 8.11.1
    ./gradlew :app:assembleDebug :lsposed:assembleDebug

The LSPosed module uses the legacy Xposed API 82 for the initial load probe. If dependency
resolution fails on your environment, do not install an arbitrary APK; share the Gradle error.

## Safety
- No sysctl writes.
- No `kill`, `am force-stop`, task killing, or LMK tuning.
- ZRAM is read-only.
- No changes to Firefds Kit, SystemUI, or existing LSPosed modules.


## Cloud build with GitHub Actions
After uploading this project to GitHub, open the repository's **Actions** tab.
The workflow **Build A51 RAM Optimizer** runs automatically after a push, or can
be started manually with **Run workflow**. When it completes, open the run and
download the **A51-RAM-Optimizer-APKs** artifact. The archive contains debug APKs.

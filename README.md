# iPadOS 26 Launcher

Native Android launcher inspired by iPadOS, designed for Android 13+ tablets.

## Current features
- Appears in the normal Android app drawer and can be launched after installation.
- Registers as an Android Home app and provides a first-run **Set as default launcher** prompt using the system Home role chooser.
- App drawer discovers launcher apps using both launcher-activity queries and an installed-applications fallback.
- Refresh apps action for rescanning installed apps.
- Home-screen appearance controls for icon size, label size, grid columns, and labels.
- Optional bottom swipe handle; swipe up to open Android Recents and dismiss an app card there.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. GitHub Actions builds a debug APK on pushes to `main`.

The swipe gesture requires the user to enable the launcher accessibility service in Android Settings. Android itself handles dismissing cards in Recents; the launcher does not force-stop other apps.

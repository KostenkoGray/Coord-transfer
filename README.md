# Мультик — Coordinate Transfer

Offline Android coordinate converter. Enter or paste coordinates; the input format is detected automatically and equivalent coordinates are displayed with an individual copy action.

## Interface

- Dark navy interface inspired by the companion app «Кальцій».
- App title: **COORD TRANSFER / Мультик**.
- Quick insertion buttons for the coordinate symbols `°`, `'` and `"`.
- Results omit the coordinate format already used in the input.
- Compass-and-waves launcher icon in the same navy/cyan palette.

## Supported input formats

- Decimal degrees (DD), for example: 49.8397, 24.0297
- Decimal degrees with hemisphere, for example: 49.8397 N 24.0297 E
- Degrees and decimal minutes (DDM), for example: 49° 50.382' N 24° 1.782' E
- Degrees, minutes and seconds (DMS)
- UTM / WGS 84, using a latitude band, for example: 35U 426000 5520000
- MGRS, for example: 35U LV 26000 20000
- Web Mercator (EPSG:3857), including the app's labeled X/Y output

## Output formats

- Decimal degrees (DD)
- Degrees and decimal minutes (DDM)
- Degrees, minutes and seconds (DMS)
- UTM / WGS 84
- MGRS (1 m precision)
- Web Mercator (EPSG:3857)

The output list excludes the matching input format to avoid showing duplicate information.

UTM/MGRS output is available from 80°S to 84°N. Web Mercator output is limited to approximately 85.0511° north/south. This initial version uses WGS 84 as its geodetic reference.

## Offline and privacy

All parsing and calculations run on the device. The app declares no internet or location permission and does not use online geocoding or map services.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35. The project uses Gradle 8.11.1 and Android Gradle Plugin 8.9.2.

Run unit tests and build the debug APK:

    gradle testDebugUnitTest assembleDebug

GitHub Actions runs the same checks on pushes and pull requests to main, then publishes the debug APK as a workflow artifact.

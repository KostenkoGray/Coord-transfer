# Coord Transfer

Offline Android coordinate converter. The app detects the coordinate format from pasted or typed input and displays equivalent values in several coordinate representations, each with a copy action.

## Planned formats

- Geographic coordinates: decimal degrees (DD)
- Degrees and decimal minutes (DDM)
- Degrees, minutes and seconds (DMS)
- UTM (WGS 84)
- MGRS
- Web Mercator (EPSG:3857)

All parsing and projection calculations run on-device. The app does not need network access or location permission.

## Build

Open the project in Android Studio (JDK 17, Android SDK 35) and run the `app` configuration. The GitHub Actions workflow also builds the debug APK.

## Project status

Initial implementation in progress.

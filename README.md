# Byzantine Pitch Monitor

A real-time pitch monitor for Byzantine chant and European scales on Android. Sing or play and
the app draws your pitch over the lines of the chosen ήχος (72-moria Chrysanthine system) or
European scale.

This project is written from scratch. It reproduces the screens and behaviour of the
"Byzantine Pitch Monitor 1.6.0" APK as a starting point; no code from that APK is used.

## Build

Requirements: JDK 17 and the Android SDK (platform 37). The Gradle wrapper is included.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests (scales, spelling, Base = voice, pitch detector)
./gradlew assembleRelease      # signed with the debug key until you add your own
```

The debug build installs as `app.pitchmonitor.byz.dev` ("Byzantine Pitch Monitor (dev)") so it
can sit next to the 1.6.0 app; the release build uses `app.pitchmonitor.byz`, version 1.6.0 (37).

## What is where

| Part | File |
| --- | --- |
| Byzantine modes, genera, European scales, note names, scale list | `app/src/main/java/app/pitchmonitor/byz/Music.kt` |
| Settings and their defaults | `Prefs.kt` |
| Pitch detector (YIN), microphone, WAV files, import, playback | `Audio.kt` |
| The pitch graph, tuner, beat lines, gestures | `PitchView.kt` |
| Main screen: bars, base controls, HOLD, record / play / stop | `MainActivity.kt`, `res/layout/activity_main.xml` |
| Settings screen and colour picker | `SettingsActivity.kt` |
| Recordings list | `RecordingsActivity.kt` |

## Debug launch options

MIUI phones block simulated taps over adb, so debug builds accept these extras:

```bash
adb shell am start -n app.pitchmonitor.byz.dev/app.pitchmonitor.byz.MainActivity --ez demo true --es scale "Ήχος Α'"
adb shell am start -n app.pitchmonitor.byz.dev/app.pitchmonitor.byz.MainActivity --es open settings --ei scrollTo 900
```

- `demo` feeds a synthetic voice instead of the microphone.
- `scale` picks a scale by name, `scaleIndex` by its place in the list (0 = C Major, 1 = Ήχος Α').
- `open settings|recordings` opens that screen; `scrollTo` scrolls Settings down (dp).

## Credits

The title uses GFS Didot by the Greek Font Society, under the SIL Open Font License
(`app/src/main/assets/fonts/GFSDidot-OFL.txt`). The lyre launcher icon is the owner's own.

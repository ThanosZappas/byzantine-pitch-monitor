# Notes

How this project relates to the 1.6.0 APK, and what is not (yet) the same.

## Approach

The 1.6.0 APK was the third-party app *Vocal Pitch Monitor* (Tadao Yamaoka) with a Byzantine
add-on patched in. Its code was **not** decompiled or copied. This project is a from-scratch
implementation that aims at the same screens and behaviour, taken from the owner's screenshots
and from using the app. Only the owner's own parts were carried over as data:

- the Byzantine tables of the add-on (genera, bases, dominants per ήχος, Πλ.Δ' εκ του Γα),
- the Ni offset in moria, ±1 / ±6 / Reset / Base = voice behaviour, and the base box text,
- the scale order and the European-scale filter,
- the lyre launcher icon (owner's icon pack) and the GFS Didot title font (OFL).

There are therefore no "renamings": every class name is new.

## Deliberately left out

- **Ads and Firebase analytics** of the original app: they belong to its author's business,
  not to the app's function, and this project has no ad account.
- **The original drawables** (record / play / stop, menu icons): redrawn as simple vectors.

## Choices where the screenshots don't show the behaviour

Each of these is my own implementation; tell me if the original behaved differently.

| Area | What this project does |
| --- | --- |
| Pitch detection | YIN, 2048-sample window, 512-sample hop at 44.1 kHz (≈ 86 readings/s), microphone source `MIC`. |
| Threshold of volume | 0.00–50.00; the value is the RMS level in thousandths of full scale. Default 1.00. |
| Zoom | Horizontal 1.0–4.0 (40 dp per second at 1.0), vertical 0.5–2.0. Pinch on the graph changes both and is saved. |
| Auto scroll | The view glides towards the latest pitch; speed 1–10. Dragging up/down pauses it for 3 s; double-tap recentres. |
| Tuner | Nearest scale note in large letters, a ±110-cent ruler with 10-cent ticks, a marker for the sung pitch, and the names of nearby scale notes. Smoothing 1–5 averages the last 3–11 readings. |
| Display frequency in Hz | Shows "nnn Hz" at four fifths of the width, as the Byzantine add-on did. |
| Transpose | B♭ +2, E♭ +9, F +7 semitones (written above sounding) plus the slider (−12…+12). Applies to European scales only; the Byzantine scales have their own Ni offset. |
| Calibration | A4 = 415–465 Hz; moves every line (Byzantine and European). |
| Octave number | "440Hz = A3" lowers every octave number by one. |
| Tempo | Beat lines every beat (first beat of a 4/4 or 3/4 bar thicker); "Blink screen" flashes the metronome colour on each beat; BPM 20–260. There is no metronome sound (none was visible). The "Tempo" button-display checkbox is kept but, as in 1.6.0, nothing on the main screen uses it since BPM was removed there. |
| Colours | Scale colours: tonic, 2nd–7th degree, semitone lines. Chromatic colours: one per pitch class, used by the Chromatic scale and by the Byzantine lines (C = base, G = dominants, D = other degrees, as in the add-on). The colour picker (hue / saturation / brightness + presets) is my own; the original picker wasn't in the screenshots. |
| Defaults | Pitch yellow, beats dark grey, metronome navy, tonic grey, other degrees dark grey, semitone darker; Settings colour gold. "DEFAULT" resets everything except the Ni offset. |
| Recordings | Record writes a 44.1 kHz mono WAV into the app's storage; Stop ends recording or playback, or leaves a loaded recording; Play plays the last recording while the graph follows it. The Recordings list opens a file on tap; long-press offers Load, Send, Rename, Delete. Import accepts any audio file Android can decode and keeps a WAV copy. |
| HOLD | Freezes the graph; drag sideways to look back. With a loaded recording, HOLD returns to the live voice. |
| Base box | "ΝΗ +nμ / f Hz" uses 261.6 Hz × 2^(n/72), without calibration, exactly like 1.6.0. |
| Byzantine line widths and label colours | Chosen to match the screenshots (base line thicker and brighter). |

## Verification (Xiaomi M2007J3SG, MIUI, 2026-11-23)

Compared on the phone with the owner's 1.6.0 screenshots:

- Main screen in Ήχος Α': top bar, axis labels, base line, moria steps, tuner ruler, HOLD,
  bottom bar — match. Πλ.Δ' εκ του Γα shows Γα 12 Δι 10 Κε 8 Ζω 12 Νη 12 Πα 10 Βου 8 Γα.
- Main screen in C Major: note labels, semitone lines, brighter C lines, full-width scale button — match.
- Settings, all sections from Recordings to the colour lists: same options, order, values and
  gold accent — match.
- The microphone runs (MIC source, 44.1 kHz mono).

## Known differences / not verified

- Values in the screenshots (orange pitch colour, vertical zoom 1.53, hidden record buttons,
  BPM 60…) were the owner's settings; a fresh install starts from the defaults above.
- Not tried on the phone, because MIUI blocks simulated taps: tapping through the scale picker,
  Base = voice with a real voice, recording, playback, Send, Import, rename/delete, the colour
  picker. They are built and compile, but need a run by hand.
- The original's exact scroll speed, zoom ranges and threshold scale are estimated from slider
  positions in the screenshots.

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

## Additions after the 1.6.0 baseline (owner's requests, 2026-11-23)

- **Song import** analyses at ~11 kHz (about 16× faster), shows a progress dialog with Cancel,
  reports errors in a dialog, and a "Back to live" button appears whenever a recording is shown.
- **Recordings list**: long-press to select several, then Send or Delete together (Rename for one).
- **Themes**: Follow the phone, Dark (default, the 1.6.0 look), Black (AMOLED), Light, Parchment.
  Each theme has its own default line/pitch colours for contrast; colours the user picked are kept.
- **App colour** (was "Settings colour") now colours the whole app: title, gear, bars, chips,
  hairlines, Settings controls. On light themes it is darkened for text contrast.
- **Colour picker**: saturation/brightness square, hue slider, old/new preview, hex code, recent
  colours, presets, and "Default" to go back to the theme's colour.
- **Base box** (ΝΗ +nμ / Hz): tap it to go back to the natural base.
- **Settings order**: threshold, zoom, tuner, auto scroll, tempo, buttons, note names, European
  scales, recordings, lines and tuning, appearance, colours, language. The Scale dropdown was removed
  (the scale is chosen on the main screen). A quiet footer sends feedback by email (email app, or
  Gmail in the browser if there is none) and shows the version and the developer's name.
- **Byzantine tempo presets** (αγωγή), next to the free BPM slider. **Approximate values** — I found no
  published standard, so these are common teaching values; please correct them if you use others:
  very slow / Papadic 56, slow / Sticheraric 72, moderate / short Sticheraric 92,
  fast / Heirmologic 112, very fast / short Heirmologic 132.
- **Language**: English (default) or Greek, independent of the phone's language. Scale names
  (C Major…) and chromatic note names stay in their international form.

## Navigation and theming redesign (2026-11-23)

**Colour roles.** All colours come from `Palette` (Theme.kt): Material 3-style roles (surface,
surfaceContainer, onSurface, onSurfaceVariant, primary, onPrimary, primaryContainer,
secondaryContainer, outline, outlineVariant) plus the graph and bar roles. They are built from the
theme (Dark, Black, Light, Parchment, Follow the phone) and the app colour, and every text role is
corrected to at least 4.5:1 and every control/graphic role to at least 3:1 (WCAG 2.1 AA).
`ContrastTest` checks all 4 themes × 7 app colours × ~30 pairs. Colours the user picks for the pitch
line and beats keep their hue but are adjusted until visible on the current graph background.
No colour literals remain in layouts, drawables or screen code (swatch/preset lists are data).

**Navigation.** Three top-level destinations: Settings, Appearance, Recordings (new icons).
- Phones (< 600 dp wide): the menu button opens a side panel from the right with the three
  destinations and a pinned footer (name, version, email).
- Wide screens (≥ 600 dp, e.g. landscape or tablets): a permanent navigation rail on the right of
  the main screen instead of the menu button.
- Inside, the destinations share one screen with a navigation rail on the right (active indicator,
  labels), animated page changes, and a footer pinned at the bottom. Content is capped at 720 dp
  wide and centred on tablets.
- Back stack: destinations are top-level, so switching does not stack; Back or the arrow returns to
  the monitor; Back first clears a recordings selection. The open destination survives rotation.
- Transitions: slide + fade in from the rail's side, fade + rise between destinations.

**Settings redesign.** Grouped cards with section headers; Material switches, sliders, segmented
buttons and choice chips, each with a one-line description; 4 dp spacing grid.

Not yet verified on a device: landscape/tablet rail (only a phone in portrait was available),
predictive back animation, the email button.

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

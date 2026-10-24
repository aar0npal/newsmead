# NewsMead Gaze-Driven Reading Prototype

NewsMead is an Android news-reading application repurposed as a thesis research prototype for gaze-driven adaptive reading. The current study build runs offline, bypasses the production login flow, estimates gaze from the phone front camera, maps gaze to article text lines, and derives a Reading State Index (RSI) from line-level reading behavior.

This README is written as a manual and study guide for understanding the implementation. It explains what was built, why each decision was made, how calibration works, how MediaPipe and the bundled model are used, how the logs should be interpreted, and how RSI is deduced from the logs.

Supporting documentation lives in [docs/](docs/README.md). Start there for the thesis memory, gaze implementation guide, RSI guide, adaptive visual-scaffolding guide, session setup, progress notes, and implementation specs.

## Current Status

The current implementation is a working prototype, not a finalized clinical or production-grade eye tracker.

Implemented:

- Offline study mode using bundled article data.
- Firebase login bypass for thesis sessions.
- Local phone-side gaze tracking using CameraX and MediaPipe FaceLandmarker.
- 16-point calibration saved to app-private CSV storage.
- Polynomial gaze mapping from raw iris/eye features to phone screen pixels.
- Median and One Euro smoothing.
- Scroll-aware article line mapping using native Android `TextView` layout data.
- Debug gaze dot overlay and FPS display.
- Stage 3 gaze accuracy test.
- Stage 5 RSI events and composite RSI score.
- Participant-relative rolling stability estimation for reversible adaptation.
- Four manuscript-aligned scaffold levels: word, line, focus window, and re-entry.
- Conservative scaffold persistence, withdrawal, confidence gating, fading, forced validation modes, and transition/recovery logs.
- Logcat output for calibration, raw gaze diagnostics, article line AOI, accuracy testing, and RSI.

Important limitation:

- The system is strongest for approximate line-level reading analysis. It should not be interpreted as word-level eye tracking, exact fixation science, comprehension diagnosis, or medical evidence.

## Project Goal

The thesis prototype answers this implementation question:

```text
Can a phone-based gaze pipeline estimate which article line a reader is looking at,
then turn that line stream into useful reading-effort signals?
```

The current answer is yes at prototype level, with major caveats around calibration quality, lighting, face visibility, posture stability, and gaze noise.

The prototype intentionally focuses on line-level behavior because a phone front camera and consumer-device landmark model are not reliable enough for microsaccades, word-level fixation, or precise eye-movement research.

## High-Level Pipeline

Current live reading flow:

```text
Phone front camera
-> CameraX ImageAnalysis frame
-> upright + horizontally mirrored bitmap
-> MediaPipe FaceLandmarker
-> face, eye, eyelid, and iris landmarks
-> raw eye-relative gaze feature: gaze_x, gaze_y
-> blink/no-face filtering
-> median filter
-> One Euro filter
-> 16-point calibration mapper
-> full-screen phone coordinate: x, y
-> GazeProvider.onGaze(x, y)
-> article TextView line AOI mapper
-> stable line/word target
-> ReadingStateInferencer
-> WindowedStabilityEstimator
-> AdaptiveScaffoldController
-> GazeOverlayView
-> GazeAOI / GazeRSI / GazeScaffold logs
```

The important architecture boundary is `GazeProvider`:

```kotlin
interface GazeProvider {
    fun interface OnGaze {
        fun onGaze(x: Float, y: Float)
    }

    fun setOnGaze(listener: OnGaze)
    fun start(owner: LifecycleOwner)
    fun stop()
}
```

Everything after gaze estimation consumes only full-screen phone pixels through this boundary. Article line mapping and RSI do not depend directly on MediaPipe, CameraX, calibration storage, or model internals.

## Implementation History

The implementation evolved in stages.

### Original App

NewsMead was originally a normal Android news app with Firebase Authentication, Firebase-backed saved lists/history, a remote article API, native Android XML layouts, Kotlin fragments, and article reading through a native `TextView`.

For the thesis, the app was converted into a controlled research instrument.

### Study Mode Conversion

The production backend was no longer reliable for study use, so the app was modified to run offline.

Main file:

```text
app/src/main/java/com/newsmead/data/StudyConfig.kt
```

Study flags:

```kotlin
const val BYPASS_LOGIN = true
const val OFFLINE_MODE = true
const val ARTICLES_ASSET = "study_articles.json"
const val LOCK_ARTICLE_FONT_SIZE = true
const val ARTICLE_FONT_SIZE_DP = 22f
const val GAZE_ENABLED = true
const val GAZE_TOUCH_VALIDATION = false
val SCAFFOLD_MODE = ScaffoldMode.ADAPTIVE
```

Meaning:

- `BYPASS_LOGIN = true`: the splash screen skips Firebase authentication and goes straight to the main app.
- `OFFLINE_MODE = true`: article data comes from `app/src/main/assets/study_articles.json`.
- `LOCK_ARTICLE_FONT_SIZE = true`: the article body size is fixed so line boxes do not change during a study session.
- `ARTICLE_FONT_SIZE_DP = 22f`: the body text is applied in `dp`, not `sp`, so Android accessibility font scaling does not change the actual pixel height.
- `GAZE_ENABLED = true`: the article screen attaches the gaze overlay, line mapper, and RSI inferencer.
- `GAZE_TOUCH_VALIDATION = false`: live gaze is used by default. If set to `true`, finger touches simulate gaze for line-mapping validation.
- `SCAFFOLD_MODE = ScaffoldMode.ADAPTIVE`: which visual scaffolding behaviour the article screen runs. `ADAPTIVE` is the study condition. Other values force a single level or cycle through all of them for validation and demo recording. See [Scaffold Modes](#scaffold-modes).

### Stage 4: Line AOI Mapping

Stage 4 connected gaze coordinates to article line numbers.

Main file:

```text
app/src/main/java/com/newsmead/gaze/LineAoiMapper.kt
```

AOI means Area of Interest. In this project, the main AOI is the article text line.

The article body is a native Android `TextView`, not a WebView. This matters because Android exposes line layout information directly through `TextView.layout`.

The line mapper does this:

```text
screen gaze y
-> subtract TextView top position on screen
-> subtract TextView top padding
-> ask Android Layout for the line at that vertical position
-> return 0-based line index
```

Because `getLocationOnScreen()` is used, scrolling is handled naturally. When the article scrolls, the `TextView`'s on-screen position changes. The mapper does not need separate scroll offset bookkeeping.

### Stage 5: Reading State Inference

Stage 5 converts line samples into reading behavior events.

Main file:

```text
app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt
```

It detects line samples, fixations, dwell events, regressions, and a composite RSI score.

It intentionally does not use saccade velocity, microsaccades, word-level fixation points, or pupil measurements. Those signals are outside the reliable range of this tracker.

### Wi-Fi Prototype Path

Earlier progress notes describe a laptop/Webcam/GazeFollower over UDP path. That was an intermediate route used to prove the architecture and line mapping. The current source no longer contains the active Wi-Fi gaze provider. The current implementation is phone-local:

```text
CameraX + MediaPipe on phone
-> local raw features
-> local calibration mapper
-> GazeProvider
```

This distinction matters for thesis writing. The active build should be described as local on-device gaze estimation, not a laptop UDP gaze stream.

## Technology Stack

Android:

- Kotlin
- Android Views / XML layouts
- ViewBinding
- Fragment navigation
- CameraX
- MediaPipe Tasks Vision
- Firebase libraries still present, but bypassed/degraded in study mode
- Room for local/offline article persistence
- Logcat for prototype output

Gaze dependencies:

```kotlin
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-core:1.3.4")
implementation("com.google.mediapipe:tasks-vision:0.10.29")
```

Model asset:

```text
app/src/main/assets/face_landmarker.task
```

## MediaPipe And TFLite Usage

The project uses MediaPipe FaceLandmarker through:

```text
com.google.mediapipe:tasks-vision:0.10.29
```

Main file:

```text
app/src/main/java/com/newsmead/gaze/MediaPipeRawGazeSource.kt
```

MediaPipe is used for landmark detection only. It does not directly output the line being read, the gaze coordinate, or the RSI.

What MediaPipe does:

- Loads `face_landmarker.task`.
- Runs in `LIVE_STREAM` mode.
- Detects one face.
- Produces normalized face landmarks, including iris landmarks.
- Provides the landmark points used by NewsMead to compute raw gaze features.

What NewsMead code does after MediaPipe:

- Extracts iris centers.
- Compares iris position against eye corners and eyelids.
- Computes raw `gaze_x` and `gaze_y`.
- Filters blink/no-face frames.
- Smooths samples.
- Uses calibration to map features to screen pixels.
- Maps screen pixels to article lines.
- Computes RSI.

### About TFLite

There is no direct `org.tensorflow:tensorflow-lite` dependency and no direct `Interpreter` usage in the app source.

The relevant model is the MediaPipe task file:

```text
face_landmarker.task
```

MediaPipe task files commonly package model assets that are executed through the MediaPipe Tasks runtime. For thesis wording, the safest description is:

```text
The prototype uses MediaPipe Tasks Vision with the bundled FaceLandmarker task model.
The app does not implement a custom TensorFlow Lite gaze model or call a TFLite
Interpreter directly. TFLite is only relevant as the underlying model format/runtime
inside the MediaPipe task package, not as a separate gaze-estimation module written
by this project.
```

Avoid saying:

```text
NewsMead uses TFLite to directly predict gaze coordinates.
```

More accurate:

```text
NewsMead uses MediaPipe FaceLandmarker to obtain face and iris landmarks. The
project code then derives gaze features and calibrates them to screen coordinates.
```

## Camera And MediaPipe Details

`MediaPipeRawGazeSource` starts a front-camera CameraX stream.

Important camera choices:

- Front camera: `CameraSelector.DEFAULT_FRONT_CAMERA`.
- Analysis resolution target: `480 x 360`.
- Backpressure strategy: `STRATEGY_KEEP_ONLY_LATEST`.
- Image format: `RGBA_8888`.
- Target FPS range: `30,30`.

Why `480 x 360` and 30 FPS:

- Full-resolution frames made MediaPipe too slow.
- Low FPS starved the smoothing filters.
- Poor frame rate caused gaze jumps and worse vertical accuracy.
- The smaller analysis stream gives more stable real-time behavior.

MediaPipe delegate behavior:

- GPU delegate is attempted first.
- If GPU initialization fails, CPU delegate is used.

Relevant logs:

```text
MediaPipeGaze: FaceLandmarker using GPU delegate
MediaPipeGaze: GPU delegate unavailable, falling back to CPU
MediaPipeGaze: FaceLandmarker using CPU delegate
```

Frame transformation:

- CameraX frames are converted into an ARGB bitmap.
- Row stride is handled to avoid corrupted frames when `rowStride != width * pixelStride`.
- The bitmap is rotated upright.
- The bitmap is mirrored horizontally for the front camera.

The mirroring step matters because a front camera behaves like a selfie camera. The gaze feature logic expects the visible eye geometry to match the screen orientation.

## Landmark Feature Extraction

MediaPipe returns normalized landmarks. NewsMead uses specific eye and iris landmarks.

Iris landmark ranges:

```text
left iris: 468..472
right iris: 473..477
```

Eye reference landmarks:

```text
Eye 1 corners: 33, 133
Eye 1 eyelids: 159, 145
Eye 2 corners: 263, 362
Eye 2 eyelids: 386, 374
```

The app computes an averaged iris center for each iris ring.

Then it computes:

```text
gaze_x = iris horizontal fraction between eye corners
gaze_y = iris vertical fraction between eyelids
```

For each eye:

```text
horizontal fraction = iris_x between corner_a and corner_b
vertical fraction   = iris_y between top_lid and bottom_lid
```

The two eyes are averaged:

```text
gaze_x = (eye1_horizontal + eye2_horizontal) / 2
gaze_y = (eye1_vertical + eye2_vertical) / 2
```

These values are raw features. They are not screen pixels.

Example raw log:

```text
MediaPipeGaze: raw gaze=(0.4821, 0.3374) fps=29.6
```

Interpretation:

- `0.4821` is the horizontal eye-relative iris position.
- `0.3374` is the vertical eye-relative iris position.
- `fps=29.6` means MediaPipe is returning about 29.6 landmark results per second.
- This does not mean the user is looking at 48.21% of the phone width and 33.74% of the phone height. Calibration is still required.

## Blink And No-Face Filtering

Blink frames are dropped because eyelid and iris landmarks become unreliable during blinks.

The system computes eye openness:

```text
openness = vertical eyelid gap / horizontal eye width
```

It averages the openness of both eyes.

Blink thresholds are in `StudyConfig`:

```kotlin
const val GAZE_BLINK_CLOSE_THRESHOLD = 0.04f
const val GAZE_BLINK_OPEN_THRESHOLD = 0.055f
```

Hysteresis is used:

- Enter blink state when openness falls below `0.04`.
- Leave blink state only when openness rises above `0.055`.
- Values between the two thresholds keep the previous blink state.

Why hysteresis:

- If only one threshold was used, the blink state could rapidly flip when openness hovers near the threshold.
- The dead band between close/open thresholds prevents chatter.

Blink diagnostic log:

```text
MediaPipeGaze: open=0.061 blink=false close=0.040 openTh=0.055 drop=3 emit=142 noFace=0
```

Fields:

- `open`: latest eye openness ratio.
- `blink`: whether the source currently considers the eye state a blink.
- `close`: threshold for entering blink state.
- `openTh`: threshold for exiting blink state.
- `drop`: number of frames dropped because of blink state.
- `emit`: number of usable raw gaze samples emitted.
- `noFace`: number of frames with no usable face landmarks.

How to interpret:

- High `emit` with low `drop` and low `noFace` is healthy.
- High `noFace` means face visibility, lighting, camera permission, or model detection is failing.
- High `drop` means many frames are being treated as blinks. This can be real blinking, eyes partly closed, glasses/lighting issues, or thresholds that are too strict for the participant.

## 16-Point Calibration

Calibration connects raw eye-relative gaze features to phone screen coordinates.

Main files:

```text
app/src/main/java/com/newsmead/activities/GazeCalibrationActivity.kt
app/src/main/java/com/newsmead/gaze/CalibrationStore.kt
app/src/main/java/com/newsmead/gaze/CalibrationView.kt
app/src/main/java/com/newsmead/gaze/GazeMapper.kt
```

Calibration is run once per participant/session or whenever posture/phone position changes enough that gaze is no longer accurate.

### Calibration Grid

The calibration screen shows 16 dots in a 4x4 grid.

Fractions:

```text
x = 10%, 36.67%, 63.33%, 90%
y = 10%, 36.67%, 63.33%, 90%
```

Grid:

```text
(10%,10%)     (36.67%,10%)     (63.33%,10%)     (90%,10%)
(10%,36.67%)  (36.67%,36.67%)  (63.33%,36.67%)  (90%,36.67%)
(10%,63.33%)  (36.67%,63.33%)  (63.33%,63.33%)  (90%,63.33%)
(10%,90%)     (36.67%,90%)     (63.33%,90%)     (90%,90%)
```

For each dot:

1. The dot is shown.
2. The app waits `800 ms` for the user's eyes to settle.
3. The app collects raw gaze samples for `1000 ms`.
4. At least `10` valid samples are required.
5. The median `gaze_x` and median `gaze_y` are computed.
6. The median raw feature is paired with the known screen dot coordinate.
7. The pair is stored.

Calibration constants:

```kotlin
SETTLE_MS = 800L
COLLECT_MS = 1000L
MIN_SAMPLES = 10
MARGIN_FRAC = 0.1f
```

### Calibration CSV

Calibration is saved in app-private storage:

```text
/data/user/0/com.newsmead/files/calibration_16point.csv
```

CSV format:

```csv
screen_x,screen_y,gaze_x,gaze_y
108.0,240.0,0.4213,0.2861
...
```

Meaning:

- `screen_x`: known target x coordinate in phone pixels.
- `screen_y`: known target y coordinate in phone pixels.
- `gaze_x`: median raw eye-relative horizontal feature while looking at the dot.
- `gaze_y`: median raw eye-relative vertical feature while looking at the dot.

### Calibration Logs

Successful per-dot log:

```text
GazeCalib: pair 1/16: screen=(108, 240) gaze=(0.4213, 0.2861) n=12
```

Fields:

- `pair 1/16`: first of 16 calibration points.
- `screen=(108, 240)`: dot location in screen pixels.
- `gaze=(0.4213, 0.2861)`: median raw feature collected while the user looked at the dot.
- `n=12`: number of valid raw samples collected.

Retry log:

```text
GazeCalib: Dot 3: only 4 samples, re-collecting
```

Meaning:

- The app did not collect enough valid samples during that dot.
- Common causes: no face, blink filtering, poor lighting, moving phone/head, camera permission issue, or MediaPipe running too slowly.

Completion log:

```text
GazeCalib: Wrote 16 calibration pairs to /data/user/0/com.newsmead/files/calibration_16point.csv
```

Meaning:

- Calibration completed and can be loaded by live article reading or the gaze test.

### Calibration Ground Truth

Calibration ground truth means the app knows where it placed the dot.

There is no external eye tracker confirming that the participant truly looked at the dot. The method assumes:

- The participant looked directly at each dot.
- The participant did not move significantly during calibration.
- The phone position stayed stable.
- Lighting and face visibility stayed good.
- MediaPipe produced stable landmarks.

Bad calibration produces bad line mapping and misleading RSI.

## GazeMapper

Main file:

```text
app/src/main/java/com/newsmead/gaze/GazeMapper.kt
```

`GazeMapper` maps raw gaze features to phone screen pixels.

It fits separate second-degree polynomial regressions for screen x and screen y.

Formula:

```text
screen = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

For x:

```text
screen_x = a0 + a1*zx + a2*zy + a3*zx^2 + a4*zy^2 + a5*zx*zy
```

For y:

```text
screen_y = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

`zx` and `zy` are standardized gaze features:

```text
zx = (gaze_x - calibration_mean_x) / calibration_std_x
zy = (gaze_y - calibration_mean_y) / calibration_std_y
```

Before mapping, live values are clamped to the calibration feature range:

```text
gaze_x = clamp(gaze_x, min_calibration_gaze_x, max_calibration_gaze_x)
gaze_y = clamp(gaze_y, min_calibration_gaze_y, max_calibration_gaze_y)
```

Why clamp:

- The polynomial can produce extreme values outside the training range.
- Clamping prevents large extrapolation jumps when landmarks drift slightly.

The fit uses ridge regularization:

```text
lambda = 1.0
```

The intercept is not penalized. Ridge regularization reduces overshoot because the calibration feature range is small and the second-degree polynomial can otherwise become unstable.

Minimum mapper samples:

```kotlin
MIN_SAMPLES = 6
```

In practice, calibration should provide 16 samples. The lower mapper minimum exists so the regression has enough rows to solve the six-feature model, but a real study session should use the full 16 points.

## Smoothing

Main file:

```text
app/src/main/java/com/newsmead/gaze/LocalCalibratedGazeProvider.kt
```

Smoothing happens before mapping:

```text
raw gaze feature
-> median filter
-> One Euro filter
-> GazeMapper
-> screen coordinate
```

This order matters. The polynomial mapper can amplify tiny raw-feature noise into large screen-coordinate jumps. Filtering before mapping reduces that amplification.

### Median Filter

Main file:

```text
app/src/main/java/com/newsmead/gaze/MedianFilter.kt
```

Default window:

```kotlin
window = 7
```

Purpose:

- Reject short spikes.
- Reduce one-frame landmark errors.
- Reduce blink-adjacent noise.

### One Euro Filter

Main file:

```text
app/src/main/java/com/newsmead/gaze/OneEuroFilter.kt
```

Current live parameters:

```kotlin
minCutoff = 0.7
beta = 0.005
```

Purpose:

- Smooth jitter when gaze is stable.
- Stay more responsive when the signal moves quickly.

## Article Line AOI Mapping

Main file:

```text
app/src/main/java/com/newsmead/gaze/LineAoiMapper.kt
```

Article wiring:

```text
app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt
```

The article body is displayed by:

```text
binding.tvArticleText
```

Line mapping logic:

```kotlin
val layout = textView.layout ?: return -1
textView.getLocationOnScreen(loc)
val localY = screenY - loc[1] - textView.totalPaddingTop
if (localY < 0f || localY > layout.height.toFloat()) return -1
return layout.getLineForVertical(localY.toInt())
```

Interpretation:

- `screenY` is the full-screen gaze y coordinate.
- `loc[1]` is the `TextView`'s current top position on the device screen.
- `totalPaddingTop` removes internal top padding.
- `layout.getLineForVertical()` converts a vertical pixel position into a line index.
- `-1` means the gaze is outside the article text body or the layout is not ready.

Line indices are 0-based:

```text
line=0 means first laid-out line
line=10 means the eleventh laid-out line
```

The logged denominator is total laid-out lines:

```text
line=10/76
```

This means line index 10 out of 76 total lines. Since the index is 0-based, the visible line is the eleventh line.

## Gaze Overlay

Main file:

```text
app/src/main/java/com/newsmead/gaze/GazeOverlayView.kt
```

The overlay draws a gaze dot over the article screen. It is used for debugging and validation.

Purpose:

- Visually check whether the estimated gaze point is near where the participant is looking.
- Compare red dot movement with `GazeAOI` logs.
- Show FPS during live gaze.

The overlay is not the data source. It only displays the output produced by the gaze provider.

The same overlay also draws the four adaptive scaffold levels. Which one appears is decided by `SCAFFOLD_MODE`.

## Scaffold Modes

Main files:

```text
app/src/main/java/com/newsmead/data/StudyConfig.kt
app/src/main/java/com/newsmead/gaze/AdaptiveScaffoldController.kt
app/src/main/java/com/newsmead/gaze/GazeOverlayView.kt
```

`SCAFFOLD_MODE` selects how the article screen decides which visual scaffold to show. It is a compile-time constant, so changing it means editing `StudyConfig.kt` and rebuilding. There is no in-app toggle.

```kotlin
val SCAFFOLD_MODE = ScaffoldMode.ADAPTIVE
```

| Value | Behaviour |
| --- | --- |
| `ADAPTIVE` | **The study condition.** Levels are selected by the Adaptive Instability Index, subject to baseline readiness, gaze confidence, and escalation/withdrawal persistence. |
| `OFF` | No scaffold at all. The static control condition. |
| `FORCE_WORD` | Level 1 only: a translucent box on the fixated word. |
| `FORCE_LINE` | Level 2 only: a translucent band on the current line. |
| `FORCE_FOCUS` | Level 3 only: text outside the current line plus two lines above/below is dimmed. |
| `FORCE_REENTRY` | Level 4 only: the last stable line is emphasised, with an arrow when it is off screen. |
| `DEMO_CYCLE` | Holds each level in turn (none, word, line, focus, re-entry) and loops, for recording all four in one continuous take. |

Every mode except `ADAPTIVE` pins the level directly and **bypasses the adaptive policy**: no baseline wait, no confidence gate, no persistence timing. Real gaze still decides *where* each level is drawn, so the forced and demo modes validate rendering and targeting only. They demonstrate nothing about instability detection, and results from them must not be reported as adaptive behaviour.

Related switches:

```kotlin
const val SCAFFOLD_DEMO_LEVEL_DURATION_MS = 12_000L
const val SCAFFOLD_DEMO_CAPTION = true
const val GAZE_DEBUG_VISUALS = true
```

- `SCAFFOLD_DEMO_LEVEL_DURATION_MS`: how long `DEMO_CYCLE` holds each level.
- `SCAFFOLD_DEMO_CAPTION`: draws the active level's name on screen during `DEMO_CYCLE`.
- `GAZE_DEBUG_VISUALS`: the red gaze dot and FPS readout. A researcher diagnostic, not a participant scaffold.

Before any participant-facing session, confirm `SCAFFOLD_MODE` is `ADAPTIVE` (or `OFF` for the control condition) and `GAZE_DEBUG_VISUALS` is `false`.

Transitions are logged under `GazeScaffold`:

```powershell
adb logcat -s GazeScaffold
```

The full controller policy, thresholds, level designs, validation order, and outstanding research decisions are documented in [docs/adaptive-visual-scaffolding.md](docs/adaptive-visual-scaffolding.md).

## RSI: Reading State Index

Main file:

```text
app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt
```

RSI is a reading-effort signal derived from line-level gaze behavior.

It is not a comprehension score, quiz score, medical diagnosis, confirmed measure of dyslexia or reading ability, or proof that the participant understood or failed to understand the article.

RSI should be interpreted as:

```text
Higher RSI = more detected reading effort or more tracking instability.
Lower RSI = smoother detected line progression.
```

Always inspect raw logs before interpreting the score.

### RSI Input

The input is a stream of line samples:

```text
line index, line count, timestamp
```

Invalid samples are ignored:

```text
lineIndex < 0
lineCount <= 0
```

This means gaze outside the text does not directly affect RSI events.

### Fixation

A fixation is emitted when gaze remains on the same line for at least:

```text
300 ms
```

Code constant:

```kotlin
DEFAULT_FIXATION_THRESHOLD_MS = 300L
```

Example:

```text
GazeRSI: fixation line=21/76 duration=406ms
```

Meaning:

- The participant's estimated gaze stayed on line index 21 long enough to count as a fixation.
- Duration was 406 ms.

### Dwell

Dwell is emitted when the participant leaves a line after spending at least:

```text
120 ms
```

Code constant:

```kotlin
DEFAULT_MIN_DWELL_MS = 120L
```

Example:

```text
GazeRSI: dwell line=21/76 duration=1018ms
```

Meaning:

- The participant spent 1018 ms on line index 21 before the active line changed.
- Dwell is counted when leaving the line or when the inferencer is flushed.

### Regression

A regression is a confirmed move from a later line back to an earlier line.

Example:

```text
GazeRSI: regression 21->18 t=1720000000000
```

Meaning:

- The highest reached line was 21.
- The gaze moved back to line 18.
- The backward movement was held long enough to be confirmed.

Regression confirmation threshold:

```text
300 ms
```

Code constant:

```kotlin
DEFAULT_REGRESSION_CONFIRM_MS = 300L
```

Minimum line jump:

```text
1 line
```

Code constant:

```kotlin
DEFAULT_REGRESSION_MIN_LINE_JUMP = 1
```

Why confirmation exists:

- Gaze estimates can bounce upward briefly due to jitter.
- A backward move is counted only if the system remains on the earlier line for 300 ms.
- Brief upward bounces are ignored.

The inferencer also suppresses duplicate regressions during the same backward episode. It uses the highest line reached as the anchor, not only the immediately previous line. This catches one-line rereads while reducing repeated counts from jitter.
## RSI Score Formula

The composite RSI score is logged from 0 to 100.

Formula:

```text
score = 100 * (
  0.40 * regressionComponent +
  0.35 * dwellComponent +
  0.25 * fixationComponent
)
```

Weights:

```text
Regression: 40%
Dwell:      35%
Fixation:   25%
```

### Component Formulas

Regression component:

```text
regressionComponent = regressionCount / linesRead
```

Capped to:

```text
0.0 to 1.0
```

Where:

```text
linesRead = maxLineReached + 1
```

Dwell component:

```text
dwellComponent = totalDwellMs / elapsedMs
```

Capped to:

```text
0.0 to 1.0
```

Fixation component:

```text
fixationRatePerMinute = fixationCount / elapsedMinutes
fixationComponent = fixationRatePerMinute / 120
```

Capped to:

```text
0.0 to 1.0
```

Reference fixation rate:

```kotlin
EXPECTED_FIXATIONS_PER_MINUTE = 120.0
```

### Weight Justification

Regression has the highest weight because returning from a later line to an earlier line is the clearest sign of rereading or reading instability. It may indicate confusion, checking missed information, careful reprocessing, or gaze jitter.

Dwell has the second-highest weight because longer time on a line can indicate hesitation, careful processing, text difficulty, distraction, or stable gaze. It matters, but it is less specific than regression.

Fixation has the lowest weight because fixation count is more sensitive to personal reading style and tracker noise. A high fixation count may reflect effort, but it can also reflect slow normal reading or small line-bouncing.

These weights are heuristic prototype weights. For a formal thesis study, they should be discussed as tunable and validated against pilot data, comprehension outcomes, or researcher-coded difficulty episodes.

## RSI Score Ranges

Suggested interpretation:

| Score | Level | Interpretation |
| --- | --- | --- |
| `0-30` | Low effort | Smooth detected reading, few rereads, short hesitation, stable forward movement. |
| `31-60` | Moderate effort | Some pauses, rereading, or attention shifts; may be normal depending on article difficulty. |
| `61-80` | High effort | Frequent pausing, rereading, unstable movement, possible difficulty or careful reading. |
| `81-100` | Very high effort | Strong signal of reading effort or tracker noise. Inspect raw logs before making claims. |

Safe wording:

```text
The RSI score suggests increased reading effort.
The high regression count may indicate rereading or gaze instability.
This segment should be reviewed together with calibration quality and raw line logs.
```

Avoid:

```text
The participant definitely failed to understand the text.
The score proves comprehension level.
The score diagnoses reading difficulty.
```

## Log Tags

Useful Logcat command:

```powershell
adb logcat -s MediaPipeGaze GazeCalib GazeStage3 GazeAOI GazeRSI DataHelper ArticleFragment
```

Core gaze tags:

| Tag | Source | Purpose |
| --- | --- | --- |
| `MediaPipeGaze` | `MediaPipeRawGazeSource` | Raw gaze, FPS, blink/no-face diagnostics, FaceLandmarker startup errors. |
| `GazeCalib` | `GazeCalibrationActivity`, `CalibrationStore` | 16-point calibration collection and CSV save/read errors. |
| `GazeStage3` | `GazeTestActivity` | Accuracy test point errors and median error summary. |
| `GazeAOI` | `ArticleFragment`, `LineAoiMapper` | Screen gaze coordinate mapped to article line index. |
| `GazeRSI` | `ReadingStateInferencer` listener in `ArticleFragment` | Line samples, fixation, dwell, regression, RSI score. |
| `GazeScaffold` | `AdaptiveScaffoldController` via `ArticleFragment` | Stability index, gaze confidence, baseline readiness, and scaffold level transitions. |

## How To Interpret MediaPipeGaze Logs

Startup:

```text
MediaPipeGaze: FaceLandmarker using GPU delegate
MediaPipeGaze: Local MediaPipe raw gaze source started; blinkClose=0.040 blinkOpen=0.055
```

Healthy meaning:

- The model loaded.
- CameraX started.
- FaceLandmarker is producing live results.
- Blink thresholds are active.

Fallback:

```text
MediaPipeGaze: GPU delegate unavailable, falling back to CPU
MediaPipeGaze: FaceLandmarker using CPU delegate
```

Meaning:

- The app still runs, but FPS may be lower.
- If FPS drops too much, gaze may be less stable.

Raw gaze:

```text
MediaPipeGaze: raw gaze=(0.4821, 0.3374) fps=29.6
```

Meaning:

- Raw eye-relative features are being emitted.
- Values are not screen pixels.
- FPS near 30 is desirable.

No face:

```text
MediaPipeGaze: No face landmarks
```

Meaning:

- MediaPipe did not detect a usable face in that frame.
- Possible causes: face outside camera view, poor lighting, camera blocked, phone angle, glasses glare, low contrast, or model failure.

Blink stats:

```text
MediaPipeGaze: open=0.032 blink=true close=0.040 openTh=0.055 drop=18 emit=201 noFace=2
```

Meaning:

- Eye openness is below the close threshold.
- The frame is being treated as a blink.
- Blink frames are dropped and not used for raw gaze.

Troubleshooting:

- If `noFace` increases rapidly, fix face visibility and lighting.
- If `drop` increases rapidly while the participant is not blinking, check eyelid visibility, glasses, and lighting.
- If FPS is far below 30, expect unstable mapping and unreliable RSI.

## How To Interpret GazeCalib Logs

Per-dot success:

```text
GazeCalib: pair 5/16: screen=(684, 240) gaze=(0.5012, 0.2935) n=11
```

Deduction:

- Dot 5 was shown at screen coordinate `(684,240)`.
- During the 1000 ms collection window, 11 valid raw gaze samples were collected.
- The median raw feature was `(0.5012,0.2935)`.
- This pair will train the mapper.

Retry:

```text
GazeCalib: Dot 5: only 3 samples, re-collecting
```

Deduction:

- Fewer than 10 usable samples were collected.
- The app does not trust the point.
- The same dot will be shown again.

Final point dump:

```text
GazeCalib: final 108.0,240.0,0.4213,0.2861
```

Deduction:

- This is one CSV row: `screen_x,screen_y,gaze_x,gaze_y`.
- It can be used to inspect whether calibration features changed across the grid.

Completion:

```text
GazeCalib: Wrote 16 calibration pairs to /data/user/0/com.newsmead/files/calibration_16point.csv
```

Deduction:

- Calibration is complete.
- Live article gaze and Stage 3 test can start.

Bad calibration signs:

- Many retries.
- Very low sample counts.
- Many `No face landmarks`.
- Raw gaze features that barely change across different dots.
- Participant moved head or phone during calibration.
- Red gaze dot is consistently shifted after calibration.

## How To Interpret GazeStage3 Accuracy Logs

The Stage 3 test uses a 3x3 grid separate from calibration.

Calibration grid:

```text
10%, 36.67%, 63.33%, 90%
```

Test grid:

```text
20%, 50%, 80%
```

This tests interpolation, not memorization of calibration dots.

Per-point log:

```text
GazeStage3: point 4/9: target=(540, 1200) est=(612, 1325) err=144 px dx=72 dy=125 samples=12 emitted=14 blinkDrop=1 noFace=0 open=0.068 blink=false th=0.040/0.055
```

Fields:

- `point 4/9`: fourth test point out of nine.
- `target=(540,1200)`: true target location.
- `est=(612,1325)`: median estimated gaze location.
- `err=144 px`: Euclidean distance from estimate to target.
- `dx=72`: horizontal signed error. Positive means estimate is to the right of target.
- `dy=125`: vertical signed error. Positive means estimate is below target.
- `samples=12`: number of collected calibrated gaze samples for this point.
- `emitted=14`: raw usable samples emitted during this point window.
- `blinkDrop=1`: frames dropped due to blink filtering.
- `noFace=0`: frames without usable face landmarks.
- `open=0.068`: last eye openness ratio.
- `blink=false`: final blink state.
- `th=0.040/0.055`: blink close/open thresholds.

Summary log:

```text
GazeStage3: ACCURACY (n=9): median err=316 px / 2.08 cm; vertical median=92 px / 0.60 cm; emitted=130 blinkDrop=4 noFace=1 open=0.067 blink=false th=0.040/0.055; xdpi=386 ydpi=388
```

Fields:

- `n=9`: nine test points completed.
- `median err`: median Euclidean gaze error.
- `vertical median`: median absolute vertical error.
- `emitted`: usable raw samples during the whole run.
- `blinkDrop`: blink-dropped frames during the run.
- `noFace`: no-face frames during the run.
- `xdpi/ydpi`: display metrics used to convert pixels to centimeters.

Why vertical median matters:

- Article line detection depends mostly on vertical position.
- Horizontal accuracy is weaker and less important for line-level reading.
- The line-level gate is:

```text
median vertical gaze error <= one article text-line height
```

If vertical median is larger than line height, line mapping can bounce between adjacent or distant lines.

How the log was deduced:

1. For each target, the app knows the exact target coordinate.
2. During collection, it stores live calibrated gaze estimates.
3. It takes the median estimated x and y.
4. It computes:

```text
dx = estimated_x - target_x
dy = estimated_y - target_y
err = sqrt(dx^2 + dy^2)
```

5. After nine points, it computes median Euclidean error and median absolute vertical error.
6. It converts pixels to centimeters using:

```text
px_per_cm_x = xdpi / 2.54
px_per_cm_y = ydpi / 2.54
```

Caveat:

- Device-reported DPI may be approximate, so centimeter values are useful but not perfect.

## How To Interpret GazeAOI Logs

Example:

```text
GazeAOI: Local calibrated gaze started with 16 calibration samples
```

Meaning:

- The article screen loaded calibration.
- `GazeMapper` fit succeeded.
- Live MediaPipe gaze started.

Missing calibration:

```text
GazeAOI: No calibration found; live gaze not started
```

Meaning:

- `calibration_16point.csv` does not exist.
- Run calibration from the article toolbar or launch `GazeCalibrationActivity`.

Invalid calibration:

```text
GazeAOI: Calibration fit failed; live gaze not started
```

Meaning:

- The CSV exists, but the polynomial fit failed.
- Common cause: bad or degenerate calibration points.
- Recalibrate.

Line log:

```text
GazeAOI: gaze=(520,1040) line=10/76
```

Fields:

- `gaze=(520,1040)`: mapped full-screen phone pixel coordinate.
- `line=10/76`: gaze y falls on 0-based article line index 10 out of 76 total laid-out lines.

Deduction:

1. MediaPipe produced raw `gaze_x,gaze_y`.
2. Filters smoothed the raw features.
3. `GazeMapper` converted them to screen `(x,y)`.
4. The overlay drew the dot at `(520,1040)`.
5. `LineAoiMapper` compared `1040` against the current `TextView` line layout.
6. It returned line index `10`.
7. RSI consumed line `10`.

Common patterns:

- Mostly increasing line numbers during normal reading: expected.
- Repeated same line: fixation or dwell likely.
- Small up/down bouncing between adjacent lines: possible gaze noise.
- Sudden jump to `-1`: gaze is outside text, layout not ready, or dot is over image/top bar/margins.
- Consistent vertical offset: calibration bias or posture drift.

## How To Interpret GazeRSI Logs

Line sample:

```text
GazeRSI: line=10/76 t=1720000000000
```

Meaning:

- RSI received valid line index 10.
- Total article body line count is 76.
- Timestamp is Unix epoch milliseconds.

Fixation:

```text
GazeRSI: fixation line=10/76 duration=350ms
```

Deduction:

- The same line remained active for at least 300 ms.
- `fixationCount` increases by 1.

Dwell:

```text
GazeRSI: dwell line=10/76 duration=900ms
```

Deduction:

- The active line changed after 900 ms on line 10.
- Because 900 ms is at least 120 ms, a dwell event was emitted.
- `totalDwellMs` increases by 900.

Regression:

```text
GazeRSI: regression 21->18 t=1720000000400
```

Deduction:

- The participant had reached line 21.
- The gaze moved back to line 18.
- It stayed there for at least 300 ms.
- `regressionCount` increases by 1.

Score:

```text
GazeRSI: score=81.6 reg=1.00 dwell=0.95 fix=0.33 events=f15/d21354ms/r58
```

Fields:

- `score=81.6`: composite RSI score out of 100.
- `reg=1.00`: normalized regression component.
- `dwell=0.95`: normalized dwell component.
- `fix=0.33`: normalized fixation component.
- `events=f15/d21354ms/r58`: raw event counts.

Raw events:

- `f15`: 15 fixations.
- `d21354ms`: 21.354 seconds total dwell time.
- `r58`: 58 regressions.

Deduction:

1. Regression component reached the cap of `1.00`, meaning regressions were very frequent relative to lines reached.
2. Dwell component `0.95` means most elapsed time was spent dwelling on detected lines.
3. Fixation component `0.33` means fixation rate was about one third of the 120 fixations/minute reference.
4. Score is high because regression and dwell are high.

Important interpretation:

- `score=81.6` may indicate very high reading effort.
- But `r58` is suspiciously high for many short sessions.
- First check calibration, gaze jitter, and line bouncing before claiming the reader was confused.

## Log-Based Deduction Workflow

Use this workflow when studying a session.

### Step 1: Confirm Tracker Health

Check `MediaPipeGaze`:

```text
FaceLandmarker using GPU delegate
Local MediaPipe raw gaze source started
raw gaze=(...) fps=...
```

Healthy:

- FPS near 30.
- Raw values update when eyes move.
- Low no-face count.
- Blink drops are reasonable.

Unhealthy:

- Repeated `No face landmarks`.
- Very low FPS.
- Very high blink drops.
- Raw values barely change.

### Step 2: Confirm Calibration Quality

Check `GazeCalib`:

- 16 pairs written.
- Each pair has at least 10 samples.
- Few retries.
- Raw gaze values vary across different targets.

Bad signs:

- Many retries.
- Dot collection fails repeatedly.
- Calibration completed while participant moved or looked away.

### Step 3: Confirm Accuracy With Stage 3 Test

Check `GazeStage3`:

- Per-point `dy` values should not be consistently huge.
- Vertical median should be close to or below article line height.
- No-face and blinkDrop should be low.

If vertical median is poor, RSI will be less trustworthy.

### Step 4: Confirm AOI Line Stream

Check `GazeAOI`:

- During normal reading, line numbers should generally move forward.
- During deliberate rereading, line numbers should move backward.
- During scrolling, line mapping should remain consistent.

If line numbers bounce rapidly while the participant is stable, this is likely tracking/calibration noise.

### Step 5: Interpret RSI

Check `GazeRSI`:

- Fixations show stable stops.
- Dwell shows time spent before leaving lines.
- Regressions show confirmed rereads/backward movement.
- Score summarizes the three components.

Always interpret RSI together with calibration quality, Stage 3 vertical error, line bouncing, article length, session duration, participant posture stability, lighting, and glasses.

## Running The Prototype

Use a real Android device with a front camera. An emulator is not suitable for MediaPipe iris/face gaze testing.

Build and install:

```powershell
.\gradlew.bat :app:installDebug
```

If building through command line fails with JDK module errors from kapt, use JDK 17 or Android Studio's configured JDK.

### Study Session Procedure

1. Install the debug build on the phone.
2. Launch the app.
3. Grant camera permission when prompted.
4. Open a study article.
5. Tap the gaze calibration button in the article top bar.
6. On the calibration screen, tap Start.
7. For each dot, ask the participant to look directly at the dot and keep still.
8. Wait until all 16 calibration pairs are saved.
9. Return to the article.
10. Confirm the gaze dot roughly follows gaze.
11. Optionally run the gaze accuracy test from the article top bar.
12. Start Logcat collection.
13. Let the participant read normally.
14. Review `GazeAOI`, `GazeRSI`, and calibration/accuracy logs after the session.

### Useful Logcat Commands

Core thesis logs:

```powershell
adb logcat -s MediaPipeGaze GazeCalib GazeStage3 GazeAOI GazeRSI
```

Include article/data logs:

```powershell
adb logcat -s MediaPipeGaze GazeCalib GazeStage3 GazeAOI GazeRSI DataHelper ArticleFragment
```

Clear logs before a session:

```powershell
adb logcat -c
```

Save logs to a file:

```powershell
adb logcat -s MediaPipeGaze GazeCalib GazeStage3 GazeAOI GazeRSI > session_log.txt
```

## File Map

Study mode:

```text
app/src/main/java/com/newsmead/data/StudyConfig.kt
app/src/main/assets/study_articles.json
```

Gaze interfaces:

```text
app/src/main/java/com/newsmead/gaze/GazeProvider.kt
app/src/main/java/com/newsmead/gaze/LocalRawGazeSource.kt
app/src/main/java/com/newsmead/gaze/LocalGazeSources.kt
```

MediaPipe raw gaze:

```text
app/src/main/java/com/newsmead/gaze/MediaPipeRawGazeSource.kt
app/src/main/assets/face_landmarker.task
```

Calibration:

```text
app/src/main/java/com/newsmead/activities/GazeCalibrationActivity.kt
app/src/main/java/com/newsmead/gaze/CalibrationView.kt
app/src/main/java/com/newsmead/gaze/CalibrationStore.kt
app/src/main/java/com/newsmead/gaze/GazeMapper.kt
```

Filtering:

```text
app/src/main/java/com/newsmead/gaze/MedianFilter.kt
app/src/main/java/com/newsmead/gaze/OneEuroFilter.kt
app/src/main/java/com/newsmead/gaze/LocalCalibratedGazeProvider.kt
```

Article integration:

```text
app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt
app/src/main/java/com/newsmead/gaze/LineAoiMapper.kt
app/src/main/java/com/newsmead/gaze/GazeOverlayView.kt
```

Accuracy test:

```text
app/src/main/java/com/newsmead/activities/GazeTestActivity.kt
app/src/main/java/com/newsmead/gaze/GazeDotView.kt
```

RSI:

```text
app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt
app/src/test/java/com/newsmead/gaze/ReadingStateInferencerTest.kt
```

## Validation And Tests

Targeted RSI tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

The tests cover:

- fixation after dwelling on the same line
- dwell when leaving a line
- confirmed regression after holding an earlier line
- one-line reread regression
- duplicate suppression during the same backward episode
- ignoring brief upward jitter
- ignoring invalid line samples
- composite RSI score calculation

Manual validation:

- Calibration completes with 16 pairs.
- Stage 3 test reports acceptable vertical median error.
- Gaze dot roughly follows the participant's gaze.
- `GazeAOI` line numbers match the approximate read line.
- `GazeRSI` emits fixation/dwell during pauses.
- Deliberate rereading emits regression events.

## Known Limitations

Gaze accuracy depends on:

- lighting
- face visibility
- camera permission
- phone angle
- head position
- reading distance
- participant posture stability
- glasses glare
- eyelid shape
- calibration compliance
- long-session drift

Current limitations:

- No external eye tracker ground truth.
- No automatic drift correction.
- No confidence score exported with each gaze sample.
- Logs are in Logcat, not structured CSV/session export.
- RSI is heuristic and needs validation with participant data.
- The system tracks approximate lines, not exact words.
- Horizontal gaze accuracy is weaker than vertical gaze accuracy.
- High RSI can mean real effort or poor tracking.

## Troubleshooting

### Calibration Keeps Retrying

Likely causes:

- Camera permission not granted.
- Face not visible.
- Poor lighting.
- Participant blinking or looking away.
- Phone moving.
- MediaPipe FPS too low.
- Blink filter dropping too many frames.

Check:

```text
MediaPipeGaze: No face landmarks
MediaPipeGaze: open=... blink=... drop=... emit=... noFace=...
GazeCalib: Dot N: only X samples, re-collecting
```

### Live Gaze Does Not Start

Check:

```text
GazeAOI: No calibration found; live gaze not started
```

Fix:

- Run calibration first.

Check:

```text
GazeAOI: Calibration fit failed; live gaze not started
```

Fix:

- Recalibrate. The saved calibration may be degenerate or corrupted.

### Gaze Dot Is Consistently Too High/Low

Likely causes:

- Poor calibration.
- Posture changed after calibration.
- Phone angle changed.
- Participant was not looking at dots during calibration.
- Raw feature range shifted after calibration.

Fix:

- Recalibrate in the same posture used for reading.
- Keep phone and head stable.
- Improve lighting.

### RSI Is Very High

Do not immediately conclude poor comprehension.

Check:

- Is `r` very high?
- Are `GazeAOI` lines bouncing?
- Is Stage 3 vertical error high?
- Did calibration have many retries?
- Did no-face/blinkDrop counts increase?
- Was the participant scrolling, distracted, or looking away?

High RSI is a flag for review, not a final judgment.

## Thesis Reporting Guidance

Accurate wording:

```text
The system estimates line-level gaze using MediaPipe FaceLandmarker iris and eye
landmarks, a participant-specific 16-point calibration, and a polynomial mapper.
The calibrated gaze coordinate is mapped to native TextView line indices, from
which fixation, dwell, regression, and RSI values are inferred.
```

Calibration wording:

```text
Each calibration point pairs a known on-screen target with the median raw
eye-relative gaze feature collected while the participant fixates the target.
These pairs train a second-degree polynomial mapper from raw gaze feature space
to phone screen coordinates.
```

RSI wording:

```text
The Reading State Index is a heuristic reading-effort score combining normalized
regression frequency, dwell proportion, and fixation rate. It is interpreted as
a signal for closer review, not as a direct comprehension score.
```

MediaPipe/TFLite wording:

```text
MediaPipe Tasks Vision was used to run the FaceLandmarker task model and extract
face/iris landmarks. The application does not directly call a TensorFlow Lite
Interpreter or train a custom TFLite gaze model; gaze coordinates are produced
by project calibration and mapping code.
```

## Recommended Next Steps

For a study-ready version:

1. Export `GazeAOI`, `GazeRSI`, calibration, and Stage 3 accuracy results to CSV.
2. Add participant/session identifiers.
3. Add timestamps relative to session start for easier analysis.
4. Add per-sample confidence or quality flags.
5. Add automatic drift checks or periodic recalibration.
6. Pilot test with target participants.
7. Validate RSI weights against comprehension checks or human-coded difficulty segments.
8. Decide whether high-noise sessions should be excluded based on calibration/accuracy thresholds.

## Summary

NewsMead's gaze thesis prototype works by turning phone-camera landmarks into raw eye-relative features, calibrating those features to screen pixels, mapping the vertical gaze coordinate to native article text lines, and deriving RSI from line-level reading behavior.

The most important rule for studying the implementation is:

```text
Do not interpret RSI alone.
Interpret RSI together with MediaPipe health, calibration quality, Stage 3 vertical
accuracy, GazeAOI line stability, article length, and session context.
```

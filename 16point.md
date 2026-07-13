# Gaze Thesis Prototype

On-device Android gaze-tracking prototype for a gaze-driven adaptive reading interface. The current implementation uses the phone front camera, MediaPipe Face Landmarker iris/eye landmarks, per-user calibration, and a polynomial mapper to estimate on-screen gaze coordinates.

The target use case is line-level reading detection: accurate enough to infer which line of text the user is reading, not character-level pointing.

## Current Status

The project has implemented and tested Stages 1-3 of the gaze module:

| Stage | Status | Notes |
| --- | --- | --- |
| Stage 1: Camera + MediaPipe landmarks | Passed on Galaxy A56 | Front camera, iris landmarks, no-face handling, about 30 fps in good lighting |
| Stage 2: Calibration collection | Passed | Originally 9-point, now upgraded to 16-point calibration |
| Stage 3: Live gaze mapping + benchmark | Implemented and benchmarked | Accuracy is inconsistent across runs; decision gate currently stopped |
| Stage 4: Reading interface line mapping | Not implemented | Planned integration with NewsMead/article view |
| Stage 5: RSI feed | Not implemented | Planned fixation/dwell/regression event feed |

The important conclusion from the current benchmark log is that the MediaPipe + polynomial approach can sometimes reach usable vertical accuracy, but it is not stable enough across runs yet to be accepted as the final tracker.

## Technology Stack

- Kotlin
- Android SDK 36
- minSdk 24
- CameraX 1.4.2
- MediaPipe Tasks Vision 0.10.29
- Apache Commons Math 3.6.1
- Android ViewBinding

Important files:

- `app/src/main/assets/face_landmarker.task` - bundled MediaPipe Face Landmarker model
- `FaceLandmarkerHelper.kt` - MediaPipe wrapper and gaze feature extraction
- `CalibrationActivity.kt` - 16-point calibration collection
- `CalibrationStore.kt` - calibration CSV save/load
- `GazeMapper.kt` - polynomial + ridge regression screen mapper
- `MediaPipeGazeProvider.kt` - live camera-to-gaze pipeline
- `Stage3GazeActivity.kt` - live gaze dot and accuracy benchmark
- `PROGRESS.md` - chronological device testing and decision log
- `context.md` - original staged build specification

## Runtime Flow

The live gaze pipeline is:

```text
Front camera
-> CameraX ImageAnalysis frame
-> upright + mirrored bitmap
-> MediaPipe Face Landmarker
-> iris and eye landmarks
-> eye-relative gaze feature: gaze_x, gaze_y
-> median + One Euro smoothing
-> polynomial calibration mapper
-> estimated screen_x, screen_y
-> GazeProvider.onGaze(x, y)
```

MediaPipe is used only for landmark detection. The app's screen-coordinate gaze estimate is produced by project code through calibration and regression.

## MediaPipe Implementation

`FaceLandmarkerHelper` initializes the MediaPipe Face Landmarker in live-stream mode:

- model: `face_landmarker.task`
- delegate: GPU preferred, CPU fallback
- faces: 1
- face blendshapes: disabled
- output landmarks: MediaPipe face landmarks with iris points

The app extracts iris landmarks:

- left iris: indices `468..472`
- right iris: indices `473..477`

The averaged iris centers are still available for Stage 1 visualization, but they are not the final calibration feature.

## Gaze Feature

The first calibration attempt used raw iris position in the camera frame. That was too sensitive to head position. The current implementation uses an eye-relative feature:

```text
gaze_x = iris horizontal position relative to eye corners
gaze_y = iris vertical position relative to eyelids
```

This is computed for both eyes and averaged.

Eye reference landmarks:

| Reference | Landmark IDs |
| --- | --- |
| Eye 1 corners | `33`, `133` |
| Eye 1 eyelids | `159`, `145` |
| Eye 2 corners | `263`, `362` |
| Eye 2 eyelids | `386`, `374` |

This reduces head-position dependence compared with raw iris-in-frame coordinates, but it does not eliminate head-pose drift.

## Ground Truth

Ground truth in this project means known screen targets shown by the app.

There is no external eye tracker currently providing independent ground truth. The app assumes that when a calibration or benchmark dot is shown, the user is looking at that dot.

### Calibration Ground Truth

The current calibration ground truth is a 16-point grid in `CalibrationActivity`.

The points are placed at these screen fractions:

```text
x = 10%, 36.67%, 63.33%, 90%
y = 10%, 36.67%, 63.33%, 90%
```

This creates a 4x4 grid:

```text
(10%,10%)     (36.67%,10%)     (63.33%,10%)     (90%,10%)
(10%,36.67%)  (36.67%,36.67%)  (63.33%,36.67%)  (90%,36.67%)
(10%,63.33%)  (36.67%,63.33%)  (63.33%,63.33%)  (90%,63.33%)
(10%,90%)     (36.67%,90%)     (63.33%,90%)     (90%,90%)
```

For each calibration dot:

- show the dot
- wait `800 ms` for eye settling
- collect gaze samples for `1000 ms`
- require at least 10 samples
- store the median `gaze_x`, `gaze_y`
- pair it with the known `screen_x`, `screen_y`

Saved CSV format:

```csv
screen_x,screen_y,gaze_x,gaze_y
```

The calibration file is stored as `calibration.csv` in app-private storage.

### Benchmark Ground Truth

The Stage 3 benchmark uses a separate 3x3 grid, not the calibration grid.

Benchmark targets are placed at:

```text
x = 20%, 50%, 80%
y = 20%, 50%, 80%
```

This is intentionally different from the 16 calibration points. It tests interpolation behavior rather than simply checking whether the mapper can reproduce training points.

For each benchmark point:

- show target
- wait `800 ms`
- collect estimated gaze for `1200 ms`
- require at least 10 samples
- take median estimated `screen_x`, `screen_y`
- compare estimate to known target

Logged metrics:

- per-point Euclidean error in pixels
- per-point vertical error in pixels
- median Euclidean error in pixels and centimeters
- median vertical error in pixels and centimeters

Centimeters are estimated from Android display metrics:

```text
px_per_cm_x = xdpi / 2.54
px_per_cm_y = ydpi / 2.54
```

This conversion is useful but not perfect because device-reported DPI can be approximate.

## Mapper / Model

`GazeMapper` fits a second-degree polynomial from gaze feature space to screen pixel space.

Separate regressions are fit for screen x and screen y:

```text
screen_x = a0 + a1*zx + a2*zy + a3*zx^2 + a4*zy^2 + a5*zx*zy
screen_y = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

Before fitting and inference:

- gaze inputs are standardized with calibration mean/std
- live gaze inputs are clamped to the calibration feature range
- ridge regularization is applied with `lambda = 1.0`
- the intercept is not penalized

The ridge term was added because the gaze feature range is small and the quadratic fit can otherwise overshoot badly between sparse calibration points.

## Smoothing

Smoothing is applied before the polynomial mapper in `MediaPipeGazeProvider`.

Current filters:

- `MedianFilter`
- `OneEuroFilter`

The reason smoothing happens before mapping is that the polynomial has high input-to-output gain. Small feature noise can become large screen-coordinate jumps if smoothing is delayed until after mapping.

Current One Euro parameters:

```text
minCutoff = 0.7
beta = 0.005
```

## Benchmarks Recorded So Far

Device used in the progress log:

```text
Galaxy A56 5G
xdpi approx 386
ydpi approx 388
about 152 px/cm
```

### Stage 1 Performance

Stage 1 passed after forcing camera FPS and using lower analysis resolution:

- camera analysis resolution: `480 x 360`
- target camera FPS range: `30,30`
- observed tracking: about `30 fps` in good lighting
- no-face handling works
- iris dots visibly track eye movement

Low light previously caused about `12-13 fps`, so lighting is a meaningful performance factor.

### 9-Point Calibration / Pre-16-Point Results

Before the 16-point upgrade, the Stage 3 decision gate produced:

| Run | Median Error | Vertical Median Error |
| --- | --- | --- |
| R1 | `259 px / 1.69 cm` | `259 px / 1.69 cm` |
| R2 | `345 px / 2.27 cm` | `315 px / 2.06 cm` |

Interpretation:

- failed line-level accuracy for typical text
- vertical error was around 3-5 text-line heights for common mobile reading fonts
- edge errors were especially bad
- quadratic instability caused extreme outliers in one run

### 16-Point + Ridge Results

After moving to 16-point calibration and ridge regression:

| Run | Result |
| --- | --- |
| R1 | overall `316 px / 2.08 cm`; vertical only `92 px / 0.60 cm` |
| R2 | vertical `352 px / 2.30 cm` |

Interpretation:

- vertical accuracy can become viable in a good run
- horizontal accuracy remains weak and systematically shifted
- run-to-run instability is the real blocker
- 16-point calibration improved vertical behavior in one run but did not make the system reliable

## Decision Gate

The project's line-level acceptance criterion is:

```text
median vertical gaze error <= one text-line height
```

This means the final pass/fail threshold depends on the reading UI:

- font size
- line spacing
- device density
- reading distance
- whether the UI uses large accessibility-friendly text

The project still needs the planned reading font/line height to make a final numerical judgment.

Current status from `PROGRESS.md`:

```text
Option 2 polynomial tuning budget spent.
Instability is inherent to single-camera appearance gaze on a handheld.
Per Stage 3 gate: STOP. Decision pending.
```

## Current Limitations

### 1. No Independent External Ground Truth

The app uses displayed targets as ground truth and assumes the user fixates them correctly. There is no external eye tracker validating true gaze.

### 2. Head-Pose Drift

The largest blocker is run-to-run instability caused by:

- phone angle changes
- head position changes
- face distance changes
- posture drift
- handheld movement

The eye-relative feature reduces but does not eliminate this.

### 3. Horizontal Accuracy Is Weak

Horizontal gaze has a smaller feature range than vertical gaze. This makes horizontal mapping less reliable.

For line-level reading, vertical accuracy matters more than horizontal accuracy, but horizontal weakness still limits general gaze-point accuracy.

### 4. Lighting Affects FPS and Landmark Quality

Stage 1 testing showed low light can reduce camera delivery or tracking performance. Good lighting is required for stable results.

### 5. Glasses / Older-Adult Faces Untested

The target thesis population includes older adults. The current benchmark appears to be from a developer device/user setup. Glasses, presbyopia, eyelid shape, wrinkles, and lighting may change landmark quality.

### 6. Calibration Requires User Compliance

The system assumes the user looks directly at each dot during calibration and benchmark collection. Blinks/no-face frames are guarded against, but incorrect fixation is not independently detectable.

### 7. Long-Session Drift Not Solved

The current system does not yet implement:

- automatic recalibration
- drift correction
- confidence scoring
- line-relative adaptive correction

Timestamps are emitted, so drift can be analyzed later.

### 8. Stage 4 and Stage 5 Are Not Integrated Yet

The gaze tracker currently outputs screen coordinates. It has not yet been integrated into:

- article line bounding boxes
- scroll-aware line mapping
- fixation detection
- dwell time
- reading regressions
- RSI computation

## Why `GazeProvider` Matters

The project keeps gaze output behind a `GazeProvider` interface:

```text
setOnGaze(listener)
start(owner)
stop()
```

This means downstream reading and RSI code should depend only on `GazeProvider`, not MediaPipe directly.

The current implementation is:

```text
MediaPipeGazeProvider
```

Future replacements can keep the same interface:

- GazeFollower over WiFi
- L2CS-Net TFLite
- another on-device gaze model
- touch-validation provider

This is important because Stages 4-5 can be built and tested even if the current MediaPipe backend is later replaced.

## Recommended Next Steps

1. Define the target reading UI line height in pixels.
2. Compare vertical median error against that line height.
3. Build Stage 4 with touch-validation mode first.
4. Use touch input to prove line mapping and RSI logic independently of gaze accuracy.
5. Keep `GazeProvider` as the only dependency boundary.
6. Decide whether to replace the current MediaPipe + polynomial backend.

Potential tracker paths:

| Option | Description | Tradeoff |
| --- | --- | --- |
| Current Option 2 | MediaPipe landmarks + calibration polynomial | Fully on-device, already built, but unstable |
| Option 1 | GazeFollower over WiFi | Better expected accuracy, but needs laptop/backend |
| Option 3 | L2CS-Net TFLite | On-device and likely more robust, but requires model conversion/integration |

## How To Run

Use a physical Android device with a front camera. The emulator is not suitable for MediaPipe iris tracking.

Build/install debug:

```powershell
.\gradlew.bat :app:installDebug
```

The launcher activity is `GazeStage1Activity`.

Typical workflow:

1. Launch app.
2. Verify Stage 1 camera/iris tracking.
3. Run calibration.
4. Run Stage 3 gaze test.
5. Use "Run Accuracy Test" to collect benchmark results.
6. Check Logcat tags:

```text
GazeStage1
GazeCalib
GazeStage3
GazeProvider
```

## Summary

This implementation proves that fully on-device gaze estimation using MediaPipe iris landmarks is feasible as a prototype. The strongest result is vertical gaze estimation, which is the axis that matters most for line-level reading.

However, the current benchmark record shows that the approach is not yet reliable enough across runs. The main research finding so far is that calibration quality is not the only issue; handheld single-camera gaze estimation is strongly affected by head-pose and session drift.

The safest path is to continue downstream development through the `GazeProvider` interface, validate line mapping with touch input, and keep the gaze backend replaceable.

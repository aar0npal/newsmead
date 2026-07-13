# Stage 4, Stage 5, and 16-Point Calibration Specs

This document explains the current role of 16-point calibration and the planned Stage 4 and Stage 5 work in the gaze thesis prototype.

Current status:

- Stage 1: implemented and passed on Galaxy A56.
- Stage 2: implemented and upgraded from 9-point to 16-point calibration.
- Stage 3: implemented and benchmarked.
- Stage 4: not implemented.
- Stage 5: not implemented.

The Stage 3 decision gate is currently stopped because gaze accuracy is inconsistent across runs. The downstream Stage 4 and Stage 5 work should therefore depend only on `GazeProvider`, so the gaze backend can be replaced later without rewriting the reading interface or RSI pipeline.

## 16-Point Calibration

The calibration flow is already implemented in `CalibrationActivity.kt`.

The app shows 16 target dots in a 4x4 grid. The user looks at each dot while the app collects gaze samples from the front camera and MediaPipe face/iris landmarks.

Each calibration point creates one pair:

```text
known screen point -> measured gaze feature
```

The saved CSV format is:

```csv
screen_x,screen_y,gaze_x,gaze_y
```

The 16 calibration points are placed at these approximate screen fractions:

```text
x = 10%, 36.67%, 63.33%, 90%
y = 10%, 36.67%, 63.33%, 90%
```

This creates the following 4x4 grid:

```text
(10%,10%)     (36.67%,10%)     (63.33%,10%)     (90%,10%)
(10%,36.67%)  (36.67%,36.67%)  (63.33%,36.67%)  (90%,36.67%)
(10%,63.33%)  (36.67%,63.33%)  (63.33%,63.33%)  (90%,63.33%)
(10%,90%)     (36.67%,90%)     (63.33%,90%)     (90%,90%)
```

For each dot, the app:

1. Shows the target dot.
2. Waits 800 ms so the user's eyes can settle.
3. Collects gaze samples for 1000 ms.
4. Requires at least 10 valid samples.
5. Takes the median `gaze_x` and median `gaze_y`.
6. Saves the median gaze feature together with the known screen position.

The 16-point calibration replaced the original 9-point plan. The reason is better screen coverage, especially near edges. The old 9-point calibration produced larger edge errors and could cause the polynomial mapper to overshoot. The denser 4x4 grid gives the mapper more training points.

One implementation note: the comment at the top of `CalibrationActivity.kt` still mentions 9-point calibration, but the actual code uses a 16-point 4x4 grid.

### Calibration Ground Truth Limitation

Calibration ground truth means the app knows where it placed the dot on screen. There is no independent external eye tracker confirming that the user truly looked at the dot.

The calibration assumes:

- the user looks directly at each target
- the user keeps their head and phone position reasonably stable
- lighting is good enough for reliable face and iris landmarks
- MediaPipe landmarks remain stable during the sample window

The app guards against no-face or too-few-sample cases, but it cannot detect incorrect fixation by itself.

## How Calibration Is Used

The calibration data is loaded by `GazeMapper`.

`GazeMapper` fits a second-degree polynomial that maps gaze features to screen pixels:

```text
screen_x = a0 + a1*zx + a2*zy + a3*zx^2 + a4*zy^2 + a5*zx*zy
screen_y = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

The mapper uses:

- standardized gaze inputs
- clamping to the calibration feature range
- ridge regularization with `lambda = 1.0`

This reduces extreme output jumps when the live gaze feature moves slightly outside the calibration range.

### Stage 3 Benchmark Status

The Stage 3 benchmark uses a separate 3x3 grid from the calibration grid. Calibration uses 10%, 36.67%, 63.33%, and 90%. Benchmarking uses 20%, 50%, and 80%.

This matters because the benchmark tests interpolation. It does not merely check whether the mapper can reproduce the calibration points it already trained on.

Recorded pre-16-point results:

| Run | Median Error | Vertical Median Error |
| --- | --- | --- |
| R1 | 259 px / 1.69 cm | 259 px / 1.69 cm |
| R2 | 345 px / 2.27 cm | 315 px / 2.06 cm |

Recorded 16-point plus ridge results:

| Run | Result |
| --- | --- |
| R1 | overall 316 px / 2.08 cm; vertical only 92 px / 0.60 cm |
| R2 | vertical 352 px / 2.30 cm |

Interpretation:

- vertical accuracy can be usable in a good run
- horizontal accuracy is weak and systematically shifted
- run-to-run instability is the blocker
- 16-point calibration improved vertical behavior in one run but did not make the tracker reliable

The line-level pass condition is:

```text
median vertical gaze error <= one text-line height
```

The project still needs the planned reading font size and line height before this can be judged numerically for the final reading UI.

## Stage 4: Overlay on the NewsMead Reading Interface

Stage 4 is not implemented yet.

The purpose of Stage 4 is to connect live gaze coordinates to the reading interface.

Before Stage 4, the tracker only produces screen coordinates:

```text
gaze_x = 420
gaze_y = 930
```

Stage 4 should turn those coordinates into a reading line:

```text
current_line_index = 12
```

To do this, the app must know where each visible line of article text is on the screen.

If NewsMead renders article text with native Android views, Stage 4 can use native layout information such as:

```text
view.getLocationOnScreen()
text layout line bounds
```

If NewsMead renders article text inside a WebView, Stage 4 likely needs JavaScript to query text or element rectangles and read the scroll offset:

```text
element.getBoundingClientRect()
window.scrollY
```

The important job is to compare the live gaze y-coordinate against the vertical band of each visible text line.

Stage 4 must also be scroll-aware. After the user scrolls, the same screen y-coordinate may correspond to a different article line. The line mapping has to update correctly after every scroll.

Expected Stage 4 output:

```text
timestamp
gaze_x
gaze_y
current_line_index
```

The debug gaze-dot overlay should remain available so developers can visually compare the estimated gaze point with the detected line.

Stage 4 should not depend directly on MediaPipe, `CalibrationActivity`, `GazeMapper`, or any other tracker-specific implementation. It should consume only the gaze coordinate stream exposed by `GazeProvider`.

This keeps Stage 4 compatible with:

- the current MediaPipe plus polynomial tracker
- a future GazeFollower-over-WiFi backend
- a future L2CS-Net TFLite backend
- a touch-validation provider

### Stage 4 Acceptance Test

Stage 4 passes when:

- the app logs the current article line index while reading
- the logged line usually matches the line the user is actually reading
- scrolling does not break the mapping
- the mapping depends only on the `GazeProvider` interface, not directly on MediaPipe

### Touch-Validation Requirement

Stage 4 should be built with touch-validation first.

In touch-validation mode, a finger tap or drag supplies the same kind of coordinate that gaze would normally supply:

```text
touch_x, touch_y -> current_line_index
```

If touch input maps to the correct article line nearly 100% of the time, then the line-bounding-box and scroll logic are working. Any remaining error after switching to gaze input belongs to the gaze tracker, not the reading interface.

## Stage 5: Feed Into the RSI Pipeline

Stage 5 is not implemented yet.

The purpose of Stage 5 is to convert the current reading line from Stage 4 into the signals needed by the RSI pipeline.

Stage 5 should produce:

- current line index
- fixation events
- dwell time
- regression events

A fixation means the user's gaze stayed on the same line or region long enough to count as meaningful attention.

Dwell time means how long the user stayed on a line or region.

A regression means the user moved from a later line back to an earlier line. For example:

```text
line 10 -> line 11 -> line 12 -> line 8
```

The jump from line 12 back to line 8 should be treated as a regression.

Stage 5 should not implement saccade velocity, microsaccade features, or other fine eye-movement metrics. The current phone camera and MediaPipe pipeline are not reliable enough for those measurements. The RSI pipeline should only consume line index, fixation, dwell, and regression signals.

Stage 5 should consume the line-index stream from Stage 4. It should not consume raw gaze coordinates unless needed for debugging or validation.

Expected Stage 5 output:

```text
timestamp
current_line_index
fixation_start
fixation_end
dwell_ms
regression_detected
```

### Stage 5 Acceptance Test

Stage 5 passes when:

- the RSI module receives a live stream of line-level reading signals
- normal reading produces mostly forward line progression
- intentionally re-reading an earlier line creates a regression event
- the RSI pipeline does not depend directly on MediaPipe or calibration internals

## Data Boundary

The required dependency direction is:

```text
Tracker backend
-> GazeProvider
-> Stage 4 line mapper
-> Stage 5 fixation/dwell/regression detector
-> RSI pipeline
```

Stages 4 and 5 must not reach backward into MediaPipe, calibration CSV parsing, or polynomial coefficients.

The current `GazeProvider` shape is:

```text
setOnGaze(listener)
start(owner)
stop()
```

Only this boundary should be shared with downstream reading and RSI code.

## Recommended Implementation Order

The safest implementation order is:

1. Define the article line-height and line-bounding-box strategy.
2. Build Stage 4 with touch-validation first.
3. Use touch input as a fake gaze coordinate to prove line mapping.
4. Add real `GazeProvider` input after line mapping works.
5. Build Stage 5 fixation, dwell, and regression events from the line-index stream.
6. Feed those events into RSI.

Touch-validation is important because it separates downstream logic from gaze accuracy. If touch input maps to the correct line nearly 100% of the time, then Stage 4 and Stage 5 are working. Any remaining error is from the gaze tracker, not the reading interface or RSI integration.

## Current Status

Current project status:

- Stage 1: implemented and passed on Galaxy A56 at about 30 fps in good lighting.
- Stage 2: implemented, passed, and upgraded to 16-point calibration.
- Stage 3: implemented and benchmarked.
- Stage 4: not implemented.
- Stage 5: not implemented.

The current blocker is Stage 3 accuracy instability. The MediaPipe plus polynomial approach can sometimes produce usable vertical accuracy, but results are inconsistent across runs.

Per the current progress log:

```text
Option 2 polynomial tuning budget spent.
Instability is inherent to single-camera appearance gaze on a handheld.
Per Stage 3 gate: STOP. Decision pending.
```

The practical next step is to build Stage 4 using touch-validation first while keeping the gaze backend swappable.

# Gaze Implementation Guide

This document explains the gaze-tracking implementation terms used in the NewsMead prototype.

## Short Version

MediaPipe is the landmark detector. It does not directly decide which line the reader is looking at.

The app uses MediaPipe to find face, eye, and iris landmarks from the phone front camera. NewsMead then turns those landmarks into raw gaze features, calibrates those features against screen points, maps them to phone-screen pixels, and feeds the result into line mapping and RSI.

```text
CameraX front camera
    -> MediaPipe FaceLandmarker
    -> iris/eye landmarks
    -> raw gaze feature: gaze_x, gaze_y
    -> 16-point calibration
    -> GazeMapper polynomial model
    -> screen coordinate: x, y
    -> GazeProvider.onGaze(x, y)
    -> line mapping / RSI logic
```

## MediaPipe

MediaPipe is Google's computer-vision framework. In this project, the Android app uses the MediaPipe FaceLandmarker task through:

```text
com.google.mediapipe:tasks-vision
```

The model file is stored at:

```text
app/src/main/assets/face_landmarker.task
```

MediaPipe's role is limited to detecting landmarks. The app's screen-coordinate gaze estimate is produced by project code, not by MediaPipe itself.

Main file:

```text
app/src/main/java/com/newsmead/gaze/MediaPipeRawGazeSource.kt
```

## Raw Gaze Features

The raw tracker emits:

```text
gaze_x, gaze_y, timestampMs
```

These are uncalibrated eye-relative features, not screen pixels. They describe where the iris appears relative to the eye landmarks.

Boundary interface:

```text
app/src/main/java/com/newsmead/gaze/LocalRawGazeSource.kt
```

This interface also exposes optional diagnostic callbacks:

- FPS, so tracker speed can be checked.
- Blink stats, so dropped frames and false blink filtering can be diagnosed.

## Calibration

Calibration connects raw gaze features to screen positions.

The calibration activity shows a sequence of screen targets. For each target, the user looks at the dot while the app collects raw gaze samples. The saved calibration pair is:

```text
screen_x, screen_y, gaze_x, gaze_y
```

Current calibration uses 16 points.

Main files:

```text
app/src/main/java/com/newsmead/activities/GazeCalibrationActivity.kt
app/src/main/java/com/newsmead/gaze/CalibrationStore.kt
app/src/main/java/com/newsmead/gaze/CalibrationView.kt
```

## GazeMapper

`GazeMapper` converts calibrated raw gaze features into phone-screen pixels.

It fits a second-degree polynomial:

```text
screen = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

Before mapping, live gaze features are:

- clamped to the calibration feature range
- standardized using the calibration mean and standard deviation
- mapped separately to screen `x` and `y`

Main file:

```text
app/src/main/java/com/newsmead/gaze/GazeMapper.kt
```

## GazeProvider

`GazeProvider` is the most important architecture boundary.

All downstream code receives gaze as:

```kotlin
GazeProvider.OnGaze { x, y -> ... }
```

The coordinates are smoothed full-screen phone pixels. Article line mapping and RSI should depend only on `GazeProvider`, not directly on MediaPipe.

This keeps the tracker replaceable. A different gaze backend can be introduced later without rewriting article line mapping or RSI logic.

Main file:

```text
app/src/main/java/com/newsmead/gaze/GazeProvider.kt
```

Current local implementation:

```text
app/src/main/java/com/newsmead/gaze/LocalCalibratedGazeProvider.kt
```

`LocalCalibratedGazeProvider`:

- receives raw `gaze_x,gaze_y`
- applies median filtering
- applies One Euro smoothing
- maps features through `GazeMapper`
- emits screen pixels through `GazeProvider`

## Line AOI Mapping

AOI means area of interest. In this project, the main AOI is the current article line.

The line mapper takes a full-screen gaze `y` coordinate and resolves it to the corresponding line in the native Android `TextView`.

Main file:

```text
app/src/main/java/com/newsmead/gaze/LineAoiMapper.kt
```

Expected log shape:

```text
GazeAOI: gaze=(520,1040) line=10/76
```

## RSI / GazeRSI

RSI means Reading State Index. It is a reading-effort signal derived from the line stream.

It is not a comprehension score.

The inferencer looks for:

- fixation: gaze stays on one line long enough
- dwell: time spent on a line before leaving it
- regression: gaze moves from a later line back to an earlier line

Main file:

```text
app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt
```

More detail:

```text
RSI_README.md
```

## Debug Views

The app has debug overlays for validating gaze behavior:

```text
app/src/main/java/com/newsmead/gaze/GazeOverlayView.kt
app/src/main/java/com/newsmead/gaze/GazeDotView.kt
```

These show the estimated gaze point and, during testing, target points.

## Important Accuracy Caveats

Single-phone front-camera gaze is sensitive to:

- head pose
- posture drift after calibration
- lighting
- glasses
- face visibility
- phone position
- calibration quality

The safest interpretation is line-level, not word-level. Treat sudden jumps, large regression counts, and off-screen points as possible tracking or calibration issues before interpreting them as reading behavior.

## Development Rule

Keep downstream code behind this boundary:

```text
GazeProvider.onGaze(x, y)
```

Do not make article mapping, RSI, or study logging depend directly on MediaPipe-specific classes. MediaPipe is an implementation detail of the current local tracker.

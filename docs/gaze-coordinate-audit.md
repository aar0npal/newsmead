# Eye-measurement and coordinate audit — 2026-09-03

## Outcome

**Fix the calibration-to-screen coordinate contract before changing eye features.**
The calibration view is physically inset 101 px from the screen top on the A56,
but its local target coordinates are saved as if their origin were the screen
top. The reading provider and AOI/overlay consumers expect full-screen pixels.
This is a confirmed coordinate-space defect, not a correction fitted to this
participant's accuracy results. Its effect is a missing translation, not a
complete explanation for compression or regional gaze errors.

This turn audited code and existing evidence. After explicit permission, the
calibration screen was opened only to inspect its bounds, then closed without
pressing Start. No calibration/test was collected, saved, or overwritten. No
Android source, estimator, mapper, filters, target timing/order, build, or install
was changed. Nothing was staged or committed.

## Confirmed device evidence

The installed app targets SDK 34. Read-only window/view inspection on the A56
(Android 16) produced:

```text
Physical display: 1080 x 2340
GazeCalibrationActivity window frame: [0,101][1080,2340]
Root FrameLayout:                     0,0-1080,2239
CalibrationView inside that root:     0,0-1080,2239
```

The 11 existing eye-local calibration logs all record `screen_px: [1080,2239]`:
that is the calibration **view** size, not the physical display size. The live
inspection confirms that this layout's view origin is screen `(0,101)`.
Historical logs do not record the origin itself, so do not silently rewrite old
files using an assumed offset, even though their dimensions match this layout.

For the inspected layout, the center target is drawn at local y=1119.5 and thus
screen y=1220.5. Calibration saves y=1119.5. An otherwise perfect mapper trained
on these targets returns a coordinate 101 px too high when used as screen y.

After closing the inspected activity, only MainActivity remained among the app's
windows, with frame `[0,0][1080,2340]`. Its origin differs from the immersive
calibration window. Different view origins within that main window are handled
by the existing `getLocationOnScreen()` consumers.

Data preservation check after closing:

- Saved calibration: 16 points, float32-equal to the FIT entries of
  `calibration_session_20260903_210045.json`.
- Feature sidecar remains `eye_local_width_average_v1`.
- Latest calibration filename remains `calibration_session_20260903_210045.json`.
- Saved CSV SHA256:
  `cdc9d4f91f782dece7819a63907d713068d514e33834e2942bb5abe4172728ed`.
- No new calibration session was created. Camera activation was limited to the
  user-approved idle-screen inspection; no camera frames were retained.

## Code trace

1. `GazeCalibrationActivity.computePoints()` creates points from the calibration
   view's width/height. `CalibrationPointCollector.capture()` draws these in
   local `CalibrationView` canvas coordinates.
2. `recordSuccess()` stores `pres.point.x/y` directly into
   `CalibrationSample.screenX/screenY`. It never adds the view's on-screen origin.
   The JSON logging and held-out validation likewise use the local values.
3. `CalibrationStore` writes/loads those values unchanged. Its sidecar validates
   raw-feature mode, but has no coordinate-space version or origin metadata.
4. `LocalCalibratedGazeProvider` returns mapper output unchanged except for an
   optional affine correction, and documents its output as full-screen pixels.
5. `LineAoiMapper` subtracts the text's on-screen location; `GazeOverlayView`
   subtracts its own on-screen location. These expect genuine screen coordinates,
   not the calibration view's local coordinates.
6. The nine-point test also generates and scores view-local targets. This can
   mask the calibration-origin error when both screens share the same inset.
   It must be converted consistently with the calibration fix, including its
   affine observations and displayed gaze point.

Hiding system bars is not proof that a window begins at the screen origin. With
this app's default cutout policy, the observed immersive window is inset. Android
documents the distinction between window and screen coordinates in immersive
cutout layouts and the need to convert between them. See
[Android display-cutout guidance](https://developer.android.com/develop/ui/views/layout/display-cutout#best-practices).

Additional contract risk: `getGlobalVisibleRect()` returns a rectangle in the
root view's coordinate space, while `getLocationOnScreen()` returns screen
coordinates. `LineAoiMapper`, the scaffold overlay's clipping, and reading
reference placement mix these APIs. They coincide when the root is at `(0,0)`
(the observed MainActivity case), but not for an inset root. Normalize these
rectangles explicitly during the same coordinate-contract change rather than
claiming a second measured 101-px reading error. ReadingValidationActivity's
window origin was not separately inspected in this turn. See
[Android View API](https://developer.android.com/reference/android/view/View#getGlobalVisibleRect(android.graphics.Rect)).

## What the upstream audit found

- Calibration and reading both construct the same `LocalGazeSources.create()`
  source with `EYE_LOCAL_WIDTH_AVERAGE_V1`; there is no screen-specific raw
  estimator, camera resolution, rotation, or feature scaling branch.
- The source uses the same reusable rotation/mirror path. Retained debug startup
  entries around the latest pair show GPU and 640x480, rotation 270, each time.
  No alternate CPU or camera-size path is evidenced there.
- Eye-local geometry converts normalized landmarks to transformed-image pixel
  coordinates first, so the width/height aspect ratio is accounted for. Its
  vertical feature is `0.5 + perpendicular iris displacement / eye-corner width`.
  Both eyes are then averaged. The old eyelid-gap fraction is not active.
- Calibration robustly aggregates raw features (120-ms lead-in trim, MAD
  rejection, median). Reading applies the existing seven-sample median and
  One Euro filters continuously. These are different processing paths, but not
  different eye-feature definitions.
- In all 11 calibrations, row-median vertical features increase from top to
  bottom in both eyes separately as well as their average. The averaged
  top-to-bottom span is approximately 0.0526–0.0671 feature units. There is no
  evidence here for one consistently flat or sign-reversed eye to discard.
  This does not prove equal eye quality during reading.
- The geometry's translation, uniform-scale, and in-plane-roll invariance does
  not establish invariance to 3-D head pitch/yaw or landmark deformation.
  Eye widths, projected iris numerators, and per-eye live readings are not in
  the reading JSONL, so those mechanisms cannot be separated retrospectively.
  Do not turn a face-position correlation from fixed row-major calibration into
  a fitted compensation coefficient.

The older v3 sparse raw debug trace is no longer present in local files or the
remaining phone log buffer; its earlier analysis is documented in E-046, not
reconstructed anew here. Existing full reading JSONL is still preserved.

The remaining debug buffer supplies 59 measured probes for `vertical_off_4` and
61 for `vertical_on_4`, covering all 14 checkpoints in each. Probes were matched
to existing reading samples by nearest callback time within 100 ms and agreement
within 1 px with the rounded, uncorrected `GazeMap` x/y; no in-session probe
failed that match. Each checkpoint has only 2–7 probes, logged every 15 emitted
samples with raw features rounded to four decimals. These are not full traces.

Raw and filtered vertical medians are generally close, but individual differences
reach 0.0055 OFF and 0.0091 ON; it would be unjustified to rule out all filter
effects. Example: OFF `line_reading_3` raw/filtered medians are 0.46050/0.46055,
and ON `line_reading_3` 0.46600/0.46620. Their absolute raw values differ between
runs before any reading correction. Sparse filtering evidence cannot identify
head pose, eye visibility, or landmark bias as the cause.

Reference reading source hashes (raw payloads read in memory only):

- `reading_validation_session_20260903_210221_564.jsonl`:
  `09f28472f6e962c03159a801fa3109e3a2e8032b3616c4ce4d3db09d061b4362`.
- `reading_validation_session_20260903_210447_787.jsonl`:
  `29f3481ab17c5bbebabe03343cbe605cb2371cc6b3590a55166f49769c94688a`.

## Interpretation limits

The coordinate defect is deterministic. Its repair must use the measured origin
on each device/layout, **not** a hard-coded `+101`, a fitted reading offset, or a
new mapper. It changes the meaning of stored targets, not the eye estimator.

On this layout 101 px is about 0.76 of the replay's actual 133-px reading line
pitch. That is meaningful for line assignment, but much smaller than some
observed regional errors. A constant missing origin cannot by itself explain
opposite top/bottom errors or the failure of every reference-derived correction.
Correct coordinates can expose previously compensating estimator errors; do not
promise every score will improve.

Earlier dot-grid results remain evidence about their **local-space** pipeline.
Reading scores remain records of the app actually tested, now with this defect
documented. Do not discard runs, claim prior corrections now work, or silently
relabel their coordinates. E-051's within-calibration candidate comparison remains
informative: its baseline and candidate were evaluated in the same target space.
The saved aggregates alone do not prove a physical eye-range collapse; different
target positions, the missing origin, and reading task behavior must be accounted
for before comparing calibration-row ranges directly with reading features.

## Repair scope approved after the audit

1. Keep target drawing/sequence unchanged. At each calibration presentation,
   convert its local target to actual screen coordinates using the calibration
   view's measured on-screen position. Use those screen targets consistently for
   stored fit pairs, held-out scoring, and logs.
2. Apply the same explicit contract to the nine-point test and affine correction
   observations. Convert full-screen gaze back to local space only for drawing.
3. Add coordinate-space/version metadata and enough layout evidence to audit the
   conversion. Do not reuse legacy local-space calibrations silently or guess
   their offsets; require a normal fresh calibration after the fix. Preserve
   historical source files and label the compatibility change clearly.
4. Convert visible rectangles into the same screen coordinate space before AOI
   clipping, overlay clipping, or reference placement. Test nonzero x/y origins,
   scrolling, and zero-origin layouts; do not assume status-bar height equals
   every view's origin.
5. Verify with automated coordinate tests and a layout-only device check first.
   Then use the existing OFF known-target reading protocol after a fresh
   calibration. Do not invent a new accuracy protocol or combine an eye-feature,
   mapper, filter, camera, or post-map correction change with this repair.

The user subsequently approved this scope. The repair is implemented with measured-origin
conversion, content-bound calibration/drift metadata, explicit legacy rejection and layout
evidence in logs. The audit findings above describe the pre-fix application and remain intact.
Build and layout-only verification are complete (E-053): 67 gaze JVM tests and four Android
tests pass; the final debug app is installed. The calibration view still measures `(0,101)`
with size `1080x2239`; its center now converts to screen `(540,1220.5)`. Reading's actual root
is `(0,0)`. A test-only inset window at `(91,272)` confirms scrolled line/word AOI clipping and
screen-to-local dot rendering; no participant calibration or test was started. The first
layout attempt was blocked by the lock screen; the unlocked rerun passed. All 105 existing
gaze/calibration files match their pre-install hashes, including the saved CSV recorded above.
The legacy warning was verified without starting measurement. Next is the user-performed
fresh calibration and existing OFF reading test; implementation is not accuracy evidence.

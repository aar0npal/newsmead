# Isolated MGazeNet input-only check checkpoint — 2026-09-07

Status: the input-only mode proposed by `gaze-mgazenet-protocol-review.md`
is implemented and host-verified in the separate
`com.newsmead.mgazenetbenchmark` app. On 2026-09-08 two authorized SM-G991B
attempts exposed successive manifestations of a bitmap-ownership defect; both
are retained as incomplete records. The complete ownership repair then passed
its exact camera-free test and a full 20-second input check on both phones. No
personal calibration was created and no active NewsMead tracker source or
behavior changed. The user continues to handle staging and commits.

## Implemented boundary

- The benchmark launcher can open a separate input-check setup. Opening either
  screen keeps the camera off and creates no record. A nonempty run label and an
  explicit Start action are required before requesting camera permission.
- Camera/model/localizer initialization has a 20-second deadline. Readiness
  starts a separate fixed 20-second observation window; initialization time is
  not silently counted as observation time.
- The mode reuses `AccuracyCameraSource`, including its CameraX resolution,
  front-camera selection, REALTIME clock requirement, Tasks localizer settings,
  pinned MGazeNet path and normal-precision four-thread inference. It presents
  no target or calibration grid, never calls SVR fitting and emits no gaze
  coordinate.
- The source exposes transient crop dimensions with its existing result. The
  input session reads only timing, reason, eye areas and crop dimensions; the
  Activity immediately erases the transient 258-value feature buffer. It saves
  no images, crops, landmarks, features, personal model or calibration.
- The complete schema is `mgazenet_input_check_v1`; stopped, backgrounded,
  timed-out and failed runs use `mgazenet_input_check_partial_v1`. Records are
  written under the benchmark app's separate `files/input-check/<session>/`
  directory and cannot access the NewsMead calibration store.

The report binds the existing pipeline metadata and hashes and records the
declared camera clock, source shape/rotation, delegate, eligible/no-face/
invalid-crop/eye-area/invalid-feature counts, eye-area and face/eye crop-size
summaries, emitted-result age, analyzer arrivals and busy drops. Planned start,
end and duration remain distinct from observed and unobserved duration.
Per-result capture/output offsets plus initial, inter-callback and final gaps
preserve where the planned window had no callback without selecting a freshness
threshold. Human blinks and target compliance are explicitly unverified.

## Camera-free verification

- All 137 benchmark JVM tests pass with zero failures, errors or skips. The seven
  new tests cover independent initialization/observation windows, aggregate-only
  retention, initialization failure, interrupted planned-time accounting,
  callback gaps, nonmonotonic clock rejection and resource-close failure.
- All 39 independent Python evidence/integrity tests pass, including two new
  collector checks for report identity, hashes, retention and calibration
  boundaries.
- The benchmark debug APK and instrumentation APK both assemble. The new Android
  setup test compiles and verifies that merely opening input-check setup keeps
  the keep-screen flag clear and creates no input record; it was not executed on
  a phone in this checkpoint. The separate recycled-buffer recovery test passed
  on the SM-G991B in 0.049 seconds and A56 in 0.043 seconds without opening a
  camera.
- Initial benchmark APK SHA-256:
  `2aa320d4f727bb9cb3570baffd6a90fae03709ea4182c686ae8372e50f0705ec`.
  First repaired APK SHA-256:
  `75bfff6df2a1bcf293861270c5ebc679401b8a7e3a6649522b67cf5c990ad453`.
  Complete ownership-repair APK SHA-256:
  `434fbdce0beaad037e6b8e8ae3846eecc1e1ff9590c86dfe89826ed5b5faa8c1`.
  Current instrumentation APK SHA-256:
  `baa286f24d80b31414e4b1922b45cfa576940421878a57d946883445c5afdbdf`.

The active application module and gaze tracker remain unchanged. Existing
uncommitted documentation, benchmark work and diagnostic evidence were
preserved; nothing was staged or committed.

## First authorized SM-G991B attempt and repair

The initial APK replaced the prior benchmark package with app data preserved;
its installed hash matched `2aa320...`. The input-only setup opened with the
camera still inactive. After the explicit labeled Start, the source verified
the REALTIME clock and reported the intended 640×480 source, 270-degree
rotation, 480×640 upright image, GPU Tasks localizer, pinned model and APK
hash. It then failed after 79.4 ms of the planned observation window with:

`java.lang.IllegalStateException: Can't call getPixels() on a recycled bitmap`

The partial record retained zero emitted results, two analyzer arrivals, one
busy drop, the complete 20-second plan and 19,920.6 ms unobserved duration. It
states that no images, crops, landmarks, features, model, calibration or gaze
coordinates were retained. The record and matching SHA-256 sidecar are under
`diagnostics-local/2026-09-08/mgazenet-input-check/sm-g991b/failed-v1-recycled-bitmap/`;
SHA-256 is
`b06726799240f0f22ba03f97894c323a2382a3e6693134f3e9f93c5d3b7e7608`.
The failed attempt was not deleted or converted into a completed check. The
benchmark was force-stopped afterward, its camera permission was revoked, and
it no longer has a running process.

The cause was a source ownership error: `AccuracyCameraSource` closed the
MediaPipe image wrapper before the same in-memory bitmap's pixels were read for
MGazeNet. The first repair kept the wrapper alive until localization, crop
creation and MGazeNet preprocessing/inference finished, then closed it in
`finally`.

That repair was installed with matching APK hash and explicitly started once.
It emitted one `no_face` result, then retained a second incomplete record after
73.7 ms with `Canvas: trying to use a recycled bitmap`. The wrapper had recycled
the reusable upright bitmap at the end of the first frame, and `CameraFrames`
attempted to draw the next frame into that same object. The record retains the
full plan, one 176.3 ms-age result, four analyzer arrivals, two busy drops and
19,926.3 ms unobserved duration. Its local directory is
`diagnostics-local/2026-09-08/mgazenet-input-check/sm-g991b/failed-v2-recycled-canvas/`;
SHA-256 is
`b61cf97ff611b316ec8e5f0162fb3688f2631ab8cbe08e4d7cb014c31eee70a5`.
The app was again force-stopped and camera permission revoked.

The complete repair makes `CameraFrames` detect an externally recycled upright
bitmap and create a new one before drawing the next frame. A new camera-free
instrumentation test recycles the returned bitmap deliberately, then verifies
that the next frame uses a live replacement with correct RGB values. That exact
test passed on the SM-G991B before the third camera attempt. No model, localizer,
crop, calibration, timing or retention setting changed. `collect_input_check.py`
retrieves only a named numeric record and verifies its device-side hash,
identity and no-retention/no-calibration contract.

## Completed SM-G991B input check

The third APK was installed with app data preserved and its installed SHA-256
matched `434fbd...`. The explicitly labeled input check completed the full
20,000 ms planned and observed window with zero unobserved planned duration.
It verified the REALTIME clock, stable 640×480 source with 270-degree rotation,
480×640 upright geometry, GPU Tasks localizer, pinned model and pipeline hashes.

The check recorded 304 analyzer arrivals, 158 observed busy drops and 145
emitted results: 142 eligible, two `no_face`, one eye-area rejection, zero
invalid crops and zero invalid features. Emitted-result age was 143.5 ms minimum,
220.2 ms median, 258.9 ms nearest-rank P95 and 341.6 ms maximum. Inter-callback
gaps were 134.9 ms median, 187.6 ms P95 and 275.1 ms maximum. These operational
timings include the deliberately retained normal-precision full candidate path;
they are not gaze-accuracy measurements and are not offset by a framerate score.

The 143 crop-valid results had median left/right pixel-polygon eye areas of
170.5/106.0 px². The right-eye minimum was 9.5 px², accounting for the one
unchanged source-rule rejection. Median face crop size was 183×184 px; median
left/right eye crops were 49×36 and 46×34 px. These are descriptive diagnostics
for this single check, not validated blink labels, target compliance or
population ranges.

The integrity-checked record is under
`diagnostics-local/2026-09-08/mgazenet-input-check/sm-g991b/completed-v3-ownership-repair/`;
SHA-256 is
`7ccc0635c0312bbe3be0733a38aad8e89f2c12e71b4d0fb1f5cbfc86960ed08b`.
Both preceding failures remain alongside it. The benchmark was force-stopped
after collection, camera permission was revoked and no process remains.

## Completed A56 input check

The same third APK and instrumentation package were installed on the A56 with
app data preserved; the prior benchmark APK was `272ac2...` and the replacement
installed hash matched `434fbd...`. Android displayed its compatibility notice
that the debug app is not 16 KB page-size compatible, naming the bundled MNN,
MediaPipe, OpenCV, image-processing and C++ native libraries. The warning did
not prevent this check, but it is a deployment requirement to resolve rather
than an accuracy or timing result.

After the exact recycled-buffer test passed camera-free, the explicitly labeled
input check completed the full 20,000 ms planned and observed window with zero
unobserved planned duration. It verified the same REALTIME clock, 640×480 source,
270-degree rotation, 480×640 upright geometry, GPU Tasks localizer, pinned model
and pipeline hashes.

The check recorded 375 analyzer arrivals, 229 observed busy drops and 145
emitted results: 142 eligible, zero `no_face`, three eye-area rejections, zero
invalid crops and zero invalid features. Emitted-result age was 172.6 ms minimum,
218.7 ms median, 243.3 ms P95 and 2,662.7 ms maximum. The maximum corresponds to
the first result and the initial callback gap was 2,442.2 ms; this warm-up gap
remains part of the planned window. Later inter-callback gaps were 118.3 ms
median, 160.4 ms P95 and 179.5 ms maximum.

The 145 crop-valid results had median left/right eye areas of 200.0/123.0 px²;
minima of 7.0/1.0 px² occurred in the three unchanged source-rule rejections.
Median face crop size was 193×194 px; median left/right eye crops were 51×38 and
49×36 px. These are descriptive diagnostics, not validated blink labels or
population ranges.

The integrity-checked A56 record is under
`diagnostics-local/2026-09-08/mgazenet-input-check/a56/completed-v3-ownership-repair/`;
SHA-256 is
`daee9c23c9c4ddc48b3156baf9b36bff283bcf6de5bd427198aca761c3fd7916`.
The benchmark was force-stopped after collection, camera permission was revoked
and no process remains.

## Two-phone interpretation

Both phones supplied a supported clock, stable geometry and nonempty eligible
input for the same frozen build. Each emitted 145 results (7.25 Hz) and 142 were
eligible (97.9% of emitted results). This repeated count is descriptive, not an
independence or performance claim. The SM-G991B/A56 busy-drop fractions were
52.0%/61.1%; median ages were 220.2/218.7 ms and P95 ages 258.9/243.3 ms. The
A56's 2.66-second first-result age is a material warm-up/missing-interval finding.

The checks close the bounded two-phone input-readiness step. They do not measure
gaze position, calibration quality, line assignment, blink-classifier validity,
responsiveness to target changes or reading events. The output rate, drop rates,
roughly 220 ms median ages and A56 warm-up gap remain measurement-validity risks;
they cannot be compensated by a favorable later spatial score or silently
removed from planned-time denominators.

## Authorization boundary

The isolated input-only check is complete on both phones and does not need
repetition unless a relevant source change or failure justifies it. The v2
stationary pilot and analysis rules were subsequently frozen and host-verified;
continue at `gaze-mgazenet-stationary-v2.md`. Its next boundary is a separately
authorized camera-free setup/geometry check on both layouts, not a personal
trial. This checkpoint does not authorize calibration, accuracy collection,
natural-reading evaluation or tracker promotion. Both failed attempts remain
retained and cannot be excluded from the software-development history.

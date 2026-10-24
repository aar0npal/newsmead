# Build Spec — On-Device Android Gaze Tracker (MediaPipe + Polynomial Calibration)

## STATUS UPDATE — READ THIS FIRST

> **Current tracker (as of 2026-07-25): MediaPipe Face Landmarker running
> on-device, with 16-point polynomial calibration.** No laptop, no backend, no
> network. This reverses an earlier decision recorded in this file; the full
> history is below because it is the evidence trail for the thesis's
> "alternatives considered" section.

> **Downstream status (2026-07-20):** Stage 4 line/word AOI mapping, Stage 5
> RSI, and all four adaptive scaffold renderers are implemented. Historical
> checklists below describe the original acceptance path. See
> adaptive-visual-scaffolding.md for current behavior.

### Tracker decision history

| # | Date | Option | Outcome |
| --- | --- | --- | --- |
| 1 | Stage 3 | **MediaPipe + polynomial, on-device** (Option 2) | **Failed** initial validation: vertical error ~1.1–2.3 cm with large run-to-run swing, root-caused to head-pose sensitivity |
| 2 | Stage 3 | **MobileGaze → TFLite** (Option 3) | **Failed**: ~10° angular error, geometrically too large for the ~11° a phone screen subtends at reading distance |
| 3 | 2026-07-02 | **GazeFollower over WiFi** (Option 1) — Python backend on a co-located laptop | **Passed** (~1.1 cm). Ported, wired, and validated end-to-end on the A56 |
| 4 | 2026-07-06 | **Back to MediaPipe on-device** | **Current.** `implementation-spec.md` was revised to remove the backend/WiFi path entirely; `WiFiGazeProvider.kt` and `GazeStream.kt` were deleted and the gaze `INTERNET` permission removed |

Full Stage 1–3 investigation numbers remain in the gaze-prototype repo's
PROGRESS.md: `C:\Users\USER\OneDrive\Desktop\gaze-thesis-prototype`

### Why the return to on-device (step 4)

The operative constraint is deployment, not accuracy: GazeFollower requires a
co-located laptop and a live WiFi bridge for every session, which is impractical
and failure-prone for a field study with older-adult participants. The recorded
rationale in progress-notes.md is that the spec was revised to require Stage 4/5
to depend only on the `GazeProvider` boundary, with the gaze stack running
locally on the phone.

**This trade is explicit: deployability was chosen over the better raw accuracy
number.** Do not present it as an accuracy improvement.

### What has changed since the original MediaPipe failure

The Stage 3 failure was measured against an *unvalidated* calibration. Since
2026-07-24 the calibration subsystem has been rebuilt (see
docs/calibration-design.md): per-point MAD outlier rejection, exclusion of
unusable points from the fit, leave-one-out cross-validation, a 5-point held-out
validation pass, an in-session acceptance gate, gradient extrapolation replacing
the hard feature clamp, and a 9-point affine drift correction for mid-session
posture change. Much of the original run-to-run instability came from bad
calibrations being silently accepted; those are now caught before reading begins.

**Accuracy nonetheless remains the binding limitation.** Vertical error is the
constraint on line-level attribution, and word- and exact-line-level intervention
claims stay *conditional on pilot validation* — with a broader focus/region
scaffold as the documented fallback. Device accuracy figures in progress-notes.md
predate the calibration rebuild and must be re-measured.

### What this means for work in this repo

- **Stages 1-3 below are historical reference**, but their MediaPipe/CameraX/
  polynomial content is once again live architecture — it is implemented in
  `app/src/main/java/com/newsmead/gaze/`, not only in the prototype repo.
- The "no laptop, no network" framing in Stage 0 is **accurate again**.
- Stage 4 and Stage 5 are **implemented**; their instructions below describe the
  original acceptance path and are kept for the record.
- `GazeProvider` has never changed. Three different trackers have been swapped
  behind it without touching Stage 4-5 — the swappability requirement did its job.

---

**For:** Claude Code and Codex
**Project:** Gaze-driven adaptive reading interface (THS, DLSU CeHCI). Built on the NewsMead Android app (Kotlin).
**This module:** A gaze estimator — front camera → gaze tracker → on-screen gaze coordinates (x, y), delivered through `GazeProvider`. (Fully on-device with no laptop and no network, per the status update above. An intermediate GazeFollower-over-WiFi architecture was adopted and then removed; see the decision history.)

---

## 0. Read first — scope and principles

**Goal of this module:** produce a real-time stream of estimated on-screen gaze coordinates, accurate enough to tell *which line of text* the user is reading (line-level, not character-level).

**Hard principles:**
1. **Build in stages. Do not write all five stages at once.** Each stage has an acceptance test. Stage N+1 starts only after Stage N passes on a physical device. *(Stages 1-3 already completed — see status update.)*
2. **Validate the plumbing before trusting the gaze.** *(Already done for Stages 1-3; still applies to Stage 4-5 — validate AOI mapping and RSI feed before trusting them.)*
3. **This runs on a PHYSICAL DEVICE only.** The Android emulator has no usable front camera. Every acceptance test is run on a real phone.
4. **Keep the gaze output behind a single interface** (`GazeProvider` with one `onGaze(x, y)` callback). This is why three tracker swaps — to GazeFollower and back to on-device MediaPipe — required no changes to Stages 4-5. Swappability is a requirement, not a nice-to-have.

## Target devices
- James: Galaxy A56 5G, One UI 8.5 (Android 16)
- Groupmate: [To be filled]

Each teammate tests acceptance criteria independently on their own
device. If results diverge significantly between devices, this is
itself a finding — record it in docs/progress-notes.md rather than
silently picking one number.

**Tech baseline (current, NewsMead repo):** Kotlin, `GazeProvider` backed by an on-device pipeline — CameraX front camera + MediaPipe Face Landmarker (`tasks-vision`, 478 landmarks with iris refinement) + a self-contained polynomial calibration/mapping layer in `com.newsmead.gaze`. *(The GazeFollower WiFi bridge and Apache Commons Math dependency are no longer part of the gaze path; see the decision history above.)*

---

## Stage 1 — Camera + MediaPipe landmarks + Logcat output — [HISTORICAL, COMPLETE]

**Build:**
- A standalone Activity (separate from NewsMead for now) that opens the front camera with CameraX and runs the MediaPipe Face Landmarker on each frame in real time.
- Enable refined landmarks so iris points are included (Face Landmarker with `outputFaceBlendshapes` off, refine/iris on — the 478-landmark output that includes the iris).
- Extract the iris-center landmarks: left iris = landmark indices 468–472, right iris = 473–477. Average each eye's five points to get a single iris-center per eye, in **pixel coordinates** (denormalize: multiply normalized x by frame width, normalized y by frame height).
- Print to Logcat every frame: timestamp, left iris (x,y) px, right iris (x,y) px, and the current frame rate.

**Acceptance test (on physical device):**
- App runs, front camera preview visible, face detected.
- Logcat shows a steady stream of iris coordinates that **change sensibly** when you move your eyes/head (look left → x shifts; look down → y shifts).
- Frame rate logged is ≥ ~20 fps on the target device.
- No crash when the face leaves frame (should log "no face" and resume cleanly).

*(Passed. See gaze-prototype PROGRESS.md.)*

---

## Stage 2 — Calibration screen (collect landmark↔screen pairs) — [HISTORICAL, COMPLETE]

**Build:**
- A full-screen calibration Activity that displays one target dot at a time at known screen positions: a 3×3 grid (9 points) covering corners, edge midpoints, and center, in screen-pixel coordinates.
- For each dot: show it, wait ~800 ms for the eyes to settle, then collect iris-center samples for ~1 second. Store the *median* iris position over that window (median is more robust to blinks/noise than mean) paired with the dot's known screen (x, y).
- Output: a list of 9 calibration pairs: `(iris_x, iris_y) → (screen_x, screen_y)`. Persist them in memory (and optionally to a file for debugging).
- Show a simple progress indicator (e.g., "3 / 9").
- Include a **blink/no-face guard**: if no face is detected during a dot's collection window, pause and re-collect that dot rather than storing garbage.

**Acceptance test:**
- Calibration runs start to finish without crashing.
- All 9 pairs are collected and logged; the iris positions for the 9 dots are visibly *different from each other* and roughly ordered.

*(Passed initially; later revealed the raw iris-in-frame feature was too weak/unordered horizontally — see gaze-prototype PROGRESS.md for the eye-corner-relative feature fix and subsequent findings.)*

---

## Stage 3 — Fit the regression + real-time gaze coordinates — [HISTORICAL, SUPERSEDED]

**Build:**
- Fit a **second-degree polynomial regression**, separately for x and y, mapping iris position → screen position:
  - `screen_x = a0 + a1·ix + a2·iy + a3·ix² + a4·iy² + a5·ix·iy`
  - `screen_y = b0 + b1·ix + b2·iy + b3·ix² + b4·iy² + b5·ix·iy`
  - Solve each by least squares over the calibration pairs (Apache Commons Math `OLSMultipleLinearRegression` or equivalent). Store the coefficients.
- After calibration, in the live loop: take each frame's iris position, plug into the two polynomials, output an estimated on-screen gaze (x, y). Expose this through the single `GazeProvider.onGaze(x, y)` interface.
- Apply light temporal smoothing to reduce jitter before emitting the coordinate.

**Acceptance test:**
- Median error small enough that the dot stays within one text-line height at the planned reading font size and distance.

*(Result at the time: FAILED. Vertical error ~1.1–2.3cm with severe run-to-run instability, root-caused to head-pose sensitivity. Escalated to Option 3 (MobileGaze/TFLite), which also failed — angular error (~10°) geometrically too large for the ~11° angle a phone screen subtends at reading distance. Escalated to Option 1 (GazeFollower over WiFi), which PASSED. Full numbers and diagnosis: gaze-prototype PROGRESS.md.)*

> **Superseded 2026-07-06.** This on-device path is the CURRENT tracker again — see
> the decision history at the top. The failure recorded above was measured against an
> unvalidated calibration; the calibration subsystem has since been rebuilt
> (docs/calibration-design.md). Accuracy remains the known limitation and the figures
> above must be re-measured, but the conclusion "do not use MediaPipe on-device" no
> longer reflects the architecture.

---

## Stage 3 Decision Gate — [RESOLVED]

*(This gate was exercised in full — see the Stage 3 result above. Outcome at the time: escalated past both on-device options to GazeFollower over WiFi, which passed. The decision was later reversed on deployability grounds (2026-07-06) and the tracker returned to on-device MediaPipe; see the decision history at the top. `GazeProvider` was unchanged throughout all three swaps; only its implementation changed. Kept for the record of what the gate looked like.)*

---

## Stage 4 — Overlay on the NewsMead reading interface — [COMPLETE]

**Build:**
- Integrate the `GazeProvider` (now backed by the on-device MediaPipe pipeline) into NewsMead's article reading screen. *(Resolved: the article body is a native `TextView`, so line boxes come from the `Layout` API — see progress-notes.md 2026-07-02.)*
- Obtain the on-screen bounding box of each text line:
  - If native views: use `view.getLocationOnScreen()` / layout bounds per line.
  - If WebView: inject JS to query line/element rects and read `window.scrollY` for scroll offset.
- Map the live gaze (x, y) to the current line by testing which line's vertical band contains the gaze-y (adjusted for scroll offset). Log the current line index.
- Keep the gaze-dot overlay available as a debug toggle.
- **Font size must be fixed before this can be finalized** — the study font size is not yet locked (check the article view's large-text control's actual point size; see StudyConfig.kt). Line bounding boxes are only stable if font size doesn't change during a session.

**Acceptance test:**
- While reading the NewsMead article view, the logged "current line index" matches the line you are actually reading most of the time as you move down the passage.
- Scrolling the article updates the mapping correctly (gaze-to-line stays right after a scroll).

---

## Stage 5 — Feed into the RSI pipeline — [COMPLETE]

> Implemented in `ReadingStateInferencer.kt` (line index, fixation, dwell,
> regression events and the cumulative Session RSI) and
> `WindowedStabilityEstimator.kt` (recent Adaptive Instability Index driving the
> scaffold controller). See docs/reading-stability-index.md and
> docs/manuscript-implementation-alignment.md. The build instructions below are
> the original spec, kept for the record.

**Build:**
- Expose the per-frame output the RSI needs: current line index, fixation events (derived from gaze dwelling within a line/region), and regressions (gaze moving from a later line back to an earlier one).
- Hand these to the existing RSI computation via a clean interface. Do **not** implement saccade-velocity or microsaccade features — they are not measurable at consumer camera frame rates; the RSI consumes fixation, dwell, and regression signals only.

**Acceptance test:**
- The RSI module receives a live feed of line index + fixation/dwell/regression events while reading.
- Sanity check: reading normally produces mostly forward progression; deliberately re-reading an earlier line registers as a regression event.

---

## Cross-cutting requirements

- **Swappability:** all gaze output flows through one `GazeProvider` interface. Stages 4–5 depend only on that interface, never on the underlying tracker directly — this is why swapping to GazeFollower and back to on-device MediaPipe required no changes here.
- **Touch-validation mode:** add a debug mode where a finger touch substitutes for the gaze coordinate. If touch input drives correct line-mapping and RSI events at ~100%, the downstream pipeline is proven correct, and any failure is isolated to the gaze tracker, not the integration.
- **Drift handling (note for later):** accuracy may degrade over a long session as posture shifts. Log a timestamp with each gaze sample so drift can be measured later.
- **Performance caveat:** if the device drops below ~20 fps end-to-end, profile before adding more features.
- **Older-adult testing:** the real accuracy unknown is glasses/presbyopia/lighting on 45–65-year-old faces. Test accuracy on a target-age face as early as possible, not only on a young developer's face. (This applies with more force after the return to on-device MediaPipe: all accuracy figures to date come from the dev team, none from target-age participants.)

---

## Deliverables checklist
- [x] Stage 1: camera + MediaPipe iris landmarks logging, ≥20 fps, tracks eye movement — *implemented in this repo (`MediaPipeRawGazeSource.kt`)*
- [x] Stage 2: calibration collecting clean landmark↔screen pairs — *implemented as the 16-point flow (`GazeCalibrationActivity.kt`); rebuilt 2026-07-24, see docs/calibration-design.md*
- [x] Stage 3: tracker accuracy validated — *GazeFollower passed and was then removed on deployability grounds; on-device MediaPipe is current and its accuracy remains the known limitation. **Re-measure after the calibration rebuild.***
- [x] Stage 4: gaze-to-line mapping on NewsMead article view, scroll-aware — *complete*
- [x] Stage 5: fixation/dwell/regression feed into RSI — *complete*
- [x] Touch-validation mode working — *`StudyConfig.GAZE_TOUCH_VALIDATION`*
- [ ] Accuracy number recorded for the CURRENT tracker + rebuilt calibration — *outstanding; prior figures predate the rebuild*
- [ ] Accuracy validated on a target-age (45–65) face — *outstanding*

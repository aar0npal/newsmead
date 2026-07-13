# Build Spec — On-Device Android Gaze Tracker (MediaPipe + Polynomial Calibration)

## STATUS UPDATE — READ THIS FIRST

Stages 1-3 are COMPLETE and their outcome has changed the tracker.
After Option 2 (MediaPipe + polynomial calibration) and Option 3
(MobileGaze converted to TFLite) both failed accuracy validation on
the target device, the team escalated to **GazeFollower** (Python
backend on a co-located laptop, WiFi bridge to the Android app) —
which passed validation. GazeFollower is the confirmed, final
tracker. Full investigation history and accuracy numbers are in the
gaze-prototype repo's PROGRESS.md: C:\Users\USER\OneDrive\Desktop\gaze-thesis-prototype

This means:
- Stages 1-3 below are historical reference only. Do not build
  MediaPipe/CameraX/polynomial regression code in this repo — that
  work is done, and it lives in the gaze-prototype repo, not here.
- The "no laptop, no network" framing in Stage 0's principles below
  is now OUTDATED — the final architecture does use a co-located
  laptop running GazeFollower over WiFi during sessions. The
  `GazeProvider` interface itself is unchanged; only its
  implementation changed.
- **Active work in THIS repo (NewsMead) starts at Stage 4.**
  `GazeProvider` (GazeFollower WiFi implementation) is being ported
  in and wired to the article reading screen. Stage 4 and Stage 5
  instructions below are still the accurate, active spec.

---

**For:** Claude Code and Codex
**Project:** Gaze-driven adaptive reading interface (THS, DLSU CeHCI). Built on the NewsMead Android app (Kotlin).
**This module:** A gaze estimator — front camera → gaze tracker → on-screen gaze coordinates (x, y), delivered through `GazeProvider`. (Original Stage 1-3 goal was fully on-device with no laptop; see status update above for why the final architecture uses GazeFollower over WiFi instead.)

---

## 0. Read first — scope and principles

**Goal of this module:** produce a real-time stream of estimated on-screen gaze coordinates, accurate enough to tell *which line of text* the user is reading (line-level, not character-level).

**Hard principles:**
1. **Build in stages. Do not write all five stages at once.** Each stage has an acceptance test. Stage N+1 starts only after Stage N passes on a physical device. *(Stages 1-3 already completed — see status update.)*
2. **Validate the plumbing before trusting the gaze.** *(Already done for Stages 1-3; still applies to Stage 4-5 — validate AOI mapping and RSI feed before trusting them.)*
3. **This runs on a PHYSICAL DEVICE only.** The Android emulator has no usable front camera. Every acceptance test is run on a real phone.
4. **Keep the gaze output behind a single interface** (`GazeProvider` with one `onGaze(x, y)` callback). This is why the GazeFollower swap required no changes to Stages 4-5 — swappability is a requirement, not a nice-to-have.

## Target devices
- James: Galaxy A56 5G, One UI 8.5 (Android 16)
- Groupmate: [To be filled]

Each teammate tests acceptance criteria independently on their own
device. If results diverge significantly between devices, this is
itself a finding — record it in PROGRESS_NOTES.md rather than
silently picking one number.

**Tech baseline (current, NewsMead repo):** Kotlin, `GazeProvider` interface backed by a GazeFollower WiFi bridge (Python backend on a co-located laptop). *(Historical Stage 1-3 tech baseline was CameraX + MediaPipe Face Landmarker + Apache Commons Math — no longer active; see gaze-prototype repo.)*

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

*(Result: FAILED. Vertical error ~1.7–2.3cm with severe run-to-run instability (4× swing between runs), root-caused to head-pose sensitivity. Escalated to Option 3 (MobileGaze/TFLite), which also failed — angular error (~10°) geometrically too large for the ~11° angle a phone screen subtends at reading distance. Escalated to Option 1 (GazeFollower over WiFi), which PASSED. Full numbers and diagnosis: gaze-prototype PROGRESS.md.)*

---

## Stage 3 Decision Gate — [RESOLVED]

*(This gate was exercised in full — see the Stage 3 result above. Outcome: escalated past both on-device options to GazeFollower over WiFi, which passed. `GazeProvider` interface was unchanged throughout; only its implementation changed. No further action needed here — this section is kept for the record of what the gate looked like.)*

---

## Stage 4 — Overlay on the NewsMead reading interface — [ACTIVE — CURRENT WORK]

**Build:**
- Integrate the `GazeProvider` (now backed by GazeFollower over WiFi) into NewsMead's article reading screen (the existing article view — **confirm whether it renders article text in a WebView or native TextViews; this is still unconfirmed and determines how line bounding boxes are obtained**).
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

## Stage 5 — Feed into the RSI pipeline — [NOT STARTED]

**Build:**
- Expose the per-frame output the RSI needs: current line index, fixation events (derived from gaze dwelling within a line/region), and regressions (gaze moving from a later line back to an earlier one).
- Hand these to the existing RSI computation via a clean interface. Do **not** implement saccade-velocity or microsaccade features — they are not measurable at consumer camera frame rates; the RSI consumes fixation, dwell, and regression signals only.

**Acceptance test:**
- The RSI module receives a live feed of line index + fixation/dwell/regression events while reading.
- Sanity check: reading normally produces mostly forward progression; deliberately re-reading an earlier line registers as a regression event.

---

## Cross-cutting requirements

- **Swappability:** all gaze output flows through one `GazeProvider` interface. Stages 4–5 depend only on that interface, never on the underlying tracker directly — this is why the GazeFollower swap required no changes here.
- **Touch-validation mode:** add a debug mode where a finger touch substitutes for the gaze coordinate. If touch input drives correct line-mapping and RSI events at ~100%, the downstream pipeline is proven correct, and any failure is isolated to the gaze tracker, not the integration.
- **Drift handling (note for later):** accuracy may degrade over a long session as posture shifts. Log a timestamp with each gaze sample so drift can be measured later.
- **Performance caveat:** if the device drops below ~20 fps end-to-end, profile before adding more features.
- **Older-adult testing:** the real accuracy unknown is glasses/presbyopia/lighting on 45–65-year-old faces. Test accuracy on a target-age face as early as possible, not only on a young developer's face. (Note: this still applies even though the tracker changed — GazeFollower's ~1.1cm accuracy was validated on the dev team, not yet on target-age participants.)

---

## Deliverables checklist
- [x] Stage 1: camera + MediaPipe iris landmarks logging, ≥20 fps, tracks eye movement — *complete, gaze-prototype repo*
- [x] Stage 2: 9-point calibration collecting clean landmark↔screen pairs — *complete, gaze-prototype repo*
- [x] Stage 3: tracker accuracy validated — *GazeFollower over WiFi, passed; Options 2 and 3 tried and failed first*
- [ ] Stage 4: gaze-to-line mapping on NewsMead article view, scroll-aware — *IN PROGRESS, this repo*
- [ ] Stage 5: fixation/dwell/regression feed into RSI — *not started*
- [ ] Touch-validation mode working
- [x] Accuracy number recorded — *see gaze-prototype PROGRESS.md*

# Gaze Calibration System — Design Architecture (Revised)

**Project:** NewsMead — gaze-driven adaptive reading interface (Android, Kotlin/View system)
**Scope:** 16-point calibration phase — on-screen procedure, sampling/rejection, quality gate, logging.
**Target population:** Older adults, **45–65** (per build-context.md; the draft's "65+" corrected).
**Status:** Design approved for implementation against the existing pipeline. Supersedes the
external draft (`calibration_design_architecture.md`); changes from that draft are listed in §9.

---

## 0. Existing-code contract (do not break)

The design below revises the *procedure*; these integration facts are fixed:

- **Capture:** `GazeCalibrationActivity` + `CalibrationView`; raw features arrive via
  `LocalRawGazeSource.onRawGaze(gazeX, gazeY, timestampMs)` from `MediaPipeRawGazeSource`.
- **Raw feature is NOT pixels.** The active `eye_local_width_average_v1` mode uses
  eye-axis iris displacement normalized by eye width, both eyes averaged (§7.4). All pre-fit
  thresholds (dispersion, drift) are therefore in **feature units**, convertible to px only
  after the mapper is fitted.
- **Fit input:** `calibration_16point.csv` (the first four mapper columns remain
  `screen_x,screen_y,gaze_x,gaze_y`), read by
  `CalibrationStore.load()` → `GazeMapper` (2nd-degree poly on standardized features,
  ridge λ=1.0; beyond the calibrated feature range the output extends linearly along the
  boundary gradient, capped at 1.5 z — the earlier hard clamp froze gaze at a drifting
  "invisible barrier") → `LocalCalibratedGazeProvider` → `ArticleFragment`. The current
  12-column schema appends per-eye and passive posture evidence; the mapper continues to use
  only the original averaged gaze pair. The codec can parse legacy four- and
  eight-column files, but runtime loading also requires current feature and coordinate
  metadata (§7.5). The CSV still contains only fit points (never the drift-repeat or
  validation points). The session log (§6) is additive.
- **Frame-level rejection that already exists:** no-face frames and blink frames
  (eye-aspect-ratio hysteresis, `StudyConfig.GAZE_BLINK_*`). MediaPipe Tasks FaceLandmarker
  exposes **no per-frame landmark confidence** — rejection is: no-face, blink, dispersion (§4).
  Nothing else is available.
- **Throughput reality:** approximately 15–30 fps from the source *before* blink/no-face
  drops across the two tested phones. The SM-G991B reaches about 30 only without a detected
  face and about 15–17 while running the 478-point landmark path (E-056). Sampling windows
  are **adaptive** (collect until target sample count or timeout), not fixed-duration. A
  non-authoritative BlazeFace plus 64x64 two-eye shadow sustained about 22.88 fps on a warmed
  SM-G991B, but its recursively refined crop failed replicated A56 compatibility (E-061/E-062).
  A detector-anchored, one-step size-only crop also failed two A56 checks (E-063). Both candidates
  remain rejected and cannot emit gaze. A detector-anchored, non-recursive centre-and-size
  re-extraction preserved intended target order in two A56 runs but failed replicated SM-G991B
  horizontal geometry (E-064-E-066). The BlazeFace/periodic-crop replacement branch is rejected and
  must remain non-authoritative; ordinary use should return to the 478-point path without shadow
  inference. A separate compact 468-landmark face-corner + iris candidate restores repeated
  horizontal ordering on both phones (E-067/E-068), but its full candidate remains only 15.5 FPS on
  SM versus 27.0 on A56 in the first gate. Bulk float packing subsequently reduces sustained SM
  work to 23.38/41.81 ms median/P95 and raises window FPS to 20.13 average (E-069). It remains
  researcher-only and uncalibrated pending standalone fit/held-out validation; it cannot yet replace
  the authoritative path.
- **FPS reporting cadence:** inference and raw-gaze delivery still run on every accepted
  result, but the on-screen/lightweight FPS callback is capped at four updates per second.
  This removes redundant UI/log work without changing samples, estimator output, or timing.
- `GazeMapper` requires ≥6 points; `GazeTestActivity` (3×3 benchmark, px/cm conversion via
  DisplayMetrics) remains the independent accuracy test.

---

## 1. Design goals (priority order)

1. Each stored pair reflects a true, settled fixation on the intended point — not a
   saccade-in-progress, overshoot correction, or drift.
2. Usable by 45–65-year-old participants without assistance: slower saccadic latency,
   longer settle, clearer pacing cues than young-adult protocols assume.
3. Robust to small head/device movement inherent to handheld use.
4. **A bad calibration is caught in-session, not mid-article:** post-fit quality gate with
   researcher Accept / Redo decision (§7).
5. Self-contained per-session log auditable at thesis writeup: per-point quality, rejected
   samples, drift delta, fit residuals, validation errors (§6).

---

## 2. Target visual design

### 2.1 Background — match the reading surface
Calibrate against the **article reading background** (light/paper tone), not the current
pure-black screen. Luminance mismatch between calibration and use changes pupil size and is
a reliable source of systematic offset. Pull the actual reading-mode background color
resource so the two screens cannot drift apart. (Color-mode buttons in the article view are
a known open flag — if sepia/dark are kept available to participants, calibrate on the mode
the participant will read in.)

### 2.2 Target structure
Hit-marker reticle, sized for reduced acuity/contrast sensitivity at 45–65:
- Outer ring: static circle, **44dp diameter**.
- Four shortened crosshair arms meet the outer ring but leave the exact center open.
- A small fixed `+` marks the exact center throughout presentation.
- A bright saturated-red disc with a black outline begins at the tips of the four
  outer arms, with the black reticle drawn above it for contrast. The arms extend only
  slightly inward from the fixed ring. The disc contracts once toward the center marker
  across APPEAR, HOLD_ATTENTION, SETTLE, and the expected 700 ms base SAMPLE window,
  reaching the size of the center `+` near normal capture/advance time.
- Color: chosen by **contrast ratio against the §2.1 background**, not fixed hue. On a
  paper-tone background: dark/charcoal ring + warm-red inner dot. (The draft's amber was
  chosen for a black background that §2.1 removes.) Verify in pilot.

### 2.3 Per-point state machine

```
APPEAR → HOLD_ATTENTION → SETTLE → SAMPLE → CONFIRM → (advance | re-capture)
```

| State | Duration | Visual | Purpose |
|---|---|---|---|
| APPEAR | 150–250 ms | fade/scale in at new position | cue the saccade |
| HOLD_ATTENTION | 400–600 ms | center cue completes one inward contraction | older-adult saccadic latency and fixation guidance |
| SETTLE | 300–500 ms, **not sampled** | final slow inward contraction | overshoot/correction saccades land here |
| SAMPLE | adaptive: until **N=10** post-rejection samples or **1500 ms** timeout | centered radial contraction during the expected first 700 ms; no translation | data collection |
| CONFIRM | ~100 ms | scale-down flash + soft audio tick | pacing feedback |

- Implemented as an explicit state enum driving `CalibrationView`, replacing the current
  scattered `Handler.postDelayed` timers. Testable; SAMPLE boundaries unambiguous.
- The guidance changes size during the expected base SAMPLE period but its center never
  translates. Treat this as a controlled accessibility intervention and compare its
  dispersion against the prior static-sample target.
- Audio: soft tick at SETTLE→SAMPLE transition (clear temporal "now" marker), soft confirm
  at capture. Non-startling; `ToneGenerator` is sufficient.

### 2.4 Progress + researcher affordances
- Keep the existing researcher **Start gate** (position phone first).
- 16-dot mini-map (done/current/pending) + "n / 16" text.
- Practice point (§3.3) labeled as practice on screen.

---

## 3. Point layout & sequencing

### 3.1 Grid
4×4 at fractions `[0.10, 0.3667, 0.6333, 0.90]` of the inset region — unchanged. The
existing 10% margin already exceeds the draft's ≥5% edge guidance.

### 3.2 Ordering
- Use the original **fixed row-major order** for participant predictability: top-left to
  top-right, then each following row through bottom-right. This prioritizes older-adult
  usability over the anti-anticipation benefit of randomization. Log `fixed_row_major` so
  the spatial/time-order coupling remains explicit in analysis.
- **Drift check:** one near-center grid point is presented **twice** — once at its fixed
  grid position and once at the end. The second presentation is logged for the drift
  delta (§5) and **excluded from the CSV/fit**.

### 3.3 Practice point
One throwaway point before the sequence (center; not logged as data, marked `practice` in
the session log) so the participant learns the pacing and audio cues before real capture.

Session length estimate: 1 practice + 16 fit + 1 drift repeat + 5 validation ≈ 23 points
× ~2 s ≈ **45–60 s** plus the quality-gate screen. Acceptable fatigue budget; no pause
mechanism needed beyond the Start gate.

---

## 4. Sampling & rejection logic

Operates purely on the stream of timestamped `(gazeX, gazeY)` samples during SAMPLE — a
pure Kotlin class (`FixationWindowFilter` or similar in `com.newsmead.gaze`), unit-testable
on the JVM and re-runnable against logged raw samples without re-collecting.

Per point:
1. Frames already rejected upstream: no-face, blink (existing hysteresis gate).
2. Discard the first **100–150 ms** of SAMPLE unconditionally (residual settle).
3. **MAD outlier rejection:** drop samples > **2.5 × MAD** from the component-wise median
   (per axis). Removes the stray saccade/twitch without assuming Gaussian noise. MAD is
   wide when the fixation is genuinely noisy, so this trims tails without discarding
   usable data.
4. Aggregate: component-wise **median** of retained samples → the `(feature, screen)` pair.
5. Minimum retained samples: **10** (existing `MIN_SAMPLES`). Fewer → re-capture.

> **Removed after first on-device run (2026-07-24):** an absolute dispersion gate
> (0.02 feature units per axis) plus a most-stable sub-window fallback. The tracker's own
> per-frame noise floor is ~0.04 horizontally and ~0.05–0.10 vertically — *above* the gate —
> so acceptance was effectively impossible: every point timed out, retried, and was excluded,
> aborting the whole calibration ("never good enough"), and the rare sub-window that did pass
> preferred stable-but-off-target moments over the target-centred median (worse accuracy than
> the old plain median). Dispersion is still **computed and logged** for data-quality
> auditing, just not used to reject. A dispersion-based low-confidence flag can return later
> with per-axis thresholds derived from measured pilot noise — not guessed.

### 4.2 Re-capture flow
- Low-confidence or under-sampled point → **immediately re-present once** (same position,
  full state machine).
- Fails twice → mark `excluded` in the session log and **omit from the fit** (do NOT feed a
  known-bad pair to the ridge fit — one bad point warps all six coefficients). The fit
  proceeds if ≥ **12** of 16 points remain; below that, abort with "re-run calibration"
  (12 is a quality floor; the mathematical floor is 6).
- **Caveat logged automatically:** excluding an edge/corner point shrinks the calibrated
  feature range, and `GazeMapper` clamps live input to that range — record which points
  were dropped so shrunken-coverage sessions are identifiable in analysis.

---

## 5. Drift check

- Delta between the two aggregated feature vectors of the repeated center point (§3.2),
  in **feature units**; additionally converted to px through the fitted mapper afterward
  for reporting.
- Threshold: flag `high_drift` if the px-converted delta exceeds **half a line height**
  (line height as defined in §7). Threshold provisional; tune in pilot.
- **Option A (adopted):** flag in the session log and surface on the quality-gate screen
  (§7); the researcher decides. No automatic re-run. (Option B — adaptive re-centering —
  deferred; not worth the complexity before pilot data.)

---

## 6. Session log

Written **incrementally after every point** (crash-safe: an aborted session with a real
participant still yields analyzable data) to app-private storage alongside the CSV:
`calibration_session_<timestamp>.json`.

```json
{
  "session_id": "…", "participant_id": "…",
  "timestamp_start": "ISO8601", "device_model": "…",
  "screen_px": [w, h], "density_dpi": 0,
  "order_seed": 0, "order_mode": "fixed_row_major",
  "points": [{
    "point_id": 0, "screen_x": 0.0, "screen_y": 0.0,
    "presentation_index": 0, "practice": false, "drift_repeat": false,
    "recaptured": false, "excluded": false,
    "raw_sample_count": 0, "retained_sample_count": 0,
    "blink_dropped": 0, "no_face": 0,
    "aggregated_feature": [0.0, 0.0],
    "dispersion_feature": [0.0, 0.0]
  }],
  "drift_check": {
    "first_pass_feature": [0.0, 0.0], "second_pass_feature": [0.0, 0.0],
    "delta_feature": [0.0, 0.0], "delta_px_postfit": 0.0, "flagged_high_drift": false
  },
  "fit": { "points_used": 16, "loo_median_px": 0.0, "loo_p95_px": 0.0,
           "loo_median_lines": 0.0, "worst_point_ids": [0] },
  "validation": [{ "screen_x": 0.0, "screen_y": 0.0, "error_px": 0.0, "error_lines": 0.0 }],
  "outcome": "accepted | redo_worst | redo_all | aborted",
  "session_quality_flags": ["…"]
}
```

Field-name change from the draft: `dispersion_px` → `dispersion_feature` etc. — px values
exist only post-fit (§0).

---

## 7. Post-fit quality gate (in-session; the loop the draft was missing)

Immediately after the last fit point:

1. **Fit** `GazeMapper` on the retained points.
2. **Leave-one-out residuals** (free, no extra participant time): refit N times leaving one
   point out, predict the held-out point, record px error per point. Report **median and
   P95**, in px **and in line-heights** — the unit that matters for line-AOI mapping.
   Line height = `ARTICLE_FONT_SIZE_DP` (22dp) × 1.6 multiplier (+1sp extra) converted via
   device density — compute from the same constants the article view uses; don't hardcode px.
3. **Validation pass** (~10 s): **5 held-out points** (center + 4 quadrant midpoints, i.e.
   positions NOT in the 4×4 grid), same state machine and rejection logic as calibration.
   Error = fitted-mapper prediction vs. true point. This is the **reportable per-session
   accuracy number** (also convertible to cm via DisplayMetrics, matching `GazeTestActivity`).
4. **Researcher decision screen** (advise-but-allow; never hard-block):
   - Median/P95 error in px and line-heights, validation error, drift flag, excluded points.
   - Color-coded guidance anchored to the tracker's DOCUMENTED accuracy (~3–4 line-heights
     on the A56, from the ~1.68–2.42 cm overall median in progress-notes.md), banded on the
     worse of LOO and validation: **green ≤ 3.0 line-heights, amber ≤ 4.5, red above**. These
     flag a calibration that is unusually bad *for this tracker*, not one that misses an
     unattainable ideal — the earlier 1.0/1.5 bands were below the tracker's own floor and
     read red on every normal run. Provisional; tighten once pilot data exists.
   - **Accept** (writes CSV + finalizes log) / **Redo worst points** (re-run only the
     worst-K LOO offenders, then re-gate) / **Redo all**.
   - Red result still acceptable at researcher discretion — logged as a quality flag.

`GazeTestActivity` (3×3, 20/50/80%) stays as the independent benchmark; the in-flow
validation pass is a quick subset, not a replacement.

### 7.1 Mid-session drift correction (added 2026-07-24)

The accuracy test doubles as a drift-correction instrument. Each run yields 9
(predicted px, true target px) pairs — exactly the data needed to correct posture-shift
drift, which is mostly bias/gain, not a change in the polynomial's shape:

- After a run, an **affine correction** `truth ≈ A·predicted + b` (6 params, least squares)
  is fitted from the pairs and offered to the researcher with the measured pre-error and the
  estimated post-error (in-sample — re-run the test to verify independently).
- On Apply, the correction is stored (`drift_correction.csv`) and applied by
  `LocalCalibratedGazeProvider` **after** `GazeMapper` — the 16-point fit and the
  `GazeProvider` boundary are untouched, and the article screen picks it up on next start.
- The test always measures the live pipeline (correction included), so a subsequent fit
  maps corrected→truth and is stored as the **composition** with the active correction.
- Any accepted full calibration **clears** the correction (`CalibrationStore.save`).
- Deliberately NOT a fresh 9-point polynomial re-fit: 6 coefficients from 9 points is a
  thin, edge-unstable fit that risks replacing a good-but-drifted map with a worse one.
  If the affine correction can't fix it (error isn't affine), the honest remedy is a full
  recalibration.

### 7.2 Re-calibration architecture (redesign, 2026-07-24)

The accuracy/re-calibration screen (`GazeTestActivity`, launched from the article top bar)
was brought up to the calibration standard and its duplication removed:

- **Shared capture core.** `CalibrationPointCollector` owns the per-point
  APPEAR→HOLD→SETTLE→SAMPLE(adaptive)→CONFIRM mechanics + `FixationWindowFilter` and drives
  `CalibrationView`. **Both** full calibration and re-calibration use it, so they share
  identical sampling (MAD outlier rejection, lead-in trim, contracting hit-marker, light
  background, audio) instead of each carrying its own copy — the old test used a separate,
  inferior plain-median-over-fixed-window path that improving calibration never touched.
- **Measurement space.** The collector is fed the *mapped screen px* from the live pipeline
  (not raw features), because the drift correction is affine in screen space. Its filter
  medians in px; the per-point `(predicted px, target px)` pairs feed the fit.
- **Purpose split: measure → remedy.** A 3×3 pass (20/50/80%, off the calibration grid)
  reports median/P95/vertical error in px and line-heights. Then the screen offers the
  right remedy, not just "apply":
  - **Apply correction** — affine fit, composed with any active correction.
  - **Revert to calibration** — clears the drift correction (base 16-point map only).
  - **Full recalibration** — launches the 16-point flow when drift isn't affine-correctable.
  - **Measure again.**
- **Honest post-estimate.** The gate shows a **leave-one-out** estimate of the corrected
  error (`DriftCorrection.leaveOneOutMedianPx`), not the optimistic in-sample residual —
  refit on n−1 points, predict the held-out one. Same line-height bands as the calibration
  gate (green ≤3, amber ≤4.5).
- **Audit trail.** Applying/reverting appends a timestamped line to `recalibration_log.csv`
  (append-only history), alongside the single active `drift_correction.csv`.

### 7.3 Calibration-pose departure shadow monitor (added 2026-08-29)

Each accepted fit point also stores the median eye-midpoint face centre, aspect-correct
eye-centre separation/scale, and eye-line roll. The active calibration defines a conservative
per-axis envelope from its observed min/max expanded by robust margins. Live samples are
classified `IN_RANGE`, `OUT_OF_RANGE`, or `UNAVAILABLE` and summarized in diagnostic logs.

This classification is deliberately **shadow only** during evaluation. It does not enter the
quadratic feature vector, alter median/One Euro filtering, change the affine drift layer,
suppress gaze coordinates, or affect AOI/RSI/scaffold decisions. It therefore cannot be
reported as an accuracy improvement. Four/eight-column samples produce `UNAVAILABLE` posture
status; runtime calibration compatibility is additionally governed by §7.4 and §7.5.

### 7.4 Reversible eye-local raw-feature candidate (added 2026-08-30)

The prospective `eye_local_width_average_v1` mode projects each iris centre onto the
aspect-correct corner-to-corner eye axis and its downward perpendicular, normalizing both
displacements by eye width. This removes global image roll and avoids using a single moving
upper/lower eyelid pair as the vertical denominator. The two per-eye values are still averaged
to the same two-dimensional mapper input; the polynomial, regularization, filters, correction,
sampling, targets, and order are unchanged.

`raw_feature_mode` is written into both session-log types. An app-private
`calibration_feature_mode.txt` sidecar is written only when calibration is accepted. Accuracy
testing and article gaze refuse to start when the active raw mode does not match the saved
calibration. A pre-sidecar calibration is conservatively identified as the original
`eyelid_fraction_average` mode. This makes the intervention and rollback explicit rather than
silently mapping incompatible feature coordinates.

---

### 7.5 Screen-coordinate contract (2026-09-03)

The coordinate audit confirmed that an immersive window need not begin at screen `(0,0)`.
Targets continue to be drawn at the unchanged local grid positions. Each presentation records
the target view's measured `getLocationOnScreen()` origin and converts the target using
`screen = local + origin`. Saved fit pairs, held-out scoring, nine-point errors and affine
observations all use these screen coordinates. Screen gaze is converted back into the drawing
view's local coordinates only when rendering. A changed origin/size between capture start and
completion aborts that pass instead of accepting mixed coordinates.

`CalibrationStore.saveScreenCalibration()` writes `calibration_coordinates.txt` only when a
fresh calibration is accepted. Its `screen_px_v1` marker is bound by SHA256 to the CSV contents
and raw-feature mode. Missing, unknown, or mismatched metadata prevents runtime use and prompts
a fresh full calibration; old files and diagnostic logs are not migrated, relabelled or deleted.
Drift correction has its own coordinate marker bound to both its contents and the calibration
CSV. A normal accepted full calibration still clears the superseded active drift correction.

Calibration and nine-point logs distinguish physical `screen_px` from `target_view_frame`
(screen origin plus local width/height); each point retains local and screen targets and the
measured frame. Accepted calibration logs include `saved_calibration_sha256`; live test logs
include `calibration_sha256`. Reading schema v5 adds the same coordinate identifier/hash and
root/viewport/text frame records, without changing protocol v4 timing, targets or parameters.

`getGlobalVisibleRect()` is root-local. AOI clipping, overlay clipping and reading-reference
placement add the root view's screen origin before combining it with screen gaze. No status-bar
height or participant-derived offset is hard-coded. This corrects coordinate meaning; it is not
a new mapper and is not proof of improved reading accuracy. See `gaze-coordinate-audit.md`.

### 7.6 Isolated compact-candidate calibration gate (2026-09-06)

`CompactFaceCalibrationGateActivity` is a researcher-only evaluation surface for the compact
face-corner plus two-eye iris candidate. It deliberately reuses the current one-centre practice,
16 fixed row-major fit targets, five held-out targets, collector timing, robust aggregation, and
six-term ridge quadratic. Its mapper exists only in memory and its numeric JSON is separate from
normal calibration logs. It must not call `CalibrationStore`, write `calibration_16point.csv`, emit
participant gaze, or enter reading/AOI decisions.

The first SM-G991B gate accepted all targets but produced 1.75/2.22/2.22-line held-out vertical
median/P95/max and 1.48/4.98/4.98-line LOO values. Its unchanged replication improved to
0.83/2.09/2.09 held-out and 1.01/3.24/3.24 LOO lines. The raw fit-grid shape repeats, but the
regional signed-error pattern does not and neither run satisfies the provisional 1.2-line
all-target reference. This permits one unchanged A56 generalization gate—not parameter tuning or
runtime integration. The saved normal calibration hash was unchanged after both runs. Run labels
are optional launch-intent metadata; there is no editable label field in this isolated activity.

The first A56 gate reached 30.00 mean completion FPS but produced 2.89/4.76/4.76-line held-out
vertical median/P95/max and only 0.778 vertical fit-target correlation. It is retained as an adverse
first cross-device result and authorizes one unchanged A56 replication under the same isolation
boundary. It does not authorize mapper/model tuning or replacement of the normal pipeline.

The unchanged A56 replication and one additional sanity run produced held-out vertical median/max
of 3.31/6.80 and 4.68/6.45 lines while retaining approximately 30 FPS. All three adverse A56 runs
remain in the evidence set. This closes the compact candidate for runtime integration: its
performance result is useful, but its calibration accuracy does not generalize across the tested
phones. The isolated activity and numeric artifacts may be retained for audit; they must not affect
normal calibration or gaze output.

E-075 interpretation qualification: these runs reject this implementation for integration,
not every compact architecture. They did not include fresh matched baseline calibrations;
they cannot show that the retained pipeline was more accurate in those same conditions or
that the phone alone caused the difference.

### 7.7 Repeated-target end-pose translation (offline rejection, 2026-09-06)

A fixed offline candidate added the full raw feature difference between the repeated same-target
observations to every 16-point fit aggregate before fitting the unchanged quadratic. The five later
held-out points were not used for fitting or modified. Across all 13 eligible eye-local calibrations,
typical vertical error often improved but the median session tail worsened and only 2/13 sessions
improved vertical plus 2-D median/max together. This global translation is rejected. Do not add a
scaled, capped, temporal, or single-axis variant selected against the same held-out answers.

E-075 qualification: this was one constant translation of all fit features, not explicit
head-pose compensation or per-point temporal dewarping. It does not exhaust other calibrated
representations. Section 7.3 is likewise only a shadow flag, not a compensation experiment.
The current next inquiry is native MGazeNet feasibility (`gaze-mgazenet-handoff.md`); none of
these document changes modifies the active estimator, mapper, filters, or calibration sequence.

## 8. Implementation notes

- State machine (§2.3): explicit enum owned by the activity (or a small controller class),
  driving `CalibrationView` rendering. No behavioral timers in the view.
- Rejection logic (§4): pure function of `List<TimestampedSample>` → result; JVM unit tests
  for MAD rejection, sub-window search, low-confidence flagging.
- LOO + line-height conversion: pure helpers, unit-tested (`GazeMapper` is already
  JVM-constructible from `CalibrationSample` lists).
- CSV written **only on Accept** — never overwrite a good previous calibration with an
  unreviewed new one. (Current code writes immediately at sequence end; change this.)
- Incremental JSON writes after each point (§6).
- Build/verify: `:app:compileDebugKotlin` + new JVM tests (JDK 17); device validation per
  build-spec on the A56.

---

## 9. Changes from the external draft (audit trail)

**Corrected:**
1. "MediaPipe landmark confidence" rejection — no such per-frame signal exists in the Tasks
   API; replaced with no-face + blink (existing) + dispersion.
2. All pre-fit px units (`dispersion_px`, drift `delta_px`) → feature units; px only post-fit.
3. Twice-failed points are **excluded from the fit** (with coverage caveat), not merely
   flagged — a flagged-but-included bad point warps all coefficients.
4. Amber-on-black target color contradicted the background-matching recommendation; color
   now derived from contrast against the reading background.
5. Target population 65+ → 45–65 (manuscript/build-context).
6. Fixed-duration SAMPLE windows → adaptive (sample-count target with timeout), because
   real post-rejection throughput (~20–30 fps minus drops) can undershoot 10 samples in
   400 ms.

**Added:**
7. §7 post-fit quality gate: immediate fit, LOO residuals, line-height error units,
   Accept / Redo-worst / Redo-all researcher screen; CSV written only on Accept.
8. §0 integration contract: CSV name and mapper columns unchanged, posture/per-eye evidence
   appended compatibly, fit points only; session log additive;
   `GazeTestActivity` retained as independent benchmark.
9. Fit floor raised 6 → 12 points for session validity; abort path defined.
10. Concrete starting thresholds (dispersion 0.02 feature units, drift ½ line-height,
    green/amber/red gate bands) — all marked provisional pending pilot.

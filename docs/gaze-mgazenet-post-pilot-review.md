# MGazeNet post-pilot evidence review — 2026-09-09

Status: camera-free review complete. The subsequently authorized v3 software
increment is implemented and host-verified; see
`gaze-mgazenet-calibration-observability-v3.md`. Read after
`gaze-mgazenet-stationary-pilot-results.md`.

## Decision

Retain MGazeNet only as an isolated research candidate. The four-session pilot
establishes that the frozen phone pipeline can emit spatially meaningful gaze
coordinates after a fresh calibration, but it does not establish repeatable
line assignment or natural-reading accuracy. Do not promote it, modify the
active tracker, select a favorable freshness threshold, subtract a post-hoc
bias, or repeat a completed session.

The primary next question is calibration/session repeatability, not framerate.
The selected next increment was a camera-free implementation of numeric
calibration observability. It has now been reviewed and verified in software;
no new device or participant boundary is implied.

## Evidence reviewed

The review used the four immutable v2 reports and their frozen offline summaries:

| Device/session | Order | vertical median / P95 / max | exact / within-one | mean Y bias | output-age median / P95 |
| --- | --- | --- | --- | ---: | --- |
| SM-G991B S1 | forward→reverse | 0.76 / 1.82 / 8.30 lines | 31.7% / 85.3% | -75.3 px | 219.8 / 281.1 ms |
| SM-G991B S2 | reverse→forward | 0.87 / 1.90 / 12.12 lines | 33.5% / 83.1% | +101.1 px | 237.9 / 307.0 ms |
| A56 S1 | forward→reverse | 0.59 / 1.53 / 6.19 lines | 32.1% / 79.3% | -36.0 px | 244.1 / 288.3 ms |
| A56 S2 | reverse→forward | 1.21 / 2.26 / 4.66 lines | 14.8% / 56.5% | +59.7 px | 223.3 / 264.5 ms |

All four sessions used distinct 585-row calibration digests. Every planned
block and location contributed, every received test result contained a
coordinate, and all unfavorable tails remain included. The scorer assigned no
gate or promotion decision. Report hashes, full target tables, fixed-age
availability and conditions are in the pilot-results document.

This review did not rescore, pool, refit or transform the predictions. It used
the already frozen summaries and inspected the producing calibration code.

## Findings

### 1. Typical spatial signal exists, but exact line performance is not stable

Session medians of 0.59–1.21 line heights are within the better range of prior
NewsMead gaze experiments. That is useful feasibility evidence. It is not enough
for reading: exact-line assignment spans 14.8–33.5%, within-one-line assignment
spans 56.5–85.3%, and individual sessions retain 4.66–12.12-line maxima.

The first A56 session is the strongest typical result, but its independent
fresh-calibration replication is materially worse. The A56 median/P95 changes
from 0.59/1.53 to 1.21/2.26 lines, exact-line from 32.1% to 14.8%, and
within-one-line from 79.3% to 56.5%. A favorable first session therefore cannot
support a device ranking or promotion.

### 2. Vertical bias is session-level evidence, not a proven order effect

Vertical bias keeps the same sign across both sweeps inside each session, then
changes sign between each phone's fresh sessions:

| Session | whole-session Y bias | sweep 1 Y bias | sweep 2 Y bias |
| --- | ---: | ---: | ---: |
| SM-G991B S1 | -75.3 px | -78.3 px | -72.1 px |
| SM-G991B S2 | +101.1 px | +80.1 px | +123.4 px |
| A56 S1 | -36.0 px | -42.2 px | -30.3 px |
| A56 S2 | +59.7 px | +44.2 px | +74.9 px |

This pattern makes fresh-calibration/session state the most useful next object
to measure. It does not prove that the SVR caused the change. Session number,
order plan, calibration, posture, compliance and fatigue remain confounded, and
the one participant reported severe ongoing fatigue. No term can be causally
subtracted from tracker error.

### 3. Exact-line and vertical-distance metrics expose different failures

The A56 sessions contain 53 and 52 off-text coordinates. Almost all occur at
right-edge grids 8 and 44; the second session's entire off-text count occurs
there. Exact-line assignment correctly requires horizontal membership in the
rendered line rectangle, so a small vertical distance alone cannot make those
coordinates correct. Do not widen the bands or replace exact-line assignment
with vertical error after seeing this result.

### 4. Timing cannot rescue the accuracy result

Median output age is 219.8–244.1 ms. The 50 and 100 ms sensitivity columns have
zero coordinate availability; target-balanced 200 ms availability is at most
0.97%. At 500 ms, coordinate availability is about 87–89%, while correct-line
availability remains much lower. Analyzer busy drops are 52.4–58.1%.

These findings preclude a low-latency reading-event claim from this pilot. They
do not show that increasing throughput would repair spatial calibration. Because
accuracy and measurement validity are the priority, do not optimize threads,
precision or display smoothing before the calibration-repeatability question.

### 5. The current calibration export cannot explain good versus bad sessions

`AccuracySession` retains 45 accepted 258-value feature rows at each of 13 fit
targets and gives all 585 correlated frame rows to fixed RBF X/Y SVRs.
`SvrCalibration.fit` returns only success/failure. The report retains target
counts, eligibility rejections and a digest, then destroys the features and
personal model. This is privacy-preserving, but it supplies no target-held-out
calibration diagnostic. A successful fit and 45 accepted rows at every target
therefore cannot distinguish A56 session 1 from session 2.

Upstream-style prediction on the same rows used for fitting would be a training
residual, not validation. Randomly splitting adjacent frames would also leak
target identity and temporal duplicates. Neither is an acceptable next metric.

## Implemented bounded software proposal: calibration observability v3

### Goal

Determine whether an unstable calibration can be detected before final
held-out accuracy measurement, without using the four pilot target results to
tune a correction or claim validation.

### Isolated implementation scope

1. Add a pure calibration-audit component to the benchmark module. It receives
   the in-memory feature rows and fixed target labels before they are destroyed.
   It must not access NewsMead calibration storage or the active tracker.
2. Reuse the active NewsMead calibration's **procedure geometry**, not its
   incompatible stored samples or mapper: a fixed row-major 4×4 grid at viewport
   fractions `.10`, `.3667`, `.6333`, `.90`, followed by a repeat of the nearest
   centre fit point and five held-out validation points at centre and quarter
   diagonals. MGazeNet still collects its own 258-value features and fits its own
   unchanged RBF SVRs. It does not read `calibration_16point.csv`, feed the active
   two-feature quadratic mapper, or change active calibration behavior.
3. Compute leave-one-**target-group**-out diagnostics: fit the unchanged SVR on
   15 complete target groups and predict all 45 rows of the omitted target.
   Report target-balanced screen-pixel and normalized X/Y median/P95/max, with
   every target represented. Never split individual frames randomly.
4. Record numeric collection health per fit target: time to obtain 45 accepted
   rows, rejection counts, output-age distribution, and scalar within-target
   feature dispersion. Do not retain feature vectors, landmarks, images or the
   personal model. Feature-space dispersion is descriptive and must not be
   called gaze error.
5. Add the six fixed post-fit checks from the active procedure: `fit_6` repeated
   as a near-centre drift observation, then held-out viewport fractions
   `(.50,.50)`, `(.25,.25)`, `(.75,.25)`, `(.25,.75)`, `(.75,.75)`. The five
   validation points remain disjoint from all 16 fit positions. Predictions are
   diagnostic-only: they do not refit, translate, correct or select the model.
6. Version the schema and bind audit method, target identities/order, screen
   geometry, pipeline identity and calibration digest into the manifest. Retain
   incomplete/failed attempts and make setup camera-off by default.
7. Assign no numeric pass threshold in this implementation. The existing four
   sessions lack the required audit telemetry, so a threshold cannot be
   backfilled. Any later development threshold and confirmatory evaluation must
   be frozen before separate data are collected.

The change from the initially proposed 13-fit/six-unused-anchor layout was made
before any v3 data existed, in response to the design review asking whether the
current 16-point NewsMead procedure should be reused. This improves procedural
comparability without pretending the models share an input space. The active
tracker maps one robustly aggregated two-value eye-local observation per target
with a six-term quadratic ridge mapper; MGazeNet fits 45 rows of 258 values per
target with two RBF EPS-SVRs. Those samples, files and mappers are not
interchangeable.

### Software verification before any phone work

- Pure tests must prove target-group isolation and reject row-level leakage,
  missing targets, duplicate targets, non-finite features, changed anchor sets,
  manifest removal and report identity changes.
- Deterministic synthetic cases must include a well-separated calibration, a
  target-specific corrupted group, a global feature collapse and a successful
  in-sample fit with poor held-out-target behavior.
- Controller tests must prove that opening setup does not request CAMERA, an
  audit computation or contract failure cannot silently continue,
  stop/backgrounding writes a partial record, and features are zeroed after
  audit/fit completion or failure.
- The offline reader must reproduce the numeric aggregates independently and
  emit no accuracy gate or promotion decision.

The completed software checkpoint, exact tests and remaining boundary are in
`gaze-mgazenet-calibration-observability-v3.md`. It does not authorize installing
a new APK, contacting a phone, opening a camera, running another calibration or
changing the active tracker.

## Explicitly rejected shortcuts

- Do not subtract any session's mean bias from its own test coordinates.
- Do not fit affine, polynomial or line-snapping correction on these pilot
  targets.
- Do not choose 500 ms because it has the largest availability.
- Do not pool frames or sessions to conceal replication differences.
- Do not repeat fatigued sessions to seek a favorable result.
- Do not change the localizer, model weights, SVR parameters, target timing or
  eye-area rule in the same version as the observability change.
- Do not optimize framerate as a substitute for calibration evidence.

## Authorization boundary

The camera-free evidence review and authorized v3 software implementation are
complete. Device installation, camera checks, personal calibration, new data
collection, model promotion and active-tracker changes remain unauthorized
unless separately and explicitly approved after the software checkpoint.

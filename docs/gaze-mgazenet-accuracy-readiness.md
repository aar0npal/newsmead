# MGazeNet accuracy and measurement readiness — 2026-09-07

**Decision: retain MGazeNet as an isolated accuracy candidate. Accurate gaze
and trustworthy reading measurements on both phones are the primary criteria.
No MGazeNet gaze-accuracy result exists on either phone.** A running model,
synthetic parity, a successful SVR fit or faster processing does not establish
correct line assignment, reading events or improvement over the active tracker.

The user clarified this priority after the camera-free profiling work. That
bounded investigation is complete; further thread/precision optimization is
not the next default task. No camera, participant calibration or active tracker
change was made. Installation authority does not itself authorize those runs.
The user continues to handle staging and commits.

Continuation: `gaze-mgazenet-accuracy-protocol.md` now records the localizer
source/asset audit, 21 exact source eye-area checks, and an implemented offline
stationary-target evaluator with explicit missing-data denominators. Its
example is synthetic; no new gaze-accuracy measurement exists.
`gaze-mgazenet-accuracy-harness.md` is the subsequent implementation and
verification checkpoint. Read it before repeating completed preparation work.

## What the evidence establishes

| Question | Evidence | Remaining limit |
| --- | --- | --- |
| Are these the intended public weights? | Pinned GazeFollower 1.0.2 model and runtime hashes in `gaze-mgazenet-benchmark.md`. | Public weights cannot inherit accuracy results from different/larger weights or a laptop setup. |
| Does the port calculate the specified inputs? | 89 source-derived crop cases and three exact synthetic tensor comparisons on both phones. | Synthetic landmarks bypass localization; real phone image distributions remain untested. |
| Does native inference reproduce the reference? | Both phones pass the original three fixtures and all 48 profiling conditions at fixed tolerances. | Feature tolerances are engineering checks, not a bound in pixels or line heights. ARM low/FP16 is untested. |
| Is the calibration solver compatible? | Native OpenCV EPS-SVR parameters match pinned source; synthetic predictions agree within 1.073e-6. | Synthetic 585-row fitting proves solver behavior, not a person's calibration quality or repeatability. |
| Is runtime cost an established exclusion? | Foreground range-check candidate medians are about 14 ms on A56 and 17 ms on SM-G991B, excluding camera/localizer/calibration/filter/display. | No complete gaze latency or sustained reading measurement exists; original 64 ms A56 result is not a hardware ceiling. |
| Is reading measurement valid? | Historical baseline and rejected-candidate evidence are preserved. | No MGazeNet held-out spatial, temporal or reading-event validation exists. |

## Source/code findings that matter before accuracy implementation

This review reread the pinned local estimator, alignment, calibration controller
and SVR source under `mgazenet-benchmark/vendor/reference/`, alongside the actual
Android `CameraBenchmark.kt` and `SvrCalibration.kt`.

1. **Localization is the largest unclosed input-contract gap.** Upstream uses
   legacy refined FaceMesh; the harness uses MediaPipe Tasks Face Landmarker
   VIDEO. Reusing landmark indices and matching box arithmetic does not prove
   identical landmarks or crops. Upright portrait phone images also differ
   from the desktop acquisition path. Establish a licensed-fixture comparison
   of localization, crop acceptance, boxes and downstream features before
   labeling the phone pipeline upstream-equivalent. If equivalence cannot be
   established, document the localizer as an explicit adaptation requiring its
   own accuracy evaluation. Do not substitute another localizer for speed alone.

2. **The calibration solver is only part of calibration.** The source controller
   waits 1.5 seconds, collects 45 accepted frame features per target, then waits
   0.5 seconds; initial center practice is separate. It checks gaze status and
   both eye-openness values against a default threshold of 10. Those openness
   values are polygon areas in pixel coordinates, so that threshold is not a
   resolution-independent blink measure. The current timing harness accepts
   valid face/eye crops without implementing that calibration eligibility rule.
   Its completed-feature count must not be called usable calibration coverage.
   Porting only the two regressors does not establish controller equivalence.

3. **Upstream calibration error is a training residual.** `SVRCalibration.calibrate`
   predicts the same features used for fitting and reports their mean Euclidean
   error. It is not held-out validation. The controller declares validation
   point lists, but that does not turn this training score into an independent
   test. Any research accuracy harness must separately identify fit targets,
   held-out targets, test sessions and their denominators.

4. **Screen coordinates require an explicit new contract.** All 258 model values
   feed separate X/Y SVRs; the first two model outputs are not phone screen
   pixels. With default fractional labels, prediction must map to the same
   physical screen coordinate system as targets and rendered text, including
   view origins, insets and scrolling. Bind calibration to device, model,
   preprocessing/localizer version and coordinate convention. Existing iris
   calibrations are incompatible. E-053's repaired coordinate defect must not
   recur in the new harness.

5. **Spatial correctness alone cannot validate reading events.** The timing
   harness discards features and has no calibrated gaze, temporal filters or
   reading decisions. Its output rate cannot validate fixation duration,
   dwell, regression count or scaffold placement. Preserve capture/result
   timestamps, gaps, raw versus filtered predictions and independent target
   timing in any later evaluation. Filtering may change both spatial error
   and delay; reducing visible jitter is not sufficient evidence of accuracy.

## Accuracy evaluation contract to prepare next, without starting runs

The next bounded work is to specify the localizer comparison and a separately
reviewable accuracy harness. Do not request repeated ordinary calibration as a
substitute for closing the input and measurement contracts above. Do not create
a participant calibration UI or start camera capture from this note alone.

The eventual comparison needs fresh, compatible calibration for each estimator,
independent held-out targets and separate-session replication on **each phone**.
Fix preprocessing, solver parameters, eligibility and filtering before testing;
split whole target blocks/sessions rather than adjacent frames of a fixation.
Counterbalance estimator order and retain failures, regional tails and drift.
One researcher's two phones cannot establish older-adult population accuracy.

Report both target-balanced summaries and clearly labeled sample-level results:

- Signed horizontal/vertical error, vertical median/P95/max in actual rendered
  line heights, 2-D error and per-region failures.
- Exact-line and within-one-line assignment, plus word accuracy only where
  reliable rendered word geometry and independent targets exist.
- Missing estimates, rejected samples, off-text estimates, valid coverage and
  invalid intervals against all planned observations; coverage on any text is
  not correct-line accuracy. A long gap must not disappear from an accuracy
  result because it contains no accepted samples.
- Drift and repeatability across session time and modest natural posture or
  lighting changes, retaining uncertainty and the number of independent blocks.
- Known-target transition timing, output age and gap distributions, with
  measurement of false line changes/regressions and dwell/fixation behavior
  under controlled tasks. These tasks test the instrument; their instructed
  sequence does not become ground truth for unconstrained natural reading.

Predeclare acceptable error and timing requirements from the intended line,
word and reading-event measurements before held-out trials. No approved numeric
accuracy threshold or FPS floor is inferred here. Five held-out points cannot
support a precise tail estimate, and thousands of correlated frames cannot
replace independent sessions. Use timing as a measurement-validity constraint,
not a score that compensates for spatial errors.

Do not use line snapping, expected reading order or the Adaptive Instability
Index as gaze ground truth. `manuscript-implementation-alignment.md` explains
how the same erroneous gaze stream can both inflate reading-instability metrics
and place a scaffold incorrectly. A successful scaffold response cannot validate
that stream. Both preserved A56 reference runs (36.5% and 85.4% within one line)
and the compact-candidate failures remain relevant counterevidence against
declaring success from one favorable session.

## Evidence anchors

- `gaze-mgazenet-benchmark.md`: pinned model, preprocessing, solver, Android
  requirements, numerical tests and original installation evidence.
- `gaze-mgazenet-inference-profile.md` and its results JSON: complete six-profile
  performance control, no accuracy interpretation.
- `gaze-accuracy-handoff.md`, `gaze-mgazenet-handoff.md`: historical evidence,
  failed controls, coordinate repair and two-phone requirements.
- `manuscript-implementation-alignment.md`: reading-metric validity and circular
  measurement contamination.
- Pinned upstream source revision: `553920edcb7998c029828677f50f6d8eb4a16249`;
  source links and model hashes are in the benchmark manifest. This note uses
  the locally retained source, not a claim about a newer release.

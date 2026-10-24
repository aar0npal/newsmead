# MGazeNet protocol review — 2026-09-07

**Implementation follow-up:** `gaze-mgazenet-input-check.md` records the now
implemented input-only mode. Its first authorized SM-G991B attempt retained a
bitmap-lifetime software failure; the first repair exposed and retained the
next-frame recycled-buffer failure. The complete repair subsequently passed its
on-device regression test and full input check on both phones. Both showed
nonempty eligible input; timing/drop findings and the A56 warm-up gap are in the
input-check checkpoint. No accuracy or calibration run occurred.

**V2 software follow-up:** `gaze-mgazenet-stationary-v2.md` records the now
implemented and host-verified stationary v2 pilot. It freezes ten repeated
locations, two counterbalanced sweeps, explicit order metadata, neutral text,
and 50/100/200/500 ms sensitivity reporting without a primary threshold. The
proposal below is retained as the design rationale; it is no longer
unimplemented. No phone, camera or personal calibration was used for that
software checkpoint.

**Two-phone geometry follow-up:** the exact v2 package subsequently passed the
authorized camera-free setup/geometry class on both A56 and SM-G991B. Each
actual layout retained ten distinct repeated locations with no exact fit overlap;
neither order was selected by default and no record was created. See
`gaze-mgazenet-stationary-v2.md`. This is not a camera, calibration or accuracy
result.

Decision: continue with the isolated candidate, with accuracy and measurement
validity first. The original v1 harness was a stationary feasibility instrument;
its software pass does not establish repeatability, reading accuracy or reliable
event timing. No camera or personal calibration was run during this review.
All proposed phone work below remains prospective, not an executed experiment.

## Findings from the actual implementation

| Finding | Consequence |
| --- | --- |
| `AccuracySession.TEST_INDICES` is always `2,8,13,15,31,33,38,44`, ordered from upper to lower rows. Each point appears once. | Screen region and elapsed test time are coupled. Comparing early and late errors cannot isolate drift. There is no within-calibration repeat at the same held-out position. |
| The middle grid row is absent from validation. Held-out Y positions move to line centers and X positions clamp to the rendered text width. | The eight points characterize those actual text positions, not the whole display or all reading locations. Calibration coverage is not validation coverage. |
| Three seconds of settling precede each 2.5-second scoring window. Transition frames are discarded. | This can measure stable instructed-point error. It cannot measure response to target changes, missed short fixations, or regression onset. |
| Test points sit at line centers inside rectangular line bands. | This is a comparatively forgiving exact-line task. It does not establish accuracy near line boundaries, at individual words, during scrolling, or across article layouts. |
| A valid model output is suppressed if either pixel-polygon eye area is at most 10. The raw rejected prediction and areas are not exported. | Reported spatial errors describe admitted coordinates. Nulls/coverage must accompany them. The current export cannot independently validate blink classification or replay alternate eligibility rules. |
| The setup screen requires one age limit, but it affects only offline availability scoring. | A missing scientific threshold need not be answered by choosing a number that improves a score. All raw spatial errors and capture/output ages remain usable; a declared sensitivity analysis is preferable for a descriptive pilot. |
| The repeated background text instructs reading at one's usual pace while session instructions request looking at the red target. | Before participant use, replace the conflicting background wording with neutral text in the isolated harness. This is a presentation change and needs its own package record. |

These are code findings, not new measurements. Relevant sources are
`AccuracySession.kt`, `AccuracyTargetView.kt`, `AccuracyCameraSource.kt`,
`AccuracyReport.kt` and `tools/gaze/mgazenet/accuracy_metrics.py`.
These findings describe the reviewed v1 implementation; the v2 follow-up above
implements the specified repairs without reinterpreting v1 evidence.

The distinction matters for NewsMead: E-054's stronger A56 run had 85.4%
within-one-line accuracy but only 36.0% exact-line and 13.1% exact-word accuracy.
The weaker run remains in the evidence. These historical reading results are
not directly comparable with MGazeNet's different target task. The actual
`ReadingStateInferencer` defaults include 120 ms minimum dwell, 300 ms fixation
and 300 ms regression confirmation. Those are implementation parameters, not
validated temporal tolerances. A stream that repeatedly assigns an adjacent line
can look acceptable under a within-one-line score while corrupting those events.

The reporting guideline by [Dunn et al., 2023 edition](https://link.springer.com/article/10.3758/s13428-023-02187-1)
supports reporting the calibration and validation design, measurement uncertainty,
processing, loss, timing, and viewing conditions. It distinguishes sampling
frequency from system latency and makes timing relevance depend on the measured
phenomenon. It supplies no universal FPS or accuracy pass threshold for this app.
The following plan is our engineering proposal, not a procedure prescribed by
that paper.

## Bounded next steps

### 1. Input-only check before asking anyone to calibrate

Implement a separate, explicit-start 20-second input check using the same
`AccuracyCameraSource` settings. It should show neutral instructions, fit no SVR,
present no calibration grid and retain only numeric diagnostics. Opening setup
must keep the camera off. Camera initialization has a separate 20-second deadline;
stop, backgrounding and errors must save an incomplete outcome.

Record source dimensions, rotation, clock declaration and observed timestamp
consistency, delegate, model/pipeline hashes, no-face/invalid-crop/eye-area counts,
numeric eye-area and crop-size summaries, emitted-result ages and analyzer busy
drops. Preserve all planned elapsed time, including no-result intervals. Do not
save images, landmarks or features. Reuse the exact current area threshold without
tuning it to this person's eyes. Human blinks/target compliance are not verified
ground truth in this check.

Run it once on each phone only after camera use is authorized. A supported clock,
stable geometry and reproducible nonempty eligible input justify proceeding to a
bounded feasibility trial. They do not certify gaze accuracy or a validated
eligibility rate. A no-result/failed check is retained and investigated; it does
not trigger repeated personal calibrations. The older 125-second camera-timing
screen lacks the needed eye-area diagnostics and uses different camera settings,
so it is not a substitute for this check.

The input-only mode was **not implemented by this review**; its later completed
implementation and both-phone findings are in `gaze-mgazenet-input-check.md`.

### 2. Freeze a v2 stationary pilot before collection

Keep the pinned estimator, preprocessing, source-family fit order/585 rows,
SVR, area rule, and no-filter/no-correction choices. Prepare the following
explicit changes to validation as a new protocol, not a silent v1 reinterpretation:

- Ten distinct held-out locations: existing eight plus grid indices 22 and 24
  in the middle row. Verify physical positions remain distinct after text
  clipping and never overlap fit coordinates on either phone.
- Two sweeps of those same positions under one calibration: forward then
  reverse in session 1, reverse then forward in session 2. Use unique block IDs,
  separate location IDs and explicit sweep/order metadata. Keep 3-second settle
  and 2.5-second measurement windows. Do not refit between sweeps.
- Two predeclared fresh sessions per phone, with a rest and ordinary repositioning
  between sessions. This is a small diagnostic minimum, not a powered sample-size
  claim. Keep lighting, posture instructions and display settings documented.
  Record glasses use and approximate viewing distance without inferring them
  from face scale. Report both sessions on each phone separately.
- Keep all failed, aborted, empty and completed attempts. Stop for discomfort or
  software failure. Do not repeat until a favorable result appears. A repair or
  tuned parameter creates a new version; its new trials are separate evidence.

This adds within-calibration repeat observations and partially separates location
from validation order. It does not remove fixed fit-order confounding, establish
long-term drift, or identify causal phone differences. Confirming generalization
needs later participants and sessions. Preserve the user-requested active tracker
target and order behavior; these changes concern only the separate candidate.

### 3. Report accuracy before responsiveness

First present each target/sweep/session's signed X/Y bias, vertical and 2-D
error median/P95/max, exact-line and within-one-line fractions. Pair these with
received/eligible/null counts, off-text counts, full planned-time coverage and
empty blocks. Show both phones even when one is worse. Do not average thousands
of correlated frames into a fictitiously large independent sample count.

Add within-target dispersion around the target-wise coordinate mean (X/Y SD,
with the precise estimator stated), and signed bias change between the same
location's two sweeps. Call these observed variability and short-session bias
change: participant gaze movement and compliance remain mixed with tracker noise.
Show target-balanced spatial summaries with the number of contributing targets;
an empty target has unknown spatial error, not zero error. Never let a pooled
median conceal the target table or the missing targets.

Then report output-age distributions and unavailable intervals. For this
descriptive pilot, propose a predeclared 50/100/200/500 ms freshness sensitivity
table on the same immutable capture/output records. None is a pass threshold;
retain the full table rather than selecting the most favorable column. Spatial
errors must be identical across columns. A future analysis tool should state
the original recorded age parameter and each analysis override explicitly.
The frozen v1 scorer still accepts its original one declared limit. The later v2
scorer implements the exact four-column sweep without choosing a favorable
column; see `gaze-mgazenet-stationary-v2.md`. It has not been used on a personal
accuracy run.

No universal success percentage can be derived from the available evidence.
For a target centered in a band of height H, exact vertical assignment requires
`-H/2 <= dy < H/2`, plus the correct horizontal band; that is geometry, not
an approved aggregate acceptance rate. The first pilot can describe error and
failure mechanisms without assigning a pass. Use its development findings to
write concrete application acceptance limits, then freeze them before separate
confirmatory trials. Do not retrospectively claim the pilot passed those limits.

### 4. Reading and estimator comparison follow spatial feasibility

A matched baseline comparison needs the same rendered target task and comparable
viewing conditions, with each estimator's compatible fresh calibration. Reverse
estimator order across planned sessions. Preserve both raw spatial and full-path
operational results if their filtering differs. Historical E-054 percentages
remain context, not a matched control for the new harness.

Before claiming reading events, use a separate task/export retaining transition
and missing intervals, actual article geometry and scrolling, with the proposed
reading processor tested in isolation. Known target holds, forward moves and
backward moves can probe false assignments and operational response, but cue
time is not verified eye-movement onset. Without independent eye timing, do not
claim validated physiological fixation duration or saccade latency. Do not use
expected reading order, line snapping or scaffold response as ground truth.

## Integrity repair and verification from this review

The preceding 35-test checkpoint allowed all manifest fields to be removed,
causing a report marked `recorded` to skip the stricter checks. A changed target
position was also accepted if it remained inside the declared line. Both were
reproduced using invented data before repair.

The scorer now requires complete manifests for recorded reports and for reports
declaring the harness protocol. It reconstructs v1 fit and held-out positions,
checks fit/practice flags, stable line geometry, screen bounds and coordinate
separation independently of the predictions. It verifies internal consistency;
hashes do not authenticate a participant's behavior or prevent an author from
fabricating an entirely self-consistent file.

At this review checkpoint, all 37 host tests passed, with no skipped integrity test. The frozen Kotlin
synthetic export is now a source-tree fixture so the integrity suite runs without
a prior Gradle build. The current generated Kotlin export still passes the
independent scorer and produces exactly the prior metrics. APK and Android
source are unchanged in this review; the prior 130 JVM and both-phone setup
checks remain historical evidence and were not unnecessarily rerun.

No installation, device contact, camera use, calibration, staging, or commit
occurred in this review. The active tracker and all prior evidence are preserved.
The later v2 software checkpoint reports 138 passing benchmark JVM tests and 41
passing MGazeNet Python tests; those counts must not be attributed to this
earlier review run.

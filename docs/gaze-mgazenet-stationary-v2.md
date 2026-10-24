# MGazeNet stationary pilot v2 software checkpoint — 2026-09-08

Status: the proposed v2 stationary pilot is implemented and host-verified in the
isolated `mgazenet-benchmark` app. This checkpoint contains no participant data
and no accuracy result. No camera, personal calibration, phone installation,
active-tracker access, staging or commit occurred. The unrelated
`app/src/main/assets/study_articles.json` edit was preserved.

Read after `gaze-mgazenet-input-check.md` and
`gaze-mgazenet-protocol-review.md`. This file is the current continuation point.

## Frozen v2 collection contract

`AccuracySession.VERSION` is now `mgazenet_stationary_viewport_v2`. The source
fit remains unchanged: the same practice point, thirteen ordered fit points,
45 accepted rows per fit point, 585 training rows, source eye-area rule, and two
OpenCV RBF EPS-SVR fits. There is no validation refit, filter or correction.

Validation now has ten held-out grid locations:

`2, 8, 13, 15, 22, 24, 31, 33, 38, 44`

Each location occurs once in each of two sweeps under the same calibration.
The operator must explicitly select one preassigned order; neither is selected
by default:

- session-order plan 1: forward, then reverse;
- session-order plan 2: reverse, then forward.

Each record has twenty unique block IDs, a stable location ID shared across the
two repeats, grid index, sweep number, sweep direction, and within-sweep order.
The 3,000 ms settle, 2,500 ms measurement and 250 ms drain remain unchanged.
Empty, stopped, failed and completed blocks remain representable. Setup still
does not start the camera by itself.

The validation background now says `Reference text for target positioning.`
instead of instructing the participant to read at a usual pace. This removes the
conflict between background wording and the stationary-target instruction.

## Frozen v2 evidence and offline analysis contract

Complete and partial records use `mgazenet_accuracy_v2` and
`mgazenet_accuracy_partial_v2`. The numeric manifest and every block carry the
new order/location metadata. `max_output_age_ms` is deliberately null. The exact
predeclared sensitivity ages are `50, 100, 200, 500` ms in both the report and
its hashed calibration manifest; none is a threshold.

The independent scorer retains frozen v1 support and adds strict v2
reconstruction. It rejects a changed location set, block count, repeated-location
identity, sweep direction/order, block ID, grid index, timing, fit/test overlap,
or age table even when a changed manifest is rehashed.

The v2 output reports:

- per target/sweep signed X/Y error, vertical and 2-D median/P95/max,
  exact-line and within-one-line fractions, received/coordinate/null and
  off-text counts, output ages, and empty blocks;
- within-target X/Y sample standard deviation around the coordinate mean,
  using the `n-1` estimator and returning null when fewer than two coordinates
  contribute;
- session, sweep and repeated-location spatial summaries;
- second-sweep minus first-sweep signed mean-bias change for each location,
  retaining missing repeats as null rather than zero;
- target-balanced spatial summaries with planned and contributing-location
  counts; and
- four full planned-time availability views at 50/100/200/500 ms. Spatial
  errors occur once outside those columns, so selecting an age cannot change
  a spatial error statistic.

No gate or promotion decision is computed. The scope remains instructed
stationary points without independently verified fixation or natural-reading
ground truth.

## Software verification

Host verification completed on 2026-09-08:

- 138 benchmark JVM tests passed with no failures or skips;
- 41 MGazeNet Python tests passed;
- the debug benchmark APK and debug instrumentation APK assembled;
- Kotlin generated a complete synthetic v2 session with twenty blocks, one
  deliberately empty block, and no retained features;
- the independent Python scorer accepted that generated record and returned
  `mgazenet_accuracy_summary_v2`, twenty planned blocks, four sensitivity
  columns, and a null primary threshold;
- the frozen v1 Kotlin fixture and analytical v1 tests continued to pass;
- dedicated v2 tests verified both counterbalanced orders, location repetition,
  order-tamper rejection, age-contract rejection, empty blocks, sample-SD
  declaration, and repeated-location bias change.

The only build warning relevant to the new source was Android's existing
deprecation warning for `scaledDensity`; Java 8 target warnings are also
unchanged. No runtime or measurement failure was observed in these host-only
checks.

The distinct v2 package record, including the neutral presentation text, is:

- benchmark debug APK SHA-256:
  `8ac48a8e51d3c9e3939e5c146b95c0ae06b1426fc3b2b65325b95adfb574a611`;
- debug instrumentation APK SHA-256:
  `65d0dea2714a150d7ca07e7286876ee4706550f7980d92c94038817873463a8f`;
- generated Kotlin synthetic v2 record SHA-256:
  `c50297198f3c8bb271a9d3bb25a427a78f86d7469a6035c9e8b46040b43b5d86`.

The synthetic-record hash identifies a host artifact only. The APK pair was
subsequently installed for the authorized A56 camera-free check below; neither
APK was used for a camera or calibration run.

## Authorized camera-free layout check

### A56 / SM-A566B — passed 2026-09-08

The A56 (`R5CY40Y2C5W`) received the exact APK pair above. The isolated
`AccuracyHarnessTest` class completed all three tests in 2.657 seconds:

- opening the v2 accuracy setup kept the screen-on session flag unset, created
  no accuracy record, exposed exactly two order choices and left both unchecked;
- the actual inset target-view layout produced ten distinct held-out coordinates,
  repeated each location exactly twice across the sweeps, with no exact fit/test
  coordinate overlap, and acknowledged the physically drawn target; and
- opening the input-check setup remained camera-free and created no input-check
  record.

The tests never pressed Start or requested CAMERA. Afterward the effective
package camera app-op remained `ignore`. The benchmark and test packages were
force-stopped and neither process remained. Historical app-op output still lists
the earlier authorized 20-second input check; it is not evidence of camera use
by this v2 layout check.

### SM-G991B — passed 2026-09-08

After reconnection, the SM-G991B (`R5CR60YSJ2X`) received the same exact APK
pair. The same isolated class completed all three tests in 2.739 seconds. Its
actual inset target-view layout independently produced ten distinct held-out
coordinates, two identical coordinates per repeated location, and no exact
fit/test coordinate overlap. Accuracy setup again created no record, exposed
exactly two order choices with neither selected, and did not set the screen-on
session flag. Input-check setup also created no record.

The tests never pressed Start or requested CAMERA. The effective package camera
app-op remained `ignore`; the displayed `allow` timestamp/duration was historical
state from the earlier authorized 20-second input check. Both packages were
force-stopped afterward and neither process remained.

The camera-free layout boundary is therefore complete on both phones for the
exact hashed v2 package. This confirms the tested presentation geometry and
setup behavior on these two layouts. It does not measure gaze accuracy or
authorize a personal trial.

**Pilot collection follow-up:** after separate authorization, both sessions on
both phones completed with fresh calibrations under opposite predeclared order
plans. Their immutable numeric records, full target tables, fixed-age sensitivity
results and comparison are documented in
`gaze-mgazenet-stationary-pilot-results.md`. All are retained without a gate or
promotion decision. The sessions occurred in the disclosed severe-fatigue
context; the results file records why this is a validity limitation rather than
a basis for deleting or correcting them. A56 session 2 did not reproduce the
stronger session-1 result. CAMERA was revoked and the app was force-stopped
after every session.

## Completed collection boundary

The predeclared two-phone stationary pilot is complete. Do not run another
personal trial, repeat an unfavorable session, tune the scorer, select a
favorable freshness threshold, refit the model, pool sessions or promote
MGazeNet from this evidence. The next authorized activity is camera-free review
of the retained evidence. Any new software or measurement phase requires an
explicit bounded proposal and separate authorization. A software, geometry or
stationary-point result does not establish natural-reading accuracy.

The subsequent camera-free evidence review and bounded calibration-observability
proposal are recorded in `gaze-mgazenet-post-pilot-review.md`. That proposal is
historical to this v2 checkpoint. Its separately authorized implementation and
host verification are recorded in
`gaze-mgazenet-calibration-observability-v3.md`; that later checkpoint still
supplies no device or participant authorization.

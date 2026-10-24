# MGazeNet calibration-observability v3 collection protocol — 2026-09-09

Status: frozen before any v3 participant data. This camera-free protocol-design
checkpoint authorizes no phone contact, CAMERA grant, calibration or collection.
Read after `gaze-mgazenet-calibration-observability-v3.md`.

Collection follow-up: the frozen protocol was not changed after data began.
All four attempts and the deferred camera-free analysis are complete; continue
through `gaze-mgazenet-calibration-observability-v3-analysis.md`, then
`gaze-mgazenet-direct-validation-screen-protocol.md` for the frozen
development-only direct screen and its separately bounded confirmation design.

## Question and evidence class

The bounded question is whether the v3 leave-one-complete-target-group-out
diagnostic varies with independent post-fit stationary verification strongly
enough to justify later development of a calibration-quality screen. The
question is calibration/session repeatability, not framerate and not whether a
training residual is small.

The planned observations are exploratory development evidence from one
fatigued participant and two phones. They cannot establish population accuracy,
natural-reading accuracy, a device ranking, an accuracy gate or a promotion
decision. No numeric gate will be selected from, or applied to, these four
sessions. If the diagnostic is promising, any proposed threshold must be
defined afterward and then tested on separately authorized, prospectively
collected confirmation data.

## Frozen implementation

Use only `mgazenet_calibration_observability_v3` in the isolated
`mgazenet-benchmark` app. The signed app APK is frozen at SHA-256
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
The instrumentation APK used only for the completed camera-free native audit
has SHA-256
`b3d1e229f9b181faab93e503dd2ffa10870cf5dae3d5f6699f6fd0380c43621f`;
it is not a participant-collection requirement.

Recorded evidence must retain the expected MGazeNet model SHA-256
`2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96`
and face-localizer SHA-256
`64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`.
The independent scorer must reject a changed model, localizer, pipeline,
manifest, geometry, timing or SVR contract. Do not rebuild, reinstall or change
the package between sessions unless a camera-free integrity check shows that
the frozen app is absent; any required package change ends this protocol and
requires review before collection.

The implemented collection remains unchanged:

- one practice target followed by sixteen fixed row-major 4×4 fit targets at
  viewport fractions `.10`, `.3667`, `.6333`, `.90`;
- 1,500 ms settle, exactly 45 accepted 258-value rows per fit target, 500 ms
  wait and 30,000 ms target timeout;
- both eye polygon areas strictly above 10 pixels and all features finite;
- sixteen whole-target-held-out RBF SVR folds, each using the other fifteen
  complete groups/675 rows, followed by a final fit on all 720 rows;
- `fit_6` repeated after fitting, then held-out centre and four quarter-diagonal
  targets in the fixed implemented order;
- 3,000 ms verification settle, 2,500 ms measurement and 250 ms drain; and
- no filter, correction, refit, line snapping, active-tracker access or active
  calibration-store access.

Each complete record must remain numeric-only. No image, landmark, feature
vector or personal SVR model may be retained. A partial/failed record must use
the implemented partial schema and be preserved as an attempted session.

## Sessions and order

Collect exactly four started development sessions, each with a fresh v3
calibration. The order and labels are frozen:

| Sequence | Device | Run label |
| ---: | --- | --- |
| 1 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r1` |
| 2 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r1` |
| 3 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r2` |
| 4 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r2` |

This G991B→A56 then A56→G991B sequence alternates device order across rounds.
Do not swap it after seeing a result. Allow at least ten minutes away from the
target task between sessions and at least fifteen minutes between rounds.
Longer pauses and splitting the sequence across days are allowed; record the
actual interval and do not change the remaining order. There is no deadline,
and the participant decides when they feel ready enough to continue safely.

Every press of Start counts toward the four started sessions. Preserve complete,
stopped, timed-out, failed and empty-block outcomes. Never repeat a complete
session because its values look unfavorable. Do not automatically replace a
partial or technical failure. First retain it and perform a camera-free review;
any replacement requires an explicit prospective amendment and authorization.

## Participant and environment instructions

Use the same practical reference condition as the retained v2 pilot: no
glasses, approximately 30 cm viewing distance with ordinary small variation,
medium screen brightness, and the same lighted-room setup. Keep screen
brightness and room lighting unchanged within a round. Do not introduce a
chinrest, ruler, head restraint or deliberate pose sequence. Hold the phone in
a normal comfortable position and look at the red target rather than following
it by deliberately moving the head.

Before each Start, record without changing the app:

- local date/time and whether it is day or night;
- glasses status, approximate distance and screen-brightness setting;
- a plain-language description of room lighting;
- whether the participant reports the previously disclosed severe-fatigue
  context as unchanged, better or worse;
- any discomfort, interruption or material posture/environment change; and
- elapsed time since the preceding session.

These are context fields, not exclusion rules or correction inputs. The
participant has reported severe fatigue and roughly two weeks without good
sleep. A rested condition is not assumed or required, fatigue is not estimated
from the gaze record, and no result may be discarded, corrected or rerun because
of fatigue.

During each session, follow every red target. The practice point is not used for
fit. The sixteen fit targets train MGazeNet; the six later targets are
diagnostic-only. Do not intentionally look at a different location to challenge
the tracker. If continuing becomes uncomfortable or target compliance is no
longer reasonable, use Stop; retain the resulting partial record.

## Collection and handling boundary

A future collection authorization should cover one named session at a time.
Before each run, confirm the frozen build identity, CAMERA runtime grant
`false`, effective camera app-op `ignore`, and no running benchmark process.
Only the participant's explicit Start may request CAMERA. Opening setup alone
must remain camera-off.

After each attempted session:

1. close the camera, explicitly revoke CAMERA and set its effective app-op to
   `ignore`;
2. force-stop the app and instrumentation packages and confirm no benchmark
   process remains;
3. preserve the newly written raw numeric record without changing it and record
   its SHA-256;
4. record the declared conditions and outcome beside that immutable record; and
5. do not inspect, summarize or compare performance values until all four
   planned attempts are complete.

The app's visible complete/incomplete outcome and file presence may be checked
for safe preservation. Do not run the metric scorer early, inspect LOO or
verification coordinates, or use one session to coach, reposition, tune or
otherwise alter later sessions. A security/privacy failure, missing frozen build
or camera that cannot be re-denied stops collection for camera-free review.

## Frozen analysis

After all four attempts, validate each complete raw record independently with
`tools/gaze/mgazenet/calibration_audit_metrics.py`. Preserve raw and derived
SHA-256 values. Never edit a raw report to make it scoreable. Report incomplete
or rejected records as such rather than treating them as zero or omitting them.

The primary per-session internal diagnostic is:

`loo.target_balanced.mean_target_median.absolute_vertical_lines`

The primary independent stationary outcome is:

`verification.held_out_validation_target_balanced.mean_target_median.absolute_vertical_lines`

The five held-out validation targets define the outcome; do not mix the
`drift_repeat_fit_6` block into it. This primary outcome is available only when
its `contributing_target_count` is exactly five; otherwise report it as missing
rather than averaging the remaining targets. For each phone, report round-2
minus round-1 change for both primary values and whether their directions agree.
Also report the four-session rank association descriptively if all four complete
records have all five held-out targets, while stating that four observations
cannot support a stable correlation or inferential claim.

Predeclared secondary reporting is:

- every one of the sixteen LOO fold summaries, not only the best targets;
- every one of the six post-fit blocks, including null and empty blocks;
- target-balanced median/P95/max absolute X, absolute Y, Euclidean and vertical
  line-height errors, plus signed X/Y bias;
- the `fit_6` repeat separately as a non-correcting within-session drift
  observation;
- per-target collection time, rejection counts, accepted-sample output age and
  feature-dispersion scalar;
- verification coordinate/null counts and output-age distributions; and
- within-phone session differences with all four sessions kept separate.

Feature dispersion is input variation, not gaze error. LOO predictions omit a
whole spatial target but still come from adjacent frames within one ordered
calibration. Declared target position does not independently verify fixation.
Do not pool rows, average phones into one apparent sample, remove tails, exclude
targets, fit a correction, subtract signed bias, choose a favorable subset, or
reinterpret post-fit verification as natural reading.

## Camera-free freeze verification

The protocol was checked against the implemented session, report and independent
scorer constants. The scorer already exposes the five held-out targets through
`held_out_validation_target_balanced`, separately from the combined six-block
verification summary. A dedicated regression test now proves that an error
introduced only into `drift_repeat_fit_6` changes the combined summary but not
the held-out-only primary result. All 50 MGazeNet Python tests pass. No Android
source, APK, installed package, phone state or active-tracker file changed in
this protocol-freeze checkpoint.

## Interpretation and next boundary

Directional agreement between the internal and held-out primary measures on
both phones would be preliminary evidence that the diagnostic may be useful for
later threshold development. Disagreement on either phone is a retained failure
to replicate that ranking. Missing or invalid sessions make the four-session
comparison incomplete. These descriptions assign no pass.

After the frozen analysis, perform a camera-free evidence review. That review
may reject the diagnostic or propose a numerical calibration screen and a
separate confirmatory protocol. It may not promote MGazeNet, change the active
tracker, or authorize more data by itself. Any CAMERA grant, personal session,
replacement attempt, threshold-confirmation collection or active-system change
requires a new explicit authorization.

# MGazeNet direct-validation confirmation software checkpoint — 2026-09-09

Status: the separately authorized isolated confirmation software is implemented
and host-verified. Its exact production measurement layout now also passes the
camera-free checks on both phones. No participant
calibration or measurement was performed, and the active NewsMead tracker under
`app/` remains unchanged. Continue into
`gaze-mgazenet-direct-validation-confirmation-device-check.md`.

Read after `gaze-mgazenet-direct-validation-screen-protocol.md`. This checkpoint
implements that frozen design without changing its target positions, timing,
thresholds, session order or analysis decision rules.

## Implemented isolated flow

The new protocol ID and complete schema are both
`mgazenet_direct_validation_confirmation_v1`; stopped/failed records use
`mgazenet_direct_validation_confirmation_partial_v1`.

`ConfirmationSession` has one monotonic state path:

1. one practice point and the frozen row-major 4×4 NewsMead fit geometry;
2. exactly 45 admitted 258-value MGazeNet rows per fit target, producing 720
   training rows;
3. the same sixteen whole-target-held-out LOO audits, followed by one full
   two-output RBF EPS-SVR fit;
4. the five frozen direct-screen targets;
5. immediate in-memory sealing of the candidate screen; and
6. all twenty independent confirmation blocks: the ten v2 stationary text
   locations once in each selected counterbalanced sweep.

The five-target result is never displayed. It cannot refit, translate, correct,
repeat, abort or choose a later target. Both a passing screen and a deliberately
failing screen enter the same first confirmation block and preserve the same
twenty-block sequence. Once the final screen drain ends, the screen result and
its source blocks are sealed: a later output whose capture timestamp belongs to
the screen window is discarded rather than changing the decision. A completed
session is also immutable.

The screen implements exactly the frozen rule:

- all five targets contribute;
- every target has at least ten coordinate outputs;
- mean target-median absolute vertical error is at most 1.0 line; and
- the worst target median is at most 1.2 lines.

P95 is recorded but not thresholded. No LOO, drift, feature-dispersion, output-
age, horizontal or framerate threshold was added.

## Numeric record and privacy boundary

`ConfirmationReport` binds the pipeline and calibration manifest hashes,
physical screen/viewport/line geometry, all fit/screen/confirmation identities,
both timing contracts, selected sweep order, frozen screen rule, training
digest and SVR identity. It contains:

- target-isolated LOO predictions and numeric fit health;
- all five numeric screen blocks and the sealed result;
- all twenty independent numeric confirmation blocks with actual text-line
  geometry; and
- explicit false fields for active-tracker access, calibration-store access,
  retained camera frames, retained feature vectors, retained personal models
  and model correction.

Frames, crops, landmarks, 258-value feature vectors and the personal SVR are
not encoded. Training copies are zeroed after audit/fit and on terminal paths.
The top-level accuracy and confirmation gates remain null and promotion remains
`not_evaluated`.

`collect_confirmation.py` later copies only one explicitly named record through
the isolated package boundary. It verifies the device-written SHA-256, schema,
session ID, privacy, correction, hidden-result and null-decision fields without
scoring gaze values.

## Independent scoring and four-session decision

`confirmation_metrics.py` independently reconstructs and rejects changes to:

- manifests, hashes, pipeline/calibration identity and privacy fields;
- all 16 fit groups and 45-row LOO folds;
- five screen targets, windows, outputs, thresholds and sealed result;
- fit/screen/confirmation coordinate separation;
- the selected forward/reverse plan, ten location identities, two repetitions,
  twenty blocks and actual rendered line geometry; and
- timing monotonicity, correction or an injected decision.

It reports per-block signed bias, vertical/2-D error, exact-line/within-one-line,
off-text, output-age and availability fields; per-sweep and repeated-location
summaries; and the frozen independent vertical criteria. It labels false accepts
and false rejects but keeps the accuracy gate null and makes no promotion.

`compare_confirmations.py` requires all four frozen device/order labels and
distinct session/calibration identities. Any screen-pass/downstream-fail case
returns `reject`; at least one true acceptance and one true rejection with zero
false accepts returns `supports_next_proposal`; otherwise it returns
`inconclusive`. None of those values is model promotion or exact-line approval.

## Host verification

Final host verification completed with Java 17:

- 153 benchmark JVM tests in 9 suites passed with zero failures, errors or
  skips;
- 74 MGazeNet Python tests passed;
- the debug app and instrumentation APKs assembled successfully;
- Kotlin generated a complete 16-fit/5-screen/20-confirmation numeric synthetic
  record with no feature vectors;
- the independent Python scorer accepted it, reproduced the sealed screen and
  scored all independent locations while leaving promotion unevaluated; and
- `git diff --check` passed and `git diff --name-only -- app` was empty.

Host artifact identities:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug app APK | 32,847,143 | `95cfc1c457955131e0271ae81472afd66d90a5006556d98fd05d53570410e73d` |
| Debug instrumentation APK | 386,886 | `9e80c0c8d542be19a66ebcfe9e537475fb87200042838681eb1c0adef52b6bbb` |
| Kotlin synthetic complete record | 159,556 | `797cd2f19a8b5f2dd4adc9b7f8aad211fc311749b4331b160ccbb5bdabd5d311` |
| Independently scored synthetic summary | 139,877 | `b4043e2541d4ca6e8ce8b226d49733da3f146cf385c46e07665c797d3f2b4c36` |

The synthetic JSON files are ignored build products, not participant evidence
and not files to stage.

## Current authorization boundary

The software-only and separately authorized two-phone camera-free checks are
complete. Subsequent separately authorized collection also completed before
the frozen scoring. Continue from
`gaze-mgazenet-direct-validation-confirmation-analysis.md`; its inconclusive
result does not authorize model promotion or an active-tracker change.

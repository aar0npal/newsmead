# MGazeNet direct-validation screen and confirmation protocol — 2026-09-09

Status: the camera-free development decision is frozen. The reproducible
offline evaluator and Android confirmation flow are implemented and
host-verified. The subsequent camera-free actual-layout check passes on both
phones. No participant session is authorized by this document.

**Confirmation-software follow-up:** the separately authorized isolated flow is
now implemented and host-verified in
`gaze-mgazenet-direct-validation-confirmation-software.md`. Its device-check
continuation is
`gaze-mgazenet-direct-validation-confirmation-device-check.md`. The frozen
protocol and thresholds below were not changed.

Read after `gaze-mgazenet-calibration-observability-v3-analysis.md`. That
four-session development set rejected the leave-one-complete-target-group-out
(LOO) diagnostic as a calibration-quality screen. This document does not assign
LOO, drift, feature-dispersion, output-age or framerate thresholds.

## Decision and narrow claim

Continue MGazeNet only through a direct five-held-out-point screen. The screen
asks whether a freshly fitted calibration has adequate **vertical stationary
line-level accuracy** to justify continuing. It does not establish exact-line,
word, scrolling, natural-reading, population or production accuracy.

The screen passes only when all of the following are true:

1. The immutable record passes the complete v3 identity, geometry, privacy and
   no-correction contracts; all five distinct post-fit validation targets
   contribute and no validation block is empty.
2. Each validation target has at least ten admitted coordinate outputs during
   its fixed 2,500 ms measurement window.
3. The target-balanced mean of the five target-specific median absolute
   vertical errors is at most `1.0` physical text-line height.
4. No individual validation target's median absolute vertical error exceeds
   `1.2` physical text-line heights.

The `1.0` aggregate limit comes from the repository's existing line-level
engineering condition, `median vertical gaze error <= one text-line height`.
The `1.2` regional limit reuses the project's historical provisional
all-target reference as a conservative guard against an acceptable aggregate
hiding a failed region. Neither is represented as a literature-derived or
population-validated threshold. Ten outputs is a newly frozen measurement-
coverage floor for the fixed validation window, not a framerate goal.

P95 and maximum errors remain mandatory report fields but are not thresholded.
The five short blocks provide only a coarse tail estimate, and choosing a P95
limit from these four development sessions would imply more precision than the
design supports. Horizontal, 2-D, timing, missingness, feature-dispersion, LOO
and drift evidence also remain visible and cannot override a spatial failure.
They are not part of this vertical screen.

## Reproducible development classification

`tools/gaze/mgazenet/evaluate_direct_validation_screen.py` independently
reconstructs the two thresholded vertical quantities from the five block
summaries, checks them against the stored target-balanced aggregate, enforces
identity/count/distribution integrity and emits
`mgazenet_direct_validation_screen_candidate_v1`. Its output deliberately keeps
`accuracy_gate_pass` null and `promotion_decision` equal to `not_evaluated`.

Applying the newly frozen rule to the already-complete v3 reports is a
**retrospective development classification**, not confirmation:

| Session | Mean target median | Worst target median | Mean target P95 (report only) | Minimum n | Candidate screen |
| --- | ---: | ---: | ---: | ---: | --- |
| G991B R1 | 0.845 lines | 1.991 lines | 1.216 lines | 17 | Fail — regional guard |
| A56 R1 | 0.391 lines | 0.640 lines | 0.996 lines | 16 | Pass |
| A56 R2 | 1.793 lines | 2.360 lines | 2.260 lines | 17 | Fail — aggregate and regional guards |
| G991B R2 | 0.686 lines | 1.160 lines | 1.188 lines | 16 | Pass |

This result is useful because the regional rule prevents the G991B round-1
aggregate from concealing two poor lower validation regions, while the direct
screen also exposes the coherent A56 round-2 vertical failure that LOO missed.
The same reports were inspected while choosing the rule, so the 2/2 split must
not be described as predictive validation or a success rate. Do not tune a new
variant against these answers.

The four ignored local output files and SHA-256 values are:

| Session | Local output SHA-256 |
| --- | --- |
| G991B R1 | `133b7e9ec5b4a581ce511e22dd8c52f87fa0648881a4d39696d7085c1e0ca88d` |
| A56 R1 | `ed5af4a2efa377d6bafcdd95d9ee9d9b7b217c3adf1dcd7b74064bf04d2ba391` |
| A56 R2 | `bb8f1e437e44e76373aca1cfc19657740214d38e25338f8bda3293565de8208a` |
| G991B R2 | `6d366fc553347e1798997d74130e222c0e83786b60f51bd9cb5a825d9dd92839` |

They are stored as `direct-validation-screen.json` beside their source
summaries under
`diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/`. They
remain private, ignored evidence and must not be staged.

## Separate confirmation software contract

If separately authorized, implement a new isolated protocol version rather
than changing a completed v3 record or the active NewsMead tracker:

1. Opening setup remains camera-off and creates no record. Neither validation
   order is selected by default.
2. Use the same v3 practice target, sixteen ordered fit targets, 45 admitted
   258-value rows per fit target, two RBF EPS-SVRs and no correction.
3. Present the same five direct-screen targets in the same order. Compute and
   seal the candidate screen after the fifth block, but do not show its result,
   refit, translate, correct, repeat a target, abort or choose later data from it.
4. Under the same in-memory calibration, always continue to the independent v2
   stationary text locations `2, 8, 13, 15, 22, 24, 31, 33, 38, 44`, once in
   each of two counterbalanced sweeps. Preserve the v2 3,000 ms settle, 2,500 ms
   measurement and 250 ms drain timing and actual rendered line geometry.
5. Record the sealed screen result and all twenty confirmation blocks only
   after the terminal outcome. Retain complete, partial, stopped and failed
   attempts. Do not retain frames, crops, landmarks, feature vectors or the
   personal SVR; zero model inputs and close resources on every terminal path.
6. The scorer must reconstruct both the five-point screen and the independent
   ten-location/two-sweep result from the immutable numeric record. It must
   reject fit/screen/confirmation overlap, changed orders, missing manifests,
   duplicate identities, non-finite values and any correction or refit.

The screen and confirmation locations serve different roles even though they
share one calibration. The five screen targets choose a candidate status; the
twenty later blocks test that status at ten independent text-layout locations.
Because every session continues regardless of screen status and the result is
hidden until completion, a failed screen cannot selectively remove unfavorable
confirmation evidence.

## Frozen confirmation collection and analysis

After host tests, synthetic end-to-end verification, APK hashing and separate
camera-free checks on both actual layouts, confirmation still requires a new
explicit participant/CAMERA authorization. If authorized, retain exactly four
started fresh-calibration attempts in this sequence:

| Order | Device/round | Stationary sweep order |
| ---: | --- | --- |
| 1 | A56 confirmation 1 | forward, then reverse |
| 2 | G991B confirmation 1 | reverse, then forward |
| 3 | G991B confirmation 2 | forward, then reverse |
| 4 | A56 confirmation 2 | reverse, then forward |

This reverses the v3 starting device, balances forward-first/reverse-first
within each phone, and alternates device order across rounds. Permit at least
ten minutes away from the target task between sessions and at least fifteen
minutes between rounds; longer delays or different days are allowed. Record
the actual interval and declared viewing, lighting, glasses and fatigue context.
Fatigue, an attention lapse or an unfavorable result is context, not an
automatic exclusion or replacement. Do not expose or score any spatial result
until all four planned attempts have terminated and their hashes are preserved.

For each confirmation session, apply the same line-level criteria independently
to the ten confirmation locations after combining their two sweeps by location:

- all ten locations and both planned sweeps must contribute, with at least ten
  admitted coordinates per block;
- the target-balanced mean of location-specific median absolute vertical error
  must be at most `1.0` line; and
- no location-specific median may exceed `1.2` lines.

Also report every target/sweep's signed X/Y bias, median/P95/maximum vertical
and 2-D error, exact-line and within-one-line fractions, coordinate/null counts,
output age, off-text results, within-target dispersion and repeated-location
bias change. No exact-line or word pass threshold is assigned in this protocol.

The candidate screen supports proceeding only if the four immutable sessions
contain at least one true acceptance and one true rejection, and every session
that passes the five-point screen also passes the independent ten-location
vertical criteria. Any screen-pass/downstream-fail case rejects the screen.
If all screen statuses are the same, all downstream statuses are the same,
coverage is incomplete, or there is no true acceptance and true rejection, the
result is inconclusive. A false rejection is retained and reported; it does not
permit threshold tuning. Confirmation data may reject this frozen rule but may
not be used to revise and retest it within the same evidence set.

Even a successful four-session result would only support proposing the next
known-target reading/estimator comparison. One participant on two phones cannot
certify production reliability, exact-line reading, word selection, scrolling
or population generalization.

## Verification completed at this checkpoint

- All 61 MGazeNet Python tests pass, including four new direct-screen tests.
- Tests cover inclusive boundaries, independent aggregate, regional and sample-
  count failures, unchanged null promotion fields, duplicate identity, count,
  aggregate, decision and invalid-distribution rejection.
- The evaluator reproduced the four recorded target-balanced values from their
  individual held-out blocks and produced the classification table above.
- `git diff --check` passes and `app/` remains unchanged.

## Current authorization boundary

The software action, two-phone camera-free check and subsequent separately
authorized four-session collection are complete. Continue from
`gaze-mgazenet-direct-validation-confirmation-analysis.md`. The frozen result is
inconclusive and does not authorize model promotion or an active-tracker
change.

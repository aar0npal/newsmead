# Current NewsMead versus MGazeNet matched comparison protocol — 2026-09-10

Status: protocol `newsmead_current_vs_mgazenet_reading_v1` is frozen before any
new participant data. This document and its machine-readable manifest authorize
software preparation only. They do not authorize CAMERA, participant
calibration or collection. The existing result-revealing, preference-derived
reading setup must not be used for this comparison until the pre-collection
controls below are implemented and verified camera-free on both phones.

## Question and claim boundary

For this participant on the A56 and G991B, under the same known-target reading
task, does either exact production gaze implementation show a consistent
directional advantage in physical-line localization?

The comparison can describe this participant on these two phones. It cannot
establish population reliability, natural-reading intent, intervention benefit
or unrestricted production fitness. Word localization is reported separately;
it cannot be inferred from line accuracy. No framerate improvement can offset a
spatial or coverage failure.

## Frozen implementation identities

The two base implementations share application ID `com.newsmead`, version code
1 and the same debug signing certificate SHA-256
`3056e94475f68ae47cd7d25babf34eeb7d201acb97999f20eaa3ab95fa200332`.
They are installed sequentially with `install -r`; uninstalling, clearing app
data or granting permissions as part of installation is prohibited.

| Arm | Source identity | Base debug APK SHA-256 |
| --- | --- | --- |
| Current NewsMead | reference branch `gaze-pipeline-improvements`, commit `ec635fd01905544c8251c37b6891105ebbdee923` | `3c4019e9ae0390e1f9af05f262ba50ab4c71332ac9258aa4c9cc172e67c78e01` |
| MGazeNet primary | branch snapshot `bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22`; production integration introduced by `5811486` | `131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9` |

The current APK was built without switching branches from a `git archive` of
the exact reference commit. The archive SHA-256 is
`5c631be6d12892768b707d433eebbdab4c7f102f18738b2f9d21d19fe2518aca`.
The build completed with Java 17. The snapshot and APK remain under ignored
`tmp/current-reference-ec635fd/` and must not be staged.

The frozen machine-readable protocol manifest is
`tools/gaze/mgazenet/manifests/newsmead-current-vs-mgazenet-reading-v1.json`,
SHA-256 `41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185`.
The target/timing implementation in `ReadingValidationProtocol.kt` and the
reading layout are identical between the two base commits.

These hashes bind the estimator baselines. Comparison-mode changes must be
limited to protocol selection, record identity, monotonic observability and
result masking. Before collection, build both final comparison APKs, record
their exact hashes and base identities in a new immutable collection manifest,
and verify that the estimator, calibration, preprocessing, correction, AOI and
stabilizer code differs from each base only where this protocol explicitly
requires. The current reference branch itself remains unchanged. No runtime
current/MGazeNet backend selector is permitted.

## Required comparison-mode controls

Collection is blocked until both arms implement the same explicit comparison
mode and pass host, synthetic and camera-disabled layout checks:

1. Require protocol ID, slot ID and order variant as explicit inputs. Reject a
   missing or unknown value; do not derive comparison order from mutable app
   preferences.
2. Lock reading vertical alignment to `OFF`, reject an active persisted drift
   correction, and leave estimator output, AOI mapping and the existing target
   stabilizer unchanged.
3. Hide calibration spatial summaries and the final reading summary until all
   four slots and their hashes are preserved. A technically complete
   calibration is saved without result-driven Accept/Redo selection.
4. Bind every record to estimator ID, base commit, final APK SHA-256, protocol
   manifest SHA-256, slot, target-order variant, calibration fingerprint,
   device, physical layout and monotonic session clock.
5. Preserve delivered raw screen coordinates and stabilized AOI assignments for
   both arms. Record monotonic delivery times for the common gap analysis.
   MGazeNet additionally retains capture time, output age, invalid/stale events
   and busy drops. Do not invent unavailable current-arm capture times.
6. Keep setup camera-off and mutation-free. CAMERA starts only after the later
   participant authorization and an explicit Start action.
7. Retain completed, interrupted and failed slots. Do not expose metrics, repeat
   a target, refit, change order or replace a slot based on its result.

An offline scorer must be implemented and tested before collection. It must
reject wrong artifacts, altered manifests, duplicate/missing slots, changed
targets or timing, active correction, wrong coordinate space, incomplete
identity, non-finite values and impossible event order. Synthetic tests must
cover complete, partial, empty-target, wrong-build and mixed-result cases.

## Fixed four-slot design

Each slot contains one fresh compatible 16-point calibration immediately
followed by one complete reading validation. Estimator order is reversed across
phones; each estimator receives order A once and order B once, and both arms use
the same target order within a phone.

| Slot | Device | Estimator | Reading order |
| --- | --- | --- | --- |
| `a56_current_A` | SM-A566B | current NewsMead | A |
| `a56_mgazenet_A` | SM-A566B | MGazeNet | A |
| `g991b_mgazenet_B` | SM-G991B | MGazeNet | B |
| `g991b_current_B` | SM-G991B | current NewsMead | B |

Run the two A56 slots as one device pair and the two G991B slots as another.
Permit 15–45 minutes away from the task between arms in a pair and at least one
overnight interval between device pairs. Use no more than two slots per day.
Record actual interval, local time, lighting, orientation, glasses/correction,
approximate distance, posture, sleep hours and a pre-slot fatigue rating. If the
participant is not ready before either slot in a device pair, postpone the whole
pair. Once a slot starts, fatigue or an unfavorable result is context, never an
exclusion or reason for replacement.

The fixed device order above is not randomization. Reversing estimator order
across phones controls the largest order effect available in this single-
participant, two-device design; device and hardware effects remain visible and
must not be pooled away.

## Calibration and app-data isolation

Each arm must create a fresh calibration using its own unchanged compatible
procedure:

- Current NewsMead: telemetry OFF, all sixteen physical-screen fit targets, no
  excluded fit target, no nine-point affine correction and no reading-surface
  vertical correction. A fresh accepted calibration clears any saved drift
  correction before reading.
- MGazeNet: separate practice, sixteen row-major targets, 45 admitted finite
  258-value rows per target, pinned two-RBF-SVR fit, the six existing descriptive
  post-fit checks and no filtering or correction. Post-fit values do not select,
  refit or reject a technically successful calibration.

The current calibration remains in its legacy `files` namespace. MGazeNet
remains in `no_backup/mgazenet-v1/calibration.bin`, bound to model, localizer,
protocol, device and geometry. Before and after every in-place APK swap, record
hashes only: the inactive arm's calibration must remain byte-identical, and the
installed APK must match the scheduled arm. Never export the personal MGazeNet
SVRs, current calibration feature rows, frames, crops, landmarks or 258-value
features. Reading-coordinate JSONL is the only planned personal measurement
export after all four device-written hashes are preserved.

## Identical known-target task

Both arms use Reading Validation Protocol v4, the locked passage, 22 dp text,
physical-screen coordinates, existing line/word AOIs and existing stabilizer.
Comparison mode locks the following sequence:

1. One unscored dot preview, excluded from every endpoint.
2. Three reading-surface references with alignment OFF: 2,000 ms acquisition
   and 2,500 ms measurement each. They are observability data only.
3. Eight highlighted word fixations: 3,000 ms acquisition and 2,500 ms
   measurement each.
4. Six highlighted physical-line trials: 2,500 ms acquisition and 5,000 ms
   measurement each.

No instruction, timer, result or gaze dot is visible during a scored target.
The participant follows the highlighted target; expected order is ground truth
only for this constrained task and must not train, correct or snap predictions.

## Frozen endpoints and common denominators

The primary endpoint is the target-balanced mean of the six guided-line
target-specific median absolute stabilized line errors. Each delivered valid
coordinate in the measurement window contributes one integer absolute line
error; each target contributes equally regardless of output rate. Lower is
better. Empty targets remain null and make the slot spatially incomplete.

Required secondary results are:

- guided-line target-balanced exact-line and within-one-line fractions;
- word-fixation target-balanced median absolute line error, exact-line and
  within-one-line fractions;
- exact-word fraction for word trials, reported as a separate horizontal-plus-
  vertical outcome;
- per-target signed bias, median/P95/maximum absolute line error, worst target,
  raw versus stabilized assignment and off-text fraction;
- delivered-coordinate and invalid counts, initial gap, longest gap and
  delivery-time coverage at 50/100/200/500 ms sensitivity limits; and
- MGazeNet-only capture age and busy-drop summaries, labeled non-comparable
  where the current arm lacks the same observation.

Every one of the fourteen scored targets must have at least ten delivered valid
coordinates for a complete spatial comparison. This reuses the previously
frozen coverage floor; it is a measurement-validity condition, not a framerate
goal. No interpolation, carry across targets, stale replay, line snapping,
expected-order imputation or sample-count weighting is allowed. Full planned
measurement time remains the denominator for availability even when no output
arrives.

## Analysis and interpretation

Do not inspect or score spatial results until all four started slots have
terminated and their device-written hashes are preserved. The scorer reports
each slot, then paired MGazeNet-minus-current differences within each phone.
Errors use negative differences for MGazeNet improvement; accuracy and coverage
use positive differences for MGazeNet improvement. Do not pool frames across
targets, slots or phones as independent observations.

Classify the primary comparison as `consistent_mgazenet_direction` only if all
four slots meet coverage, MGazeNet has lower primary error on both phones, and
its guided within-one-line result is not lower on either phone. Apply the exact
symmetric rule for `consistent_current_direction`. Every other complete result
is `mixed`; any missing/failed slot is `inconclusive`. This is a directional
single-participant classification, not a superiority test or production gate.
Always report magnitudes, regional tails, word outcomes and availability beside
the classification. No result permits threshold tuning or replacement runs.

## Stop boundary

The next authorized work is software-only comparison-mode implementation,
offline scoring, exact-build hashing and camera-disabled checks. Stop again
before CAMERA or participant calibration. A later explicit authorization must
name the four-slot collection boundary. The known 4 KB-aligned native libraries
remain a separate 16 KB compatibility limitation and are not resolved by this
protocol.

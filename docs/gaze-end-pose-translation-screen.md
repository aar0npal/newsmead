# End-pose feature-translation screen — 2026-09-06

## Decision

Reject the fixed end-pose translation. It improves typical held-out vertical error in 10/13
calibrations, but worsens it in three and does not control tails: vertical maximum improves in only
7/13, both vertical median and maximum improve in only 5/13, and all selected vertical plus 2-D
median/maximum metrics improve together in only 2/13. The median session maximum worsens from
155.9 to 183.8 px.

This is a completed retrospective screen. It does not authorize a phone run, runtime change, scaled
variant, axis-specific variant, cap, or parameter sweep.

## Fixed hypothesis and method

Normal calibration already presents the same near-centre grid target twice: once during the 16-point
fit and once immediately before held-out validation. The candidate uses this target-independent raw
feature difference as an estimate of calibration-to-end drift:

`drift = second_same_target_feature - first_same_target_feature`

The complete two-axis drift vector is added once to every fit aggregate. The existing six-term ridge
quadratic is then fitted unchanged. Original held-out observations are mapped without modification.
The candidate does not use held-out answers, change target coordinates, select an axis, scale or cap
the drift, alter ridge strength, or search parameters.

The baseline mapper reproduces all 273 logged prediction pairs (13 × 16 LOO plus 13 × 5 held-out)
exactly at stored precision; maximum baseline parity difference is 0 px. Uniform translation leaves
LOO geometry mathematically unchanged apart from sub-millipixel Float rounding (maximum aggregate
metric difference 0.00054 px), so the decision comes from the five later held-out targets.

## Evidence set

All 13 accepted, complete `eye_local_width_average_v1` calibrations from September 1–4 are included.
This extends the earlier 11-session mapper set with both post-coordinate-repair A56 calibrations.
No session was excluded for high drift, poor accuracy, an unlabelled run, or later replacement.
These are repeated sessions from one participant and one phone, not independent participants.

Values below are held-out errors in px, deployed → candidate. With five points, nearest-rank P95 is
the maximum.

| Calibration | Vertical median | Vertical max | 2-D median | 2-D max | Logged high drift |
|---|---:|---:|---:|---:|---|
| `20260901_191457` | 97.4 → 85.9 | 155.6 → 236.8 | 179.4 → 131.4 | 194.0 → 258.7 | yes |
| `20260901_191802` | 122.1 → 133.0 | 224.7 → 188.7 | 138.9 → 154.7 | 271.1 → 242.2 | yes |
| `20260901_202803` | 59.6 → 47.3 | 117.7 → 102.2 | 66.2 → 78.5 | 119.7 → 105.2 | no |
| `20260901_204922` | 83.8 → 74.8 | 128.0 → 119.4 | 135.5 → 124.6 | 192.2 → 180.9 | no |
| `20260901_205322` | 141.6 → 110.8 | 156.9 → 183.8 | 147.4 → 137.8 | 204.8 → 186.3 | no |
| `20260901_210620` | 104.7 → 58.4 | 144.1 → 204.5 | 191.4 → 83.4 | 212.3 → 205.6 | yes |
| `20260901_230844` | 58.9 → 43.6 | 96.9 → 110.7 | 79.4 → 110.7 | 122.2 → 140.6 | no |
| `20260902_212424` | 146.0 → 139.9 | 282.4 → 289.1 | 148.8 → 145.9 | 292.3 → 301.2 | no |
| `20260903_011246` | 236.4 → 306.1 | 290.8 → 377.4 | 237.0 → 326.3 | 293.6 → 381.8 | no |
| `20260903_011422` | 73.7 → 71.9 | 155.9 → 133.0 | 95.0 → 126.1 | 159.6 → 159.8 | no |
| `20260903_210045` | 83.6 → 99.8 | 228.1 → 168.7 | 126.8 → 188.9 | 242.5 → 199.2 | no |
| `20260904_024210` | 307.5 → 122.3 | 602.7 → 329.3 | 311.1 → 191.5 | 603.1 → 356.2 | yes |
| `20260904_195333` | 65.4 → 57.3 | 152.0 → 145.4 | 163.4 → 158.7 | 193.8 → 226.2 | no |

Across sessions, median vertical median improves 97.4 → 85.9 px, but median vertical maximum
worsens 155.9 → 183.8 px. Median 2-D median improves only 147.4 → 137.8 px, while median 2-D
maximum is effectively unchanged/slightly worse at 204.8 → 205.6 px. The large improvement in the
poor September 4 02:42 session does not generalize and cannot justify choosing the candidate from
that favorable example.

## Interpretation and boundary

The repeated same-target measurement contains useful evidence that raw features move, but a single
global translation is not a reliable correction. Drift can be regional, nonlinear, or continue to
change after the repeated target. Applying the whole vector to all fit points trades typical error
against different target tails, reproducing the failure pattern of earlier global corrections.

No Android source, estimator, mapper, filter, calibration, saved gaze output, or installed app was
changed. The result does not evaluate reading-line accuracy because the held-out checks are
calibration-path aggregates rather than live reading samples.

## Reproduction

- Analyzer: `tools/gaze/screen_end_pose_translation.py`
- Synthetic tests: `tools/gaze/test_screen_end_pose_translation.py`
- Derived report: `gaze-end-pose-translation-screen-2026-09-06.json`
- All 27 offline gaze-analysis tests pass.
- Derived JSON SHA256: `60c40404cd0eac19c4f8d16c6fa0c3ebad1afc4315e50131aa0af426bccbb238`

Device payloads were read in memory through ADB and were not copied into the repository. The derived
JSON retains source filenames, sizes, SHA256 hashes, errors, and aggregate metrics, but not raw eye
features.

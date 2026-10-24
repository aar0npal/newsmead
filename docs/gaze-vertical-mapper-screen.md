# Vertical-only mapper screen — 2026-09-03

## Decision

Keep the current mapper. Removing all horizontal terms from its vertical output
worsens the leave-one-fit-point-out maximum in **11/11 calibrations** and the
separate held-out maximum in **8/11**. No session improves both maxima.

This closes one narrow candidate, not all mapper research and not the possibility
of improving the eye measurements. No Android code, calibration file, temporal
filter, target, sequence, affine correction, or installed app was changed.
No further phone run is requested for this candidate.

## Question and fixed candidate

The current vertical polynomial includes horizontal input, so movement in that
input can change predicted y. The hypothesis was that removing this coupling
would reduce inappropriate vertical movement. Coupling itself is not proof of a
bug: horizontal input can also compensate for gaze-angle-dependent eye geometry.

Compare exactly two models, without a parameter sweep:

- Baseline y: `b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy`.
- Candidate y: `c0 + c1*zy + c2*zy^2`.
- Both retain the baseline six-term x output unchanged.
- Population mean/std are fitted within each training fold; std floor is 1e-6.
- Ridge lambda is 1 for non-intercept coefficients; the intercept is unpenalized.
- Inputs and returned screen coordinates use Android Float precision; fitting
  uses double precision.
- Inside the training feature range, evaluate the polynomial. Outside it, use
  its boundary derivative and the existing `2.5*tanh(excess/2.5)` extension.
  The candidate has zero x derivative for its y output.
- No new feature normalization, gate, clipping, post-map offset, target-specific
  term, reading-answer fit, or outcome-dependent choice is introduced.

The runtime `GazeMapper.kt` is authoritative here; the older design document's
introductory 1.5-z statement is stale. This screen uses the actual 2.5-z scale.

## Inclusion and replay verification

Include all 11 app-private calibration records from September 1–3 using
`eye_local_width_average_v1`. All contain accepted 16-point quadratic fits,
five accepted validation points, and detailed telemetry OFF. None was dropped
for high error, an unlabelled run, an earlier confusing reading UI, or being
replaced by another calibration. In particular, September 3 01:12:46 is included
even though another accepted calibration replaced it before reading.

The earlier reading UI invalidates its reading experiment, not these complete
calibration records. Older eyelid-fraction calibrations are a different feature
generation and are outside this comparison. These 11 repeated calibrations are
from one participant/device, not 11 independent participants.

Fit only the 16 saved fixation aggregates. For leave-one-out, train separately on
15 and predict the excluded target, re-fitting normalization each time. For the
five separate validation targets, train on all 16. Never use validation targets
to fit the candidate. For repeat attempts, use final fit entries and the final
validation pass, with logged-baseline agreement required before scoring.

The baseline reproduces **all 176 logged LOO x/y residual pairs and all 55
held-out x/y prediction pairs exactly at stored precision** (231 coordinate
pairs, 462 scalar components; maximum difference 0 px). The analyzer refuses a
difference above 0.001 px. The candidate's x output is identical for every point.

New device payloads were read in memory, not archived into OneDrive. Only derived
errors, metrics, source filenames, byte counts, and SHA256 hashes are retained in
[gaze-vertical-mapper-screen-2026-09-03.json](gaze-vertical-mapper-screen-2026-09-03.json).

## All sessions

Values are absolute vertical errors in **pixels**, current -> candidate.
P95 is nearest-rank and equals the maximum with 16 LOO or five validation targets;
these are not two independent tail statistics.

| Calibration timestamp | LOO median | LOO P95 / max | Held-out median | Held-out P95 / max |
|---|---:|---:|---:|---:|
| `20260901_191457` | 100.8 -> 88.3 | 218.2 -> 281.5 | 97.4 -> 114.4 | 155.6 -> 191.7 |
| `20260901_191802` | 86.9 -> 65.5 | 208.9 -> 229.5 | 122.1 -> 115.6 | 224.7 -> 202.5 |
| `20260901_202803` | 35.2 -> 60.4 | 116.3 -> 232.3 | 59.6 -> 73.4 | 117.7 -> 195.2 |
| `20260901_204922` | 52.6 -> 70.5 | 318.2 -> 362.6 | 83.8 -> 42.0 | 128.0 -> 175.7 |
| `20260901_205322` | 102.1 -> 82.6 | 178.2 -> 238.2 | 141.6 -> 71.1 | 156.9 -> 280.7 |
| `20260901_210620` | 60.6 -> 80.3 | 177.6 -> 331.9 | 104.7 -> 98.6 | 144.1 -> 224.1 |
| `20260901_230844` | 86.0 -> 89.8 | 202.0 -> 324.5 | 58.9 -> 49.8 | 96.9 -> 173.3 |
| `20260902_212424` | 109.5 -> 123.7 | 286.0 -> 341.9 | 146.0 -> 157.2 | 282.4 -> 219.8 |
| `20260903_011246` | 77.0 -> 91.6 | 185.4 -> 344.4 | 236.4 -> 242.7 | 290.8 -> 366.9 |
| `20260903_011422` | 65.5 -> 39.4 | 151.2 -> 181.7 | 73.7 -> 55.4 | 155.9 -> 210.8 |
| `20260903_210045` | 65.8 -> 79.5 | 193.0 -> 322.3 | 83.6 -> 92.5 | 228.1 -> 193.4 |

Across sessions, the median LOO maximum increases **193.0 -> 322.3 px**.
The median held-out maximum increases **155.9 -> 202.5 px**. Session-mean
maxima also worsen (203.2 -> 290.1 and 180.1 -> 221.3 px respectively), so
the conclusion does not depend on choosing only the median aggregation.

Typical error is mixed: LOO medians improve in 4/11 sessions and held-out medians
in 6/11. The median of held-out medians improves slightly (97.4 -> 92.5 px), but
that small gain masks the worse tails. Held-out maxima improve in three sessions
(September 1 19:18, September 2 21:24, September 3 21:00); none also improves its
LOO maximum. All individual signed residuals are retained in the aggregate JSON.

## Regional checks

- In the calibration preceding `accuracy_v3` (September 1 21:06), top-left
  improves from 177.6 to 4.1 px absolute error. However, row 3/column 1 worsens
  from 144.9 to 331.9 px, and the held-out upper-right error grows from
  107.7 to 224.1 px. Fixing top-left alone would conceal another failure.
- In the calibration preceding the first v4 pair (September 2 21:24), the
  held-out maximum improves from 282.4 to 219.8 px, but the outer top-right LOO
  error worsens from 134.4 to 341.9 px.
- In the latest calibration (September 3 21:00), held-out maximum improves from
  228.1 to 193.4 px, while outer top-left worsens from 45.3 to 234.9 px and
  top-right from 193.0 to 322.3 px.

These are held-out prediction errors, not in-sample calibration fit residuals.
The five validation positions do not cover outer corners; inspect both
validation and grid-edge LOO before selecting a candidate.

## Interpretation and next action

Removing horizontal dependence is not supported by this screen. An inference
consistent with these results is that horizontal terms currently compensate for
some cross-axis geometry; this does not establish the physical cause or prove
that the current mapping is optimal.

This is **not** a replay of reading accuracy. Calibration records contain
aggregated raw features, not the complete live filtered stream. The reading logs
do not retain enough raw input to evaluate this replacement mapper. Errors here
are pixels, not observed exact-line/word accuracy; do not divide by a nominal
line height and present that as a reading result. Existing sessions are development
evidence, not a cross-participant test of generalization.

Next, leave the mapper in place and audit the upstream eye-measurement path for
why vertical separation changes between calibration and reading. Start with the
retained eye-local projection/normalization and the available aggregate evidence.
Do not revive the failed per-eye mapper, add posture coefficients from the fixed
target sequence, or request another phone repetition without a specific
testable change. Any proposed runtime intervention requires a separate decision;
none was implemented here.

## Reproduction and tests

- Analyzer: `tools/gaze/compare_vertical_mapper.py`.
- Synthetic tests: `tools/gaze/test_compare_vertical_mapper.py` (12 pass).
- Existing offset replay tests also pass (11; 23 total offline tests).
- Both Python and NumPy are required, as for the existing mapper comparison tool.
  The bundled Python runtime was used; the default shell Python lacks NumPy.
- Analyzer accepts explicit local calibration paths or, with `--adb`, exact
  app-private calibration filenames. Raw device inputs stay in memory. It prints
  derived JSON; it never writes, removes, or changes source recordings.
- The JSON lists every source needed to reproduce the 11-session comparison.

Example for an already-local calibration, using a Python environment with NumPy:

```bat
python tools\gaze\compare_vertical_mapper.py diagnostics-local\calibration_session_20260902_212424.json
python -m unittest discover -s tools\gaze -p test_compare_vertical_mapper.py -v
```

Tests cover agreement with the existing independent baseline implementation,
x-output preservation, y invariance to x input, unpenalized intercept, the
standard-deviation floor, boundary extrapolation, fold leakage, input
nonmutation, exact logged-prediction verification, malformed/partial records,
final attempts, percentiles/session weighting, source-path restrictions, and
duplicate-source rejection.

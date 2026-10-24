# Participant baseline deviation detector

Status: exploratory implementation on `measurement/mahalanobis`, updated 2026-10-19.
This is software behavior and a measurement plan, not a validated reading
difficulty or comprehension detector. The [branch audit](mahalanobis-replacement-audit.md)
records Scanpath removal and the existing feature definitions; the
[removal inventory](mahalanobis-removed-files.json) names deleted source and tests.

## Runtime and feature contract

The article's calibrated and smoothed MGazeNet observation enters the neutral
`ArticleReadingSession` for capture-aligned screen/text mapping. It supplies the
same `TextTarget`/line events to `ReadingFeaturePipeline` that the dedicated
baseline activity uses. `ReadingStateInferencer` retains the prior rules:
regression is a confirmed return at least one line behind the observed high-water
line, dwell is completed line residence of at least 120 ms, and F is a sustained
line stay after 300 ms. A gap closes pending residence at its last observed
capture and resets regression sequence history; cumulative event counts remain.

`RollingReadingFeatures` takes cumulative differences in one approximately
10-second span. R and F are events/minute; D is completed dwell milliseconds
divided by the same span, clipped to [0,1] as before. Evaluation is scheduled
every 500 ms on the elapsed-real-time clock. The actual start/end and 500 ms
maximum overhang are recorded. Partial windows are unavailable. A window is
invalid when the most recent gaze is off text/invalid/stale, the valid-sample
fraction is below 0.65, or continuity was broken. Each completed dwell is
credited when its line visit closes, so an ongoing visit may be absent and a
boundary-crossing visit can be credited late. These are line-based proxies, not
physiological fixations.

## Baseline and scoring

Spatial calibration precedes the separate Home → Baseline reading activity.
That activity uses the fixed, provisional stimulus copied from the existing
validation passage, without prompts or aids. A researcher enters a participant
code and explicitly starts capture. The baseline and article use the same
pipeline and quality rules. An explicit Finish fits the profile only when there
are at least 30 retained valid full windows and at least 60,000 ms of **union
coverage** across their intervals. The first full window needs 10 seconds, but
its interval includes those first 10 seconds, so continuous valid reading takes
at least about 60 seconds elapsed. Invalid/missing windows never contribute.
Insufficient duration/count, nonfinite data, or unusable covariance leaves no
profile. App restart, new participant session, or calibration change requires a
new baseline; an experimental article never refits the frozen profile.

For retained vectors x=[R,D,F], the collector calculates the sample mean μ and
full 3×3 sample covariance Σ (denominator n−1). It forms Σr=Σ+λI, with default
λ=1e−6 in the raw feature units. A cached Cholesky factor of Σr solves the
linear system; the score is `MD² = ||L⁻¹(x−μ)||²`, and `MD = sqrt(MD²)`.
Signed R/D/F differences are retained for diagnosis. Cholesky diagonal ratio
is logged as a matrix diagnostic, **not** as a condition number. No inverse,
weights, or one-sided clipping are used during scoring. Missing baselines and
invalid windows return explicit unavailable statuses and null distances.

The in-sample baseline MD² values are sorted. P90, P95, and P99 use linear
type-7 interpolation at `(n−1)p`. Default operating mode is exploratory P95;
P90, P99, and an explicit numeric custom threshold are supported. `MD² >=
threshold` marks an instantaneous candidate. The separate temporal controller
requires 1,500 ms of continuous high updates to confirm and 3,500 ms of valid
low updates to recover. Missing/out-of-order updates, gaps beyond 1,000 ms, and
threshold changes break continuity. Consecutive high updates form one episode
with start/end, duration, peak, mean, count and threshold. Default confirmed
support uses the existing line scaffold renderer; independent displaced-return
evidence can select its re-entry renderer. No extra MD² severity bands are
inferred. These timings and interventions are prototype settings, not validated
human thresholds.

Normal spatial scaffolds use a separate post-confirmation target gate. When the
temporal controller first confirms an episode, the renderer clears any prior
normal target and waits for gaze captured at or after that confirmation. A
mapped line must remain the candidate for 120 ms before it can be rendered. If
the mapped line changes, the old scaffold is cleared while the new line is
stabilized. Eligible capture-time gaze can then move the target immediately;
target movement does not wait for the next 500 ms Mahalanobis update. An invalid
target clears normal spatial support after a 200 ms grace period. These values
are centralized in `AdaptiveScaffoldConfig` through
`StudyConfig.MAHALANOBIS_SCAFFOLD_CONFIG`.

Re-entry remains explicit independent positional evidence: after at least 800
ms off text and a return at least two lines from the last stable line, confirmed
support may intentionally render that earlier stable line for the configured
evidence interval. A re-entry target therefore has a different logged status
from a normal current-gaze target. Mahalanobis confirms a time interval of
baseline deviation; it does not identify the causal line.

All parameters are centralized in `MahalanobisConfig`, exposed through
`StudyConfig.MAHALANOBIS_CONFIG`. The default baseline sampling mode is
`ALL_VALID_WINDOWS`; `THINNED_WINDOWS` and `NON_OVERLAPPING_WINDOWS` are
available. The mode, total/valid/retained/invalid counts and union coverage are
logged. Overlapping windows are autocorrelated and are **not independent
participant observations**. Selecting a different sampling mode may need a
longer reading session to meet the retained count/duration gates.

## Logging and offline replay

Private `mahalanobis_*.jsonl` files are written in `noBackupFilesDir`; export is
an explicit action from the article options or baseline activity. The profile
record includes config, μ, original and regularized Σ, λ, regularization flag,
Cholesky diagnostic, all baseline MD² values, P90/P95/P99, active threshold,
sampling mode/counts, and in-sample provenance. Baseline vector records contain
timestamp, actual window start/end, R/D/F, quality state, valid fraction and
valid/total sample counts. Every scoring record additionally includes mean and
signed difference per feature, directional labels, MD², MD, threshold/mode,
candidate and confirmed states, score/baseline/quality validity, persistence
state, and current scaffold. Candidate episodes are separate records. No camera
images or artificial labels enter these logs.

Each scoring record also stores target status; mapped, stabilized, current,
rendered, and re-entry line indices; target capture, detector confirmation, and
not-before timestamps; and target age. Material target decisions are separate
`scaffold_target_update` records with a reason such as waiting for a fresh
post-confirmation target, starting a line candidate, acquiring a fresh target,
invalidating a target, or using independent re-entry evidence. `reading_setup`
stores the scaffold timing and re-entry configuration once per session. This is
Mahalanobis log schema version 2.

Run recorded baseline and reading logs without the UI:

```powershell
./tools/gaze/run_mahalanobis_replay.ps1 -BaselineLog <baseline.jsonl> -InputLog <reading.jsonl> -OutputDirectory <output-dir>
```

The Gradle replay task requires the exact logged config, fits the baseline once
per P90/P95/P99 mode with the shared core, and exports update and episode CSVs.
Each update has a timestamp, MD² and `DETECTED`, `NOT_DETECTED` or `UNAVAILABLE`
state, plus candidate/quality fields. Those predictions can later be joined to
**independently annotated** episodes with a predeclared event-matching tolerance.
At that point reader-aware confusion matrices, precision, recall, specificity,
F1, false alerts per reading minute, misses and detection latency can be
calculated. The current replay makes no ground-truth labels and estimates none
of those validation metrics.

## Remaining research decisions

The baseline passage and duration need feasibility checks with the intended
readers; its current length may require rereading. Window overlap and in-sample
threshold estimation can make empirical percentiles optimistic. The regularized
matrix may be ill-conditioned despite a successful factorization, especially
with correlated line features or little variance. A high two-sided distance can
come from below-baseline R/D/F. Capture validity and on-text eligibility do not
establish coordinate accuracy. Choose operating thresholds and support policy
using separate annotated formative data, then evaluate on independent readers
and passages before claiming detection of reading instability or benefit.

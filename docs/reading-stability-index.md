# Reading State Index (RSI) Guide

This document explains how to interpret the `GazeRSI` logs produced by the NewsMead gaze-reading prototype.

RSI is a reading-effort signal. It is not a quiz score, comprehension score, medical measure, or final diagnosis of reading ability. It summarizes gaze behavior while a participant reads an article.

## Example Log

```text
GazeRSI: score=81.6 reg=1.00 dwell=0.95 fix=0.33 events=f15/d21354ms/r58
```

Read this as:

- `score=81.6`: composite reading-effort score from 0 to 100.
- `reg=1.00`: normalized regression component.
- `dwell=0.95`: normalized dwell-time component.
- `fix=0.33`: normalized fixation-rate component.
- `events=f15/d21354ms/r58`: raw event counts used to produce the score.

## Score Meaning

Higher RSI means the system detected more reading effort. Lower RSI means the reading pattern looked smoother.

| Score Range | Level | Interpretation |
| --- | --- | --- |
| `0-30` | Low effort | Reading appears smooth. Few rereads, short hesitation, and stable forward progress. |
| `31-60` | Moderate effort | Some pauses, rereading, or attention shifts. This can be normal depending on text difficulty. |
| `61-80` | High effort | Frequent pausing, rereading, or unstable movement across lines. The reader may be struggling or reading carefully. |
| `81-100` | Very high effort | Strong signal of difficulty, confusion, fatigue, distraction, or noisy gaze calibration. Inspect raw metrics before interpreting. |

Use the score as a flag for review. A high score tells the researcher to inspect the raw `GazeRSI` events and the calibration quality.

## Cumulative RSI Versus Adaptive Stability

The GazeRSI score described in this document is cumulative from the beginning of
the reading session and remains the value used for session reporting. It is not
used directly to select visual scaffold levels because an early difficult
episode could keep a cumulative score elevated after the reader recovers.

The adaptive interface instead derives recent regression, dwell, and fixation
rates from cumulative-counter deltas inside a 10-second window. These recent
metrics are normalized against a participant-relative baseline, combined with
the same weights, and smoothed before the scaffold controller sees them.

The current app automatically collects a 20-second per-article baseline as an
engineering fallback. The manuscript calls for a separate standardized baseline
phase; that profile still needs to be carried into the experimental articles
before main data collection. See adaptive-visual-scaffolding.md.

### Terminology decision

Use these names consistently in research notes, logs, and manuscript revisions:

- **Session RSI** is the cumulative `GazeRSI score` on the 0-100 scale. It
  describes detected effort across the article/session.
- **Adaptive Instability Index** is the recent participant-relative
  `GazeScaffold index` on the 0-4 scale. It selects the desired scaffold level.

Avoid calling both values simply RSI. Adaptive RSI may be used informally, but
Adaptive Instability Index is the preferred research term because the value is
not derived from the displayed cumulative score and uses a different scale.

For example, the following values describe two different time horizons:

```text
GazeRSI: score=47.6
GazeScaffold: index=2.12 raw=2.03 confidence=0.64 baselineReady=true
```

The first summarizes the session so far. The second describes recent deviation
from baseline; in this example it was high enough for the focus pathway, but the
0.64 confidence value blocked adaptation.

### Why Session RSI does not control scaffolds

A cumulative value has excessive memory for a temporary intervention. Difficulty
early in an article can keep Session RSI elevated after recovery, leaving support
on too long. Conversely, a new loss of place after a long stable period can be
diluted by the earlier history and trigger too slowly. Session RSI is also not
participant-baseline-relative.

Session RSI remains appropriate for end-of-session description, comparison of
articles or conditions, and analysis of overall reading effort. A short-window
0-100 score would still require personalization, smoothing, confidence gating,
and recovery logic; it would reproduce the role of the Adaptive Instability
Index under a different scale.

#### Alternative considered: direct Session RSI level mapping

Directly mapping the cumulative 0-100 Session RSI to scaffold levels was
considered and is not the current design. It would make the interface dependent
on when difficulty occurs. Early difficulty or tracker noise would decay slowly
and could keep support active after recovery. A late difficulty episode after a
long stable period could be diluted and trigger too slowly. Naturally slow
readers could also receive unnecessary support because Session RSI is not
normalized against their personal baseline.

The adaptive thresholds 1.0, 1.5, 2.0, and 2.5 cannot be reused for Session RSI
because the scales have different definitions. A direct controller would need a
new, pilot-validated set of 0-100 cutoffs. Values such as 0-30 NONE, 31-45 WORD,
46-60 LINE, 61-80 FOCUS, and 81-100 REENTRY are only an illustration of the
additional decision required; they are not validated, configured, or approved
for the study. REENTRY would still require independent loss-of-position evidence
rather than a high score alone.

A rolling 10-second RSI expressed on a 0-100 scale is a possible presentation
alternative, but without baseline normalization it is less personalized. Adding
participant normalization, smoothing, spatial-quality/confidence gates, and
recovery persistence would make it functionally equivalent to the existing
Adaptive Instability Index apart from its numeric scale.

### Exact Adaptive Instability Index calculation

Every 500 ms, after at least two seconds of recent observations, the estimator
uses cumulative-counter deltas inside the latest 10-second window to calculate
regressions per minute, dwell-time proportion, and fixations per minute. During
baseline it learns a mean and sample standard deviation for each metric.

After baseline, each recent metric becomes a positive z-score:

```text
z = max(0, (recent value - baseline mean) / max(baseline SD, SD floor))
raw index = clamp(0, 4, 0.40*zRegression + 0.35*zDwell + 0.25*zFixation)
```

The standard-deviation floors are 1.0 regression/minute, 0.05 dwell proportion,
and 5.0 fixations/minute. The raw index is exponentially smoothed with a
two-second time constant. That smoothed value is the logged `index` supplied to
the scaffold controller.

## How The Score Is Computed

The current implementation combines three normalized components:

```text
score = 100 * (
  0.40 * regressionComponent +
  0.35 * dwellComponent +
  0.25 * fixationComponent
)
```

Weights:

| Component | Weight | Why it matters |
| --- | ---: | --- |
| Regression | `40%` | Looking back to earlier lines is often the strongest sign of rereading or confusion. |
| Dwell | `35%` | Spending longer on lines suggests hesitation, careful reading, or processing difficulty. |
| Fixation | `25%` | Repeated stable fixations show the reader is stopping on text rather than smoothly scanning. |

### 4.3.4 Weight Justification

The component weights are ordered as `regression > dwell > fixation` because the three gaze features differ in how strongly they indicate reading instability. Regression receives the highest weight (`0.40`) because returning from a later line to an earlier line is the most direct behavioral sign of rereading. In normal forward reading, gaze should generally progress downward through the article; a confirmed backward movement suggests that the reader may be checking missed information, resolving confusion, or reprocessing a difficult phrase. For this reason, regression is treated as the strongest instability cue.

Dwell receives the second-highest weight (`0.35`) because prolonged time on a line can indicate hesitation, careful processing, or difficulty, but it is less specific than regression. A reader may dwell longer because the sentence is complex, because they are reading carefully, or because gaze tracking remains stable on one line. Therefore, dwell is important for detecting increased effort, but it is not weighted as strongly as regression because it does not always imply rereading or confusion.

Fixation receives the lowest weight (`0.25`) because fixation count is the most sensitive to individual reading style and tracker noise. Frequent fixations can reflect effortful reading, but they can also occur during normal slow reading, skimming interruptions, or small gaze-estimation fluctuations. Since fixation frequency is useful but less diagnostic on its own, it contributes to the final score with a smaller weight.

Overall, the weighting scheme prioritizes the metric with the clearest connection to reading instability, while still preserving dwell and fixation as supporting evidence. The values are heuristic prototype weights and should be treated as tunable parameters. In a formal study, they should be validated against pilot data, comprehension outcomes, or researcher-coded reading difficulty episodes.

## Raw Metrics

### Fixations: `f`

Example:

```text
events=f15
```

`f15` means 15 fixations were detected.

A fixation is counted when gaze stays on the same line for at least `300ms`.

Interpretation:

| Fixation Pattern | Possible Meaning |
| --- | --- |
| Low fixation count | Smooth reading, skimming, or weak gaze detection. |
| Moderate fixation count | Normal reading with occasional pauses. |
| High fixation count | Careful reading, difficulty, fatigue, or tracking instability. |

### Dwell Time: `d`

Example:

```text
events=d21354ms
```

`d21354ms` means the reader spent a total of `21.354 seconds` dwelling on detected text lines.

A dwell event is counted when the reader stays on a line for at least `120ms` before moving away.

Interpretation:

| Dwell Pattern | Possible Meaning |
| --- | --- |
| Low dwell | Fast reading, skimming, or gaze not landing consistently on text. |
| Moderate dwell | Normal line-by-line reading. |
| High dwell | Careful reading, hesitation, difficult text, distraction, or poor calibration. |

### Regressions: `r`

Example:

```text
events=r58
```

`r58` means 58 regressions were detected.

A regression is a confirmed move from a later line back to an earlier line, for example:

```text
line 21 -> line 18
```

The current logic filters out small gaze jitter. A regression only counts when:

```text
the gaze moves back to an earlier line
and stays on that earlier line for at least 300ms
```

Interpretation:

| Regression Count | Short Reading Sample Interpretation |
| --- | --- |
| `r0-r5` | Normal or smooth reading. |
| `r6-r15` | Some rereading or careful checking. |
| `r16-r30` | Heavy rereading, possible difficulty, or unstable gaze. |
| `r30+` | Suspicious. Check calibration, gaze jitter, and whether the article was long. |

High regression counts can mean real rereading, but they can also mean the gaze dot is jumping between lines because calibration is poor.

## Component Fields

### `reg`

Regression component:

```text
regressionComponent = regressionCount / linesRead
```

It is capped at `1.0`.

A high `reg` value means regressions are frequent relative to how far the reader has progressed in the article.

### `dwell`

Dwell component:

```text
dwellComponent = totalDwellMs / elapsedMs
```

It is capped at `1.0`.

A high `dwell` value means most of the session time was spent dwelling on detected lines.

### `fix`

Fixation component:

```text
fixationRatePerMinute = fixationCount / elapsedMinutes
fixationComponent = fixationRatePerMinute / 120
```

It is capped at `1.0`.

A high `fix` value means the reader is producing many stable fixations per minute compared with the current reference rate of `120 fixations/minute`.

## Practical Interpretation

Use these combinations when reviewing logs:

| Pattern | Likely Meaning |
| --- | --- |
| Low score, low `r`, moderate `dwell` | Smooth reading. |
| Medium score, moderate `dwell`, low `r` | Careful but stable reading. |
| High score, high `dwell`, low `r` | The reader may be pausing or processing difficult text without rereading much. |
| High score, high `r` | Rereading, confusion, or calibration jitter. Inspect gaze overlay and line logs. |
| Very high score with `r30+` | Treat as suspicious until calibration quality is confirmed. |
| Low score with very low raw events | The tracker may not be detecting gaze reliably. Check `GazeAOI` and overlay behavior. |

## Calibration And Noise Caveats

RSI depends on gaze quality. Before interpreting a high score, check:

- The red gaze dot follows the reader's approximate gaze location.
- `GazeAOI` line numbers move mostly forward during normal reading.
- Regressions are not caused by rapid line bouncing.
- Phone and participant posture stayed stable after calibration.
- The phone remained within the laptop screen tracking area.
- Lighting and face visibility were stable.

A score like this is suspicious:

```text
score=81.6 reg=1.00 dwell=0.95 fix=0.33 events=f15/d21354ms/r58
```

It indicates very high effort, but `r58` is high enough that the first interpretation should be: check calibration and jitter before claiming the reader was confused.

## Circular Measurement Contamination

RSI and the adaptive scaffold currently consume the same estimated line stream.
Spatial jitter can therefore create artificial line changes, regressions,
fixations, and dwell events that increase the score or recent adaptive index.
The same error can then place a triggered scaffold on the wrong location. A
successful trigger is not independent evidence that genuine reading instability
occurred.

The current adaptive confidence value only measures whether gaze landed
somewhere on visible text. It does not establish that the correct line or word
was identified. RSI analysis must therefore be paired with synchronized raw
gaze, stabilized line, calibration-quality, tracking-loss, and scaffold-target
records. Where possible, compare inferred events against researcher-coded video
or an independent eye-tracking reference.

Before inferential use, quantify vertical error in line-height units, exact-line
and within-one-line accuracy, within-target dispersion, jump rate, off-text rate,
and drift over time. Freeze acceptable limits from pilot data. If these limits
are not met, report RSI as an exploratory tracker-derived signal rather than a
validated measure of participant reading instability.

## Recommended Reporting Language

Use cautious language in reports:

- Good: "The RSI score suggests increased reading effort."
- Good: "The high regression count may indicate rereading or gaze instability."
- Good: "This segment should be reviewed with calibration quality and raw line logs."
- Avoid: "The reader definitely did not understand the text."
- Avoid: "The score is a comprehension grade."

Reports should explicitly state when tracker noise may have contributed to both
the inferred instability and scaffold location. A scaffold transition must not
be described as proof that the reader became unstable.

## Summary

- Low RSI: smoother reading pattern.
- High RSI: more detected reading effort.
- High `r`: possible rereading, but also possible tracking noise.
- High `dwell`: longer time spent on lines.
- High `fix`: more frequent stable stops on text.
- Always interpret RSI together with raw events, article length, session duration, and calibration quality.

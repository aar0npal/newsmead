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

## Recommended Reporting Language

Use cautious language in reports:

- Good: "The RSI score suggests increased reading effort."
- Good: "The high regression count may indicate rereading or gaze instability."
- Good: "This segment should be reviewed with calibration quality and raw line logs."
- Avoid: "The reader definitely did not understand the text."
- Avoid: "The score is a comprehension grade."

## Summary

- Low RSI: smoother reading pattern.
- High RSI: more detected reading effort.
- High `r`: possible rereading, but also possible tracking noise.
- High `dwell`: longer time spent on lines.
- High `fix`: more frequent stable stops on text.
- Always interpret RSI together with raw events, article length, session duration, and calibration quality.

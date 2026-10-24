# Manuscript-Implementation Alignment Guide

## Purpose and status

This is the living insertion guide for keeping the thesis manuscript aligned
with the implemented research prototype. Add future implementation decisions,
manuscript wording, formulas, methodological consequences, limitations, and
traceability notes here as the system evolves.

The first recorded alignment topic is the distinction between the cumulative
Session Reading State Index and the recent Adaptive Instability Index used for
visual scaffolding.

**Section 4.3.2 (Gaze Tracking)** — calibration, the gaze mapping model,
calibration quality assurance, and drift correction — is drafted separately in
`docs/calibration-methods.md`, following the same conventions.

These revisions are required because the manuscript must not imply that the
cumulative 0-100 RSI directly selects scaffold levels. The actual implementation
uses a participant-relative recent-window signal. The current adaptive thresholds
and timing values are provisional engineering parameters and must be validated
and frozen using pilot data before main data collection.

## Required manuscript changes

1. Update Section 4.3.4 to define the existing cumulative score as Session RSI.
2. Add Section 4.3.4.1 for the Adaptive Instability Index.
3. Update Section 4.3.5 so scaffold levels explicitly use the Adaptive
   Instability Index rather than Session RSI.
4. Separate the two signals in the methodology, variables, logging, and data
   analysis sections.
5. Add the gaze-quality and circular-measurement-contamination limitation.
6. Record direct Session RSI level mapping as an alternative considered but not
   adopted.

## Section 4.3.4: Session Reading State Index

Retain the existing component weights and add or revise the text as follows:

> The Session Reading State Index is a cumulative measure of detected reading
> effort across the current article-reading session. It combines regression,
> dwell, and fixation components on a 0-100 scale using weights of 0.40, 0.35,
> and 0.25, respectively. Higher values indicate that the gaze-derived reading
> pattern contains more behaviors associated with rereading, hesitation, or
> effort. Session RSI is a descriptive research outcome; it is not a
> comprehension score and does not directly select visual-scaffolding levels.

Use the implemented formula:

```text
Session RSI = 100 * (0.40R + 0.35D + 0.25F)
```

The normalized cumulative components are:

- `R`: confirmed regression count divided by lines reached, bounded to 0-1;
- `D`: accumulated dwell time divided by elapsed session time, bounded to 0-1;
- `F`: fixation rate divided by the reference rate of 120 fixations/minute,
  bounded to 0-1.

Add the controller rationale:

> A cumulative measure is unsuitable for immediate adaptation. Difficulty or
> tracking noise early in an article may keep Session RSI elevated after the
> reader recovers, while a new difficulty episode late in a long stable session
> may be diluted by earlier history. Session RSI is also not normalized against
> each participant's normal reading pattern. It is therefore retained for
> end-of-session description and comparison of articles or experimental
> conditions rather than real-time scaffold selection.

## New Section 4.3.4.1: Adaptive Instability Index

Insert a subsection immediately after Session RSI:

> The Adaptive Instability Index is a recent, participant-relative measure used
> for real-time scaffold selection. It is computed from regression rate, dwell-
> time proportion, and fixation rate within a rolling 10-second window. Each
> recent metric is standardized against the participant's baseline, and only
> positive deviations associated with increased effort are retained.

Define each positive standardized deviation as:

```text
z+ = max(0, (recent metric - baseline mean) / max(baseline SD, SD floor))
```

Then define the raw Adaptive Instability Index:

```text
AIIraw = clamp(0, 4, 0.40zR+ + 0.35zD+ + 0.25zF+)
```

The implemented standard-deviation floors are 1.0 regression/minute, 0.05 dwell
proportion, and 5.0 fixations/minute. Metrics update every 500 ms after at least
two seconds of recent observations. The raw result is exponentially smoothed
with a two-second time constant; the smoothed value is the Adaptive Instability
Index supplied to the scaffold controller.

The current prototype collects an automatic baseline during the first 20
seconds of each article. This is an engineering fallback. The final study should
collect the standardized participant baseline described by the manuscript,
persist the resulting mean and standard deviation for each metric, and reuse the
profile across experimental articles.

## Section 4.3.5: Adaptive Visual Scaffolding

Replace any statement that maps the cumulative RSI directly to scaffold levels
with the following:

> Visual-scaffolding levels are selected using the smoothed Adaptive Instability
> Index rather than the cumulative Session RSI. The controller applies only one
> intervention at a time and escalates or withdraws support conservatively to
> avoid rapid visual oscillation.

Use the current implemented mapping:

| Adaptive Instability Index | Desired state |
| ---: | --- |
| less than 1.0 | No scaffold |
| 1.0 to less than 1.5 | Level 1: word highlighting |
| 1.5 to less than 2.0 | Level 2: line emphasis |
| 2.0 to less than 2.5 | Level 3: five-line focus window |
| at least 2.5 with loss evidence | Level 4: re-entry support |
| at least 2.5 without loss evidence | Level 3: five-line focus window |

Also state the controller conditions:

- Baseline collection must be complete.
- At least 65% of gaze samples in the recent window must land on visible article
  text. This is a coverage gate, not proof of correct-line accuracy.
- Elevated behavior must persist for 1.5 seconds before escalation.
- Recovery must persist for 3.5 seconds before withdrawal.
- Escalation and withdrawal proceed one level at a time.
- Level 4 additionally requires at least 800 ms off text followed by a return at
  least two lines from the last stable line.

Add this qualification:

> The thresholds 1.0, 1.5, 2.0, and 2.5 and the current persistence values are
> provisional engineering parameters. They must be tuned and frozen using pilot
> data with researcher-coded reading episodes and tracker-error periods excluded
> or separately labeled.

## Methodology and data-analysis revisions

Define and analyze the signals separately:

| Variable | Role | Scale and horizon |
| --- | --- | --- |
| Session RSI | Descriptive outcome for participant, article, or condition comparison | cumulative 0-100 |
| Adaptive Instability Index | Time-varying process variable controlling support | smoothed 0-4, recent 10-second behavior |
| Scaffold transition | Timestamped intervention event | NONE, WORD, LINE, FOCUS, or REENTRY |
| Recovery latency | Time until stable reading/support withdrawal | milliseconds |
| Gaze coverage confidence | Quality gate based on samples on visible text | 0-1 over recent window |

The two numeric scales must not be pooled or treated as interchangeable. Session
RSI may be analyzed as an overall outcome. The Adaptive Instability Index should
be analyzed as a time series together with transition timestamps, recent raw
metrics, confidence, gaze quality, and recovery events.

The study records should retain synchronized:

- raw gaze coordinates;
- raw and stabilized line/word targets;
- fixation, dwell, and regression events;
- cumulative Session RSI;
- raw and smoothed Adaptive Instability Index;
- gaze-quality measurements;
- scaffold state and rendered target;
- disruption and recovery markers where used.

## Required limitation: gaze measurement and circular contamination

Add the following to the manuscript limitations:

> The Adaptive Instability Index and scaffold location depend on the same gaze-
> estimation stream. Tracking noise may therefore create artificial line
> changes, regressions, fixations, or dwell events that increase the inferred
> instability while simultaneously placing the intervention on an incorrect
> word or line. This circular measurement contamination limits both instability-
> detection validity and intervention-placement validity. A successful scaffold
> transition cannot, by itself, prove that genuine reading instability occurred.

Clarify the confidence limitation:

> The current confidence value measures the proportion of recent samples that
> land somewhere on visible article text. It does not measure distance from true
> gaze, correct-line accuracy, dispersion, head stability, or drift. A gaze
> estimate on the wrong line can therefore count as valid.

Before participant-facing claims, report median and 95th-percentile vertical
error in pixels and line-height units, exact-line and within-one-line accuracy,
word-selection accuracy, dispersion, jump rate, off-text rate, tracking loss,
and drift across scrolling and session time. If word or exact-line accuracy is
insufficient, revise the intervention hierarchy toward a broader region/focus
scaffold or replace the gaze provider.

## Alternative considered: direct Session RSI control

Record the design alternative as follows:

> Direct mapping of cumulative Session RSI to scaffold levels was considered but
> not adopted. Its historical accumulation can preserve early difficulty after
> recovery, dilute a late difficulty episode after prolonged stable reading,
> and apply non-personalized absolute cutoffs to readers with different normal
> reading patterns. Direct control would also require a new set of empirically
> validated 0-100 thresholds; the current Adaptive Instability Index thresholds
> cannot be converted directly to Session RSI ranges. A high Session RSI would
> not replace the independent loss-of-position evidence required for Level 4.

Do not present illustrative Session RSI bands as study parameters. A rolling
10-second RSI on a 0-100 scale is a possible display convention, but after adding
participant normalization, smoothing, quality gates, escalation persistence,
and recovery logic, it is functionally the Adaptive Instability Index under a
different numeric scale.

## Manuscript consistency checklist

- Use **Session RSI** only for the cumulative 0-100 score.
- Use **Adaptive Instability Index** for the recent 0-4 scaffold-control signal.
- Do not use Adaptive RSI as the primary formal term.
- State explicitly that neither value is a comprehension score.
- Ensure all scaffold-level tables use the Adaptive Instability Index.
- Mark thresholds and timing values as provisional until pilot validation.
- Keep Level 4 conditional on loss-of-position evidence.
- Separate coverage confidence from spatial accuracy.
- Include circular measurement contamination in limitations.
- Make word and exact-line claims conditional on target-device and target-
  population validation.

## Implementation traceability

The manuscript descriptions correspond to:

- `ReadingStateInferencer.kt`: Session RSI and cumulative event counters;
- `WindowedStabilityEstimator.kt`: recent metrics, baseline normalization, raw
  and smoothed Adaptive Instability Index, and coverage confidence;
- `AdaptiveScaffoldController.kt`: level thresholds, persistence, withdrawal,
  and re-entry evidence;
- `GazeOverlayView.kt`: word, line, focus, and re-entry rendering;
- `StudyConfig.kt`: current study parameters and validation modes.

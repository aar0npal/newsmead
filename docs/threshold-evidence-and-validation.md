# Adaptive Threshold Evidence and Validation Plan

## Decision status

The current Adaptive Instability Index thresholds and controller timing values
are provisional engineering hypotheses. Literature supports the direction of the
selected gaze features and the use of participant-relative adaptation, but no
identified study validates NewsMead's exact `1.0`, `1.5`, `2.0`, and `2.5`
scaffold boundaries.

The final thresholds must be estimated with a formative pilot, frozen before
main data collection, and reported as empirically selected study parameters.
Until that work is complete, the implementation must not be described as using
literature-validated cutoff values.

## Current parameter inventory

| Parameter | Current value | Evidence status |
| --- | ---: | --- |
| Level 1 WORD boundary | `1.0` | Engineering hypothesis; no direct cutoff evidence found |
| Level 2 LINE boundary | `1.5` | Engineering hypothesis; no direct cutoff evidence found |
| Level 3 FOCUS boundary | `2.0` | Engineering hypothesis; no direct cutoff evidence found |
| Level 4 REENTRY boundary | `2.5` plus loss evidence | Engineering hypothesis; no direct cutoff evidence found |
| Recent behavior window | `10 s` | Indirect support from real-time workload research, not reading-specific validation |
| Escalation persistence | `1.5 s` | Plausible relative to published gaze-trigger timing, not validated for this index |
| Recovery persistence | `3.5 s` | Engineering choice; no direct evidence found |
| Valid-text confidence gate | `0.65` | Engineering choice; measures coverage, not spatial accuracy |
| Off-text loss period | `800 ms` | Operational prototype definition; no direct evidence found |
| Re-entry line displacement | `2 lines` | Operational prototype definition; no direct evidence found |
| Re-entry evidence lifetime | `10 s` | Engineering choice; no direct evidence found |

## What primary studies support

### The selected gaze features are relevant but not specific

Reading difficulty and visually degraded text can be associated with longer and
more numerous fixations and additional regressions. However, older readers also
show more and longer fixations and more regressions while maintaining normal
comprehension. This supports using fixation, dwell, and regression as candidate
signals, but it argues against treating fixed raw values as universal evidence of
difficulty. See [Adult Age Differences in Effects of Text Spacing on Eye
Movements During Reading](https://doi.org/10.3389/fpsyg.2018.02700).

Individual differences in several reading eye-movement effects can have poor
split-half reliability. Participant-level effects should not be assumed stable
without measurement and adequate sampling. This supports baseline-relative
analysis while also requiring reliability checks for the baseline itself. See
[How reliable are individual differences in eye movements in
reading?](https://doi.org/10.1016/j.jml.2020.104190).

### Trigger values are commonly empirical and adjustable

GazePrompt used fixation duration, refixation count, and total fixation duration
to trigger difficult-word assistance. Its formative participants reported both
late triggers and excessive triggering, so the researchers made timing
thresholds adjustable. Participant-selected first-fixation thresholds shown in
the study appendix ranged from approximately 450-650 ms, while total-fixation
thresholds ranged from approximately 1,250-2,250 ms. The study supports
behavior-based intervention and personalization; it does not establish a
universal threshold for NewsMead's composite index. See [GazePrompt: Enhancing
Low Vision People's Reading Experience with Gaze-Aware
Augmentations](https://doi.org/10.1145/3613904.3642878).

See Where You Read detected jump reading after gaze remained outside the expected
line region for 2.5 seconds of accumulated active gaze. The value was paired with
an empirical gaze-error model collected from 16 users. The study reported that
centimeter-scale gaze error can greatly exceed millimeter-scale line spacing,
supporting empirical error modeling rather than direct point-to-line trust. See
[See Where You Read: Reading Tracking via Eye Gaze Tracking and Large Language
Model](https://doi.org/10.1145/3803853).

### A 10-second window is plausible but indirect

A driver cognitive-load study selected a 10-second eye-tracking window as a
balance between reliable feature extraction and temporal responsiveness. This
provides an engineering precedent, not reading-specific validation. Window size
must be compared empirically in the NewsMead pilot. See [Classification of Driver
Cognitive Load: Exploring the Benefits of Fusing Eye-Tracking and Physiological
Measures](https://doi.org/10.1177/03611981221090937).

## What the literature does not establish

The reviewed studies do not provide evidence that:

- an index of `1.0` uniquely represents mild reading instability;
- `1.5`, `2.0`, and `2.5` separate line, focus, and re-entry needs;
- 1.5 seconds is the optimal escalation delay;
- 3.5 seconds is the optimal recovery delay;
- 65% on-text coverage is sufficient tracking quality;
- 800 ms off text plus a two-line return uniquely identifies loss of position;
- the current 0.40/0.35/0.25 weights are optimal for older adults or this device.

These values must therefore be presented as parameters to validate, not findings
derived from prior research.

## Mathematical interpretation warning

The three component deviations are positive z-scores, but the combined Adaptive
Instability Index is not itself a standard z-score:

```text
AIIraw = clamp(0, 4, 0.40zR+ + 0.35zD+ + 0.25zF+)
```

The components are truncated at zero, correlated, calculated from overlapping
windows, weighted, capped, and then exponentially smoothed. Therefore an index of
`2.0` must not be described as exactly two standard deviations above baseline or
assigned a standard-normal percentile. Use **weighted z-derived index units** in
technical descriptions.

The current 20-second baseline also contains overlapping measurements updated
every 500 ms. Those observations are autocorrelated rather than independent.
Baseline duration and reliability must be tested; eight or more overlapping
metric updates do not by themselves establish a stable participant profile.

## Required formative-pilot design

Conduct threshold selection separately from the main evaluation:

1. Collect a standardized participant baseline in the intended phone position,
   lighting, font, article layout, and reading posture.
2. Record multiple articles or passages containing independently defined stable,
   mild-difficulty, sustained-rereading, severe-difficulty, and loss-of-position
   episodes.
3. Obtain reference labels using researcher coding, reading-aloud evidence where
   appropriate, participant event reports, controlled disruption markers, or an
   independently validated eye tracker.
4. Mark tracker-invalid periods separately. Do not train thresholds on gaze noise
   labeled as participant instability.
5. Compare candidate windows and smoothing/persistence values rather than
   validating only the currently implemented configuration.
6. Select parameters on pilot participants and verify them with participant-held-
   out validation, preferably leave-one-participant-out evaluation.
7. Freeze the complete controller configuration before main data collection.

## Outcomes for threshold selection

For every candidate controller configuration, report:

- sensitivity/recall for labeled instability and loss-of-position episodes;
- precision and positive predictive value of scaffold activation;
- false activations per minute during researcher-coded stable reading;
- median and 95th-percentile activation latency;
- recovery and scaffold-withdrawal latency;
- percentage of reading time under each scaffold level;
- number of rapid level reversals and unnecessary escalations;
- scaffold target accuracy in exact-line and within-one-line terms;
- comprehension, reading time, perceived usefulness, distraction, and annoyance;
- results stratified by participant, gaze quality, glasses/visual condition, and
  relevant older-adult characteristics.

Prioritize low false-positive and distraction rates because an unnecessary
moving scaffold can itself interrupt reading. Do not optimize detection accuracy
without also measuring placement accuracy and participant experience.

## Threshold decision rule

Choose the simplest controller whose pilot results demonstrate acceptable
precision, latency, placement accuracy, and participant acceptance. Determine
acceptance limits before evaluating the final held-out pilot participants.

If stable, separable index ranges do not emerge:

- do not retain increasingly precise levels merely to match the proposed model;
- consider participant-specific thresholds or percentiles;
- merge levels that cannot be distinguished reliably;
- prefer a broader region/focus intervention when line or word placement fails;
- improve or replace the gaze provider before making behavioral claims;
- describe the system as an exploratory prototype rather than a validated
  adaptive intervention.

## Manuscript wording

Use this statement until pilot validation is complete:

> Prior research supports fixation, dwell, and regression measures as indicators
> associated with reading effort and supports participant-sensitive gaze-aware
> assistance. However, the literature does not provide universal cutoff values
> for the proposed composite index. The current scaffold boundaries and timing
> values are provisional engineering parameters. Their final values will be
> established through formative pilot testing that balances detection
> sensitivity, false activation, intervention latency, recovery latency, spatial
> placement accuracy, and participant acceptability.

After the pilot, replace that statement with the preregistered selection method,
final frozen values, held-out performance, and remaining uncertainty. Do not
claim that literature directly supplied the numeric thresholds unless a study
using the same construct, population, device, and task is identified.

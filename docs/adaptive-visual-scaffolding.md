# Adaptive Graded Visual Scaffolding

## Status

Implemented in NewsMead on 2026-07-20. This document is the canonical guide for
the visual-scaffolding layer described in manuscript Section 4.3.5.

The implementation provides four mutually exclusive interventions:

1. Level 1: gaze-contingent word highlighting.
2. Level 2: current-line emphasis.
3. Level 3: a five-line focus window that softly de-emphasizes surrounding text.
4. Level 4: re-entry support using the last stable line and a directional cue.

Stable reading uses an additional internal NONE state. Only one intervention is
drawn at a time. Transitions fade over 400 ms.

## Runtime Pipeline

~~~text
GazeProvider.onGaze(x, y)
-> LineAoiMapper.targetAt(x, y)
-> GazeTargetStabilizer
-> ReadingStateInferencer (cumulative session RSI/events)
-> WindowedStabilityEstimator (recent participant-relative signal)
-> AdaptiveScaffoldController
-> GazeOverlayView
~~~

Important files:

- gaze/TextTarget.kt: line and optional word target.
- gaze/LineAoiMapper.kt: visible-text, padding-aware line and word mapping.
- gaze/GazeTargetStabilizer.kt: 120 ms line, 300 ms word, and 200 ms
  off-text debounce.
- gaze/WindowedStabilityEstimator.kt: rolling metrics, automatic baseline,
  positive z-scores, gaze confidence, and exponential smoothing.
- gaze/AdaptiveScaffoldController.kt: persistence, one-level-at-a-time
  escalation, conservative withdrawal, and re-entry detection.
- gaze/GazeOverlayView.kt: all four visual renderers.
- fragments/article/ArticleFragment.kt: integration and GazeScaffold logs.
- data/StudyConfig.kt: study mode, thresholds, timing, and validation switches.

WordHighlighter.kt is deprecated. It mutated TextView content, discarded spans,
and could cause relayout. Word support is now drawn without modifying content.

## Stability Signal

The existing RSI remains a cumulative session report:

~~~text
RSI = 0.40R + 0.35D + 0.25F
~~~

It is not used directly for adaptation because cumulative values cannot recover
quickly enough to withdraw support. Adaptation derives recent metrics from the
cumulative counter deltas inside a 10-second window:

- regression rate per minute;
- dwell-time proportion;
- fixation rate per minute.

During baseline collection, the estimator learns the participant's mean and
standard deviation for each measure. After baseline, only positive deviations
associated with increased effort are retained and combined with the manuscript
weights. The result is smoothed with a 2-second exponential time constant.

Adaptation is disabled while the baseline is incomplete or while fewer than 65%
of recent gaze samples land on visible article text.

### Session RSI versus Adaptive Instability Index

The two signals must not be treated as interchangeable:

| Signal | Log field | Scale | Horizon | Research role |
| --- | --- | --- | --- | --- |
| Session RSI | `GazeRSI score` | 0-100 | cumulative session | descriptive outcome/report |
| Adaptive Instability Index | `GazeScaffold index` | 0-4 | recent 10-second window | scaffold selection |

Session RSI does not drive the controller. Its cumulative history can keep a
scaffold active after recovery or dilute a new difficulty episode after a long
stable period. The recent index can both rise and fall, is normalized against
the participant baseline, and therefore matches temporary support and
withdrawal. A short-window version of Session RSI would still require baseline
normalization, smoothing, quality gates, and recovery policy, which is
functionally the current Adaptive Instability Index.

The logged `raw` value is the weighted positive z-score combination before
smoothing; logged `index` is the two-second exponentially smoothed value used by
the controller. Neither value should be interpreted as a comprehension score.

Direct 0-100 Session RSI level mapping was considered but is not implemented.
It would retain early difficulty/noise after recovery, dilute a late difficulty
episode after long stable reading, and apply the same absolute cutoffs to readers
with different normal dwell and fixation behavior. It would require an entirely
new set of pilot-validated thresholds; the current 1.0/1.5/2.0/2.5 adaptive
cutoffs are not convertible to Session RSI ranges. A high Session RSI also cannot
replace the separate loss-of-position evidence required for REENTRY.

A rolling 0-100 RSI could be used as an alternative display scale. Once it adds
participant baseline normalization, smoothing, quality gates, escalation and
withdrawal persistence, it has the same functional role as the current Adaptive
Instability Index. Changing the scale alone does not solve measurement noise or
threshold-validation requirements.

## Current Controller Policy

The thresholds below are initial engineering values expressed in positive
standard-deviation units. They are configurable and are not yet empirically
validated.

| Smoothed index | Desired state |
| --- | --- |
| less than 1.0 | NONE |
| 1.0 to less than 1.5 | WORD |
| 1.5 to less than 2.0 | LINE |
| 2.0 to less than 2.5 | FOCUS |
| at least 2.5 plus loss evidence | REENTRY |
| at least 2.5 without loss evidence | FOCUS |

Escalation requires 1.5 seconds of persistent elevation and moves one level at a
time. Withdrawal requires 3.5 seconds of recovery and also moves one level at a
time. Low confidence clears support immediately instead of treating tracking
noise as reading difficulty.

## Level Details

### Level 1: Word Highlight

The stable gaze x/y target is mapped to a character offset and locale-aware word
boundary. A translucent word rectangle is drawn only after the same horizontal
target persists for 300 ms. Whitespace and gaze outside the rendered line width
do not produce a word target.

### Level 2: Line Emphasis

A low-alpha full-text-width rectangle follows the stable current line. This is
the most reliable scaffold because the tracker and acceptance criteria are
primarily line-level.

### Level 3: Focus Window

Visible article text outside the current line plus two lines above and below is
dimmed. Toolbars and controls are not intentionally included in the mask. The
five-line window is deliberately broader than a single line to avoid creating an
overly narrow moving-window effect.

### Level 4: Re-entry Support

The controller remembers the last line observed during low-instability reading.
Loss evidence is recorded when gaze remains off text for at least 800 ms and
returns at least two lines away from that anchor. If the smoothed index is also
at least 2.5, the anchor line is emphasized. When it is outside the visible
region, an up/down arrow indicates its direction. The arrow is drawn in the
margin beside the text column, never over the words.

The app never auto-scrolls during re-entry support because unexpected movement
could further disorient the reader. A regression alone does not activate Level 4;
it may represent intentional rereading.

## Study and Validation Modes

Set StudyConfig.SCAFFOLD_MODE to one of:

- OFF: static control condition.
- ADAPTIVE: manuscript intervention condition.
- FORCE_WORD
- FORCE_LINE
- FORCE_FOCUS
- FORCE_REENTRY

Forced modes bypass baseline, confidence, and thresholds but still require a
valid text target to render at the correct location. Use them with
GAZE_TOUCH_VALIDATION set to true to validate geometry independently of gaze
accuracy.

GAZE_DEBUG_VISUALS separately controls the red gaze dot and FPS. The current
physical-device diagnostic build enables it; it must be false for
participant-facing sessions.

Recommended validation order:

1. Enable touch validation.
2. Force and visually inspect each level before and after scrolling.
3. Confirm word and line targets around margins, punctuation, and wrapped text.
4. Return to ADAPTIVE.
5. Run calibration and confirm transition logs under GazeScaffold.
6. Verify that deliberately looking away and returning several lines away can
   produce re-entry support only after high instability persists.

### Natural adaptive-trigger test

Use this after forced geometry validation to exercise the real ADAPTIVE policy:

1. Calibrate with the participant/device in the reading posture, then open a
   fresh article with GAZE_TOUCH_VALIDATION false and SCAFFOLD_MODE ADAPTIVE.
2. Read normally for the first 20 seconds while keeping gaze within the article
   body. Deliberate difficulty during this interval contaminates the provisional
   baseline and makes later triggering harder to interpret.
3. After baseline readiness, read one difficult paragraph for 10-15 seconds:
   pause on lines for 1-2 seconds and reread between two or three nearby lines,
   but do not look at the toolbar. This elicits dwell, fixation, and regression
   evidence while preserving valid-text gaze confidence.
4. Confirm GazeScaffold reports baselineReady=true and confidence at least 0.65.
   An elevated index never activates support when confidence is below this gate.
5. Hold the behavior long enough for 1.5-second persistence. Escalation is
   stepwise, so sustained index at least 2.0 takes approximately 4.5 seconds to
   progress from NONE through WORD and LINE to FOCUS.
6. For a naturalistic re-entry check, establish a stable line, look away for at
   least 800 ms, and return at least two lines away while the index is at least
   2.5. Regression by itself must not trigger REENTRY.

Interpret the adaptive index, not the cumulative GazeRSI score, when predicting
the active scaffold. A diagnostic run on 2026-07-20 reported index=2.12,
baselineReady=true, and confidence=0.64. The index was sufficient for the focus
pathway, but the run correctly remained at NONE because confidence was below
0.65. Keep gaze on article text for a complete 10-second rolling window before
classifying that outcome as a controller or renderer failure.

### Known-target reading accuracy gate

Adaptive testing is paused until the dedicated `ReadingValidationActivity`
demonstrates acceptable known-target accuracy. It contains an unscored visible-
dot preview, an identical three-reference vertical-alignment OFF/ON control,
eight highlighted-word fixations, and six highlighted physical-line reading
trials. It contains no adaptive or unconstrained-reading phase and makes no
claim about inferred free-reading intent. See
`docs/reading-validation-protocol.md` for the procedure and interpretation gate.

## Observed Gaze-Pipeline Limitation

Physical-device testing on 2026-07-20 showed that a scaffold could trigger while
its apparent position moved inconsistently with the reader's intended gaze. The
debug dot displays raw screen coordinates, while scaffold targets pass through
line/word debounce, so the dot is expected to look noisier than the scaffold.
However, visible scaffold displacement still indicates that temporal debounce
alone does not make the underlying estimate spatially reliable.

This creates **circular measurement contamination**: the same noisy gaze stream
can create false line changes, regressions, fixations, or dwell events that raise
the adaptive index, and then place the resulting intervention on the wrong word
or line. Detection validity and rendering validity therefore cannot be inferred
from a successful transition log. The current confidence value is only the
fraction of recent samples landing somewhere on visible article text. A sample
on the wrong line can still count as valid, so confidence at least 0.65 is a
coverage gate, not evidence of spatial accuracy.

Before participant-facing claims, measure the complete pipeline against known
targets and report:

- median and 95th-percentile vertical error in pixels and article-line heights;
- exact-line accuracy and accuracy within one adjacent line;
- horizontal/word-selection accuracy for Level 1;
- within-target dispersion, sudden-jump rate, off-text rate, and tracking loss;
- accuracy before and after scrolling and over session time to expose drift;
- raw-coordinate, stabilized-target, inferred-event, adaptive-index, and
  rendered-scaffold traces as separate synchronized records.

Recommended engineering mitigations, to be evaluated rather than assumed valid,
are robust spatial filtering, line-boundary hysteresis, minimum target dwell,
large-jump rejection, head/face stability checks, drift checks, and recalibration
prompts. Add a spatial-quality gate based on dispersion and target consistency;
do not rely only on valid-text percentage. Keep the tracker behind GazeProvider
so a better backend can replace it without rewriting the reading interface.

If pilot testing cannot demonstrate reliable word or exact-line localization,
revise the intervention hierarchy and manuscript claims. Prefer a broader focus
window or another region-level scaffold, and describe word highlighting as
provisional or future work. Do not tune the visual renderer to conceal tracker
error, because that would leave behavioral inference contaminated.

## Logs

GazeScaffold emits:

- a stability summary approximately every two seconds;
- baseline readiness and gaze confidence;
- raw recent regression/dwell/fixation metrics;
- every scaffold level transition and reason;
- current and re-entry target lines;
- recovery latency when the controller returns to NONE.

The logged recovery latency currently measures time from the first scaffold
activation to return to NONE. If the experiment defines recovery from an
externally marked disruption, that marker and its latency must be logged
separately.

## Required Research Decisions Before Main Data Collection

1. **Baseline protocol:** the app currently learns a fresh baseline during the
   first 20 seconds of each article and shows no support during that interval.
   The manuscript specifies a separate standardized baseline-reading phase.
   Before the main study, capture that profile explicitly and pass it into
   WindowedStabilityEstimator across experimental articles.
2. **Threshold validation:** 1.0/1.5/2.0/2.5 z and the timing values are
   provisional. Tune and freeze them using formative/pilot data, then record the
   final values in this document and the manuscript.
3. **Word-level feasibility:** horizontal gaze accuracy remains weaker than
   vertical line accuracy. Measure word-selection accuracy on the target devices
   and older-adult participants. If it is unreliable, revise Level 1 to a
   line-compatible intervention rather than silently claiming word accuracy.
4. **Visual design validation:** highlight colors, focus-mask opacity, five-line
   window size, and 400 ms fade require formative testing with the target age
   group in light, dark, and sepia modes.
5. **Loss-of-position definition:** the 800 ms off-text plus two-line return rule
   is an operational prototype definition. Validate it against researcher-coded
   loss/recovery episodes.
6. **Spatial-validity gate:** valid-text confidence does not detect wrong-line
   gaze. Define and freeze pilot-based limits for vertical error, dispersion,
   jump rate, drift, exact-line accuracy, and within-one-line accuracy before
   treating adaptive transitions as participant reading behavior.
7. **Circular contamination:** compare inferred regressions/dwell/fixations with
   synchronized raw gaze and researcher-coded reading episodes. Report how often
   tracker error both raises the adaptive index and misplaces the scaffold.
8. **Intervention granularity:** retain word and exact-line scaffolds only if the
   target-device and target-population results support their spatial demands.
   Otherwise revise the manuscript hierarchy toward a robust region-level aid.

## Verification

~~~powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest --tests com.newsmead.gaze.GazeTargetStabilizerTest --tests com.newsmead.gaze.WindowedStabilityEstimatorTest --tests com.newsmead.gaze.AdaptiveScaffoldControllerTest
~~~

Physical-device validation is still required because native text geometry,
scrolling, camera gaze, and overlay appearance cannot be proven by JVM tests.

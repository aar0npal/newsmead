# Known-Target Visible-Feedback Reading Protocol

Current status (2026-09-21): ordinary structured validation is protocol v6. The
orange gaze dot remains visible during references and scored word/line trials,
and the same article One Euro-smoothed coordinates drive the dot, word/line
mapping, and scoring. Pre-filter coordinates remain in separate source records.
These runs measure a deliberately specified closed-loop, article-pipeline
visible-feedback condition; they are not unaided tracker-accuracy measurements
and must not be pooled with raw visible-dot v5 or hidden-dot v4 runs. The frozen
matched-comparison path retains its original hidden-dot v4 behavior. Keep the
rejected experimental vertical correction OFF; see
[gaze-offset-replay.md](gaze-offset-replay.md).

## Purpose and boundary

This protocol answers one question: with the article's rendered and smoothed
gaze point visible, when the participant is instructed to look at a specified
word or read a specified physical line, can that pipeline and its feedback keep
the reported point on that word or line?

It does not isolate unaided estimator accuracy because the participant may use
the dot to compensate for an apparent offset, and the dot may itself obscure
text. It also does not evaluate adaptive scaffolding, infer unconstrained
reading intent, or prove performance for a future dot with different size,
opacity, color, or latency. Free-reading and adaptive claims remain separate.

The test uses the existing calibrated `GazeProvider`, `LineAoiMapper`, and
`GazeTargetStabilizer`. Protocol v6 enables the same `MgazeNetReadingSmoother`
already used by article reading before the optional session-only vertical layer,
mapping, and target stabilizer. It does not change or persist the raw estimator,
quadratic mapper, smoother parameters, or existing nine-point affine correction.
The fixed passage uses the study's locked 22 dp article font.

## Test sequence

### Unscored orange-dot preview and continuous feedback

The orange gaze dot is visible before measurement. Look at several words around
the page and confirm that the tracker is producing a live signal. Preview
samples are logged but excluded from accuracy results. Unlike v4, the same dot
remains visible during every reference and scored trial.

Tap **Start vertical references** when ready. The bottom control panel disappears,
but the orange gaze dot remains visible as closed-loop feedback.

### Reading-surface vertical references

Ordinary protocol-v6 runs use **OFF**: they record reference evidence without
applying a correction. Three orange bullseyes appear at the top, middle, and
bottom of the actual reading surface. Each target has 2 seconds of finding time
and 2.5 seconds of measurement. The gaze dot is visible; no instruction, timer,
or status label is visible while a reference is active.

The three live-pipeline median coordinates still screen the historical
`targetY = intercept + gain * observedY` candidate, but OFF never applies that
fit. Historical v4 ON runs are retained as rejected experimental evidence, not
as the current ordinary procedure.

### Word-fixation accuracy

Eight actual laid-out words are highlighted yellow across different horizontal
and vertical regions. Timing advances automatically and is deliberately not
shown while a target is active. Every target has:

- 3 seconds of unscored finding time; and
- 2.5 seconds of scored measurement.

Look only at the yellow word and keep looking at it until the next word appears.
The orange gaze dot remains visible, while no instruction, countdown, progress,
or ACQUIRE/MEASURING label is visible during an active target. The expected
physical line and exact character range
come directly from the Android text layout, so the result does not assume which
word the participant intended to view.

Runs alternate automatically between order A and B. Both use the same regions
but begin in different regions, helping separate an always-first-target problem
from a persistent regional problem.

### Guided physical-line reading accuracy

Before this section begins, a centered instruction dialog appears while no
target is active. Six complete physical lines are then highlighted yellow at
top, middle, and bottom viewport positions, including positions requiring
programmatic scrolling. Each line has:

- 2.5 seconds to identify the highlighted line; and
- 5 seconds of measurement.

Read the yellow physical line from left to right once, then keep gaze on that
same line until the next line appears. The orange gaze dot remains visible;
timing is automatic and no instructions or status labels are visible while a
line is active. Exact word identity is not
scored during moving line reading because the protocol has no independent
timestamp for each word. Exact-line and within-one-line scores measure agreement
with the visibly fixed instructed line, conditional on following that instruction;
actual eye position is not independently observed.

The test ends after guided line reading. It contains no adaptive phase.

## Exact device procedure

1. Use the intended handheld posture, lighting, orientation, and reading
   distance.
2. Open an article and tap the gaze-diagnostics dots beside Back.
3. Select **Structured reading validation**, then **Start validation**.
4. Enter a condition-bearing label such as `font22_article-smoothed_run1`, and tap
   **Continue**. Ordinary runs use alignment **OFF**.
5. In the unscored preview, look around the passage and verify that the orange
   gaze dot responds. Tap **Start vertical references**.
6. Read the centered reference instructions, tap **Begin**, and look only at
   the center of each orange target until the next appears.
7. Read the centered word-test instructions, then tap **Begin**. The bottom
   panel disappears before the first target, but the orange dot remains.
8. For each yellow word, look only at that word and continue looking until the
   next yellow word appears. The dot may be used as feedback; do not look for a
   timer or status message.
9. Between sections, read the centered line-test instructions and tap **Begin**.
10. For each yellow line, read only that physical line once from left to right,
   then remain on the same line until the next trial.
11. Wait for the visible-feedback performance summary and record the JSONL filename.

Stopping early writes an `interrupted` session. Timestamped filenames ensure
that retries never overwrite earlier records.

## Persistent data

Article-matched visible-feedback runs use log schema **9** and protocol **6**.
New logs declare
`gaze_feedback_mode: continuous_orange_dot_article_smoothed`, visibility by
phase, `mgazenet_article_one_euro_v1` as the output-coordinate filter applied to
dot, mapping, and scoring, and the dot's radius and RGBA channels. Source events
retain the unfiltered `raw_screen_x` and `raw_screen_y`. These fields accompany
the accepted calibration artifact's SHA256,
physical display dimensions, and measured root/viewport/text coordinate frames at reference
setup and measurement boundaries. These fields distinguish repaired runs from historical
local/screen-mixed recordings; old recordings are preserved, not silently rescored.

Each run writes:

```text
reading_validation_session_<yyyyMMdd_HHmmss_SSS>.jsonl
```

Records contain synchronized timestamps, alignment mode, all three reference
medians, guard results, whether the layer was applied, base and effective gaze
y coordinates, base/effective unstabilized AOIs of the selected scoring stream,
protocol/trial state, target kind,
expected line and character range, stabilized AOI, text position, scroll
offset, and lightweight FPS. The summary reports word-trial exact-line,
within-one-line, and exact-word accuracy plus guided-reading exact-line and
within-one-line accuracy.

No camera frames or face images are stored. Detailed source telemetry remains
off. Tracking gaps can be reconstructed from output timestamps and FPS records.

## Copy logs using Windows CMD

```bat
adb shell run-as com.newsmead ls files
adb exec-out run-as com.newsmead cat files/reading_validation_session_YYYYMMDD_HHMMSS_SSS.jsonl > diagnostics-local\reading_validation_session_YYYYMMDD_HHMMSS_SSS.jsonl
```

Replace the example timestamp with the exact filename shown by the app.

## Interpretation

Report the two trial types separately. Do not use the preview samples in any
metric. For word trials, report exact-line, within-one-line, exact-word,
off-text, acquisition latency, dispersion, jumps, and region/order results. For
guided line trials, report exact-line and within-one-line accuracy while the
participant moves naturally across the highlighted line.

These results estimate performance while the reader can see and react to the
orange gaze dot. They do not establish unaided estimator accuracy, and they are
not directly transferable to a future dot with a different presentation. They
also do not prove what someone reads during an unconstrained article session.
That claim would require an independent eye-position reference. A screen video
of the estimated dot, or coding of the same gaze samples, does not provide it.
An external tracker or another eye-position method needs its own validated
accuracy, synchronization and text-assignment rules.

Protocol v2 runs are excluded from accuracy analysis because changing
instructions and timing status were displayed below the passage during active
targets, requiring participants to look away from the target. Protocol v3 moves
all instructions to centered dialogs between sections and hides the control
panel throughout every measured trial. Protocol v4 retains those protections
and adds the identical OFF/ON reading-surface reference control described above.
Protocol v5 kept the control panel hidden and made the raw-coordinate orange
gaze point continuous in ordinary validation. Its interrupted diagnostic is
retained as a separate historical condition. Protocol v6 keeps the same visible
presentation while matching the article's One Euro output path for the dot,
mapping, and scoring. The frozen matched-comparison launcher remains hidden-dot
v4.

# NewsMead Thesis Memory

## Thesis Core

The thesis is a gaze-driven adaptive reading interface for older adults. The system extends the NewsMead Android news-reading app and turns it into a research prototype for sustained digital reading support.

The central problem is that digital reading can be disrupted by attention shifts, UI distractions, loss of place, and age-related changes in visual and cognitive processing. Older adults may take longer to recover their reading position after disruptions. The thesis is not mainly about improving raw reading speed. It is about reducing the recovery cost after reading instability.

The proposed solution is a personalized adaptive interface that observes gaze behavior in real time, detects instability, and applies graded visual scaffolding to help the reader maintain or recover orientation in the text.

## Key Research Concepts

- **Reading stability**: A state where the reader progresses through text smoothly, mostly forward, with normal fixation and dwell behavior.
- **Reading instability**: A state where gaze behavior suggests difficulty, hesitation, rereading, loss of place, or recovery effort.
- **Recovery latency**: The time between a disruption or instability event and the return to stable reading behavior.
- **Visual scaffolding**: Temporary visual support that helps the reader orient in the text. It should be applied conservatively, increased only when needed, and faded/withdrawn when the reader recovers.
- **Personalization**: Instability should be judged relative to each participant's baseline reading behavior, not only fixed global thresholds.

## Manuscript Model

The manuscript currently frames the system as a four-level graded scaffolding interface. Section 4.3.5 describes the adaptation decision as mapping a smoothed stability index to increasingly strong levels of support.

The current manuscript levels are:

1. **Level 1 Word highlighting**: The fixated word is dynamically emphasized.
2. **Level 2 Line emphasis**: The current reading line is highlighted to reduce navigation errors.
3. **Level 3 Focus window**: Text outside a gaze-centered region is softly de-emphasized.
4. **Level 4 Re-entry support**: A directional cue or line highlight guides the reader back after loss of position.

The manuscript says only one intervention should be active at a time. The least intrusive support should be applied first. Triggering should be conservative to avoid making the interface distracting, especially for older readers.

## Current App State

The repo already contains a study-mode version of NewsMead.

Important study-mode switches live in:

- `app/src/main/java/com/newsmead/data/StudyConfig.kt`

Current relevant settings:

- `BYPASS_LOGIN = true`
- `OFFLINE_MODE = true`
- `LOCK_ARTICLE_FONT_SIZE = true`
- `ARTICLE_FONT_SIZE_DP = 22f`
- `GAZE_ENABLED = true`
- `GAZE_TOUCH_VALIDATION = false`

The app reads bundled offline articles from:

- `app/src/main/assets/study_articles.json`

The article body is rendered as a native Android `TextView`, not a WebView:

- `app/src/main/res/layout/fragment_article.xml`
- `tvArticleText`

This matters because native `TextView` exposes Android layout APIs that can map screen coordinates to text lines.

## Gaze Pipeline Understanding

The code keeps gaze output behind the `GazeProvider` interface. Downstream reading logic should depend only on this interface, not directly on the specific gaze backend.

The active local gaze pipeline is:

1. Front camera / MediaPipe raw eye features.
2. Calibration samples from the gaze calibration activity.
3. `GazeMapper` maps raw gaze features to screen coordinates.
4. `LocalCalibratedGazeProvider` emits full-screen `(x, y)` gaze coordinates.
5. `ArticleFragment` receives gaze and sends it to article-line mapping and RSI.

Relevant files:

- `app/src/main/java/com/newsmead/gaze/GazeProvider.kt`
- `app/src/main/java/com/newsmead/gaze/MediaPipeRawGazeSource.kt`
- `app/src/main/java/com/newsmead/gaze/GazeMapper.kt`
- `app/src/main/java/com/newsmead/gaze/LocalCalibratedGazeProvider.kt`
- `app/src/main/java/com/newsmead/activities/GazeCalibrationActivity.kt`
- `app/src/main/java/com/newsmead/activities/GazeTestActivity.kt`

The project history suggests that line-level gaze is the realistic target. Character-level or exact word-level gaze is riskier because consumer-grade phone gaze is not accurate enough horizontally or spatially stable enough.

## Current Line Mapping

Line mapping already exists:

- `app/src/main/java/com/newsmead/gaze/LineAoiMapper.kt`

`LineAoiMapper` takes a full-screen gaze or touch `y` coordinate and resolves it to a 0-based article body line index.

It uses:

- `TextView.getLocationOnScreen()`
- `textView.layout`
- `layout.getLineForVertical()`

Because the `TextView` moves on screen as the `NestedScrollView` scrolls, the mapper does not need a separate scroll offset. It computes `screenY - textViewTop - totalPaddingTop` and maps that local y-coordinate into the text layout.

This makes current gaze-to-line mapping already scroll-aware for the native `TextView` article body.

## Current RSI Implementation

The app already has a `ReadingStateInferencer`:

- `app/src/main/java/com/newsmead/gaze/ReadingStateInferencer.kt`

It consumes a stream of line indices and emits:

- line samples
- fixation events
- dwell events
- regression events
- composite RSI-like score

Current event logic:

- A fixation is emitted when gaze stays on the same line for at least 300 ms.
- A dwell event is emitted when leaving a line after at least 120 ms.
- A regression is emitted when gaze returns to an earlier line and remains there for at least 300 ms.
- The score combines regression, dwell, and fixation components.

Current weights:

- regression weight: `0.40`
- dwell weight: `0.35`
- fixation weight: `0.25`

### RSI terminology and control decision

The research now distinguishes **Session RSI** from the **Adaptive Instability
Index**. Session RSI is the cumulative 0-100 `GazeRSI score` used to describe
overall detected effort. The Adaptive Instability Index is the smoothed 0-4
`GazeScaffold index` derived from participant-relative regression, dwell, and
fixation changes in the latest 10-second window.

Session RSI must not directly select scaffold levels. Cumulative history can
remain elevated after recovery or dilute a new difficulty episode after a long
stable period. The recent index can rise and fall with current behavior and is
therefore used for temporary escalation and withdrawal. Session RSI remains a
session outcome for article/condition comparison; neither signal is a
comprehension measure.

Current adaptive thresholds of 1.0, 1.5, 2.0, and 2.5 standard-deviation units
are engineering defaults. They require pilot validation against labeled reading
episodes after excluding or marking tracker-error periods.

Direct mapping from cumulative Session RSI to scaffold levels was considered and
rejected as the current design. Its history would make support slow to withdraw
after early difficulty and slow to activate for a late episode after prolonged
stable reading. It would also require separate, empirically derived 0-100 level
cutoffs and would not remove the need for loss-of-position evidence at Level 4.
A rolling 0-100 RSI remains a possible display convention, but with baseline
normalization, smoothing, quality gating, and recovery logic it is functionally
the Adaptive Instability Index under another scale.

Use `docs/manuscript-implementation-alignment.md` as the living insertion guide for
bringing Sections 4.3.4 and 4.3.5, methodology, analysis, and limitations into
alignment with this decision and future implementation changes.

There are unit tests for RSI behavior:

- `app/src/test/java/com/newsmead/gaze/ReadingStateInferencerTest.kt`

## Current Article Integration

`ArticleFragment` sets up the gaze layer:

- `app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt`

Important behavior:

- It locks article body font size in study mode.
- It creates `GazeOverlayView`.
- It creates `LineAoiMapper(binding.tvArticleText)`.
- It creates `ReadingStateInferencer`.
- It defines one `onGaze` sink that receives full-screen coordinates.
- The sink updates the gaze dot overlay.
- The sink maps gaze y-coordinate to a line index.
- The sink logs `GazeAOI`.
- The sink feeds the line index into RSI.

Touch validation also exists. When `GAZE_TOUCH_VALIDATION` is true, finger position substitutes for gaze. This is useful for proving line mapping and RSI without depending on tracker accuracy.

## Current Adaptive Scaffold Implementation

As of 2026-07-20, all four manuscript levels are implemented and wired through
the article's single GazeProvider callback:

- Level 1 word highlighting is an overlay rectangle derived from a debounced,
  padding-aware x/y-to-word target. The old TextView-mutating WordHighlighter is
  deprecated and no longer wired.
- Level 2 line emphasis draws a low-alpha full-width line anchor.
- Level 3 draws a five-line focus window and de-emphasizes surrounding visible
  article text.
- Level 4 remembers the last stable line and renders that line or an up/down cue
  after an off-text loss-of-position pattern.

WindowedStabilityEstimator separates adaptation from the cumulative RSI report.
It derives 10-second recent metrics, collects a participant-relative baseline,
uses positive z-scores with the manuscript's 0.40/0.35/0.25 weights, and smooths
the result. AdaptiveScaffoldController applies conservative persistence,
one-level-at-a-time escalation/withdrawal, confidence gating, and one active
intervention at a time. GazeOverlayView fades transitions over 400 ms.

StudyConfig supports OFF, ADAPTIVE, and one forced validation mode for each
level. The red gaze dot/FPS is now a separate debug switch and is hidden in
participant-facing sessions.

The automatic first-20-second per-article baseline and current z thresholds are
engineering defaults. The main study still needs the manuscript's standardized
baseline profile carried across articles and pilot-validated thresholds. Word
accuracy, visual parameters, and the operational loss-of-position rule also need
target-population device testing. The canonical technical and research guide is
adaptive-visual-scaffolding.md.

### Key measurement limitation observed on device

A 2026-07-20 physical-device run produced a naturally triggered scaffold whose
position appeared unstable together with the gaze indicator. The indicator is a
raw coordinate diagnostic and is expected to be noisier than the debounced
scaffold target, but the observation exposes a central validity risk: the same
gaze error can inflate regression/dwell/fixation evidence and then misplace the
intervention. This circular measurement contamination must be treated as a key
pipeline and manuscript limitation.

Valid-text confidence is not spatial confidence; wrong-line samples still count
as valid when they land anywhere on article text. Before main data collection,
measure median/P95 vertical error in line heights, exact-line and adjacent-line
accuracy, horizontal word accuracy, dispersion, jump/off-text rates, tracking
loss, and time/scroll drift. Preserve separate synchronized raw gaze, stabilized
target, inferred event, index, transition, and rendered-target records.

Recommended priorities are a spatial-quality gate, robust filtering and line
hysteresis, head/face stability and drift checks, recalibration prompts, and
pilot comparison with known targets or independent/researcher-coded ground
truth. If word or exact-line localization is not reliable on the study device
and older-adult population, revise the manuscript hierarchy toward a broader
focus/region scaffold or replace the gaze backend. Do not interpret a transition
as proof of genuine reading instability.

## Historical Visual Overlay Notes (Superseded 2026-07-20)

The current overlay is:

- `app/src/main/java/com/newsmead/gaze/GazeOverlayView.kt`

It currently draws:

- the gaze dot
- optional FPS text

It does not yet draw:

- current line highlight
- word highlight
- focus window
- re-entry cue

## Historical Scaffolding Implementation Implication

The easiest and most technically aligned next scaffolding feature is current-line highlight.

The app already knows the current line index. To render line-by-line highlight, we can extend `GazeOverlayView` so it draws a translucent rectangle over the current `TextView` line.

Likely implementation:

1. Add highlight state to `GazeOverlayView`.
2. Add a method such as `setHighlightedLine(textView: TextView, lineIndex: Int)`.
3. Use `textView.layout.getLineTop(lineIndex)` and `getLineBottom(lineIndex)`.
4. Use `getLineLeft(lineIndex)` and `getLineRight(lineIndex)`, or draw full text width for a stronger line guide.
5. Convert the `TextView` line rectangle from screen coordinates into overlay-local coordinates.
6. Draw a low-alpha highlight behind or over the line.
7. Clear the highlight when `lineIndex < 0`.
8. Gate the behavior with a `StudyConfig` flag.

This approach is safer than modifying the `TextView` text with spans because it avoids relayout, preserves scrolling behavior, and does not mutate article content.

## Historical Manuscript Alignment Issue

There is a terminology mismatch to decide before implementation.

The manuscript currently calls **word highlighting** Level 1 and **line emphasis** Level 2.

The app currently supports line-level gaze much better than word-level gaze. Therefore:

- If we implement line-by-line highlight now, it matches the current app capability and the current gaze accuracy target.
- But in the manuscript's current wording, it is Level 2, not Level 1.

Possible resolution:

1. Revise the manuscript so Level 1 is line highlighting, and word highlighting becomes future work or optional.
2. Keep the manuscript unchanged and implement this as Level 2 scaffolding.
3. Attempt word highlighting, but this is higher risk because it requires reliable horizontal gaze and word-boundary mapping.

My recommendation is to implement line-by-line highlight first and align the manuscript wording around line-level scaffolding unless the thesis committee specifically requires word-level Level 1.

## Historical Word Highlighting Risk

Exact word highlighting is possible in Android `TextView`, but it is a harder and less reliable feature.

It would require:

- mapping gaze `(x, y)` to a line
- mapping gaze `x` to a character offset using `layout.getOffsetForHorizontal(line, localX)`
- expanding that character offset to word boundaries
- applying a span or drawing a word rectangle
- handling punctuation, Filipino/English text, wrapped lines, and jitter

The biggest concern is not code feasibility. The biggest concern is gaze accuracy. Word-level highlighting may flicker, select neighboring words, or distract older readers if gaze estimates drift horizontally.

Line-level highlight is more consistent with the stated line-level accuracy goal in the repo documentation.

## Historical Practical Next Step

For the next implementation pass, add a conservative line-highlight scaffold:

- draw one translucent line highlight at a time
- update only when the mapped line changes
- clear when gaze leaves article text
- keep the gaze dot as a debug option
- log scaffold state changes for study review
- keep the feature behind `StudyConfig`

This would give the project a visible adaptive scaffolding layer using the line stream already produced by the app.

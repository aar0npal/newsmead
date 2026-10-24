# Working proposal: ordered line visits for adaptive reading support

Status: **historical fixed-grammar and scaffold-mapping option; not an approved study protocol or implemented controller**. The [current scanpath-to-visual-aid implementation proposal](adaptive-visual-aid-proposal.md) derives both word and line scanpaths and classifies descriptive patterns without inheriting this document's particular aid mappings as validated behavior. Prepared 2026-09-23 from the Codex task **Explore feasible gaze metrics** (`01a0be09-fe3e-76c2-827c-94dcfadcc416`), the current NewsMead measurement documents, and the thesis source in `../../THS-ST-Paper`. The subsequent [critical audit and better-supported alternatives](gaze-proposal-evidence-audit.md) challenges every major unvalidated choice. Recheck newer results before implementation or manuscript submission. The companion [evidence review](scanpath-scaffolding-evidence.md) distinguishes research precedent from validation still needed in NewsMead.

## Project purpose and decision

NewsMead is an on-device Android news reader being extended with gaze-driven visual assistance for adults aged **45 and above**. The thesis is implementation-centered: build and evaluate an adaptive interface against a matched static reading interface under controlled study conditions. The interface should help readers maintain or regain their place without unnecessarily interrupting useful reading. Font size may vary in future evaluation and must be treated as a measurement condition.

The original proposed **primary** control representation was a deterministic, quality-gated sequence of visits to physical text lines. It retains visit order, document position, and duration, then recognizes prespecified navigation patterns. It does not require supervised model training. This primary-controller choice is **withdrawn pending natural-reading line-event reliability and independent need-for-help evidence**. An Olivier-style hidden semi-Markov model (HSMM) was an **offline** exploratory comparison only; its published parameters and cognitive state names cannot be imported into NewsMead. The existing weighted Session RSI/Adaptive Instability Index and the teammate's Mahalanobis-distance proposal remain historical or exploratory comparators, not validated replacements for this detector.

The implementation may say that it detected a sustained backward revisit, line cycling, forward progression, or an observation break **when line assignment is sufficiently reliable**. It may not equate these events with confusion, mind wandering, failed comprehension, or harmful rereading. Regressions can support comprehension. The decision to show a scaffold is a design hypothesis whose benefit must be evaluated independently.

The proposal does **not** require four mutually exclusive scaffold levels. NewsMead currently has four renderers and a legacy controller; future assistance may be continuous, combined, or variable in intensity if testing supports it. Do not silently turn the existing UI hierarchy into a research requirement.

## Research question and intended contribution

**Can NewsMead reliably detect ordered line-level reading-navigation patterns with its phone gaze pipeline, and can conservative support triggered by those patterns improve an independently measured reading outcome without reducing comprehension or usability?**

The intended contribution has three separable parts:

1. A reproducible gaze-to-line-visit representation with explicit missingness and geometry handling.
2. An on-device, interpretable pattern detector and scaffold policy that can abstain when measurement quality is inadequate.
3. A controlled evaluation of line-event validity, unwanted interventions, and reading benefit. Implementing the detector alone does not establish the third claim.

### Specific objectives

1. Define and implement stable line visits and eligible transitions from calibrated phone gaze, with explicit uncertainty and continuity breaks.
2. Prespecify a small deterministic grammar for forward progression, backward revisits, repeated cycling, and prolonged residence, and measure its agreement with independent annotations.
3. Measure line and transition reliability across the intended font sizes, participant group, and rendering/feedback conditions.
4. Connect only sufficiently reliable patterns to conservative, testable scaffold responses and record both activations and abstentions.
5. Compare the adaptive interface with a matched static interface using an independently defined reading outcome, comprehension, unwanted assistance, and usability.
6. If data permit, compare the deterministic representation with an offline fitted HSMM without treating its inferred states as ground truth.

The principal research questions are whether line visits can be measured reliably on the target setup, whether the stated patterns can be detected with acceptably few false interventions, and whether their scaffold responses improve reading outcomes without distraction or comprehension loss. These questions require distinct measurement, detector, and intervention evidence.

## Proposed measurement contract

The current article path uses `MgazeNetGazeProvider` with article One Euro smoothing before display and text mapping; raw provider coordinates are retained separately in diagnostic logs. A future online line-visit extractor must declare which coordinate stream it uses and keep that choice fixed for a given analysis. The existing natural-reading offline analyzer primarily extracts **word** visits from raw coordinates. Its output is not an already validated line-event controller.

| Layer | Required record | Interpretation |
| --- | --- | --- |
| Sample | Capture time, source/display coordinates, tracking availability, geometry version, scroll state | A gaze observation, not a fixation or reading action |
| Stable event | Start/end times, assigned line or ambiguity, sample count, span, censor/quality flags | A provisional spatially stable gaze event |
| Line visit | Physical text-line ordinal in **document order**, entry/exit, observed duration, confidence/ambiguity | Consecutive eligible events attributed to one line |
| Transition | Previous/current line, elapsed gap, geometry and quality status | Eligible movement between visits, or an explicit break |

Proposed **NewsMead-defined** transition vocabulary (document-line ordinal difference, after quality gating). This is an operational design, not a published or validated set of reading-state classes. It draws on [word-distance encoding by Olivier et al. and multiline segment-transition work by Yamaya et al.](scanpath-scaffolding-evidence.md#provenance-of-the-proposed-order-categories), but neither study tested these exact phone line-visit labels.

| Symbol | Observable condition |
| --- | --- |
| `FWD1` | Next physical line in document order |
| `FWD_LONG` | Next eligible visit is more than one physical line later in document order; intervening reading is unobserved, not proven skipped |
| `BWD1` | Return to the immediately preceding physical line |
| `BWD_LONG` | Return across more than one physical line |
| `BREAK` | Tracking loss, ambiguous attribution, scroll/geometry instability, or excessive unclassified time |

`HOLD` describes a **visit-duration condition**, not a movement between lines. `BREAK` records lost sequence continuity, not a reading behavior. A large leftward return sweep from one line's end to the next line's start is `FWD1` in document order. A small correction at that line's start must not automatically become a comprehension-related regression. The earlier draft called `FWD_LONG` `FWD_SKIP`; the name changed because missing line visits do not prove that their text was skipped.

Thresholds for stable events, visit duration, line agreement, gaps, and pattern persistence are **engineering candidates** until sensitivity checks and independent annotation support them. Preserve explicit eligible denominators, uncertain segments, left/right censoring, and `null` for undefined rates. Never bridge an unclassified gap to manufacture a revisit or recovery sequence.

## Candidate patterns and scaffold responses

These are event definitions to specify and test, not validated cognitive states or final trigger thresholds.

| Pattern | Illustrative ordered visits | Defensible report | Candidate response |
| --- | --- | --- | --- |
| Forward progression | `4 -> 5 -> 6` | Forward line progression observed | Usually no new assistance |
| Local revisit and return | `4 -> 5 -> 4 -> 5` | Previous line revisited, prior frontier reached | Preserve a subtle line anchor if useful |
| Extended revisit | `4 -> 5 -> 6 -> 3 -> 4` | Earlier lines revisited | Offer a broad orientation cue only after persistence and quality checks |
| Repeated cycling | `4 -> 5 -> 4 -> 5 -> 4` | Repeated visits among lines | Test whether a mild cue helps; do not assume failed repair |
| Long residence | Extended eligible time on line 4 | Prolonged line visit | Usually observe first; duration alone is ambiguous |
| Observation break | `4 -> BREAK -> 7` | Sequence continuity unknown | Suspend inference and clear or hold support according to a tested UI rule |

A single backward transition must not trigger strong assistance. Escalation, withdrawal, cooldown, and suppression after scrolling should be prespecified and replayed before participant use. An inferred line is both the trigger input and the possible visual target: a wrong-line estimate could create a wrong-line cue. A coarser region cue is an option **only after its own placement reliability is measured**.

## Current NewsMead evidence and limits

- The completed hidden-dot protocol-v4 known-target run reported 40.2% word-trial exact-line, 17.1% exact-word, and 0.8% guided-line exact-line accuracy. This is a separate measurement condition from v6.
- The **one reported** protocol-v6 run after recalibration (`reading_validation_session_20260921_155148_059.jsonl`, summarized in the source task) recorded 1,192 valid scored samples at median 23.83 FPS. It reported 89.3% word-target exact-line, 47.5% exact-word, 61.6% guided-reading exact-line, and 99.2% guided-reading within-one-line accuracy. This is promising for line-level work at the locked **22 dp** font under continuous visible-dot, article-smoothed feedback. It is neither unaided estimator accuracy nor demonstrated free-reading line-transition accuracy; the run combines changed calibration, smoothing, and feedback. Repeatability and performance in the target population remain unknown.
- The first natural-reading diagnostic lasted 86.47 s. It had user-reported vertical misalignment, 87 provisional fixation candidates, 35 ambiguous word assignments, and only **four eligible word transitions** after the gap correction. Four word transitions do not quantify line-transition coverage. See [run 01](reading-measurement-run-01.md).
- Protocol v6 defines a closed-loop visible-dot instrument. Its results must not be pooled with v4/v5 or transferred to a future dot with a different size, opacity, or latency. See [validation protocol](reading-validation-protocol.md).
- `StudyConfig.SCAFFOLD_MODE` currently selects `DEMO_CYCLE`. That mode demonstrates renderers; it is not an adaptive study condition. The existing weighted controller and four renderers remain in the app but are not this proposal's validated implementation.

## Evaluation path and decision gates

1. **Measurement feasibility.** Under the intended device, posture, typography, and dot/scaffold presentation, repeat known-target checks and obtain a separately annotated natural-reading sample. Report exact-line and neighboring-line confusion, temporal lag, transition precision/recall, eligible line-visit coverage, ambiguity, false reversals, scroll-related errors, and participant/session variation. Test each intended font size; 22 dp alone cannot establish smaller-font feasibility. Tracking availability is not spatial accuracy.
2. **Detector construction.** Freeze a line-visit definition and candidate pattern grammar using development material. Reuse deterministic replay and discontinuity tests. Compare predicted events with independent annotations, including normal return sweeps and deliberate rereading. Report both missed patterns and false scaffold triggers. Keep tuning sessions/passages separate from evaluation sessions/passages.
3. **Intervention evaluation.** Compare adaptive support with a static interface using matched passages, typography, gaze-dot condition, and task instructions. Counterbalance order and passage assignment. Specify the primary outcome *before* collection using a disruption/onset and recovery assessment independent of the detector's score; an experimentally timed reorientation task with an externally observed endpoint is one candidate. Reaching a gaze-derived line frontier alone is not proof of comprehension recovery. Secondary outcomes should include comprehension, reading time, unwanted support, distraction, perceived usefulness, and gaze-data eligibility. Account for repeated episodes within readers and passage effects; a small formative study does not establish general effectiveness.
4. **Fallback.** If exact-line transitions are not reliable at a target size or for the target population, pause line-specific triggering. Evaluate broader region support or a user-invoked cue separately. Report the failed granularity honestly rather than concealing it with smoothing or tuning on the evaluation set.

An optional HSMM can be fitted to retained line-visit sequences after measurement feasibility is established. It is a comparison of sequence representations, not ground truth for the deterministic detector, and full-sequence decoding must not be presented as a causal real-time controller.

## Open decisions before a final protocol

- Which font sizes, devices, calibration/feedback condition, and natural-reading passage set represent the intended study?
- What independent reference can label line visits and unwanted cues without retaining camera images?
- What exact stable-event, gap, visit, pattern, trigger, cooldown, and abstention rules survive development-set sensitivity checks?
- Which scaffold visuals can be placed reliably, and should the gaze dot remain visible in both study conditions?
- What is the single prespecified primary outcome and its time origin, event eligibility, censoring rule, and analysis unit?
- How will the thesis' current weighted-index language and recovery-latency definition be revised once the method is fixed? The current `../../THS-ST-Paper/chapter_1.tex` and `../../THS-ST-Paper/chapter_4.tex` still describe the legacy index and must not be cited as already implementing this proposal.

## Source map

- Primary-study evidence and transfer limits: [scanpath-scaffolding-evidence.md](scanpath-scaffolding-evidence.md).
- Current acquisition and replay: [reading-measurement-pilot.md](reading-measurement-pilot.md), [reading-measurement-run-01.md](reading-measurement-run-01.md).
- Known-target condition: [reading-validation-protocol.md](reading-validation-protocol.md).
- Historical weighted controller: [reading-stability-index.md](reading-stability-index.md), [adaptive-visual-scaffolding.md](adaptive-visual-scaffolding.md).
- Current research checkpoint: [agent-context.md](agent-context.md).

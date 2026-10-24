# NewsMead scanpath-to-visual-aid implementation pilot

**Current stream amendment — 2026-10-13:** owner authorized consistent
calibrated-smoothed coordinates for the dot, live scanpaths and aid decisions.
The live pilot now uses schema 2 / `newsmead_scanpath_pilot_v2` with selected
scoring; replay uses explicitly synthetic post-filter trajectories. Earlier
raw-stream checkpoints and results below are historical. Prototype-first work
continues; this route change is not new participant validation. See
[stream contract](calibrated-smoothed-scanpath-path.md),
[live pilot](scanpath-live-pilot.md) and [replay](scanpath-replay-screen.md).

**Revision 6 — 2026-09-30.** Branch: `measurement/scanpath-pilot`. Development and evaluation plan for the [current feature proposal](adaptive-visual-aid-proposal.md). Preserve revision 5's build-before-participant-evaluation order. The owner permits replacing the old scaffold design; use independent study-linked aid types and within-type strength parameters, not mandatory levels. The [aid design and evidence contract](scanpath-adaptive-aid-design.md) records mappings, source locations and limits.

**Implementation checkpoint — 2026-10-01:** the [P01–P13 detector layer](scanpath-detectors.md)
is integrated into analyzer v5 and the article's live raw-observation stream.
Focused JVM extraction, policy and pipeline checks and 13 synthetic cases/183
raw prefixes pass; the debug app builds. P01–P12/common fixation fields match the reference; live P13 resets
immediately rather than relying on a future fixation. Four synthetic Android
geometry/magnifier tests passed on SM-A566B/API 36 after fixing detached-view
fixtures; [device check 01](scanpath-device-check-01.md) retains both attempts.
Visible article UI and live gaze behavior still need checks. P09 requires explicit geometric endpoint
settings and verified LTR direction; P12 long/slow flags require explicit thresholds and coverage/window
settings. P11 currently implements A–B–A–B alternation. The matched task
instrument remains pending. The [runtime checkpoint](scanpath-aid-runtime.md)
implements independent adjustable line/word/focus displays, reader controls and
bounded numeric action/render provenance, including experimental variable
word/focus strength. Further device checks and participant evaluation remain pending.

**Current phase: prototype implementation.** Build the live word/line engine,
pattern-to-aid policy, adjustable A0–A4 displays, reader controls and action
logging before participant evaluation. Build software checks and the evaluation
instrument into that work; running measurement tasks is a later phase. Existing
gaze evidence carries forward. Configurable pattern/strength rules can be
implemented as experimental methods without claiming validated difficulty or
aid benefit.

**Interactive screen checkpoint — 2026-10-02:** the first
[replay milestone](scanpath-replay-screen.md) provides five scripted raw-coordinate
scenarios through actual layout, existing detectors/policy and real aid displays,
reader controls, interruption handling and complete synthetic trace provenance.
It creates no camera/provider or human reference. Its guide records focused
software/device checks. Live prompted placement/order tasks and the matched
human evaluation record are still a subsequent package.

**Live instrument checkpoint — 2026-10-03:** the
[separate pilot screen](scanpath-live-pilot.md) adds ten raw-derived prompted
word/line tasks and uninterrupted engine processing. Ordinary reading supplies
hidden-dot no-aid/shadow/display conditions with the same reserved viewport.
Private records include capture-window target agreement, unshown opportunities,
descriptive path segments/alignment, engine acceptance, full evidence/actions,
draw proxies, adherence reports and terminal counters. Holds do not label
physiological refixations; descriptive alignment is not temporally matched event
scoring. Focused JVM/host checks and APK builds pass; new device execution,
phase-specific study procedures and participant collection remain pending.

## Purpose and objectives

**General objective:** build a phone-based system that derives ordered word and physical-line scanpaths, recognizes defined reading-navigation patterns, and varies visual aid type and strength during reading. Determine which measurements, triggers and strengths are usable for adults aged 45+ reading news in NewsMead.

~~~text
versioned gaze observations + capture time + article geometry
  -> candidate fixations, word/line assignments and explicit breaks
  -> fixation-level word transitions + word visits + line visits
  -> observable movement, revisit and duration events
  -> candidate decision: none / aid type / target / strength / duration
  -> displayed aid + independently measured reader outcomes
~~~

| Objective | Pilot question | Required output |
| --- | --- | --- |
| O1. Derive both resolutions | Can the system preserve usable word/line order, timing and uncertainty from the existing gaze input? | Versioned extractor; resolution-specific coverage and prompted-order results. |
| O2. Classify patterns | Does each rule recognize its defined movement/episode, including counterexamples and missing data? | Source-linked registry, deterministic classifier, fixtures and per-pattern evaluation. |
| O3. Run causally on the phone | Does live processing reproduce causal replay without future observations, duplicates or stale geometry? | Live engine; prefix-replay parity, resets and processing/render timing. |
| O4. Implement adjustable help | Can events produce correctly anchored, controllable line cues, word magnification and focus regions at different strengths? | Separate aid policy, renderer parameters, user controls and event-to-action log. |
| O5. Investigate support need | Which usable features associate with independently reported difficulty/help need or reading outcomes? | Exploratory associations with defined labels; held-out evaluation where data permit. No borrowed difficulty weights. |
| O6. Evaluate help and adaptation | Which forms/strengths help, and does varying strength improve outcomes relative to constant strength? | Controlled comparisons, unwanted-cue results and a justified candidate policy for independent evaluation. |

**Completion:** an integrated research prototype plus a report for every planned pattern and aid: implementation, evidence source, software checks, phone feasibility, outcome evidence and uncertainty. Word level remains an implementation/feasibility track. A failed word track is a reported result; evaluate any usable line track on its own evidence.

The pilot can establish feasibility and estimate effects. A conclusive population benefit or calibrated live difficulty probability requires sufficient independent data and a prespecified evaluation. Code completion or an owner demonstration does not establish those claims.

## Existing work and current readiness

Carry forward completed gaze evaluation. New checks address sequence extraction, classification, aid placement/timing, or an explicitly changed model/condition.

| Existing component/evidence | What it provides | Pilot extension |
| --- | --- | --- |
| [MGazeNet observability results](gaze-mgazenet-calibration-observability-v3-results.md), [confirmation analysis](gaze-mgazenet-direct-validation-confirmation-analysis.md), [model comparison](gaze-current-vs-mgazenet-evidence.md) | Historical estimator/calibration evidence with configuration and participant limits. | Input baseline; no broad estimator-comparison restart. |
| V6 result from **Explore feasible gaze metrics**, summarized in [agent context](agent-context.md) | One visible-dot, article-smoothed run: 47.5% exact-word, 61.6% guided exact-line and 99.2% guided within one line at 22 dp. | Ordered-event/action evaluation. Target scores are neither raw-stream scores nor classifier performance. |
| [Raw reading logger](reading-measurement-pilot.md), offline analyzer v5 | Candidate fixations, independent assignments, word/line visits/edges and named descriptors with geometry/evidence timing. | Matched reference tasks, Android causal replay and action outputs. |
| WORD/LINE/FOCUS/REENTRY renderers | Reusable UI assets. | Adjustable parameters and new policy. Legacy weighted control and DEMO_CYCLE do not implement this feature. |
| [First natural-reading diagnostic](reading-measurement-run-01.md) | Analysis v2: 87 candidates, 35 ambiguous word assignments, four eligible word transitions; reported vertical misalignment. | Extraction diagnosis, not natural eye-position validation. Historical counts have not been rerun under v4. |

**Implemented:** live/offline named rules, independent aid policy, adjustable displays,
reader controls and action/render journal. The runtime uses finalized P01–P13
evidence and a separate established ongoing-prefix contract for residence; that
prefix adds time, not a finalized refixation. The [runtime guide](scanpath-aid-runtime.md)
records experimental parameters and limits. **Pending:** matched raw
placement/continuous-order instrument, device execution and participant pilot.

The owner is requesting a stronger research model from its provider. Artifact, weights, requirements and phone performance are unverified. Continue development around a versioned observation interface. If supplied, record model/runtime/calibration identity and repeat affected input-to-event checks; retain separate results by model. No improvement is assumed.

## Build packages and acceptance checks

| Package | Deliverable / boundary | Acceptance check |
| --- | --- | --- |
| WP1. Extraction reference | Extend `tools/gaze/analyze_reading_measurement.py` around v4 contracts, retaining raw/display provenance, capture time, geometry, paths and uncertainty. | Existing suite passes; fixtures reproduce order/spans; boundaries cannot manufacture links. Version and review semantic changes. |
| WP2. Pattern classifier | Implement P01–P13 with source, input sequence, rule, parameters, minimum evidence and counterexamples. | Positive, negative, boundary and ambiguous cases for each pattern; outputs retain evidence IDs and resolution. |
| WP4. Causal live engine | Implemented under `app/src/main/java/com/newsmead/gaze/scanpath/`; consume each raw observation once; reset on quality/layout/calibration boundaries. | P01–P12/common fixation fields match synthetic replay prefixes and evidence availability; immediate live P13 has separate reset tests. No early use of final visit duration or future observations. Device timing/geometry execution remains to be measured after prototype completion. |
| WP5. Policy, UI, action record | Separate policy; extend `GazeOverlayView`/article controls. Log candidate, suppressed, chosen and displayed actions. | Reproducible decisions; current anchors/geometry; strength and user-off work; unknown/stale input suppresses new actions. Record event evidence times and render/frame acknowledgment; identify whether the acknowledgment is a render proxy or verified visibility. |
| WP3. Separate pilot instrument | After the integrated core is built, add matched raw word/line location and uninterrupted sequence tasks with timestamped prompts/compliance notes; preserve frozen v6. Run these tasks during evaluation. | Target/layout identity, raw scoring stream, capture-clock intervals and full denominators. Holds and continuous order scored separately. |
| WP6. Shadow/controlled pilot | Run phases below with the completed study plan and collection authorization. | Per-pattern feasibility, timing, independent outcomes and feedback reported with failures/denominators. |
| WP7. Policy decision | Select resolution, aid forms and strength rules from development evidence; freeze for untouched evaluation. | Decisions trace to support-need and intervention results; unsupported mappings stay experimental; no-aid retained. |

Both current paths share the provisional fixation detector. Word ambiguity can leave line assignment usable, but poor extraction coverage cannot be repaired by calling the remaining line labels accurate.

## Offline pattern registry and evaluation targets

`w` is document-order word ordinal; `L` is physical line in the current layout. Examples are **test inputs**, not participant traces or published difficulty schematics. Every rule requires continuity, eligible assignments and compatible geometry.

| ID / family | Operational observation and input | Source and transfer limit | Pilot reference/check |
| --- | --- | --- | --- |
| P01. Word forward step | Consecutive assigned candidate fixations: `delta_w = +1`. | [Olivier et al. (2022)](https://doi.org/10.16910/jemr.15.4.5), word-distance alphabet, research-grade information-search tracking. Movement precedent, no difficulty implication. | Fixtures; prompted `w10 → w11 → w12`. |
| P02. Word forward jump | `delta_w > +1`; retain distance. | Olivier's longer-forward category. Nonadjacent landing does not establish intervening words were unread. | Nonadjacent targets; missingness must break links. |
| P03. Word backward step/longer return | `delta_w = -1` or `delta_w < -1`; retain distance/line identities. | Olivier's backward categories. Returns can support useful rereading. | Backward targets, forward counterexamples. |
| P04. Same-word refixation candidate | Two distinct consecutive candidate fixations on one word: `delta_w = 0`; fixation-level sequence. | Olivier's `Rfx`. One hold or collapsed visit is not two fixations. | Two-candidate vs one-hold fixtures. Phone prompts cannot independently verify physiological refixations within a word. |
| P05. Next-line progression | Eligible line visits: `delta_L = +1`. | Multiline research motivates order; this bin is a NewsMead descriptor. | Prompted `L2 → L3`; distinguish P09. |
| P06. Forward multiline landing | `delta_L > +1`; retain distance. | NewsMead descriptor. [Yamaya et al. (2017)](https://doi.org/10.2197/ipsjjip.25.100) classified sequential-reading segment transitions, including Skipping; those are different units/rules. | Nonadjacent lines; no unread-text claim. |
| P07. Same-line backward return | Backward word movement within one confidently assigned physical line. | Yamaya's Short Re-reading supplies a same-line segment precedent, not this exact detector. | Within-line reverse targets; uncertain word order yields unknown. |
| P08. Earlier-line return | `delta_L < 0`; retain one-line/longer distances. | Yamaya's Long Re-reading crosses lines; [Carr et al.](https://doi.org/10.3758/s13428-021-01554-0) discuss regressions and drift-related assignment errors. | Earlier-line tasks; neighboring-line confusion separate. |
| P09. Return-sweep candidate | Line-end region to next-line start region, forward line order, leftward screen movement in left-to-right text; endpoint tolerances specified. | [Parker et al. (2019)](https://doi.org/10.1016/j.visres.2018.12.007), Yamaya. Sweeps can undershoot and need correction; exact first-word landing is not mandatory. | End/start tasks; undershoot and same-line-leftward fixtures. `+1 line` alone is insufficient. |
| P10. Revisit/return toward frontier | Leave word/line, revisit earlier text, then approach/reach the furthest previously observed position in the continuous group. | Yamaya's Resuming and [von der Malsburg & Vasishth (2011)](https://doi.org/10.1016/j.jml.2011.02.004) motivate ordered rereading. Exact frontier rule is a project definition. | Scripted word/line episodes; reset frontier at breaks; use only past evidence. |
| P11. Repeated return/cycle | Current v1 detects four collapsed visits A–B–A–B separately by word/line; optional elapsed-time limit. Other nonalternating return grammars remain extensions. | Project descriptor inspired by rereading research. No reviewed paper validates this exact cycle as confusion, dyslexia or support need. | Prescribed cycles/counterexamples; document grammar/time settings. |
| P12. Residence/progression | Observed fixation-span sum within word/line; change in word frontier/line order over a specified interval. Retain text amount, elapsed interval, missingness/censoring. | Duration complements order. No universal long/slow threshold or direct slow-line-to-focus benefit. [Regularity research](https://doi.org/10.1111/cogs.12208) shows reader/text context matters. | Span arithmetic/prefix tests; repeatability and independent support-need comparisons. Missing time cannot increase assumed residence. |
| P13. Unknown/break | Invalid data, gaps, ambiguity, scroll/reflow, calibration change or insufficient evidence. | Measurement boundary; not a difficulty class. | Each reason interrupts the affected resolution; terminal boundaries/abstentions reported. |

For P09–P12, freeze tolerances, windows, minimum evidence and comparison baselines after development. Retain continuous values alongside flags; text amount/complexity, reader pace and tracking loss can affect duration/progression.

The [classification dictionary](scanpath-classification-map.md) also catalogs empirical clusters and inferred model phases. Those need their own specified clustering/HSMM/model procedure and evaluation. This pilot does not claim those are implemented. Initial descriptive rules require no trained difficulty model or weights.

## Visual aids and variable strength

Implement strength as explicit parameters, then test which values help in a defined situation before automatic escalation.

A0–A4 are aid/decision identifiers, not ordered levels. Type addresses a task;
strength changes parameters within that type. The new policy does not use
`ScaffoldLevel.ordinal` or legacy index thresholds to move through aid families.
Every rule records its source and whether evidence matches its trigger, aid form
or outcome; numerical settings and stronger-help assumptions cannot be borrowed
from paper significance or cluster membership. Build the adjustable modules now;
their participant evaluation follows prototype completion. Preserve A4's focus
definition and give ruler/coloring variants distinct versioned form IDs; report
their exposures separately.

| Aid / candidate event | Display and strength parameters | Evidence and pilot decision |
| --- | --- | --- |
| A1. Current-line highlight/anchor | Emphasize reliable current line/start; vary prominence and duration. Preserve earlier text during returns. | [GazePrompt](https://arxiv.org/html/2402.12772) supplies line-aware highlighting precedent. Establish phone placement, cue burden and independent outcomes; returns alone do not justify escalation. |
| A2. Next-line start cue | Test prospective line-end and reactive P09 cues separately; vary marker prominence/duration and log their timing. | GazePrompt: 13 low-vision adults, reading aloud, 120 Hz desktop tracker; switching time improved (p = .014), accuracy did not significantly improve. Check whether phone timing offers useful support. |
| A3. Local word magnifier | At eligible word anchor, magnify while preserving surroundings; vary scale/prominence/duration. P04/P12 within-visit inspection supplies a candidate method; a P10 revisit trigger is a separate unsupported extension. | GazePrompt's within-one-pass duration/refixation rules are a precedent, not equivalent to any single P04/P12 event. Tested word outcomes did not significantly improve. Establish word placement and benefit. Existing WORD highlighting is not magnification. |
| A4. Context-preserving focus region | Lightly emphasize line/region; vary span/prominence/duration. Test usable P11/P12 episodes and directly requested help. | Experimental mapping. [Rayner et al. (2010)](https://pmc.ncbi.nlm.nih.gov/articles/PMC3075059/) and [Paterson et al. (2013)](https://pubmed.ncbi.nlm.nih.gov/23066801/) measured perceptual span, not slow-progress-triggered aid benefit. Test preview/distraction costs. |
| RULER_GREY_BAR_V1 / RULER_UNDERLINE_V1 | Optional line bar/underline forms with explicit anchors and adjustable prominence; independent from A4. Slow/return triggers remain experimental. | [Digital Reading Rulers](https://doi.org/10.1145/3544548.3581367) supplies a mouse-controlled form comparison; Grey Bar versus no ruler is the stronger result. Exact comparisons and transfer limits: [design contract](scanpath-adaptive-aid-design.md). |
| WORD_PROGRESS_COLOR_V1 | Optional persistent coloring of visited words, independent from A3 magnification. | [Rummens & Beier](https://doi.org/10.1080/00140139.2025.2514600) supplies a continuous gaze-progress form in children, not an adult difficulty-triggered aid. |
| A0. No new cue/suspend | Suppress on P13, unsupported placement, stale geometry or user-off; specify existing-cue expiration/clearing. | Report withheld opportunities as well as incorrect cues. Uncertainty cannot cause stronger assumed difficulty. |

[Schotter et al. (2014)](https://doi.org/10.1177/0956797614531148) found preventing rereading reduced comprehension. [Cheong et al. (2009)](https://pubmed.ncbi.nlm.nih.gov/19661837/) found mixed line-guide effects in macular degeneration. Preserve rereading and measure tradeoffs. Full populations/hardware/findings: [aid evidence map](scanpath-visual-aid-evidence-map.md), [primary-study ledger](scanpath-scaffolding-evidence.md).

**Difficulty-to-strength rule:** test path associations against independent support-need reports/outcomes; compare candidate strengths in that context. Paper correlations, p-values, cluster frequencies and HSMM-state probabilities are not NewsMead difficulty weights. Configurable development rules can exercise shadow/manual study actions, labeled experimental. Freeze automatic rules after supporting placement, association and intervention evidence. Greater strength may help, harm or make no difference.

## Evaluation phases

Development can proceed before new participant recording. Phone-only prompts supply intended target/order references conditional on compliance; they do not independently observe natural eye position.

### Phase A — complete the integrated prototype

Use the implemented offline P01–P13 rules with deterministic tools, fixtures and approved development records. Retain evidence IDs, settings and counterexamples. Exercise timestamp reversal/duplicates, sparse data, ambiguity, complete loss, scroll/reflow and calibration boundaries.

Add causal prefix replay, checking when each event becomes available and comparing with live processing. Replay A0–A4 decisions in shadow mode with exact target, strength, suppression reason and timing.

Port the causal extractor/classifier into Android, connect its events to a
configurable aid policy, and integrate current-line highlighting, next-line
cues, localized word magnification and separately identified ruler/focus variants into
article reading. Implement variable strength, duration/release, reader-off,
uncertainty suppression and action/render logging. Keep configuration provenance
and experimental trigger status explicit; strength parameters are not validated
difficulty probabilities.

**Prototype completion checkpoint before Phase B:** an Android build contains
the full gaze-to-scanpath-to-event-to-aid path. Synthetic/replay inputs through
that path exercise every implemented pattern, each aid, varying strength,
resets, dismissal and user-off. Decision and rendered-action records identify
the actual target, strength, timing and reason. Complete the matched evaluation
instrument after core integration, before running the participant tasks.

**Result:** an integrated experimental prototype with software checks and
inspectable decisions. Synthetic inputs and coding of the same estimated stream
establish processing semantics, not human eye-position accuracy or aid benefit.
Participant measurement and evaluation start after this build checkpoint.

### Phase B — prompted pattern feasibility

Use a **separate matched raw instrument**, current 22 dp font, fixed layout, raw-derived AOIs and timestamped scoring intervals. Preserve frozen v6 and its smoothed-score identity.

Include word/line holds and uninterrupted prescribed forward, backward, nonadjacent, end/start and repeated-return sequences. Prespecify/randomize order; include counterexamples. Define allowed extra fixations and temporal matching tolerances before scoring. Start without scrolling; treat reflow/scrolling separately.

Report target agreement, recovered prescribed events, extra/missed events, timing and abstention by reader/session/pattern. Holds cannot label physiological refixations; instructed rereading is not verified difficulty.

**Result:** conditional task agreement and usable event coverage. Freeze development rules before independent reader/passage evaluation. Failed trials remain in feasibility totals.

### Phase C — ordinary reading in shadow mode

Use fresh news passages; record both resolutions and shadow decisions with aids
off. The earlier diagnostic recorder shows the gaze dot: retain that condition
label. The separate live pilot now implements hidden-dot reading with matched
no-aid/shadow/display viewports; verify the new condition on the study phone and
hold visibility constant across compared arms. Its bundled fictional passages
are practice material, not the required fresh-news evaluation corpus.

Specify independent support-need reports, such as reader-initiated help/lost-place responses and/or passage difficulty ratings, plus comprehension questions. A help response timestamps a report, not true difficulty onset. Reporting can interrupt reading; record the procedure/burden. Keep momentary reported need and passage comprehension as separate outcomes.

For momentary prediction, prespecify the feature-window length, prediction lead
time/guard interval and negative-window sampling. End each input window before
report onset; use only information available by that cutoff. Log the first help
interaction, reporting interval and reading-resume boundary. Exclude report-control
interaction/recovery and prompted-question periods from ordinary-reading inputs.
Freeze these rules using development data and assess guard sensitivity; anticipatory
button seeking may remain a confound. A contemporaneous/post-report association
must be labeled separately and cannot establish advance prediction. Keep both
readers and passages separate from evaluation during rule/normalization selection.

Compare usable features with these outcomes accounting for reader, passage and coverage. Keep aids off while developing the association. Passage scores cannot label every moment. Do not fill gaps with expected reading order.

**Result:** ordinary-reading extraction feasibility and exploratory associations. True natural word order, physiological fixation accuracy and event-level difficulty truth remain unverified with phone-only measurement.

### Phase D — controlled aid and strength trials

After relevant placement/software checks, display explicitly experimental actions with reader-off controls. Reported-help or known-target tasks can test aids before automatic difficulty triggers are validated.

For each aid, define a small justified parameter set; compare selected conditions with no cue. Randomize/counterbalance fresh comparable passages or trials; freeze assignment, outcomes and stopping rules. Choose comparators for the question:

- Line cue: correct line-location response, task time, unwanted/wrong cues and passage comprehension.
- Magnifier: independent word-recognition response, helpfulness, obstruction/distraction and comprehension.
- Focus region: place keeping/helpfulness and comprehension; include a line anchor if testing that question; inspect preview costs.
- Strength: compare values within the same aid type/context; greater observed difficulty does not automatically select maximum prominence.

For timing, define an observable response independent of gaze, e.g. a correct line-selection tap after a timestamped instruction. This measures the localization task, not natural eye landing. Silent-reading benefit uses separately scored comprehension and reader feedback; gaze-derived recovery time cannot be its sole endpoint.

**Result:** usability, cue burden and preliminary aid/strength effects. Nonsignificance in a small comprehension comparison does not establish absence of harm.

### Phase E — freeze and evaluate adaptation

Specify and freeze event eligibility, support-need rule, type, strength, duration, release and reacquisition from development evidence before untouched evaluation.

For **changing strength**, compare varying strength with the same aid type/event/timing eligibility logic at constant strength. Prespecify comparable exposure opportunities and report actual count/duration; changed behavior can change exposure. A static aid comparator fits a timing/location-adaptation question. A universal three-arm design is not required.

Randomize/counterbalance selected conditions, using fresh materials and separate evaluation readers/passages. Measure independent task performance/comprehension, reader reports, burden, failures and adverse feedback. Fewer returns, shorter dwell or a reduced trigger score cannot establish benefit.

**Result:** an evaluated candidate policy or a documented reason to revise/defer mappings. Plan a larger confirmatory study when a population benefit claim requires greater precision.

## Participant plan and session procedure

Complete the [blank record](scanpath-pilot-session-record.md) privately before recruitment: age range (target 45+), language, vision correction/inclusion rules, recruitment, reader count/rationale, independent repeats, passages, phase-specific conditions, endpoints, matching/progression criteria, stopping/retry rules.

Choose pilot size for intended feasibility/effect precision and practical scope; plan later efficacy size from an explicit design/effect rationale. Samples/windows/repeated passages are not independent participants. Owner-only runs are engineering evidence. Retain failed tracking/calibration attempts in feasibility totals.

Freeze initial device, 22 dp font, layout/theme/orientation/posture/lighting. Alternative fonts/devices/models are explicit versioned conditions. Preserve exact article versions/hashes privately. Record APK hash/build provenance and analyzer/classifier/policy versions/configuration hashes; Git HEAD cannot identify a dirty-tree APK.

For each linked collection set:

1. Record phase/condition and calibrate before the pre-check.
2. Run matched placement/order checks; retain the same calibration through reading/task and post-check.
3. Run prompted task, shadow reading or experimental aid trial. Record start/end, interruptions and conditions. Keep deliberate plumbing actions separate from spontaneous reading.
4. Repeat matched checks before recalibration. Failed post-check leaves drift onset unresolved; retain/flag the run. Recalibration starts a new linked set.
5. Verify completeness/loss counters; inventory files, responses, deviations and stop reasons.

Installation, calibration, collection and private transfer require task-specific authorization under `AGENTS.md`. This revision performs none. Keep recordings app-private/in an approved private location; retain no camera frames.

## Endpoints, denominators and progression

| Component | Report | Reference/denominator limit |
| --- | --- | --- |
| Placement/order | Exact/neighboring word/line agreement, signed confusion, prescribed-event recovery, missed/extra events and temporal matching. | All scheduled opportunities plus conditional eligible results; compliance/failures visible. No natural eye-position accuracy. |
| Per-pattern classification | Fixtures; prompted precision/recall where the reference labels the class; unknown rate and evidence support. | Referenced opportunities, predicted events and withheld events separately. Word holds cannot provide physiological refixation precision. |
| Natural extraction | Accepted samples vs raw eligibility; observed spans, ambiguity/censoring, group lengths, edges and breaks. | Capture-span and sample denominators separately. Sparse retained assignments cannot establish adequate coverage. |
| Live/actions | Prefix parity, duplicates/missing events, stale/wrong target under known geometry, suppression, chosen/displayed/expired actions. | All scripted opportunities/decisions including render failures. Same-stream audit cannot validate actual eyes. |
| Timing | First supporting and last decisive capture times, evidence-available time, decision time and render/frame acknowledgment; onset-to-render and decisive-capture-to-render intervals plus component delays, missing/negative counts. Prompt-to-response where defined. | Document common monotonic clock/conversions and acknowledgment meaning. First support is operational event onset, not physiological onset. Assess cue timing against prompted transition and independent response, separately for prospective/reactive cues; render callback alone is a proxy for visibility. |
| Support-need association | Effects/prediction against independent reports/outcomes, uncertainty/coverage; frozen pre-report windows, lead/guard interval, report exclusions and negative-window sampling. | Reader/session/passage units and holdout splits. No future/report-interaction inputs. Passage score is not a momentary label; probability claims require calibration and precision/recall. |
| Aid/strength | Independent performance/comprehension, reported help/distraction, dismissals, cue exposure and unwanted/wrong cues. | All randomized trials/readers with missingness/failures. Unwanted reports differ from proven spatial misplacement. |
| Repeatability | Within-reader independent-session changes and between-reader distributions by resolution/model/condition. | Account for reader/passage clustering; no population interval from one reader. |

No numeric wrong-cue tolerance, placement gate, required coverage or long/slow threshold is validated. Specify criteria/rationale from development evidence before independent evaluation. Software readiness and participant evidence remain distinct.

Progress per pattern/aid: **defined → fixture-tested → prompted-feasible → shadow-inspected → experimentally displayed → independently evaluated**. Record unresolved/failed stages per feature. Automatic ordinary-reading use requires placement, trigger and intervention evidence; manual/controlled aid studies can proceed earlier under defined conditions.

## Replay contract retained from the audit

- Preserve raw coordinates/raw-derived AOIs, display stream separately, capture time, text/layout/calibration identity and detector settings. I-DT defaults are provisional; sampling limits physiological timing.
- V4 `coordinate_geometry_eligible_sample_records` is pre-timing eligibility; `valid_sample_records` is accepted monotonic samples. Latest capture time survives boundaries; duplicate/older samples are rejected.
- Keep candidate spans/centroids/counts, independent word/line assignment support, unknowns/censoring. The 80% assignment agreement is not spatial accuracy.
- Preserve fixation-level word edges, `ordered_word_visits`, word-visit edges, `line_visits` and line edges. Visit duration sums observed spans; elapsed residence/missing intervals remain distinct.
- Break on input/geometry/timing boundaries and resolution-specific unknowns. Word ambiguity may leave line assignment eligible; detection loss affects both.
- Separate `segment_boundary_event_counts` from broken adjacent-fixation links; include terminal boundaries. Invalid observations are events, not behavior episodes.
- Observed fixation-span/capture-span coverage excludes unobserved tails. Footer elapsed time includes writer draining, not precise reading-stop time.
- `coordinate_output_age` is logged coordinate delivery-minus-capture age with missing/negative counts, excluding filter/detector/render delay.
- Keep undefined rates `null`, failures and versioned settings. Incomplete/lossy/`--allow-incomplete` results stay diagnostic. Prespecify sensitivity variants; post hoc choices stay exploratory.

Contracts: [raw recording specification](reading-measurement-pilot.md), [audit](scanpath-pilot-audit.md). [Valliappan et al.](https://www.nature.com/articles/s41467-020-18360-5) and [Lepton](https://storage.googleapis.com/gweb-research2023-media/pubtools/4596.pdf) provide phone-reading precedents with different devices/populations and coarse/timing limitations; neither validates NewsMead's natural word paths.

## Immediate implementation order

1. **Offline rule layer implemented:** P01–P13 registry/classifier, unknowns, continuous values and fixtures. Freeze/inspect configuration before applying study labels; P11 v1 covers A–B–A–B.
2. **Implemented:** Android causal word/line extractor and P01–P13 engine, with synthetic causal-prefix parity and immediate boundary checks.
3. **Implemented experimentally:** independent pattern-to-aid policy with target, type, variable word/focus strength, duration, release and suppression reasons.
4. **Integrated/build-checked:** adjustable line/word/focus displays, reader controls and bounded numeric action/render journal. Synthetic engine-to-policy checks pass; Android geometry/render tests compile and still need device execution.
5. Build the matched raw placement/sequence instrument for the completed core and finish the phase-specific participant plan. Do not make new participant measurement a prerequisite for steps 2–4.
6. After prototype completion and collection authorization, run prompted feasibility, ordinary-reading shadow evaluation and controlled aid/strength trials. Use development results to revise/freeze the candidate policy, then evaluate on untouched readers/passages.

Focused extraction check:

~~~powershell
python -m unittest discover -s tools/gaze -p test_analyze_reading_measurement.py -v
python -m unittest discover -s tools/gaze -p test_scanpath_patterns.py -v
~~~

The v4 audit's 31 extraction tests are retained. New software tests cover named
patterns, geometry/availability, configuration and raw-record/CLI integration.
These checks establish software behavior. The Android causal engine and aid
prototype now build; device processing/render timing and participant validation
remain pending. See the detector dictionary and runtime guide for exact contracts.

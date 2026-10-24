# Historical narrow option: three-state gaze-adaptive line guide

**Version:** 2026-09-23. **Population:** adults aged 45+ reading news on the on-device Android prototype. **Status:** historical narrower study option. It assumes one line cue with three strengths and a learned support-need model. The [current NewsMead implementation proposal](adaptive-visual-aid-proposal.md) instead builds from ordered scanpaths through descriptive pattern classification to a configurable choice of visual aids; it does not require those narrow assumptions. This file is retained to preserve the earlier study context, not as the current feature specification.

## Proposal in brief

NewsMead will display a **visual line guide whose strength changes with the reader's difficulty keeping their place**. Phone gaze estimates the line being read. A separately validated model estimates the current *need for place-keeping support* from recent reading behavior. The guide has three states: absent, gentle, and prominent. Its position follows the text in both fixed-strength and adaptive conditions. The central comparison is therefore **adaptive strength versus fixed strength**, not a moving aid versus an immobile one. Development data set the model's coefficients and decision boundaries; new readers test detection before a counterbalanced study compares aid policies. Reading time, comprehension, wrong cue placement, reported place-keeping difficulty, and distraction are measured separately.

## Questions and operational meaning

1. **Measurement:** Can the intended phone identify the line to support, including line changes, with useful accuracy and coverage in adults 45+?
2. **Detection:** Can past phone-gaze observations predict an independent report of current difficulty keeping or finding the line on new readers and passages?
3. **Intervention:** Does changing guide strength according to that estimate benefit readers compared with no guide and the same guide held at fixed strengths?

**Difficulty** means *the reader reports trouble staying on, finding, or returning to the intended line and wants visual place support*. It is not a diagnosis of poor comprehension, cognitive decline, mind wandering, or harmful rereading. The detector can be wrong, so its errors can be measured. A regression, long dwell, fixation, or `4 → 5 → 4 → 5` sequence is never called difficulty by itself.

**Prespecified hypotheses:** (H1) the frozen gaze model predicts reported support need on held-out readers better than a no-gaze baseline; (H2) the adaptive-strength policy shortens silent article completion time relative to fixed gentle support; (H3) any time advantage is acceptable only if the prespecified comprehension-loss bound is ruled out. Distraction and wrong-line cues are required safety/acceptability results, not optional anecdotes. H1–H3 are **hypotheses to falsify**, not effects already established by the cited studies.

## Primary-study basis and transfer limits

| Primary study | Finding used here | What it does **not** establish |
| --- | --- | --- |
| [Wang et al. (2024), *GazePrompt*](https://doi.org/10.1145/3613904.3642878) | A gaze-aware line cue updated with readers' line following, switching, and jumps. In 13 low-vision readers using enlarged desktop text, line support reduced line-switching time during reading aloud. | It did not significantly improve line-switching accuracy and does not validate NewsMead phone gaze, adults 45+, silent news reading, or **strength changes**. |
| [Rummens and Beier (2025 online; 2026 issue)](https://www.tandfonline.com/doi/full/10.1080/00140139.2025.2514600) | Live gaze-contingent word color improved some efficiency measures in 70 Danish second graders reading aloud, while readers preferred plain black text. | This motivates testing distraction; it does not justify a NewsMead word cue or prove comprehension safety for adults. |
| [Valliappan et al. (2020)](https://doi.org/10.1038/s41467-020-18360-5); [Carr et al. (2022)](https://doi.org/10.3758/s13428-021-01554-0) | Phone gaze can support coarse reading analyses, while multiline gaze-to-text assignment can suffer vertical drift even with laboratory tracking. | Neither provides NewsMead line accuracy, thresholds, or a difficulty detector. |
| [Warrington et al. (2018)](https://doi.org/10.1037/xlm0000543) | Reading and display-quality effects vary across young, middle-aged, and older groups. | Age 45+ is a study population choice, not proof this aid helps everyone in it. |
| [Schotter, Tran, and Rayner (2014)](https://doi.org/10.1177/0956797614531148) | Experimentally preventing rereading reduced comprehension. | A backward move must not automatically increase support. |
| [Cheong, Bowers, and Lovie-Kitchin (2009)](https://doi.org/10.1097/OPX.0b013e3181b4c4d9) | A physical line guide helped some navigation in 29 readers with age-related macular degeneration but slowed reading overall; preferences differed. | A constant or prominent guide may impose a cost; different device and population. |
| [Klasnja et al. (2015)](https://doi.org/10.1037/hea0000305) | Repeatedly randomizing digital support at decision points is an established way to learn when adaptive components help. | Study-design precedent from mobile health, not proof of NewsMead benefit. |

These studies support the **question, cue family, and evaluation design**. None gives a validated NewsMead formula, feature weight, cutoff, high-strength appearance, or benefit estimate. Those are outputs to test, not facts to borrow. The [evidence audit](gaze-proposal-evidence-audit.md) explains why previous line-pattern and weighted-index claims exceeded their sources.

## Aid and controller

The article layout stays the same across conditions. A translucent line-aligned overlay is drawn without changing text wrapping. The **same cue form** is used at both active strengths; only its contrast changes. A target-user formative test will select two clearly distinguishable, readable contrast levels and freeze the color and opacity before the main study. This is design calibration, not a search for the main study's best result.

| State | Display | Policy interpretation |
| --- | --- | --- |
| **0 — none** | No line guide. | Predicted support need is low, or placement is uncertain. |
| **1 — gentle** | Low-contrast emphasis on the estimated current line. | Predicted support need is moderate. |
| **2 — prominent** | The same emphasis at a higher, usability-checked contrast. | Predicted support need is high. |

In states 1 and 2, the cue follows the reader's estimated line. It moves only when a frozen stability rule supports the new document position. During gaze loss, uncertain adjacent lines, scrolling, or reflow, the aid abstains and logs why; missing gaze is **not** severe difficulty. A prominent cue is a candidate stronger aid, not an established treatment. It must not cover words or mask neighboring text.

Predeclare a small candidate feature set the phone may measure: time within a line or region relative to visible text amount, signed changes between reliably assigned lines, and repeated visits to a region. Observation-quality measures gate **whether to abstain**, never whether a reader is struggling. Compare a no-gaze baseline with a simple regularized ordinal model of these features. Learn coefficients from independent labels, not hand weights. Drop any feature the phone cannot measure reliably. The model maps calibrated predicted need to states 0, 1, and 2; select decision boundaries in development using observed costs of unwanted prominent cues and missed help, then freeze them. Use only past observations. The old Adaptive Instability Index, its 0.40/0.35/0.25 weights, and four scaffold thresholds are **not** this detector.

## Study protocol

### Phase A — measurement and development

Recruit adults 45+ with variation in age, vision correction, and ordinary reading font choices. Use the intended Android phone, article layout, calibration, posture, and **the same gaze-dot setting in every later condition**. The current single 22 dp known-target run reported 61.6% guided exact-line and 99.2% within one line; neither is natural-reading line-switch accuracy. Repeat known-target line trials at all intended font sizes. Record exact-line and neighboring-line confusion, lag, usable coverage, false jumps, scrolling errors, and per-reader failures. A neighbor-line hit is not exact placement.

For independent difficulty labels, leave the aid off. At sparse unpredictable pauses ask: **“Just before this pause, how much help would you have wanted to keep or find your line?”** Responses are *none, a little, or a lot*. Permit an optional “lost my place” tap while reading. These responses do not derive from gaze. They measure **perceived support need**, not objective comprehension difficulty; probes can themselves disturb reading, so Phase A cannot demonstrate intervention benefit. Time-stamp the pre-prompt gaze window and answer separately. Include ordinary news passages and report how often each label occurs. Participants also try the gentle and prominent cue appearances in a short usability task and rate readability and distraction. Freeze visual styles, feature definitions, quality rules, and model-fitting procedure after this phase. Do not reuse these readers or passages as independent validation.

### Phase B — held-out measurement and detector validation

On new readers and passages, run the frozen line mapper and need model **without showing the adaptive aid**. Report exact-line accuracy, coverage, abstentions, and errors per reader and font. For predicted need, report the full three-by-three confusion table, class-wise sensitivity and precision, calibration, and false prominent-cue rate, all with uncertainty. Compare against the no-gaze baseline. Hold out whole readers and passages; repeated windows from one reader are correlated.

Before Phase B begins, freeze the **minimum acceptable correct-line placement**, **maximum tolerable wrong/prominent-cue rate**, and **minimum improvement in decision value over the no-gaze baseline** using Phase A user-rated costs and the intended study task. Publish those numeric gates with their denominators and uncertainty rule before seeing Phase B outcomes. No paper in the evidence ledger supplies defensible NewsMead values for them. A gate that fails or is too imprecise does not pass by changing the denominator, retuning the model, or counting neighboring lines as exact.

An independently labeled reading-aloud subtask can check gross line progression through word-aligned audio, but spoken transitions are not exact eye-movement timing. A calibrated laboratory tracker would be a stronger natural-reading reference used only for validation, not deployed with the app. If only structured targets and speech are available, restrict the placement claim accordingly. **Stop the adaptive-efficacy claim** if held-out line placement or need prediction is too unreliable to distinguish useful support from misdirected cues. Report feasibility and errors instead.

### Phase C — randomized policy comparison

Use **new readers and passages** in a counterbalanced within-reader study. Phone gaze is recorded in all conditions. The location and quality rules are identical in the three aided conditions:

1. **Plain:** no added guide.
2. **Fixed gentle:** the guide follows gaze but stays gentle whenever location is valid.
3. **Fixed prominent:** the guide follows gaze but stays prominent whenever location is valid.
4. **Adaptive:** the same guide follows gaze and selects none, gentle, or prominent using the frozen need model.

Rotate matched news articles through all conditions and randomly assign readers to balanced condition/article orders. Give practice reading and allow breaks. Hold font, camera use, gaze-dot condition, article style, instructions, and question format constant. Record every assigned trial and cue transition, including abstentions and wrong placements. Do not exclude difficult readers or windows merely because tracking failed; report all-assigned results and eligible-time diagnostics separately.

If the gaze dot remains visible, the result is the **incremental effect of the line guide with that dot present**. It cannot be generalized to a hidden-dot version without a separate matched test.

**Primary endpoint:** silent-reading article completion time in seconds, measured independently of the gaze model. **Key comprehension safeguard:** questions fixed for each article before condition assignment, including content from prespecified text regions. Do not select questions after observing where prominent support appeared. Call the adaptive policy a reading-efficiency benefit only if its time advantage over **fixed gentle** is supported **and** the data rule out a predeclared meaningful comprehension loss. Adaptive versus fixed prominent and adaptive versus plain are planned secondary contrasts. Also report reader-rated place-keeping difficulty, distraction, preference, wrong-line cue rate, and the proportion of reading time spent in each state. Shorter time alone does not prove better understanding; a nonsignificant comprehension difference does not establish safety.

Treat the reader as the independent unit and account for article and condition order. Estimate within-reader time differences with confidence intervals; analyze repeated articles/events with reader- and article-aware models or reader-level summaries. Report comprehension differences and intervals against a **meaningful-loss margin chosen before confirmatory collection with target users and thesis advisers**. Prespecify multiplicity handling for the secondary contrasts, inspect assumptions and missingness, and never define success by the detector's own score returning to baseline.

### Sample-size rule and scope

The manuscript's approximate **15 readers** can support a bounded feasibility pilot, not a confirmatory three-category detector, adaptive-efficacy claim, or proof of no comprehension harm. From Phase A, estimate reader-level variation in paired completion time, need-category prevalence, unusable-gaze and attrition rates, and comprehension variation. Before Phase C recruitment, state the smallest useful time saving and acceptable comprehension loss in ordinary units; calculate the required reader count for **both** requirements, add expected attrition, and freeze recruitment and analysis. Do not import GazePrompt's effect size from low-vision desktop readers. If feasible recruitment cannot meet the count, label Phase C exploratory and report intervals without a confirmatory claim. This sample-size **decision rule** is fixed now; a numeric confirmatory sample would be invented without the pilot inputs.

## Falsification and interpretation

| Finding | Supported conclusion |
| --- | --- |
| Phone line placement fails. | The present phone/layout cannot reliably locate this cue. |
| Placement works but need prediction fails. | A moving line guide may be studied, but **difficulty-adaptive strength is not validated**. |
| Prediction works but prominent support distracts or slows readers. | Detection alone does not justify stronger support. |
| Adaptive beats plain but not fixed gentle or prominent. | The aid may help; added value from changing strength is unproven. |
| Adaptive beats fixed gentle with acceptable comprehension, distraction, and uncertainty. | Evidence supports this strength-changing NewsMead aid on the tested phone, fonts, readers, and tasks. |

No result establishes a universal cognitive-instability index or proves rereading is harmful. An inconclusive result still identifies a measurement, detection, or intervention limit.

## Manuscript and implementation alignment

NewsMead contains four scaffold renderers and a weighted Adaptive Instability Index, but availability is not evidence for this **three-state policy**. The two active strengths could use the existing line-overlay renderer after formative calibration; this document does **not** authorize an app behavior change or device collection. The thesis in `../../THS-ST-Paper/chapter_3.tex` and `../../THS-ST-Paper/chapter_4.tex` still claims a weighted difficulty index, four graded levels, and “recovery latency” defined by the same index returning to baseline. If this research direction is adopted in the manuscript, replace those claims with the independently labeled need model, three-state aid, four-condition evaluation, and external outcomes **before participant enrollment**. The manuscript was not edited here.

This is a targeted appraisal of primary studies and a supporting **candidate research plan**, not a claim that novel operational decisions already have published validation. The study is designed to decide whether those decisions work. No participant recording, camera-frame retention, or device installation occurred while preparing it.

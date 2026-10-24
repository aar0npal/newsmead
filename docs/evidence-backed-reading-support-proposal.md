# Evidence-backed NewsMead proposal: find the reading place after an interruption

**Status:** Historical **interruption-bookmark option**, prepared 2026-09-23 and withdrawn as the recommended primary direction after the project owner clarified that the aid must adapt during ordinary reading. See the [current adaptive-visual-aid proposal](adaptive-visual-aid-proposal.md). This file remains as evidence and a possible later re-entry feature; it is not an approved participant protocol. The [claim-by-claim audit](gaze-proposal-evidence-audit.md) explains why the earlier line-visit controller was withdrawn.

## The claim this study can test

**Research question:** When an interruption to phone news reading is known, can a cue marking the reader's last reading area help adults aged 45+ find their place, and can NewsMead's front-camera gaze estimate place that cue reliably enough to help?

The proposed product behavior is narrow: at a **known interruption**, NewsMead records the last plausible reading area. When the article returns, it briefly highlights that area. It does not diagnose confusion, mind wandering, harmful rereading, or a general state of “reading instability.” It does not need a weighted score, personalized baseline, fixation/regression/dwell combination, ordered-line grammar, or four scaffold levels. Gaze supplies a *location estimate* for one cue, not a judgment about the reader's comprehension.

This is a deliberate change from the manuscript's present continuous-instability and four-level account. If continuous spontaneous assistance is essential to the thesis, the evidence below does **not** yet justify an online trigger for it. That would require a separate study with an independently defined need-for-help outcome.

## What comes from studies, and what does not

| Proposal element | Primary evidence | Limit of the evidence |
| --- | --- | --- |
| A spatial cue can help after a known reading interruption. | [Cane, Cauchard, and Weger (2012)](https://doi.org/10.1080/17470218.2012.656666) experimentally interrupted paragraph reading; highlighting the interruption word aided resumption in their second experiment. | Their retained second-experiment sample was 28, mean age 19.54; they used a 1000 Hz laboratory eye tracker, head restraint, and a desktop display. This is a reason to **test**, not assume, an effect on a phone at age 45+. |
| A broader cue is a studied form of the same idea. | [Jo, Kim, and Seo (2015), *EyeBookmark*](https://idclab.skku.edu/assets/EyeBookmark.pdf) tested point, sentence, and block bookmarks. Block and sentence cues shortened annotated resumption lag relative to no cue in controlled reading. Their block highlighted a fixed number of lines including and preceding the last fixated word. | The main study had 13 Korean adults aged 20–28, a Tobii X60 desktop tracker, and a chin rest. It did not show a significant comprehension gain. Its exact highlight geometry, timing, and effect size cannot be imported into NewsMead. |
| A known interruption and separately annotated resumption are usable study methods. | [Zermiani et al. (2024), *InteRead*](https://aclanthology.org/2024.lrec-main.802/) supplies an interrupted-reading dataset and separate resumption annotations. | It is a measurement precedent, not proof that a NewsMead cue helps. |
| Phone gaze can be informative during reading. | [Valliappan et al. (2020)](https://doi.org/10.1038/s41467-020-18360-5) studied front-camera phone gaze, including scrolling reading. | Different phone, model, task, and participants. It does not establish NewsMead's exact-word, line, or block placement accuracy. |
| The 45+ population merits its own reading evaluation. | [Warrington et al. (2018)](https://doi.org/10.1037/xlm0000543) measured age- and display-related variation in reading across young, middle-aged, and older groups. | Age effects do not show that this cue benefits all adults 45+ or that this group has a specific interruption deficit. Age 45+ is the thesis population, not a proven treatment-response boundary. |
| Rereading must not be labeled a failure by default. | [Schotter, Tran, and Rayner (2014)](https://doi.org/10.1177/0956797614531148) experimentally restricted rereading and found a comprehension cost; [Chevet et al. (2022)](https://doi.org/10.1016/j.learninstruc.2021.101565) observed more rereading after interruptions to digital reading. | Neither study supplies a NewsMead rule that detects when rereading is harmful or when a cue should appear. |

**Evidence boundary:** Cane and Jo support the *cue-after-interruption hypothesis*. No cited study validates the full combination of NewsMead hardware, front-camera placement, phone news layout, adults 45+, and this specific outcome. Demonstrating that combination is the thesis contribution. A novel operational choice is acceptable only when declared, frozen before the main comparison, and evaluated; it must not be described as a published fact.

## Proposed intervention and fair comparisons

Use **one** visual bookmark: a modest block around the last reading area, including a little preceding context, based on EyeBookmark's block condition. The font, article layout, gaze-dot condition, background, and interruption task stay the same across comparisons. The exact block size, color, visibility time, and how the user dismisses it are **NewsMead design parameters**, not values established by EyeBookmark. Select them through a small usability/placement pilot with the target population; record the selection rule and freeze them before the main comparison. Do not select a design because it produced the best main-study outcome.

For the controlled study, every trial has the same externally scheduled interruption and the same brief pre-interruption location report: at the interruption signal, the participant taps the word or text area they had reached before the article is hidden. NewsMead freezes its gaze estimate **before** the signal and tap, so the tap cannot leak into gaze placement. The tap provides a separate, imperfect reference and is performed in every condition. The article then disappears for a 60-second spoken-news task with a brief attention check, closely following Cane's interruption method. Rotate clips across conditions so clip difficulty is not tied to one cue condition. This tap may help memory or change natural reading, so the result applies first to **marked, controlled interruptions**; an unmarked real-world setting needs later validation.

Randomize and counterbalance three return conditions within readers:

1. **No cue:** article returns without highlighting.
2. **Reference cue:** the same block is placed around the participant's pre-interruption tap. This estimates what a correctly located cue could achieve under the study procedure.
3. **Gaze cue:** the same block is placed from NewsMead's gaze estimate frozen before the interruption signal. If the estimate fails its prespecified quality rule, the system abstains; that trial stays in the gaze-assigned analysis and the abstention is reported.

The **gaze cue versus no cue** contrast is the main test of the actual phone feature. **Reference cue versus no cue** tests the cue idea with placement uncertainty reduced. **Gaze cue versus reference cue** estimates what is lost when gaze replaces a known location. All three have the same pre-interruption tap, so this experiment cannot claim to measure the convenience of removing that tap in everyday use.

## What counts as success

**Primary outcome: place-finding time.** Start the clock when the article returns. Stop it when the participant first taps the correct return area, using the pre-interruption location report as the trial reference. A wrong tap is recorded, and the participant can correct it; record time to the first correct tap plus first-tap error separately. Call this *place-finding*, not full cognitive recovery or comprehension. This tap outcome is a **new NewsMead laboratory proxy**, not an endpoint validated by Cane or EyeBookmark. The study must predefine the acceptable return area and timeout from the pilot before main data collection. Ask participants to resume and finish the article afterward, so the test does not end at merely finding a highlighted box.

**Secondary outcomes:** wrong return taps, article completion time, comprehension questions tied to the interrupted and later text, perceived usefulness/distraction, cue-placement errors, and gaze abstentions. A faster tap alone cannot establish better understanding. A nonsignificant comprehension difference cannot establish that comprehension is unharmed; report estimates and uncertainty, and set an acceptable loss margin before making a non-harm claim.

The pre-interruption tap is an *independent reference*, not infallible ground truth. Flag uncertain reports and check their rate by condition. The participant's final tap is independent of NewsMead's gaze algorithm as a measurement channel, although both concern the same text location. If the intended outcome is true *reading-resumption lag*, add an independent reference/annotation method and measure annotator agreement; do not define recovery as the gaze score that triggered the cue returning to baseline.

## Study sequence and gates

### 1. Placement and usability pilot

Recruit from the intended 45+ population and use the intended phone, final text sizes, posture, calibration, and visible-dot condition. Capture the gaze estimate before each interruption and compare its block placement with the independent location report. Record per-reader and overall exact-block agreement, overlap with the reported area, neighboring-line confusion, false placement, placement lag, valid-sample coverage, abstention, and reasons for missingness. Inspect scrolling, layout changes, and reflow separately. Use distinct pilot readers/passages to select a block size and quality rule. The current single 22 dp known-target run (61.6% guided exact-line; 99.2% within one line) cannot substitute for this measurement, and neither percentage should be renamed block-placement accuracy.

Before the main comparison, specify the largest cue-placement error rate and abstention rate acceptable for the actual user cost of a wrong or absent cue. Base those limits on pilot feedback and the desired place-finding benefit, not on a number copied from another tracker. If phone placement does not meet the frozen criterion, test the reference/user-placed cue as a reading aid and report that gaze automation failed its feasibility gate. Do not relabel it as a gaze-driven success.

### 2. Controlled 45+ comparison

After fixing the cue and analysis, use new readers and passages for the three-condition study above. Match article length and difficulty as well as practical, distribute each passage across conditions, and counterbalance condition order. Give a practice interruption before measurement. Record all assigned trials, including wrong taps, missing gaze, abstentions, and incomplete responses. Do not discard difficult episodes solely because the tracker had poor data; report their denominator and effect on deployable performance.

The primary comparison is the within-reader difference in place-finding time between **gaze cue and no cue**, with a confidence interval and the error/failure rates beside it. Analyze repeated interruptions as nested within readers and passages, or summarize each reader first; dozens of episodes from one person are not dozens of independent participants. The reference-cue contrasts are planned secondary comparisons. Report heterogeneity by age band, vision correction, font size, and gaze quality descriptively unless powered for subgroup claims.

The manuscript's approximate **15 participants** would be a feasibility sample for this new outcome unless a reader-level sample-size calculation supports more. Use pilot variation, missingness, a stated smallest useful time saving, and the planned analysis to calculate the confirmatory sample before recruitment. Do not borrow EyeBookmark's young-adult effect size as if it applies to this population and phone.

### 3. Claim only what the result establishes

| Result | Permissible claim |
| --- | --- |
| Reference cue helps; gaze placement fails. | A spatial cue may help in this controlled phone task; the current gaze implementation is not ready to place it. |
| Gaze placement passes; gaze cue does not help. | The phone can estimate the location under test conditions, but that does not yet yield a useful intervention. |
| Gaze cue helps, with reported failures and no unresolved comprehension concern. | A gaze-placed bookmark improves measured place finding after marked, known interruptions in the tested 45+ readers and phone setup. |
| Results are imprecise or mixed. | Feasibility and effect estimates, with no confirmatory efficacy claim. |

No result here establishes detection of spontaneous confusion, continuous reading instability, or a benefit from four graded scaffolds.

## Relation to the existing project

NewsMead's existing weighted Adaptive Instability Index, four scaffold renderers, and `4 → 5 → 4 → 5` line-cycle idea remain historical/exploratory. They are not components of this proposed primary test. The current [measurement protocol](reading-validation-protocol.md) and [first natural-reading diagnostic](reading-measurement-run-01.md) remain useful for understanding calibration, geometry, and missingness, but neither validates this cue. The source in `../../THS-ST-Paper/chapter_3.tex` and `../../THS-ST-Paper/chapter_4.tex` still describes the old trigger and a recovery endpoint defined by that same index. If this direction is selected, revise the manuscript's question, mechanism, comparison, and endpoint **before** participant collection; do not present the two mechanisms as one tested framework.

This is a targeted reading of primary studies, not a systematic review. No participant capture, device installation, or new validation run was performed to prepare this proposal.

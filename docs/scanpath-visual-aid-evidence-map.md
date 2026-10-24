# Scanpath behavior to visual-aid evidence map

**NewsMead targeted primary-study review, 2026-09-24.** This companion to the [classification dictionary](scanpath-classification-map.md) asks which visual response has actually been tested for a closely related reading movement. A [table-form PDF edition](../output/pdf/newsmead-scanpath-visual-aid-evidence-map.pdf) is also available. The review is targeted, not systematic or exhaustive. An observed movement is not itself a difficulty diagnosis or proof that a cue is wanted. The [implementation proposal](adaptive-visual-aid-proposal.md) remains the feature specification; this map refines the evidence behind its aid candidates.

## Evidence key

**2026-09-30 design checkpoint:** the [independent aid design](scanpath-adaptive-aid-design.md)
uses task-specific types and within-type strength, with study references for each
rule. Added N23 as a ruler-form precedent. Its aid results do not fill the gap
between slow/cyclic scanpaths and need for help; retain exact form/condition IDs.

| Relationship | Meaning |
| --- | --- |
| **Movement-matched aid test** | A study changed a visual aid and measured a closely related movement or place-finding outcome. Population, task, display, or cue timing may still differ from NewsMead. |
| **Aid test, trigger untested** | The aid changed an outcome, but the study did not detect the proposed NewsMead pattern and activate the aid because of it. |
| **Caution or mechanism** | A manipulation established a possible cost or reading mechanism, not an aid benefit. |
| **No direct aid evidence located** | The pattern-to-aid rule remains a NewsMead hypothesis. |

The evidence relationship applies to the **whole chain** from observed pattern to aid outcome. A fixed line marker that changes return-sweep landings does not prove a phone can predict a sweep early enough to add it adaptively.

Parenthetical A1–A4/B labels in the classification column refer to the
[classification dictionary](scanpath-classification-map.md), not the pilot's
separate A0–A4 aid IDs. Record namespace and exact form when tracing a rule.

**Timing distinction:** Slattery and Vasilev's line-start bolding was continuously visible before the sweep. GazePrompt identified the target from recent fixations and augmented the new line after detecting the sweep. Its faster switching result does not show that an early cue improved the initial landing.

## A. Local movements and recognition rules

| Classified observation | Candidate visual aid | Primary finding and evidence relationship | NewsMead decision and missing proof |
| --- | --- | --- | --- |
| **Line switching, Return Sweep, or undersweep correction** (A2/A3/A4/B) | Emphasize the next line's beginning with a subtle first-word bold/marker, or a line highlight/arrow. It must appear before it could guide landing. | **Movement-matched:** [Slattery & Vasilev (2019)](https://doi.org/10.3758/s13414-019-01742-3) permanently bolded line-initial words for 32 adult readers; sweeps landed nearer the left margin and required fewer corrective saccades, but page time and comprehension did not improve. **Aid test in low vision:** [GazePrompt (Wang et al., 2024)](https://doi.org/10.1145/3613904.3642878) offered a gaze-aware line highlight/arrow to 13 low-vision adults; line-switch time fell versus baseline (p=.014), while switch error frequency/magnitude did not improve significantly. | **Prioritize as a tested candidate**, with static line-start and no-cue controls. Validate line placement and cue latency. Neither study validates the phone detector or an automatically chosen cue strength. |
| **Line following, ordinary Progressing or Fwd** (A1/A2/A3) | Optional low-salience current-line highlight or continuous color change on the fixated word. | GazePrompt's quantitative gain concerns line switching, not cueing every normal step. [Rummens & Beier (2025 online; 2026 issue)](https://doi.org/10.1080/00140139.2025.2514600) found 70 Danish second graders read aloud faster when each fixated word changed color, with shorter fixations, fewer regressions/re-readings, and no measured accuracy or comprehension loss; they preferred static text. **Aid test, trigger untested** for older readers. | Do not infer support need from normal progress. If tested, make word coloring optional and compare with static/no cue. The child oral-reading result does not validate older silent phone reading or activation only after a slow/repeated event. |
| **Line jumping or abrupt multiline relocation** (A2/A3) | Stable current-line or read-progress marker; perhaps a paragraph-level trail that preserves the reader's chosen destination. | [See Where You Read (Yang et al., 2026)](https://doi.org/10.1145/3803853) combined tracking with paragraph highlighting. Its 24-volunteer field test reported average answer-finding time of 97.3 s versus 113.4 s for its blank baseline; controlled jump-relocation accuracy was 84%. **Aid test, trigger untested:** the result belongs to the combined system, not an aid isolated after a wrong-line jump. GazePrompt's line jumping is a recognition rule, not proof of an error. | Experimental only. Distinguish intentional preview/review from lost place and phone alignment error. Test an unobtrusive marker against no cue and a fixed marker; log wrong destinations and user override. Do not force gaze back. |
| **Same-word refixation, long word residence, local word return or Short Re-reading** (A1/A3/B) | Local word color emphasis; reader-invoked word magnifier when acuity calls for it. Keep neighboring words visible. | **Trigger tested, benefit unconfirmed:** GazePrompt used first-fixation duration, refixations, and one-pass dwell to trigger magnification or speech, but maximum first-pass fixation time (p=.86) and misread-word count (p=.24) did not improve significantly in 13 readers; magnifier benefit was not isolated from speech. Ten of 13 lengthened trigger thresholds to avoid false positives. Rummens & Beier tested continuous fixation-linked coloring in children, not a refixation-triggered magnifier. | No automatic word magnifier justified yet. Keep reader-requested magnification as an accessibility option. Test word color separately after exact-word placement is reliable; assess recognition, comprehension, wrong-word cues, occlusion, and preference. Fewer refixations alone is not success. |
| **Fwd+ or apparent Skipping** (A1/A3) | No forced “go back” cue. An optional progress marker could be tested after a clearly voluntary jump. | Olivier's Fwd+ is a word-distance code and Yamaya's Skipping is a segment annotation. Neither proves intervening words were unread. **No direct aid evidence located** for automatically magnifying or highlighting text from either label. | Do not trigger a corrective aid from this label. Validate text mapping and independent comprehension before defining a need state. |

## B. Longer paths and timing observations

| Classified observation | Candidate visual aid | Primary finding and evidence relationship | NewsMead decision and missing proof |
| --- | --- | --- | --- |
| **Multiline return, repeated line revisit, within-line or between-line regression** (A3/A4/B) | Preserve previously read text; optionally leave a quiet line/region anchor while the reader rereads. | **Caution:** [Schotter, Tran, & Rayner (2014)](https://doi.org/10.1177/0956797614531148) masked words after first pass so readers could not use regressions; comprehension worsened. This supports access to rereading, not an automatic cue. [Cheong et al. (2009)](https://doi.org/10.1097/OPX.0b013e3181b4c4d9) tested a physical line guide with 29 people with AMD: some vertical control improved, but retrace corrections rose, navigation took longer, and speed fell by about 6 words/minute. | No forced escalation or masking. A context-preserving anchor is only a test candidate for spontaneous revisits. Compare it with no cue; measure misdirection, comprehension, and burden. |
| **Resuming after an earlier-line detour** (A3/B) | Highlight a known departure point, sentence, or block for re-entry, leaving the detour text readable. | **Movement-matched only to a known interruption:** [Cane et al. (2012)](https://doi.org/10.1080/17470218.2012.656666) highlighted the interruption word; after a forced 60-second interruption, rereading time was 3.71 versus 5.90 s (p&lt;.001) in 28 young adults. [EyeBookmark (Jo et al., 2015)](https://doi.org/10.1145/2702123.2702340) found block/sentence highlighting reduced resumption lag from 2.4 to 1.4 s in 13 young adults, without significant comprehension gain. | Use only when departure is known; spontaneous multiline rereading is not a known interruption. A bookmark after an unobserved detour requires its own detector and evaluation, and is not the project's primary adaptive response. |
| **Sustained line residence or slow within-line movement** (B; timing, not order) | A context-preserving focus-region emphasis is an **experimental option**. Avoid dark masks or narrow single-word windows as a default; compare a simpler line anchor and no cue. | **No matched aid benefit found.** [Rayner, Slattery, & Bélanger (2010)](https://doi.org/10.3758/PBR.17.6.834) and [Paterson et al. (2013)](https://doi.org/10.1037/a0030350) varied available or filtered surrounding text to measure perceptual span; they show that context matters, including in older adults. These are measurement manipulations, not focus-aid trials. [Huth et al. (2024)](https://doi.org/10.1145/3649902.3653521) found static highlights drew fixations toward highlighted regions, without significant reading-time or answer-accuracy benefit in 12 mostly young adults. | Keep out of the automatic policy until tested. First rule out tracker loss, long text, complex content, or deliberate rereading. Compare no cue, line anchor, and lightly emphasized region on comprehension, place keeping, pace, distraction, and context loss. |
| **Gap-spanning jump or ambiguous assignment** (B; missing evidence) | Hold or clear a stale cue; wait to reacquire location before a new gaze-conditioned aid. | No intervention study validates this NewsMead abstention rule. The path across missing samples was never observed; [Carr et al. (2022)](https://doi.org/10.3758/s13428-021-01554-0) documents difficult line assignment under drift. | Measurement safeguard. Log wrong cues prevented and potentially useful cues withheld. Do not relabel a gap as skipping or return. |

### Additional placekeeping form: digital reading ruler

[Niklaus et al. (2023)](https://doi.org/10.1145/3544548.3581367) supplies a
mouse-controlled ruler-form comparison, not a slow/return-triggered gaze policy.
Primary conditions, corrected comparisons and source locations are recorded in
the [aid design contract](scanpath-adaptive-aid-design.md). Keep the ruler forms
distinct from a focus-region condition and do not infer stronger help from a
long residence or cycle alone.

## C. Whole-path clusters and modeled phases

| Classification family | Visual-aid implication | Evidence boundary |
| --- | --- | --- |
| von der Malsburg A/B/C and exploratory D-Q; Parshina fluent/intermediate/beginner | No automatic aid assigned. These are study-specific clusters of complete sentence paths, not a momentary phone event. | Neither cluster study tested an aid conditional on cluster membership. |
| Olivier NR/IS/FR/SC | No automatic aid assigned. These are model-inferred phases of runs of word codes from a topic-judgment task. | No study here shows an inferred phase selects the correct NewsMead aid or strength. |

## Study settings that limit transfer to NewsMead

| Source | Readers and task | Display or tracker | Why the result cannot be copied directly |
| --- | --- | --- | --- |
| [Slattery & Vasilev (2019)](https://doi.org/10.3758/s13414-019-01742-3) | 32 English-speaking adults silently read two desktop stories. | EyeLink 1000 at 1,000 Hz, head support; every line's first word stayed bold throughout one story. | A static cue changed landing/correction measures, not phone cue timing or reading benefit. |
| [GazePrompt (2024)](https://doi.org/10.1145/3613904.3642878) | 13 low-vision adults read enlarged web passages aloud. | Tobii Pro Fusion at 120 Hz and 24-inch desktop display. | Its line cue appeared after detected sweep; word support combined magnification and speech choices. |
| [Rummens & Beier (2025/2026)](https://doi.org/10.1080/00140139.2025.2514600) | 70 Danish second graders read aloud. | Gaze-linked coloring of each fixated word versus unchanged black text. | Continuous word coloring in children is not a response to detected adult refixation or slow progress. |
| [Yang et al. (2026)](https://doi.org/10.1145/3803853) | 24 volunteers in a field comparison answered questions after reading paragraphs. | Tobii Pro Spark and 15.6-inch laptop; tracking and paragraph highlighting operated together. | The timed outcome was question-answer time; neither natural-reading comprehension benefit nor aid-only benefit was isolated. |
| [Cane et al. (2012)](https://doi.org/10.1080/17470218.2012.656666); [Jo et al. (2015)](https://doi.org/10.1145/2702123.2702340) | Young adults resumed after imposed interruptions. | EyeLink 1000 or Tobii X60 desktop setups; interruption location known. | Their cues cannot justify labeling spontaneous rereading as an interruption. |
| [Paterson et al. (2013)](https://doi.org/10.1037/a0030350) | Young adults 18-30 and older adults 65+ read filtered text. | Research gaze-contingent moving window with frequency-filtered surrounding text. | It measured visual-information use, not whether a focus window helps an older phone reader. |

## Optional accessibility branch, separate from scanpath diagnosis

A reader may want larger or less crowded text regardless of the current scanpath. [Wallis, Yang, & Anderson (2018)](https://doi.org/10.1038/s41598-018-19859-0) presented one word at a time at its natural line position under **simulated** central vision loss in four normally sighted observers aged 16-39; each next word required a button press. Peripheral oral reading improved versus full short sentences at small text sizes. The experiment did not include people with macular disease, a spontaneous slow-progress detector, or NewsMead-style phone news reading. It supports testing reader-selected low-vision presentation modes, not automatically isolating words when a line appears slow.

## Evidence-based evaluation order

1. **Line transition aid first:** compare no cue, static line-start emphasis, and correctly timed gaze-aware line highlight/arrow. Prespecify line-switch time and wrong-line/undersweep correction outcomes, reading time, comprehension, and unwanted cues.
2. **Word aid after a word-mapping gate:** compare no cue, optional continuous fixation-linked word color, and reader-invoked magnification. Test a refixation-triggered version only after showing that the event predicts independent help need.
3. **Context-preserving orientation aids:** test a line/paragraph trail for genuine lost-place episodes, not all revisits. Keep reread text visible. Known interruptions can use a separately logged bookmark condition.
4. **Focus-region aid last:** treat sustained residence as a measured descriptor, not a diagnosis. Establish benefit against a line anchor and no cue before increasing strength or duration automatically.
5. **Independent adaptive test:** once an aid works, compare a pattern-conditioned policy with matched static exposure and no aid on held-out readers and passages. The triggering metric cannot be the only outcome.

No identified trial validates NewsMead's full phone sequence of pattern detection, visual-aid selection, adaptive strength, and improved reading outcome.

## Primary reference key

| Key | Primary source and role |
| --- | --- |
| S19 | [Slattery & Vasilev (2019), line-initial bolding](https://doi.org/10.3758/s13414-019-01742-3). |
| N23 | [Niklaus et al. (2023), Digital Reading Rulers](https://doi.org/10.1145/3544548.3581367); [primary PDF, Table 4](https://thereadabilityconsortium.org/wp-content/uploads/2023/08/Digital-Reading-Rulers-Evaluating-Inclusively-Designed-Rulers-for-Readers-With-Dyslexia-and-Without.pdf#page=11). |
| W24 | [Wang et al. (2024), GazePrompt line and word aid conditions](https://doi.org/10.1145/3613904.3642878); [author full text](https://arxiv.org/html/2402.12772). |
| R26 | [Rummens & Beier (2025 online; 2026 issue), gaze-linked word coloring](https://doi.org/10.1080/00140139.2025.2514600). |
| Y26 | [Yang, Yan, & Du (2026), See Where You Read progress highlighting](https://doi.org/10.1145/3803853). |
| C09 | [Cheong et al. (2009), line guide with stand magnification](https://doi.org/10.1097/OPX.0b013e3181b4c4d9). |
| ST14 | [Schotter, Tran, & Rayner (2014), trailing mask and rereading](https://doi.org/10.1177/0956797614531148). |
| CA12 | [Cane, Cauchard, & Weger (2012), cue after known interruption](https://doi.org/10.1080/17470218.2012.656666). |
| J15 | [Jo, Kim, & Seo (2015), EyeBookmark](https://doi.org/10.1145/2702123.2702340). |
| RA10 | [Rayner, Slattery, & Bélanger (2010), moving-window perceptual span](https://doi.org/10.3758/PBR.17.6.834). |
| PT13 | [Paterson et al. (2013), filtered gaze window and age](https://doi.org/10.1037/a0030350). |
| H24 | [Huth et al. (2024), static visual enhancement](https://doi.org/10.1145/3649902.3653521). |
| W18 | [Wallis, Yang, & Anderson (2018), Word Mode under simulated central loss](https://doi.org/10.1038/s41598-018-19859-0). |
| CR22 | [Carr et al. (2022), line-assignment drift cases](https://doi.org/10.3758/s13428-021-01554-0). |

**Review method:** applied the project's scientific critical-thinking procedure to separate observed patterns, intervention comparisons, aid benefit, and transfer to NewsMead. Procedural source: Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner, A. M. (2026), [*Scientific Agent Skills: A Library of Procedural Knowledge for Research Agents*](https://doi.org/10.48550/arXiv.2609.00065). This is a method reference, not evidence that any NewsMead cue works.

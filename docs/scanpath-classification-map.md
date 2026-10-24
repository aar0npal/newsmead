# Scanpath classification map and dictionary

**NewsMead reference, 2026-09-24.** This is a lookup companion to the [scanpath-to-visual-aid proposal](adaptive-visual-aid-proposal.md) and the [source-by-source sequence table](scanpath-sequence-behavior-table.md). A [table-form PDF edition](../output/pdf/newsmead-scanpath-classification-map.pdf) adds each label's spatial level, schematic rule or path, observed behavior and limit, and source status. It covers the named categories in the primary studies used for the current proposal. It is not a universal taxonomy of reading, a trained NewsMead classifier, or evidence that any pattern requires assistance.

For visual aids tested near these observations, use the [behavior-to-aid evidence map](scanpath-visual-aid-evidence-map.md) and its [PDF table](../output/pdf/newsmead-scanpath-visual-aid-evidence-map.pdf).

## Dictionary directory

- **A. Movements and recognition labels**
  - **Word-fixation transitions:** Olivier's `Fwd+`, `Fwd`, `Rfx`, `Bwd`, `Bwd-`.
  - **Line-fixation rules:** GazePrompt's line following, line switching, and line jumping.
  - **Reading-segment transitions:** Yamaya's Return Sweep, Progressing, Short Re-reading, Long Re-reading, Skipping, and Resuming.
  - **Multiline movement terms:** Parker's return sweep and undersweep/correction; Carr's within- and between-line regressions as line-assignment cases.
- **B. Ordered path and episode descriptions**
  - **Line/multiline:** return sweep followed by correction; earlier-line return followed by resumption.
  - **Word/line NewsMead illustrations:** word return and repeated line revisit. These exact arrow strings are proposed descriptors.
  - **Outside order:** long line residence is timing; a gap is missing evidence.
- **C. Empirical clusters of sentence paths**
  - **Sentence regions:** von der Malsburg and Vasishth's A, B, C, plus exploratory D-Q variants.
  - **Whole-sentence word paths:** Parshina et al.'s fluent, intermediate, and beginner clusters.
- **D. Model-inferred phases of word-code runs**
  - Olivier et al.'s normal reading (`NR`), information search (`IS`), fast reading (`FR`), and slow confirmation (`SC`).

Within each group, the entry gives its spatial unit, rule or characteristic path, reading-behavior observation, and primary source. A segment is a run of fixations and is not automatically one physical line. Sequence length alone does not determine the group: a four-visit line cycle remains a descriptive path unless a study defines and validates a cluster. `w` denotes document-order word position, `L` a physical line, and `S` a detected segment. Every arrow below is schematic, not a participant trace.

## A. Published movement and recognition dictionary

### A1. Word-to-word fixation transitions

[Olivier, Guérin-Dugué, and Durand (2022)](https://doi.org/10.16910/jemr.15.4.5) encoded the signed number of words crossed between successive word-assigned fixations while 15 retained French-speaking adults judged whether short texts related to a target topic. The device was an EyeLink 1000. Their word assignment used a task/language-specific spatial window, so these codes depend on valid text mapping rather than raw nearest-word landing. They are **observed displacement codes**, not five levels of difficulty.

| Code | Schematic sequence | What it says |
| --- | --- | --- |
| `Fwd+` | `w_i -> w_(i+2 or farther)` | Next assigned fixation lands more than one word forward. It does not prove intervening words were unread. |
| `Fwd` | `w_i -> w_(i+1)` | Next assigned fixation lands on the following word. |
| `Rfx` | `w_i -> w_i` | Two consecutive fixations are assigned to the same word. This is not a later revisit or a long line hold. |
| `Bwd` | `w_i -> w_(i-1)` | Next assigned fixation lands on the preceding word. |
| `Bwd-` | `w_i -> w_(i-2 or farther back)` | Next assigned fixation lands more than one word backward. |

### A2. Line-fixation recognition rules

[Wang et al. (2024), GazePrompt](https://arxiv.org/html/2402.12772) recognized three line behaviors from fixation sequences in a gaze-aware desktop reading aid. Thirteen low-vision adults evaluated the aid in a controlled reading-aloud study with a 120 Hz Tobii tracker. These are **system recognition rules**, not independent diagnoses of reading difficulty.

| Rule | Schematic sequence | What it says |
| --- | --- | --- |
| Line following | Assigned line stays `L_i` across fixations. | Gaze remains assigned to the current line; this does not establish progress within it. |
| Line switching | Line end -> next-line start with return-sweep geometry. | A move toward the following line; the system could show a line cue. |
| Line jumping | `L_i -> L_j` without detected sweep, stable for three fixations. | A different line is selected; it could be a skip, revisit, or misclassification during slow back-tracing. |

### A3. Transitions between reading segments

[Yamaya, Topić, and Aizawa (2017)](https://doi.org/10.2197/ipsjjip.25.100) grouped horizontally progressive fixations into segments and manually labeled 1,606 transitions from 33 English-document trials (11 readers, Tobii TX300). Their random-forest classifier supported vertical drift correction and fixation-to-word mapping. The labels refer to **segment relations**, not NewsMead line-ordinal steps.

| Label | Segment relation | Observed behavior / caution |
| --- | --- | --- |
| Return Sweep | End of one line -> beginning of the next. | Ordinary line change; a leftward screen move alone is insufficient. |
| Progressing | One segment -> farther-right segment on the same line. | Continued reading after the segmentation procedure divided one line. |
| Short Re-reading | Backward to earlier text on the same line. | Same-line return or saccade correction; not proof the sentence was misunderstood. |
| Long Re-reading | Upward/back to a previous line, possibly continuing. | Return to earlier multiline material; reason is not known from geometry alone. |
| Skipping | Jump to another target before the current line is fully traversed. | Task-specific bypass label; does not prove intervening text was unread. |
| Resuming | After long rereading/skipping -> latest prior reading point. | Return after a detour; needs path history. |

In Yamaya's setting, Short Re-reading and Skipping had F1 scores of 0.47 and 0.31, respectively. The complete six-class classifier had F1 0.78; the subsequent mapping pipeline reported 87% fixation-to-word accuracy. Its classifier also used two preceding **and two following** transitions as features, so those results do not describe a causal live detector. None of the figures establishes NewsMead phone accuracy or aid benefit.

### A4. Multiline movement distinctions used as measurement cautions

| Study | Term | Meaning in the study |
| --- | --- | --- |
| [Parker, Slattery, and Kirkby (2019)](https://doi.org/10.1016/j.visres.2018.12.007) | Return sweep | Saccade from near one line's end to near the next line's start during ordinary multiline reading. |
| Parker et al. | Undersweep + corrective saccade | The sweep lands short, then a leftward correction approaches the next line's start; that leftward movement need not be rereading. |
| [Carr et al. (2022)](https://doi.org/10.3758/s13428-021-01554-0) | Within-line regression | Return to an earlier point on the same line, examined in natural examples and line-assignment simulations. |
| Carr et al. | Between-line regression | Return to a previous line, a challenging case for vertical-drift correction. |

## B. Ordered path and episode descriptions

A **path motif** specifies how several movements relate in time. It can be described without discovering a statistical cluster. The first row has a published oculomotor pattern; the second composes published segment relations rather than naming a separately validated episode. The NewsMead rows are operational illustrations to measure and test.

| Motif and resolution | Schematic order | Source status and permissible reading |
| --- | --- | --- |
| Return sweep with correction, line/multiline | `L4(end) -> L5(mid) -> L5(start)` | Published oculomotor pattern in Parker et al.; leftward correction on L5 can be part of normal progression. |
| Multiline return and resume, segments | Current segment -> earlier-line segment -> former frontier | Illustration composed from Yamaya's Long Re-reading and Resuming relations; this exact episode is not a separately validated class and requires path history. |
| Word return, word | `w12 -> w13 -> w12` | NewsMead composition of Olivier's `Fwd` then `Bwd`; no cognitive meaning follows from this small motif alone. |
| Repeated line revisit, physical line | `L4 -> L5 -> L4 -> L5` | NewsMead illustration only. No cited study names this exact cycle as a difficulty or aid-need class. |
| Long line residence, physical line | Time accumulated while gaze is assigned to `L4` | **Not an order class.** Compare only with known eligible time, line/text amount, and assignment quality. |
| Gap-spanning jump, any resolution | `w12 -> [gap] -> w16` | **Not a reader sequence.** Break continuity rather than claim skipping across missing gaze. |

## C. Empirical scanpath clusters

### C1. Data-derived clusters of sentence paths

| Study / spatial unit | Cluster | Prototypical path | Authors' interpretation and limit |
| --- | --- | --- | --- |
| [von der Malsburg & Vasishth (2011)](https://doi.org/10.1016/j.jml.2011.02.004), Spanish sentence regions | A | Return to sentence beginning, then reread all or part. | Only A was significantly more frequent in garden-path than comparison sentences; it also occurred in both conditions. |
| Same | B | One saccade to early sentence material. | Brief return; its purpose cannot be determined from the path alone. |
| Same | C | Shift back to nearby disambiguating region. | Possible rechecking, with oculomotor and other processing explanations. |
| Same, finer exploratory solution | D-Q | D resembles A; E rereads only the first line; F-M refine B; N-P refine C; Q blends B/C. | Finer subclusters depend on analytic dimensionality. They are not 14 universal reading states. |
| [Parshina et al. (2022)](https://doi.org/10.1002/rrq.414), Russian whole-sentence word paths | Fluent | Mostly forward, fast, few regressions, more unfixated words. | Exploratory descriptive pattern across reader groups; not a momentary proficiency score. |
| Same | Intermediate | Slower, longer fixations, short regressions, little skipping. | Authors suggest local processing challenges; one return is not diagnostic. |
| Same | Beginner | Slowest, long fixations, frequent word/sentence rereading, little skipping. | Authors suggest broader language-integration challenges in some groups; not a NewsMead aid class. |

## D. Model-inferred phases of word-code runs

[Olivier et al. (2022)](https://doi.org/10.16910/jemr.15.4.5) fitted a hidden semi-Markov model to runs of the five word-transition codes and grouped five model states into four interpreted phases. These are **modeled phases, not empirical whole-path clusters**. Their phase duration counts fixation steps, not one long fixation.

| Phase | Characteristic run in that task | Why it cannot label one NewsMead event |
| --- | --- | --- |
| Normal reading (`NR`) | Mix of ordinary forward steps and refixations. | A single `Fwd` or `Rfx` does not identify the phase. |
| Information search (`IS`) | More direction changes and refixations. | The label belongs to a topic-search task and modeled run. |
| Fast reading (`FR`) | Higher probability of `Fwd+`. | One forward leap does not establish speed or understanding. |
| Slow confirmation (`SC`) | More backward movements near task completion. | A backward move does not establish confirmation or difficulty. |

## NewsMead use boundary

The defensible first implementation is an **observable event dictionary** that preserves word and line order, timing, assignment quality, and breaks. Clusters and phases can be exploratory comparisons only after suitable local data; their published names must not be used as live phone diagnoses. A visual aid also requires separate evidence that the relevant event is detected accurately, that its placement and timing work, and that it improves an independent reading outcome. The [evidence ledger](scanpath-scaffolding-evidence.md) and [proposal](adaptive-visual-aid-proposal.md) state those gates.

## Primary reference key

- Olivier, B., Guérin-Dugué, A., & Durand, J.-B. (2022). *Hidden Semi-Markov Models to Segment Reading Phases from Eye Movements*. [Journal article](https://doi.org/10.16910/jemr.15.4.5).
- Yamaya, A., Topić, G., & Aizawa, A. (2017). *Vertical Error Correction Using Classification of Transitions between Sequential Reading Segments*. [Journal article](https://doi.org/10.2197/ipsjjip.25.100).
- Wang, R., et al. (2024). *GazePrompt: Enhancing Low Vision People's Reading Experience with Gaze-Aware Augmentations*. [Author manuscript](https://arxiv.org/html/2402.12772).
- Parker, A. J., Slattery, T. J., & Kirkby, J. A. (2019). *Return-sweep saccades during reading in adults and children*. [Author manuscript](https://eprints.bournemouth.ac.uk/31639/1/VR_Rev2_TJS_JAK.pdf).
- Carr, J. W., et al. (2022). *Algorithms for the automated correction of vertical drift in eye-tracking data*. [Journal article](https://doi.org/10.3758/s13428-021-01554-0).
- von der Malsburg, T., & Vasishth, S. (2011). *What is the scanpath signature of syntactic reanalysis?* [Author manuscript](https://tmalsburg.github.io/MalsburgVasishth2011.pdf).
- Parshina, O., Sekerina, I. A., Lopukhina, A., & von der Malsburg, T. (2022). *Monolingual and Bilingual Reading Processes in Russian: An Exploratory Scanpath Analysis*. [Journal article](https://doi.org/10.1002/rrq.414).

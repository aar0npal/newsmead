# Scanpath pilot audit — 2026-09-30

Audited [pilot revision 1](scanpath-measurement-pilot.md), the raw logger/schema, structured protocol v6 and offline replay. Current branch: `measurement/scanpath-pilot`. Existing Android/other working-tree edits were preserved. This audit changes Python replay and documentation; no device/private recording was inspected or collected.

## Findings and actions

| Finding | Consequence | Action/status |
| --- | --- | --- |
| **High: capture order reset at a boundary.** Synthetic times `0,40,80,120,80,40,80,120,160` produced fixations `0–120` then `40–160`. | A path could move backward in time, overlap fixation spans and corrupt duration/order interpretation. | **Fixed in analysis v4.** A persistent latest capture timestamp survives boundaries; older/duplicate samples are rejected until time advances. Invalid finite capture observations also advance that timestamp. |
| **Medium: usable sample count incremented before time rejection.** | A timestamp-rejected observation still counted as usable coverage. | **Fixed.** Separate coordinate/geometry eligibility, accepted monotonic samples and nonmonotonic rejections. `valid_sample_records` now means accepted samples; version retained to identify this semantic correction. |
| **Medium: break summaries only counted links between detected fixations.** | Terminal invalid input or a boundary with no next fixation was absent from summary break counts. | **Fixed.** All segment-boundary events are counted separately from broken adjacent-fixation links. Counts describe events/links, not behavioral episodes. |
| **Medium: no duration coverage, visit censoring or output-age summary despite protocol requests.** | Few retained fixations could give impressive assignment fractions while most of the trace was unclassified; incomplete visits and time lag were hard to assess. | **Fixed.** Observed spans/capture-span denominator, word/line visit censoring, and coordinate delivery-minus-capture age with missing/negative counts. Age is not full display/aid latency. |
| **Medium: fresh calibration was requested after the target check.** | Reading could use a different calibration from the checked one. | **Corrected procedure.** Calibrate before the linked pre-check/read/post-check; recalibration starts a new set. Added same-calibration post-check for drift. |
| **Medium: protocol suggested running every desired font.** | Current validation/article font is locked to 22 dp, so the instruction was not executable as written. | **Corrected.** Freeze current 22 dp condition; alternative fonts require a new explicit instrument/condition. |
| **Major collection dependency: smoothed v6 targets vs raw reading paths.** | V6 placement scores cannot validate raw AOIs used in natural replay. Logged raw coordinates alone are not a raw target score. | **Pending.** A separate matched raw target instrument is needed. Frozen v6 remains unchanged. |
| **Major collection dependency: no continuous-order reference task.** | Separate target holds/automatic scrolling do not measure uninterrupted target-order recovery or fixation detection. | **Made explicit.** Separate no-scroll, timestamped word/line sequence tasks are required. Intended sequence is a task label, conditional on compliance; physiological fixation truth remains unavailable. |
| **Study plan incomplete: readers, sessions, passages, stopping and precision not set.** | No defined repeatability/generalization claim; camera samples could be mistaken for independent participants. | **Added planning requirements and blank session record.** Counts/inclusion rules must be fixed before recruitment; no invented universal sample size or population inference from the owner alone. |
| **Line fallback wording omitted shared detector dependence.** | Good line assignment among sparse events might be mistaken for reliable line-path extraction. | **Corrected.** Both paths use I-DT. Word ambiguity may leave line assignments usable, but detector validity/coverage still apply. A coarse sample method would need separate definition/testing. |
| **Return-sweep/latency language exceeded implemented output.** | A line increment could be labeled a return sweep; delivery age could be labeled eye-to-aid latency. | **Corrected.** Line edges are order only; end/start geometry and an operational rule are additional dependencies. Delivery age is explicitly limited to logged coordinate output. |
| **Session provenance was incomplete in automatic logs.** | Build/calibration age/reader linkage or exact source text could be unavailable later. | **Added external manifest requirements.** Record APK/build provenance, calibration time, linked filenames, reader/session codes, text and source/input hashes privately. Git HEAD alone is insufficient for a dirty build. |
| **Same-stream video/annotation could be mistaken for eye-position truth.** | Agreement with a drawing/coder of the estimate would be circular spatial validation. | **Clarified.** Such annotation can audit processing or prompt timing only. Natural eye-position truth needs an independent, validated reference. |
| **Three later intervention arms were presented as a universal requirement.** | An unsupported study design could be treated as frozen before the aid question is specified. | **Refined.** Use a no-aid baseline; include a fixed-aid comparator when testing the value of adaptation. Freeze arms/sample size under the later question. |

## Evidence check

[Valliappan et al. (2020)](https://www.nature.com/articles/s41467-020-18360-5) supports phone point-target performance and task-level reading associations, with 30 Hz timing limits. Its reading study does not independently validate word/line order. [Lepton](https://storage.googleapis.com/gweb-research2023-media/pubtools/4596.pdf) provides relevant 20 fps phone-news evidence for instructed coarse line-count/reread/skip features, not exact natural eye-position truth. [Trabulsi et al. (2021)](https://www.frontiersin.org/journals/neuroscience/articles/10.3389/fnins.2021.578439/full) shows mobile AOI metrics depend on filter settings; its external-tracker human coding benchmark is processing agreement, not independent spatial accuracy. Population/hardware and transfer limits are in the revised pilot table.

No source contradicted the pilot's phone-only natural-path limitation. The source check does not validate NewsMead's thresholds, detector, intended sequence task or aid mappings. The review uses the project's scientific-critical-thinking/statistical-analysis guidance; this is not a completed human validation study.

## Checks and remaining readiness

- Focused replay suite: **31 tests passed**, including new timestamp-reversal, invalid-boundary timing, accepted-count, terminal-boundary, censoring and output-age cases.
- Independent focused review of v4 found no remaining actionable issue in those fixes. The earlier 24 tests passed but did not exercise the discovered reversal case.
- No Android code/build/install, calibration, recording, transfer, staging or commit was performed.

**Ready:** deterministic offline diagnostic extraction with explicit limitations. **Required before a raw placement/order collection claim:** matched instrument, completed reader/session/material plan, frozen scoring/denominators, and task-specific collection authorization. **Unavailable with this phone-only setup:** independent natural physiological fixation/word-order accuracy. Path-outcome and aid-benefit claims remain later evaluations.

## Scope clarification after owner review

The owner clarified that this pilot is for building the scanpath system for adaptive visual aids and should carry forward completed gaze-model evaluation. Revision 2 overemphasized measurement preparation and did not provide a full classifier/policy build inventory. Revision 3 corrects the scope: existing gaze results are the input baseline; extraction, named pattern detectors, causal live engine, candidate aid policy and controlled aid inspection are explicit work packages. New checks address their own behaviors rather than restart generic estimator evaluation.

No complete named pattern classifier or scanpath-to-aid policy is currently implemented. Exact word/line edges are available; the existing aid renderers and legacy weighted controller are separate. The owner is seeking a stronger model from its provider; receipt/compatibility/performance remain unverified and do not block classifier development. The timestamp/coverage fixes and phone-only evidence limits in this audit still apply.

## Revision 4 after the request to revise the full pilot

The current plan now specifies objectives, build deliverables/acceptance checks,
P01–P13 operational pattern definitions and A0–A4 candidate aids with explicit
strength parameters. Evaluation proceeds through causal replay, prompted pattern
feasibility, ordinary-reading shadow decisions/support-need reports, controlled
aid/strength trials and frozen varying-strength evaluation. Phone-only reference
limits, same-calibration checks and the v4 replay corrections remain applicable.
The expanded blank record carries per-pattern and per-aid denominators, conditions,
outcomes and failures. This documentation revision adds no classifier, device
collection or human validation result.

Focused independent review of revision 4 found two protocol gaps, corrected in
the plan and worksheet: support-need prediction now requires prespecified input
windows before reports plus report-interaction/recovery exclusions; cue timing
now includes first-support and decisive-capture-to-render intervals, not only
post-detection component delays. Render acknowledgment is labeled as a proxy
unless visibility is verified. The review otherwise found the scope and reference
limits coherent; it is a document review, not completed participant validation.
Targeted closure review confirmed both corrections. Seven-document checks found
75 valid local links, complete registry/phase headings and consistent pilot/record
tables; Git diff whitespace checks passed. No new software build or device study
was performed for revision 4.

## Offline detector implementation checkpoint

Analyzer v5 now integrates P01–P13 descriptive outputs from classifier v1; see
the [detector contract](scanpath-detectors.md). Positive sweep detection requires
explicit endpoint settings and known LTR direction; residence/progression
thresholds remain development settings. A–B–A–B is the implemented cycle grammar.
Matched counts separate sweep matches from abstained/not-matched evaluations.
Geometry snapshots and closing-evidence timing retain their provenance.

Independent code review led to fixes for backward movement mislabeled as frontier
approach, incomplete/unordered episode evidence, missing direction assumptions,
and short-looking censored residence updates. Regression checks now pass:
39 extraction/integration plus 13 classifier tests (52 total). This is software
verification; no new natural eye-position, difficulty or intervention validation
was performed. Android causal processing, matched tasks and aid policy remain.

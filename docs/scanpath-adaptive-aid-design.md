# Scanpath-based reading aids: design and evidence contract

**2026-10-13 current marker amendment:** article/pilot/replay use definition V3 /
policy 4 and the [last recorded-line bookmark](scanpath-reading-bookmark.md).
EB15/CW12 support cues at known reading-interruption locations; the autonomous
outside/jump trigger and fixed phone gutter bar remain experimental. The V2
expected-successor route remains a historical comparison, not the current rule.
Current word help is the near-word definition card; focus is default on at the
owner's request, with its original trigger/strength validation limits retained.

**2026-10-03 ordinary-article amendment:** the owner's top-magnifier correction
adds a separate [definition aid and article demo](article-definition-aid.md).
H03/M25 support gaze-triggered lexical-assistance precedents, with explicit
population/hardware/outcome limits. The flat 800 ms rule and English phone benefit
are experimental. Consultation is a separate commanded interval, excluded from
article-path interpretation. The previous magnifier form below remains in the
legacy replay/pilot conditions; existing IDs and frozen protocols retain meaning.

**Decision checkpoint — 2026-09-30.** Companion to the
[implementation proposal](adaptive-visual-aid-proposal.md) and
[pilot](scanpath-measurement-pilot.md). The owner requires defensible
pattern-to-aid connections and permits replacing the previous scaffold design.
Build the integrated prototype before participant measurement/evaluation.
The [runtime checkpoint](scanpath-aid-runtime.md) now implements the core line,
word and focus modules. This document remains the evidence contract; optional
ruler/coloring variants and participant outcome evidence are still pending.

## Design decision

Use independent reading aids selected from observable scanpath events. Aid type
addresses a particular task; strength is a parameter within that type. There is
no required ordering of WORD, LINE, FOCUS and REENTRY, no requirement for four
levels, and no requirement that every detected pattern activate an aid.

The engineering rationale is that the reviewed interventions address different
tasks, with different findings and reader preferences. The sources below support
specific forms or trigger precedents; they do not test the legacy ordinal ladder.
This is a reason to use independent modules, not a claim that the new software
architecture has itself been experimentally validated.

### Aid families for the prototype

| Family | Display | Adjustable parameters | Implementation priority |
| --- | --- | --- | --- |
| Line guidance | Ongoing current-line emphasis and a separate bookmark at the last supported line after outside departure or a supported non-normal jump. | Highlight prominence; fixed bookmark visibility, saved-line anchor and lifecycle hold. | W24/S19 provide line-form precedents; EB15/CW12 provide reading-resumption evidence. Exact bookmark rule/form remain experimental. See [current marker contract](scanpath-reading-bookmark.md). |
| Local word support | Current near-word definition card with persistent source highlight; magnifier retained only in legacy comparison conditions. | Placement, lookup and consultation lifecycle; legacy magnification. | H03/M25 lexical-assistance precedents are appraised in [article definitions](article-definition-aid.md); W24 remains a magnifier method precedent. Exact phone benefit remains uncertain. |
| Placekeeping ruler | Line bar or underline that follows a reliable anchor; compare with an optional context-preserving focus variant. | Style, prominence and span; reader-selected preferences. | Candidate module using N23. Prioritize the bar as the stronger no-ruler comparison precedent; do not assume Lightbox/masking is stronger help. |
| Word-progress coloring | Optional persistent coloring of visited words. | Color and activation condition. | Optional alternative from R26; different from a difficulty-triggered magnifier. |

Existing A0–A4 identifiers remain lookup IDs for study records, not ranks. A1/A2
are line guidance, A3 is a definition in the current profile and magnification
in the legacy comparison, and A4 retains the defined
context-preserving focus region. A0 records no new aid or suspension. Ruler
variants use distinct `RULER_GREY_BAR_V1` / `RULER_UNDERLINE_V1` form IDs;
progress coloring uses `WORD_PROGRESS_COLOR_V1`. Preserve the exact condition
and form in records; do not pool these forms under A4 or an ordinal severity
position.

## Exact pattern-to-aid relationships

Pattern definitions remain those in the [detector dictionary](scanpath-detectors.md).
Every active rule must retain its source, supporting event IDs, settings and
experimental status.

| Implemented observation | Role or candidate action | Evidence and boundary |
| --- | --- | --- |
| P01/P02 word forward movement; P05/P06 line forward movement | Update the target of an enabled line guide; optionally update word-progress coloring. | W24/R26 provide gaze-following forms. Forward jumps do not establish unread text or difficulty. No strength escalation from jump distance alone. |
| Supported visible source line, followed by sustained usable outside-article coordinates or uninterrupted supported non-normal line jump | Offer the fixed bookmark at the line left behind. Same-line/normal adjacent progression updates memory without a cue. | EB15/CW12 support known-interruption location cues, not the autonomous departure/jump rule or blue gutter form. Intentional rereading/skipping is not labeled harmful. |
| P09 geometric return-sweep candidate | Retain the observed movement in detector records; it no longer chooses a marker. | A P09 match occurs after the adjacent landing. It cannot establish prediction of an initial landing or detect a skipped/backward destination by itself. |
| Supported current-word residence, with no required refixation count in the current definition profile | Near-word definition at 800 ms observed current-word time. Historical magnifier uses refixations plus residence. | H03/M25 provide lexical-assistance precedents, not validation of the exact flat threshold or phone benefit. W24's refixation criteria remain relevant to the historical magnifier comparison. |
| P03/P07/P08 backward returns + P10 revisit/resumption | Follow the returned location; preserve access to earlier text. A ruler/anchor response can be explicitly experimental or reader-requested. | S14 supports preserving rereading. N23 supports a ruler form, not a return-triggered benefit. P10 is not proof of a known interruption or lost place. |
| P11 A–B–A–B cycles + P12 line residence/progression | Retain observations for an experimental ruler/region or strength rule; no forced aid solely because a cycle occurred. | N23 supplies an aid-form comparison. No matched trial in this targeted review validates these exact events as need for a ruler/focus window. Keep the missing trigger evidence explicit. |
| P13 unknown/break | Suppress new transient gaze aids and break jump comparison. The bookmark may retain saved memory/static cue in unchanged geometry, but tracking loss cannot activate it. | Measurement safeguard, not an intervention finding or evidence of greater difficulty. Geometry/lifecycle/consultation invalidation clears the bookmark. |

## Primary evidence and source locations

This is a targeted appraisal, not a systematic review. Null findings and transfer
limits constrain the proposal; a plausible mechanism does not fill a missing
intervention result.

| Key | Study, condition and measurement | Finding and permitted use | Verifiable location |
| --- | --- | --- | --- |
| W24 | Wang et al. (2024), GazePrompt: 13 low-vision adults reading aloud; 120 Hz Tobii Pro Fusion, desktop. | Line-switch time improved; switch accuracy did not significantly improve. Word support used duration/refixation triggers, but tested word outcomes did not significantly improve; magnifier and speech choices were not isolated aid effects. Use as a method precedent. | [Author full text](https://arxiv.org/html/2402.12772): §§3.1–3.3, 4.3.2–4.3.3; Figures 1–2. |
| S19 | Slattery & Vasilev (2019): 32 adults, silent desktop stories; EyeLink 1000, 1,000 Hz. First word of every line stayed bold. | Landings shifted toward the margin and corrections decreased; global reading time did not improve. Supports line-start salience, not gaze-conditioned timing/strength or a phone difficulty diagnosis. | [Published paper](https://eprints.bournemouth.ac.uk/32336/25/Slattery-Vasilev2019_Article_AnEye-movementExplorationIntoR.pdf): pp. 1198–1202; Figure 1 and Results. |
| N23 | Niklaus et al. (2023), Digital Reading Rulers: 177 adults, 91 in the self-report/screening dyslexia group; mouse-controlled desktop rulers, no gaze detector. | Grey Bar significantly increased speed versus no ruler in that group. Underline/Shade mean gains versus no ruler were descriptive; their significant pairwise comparisons were against Mouse/Lightbox. No significant comprehension gain or speed gain in the other group. Supports a form to test, not a slow-path trigger. | [Primary paper](https://thereadabilityconsortium.org/wp-content/uploads/2023/08/Digital-Reading-Rulers-Evaluating-Inclusively-Designed-Rulers-for-Readers-With-Dyslexia-and-Without.pdf): §§4.1–4.4, Table 4 (PDF p. 11), §5.5. |
| R26 | Rummens & Beier (2025 online/2026 issue): 70 Danish second graders, ages 8–9; laptop, Tobii 5L at 120 Hz. Words remained blue after gaze-progress highlighting. | Reading-time/revisit measures improved in retained gaze data; pronunciation/comprehension did not significantly improve. Continuous coloring was not triggered by slow reading or refixation. Population, preference and missing-data limits remain. | [Publisher record](https://doi.org/10.1080/00140139.2025.2514600), [author-uploaded full text](https://www.researchgate.net/publication/393028765_Gaze-based_word_highlighting_boosts_reading_performance_an_eye_tracking_study_in_second_graders): Methods pp. 3–5, Results pp. 6–8. Publisher full-text retrieval was blocked; author manuscript was checked. |
| S14 | Schotter, Tran & Rayner (2014): 40 undergraduates, single-line sentences; EyeLink 1000 at 1,000 Hz. Previously passed words were masked versus normal reading. | Restricting rereading reduced comprehension. Supports retaining readable context; does not identify which return needs a new aid. | [Publisher issue containing the original article](https://journals.sagepub.com/cms/asset/8fb4a866-be79-4397-b847-74a1d85670f1/psys_25_6_full-issue_pdf.pdf#page=66): article pp. 1218–1226; Results p. 1221. |

None of these studies establishes the full current-phone event-to-aid-to-strength
chain for adults 45+ silently reading news. Report a source's studied condition
and exact supported claim whenever presenting a mapping.

## Policy and strength contract

**Source recheck — 2026-10-03:** the
[implementation evidence review](scanpath-trigger-evidence-review.md) records
exact live predicates and a trigger-focused flowchart. GazePrompt's original
word detection uses OR across first-fixation, refixation-count and within-pass
duration criteria; NewsMead's two-refixation AND 750 ms rule is a changed
experimental condition. None of the reviewed papers validates the current
cycle-to-focus rule or normalized adaptive dose. Preserve these limits in
panel/manuscript claims and evaluate the conditions independently.

An action describes `aid_type`, exact `aid_form_id` and study `condition_id`,
a word/line/region anchor and layout revision,
named parameters with units, onset/expiry, supporting pattern events, rule/config
version, source IDs, evidence relationship, mode and reason. Chosen and rendered
actions are separate records. Missing/uncertain anchors produce a logged
suppression rather than an assumed difficulty response.

Changing strength with support need remains a project requirement. Implement
configurable experimental rules and adjustable parameters now. The reviewed
papers do not establish universal difficulty-to-strength cutoffs, transferable
probability weights or that more prominence/magnification always helps. Select
and evaluate those rules after the complete prototype exists, using independent
reports/outcomes and later held-out readers/passages.

No shared ordinal `level` determines aid type. No type's enum position decides
escalation. Reader controls choose/disable forms and limit parameters. If two
actions conflict, apply an explicit configured arbitration rule and log it;
combinations require their own configuration and evaluation, not assumed
additive benefit. Candidate mappings can be demonstrated using replay/synthetic
inputs before participant measurement. Keep ordinary-reading release decisions
separate from prototype implementation status.

## Implementation migration and completion

Add an aid-action interface beside the causal scanpath engine. Adapt useful
drawing primitives in `GazeOverlayView` to named aid parameters; the old
`ScaffoldLevel`/weighted `AdaptiveScaffoldController` and `DEMO_CYCLE` are legacy
behavior. Frozen validation/recording conditions keep their documented identity.
The new scanpath route must consume its own events and must not silently select
aid types through the legacy index or ordinal ladder.

Complete live extraction/classification, independent aid modules, variable-strength
policy, controls, lifecycle and action/render logging before participant tasks.
Software replay/build checks accompany implementation. The 2026-10-01
[runtime checkpoint](scanpath-aid-runtime.md) implements the live engine,
independent policy, adjustable forms, reader controls and action/render journal.
The matched evaluation instrument and device/participant checks remain pending.

## Appraisal method

Applied `scientific-critical-thinking` to examine studied populations/hardware,
exact triggers, intervention comparators, null findings and transfer limits.
Procedural reference: Kassis, T., Agarwal, V., He, Y., Patel, D., & Brueckner,
A. M. (2026), [Scientific Agent Skills: A Library of Procedural Knowledge for
Research Agents](https://doi.org/10.48550/arXiv.2609.00065), current record v2
checked 2026-09-30. This is workflow provenance, not evidence that an aid works.

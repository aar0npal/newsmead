# Proposed responsive line support

**Superseded on 2026-10-13:** the owner clarified that the highlight must have
its own rule. The [independent line-highlight proposal](scanpath-line-highlight-rule.md)
replaces the coordination recommendation below. This draft records an earlier
proposal, not an accepted or implemented controller.

2026-10-13 · design response to the owner's concern about a fixed 25% highlight.
**Proposal only:** current installed A1 still uses its fixed adaptive baseline.
No controller, detector, APK or device behavior changed in this design review.

## Problem and recommendation

The current guide responds to reading position, but its prominence does not
respond to the line-support evidence. It provides ongoing guidance without
fulfilling the proposed strength response for this particular aid.

Recommend coordinating the current-line highlight with the existing line-support
response, while retaining the bookmark and definition as separate task-specific
aids. A single line-support decision should determine highlight prominence and
whether a context focus window accompanies it. Separate arbitrary timers for
each form would make their relationship harder to explain.

| Observed situation | Proposed response | Evidence status |
| --- | --- | --- |
| Supported forward reading, e.g. lines 3 → 4 → 5 | Subtle current-line guide; switch its anchor only with supported line evidence | GazePrompt supplies a gaze-following/stabilized line-guidance precedent; exact NewsMead baseline remains a prototype choice |
| Supported repeated visits 4 → 5 → 4 → 5, plus limited new frontier progress | Stronger emphasis on the currently supported line, within the reader's cap; coordinate it with the existing optional context focus response | Observable cycle/progression descriptors exist; their mapping to stronger highlighting/focus has no direct intervention validation in the reviewed papers |
| Usable departure from the article and later return | Retain the source bookmark; offer temporary contextual emphasis around the saved reading location during resumption | EyeBookmark supports context cues after known interruptions; automatic phone departure detection, source-line-only emphasis and clearing criterion are adaptations |
| Supported forward reading resumes | Return to the subtle guide after confirmed progress; use an explicit release rule so one row wobble cannot toggle assistance | Proposed engineering lifecycle; exact progress count/release setting needs selection and independent evaluation |
| Tracking loss or uncertain target | No new strength escalation or guessed highlight location; preserve the bookmark according to its existing contract | Measurement safeguard; uncertainty is not evidence of greater reading difficulty |

No numerical strength ramp or new millisecond cutoff is justified by this review.
The existing focus timing/ramp is also exploratory. A shared response would not
turn those values into published difficulty probabilities or validated doses.
Actual line geometry handles wrapping; time spent on a physical line is not a
layout-independent quantity of text. A single long line visit alone is inadequate
grounds for a stronger navigation cue.

## Primary findings that constrain the design

- **GazePrompt**, Wang et al. (CHI 2024),
  [primary full text](https://arxiv.org/html/2402.12772), sections 3.1.1,
  3.2.3 and 4.3.2: current line cues follow stabilized reading behavior;
  return-sweep detection and persistent changed-line evidence govern switching.
  In the controlled study with 13 low-vision adults and a desktop eye tracker,
  line support reduced line-switch time without significant switch-accuracy
  improvement. Highlight/arrow and color preferences varied. It did not test
  brightness escalation from line residence or cycles; desktop fixation-vote
  settings cannot be copied as phone accuracy guarantees.
- **EyeBookmark**, Jo, Kim & Seo (CHI 2015),
  [primary PDF](https://idclab.skku.edu/assets/EyeBookmark.pdf), Figure 1 and
  Table 1/results: sentence/block context cues at the observed interruption
  location shortened resumption lag. Main study: 13 young Korean university
  adults, Tobii X60, desktop/chin rest and imposed interruptions. No significant
  comprehension gain or direct comparison of weak/strong highlight intensity.
  Supports a resumption task, not cycles as lost attention or a phone dose curve.

## Requirements before implementing this proposal

Preserve baseline versus extra-support identity, event/provenance links, geometry,
reader controls and manual mode. Specify activation, release, caps and priority
with definitions/bookmarks; version the changed profile and form. Log when aids
are already visible: changes in gaze after displaying an aid cannot be treated
as independent proof of worsening difficulty. Update synthetic replay and focused
tests, then review and build before any separately authorized device check.

Compare the proposed response with the current fixed guide using independent
outcomes and reader reports. The proposal is a study-informed intervention
hypothesis; neither its benefit nor a cycle-to-strength formula is established.

# Last recorded reading-line bookmark

## Current correction — 2026-10-13

Looking away can now show a bookmark even when the provider loses the face/eyes:
**500 ms of sustained reported unusable gaze**, timed from the first accepted
unavailable callback's delivery. The next 100 ms runtime tick or input confirms
it. This is a prototype delay, not a study-derived attention threshold. A usable
gaze recovery before the delay cancels the pending cue. Hardware/localization
problems can also cause it; the recorded reason is `sustained_gaze_unavailable`.

| Reading sequence | Current result |
| --- | --- |
| Supported line 4 → outside-article points for 100 ms / 3 samples | Fixed marker at line 4 |
| Supported line 4 → reported unusable gaze for 500 ms | Fixed marker at line 4, even with no coordinates |
| Supported line 4 → brief loss → usable gaze before 500 ms | No new marker |
| No recorded reading line → loss | No marker; no location is invented |
| Marker → supported return or continued reading elsewhere | Marker clears |
| Scroll/reflow, pause, dictionary, controls or disable | Memory and marker clear |

Missing reasons admitted are `no_face`, `invalid_crops`, `eye_area`, `stale`,
`invalid_prediction`, and `coordinate_unavailable`. Invalid clocks, stopped or
excluded states, old/future deliveries cannot start it. Missingness never creates
a scanpath transition or implies cognitive difficulty. Word accuracy is not
required for the saved line.

Gap evidence uses `departure_time_basis=delivery_elapsed_time`, separate delivery
bounds and **null departure capture bounds**. Action `last_capture_ms` stays at
the saved source's real capture. A timer tick cannot fabricate an eye sample.
Policy **7**, rule `A2_LAST_READ_LINE_BOOKMARK_V2`, smoothed condition suffix
**V2** distinguish this correction; visual form/strength stay fixed. Replay adds
**Bookmark when tracking disappears** (`bookmark_tracking_loss`), with synthetic
no-face callbacks and absent coordinates. Outside-coordinate replay remains.
**Verification:** 173 focused JVM tests pass; app and test APKs build; whitespace
passes. Independent source review has no actionable P1/P2 finding. Native
runtime-timer and replay/trace fixtures compile but were not executed on a phone.
Evidence/XML/APK hashes: `output/bookmark-tracking-loss-check-01/summary.json`.
For missing-gaze evidence, `departure_sample_count` counts distinct reported
unavailable callbacks, not usable coordinate samples or physiological fixations.
No device install, camera, participant collection, staging or commit ran.

The original implementation/results below are historical. Its silent tracking
loss rule is superseded by the sustained-loss trigger. The interruption studies
support location reminders but do not validate this timer or identify loss causes.

## Original implementation checkpoint

2026-10-13 · owner requested replacing expected-next-line prediction and selected
both outside-article departure and unexpected line-jump triggers.

## Meaning and behavior

The blue gutter cue identifies the last supported reading line. "Supported"
means the existing detector assigned a stable, fresh candidate/fixation to that
visible line; it does not prove comprehension or identify internal distraction.

| Observed sequence (one-based lines) | Current bookmark |
| --- | --- |
| Supported line 4 → supported line 5 | None; remembered location advances normally |
| Supported line 4 → sustained usable gaze coordinates outside the article | Fixed cue at line 4 |
| Supported line 4 → supported line 1 or line 8 | Fixed cue at line 4, with separate source/jump evidence |
| Cue at line 4 → supported return to line 4 | Cue clears |
| Cue at line 4 → distinct supported continuation elsewhere | Cue clears; deliberate rereading/skipping remains possible |
| Line 4 → tracking loss → recovered line 8 | No inferred jump across missing input; comparison reseeds |

Tracking loss alone retains location silently. Without usable outside-article
coordinates it cannot confirm looking away. In-article whitespace, a blink,
mind-wandering while looking at text, or poor tracking is not an attention label.

## Operational rules

- Current definition profile uses `READING_BOOKMARK`, form
  `LAST_READ_LINE_BOOKMARK_V1`, rule `A2_LAST_READ_LINE_BOOKMARK_V1`, source IDs
  `EB15`/`CW12`. Legacy `NEXT_LINE_START` keeps its historical meaning.
- Source/landing support matches latest target, generation and rendered geometry;
  the saved line remains fully visible and LTR. Raw wobble, older departed
  finalizations and same-prefix relabels do not establish jumps.
- Outside departure requires explicit `outside_visible_text` exclusion and finite
  coordinates beyond the visible article rectangle. Existing detector settings
  confirm **100 ms / three samples**, maximum sample gap **125 ms**, ordered
  capture/delivery and fresh inputs. These are engineering confirmation settings,
  not a measured time to lose attention.
- Jump/continued-reading comparison needs uninterrupted same-segment support
  within the detector's **125 ms transition gap**. Missing input breaks comparison
  while preserving the saved location/static cue. The first recovered elsewhere
  prefix only reseeds; supported source return or subsequent distinct supported
  continuation can clear the cue.
- Fixed **6 dp / alpha 240** cue, existing gutter and **4 dp** text gap. Positive
  strength settings do not attenuate it; off/zero disable it.
- Cue holds until supported resumption/continuation or explicit invalidation;
  finite `Double.MAX_VALUE` expiry represents that lifecycle in action records.
- Geometry/text/scroll, definition consultation, pause/restart, calibration/control
  boundaries and reader disable clear memory/display. Other gaze aids still clear
  normally on invalid input.

## Primary evidence and limits

| Source | Studied aid and finding | Transfer limit |
| --- | --- | --- |
| **EB15:** Jo, Kim & Seo (2015), *EyeBookmark: Assisting Recovery from Interruption during Reading*, DOI [10.1145/2702123.2702340](https://doi.org/10.1145/2702123.2702340). [Author PDF](https://idclab.skku.edu/assets/EyeBookmark.pdf), Figure 1 p. 2; Table 1/results pp. 3–4. | Nine-person pilot; main study 13 young Korean university adults, Tobii X60/24-inch desktop/chin rest, imposed target-word interruptions and 60-second videos. Last-fixation plus sentence/block context shortened resumption lag; comprehension did not significantly improve. | Supports observed interruption-location cues. No validation of our autonomous outside/jump trigger, blue gutter bar, confirmation timing, phone accuracy or older-reader benefit. The pilot's point cue was not reported as a significant improvement against no cue; context cues carried the stronger result. No comparison with predicted-next-line guidance. |
| **CW12:** Cane, Cauchard & Weger (2012), *The time-course of recovery from interruption during reading: Eye movement evidence for the role of interruption lag and spatial memory*, DOI [10.1080/17470218.2012.656666](https://doi.org/10.1080/17470218.2012.656666). [Primary PDF](https://interruptions.net/literature/Cane-QJExpPsychol12.pdf), Experiment 2 methods/results pp. 9–12. | Young university readers, EyeLink 1000/desktop display, imposed audio interruption. Yellow highlighting of the interruption target reduced time spent rereading pre-interruption text after return. | Explicit interruption and word-level cue; does not validate gaze departure/jumping as lost focus or our single-line marker. |

The bookmark principle is more directly connected to interruption-resumption
research than successor prediction. This is a design rationale, not a measured
head-to-head advantage for NewsMead. Exact trigger/form/lifecycle remain
experimental; intentional rereading and skipping remain legitimate.

## Profiles, replay, trace and checks

Current article/pilot/replay select `DEFINITION_PROFILE_V3`, policy **4**, and
`EXPERIMENTAL_DEFINITION_V3`, `PILOT_DEFINITION_*_V3` / `REPLAY_DEFINITION_V3`.
Frozen gaze, extraction and P01–P13 protocols are unchanged. Older V2 continuation
traces/tests keep their identities.

The six-stage demo previews the bookmark on the source line, with no detector
evidence. Raw-coordinate scripts `bookmark_return`, `bookmark_skip` and
`bookmark_lookaway` exercise this route; outside departure uses numeric coordinates
beyond the article rather than simulated camera loss.

`bookmark_evidence` in both action journals retains source support, departure
reason, separate capture/delivery bounds, sample count and optional jump landing.
Old continuation fields are not repurposed; choices and drawing receipts stay
distinct. Source review corrected gap-bridged comparison and both serializers;
current Android expectations now map the legacy marker type to the bookmark.

The first build stopped before tests at a Kotlin nullable-geometry access in the
new rule. After correction and owner-approved retry, **94 focused JVM checks**
and both APK assemblies pass. Updated APKs installed preserving data on the
SM-A566B/API 36. The camera-free phone invocation passed **30/32** checks; two
new assertions read `aid_form_id` instead of the replay schema's `form_id`.
After correcting that test-only field and explicit retry approval, **both checks
pass** in 28.861 s. All 32 distinct cases are covered across these invocations,
not a single clean full-suite run.

`output/reading-bookmark-check-01/summary.json` retains initial failures,
corrected logs, checks and installed APK hashes. These establish software
selection, rendering and trace behavior under scripted input. No camera/live
gaze, calibration, private recording or participant benefit was evaluated.

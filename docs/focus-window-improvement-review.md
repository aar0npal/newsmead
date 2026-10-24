# Focus window improvement review

2026-10-13 · Original discussion/evidence review. Owner subsequently authorized
implementation and selected3 episodes/30 seconds. See the
[current implementation contract](repeated-interruption-focus-window.md).
Proposal descriptions below retain the reasoning before implementation.

## Clarification after owner feedback

The owner specifies **repeated separate unavailability episodes**, not a single
sustained loss, as the proposed focus trigger. A single interruption belongs to
the independent bookmark/placekeeping response. One event is usable reading →
qualifying unavailability → supported reading recovery. Many missing callbacks
or one long absence count as one event. A minimum loss duration may filter brief
glitches but does not by itself activate focus.

Count qualified episodes in a recent rolling interval. Specify both the count
and interval; a whole-session count can combine unrelated interruptions. On
reaching that threshold, show the focus window around a supported reading area
and dim the surroundings. Further episodes could modulate prominence/duration;
continued supported reading should ease it off. Exact count, interval, intensity
and recovery settings remain to be specified/tested. No new runtime rule is
implemented in this discussion.

Apply the same evidence standard to all signals. Missing gaze, repeated line
alternation and scattered jumps each admit other explanations. Repeated episodes
strengthen the reason to test focus assistance; they do not conclusively diagnose
distraction, since tracker faults can recur. Keep tracked outside-article
departure and no-face evidence separate from stale/invalid tracking; do not
increase a supposed attention-confidence score by counting missing frames.

## Current gap and recommendation

The current A4 rule requires a recent A–B–A–B line cycle, at least 1000 ms
current-line residence and slow frontier progress. Its strength follows residence,
not switch or interruption count. The owner proposes including repeated gaze
departures/unavailability and inconsistent reading progression.

The window's intended function is to emphasize a manageable reading area and
help resume reading. It cannot address off-phone sights or sounds and does not
prove that the reader is distracted. Prefer independent activation routes over
requiring every signal simultaneously:

1. **Repeated interruptions:** activate focus after a specified count of distinct
   qualifying unavailability/recovery episodes in a recent interval. One prolonged
   absence cannot reach the count threshold by itself. Dimming around the supported
   reading area is the response; the bookmark remains independent. Benefit and
   exact frequency-to-strength/duration mapping remain to be evaluated.
2. **Repeated supported line alternation:** a separate optional trigger for local
   context support, without requiring subsequent slow residence. Additional
   recognized alternations could modulate prominence, subject to pilot tuning.
3. **Scattered jumps:** retain as descriptive data initially. A path such as
   4→8→3→7 may be deliberate scanning, rereading or tracking error. No verified
   primary study validates this exact phone sequence as distraction or need for
   a focus window. Do not add a default diagnosis/strength rule from it.
4. **Reader-requested support:** an always-on option is a reasonable comparator
   to automatic activation; existing Focus toggle merely permits automatic rules.

Keep rendering anchored to supported lines or an unambiguous sentence/short
paragraph. Preserve surrounding readable text, text layout and manual disable.
During missing gaze, retain location rather than moving a window to a fabricated
point. Test prominence and episode rules on the target phone; no published
weight or dose schedule was established in this bounded review.

## Primary evidence checked

| Study / primary location | Population and apparatus | Supported finding and limits |
| --- | --- | --- |
| Vatány et al. (2026), *Evaluating Interactive Highlighting Techniques in Digital Reading*. [Official article](https://www.visible-language.org/journal/issue-60-1-highlighting-techniques/), [full PDF](https://www.visible-language.org/Issue-60-1/VL-60-1-Vatany-et-al.pdf), methods journal pp.63–67; Table 5 pp.77–78 | 80 regular digital readers, ages18–75; standardized13/14-inch MacBookPro, mouse/trackpad hover, short informational texts. Severe reading difficulty excluded. | Sentence emphasis received easier-attention ratings from44/80; paragraph36/80; line34/80. No significant speed/comprehension difference detected. Ratings concern perceived attention, not independently measured distraction. Automatic gaze triggers, phone transfer and adaptive opacity were not tested. Full tables are now accessible; prior local retrieval-limit notes remain historical. |
| Jo, Kim & Seo (2015), *EyeBookmark*. [Author PDF](https://idclab.skku.edu/assets/EyeBookmark.pdf), Figure1 p.2, Table1 p.3, main-study results p.4 | Main study13 young Korean university adults; TobiiX60,24-inch desktop/chin rest; imposed60-second video interruption. | Sentence/block context at the interrupted location shortened resumption lag; comprehension did not significantly improve. Supports a resumption-context precedent, not spontaneous loss detection, repeated-interruption count, our focus window or older phone readers. |
| Niklaus et al. (2023), *Digital Reading Rulers*. [Primary PDF](https://thereadabilityconsortium.org/wp-content/uploads/2023/08/Digital-Reading-Rulers-Evaluating-Inclusively-Designed-Rulers-for-Readers-With-Dyslexia-and-Without.pdf), §4.4/Table4 PDFp.11; §4.5.1 | 177 desktop crowdworkers;91 classified dyslexic by self-report/screening; mouse-controlled rulers, no gaze detector. | GreyBar improved speed versus no ruler in the dyslexia group. Lightbox was commonly preferred, but preference did not establish superior speed/comprehension. No significant comprehension benefit; no significant speed/comprehension effect in the other group. A darker/narrower window is not established as better help. |
| Reichle, Reineberg & Schooler (2010), *Eye Movements During Mindless Reading*. [DOI](https://doi.org/10.1177/0956797610378686), [author PDF](https://eprints.soton.ac.uk/348426/1/348426_Reichle_Eye_20Movements_20During_20Mindless_20Reading.pdf), pp.1301–1309 | Four undergraduates reading a novel across12–15 hour-long sessions; EyeLink1000,1000Hz, chin/forehead rest; self-caught reports and intermittent thought probes. | Mindless intervals differed in fixation/linguistic effects while eyes still moved over text. Erratic behavior near reports is an association from a tiny controlled sample, not a validated individual classifier. No-gaze is neither necessary nor sufficient for distraction. |

The 2023 DOI10.1080/0163853X.2023.2266964 is Olkoniemi et al.'s irony scanpath
study, not a mind-wandering/ADHD detector; extensive rereading has content-based
explanations. Existing detailed appraisal remains in
`scanpath-reading-difficulty-evidence.md`. Source review cannot establish phone
benefit. Validation should assess resumption, comprehension and perceived
distraction separately, with participant-reported attention if an attention claim
is intended. Software replay only verifies the implementation rules.

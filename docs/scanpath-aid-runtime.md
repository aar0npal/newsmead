# Scanpath-to-aid runtime checkpoint

**Current appearance selector — 2026-10-14:** policy14 / conditionV9. Reading aids
offers Fade surrounding text (default/background mask) and Dark surroundings
(black mask). As of2026-10-15, Fade uses90%/98%; Dark uses40%/60% and400ms onset
to soften the immediate visual change. Opening/motion/detector rules remain.
Selector `FOCUS_MASK_STYLE_V3` adds per-style opacity/onset metadata.15 focused
JVM checks and2 camera-free A56 checks pass after the approved build correction
retry; both APKs build/install preserving data/calibration. Evidence:
`output/focus-dark-opacity-check-03/summary.json`.
`focus_style` is explicit on actions, preferences and held scroll presentation.
18 focused JVM checks and both APK builds/installs pass. All7 distinct native
checks pass across the initial run and approved two-check retry: reviewed test
callback wait and an awake/unlocked display for Replay. Data/calibration preserved.
[Current contract](repeated-interruption-focus-window.md).

**Previous display amendment — 2026-10-14:** policy13 / conditionV8. V4 focus
uses the article background at90%/98% opacity at50%/100% initial strength,
180ms anchor-safe motion, and viewport-pinned presentation during vertical
scrolling plus<=1500ms handover. Detector boundaries remain; held display has
separate provenance, with no new gaze evidence. Same5/4-row/full-width contract.
50 JVM checks and6 distinct camera-free A56 checks pass across the initial run
and approved test-fixture retry. App/test APKs build/install; data preserved.
[Current contract](repeated-interruption-focus-window.md).

**Previous appearance amendment — 2026-10-14:** policy12 / conditionV7. V3 focus
prefers5 clear rows, uses4 only when5 leaves no shaded surroundings, and covers
the full article viewport width including side margins. No60% cap or reduction
to3. Counts/triggers and gaze/text geometry are unchanged.14 focused JVM and4
distinct camera-free A56 checks pass across the initial run and approved
test-selection retry. Both APKs build/install. Natural reading remains untested.
[Current contract](repeated-interruption-focus-window.md).

**Previous return/visit amendment — 2026-10-14:** policy11 / smoothed conditionV6.
Returns require100ms/3 fresh article-presence samples, allowing moving search
without a stable line/word.3/30s/500ms and50/100% remain. Completed summaries
survive same-article scroll/definitions; pending sequences/anchors clear.
The ongoing fourth supported ABAB visit uses a separate8s aid window; finalized
P11 remains descriptive. V2 focus prefers3 clear lines, capped at60% viewport
height by removing neighbors while preserving the anchor. Empty shading is
withheld.219 focused JVM and5 camera-free A56 checks pass; both APKs build and
are installed. Natural-reading benefit remains unverified.
[Current contract](repeated-interruption-focus-window.md).

**Previous repeated-focus amendment — 2026-10-14:** policy **10**, smoothed condition
suffix V5. Focus uses 3 distinct qualifying unavailability/recovery episodes in
30s, with count-based strength and observed-reading fade. P11 line alternation
is an independent route; V4 no longer requires cycle + residence + slow progress.
Focus has two initial strengths, 50%/100%, and preserves completed witnesses
across ordinary unclassified-fixation gaps. Coarse line support does not require
word/glyph x precision. Initial strength holds3s then fades7s of usable captured
reading. Same-action last-line display tolerates<=1s since actual supported
delivery through brief missing/unconfirmed-line input, without invented evidence.
Definitions now require1500ms observed current-word residence. The bookmark stays independent.
[Exact rules and verification](repeated-interruption-focus-window.md).

**Current bookmark correction — 2026-10-13:** policy **7** / bookmark rule V2
adds a fixed last-line marker after **500 ms of reported unusable gaze**, including
looking away when coordinates disappear. The delivery-time timer is an
availability reminder, not a distraction label. Smoothed condition suffix V2;
outside/jump rules and calibration/filter remain. [Contract](scanpath-reading-bookmark.md).

**Current 2026-10-13 stream implementation:** the orange dot, article scanpaths
and aid decisions use the same calibrated, smoothed provider `x/y` point,
`article_smoothed_v1`, with no second filter or raw fallback. Policy **6** retains
the definition V4 visuals and rule timings. New conditions and schema-2 numeric
exports distinguish this route; true calibrated raw values stay separate.
[Full contract and checks](calibrated-smoothed-scanpath-path.md).

**Historical 2026-10-13 coordinate clarification:** "raw" here means **calibrated screen
coordinates before the article One Euro filter**. Saved SVR calibration and
eligibility gating are still applied. The visible dot uses smoothed x/y; current
scanpath mapping, candidate evidence and aid policy use rawX/rawY. This deliberate
match to research/replay preserves processing provenance but does not establish
that unsmoothed coordinates are the better interaction input. Filtering reduces
jitter with a possible lag cost ([One Euro authors](https://gery.casiez.net/1euro/)).
A filtered interaction variant would need consistent filtered mapping/detection,
separate identity and checks while retaining original research coordinates. Do
not splice a filtered word anchor into raw duration/pattern evidence. No behavior
or filter parameters changed in this explanation.

**Historical 2026-10-13 guide/menu amendment:** that checkpoint selected definition V4 / policy
5 and shared Reading aids controls. Line or sentence-context guidance uses coarse
supported lines; ambiguity uses paragraph fallback or abstains. Current gray
guides draw behind letters at 7.843% default opacity. No entry pulse or word-step
release is implemented. [Current contract and verification](reading-aid-menu-and-sentence-guide.md)
supersede older guide descriptions below. Previous V3 phone checks are historical.

**Historical 2026-10-13 bookmark amendment:** article, pilot and replay shared definition V3 /
policy 4 and the [last recorded-line bookmark](scanpath-reading-bookmark.md).
The fixed source-line cue follows a supported non-normal jump or sustained usable
outside-article coordinates; tracking loss alone does not create it. The 94 JVM
checks and all 32 distinct phone cases pass across the initial/corrected runs;
the bookmark contract retains the failures, installed hashes and validation limits.

**Historical 2026-10-04 amendment:** article, pilot and replay shared the
definition profile V2 and policy v3. The [continuation marker](scanpath-continuation-marker.md)
replaces the post-P09 cue with an expected-line mismatch cue at fixed visibility.
The older magnifier descriptions and software/device checkpoints remain historical.

**Historical 2026-10-03 article update:** ordinary articles selected the separate
`DEFINITION_PROFILE_V1` and nearby definition form instead of the top magnifier.
An explicit demo cycles aids in the loaded article with camera stopped.
[Current behavior, studies and consultation boundaries](article-definition-aid.md)
superseded the original ordinary-article word route. At that checkpoint,
replay/pilot retained the magnifier profile; all three now use definition V3.

**2026-10-02 — branch `measurement/scanpath-pilot`.** The article now connects
raw MGazeNet observations to the live word/line engine, an independent aid
policy, adjustable displays, reader controls and an inspectable action trace.
This is an experimental implementation, not a validated difficulty detector or
demonstration of improved reading. The [evidence contract](scanpath-adaptive-aid-design.md)
retains the source findings, populations, hardware and transfer limits.

## Implemented route

```text
calibrated-smoothed provider point + capture/delivery time + current Android text geometry
  -> provisional I-DT candidates + independent word/line assignments
  -> finalized P01-P13 events and a separate established ongoing prefix
  -> current target + source-linked policy + reader preferences
  -> line overlay / last recorded-line bookmark / near-word definition / focus region
  -> decision, numeric support and drawing receipt in a bounded memory journal
```

`StudyConfig.SCANPATH_READING_AIDS=true` selects this route for ordinary article
reading. It bypasses the old weighted index, ordinal controller and demo cycle.
Touch validation retains its existing route. The schema-1 raw diagnostic logger
and frozen v6/schema-9 validation protocol keep their identities; the new aids
are suspended during that raw diagnostic condition.

### Aid rules and displays

| ID / form | Selection | Display and strength | Source relationship |
| --- | --- | --- | --- |
| A1 / `CURRENT_LINE_HIGHLIGHT_GRAY_V1` | Current-line assignment from a finalized candidate or established ongoing prefix, matching the latest eligible selected-stream target. | Gray text background; adaptive baseline 0.25 (20/255 opacity), or reader-selected fixed strength. Sentence context is a reader option. | W24 gaze-following line form; N23 ruler-form comparison. This phone form and trigger are untested with human reading. |
| A2 / `LAST_READ_LINE_BOOKMARK_V1` | A remembered visible supported line, followed by sustained usable selected-stream coordinates outside the article or an uninterrupted supported non-normal jump. | Fixed 6 dp blue gutter bar, alpha 240, at the **last recorded source line**. No cue for normal next-line progression or tracking loss alone; holds until supported resumption/continuation or invalidation. | EB15/CW12 support interruption-location cues; this autonomous phone trigger and gutter form remain experimental. See [bookmark contract](scanpath-reading-bookmark.md). |
| A3 / `CONTEXT_WORD_DEFINITION_V1` | At least 1,500 ms observed current-word residence, from supported finalized visit time plus a nonoverlapping established prefix, or the prefix alone; current matching word/line. Missing time and separate short prefixes cannot fill it. | Near-word definition card; the source word stays highlighted. Consultation has its own lifecycle and excludes its interval from article sequences. | H03/M25 lexical-assistance precedents and transfer limits are recorded in [article definitions](article-definition-aid.md). The longer delay is an engineering response to the owner's interruption concern, not a validated optimal threshold. |
| A4 / `CONTEXT_FOCUS_REGION_V1` | Focus enabled; 3 completed qualifying unavailable/recovery episodes in 30 seconds, or an independent supported P11 line A-B-A-B cycle. A loss lasts at least 500 ms before tracked coordinates return; new supported reading completes the episode. | Current line plus two lines each side remain undimmed. Episode counts 3/4+ start at 50/100%; independent nonoverlapping cycles 1/2+ use those steps. Hold3s then fade7s of accumulated usable capture time; retain last confirmed position through<=1s support-delivery age. | EB15/N23/S14 support context-preserving aid forms with population/hardware limits; none validates the NewsMead count, delay, dose or phone benefit. [Exact rule](repeated-interruption-focus-window.md). |

A3 takes priority over A4. A1/A2 may accompany word support as separately
identified actions. A backward return or cycle alone does not force a support
response. Forward movement updates location; it does not increase assumed
difficulty. A0 is no aid/suspension, not a rendered level. Other grey-bar,
underline and word-progress coloring variants remain candidate forms.

The ongoing prefix becomes available only after the minimum detector span and
sample count. It can add **observed time**, but never an extra finalized fixation
or P04 refixation. Duration sums exclude unobserved gaps. Word and line agreement
remain independent; a usable line does not manufacture a word assignment.

The historical `CONTEXT_WORD_MAGNIFIER_V1` comparison form remains available in
the separate experimental profile. It requires two finalized same-word
refixations and 750 ms residence, with strength rising over 750–1,500 ms. It is
not the current article/pilot/replay word aid.

## Experimental settings and historical magnifier comparison

These are versioned engineering settings, not published cutoffs, confidence
weights or calibrated probabilities of reading difficulty. Current definition
conditions use `DEFINITION_PROFILE_V4`; the separate magnifier comparison uses
`EXPERIMENTAL_PROFILE_V2`, condition `EXPERIMENTAL_AIDS_V1`. Shared settings and
the retained comparison parameters are listed below.

| Setting | Value / units |
| --- | --- |
| Candidate minimum span / samples | 100 ms / 3 samples |
| Dispersion | Sum of x and y ranges <= 0.5 physical line heights |
| Maximum sample / transition gap | 125 ms / 125 ms |
| Assignment agreement | 0.8, independently for word and line |
| Geometry settle guard | 150 ms after physical geometry change |
| P09 endpoint fractions | Departure >= 0.75; arrival <= 0.25 of line width; verified LTR |
| Current bookmark departure | Supported non-normal jump, or usable outside-article coordinates for 100 ms / 3 samples; comparison requires an uninterrupted segment and <=125 ms transition gap |
| Legacy V2 continuation source progress / remembered-end window | First-quarter <=0.25 then last-quarter >=0.75; landing within 2,000 ms of latest end evidence; no limit on source-line reading time |
| Bookmark marker appearance | Fixed 6 dp / alpha 240, 4 dp before source text in the existing 16 dp gutter |
| Current definition observed residence | 1,500 ms; no obligatory refixation count |
| Word minimum refixations / residence / strength saturation | 2 finalized refixations / 750 ms / 1,500 ms |
| Line residence minimum / strength saturation | 1,000 ms / 2,000 ms |
| P11 cycle window | 2,000 ms |
| Current focus loss qualification / count window | 500 ms contiguous reported unavailability / 3 completed episodes within 30,000 ms |
| Current focus dose / hold / fade / grace | Episode counts 3/4+ or independent cycles 1/2+ yield 50/100%; hold3,000ms then fade7,000ms accumulated usable selected captures; support-delivery grace1,000ms |
| P12 frontier window / maximum line rate / minimum observed fraction | 1,000 ms / 0.5 lines per second / 0.5 |
| Maximum evidence age | 250 ms, coherent capture/delivery/elapsed time |
| Transient action lifetime / non-guide cooldown | 1,500 ms / 2,000 ms; current bookmark holds until supported resumption/continuation or explicit invalidation |
| Adaptive minimum / maximum magnification | 0.25 / 1.5 times source text size |
| Focus context radius | 2 physical lines each side; strength changes veil prominence, not readable span |
| Pending sample / finalized history caps | 4,096 samples / 512 finalized candidates per Android engine generation |
| Journal / source-fact capacity | 2,048 records / 512 facts |

The generic engine still leaves P09 endpoint and P12 long/slow thresholds unset.
The article runtime supplies the explicit profile above. At 512 finalized
candidates the adapter emits `history_limit`, starts a new generation and carries
the last capture-time floor forward. Long paths crossing this resource boundary
are not joined. It bounds prefix-support work; it is not a human reading threshold.

New actions require coherent evidence with actual observation-delivery
provenance. Missing/invalid input and target movement clear transient gaze aids.
The current bookmark keeps its saved source/static cue through ordinary tracking
loss in unchanged geometry; explicit reported tracking loss for 500 ms can also activate it. Jump comparisons
break across missing input, and first recovery elsewhere only reseeds comparison.
Supported source return or distinct supported continued reading clears it.
Geometry/text/scroll, pause/restart, calibration/controls and definition
consultation invalidate it. Source and departure bounds are recorded separately
in `bookmark_evidence`; P09 does not select it. A 100 ms timer and pre-draw check
enforce transient expiration even when new gaze stops. Rendering rechecks the
geometry and action lifetime, including the bookmark's lifecycle hold.

## Reader controls and trace

### Timing and typography evidence audit — 2026-10-08

- Hyrskykari, Majaranta & Räihä, *Proactive Response to Eye Movements* (2003),
  sections 4.2.1 and 5.2: an approximately 800 ms total-word-time help threshold
  varied with word length/frequency and included returns. This is a lexical-help
  interaction precedent, not a validated cutoff for our current-word-visit,
  English-definition, older-phone-reader condition.
  [Author paper](https://www.researchgate.net/publication/221054255_Proactive_Response_to_Eye_Movements).
- GazePrompt, sections 3.2.2–3.3: 500 ms first-fixation / 1500 ms within-pass
  defaults were adjustable; the 13 low-vision desktop participants could also
  choose font size. This supports adjustable delays, not our exact focus ramp.
  [Primary paper](https://arxiv.org/html/2402.12772).
- Franken, Podlesek & Možina, *Eye-Tracking Study of Reading Speed from LCD
  Displays: Influence of Type Style and Type Size*, Methods/Results pp. 3–5:
  48 adults, Tobii X120, desktop LCD, Georgia/Verdana 12–24 pt. Mean fixation
  duration changed with size/typeface. This does not supply a font-to-aid-delay
  formula or validate phone reading thresholds.
  [Primary paper](https://bop.unibe.ch/JEMR/article/download/2395/3591/8619).
- Current article study settings lock text at 22 dp. Actual rendered layout and
  line height control geometry and dispersion, but the policy has no font-size
  input for its timing. A changed layout breaks continuity; it does not rescale
  the 1500 ms definition rule or the legacy 1000/2000 ms residence rules. Current
  focus uses loss/cycle frequency, without a residence-based strength ramp.
  Line residence and physical-lines-per-second also
  depend on wrapping and are not equivalent amounts of text across layouts.
- No reviewed paper establishes the current focus frequency/strength/fade settings,
  the legacy 1000–2000 ms linear focus schedule or 25% current-line baseline.
  Their units describe experimental display
  settings, not a calibrated difficulty probability or validated aid dose.

The article diagnostics menu contains **Reading aids** and **Save aid diagnostics**.
Controls independently enable the aid forms, switch adaptive or
fixed strength, and set maximum/fixed strength from 0 to 100 percent. Positive
strength values do not attenuate the bookmark marker. Preferences currently
last for that fragment session. Zero strength clears actions.

As requested by the owner on 2026-10-08, focus is enabled by default for the
current prototype in article, pilot and replay preferences. V4 uses the independent
repeated-interruption and supported-alternation rules above, and can be disabled
in the controls. Pilot shadow/no-aid conditions still do not display aids.

The historical magnifier uses a fixed **68 dp reserved strip** below the article
top bar while enabled. Current definition conditions reserve **0 dp**. The old
strip changes the available article viewport, preserves the paragraph,
and avoids layout changes whenever a word appears. A word that cannot fit is
suppressed and recorded, rather than truncated. Phone layout and preference
effects still need device checks.

The trace records configuration/version/model/calibration/text hashes, reader
preferences, action/form/condition IDs, UTF-16/line anchors, capture/evidence/onset
times, strength, reasons, study keys, numeric supporting event/prefix facts and
drawing receipts. Save is explicit and writes an app-private
`noBackupFilesDir/scanpath_aids_<uuid>.json`. Schema 2 contains selected/original
numeric gaze coordinates, but no word strings, camera images or upload. It is an action audit, not a replacement
for the matched raw evaluation instrument.

Drawing receipts are **canvas/text draw proxies**, not verified physical screen
visibility or eye-to-cue latency. Bounded exports report dropped records, evicted
facts, missing support IDs and `complete_trace`; partial traces must not be treated
as complete sessions. Record the installed APK/build identity separately during
future collection.

## Historical software checkpoints and remaining work

The [trigger evidence review](scanpath-trigger-evidence-review.md) rechecks the
actual activation predicates against primary methods and results. It identifies
the GazePrompt OR-to-NewsMead AND adaptation and missing focus/dose validation.
The current replay screen provides the shared article presentation-only demo.
Its six four-second stages show baseline, line guidance, fixed source-line
bookmark, word definition, light focus and strong focus without detector evidence.
Stop/pause/layout changes clear it. This presentation is not a detection result.

The [interactive replay screen](scanpath-replay-screen.md) is the first milestone
of the pilot screen: raw-coordinate scenarios use actual Android text
layout and the same engine, policy and drawing views. Home and article diagnostics
provide entry points. Its private full-input trace explicitly labels synthetic
capture times and actual delivery times, with no camera/calibration access.
Controls, geometry changes and pause terminate a run. The guide records the
current build/device checks and limits. The separate
[live pilot screen](scanpath-live-pilot.md) now implements prompted placement/order
tasks and matched raw reading conditions; their human measurement remains pending.
All five real-activity tests pass on SM-G991B/API 35, including expected aid draw
proxies and causal trace links; [device check 03](scanpath-device-check-03.md)
retains the initial test lookup failure, correction and successful results for
that preceding build. Current bookmark cases have the camera-free A56 results
recorded in [the bookmark contract](scanpath-reading-bookmark.md).

Focused synthetic JVM checks exercise extraction, prefix parity, policy choices,
adaptive/fixed strength, reset/suppression and the real engine-to-policy route.
The Python reference suite and fixture consistency check protect the shared
detector contract. Debug APK assembly and Android test compilation establish
build readiness. [Device check 01](scanpath-device-check-01.md) ran four synthetic
geometry/render tests on SM-A566B/API 36. The first attempt had one pass and three
failures from missing fixture layout parameters. After correction and an
owner-authorized retest, **all four passed**. [Device check 02](scanpath-device-check-02.md)
then passed the same four tests on SM-G991B/API 35 using identical APKs. Visible
article UI and live gaze behaviour still need separate checks.
Independent source review covered timestamp/geometry boundaries, causal support,
policy state, resource bounds and action provenance.

The current-build A56/API 36 check is [device check 04](scanpath-device-check-04.md):
the first attempt had eight demo/replay failures and five passing pilot checks.
The shared word renderer's available-height check now uses compound padding,
retaining true font/word fit and reserved height; corrected local builds and
56 JVM tests pass. The owner-authorized corrected A56 retest passes **all 15
checks**, including demo/replay, all five pilot checks and the centered-strip
regression. App-only word/focus previews were inspected. This is synthetic
software evidence; natural gaze and human benefit remain unmeasured.

The matched raw placement/continuous-order instrument is now built under its own
pilot identity, with capture-window agreement, private full-input traces and
no-aid/shadow/display conditions. Further authorized device inspection and
phase-specific study procedures precede participant tasks in
[pilot revision 6](scanpath-measurement-pilot.md). Natural fixation
validity, word/line accuracy, processing/render latency, unwanted cues and human
benefit remain unmeasured. Whole-path clusters, HMM/HSMM phases, a trained
difficulty model and calibrated decision weights are not implemented.

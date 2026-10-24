# Repeated-interruption focus window

## Font-aware line switching and visible surroundings — 2026-10-15

Owner reports that small-font reading activates focus too quickly. The final
instruction changes **only line switching**: look-away remains **3 separate
qualified episodes within 30 seconds**, with the existing loss/return guards.

| Rendered font size | ABAB cycles before activation | Minimum supported visit span |
| --- | --- | --- |
| 22 dp and above | 1 | Existing detector minimum (100 ms) |
| 18 to below 22 dp | 2 | 160 ms |
| Below 18 dp | 3 | 240 ms |

Size means actual font pixels divided by Android display density, including
system font scaling. With system scaling at 100%, the article's sp selection
matches these dp values. The tiny 0.001 dp boundary tolerance avoids float
roundoff; invalid size uses the strictest rule. A visit is an established
same-line evidence prefix, not accumulated time across separate fixations.
No word assignment is needed. Every cycle uses four new nonoverlapping visits
and must fit within the existing 8-second cycle limit. Completed cycles count
within the existing rolling 30 seconds. First qualifying demand is 50%; one
additional new cycle reaches 100%. The normal 3-second reading-time hold and
7-second recovery fade remain.

A detector-established visit that ends before meeting the extra font guard
breaks the pending order: A–B–C(short)–A–B cannot become an invented ABAB.
A still-growing prefix may qualify later. Any font-size change, even within
one tier, clears pending/completed line cycles and line-triggered demand.
Completed look-away episodes and their demand remain independent of font size;
operational controls still clear pending evidence and require a fresh anchor.

Fade surrounding text now uses **60% opacity at 50% action strength** and
**85% at 100%**, retaining nominally 40%/15% of original text/background
contrast (8-bit alpha 153/217). All resolved focus masks are capped at 85%.
Dark surroundings retains 40%/60% black opacity and its 400 ms onset.
The 4–5-line opening, full width, 180 ms motion and scroll handover are unchanged.
This preserves pixel contrast; readability and comfort are not established.

These font tiers, counts, durations and opacity values are trial safeguards
chosen for this implementation and owner feedback, not study-derived difficulty
cutoffs. Scanpath extraction, calibration, smoothing, word-definition rules and
controlled validation text sizes are unchanged. Policy version 16, line rule
`A4_FONT_SCALED_LINE_VISITS_V3`, font rule `RENDERED_SIZE_LINE_VISITS_V1` and
appearance `FOCUS_MASK_STYLE_V4_VISIBLE_SURROUNDINGS` identify the change. Aid
configuration/context/evidence record actual size and effective cycle/visit rules.

Source review caught and corrected the short-detour issue; follow-up has no
actionable findings. First run assembled both APKs and passed 84/85 focused
JVM checks. The new typography fixture assumed an 80 ms detector baseline;
the existing minimum is 100 ms. Its source correction supplies established
prefixes at 120 ms on a 40 ms sample grid and checks the actual 100 ms minimum.
Review found no issue with that correction. Owner-approved retry passes both
APK builds and **85/85** focused JVM checks. Data-preserving A56 app/test installs
succeed; all **5/5** camera-free native checks pass in **5.007 s**. Actual mask
pixels are 153/217 at half/full strength; Dark onset, scrolling hold, fresh-anchor
handover, rendered-size settings and unchanged three-episode recovery are checked.
No camera, calibration changes, staging or commit. Final evidence:
`output/focus-font-visibility-check-02/summary.json`, build/native/install logs
and copied JVM XML. Initial failed-run evidence stays in 01.
Native settings checks cover real rendered sizes; activation and preservation
across sizes are covered by synthetic policy fixtures, not natural gaze data.

Earlier amendments below retain historical values and verification.

## Softer dark onset amendment — 2026-10-15

Owner clarifies that Dark surroundings is too strong as an immediate visual
change. Dark now targets40% black opacity at50% initial action strength and60%
at100%, introduced over400ms using a smoothstep fade-in. Fade surrounding text
retains90%/98% background coverage. Strength below50% still scales toward zero
during recovery. These are interaction settings chosen in response to owner
feedback, not study-validated doses. The opening, position animation, scroll hold,
detectors and action strengths stay the same. Onset belongs to continuous visible
presentation, not each refreshed action ID, and its first zero-alpha frame cannot
claim a successful draw. Current selector `FOCUS_MASK_STYLE_V3` records per-style
opacity/onset settings in the configuration hash; policy14/conditionV9/formV4
remain. Shared article/demo/Pilot/Replay renderer receives this change. Initial
opacity-only13 JVM checks/builds pass. A missing per-frame flag in the final onset
build was restored and source-reviewed. Owner-approved retry passes15 focused
JVM tests, both APK builds/data-preserving installs and2 camera-free A56 checks
in9.957s. Native assertions cover actual onset alpha, nonrenewal across refreshed
actions, hold/fresh-source continuity, resets, and scheduled Dark/Fade Replay
rendering. Evidence: `output/focus-dark-opacity-check-03/summary.json`; failed02
build retained. No camera/calibration/staging/commit; human comfort unverified.

## Selectable appearance amendment — 2026-10-14

Reading aids → Focus window appearance offers **Fade surrounding text** (default)
and **Dark surroundings**. Fade uses the article background; Dark uses black.
Both have90% mask opacity at50% initial strength and98% at100%, then the existing
recovery fade. The4–5-line opening,180ms motion, scrolling handover and detector
triggers stay the same. These are appearance choices, not new difficulty scores.
Choices are session-only, disabled while focus/master is off, and retained when
those switches are restored. Article/Pilot/Replay and article/Replay demo use the
same selected style. Style is stored on each focus action and in preferences,
so held scroll presentation retains its source's style and exports are auditable.
Current policy14/conditionV9 adds the selector; form remains V4.18 focused JVM
checks and both APK builds/installs pass.5/7 native initially pass. A reviewed
test-only correction waits off the UI thread for the posted dismissal callback.
Replay chose focus but recorded no draws; device was found dozing afterward.
Owner-approved test rebuild/install and two-check retry, with display confirmed
awake/unlocked, pass in10.728s. All7 distinct native checks pass across both runs.
App APK/data/calibration stay preserved; no camera/staging/commit. This verifies
software/UI behavior, not human visibility/comfort or reading benefit. Evidence:
`output/focus-style-menu-check-02/summary.json`; initial logs/trace/XML retained
under `output/focus-style-menu-check-01/`.

## Current contrast, motion and scrolling amendment — 2026-10-14

Owner observes distracting visible surroundings, abrupt movement and disappearance
while scrolling. Source confirms V3 initially paints only20/255 black alpha at50%
strength and clears active anchors on every scroll geometry update. These explain
the implementation behavior; no natural-reading trace establishes benefit/harm.

V4 retains the5-row clear area (4 only when5 leaves no surroundings) and full
width. It paints the reader background over surrounding text at90% opacity for
50% initial strength and98% for100% initial strength (8-bit values230/255 and250/255).
Below50%, opacity scales continuously toward zero during the existing recovery
fade. This leaves approximately10% or2% of original text/background contrast,
instead of putting a gray filter over still-readable text. Light, dark and sepia
use the actual ancestor background. These percentages are owner-selected interaction
tuning, not validated difficulty probabilities or paper-derived doses.

Vertical window transitions take180ms; presentation interpolation does not alter
the selected gaze stream, detector timestamps, fixation positions or evidence.
The currently supported anchor row remains uncovered during a transition.

When geometry changes only because the unchanged article translates vertically,
an already visible V4 focus may retain a viewport-based presentation during
scrolling, then for at most1500ms after the last translation. It holds the previous
clear band in screen coordinates, fitting it within a changed article viewport;
text passes through it without snapping the mask to every passing row. Fresh eligible support hands back
to the ordinary focus action. This is display continuity, **not a new reading
location measurement**: scanpath boundaries, geometry settling, pending-visit and
pending-interruption cancellation remain. Dedicated `focus_scroll_presentation`
records identify the original source action, old/current geometry, drawn bounds
and `new_gaze_evidence=false`. It does not fabricate actions or source samples.
Reflow/text changes, unavailable geometry, menus, consultation, disable and screen
stops clear the presentation. Manual Replay scrolling still stops its synthetic
coordinate run, because those planned coordinates no longer match.

Current article/Pilot/Replay conditions use suffixV8; policy13 and focus form
`CONTEXT_FOCUS_REGION_V4` distinguish this revision. The article demo uses V4;
its layout-change stop remains an explicit presentation-preview boundary.
The previous checkpoints below retain historical settings and evidence.
Verification:50 focused JVM tests pass with zero failures/errors/skips; both APKs
build and data-preserving A56 installs succeed. Five of six native checks pass on
the initial run; the new runtime fixture's detached parent scroll did not change
reported text origin. A reviewed test-only correction uses TextView scroll-offset
translation. Owner-approved test APK rebuild/install and the single native retry
pass in0.707s. All six distinct native checks pass across the two runs. Coverage
includes actual V4 background/full-width pixels, five clear rows, fresh-anchor
safety, half-row pinned movement,1500ms expiry without tick renewal, runtime
presentation without new policy support, demo form and Replay geometry stops.
The detached runtime fixture does not establish attached NestedScrollView callback
ordering or human benefit. No camera/calibration/data clearing/staging/commit.
Evidence: `output/focus-display-scroll-check-02/summary.json`; initial logs/failure
and50 JVM XML retained under `output/focus-display-scroll-check-01/`.

## Current appearance amendment — 2026-10-14

Owner requests4–5 clear lines and full-width shade without white side gutters.
`CONTEXT_FOCUS_REGION_V3` prefers the current physical line plus two above/below,
sliding the five-row band near visible/article edges. If five would fill the
viewport and leave no dimmed area, it uses four; it never reduces to three for
visibility. Fewer available rows stay clear, and a viewport with no surrounding
area produces `focus_no_dimmed_area` rather than a false draw receipt. The old60%
height cap applies only to historical V2 rendering.

The veil reaches the visible overlay's left/right edges across the article's
vertical area, covering text margins. Gaze assignment, text wrapping and measurement
geometry retain their own original bounds. Article, demo, Pilot and Replay share
radius2/V3; policy12/conditionV7 identifies this visual revision. Activation/count,
hold/fade, strength and dictionary settings remain as below. The demo uses the
same resolver to determine whether a focus preview can actually draw.

New regression checks cover five-row height above the former60% budget, sliding
at both edges, four-row fallback, no shrinking to three, bitmap pixels at both
outer edges beyond24dp text margins, and demo availability.14 focused JVM tests
and both APK builds pass; both data-preserving A56 installs succeed.3/4 native
checks pass (bitmap/full width and both demo cases). The failed Replay test ran
the default navigation scenario, confirmed in its complete synthetic trace, so
its assertion expecting focus was inappropriate for that run. The selector
helper now waits for initial layout and requested visible selection, with an
explicit result-scenario assertion. Source correction review reports no actionable
finding. Owner-approved test APK rebuild/install and the one native retry pass
in10.002s. All4 distinct camera-free checks now pass across the two runs; the
failed default-scenario trace remains retained. No natural gaze/camera, staging
or commit. Evidence: `output/focus-window-width-check-02/summary.json`; initial
failure retained under `output/focus-window-width-check-01/`.

## Current return and line-visit revision — 2026-10-14

Owner authorized this revision after reporting that both live look-away and line
switching still fail, and pointing out that searching for the old reading place
may prevent the old supported-line recovery gate from completing. Source review
confirmed that gate, history resets during scrolling/definitions, finalization
delay and the two-second P11 deadline, and potentially empty visible shading.
Those are confirmed code behaviors; no current natural-reading trace establishes
which caused the owner's attempts to fail.

### Interruption route

1. Arm after established reading support or confirmed article presence.
2. An explicit unavailable-gaze interval must still reach500ms. Missing callback
   delivery to the first fresh finite tracked return measures that interval;
   tracked time outside the article cannot extend a brief loss to qualify.
3. Confirm return using **at least3 fresh samples over100ms** inside the visible
   article rectangle. Successive captures must be<=125ms apart, capture age<=250ms,
   and capture/delivery clocks ordered. Gaze may move between lines or words;
   mapped text eligibility, dispersion and a stable fixation are not required.
   One tracked blip cannot complete or rearm an episode. A qualified loss followed
   by unstable returns remains one episode until presence is confirmed.
4. Count completion at the confirming sample's **delivery** timestamp. Three
   distinct completions in30s start50%; four or more start100%. These counts are
   separate from line alternation. Missing frames never count as separate events.
5. Demand can activate without a confirmed current line. If a previously supported
   line is still fully visible in the same geometry and<=30s old, use it while the
   reader searches. Fresh article presence maintains its one-second display grace;
   a new supported line moves it. If no valid anchor exists, retain demand and
   report waiting for an anchor; never invent a reading location.

### Line-visit route

Observe four confirmed physical-line visits **A→B→A→B**, A!=B, within an
aid-specific **8,000ms** window, including the established ongoing fourth visit.
Repeated prefixes or finalized callbacks from the same visit do not count again.
One nonoverlapping sequence starts50%; a second sequence within30s starts100%.
Use line support from the existing selected-stream candidate/fixation path, with
freshness and physical-row checks; no exact word assignment is required.

The separate finalized P11 classifier keeps its original2s window and diagnostic
identity. It does not supply an additional current-profile focus trigger. Ordinary
unclassified fixation motion can retain aid visits while selected input remains
continuously available; a missing/outside sample, capture gap, operational pause,
segment or geometry change breaks that visit sequence. No finalized P11 path is
joined through such a boundary. Aids trace labels aid visits separately from P11.

### Persistence, appearance and audit

- Same-article scrolling/geometry and definition consultation retain completed
  episode/alternation summaries. They cancel pending episodes, return confirmation,
  line sequences and screen anchors; consultation/controls cannot create episodes.
  Current geometry and fresh support are required before drawing after those pauses.
  New article/generation, explicit stop/reset, calibration restart, disable or zero
  strength clears history. Reader-controls pauses retain completed summaries.
- An activated response retains its triggering witnesses for its own3s usable-reading
  hold and7s fade, bounded by30s since activation. An older counting-window witness
  expiring cannot immediately erase a newly activated response. Missing time and
  repeated ticks never advance usable-reading time or extend the bounded expiry.
- Previous form `CONTEXT_FOCUS_REGION_V2` prefers the current line plus one above
  and below. It removes neighboring context when that clear area exceeds60% of
  the visible article height, always preserving the visible anchor row itself.
  Alpha remains20/255 at50% and40/255 at100%. Empty surrounding area or zero alpha
  produces an explicit withheld receipt, not a successful draw proxy. The article
  demo uses the same form. Render receipts remain proxies, not human perceptibility.
- `presence_recovery` stores real capture/delivery bounds, sample count and selected
  stream; `recovery` is null when no line recovery was required. Completion timestamps
  and aid-specific `line_visit_alternations` are explicit. A state-change journal
  reports counts, pending return, active demand and waiting for presence/anchor.
- Shared policy11 and smoothed article/pilot/replay conditionV6 distinguish this
  implementation. Calibration, smoothing, raw originals, dictionary1500ms trigger,
  descriptive scanpath detector and frozen independent measurement protocols remain.

The100ms presence debounce,8s aid window,60% context cap and all existing count,
strength and duration values are configurable **engineering trial settings**.
No reviewed study validates them as distraction thresholds, optimal assistance,
or probabilities. The [evidence appraisal](focus-window-improvement-review.md)
and [SwitchBack primary paper](https://ubicomplab.cs.washington.edu/pdfs/switchback.pdf)
support studying return-time assistance; they do not validate NewsMead's combined
rules. New real-engine and Replay cases cover moving search returns and slower
alternation while the fourth visit is still ongoing. Current verification passes
**219 focused JVM tests**, with zero failures/errors/skips, and builds both APKs.
Source review's expired-anchor retention finding was corrected and independently
rechecked. Both data-preserving A56 installs succeeded; **5 camera-free Android
tests pass** in31.501s, including actual shaded-pixel checks, moving returns,
ongoing-fourth visits,50/100% counts, consultation exclusions and same-article
geometry retention with fresh-anchor requirements. No natural gaze/camera,
staging or commit. Evidence/XML/APK/source hashes and device logs:
`output/focus-return-activation-check-01/summary.json`. Previous passing totals
below describe older implementations; these supplied-input checks do not prove
natural-reading sensitivity, specificity or human benefit.

## Previous stability revision — 2026-10-14

Owner now reports a successful live trigger followed by immediate disappearance
and authorizes the stability correction plus a stricter dictionary trigger.
Current targets: hold focus's initial50/100% for **3,000 ms of usable reading**,
then fade over **7,000 ms**; preserve its last confirmed line through at most
**1,000 ms since actual supported evidence delivery** during brief unavailable or
unconfirmed line-transition input. Repeated ticks cannot renew that support age.
Fresh supported lines move the window. Geometry/scroll, menus, pause, disable,
definition consultation and longer unsupported periods still clear the display.
Counter/candidate/fixation evidence and recovery reading time cannot be fabricated
from retained drawing. Word definitions require **1,500 ms observed current-word
residence**, raised from800ms. Shared policy10/conditionV5. These are prototype
timings, not validated optimal aid durations or distractibility measures.

All **207 focused JVM tests pass**, both APKs build and source review has no
actionable finding. A Replay dwell was corrected to1750ms to exceed the1500ms
definition delay while retaining the older compound rule's separate 2 s cycle
window. Owner-authorized A56 updates succeeded; **3 camera-free Android tests
pass** in 23.743 s. They cover brief-gap retention, third/fourth strengths,
definition display at the longer threshold, and consultation exclusions/priority.
Natural reading and benefit have not been validated by these supplied-input checks.
Evidence: `output/focus-stability-definition-delay-check-02/summary.json`, copied
XML, APK hashes and install/check logs. Previous scores below are historical.

## Previous two-strength checkpoint

2026-10-14 preceding revision: owner reports live guides/bookmark work and Replay
shows focus, but intentional live alternation and repeated look-away do not.
Owner requests two or three activation strengths for short news reading; this
revision uses **50% and 100%**. Live continuity/line-support corrections and the
new strength mapping pass **200 focused JVM tests**, with both APKs built and no
remaining actionable source-review finding. Owner-authorized data-preserving
A56 updates succeeded; **2 camera-free Android tests passed** (15 scripted runs)
with interruption50/100% and line-cycle drawing checks. Natural live reading has
not been collected for this revision. The earlier
194-test build below is historical. Exact cause of the reported live failure and
reading benefit are not established by these synthetic checks.

## Previous supported-recovery rule

One episode is supported reading → qualifying reported gaze unavailability →
fresh supported reading recovery. The unavailable period must last at least
**500 ms**, measured from the first unavailable callback's delivery to the first
fresh finite selected-coordinate return, even if that return is outside the
article. Counting completes only when a new line candidate or fixation supports
reading recovery. Tracked time outside text cannot extend a short loss to meet
the threshold, and short losses separated by tracked points are not combined.
One long absence counts once; missing frames never count as separate
interruptions. After a qualifying loss, unstable tracking returns and further
missing bursts remain one episode until reading is supported again. The first
qualified interval's duration is retained rather than extended across tracked time.

The controller counts recovery completions in the most recent **30,000 ms**.
Three activate the window; single-interruption placekeeping remains the
independent bookmark rule. No word assignment is required for focus recovery.

| Recent qualifying episodes | Initial adaptive strength | Outside-text opacity before fade/caps |
| --- | --- | --- |
| 0–2 | No interruption-triggered focus | None |
| 3 | 50% | 20/255 = 7.84% |
| 4 or more | 100% | 40/255 = 15.69% |

These are two initial activation strengths. Gradual fade can produce intermediate
percentages; it does not introduce additional trigger tiers. The 30-second period
is a rolling look-back window, not a waiting time or minimum article duration.

The current line plus **two physical lines above and below** stay undimmed;
surrounding text stays readable. Strength changes darkness, not text layout or
window size. Reader maximum/manual strength remains effective. Word-definition
consultation has priority and excludes its interval from article interpretation.

The window follows fresh supported reading lines. Strength holds at its initial
value for **3,000 ms**, then fades linearly over **7,000 ms**, using accumulated
usable reading capture time: only finite, ordered,
eligible selected-stream samples whose successive captures are no more than
125 ms apart contribute. Missing periods and long gaps do not advance the fade;
wall-clock ticks do not invent reading. During brief unavailable or unconfirmed
line input, the same action stays at the last confirmed line for at most
**1,000 ms since the last supported evidence delivery**. Ticks and duplicate
support do not renew that time. A new supported line moves the window. Longer
unsupported periods hide it while eligible history is retained; fresh support
can restore the remaining hold/fade phase. Expired frequency evidence cannot
restore the window. Operational invalidation and definition priority still clear
immediately; the three-second modulation hold is not an unconditional wall-clock
display guarantee through those events.

Existing **P11 A–B–A–B line alternation** is a separate V4 trigger without the old
residence/slow-progress conjunction. Each accepted cycle is within the existing
2-second cycle window; nonoverlapping cycles in the recent 30-second interval
start at 50%, with a second or later cycle starting 100%. The two routes' counts are not pooled; the
strongest currently supported dose governs. A weaker route cannot abruptly
reduce stronger valid support. Normal forward progression and arbitrary
scattered jumps do not supply new focus triggers.

## Eligibility, resets and evidence

- Missing reasons: `no_face`, `invalid_crops`, `eye_area`, `stale`,
  `invalid_prediction`, `coordinate_unavailable`. Preserve these distinctions;
  recurring tracker faults may mimic looking away.
- Finite ordered delivery clocks, current rendered geometry, and a supported
  source line are required. Old/future clocks, controls, dictionary consultation,
  pause/stop, scrolling/reflow/text change, disabled focus and zero strength
  cannot count as interruption episodes. Operational resets clear history.
- Ordinary `unclassified_fixation_gap` breaks word/line scanpath continuity and
  retains the last confirmed focus within its delivery-age grace, retaining completed focus episode
  and cycle witnesses within their 30-second window. It cannot manufacture a
  new line cycle across the gap. The prior policy accidentally erased history.
- Definition consultation and reader controls still reset focus history in the
  current runtime; this correction does not change those operational rules. For
  an isolated live focus check, disable definitions before starting the sequence.
  A camera-free Replay pass does not prove the user's natural look-away generated
  unavailable input or that their intended line switches formed supported P11.
- Focus accepts supported physical-line targets across the visible article width,
  including line-only assignments outside the glyph ink of a short line. Finite
  selected coordinates, matching row y, current geometry and freshness remain
  required. Word assignment and bookmark matching are unchanged.
- Article/pilot/replay continue using `article_smoothed_v1`, the same calibrated,
  filtered provider point as the dot. No new filter, raw fallback, threshold
  radius, word-model requirement or frozen measurement-protocol change.
- Policy **10**, current smoothed condition suffix **V5**; visual form remains
  `CONTEXT_FOCUS_REGION_V1`. The legacy experimental/magnifier profile retains
  its earlier compound focus rule.
- `focus_interruption_episode` records retain source/recovery support, real
  missing callback capture/delivery bounds, first tracked-return delivery,
  reasons and callback count. Watchdog capture timestamps may repeat or be null;
  they are observation provenance, not fixation samples. Episode duration uses
  delivery time; callback bounds may span later missing bursts before supported
  text recovery, while `qualifying_loss_elapsed_ms` stops at the first tracked
  return. `focus_evidence` links the distinct episodes/cycles, initial
  strength, three-second recovery hold and accumulated recovery time to decisions.

## Replay and verification

Replay **Repeated interruptions** delivers four distinct synthetic no-face
gaps and supported returns. The third return should choose 50%; the fourth
should choose 100%. **Brief interruptions** uses three gaps shorter than 500 ms
and should not choose interruption focus. The existing one-gap bookmark example
should also have no interruption-triggered focus. These examples keep camera
and live dictionary off; they test software only.

Current two-strength revision: the real-engine continuity tests use the live
detector configuration, selected-stream `rawSample` and actual callback order.
They verify completed interruption/cycle retention across ordinary word/line P13
gaps, fresh support before restoration, no new cycle joined through a gap,
operational/geometry resets, and coarse line-only support while rejecting
outside-article or wrong-row coordinates. All **200 focused JVM tests pass**.
Both APKs built before test-only assertion corrections; app and Android-test
source stayed unchanged during the retries. Source review found no actionable
issue. Evidence: `output/focus-live-two-strength-check-04/summary.json`, copied
XML, APK/source hashes and device install/check logs. Two native Android tests
pass on A56 in50.631s, covering15 scripted scenario runs, including no-focus
counterexamples, third-return50%, fourth-return100%, cycle focus and drawing
receipts. No camera, natural gaze recording, staging or commit ran.

Historical four-strength build: first gate stopped at a nullable geometry access before tests. The approved
second gate built both APKs and passed 188 of 189 JVM checks; one new replay
assertion incorrectly required no descriptive residence records during short
visits. The assertion now checks residence duration below threshold. Review also
found continued-alternation anchoring, expired frequency evidence, capped/manual
route selection, and tracked outside-text time inflating a brief loss. Source
corrections and regressions are saved. The owner-approved final retry passes all
**194 focused JVM tests**, with zero failures/errors/skips, and builds both app
and Android-test APKs. Focused source review has no remaining actionable P1/P2
finding. Tracked-file whitespace checks pass; new focus sources/tests have no
trailing whitespace. Native replay/pilot fixtures compile but were not executed
on a phone. No install, camera, collection, staging or commit ran.
Evidence: `output/repeated-focus-check-01/`, `output/repeated-focus-check-02/`,
and `output/repeated-focus-check-03/summary.json` (copied XML and APK/source hashes).

The user selected the count/window for a pilot and requested fewer strengths.
The 500 ms qualification, two-strength mapping, three-second hold/seven-second
fade and one-second display grace are engineering settings,
not published attention thresholds or validated difficulty probabilities. The
[primary evidence review](focus-window-improvement-review.md) retains aid-form
findings, populations and transfer limits. Software passing does not establish
human attention, comprehension or benefit on NewsMead phones.

# NewsMead — Progress Notes

## 2026-10-15 - Softer Dark surroundings onset

Owner corrects the rationale: avoid an overly strong immediate appearance change.
Dark opacity reduced90%/98%→40%/60% at50%/100% initial action strength, with a
400ms smooth onset. Fade style, triggers, action strength, opening and scroll
position remain unchanged. Selector metadata V3 records per-style opacities and
onset; policy14/conditionV9 retained. Opacity-only13 JVM checks/builds pass. Final
build caught a removed still-required flag; restored/source-reviewed. Owner-approved
retry passes15 JVM checks, both APK builds/data-preserving A56 installs and2 native
checks in9.957s. Covers alpha0/51/102 at0/200/400ms, continuity/reset/hold and actual
Dark/Fade Replay rendering with display awake. Evidence:
`output/focus-dark-opacity-check-03/summary.json`; failed02 build retained.
No camera/calibration/staging/commit or research-validity claim.

## 2026-10-14 - Selectable focus appearance

Owner requests a dark surround option in the aids menu. Add exclusive Fade
surrounding text (default, background color) and Dark surroundings (black), with
the same90%/98% opacity, clear area, motion and activation behavior. Session-only
choice follows existing settings scope. It is explicit on focus actions and
preferences/export records, including held scrolling style. Article/Pilot/Replay
and article/Replay demo are wired. Policy14/conditionV9; form remains V4.
18 focused JVM checks and both APK builds/installs pass.5/7 native initially pass;
the existing dismiss test asserted before its posted callback, and Replay had no
draws with the device found dozing afterward. Reviewed test-only callback wait,
owner-approved rebuild/install and awake/unlocked-display retry pass both checks
in10.728s. All7 distinct native checks pass across both runs. App APK unchanged
on retry; data/calibration preserved. Evidence:
`output/focus-style-menu-check-02/summary.json`; initial failure/logs/XML retained.
No camera, frozen measurement protocol, staging or commit change.

## 2026-10-14 - Focus contrast, smooth motion and scroll continuity

Owner requests faint competing text, smoother movement and persistence while
scrolling. V4 paints the article background at90%/98% coverage at50%/100% initial
strength, with180ms presentation motion and a viewport-pinned scroll window
that expires<=1500ms after the last actual translation unless fresh support
takes over. Preserve5/4-row/full-width geometry. Detector resets/settling stay;
held display logs original source/current geometry separately, never new gaze.
Policy13/conditionV8 distinguish this revision in article/Pilot/Replay/demo.
Independent review caught and corrected row snapping and repeated-disable
invalidation. Correction review reports no actionable finding.50 focused JVM
checks pass; both APKs build/install on A56 preserving data/calibration.5/6 native
initially pass; a detached-parent fixture did not translate reported text origin.
Reviewed test-only correction, owner-approved test rebuild/install and one retry
pass in0.707s. All6 distinct native checks now pass across both runs. No camera,
staging/commit. Evidence: `output/focus-display-scroll-check-02/summary.json`;
initial failure/logs/XML retained. Live attached scrolling/benefit still need use.

## 2026-10-14 - Four/five-line focus and full-width shade

Owner requests a wider clear area and removal of side gutters. V3 prefers5 rows,
uses4 only when5 leaves no surrounding shade, and never shrinks to3 for contrast.
The band slides near visible/article edges; veil x bounds use the full visible
overlay while gaze/text geometry retains its original bounds. Article/demo,
Pilot and Replay select radius2; policy12/conditionV7. Demo availability uses the
same resolver after source review caught a false short-viewport preview.
14 focused JVM checks and app/test builds pass. Data-preserving A56 installs
succeed;3/4 native checks initially pass (actual edge pixels/five clear rows and
both demo cases). Failed Replay trace proves the test ran default navigation,
not requested focus input. Selector waits for initial layout and visible target
selection; returned scenario IDs are asserted. Correction review passes. Owner
approved test APK rebuild/reinstall and one native retry; it passes in10.002s.
All4 distinct checks pass across the two runs, with failed evidence retained.
No activation threshold, filter/calibration, dictionary timing, frozen protocol,
natural gaze/camera, staging or commit change. Evidence/APK/source hashes:
`output/focus-window-width-check-02/summary.json` (initial gate/failure in folder01).

## 2026-10-14 - Focus return presence, ongoing visits and visible context

Owner authorizes the activation redesign after both live routes remain unreliable.
Returns now count after100ms/3 fresh selected-coordinate article-presence samples,
even during moving place search.3/30s/500ms and50/100% remain. Completed summaries
survive same-article geometry/definition/control pauses while pending sequences
and anchors clear. Four supported ABAB visits, including the ongoing fourth,
use a separate8s aid window; finalized P11 retains2s and descriptive identity.
V2 focus prefers3 clear lines, removes neighbors above60% of viewport height,
and withholds zero-area/zero-alpha success. Shared policy11/conditionV6; article,
pilot, replay, demo, diagnostics and contract synchronized. Independent review
caught expired anchors remaining visible during fresh unmapped search; corrected
with integration regression and passing correction review. All219 focused JVM
tests pass, zero failures/errors/skips; both APKs build. Both data-preserving A56
updates succeed;5 camera-free Android tests pass in31.501s, including real bitmap
shade, moving returns, ongoing fourth visits, count strengths, consultation
exclusion and geometry/fresh-anchor behavior. No natural camera/gaze collection,
staging or commit. Trial settings are not validated attention thresholds or
benefit. Evidence/XML/APK/source hashes: `output/focus-return-activation-check-01/summary.json`.

## 2026-10-14 - Focus visibility hold and longer definition trigger

Owner reports successful live focus activation followed by immediate removal,
then authorizes brief-gap/line-transition stability and a stricter definition
trigger. Focus now holds its initial50/100% for3s of usable captured reading and
fades over7s; its last confirmed line/action can remain through<=1s delivery-age
grace. Tick/duplicate callbacks cannot refresh support. Operational invalidation,
longer unsupported intervals, off/zero and definition priority still clear it.
Grace display does not create samples/fixations, interruption counts or reading
time. Default definitions require1500ms observed current-word residence, versus
the former800ms. Shared article/pilot/replay, phase provenance and fixtures use
policy10/conditionV5. Source review reports no actionable finding. All **207
focused JVM tests pass** and both APKs build. First gate passed205/207; the longer
Replay dwell was shortened to1750ms so it exceeds the1500ms definition setting
without exceeding the legacy comparison's2s cycle window. The owner approved
the passing retry. Existing A56 aid-update/camera-free authorization covers this
continuing correction; both data-preserving APK updates succeeded and **3
camera-free Android tests pass** in 23.743 s. They verify brief-gap focus
retention, the delayed dictionary card, line-cycle priority and excluded
consultation samples. Evidence/XML/APK/source hashes:
`output/focus-stability-definition-delay-check-02/summary.json`.
No camera/staging/commit. Timings are prototype settings; human benefit unvalidated.

## 2026-10-14 - Live focus history/line-support fixes and two strengths

Owner reports normal article guides/bookmark and Replay work, but intentional
live focus triggering does not. For short articles owner requests two or three
strengths. Current focus now starts at50% after3 completed losses within30s and
100% at4+; independent cycles1/2+ use50/100%. Fade remains gradual. Fixed P13
unclassified-fixation gaps clearing completed focus witnesses; detector continuity
still breaks and no cycle is joined through a gap. Focus accepts coarse line-only
support across article width while retaining matching-row/freshness/geometry
guards. Article/pilot/replay/demo are synchronized; policy9/conditionV4.

All **200 focused JVM tests pass**, both APKs build and focused source review has
no actionable finding. One new regression needed three assertion corrections
before its approved passing run; app source stayed unchanged through those
retries. Owner approved data-preserving A56 app/test installs. Both succeed and
**2 camera-free Android tests pass** in50.631s (15 scripted scenario runs),
including third-return50%, fourth-return100%, line-cycle focus and drawing
receipts. Human benefit and the exact cause of the earlier live phone report
remain unverified; no natural gaze was collected. Evidence/XML/APK/source hashes:
`output/focus-live-two-strength-check-04/summary.json`; failed gates retained in
folders01–03. [Contract](repeated-interruption-focus-window.md).
No camera capture, staging or commit.

## 2026-10-14 - Repeated-focus implementation completed after usage interruption

The selected 3-episodes/30-second focus rule, independent supported line-cycle
route, shared article/pilot/replay integration and episode provenance are saved.
The approved second gate built both APKs and passed 188/189 focused JVM tests.
Its failed replay assertion has been corrected to check short word-residence
duration instead of absence of descriptive records. Review fixes also cover
cycle anchoring, expired evidence, reader-capped/manual dose selection and
tracked outside-text time inflating a short unavailable interval. Four new
timing regressions are saved; final source review reports no actionable P1/P2
finding. Owner-approved final retry passes **194 focused JVM tests**, with zero
failures/errors/skips, and both APK assemblies. Focused tracked-file whitespace
checks pass; new focus sources/tests have no trailing whitespace. Native replay
and pilot fixtures compile; phone execution is pending for this build.
Evidence: `output/repeated-focus-check-01/`,
`output/repeated-focus-check-02/` (earlier failed gates),
`output/repeated-focus-check-03/summary.json` (XML and APK/source hashes).
[Current contract](repeated-interruption-focus-window.md).
No install, camera, collection, staging or commit. Human benefit remains untested.

## 2026-10-13 - Bookmark when looking away loses tracking

Owner expects looking away to show the last-line bookmark even when no gaze
coordinates remain. Added a 500 ms delivery-time timer for explicit provider
unavailability. Recovery before the delay cancels pending loss; source-less,
invalid-clock, old/future and operationally excluded input cannot create a cue.
Single stale callbacks with repeated captures can mature through runtime ticks.
Gap departure capture bounds are null; distinct delivery bounds and the actual
saved source capture are retained. No distraction or difficulty label is inferred.
Policy 7, bookmark rule V2, smoothed condition suffix V2; visuals, calibration,
filter and original jump/outside rules stay intact. Replay adds
`bookmark_tracking_loss` and native timer/trace regressions compile in the gate.
Focused independent source review has no actionable P1/P2 finding. All **173
focused JVM tests pass**, app/test APKs build and whitespace passes. Native
runtime-timer/replay fixtures compile but phone execution remains pending.
Evidence/XML/APK hashes: `output/bookmark-tracking-loss-check-01/summary.json`.
[Contract](scanpath-reading-bookmark.md). No device install/camera/collection,
staging or commit.

## 2026-10-13 - One calibrated-smoothed reading path

Owner authorized the dot, live scanpaths and aid decisions to share the exact
existing filtered provider point. Article/pilot now select `article_smoothed_v1`
for mapping, candidate grouping/centroids, policy anchors, bookmark departures
and pilot scoring. True calibrated raw coordinates remain separate; no raw
fallback or second filter. Calibration, One Euro 0.7/0.002, detector settings and
aid timings are retained. New condition IDs, policy 6 and schema-2 provenance
separate this route from earlier raw results. Replay supplies explicitly
post-filter synthetic points with absent raw originals; frozen accuracy and raw
diagnostic protocols retain their identities.

Independent source review found an obsolete replay version assertion and an
incomplete provider-point audit check; both corrected with regression coverage.
All **166 focused JVM tests and 10 host checks pass**; app/test APKs build.
The first host invocation failed before tests on its sibling import path; the
owner approved the successful retry. Native fixtures compile but phone execution
remains unperformed for this build. Evidence/XML/APK hashes:
`output/smoothed-scanpath-check-01/summary.json`.
[Stream contract](calibrated-smoothed-scanpath-path.md). No install, camera,
participant collection, staging or commit. These checks establish software
consistency, not new accuracy or reading-benefit evidence.

## 2026-10-13 - Exact online dictionary fallback; gaze dot clarified

Owner says popups open without definitions and questions whether the dot's size
limits matching. Source confirms dot radius 18 screen px is visual; scanpath
mapping uses raw point/layout, without padded word radius. Dot smoothing and
I-DT dispersion are separate. No matching radius/detector/protocol changed.
Added one exact-word Datamuse fallback after primary dictionary failure; results
must match original normalized spelling, retain attribution and two-sense bounds.
Reader controls, trigger timing, raw data and bundled-only synthetic conditions
remain. Provider/adapter identities are recorded. Source review has no finding;
125 focused JVM checks and APK assemblies pass. Data-preserving A56 updates
installed. Nine phone checks pass, plus one targeted analyst native-popup check:
live Datamuse lookup filled meaning/source and Close worked. Ten distinct cases
across two runs, with analyst latency 794 ms and 3,467 ms. Evidence/hashes:
`output/dictionary-article-check-03/summary.json`. Exact natural article state
was not inspected successfully. No camera/gaze, participant data, staging or
commit. Datamuse's announced February 2027 key requirement is documented in
[dictionary contract](article-definition-aid.md).

## 2026-10-13 - Fresh dictionary requests verified on A56

Owner authorized the ADB retry, data-preserving installs and camera-free check.
Added an optional HTTP-cache bypass and public-vocabulary fixture so the check
cannot pass on memory/HTTP cache alone. A56/API 36 Internet permission is granted;
installed app hash matches the build. Keyboard live definition arrived in 408 ms,
populated the native popup and closed; unbundled hello live in 83 ms; analyst
glossary fallback in 79 ms. All nine phone checks pass in 0.792 s, plus 19 focused
JVM checks and both APK assemblies. Default reading cache remains enabled.
Evidence/hashes: `output/dictionary-connection-check-02/summary.json`. Connectivity
is verified for these requests; full dictionary coverage and natural gaze-triggered
article behavior are not. No camera, gaze, participant data, staging or commit.

## 2026-10-13 - Fixed Aids entry and dictionary failure feedback

Owner reports running the menu build successfully, then requests fixed bottom
access. Removed article-tap/scroll visibility transitions; menu capture gates
and screen-coordinate exclusion remain. Raw diagnostics disable the entry and
retain the frozen recording route. Owner's normal-article **analyst** lookup
showed the blanket connection message. One host request returned HTTP 404 in
406 ms; phone cause and response payload are not verified. Added precise failure
feedback and an original analyst glossary entry while preserving live-first
lookup, retryability, cache and consultation safeguards. Sources/limits:
[dictionary amendment](article-definition-aid.md). Review's diagnostic-dot finding
is corrected and source-confirmed. Initial test-only nullable-property compile
error was fixed; owner-approved retry passes 119 focused JVM checks, both APK
assemblies and whitespace check. Evidence:
`output/fixed-aids-dictionary-check-01/summary.json`. No device install, camera,
participant validation, staging or commit; actual phone behavior remains untested.

## 2026-10-13 - Reading aids menu and sentence-context prototype

Owner approved menu plus sentence guidance and selected tap-to-reveal bottom
access. Shared controls support independent aid switches and exclusive line or
sentence context. Coarse supported lines select text units; same-line ambiguity
uses containing paragraph or abstains, without exact-word dependence. Gray guides
draw behind letters at 7.843% default opacity; no entry pulse. Current definition
profile V4 / policy 5 records separate guide ranges and reader choices. Pilot and
replay reuse controls while preserving prompted/shadow/no-aid behavior. Source
review findings about options suspension and screen coordinates were resolved.
Initial sandbox cache denial and stale demo-strength assertion were retained;
owner approved both retries. Corrected gate passes all 114 focused JVM checks,
both APK assemblies and whitespace check. [Contract](reading-aid-menu-and-sentence-guide.md)
and `output/guide-menu-check-01/summary.json` retain logs, failed/corrected XML and
hashes. Android fixtures compile; actual article gestures/insets/lifecycle remain
untested on phone. No install, camera, human validation, staging or commit ran.

## 2026-10-13 - Last recorded reading-line bookmark

Owner selected both outside-article departure and non-normal line-jump triggers
for a bookmark at the line left behind. Current article, demo, pilot and replay
use definition V3 / policy 4. The fixed gutter cue does not predict a successor
or diagnose lost attention. Usable outside coordinates are required for the
look-away route; tracking loss alone preserves memory silently. Separate source
and comparison memory prevents jumps/clearing across unknown gaps. Both action
writers retain source and departure evidence; legacy V2 identities remain intact.
[Contract and primary evidence](scanpath-reading-bookmark.md) record EB15/CW12's
known-interruption resumption findings and the unvalidated phone trigger/form.

Focused review findings were corrected. Initial nullable-geometry compilation
failure was repaired; owner-approved retry passes 94 JVM checks and both APK
assemblies. Data-preserving app/test updates installed on SM-A566B/API 36.
Camera-free phone checks passed 30/32 initially; two new assertions used the
wrong action JSON field. Corrected, owner-approved two-test retry passes in
28.861 s. All 32 distinct cases are covered across the runs. Evidence, initial
failures and hashes: `output/reading-bookmark-check-01/summary.json`.
No camera/live gaze, calibration, private recording, human reading validation,
staging or commit ran.

## 2026-10-08 - A56 live dictionary check 06

Owner authorized a quick camera-free device check. Added an explicitly opt-in
public-word API/native-popup smoke fixture. Its initial test-only cleanup
compilation error was corrected; owner approved rebuild/retry. App/test APKs
installed preserving data on SM-A566B/API 36; all 15 focused checks pass in
0.835 s. Real dictionary `keyboard` result arrived in 443 ms with live-source
attribution, populated the card and closed correctly. Offline, popup lifecycle,
synthetic consultation and article-presenter checks also pass. Evidence/hashes
and retained initial error: `output/dictionary-device-check-06/summary.json`.
No camera/live gaze, human reading, private-recording access, staging or commit
ran; the visible live-article gaze trigger still needs a manual reading check.

## 2026-10-08 - Online word definitions

Owner requested live dictionary help. Changed lookup to successful online cache
→ Free Dictionary API → bundled fallback; synthetic replay/pilot stay offline.
Live pilot records `online_then_bundled`. Only live successes enter memory, and
cancelled/duplicate responses cannot deliver or populate it. One public host
probe returned HTTP 200 and keyboard definitions/attribution in 395 ms. All 26
focused JVM checks pass; both APKs assemble fresh, and source review has no
finding. No phone install/tests or live gaze ran. Evidence and phone limits:
`output/dictionary-online-check-01/summary.json`, [article aid](article-definition-aid.md).

## 2026-10-08 - A56 merged-build check and replay correction

Owner-authorized data-preserving installation succeeded on SM-A566B/API 36.
One camera-free 38-test gate passed 33 and failed 5 in 89.668 s. Three popup
fixtures have compiled constructor ABI mismatch; two continuation replay checks
reject a short destination row unnecessarily. Locally corrected generator
landing/arrival selection with source progress/visibility/timing retained and
three JVM regressions. Source review found no issue. The first fresh-build
invocation stopped before compilation on PowerShell argument parsing. After
owner authorization, a fresh nonincremental build passes 83 JVM checks, both
APKs reinstall preserving data, and all 38 A56 checks pass in 94.573 s. Rebuilt
popup bytecode matches the current constructor. Home launch returned success
but its captured hierarchy showed the launcher and no app nodes, leaving actual
feed display/publisher extraction unverified; no retained app crash was found.
[Device check 05](scanpath-device-check-05.md) retains initial failures, corrected
hashes, scope and evidence. No live camera/gaze, calibration, private export,
staging or commit ran.

## 2026-10-08 - Recommendation API integration

The owner authorized merging the latest fetched `Recommendation` (`501e9be`,
11 incoming commits) into the current scanpath branch and configuring NewsData.
The key lives in ignored `local.properties`; online feed mode is enabled. Ten
conflicts are resolved while retaining article aids, demo, pilot/replay and
native/frozen settings. The home screen combines selectors with study entries.
Focused review led to article readiness, callback, usage-period and calibration
restart repairs. One computer-side API request returned 10 articles. All 80
focused JVM checks pass, including current continuation cases; both APKs assemble
and the generated API key matches the ignored property. Android fixtures compile;
phone behavior and publisher extraction remain untested.
[Integration record](recommendation-merge.md) retains configuration, findings and
evidence paths. No device operation, calibration, private export or push ran.

## 2026-10-04 - Expected-continuation marker

The owner approved replacing the successful-P09 landing cue. Supported
first-quarter then last-quarter progress remembers the next fully visible LTR
line. A supported landing elsewhere offers a fixed 6 dp blue marker at that
expected line; normal next-line arrival produces none. The cue clears on
supported arrival, distinct continued reading, expiry or tracking/layout
boundaries. Positive strength settings do not attenuate it. Deliberate rereading
and skipping are not labeled errors or difficulty.

Article/demo/pilot/replay and trace metadata now share this rule under definition
profile V2 and policy v3. Beginning/end/landing support is exported separately;
P01–P13 and frozen protocols retain their identities. Raw-coordinate replay adds
backward-return and forward-skip examples. [Exact rule, flowchart and study
limits](scanpath-continuation-marker.md) preserve the implementation decision.
Focused independent review found and resolved left-censored prefix loss,
row-wobble clearing and departed-finalization issues; final fixture corrections
have no remaining actionable review finding.

Both APKs assemble; Android fixtures compile but have not run on a phone.
The first JVM gate stopped at a new test's incorrect `lineAgreement` field;
`lineSampleAgreement` is now used. Rerun approval is pending under AGENTS.md's
one-failed-attempt boundary. Resource XML and scoped whitespace checks pass.
Evidence/hashes: `output/continuation-marker-check-01/summary.json`.
No device install/capture, staging, commit or participant validation ran.

## 2026-10-04 - Pilot/replay match current contextual aids

The owner selected full aid alignment. Both evaluators now use the article's
definition profile, same popup/highlight/close/gutter rendering and no reserved
magnifier strip. New condition IDs and UI/dictionary metadata distinguish them
from historical magnifier results. Live pilot keeps bundled/online lookup;
synthetic pilot/replay use bundled-only lookup. Consultation boundaries retain
raw callbacks and exclude popup/late captures from article patterns. Prompted
tasks keep aids off, and no-aid/shadow/display viewports match.

Replay uses the shared six-stage presenter with detached runtime callbacks.
Detector-only expectations remain independently checked; current aid checks
account for consultation interrupting later evidence. Independent source review
found no P1/P2 issue. Direct line-cycle and replay-to-demo regressions address its
coverage limits. All 64 focused JVM tests pass; app/test APK assembly, XML parsing
and whitespace checks pass. Updated Android fixtures compile; phone execution
remains pending. Evidence/hashes: `output/evaluator-current-aids-check-01/summary.json`.
No device install/capture, staging, commit or protocol edit ran.

## 2026-10-04 - Article gutter for the line-start marker

The owner's screenshot showed the marker covering the first letter. Added 16 dp
start padding to the article body, present in every aid stage. Live/demo article
overlays place the marker in that gutter with a 4 dp gap before the text and
withhold it when the gutter is clipped. Source anchors use the existing
padding-aware TextView geometry; the narrower column can change wrapping.
A camera-free rendering fixture compiles to check actual marker pixels clear the
text. App/test APK assembly, article XML parsing and whitespace checks pass;
the new fixture has not run on a phone and visual inspection is pending.
No device install/capture, staging or commit ran.

## 2026-10-04 - Internet popover template and light offline glossary

The definition card now adapts the official Flowbite React Default Popover in
native Android, using NewsMead theme colors/fonts. It preserves the highlighted
source word and compact larger close control. A 75-entry original English glossary
returns immediately with explicit offline attribution; a lazy network queue is
created only for uncovered words. The article demo prefers a covered actual source
word without changing article text or UTF-16 geometry. Unsupported languages still
abstain live. [Source, coverage and checks](article-definition-aid.md) record this
amendment. No device install/capture, staging, commit or backend change ran.
All 64 focused JVM tests pass, both APKs assemble and popup XML parsing/whitespace
checks pass. Offline delivery and covered-word/fallback Android fixtures compile;
their device execution and the new card's phone appearance remain unverified.

## 2026-10-03 - Article-native demo and definition prototype

The owner's correction replaces the top magnifier in ordinary articles with a
near-word dictionary card and adds an explicit demo in the actual loaded article.
[Implementation/evidence record](article-definition-aid.md) documents H03/M25,
the exploratory 800 ms trigger, provider contract and consultation boundaries.
Fetched Recommendation at `501e9be` for inspection only; it has news/recommendation
and logging work, with no dictionary endpoint found. The prototype uses a
replaceable English dictionary API, bounded cache and honest unavailable states.

Review found and fixed callback reentrancy when finalized word evidence opens
help. Raw consultation callbacks remain available with capture-time exclusions;
engine continuity breaks on opening/closing. Source layout is unchanged. Demo
stops the provider and never enters the live journal. Legacy pilot/replay and
frozen comparison identities are retained. The first compile's nullable receiver
error was corrected; 61 focused JVM tests and both APK assemblies pass. New
camera-free Android fixtures compile, including the finalized-P12 regression;
device execution, API service availability, natural word assignment and human
benefit remain unmeasured. No device operation, staging, commit or backend merge.

## 2026-10-03 - Corrected A56 retest passes all 15 checks

The owner approved corrected app/test installation and a bounded camera-free
retest. Both APKs installed with data-preserving updates; **OK (15 tests)** in
50.908 s on SM-A566B/API 36. All four demo, the centered magnifier regression,
all five replay and all five pilot checks pass. All five synthetic replay
scenarios complete with expected patterns/aid draw proxies and strict causal
time/evidence ordering; 265 scheduled samples have callback lag 0.013–2.879 ms.
This is synthetic delivery timing, not gaze/camera or eye-to-cue latency.

Inspected app-root demo word/focus and detector-driven replay word drawings:
readable labels/text, full words fit, context-preserving veil. Demo preparation
diagnostics corroborate the old gravity-including padding defect: 191 px strip,
28 px compound versus 72 px total vertical padding per edge. Initial failures remain
in `scanpath-device-check-04.md` alongside corrected hashes/results and local
evidence paths. Software inspection is ready on A56; this corrected build has
not been checked on S21. No camera, calibration, human gaze/benefit measurement,
staging or commit occurred.

## 2026-10-03 - A56 compatibility attempt and local corrections

After explicit owner approval, both current APKs installed with data-preserving
updates on SM-A566B/API 36. One camera-free synthetic run finished: 6 of 14 pass,
8 fail. All five pilot tests pass, including all ten prompted task plans and
shadow/display viewport/rendering checks. Four demo tests fail readiness; four
replay tests stop on fractional negative callback lag mislabeled late. Captured
known synthetic replay traces and an app-only pilot drawing; no human files or
camera collection. `scanpath-device-check-04.md` retains hashes, results and limits.

Locally fixed early callbacks to retry the same deadline/ordinal before input,
preserving actual delivery and the 100 ms lateness guard. Source review found the
demo/word renderer used gravity-including total padding to estimate capacity;
switched vertical checks to compound padding. The exact original demo exception
was not retained, so device confirmation remains needed. Added timing and
centered low/high-fit/oversize regressions plus app-only diagnostic/previews.
Independent review closed; all 56 focused JVM tests and app/test assembly pass
using Java 17 and offline user cache. One helper build failed before tests on its
Android home path; its daemon finished before the successful lead build.
At that local checkpoint, no corrected APK had been installed: AGENTS.md required
explicit authorization after the first failure. The successful authorized retest
is recorded above. No staging/commit occurred.

## 2026-10-03 - Trigger evidence review and explicit aid demo

Rechecked the actual aid predicates and primary findings for panel presentation.
Added `scanpath-trigger-evidence-review.md` with a decision flowchart, source
locations, transfer limits, the changed GazePrompt word predicate and unsupported
focus/strength claims. No live policy threshold or frozen protocol was changed.

Added an explicit render-only Demo cycle to Scanpath replay: seven four-second
stages show baseline, line highlight, start marker and low/high word/focus forms.
Previous/Next, Stop, pause and geometry changes cancel presentation. No detector,
provider/calibration, runtime, trace or replay result is created; status explicitly
identifies manufactured button/timer activation. Required renderer timing fields
use presentation elapsed time and are excluded from measurement outputs.

Independent review found that replay's strip could not fit the maximum Arial
magnifier; reduced vertical padding to 10 dp in replay and pilot, preserving 68 dp
reservation and giving 48 dp content height. New demo tests assert both word
strengths display text. All 53 focused JVM tests pass; both APKs assemble; four
camera-free demo activity tests compile. Installation/device checks await task-
specific authorization. No camera/human collection, staging or commit occurred.

## 2026-10-03 - Separate live scanpath pilot and raw recording

Based on `50ff2b7`, built ten prompted word/line placement/order tasks with actual
visible native geometry and continuous existing engine processing. Added
hidden-dot ordinary reading with no-aid/shadow/display conditions and a constant
68 dp reserved strip. The front camera starts only on explicit Start live with
compatible saved calibration. Prompted geometry changes and activity pause stop
the run; shutdown retains trailing callbacks/counters without post-stop mapping.

Shared the typed full-input trace writer with replay under separate instrument
identities. Added exact engine-acceptance callbacks, capture-window prompted
agreement, explicit unknown/missing denominators, unshown targets, descriptive
path segments/alignment and adherence reporting before scores. Independent
review found counter contamination and scoring interpretation risks; fixed
reset-before-setup, unavailable word references, continuity metadata and separation
of descriptive alignment from drawn capture-window opportunities. Later cue
settling does not erase transitions from descriptive raw paths. The repeated-word
cue task remains a negative control, never refixation truth.

All 51 focused JVM tests pass (17 new task/scorer cases); 8 host summary contract
tests pass; app/test APKs assemble. Five new camera-free activity integration
tests compile. New pilot device execution awaits task-specific authorization.
No camera capture, calibration, participant data, staging or commit was performed.
Usage, time/denominator/provenance rules, host tool and remaining study procedures:
`scanpath-live-pilot.md`. Software completion does not establish natural scanpath
truth, difficulty classification or aid benefit.

## 2026-10-02 - Interactive raw-coordinate scanpath replay

Built the pilot screen's first replay milestone. Five explicit raw-coordinate
scripts use the actual visible Android text layout, existing session/engine,
experimental aid policy and real line/start/word/focus views. Home and article
diagnostics provide entry points. Reader controls, Stop, scroll/geometry changes
and pause clear aids and stop input. No provider/camera or calibration is opened.

Full private synthetic JSONL records preserve planned/callback times, geometry,
raw assignments/exclusions, finalized/prefix evidence, patterns, decisions,
anchors, variable strengths and draw proxies. Partial files retain failure
status and a reserved footer; final names appear after successful close.
Independent review prompted pause stopping and drawn-proxy requirements for the
software-success flag. Frozen protocols and existing logger identities stay intact.

All 34 focused JVM tests and both APK assemblies pass. First device attempt on
SM-G991B/API 35 had 4 passes and 1 test-ID lookup failure: the test omitted valid
finalized-fixation support IDs. Corrected only that test, then the owner authorized
a retest: all 5 passed. Visual QA found clipped Back/Controls labels; corrected
their sizing/padding and repeated the focused checks on the final target:
all 5 passed. Synthetic preview is readable; traces show word strength 0.25–1
and optional focus strength 0.625–0.8125. Hashes, attempts and local evidence paths:
`scanpath-device-check-03.md`; usage/time/trace contract: `scanpath-replay-screen.md`.

These are software outputs, not difficulty probabilities, natural gaze accuracy
or aid-benefit results. Replay checks ran only on this phone. Next implement
live prompted placement/continuous-order tasks and matched human recording.
No staging or commit was performed.

## 2026-10-01 - Same synthetic tests pass on the second phone

At the owner's request, installed the same target/corrected test APKs on the
connected SM-G991B/API 35 with `install -r`. All four geometry/magnifier tests
passed on the first attempt. No code change or rebuild was needed. Source
checkpoint `9559c95`, matching APK hashes and runner results are retained in
`scanpath-device-check-02.md`. The same four tests now pass on two phones; this
does not validate visible article layout, live gaze or reading benefit. No camera,
calibration or reading collection was started. Further tests remain as specified
in the runtime guide and pilot.

## 2026-10-01 - First Android device checks and corrected retest

Installed the checkpoint debug app on the owner's connected SM-A566B/API 36
with a data-preserving update. Four synthetic geometry/magnifier tests initially
had one pass and three missing-layout-parameter failures in detached fixtures.
Added explicit parent-style layout parameters in the two test files; no app,
detector, policy, calibration or frozen protocol changed. Corrected test assembly
passed, then the owner authorized `retest`: all four device tests passed.
`scanpath-device-check-01.md` preserves failures, successful test names, APK
hashes and limits. No provider/camera or participant recording was started.
Visible article aid behavior, natural gaze validity and benefit remain pending.

## 2026-10-01 - Integrated independent scanpath reading aids

Connected live word/line scanpath evidence to an independent policy, current-line
highlight, reactive destination-line marker, reserved localized magnifier and
optional context-preserving focus region. Word/focus strength varies with observed
residence under `EXPERIMENTAL_PROFILE_V1`; current-line/start cues have baseline
prominence. Reader controls cap strength, select fixed strength and enable each
form. Normal article reading bypasses the old weighted ordinal controller/demo.
Touch validation and frozen logger/protocol identities are retained.

Added separate established ongoing-prefix evidence: it adds observed duration,
not finalized refixations. Invalid/late input and lifecycle/geometry changes clear
stale aids. The magnifier reserves 68 dp while enabled, retaining the paragraph;
oversized words are suppressed. A4 requires opt-in plus cycle, residence and
configured slow progression, with all three supporting event IDs retained.

The bounded journal saves numeric trigger facts, settings/source IDs, decisions
and drawing proxies only through an explicit private export. Partial exports
report dropped records/missing evidence; drawing is not verified physical
visibility. Independent review prompted a 512-finalized-candidate history cap
with explicit breaks and preserved timestamp order, and numeric action support.

Focused JVM policy/pipeline/engine checks, 183 synthetic reference prefixes,
52 Python tests, fixture consistency, debug assembly and Android test compilation
pass. Android geometry/drawing tests are not device-executed. Review also checked
adaptive saturation boundary semantics. No install, camera capture, calibration,
participant collection, staging or commit was performed. Exact runtime settings,
forms, controls and remaining evidence limits are saved in `scanpath-aid-runtime.md`.
Next build the matched raw evaluation instrument; device/human validation follows
the completed prototype and task-specific authorization.

## 2026-10-01 - Live raw word/line scanpath engine

Implemented `gaze/scanpath/` typed models, incremental provisional I-DT extraction
and P01–P13 rules, then connected the article's raw MGazeNet observation callback.
The main-thread adapter uses physical screen bounds, UTF-16 word ranges, cached
Layout/bidi geometry, capture-time settling and separate word/line ambiguity.
Pause/scroll/layout/provider/text boundaries reset continuity. No new aid decision
or recording is created; legacy display/controller and frozen protocols remain.

Checks: eight focused JVM tests plus 13 synthetic cases/**183 raw-action prefixes**
match common Python fixation fields and P01–P12; 52 Python reference tests pass.
Live P13 is deliberately immediate and separately reset-tested, whereas offline
batch breaks may use a later fixation. `:app:assembleDebug` and
`:app:compileDebugAndroidTestKotlin` pass using installed Java 17 and the
in-process Kotlin compiler (existing Java 21/Kapt daemon incompatibility).
Two Android geometry tests are compiled only. Independent review found repeated
line/bidi scans and retained scroll snapshots; caching and post-flush geometry
eviction fix these findings, with closure review and a geometry regression.

Default long/slow/sweep endpoint settings remain unset. The pending sample buffer
has a 4,096-sample guard; callback-only delivery avoids retaining quadratic P12
event support lists. Fixation history is linear and full P12 support generation
still costs prefix-length work. Pattern updates wait for candidate finalization;
ongoing residence evidence needs an explicit contract for within-fixation cues.
See `scanpath-detectors.md` for code, timing, reproduction and limits. Next build
the independent aid policy, adjustable renderers/controls and action logging.
No install, camera capture, calibration, participant collection, staging or
commit was performed. Software parity does not establish reading validity.

## 2026-09-30 - Independent reading aids and source-linked mappings

The owner clarified that the old scaffold levels do not constrain the scanpath
design. Added `scanpath-adaptive-aid-design.md`: independent aid types,
within-type strength, complete P01–P13 action relationships, exact evidence
limits/source locations and migration from the weighted ordinal ladder.
Primary-source checks cover GazePrompt, line-start salience, digital rulers,
word-progress coloring and preservation of rereading. Digital ruler comparisons
distinguish a significant Grey Bar/no-ruler result from descriptive mean gains.
Focused review caught reuse of A4 for different forms; kept A4's focus meaning
and assigned ruler/coloring variants separate versioned form IDs and condition
records. Pilot revision 6 preserves build-before-participant-evaluation order.
This updates the specification and evidence notes; Android migration is pending.

## 2026-09-30 - Prototype completion before participant measurement

At the owner's correction, pilot revision 5 moves live scanpath extraction,
P01–P13 classification, the pattern-to-aid policy, adjustable A0–A4 displays,
variable strength, reader controls and action/render logging ahead of participant
measurement/evaluation. Complete the integrated Android build, software replay
checks and matched evaluation instrument before running the participant tasks.
Existing gaze evidence carries forward; experimental trigger/strength rules are
implemented for later evaluation without claiming validated difficulty or benefit.
Updated the proposal, detector guide, context and documentation index to retain
this order. This changes the saved plan; Android implementation remains pending.

## 2026-09-30 - Named offline scanpath detectors implemented

Added `tools/gaze/scanpath_patterns.py` classifier v1 and integrated P01–P13
outputs into analyzer v5. Outputs cover word/line movements, geometric return
sweeps, revisit/frontier episodes, A–B–A–B cycles, observed residence/frontier
progression and explicit breaks. Sweep endpoints and verified LTR direction are
required for positive P09 matches; long/slow flags have no default human cutoffs.
Added unique geometry snapshots, supporting-evidence availability/provenance,
event IDs, configuration hash, per-pattern statuses and matched-vs-evaluated
counts. Original raw-stream extraction/coverage behavior and logger schema remain.

Focused checks: **39 extraction/integration tests + 13 classifier tests = 52
passed**, including synthetic raw-record and CLI configuration/output tests.
Independent review identified P10 backward-phase/provenance errors, unrecorded
direction assumptions and below-threshold censored residence labels; corrected
them and added regressions. The closure review's remaining evidence-index-order
issue was fixed with sorted unique support and a frontier-before-revisit fixture.
See `docs/scanpath-detectors.md` for the exact contract and transfer limits.

This implements offline named descriptors, not Android live detection, a
difficulty model or the new aid policy. No private recording, device/build/install,
calibration, collection, staging or commit was performed.

## 2026-09-30 - Pilot revision 4: full implementation and strength evaluation

Reorganized the pilot around six objectives and seven build/evaluation packages.
Added source-linked P01–P13 operational definitions and A0–A4 candidate aid
parameters; specified causal prefix/live parity, separate raw prompted tasks,
shadow decisions, independent support-need reports, controlled aid/strength
trials and frozen varying-strength evaluation. Existing gaze work is baseline
evidence. Expanded the private blank record for per-pattern and action outcomes,
assignment/exposure, missingness and holdout provenance. Counts and numerical
progression criteria remain phase-specific planning inputs before collection.
Named classifiers/live policy remain pending; this revision changes documentation.
Focused independent review identified report-interaction leakage and omitted
evidence-accumulation latency. Added prespecified pre-report windows/lead guards,
interaction/recovery exclusions and first-support/decisive-capture-to-render
timing, with render-proxy and prospective/reactive distinctions.
Targeted review confirmed both corrections. Documentation checks passed for seven
files, 75 local links, registry/phase completeness and table structure; Git diff
whitespace checks passed. No Android build/device collection or new human result.

## 2026-09-30 - Pilot scope corrected to classifier-to-aid implementation

At the owner's clarification, pilot revision 3 now treats completed gaze/model
evaluation as baseline evidence and includes the full scanpath classifier/live
engine/aid policy build checkpoints. Named patterns and their current missing
implementation are listed explicitly. The previous measurement protocol is one
work package, not the full pilot. The stronger research model being requested
from its provider is a pending versioned input; classifier work can continue.
No classifier or Android behavior was implemented by this scope correction.

## 2026-09-30 - Scanpath pilot double-check

The second audit found capture time could reverse after a replay boundary and
detector-rejected timestamps still counted as usable. Analysis v4 fixes both,
adds separate terminal-boundary counts, visit censoring, duration coverage and
coordinate output-age summaries. All 31 focused tests pass; independent review
found no remaining issue in those fixes. Revised the pilot procedure to hold
calibration constant through pre-check/read/post-check, freeze current 22 dp,
and require matched raw placement/continuous-order tasks and a completed study
plan. Added an audit and blank session record. No device/Android change or
private-recording analysis was performed; see `scanpath-pilot-audit.md`.

## 2026-09-30 - Scanpath pilot preparation

Moved the uncommitted measurement checkout from `measurement/RII_2` to
`measurement/scanpath-pilot` at the same commit. Prepared a phone-only word/line
scanpath pilot, with separate instructed placement checks and provisional
free-reading path extraction. The offline replay now reports ordered visits,
signed transitions, explicit breaks and coverage at both resolutions. This is
software preparation only: no device installation, calibration, recording or
independent natural-reading path validation was performed. The v6 known-target
score uses smoothed coordinates while this replay uses raw coordinates; a
separate matched raw target check is needed before placement gating. Focused
replay tests: 24 passed; independent code review found no remaining material
issues in the added path output. See
`scanpath-measurement-pilot.md` for the pilot contract and limits.

## 2026-09-21 - Article-matched structured validation

The interrupted raw-visible protocol-v5 diagnostic sustained about 23.7 FPS,
with zero observed analyzer-busy drops and output age comparable to the prior
hidden-dot run, so it does not support an FPS-drop explanation for the visibly
jumpier dot. An exact offline replay of the article One Euro filter reduced
same-state median frame-to-frame movement from about 45 px to 15 px. Ordinary
structured validation now uses protocol v6/schema 9: the same article-smoothed
coordinates drive the orange dot, mapping, and scoring, while raw source
coordinates remain separately logged. The raw-visible v5 and frozen hidden-dot
v4 identities remain distinct and unchanged.

## 2026-09-20 - Continuous gaze-feedback validation

The completed hidden-dot `font22_run1` diagnostic retained all 1,183 scored
samples at about 23.8 FPS but reached only 40.2% word-trial exact-line, 17.1%
exact-word, and 0.8% guided exact-line accuracy; its saved evidence is
`diagnostics-local/reading_validation_session_20260920_190328_306.jsonl`.
At the user's request, ordinary structured validation now keeps the orange gaze
dot visible through references and scored trials as a deliberately specified
closed-loop diagnostic condition. This is separately identified as protocol v5/schema 8
and `known_target_visible_feedback_performance`; it must not be pooled with or
reported as unaided hidden-dot accuracy. The frozen matched-comparison path
retains hidden-dot protocol v4 behavior.

## 2026-09-13 - First natural-reading recording audit

Read the 86.47 s recording in memory: complete log, 1,537 observations, but sparse fixation/word coverage and user-reported 1–2-line vertical offset. Fixed offline sequence bridging across long unclassified inter-fixation intervals (analysis v2; 16 tests pass); only four default-setting word transitions remain. No phone changes; use a fresh aligned calibration for the next comparison and see `reading-measurement-run-01.md`.

## 2026-09-13 - Device-test calibration loss

After the connected Android test installation, NewsMead's calibration is missing and firstInstallTime equals lastUpdateTime (2026-09-13 00:08:46), indicating a fresh installation/data reset. No calibration bundle was found in the app, the benchmark's corresponding location, or either project workspace. Do not run Gradle connected tests on the user's working app data without a verified recoverable backup; use preserving updates and direct instrumentation or a separate test installation. The earlier assurance that calibration would be preserved was incorrect.

## 2026-09-13 - Reading measurement pilot

Added explicit article diagnostics recording with the orange gaze dot visible (user request), scaffolds off, raw/display capture timestamps, geometry/gap exclusions, and app-private JSONL. Added the measurement specification and offline I-DT replay for provisional fixation, first-pass, rereading, regression and skipping measurements; no new RII formula is selected. Next: inspect an actual diagnostic reading run and establish word/event reliability before independent-label collection; see `reading-measurement-pilot.md`.
Validation: debug build, 219 gaze JVM tests, 14 replay tests, and two synthetic Android checks passed; the updated debug build was installed on the A56 through the device checks.

## 2026-09-12 - MGazeNet article-output smoothing

Added an article-only One Euro screen-coordinate filter to reduce visible MGazeNet jitter while preserving responsive gaze shifts. Calibration and accuracy instruments remain raw; observations expose both raw and filtered coordinates, and unavailable/stale input resets the filter instead of bridging gaps.

## 2026-09-12 - MGazeNet article pipeline throughput

On the Galaxy A56 article screen, face-present MGazeNet output improved from about 8 FPS to a sustained 24 FPS without changing resolution, model precision, pixel/crop semantics, gaze coordinates, or calibration identity. Promoted the previously validated finite-input guard, moved bitmap conversion/normalization out of Kotlin hot loops, removed landmark/article/UI allocations and redundant redraw/log work, and overlapped serial GPU localization with bounded double-buffered CPU inference. The steady face-present stage averages were 4.5-6.2 ms copy/rotation, 21.9-23.7 ms localization, 1.3-1.4 ms preprocessing, 26.5-29.0 ms MNN, and 0.3-0.4 ms SVR; article rendering remained healthy at 5 ms median and 8 ms P99 in the post-reset sample. Focused MGazeNet/geometry/stability tests and all six native A56 pipeline tests pass; the full 216-test JVM run has one unrelated legacy Firebase-runtime failure.

## 2026-09-03 - Replicated vertical control and offset-only offline screening

At the user's request, retained the original experiment unchanged for two more pairs rather than ending evaluation after one adverse run. The second ON run helped guided reading but hurt word targeting; the third harmed accuracy on its own samples. Then evaluated one fixed median-reference offset rule across all six recordings. It improves some aggregate scores strongly but turns top-left within-one-line accuracy from 100% to 0% in three recordings, so it is not ready for implementation. The line-only replay reconstructs actual layout geometry and reproduces all 10,580 base/effective assignments; all 11 pure analyzer tests pass. Detailed findings and aggregate results are in `gaze-offset-replay.md`. Newer raw logs were read from the phone in memory, not copied into OneDrive. No Android source/build/install or git staging/commit change was made.

## 2026-09-02 - Reading-surface vertical alignment rejected

The protocol-v4 telemetry-OFF pair completed under one fresh calibration and no nine-point correction. ON passed the original three-reference guards, but the same-sample counterfactual showed a harmful trade: raw word exact-line accuracy rose from 13.1% to 35.7%, while guided within-one-line accuracy fell from 74.6% to 29.2%, overall within-one-line fell from 70.5% to 47.8%, and corrected validity fell to 80.2%. One bottom-line checkpoint became entirely invalid. The global session y gain/bias candidate is rejected; do not repeat or tune it from this run. Restore the uncorrected reading path before the next bounded upstream intervention.

## 2026-09-01 - Known-target reading accuracy instrument

Device smoke testing rejected the initial free-reading/adaptive protocol as confusing and unable to establish independent reading ground truth. Replaced it with an accuracy-only instrument: unscored visible-dot preview, eight highlighted A/B word fixations, and six highlighted physical-line reading trials; timestamped numeric-only JSONL remains, while adaptive validation is explicitly deferred. The first accuracy-only v2 device run was also rejected because changing instructions and timing status remained visible below the passage during active targets and competed for the participant's gaze. Protocol v3 presents instructions only in centered between-section dialogs and hides the entire control panel while targets are active. Its first valid run showed only 40.5% guided exact-line and 10.9% exact-word accuracy with vertical center compression. Protocol v4 adds an unvalidated control: OFF and ON collect identical top/middle/bottom live reading-surface references, while only ON may apply a fixed-guard, session-only vertical gain/bias layer; estimator, quadratic mapper, temporal filters, and existing affine persistence are unchanged.

## 2026-08-28 - Five-pair offline tail analysis

Compared the five valid recent calibration/immediate-test pairs from app-private aggregate logs without collecting another run or copying participant artifacts. Mapper conditioning and held-out errors did not rank the later live tails. Top-left was the worst calibration LOO point in all five runs and the worst live point in four, confirming a systematic region/first-target weakness rather than a bad-session selector. High drift was the only directional warning, but low drift still allowed a 3.04-line live tail. The next isolated change is therefore a conservative gate rule: high drift forces one Redo All; a second high-drift result aborts and retains the prior calibration. This is not an accuracy guarantee, and no estimator/mapper/filter/target/order change is authorized by this finding.

## 2026-08-28 - Fresh-session handheld repeatability check

The independent telemetry-OFF calibration/test pair completed correctly with no affine correction: 25.6 FPS, 12 retained test samples median, 1.55-line vertical median, and a 4.87-line top-left maximum. Typical error remains substantially better than E-030, but severe calibration/held-out tails returned (LOO max 6.24 lines; held-out median/max 4.49/7.44), so the current path is improved but not repeatable enough for exact-line claims; no further phone run or code change yet.

## 2026-08-24 - Centered pre-run countdown

Added a centered 3–2–1 countdown before full calibration, calibration redo paths, and every nine-point measurement so capture no longer begins immediately after the setup dialog or gate action. The countdown is outside all target/sample timing and does not change estimator, mapper, filters, order, or per-target behavior; all gaze JVM tests and `assembleDebug` pass, and the build is installed on the A56.

## 2026-08-24 - Detailed telemetry ON/OFF control

Added labelled ON/OFF selection to full calibration and the nine-point check. OFF removes per-frame source/pipeline callbacks and arrays while both modes retain point metrics plus constant-memory FPS summaries; estimator, mapper, filters, target, timings, and order are unchanged. Focused and all gaze JVM tests plus `assembleDebug` pass. Two valid natural-handheld calibration/test pairs per mode showed no OFF FPS or accuracy recovery, so detailed telemetry is closed as the regression cause; the mismatched `off_cal_redo-1` attempt is excluded by its embedded ON mode.

## 2026-07-26 - DEMO_CYCLE scaffold mode for presentation recording

Added `StudyConfig.ScaffoldMode.DEMO_CYCLE` (+ `SCAFFOLD_DEMO_LEVEL_DURATION_MS`
12s, `SCAFFOLD_DEMO_CAPTION`): holds each level in turn (NONE -> WORD -> LINE ->
FOCUS -> REENTRY, looping) while real gaze still drives placement, so all four
render in one continuous take without a rebuild per level. `forcedLevel` is now a
settable `var` and forced runs also track `lastStableLine`, so a forced REENTRY
anchors to the line the reader actually left. **`SCAFFOLD_MODE` is currently set
to DEMO_CYCLE - set it back to ADAPTIVE before any participant-facing session.**
Demo bypasses baseline/confidence/persistence, so it shows the renderers only,
never instability detection. compileDebugKotlin + assembleDebug + all
com.newsmead.gaze.* tests pass (new forced-anchor test).

## 2026-07-24 - Calibration redesign doc (review of external draft)

Reviewed an externally drafted 16-point calibration design and wrote the revised,
codebase-accurate version as docs/calibration-design.md (state machine, feature-unit
rejection/dispersion, drift check, post-fit LOO + validation quality gate with
Accept/Redo, session JSON log; §9 lists all corrections vs. the draft).

Same day - implemented the full redesign. New: `FixationWindowFilter` (lead-in
trim, MAD outlier rejection, dispersion gate + sub-window fallback),
`CalibrationQuality` (LOO residuals, median/P95), `CalibrationSessionLog`
(incremental per-session JSON). Rewrote `GazeCalibrationActivity` (per-point
APPEAR/HOLD/SETTLE/SAMPLE-adaptive/CONFIRM state machine, randomized order +
practice point + drift repeat, 2-attempt exclusion with 12-point floor, audio
cues, 5-point held-out validation pass, Accept/Redo-worst/Redo-all gate with
line-height error bands; **CSV now written only on Accept**) and
`CalibrationView` (light reading-surface background, dp bullseye, appear/confirm
animation, 16-dot mini-map). Verified: `:app:assembleDebug` + all
`com.newsmead.gaze.*` JVM tests pass (JDK 17), incl. new
FixationWindowFilterTest/CalibrationQualityTest. On-device (A56) validation of
the new flow still required; thresholds (dispersion 0.02, gate bands 1.0/1.5
lines, drift 0.5 line) are provisional pending pilot.

Same day - **first on-device run regressed vs. the old design** (worse accuracy,
gate always said redo). Root cause: the 0.02 feature-unit dispersion gate was
BELOW the tracker's own per-frame noise floor (~0.04 horizontal, ~0.05-0.10
vertical), so no SAMPLE window ever passed -> every point timed out, retried,
excluded -> full-calibration abort; the rare sub-window that passed picked
stable-but-off-target moments (worse than plain median). Fix: removed the
dispersion gate AND sub-window selection from `FixationWindowFilter` - kept only
lead-in trim + 2.5x MAD outlier rejection + median (old robust behavior plus a
light trim). Dispersion still computed/logged, not used to reject. Also
re-anchored gate bands to the tracker's DOCUMENTED accuracy (green <=3.0 /
amber <=4.5 line-heights; drift flag at 1.0 line) since the old 1.0/1.5 bands
were below the tracker floor and read red on every normal run. Tests updated;
compileDebugKotlin + all com.newsmead.gaze.* pass. Re-test on A56.

## 2026-07-24 - Calibration button moved to home + drift correction from accuracy test

- Home page: replaced the language-toggle button ("Show me English/Filipino
  news" - inert in offline study mode, all articles English) with "Calibrate
  eye tracker" launching GazeCalibrationActivity. Removed the calibration
  button from the article top bar (accuracy-test button stays, re-anchored
  next to back). Language-toggle logic deleted from HomeFragment; restore from
  git history if Filipino content ever returns.
- Accuracy test now doubles as re-adaptation (James's idea): after a run, an
  affine drift correction (truth ~ A*predicted + b) is fitted from the 9
  (predicted, target) pairs and offered with pre -> est.-post medians. Applied
  post-mapper in LocalCalibratedGazeProvider (16-pt fit + GazeProvider boundary
  untouched), persisted as drift_correction.csv, composed across successive
  runs, cleared automatically when a new full calibration is accepted.
  Deliberately an affine layer, NOT a fresh 9-point polynomial refit (thin fit
  would risk replacing a better map). New DriftCorrection + 6 JVM tests;
  design doc §7.1. assembleDebug + all com.newsmead.gaze.* tests pass.
  On-device drift-correction round-trip (test -> apply -> re-test) pending.
- On-device: drift correction confirmed to improve accuracy (James), but gaze
  hit an "invisible barrier" - would not track above/below a session-varying
  vertical bound (sometimes near top, sometimes mid-screen). Root cause:
  GazeMapper hard-clamped live features to the calibrated min/max range, so
  head-pitch drift beyond the calibrated range froze the mapped output at the
  boundary px - and the active affine drift correction relocated that frozen
  point per session, which is why the wall moved. Fix: replaced the hard clamp
  with gradient (first-order) extrapolation beyond the calibrated range, capped
  at 1.5 standardized units, so out-of-range gaze continues smoothly instead of
  stopping while garbage features still can't fling the estimate to infinity.
  New GazeMapperTest covers inside-range fidelity, barrier removal, and the
  cap. No recalibration needed (fit unchanged, mapping only).

## 2026-07-24 - Barrier persisted after recalibration + fixation-pulse dot

- Barrier still appeared after a fresh calibration. Found the remaining hard
  clamp: the gradient extrapolation in GazeMapper.map() hard-capped the
  out-of-range excess at +/-1.5 z (coerceIn) -> a flat wall once gaze went 1.5 z
  past the calibrated range, which lands mid-screen when a calibration captured
  little vertical feature spread. Replaced the hard cap with a tanh soft-limit
  (widened to 2.5 z): ~linear near the boundary (keeps moving, no wall), smooth
  asymptote far out (still bounds glitch features). Note: polynomialFeatures()
  retains a raw-space clamp but is used only for FITTING (in-range samples), not
  live mapping - harmless.
- Added a raw->mapped diagnostic log (tag `GazeMap`, every 15 samples) in
  LocalCalibratedGazeProvider: shows raw feature, filtered feature, and mapped
  px together. If a wall persists, this distinguishes source-side vertical
  feature saturation (raw fy flatlines) from mapping (raw moves, mapped stops).
  Watch this next on-device: `adb logcat -s GazeMap`.
- Calibration target: inner red dot now "breathes" (scale-only pulse 0.7-1.3x,
  650ms, against the fixed ring) to hold fixation on the centre. Scale-only =
  no translation, so it does not induce smooth pursuit. This intentionally
  relaxes the design doc's "no motion during SAMPLE" for a centred pulse;
  revisit if pilot data shows fixation instability.

## 2026-07-24 - Fixed live gaze not restarting after the accuracy test

- Symptom: after running the accuracy test / applying drift correction from the
  article page, gaze appeared dead until fully exiting and re-entering the
  article - and reading showed no benefit from the correction.
- Root cause: a CameraX teardown race. GazeTestActivity shares the process-wide
  ProcessCameraProvider singleton and calls unbindAll() in ITS onDestroy, which
  runs *after* ArticleFragment.onResume. The article rebound the camera in
  onResume, then the finishing test activity's unbindAll() immediately clobbered
  it. A fresh re-entry worked only because nothing tore down afterward. This
  also explained "no improvement": the article never actually ran with the newly
  applied correction (it reloads the correction in attachLiveGaze, but the
  camera was dead).
- Fix: ArticleFragment.onResume now defers restartLiveGazeAfterCalibration by
  GAZE_RESTART_DELAY_MS (800ms, isAdded-guarded) so the test activity finishes
  its onDestroy camera release before the article rebinds. attachLiveGaze
  already reloads drift correction, so reading now uses it without re-entry.
  assembleDebug OK. On-device: test -> apply -> back should now show the live
  dot resume within ~1s and reflect the correction; confirm via `GazeMap` log
  showing the "[drift-corrected]" suffix during reading.

## 2026-07-25 - Corrected stale tracker documentation (GazeFollower -> MediaPipe)

`docs/build-context.md` and `CLAUDE.md` both still declared GazeFollower-over-WiFi
the "confirmed, final tracker" and instructed *not* to build MediaPipe/CameraX
code here - contradicted by the code since 2026-07-06 (WiFiGazeProvider.kt and
GazeStream.kt deleted, MediaPipeRawGazeSource.kt active) and by
implementation-spec.md, which calls MediaPipe "the current tracker."

Both updated. The STATUS UPDATE in build-context.md is now a **tracker decision
history table** (MediaPipe on-device failed -> MobileGaze/TFLite failed ->
GazeFollower passed and was adopted -> reverted to on-device MediaPipe on
deployability grounds), kept deliberately because it is the evidence trail for
the manuscript's "alternatives considered" section. Recorded explicitly that the
revert traded raw accuracy for deployability (no co-located laptop per session)
and is NOT an accuracy improvement, and that accuracy remains the binding
limitation with word/exact-line claims conditional on pilot validation. Stage
3's historical FAILED verdict is retained with a "superseded" note rather than
deleted. Deliverables checklist corrected (Stages 4-5 complete; accuracy
re-measurement and target-age validation outstanding).

Also fixed `docs/session-setup.md` steps 3-4: calibration now launches from the
**home screen**, not the article page; the article top-bar control is the 9-point
accuracy check.

Follow-up same day: Stage 4 and Stage 5 section headers in build-context.md also
corrected (were "[ACTIVE - CURRENT WORK]" and "[NOT STARTED]"; both are complete
- Stage 5 is `ReadingStateInferencer.kt` + `WindowedStabilityEstimator.kt`).

Working figures/assumptions recorded for now, both to be confirmed later:
- **Stage 3 vertical error: ~1.1-2.3 cm** (per James). Other figures recorded
  elsewhere in the repo differ (prototype 16-point R1 vertical 0.60 cm / R2
  2.30 cm; newsmead 3-run vertical median 0.67-1.01 cm) and all predate the
  2026-07-24 calibration rebuild. Re-measure on the current build and make every
  doc + the manuscript cite one set.
- **The manuscript is assumed to still describe the GazeFollower/laptop
  architecture and to need revision.** NOT verified - `Manuscript_Updated.pdf`
  is password-protected and could not be read from here. Confirm by searching it
  for "GazeFollower", "WiFi", "laptop".

Defense prep: expect "your own docs say this tracker failed validation" - answer
is the deployability trade, the calibration rebuild, and the limitation being
retained rather than claimed solved.

## 2026-07-25 - Section 4.3.2 manuscript draft for the gaze/calibration system

Added `docs/calibration-methods.md`: insertable Section 4.3.2 (Gaze Tracking)
draft covering feature extraction, the 16-point calibration procedure, the
polynomial mapping model, quality assurance, and 9-point verification/drift
correction. Follows the conventions of `manuscript-implementation-alignment.md`
(blockquoted manuscript prose, formula fences, provisional-parameter marking,
traceability table, consistency checklist), which now cross-references it.

Terminology decision recorded: the 3x3 procedure is **accuracy verification**
(+ affine **drift correction**), NOT "9-point calibration" - it never re-estimates
the polynomial. Two items flagged for James before submission: (1) the A56
accuracy figures in these notes predate the calibration redesign and must be
re-measured; (2) MediaPipe confidence thresholds read 0.5 in source but 0.35 in
the 2026-07-06 note - source is authoritative, confirm before citing.

## 2026-07-24 - Re-calibration system re-architected to the calibration standard

Full re-architecture (design doc §7.2); re-calibration stays on the article page.
- Extracted `CalibrationPointCollector` - the shared per-point capture engine
  (APPEAR/HOLD/SETTLE/SAMPLE-adaptive/CONFIRM + FixationWindowFilter driving
  CalibrationView). Both GazeCalibrationActivity and GazeTestActivity now use it,
  removing the duplicated/divergent sampling (the test previously used the old
  plain-median-over-fixed-window path, which is why improving calibration never
  improved the test). GazeCalibrationActivity refactored onto it with behavior
  preserved.
- Redesigned GazeTestActivity as an accuracy-check + drift re-calibration screen:
  shared capture (good sampling, light bg, pulsing bullseye, audio, mini-map);
  3x3 measure feeding an affine fit in screen space; **honest leave-one-out**
  post-correction estimate (DriftCorrection.leaveOneOutMedianPx) instead of the
  optimistic in-sample residual; median/P95/vertical + line-height bands
  matching the calibration gate. Remedy gate now offers Apply / **Revert to
  calibration** / **Full recalibration** / Measure again (was Apply-only).
- Added append-only recalibration_log.csv audit trail (apply/revert) alongside
  the single active drift_correction.csv.
- New DriftCorrection.leaveOneOutMedianPx + tests. assembleDebug + all
  com.newsmead.gaze.* tests pass. On-device: re-verify measure -> apply -> revert
  and that calibration still runs identically after the collector refactor.

## 2026-07-07 — Align MediaPipe gaze feature with the prototype

Reworked `gaze/MediaPipeRawGazeSource.kt` to match the sister-repo prototype's
`FaceLandmarkerHelper` feature extraction: front-camera frame is now mirrored
(`postScale(-1,1)` in `uprightMirrored`), each iris is paired to its nearer eye
via the `frac`-based corner/eyelid feature (iris rings 468..472 / 473..477), and
blink frames are dropped via eye-aspect-ratio openness with hysteresis
(BLINK_CLOSE 0.04 / BLINK_OPEN 0.055) before `onRawGaze` emits. Calibration and
live gaze share this source, so both benefit and stay self-consistent. Raw gaze
feature values are NOT comparable to the pre-change ones — recalibrate.

Follow-up (same day) — fixed low FPS / lost vertical accuracy: `MediaPipeRawGazeSource`
was running CPU inference on an uncapped-resolution stream with no FPS floor, so it
ran far below real time and starved the median/One Euro smoothing. Ported the
prototype's Stage 1 camera profile: GPU delegate with CPU fallback, 480x360 analysis
resolution, and `CONTROL_AE_TARGET_FPS_RANGE` pinned to 30. Also switched
`detectAsync` to the monotonic `nextTimestampMs()` (avoids MediaPipe timestamp
collisions at 30 fps). Added an FPS readout: `LocalRawGazeSource.OnFps` callback
surfaced from the source, shown top-right on the calibration screen (`fpsText`) and
top-left on the article `GazeOverlayView` during live reading.
Verified `:app:compileDebugKotlin` passes. NOTE: CLI kapt fails on JDK 21 without
`--add-opens jdk.compiler/...`; build in Android Studio or add those to
`gradle.properties` `org.gradle.jvmargs`.

Follow-up (same day) — crash fix + gaze test. On-device: repeated native SIGBUS in
`libmediapipe_tasks_vision_jni.so` (~75-90s in). Bumped MediaPipe tasks-vision
0.10.14 -> 0.10.29 (matches prototype, which crashed far less); crash gone in
testing. Diagnosed "dot stuck at top": logcat of live raw feature shows gaze_y
varies fine (0.19-0.38) and mapped Y sweeps full screen (108-2207) on deliberate
up/down, but median gaze_y in reading ~0.33 (upper part of calib range) so the dot
biases high during natural reading — this is the inherent appearance-based limit,
not a mapper bug. Ported the prototype's Stage 3 accuracy test as
`GazeTestActivity` (+ `GazeDotView`, `activity_gaze_test.xml`, `gaze_test_*`
strings): live calibrated dot on a blank screen + 3x3 (20/50/80%) benchmark that
logs median + vertical error (px/cm) under tag `GazeStage3`. Launch via the new
dot button next to the calibrate button in the article toolbar (needs calibration
first). Diagnostic raw-gaze logging added to `MediaPipeRawGazeSource` (tag
`MediaPipeGaze`, every 15 frames).

Measured accuracy (A56, MediaPipe on-device path, 3 runs): vertical median
0.67-1.01 cm, overall median 1.68-2.42 cm; horizontal poor/right-shifted. James
reports the prototype gets ~0.7 cm CONSISTENTLY with this same adopted logic, so
this is NOT a ceiling - newsmead was still diverging. Gaze-test UX: per-point
error now shown on-screen (numeric + yellow estimate dot + miss line); fixed the
article toolbar so Read Aloud no longer overlaps the new test button.


Follow-up (same day) - blink threshold configurability + accuracy diagnostics: moved the MediaPipe blink hysteresis thresholds out of hardcoded source constants into `StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD` / `GAZE_BLINK_OPEN_THRESHOLD` (defaults remain 0.04 / 0.055). Added `LocalRawGazeSource.BlinkStats` so the raw source reports emitted samples, blink-dropped frames, no-face frames, last openness, blink state, and active thresholds. `GazeTestActivity` now logs these as per-point and whole-run deltas in `GazeStage3`, alongside signed `dx/dy`, so accuracy runs can show whether poor results came from bias, false blink filtering, missing landmarks, or low usable sample count.
Found + fixed the last calibration divergence: `GazeCalibrationActivity` was
collecting differently from the prototype - COLLECT_MS 2500 (proto 1000),
MIN_SAMPLES 5 (proto 10), and MAD outlier rejection (`robustCenter`) instead of a
plain component-wise median. Since the mapper is only as good as its calibration
points, aligned all three to the prototype (1000 ms, 10 samples, plain median;
removed robustCenter/OUTLIER_K). MUST RECALIBRATE for this to take effect, then
re-run the accuracy test. If still short, next suspect is the live provider blink
handling: prototype `MediaPipeGazeProvider` resets the median filter on
blink-reopen; newsmead drops blinks at the source and doesn't reset - not yet
ported.

## 2026-07-01 — Bypass login gate for thesis research-instrument use

**Context:** NewsMead is being repurposed as a research instrument for a thesis
study. No real users, no personalization, no account system. The Firebase login
screen was blocking access to the home/article screens, and we don't want to
stand up the backend auth flow just to get past it.

### What the login gate was
- Enforced in `app/src/main/java/com/newsmead/activities/SplashActivity.kt`
  (the `LAUNCHER` activity, see `AndroidManifest.xml`).
- Check: `if (auth.currentUser != null)` — a **Firebase Authentication** check
  (`FirebaseAuth.getInstance().currentUser`). Not a local session token, not a
  `newsmead-api` call. If a Firebase user existed → `MainActivity` (home);
  otherwise → `AccountActivity` sign-up/login.
- `MainActivity` hosts `nav_graph.xml`, whose `startDestination` is already
  `homeFragment`. So the only thing gating the home screen was that one `if`.

### The change (smallest, reversible, non-destructive)
Added a feature flag in `SplashActivity` and short-circuited the auth check:

```kotlin
companion object {
    const val BYPASS_LOGIN = true   // false = restore normal login/sign-up flow
}
...
if (BYPASS_LOGIN || auth.currentUser != null) {
    navigateToMainActivity()
}
```

- All login code (LogInFragment, SignUpFragment, AccountActivity, Firebase) is
  left fully intact for later reference. Flip `BYPASS_LOGIN` to `false` to revert.
- Launch now goes straight to `MainActivity` → `homeFragment`.

### Known secondary consideration (feed content)
- Article/home content comes from a **public REST API** (Volley GETs to
  `newsmead.southeastasia.cloudapp.azure.com`) that does **not** require a
  Firebase token — see `DataHelper.loadArticleData()`. Browsing by
  category/source/search uses the `/articles/?…` endpoint and works with no login.
- BUT the **default home feed** hits `…/recommendations/{uid}`, where
  `uid = FirebaseHelper.getUid()`. With no Firebase user, `getUid()` returns the
  literal string `"null"`, so the request becomes `…/recommendations/null`.
  If the recommender rejects an unknown uid, the **default home feed may be
  empty** (screen still renders; list just comes back empty).
  - Server behavior for `recommendations/null` was not verifiable at edit time
    (the Azure host was unreachable from the dev sandbox).
  - If the feed comes up empty on-device, the smallest fix is to fall back to the
    generic `/articles/` endpoint when `uid == "null"` inside
    `DataHelper.loadArticleData()` (the code already knows how to parse that
    response). Not applied yet — pending confirmation of actual server behavior.
- Firebase-backed features (Saved lists, History, Profile) already guard on
  `uid == "null"` and degrade gracefully to a "Please login" toast — no crash.

---

## 2026-07-02 — Offline local article dataset (backend is gone)

**Context:** The content backend no longer exists — both API hosts
(`newsmead.southeastasia.cloudapp.azure.com` and `…-fil.…`) **no longer resolve
in DNS** (Azure VMs deprovisioned). So bypassing login alone leaves every feed
empty. Per the study plan (Option 1), article content is now served from a
bundled local JSON asset so the app runs **fully offline**.

### Switches
All study-mode flags now live in one place:
`app/src/main/java/com/newsmead/data/StudyConfig.kt`
- `BYPASS_LOGIN = true` — used by `SplashActivity` (replaces the local const
  from the previous entry).
- `OFFLINE_MODE = true` — serve articles from the local asset.
- `ARTICLES_ASSET = "study_articles.json"`.
Flip both to `false` to restore full production behaviour.

### What changed
1. **`DataHelper.loadArticleData()`** — when `OFFLINE_MODE`, delegates to a new
   private `loadLocalArticleData()` that reads `assets/study_articles.json`,
   parses the **same JSON schema the old API returned**, and applies the same
   filters server-side used to (source / category / language / search / pageSize).
   So home feed, search, by-source, by-category, and the recommended list all
   work unchanged against local data.
2. **`FirebaseHelper.isNetworkAvailable()`** — returns `true` immediately when
   `OFFLINE_MODE`. The feed screens only render inside
   `if (isNetworkAvailable(...))` (8 sites), so without this the shimmer would
   spin forever on a device with no network.
3. **`app/src/main/assets/study_articles.json`** — the dataset. Now holds **2
   original dummy study articles** (`article_id` 1 = health/lifestyle,
   2 = drainage/news), both English, `source: "study"`. Source anonymized:
   `"study"` maps to the neutral in-app label **"NewsMead"** (via `sourceNameMap`
   / `reverseSourceNameMap` in `DataHelper`) so nothing looks like a real outlet.
   Add more articles (up to the 4 A–D conditions) by appending to the array.

### JSON schema (dictated by the parser in `DataHelper`)
Top-level object with an `articles` array. Each article:

| field        | type   | notes |
|--------------|--------|-------|
| `source`     | string | raw key: `gmanews`, `inquirer`, `philstar`, `manilabulletin`, `news5`, `abantenews`, or `study`. Maps to the bundled logo + display name; any other value → generic logo + blank name. `study` → display name **"NewsMead"** + generic logo (added for the anonymous thesis articles). |
| `title`      | string | headline |
| `image_url`  | string | hero image. `""` = none. For an offline image, put the file in `assets/study_images/` and use `file:///android_asset/study_images/<file>` (Glide loads it). A normal `http(s)` URL only shows if the device happens to be online. |
| `date`       | string | **must** be `yyyy-MM-dd HH:mm:ss` (falls back to the raw string if unparseable, logged as a warning) |
| `body`       | string | full article text, no length limit |
| `category`   | string | e.g. `news`, `opinion`, `sports`, `technology`, `lifestyle`, `business`, `entertainment` |
| `language`   | string | `English` or `Filipino` (non-English hides the Translate button, which needs the dead API) |
| `read_time`  | string | free text, e.g. `"5 min read"` |
| `url`        | string | original article URL (used by the Share button) |
| `article_id` | int    | unique integer; identity used for de-dup and Latin-square condition assignment |

Latin-square note: the 4 articles map cleanly to conditions A–D via
`article_id`. The feed shows them in array order; per-participant ordering is a
study-runtime concern, not yet wired into the app.

---

## 2026-07-02 — Stage 4 prep: article view confirmed native + font size locked

- **WebView vs native (Stage 4 open question):** RESOLVED — the article body is a
  native `TextView` (`tvArticleText`, set via `.text = article.body`). No WebView
  anywhere (only a boilerplate mention in `proguard-rules.pro`). So line boxes for
  AOI mapping come from the `TextView` `Layout` API, not injected JS.
- **Font size (Stage 4 blocker):** RESOLVED and locked.
  - The article view had **no "large" preset** — just a ± stepper (min 14 / max 60,
    `ArticleFragment.addBottomAppBarListeners`). Default body = **20sp**
    (`ts_body_large`). The stepper is buggy (mixes px/sp: "larger" +~0.7sp/tap,
    "smaller" −2sp/tap) and density-dependent — unusable for fixed AOIs.
  - **Decision: 22sp**, locked. Implemented in `StudyConfig`
    (`LOCK_ARTICLE_FONT_SIZE`, `ARTICLE_FONT_SIZE_DP = 22f`) and applied in
    `ArticleFragment` via `setTextSize(COMPLEX_UNIT_DIP, 22f)` — **dp, not sp**, so
    the OS accessibility font-scale can't change the pixel size. The ± controls
    (`llArticleBottomButtons`) are hidden in study mode → no runtime adjustment.
  - NOTE: body line spacing is `lineSpacingMultiplier 1.6` + `lineSpacingExtra 1sp`
    (from `ts_body_large`) — relevant when computing per-line vertical bands.
  - Color-mode buttons (light/dark/sepia) are left active; they don't change line
    geometry. Flag if you want them locked too for visual consistency.

---

## 2026-07-02 — Stage 4 slice 1: WiFi gaze stack ported into NewsMead

Ported the self-contained GazeFollower-over-WiFi tracker from
gaze-thesis-prototype into **`app/src/main/java/com/newsmead/gaze/`** (verbatim
except `package`): `GazeProvider` (the swappable boundary), `WiFiGazeProvider`,
`GazeStream` (UDP listener, port 5005), `GazeMapper` (affine laptop-px→phone-px),
`OneEuroFilter`, `MedianFilter`, `CalibrationStore` (+`CalibrationSample`).
- Added dep `org.apache.commons:commons-math3:3.6.1` (GazeMapper least-squares).
- Added `<uses-permission android:name="android.permission.INTERNET"/>` for the
  UDP socket (was only present transitively via Firebase/Volley).
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL. Not yet wired to any screen.
- NOT ported (prototype-only, not needed here): the Stage-1/2/3 camera/menu
  activities, `WiFiListenerActivity`, `CalibrationActivity`/`CalibrationView`,
  `GazeDotView`. The calibration *capture* UI is still needed to produce
  `calibration_wifi.csv` before `WiFiGazeProvider` can map real gaze — see slice
  decision below.

## 2026-07-02 — Stage 4 slice 2: article-screen wiring (touch-validation)

Wired the gaze AOI layer into the article reading screen, driven by touch for
validation (no tracker/laptop needed yet). New files in `com.newsmead.gaze`:
- **`LineAoiMapper`** — `lineAt(screenY)` → body line index. Scroll-aware for free:
  uses `tvArticleText.getLocationOnScreen()` (which already moves with scroll), so
  `screenY − textViewTop − totalPaddingTop` indexes the `Layout` directly; returns
  −1 when the point is off the text. Stable because font size is locked (22dp).
- **`GazeOverlayView`** — translucent dot drawn from full-screen px; non-interactive
  (passes touch through), sits on top of the article.
- **`ArticleFragment.setupGazeDebug()`** — gated by `StudyConfig.GAZE_ENABLED`. Adds
  the overlay over `binding.root`, and routes all gaze through one
  `GazeProvider.OnGaze` entry point (so live `WiFiGazeProvider.setOnGaze` drops in
  later with no fragment changes). With `GAZE_TOUCH_VALIDATION`, an
  `nsvArticleText` touch listener feeds `event.rawX/rawY` and returns false so
  scrolling still works.
- Logs `GazeAOI: gaze=(x,y) line=N/total` per event.

**On-device test (James, A56 — no laptop needed):** open a study article, drag a
finger down the text, `adb logcat -s GazeAOI`. The logged `line=N` should match the
line under your finger, and stay correct after scrolling. This is the build-spec
touch-validation gate; passing it isolates any later error to the gaze tracker,
not the integration.

`:app:assembleDebug` → BUILD SUCCESSFUL (with JDK 17).

**On-device (A56):** VALIDATED — `GazeAOI` logs stream and `line=N` is responsive
to touch as the finger moves down the passage (touch-validation gate). Scroll-
awareness confirmation recommended as a final check.

## 2026-07-02 — Stage 4 slice 3: live WiFi gaze wired (needs laptop rig to test)

Ported the calibration *capture* screen and wired the live tracker into the
article screen. Builds (`:app:assembleDebug` OK, JDK 17). **VALIDATED live on the
A56 (2026-07-02):** full rig run passed — laptop stream → 16-dot phone
calibration → live gaze on the article view, dot follows gaze and `line=N` tracks
the read line, scroll-correct. Stage 4 acceptance met.
- **`GazeCalibrationActivity`** (`com.newsmead.activities`) + `CalibrationView`
  (`com.newsmead.gaze`) + `activity_gaze_calibration.xml` + `calib_*` strings —
  full-screen 16-dot calibration, reads UDP gaze via `GazeStream`, robust
  outlier-rejected median per dot, writes `calibration_wifi.csv` via
  `CalibrationStore`. Registered in manifest (`exported=true`, see below). Logs
  the phone IP (`GazeCalib` tag) for the laptop's `--phone-ip`. Has a **"Start"
  gate** — opens and waits for a tap so the researcher can position the phone
  before the dots begin (doesn't auto-run).
- **`ArticleFragment`**: when `GAZE_TOUCH_VALIDATION = false`, builds
  `WiFiGazeProvider(GazeMapper(CalibrationStore.load()))`, streams gaze through
  the same `GazeProvider.OnGaze` entry point (main-thread hop), and stops it in
  `onDestroy` (releases the UDP socket). Falls back to touch validation if no
  calibration exists or the fit fails.
- Flags unchanged (`GAZE_TOUCH_VALIDATION = true`), so current behaviour is still
  touch validation until the flag is flipped for a live session.

**Launch trigger (decided):** adb, no in-app UI (laptop is always present at a
session). Launched with
`adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity`.
NOTE: `GazeCalibrationActivity` must be `android:exported="true"` — Android 16 /
One UI on the A56 blocks adb from starting non-exported activities (confirmed:
`SecurityException: not exported`). Verified launching on-device. Full runbook:
**docs/session-setup.md**.

**End-to-end live test — PASSED (A56, 2026-07-02):** per docs/session-setup.md, all
steps 1–6 confirmed by James (laptop stream → phone calibration → live
gaze-to-line on the article, scroll-aware, no socket leak on re-entry).
Manifest fix required along the way: `GazeCalibrationActivity` set
`exported="true"` (Android 16 blocks adb launching non-exported activities).

### Build note
- The project's kapt is **incompatible with JDK 21** (fails with
  `module jdk.compiler does not export com.sun.tools.javac.main`). This is a
  pre-existing environment issue, unrelated to this change.
- Build with **JDK 17** instead, e.g. `C:\Program Files\Java\jdk-17`:
  `JAVA_HOME="C:\Program Files\Java\jdk-17"; ./gradlew :app:compileDebugKotlin`
- Verified: `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** with all changes
  above (login bypass + offline dataset). `study_articles.json` validated as
  well-formed. Full on-device confirmation (home feed renders, article opens)
  still requires an emulator/device.

## 2026-07-02 - Stage 5 slice 1: RSI feed from line AOI stream

Added `ReadingStateInferencer` in `com.newsmead.gaze` and wired it at the existing `GazeProvider.OnGaze` entry point in `ArticleFragment`. It emits/logs `GazeRSI` line samples, fixation, dwell, and regression events only; no saccade or microsaccade features. Targeted JVM tests for the RSI inferencer pass; full `testDebugUnitTest` still has the pre-existing `FirebaseTest` JVM Android API failure.
## 2026-07-05 - Stage 5 slice 2: RSI scoring, regression debounce, and interpretation docs

Extended `ReadingStateInferencer` with a composite RSI score logged through `GazeRSI`: `score`, normalized `reg`, `dwell`, `fix`, and raw `events=fN/dNms/rN`. The score is a reading-effort signal, not a comprehension grade.

Adjusted regression detection to reduce false positives from gaze jitter: a backward move counts as a regression only after gaze remains on the earlier line for 300ms. Brief upward bounces are ignored. Later follow-up changed the anchor from the immediately previous line to the highest line reached, so confirmed one-line rereads are caught while duplicate counts during the same backward episode are suppressed.

Added targeted JVM coverage for sustained regressions, ignored upward bounces, and the composite score. Verified:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Result: BUILD SUCCESSFUL.

Added `docs/reading-stability-index.md` with score ranges, raw metric meanings, interpretation examples, and calibration/noise caveats. `README.md` now links to the RSI guide from the Stage 5 section.

---

## 2026-07-06 - Local 16-point MediaPipe gaze integration, no backend/Wi-Fi

**Context:** `docs/implementation-spec.md` was updated to remove the backend/Wi-Fi gaze path and require Stage 4/5 to depend only on the `GazeProvider` boundary. Stage 3 remains accuracy-limited, but the app now runs the gaze stack locally on the phone.

### What changed
- Removed the live UDP/Wi-Fi gaze path from source:
  - deleted `app/src/main/java/com/newsmead/gaze/GazeStream.kt`
  - deleted `app/src/main/java/com/newsmead/gaze/WiFiGazeProvider.kt`
  - removed the gaze-related `INTERNET` permission from `AndroidManifest.xml`
- Added local raw gaze pipeline:
  - `MediaPipeRawGazeSource` uses CameraX front camera + MediaPipe FaceLandmarker.
  - `LocalRawGazeSource` is the raw feature boundary: emits `gaze_x,gaze_y,timestamp`.
  - `LocalCalibratedGazeProvider` smooths raw features, applies `GazeMapper`, and emits screen px through `GazeProvider`.
  - `LocalGazeSources.create(context)` now returns `MediaPipeRawGazeSource(context)`.
- Added required runtime pieces:
  - `android.permission.CAMERA`
  - CameraX dependencies
  - `com.google.mediapipe:tasks-vision:0.10.14`
  - `app/src/main/assets/face_landmarker.task`
- Updated calibration storage:
  - new file name: `calibration_16point.csv`
  - legacy `calibration_wifi.csv` can still be read with a warning, but recalibration should write the new file.
- Updated `GazeMapper` from the old affine/Wi-Fi mapper to the Stage 3 local mapper:
  - second-degree polynomial features
  - standardized gaze inputs
  - clamping to calibration feature range
  - ridge regularization (`lambda = 1.0`)
- Updated `GazeCalibrationActivity`:
  - collects samples from `LocalRawGazeSource`, not UDP.
  - still uses the 4x4 / 16-dot sequence.
  - restarts the local raw gaze source after camera permission is granted.
- Updated `ArticleFragment`:
  - live mode now builds `LocalCalibratedGazeProvider(GazeMapper(samples), LocalGazeSources.create(...))`.
  - Stage 4 line mapping and Stage 5 RSI still consume only the `GazeProvider.OnGaze` stream.
- Set `StudyConfig.GAZE_TOUCH_VALIDATION = false`, so article reading now uses live local MediaPipe gaze by default.

### Current pipeline

```text
CameraX front camera
-> MediaPipe FaceLandmarker
-> raw iris/eye ratio feature
-> 16-point calibration CSV
-> GazeMapper
-> GazeProvider
-> LineAoiMapper
-> ReadingStateInferencer / RSI logs
```

### Test plan
1. Run app on a real phone.
2. Grant camera permission.
3. Launch calibration:

```powershell
adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity
```

4. Complete all 16 dots.
5. Open an article normally.
6. Watch Logcat tags:

```text
MediaPipeGaze
GazeCalib
GazeAOI
GazeRSI
```

Expected logs:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: Wrote 16 calibration pairs
GazeAOI: gaze=(x,y) line=N/total
GazeRSI: score=...
```

### Verification
Verified locally:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Both commands returned `BUILD SUCCESSFUL`.

### Remaining validation
- Needs on-device calibration quality check with the new phone-local MediaPipe source.
- If calibration dots keep retrying, likely causes are camera permission, missing face visibility, poor lighting, unstable phone/head position, or FaceLandmarker not producing iris landmarks consistently.
- Stage 4/5 integration is still isolated behind `GazeProvider`; if the raw feature needs tuning, downstream line mapping and RSI should not need rewrites.

### 2026-07-06 follow-up: MediaPipe calibration read fixed on device

Initial on-device logs showed two issues:

```text
MediaPipeGaze: No face landmarks
GazeCalib: Dot 1: only 0-4 samples, re-collecting
GazeCalib: Loading legacy calibration_wifi.csv
```

Fixes applied:
- `MediaPipeRawGazeSource` now converts CameraX `RGBA_8888` frames with row-stride handling before passing them to FaceLandmarker. The previous direct buffer copy could corrupt frames on devices where `rowStride != width * pixelStride`, causing repeated `No face landmarks`.
- FaceLandmarker confidence thresholds lowered from `0.5` to `0.35` for the phone-local source.
- Live-stream timestamps are forced monotonic before `detectAsync()`.
- Calibration no longer loads legacy `calibration_wifi.csv`; local MediaPipe gaze requires `calibration_16point.csv`.
- Calibration collection window changed to `2500ms`, with `MIN_SAMPLES = 5`, because phone-local FaceLandmarker throughput is lower than the old UDP stream.
- Added periodic `MediaPipeGaze raw=(x,y)` diagnostic logging.

Confirmed on connected phone after installing the fixed debug build:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: pair 1/16 ... n=10 used=10
...
GazeCalib: pair 16/16 ...
GazeCalib: Wrote 16 calibration pairs to /data/user/0/com.newsmead/files/calibration_16point.csv
```

Verified again:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All returned `BUILD SUCCESSFUL`; the fixed APK was installed on the connected phone.

### 2026-07-06 follow-up: in-article recalibration button

Added an in-app control to rerun calibration without using adb:
- `fragment_article.xml` now has `btnRunGazeCalibration` in the article top bar beside the back button.
- The button launches `GazeCalibrationActivity` directly.
- `ArticleFragment` stops the current live gaze provider before launching calibration.
- When the user returns from calibration, `ArticleFragment` reloads `calibration_16point.csv` and restarts `LocalCalibratedGazeProvider` so the new calibration is used immediately.
- Added string `article_calibrate_gaze` for the button content description.

Verified:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All completed successfully.

## 2026-07-20 - Manuscript-aligned adaptive graded visual scaffolding

Implemented the full Section 4.3.5 adaptation layer. The previous word
highlighter ran on every gaze sample and the line renderer was not connected;
focus, re-entry, level selection, and withdrawal did not exist.

### Implementation

- Added visible-text, padding-aware line and locale-aware word targeting.
- Added 120 ms line, 300 ms word, and 200 ms off-text target stabilization.
- Added a 10-second WindowedStabilityEstimator using participant baseline
  mean/std, positive z deviations, manuscript weights, smoothing, and gaze
  confidence. Cumulative GazeRSI remains the session report.
- Added NONE, WORD, LINE, FOCUS, and REENTRY states. Escalation is stepwise
  after 1.5 seconds; withdrawal is stepwise after 3.5 seconds; support clears
  below 65% valid-text gaze confidence.
- Re-entry requires index at least 2.5 plus at least 800 ms off text and a return
  at least two lines from the last stable line. Regression alone is insufficient.
- Added overlay word, line, five-line focus-window, and re-entry line/arrow
  rendering. Transitions fade over 400 ms and never auto-scroll.
- Deprecated WordHighlighter, added OFF/ADAPTIVE/forced validation modes, hid
  gaze debug visuals by default, and added GazeScaffold transition/recovery logs.
- Geometry/baseline starts after final article text loads and resets after
  calibration/test interruption.

### Verification and open research work

- compileDebugKotlin and 18 targeted gaze/scaffold JVM tests pass.
- Physical-device forced-level and adaptive visual validation remains required.
- The per-article 20-second baseline is a fallback; carry the manuscript's
  standardized baseline profile into experimental articles before collection.
- Thresholds, timing, visual parameters, word accuracy, and the operational
  re-entry/recovery definitions require formative/pilot validation.

Canonical guide: docs/adaptive-visual-scaffolding.md.

## 2026-07-20 - Gaze diagnostics enabled

- Enabled `StudyConfig.GAZE_DEBUG_VISUALS` for physical-device debugging.
- The article overlay now shows the live gaze dot and tracker FPS independently
  of the active scaffold level.
- This is a researcher diagnostic, not a participant scaffold. Disable it before
  participant-facing data collection.

## 2026-07-20 - Natural adaptive-trigger validation guidance

- Added a repeatable naturalistic smoke test: clean 20-second baseline followed
  by 10-15 seconds of within-text pauses and rereading.
- Documented that adaptation uses the recent windowed index rather than the
  cumulative GazeRSI score, requires at least 0.65 valid-text confidence, and
  escalates one level per 1.5-second persistence interval.
- Recorded the observed index=2.12/confidence=0.64 run as an expected confidence
  gate, not a scaffold-rendering failure.
- Recommended forced touch/geometry validation before adaptive validation,
  calibration in the reading posture, and disabling debug visuals before
  participant-facing collection.

## 2026-07-20 - Scaffold displacement exposes gaze-validity limitation

- Physical-device observation: adaptive scaffolding triggered, but its apparent
  target moved inconsistently together with the raw gaze indicator.
- Recorded circular measurement contamination as a key limitation: one noisy
  stream can inflate regression/dwell/fixation evidence and misplace the scaffold.
- Clarified that the current confidence value is valid-text coverage, not
  correct-line or spatial-accuracy confidence.
- Added required validation measures across the research docs: median/P95
  vertical error in pixels and line heights, exact-line/within-one-line accuracy,
  word accuracy, dispersion, jump/off-text rates, tracking loss, and drift.
- Added recommendations for synchronized pipeline logs, spatial-quality gating,
  robust filtering, line hysteresis, minimum dwell, outlier rejection,
  head/face and drift checks, recalibration prompts, and independent or
  researcher-coded ground truth.
- Marked word/exact-line intervention claims as conditional. If pilot accuracy
  is inadequate, prefer a broader focus/region scaffold, revise the manuscript,
  or replace the backend rather than hiding tracker error in the renderer.

## 2026-07-20 - Session RSI and adaptive-index terminology decision

- Formalized two separate signals to remove research and implementation
  ambiguity. **Session RSI** is the cumulative 0-100 `GazeRSI score` used for
  session/article/condition reporting. **Adaptive Instability Index** is the
  recent participant-relative 0-4 `GazeScaffold index` used for scaffold control.
- Recorded why Session RSI does not drive adaptation: early difficulty can keep
  a cumulative value elevated after recovery, while a new late-session event can
  be diluted by a long stable history. It is also not baseline-relative.
- Documented the exact adaptive calculation: 10-second counter deltas for
  regression/minute, dwell proportion, and fixation/minute; positive baseline
  z-scores; 0.40/0.35/0.25 weighting; clamp to 0-4; and two-second exponential
  smoothing. Metrics update every 500 ms after at least two seconds of data.
- Retained Session RSI for overall descriptive analysis. A short-window 0-100
  alternative would still need personalization, smoothing, quality gates, and
  recovery logic and would reproduce the current adaptive signal under another
  scale.
- Recorded the current 1.0/1.5/2.0/2.5 adaptive thresholds as provisional
  engineering values requiring pilot tuning against labeled reading episodes
  with tracker-error periods excluded or separately marked.

### Controller alternative considered

- Considered using cumulative Session RSI directly for scaffold levels.
- Rejected it as the current controller because cumulative history can preserve
  early difficulty after recovery, dilute late difficulty after long stable
  reading, and apply non-personalized cutoffs to different reading styles.
- Confirmed that direct use would require new pilot-validated 0-100 thresholds;
  the current adaptive thresholds cannot be converted directly. Any example
  Session RSI bands remain illustrative only and are not study parameters.
- Recorded that Level 4 still requires independent loss-of-position evidence.
- Retained a rolling 0-100 RSI as a possible display-scale alternative, while
  noting that adding personalization, smoothing, quality gates, escalation, and
  recovery makes it functionally equivalent to the Adaptive Instability Index.

## 2026-07-21 - Manuscript RSI/adaptive revision guide

- Added `docs/manuscript-implementation-alignment.md` as the authoritative,
  living insertion guide for updating the actual manuscript as implementation
  decisions continue.
- Mapped required changes to Section 4.3.4, a new Section 4.3.4.1, Section
  4.3.5, methodology/data analysis, and limitations.
- Included the implemented formulas, controller table, persistence and quality
  gates, research-variable separation, synchronized logging requirements,
  circular measurement contamination, the rejected direct Session RSI
  alternative, and a manuscript consistency checklist.
- No PDF or manuscript source was edited; the repository currently contains only
  `Manuscript/Manuscript_Updated.pdf` and no editable DOCX or LaTeX source.

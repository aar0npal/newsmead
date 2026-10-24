# Agent context — updated 2026-10-19

**2026-10-19 scaffold target freshness change (source change, device unverified):**
User observation showed confirmed support could appear on a previously viewed
line. Mahalanobis confirmation is based on a rolling 10-second R/D/F interval
and does not localize a causal line; the old renderer consumed the target held
at the 500 ms scoring tick. Normal scaffolds now clear their pre-confirmation
target, require gaze captured at or after confirmation, and require the mapped
line to remain stable for 120 ms. A changed line clears the old highlight while
the replacement is stabilized, and capture-time gaze can update the target
between scoring ticks. Invalid targets clear after a 200 ms grace period.
Independent displaced-return evidence is retained as an explicit `REENTRY`
target because it intentionally points to an earlier stable line. Schema version 2 reading logs
now record scaffold configuration, mapped/stabilized/current/rendered/re-entry
lines, capture/confirmation/not-before timestamps, target age/status, and
material target-update reasons. No camera images or raw gaze coordinates were
added. The user reserved focused behavioral tests; no device install or capture
was performed. The local wrapper first attempted a blocked Gradle download; a
direct cached Gradle 8.4 invocation then stopped because Android Gradle Plugin
8.3.0 was absent from the offline cache, so compilation remains unverified in
this environment.

**2026-10-18 A02 first-output startup timeout during article reading:**
The newly installed build produced valid frozen baseline profiles in private
`no_backup/mahalanobis_baseline_e4318368-e8b4-4dc8-a106-ea11a8a19bbc.jsonl`
and `no_backup/mahalanobis_baseline_442399b3-a206-4a62-9642-b89b8a0cf02a.jsonl`.
Their first logged gaze inputs arrived about 19.6 and 20.1 seconds after the
first baseline tick. Subsequent private article logs
`no_backup/mahalanobis_reading_7901cd23-59f1-4d3f-be51-23c26f129f4c.jsonl`
and `no_backup/mahalanobis_reading_7535d61e-3ee2-4a54-b915-5bcd176980ed.jsonl`
had a valid baseline profile but zero gaze samples and ended `provider_failed`
after about 14 and 20 seconds of scoring. The provider's first-output watchdog
was 20 seconds, so slow startup on this A02 is the leading explanation; the
old reading logs did not retain the exact provider failure message. The
first-output watchdog is now 40 seconds, while per-frame 650 ms expiry and
feature quality are unchanged. Article reading logs the provider failure
message for its own active session. This is an operational fix, not evidence
of valid live MD2 scoring. No agent-led install or post-fix collection occurred.

**2026-10-18 A02 post-scroll-fix baseline follow-up (source fix, device unverified):**
Private run `no_backup/mahalanobis_baseline_a372aa79-9041-4c69-84e2-e4af986ed86c.jsonl`
used the 650 ms reading output age limit. It yielded 179 vectors over about
90 seconds: 20 `INSUFFICIENT`, 141 `MISSING`, 18 `DISCONTINUITY`, zero valid.
The 10-second rolling span now holds (median about 10.07 seconds); 396
`reading_scroll` boundaries and one `geometry_change` show the earlier scroll
reset was removed. Quality was the blocker: maximum valid gaze fraction 0.50
against the configured 0.65. Interval exclusions totaled 139 stale, 100 blink,
12 outside visible text, and ten geometry unsettled; 86 of 347 reported
adapter observations were eligible. Interval mean capture-to-delivery ages had
median about 588 ms. The timeout watchdog previously called full live-output
invalidation, resetting the capture-ordered blink gate soon after a processed
coordinate aged past 650 ms. This is a plausible cause of recurring blink
exclusions, not proven from the old log because it did not distinguish timeout
and source stale observations. The watchdog now resets the coordinate smoother
without resetting eye state; a new measured closed eye still blocks output.
Baseline vectors now log `watchdogStaleCountSincePreviousUpdate` to distinguish
timeout observations in a later run. Focused source review found no P1/P2 issue;
the focused live-output test and debug APK build passed. The agent did not
install this build or collect a post-change device run.

**2026-10-18 baseline window reset follow-up (source fix, device unverified):**
Read-only summary of the user's latest private A02 baseline log
`no_backup/mahalanobis_baseline_765bca9a-edab-4197-a965-fb01b9b0730c.jsonl`
found 293 `geometry_change` boundaries in 32 seconds, 64/64 `INSUFFICIENT`
vectors, and a longest uninterrupted rolling span of about 7.0 seconds.
There were 63 adapter observations but only one eligible; exclusions were 19
stale, 19 blink, 17 outside visible text, and seven geometry unsettled. These
counts identify both a feature reset problem and separate gaze eligibility
problems; they do not prove a participant or detector outcome. Verified manual
vertical translation now breaks line-event continuity without discarding the
10-second rolling feature window in baseline and adaptive article reading.
True text/layout changes still reset the window. An expired coordinate stays
excluded, while its capture-ordered eyelid sample may advance blink recovery;
duplicate clocks and missing eye data retain their abstention behavior. The
baseline screen now displays valid-window and covered-time progress, the latest
quality reason, and gives the text more screen space during collection. Focused
source review found no remaining integration issue. No new APK install or
post-change camera collection was performed by the agent.

**2026-10-18 A02 repeat baseline and reading freshness follow-up:** A newer
installed debug APK (package last updated 02:00:26 local) produced private log
`no_backup/mahalanobis_baseline_8c41eff9-d7f0-41d6-8bc7-33e9a7217eba.jsonl`:
173 vectors (146 insufficient, 27 missing), 379 adapter observations, 20
eligible, maximum valid fraction 0.30; exclusions 152 stale, 118 blink, 76
outside visible text and 13 geometry unsettled. Interval capture-to-delivery
mean ages had median about 485 ms and P90 about 528 ms. Current
`MGazeNetPerf` batches show the one-slot live queue change worked mechanically
(average queue about 0.2–0.3 ms, versus 115–160 ms previously), yet overall
age remains near the 500 ms gate. The reading-only exploratory output age limit
is now 650 ms for baseline and article; calibration/validation keep the default
500 ms. Article reading can use current geometry only when text is not in
controlled motion and the capture-based settle guard passes. Auto-scroll motion
and its late captures are excluded from reading features while controller input
still receives the original coordinate. Capture timestamps and the 65% window
quality requirement are unchanged. This is software tuning from one A02 run,
not human detector validation. Source review caught and corrected a controller
routing regression before delivery. Debug APK and androidTest sources compile;
focused gaze/Mahalanobis JVM tests pass. Full JVM suite had 311/312 pass, with
unrelated `FirebaseTest.createAccount` failing at `FirebaseTest.kt:16`.
No new app install or post-change camera collection was performed by the agent.

**2026-10-18 attached A02 baseline failure (read-only device evidence):** The
latest private `no_backup/mahalanobis_baseline_7d4be860-4831-4861-8b63-616c5e0937a3.jsonl`
contains 121/121 `INSUFFICIENT` vectors, 209 adapter inputs, zero eligible,
208 `stale` and one `blink`; maximum valid fraction is zero. Recent
`MGazeNetPerf` logcat batches report average worker queue time about 115–160 ms
and analyzer-start-to-finish total about 430–570 ms. These are separate
measurements: the performance averages do not directly measure capture-to-output
age. The baseline geometry correction below did not resolve this observed run.
`MgazeNetCameraSource.kt` now permits one outstanding cropped inference for a
saved-calibration/live source, keeping calibration's two-slot path unchanged.
`ReadingBaselineActivity.kt` records interval capture-to-delivery age alongside
rejection counts. Source review found no concurrency issue. No new app install,
camera collection, or post-change device validation occurred. Local Gradle
checks could not resolve the Android Gradle plugin from the offline cache.

**2026-10-18 baseline zero-valid-window follow-up (source fix, device unverified):**
The unassisted baseline screen now uses stationary text geometry in
`ReadingBaselineActivity.kt` rather than the optional moving-article geometry
history. The previous default required a geometry snapshot no more than 100 ms
before each camera capture; delayed frames on a static baseline page could all be
excluded as `scroll_geometry_unavailable`. At that point manual scrolling reset
the feature window; the newer follow-up above changes this. The existing 150 ms
settle guard remains. A
synthetic delayed-capture fixture was added in `ArticleReadingSessionTest.kt`.
Each baseline vector log now includes input/eligible counts and exclusion-reason
counts for its update interval, allowing a later device run to distinguish this
path from camera, text-region, or quality failures without storing gaze images.
Focused source review found no correctness issue, but no device observation or
completed Gradle build establishes that this was the user's sole failure cause.

**2026-10-18 ordinary calibration sample guard (software):** The six post-fit
checks now wait at least the existing 4-second capture interval, extend a
low-sample location to at most 8 seconds after the same 3-second settling
period, then retry that location once if it still has fewer than 12 usable
predictions. Completed locations and the fitted model are retained during this
local retry. The 12-sample and spatial-error gates are unchanged, and accuracy
failures are not retried to obtain a favorable result. Matched comparison runs
retain their fixed 4-second check procedure. Attempt counts/duration appear in
the results. Host checks do not establish performance on the user's device.

**2026-10-17 ARMv7 calibration check adjustment:** On a 32-bit ARM device, the
user reported five post-fit locations with 10–11 usable samples where 12 were
required, while all reported spatial error summaries passed the existing limits.
The post-fit sample interval was extended from 2.5 to 4.0 seconds per location
after the same 3-second settling interval; the 250 ms output grace, 12-sample
gate and spatial error limits are unchanged. This is a software timing response
to a reported run, not proof that another run or the reading detector will pass.
No device calibration or collection was performed by the agent.

**2026-10-17 Mahalanobis branch checkpoint (exploratory software):**
`measurement/mahalanobis` replaces the active Scanpath detector, pilot, replay,
pattern aids and logs with a frozen participant baseline and standard full
covariance Mahalanobis distance over existing line regression, completed dwell
and sustained line-stay rates. A separate unassisted baseline-reading activity
was added because the prior 20-second automatic warm-up was not the standardized
phase. Default 10-second windows/500 ms updates, 60 seconds of valid union
baseline coverage, 30 retained windows, λ=1e−6, in-sample P95, 1.5-second
confirmation and 3.5-second recovery are exploratory settings. Capture/geometry
and coordinate-stream infrastructure was extracted to neutral `gaze/reading`.
Historical Scanpath notes below remain as provenance; they do not describe an
active detector on this branch. See [implementation and limits](mahalanobis-detector.md),
[branch audit](mahalanobis-replacement-audit.md), and
[removal inventory](mahalanobis-removed-files.json). No human validity claim.

This is a routing note, not a new research protocol. Recheck source and newer
dated evidence before continuing. It summarizes the project's recent tasks and
local documentation to avoid loading full histories each time.

Use the upstream `scientific-critical-thinking` skill for evidence appraisal and
`statistical-analysis` for statistical planning and interpretation. Their source,
revision and validation limits are in `docs/codex-agent-workflow.md`. The custom
project skills were retired at the user's request. This note and the measurement
protocol remain authoritative for NewsMead-specific decisions and tools.

## Objective and current direction

**2026-10-16 gaze-scroll direction research (design pending):** owner explicitly
requests gaze down → scroll down, gaze up → scroll up, rejecting manual speed
control and calibration-specific design. Primary-source review found a direct
bidirectional steady-speed precedent in Käki et al.2014, plus mobile gesture/dwell
comparisons in DynamicRead2023. Stanford2007 reading auto-scroll was intentionally
forward-only; it does not establish bidirectional control or our old thresholds.
Actual reading gaze before manual scrolling varies across people/regions
(Turner et al.2015). Intent confirmation and phone-specific timings remain open.
A provisional policy/test draft is saved outside active source in
`output/gaze-scroll-direction-research/directional-policy-draft.patch`; its two
initially clean source files were restored so the app retains the verified V3
controller and existing moving-text aid edits. No new build/device attempt.
[Evidence and exact source locations](gaze-scroll-interaction-evidence.md).
Follow-up: owner supplied GazeScroll (officially bidirectional, undisclosed
controller). OptiKey Look-to-scroll is a detailed public reference for a chosen
anchor, stationary deadzone, two-way control and optional zero distance
acceleration. Android Deep-SkyLabs source exists but uses discrete actions and
has simulator/configuration caveats. SeeSo UIAutoScroll source is forward-only.
No external program/camera/install or source import ran; findings appended to
the evidence note. No new direction controller has been installed.

**2026-10-16 aids during auto-scroll (verified software checkpoint):** owner
reports aids do not trigger. Source confirms broad motion exclusion plus geometry
reset on every translation. Live-only opt-in adapter now uses capture-aligned
text-local analysis while preserving raw/display/processing screen points. Last
geometry at/before capture, max100 ms snapshot age/250 ms input age, two-second/
4096-snapshot history. Explicit self-scroll retains layout ID; manual/external
scroll or reflow clears it. Engine coordinates are local; policy/render positions
project into current display. Runtime preserves same-ID translation evidence and
logs motion start/end without clearing it. Existing guide, definition, focus and
bookmark rules remain; lookup offer/panel pauses navigation. Condition
SMOOTHED_ARTICLE_CONTENT_AIDS_V11/modeauto_scroll_content_frame_v1 labels
content-residence proxies, not physiological fixations or validated difficulty.
Pilot/Replay default adapter and classifier thresholds remain unchanged. Review
caught callback-before-preDraw external scroll retaining history; controller
prechecks and forced invalidation/newID now cover it. Follow-up source review
clean. First attempt passed 271 JVM checks/builds and 8/10 native checks, with
focus/bookmark failures retained. Source confirmed startup no-input timer advanced
bookmark capture floor past delayed first prefix; corrected to advance only actual
finite capture floors, with two passing policy regressions. Native focus fixture
uses explicit 24 dp STANDARD tier and asserts its premise; font scaling is a
possible first-failure cause, not proved from that log. Final corrections reviewed.
Owner-approved retry passes **273/273 JVM checks across 31 suites**, both APK builds
and **5/5 aid integration checks in 14.222 s**. A56 replace installs preserve app
data/calibration. Guides, lookup/navigation block, real line-cycle focus,
tracking-loss bookmark and forced external-motion invalidation pass. The old five
navigation tests passed on attempt 01, not rerun on the corrected APK. Evidence:
`output/auto-scroll-reading-aids-check-02/summary.json` plus copied XML/build/
native/install logs; attempt 01 remains intact. Camera stayed off; no human
collection. Replay-hosted attached fixtures exercise production components with
synthetic inputs; complete live Fragment routing and human validity remain unchecked.
[Contract](auto-scroll-reading-aids.md).

**2026-10-16 auto-scroll V3 continuous speed (verified software checkpoint):**
owner confirms V2 works but wants immediate, controlled Athena-like movement;
explicit choice is lower gaze speeds up, higher gaze slows/stops. V3 replaces
steps/dwell/rearm/cooldown with continuous velocity: target0 atY<=40% physical
screen, linear to6% screen height/s atY>=65%; nominal180 ms acceleration/braking,
per-frame elapsed cap50 ms. Selected calibrated/smoothed coordinates stay shared
with dot; no line/word/reading-speed inference. Freshness delivery150 ms/capture
250 ms; missing/invalid gaze hard-stops. Own translation does not reset control;
real reflow/external scroll/controls require fresh settled input. Settling rejects
do not move input floor forward, avoiding starvation of delayed camera captures.
Motion/+150 ms remains excluded from stationary scanpaths, so continuous mode
can exclude most reading time; no motion-compensated metric validation is claimed.
Review caught idle time contributing to the first lower-gaze restart frame;
acceptance now resets its baseline when zero demand/speed becomes positive, with
a10 ms regression. Source follow-up clean.202/202 JVM checks across22 suites and
both APK builds pass. Continuing owner-authorized A56 app/test updates use-r,
preserving data/calibration.5/5 camera-free attached/controller checks pass in
13.550 s: speed changes, upper braking/restart, stale/noncoordinate stops,
lookup blocking, touch pause/resume, tail and menu scope. The80 ms capture lag
fixtures verify first actual movement within400 ms and multiple scroll changes.
Evidence: `output/article-auto-scroll-continuous-check-01/summary.json` plus
XML/build/native/install logs. No camera started or human gaze collected;
actual comfort/accuracy and complete live Fragment routing remain unvalidated.
Official Athena
product description and Kumar/Winograd2007 Figure4 support interaction precedent,
not exact trial settings or human outcomes. [Contract](article-auto-scroll-prototype.md).

**2026-10-16 auto-scroll V2 correction (verified software checkpoint):** owner
reports Pause button but no movement and explicitly requests gaze up/down rather
than line classification. Policy/controller now use clipped-body gaze regions:
Y>=45% screen for700 ms/min5, across changing X/Y/rows; fresh Y<=37.5% for200 ms/
min3 rearms, with existing2 s cooldown. Final gaze's text point moves toward30%
screen, guarded3% below viewport top;400 ms motion/150 ms settling remain. Row
lookup, same-row support and Layout-instance comparisons are removed. Content,
size and viewport changes still break support. The menu reports pre-open status;
phase/block reasons are journaled. A new actual attached article-layout test uses
synthetic varying-region gaze and real capture clocks, without ArticleFragment
or camera startup. Review caught an unreachable upper-rearm viewport; geometry
now requires usable upper space before moving. Both APKs build;200/201 JVM checks
pass. A new test used gaze outside its shortened candidate body and got
outside_article rather than geometry_unavailable; its gaze now uses the body's
center. Owner-approved retry passes201/201 JVM checks across22 suites and builds
both APKs. Approved A56 app/test updates use-r preserving app data/calibration;
7/7 camera-free native checks pass in3.459 s in one run, including attached actual
article layout, changing lower-region Y and400 ms animation. Same text point
reaches30% screen; motion/settle exclusion, controls, touch cancellation, missing
gaze and optional tail pass. The attached fixture uses the controller directly;
it does not start ArticleFragment, a gaze provider, camera or human collection.
Evidence: `output/article-auto-scroll-region-check-02/summary.json` and copied
XML/build/native/install logs; failed first-attempt evidence remains in01.
Actual live reading comfort and complete Fragment routing remain unverified.
This removes an unnecessary row gate; the exact
cause of the owner's live failure remains unconfirmed. [Contract](article-auto-scroll-prototype.md).

**2026-10-16 optional auto-scroll (verified software checkpoint):** owner-approved
article-only prototype adds default-off aids-menu switch and visible Pause/Resume
while enabled. Same calibrated/smoothed point; no exact-word requirement or model,
calibration, numeric smoothing or frozen Pilot/Replay changes. Trial V1 uses700 ms
same-row support/min5 captures at/below45% physical-screen height,150 ms maximum
sample gap,250 ms maximum age;400 ms step moves row center toward30% screen,
subject to toolbar/viewport/actual scroll bounds. Fresh upper-row support200 ms/
min3 and2 s cooldown precede another700 ms trigger. Touch pauses until explicit
Resume. Controls/lookup offers/definition panels/read-aloud/gaze loss/reflow/pause
interrupt pending support and motion. Enabled-only70% screen-height tail lets
last body lines reach the band. Journals mark support/request/actual motion and
start/end/cancel; scroll-depth events mark auto versus manual-or-layout origin.
Motion and captures through end+150 ms are excluded from reading patterns and
look-away evidence; actual geometry still must settle150 ms. Completed focus
summaries survive operational boundaries; pending anchors/chains do not. Review
caught control-excluded inputs feeding navigation and P13 auto boundaries erasing
focus history; both fixed with regressions, follow-up clean.202/202 focused JVM
checks and both APK builds pass. Owner-approved A56 app/test installs preserve
data/calibration. Initial native run passes3/6; three tests asserted zero-duration
animation completion within one UI callback. Owner-approved test-only retry waits
off UI for next frame/end latch;2/3 rerun checks pass,5/6 distinct native checks
now pass. Remaining check confirms scroll delta then uses an invalid detached
getLocationOnScreen position (SDK36.1 View source confirms zero without AttachInfo).
Fixture now uses descendant-to-root rectangle conversion including parent scroll;
review clean. Owner-approved final test APK rebuild/install passes remaining1/1
native check in0.060 s. All6/6 distinct native checks pass across initial run and
two approved retries, not one six-check run. App hash unchanged. Detached fixture
does not establish production window-inset behavior or human reading benefit.
No camera, recalibration, staging or commit. Upper-region advantage is intermittent, not demonstrated
accuracy improvement. Primary studies, trial rules, source paths and limitations:
[contract](article-auto-scroll-prototype.md). Evidence:
`output/article-auto-scroll-check-03/summary.json`; prior gates retained in01/02.

**2026-10-15 lower-screen vertical error (user observation; unresolved):** owner
reports good top-half vertical accuracy and worse behavior lower down. Owner
clarifies the dot still moves downward but starts to struggle past the middle;
it is not a hard stop, and this predates the blink update.
Owner subsequently reports good tracking again without another install or runtime
change; the quality issue appears intermittent and its cause remains unresolved.
Source-only inspection finds live output has no lower-quarter cutoff: normalized
SVR predictions scale to physical screen pixels, reading smoothing is independent
of position, and the article dot uses the same selected coordinates as scanpaths.
Blink gating can withhold output but does not shift coordinates. Calibration and
live prediction share camera preprocessing and features; no static scaling
mismatch was found. Regional fit quality and posture changes remain unconfirmed
possibilities, not established causes. Calibration fit targets reach90% viewport height
(`CalibrationIdentity.kt`); save-gate checks aggregate regions and cannot establish
bottom-specific accuracy (`MgazeNetCalibrationQualityGate.kt`). The existing
nine-point accuracy task samples20/50/80% viewport rows and reports signed dy per
target (`MgazeNetAccuracyProtocol.kt`, `GazeTestActivity.kt`). No recordings read,
new tests, device actions, recalibration or runtime changes for this observation.
The next diagnostic is the existing raw nine-point check using the current saved
calibration, comparing signed vertical errors and availability across its rows;
it excludes article smoothing/blink filtering and does not reproduce article
reading. Preserve this distinction when interpreting results. Do not infer a
cause or apply a vertical correction without matched evidence.

**2026-10-15 live blink filter (verified software checkpoint):** owner reports
closed eyes still move gaze vertically; old16-point source had eyelid hysteresis,
while MGazeNet only guards pixel eye areas. New live-only per-eye projected lid
gap/width guard reuses initial0.040close/0.055open tuning and requires2fresh
clearlyopen frames at startup/reopening. Article/livePilot explicitly enable it;
provider/camera flags defaultfalse, preserving calibration/accuracy raw contracts
and saved calibration. Invalid/closed frames have nullselectedpoint/no dot callback;
rawpredictions remain excluded diagnostics. Source clock/expiry precedes blinking;
invalid inputs reset smoothing. Brief blinks do not enter look-away sets or reading
time; existing focus grace holds, scanpath gap remains. Metadata/optional per-eye
ratio fields support later review without camera images. Source review caught
blink time lengthening a pending short no-face loss; the corrected focus branch
discards unqualified pending losses and preserves already qualified evidence.
Mixed-loss and repeated-blink regressions are added; follow-up review is clean.
All70/70 focused JVM checks and both APK builds pass. Owner-approved A56 app/test
updates preserve app data and calibration;2/2 camera-free native checks pass in
3.276 s, covering closure/reopening scanpath gaps and focus scroll continuity.
No camera started. Actual eyelid closure still needs the owner's live article
check; these are trial gates, not validated human blink labels. Evidence:
`output/live-blink-check-01/summary.json` and native/build logs.
[Contract](live-gaze-blink-filter.md).

**2026-10-15 font-aware focus (verified software checkpoint):** owner reports
small fonts activate focus too quickly, then clarifies to keep look-away at
3 episodes/30 s and use an 85% maximum mask. Line route uses rendered font dp:
22+ requires 1 ABAB; 18..<22 requires 2 cycles with each supported visit >=160 ms;
below18 requires 3 cycles with visits >=240 ms. Detector baseline remains100 ms.
Each cycle is four new visits/<=8 s; counts retain the existing30 s window.
Resize clears only line-cycle history/demand, including within one size tier.
Completed look-away histories remain. Fade changes90%/98% to60%/85%, all masks
capped85%; Dark40%/60%, 4–5 lines, motion/scroll, hold/recovery stay the same.
Source review's short-detour finding is fixed: an unqualified intervening
established visit breaks pending ABAB instead of being removed from the order.
Follow-up has no actionable findings. Policy16/font-ruleV1/line-ruleV3 and
appearanceV4 record current settings and actual size in traces. Values are trial
safeguards, not validated difficulty cutoffs. Initial run built both APKs and
passed84/85 JVM checks; the new fixture assumed80 ms baseline. Its reviewed
correction uses the actual unchanged100 ms minimum, sampled at120 ms on a40 ms
grid. Owner-approved retry passes85/85 JVM checks and both builds. Approved
data-preserving A56 updates succeed;5/5 camera-free native checks pass in5.007 s:
actual153/217 mask pixels, Dark onset, scroll/fresh-anchor continuity, rendered
size settings and unchanged three-episode return. Small-font activation and
look-away preservation across resizing rely on policy fixtures/source review;
natural gaze accuracy, visibility and comfort remain unvalidated. No camera,
calibration changes, staging or commit. Final evidence:
`output/focus-font-visibility-check-02/summary.json`; initial failed gate in01.
[Contract](repeated-interruption-focus-window.md).

**2026-10-15 footer appearance revision (verified software checkpoint):** owner requests gear
Aids, matching circular font controls and tighter theme spacing. All six controls
share a transparent circle/caption style: theme swatches fixed 144 dp on the left,
font/gear fixed 144 dp on the right, 8 dp outer padding and flexible center gap.
Symbols are 28 dp with 48 dp touch widths; font and gear tint follow article
colors. First build caught plain-Button binding types for font icon tinting;
explicit MaterialButton declarations correct it. Owner-approved retry passes both
APK builds and data-preserving A56 updates. The existing native footer check
passes at 320/360/400 dp with all controls visible, contained, nonoverlapping and
at least 48 dp touch dimensions. Synthetic 320/400 dp previews are visually
inspected. Test-only pre-draw refinement renders detached labels; final native
check passes in 0.513 s. No camera, calibration change, staging or commit.
Evidence: `output/article-footer-style-check-02/summary.json` and previews/logs;
failed build retained in 01.
[Contract](article-text-size-controls.md).

**2026-10-15 article font controls (verified software checkpoint):** owner asks
to restore increase/decrease and proportional line spacing. Ordinary articles now
use remembered 22 sp default, 14–60 sp bounds, symmetric 2 sp steps and system font
scaling. Footer fits themes, A−/A+ and Aids in one row. Spacing is 1.6× plus extra
px equal to rendered font px/22. Resize preserves the first visible source offset
where possible, clears old aid/lookup/demo state, gates reflow, refreshes actual
geometry and keeps the existing 150 ms settling guard. Recording disables size
controls; dedicated Validation/Pilot/Replay keep their controlled 22 dp settings.
Typography pixel metrics accompany aid trace configuration and geometry. Timers
are unchanged, not font-normalized or validated. Initial review caught pause
discarding the pending reading-position restore; the callback/gate now persist
for the same view's next draw, with destruction invalidating them. Follow-up
review has no actionable findings. First app build passed but new Android test
had a View/ViewParent property ambiguity; reviewed explicit-View correction and
owner-approved retry pass both APK builds and 10/10 focused JVM checks. Approved
data-preserving A56 app/test updates succeed; all 7/7 camera-free native checks
pass in 2.002 s, including real footer bounds at 320/360/400 dp, proportional
spacing/reflow, fresh settled geometry and stale lookup invalidation. Fragment
pause/resume is source-reviewed, not exercised by the native fixtures. No camera,
calibration changes, staging or commit. Evidence:
`output/article-text-size-check-02/summary.json`; failed build retained in 01.
[Contract](article-text-size-controls.md).

**2026-10-15 closer lookup bubble (verified software checkpoint):** owner screenshot shows the
V3 bubble too far above the source word. V4 anchors its vector tail to the word's
painted top (layout baseline plus current word ink bounds), with3dp gap, replacing
line-top/font-leading placement. Blue28dp glyph,48dp hit target, right-corner
alignment/mirroring,1,500ms rule and5s lifetime remain. Target must fit viewport
and end above painted source letters; it may occupy unused font leading. No text,
font size/spacing, detector or calibration changes. Plain-string English article
text uses Arial22dp and1.6 spacing; native regression uses those settings on a
third-line target plus actual rendered letter pixels. Source review has no
actionable findings; both APK builds and data-preserving A56 installs pass. All15
focused native checks pass in9.384s (13 offer fixtures plus accepted/ignored Replay),
including 3dp painted-letter gap, normal/mirrored alignment, no hit-area overlap,
geometry cleanup, expiry and tap-only consultation. Normal and article-spacing
synthetic previews visually inspected. Evidence:
`output/lookup-bubble-gap-check-01/summary.json`. No camera, calibration changes,
staging or commit. UI spacing choice; no human benefit/accuracy validation.
[Contract](article-definition-aid.md).


**2026-10-15 blue information bubble (verified software checkpoint):** owner rejects the box,
asks for a more noticeable bubble and a tail aimed at the word's upper-right, then
supplies a blue speech bubble with white lowercase “i”. Native28dp vector uses
blue fill/dark-blue outline/white “i”, transparent exterior, no outer tile/shadow.
The48dp target remains wholly above the source line; bottom-aligned glyph leaves
its tail about6dp above the word. Tail aligns to wordright and mirrors near the
right edge; “i” stays upright. Missing room withholds rather than shifting tail.
Runtime style `WORD_LOOKUP_INFO_BUBBLE_CORNER_V3`; policy15/conditionV10/trigger/
timer unchanged. Prior monochrome placement build and15 camera-free A56 checks
passed; that appearance is superseded. Layout delta review found no actionable
findings. Blue app/test builds and3 selected native checks pass in4.657s.
Synthetic bitmap inspection exposed missing native stroke; filled outer silhouette
with inset blue gradient corrects the border. Final app rebuild/install and both
normal/mirrored native checks pass in0.169s; previews visually inspected. Existing
test APK reused (only app vector assets changed). A56 data/calibration preserved.
Evidence: `output/lookup-bubble-corner-check-03/summary.json`; prior15-case layout
gate in01, initial blue3-case gate/previews in02.
No camera, calibration changes, staging or commit. [Contract](article-definition-aid.md).

**2026-10-15 opt-in word lookup (verified software checkpoint):** owner asks
for an above-word icon before the distracting automatic card, suggesting an outlined
speech-bubble/search glyph. Existing1,500ms word-residence rule now creates an
above-only20dp icon/48dp target with5s fixed lifetime, tap confirmation and no
query/consultation until accepted. Ignore expires; same-word repeats suppressed
until observed departure to another valid word. Geometry/control/lifecycle clears
state. Icon hit bounds exclude article interpretation; rest of reading continues.
Offer may coexist with focus; actual panel consultation keeps prior gating.
Article/Pilot/Replay/demo use `CONTEXT_WORD_LOOKUP_OFFER_V1`, policy15/conditionV10;
trace phase/event records distinguish offer and panel, preserve evidence times.
Replay reserves56dp above its body for fixture icons; real article layout unchanged.
Five seconds is trial interaction tuning, not a published validated dose.

Source review's pre-tap phase labeling finding and first build's ancestor type
ambiguity are fixed. A new JVM assertion wrongly forbade normal focus refresh;
reviewed correction preserves demand/strength/episode assertions. Approved retry
passes199/199 JVM tests and both APK builds. Approved data-preserving A56 installs
succeed;20/21 camera-free native checks pass in21.148s, including expiry, tap-only
query, headline headroom, invalidation, Replay consultation and demo. Remaining
hit-area fixture used untransformed detached getLocationOnScreen coordinates;
reviewed test-only correction now uses visible bounds, retains48dp/edge assertions.
App unchanged. Owner-approved test APK rebuild/install and corrected native
check pass in0.030s. All21 distinct native cases pass across the two invocations;
199 JVM checks and both APK builds pass. Attached Replay rendering and consultation
are covered; natural gaze accuracy and human benefit remain unverified.
Evidence: `output/definition-lookup-offer-check-04/summary.json`,03 build/native logs;
failed software gates retained in01/02 and failed native fixture in03. No camera, calibration changes, staging or
commit. [Contract](article-definition-aid.md).

**2026-10-15 softer dark focus appearance (verified software checkpoint):** owner finds
Dark surroundings too strong, clarifying that the issue is abrupt visual change,
not outside-text readability. Reduce only DARK shading from90%/98% to40%/60%
at initial50%/100% action strength, and introduce a400ms smooth onset. Fade style
stays90%/98%; action strengths/triggers/opening/scroll positions remain. Onset
must follow continuous presentation, survive action refresh/scroll handover and
reset at a real removal/style/operational boundary. It changes paint only, with
no successful draw receipt at zero alpha. Selector `FOCUS_MASK_STYLE_V3` and
per-style settings identify this cosmetic revision; policy14/conditionV9 remain.
Opacity-only13 JVM checks/builds pass. Final onset build initially failed because
the lead removed a still-required per-frame flag; restored and correction reviewed
with no remaining actionable finding. Owner-approved retry passes15 focused JVM
checks, both APK builds and data-preserving A56 installs. Both camera-free native
checks pass in9.957s: deterministic0/51/102 alpha at0/200/400ms, ID-refresh/hold/
handover continuity, removal/style resets, clear rows and actual scheduled Dark
and Fade Replay rendering. Display confirmed awake/unlocked. Evidence:
`output/focus-dark-opacity-check-03/summary.json`; failed build retained in02,
opacity-only evidence in01. No camera, calibration changes, staging or commit.
Trial interaction tuning, not an established comfort/reading-benefit threshold.

**2026-10-14 selectable focus appearance (verified software checkpoint):** owner requests a
dark surround option in Reading aids. Session-only `focusStyle` offers
`FADE_TEXT` (existing default, reader-background mask) and `DARK_SURROUNDINGS`
(black mask), both at90%/98% opacity for initial50%/100% strength. The clear
window/motion/scroll/activation rules are unchanged. Style is explicit on current
focus actions, including held presentation provenance, preferences and action
exports. Menu disables appearance choices with focus/master off while retaining
the selection. Article and Replay demo, live article and Pilot share the choice.
Policy14/conditionV9 identify the new selector; form remains V4.18 focused JVM
checks and both APK builds/installs pass.5/7 native initially pass; existing
dismissal assertion ran before its posted callback, and Replay had chosen focus
but zero render records. Device was found dozing afterward. Reviewed test-only
callback wait, owner-approved test rebuild/install and awake/unlocked-display
retry pass both checks in10.728s. All7 distinct native checks pass across both
runs. App APK unchanged on retry; data/calibration preserved. Evidence:
`output/focus-style-menu-check-02/summary.json`; initial failure/trace/XML retained
under `output/focus-style-menu-check-01/`. No camera/staging/commit. Checks do not
establish human comfort or reading benefit.

**2026-10-14 focus display revision (verified software checkpoint):** owner reports weak
gray surroundings, abrupt motion and disappearance on scroll; selects very faint
outside text rather than hiding it. Current V4 uses article-background90%/98%
opacity at50%/100% initial strength, continuous fade below50%,180ms anchor-safe
window motion and a pinned viewport presentation during translation-only scroll
plus<=1500ms after the last translation. Fresh supported focus takes over.
This presentation has separate trace provenance, no new gaze evidence/action;
scanpath breaks and geometry settling remain. Reflow/text/operational changes
clear the hold.5/4 clear rows and full width remain. Policy13/conditionsV8; demo
uses V4 but still stops on layout change. Review caught row snapping and repeated
disable redraw loops; corrections reviewed with no actionable findings.50 focused
JVM checks pass; both APKs build/install on A56 preserving data/calibration.5/6
native initially pass; the failed detached-parent fixture had no screen-coordinate
translation. Reviewed test-only TextView-offset correction, approved rebuild and
one native retry pass in0.707s. All6 distinct native checks pass across both runs.
Evidence: `output/focus-display-scroll-check-02/summary.json`; initial logs/XML
retained under `output/focus-display-scroll-check-01/`. No camera/staging/commit.
Attached live scroll/human benefit remain unverified. Trial settings, not paper doses.

**2026-10-14 wider/full-width focus (verified software checkpoint):** owner requests4–5
clear lines instead of3 and removal of unshaded side gutters. Current V3 form
prefers5 physical rows, slides at visible/article edges, and uses4 only when5
would leave no shaded surroundings. It never shrinks to3 to create shade. Fewer
available rows remain clear; empty surroundings are honestly withheld. The veil
uses the full visible overlay width and the existing text vertical extent, while
gaze/text geometry remains unchanged. Article/demo/pilot/replay select radius2,
policy12/conditionV7. Independent source review's demo-availability finding was
corrected: preview availability now uses the same appearance resolver. New unit
and native bitmap/demo regressions are saved.14 focused JVM checks and both APK
builds pass; A56 data-preserving installs succeed.3/4 camera-free native checks
pass: actual full-width/five-row pixels and both demo cases. Failed Replay check
recorded no focus because its exact complete synthetic trace ran `navigation`,
not the requested `interruption_place_search`. Test helper now waits for initial
layout/visible requested Spinner selection and asserts returned scenario identity.
Correction source review reports no actionable finding. Owner approved the test
APK rebuild/retry; it builds/installs and the corrected native check passes in
10.002s, asserting both requested scenario identities. All4 distinct camera-free
checks now pass across the initial run and approved retry; failed trace retained.
App APK and calibration/data stay preserved. Evidence/hashes/logs:
`output/focus-window-width-check-02/summary.json`, with the initial failure in
`output/focus-window-width-check-01/failed-native-replay.jsonl`.
No activation, calibration/filter, dictionary timing, frozen measurement or Git
staging/commit changes. This supersedes V2's60% context cap below.

**2026-10-14 return recognition implementation (verified software checkpoint):** owner
authorized the reviewed redesign. Current policy11/conditionV6 confirms returns
from100ms/3 fresh selected-coordinate article-presence samples, allowing movement
between lines without fixation or word assignment.3/30s/500ms and50/100% retained.
Completed summaries survive same-article geometry/definition/control pauses;
pending episodes/visits/anchors clear. Known same-geometry fully visible source
can anchor search for<=30s; fresh presence maintains grace. Aid-specific ongoing
fourth ABAB visit uses8s; finalized fast P11 remains2s and is not double counted.
V2 focus prefers3 clear lines, reduces neighbors above60% viewport height and
withholds empty shading. New Replay search/ongoing-fourth fixtures and regression
tests are saved. Independent review's expired-anchor retention finding is fixed;
correction review has no actionable findings. All **219 focused JVM tests pass**,
zero failures/errors/skips, and both APKs build. Existing authorization covered
data-preserving A56 app/test updates; both installs succeed and **5 camera-free
Android tests pass** in31.501s. Checks cover moving search and ongoing fourth
visits, third/fourth50/100%, definition consultation exclusions, geometry history
retention/fresh anchors and actual bitmap shading. Natural gaze/benefit remain
unverified. Evidence/XML/APK hashes: `output/focus-return-activation-check-01/summary.json`.
No camera/staging/commit. Trial settings are not validated
distraction thresholds. [Current contract](repeated-interruption-focus-window.md).

**2026-10-14 focus activation reconsideration (source review, no app change):**
owner still finds live focus unreliable for both look-away and line alternation,
and suggests searching for the old reading position prevents recovery recognition.
Source confirms interruption completion currently requires a supported line
candidate/fixation; mere return to the article is insufficient. This is a plausible
barrier, not a trace-established cause. Independent read-only review also confirms
same-article scroll/geometry and definition open/close erase completed history;
P11 requires four finalized alternating visits within2s; five clear lines may fill
the viewport while a zero-area shade still reports a successful draw proxy.
Fresh-support display grace and evidence-age gates remain additional barriers.

Recommended next contract: separate return/presence confirmation from line/word
assignment, so moving among lines during place search can complete an episode.
Debounce/rearm presence to avoid counting tracked blips as separate returns;
preserve missing reasons and the chosen3/30s/500ms trial settings. Activate demand
independently of whether an anchor is currently drawable. Retain completed recent
episode summaries through same-article scrolling/consultation, clearing pending
sequences and stale screen anchors; exclude those operational intervals. Require
current geometry before drawing. Consider an aid-specific supported-line-visit
route that recognizes the ongoing fourth ABAB visit; retain finalized fast P11
as a separate descriptive observation. An explicit aid window and presence
confirmation settings still need definition; no replacement timing is validated.
Review actual dimmed area/contrast and log why activation or rendering is withheld.
No new runtime thresholds, build, device operation, capture, staging or commit.

Evidence: current `ReadingFocusPolicy.kt` input/support paths;
`ReadingAidPolicy.kt` focusSupport/prepare;
`ArticleReadingAidRuntime.kt` geometry/consultation resets;
`ScanpathEngine.kt` P11 finalization/window;
`ReadingAidOverlayView.kt` FOCUS_REGION rendering. Reviewer
`focus_activation_audit` independently checked source and proposed safeguards.
Primary precedent rechecked: [SwitchBack (2015), pp.3,6](https://ubicomplab.cs.washington.edu/pdfs/switchback.pdf)
separates phone-facing detection from reading-location estimation, restoring a
saved line on return. This supports the interaction rationale, not our repeated
count, dimming response, timing or an individual distraction diagnosis. The
[existing evidence review](focus-window-improvement-review.md) retains ruler
findings and population/hardware limits. No current natural-reading trace was
available in this review; earlier synthetic successes do not resolve live failure.

**2026-10-14 focus visibility / stricter definition (completed checkpoint):** owner now
reports live focus can trigger but immediately disappears; authorizes stability
through brief loss and line transitions, and longer word-definition dwell.
Current defaults: focus holds3s accumulated usable reading then fades7s,
last supported line retained through<=1s delivery-age grace without renewing
evidence on ticks; new supported line moves it. Operational invalidation and
definition priority remain. Definition threshold rises800→1500ms observed
current-word residence; missing time is not counted. Two initial strengths50/100%
and3/30s/500ms count rules retained. Policy10/conditionV5. Focused source review
has no actionable finding. All **207 focused JVM tests pass**, zero failures,
errors or skips, and both APKs build. First gate passed205/207; the extended
Replay definition dwell crossed a separate legacy cycle window, corrected to
1750ms before the owner-approved passing retry. Existing owner authorization
covers continuing data-preserving A56 aid updates and camera-free checks; both
APK installs succeeded and **3 Android tests pass** in 23.743 s. Native checks
cover brief fourth-gap focus retention, third/fourth strengths, delayed dictionary
popup and line-cycle consultation priority/excluded samples. Natural reading and
human benefit remain unverified. No camera/staging/commit.
Evidence/XML/APK hashes: `output/focus-stability-definition-delay-check-02/summary.json`.
[Focus contract](repeated-interruption-focus-window.md),
[definition contract](article-definition-aid.md).

**2026-10-14 live focus correction / two strengths:** owner reports
normal article guide/bookmark work and repeated-interruption Replay shows focus,
but neither intentional live alternation nor repeated look-away displayed it.
Owner requests two or three strengths for short articles; default focus now uses
50% at 3 completed episodes, 100% at4+; independent cycles1/2+ use50/100%. Fade
remains continuous; 3/30s/500ms thresholds retained. Source fixes ordinary P13
unclassified-fixation gaps erasing completed focus history, and line-only focus
requiring x inside glyph ink despite a valid physical-line assignment. Engine
continuity remains broken, with no cycle joined across gaps. Shared article,
pilot, replay and demo are synchronized; policy9/conditionV4. New real-engine
regressions use live detector settings and rawSample/selected-stream callbacks.
Source review reports no actionable finding. All **200 focused JVM tests pass**;
both APKs build. The same new regression needed three assertion corrections
before the approved passing gate: fresh post-gap support already existed, rolling
cycles overlap, and pending pre-gap visits finalize later. No app source changed
during those retries. Evidence/XML/APK hashes:
`output/focus-live-two-strength-check-04/summary.json`; failed gates retained in
folders01–03. Owner authorized data-preserving A56 updates and camera-free Replay
checks; both APK installs succeeded and **2 Android tests passed** in50.631s,
covering15 scripted runs. Interruption50/100% choices, line-cycle focus and actual
draw proxies passed. Natural live gaze was not collected; no current phone trace
proves the exact live cause or human benefit. No camera, staging or commit.
[Current focus contract](repeated-interruption-focus-window.md).

**2026-10-13 repeated-interruption focus implementation:** owner authorized the
revised feature and selected 3 separate episodes within 30 seconds. V4 focus
counts qualifying unavailable/recovery episodes, not missing frames; 500 ms loss
qualification, 25/50/75/100% initial strength at 3/4/5/6+ episodes, fade over10s
accumulated usable selected-capture deltas. Independent supported line cycles
also trigger without residence/slow AND; counts are separate. Window follows
supported lines; bookmark remains independent. Policy8/conditionV3, typed
episode/action provenance; calibration/filter/frozen instruments retained.
First build stopped on nullable cycle geometry. Approved second gate built both
APKs and passed 188/189 JVM tests; the failed replay assertion is corrected.
Review corrections cover continued-alternation anchoring, expired frequency,
preference-adjusted route strength and tracked outside-text time inflating brief
losses. Work resumed on 2026-10-14 after a helper usage limit; the owner-approved
final retry passes **194 focused JVM tests**, zero failures/errors/skips, and both
APKs build. Four timing regressions and capped/manual mixed-route cases pass.
Source review has no remaining actionable P1/P2 finding; scoped whitespace passes.
Native fixtures compile but phone execution remains pending. No device
install/camera/collection/staging/commit. Current contract:
[repeated focus](repeated-interruption-focus-window.md),
`output/repeated-focus-check-03/summary.json` (test XML and APK/source hashes;
failed gates retained separately in folders 01 and 02).

**Historical 2026-10-13 focus-window improvement discussion:** owner proposes repeated
look-away/no-gaze episodes and inconsistent progression as additional focus
inputs. Current A4 still requires cycle + residence + slow progress, with
residence-based strength. Bounded primary review recommends independent
resumption and supported-alternation routes; count distinct loss/recovery
episodes, retain tracked departure separately from unavailability, and withhold
scattered-jump distraction claims. The window may support perceived focus or
resumption; no study validates the proposed phone trigger/count/strength dose.
Manual support and readable context are useful comparators. Full2026 highlighting
PDF/table now accessible; prior retrieval limits are historical. No runtime,
build/device/camera or staging changed in this discussion. Evidence/proposal:
[focus improvement review](focus-window-improvement-review.md).

**Historical owner clarification before threshold selection:** focus should activate from a specific number of
distinct unavailability/recovery episodes in a recent time window, not one
sustained absence. Count complete episodes, not missing frames; one long absence
counts once. The independent bookmark/line guidance handles placekeeping after
a single interruption. Focus then dims surroundings around a supported reading
area, with potential frequency-based prominence/duration and easing after stable
continuation. Exact count/window/dose remains unset and unvalidated; no focus
runtime change yet. Uncertainty applies equally to line alternation/scattered
paths and missing gaze. Repeated tracker faults can mimic repeated interruptions;
retain reason distinctions and avoid a conclusive distraction label. This
supersedes the earlier single-gap focus suggestion.

**2026-10-13 sustained missing-gaze bookmark correction:** owner expects looking
away to trigger even when tracking disappears. Added a 500 ms delivery-time
confirmation of explicit unusable-gaze reports, marking the last supported line.
Brief recovery cancels pending loss; geometry, controls, dictionary, pause and
disable clear memory. Reason `sustained_gaze_unavailable` is not an attention or
difficulty diagnosis. Nullable departure capture bounds and separate delivery
evidence avoid invented capture samples. Policy 7 / bookmark rule V2 / smoothed
condition suffix V2; calibration/filter, outside/jump rules and fixed visual form
remain. Replay adds `bookmark_tracking_loss`. All **173 focused JVM tests pass**,
app/test APKs build, whitespace passes and source review has no actionable
finding. Native runtime-timer/replay fixtures compile; phone execution is pending.
No device install/camera/collection, staging or commit ran. Evidence:
[bookmark contract](scanpath-reading-bookmark.md),
`output/bookmark-tracking-loss-check-01/summary.json`.

**2026-10-13 authorized calibrated-smoothed implementation:** owner explicitly
requests one path for the dot, live scanpaths and aid decisions. Article and live
pilot now select the exact provider `x/y` output as `article_smoothed_v1`;
mapping/grouping/centroids, aid anchors/bookmark checks and pilot scoring use it.
True calibrated pre-filter raw values are retained separately; missing filtered
input abstains without fallback. Existing calibration and filter 0.7/0.002 are
unchanged. Replay supplies honestly labeled post-filter synthetic trajectories,
raw originals null, without pretending to run the smoother. New policy 6,
schema-2 traces and smoothed condition IDs distinguish the route; frozen v6 and
schema-1 diagnostics retain their own identities. Host/Android/JVM fixtures
cover disagreement, missingness and provenance. **166 focused JVM tests and 10
host checks pass; app and test APKs build.** The first host invocation failed
before tests because of the sibling import path; owner-approved discovery retry
passes. Focused source review's old replay policy assertion and missing
provider/display audit pairing were corrected. Native fixtures compile but were
not executed on a phone for this build. See [stream contract](calibrated-smoothed-scanpath-path.md) and
`output/smoothed-scanpath-check-01/`. No device install/camera/collection, staging
or commit was authorized for this implementation task.

**Historical 2026-10-13 calibrated/raw/smoothed clarification:** owner challenges using raw
for aid decisions after smoothing work. Current `rawX/rawY` are calibrated SVR
screen predictions, not uncalibrated camera/model features. Saved calibration and
output eligibility gates remain upstream. Article One Euro filtering (minCutoff
0.7, beta0.002) supplies displayed x/y; new scanpath engine and its aid decisions
use the calibrated unsmoothed branch. The raw branch was deliberately matched to
the research/replay protocol; the checked records do not establish its superiority
for interaction. One Euro's author documentation describes jitter/lag tradeoffs
(https://gery.casiez.net/1euro/). Calibration, smoothing precision and actual target
accuracy must not be conflated. A prospective filtered aid route must keep its
mapping, candidates and triggering on the same declared stream while preserving
raw research records; mixing raw duration support with another filtered word
anchor is not an acceptable shortcut. This was an explanatory source review,
not authorization to replace the live engine or frozen protocol. No runtime,
smoother parameters, device operation or test changed.

**Historical 2026-10-13 dot/tolerance clarification and continued dictionary report:** owner
asks whether the orange dot limits matching and reports that popups open without
definitions. Source inspection confirms 18 screen-pixel drawing radius only;
ordinary scanpath word/line mapping uses raw coordinates while the dot may show
smoothed ones. There is no padded nearest-word radius. Current 0.5-line-height
dispersion groups candidate samples, not model uncertainty; 80% assignment vote
is consistency, not accuracy. Calibration saves no per-user target uncertainty
radius. No detector, tolerance, radius or frozen protocol change is authorized
by this explanatory question. Any wider target region needs a measured error
bound and ambiguity handling rather than assigning every overlapping word.
Definition popup appearing means selection fired; empty content is a separate
lookup/coverage/rendering question. Live card field inspection returned no
readable hierarchy; no screen image or gaze recording was collected. A public
exact-word Datamuse analyst probe returned 200 in 493 ms, one exact entry/six
definitions. Added one exact-match online fallback separately from gaze:
Free Dictionary API → Datamuse exact match → glossary. No fuzzy substitution;
provider/route metadata updated, offline replay/pilot remain bundled-only.
Source review has no actionable P1/P2 finding. All 125 focused JVM checks and
both APK assemblies pass. Authorized data-preserving A56 updates installed.
Nine camera-free checks pass in 1.642 s: keyboard/hello primary live, analyst
Datamuse live in 794 ms. A targeted native analyst popup check also passes in
3.529 s, definition/card/Close verified with a fresh 3,467 ms lookup. Ten distinct
cases across two invocations, not one ten-test run. Native fixtures establish
delivery but not the owner's exact natural-reading state. Evidence:
`output/dictionary-article-check-03/summary.json`. Datamuse docs require keys from
Feb 1, 2027; future configuration remains necessary. No camera/gaze, participant
data, staging or commit ran. Matching/detection tolerance was not changed.

**2026-10-13 fresh dictionary connection check:** owner reported continuing
dictionary trouble and authorized ADB retry, data-preserving app/test updates
and camera-free phone checks. A56 / API 36 Internet permission is granted. Fresh
requests bypass both per-instance memory and Volley HTTP cache: keyboard live
definition in 408 ms populated the native card and Close worked; unbundled hello
returned live in 83 ms; analyst returned labeled glossary fallback in 79 ms.
All nine phone checks pass in 0.792 s; 19 focused JVM checks and both APK assemblies
pass. Installed app hash matches the built APK. Default reading retains HTTP
caching; `allowHttpCache=false` is only used in explicit connection fixtures.
Evidence: `output/dictionary-connection-check-02/summary.json`. This confirms
phone-to-service connectivity and native fixture delivery, not complete service
coverage or natural gaze-triggered article behavior. The analyst host request
returned 404; its error-body parser did not preserve the payload. No new provider
was integrated. No camera, gaze, participant data, staging or commit ran.

**Historical 2026-10-13 fixed entry and dictionary feedback correction:** owner reports
running the app successfully and now wants **Aids** fixed in its existing bottom
position. Article tap/scroll reveal logic is removed; fixed button coordinates
remain excluded from article interpretation and raw diagnostics retain their
original recording route. Button is disabled during diagnostic recording.
Owner saw unavailable/connection text for **analyst** in a normal article. One
host request returned HTTP 404 in 406 ms; its payload and phone cause are not
verified. Merged debug manifest has Internet permission. Dictionary feedback now
distinguishes unsupported token, exact missing-entry response, service failure,
offline-only absence and unknown absence. Added original, clearly labeled
analyst glossary fallback; live-first lookup, retryability, successful cache and
consultation safeguards remain. Source review caught the disabled-entry diagnostic
dot exclusion; recording now updates its full-screen dot before those exclusions,
independently confirmed in source. Initial build compiled main/Android sources but
the new JVM fixture had a nullable-property compile error. Owner-approved corrected
retry passes all 119 focused JVM checks and both APK assemblies; whitespace passes.
Evidence, initial failure, XML and hashes:
`output/fixed-aids-dictionary-check-01/summary.json`. No new device operation,
camera, staging or commit. Actual fragment/dictionary phone behavior remains untested.

**Historical 2026-10-13 Reading aids menu and sentence prototype:** owner approved both and
selected tap-the-article access. Bottom **Aids** starts hidden, toggles on body
tap and hides on scroll/menu/pause. Shared sheet has independent switches and
exclusive Line/Sentence context options; coarse supported lines select text
sentences, with containing-paragraph fallback or abstention on ambiguity, without
word-level targeting. Current guides use behind-letter gray at 20/255 = 7.843%
default opacity; no proposed 20% pulse or word-step release. Article/pilot/replay
now select definition V4 / policy 5; evaluator layout and action ranges are
versioned. Scoped menu suspension excludes delayed captured frames and screen
coordinates use the existing helper. Source review findings resolved. After an
initial cache-denied attempt and a stale demo-strength assertion (113/114 pass),
owner-approved corrected retry passes all 114 JVM checks and both APK assemblies;
whitespace check passes. Logs, failed/corrected XML and hashes:
`output/guide-menu-check-01/summary.json`. No new device installation or camera
action; actual article taps/insets/lifecycle remain untested on phone.
[Current contract](reading-aid-menu-and-sentence-guide.md) retains research limits.
Prior phone results refer to V3, not current V4.

**Historical 2026-10-13 guidance-unit challenge and accuracy constraint:** owner questions
the line-highlight rationale and suggests sentences, while requiring low reliance
on word-level gaze accuracy. [Evidence/design review](scanpath-guidance-evidence-review.md)
records GazePrompt's navigation-only support, EyeBookmark's interruption-context
benefit, and Vatány et al. 2026 (80 adults, desktop hover): sentence/paragraph
ratings exceed static perceived-attention ratings, without detected speed or
comprehension gain. No study selects a winning automatic phone guide or validates
the 8%/20% opacity pulse. Proposed sentence/paragraph context units would come
from text segmentation plus supported coarse regions, with no guessed sentence
on shared/ambiguous lines. Retain line guidance as an option. The prior proposed
P01/P02 word-step release is withdrawn; choose the unit before refining intensity.
These are design candidates; installed app and previous test evidence are
unchanged. No new build, device action, recording, staging or commit ran.

**Historical 2026-10-13 independent line-highlight proposal:** owner clarified that A1 must
have its own rule; the coordinated cycle/focus proposal is superseded. Current
code still follows position at its fixed adaptive baseline; no APK/code change
in this design review. [Independent rule](scanpath-line-highlight-rule.md) proposes
baseline guidance, entry emphasis on a confirmed uninterrupted line change, then
baseline after supported same-line forward word movement or the bounded existing
transient lifetime. It consumes no focus/cycle/bookmark/definition activation.
GazePrompt sections 3.1.1/3.2.3/4.2.1 support line entry/stabilization and observed
landing/progress criteria; the boosted phase, release, normalized strength and
timeout are unvalidated NewsMead choices. No paper-backed difficulty dose is
claimed. Owner requested exact percentages and asked about gray: proposed A1
appearance is `#808080`, actual opacity 8% for same-line reading and 20% on
confirmed line entry, 0% when hidden. This replaces the prior proposed normalized
25%/reader-cap appearance, not installed behavior. Current yellow 25% strength
actually draws at 20/255 = 7.84% opacity. Gray/percentages and behind-letter fill
are unimplemented design choices requiring theme/reader checks; no numerical
paper justification or universal distraction reduction is claimed.
Digital Reading Rulers direct PDF retrieval timed out during the prior
review; no new exact final-table claim was drawn. Prior software/device evidence
remains unchanged and does not test this proposal.

**2026-10-13 last-line bookmark:** owner requested replacing expected-next-line
prediction and selected both look-away and non-normal line-jump triggers.
Current article/pilot/replay select definition V3 / policy 4 / `READING_BOOKMARK`,
anchored at the last supported source line with fixed gutter appearance. Outside
activation requires sustained usable coordinates beyond the article rectangle;
tracking loss alone retains memory silently. Source/transition memory are separate,
so unknown gaps cannot create jumps or clear a held cue on first elsewhere recovery.
Geometry/lifecycle/consultation and reader disable clear the cue. Demo and raw
replay are aligned; source/departure evidence is retained by both trace writers.
EB15/CW12 support known-interruption location cues, not the exact autonomous
phone trigger/bar. [Bookmark contract](scanpath-reading-bookmark.md) gives rules,
sources and limits. After owner-approved correction of a nullable-geometry build
error, 94 focused JVM checks and both APK assemblies pass. Data-preserving APK
updates installed on SM-A566B/API 36. Camera-free checks passed 30/32 initially;
the two new assertions used the wrong JSON field. After correction and explicit
retry approval, both pass in 28.861 s. All 32 distinct cases are covered across
those invocations; this is not a single clean 32-test run. Initial failures,
corrected logs and installed hashes remain in `output/reading-bookmark-check-01/`.
No camera/live gaze, calibration, private recording, participant validation,
staging or commit ran.

**2026-10-08 A56 dictionary check 06:** owner authorized the connected-device
check and explicitly approved retry after a test-only local-lateinit cleanup
compile error. Corrected app/test APKs installed with data-preserving updates on
SM-A566B/API 36. One camera-free invocation passes all 15 checks in 0.835 s:
real-repository public `keyboard` definition delivered in 443 ms, live API
attribution and native-card content/Close verified, plus offline, popup lifecycle,
synthetic consultation and article-presenter checks. The new HTTP smoke test is
opt-in (`liveDictionarySmoke=true`), so normal test runs do not send requests.
Evidence/hashes and initial failure: `output/dictionary-device-check-06/summary.json`.
No camera/live gaze, human reading, private-recording access, staging or commit
ran. Actual gaze-triggered article reading remains a manual check.

**2026-10-08 live dictionary:** the owner requested actual online word
definitions. Article/demo and live pilot now use successful online cache →
Free Dictionary API → bundled glossary on failure. Synthetic pilot/replay stay
bundled-only. Fallbacks remain retryable; cancellation/duplicate delivery guards
preserve the popup lifecycle. Pilot records `online_then_bundled` for live runs.
NewsData has no dictionary endpoint and its key is not sent to this service.
One public host probe returned `keyboard` with definitions/attribution, HTTP 200
in 395 ms. All 26 focused JVM checks pass; both APKs assemble from a fresh build;
independent source review has no finding. No device install/tests or live gaze
ran. Evidence: `output/dictionary-online-check-01/summary.json` and `api-probe.json`;
current behavior/limits: [article definitions](article-definition-aid.md).

**2026-10-08 timing/font evidence audit:** current article text is locked at
22 dp (`StudyConfig`); geometry and dispersion use actual rendered line height,
but aid timing/strength have no font-size adjustment. Hyrskykari et al. (2003),
sections 4.2.1/5.2, provide an approximately 800 ms lexical-help precedent with
word-length/frequency adjustments, not validation of our flat current-visit timer.
The 1000–2000 ms focus ramp and 25% line-guide baseline remain prototype settings.
GazePrompt supports user-adjustable delays; Franken et al. show font-dependent
fixation measures, not an aid timing formula. Exact links and limits are in
[aid runtime](scanpath-aid-runtime.md). No detector, timing or font code changed.

**2026-10-08 focus default:** the owner requested enabling the focus window by
default for now. Shared article/pilot/replay preferences now enable it; existing
cycle/residence/slow-progression requirements and reader opt-out remain. Pilot
shadow/no-aid conditions still suppress display. All 30 focused policy, definition
and replay JVM checks pass, and the debug APK assembles. The default is included
in the A56 APK installed for dictionary check 06; its human-reading behavior is
not established by those dictionary checks. See [aid runtime](scanpath-aid-runtime.md).

**2026-10-08 A56 check 05:** the owner authorized the connected phone check.
Merged app/test APKs installed with data-preserving updates on SM-A566B/API 36.
One camera-free run passed 33/38 checks. Three failures are stale popup-fixture
bytecode calling the old action constructor; two are continuation replay setup
requiring an unnecessary quarter-line destination center. The generator now
accepts actual supported landing/arrival centers while retaining source progress,
visibility and timing guards; three real-engine JVM regressions are added and
source review has no finding. The first fresh-build command stopped before
compilation at a PowerShell argument error, now corrected. Owner-authorized fresh
rebuild passes 83 JVM checks and the corrected A56 retest passes all 38 in
94.573 s; popup constructor bytecode now matches. Both corrected APKs are
installed. Home launch succeeded but its UI snapshot showed the launcher and
zero app nodes, so phone feed display/publisher extraction remain unverified;
no retained app crash message was found. No camera/calibration, private journal
export, staging or commit ran in this task.
Evidence/limits: [device check 05](scanpath-device-check-05.md),
`output/scanpath-device-check-05/`. Preserve the initial failed result.

**2026-10-08 Recommendation integration:** the owner authorized merging fetched
`origin/Recommendation` at `501e9be` into `measurement/scanpath-pilot`. The NewsData
key is configured only in ignored `local.properties`; current feed mode is online
(`OFFLINE_MODE=false`), while the existing login bypass remains enabled. Current
article aids/demo/pilot/replay and frozen estimator/protocol settings are retained.
Ten conflicts are resolved. Article readiness awaits extraction; callbacks and
pending gaze restart respect view/lifecycle/calibration boundaries. Usage periods
reopen after backgrounding; home callbacks retain the latest selection. A source
review found no remaining actionable issue after repairs. One computer-side API
probe returned 10 articles. All 80 focused JVM checks and both APK assemblies
pass, including current continuation checks; Android fixtures compile but have
no current device result. The historical compile failure remains retained.
Exact resolution and limits: [Recommendation integration](recommendation-merge.md).
Evidence: `output/recommendation-merge-check-01/`. No device capture/install,
calibration, private-log export or push ran.

**2026-10-04 continuation-marker amendment:** the owner approved replacing the
post-P09 landing cue with an expected-continuation cue. Supported first-quarter
to last-quarter progress remembers the next fully visible LTR line; a supported
landing elsewhere offers a fixed marker at that expected line. A normal next-line
arrival produces no marker. This is optional navigation assistance, not an error
or difficulty diagnosis. Appearance is fixed 6 dp / alpha 240, with the existing
4 dp gutter gap; positive strength controls do not attenuate it. New conditions
use `DEFINITION_PROFILE_V2`, policy v3 and `PILOT_DEFINITION_*_V2` /
`REPLAY_DEFINITION_V2`; detector/frozen protocols retain their identities. Exact
state, evidence limits and sources: [continuation marker](scanpath-continuation-marker.md).
The prior V1/evaluator/device checkpoints below remain historical.
Both APKs assemble and Android fixtures compile; they have no current phone
result. The first JVM gate stopped at a corrected test-only field-name error;
retest approval is pending under the one-failed-attempt boundary. Scoped
whitespace and resource XML checks pass. Independent review findings were
resolved, including first-prefix censoring, row wobble and departed-finalization
handling. Evidence/hashes: `output/continuation-marker-check-01/summary.json`.
No device install/capture, staging, commit or participant validation ran.

**2026-10-04 evaluator alignment:** the owner explicitly selected matching the
current article aids for pilot and replay. Both now use `DEFINITION_PROFILE_V1`,
gutter markers and the same near-word popup; the obsolete strip reserves 0 dp in
every condition. Pilot conditions are `PILOT_DEFINITION_*_V1`; replay is
`REPLAY_DEFINITION_V1`. Their UI/dictionary mode is recorded separately from old
magnifier traces. Pilot live lookup retains the offline glossary/online fallback;
synthetic pilot/replay disable network. Consultation boundaries preserve raw
callbacks and exclude card intervals/late captures from article paths. Prompted
tasks remain aids-off; shadow/display share geometry. Replay uses the shared
six-stage article presenter with callbacks detached from closed runtimes.
Historical detector-only replay assertions remain; current display checks are
separate because consultation changes later article evidence. See
[pilot](scanpath-live-pilot.md) and [replay](scanpath-replay-screen.md). Independent
source review found no P1/P2 issue; direct line-cycle and replay-to-demo regressions
address its coverage limits. 64 focused JVM tests and both APK assemblies pass;
updated Android fixtures compile but have not run on a phone. Evidence/hashes:
`output/evaluator-current-aids-check-01/summary.json`. No device capture/install,
staging or commit ran.

**2026-10-04 article line-start spacing:** the owner's screenshot showed the
marker covering the first letter. The article body reserves 16 dp start padding
from inflation; article overlays put the marker in this gutter with a 4 dp text
gap. Both live and demo construction select this placement. Existing geometry
already accounts for TextView total padding; marker clipping abstains. Text may
wrap differently in the narrower column. A camera-free pixel rendering regression
compiles for separation from the text. App/test APK assembly, article XML parsing
and whitespace checks pass; device execution/appearance remain pending.

**2026-10-04 definition layout/offline amendment:** the owner clarified that the
card should follow an internet template and requested a light offline dictionary.
The native card adapts Flowbite React's Default Popover (shaded header, divider,
border, pointer) using NewsMead colors/fonts, with the larger compact close control
and source highlight. A 75-entry original English glossary supplies immediate,
explicitly attributed offline definitions. The article demo prefers a covered
actual visible source word; no text substitution. Uncovered words retain the API
route/unavailable state; unsupported languages still abstain live. Source links,
coverage and check results are in [Article definition aid](article-definition-aid.md).
64 focused JVM tests pass; app/test APK assembly and popup XML parsing pass.
New offline/word-selection Android fixtures compile but have not run on a device.
Phone rendering of this revision remains unverified; no device install/capture,
staging, commit or backend change ran.

**Historical article UI checkpoint — 2026-10-03:** the owner rejected the top word
magnifier and requested a definition popup and demo inside the loaded article.
[Article definition aid](article-definition-aid.md) now records the implementation,
H03/M25 lexical-assistance precedents, transfer limits and consultation exclusions.
Ordinary articles select separate `DEFINITION_PROFILE_V1` / `EXPERIMENTAL_DEFINITION_V1`;
the configurable 800 ms current-word residence trigger is exploratory, not a
validated difficulty cutoff. Cards remain during consultation and preserve source
layout; open/close boundaries exclude consultation and late captures from article
sequences while retaining numeric raw callbacks. The source word stays highlighted
using a noninteractive overlay while its definition is open. Close uses a large
vector × and a compact 28 dp gray box inside a 48 dp tap area, without source
spans/font changes. Callback resets are deferred
until engine dispatch completes. The explicit article demo stops the provider,
cycles real aid views and keeps camera off afterward. `origin/Recommendation`
was fetched at `501e9be1f83d4d962dc4931ef9044ab1f18a3e1b`, without merge; no dictionary
endpoint was found. A replaceable English Free Dictionary API adapter provides
actual definitions and bounded caching; unsupported languages abstain live.
61 focused JVM tests pass and app/test APKs assemble. New camera-free Android
fixtures compile but have not run on a device; the earlier A56 15-test evidence
applies to the preceding build. The evaluator migration above supersedes this
checkpoint's deferred replay/pilot update; frozen protocols retain their meaning.
No device install/capture, staging or commit ran.

Help older adults sustain attentive reading through real-time visual assistance
in the on-device Android NewsMead prototype. The immediate development priority
is to complete the integrated live scanpath-to-visual-aid prototype before
participant measurement/evaluation. Build software replay checks during
implementation; carry forward existing gaze evidence. The user explicitly
allows continuous, combined, or variable
intensity scaffolds; four mutually exclusive levels are an existing implementation,
not a fixed requirement for future research. Re-entry is only one possible use.

Owner clarification on 2026-09-30: visual help need not be called scaffolds or
organized as levels. Replace the legacy design when a better suited,
study-defensible design exists. The [aid design and evidence contract](scanpath-adaptive-aid-design.md)
now specifies independent aid types with within-type strength, per-rule source
provenance and exact trigger/form/outcome limits. Prioritize line guidance and
localized word support; optional ruler and word-progress forms get distinct
versioned IDs. Preserve A4's focus-region meaning rather than pooling variants.
No forceful intervention follows every return/cycle, and poor tracking cannot
increase assumed difficulty. The [runtime checkpoint](scanpath-aid-runtime.md)
now implements the independent line/word/focus route, controls, variable
word/focus strength and bounded action provenance. The
[matched pilot instrument](scanpath-live-pilot.md) now provides prompted tasks
and ordinary-reading raw/shadow/display conditions. Further authorized device
execution and participant evaluation follow this integrated build.

First device check on 2026-10-01: Samsung SM-A566B/API 36, target APK from
`e5fa5b2` installed with data-preserving update. Four synthetic geometry/magnifier
tests ran: one passed, three failed on missing detached-fixture layout params.
After local layout-parameter corrections and explicit owner authorization, the
corrected test APK was installed and all four tests passed (`OK (4 tests)`).
No gaze capture or participant measurement was started. Record and limits:
[device check 01](scanpath-device-check-01.md). Keep this distinct from natural
gaze or visible article-aid validation.

The owner then requested the other phone: SM-G991B/API 35. Identical target/test
APKs installed with `-r`; the same four synthetic tests passed on the first
attempt. Source checkpoint `9559c95`, hashes/results/limits in
[device check 02](scanpath-device-check-02.md). Both phones pass these synthetic
checks; visible article layout, live gaze and human validity remain unmeasured.

The owner authorized the interactive pilot screen's first replay milestone.
[Scanpath replay screen](scanpath-replay-screen.md) now provides five explicit
raw-coordinate scenarios through actual rendered word/line geometry, the existing
engine/policy and real aid views, with controls and full private synthetic traces.
Replay stops on geometry change, pause, Stop or changed controls/scenario. Its
capture clock is scheduled synthetic elapsed time; delivery is actual callback
time, and >100 ms scheduling lag aborts. Synthetic identity skips calibration
and creates no camera/provider. This is the scripted inspection route; live
prompted tasks and matched human recording remain pending. Check the guide's
dated build/device results before inferring readiness. All 34 focused JVM tests
and APK assembly pass; [device check 03](scanpath-device-check-03.md) retains the
initial test lookup failure, authorized correction/retest and final five-test
pass on SM-G991B/API 35. Actual laid-out replay and variable word/focus choices
have software evidence on that phone; the synthetic word preview was inspected.

**2026-10-03 implementation:** starting from `50ff2b7`, a separate live pilot
screen now provides ten geometry-derived prompted word/line tasks, continuous
engine processing, hidden-dot ordinary reading and no-aid/shadow/display
conditions with the same reserved viewport. Private JSONL retains raw inputs,
capture-time prompted intervals, engine acceptance, full evidence/action/draw
provenance, adherence reports and shutdown counters. Per-target agreement and
unshown opportunities are separate from descriptive global alignment; holds
cannot label physiological refixations. Five camera-free activity tests compile,
51 focused JVM and 8 host summary tests pass, and both APKs assemble. Focused
independent review found no remaining issue after scoring/lifecycle fixes.
Device execution of the new pilot awaits task-specific authorization; no live
gaze or human data were collected. Read [the guide](scanpath-live-pilot.md) for
units, denominators, time assumptions, partial-file rules and remaining study
procedures. Do not infer a completed participant plan from default task settings.
No human validity follows from synthetic replay.

**Trigger review/demo — 2026-10-03:** [evidence review and flowchart](scanpath-trigger-evidence-review.md)
rechecked primary methods/outcomes and current decision predicates. Line guidance
has the strongest intervention precedent; the exact word AND trigger differs
from GazePrompt's OR trigger, while the cycle-to-focus and monotonic dose rules
lack direct validation. The [replay screen](scanpath-replay-screen.md) now adds
an explicit render-only Demo cycle with seven four-second stages, Previous/Next
and Stop. Manufactured actions never enter detector/policy/results/traces, and
no camera/calibration opens. Independent review fixed insufficient max-word
height in replay/pilot strips: 68 dp reserved height remains, vertical padding
is 10 dp. All 53 focused JVM tests pass and app/test APKs assemble; new device
execution awaits task-specific authorization. Preserve this status when making
panel claims. No human difficulty/aid-benefit evidence was collected.

**A56 compatibility — 2026-10-03:** the owner explicitly approved installation
and camera-free checks after automatic installation review required explicit
A56 approval. Both APKs installed with `-r`. [Device check 04](scanpath-device-check-04.md)
records one bounded 14-test attempt: **6 pass, 8 fail**. All five pilot tests
pass, including all ten prompted plans and shadow/display behavior. Four demo
tests fail readiness; four replay tests abort because tiny negative callback
lag was treated as late. Later replay scenarios were not executed. Known
synthetic traces and an app-only pilot preview are retained locally. Device
attempts stopped; local fixes now retry unchanged early deadlines and use the
actual compound-padded word-strip height. Source review closed; all 56 focused
JVM tests and both APK assemblies pass. Improved status diagnostics and a
centered-strip regression compile. The owner then authorized the corrected
installation/retest: **all 15 tests pass, 50.908 s** on A56/API 36. All five replay
scenarios completed with expected patterns/aid draw proxies, strict causal
timing and synthetic callback lag 0.013–2.879 ms. Demo/renderer and all five pilot
checks pass. App-only demo word/focus and replay word previews are readable.
Corrected hashes/results/evidence paths are in check 04; this build has not been
tested on S21. Home → Scanpath replay → Demo cycle is ready for camera-free
inspection. Earlier pending-device statements describe pre-attempt status.
No camera/human collection, staging or commit ran.

The [scanpath-to-visual-aid implementation proposal](adaptive-visual-aid-proposal.md)
is the current **NewsMead feature specification**. Re-reading the source task
**Explore feasible gaze metrics** clarified the owner's final intent: preserve
ordered gaze-to-text visits, classify **observable navigation patterns**, then
select or parameterize visual help. The owner subsequently clarified that the
proposal must derive and evaluate **both word-level and line-level** scanpaths,
trace the order categories to primary papers, and connect each visual aid to
the observed situation it might address. Word-level analysis is a feasibility
track despite weak current exact-word placement. Slow line progression to a
focus window is an experimental mapping with no identified direct intervention
evidence; window restrictions may remove useful parafoveal text. A universal
reading-instability construct, supervised support-need model, and fixed
four-scaffold ladder are **not** required. Aid type, target, strength, duration,
and combination may vary during ordinary reading; the candidate policy and its
benefit must be independently tested. The current proposal specifies code
boundaries, derivation, a descriptive classifier, a configurable aid policy,
primary-study precedents, and separate measurement, pattern, and intervention
gates. An
[illustrated PDF companion](../output/pdf/newsmead-scanpath-visual-aid-proposal.pdf)
shows the same derivation, order-to-aid bridge, and proposed UI states; its
examples are schematic, not observed NewsMead scanpaths. The earlier
[three-state line-guide research plan](adaptive-visual-aid-research-plan.md)
is retained as a historical narrower option, not the current requirement.
The [known-interruption bookmark](evidence-backed-reading-support-proposal.md)
and [fixed pattern/scaffold proposal](scanpath-scaffolding-proposal.md) are
historical options. The [critical audit](gaze-proposal-evidence-audit.md) still
applies to unsupported line-pattern meanings and aid mappings; the
[primary-study ledger](scanpath-scaffolding-evidence.md) records evidence and
transfer limits. An HSMM is at most an offline exploration; the old weighted
score and Mahalanobis distance do not establish reading instability. Preserve
useful rereading and abstain across ambiguous or missing gaze. The thesis source
still describes the legacy index and a circular recovery-latency endpoint;
align it only after the method and independent outcome are fixed.

The [order-category provenance table](scanpath-scaffolding-evidence.md#provenance-of-the-proposed-order-categories)
identifies Olivier's word-distance alphabet and Yamaya's multiline segment
classes as precedents, while marking NewsMead's physical-line bins as original
operational definitions. Preserve separate fixation-level word transitions,
word visits, and line visits. Olivier's same-word `Rfx` is not a long line hold;
`HOLD` is duration, not order; `BREAK` marks missing sequence continuity. The
historical forward multi-line symbol `FWD_LONG` (formerly `FWD_SKIP`) must not
imply that intervening text was unread.

The [published sequence-to-behavior table](scanpath-sequence-behavior-table.md)
is the current lookup for the exact word, segment, line, and longer-path labels
used in the proposal. It separates observed geometry from model/author
interpretations and maps NewsMead arrow examples only as proposed descriptors.
The [pattern ground-truth audit](scanpath-pattern-ground-truth-audit.md) identifies
which studies used human movement annotations, known geometry, instructed tasks,
experimental text conditions, or exploratory clusters. Exact line cycles such as
`L4 -> L5 -> L4 -> L5` remain NewsMead illustrations, not validated difficulty
classes.
The [original figure reading guide](scanpath-source-figure-guide.md) links directly
to primary-source pages and images for NN/g's named web patterns, the 2026
anchor-search-confirm process, sentence-region rereading prototypes, Hyönä's
expository reading profiles, Parshina's sentence-path prototypes, and Chen's
task-reading strategy transitions. It distinguishes individual gaze plots from
heatmaps, aggregate profiles, and authors' explanatory figures and records
task/evidence limits. Do not substitute generated redraws for original figures
when the owner asks for verifiable paths.
The [reading-difficulty scanpath evidence guide](scanpath-reading-difficulty-evidence.md)
separately audits paths against independent reading-skill, comprehension, and
controlled-difficulty outcomes, with original figure and table locations. No
reviewed named path is a conclusive live difficulty sign for older adults reading
news on a phone. Notably, regressive paths relate differently to comprehension
across genres, and news-page gaze studies found so far do not validate a
path-to-difficulty outcome. Its quantitative audit shows that published cluster
shares, correlations, model coefficients, R², and benchmark AUROCs are not
calibrated `P(current difficulty | path)` or aid-benefit weights. Preserve
candidate paths as observations pending NewsMead measurement, independent
outcome prediction, calibration, and randomized aid-benefit tests; low gaze
quality is an abstention reason, not a difficulty signal. The current
[implementation proposal](adaptive-visual-aid-proposal.md#confidence-and-weights-for-adaptive-decisions)
records how a future weighted policy would be learned and gated.
The [classification dictionary](scanpath-classification-map.md) and its
[table-form PDF](../output/pdf/newsmead-scanpath-classification-map.pdf) group
movement labels, ordered path patterns, empirical whole-path clusters, and
model-inferred phases. Those are different types of labels; sequence length
alone does not define their type. The dictionary names primary studies and
marks NewsMead examples as proposed descriptions rather than published classes.
The [visual-aid evidence map](scanpath-visual-aid-evidence-map.md) and its
[PDF table](../output/pdf/newsmead-scanpath-visual-aid-evidence-map.pdf) connect
those observations to tested aids and separates movement-matched results from
untested NewsMead trigger rules. Current closest aid evidence is for return-
sweep/next-line localization; automatic word magnification after refixation,
focus windows after slow line residence, and cues for spontaneous rereading
remain unvalidated. Preserve context and reader control during rereading.

The [public gaze-sequence capability-model survey](scanpath-capability-models.md)
checks EyeBench and individual LSTM, CNN, transformer, and HSMM precedents against
their actual labels, public code, validation splits, and NewsMead fit. EyeBench is
the strongest offline comparison framework, but its published models do not
validate a live aid-need detector for older phone readers. No candidate model
has been selected for the app, and no task-ready trained weights were verified.
The survey also covers phone front-camera precedents: Lepton, StrategicReading,
and Valliappan et al. A hardware-relevant study may use any text read on a phone;
it need not use news. These studies support testing coarse line/region signals,
but none supplies a public validated model that chooses real-time aid strength.
Lepton's reported comprehension correlation gain comes from scrolling features
in its leave-one-reader-out table, not gaze alone. Preserve that distinction.

MGazeNet has been integrated and optimized. Old July README descriptions and the
September 6 replacement handoff do not describe the latest runtime. Verify the
active provider in source before making tracker claims. Better visible tracking
does not by itself validate word assignment, fixation detection, or an index.

## Current measurement work

- Owner clarification on 2026-09-30: the pilot's objective is to build and evaluate
  the scanpath-to-visual-aid system, carrying forward existing gaze evaluation.
  The owner clarified that measurement tasks follow completion of the prototype.
  [Pilot revision 6](scanpath-measurement-pilot.md) is the current build/evaluation
  plan: six objectives, seven work packages, P01–P13 pattern rules and A0–A4 aid
  candidates, plus separately identified optional ruler/coloring forms. Build
  the Android causal word/line engine, pattern-to-aid policy,
  adjustable renderers with variable strength, reader controls and action/render
  logging first. Complete software replay/build checks and the matched evaluation
  instrument before running participant measurement. Phases then cover prompted feasibility,
  ordinary-reading shadow decisions and independent support-need reports,
  controlled aid/strength trials, then frozen varying-strength evaluation.
  Offline analyzer v5 now includes the [P01–P13 named detector layer](scanpath-detectors.md):
  word/line movements, geometric sweep candidates with explicit endpoint settings
  and verified LTR direction,
  revisit/frontier episodes, structural A–B–A–B cycles, residence/progression
  descriptors and breaks. Long/slow flags require explicit development settings.
  Live engine v1 now runs P01–P13 from the article's raw observation callback.
  Focused JVM tests and 13 synthetic cases/183 raw-action prefixes pass; common
  fixation fields and P01–P12 agree with Python, while live P13 resets immediately.
  The debug app builds and Android geometry tests compile; device execution,
  human validity and runtime timing are unmeasured. Layout/bidi caching and old
  geometry eviction were independently reviewed and corrected. The in-memory
  adapter uses callback-only event delivery and starts a new generation on
  text/locale changes or a 512-finalized-candidate resource cap, preserving the
  capture-time floor. Pattern events require finalization; a separate established
  ongoing-prefix contract can add observed time without incrementing refixations.
  `gaze/aid/` now implements a source-linked policy, independent displays,
  reader controls and bounded numeric action/render journal. Normal article
  reading selects this route instead of the legacy weighted controller/demo.
  Word magnification and optional focus strength vary under an explicitly
  experimental profile; focus requires cycle + residence + slow progression,
  never a cycle alone. Action audits are exported privately only from the menu;
  they do not contain raw gaze or replace the matched evaluation instrument.
  Focus new checks on sequence,
  classifier and aid behavior; do not make a broad gaze benchmark restart a
  prerequisite. Raw/smoothed and conditional-reference limits still apply.
  The owner is requesting a stronger research model from the public-model owner;
  artifact/compatibility/performance are unverified. Continue model-versioned
  sequence development and reevaluate affected measurements when supplied.
  Focused detector checks: 39 extraction/integration plus 13 classifier tests
  passed (52 total). Independent review findings on direction, episode phase/
  evidence and censored residence were corrected with regression fixtures.
  Evidence contract: `docs/scanpath-detectors.md`; tests:
  `tools/gaze/test_scanpath_patterns.py` and `test_analyze_reading_measurement.py`.
- The active scanpath preparation branch is `measurement/scanpath-pilot`, created
  from the same commit as `measurement/RII_2` while retaining pre-existing
  uncommitted changes. No scanpath measurement has been run on the device in this
  branch. The [phone-only scanpath measurement pilot](scanpath-measurement-pilot.md)
  specifies separate word and physical-line paths, explicit continuity breaks,
  known-target placement checks, and natural-reading evidence limits. It lists
  descriptive patterns to implement; cognitive labels and validated aid/intensity
  weights are not established. Review the analyzer's
  current `analysis_version` and focused tests before using its outputs. The
  structured v6 target score is based on smoothed displayed coordinates, whereas
  the natural-reading replay uses raw coordinates and raw-derived AOIs; v6's
  current score cannot pass a raw path placement gate. A separate matched raw
  target/order instrument supports evaluation of the completed prototype,
  without changing frozen v6. The offline registry/classifier is implemented;
  the integrated live engine/policy/displays/controls/action journal are built.
  Next build the matched raw evaluation instrument, then conduct authorized
  device checks and participant tasks. New participant measurement was not a
  prerequisite for prototype implementation. The blank
  record now covers pattern and aid outcomes, condition assignment and holdout
  allocation. Set participant counts, numeric tolerances and study conditions
  from the phase-specific plan before collection; they are not validated defaults.
- The [2026-09-30 pilot audit](scanpath-pilot-audit.md) corrected a timestamp-order
  reversal across replay boundaries and overstated accepted-sample coverage.
  Analysis v4 preserves a global capture timestamp, separates eligibility from
  accepted samples, and reports visit censoring, duration coverage, output age
  and terminal boundary events. Focused suite: 31 tests; independent review
  found no remaining issue in the fixes. Protocol revision 2 requires the same
  calibration through pre-check/read/post-check, current locked 22 dp font,
  matched raw placement plus continuous-order tasks, and a frozen sampling/
  material plan. Use the blank session record privately. This is diagnostic
  preparation, not validated natural-reading or aid behavior.
- Ordinary known-target structured validation is now protocol v6/schema 9: the
  orange gaze dot stays visible and the same article One Euro-smoothed
  coordinates drive display, word/line mapping, and scoring. Raw MGazeNet source
  coordinates remain logged separately. Interpret its output as closed-loop
  article-pipeline UI performance, not unaided estimator accuracy, and do not
  pool it with raw visible-dot v5 or hidden-dot v4 data. The frozen
  matched-comparison path remains raw hidden-dot v4. The interrupted raw-visible
  v5 diagnostic is retained at
  `diagnostics-local/reading_validation_session_20260921_114932_153.jsonl`; the
  preceding hidden-dot evidence is
  `diagnostics-local/reading_validation_session_20260920_190328_306.jsonl`.
- The source task **Explore feasible gaze metrics** reports one completed v6 run
  (`reading_validation_session_20260921_155148_059.jsonl`): 89.3% word-target
  exact-line, 47.5% exact-word, 61.6% guided exact-line, and 99.2% guided
  within-one-line at 22 dp, after recalibration with the visible smoothed dot.
  This is promising closed-loop known-target evidence, not unaided or natural-
  reading line-transition validation. Repeatability, font-size effects, and
  target-population performance remain open.
- `docs/reading-measurement-pilot.md`: specification for raw natural-reading JSONL
  recording and reproducible offline extraction. The requested orange gaze dot
  stays visible; scaffolds and the old adaptation feed are disabled during this
  recording mode. This is a diagnostic-dot condition, not an unassisted study.
- Raw and displayed coordinates are separate. Analysis uses capture timestamps,
  physical-screen geometry, and Android UTF-16 offsets. Coordinate/gaze validity,
  scrolling, changed geometry, ambiguous assignment, and unclassified gaps matter.
- `docs/reading-measurement-run-01.md`: first run is a plumbing/measurement
  diagnostic with user-reported vertical misalignment, not independent accuracy
  ground truth or index validation. The corrected analyzer breaks continuity
  across long unclassified inter-fixation intervals. Sparse retained transitions
  limit sequence claims; do not restore the earlier gap-bridging behavior.
- `tools/gaze/analyze_reading_measurement.py` and
  `tools/gaze/test_analyze_reading_measurement.py`: current offline analysis and
  regression checks. Check the actual test runner and current version before use.
- Candidate fixation, first-pass, rereading, regression, skipping, and scanpath
  features remain provisional. No final RII formula, weights, baseline, or
  replacement online controller is established by this pilot.
- Preserve explicit eligible denominators, undefined rates as `null`, ambiguous
  assignments, censoring and continuity breaks. Use the current analyzer CLI and
  tests from the pilot document; generic statistical utilities do not replace it.
- Repeated windows are not independent participants. Plan participant/session/
  passage separation appropriate to the target claim, account for clustering,
  and prevent evaluation leakage through normalization or future observations.
  Generic assumption-check reports do not establish independence or measurement
  validity; do not automatically remove or winsorize observations they flag.
- `docs/reading-stability-index.md` and `docs/adaptive-visual-scaffolding.md`
  explain the legacy line-event score and rolling controller. They are distinct
  from the new raw-coordinate measurement pipeline.

## Research judgment

Prior tasks discussed scanpath regularity and a personalized multivariate
deviation score. These are candidate approaches, not approved final methods.
Verify each paper's population, hardware, sampling, feature definition, outcome,
and validation split before adapting it. Do not turn an association into a causal,
clinical, or real-time detection claim. Do not assume unusual rereading is harmful;
deliberate careful rereading and tracker noise are alternative explanations.

Select reproducible measurements first; compare candidate indices to independent
labels/outcomes; evaluate scaffolds on reading benefit rather than simply a
reduction in the score used to trigger them. Retain the on-device deployment
objective. Older constraints include both A56 and SM-G991B relevance and no paid
EyeDid or required laptop/WiFi backend; revisit only if the user changes scope.

## Evidence entry points

Read `docs/progress-notes.md` from the top for recent implementation decisions.
Read a historical gaze handoff only when the task concerns that experiment.
Related workspaces are `../THS-ST-Paper` and `../gaze-thesis-prototype`; do not scan
or edit them unless relevant to the current request.

Task context checked during setup (retrieve only if a specific ambiguity remains):

| Task title | Task ID | Relevant context |
| --- | --- | --- |
| Check scanpath usage | 01a0ae5f-010b-7b83-9566-6634fb337b56 | Event counts versus ordered visits; scanpath proposal status |
| Review thesis gaze limitations | 01a09609-43ab-7b61-8640-d158dd5cdfb8 | Index validity and candidate deviation score |
| Review thesis gaze | 01a0a29f-cd3f-7de0-997c-b2f2e1ad5260 | Index first; flexible scaffolds; literature leads |
| Gaze Measurement Pilot | 01a0a2a3-8959-7be3-aa3c-02de33b98972 | Orange dot visible, scaffolds off; raw measurement implementation |
| Improve article reading FPS | 01a09405-e15c-7112-8dd1-770f1d1ed400 | Integrated MGazeNet and calibration keeper checkpoint |
| Explore feasible gaze metrics | 01a0be09-fe3e-76c2-827c-94dcfadcc416 | Proposal decisions, Mahalanobis comparison, v6 run, primary-paper discussion |

At setup, existing uncommitted pilot/analyzer edits were present. Preserve them;
this agent-framework task does not assess or adopt those edits as its own.

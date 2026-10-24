# Reading aids during auto-scroll

## Status — 2026-10-16

The owner reports continuous scrolling works but aids do not activate during it.
Source confirms two blockers: motion observations were excluded and each
translation replaced geometry identity/reset pending evidence. The new live-only
content-frame aid adapter is implemented and source-reviewed. The approved
corrected retry passes **273/273 focused JVM checks** across 31 suites and builds
both APKs. Both APKs are installed on the A56 using replace installs, preserving
app data and calibration. **All five aid integration checks pass in 14.222 s**:
line/sentence guides, lookup, focus, bookmark and external-motion invalidation.
Navigation speed rules remain V3. The five existing navigation checks passed on
the first attempt; they were not rerun on the corrected APK.

Source diagnosis confirms a bookmark defect: startup timer-only invalid context
advanced the capture floor to wall time, blocking a delayed first sustained
prefix. The saved correction advances it only from actual finite capture times;
explicit boundary floors remain. Both policy regression fixtures pass. The focus
fixture now uses explicit 24 dp rendered STANDARD size and checks its rule before
input; 22 sp plus system scaling was an uncontrolled test condition. It is a
possible cause of the first focus failure, not proved by that artifact. Focus now
passes with the controlled fixture; production font rules were not changed.
Final corrections passed source review before the approved retry. Current evidence:
`output/auto-scroll-reading-aids-check-02/summary.json` plus JVM/build/native/install
logs. The first attempt's 8/10 result and failures remain in
`output/auto-scroll-reading-aids-check-01/`. No camera or human collection ran.

## Input and display contract

1. The orange dot and recorded selected input remain the calibrated, smoothed
   physical-screen coordinates. Raw diagnostics also remain unchanged.
2. A bounded two-second geometry timeline selects the last observed text geometry
   at or before each capture timestamp. It never substitutes delivery-time geometry
   or interpolates across an unobserved change. Capture-to-snapshot age must be
   at most100 ms; the capture itself must be at most250 ms old.
3. Explicit analysis coordinates are selected screen X/Y minus that captured text
   origin. These are text-local pixels. The engine's opt-in geometry has zero
   origin/full text bounds and a stable layout ID during controlled translation.
4. Following one moving word can therefore accumulate content residence; a fixed
   screen point over changing words does not automatically become word residence.
   A moving-text cluster is an experimental content-residence proxy, not a claim
   that the eye was physiologically stationary.
5. For policy visibility checks and rendering, the supported content anchor is
   projected into the current display geometry. Its original source offsets stay
   attached to the text. The recorded input point is not replaced by this projection.

The timeline is bounded to4096 snapshots as an additional resource guard.
Geometry timestamps are main-thread observed view positions, not hardware display
presentation timestamps. Frame timing, model error, lag and smooth-pursuit behavior
still limit physical accuracy; the100/250 ms settings are engineering guards.

## Aid behavior

| Aid | Existing rule retained during auto-scroll |
| --- | --- |
| Line/sentence guide | Established supported line prefix selects the guide in current display geometry |
| Word lookup | Same word's supported residence reaches the existing1500 ms minimum, then offers the icon |
| Focus window | Existing font-aware supported line alternations or repeated qualifying tracking interruptions |
| Last-reading bookmark | Existing supported-reading departure/loss rule; missing gaze stops actual scrolling |

A lookup offer or open definition panel still pauses navigation so its target
does not move away from the reader. No automatic definition panel or network
request is introduced by offering the icon. The original font-aware focus counts,
look-away counts, definition duration and strength rules are unchanged.

Only controller-declared self-scroll retains layout identity. Manual/external
scrolling, text replacement, reflow, font/spacing/padding changes, missing geometry,
menus and consultations retain their discontinuity/input gates. The controller
checks movement before a gaze sample is interpreted. External/layout stop reasons
also force history invalidation and an engine boundary even if geometry was
already observed, preventing old evidence from bridging an external jump.

## Research and trace limits

Live article condition: SMOOTHED_ARTICLE_CONTENT_AIDS_V11.
Analysis mode: auto_scroll_content_frame_v1. Trace inputs retain processing/raw
screen points and add captured text origin/time, text-local analysis coordinates,
and current presentation origin. Configuration marks motion-compensated aids and
the content-residence interpretation. The frozen Pilot/Replay adapter defaults
remain screen-frame processing with their original geometry settling behavior.
No calibration, numeric smoothing or classifier thresholds change.

These aid observations must not be pooled as validated stationary-text fixations,
regressions or diagnostic difficulty labels. The existing pattern code supplies
operational trigger descriptions in a new coordinate condition; that condition
requires its own human measurement/benefit validation. The user request authorizes
making aids operational while scrolling, not a new scientific validity claim.

## Checks

Focused source review corrected an external-scroll callback-order edge and found
no remaining actionable issue. Pure tests cover causal geometry lookup,
preservation of selected input, content pursuit, fixed-screen crossing, malformed
metadata, frame changes and projection abstention. Five new attached Android
fixtures use real40 ms observations with80 ms capture lag to exercise actual
controller/session/engine/policy/rendering. They cover guides, lookup, line-cycle
focus, tracking-loss bookmark and forced external-motion invalidation.
The corrected retry passes all five attached fixtures. They run in a Replay-hosted
article layout with synthetic inputs and the production controller, session,
engine, policy and renderer; they do not start ArticleFragment or a gaze provider.
Complete live routing, human gaze accuracy and reading benefit still require
actual reading checks. No camera or human collection is part of these tests.

## Main files

- scanpath/ScanpathMotionCompensation.kt, ScanpathModels.kt, ScanpathEngine.kt
- scanpath/ArticleScanpathSession.kt
- aid/ArticleReadingAidRuntime.kt, ReadingAidModels.kt, ReadingAidTrace.kt
- aid/ReadingAidPolicy.kt, ReadingFocusPolicy.kt, ReadingBookmarkPolicy.kt
- fragments/article/ArticleFragment.kt
- AutoScrollReadingAidIntegrationTest.kt, ScanpathMotionCompensationTest.kt,
  ReadingAidMotionContextTest.kt and ReadingAidBookmarkPolicyTest.kt

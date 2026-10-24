# Optional article auto-scroll

## Current V3 direction — 2026-10-16

The owner confirms V2 scrolling works, but wants more immediate, continuous
control like Athena. The owner explicitly selects: gaze lower to speed up;
gaze higher to slow or stop. V3 replaces stepped navigation with continuous
gaze-height velocity control. Focused source review is complete;202/202 JVM checks
across22 suites pass and both APKs build. Updated A56 app/test APKs use-r to preserve
data/calibration. All5/5 camera-free native checks pass in13.550 s in one run.
Actual live reading comfort and gaze accuracy still require the owner's check.
Evidence: output/article-auto-scroll-continuous-check-01/summary.json plus
build/native/install logs and copied JVM XML. No camera or human collection started.

The selected coordinate stream remains the same calibrated, smoothed physical
screen point used by the orange dot. There is no line/word assignment, fixation
classification, inferred reading speed, 700 ms dwell, rearm or cooldown in V3.

## Exact trial behavior

These are engineering trial settings, not validated reading thresholds or
Athena's proprietary algorithm.

| Gaze height from physical-screen top | Target forward speed |
| --- | --- |
| At/above40% | Zero |
| 45% | 1.2% of physical screen height per second |
| 50% | 2.4% of physical screen height per second |
| 60% | 4.8% of physical screen height per second |
| At/below65% | Maximum6% of physical screen height per second |

Between40% and65%, target speed increases linearly. The controller renders on
Android animation frames and accumulates fractional pixels; there are no page
jumps or fixed-distance steps. Speed changes are bounded by a nominal180 ms
zero-to-maximum acceleration/braking ramp. Each frame integrates at most50 ms;
a stalled UI never catches up by jumping a whole delayed interval. Upper gaze
brakes to zero; a fresh lower point can start again immediately.

Movement requires the point to remain inside the actual clipped article body,
with a usable stop region in the viewport. Headers, images and controls do not
become eligible just because their Y position is low. A fresh source capture must
be no older than250 ms and delivery no older than150 ms. Invalid/unavailable gaze
hard-stops motion; if callbacks cease, the next animation frame stops after that
delivery-age limit. New eligible input can resume without a dwell.

These timings constrain software behavior; end-to-end camera/model latency and
frame scheduling still affect perceived response. The values need live user
assessment. Speed is not a measure of reading capability or comprehension.

## Reader controls and layout

- Article gear menu: **Auto-scroll**, defaultOff, session preference.
- The master aids switch disables auto-scroll.
- While enabled, **Pause auto-scroll / Resume auto-scroll** stays available.
  Touching article content pauses until explicit Resume.
- Menus, lookup offers/definition panels, Read Aloud, diagnostic recording,
  lifecycle pause and real text/viewport reflow stop motion and discard input.
- Ordinary translation from the controller's own scrolling does not invalidate
  its next gaze update. External scroll or layout changes require fresh capture
  support after the150 ms layout settling guard.
- Delayed captures discarded by that guard do not advance its input floor;
  otherwise a causal but delayed camera stream could be starved indefinitely.
- Only forward document motion is automatic; manual navigation remains available.
- Enabled-only tail space equal to70% of physical-screen height follows the
  article so final content can reach the upper area. Actual scroll range bounds
  every movement. Disabling removes the tail.
- Pilot, Replay and frozen calibration/accuracy tasks do not auto-scroll.
  Their shared menu hides the article-only option.

The menu describes the last navigation status before opening. Start/end and
deduplicated status events are journaled with the V3 configuration, source
capture/delivery, selected coordinate stream, target speed and actual scroll
positions. Export still uses the explicit Save aid trace action.

## Scanpath implications

**Current live aid extension (2026-10-16):** the owner requested aid activation
while scrolling. The opt-in live article adapter now retains selected screen
coordinates and derives capture-aligned content coordinates for experimental aid
triggers. Controlled translations preserve layout identity; external changes
still break it. See [aids during scrolling](auto-scroll-reading-aids.md) for the
contract, trace condition and software/device verification. The following paragraph
describes the original stationary adapter, which remains the default for frozen
Pilot/Replay; its blanket exclusion is no longer applied to the opted-in live aid
adapter. Content-residence proxies are not validated stationary-text metrics.

This mode moves text while the reader may be looking at it. Existing stationary
text detectors are not motion-compensated. Motion intervals and captures through
motion end+150 ms remain excluded with auto_scroll_motion/auto_scroll_settling;
actual geometry must also settle. Consequently continuous scrolling can exclude
much more reading time than V2. Do not present those intervals as observed
fixations, regressions or distraction, or compare their counts directly with a
manual-scroll condition.

The navigation controller continues receiving the selected gaze during its own
motion; the exclusion applies to stationary-text scanpath evidence. Completed
focus summaries survive operational boundaries, while pending anchors/chains are
cleared. Scroll-depth logging marks auto_scroll versus manual_or_layout.
No model, saved calibration, numeric smoothing or frozen measurement protocol
has been changed. Valid reading metrics during continuous motion would require
a separate motion-aware measurement design and validation.

## Sources and what they establish

- [Athena's official description](https://athenahandsfree.com/) and
  [developer App Store description](https://apps.apple.com/us/app/athena-eye-control-reader/id1633121969):
  Athena markets adaptation to reading location and speed. These product claims
  motivate the owner's desired interaction; they do not disclose its thresholds,
  validate this implementation, or establish reading benefit.
- [Kumar & Winograd, Gaze-Enhanced Scrolling Techniques (2007)](https://hci.stanford.edu/cstr/reports/2007-11.pdf),
  pp.2–3, Figure4, “Smooth scrolling with gaze-repositioning”: gaze-height regions
  govern start, stop and faster scrolling, with gradual rate transitions. Their
  system used a Tobii1750; pilot feedback from10 users is preliminary. This is a
  precedent for the interaction pattern, not evidence for NewsMead's phone
  accuracy or the40/65%,6%/s and180 ms choices. V3 does not implement their
  reading-speed estimator or claim to reproduce Athena.
- [Lei et al., DynamicRead (2023)](https://arxiv.org/html/2304.09584v1), §§3.1,
  5.2,6.5: an iPhone13ProMax/server-iTracker study with20 adults aged20–31.
  Its upper-region pilot and prematurely advancing speed prediction motivate
  testing and explicit reader control, not NewsMead-specific thresholds.

The owner's intermittent upper-screen accuracy advantage remains unmeasured.
Software checks cannot establish human accuracy, comfort, comprehension or
reduced distraction.

## Source and validation

Policy/controller: gaze/scroll/ArticleAutoScrollPolicy.kt and
ArticleAutoScrollController.kt. Integration: ArticleFragment.kt and
ReadingAidControlsSheet.kt; strings: values/article_auto_scroll.xml.
Policy JVM tests exercise speed mapping, causal clocks, braking, missingness,
frame caps, translation and document bounds. Attached Android tests exercise
actual article layout/navigation with supplied coordinates and no camera.
The five native checks cover default-off/menu scope, lower-gaze speed changes,
upper braking/restart, stale/noncoordinate stops, lookup blocking, touch pause/
resume and enabled-only tail. Sources carry80 ms capture latency; first actual
movement is checked within400 ms of the first capture, with multiple real scroll
updates. Review corrected a zero-speed restart inheriting idle frame time; its
JVM regression checks only10 ms contributes to a10 ms restart frame. These are
software timing/navigation checks, not evidence of human reading performance.

## Historical evidence retained

- V2: owner confirms it works, then requests the continuous redesign.
  201/201 JVM checks across22 suites and7/7 native checks in3.459 s passed.
  The native run included actual attached article layout with changing lower
  Y positions, same text point reaching30% height, and motion exclusions.
  App/test updates preserved A56 data/calibration; camera stayed off.
  Evidence: output/article-auto-scroll-region-check-02/summary.json and
  build/native/install logs. First attempt had one test-fixture status mismatch
  (point outside shortened candidate body); its failure/XML are retained in01.
- V1:202/202 JVM checks and6 distinct native checks passed across an initial run
  and two approved retries, not one six-test run. Test-only corrections addressed
  zero-duration animation completion and detached coordinate assertions.
  Evidence: output/article-auto-scroll-check-01 through03/summary.json.

Historical V1/V2 outcomes do not verify V3 or establish live human reading benefit.
No files staged or committed for this task.

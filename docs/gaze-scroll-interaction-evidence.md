# Gaze scrolling: interaction evidence checkpoint

## Direction — 2026-10-16

The owner requests gaze down → document down and gaze up → document up. They
reject manual speed control and an interaction tailored to one calibration issue.
The installed V3 downward-only controller remains current while this choice is
examined. A provisional signed-direction policy/test draft was saved to
`output/gaze-scroll-direction-research/directional-policy-draft.patch` and removed
from active source; it is unreviewed and unbuilt. No new device operation ran.
Earlier verified moving-text aid changes remain in the worktree.

## Primary sources

| Work | Actual control and evidence limits |
| --- | --- |
| [Käki et al., 2014, author-uploaded full paper](https://www.researchgate.net/publication/267514090_Effects_of_haptic_feedback_on_gaze_based_auto_scrolling), DOI 10.1145/2639189.2670247, Method and Figure 3 | Bidirectional active areas, each 260 px high; 700 ms dwell then steady 67 px/s while gaze stays in the area. Upper area scrolls up; lower down; centre stationary. Twelve novices, Tobii T60, 17-inch 1280×1024 desktop. Comparisons concerned haptic feedback, with no significant task-time/gaze differences. This establishes a precedent, not optimal timing or superiority on phones. [Author poster](https://homepages.tuni.fi/oleg.spakov/publications/Kaki_NordiCHI_14p.pdf). |
| [Lei et al., DynamicRead, 2023](https://arxiv.org/html/2304.09584v1), §3.1 and Figure 1; §§4–6 | Forward page turns: bottom-to-top Eye-Swipe after 500 ms lower-region priming; bottom hitbox dwell selectable 0.5–2 s; pursuit of a moving bar; or reading-speed prediction. Twenty adults aged 20–31, iPhone 13 Pro Max camera, iTracker with server inference, seated/walking. Eleven ranked Eye-Swipe first; overall SUS/workload differences were not significant. Implicit prediction sometimes turned before reading finished. No bidirectional gaze-up reverse rule was evaluated. |
| [Kumar & Winograd, 2007](https://hci.stanford.edu/cstr/reports/2007-11.pdf), PDF pp.2–4, Figures 3–6 | Reading auto-scroll variants intentionally move forward only; backward navigation uses manual input or off-screen gaze controls. Continuous hotspots use approximately 150–200 ms micro-dwell; explicit Scroll Lock enables the mode. Tobii 1750 and preliminary ten-user pilot. Not validation of NewsMead's 40/65% settings. |
| [Turner, Iqbal & Dumais, 2015](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/07/PETMEI2015scrolling_petmei_15_final.pdf), PDF pp.4–7 | Eighteen adults, desktop Tobii TX300, manual scrolling. Before downward reading scrolls, gaze was in the top half for 51% of events and bottom half for 49%, with individual differences. This is observational evidence, not a tested bidirectional controller. |
| [Athena official support](https://athenahandsfree.com/support) | Describes machine learning for reading speed, location and patterns; does not disclose enough detail to reproduce its controller. Product documentation is not a comparative study. |

## Judgment and outstanding choice

Bidirectional zone control has direct precedent. The remaining design question is
how to separate a reading glance from a navigation command: dwell, deliberate
gesture, explicit gaze target or another tested interaction. A zone mapping alone
does not establish intent. Choosing a confirmation duration and zone dimensions
for NewsMead remains an engineering decision requiring phone assessment. Do not
present the saved 40/60% zero-dwell draft as derived from these studies.

## Public product and source follow-up — 2026-10-16

- [GazeScroll official page](https://gazerecorder.com/gazescroll/) explicitly
  describes webcam-driven scrolling in both directions. It does not disclose
  exact control thresholds, timing or algorithm. The linked extension/video could
  not be inspected through this session; no extension or camera was started.
- [OptiKey Look-to-scroll documentation](https://github.com/Optikey/Optikey/wiki/Look-to-scroll)
  documents explicit mode activation, selectable anchor/bounds, a stationary
  deadzone and continuous scrolling above/below it. Distance acceleration can be
  disabled in favour of a base speed. This is the clearest documented reference
  for the requested interaction. Controller code access was unsuccessful in this
  bounded review, so these findings are documentation-based, not code execution.
- [Deep-SkyLabs Android controller source](https://github.com/Deep-SkyLabs/gaze-control/blob/main/android/app/src/main/kotlin/com/example/gaze/GazeForegroundService.kt)
  and [accessibility service](https://github.com/Deep-SkyLabs/gaze-control/blob/main/android/app/src/main/kotlin/com/example/gaze/GazeAccessibilityService.kt)
  contain actual bidirectional dwell-triggered actions with hysteresis/cooldown.
  They dispatch discrete scrolling/swipes, rather than continuous scrolling.
  Review found a simulated-gaze fallback on failed initialization, configuration
  differences from the README, and conflicting README/LICENSE declarations.
  Treat it as code to inspect, not a verified Android dependency or human outcome.
- [SeeSo UIAutoScroll source](https://github.com/visualcamp/UIAutoScroll/blob/main/Sources/UIAutoScroll/UIAutoScrollView.swift)
  implements downward movement or cancellation to zero; its negative adjustment
  cancels a positive base distance and does not scroll backwards. It is Swift,
  uses SeeSo gaze input, and its combined parameter setter is empty. It does not
  implement the requested bidirectional behaviour.
- [AutoScroller source](https://github.com/DissanayakeLYB/AutoScroller/blob/main/AutoScroller.py)
  compares eye-contour centroid Y in camera pixels with a baseline. That is not
  screen gaze estimation and can respond to head motion. Its name alone is not
  evidence of an appropriate reading controller.

No source code was imported or run. These are implementation precedents; they do
not establish NewsMead tracking accuracy or reading benefit. Any future port must
retain the shared calibrated/smoothed input and current moving-text aid integration.

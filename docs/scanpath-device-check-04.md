# A56 demo, pilot and replay compatibility check 04

**2026-10-03.** Device: Samsung SM-A566B, Android API 36, ADB identity
`R5CY40Y2C5W`. Source: uncommitted demo/padding changes on
`measurement/scanpath-pilot`, starting at `b2b7a23`.

**Current result: the owner-authorized corrected retest passed all 15 tests
in 50.908 seconds.** Corrected APKs are installed on this A56. The initial
eight failures remain documented below; they are superseded by the retest for
the corrected artifacts. Human gaze and reading benefit were not tested.

The first installation request was rejected by automatic approval review for
missing explicit A56 installation authorization. The owner then explicitly
approved installation and compatibility checks. Both packages installed with
`adb install -r`, preserving app data. No camera, gaze provider, calibration or
human collection was started. Tests use synthetic text/coordinates and app-only
view drawing.

## Installed artifacts and first attempt

| Artifact | SHA-256 |
| --- | --- |
| App APK | `02EC413F022E1C9FFDC934038D49E77055D92630155D9312AE9FED55A55DAFDC` |
| Test APK | `0CA5476B2BFDF34142F066BDAF57F20B666E26AF78DF5451B8129514380237D2` |

One bounded instrumentation invocation ran **14 tests in 40.993 seconds**:
**6 passed, 8 failed**. ADB process exit 0 did not mean success; the JUnit runner
reported `FAILURES!!! Tests run: 14, Failures: 8`. Device attempts stopped after
this failure, as required by AGENTS.md. At that checkpoint, no corrected-build
device retest had run; the subsequent approved retest is recorded below.

| Test group | Passed / total | Observed result |
| --- | --- | --- |
| Render-only demo | 0 / 4 | All four timed out waiting for enabled navigation. The initial assertion did not retain the setup error/status, so the exact cause is not established by this run. |
| Live-pilot screen, synthetic input | 5 / 5 | Idle behavior; all ten prompted task plans/raw scoring and trace provenance; failed setup resets; scroll/pause retains unshown targets; shadow/display matched viewport and distinct rendering. |
| Scripted replay | 1 / 5 | Trace-cap/incomplete-footer test passed. Pause, scroll, zero-strength and all-scenarios tests aborted with `scheduler_late`. |

The all-scenarios replay test stopped in Navigation; its later scenarios were
not executed. Demo stage rendering, timer, pause and scroll behavior were not
verified because setup did not finish. Pilot pass results establish software
behavior under synthetic coordinates, not human target placement or physiological
fixation/refixation validity.

Invocation:

```cmd
adb -s R5CY40Y2C5W shell am instrument -w -r -e class com.newsmead.gaze.aid.demo.ReadingAidDemoActivityTest,com.newsmead.gaze.scanpath.pilot.ScanpathPilotActivityTest,com.newsmead.gaze.scanpath.replay.ScanpathReplayActivityTest com.newsmead.test/androidx.test.runner.AndroidJUnitRunner
```

## Diagnostic evidence

Four complete failed-replay traces record **negative**, fractional callback lag,
rather than lag over 100 ms. The existing guard treated both cases as
`scheduler_late`. Early callbacks must wait until their synthetic elapsed-time
deadline before feeding the engine; capture/delivery times must remain ordered.
The late-callback limit remains a separate failure condition.

| Trace suffix | Sent samples | Abort ordinal | `delivery_ms - scheduled capture_ms` |
| --- | --- | --- | --- |
| `27efb667-4e6a-4d6a-aaa9-0d2a7f62770d` | 5 | 5 | −0.071255 ms |
| `095a31bf-c3e2-44c5-8772-f276a39d5730` | 45 | 45 | −0.090786 ms |
| `ec861686-7b66-4339-b365-3519b7b0a57c` | 5 | 5 | −0.104615 ms |
| `adce5ee9-293e-4a02-b528-cc38aefd0a75` | 19 | 19 | −0.121255 ms |

Replay geometry retains more than ten visible nonempty lines in portrait, but
this is not a saved demo setup snapshot. It cannot by itself establish the
demo's exact failure. Source review identified a capacity-check defect: the
centered TextView's `totalPaddingTop/Bottom` includes vertical gravity offsets,
so it describes space around the current text layout rather than the full
padded box. [Android's TextView reference](https://developer.android.com/reference/android/widget/TextView#getTotalPaddingTop())
documents that distinction. This is a supported source finding, not a retained
exception from the failed demo.

Generated local evidence:

- `output/scanpath-device-check-04/install-target.txt`
- `output/scanpath-device-check-04/install-test.txt`
- `output/scanpath-device-check-04/runner.txt`
- `output/scanpath-device-check-04/summary.json`
- The four complete synthetic replay JSONL files above, plus capped
  `scanpath_replay_6b0f216a-fd25-4e44-9ccb-0115a03a5a58.jsonl.partial`.
- `output/scanpath-device-check-04/scanpath_pilot_prompt_preview.png`:
  test-created app-root drawing; inspected for readable labels/text. It shows a
  synthetic interrupted pilot screen, not a live camera or another app's screen.

Only these known synthetic artifacts were exported; no human gaze recordings
were read. The pilot's synthetic JSONL assertions ran on the phone; their full
files were not exported. Earlier S21/check-03 results belong to earlier APKs.

## Limits and next gate

At the first-attempt checkpoint, installation compatibility and all five pilot
software checks passed, while demo/replay readiness was not established. The
subsequent corrected retest below establishes these software checks for its
APKs. Natural gaze, difficulty classification, cue visibility to a reader and
reading benefit remain unmeasured.

## Local correction checkpoint before retest

Prepared these bounded fixes:

- Replay retries an early callback with the same ordinal and capture deadline
  before cursor/engine delivery. Each retry checks cancellation/geometry and
  measures actual delivery again. The 100 ms maximum lag remains unchanged;
  timestamps are not clamped or relabeled.
- Demo preparation and the shared word renderer use `compoundPaddingTop/Bottom`
  for available height. Font metrics and oversized-word withholding remain;
  no strip height, detector or aid-policy thresholds changed.
- Three JVM timing cases cover early callbacks, the 100 ms boundary and retry
  delivery. One Android renderer regression covers centered low-to-high
  magnification and oversized width/height withholding.
- Activity tests retain timestamped app status/layout diagnostics. Demo tests
  save synthetic app-root word/focus PNGs after layout; the retest generated
  these previews on the phone.

Focused independent source review found no remaining actionable issue. A helper's
first local Gradle invocation failed before tests because its Android home
resolved to unavailable `C:\.android`; no checks passed in that invocation.
The established Java 17/offline user-cache/in-process recipe then completed:
**56 JVM tests, zero failures/errors**, app and test APKs assembled. Tracked
`git diff --check` and new-file whitespace/EOF checks pass. No files staged or
committed. The unchanged pilot summary tool was not rerun unnecessarily.

| Corrected local artifact | SHA-256 |
| --- | --- |
| App APK | `5A4520CB1B18D910EB126BBD46F506F14E2F6A06351D53ED9A4D386C81C34E28` |
| Test APK | `5DFF26CC3091102C07A20162EC6CFC47BF570FBE36D89415C4FA4D21A9349DB9` |

Local build output and test/hash summary:
`output/scanpath-device-check-04/corrected-build.txt` and
`output/scanpath-device-check-04/corrected-local-summary.json`.

## Owner-authorized corrected A56 retest

The owner explicitly approved the corrected installation and retest. Both
corrected APKs above installed successfully with `-r`, preserving app data.
The same 14 activity tests plus the single new renderer regression passed:
**`OK (15 tests)`, 50.908 seconds**. No camera, calibration or human collection
ran. No further device run was needed.

| Test group | Passed / total | Verified scope |
| --- | --- | --- |
| Render-only demo | 4 / 4 | Explicit preparation, low/high magnifier text and unchanged strip height, no replay result/evidence IDs, timer advance, Stop, pause/no automatic resume, scroll clearing. |
| Word renderer regression | 1 / 1 | Centered low-to-high magnification fits; oversized width and height are withheld. |
| Scripted replay | 5 / 5 | All five scenarios and their expected patterns/aid draw proxies, causal evidence/time ordering, zero-strength withholding, scroll/pause stopping, capped incomplete trace. |
| Pilot screen with synthetic inputs | 5 / 5 | All ten prompted plans/raw scoring/provenance, idle/setup reset, scroll/pause/unshown targets, identical shadow/display viewport and distinct rendering. |

Invocation adds this test to the three full activity classes:
`com.newsmead.gaze.aid.ReadingAidRenderingTest#centeredStripFitsLowThenHighStrengthButWithholdsOversizedWords`.

The demo diagnostics confirm a 191 px strip, 28 px compound padding on each
vertical edge and 72 px total padding on each edge while the strip is empty.
Thus the true capacity is 135 px; the old gravity-including calculation
reported only 47 px. The corrected demo prepares successfully on all four
checks. This corroborates the source finding; the original setup exception was
not retained.

All five completed scenario traces have `complete_trace=true`,
`software_expectations_met=true` and `termination=completed`. Their 265 sent
samples have nonnegative callback lag from **0.013237 to 2.879409 ms**. This is
synthetic scheduled-input-to-callback lag, not camera or eye-to-aid latency.

| Scenario | Sent samples | Required patterns | Drawn proxies | Aid strength |
| --- | --- | --- | --- | --- |
| Navigation | 56 | P01/P02/P03/P07/P10 | Current line | 0.25 |
| Return sweep | 35 | P05/observed P09 | Current line, destination-line start | 0.25 / 0.6 |
| Word refixations | 52 | P04/P12 | Current line, word magnifier | 0.25 / 0.25–1.0 |
| Line cycle | 79 | P08/P10/P11/P12 | Current line, focus region | 0.25 / 0.625–0.8125 |
| Loss/recovery | 43 | P13 | Current line after fresh evidence | 0.25 |

Additional descriptors/closure boundaries also occur. Strength is a configured
display parameter, not a difficulty probability. Focus was explicitly enabled
for replay; the live default remains off.

The demo word (100% strength), demo focus (25%) and detector-driven replay word
previews were inspected. Text/control labels are readable and the full magnified
words fit. The focus preview preserves the central five physical lines with
lightly veiled surrounding text. These are app-root Canvas drawings of synthetic
content, not screen capture, camera images or published study figures.

Retest evidence is in `output/scanpath-device-check-04/retest/`:

- Installation logs, `runner.txt`, `summary.json`, `replay-summary.json`.
- `scanpath_demo_preparation.txt`, `scanpath_replay_last_status.txt`.
- `scanpath_demo_word_preview.png`, `scanpath_demo_focus_preview.png`,
  `scanpath_replay_word_preview.png`.
- Navigation: `scanpath_replay_b83488ff-569f-406e-b5bb-151097df7942.jsonl`.
- Return sweep: `scanpath_replay_05ac8b3d-9c7e-4081-8ca3-35a8765a14d8.jsonl`.
- Word: `scanpath_replay_6ffc651b-180f-4af5-b77e-e922ff018d3a.jsonl`.
- Cycle: `scanpath_replay_80459765-c612-4456-ac6c-2b649f70bf4f.jsonl`.
- Loss/recovery: `scanpath_replay_29df26b2-07c2-450a-83d1-a4fb6b7d7882.jsonl`.

The software inspection route is ready on this A56:
**Home → Scanpath replay → Demo cycle** for presentation, or **Run replay** for
detector-driven synthetic scenarios. Human gaze/reading validity remains a
separate evaluation. The corrected APKs have not been tested on the S21.
No staging or commit was performed.

## Suggested checkpoint commands (Windows CMD)

Run these from the NewsMead repository after reviewing the diff. They cover
the aid demo, source review, compatibility fixes/tests and documentation, and
exclude local diagnostics, agent setup and unrelated files. Nothing was staged
or committed by the agent.

```cmd
git add -- ^
  app/src/main/java/com/newsmead/activities/ScanpathReplayActivity.kt ^
  app/src/main/java/com/newsmead/gaze/aid/ReadingAidOverlayView.kt ^
  app/src/main/java/com/newsmead/gaze/aid/demo/ReadingAidDemoPlan.kt ^
  app/src/main/java/com/newsmead/gaze/scanpath/replay/ReplayDispatchTiming.kt ^
  app/src/main/res/layout/activity_scanpath_pilot.xml ^
  app/src/main/res/layout/activity_scanpath_replay.xml ^
  app/src/main/res/values/strings.xml ^
  app/src/androidTest/java/com/newsmead/gaze/aid/demo/ReadingAidDemoActivityTest.kt ^
  app/src/androidTest/java/com/newsmead/gaze/aid/ReadingAidRenderingTest.kt ^
  app/src/androidTest/java/com/newsmead/gaze/scanpath/replay/ScanpathReplayActivityTest.kt ^
  app/src/test/java/com/newsmead/gaze/aid/demo/ReadingAidDemoPlanTest.kt ^
  app/src/test/java/com/newsmead/gaze/scanpath/replay/ReplayDispatchTimingTest.kt ^
  docs/README.md ^
  docs/agent-context.md ^
  docs/progress-notes.md ^
  docs/scanpath-adaptive-aid-design.md ^
  docs/scanpath-aid-runtime.md ^
  docs/scanpath-live-pilot.md ^
  docs/scanpath-replay-screen.md ^
  docs/scanpath-trigger-evidence-review.md ^
  docs/scanpath-device-check-04.md
git diff --cached --check
git diff --cached --stat
git commit -m "Add reading-aid demo and fix A56 replay and magnifier compatibility"
```

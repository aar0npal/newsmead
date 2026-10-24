# Scanpath Android device check 01

**2026-10-01.** Owner connected the phone for prototype device testing.
Device: Samsung SM-A566B, Android API 36. Source checkpoint:
`e5fa5b2` (`Add live scanpath-driven adjustable reading aids`). Tracked source
matched that commit before testing.

## Build and installation

- Debug target: `com.newsmead`, version 0.2, versionCode 1.
- Target APK SHA-256:
  `3F560690BA361611C0F1A3AEFD4647A58A1137E5D9207DB1F66ACDEDADA49AF8`.
- Existing app updated with `adb install -r`; app data was not cleared.
- `:app:assembleDebugAndroidTest` succeeded with installed Java 17/offline cache.
- Test APK `com.newsmead.test` installed successfully.
- Only `ScanpathTextGeometryTest` and `ReadingAidRenderingTest` executed through
  `AndroidJUnitRunner`. They instantiate synthetic text/views without starting
  an activity, gaze provider, camera, calibration or participant recording.

## First attempt: 4 tests, 1 passed, 3 failed

| Test | Result | Evidence |
| --- | --- | --- |
| UTF-16 word offsets and physical screen-origin assignment | Passed | Runner reported status 0. |
| Cached line data and mixed-bidi abstention | Failed | `TextView.checkForRelayout()` null layout parameters when replacing text after measuring the detached fixture. |
| Expired magnifier drawing suppression | Failed | Same null layout parameters when `ReadingAidWordView.showAction()` changed text in a detached, measured fixture. |
| Reserved-view magnification and draw receipt | Failed | Same fixture failure before the magnifier assertions. |

The runner reported `Tests run: 4, Failures: 3`. The ADB process returned exit
code 0 despite failed assertions; the runner result, not the process code,
determines test success. The observed stack traces identify missing fixture
layout parameters. Actual article views obtain these from XML/parents.

## Local correction and limits

Added explicit `ViewGroup.LayoutParams` to the geometry fixture and both magnifier
fixtures before measurement/text changes. No application behavior, detector,
policy, calibration or frozen protocol was changed. The corrected test APK
builds and was rerun after the owner's explicit `retest` instruction.

Device execution stopped after the first failing attempt, following `AGENTS.md`.
The owner then authorized the corrected retest. The original failure above is
retained alongside the successful result below.

## Authorized retest: all 4 passed

**2026-10-01, result recorded at 22:46 +08:00.** Only the corrected test APK was
reinstalled with `install -r`; the target app was not reinstalled for this attempt.
The test sources are the `e5fa5b2` checkpoint plus the layout-parameter corrections
in the two Android test files. Corrected test APK SHA-256:
`95F83D59228258EA9C050139F15BF45AEC503C90C75D488EDA4804F8ED302F09`.

| Test | Retest result |
| --- | --- |
| `wordOffsetsStayUtf16AndTargetsUsePhysicalScreenOrigin` | Passed, status 0 |
| `unchangedLayoutReusesLineDataAndMixedBidiAbstains` | Passed, status 0 |
| `magnificationUsesItsReservedViewAndReportsADrawProxy` | Passed, status 0 |
| `anExpiredMagnifierNeverReportsADrawnFrame` | Passed, status 0 |

Runner summary: **`OK (4 tests)`**. No extra test classes were selected.
The invocation was:

```cmd
adb shell am instrument -w -r -e class "com.newsmead.gaze.scanpath.ScanpathTextGeometryTest,com.newsmead.gaze.aid.ReadingAidRenderingTest" com.newsmead.test/androidx.test.runner.AndroidJUnitRunner
```

The layout-parameter fixture failure is resolved for this device/test set. This
does not change the tested application, scanpath rules or strength policy.

These detached synthetic view tests do not establish visible article UI behavior,
natural word/line accuracy, scanpath difficulty classification, eye-to-aid timing
or reading benefit. The dedicated matched raw/order pilot screen remains pending.

The same APKs and corrected tests subsequently passed on a second phone,
SM-G991B/API 35; see [device check 02](scanpath-device-check-02.md).

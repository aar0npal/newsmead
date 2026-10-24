# Scanpath Android device check 02

**2026-10-01, result recorded at 23:11 +08:00.** The owner requested the same
tests on another phone. ADB identified one connected device: Samsung SM-G991B,
Android API 35. Repository checkpoint: `9559c95` (`Fix Android test fixtures and
record successful device checks`); tracked source was unchanged before the run.

## Matched artifacts and installation

Reused the exact target and corrected test APKs from
[device check 01](scanpath-device-check-01.md); no rebuild or code change.

| Artifact | Identity |
| --- | --- |
| Target | `com.newsmead`, version 0.2, versionCode 1; application sources from `e5fa5b2` |
| Target APK SHA-256 | `3F560690BA361611C0F1A3AEFD4647A58A1137E5D9207DB1F66ACDEDADA49AF8` |
| Test APK SHA-256 | `95F83D59228258EA9C050139F15BF45AEC503C90C75D488EDA4804F8ED302F09` |
| Test sources | The two corrected Android fixtures now committed in `9559c95` |

The existing NewsMead package was present. Both APKs installed successfully with
`adb install -r`; no app-data clear or uninstall was performed. Only the two test
classes below were selected. They instantiate synthetic text and detached views
without starting an activity, gaze provider, camera, calibration or recording.

## First attempt: all 4 passed

| Test | Result |
| --- | --- |
| `wordOffsetsStayUtf16AndTargetsUsePhysicalScreenOrigin` | Passed, status 0 |
| `unchangedLayoutReusesLineDataAndMixedBidiAbstains` | Passed, status 0 |
| `magnificationUsesItsReservedViewAndReportsADrawProxy` | Passed, status 0 |
| `anExpiredMagnifierNeverReportsADrawnFrame` | Passed, status 0 |

Runner summary: **`OK (4 tests)`**. The invocation was:

```cmd
adb shell am instrument -w -r -e class "com.newsmead.gaze.scanpath.ScanpathTextGeometryTest,com.newsmead.gaze.aid.ReadingAidRenderingTest" com.newsmead.test/androidx.test.runner.AndroidJUnitRunner
```

The operation was targeted to the second phone's ADB identity. Runner assertions,
rather than the ADB process exit code alone, determine success.

## Cross-device scope and remaining checks

The same four synthetic tests now pass on SM-A566B/API 36 and SM-G991B/API 35
using identical APKs. These are four distinct tests executed on two devices,
not eight distinct tests or independent reader observations.

The fixtures use prescribed dimensions and synthetic anchors. They do not test
the visible article viewport on each phone, camera inference, natural word/line
accuracy, live scanpath-to-aid timing, difficulty classification or reading benefit.
Visible article aid inspection and the matched raw/order pilot screen remain
pending. No extra collection or calibration was performed during this check.

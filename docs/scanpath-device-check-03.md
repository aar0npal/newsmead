# Interactive scanpath replay device check 03

**2026-10-02.** The owner authorized building the interactive replay milestone
and explicitly authorized a corrected phone retest. Device: Samsung SM-G991B,
Android API 35, ADB identity `R5CR60YSJ2X`. Source: uncommitted replay changes on
`measurement/scanpath-pilot`, starting at `25b8c34`.

## Artifacts and attempts

Both packages installed with `adb install -r`, preserving app data. Tests launch
the replay activity with synthetic prose and scripted coordinates. No camera,
gaze provider, calibration or participant collection was started.

| Attempt | Target APK SHA-256 | Test APK SHA-256 | Result |
| --- | --- | --- | --- |
| Initial | `86F253A98D86A89EDBF1D9B30825E6AC7CC4CE2ED474A75461184EC18DF2D383` | `6F40E9970BFFDA96B89A8D67115D521FE1A700915F4F2CD394F20483A8E54B41` | 4 passed, 1 failed: test assumed all evidence IDs were ongoing prefixes. |
| Owner-authorized retest | Same target | `E2A7BB61F8ED98A6F97FD61C94161A3CEDAF0FBD24FFEA0E47624F3A9E3CA6E8` | `OK (5 tests)`, 21.452 s. |
| Final layout check | `51B3FE6A685D0731D1C8290F1508BF8E15504BA787A4FE46751BF0D01C91D62B` | Same corrected test | `OK (5 tests)`, 21.277 s; synthetic UI preview inspected. |

The initial failure was `AssertionError: prefix precedes action` in the
all-scenarios test. Its return-sweep trace contained the supporting finalized
fixation before the line-start action, which cited `fixation:1`. The test lookup
handled only `candidate_prefix` IDs. Added finalized-fixation IDs to that lookup;
no detector/policy change was needed. The attempt stopped after navigation and
return sweep; later scenarios were not inferred as passing.

After the successful retest, visual inspection found clipped Back/Controls
labels. Set those labels to 14 sp, 8 dp horizontal padding and one line, rebuilt
the target and repeated the focused checks. The final preview shows both labels
fully, a word magnifier, current-line highlight and synthetic cursor. The test
made it with `root.draw(Canvas)`, not a camera image or another app's screen.

## Final tests

| Test | Result |
| --- | --- |
| `pausingWithoutStoppingTheActivityTerminatesTheReplay` | Passed |
| `zeroStrengthWithholdsEveryAidWhileTheDetectorStillRuns` | Passed |
| `scrollingInterruptsTheReplayAndWritesTheBoundaryInsteadOfUsingOldCoordinates` | Passed |
| `cappedTraceRetainsAnIncompleteFooterAndPartialFile` | Passed |
| `allScenariosUseActualLayoutAndProduceExpectedPatternsAndDrawReceipts` | Passed; all five scenarios |

Invocation:

```cmd
adb -s R5CR60YSJ2X shell am instrument -w -r -e class com.newsmead.gaze.scanpath.replay.ScanpathReplayActivityTest com.newsmead.test/androidx.test.runner.AndroidJUnitRunner
```

The last test requires expected patterns, chosen aids and drawing receipts. It
checks capture/delivery/record timestamp inequalities, bounded delivery lag,
prior supporting event/prefix/fixation IDs, action evidence timing and receipt
timing within action lifetime. Assertions determine success, not ADB's process
exit code alone.

## Final scenario outputs

| Scenario | Samples including 1 s tail | Required patterns found | Drawn proxies | Chosen strength range |
| --- | --- | --- | --- | --- |
| Navigation | 56 | P01/P02/P03/P07/P10 | Current line | 0.25 |
| Return sweep | 35 | P05/observed P09 | Current line, destination-line start | 0.25 / 0.6 |
| Word refixations | 52 | P04/P12 | Current line, word magnifier | 0.25 / **0.25–1.0** |
| Line cycle | 79 | P08/P10/P11/P12 | Current line, focus region | 0.25 / **0.625–0.8125** |
| Loss/recovery | 43 | P13 | Current line after fresh evidence | 0.25 |

Strength is a configured UI parameter, not a difficulty probability. These are
minimum required IDs; additional descriptors and closure boundaries occur.
P12 update/P13 record counts are not independent reading-problem counts. Focus
was explicitly enabled for the all-scenarios test; the default remains off.

Local generated evidence, outside the source staging recommendation:

- `output/scanpath-replay-check-03/final-runner.txt`
- `output/scanpath-replay-check-03/summary.json`
- `output/scanpath-replay-check-03/scanpath_replay_word_preview.png`
- Navigation: `scanpath_replay_663f7a70-b91e-4f50-9eb3-fa46b2ade374.jsonl`
- Return sweep: `scanpath_replay_099e2a67-8889-4d6e-9cd5-726518f534c6.jsonl`
- Word: `scanpath_replay_8c212b9f-f0b6-43f7-884c-65a88a0002ed.jsonl`
- Cycle: `scanpath_replay_810ca0a9-8850-436b-ac51-4b36ac4cea66.jsonl`
- Loss: `scanpath_replay_83a98bc1-2b7b-4215-be67-30a126327107.jsonl`

Final trace copies are in the same local output directory; originals remain
app-private. No human recordings were read or exported.

## Software checks and limits

All **34 focused JVM tests** passed: 9 engine, 1 reference parity, 15 policy,
2 engine-to-policy and 7 replay-scenario tests. Debug and Android-test assembly
passed with Java 17, offline Gradle dependencies and the in-process compiler.
Independent review led to immediate pause stopping and a success flag requiring
drawn proxies. Trace completion reserves a footer and publishes a final filename
only after successful close.

This checks real Android layout and scripted input-to-display behavior on one
phone. It does not establish natural gaze accuracy, fixation validity, human
difficulty classification, physical cue visibility/eye-to-cue latency or reading
benefit. Earlier four geometry/magnifier tests passed on two phones; these new
replay-activity checks ran only on SM-G991B. Live prompted tasks and matched
human recording remain the next implementation package.

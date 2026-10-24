# A56 merged-build compatibility check 05

**2026-10-08.** Connected Samsung SM-A566B, Android 16/API 36, arm64-v8a,
ADB identity `R5CY40Y2C5W`. Installed source: `2a3931c`, the owner-authorized
Recommendation merge into `measurement/scanpath-pilot`.

**Current result:** after owner authorization, the corrected build passes all
**38 A56 checks in 94.573 s**, with **83 focused JVM checks** passing locally.
Both corrected APKs are installed using data-preserving updates. The initial
failed run and command-line error are retained below. Actual home-feed UI smoke
remains unverified: its captured hierarchy showed the launcher rather than
NewsMead, with no retained crash message for the launched PID.

## Initial attempt

Both reviewed APKs installed successfully with `adb install -r`, preserving app
data. One camera-free instrumentation invocation ran **38 tests in 89.668 s**:
**33 passed, 5 failed**. ADB exit 0 is not success: the runner reported
`FAILURES!!!`. Both failed and passing invocations report final code -1, so the
JUnit summary establishes the result. Further device attempts stopped under
AGENTS.md's one-failed-attempt boundary.

| Artifact | SHA-256 |
| --- | --- |
| App | `f385b8cd4cf9682344196e64b368199b11f94a1af230b13c4f2b153a5021beda` |
| Android test | `50cec64d6eb60cca3fb3b297a450ff364cbff5889f25f2f619b785fcd6c3f38e` |

| Group | Passed / total | Result |
| --- | --- | --- |
| Native rendering | 4 / 4 | Gutter separation/fixed marker appearance and retained magnifier rendering checks |
| Definition popup | 1 / 4 | Parser passes; three native fixtures fail before popup checks with `NoSuchMethodError` |
| Consultation session | 1 / 1 | Finalized residence, consultation boundary and callback reentrancy regression |
| Offline repository | 2 / 2 | Covered and uncovered bundled-only lookup behavior |
| Article demo presenter | 7 / 7 | Actual source words, geometry/reset behavior and fixed continuation preview |
| Replay demo activity | 5 / 5 | Six-stage presentation, definition-to-demo transition, timer/pause/scroll behavior |
| Synthetic pilot | 6 / 6 | Prompted raw scoring, reset/viewport checks and definition consultation exclusion |
| Synthetic replay | 7 / 9 | Two multi-scenario checks stop when continuation-return input cannot be generated |

## Findings and local correction

1. **Stale instrumentation bytecode:** three popup fixtures call the removed
   20-argument `ReadingAidAction` constructor. The current app constructor has
   21 arguments, including continuation evidence. Host `javap` confirms that
   mismatch in the compiled fixture. Prepare a fresh nonincremental build of
   app and test packages; do not add a production overload to hide stale tests.
2. **Replay geometry:** `continuation_return` required a destination word center
   in the first quarter of its line. A one-word paragraph-final row can have its
   only center at 50%. Current policy accepts a supported landing anywhere on
   that row. The generator now uses real destination/arrival centers, retains
   first/last-quarter source progress and searches later contiguous fully visible
   rows if necessary. Unsupported geometry still abstains.

Three new JVM regressions cover both continuation directions, one-word rows,
later compatible source rows, clipping/abstention, expected marker/clearing and
unchanged sample timing. Independent source review found no actionable issue.
This corrects synthetic scenario generation; controller and detector cutoffs,
measurement protocols and gaze model are unchanged.

The first fresh-build command stopped before compilation because PowerShell
misparsed the unquoted `-Pkotlin.incremental=false` option as a task suffix.
The corrected command quotes the property. The owner then authorized solving the
issues and proceeding with the corrected build/device retest.

## Corrected build and authorized retest

A nonincremental rebuild reran all 85 Gradle tasks and succeeded in 1m 39s.
All **83 focused JVM checks** pass with zero failures/errors/skips. Host `javap`
confirms that the rebuilt popup fixture calls the current constructor with its
continuation-evidence default marker. No production overload was introduced.

| Corrected artifact | SHA-256 |
| --- | --- |
| App | `3057913a0c1d560517d25a1c8f6c4fa3eaad5ad7ebd365699b5c184390456e3f` |
| Android test | `ca393e7abaf08432c196b55b4ddf0481b817c122aab2212eee2f8e6550271c1c` |

Both installed successfully with `adb install -r`. The same eight classes now
report **OK (38 tests)** in **94.573 s**: rendering 4/4, popup 4/4, consultation
1/1, offline repository 2/2, presenter 7/7, demo activity 5/5, pilot 6/6 and replay
9/9. The formerly failing popup fixtures and both continuation paths pass.

Fresh build command (CMD):

```cmd
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks "-Pkotlin.incremental=false"
```

The actual Home feed smoke launch reported `MainActivity` / `Status: ok`, but
its captured UI hierarchy contained the Samsung launcher and zero NewsMead
nodes. The captured app PID had no retained DataHelper or crash messages.
This does not establish an API failure or successful feed display. No additional
UI attempt ran after that unsuccessful check; a future check needs NewsMead
left visibly open on the phone. No reading article or camera was opened.

## Evidence and limits

- Initial installation/runner/constructor evidence and summary:
  `output/scanpath-device-check-05/`.
- Source changes: replay scenario generator and its JVM regression tests.
- No camera, live gaze, calibration change, raw human journal read or export ran.
  The phone's camera permission was already granted and was not changed.
- Corrected build, ABI evidence, hashes and `retest/runner.txt` are retained in
  the same local output directory. The app remains on the corrected build.
- Actual news-feed display and publisher-article smoke remain unverified.
  Computer-side API success and the earlier 80 JVM passes belong to the preceding
  [merge checkpoint](recommendation-merge.md).
- Passing synthetic fixtures establishes software behavior on this A56; it does
  not establish human gaze accuracy, reading difficulty or aid benefit.

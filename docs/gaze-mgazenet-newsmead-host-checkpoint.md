# MGazeNet primary integration: host checkpoint — 2026-09-10

**Device-check follow-up:** after commit `5811486` and separate authorization,
the exact APK pair passed all nine camera-disabled tests on both the A56 and
SM-G991B. See
`gaze-mgazenet-newsmead-device-check.md` for installation, permission,
data-preservation and both phones' results. The host-only account below remains
the historical software checkpoint. A later synthetic save/force-stop/load test
also passed across distinct Android processes on both phones, as documented in
the device-check report.

Software-only integration is implemented in the existing `mgazenet-feasibility`
worktree. Starting HEAD was `fa8acbfcd68fbed08a9e87340751d97becfa4cb1` and the
worktree was clean. The reference `gaze-pipeline-improvements` branch remains
`ec635fd01905544c8251c37b6891105ebbdee923`. No branch switch, staging, commit,
phone contact, installation, CAMERA action or participant calibration occurred.
The user retains control of staging and commits.

## Implemented boundary

`GazeCalibrationActivity`, `GazeTestActivity`, `ReadingValidationActivity` and
`ArticleFragment` now use MGazeNet. None constructs the old provider or reads
the old calibration store. There is no current/MGazeNet backend selector.
The legacy mapper/store/source files remain unchanged and unused by those
normal paths. Historical research tools remain available as historical tools.
The existing touch-validation mode is unchanged.

The production package contains adapted benchmark camera conversion, crop
geometry, preprocessing, MNN JNI/inference, and X/Y OpenCV EPS-SVR code. It does
not depend on the benchmark Android application. The ordinary app build uses
the ignored, explicitly fetched vendor inputs and verifies all five pinned
model/runtime hashes. It also verifies the existing production localizer asset.
Generated build assets carry the model, manifest and attribution; neither
vendor binaries nor generated assets are added to Git. `verifyMgazeNetDebugApk`
checks the actual APK entries, including which `libc++_shared.so` was selected.
Release shrinking remains enabled, with an explicit keep rule for MNN JNI names.
The pinned native runtime restricts this research build to `arm64-v8a`.

The gaze path remains on-device and has no network/analytics/logging path for
frames, crops, landmarks or 258-value features. NewsMead's existing networking
for news/account functions is not removed. The source retains upright
unmirrored frames, right-eye-only flipping, normal-precision four-thread MNN,
Tasks VIDEO/GPU with initialization-only CPU fallback, and the original
resolution-dependent eye-area admission rule. Both documented bitmap lifetime
repairs are retained.

## Calibration and persistence

Setup does not instantiate the camera/native source, request CAMERA, write a
calibration, or retain a screen-on flag. Explicit Start requests permission if
needed and begins initialization. Native synthetic train/save/load/predict
parity must succeed before the source binds the camera. Failure blocks the
session; it never falls back to the old tracker or saves all training rows.

Calibration retains the real existing full `CalibrationView` layout and its
E-053 screen-origin helpers. Targets are row-major `.10/.3667/.6333/.90`.
Practice is separate. Each fit target contributes exactly 45 admitted finite
258-value rows, with 720 rows total; practice and verification never train.
Source buffers are copied on admission and worker ownership is transferred
before fitting so cancellation cannot clear arrays while the solver reads them.
Training arrays are cleared after fit/failure and pending UI rows on cancellation.

The integrated presentation retains the 1,800 ms attention contraction,
acknowledges drawing, then uses the pinned 1,500 ms settle and 500 ms post-capture
wait. This extra attention period is an explicit integration adaptation, bound
in the protocol identity, rather than a claim of identical benchmark timing.
Each collection target has a 30-second collection deadline, initialization has
a 20-second deadline, and the session has a ten-minute deadline. Layout changes,
backgrounding, failures and cancellation stop the session and release resources.

After fitting, the fixed `fit_6` repeat and five centre/quadrant checks use
3,000/2,500/250 ms settle/measure/drain windows. They display descriptive numeric
summaries; they do not assign a pass, refit, reject regions selectively, or apply
a correction. The camera is paused before the explicit Save decision. These
on-screen summaries are not a new persisted research-evidence schema.

The only persistent participant artifact is an atomic binary bundle under
`noBackupFilesDir/mgazenet-v1/calibration.bin`, containing two trained native
SVR models and a hash-bound manifest. Trained support vectors are part of the
participant-specific model; no separate training-row dataset is retained.
The namespace is outside Android backup and outside ordinary gaze diagnostic
exports. Existing `calibration_16point.csv`, feature markers, affine/drift files
and participant evidence are neither read nor deleted.

The manifest binds model/localizer hashes, preprocessing and collection version,
SVR settings, device/install identity, physical display dimensions/rotation,
screen coordinate contract, measured calibration viewport, all target positions,
and actual camera dimensions/rotation/localizer delegate. Loading reconstructs
and validates that geometry and checks the current display/device. Camera
initialization rejects a changed acquisition signature. Native loading checks
solver parameters, feature count, support-vector and decision-coefficient
finiteness, and finite probe predictions. Missing, legacy, partial, corrupted,
changed-hash/version/device/display artifacts require calibration; no legacy
fallback exists. Merely checking compatibility is read-only, including after an
interrupted atomic write.

Native OpenCV persistence requires temporary model paths. Temporary models stay
in the same no-backup namespace, are removed in `finally`, and interrupted
temporary files are cleaned on explicit runtime initialization under a shared
storage lock. This cleanup never targets the committed bundle or legacy files.
Every actual save checks synthetic prediction parity against its reloaded pair
and checks cancellation before committing. Android `AtomicFile` commits the
single bundle, so X/Y cannot independently become the current calibration.

## Output and measurement limits

`GazeProvider` still carries physical-screen pixel coordinates. No median/One
Euro filter, affine/drift layer, target-answer correction or line snapping is
applied by MGazeNet. AOI, scrolling, RSI and reading inference algorithms remain
unchanged. Invalid or missing input never triggers coordinate replay.

The integration explicitly defines a **500 ms operational output expiry**.
This is a new operational policy, not a validated accuracy/reading-event limit
or promotion of one column of the earlier sensitivity analysis. Stale raw finite
predictions remain observable but are not emitted to `GazeProvider`; expiration
also clears the debug dot. No interpolation fills unavailable intervals.
The reading trace is schema 6 and includes raw physical predictions, capture and
delivery times, ages, rejection/stale events and observed arrival/busy-drop
counts. It identifies MGazeNet and the expiry policy. The former optional
reading vertical correction is fixed OFF.

The nine-point check now uses raw MGazeNet observations, fixed
3,000/2,500/250 ms windows and no estimated dot during targets. It no longer uses
the old two-feature collector or fits/applies an affine correction. Its
on-screen results are descriptive, with no pass claim or automatic rerun. It is
not interchangeable with historical current-model diagnostic records.

Article tracking releases resources on stop and reopens on resume; returning
from calibration also rebinds the new source. Controlled reading validation
terminates its partial trace on backgrounding/failure. These lifecycle changes
do not change AOI/RSI inference. The known ~220 ms ages, missing intervals and
uncertain reading-event validity remain substantive measurement risks. No new
reading, population, phone accuracy or production-superiority claim is made.

## Host verification

| Check | Result |
| --- | --- |
| Final focused production gaze JVM tests | 207 passed; zero failures/errors/skips |
| Final production JVM suite excluding external Firebase account creation | 208 passed; zero failures/errors/skips |
| Benchmark JVM tests | 153 passed; zero failures/errors/skips |
| MGazeNet Python suite in pinned reference environment | 75 passed |
| Synthetic native host OpenCV 4.11.0 serialization | Two 720-row/258-feature regressors; 20 queries each; exactly equal finite predictions after reload and fresh process |
| Vendor verification and APK-entry hash verification | Passed |
| Production debug and Android-test APK assembly | Passed |
| APK native inspection | Required MNN JNI exports present; strong C++ symbols resolve; no native execution |
| `git diff --check` | Passed |
| Staged paths / ignored vendor or private evidence staged | None |

The first complete production suite, before the last output/lifecycle tests,
reported 205/206 passing with the existing `FirebaseTest.createAccount` failure.
Automatic approval review rejected a later full-suite rerun because that test
can create remote Firebase accounts/data. The final safe suite explicitly
excludes that class using `tools/gaze/mgazenet/host-tests.gradle`; it is not
silently counted as passing. The default project test configuration is unchanged.

Host work also retained ordinary development failures: a cache-write sandbox
denial, corrected PowerShell argument quoting, and a corrected Gradle ZIP import.
They preceded the passing final builds. No permission rejection was bypassed.

Final debug APK SHA-256:
`131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9`.

Final Android-test APK SHA-256:
`76bcf8759237409549f82257c6f360bd465ca0d7d3d4246a5c03e2d0f0feb7bd`.

The APK is **not fully 16 KB page-size compatible**: the retained
`libimage_processing_util_jni.so` and historical research dependency
`libtensorflowlite_jni.so` have 4 KB load alignment. OpenCV also supplies a C++
runtime, causing AGP's duplicate-library warning; the actual packaged C++ hash
is explicitly verified as the pinned MNN copy. No runtime compatibility claim
is inferred from symbol/hash checks.

Builds used Java 17 at `C:\Users\USER\.jdks\jbr-17.0.14`, the existing user
Gradle cache, and the Kotlin in-process compiler. Host logs, earlier build hashes
and native audit JSON are under ignored `tmp/mgazenet-*`; they contain software
and synthetic evidence only. No ignored path should be staged.

Reproduce the safe host checks from Windows CMD after the documented pinned
vendor fetch, with Java 17 configured:

```bat
gradlew.bat :app:testDebugUnitTest -I tools/gaze/mgazenet/host-tests.gradle :app:verifyMgazeNetDebugApk :app:assembleDebugAndroidTest -Pkotlin.compiler.execution.strategy=in-process
gradlew.bat -PmgazenetBenchmark=true :mgazenet-benchmark:testDebugUnitTest :mgazenet-benchmark:verifyVendor -Pkotlin.compiler.execution.strategy=in-process
mgazenet-benchmark\build\reference-env\Scripts\python.exe -m unittest discover -s tools\gaze\mgazenet -p test_*.py -v
```

## Stop boundary

This is the verified **host-build checkpoint**. Android instrumentation tests
were compiled, not executed. Their setup, bitmap, crop/inference, native
serialization/store-reload and cancellation assertions still require a later
explicitly authorized camera-disabled install/native check. Host OpenCV parity
does not substitute for execution of Android's JNI path. No phone was queried
even to inspect connection state. CAMERA and participant calibration remain
separate later authorizations.

Do not start participant comparison from this checkpoint. First verify the
exact APK on the actual layouts and native runtime under the allowed boundary,
then separately freeze the model comparison and its measurement policies.
Existing fatigue context and all unfavorable historical sessions remain in the
evidence. No additional participant run was needed for this software work.

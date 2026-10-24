# Native MGazeNet feasibility: isolated benchmark

Checkpoint: 2026-09-07, work directly in `newsmead`, starting from `ec635fd`
on `mgazenet-feasibility`. Read after `gaze-accuracy-handoff.md` and
`gaze-mgazenet-handoff.md`. Their historical evidence and rejected experiments
remain valid records; this document does not supersede their accuracy gates.

**Current status: both phones pass camera-free native checks and synthetic
numerical parity. The bounded inference investigation is complete; foreground
tests supersede the initial timings as a guide to configuration. Camera-path
performance and gaze accuracy remain unmeasured. No production tracker promotion.**

The user's clarified priority is accuracy and trustworthy reading measurement.
Continue from [accuracy readiness](gaze-mgazenet-accuracy-readiness.md), which
records the unresolved localizer, calibration eligibility, independent
validation and reading-measurement requirements. Further speed optimization is
not the automatic next step.

The subsequent [accuracy harness checkpoint](gaze-mgazenet-accuracy-harness.md)
implements the separate session and records completed camera-free setup/layout
checks on both phones. Personal gaze accuracy and the real camera path remain
untested; read that checkpoint before repeating earlier work.

The subsequent [inference cost investigation](gaze-mgazenet-inference-profile.md)
separates validation, copies and native execution and tests thread counts.
The original timings below remain the original checkpoint evidence.

## Scope and isolation

`mgazenet-benchmark` is a separate Android application:
`com.newsmead.mgazenetbenchmark`. Root settings include it only with
`-PmgazenetBenchmark=true`. A normal project listing contains only `:app`.
The main app has no dependency on it; no active tracker, calibration store,
reading logic, main app manifest, or main app dependencies were changed.
Nothing starts automatically when the benchmark launches. Synthetic checks
never request camera permission. The separate camera button requests it only
when explicitly pressed. Stop and backgrounding stop the camera run.

At the initial build checkpoint no phone was contacted. The first follow-up
recorded only read-only A56 properties. The user subsequently authorized the
reference environment and separate benchmark/test installations; their results
are recorded in the installation checkpoint below. No camera or participant
calibration was used. No NDK, CMake, or replacement Python runtime was installed. Building
downloaded pinned benchmark libraries and normal Gradle dependencies using
the existing tools. No staging or commits were performed.

## Verified source/model contract

| Item | Pinned evidence / consequence |
| --- | --- |
| GazeFollower | v1.0.2, commit `553920edcb7998c029828677f50f6d8eb4a16249` |
| Public model | `base.mnn`, 6,245,556 bytes; SHA-256 `2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96` |
| Provenance | Repository and PyPI 1.0.2 wheel contain identical weights and estimator/calibration code. Public weights are the seven-million-sample version; larger requested weights and paper results cannot be attributed to these bytes. |
| Stored inputs | float32 `face` 1×3×224×224; `left` and `right` 1×3×112×112; `rect` 1×12 |
| Upstream Python | Module API receives NHWC patches and converts to the stored graph's layout. Android Session JNI receives explicit NCHW. |
| Output | `output_0`: 258 values (2 raw outputs + 128 eye + 64 face + 64 rectangle features). All 258 feed SVR. First two are not phone screen pixels. |
| Graph | 807 operators, including dynamic shape operations; no external model files identified. Full CPU runtime used, not a stripped operator subset. |
| Runtime | MNN 3.6.1, commit `d407447ed56c4121a11ccbd266dc184ca1ead0c2`; CPU, four threads |
| Precision distinction | Stock Android JNI does not expose BackendConfig precision and uses default normal precision. Upstream Python explicitly uses low precision. Separate comparisons are mandatory. |
| Calibration | OpenCV EPS-SVR, RBF, C=1, gamma=.005, P=.001; MAX_ITER 10000, epsilon argument 1e-4; independent X/Y regressors |
| Training unit | Upstream keeps 45 frame features per actual target, 13 targets by default; initial center practice is separate. This is not a fit to 13 target medians. |
| Labels | Default screen fractions, multiplied by screen dimensions after prediction. Upstream can use physical units with screen dimensions supplied; old pixel/iris calibrations cannot be reused. |

Primary sources: [estimator](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/gazefollower/gaze_estimator/MGazeNetGazeEstimator.py),
[alignment](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/gazefollower/face_alignment/MediaPipeFaceAlignment.py),
[SVR](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/gazefollower/calibration/SVRCalibration.py),
[calibration controller](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/gazefollower/calibration/CalibrationController.py),
[MNN JNI](https://github.com/alibaba/MNN/blob/d407447ed56c4121a11ccbd266dc184ca1ead0c2/source/jni/mnnnetnative.cpp).

## Preprocessing and unresolved equivalence

The benchmark uses upright **unmirrored RGB**. CameraX RGBA bytes are unpacked
explicitly with row stride respected, then rotated; neither the current
tracker's mirrored image nor its iris feature vectors are reused. The
[CameraX API](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis#OUTPUT_IMAGE_FORMAT_RGBA_8888())
specifies R, G, B, A byte order. Four rotations, non-square dimensions, color
channels, and a last row without padding have camera-free instrumented tests.

`GazeGeometry` ports upstream 478-point crop geometry: ties-to-even pixel
rounding, face aspect correction, strict eye borders, upstream eye identity
(33/133 for `left`, 362/263 for `right`), and fractional eye padding before
integer truncation. Invalid/non-finite inputs are rejected. Deliberately
malformed coordinates are rejected rather than reproducing int16 overflow.
`Preprocessor` uses OpenCV INTER_LINEAR on uint8 crops before float32 /255;
only the resized right eye is flipped horizontally. Crops use half-open
bounds, clipping at the bottom/right without padding. The 12 rectangle
values retain original box dimensions and normalize W,H,X,Y by image W,H.

The camera harness uses MediaPipe Tasks 0.10.29 VIDEO with the existing,
hash-pinned Face Landmarker asset, GPU with initialization-only CPU fallback.
Its 478 landmarks are **not established as numerically equivalent** to the
upstream legacy refined FaceMesh. It also operates on the phone's upright
portrait frame rather than the desktop camera's resized landscape frame.
Known boxes in synthetic fixtures verify tensor preparation, not landmark
localization or real phone crop distributions. The upstream BlazeFace option
changes geometry and color handling, so it is not treated as a drop-in speedup.

## Android package audit

This module keeps AGP 8.3, compile/target SDK 34, min SDK 24 and ARM64 only.
It uses CameraX 1.3.4, MediaPipe Tasks 0.10.29 and full OpenCV 4.11.0 from Maven.
MNN's similarly named OpenCV component does not replace OpenCV's ML/SVR API.

The official [MNN 3.6.1 Android archive](https://github.com/alibaba/MNN/releases/tag/3.6.1)
is pinned by SHA-256
`46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71`.
Only `libMNN.so`, `libmnncore.so` and `libc++_shared.so` are extracted for CPU
Session inference. No MNN Express, GPU, LLM, audio, or OpenCV-substitute library
is included. The model and three native runtime hashes are checked at build;
the model is checked again before inference. JNI input lengths and finite
values are checked before its unchecked native copy operation.

OpenCV also supplies `libc++_shared.so`; AGP 8.3 selects the app module's MNN
copy and warns about the duplicate. `audit_apk.py` verifies the actual packaged
hash, the nine required JNI exports, and strong C++ imports against bundled
exports. They pass statically. This does not certify ABI behavior, Android
system-symbol availability, native loading, or inference execution.

**Specific alignment finding:** CameraX 1.3.4's
`libimage_processing_util_jni.so` has 4 KB ELF segments. The five other bundled
libraries, including MediaPipe and OpenCV, have 16 KB alignment. Compressed
native packaging avoids the old AGP ZIP-alignment issue but cannot repair ELF
alignment. The A56 follow-up confirms 4 KB pages; the second phone remains unchecked. This is not a
16 KB-compatible package claim. Android's [page-size guidance](https://developer.android.com/guide/practices/page-sizes)
separates these requirements. Do not hide this result by treating the audit
script's JNI/C++ success exit code as a general compatibility pass.

Upstream model/crop terms are CC BY-NC-SA 4.0, and MNN is Apache 2.0. Attribution
and upstream license texts are bundled; see `mgazenet-benchmark/NOTICE.md`.
No commercial-use or redistribution approval is inferred.

## What the harness measures

Synthetic mode writes three deterministic RGB fixtures' face/eye/rectangle
tensors and all 258 model outputs, checks repeatability, then times 100
preprocessing/inference iterations after 20 warm-ups. It fits two **synthetic**
585-row SVRs, records seven prediction pairs, support-vector counts, fit time,
and prediction time. It never fits a person or reads a saved calibration.
Successful execution is labelled `completed_pending_external_parity`.

`reference.py reference` independently prepares those tensors with NumPy and
OpenCV and runs the upstream MNN Module path at normal and low precision.
`reference.py compare` checks hashes, shapes, completeness, runtime metadata,
finite outputs and SVR predictions. Tolerances are tensor absolute 1e-7,
inference absolute 1e-3 plus relative 1e-4, and SVR absolute 1e-5. These are
predeclared engineering checks, not validated gaze-error bounds; do not loosen
them after seeing failures without investigation. Normal and upstream-low
results stay separate. A normal pass alone is not upstream precision parity.

The reference command requires an **already available** MNN 3.6.1/OpenCV
4.11.0/NumPy Python environment. None was found locally, so it has not run and
no reference outputs have been fabricated. Its host comparator tests use mock
numbers solely to verify that corrupt or incomplete evidence is rejected.

Camera mode runs for five seconds warm-up plus 120 seconds of measurement,
with a wall-clock stop even if no frames arrive. It requests 640×480 at 30 FPS,
records delivered dimensions and rotation, uses one in-flight job, and counts
observable busy drops separately from unobservable CameraX discarded frames.
It records per-frame copy/rotation, localization, crop/tensor preparation and
MNN I/O timings, completion FPS, feature gaps, no-face/invalid-crop outcomes,
and Android thermal status. Capture-to-feature age is used only for a camera
clock reporting the REALTIME source; otherwise analyzer-arrival latency is
reported and capture-age sample count is zero. Median/P95/max are retained.

This is camera-to-feature timing. Calibration, temporal filters, UI rendering,
and reading behavior are excluded. Synthetic SVR timing cannot simply be added
to certify production latency. No gaze coordinates or accuracy result are
emitted. Camera pixels, crops, landmarks, and feature vectors are discarded;
only numeric timing reports persist in this separate app's private files.
Synthetic tensor files are permitted only in synthetic mode.

## Reproduce without contacting a phone

Windows CMD, from the repository root, using existing Java 17 and Python:

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
python tools\gaze\mgazenet\fetch_vendor.py
gradlew.bat -PmgazenetBenchmark=true -Dorg.gradle.java.home="C:\Program Files\Java\jdk-17" -Pkotlin.compiler.execution.strategy=in-process :mgazenet-benchmark:testDebugUnitTest :mgazenet-benchmark:assembleDebug :mgazenet-benchmark:assembleDebugAndroidTest
python -m unittest discover -s tools\gaze\mgazenet -p test_reference.py -v
python tools\gaze\mgazenet\audit_apk.py mgazenet-benchmark\build\outputs\apk\debug\mgazenet-benchmark-debug.apk --output mgazenet-benchmark\build\native-audit.json
```

The fetch is explicit, hash-pinned and writes only ignored `vendor/`; builds
do not silently fetch that model/runtime. Gradle resolves its declared Maven
dependencies normally. `build/` and `vendor/` are ignored in this module.
Reports can later be retrieved from the separate app's `files/benchmarks/`
after device work is authorized. No installation or calibration commands are
part of this checkpoint.

## Initial validation and decision boundary (before installation)

- Benchmark and camera-free instrumentation APKs compile successfully.
- Seven JVM crop/bounds/normalization tests pass.
- All 80 existing gaze-focused app JVM tests pass; no app source changes.
- Twelve standard-library evidence-comparator tests pass, including NaN,
  malformed shapes, changed versions, missing/duplicated fixtures, corrupted
  bytes, path traversal, partial runs and separate low-precision failures.
- APK static audit verifies pinned MNN runtime bytes, required JNI exports,
  and strong C++ symbol availability. CameraX's 4 KB ELF finding remains.
- Four Android instrumented tests compile but have **not run**.
- No native numerical parity or either-phone timing result exists yet.

Built debug APK SHA-256:
`ac2f53cbce00ff58e0dd12063758a956a888b543248938970a4efc065c5c2873`.
The native audit is preserved in `gaze-mgazenet-native-package-audit.json`.

Next evidence is camera-free native loading and synthetic parity on **both**
SM-A566B and SM-G991B, with actual OS/ABI/page size recorded. Reference-runtime
availability must be resolved before claiming numerical equivalence. Only
after that should the prepared camera timing screen assess the complete
camera-to-feature cost and freshness on both devices. Localizer equivalence
and sustained thermals remain separate questions.

Any later participant calibration/reading validation requires its own agreed
protocol, independent held-out targets and reading-line accuracy, repeatability,
tail errors, realistic pose and lighting, and calibrated output latency. The
preserved A56 reading contrast (36.5% versus 85.4% within one line), compact
candidate failures, and E-074 rejected shifts remain counterevidence against
declaring success from a working model, faster FPS, or one good session.
No calibration runs or active tracker replacement are requested now.

## Follow-up: source crop parity and device prerequisites, 2026-09-07

The exact upstream alignment source was read again and verified against
SHA-256 `5378917b8f53d4192a22e02fbd0fdbe42064a408db22d4a641bdca2e902426ff`.
`tools/gaze/mgazenet/crop_reference.py` extracts its unchanged `detect` and
`calculate_polygon_area` methods plus index constants. It supplies synthetic
FaceMesh results and image dimensions; it does not import GazeFollower or
MediaPipe, initialize a camera, or run the model. The already available NumPy
runtime generates expected decisions and face/eye boxes.

All **89 upstream synthetic cases pass** against the actual Kotlin geometry:
45 accepted and 44 rejected. They cover square, portrait and landscape frames,
scale and translations, eye boundaries, face aspect corrections, fractional
pixel coordinates and lips outside the image. `UpstreamCropParityTest` reads
the generated `src/test/resources/upstream-crops.tsv`; its SHA-256 is
`cbf84fa65999957b45f6889a3dd945399feb8ac3bdf898bf78ed546992c44ba5`.
Together with the existing seven geometry tests, the benchmark JVM suite now
has 96 passing tests. This establishes crop-decision/box agreement for the
specified synthetic inputs only. Landmark localization, eye-openness values,
resized pixel tensors, model outputs and actual gaze remain separate checks.
The previously built APK is unchanged because this follow-up adds host tests.

Read-only ADB findings:

| Check | Result |
| --- | --- |
| Connected device | A56 / SM-A566B, `R5CY40Y2C5W` |
| OS / SDK | Android 16 / API 36 |
| ABI | `arm64-v8a` |
| Page size | 4096 bytes |
| Benchmark installed | No package path returned for `com.newsmead.mgazenetbenchmark` |
| SM-G991B | Not present in the connected-device list; no properties inferred |

Thus the observed CameraX 16 KB alignment issue does not apply to the A56's
current 4 KB page configuration. Native loading remains untested. No camera,
app launch, package installation, calibration or saved participant data access
was performed during these checks.

The existing CPython 3.12 environment has NumPy but no MNN or OpenCV. Exact
Windows wheels are available for [MNN 3.6.1](https://pypi.org/project/mnn/3.6.1/)
and [OpenCV 4.11.0.86](https://pypi.org/project/opencv-python/4.11.0.86/).
`tools/gaze/mgazenet/reference-requirements-win-py312.txt` pins those plus
NumPy 1.26.4, including the Windows-wheel SHA-256 hashes from PyPI. Metadata
requirements are compatible with CPython 3.12; native imports still need testing.
No reference dependencies were installed.

The concrete next step requires lifting the user's original no-install
restriction: create a separate reference venv under the ignored benchmark
build directory; install only the hash-pinned binary wheels; generate normal
and low-precision reference outputs; then install the already built separate
benchmark/test APKs on the available A56 and run camera-free synthetic checks.
Keep the active tracker, cameras, and participant calibration untouched.
Repeat on SM-G991B once connected; an A56-only pass cannot close the two-phone
requirement. Installation authorization remains pending at this checkpoint.

## Authorized installations and A56 native evidence, 2026-09-07

The user explicitly authorized installations. A separate environment was
created at `mgazenet-benchmark/build/reference-env` using the existing CPython
3.12.14. MNN 3.6.1, OpenCV 4.11.0.86 and NumPy 1.26.4 were installed from the
hash-pinned Windows wheels; `pip check` passes. No global Python packages were
changed. The separate benchmark and instrumentation packages were installed
on A56. An instrumentation test now exports the synthetic report without
starting an Activity or opening a camera.

All **five camera-free Android tests pass**: padded RGBA conversion and four
rotations, RGB/NCHW packing with only the right-eye flip, stable 258-feature
model output and invalid input rejection, synthetic report export, and native
OpenCV frame-level SVR. Two runs are retained: the initial package and final
package after removing dependency-added INTERNET/ACCESS_NETWORK_STATE
permissions. The final package permission check shows CAMERA ungranted and
those network permissions absent. No NewsMead app package or data was changed.

The final APK is SHA-256
`3c6721de746ebbcf7109ffc127e565757208a404a95dd59b422deac5255d88a2`;
the instrumentation APK is
`67558de0665d32bf243435c67932c74b5bd14c46914e6152b1689991dfa1ad10`.
The earlier package audit JSON remains historical; the final audit is in the
evidence directory below. All six native-library hashes are unchanged.

Across all three synthetic fixtures, every input float matches the desktop
reference exactly. Largest model-feature difference is approximately
`3.91e-5`, within the predeclared absolute/relative tolerances; the 14 synthetic
SVR prediction values differ by at most `1.073e-6` in the first run. The final
package also passes all comparator checks. No tolerance was loosened.

**Precision qualification:** the Windows AMD64 reference reports `fp16:0`.
Requested normal and low precision produced identical reference outputs on
these fixtures. Thus the low-configuration comparison passes against this
desktop reference, but **Android low/FP16 execution has not been tested**.
The comparator now records this scope, host metadata, report hashes and the
normal-versus-low difference explicitly. This prevents a desktop low request
from being mistaken for verification of ARM low-precision arithmetic.

| A56 synthetic timing (100 iterations, after 20 warm-ups) | First APK run | Final APK run |
| --- | --- | --- |
| Model with I/O median / P95 | 64.38 / 69.95 ms | 64.06 / 68.36 ms |
| Synthetic preprocessing + model median / P95 | 65.25 / 70.78 ms | 65.02 / 69.19 ms |
| Synthetic SVR prediction median / P95 | 0.346 / 0.389 ms | 0.345 / 0.386 ms |
| Synthetic SVR fit | 325.83 ms | 328.12 ms |

This is roughly 15 model results/s in this test configuration before face
localization, camera conversion, production calibration/filtering and display.
The initial native build therefore has a substantial responsiveness concern.
It does not establish a hard hardware ceiling or prove all native MGazeNet
implementations too slow: precision, thread scheduling, foreground workload
and sustained thermal behavior remain untested variables. These timings use
synthetic crops and a debug instrumentation process, not a reading session.

The complete synthetic evidence is retained outside disposable build outputs:
`diagnostics-local/2026-09-07/mgazenet-native-parity/`. It contains initial/final
A56 reports and tensors, desktop references, comparison JSON, instrumentation
logs, reference console output and the final static package audit. Original
reports retain `completed_pending_external_parity`; comparisons are separate
derived records. `collect_synthetic.py` retrieves only the named synthetic
report and its hash-checked tensors from the isolated benchmark package.

The A56 has been released for disconnection. The user can connect only one
phone at a time and is switching to SM-G991B for the same camera-free checks.
Do not request participant calibration based on this numerical pass.

## Both-phone checkpoint after the USB switch, 2026-09-07

SM-G991B (`R5CR60YSJ2X`) was connected after disconnecting A56. It reports
Android 15 / API 35, ARM64 support, and 4096-byte pages. The same final benchmark
and test APKs were installed. All five camera-free instrumented tests passed;
CAMERA remains ungranted and network permissions are absent. Its synthetic
report was copied and hash-checked before releasing the phone for disconnection.

Both phones' three synthetic fixtures have exactly matching input tensors
against the desktop reference. Their model outputs are identical to each
other for these fixtures; maximum difference from the desktop reference is
`3.910064697265625e-5`. Synthetic SVR prediction difference is at most
`1.0728836059570312e-6`. Both pass the existing tolerances, including the
reference-host requested-low comparison with the FP16 qualification above.
This is bounded numerical evidence, not participant calibration validation.

| Same final APK, CPU default precision / 4 threads | A56 | SM-G991B |
| --- | --- | --- |
| Model with I/O median / P95 / max | 64.06 / 68.36 / 71.54 ms | 22.03 / 29.12 / 106.99 ms |
| Synthetic preprocess + model median / P95 / max | 65.02 / 69.19 / 72.34 ms | 23.57 / 30.69 / 108.51 ms |
| Synthetic SVR prediction median / P95 | 0.345 / 0.386 ms | 0.415 / 0.641 ms |
| Synthetic SVR fit | 328.12 ms | 323.71 ms |

The SM-G991B's faster median does not erase its long iteration or the A56's
slower throughput. Neither run includes face localization, camera acquisition,
full-frame conversion, real calibration, filters or reading display. No
end-to-end FPS, sustained thermal stability, gaze responsiveness or accuracy
gate has passed. These short debug instrumentation runs also do not establish
a causal hardware explanation for the difference.

`gaze-mgazenet-native-parity-results.json` records both devices, hashes,
comparison limits and exact statistics. Complete synthetic reports, tensor
files and test logs remain in the ignored evidence directory above. The
comparator host suite now has 13 passing tests, including a non-finite
low-reference case that cannot claim precision parity. Existing app source
and the staging area remain unchanged.

The decision at that checkpoint was to retain this isolated research path and
investigate A56 inference cost before considering active tracker integration.
Useful next controlled variables are true Android low-precision execution,
thread/scheduling behavior, and model-versus-JNI cost separation, with the
same numerical checks kept fixed. Only then can a camera-to-feature benchmark
address localization overhead and freshness. Neither a working 258-value
model nor these synthetic SVRs justify requesting participant calibration or
claiming accurate responsive gaze across both phones. That bounded inference
investigation is now complete; the accuracy-readiness note above supplies the
current continuation priority.

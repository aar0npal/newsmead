# MGazeNet inference cost investigation — 2026-09-07

Starting checkpoint: `4b714cb`, isolated benchmark and two-phone parity evidence.
The working tree and index were clean when this investigation began; the user
handles staging and commits. This work changes only the isolated research
module, its analysis tools and documents. No active tracker or participant
calibration changes. No camera tests.

## Question and fixed controls

The first native checkpoint measured approximately 64 ms median model+I/O on
A56 versus 22 ms on SM-G991B. These numbers included finite-value validation,
copies and output handling, and came from debug instrumentation without an
Activity. They did not isolate the network execution time.

`MnnEstimator.profileInference` separately times validation, JNI input copy,
synchronous native `runSession`, and output read/validation/copy. The regular
`infer` implementation remains the original path. Model bytes, CPU backend,
normal/default precision and the three synthetic fixtures remain fixed.
Each condition checks the profiled output against regular inference, then
records the output for comparison with the pinned desktop reference. The
predeclared absolute 1e-3 / relative 1e-4 tolerances remain unchanged.

Eight blocks use 20 warm-up iterations and 60 measured iterations each.
The first experiment orders thread counts **4, 1, 2, 8, 8, 2, 1, 4**. A second
experiment orders (threads, range validation): **(4,off), (4,on), (2,off),
(2,on), (2,on), (2,off), (4,on), (4,off)**. Baseline bookends and reverse order
help expose time drift; they do not replace randomized repeated-session tests.
There is no preprocessing, camera, real calibration, temporal filtering or
display in the timed region.

Reports include every timing sample, process CPU/wall totals, thermal status,
power-save state, screen-interactive state and the process's allowed CPU list
and cgroup. Process CPU totals cover the whole process, not just inference.
Profiling reports use `synthetic_profile`; they cannot be collected as camera
data. `compare_profile.py` rejects changed orders, incomplete samples, invalid
timings, incompatible runtime metadata, and any condition failing numerical
parity. No thread configuration is automatically selected or promoted.

## A56 results

All 16 blocks across both experiments pass the fixed reference comparison.
The test process reports `/foreground`, CPUs `0-6`, power saving off, screen
interactive, and thermal status 0 at all block boundaries. This is not a
top-app/Activity performance measurement, and status 0 is not a temperature
trace or proof that clock frequencies stayed constant.

First experiment, medians for first / reverse-order blocks:

| Threads | Validation | Native execution | Full profiled call |
| --- | --- | --- | --- |
| 1 | 12.42 / 12.41 ms | 43.64 / 43.76 ms | 56.25 / 56.32 ms |
| 2 | 12.42 / 12.43 ms | 24.78 / 24.69 ms | 37.34 / 37.26 ms |
| 4 | 12.41 / 12.41 ms | 50.31 / 49.96 ms | 62.89 / 62.46 ms |
| 8 | 12.41 / 12.40 ms | 51.35 / 50.92 ms | 63.88 / 63.56 ms |

Input copy medians are around 0.07–0.10 ms and output handling around 0.04 ms.
Copies therefore do not explain the original 64 ms. In this execution context,
two threads are faster than four, and the finite-value predicate is expensive.
The data does not establish which physical CPU cores execute MNN worker threads
or prove a specific cause for the scheduling difference.

## Equivalent finite-value check

`FloatInputValidation.allFinite` uses ordered comparisons against positive and
negative `Float.MAX_VALUE`. Finite values, signed zeros and subnormals pass;
NaN fails ordered comparisons and infinities exceed the limits. It avoids the
per-element helper calls in the original predicate without dropping input
validation or changing input values. Array length checks remain separate and
mandatory before JNI's unchecked copy.

Three JVM tests cover finite extremes, signed zeros/subnormals, infinities and
multiple NaN payloads at each array position, plus 20,000 fixed random float
bit patterns compared with the standard predicate. Android instrumentation
also rejects NaN, both infinities and malformed lengths before JNI.

Second A56 experiment, first / reverse-order blocks:

| Condition | Validation median | Full call median | Full call P95 |
| --- | --- | --- | --- |
| 4 threads, original check | 12.55 / 12.57 ms | 64.32 / 64.37 ms | 68.57 / 69.62 ms |
| 4 threads, range check | 0.379 / 0.379 ms | 46.40 / 45.93 ms | 49.94 / 49.61 ms |
| 2 threads, original check | 12.56 / 12.57 ms | 37.42 / 37.53 ms | 37.95 / 37.76 ms |
| 2 threads, range check | 0.378 / 0.378 ms | 25.17 / 25.12 ms | 26.12 / 25.38 ms |

The candidate cuts the measured call from approximately 64 to 25 ms while
retaining synthetic parity and malformed-input rejection. Native execution
also changes between the four-thread guard conditions despite unchanged model
code; do not assume the timing components behave independently of scheduling.
The result does not establish 40 FPS end-to-end gaze or reading accuracy.

## Reproduction and evidence

Use the existing authorized build environment and install only the separate
benchmark/test packages. Run `InferenceProfileTest` for thread/stage attribution
and `FiniteGuardProfileTest` for the guard comparison. Neither opens an Activity
or a camera. Each reports a run ID that `collect_synthetic.py` can retrieve.
Compare its JSON using `compare_profile.py` against the retained desktop
`reference-with-host-metadata/report.json`.

Complete A56 evidence is retained in
`diagnostics-local/2026-09-07/mgazenet-inference-profile/`, including both
reports, all samples, fixture predictions, test logs and comparison results.
The guard-experiment APK SHA-256 is
`7cf2d1e8413214db5ffdc99e03aa1c4f27a9a1eeba81fedcf6ea3e83f69da044`;
test APK SHA-256 is
`41a6bd685ae0a6c34c9ef742ebc9c1f9ba9c7e36506e9cafaa27d46ba7ee1e65`.

## SM-G991B comparison and foreground control

SM-G991B passed both camera-free experiments and all 16 block-level reference
comparisons. Its `/foreground` process CPU list was `0-2,4-7`. Thermal status
remained 0 at block boundaries, power saving was off and the screen interactive.
Unlike A56, two threads were slower than four.

| SM-G991B guard comparison | Validation median, first / reverse | Full call median, first / reverse |
| --- | --- | --- |
| 4 threads, original check | 1.78 / 1.79 ms | 21.61 / 21.67 ms |
| 4 threads, range check | 0.528 / 0.530 ms | 20.33 / 20.41 ms |
| 2 threads, original check | 1.79 / 1.79 ms | 36.78 / 36.53 ms |
| 2 threads, range check | 0.528 / 0.534 ms | 35.29 / 35.51 ms |

The SM-G991B thread-sweep four-thread baseline medians drifted from 14.92 to
17.81 ms; the later guard comparison was around 21.6 ms. The eight-thread
reverse block had an 83.50 ms P95, and the final original-check four-thread
guard block had a 102.53 ms maximum. All conditions and tails remain retained;
these runs cannot be reduced to one headline FPS value.

Bytecode inspection confirms the original predicate calls `Float.isInfinite`
and `Float.isNaN` per value. The A56 versus SM-G991B validation-cost difference
is observed, but its exact ART/device cause is not proven.

Because both original profiling processes lacked a visible Activity, a
`ForegroundProfileTest` repeats the same eight guard/thread blocks with a
static benchmark Activity visible and the screen kept on. It does not invoke
the camera button or request camera permission. It records process **and
calling-thread** CPU masks/cgroups; previous process masks do not establish
the affinity of each MNN worker thread.

SM-G991B foreground control: all eight blocks pass numerical parity. Process
and calling thread report `/top-app` and CPUs `0-7` throughout. Four-thread
range-check calls have medians 16.65 / 16.87 ms, P95 17.42 / 17.02 ms and maxima
17.67 / 17.93 ms. Two-thread range-check medians are 30.41 / 29.13 ms. The
original-check four-thread bookends drift from 16.78 to 18.94 ms, so the total
benefit of the smaller guard is not isolated precisely by this short sequence.
The guard itself remains faster; four threads are consistently faster than two
within this foreground experiment.

The foreground-control APK SHA-256 is
`ddc167f35b2ce57304a1df1847855098b2ded7a1fc5456ef42d720b046b35d82`;
test APK SHA-256 is
`7e3927f62af64fa8852ffe3fb801de9a5e706976ef700971c1e5b71ab67e55ee`.
SM-G991B reports, test logs and comparison results are retained beside the A56
evidence. The final A56 foreground control is complete and all eight blocks
pass the same numerical gate. Process and calling thread report `/top-app`,
CPUs `0-7`, power saving off, screen interactive and thermal status 0 at every
block boundary. Its report SHA-256 is
`a15eb090c83e2f1230d603e6198dcf15bebbe3c7dc34f13d7fe1a4bdef34486c`.

| A56 foreground condition | Full call median, first / reverse | P95, first / reverse |
| --- | --- | --- |
| 4 threads, original check | 27.89 / 27.81 ms | 28.69 / 28.69 ms |
| 4 threads, range check | 14.18 / 14.09 ms | 14.28 / 14.17 ms |
| 2 threads, original check | 36.64 / 36.26 ms | 38.30 / 38.30 ms |
| 2 threads, range check | 24.19 / 24.22 ms | 24.42 / 24.31 ms |

The foreground result reverses the A56 thread ranking: four threads beat two
on both phones in this control. The earlier no-Activity result cannot justify
a device-specific two-thread default. Foreground context is associated with
the changed performance; separate sessions and unmeasured worker scheduling
prevent attributing the whole difference to one CPU mask or mechanism.
The A56 range guard takes about 0.335 ms versus 10.5 ms for the original guard.
Its largest feature difference from desktop is 6.2943e-5, within the unchanged
tolerance. Neither numerical agreement nor these timings measure gaze error.

All six profiles, 48 condition blocks and 2,880 timed calls are retained.
`gaze-mgazenet-inference-profile-results.json` recomputes statistics from the
raw samples and records report hashes and parity results. There are three
fixture predictions per block; the timed calls reuse one synthetic fixture,
so 2,880 timings are not 2,880 independent accuracy observations.
The 21 host comparator tests pass; the benchmark JVM suite has 99 passing
tests at the guard checkpoint, and both foreground instrumentation tests pass.
The four-thread default remained unchanged at this investigation checkpoint.

## Production promotion — 2026-09-12

The equivalent range guard was promoted to the regular estimator after a live
article-pipeline investigation reproduced the A56 bottleneck. The production
path retains all length/non-finite rejection and the four-thread CPU runtime.
An exact on-device parity test also gates the new bitmap-to-OpenCV input path.
Together with bounded overlap of GPU localization and CPU MNN work, the A56
article screen sustained 24 face-present outputs/s versus the observed 8 FPS
serial baseline; see `progress-notes.md` for the complete stage averages and
the downstream UI result. This throughput result is not an accuracy claim.

This bounded performance investigation is closed. Accuracy and measurement
validity take priority, as the user clarified on 2026-09-07. True Android low
precision, sustained operation and the complete camera-to-feature path remain
unmeasured; they are not automatic next experiments. Continue from
`gaze-mgazenet-accuracy-readiness.md`. Participant calibration is not requested.

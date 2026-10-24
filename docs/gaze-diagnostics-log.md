# Current Gaze System Diagnostics Log

## Purpose

This document is the evidence log for the initial diagnostic investigation of NewsMead's current gaze system. The investigation must determine what is working, what is failing, and what remains uncertain before any improvement is proposed or implemented.

The diagnostics are exploratory. Checks will follow the evidence found in the current implementation and test results rather than a predetermined improvement roadmap.

## Current Status

- **Started:** 2026-08-20
- **Branch:** `gaze-pipeline-improvements`
- **Status:** Fresh-session check complete; typical error remains improved, but calibration/live tails are not repeatable enough for exact-line use
- **Current backend:** MediaPipe Face Landmarker with iris-relative features, participant calibration, and screen-coordinate output
- **Repository state:** Code/document checkpoints are kept separate from numeric participant artifacts; those artifacts remain local and must stay unstaged
- **Latest inquiry (2026-09-06, E-075):** Native MGazeNet feasibility, not runtime replacement yet. The tested compact and constant-shift candidates remain rejected, but the earlier inference that all practical improvements were exhausted is withdrawn. Read `gaze-mgazenet-handoff.md` before acting on historical next steps.

## Investigation Boundaries

- Do not change gaze-estimation behavior while establishing the initial diagnosis.
- Do not describe a proposed modification as an improvement without comparative evidence.
- Keep calibration, validation, and accuracy-test observations distinct.
- Distinguish tracker error from calibration, coordinate-transform, filtering, timing, scrolling, and AOI-mapping errors.
- Preserve raw measurements when evaluating any displayed or filtered output.
- Do not retain participant camera frames under the current consent conditions.
- Do not stage, commit, or push diagnostic work without explicit approval.

## Baseline Context

The manuscript reports historical MediaPipe measurements of approximately 1.68-2.42 cm overall error and 0.60-1.01 cm vertical error. These values predate the rebuilt calibration flow and are context only; they are not the authoritative baseline for the current implementation.

The relevant reading threshold is approximately one rendered line height, currently treated as about 0.6 cm under the study configuration. The current system must be measured again before determining whether this threshold is met consistently.

## Investigation Journal

Use one entry for each inspection, diagnostic run, or newly discovered line of inquiry. Do not record an interpretation as a finding until supporting evidence is available.

### 2026-08-20 — Investigation opened

**Question:** What, if anything, is currently limiting the gaze system's accuracy, stability, or downstream interpretation?

**Action:** Created this diagnostic log before modifying the gaze pipeline.

**Evidence:** No diagnostic execution has been performed yet.

**Observation:** The historical manuscript measurements cannot be treated as the current baseline because the calibration flow has since been rebuilt.

**Interpretation:** Pending inspection and measurement.

**Next inquiry:** Inspect the current gaze data path and identify observable points where evidence can be collected without changing estimator behavior.

### 2026-08-21 — Static camera-to-AOI data-path audit

**Question:** Can the current implementation produce an authoritative, reproducible diagnosis without changing gaze-estimation behavior?

**Action:** Traced the current implementation from CameraX and MediaPipe through raw-feature extraction, calibration collection, polynomial mapping, temporal filtering, optional drift correction, the nine-point accuracy screen, `GazeProvider`, article AOI mapping, and RSI/scaffolding consumers. Reviewed the existing gaze unit-test inventory and attempted to execute it.

**Evidence:** E-002 through E-009.

**Observations:**

- The main coordinate contract is internally explicit: `GazeProvider` emits full-screen pixels, calibration and accuracy targets occupy full-screen `CalibrationView` instances, and article AOIs use `getLocationOnScreen`. Static inspection did not reveal a definite double offset or view-local/full-screen mix-up. This still requires an on-device coordinate check.
- The raw source averages the two eyes into one feature pair before exposing data. Separate left-eye and right-eye behavior cannot be diagnosed from the current callback or logs.
- The timestamp emitted with a raw sample is created when the MediaPipe result callback runs, not when the camera frame was captured. Downstream code creates another wall-clock timestamp when consuming gaze. The current data cannot measure camera-to-output latency.
- Calibration JSON persists per-target counts, aggregate feature medians, dispersion, blink/no-face deltas, leave-one-out fit metrics, and held-out validation errors. It does not persist the individual raw samples described by `FixationWindowFilter`, so the filter cannot actually be replayed from the saved session log.
- Calibration validation maps an aggregated raw feature directly through `GazeMapper`. The normal live path first applies a seven-sample median and One Euro filters and may then apply a saved affine drift correction. Calibration validation is therefore useful for the base mapper but is not an end-to-end measurement of the signal consumed by the article.
- The nine-point test does measure the live provider, but it automatically loads any saved drift correction. A run is not an uncorrected baseline unless the correction is absent or explicitly reverted.
- Nine-point observations and their per-target errors are printed to Logcat and shown in the UI but are not saved as a session artifact. Only applied/reverted correction summaries are persisted. Existing output is insufficient for a durable repeated-run baseline.
- The article currently uses `ScaffoldMode.DEMO_CYCLE`. This does not affect the standalone calibration or nine-point test, but it means an article run is not presently exercising adaptive scaffold triggering.
- Pure-Kotlin unit tests exist for the mapper, calibration quality, filters, drift correction, target stabilization, reading inference, stability estimation, and scaffold controller. There are no equivalent automated tests for camera transformation, MediaPipe feature extraction, Android screen-coordinate alignment, or live article AOI alignment.

**Test execution result:** The gaze unit-test suite did not execute. The first command used JDK 21 and failed in the project's older KAPT configuration. Retrying with JDK 17 passed that point but the command-line build could not read the installed Android SDK metadata or locate Build Tools 34.0.0. These are environment/toolchain failures, not failed gaze assertions. Temporary Gradle/Android diagnostic caches created by the attempt were removed.

**Interpretation:** No specific gaze-estimation defect has yet been demonstrated. The confirmed immediate problem is diagnostic observability: the current app cannot persist a clean, condition-labelled, per-target end-to-end baseline or separate source, mapping, filtering, correction, and latency effects. A diagnostics-only instrumentation change is required before the phone run can answer the root-cause question reliably.

**Next inquiry:** Decide whether to add diagnostics-only logging that preserves current estimator behavior while recording the signals and condition metadata missing from the current baseline path.

### 2026-08-21 — Diagnostics-only instrumentation added locally

**Question:** Can the missing evidence be captured during the existing nine-point test without changing the gaze estimate delivered to the UI?

**Action:** Added passive telemetry callbacks and a numeric-only `gaze_accuracy_session_*.json` writer. The nine-point screen now asks for a run label and records data only during each target's existing SAMPLE window.

**Evidence:** E-010 through E-014.

**Recorded metadata and signals:**

- Run label, device/Android details, screen size, density, rendered line height, calibration-point count, and whether an affine drift correction was active.
- Per-attempt target, final estimate, horizontal/vertical residual, Euclidean error, output dispersion, and collector sample counts.
- Separate eye-1 and eye-2 normalized gaze features and openness, their unchanged averaged raw feature, frame dimensions/rotation, capture/submission/result timestamps, result outcome, and cumulative busy-frame drops.
- Averaged raw feature, seven-sample median output, One Euro output, base mapper output, and final corrected-or-uncorrected screen output for every emitted sample inside the measurement window.
- Run summary containing accepted-point count, median error, P95 error, median absolute vertical error, and the existing leave-one-out estimate for the optional affine correction.

**Behavioral boundary:** The values passed to `GazeProvider.onGaze` still follow the existing calculation and order. Diagnostic callbacks observe intermediate values, and JSON is flushed after a point finishes rather than during its SAMPLE window. No camera bitmap or participant image is saved.

**Verification status:** Static inspection and `git diff --check` completed. A command-line Android build remains blocked by the SDK/toolchain access issue recorded in E-009, so compilation and A56 behavior are not yet confirmed. ADB is installed, but no Android device was connected when checked.

**Interpretation:** The next phone run can be diagnostically useful once this local build compiles and is installed. It should begin with no saved drift correction so the first labelled run measures the base live pipeline.

**Next inquiry:** Compile/install the diagnostics build, run the existing full calibration, then collect a labelled nine-point base-pipeline session on the Galaxy A56.

### 2026-08-21 — Full calibration diagnostics coverage added locally

**Question:** Does the diagnostics coverage match the system's actual 16-point calibration and 5-point validation design, rather than observing only the nine-point accuracy/recalibration screen?

**Action:** Extended the existing calibration-session JSON to record every SAMPLE attempt from the practice target, all 16 fitting targets, the repeated near-center drift target, and all 5 held-out validation targets. Each attempt now includes its presentation kind and result status plus the same per-eye, openness, source-outcome, frame-orientation, timing, and busy-drop telemetry captured by the nine-point diagnostics. Validation entries now also retain mapped coordinates and horizontal/vertical residuals.

**Evidence:** E-015 and E-016.

**Behavioral boundary:** The calibration targets, timings, filtering, retry/exclusion rules, mapper fit, validation calculation, and save/quality-gate behavior are unchanged. Only passive callbacks and numeric JSON fields were added; no camera image is retained.

**Verification status:** Call-site inspection and `git diff --check` passed (apart from existing line-ending warnings). Device compilation and execution remain blocked until the Android build environment is usable and the Galaxy A56 is connected.

**Interpretation:** Diagnostic coverage now reflects the role of each screen: the 16-point sequence fits the main mapper, the repeated point measures within-session drift, the 5 held-out points test that mapper, and the separate 9-point screen measures the complete live path and optionally fits affine recalibration.

**Next inquiry:** Verify the local changes statically, then compile/install and collect the calibration and uncorrected nine-point artifacts on the Galaxy A56.

### 2026-08-23 — Initial Galaxy A56 baseline collected

**Question:** What measurable limitations or improvement opportunities appear in the current tracker under one normal-light, glasses, handheld baseline condition?

**Action:** Built and ran the diagnostics instrumentation through Android Studio on the Galaxy A56. Retrieved two calibration sessions and four nine-point accuracy sessions. The first calibration ended in `redo_all`; the second was accepted. The first accuracy run was unlabelled, followed by three labelled baseline repetitions. No affine drift correction was active in any accuracy artifact.

**Evidence:** E-017 through E-020. Numeric artifacts are retained locally under `diagnostics-local/2026-08-23/diagnostic-export/` and remain unstaged. Originals remain in app-private phone storage.

**Observations:**

- The accepted calibration reported a 311 px (2.62-line) leave-one-out median, 913 px (7.70-line) leave-one-out P95, 286 px (2.41-line) held-out validation median, and 253 px (2.13-line) within-calibration drift. The drift check was flagged high, but the calibration still received the green advisory because the gate uses only the worse of the two medians and does not incorporate P95 or the drift flag into its band.
- The unlabelled live-path run measured 201 px (1.70 lines) median error. The three labelled baseline repetitions then measured 404, 492, and 489 px (3.41, 4.15, and 4.12 lines). Their vertical medians were 205, 310, and 292 px (1.73, 2.61, and 2.46 lines). This is meaningful short-term between-run variation even though the correction state remained unchanged.
- Across the 27 labelled measurements, horizontal residuals were consistently leftward and increased with target column: median `dx` was -201 px at the left column, -346 px at center, and -483 px at the right. This is not random point jitter; it is a live horizontal offset/compression pattern relative to the accepted calibration.
- The labelled live raw horizontal feature range was approximately 0.477-0.523, narrower and left-shifted relative to the accepted calibration's approximately 0.49-0.55 feature progression across columns. The earlier unlabelled run reached approximately 0.537 and was substantially more accurate. This associates the live error increase with a changed eye/phone/head geometry or feature response rather than a newly enabled correction.
- Vertical target ordering remained visible in the average raw feature, but individual target estimates were unstable and sometimes extrapolated outside the screen (for example, one top-center estimate mapped to y=-230 px). The quadratic mapping's high LOO tail error is consistent with localized instability even when its median passes.
- Both eyes responded monotonically to horizontal target position in the labelled runs. Their normalized horizontal values had a consistent offset (roughly 0.07-0.10 median absolute separation), so the current evidence does not justify declaring either eye defective; it does show that per-eye treatment is worth evaluating instead of assuming averaging is optimal.
- No blink, no-face, or insufficient-landmark outcomes occurred inside the recorded SAMPLE windows. Median MediaPipe submit-to-result time was approximately 29-31 ms and P95 approximately 37-38 ms. Busy-frame drops occurred, but every accuracy target was accepted and retained enough samples. Tracking loss and inference execution time were not the dominant limitation in these runs.
- Diagnostic frames reveal a negotiated camera frame of 320x240 (240x320 after upright rotation), not the 480x360 analysis profile described in source comments and project documentation. Whether a higher supported capture resolution improves iris geometry without unacceptable throughput loss is now a testable opportunity.
- The `1.7` value displayed during the first accuracy run was its measured median error in line-heights, not a newly changed pass threshold. Current calibration and accuracy bands remain green at no more than 3.0 lines and amber at no more than 4.5 lines; those constants predate this diagnostics branch.

**Interpretation:** The initial evidence does not point to face detection or raw inference latency as the primary constraint. The strongest improvement opportunities are calibration-gate sensitivity to tail error/drift, robustness to small geometry changes, horizontal live-range compression/bias, localized instability in the quadratic map, confirmation of the actual CameraX resolution tradeoff, and evidence-based per-eye combination. These are diagnostic conclusions, not implemented changes.

**Next inquiry:** Reproduce the rapid post-calibration shift under one fixed-phone condition and compare the accepted calibration's target-wise feature geometry with immediate and delayed nine-point runs before selecting a modification.

### 2026-08-23 — Fixed-phone repeatability check completed

**Question:** Was the handheld baseline's rapid accuracy change primarily caused by motion of the phone, or does substantial instability remain when the phone is physically supported?

**Action:** Collected five full calibration-session artifacts while the phone was supported at approximately 50 cm, followed by three uncorrected nine-point runs immediately, after one minute, and after two minutes. Lighting and glasses status were held constant. The final accepted calibration was the mapping active for all three accuracy runs.

**Evidence:** E-021 through E-024. Artifacts are retained locally under `diagnostics-local/2026-08-23/fixed-phone/fixed-phone-export/` and remain unstaged.

**Observations:**

- Calibration repeatability remained poor with the phone fixed. Across the five sessions, leave-one-out median error ranged from 191-539 px (1.62-4.54 lines), held-out validation median ranged from 156-782 px (1.31-6.60 lines), and measured drift ranged from 12-681 px. Three sessions were redone and two were accepted.
- The final calibration had a 191 px (1.62-line) leave-one-out median and 211 px (1.78-line) validation median, but its leave-one-out P95 was 499 px (4.21 lines), one held-out validation point missed by 569 px (4.80 lines), and the repeated calibration target moved by 500 px (4.22 lines). It still received a median-based green advisory with a separate drift warning.
- Fixed-phone nine-point median error was 322, 306, and 400 px (2.72, 2.58, and 3.37 lines). This was generally better than the labelled handheld baseline's 3.41-4.15 lines, so phone stability matters, but it did not make the tracker reliably line-accurate.
- Fixed-phone nine-point P95 error remained extreme at 982-1106 px (8.28-9.33 lines). Bottom-right error was repeatably severe at 990, 846, and 1106 px across the three runs. Median-only reporting hides this regional failure.
- Horizontal median residual changed from +113 px immediately to -49 px after one minute and -188 px after two minutes. Over the same period, the observed raw horizontal range shifted left from approximately 0.492-0.532 to 0.479-0.520 even though the phone was fixed and no correction was active.
- The final calibration's repeated near-center raw feature changed from `(0.5150, 0.4030)` to `(0.5205, 0.3904)`. The vertical change of approximately 0.0126 is large relative to the roughly 0.035 total median feature separation between the top and bottom calibration rows and produced the 500 px mapped drift.
- Calibration vertical features overlap substantially between rows, especially toward the lower half of the screen. The final bottom-right calibration feature (`fy` approximately 0.38) was similar to several middle-row features, limiting how uniquely a two-value iris-in-eye feature can identify vertical screen position under changing head/eyelid geometry.
- No face, blink, or insufficient-landmark outcomes occurred during these fixed-phone SAMPLE windows. MediaPipe submit-to-result latency remained approximately 30-31 ms median and 36-38 ms P95. The new evidence therefore continues to point away from detection availability or inference execution time as the binding issue.

**Interpretation:** Supporting the phone reduces some bias but does not resolve the main instability. Phone motion was a contributor, not the sole cause. The dominant observed limitations are calibration-to-calibration variability, raw-feature sensitivity to small participant geometry changes, weak/overlapping vertical feature separation, localized polynomial failure, and a quality gate that does not reject severe tail, regional, or drift failures. These findings are sufficient to guide a proposal; additional arbitrary lighting-condition runs are not required before evaluating diagnostic-backed options.

**Next inquiry:** Compare low-risk changes to calibration acceptance, mapping robustness, pose/eye normalization, resolution negotiation, and live drift handling against these recorded failure modes before choosing what to implement.

### 2026-08-23 — Historical regression and target-wise error confirmed

**Question:** Is the present performance an unavoidable limit of the MediaPipe/iris backend, or has the NewsMead calibration/live path regressed from a previously more usable state?

**Action:** Recorded the researcher's observation that earlier runs were typically close to the intended line-level range, with approximately 1.2 line-heights or lower considered the useful target. Compared that observation with the repository's dated performance notes and Git history, then replayed the final fixed-phone calibration and all three fixed-phone accuracy streams through alternative mapping/aggregation paths without changing the app.

**Evidence:** E-025 through E-028. The replay implementation is retained locally at `diagnostics-local/analyze_mapper_replay.py` and exactly reproduced the current saved point estimates (0.00 px median replay difference).

**Observations:**

- The repository independently records pre-redesign A56 vertical medians of 0.67-1.01 cm and a sister-prototype result near 0.7 cm consistently. At the current rendered line height on the A56 (approximately 0.67 cm), the prototype figure is about one line-height and broadly corroborates the researcher's memory. The current fixed-phone vertical medians of 269-341 px are approximately 1.52-1.92 cm or 2.27-2.88 line-heights.
- The likely regression window begins with the July 24 calibration redesign and includes the new shared adaptive collector/filter, subsequent live extrapolation changes, and accuracy-test rearchitecture. This identifies a bounded comparison target; it does not establish which change caused the regression.
- Replaying the old hard-clamped quadratic mapping reduced P95 from 990 to 919 px immediately, 981 to 824 px after one minute, and 1106 to 709 px after two minutes. Median error changed from 322 to 322 px, 306 to 242 px, and 400 to 350 px respectively. Soft extrapolation therefore amplifies some out-of-range regional failures, but restoring the clamp alone does not recover line-level performance.
- Replacing the current lead-trim/MAD aggregation with a plain median over the available recorded SAMPLE data produced small and mixed changes. The new point filter is therefore not sufficient by itself to explain the regression.
- A simple affine raw-feature mapper also produced mixed results and did not restore the earlier performance. The raw feature geometry and its temporal shift remain limiting even under a less curved model.
- Individual target results confirm the researcher's concern about median-only decisions. In the fixed-phone runs, the bottom-right target missed by 990, 846, and 1106 px while the session median could still receive a favorable band. One side or region can therefore fail severely while the aggregate median hides it.

**Interpretation:** The historical evidence and offline replay support treating the current state as a regression investigation, not as proof that MediaPipe iris landmarks can never approach line-level use. Mapper extrapolation contributes to catastrophic tail errors, but calibration-feature instability/overlap remains after removing it. Target-wise vertical and regional results must be used both for acceptance and for deciding whether any correction is global or localized.

**Correction-design constraint:** A single worst target should first be re-measured to establish that the miss is repeatable. If a regional miss persists, forcing that one point to zero with an unconstrained correction could distort neighboring regions. A defensible correction needs spatial regularization, target-wise reporting, and evaluation on a separate post-correction run. The same nine points used to fit a local correction cannot also serve as evidence that it works.

**Next inquiry:** Produce a diagnostics-backed proposal that separates (1) stricter target-wise acceptance/recheck, (2) restoration/comparison of the last known better calibration and mapping behavior, and (3) robust global versus regional correction, with an independent verification requirement.

### 2026-08-23 — Reading-oriented spatial measurement revision implemented

**Question:** How should the existing 16-point calibration and nine-point live-pipeline check report quality without allowing a favorable session median to hide a severe target or regional failure?

**Action:** Revised measurement and presentation only; the raw gaze estimator, temporal pipeline, quadratic mapper, and affine correction fit were not changed. Both checks now retain signed per-target horizontal and vertical residuals, express vertical residuals in rendered article line-heights, report median/P95/maximum and counts within 0.5, 1.0, and a provisional 1.2-line reference, identify the worst target, and show regional summaries. The 16-point fit is evaluated with leave-one-out predictions and separately reports all five held-out checks and x/y drift. The nine-point check reports all nine targets and row/column medians. Result panels are scrollable so none of this evidence is hidden.

**Evidence:** E-029. The shared metrics and activity changes compile. `CalibrationQualityTest` and `ReadingSpatialMetricsTest` pass. The complete repository unit suite still contains an unrelated failure in `FirebaseTest.createAccount`.

**Interpretation:** Green now means only that every recorded target meets the provisional 1.2-line vertical spatial reference (with complete/no-exclusion and drift conditions also applied to the 16-point gate). It no longer means “good for reading” or “no correction needed.” Both screens explicitly state that direct reading validation is still required. Redo-worst selection in the 16-point flow now prioritizes the largest vertical leave-one-out residuals, consistent with line assignment as the primary use case.

**Constraint:** A correction estimated from a nine-point run remains a candidate, not proof of improvement. It must be verified on separate data before it can support a reading-performance claim, particularly when errors are regional rather than global.

### 2026-08-24 — Revised 16-point and nine-point measurements verified on device

**Question:** Do the reading-oriented result screens and JSON records correctly expose individual, tail, regional, drift, and coverage failures in a newly paired calibration and immediate live-pipeline run?

**Action:** Completed and saved one fixed-phone, normal-light, no-glasses 16-point calibration, then immediately completed an uncorrected nine-point run using that newly saved 16-point map. The first same-day attempt was retained separately but excluded as a paired comparison because its calibration activity was exited without saving and its nine-point run therefore used the previous day's map.

**Evidence:** E-030. Paired artifacts are retained locally under `diagnostics-local/2026-08-24/metrics-v2/run-2/`. The calibration outcome is `accepted`; `calibration_16point.csv` was updated at 11:51; the nine-point artifact records 16 calibration points and `drift_correction_active: false`.

**Observations:**

- The 16-point leave-one-out vertical median was 3.61 line-heights, P95/maximum was 6.65, and only 3/16 targets were within the provisional 1.2-line reference. The largest vertical miss was point 16 (row 4, column 4) at 6.65 lines.
- The five held-out checks had a 3.00-line vertical median and 8.07-line P95/maximum; only 1/5 was within 1.2 lines. The bottom-left held-out check was worst at 8.07 lines, while bottom-right was 0.86 lines, demonstrating a strongly localized rather than uniformly lower-screen error.
- Start-to-end mapped drift was approximately `dx = -59 px`, `dy = -382 px` (about -3.23 line-heights vertically), with a 387 px total, and was correctly flagged.
- The immediate nine-point live-path vertical median was 2.69 lines, P95/maximum was 8.17, and only 1/9 targets was within 1.2 lines. The worst target was top-right at 8.17 vertical lines; row medians were approximately 4.02 (top), 2.07 (middle), and 2.69 (bottom) lines, while column medians were 2.30 (left), 2.07 (center), and 4.38 (right).
- Nine-point total 2-D error was 433 px median and 985 px P95/maximum. The global affine candidate's leave-one-out 2-D median estimate was 223 px, but this is not an independent post-correction result and does not establish that the regional failures would be repaired.
- All expected target-level metric collections were present: 16 leave-one-out fit targets, five held-out targets, and nine live-pipeline targets. The provisional assessment correctly remained false and did not allow the median to hide the worst targets.

**Interpretation:** The revised validation measurement is functioning as designed. It confirms that the current problem is not a single uniform offset: large errors change location and direction across fit, held-out, and immediate live checks, alongside substantial within-session vertical drift. Applying the nine-point affine candidate is not yet justified as an improvement because it was fit and estimated on the same nine observations and has not been independently verified.

**Next inquiry:** Introduce the proposed fixed center crosshair plus contracting ring as one controlled capture-stimulus change, then repeat the same saved-calibration/immediate-nine-point pair and compare capture dispersion, drift, target-wise vertical error, and regional tails against E-030.

### 2026-08-24 — Older-adult fixation target and predictable order implemented

**Question:** Can the shared target provide a clearer exact fixation location and easier attentional guidance for older adults, while avoiding repeated animation during measurement?

**Action:** Replaced the continuously pulsing inner cue with a code-drawn hit-marker: a fixed outer ring, four crosshair arms with deliberately short inward segments, a small permanent center `+`, and a saturated bright-red disc with its own black outline. The red disc begins at the outer-arm tips and contracts to the overall size of the center `+` over 1800 ms: the 1100 ms APPEAR/HOLD/SETTLE period plus the expected 700 ms base SAMPLE window. The black reticle is drawn over the opaque red disc so it remains visible throughout. Restored the original 16-point fixed row-major traversal and made redo-worst ordering deterministic. The already-fixed nine-point row-major traversal was retained. Calibration JSON now records `order_mode: fixed_row_major`.

**Evidence:** E-031. Local implementation in `CalibrationView.kt`, `GazeCalibrationActivity.kt`, and `CalibrationSessionLog.kt`. The app compiles and the focused gaze-metrics tests pass; on-device usability and outcome comparison remain pending.

**Interpretation:** The intervention is accessibility-oriented and changes only target presentation/order, not raw gaze estimation, filtering, mapping, or validation calculations. Fixed order reduces transition uncertainty for participants but couples screen region with elapsed sequence time. The center remains stationary, but radial size change now continues through the expected base sample window; its effect on sample dispersion must be checked rather than assumed beneficial.

**Next inquiry:** Visually verify target size, contrast, contraction timing, and fixed traversal on the A56, then collect the same saved-calibration/immediate-nine-point pair used for E-030.

### 2026-08-24 — Conversation-era change and instrumentation audit

**Question:** Did gaze-estimation behavior change during the diagnostic conversation, and could the added telemetry itself be affecting accuracy or FPS?

**Action:** Compared the branch baseline `f77ce6e`, diagnostics commit `79a5ceb`, and validation/target commit `e214eb6` file-by-file. Separated numerical estimator/filter logic from runtime instrumentation and later participant-facing capture changes.

**Evidence:** E-032. Git confirms that `GazeMapper`, `FixationWindowFilter`, `DriftCorrection`, `MedianFilter`, and `OneEuroFilter` are byte-for-byte unchanged from `f77ce6e`. The MediaPipe emitted feature remains the same two-eye arithmetic average, and the live order remains median → One Euro → mapper → optional correction.

**Observations:**

- Commit `79a5ceb` did not intentionally change gaze mathematics, sampling durations, or filter thresholds, but it added per-frame source/pipeline callbacks, UI-thread queue work, in-memory detailed event buffers, and repeated synchronous pretty-JSON rewrites. Calibration artifacts grew from roughly 11 KB historically to roughly 400–580 KB in the instrumented runs.
- This overhead is a plausible runtime confound even though recorded MediaPipe submit-to-result latency remained about 30–31 ms median, all diagnostic targets retained enough samples, and no face/blink loss dominated the SAMPLE windows. The current evidence neither proves nor safely excludes a callback/queue/persistence effect on effective sample arrival or participant pacing.
- Commit `e214eb6` changed validation reporting and, after E-030 had already been recorded, changed target presentation and order at the researcher's request. It therefore cannot explain E-030's earlier severe errors, although it must remain fixed during subsequent comparisons.
- The branch baseline `f77ce6e` already included the July 24 calibration redesign. Historically better behavior may therefore predate the conversation without contradicting the unchanged estimator files observed since `f77ce6e`.

**Interpretation:** Open-ended diagnosis is complete enough to stop collecting arbitrary conditions. Before attributing the regression to the July redesign or modifying the estimator, run one bounded A/B with identical target/order/model behavior and detailed telemetry selectively ON versus OFF. Retain lightweight per-point metrics and FPS in both modes.

**Next inquiry:** Implement a selectable detailed-telemetry mode, run matched saved-calibration/immediate-nine-point pairs, and use the result as the entry gate to accuracy improvement or legacy-path comparison.

### 2026-08-24 — Detailed-telemetry control implemented

**Question:** Can detailed per-frame instrumentation be selected independently while retaining enough lightweight evidence for a valid ON/OFF comparison?

**Action:** Added an explicit run-label and detailed-telemetry checkbox to both full calibration and the nine-point check. OFF clears the MediaPipe diagnostic sink and omits `source_events`/`pipeline_samples`; ON preserves the existing callbacks and arrays. Both modes retain per-point target/estimate, counts, dispersion, spatial metrics, drift/validation summaries, and constant-memory point/run FPS summaries.

**Evidence:** E-033. The implementation compiles, all `com.newsmead.gaze.*` JVM tests pass, `assembleDebug` passes, and the debug build is installed on the connected Galaxy A56. Git diff inspection confirms no changes to mapper, fixation filter, drift correction, smoothing filters, target drawing, collector timing, or point ordering.

**Interpretation:** The software control is ready, but it is not yet empirically evaluated. No estimator or general gaze improvement may begin until the matched fixed-phone ON/OFF pairs are collected and compared.

**Next inquiry:** Run one full procedurally valid 16-point calibration plus immediate uncorrected nine-point check in each telemetry mode under unchanged fixed-phone, normal-light, no-glasses conditions.

### 2026-08-24 — Detailed-telemetry handheld control evaluated

**Question:** Does disabling detailed per-frame instrumentation materially restore throughput, sample capture, or spatial accuracy under natural handheld use?

**Action:** Evaluated two valid calibration-plus-immediate-nine-point pairs per mode on the Galaxy A56, normal light, no glasses, natural handheld posture. Classification used the embedded `telemetry_mode`, not the typed label. One attempted OFF redo (`handheld-tell-off_cal_redo-1`) actually recorded `detailed_on` and was excluded with its following OFF test as a mismatched pair; the second OFF redo was valid. No affine correction was active in any included nine-point run.

**Evidence:** E-034. Calibration FPS averaged 26.54 ON versus 26.25 OFF; nine-point FPS averaged 27.08 ON versus 26.60 OFF. Median retained samples were 12/12 for ON calibrations versus 13/13 OFF, and 12/13 for ON tests versus 12/11 OFF. Calibration feature dispersion was comparable and did not improve consistently OFF. Nine-point vertical medians were 1.09 and 0.59 lines ON versus 1.38 and 1.06 OFF; P95/max were 3.43 and 3.04 ON versus 3.08 and 2.54 OFF. ON had better typical error and threshold coverage, while OFF had somewhat smaller tails; calibration LOO, held-out validation, drift, output dispersion, and failure regions varied by run rather than mode. OFF artifacts were roughly 9–25 KB versus 251–440 KB ON, confirming that the control removed the intended detailed history.

**Interpretation:** Telemetry OFF did not restore FPS, sample arrival, or accuracy. Mixed tail and regional differences are consistent with the tracker’s existing run-to-run/handheld variation, not a mode-specific recovery. The control rules out a material detailed-instrumentation regression under these runs, though it cannot exclude a subtle effect. Close this diagnostic gate and do not spend the next iteration optimizing telemetry as an accuracy intervention.

**Next inquiry:** Compare the current July 24 calibration path against the pre-redesign behavior, one behavior change at a time, while keeping detailed telemetry OFF for lower runtime/storage overhead.

### 2026-08-24 — Pre-run countdown added before fresh-session confirmation

**Question:** Can the abrupt transition from setup confirmation to the first target be removed without changing capture behavior?

**Action:** Added a centered 3–2–1 countdown before initial full calibration, redo-worst, redo-all, and every nine-point measurement. Each numeral displays for one second; the countdown hides before the existing first target begins. No estimator, mapper, filter, target, order, SAMPLE boundary, or per-target duration changed.

**Evidence:** E-035. `compileDebugKotlin`, all `com.newsmead.gaze.*` JVM tests, and `assembleDebug` pass. The updated APK is installed on the connected A56 with existing app data preserved.

**Interpretation:** The four valid telemetry-control accuracy runs (0.59–1.38-line medians and 2.54–3.43-line tails) were substantially better than E-030 and the earlier fixed-phone set. A stable regression is no longer established; those earlier sessions may have been poor runs or may reflect the pre-E-031 target/order behavior. Before changing calibration math, obtain one independent natural-handheld OFF pair after a genuine session reset.

**Next inquiry:** After several hours or on another day, collect one telemetry-OFF full calibration plus immediate uncorrected nine-point run with the countdown build. Accept the first procedurally valid calibration as-is and do not repeat merely for a poor score.

### 2026-08-28 — Fresh-session OFF repeatability check completed

**Question:** Does the substantially improved August 24 handheld accuracy repeat after a genuine multi-day reset without selecting a favorable calibration?

**Action:** Collected one natural-handheld full calibration (`handheld_fresh_off_cal_20260828_1`) and immediate nine-point check (`handheld_fresh_off_test_20260828_1`) with detailed telemetry OFF. The first procedurally valid calibration was saved as-is, and no affine correction was active.

**Evidence:** E-036. Calibration averaged 25.81 FPS with median raw/retained counts 20/14 and low median feature dispersion (0.00102 x, 0.00184 y). Its LOO vertical median/P95/max was 1.29/6.24/6.24 lines, drift was 286 px, and held-out validation median/P95/max was 4.49/7.44/7.44 lines. The immediate live test averaged 25.61 FPS with 19/12 raw/retained samples, a 1.55-line vertical median, 4.87-line P95/max, 277 px 2-D median, and 4/9 targets within 1.2 lines. Top-left was worst at 4.87 lines; bottom-right, bottom-center, and middle-left were 2.35–2.67 lines. All nine estimates were horizontally left of target.

**Interpretation:** The independent session remains better in typical vertical error than E-030 (1.55 versus 2.69 lines), so the recent improvement was not purely a single-session accident. However, severe regional tails returned, and the calibration’s held-out validation was markedly poor despite healthy FPS, sample counts, and low within-point dispersion. The system is therefore improved but not repeatable enough for exact-line attribution. Across the five recent valid live runs, top-left is the worst vertical target in four, identifying a recurring regional weakness rather than purely random bad runs.

**Next inquiry:** Without collecting another run or changing code, compare the five recent calibration/test pairs for predictors of tail failure—feature-range geometry, target-wise calibration residuals, held-out regions, drift, and mapper conditioning—with special attention to the recurring top-left failure. Determine whether bad sessions can be detected reliably at the quality gate before choosing a single calibration-path intervention.

### 2026-08-28 — Five-pair offline tail-predictor analysis

**Question:** Can any metric available before Save reliably distinguish a calibration that will produce a severe immediate live tail?

**Action:** Read the five valid calibration/immediate-nine-point pairs directly from app-private storage and compared only saved aggregate evidence. For each calibration, reconstructed the standardized six-term quadratic design and ridge normal matrix; measured feature spread, row/column ordering and separation, ridge leverage, fitted top-left vertical gain, pointwise LOO error, held-out validation, and drift. Compared these with live median, maximum, top-left, 2-D, and signed-horizontal errors. The mismatched ON-calibration/OFF-test pair remained excluded. No raw participant artifact was copied out of app-private storage; the reusable script prints derived aggregates only.

**Evidence:** E-037. All five calibrations had perfect horizontal target ordering. Vertical feature ordering was reversed at 0–2 of 12 adjacent row comparisons, with especially weak top-row/second-row separation in OFF1 and the fresh OFF run. Unregularized design condition numbers were modest (4.23–7.91), and the ridge-normal condition numbers were 16.35–39.30; the worst fresh live run was not the most ill-conditioned and had the lowest fitted top-left vertical gain. The top-left calibration target was the maximum vertical LOO error in every pair (5.23–6.59 lines), while it was the worst live target in four of five pairs. Held-out maximum did not rank live tails: OFF2 had the largest held-out maximum (7.82 lines) but the smallest live maximum (2.54), whereas fresh OFF had 7.44 held-out and 4.87 live. High-drift calibrations (215, 241, and 286 px) had live maxima of 3.43, 3.08, and 4.87 lines; low-drift calibrations (71 and 36 px) had 3.04 and 2.54. With only five pairs, rank correlations are descriptive and not inferential.

**Interpretation:** Polynomial conditioning, LOO aggregates, and held-out errors do not provide a reliable pre-save selector for the later live tail. High drift is the only practical directional warning, but it is not a guarantee: a low-drift pair still reached a 3.04-line live maximum. The repeated top-left failure is systematic across calibration and live measurement, not a marker unique to the fresh bad-tail session. Because fixed row-major traversal makes top-left both a corner and the first measured fit target, these data cannot separate regional feature geometry from a first-target/sequence effect.

**Next inquiry:** Adopt one narrow stopping rule before changing calibration math: when the existing gate flags high drift, do not Save that calibration; use Redo All once, and if the repeat is also high-drift, abort and retain the prior calibration. Treat this as risk control, not proof of line-level accuracy. After that rule is implemented, isolate the top-left region/first-target confound in a separate controlled intervention rather than combining changes.

### 2026-08-29 — Regularized per-eye linear mapper rejected prospectively

**Question:** Does retaining four per-eye iris features in a small five-coefficient ridge-linear mapper improve accuracy over the six-coefficient quadratic mapper of the two-eye average?

**Action:** Compared five small mapper families offline on nine compatible detailed calibration sessions, then prospectively deployed the best descriptive candidate (`per_eye_linear`, ridge 1). The implementation preserved the target, sequence, capture filter, smoothing parameters, and separate affine drift layer; it added per-eye aggregation and an eight-column calibration CSV with legacy four-column fallback. Two telemetry-OFF full-calibration/immediate-nine-point pairs were collected. The active mapper was then restored to `quadratic_average` after both live runs failed.

**Evidence:** E-038. The immediately preceding quadratic live run had a 0.76-line vertical median and 1.04-line maximum. Per-eye run 1 produced 5.74/8.83 lines and per-eye run 2 produced 2.63/4.19 lines. Run 1 had 1/9 targets within 1.2 lines; run 2 had 0/9. FPS remained comparable (18.14 and 20.12). Same-calibration offline counterfactuals were mixed: per-eye improved run-1 held-out median/max (3.40/5.74 lines versus quadratic 3.78/6.85), but worsened run 2 (2.51/4.87 versus quadratic 1.63/3.21). Run-1 drift was 774 px and run-2 drift was 71 px, so the live failure was not restricted to a high-drift calibration.

**Interpretation:** Selective per-eye retention is not a deployable accuracy improvement in this form. Earlier descriptive wins across repeated sessions did not generalize to the two prospective runs, and the same-calibration comparison did not consistently favor it. Keep the quadratic mapper active. Retain aggregate-only per-eye logging and the offline comparison tool so future alternatives can be evaluated without enabling costly per-frame telemetry.

**Next inquiry:** Do not deploy another mapper from retrospective rankings alone. Any posture compensation must first establish that its posture inputs vary independently of target position; the present fixed calibration sequence cannot identify that safely. Treat pose-departure detection as reliability protection rather than an accuracy claim.

### 2026-08-29 — Quadratic rollback sanity check completed

**Question:** After rejecting the per-eye intervention, does a fresh calibration and immediate test confirm that the installed app is again executing the quadratic-average pipeline?

**Action:** Installed the tested rollback build, then collected a fresh 16-point calibration (`quadratic_restored_tel-off_cal_1`) and immediate nine-point test (`quadratic_restored_tel-off_test_1`). The embedded mapper field, rather than the typed label, was used to verify the active path.

**Evidence:** E-039. Both artifacts recorded `active_mapping_mode: quadratic_average`, 16 calibration points, no affine correction, and approximately 20–21 FPS. The calibration was detailed-OFF and had 2.50/6.33-line LOO median/max, 1.53/3.73-line held-out median/max, and 259 px drift. The live test produced a 1.35-line vertical median, 2.58-line maximum, and 3/9 targets within 1.2 lines. Its embedded telemetry mode was `detailed_on` despite the `tel-off` typed label, so it is not an OFF/OFF telemetry pair. All three top-row targets were the largest vertical errors at 2.39–2.58 lines; top-center, not top-left, was worst.

**Interpretation:** The rollback is operational and materially better than both prospective per-eye runs, although this fresh quadratic session does not reproduce the unusually strong 0.76/1.04-line baseline. The remaining run-to-run spread belongs to the base tracker/calibration problem, not a failure to restore the mapper. The telemetry mismatch does not invalidate this mapper sanity check because the active mapper is explicit and the completed E-034 control found no material telemetry effect; do not require another participant run solely to correct the label/mode mismatch.

**Next inquiry:** Close the per-eye intervention. Keep the quadratic mapper active and choose the next intervention from a mechanism that is identifiable under the calibration procedure; do not fit posture terms that are confounded with fixed target order.

### 2026-08-29 — Upper-left acquisition practice intervention installed

**Question:** Is the recurring upper-left/early-top-row weakness partly caused by the first recorded point also requiring the largest initial center-to-corner acquisition movement?

**Action:** Removed the rejected per-eye mapper, mapper factory, live per-eye filters, and associated runtime tests. Kept aggregate-only per-eye calibration persistence and the offline comparison tool. Added one unrecorded upper-left practice fixation after the existing center practice and immediately before the unchanged 16-point row-major fit sequence. If Redo Worst includes point 0, the same practice fixation precedes its recapture. No mapper, filter, capture timing, recorded target coordinate/order, camera resolution, nine-point sequence, or affine behavior changed.

**Evidence:** E-040. The calibration log now records `practice_target_count: 2` and `upper_left_practice_before_first_fit: true`, while continuing to record `quadratic_average` as the requested and active mapper. `compileDebugKotlin`, the focused gaze JVM suite, and `assembleDebug` passed; the APK was installed over the existing app with data and prior logs preserved. Git staging and commit were not performed.

**Interpretation:** This is a narrow acquisition/sequence intervention, not a new estimator. It preserves the predictable older-adult row-major traversal while preventing recorded point 0 from being the participant's first upper-left fixation. One prospective calibration/immediate-test pair is sufficient for its initial keep/revert decision; do not repeat merely to select a favorable run.

**Next inquiry:** Collect one telemetry-OFF 16-point calibration and immediate telemetry-OFF nine-point test. Compare point-0/top-row LOO and live top-row errors with E-039, while also reporting whole-session median/max so a local improvement cannot hide a global regression.

### 2026-08-29 — Upper-left acquisition practice rejected prospectively

**Question:** Did the extra unrecorded upper-left fixation improve the recurring upper-left/early-top-row error without degrading whole-session behavior?

**Action:** Collected exactly one telemetry-OFF calibration (`upperleft-practice_tel-off_cal_1`) and immediate telemetry-OFF test (`upperleft-practice_tel-off_test_1`). Verified two accepted practice targets, `quadratic_average`, 16 fit points, and no affine correction from embedded fields. After evaluation, removed the extra practice from initial calibration and point-0 recapture, rebuilt, retested, and installed the original one-practice sequence.

**Evidence:** E-041. Against E-039, live vertical median improved from 1.35 to 0.96 lines and coverage improved from 3/9 to 5/9 within 1.2 lines. However, top-left worsened from 2.52 to 2.78 lines, maximum error worsened from 2.58 to 3.53 lines, 2-D median increased from 215 to 343 px, and estimates developed a large leftward bias. Calibration point-0 LOO improved from 6.33 to 4.30 lines, but the LOO maximum worsened to 7.44 lines, held-out median/max worsened from 1.53/3.73 to 5.72/10.12 lines, and drift increased from 259 to 850 px. Top-center/top-right live errors improved, but bottom-left became worst at 3.53 lines.

**Interpretation:** The intervention failed its primary target-specific hypothesis and traded a better median for worse regional/global tails. It is rejected rather than retained based on the favorable aggregate. The upper-left failure cannot be attributed primarily to the initial center-to-corner acquisition under this one-run controlled intervention. Do not add target-order complexity on this evidence.

**Next inquiry:** Keep the original predictable calibration sequence and quadratic mapper. Do not request another repeat of this intervention. The next change should not be another retrospective mapper or target-order variant; posture-related work must be framed as reliability protection unless independent posture information can support an accuracy model.

### 2026-08-29 — Calibration-pose departure monitoring added in shadow mode

**Question:** Can the system identify obvious departure from the pose under which the active calibration was collected, without feeding posture into the gaze estimate or reading logic?

**Action:** Added passive eye-midpoint face centre, aspect-correct eye separation/scale, and eye-line roll features to raw samples and calibration aggregates. Extended the calibration CSV additively to 12 columns while retaining 4/8-column read compatibility. A participant-independent rule builds a conservative envelope from at least eight valid calibration points (observed min/max plus the larger of three MADs or a fixed minimum margin). The live provider assesses each sample but does not alter or suppress it. Accuracy logs retain lightweight per-point and whole-run summaries even with detailed telemetry OFF; telemetry ON also records per-frame fields. Article use emits only throttled status in the existing diagnostic log.

**Evidence:** E-042. Static data-flow audit confirms posture assessment is side-channel only: the existing average-eye values still enter the same median-7, One Euro, quadratic mapping, and optional affine correction in the same order, and the mapped output is always emitted. All gaze-focused JVM tests pass, the APK assembles, and the build was installed over the A56 app with data preserved. The full local suite reaches 52 tests with only the unrelated existing `FirebaseTest.createAccount` failure. A posture-aware calibration/test remains pending.

**Interpretation:** This is reliability instrumentation, not an accuracy intervention. It uses the same algorithm and margins for every participant while referencing each participant's own calibration pose. Existing calibrations remain usable but report posture as unavailable until replaced through the normal calibration flow.

**Next inquiry:** Collect one telemetry-OFF ordinary calibration and immediate telemetry-OFF nine-point test. Evaluate flag availability and false-positive behavior before allowing posture status to influence any downstream event.

### 2026-08-29 — Posture-departure shadow control evaluated

**Question:** Is the calibration-pose shadow status available during a normal OFF run, and does it identify the targets with poor coordinates well enough to justify any downstream reliability action?

**Action:** Collected `posture-shadow_tel-off_cal_1` and the immediate `posture-shadow_tel-off_test_1` with embedded `detailed_off`, `quadratic_average`, no affine correction, and `posture_affects_gaze_output: false`. Compared posture summaries target by target with the unchanged spatial metrics and with adjacent-run throughput.

**Evidence:** E-043. The calibration produced a complete 16-point posture profile; all 145 live samples were assessed and none were unavailable. The monitor marked 31/145 (21.4%) out of range, exclusively for `face_center_x` during bottom-center (15/15) and bottom-right (16/16). Exceedance was modest (maximum 0.29 profile margins beyond the bound, approximately 0.0063 normalized frame width). Those flagged targets had 1.58- and 0.66-line vertical errors, while unflagged top-center was worst at 4.20 lines. Live median/max was 2.76/4.20 lines with 2/9 within 1.2; calibration LOO median/max was 2.43/6.24, held-out median/max 4.72/8.25, and drift 121 px. Calibration/test FPS was 20.65/20.40 versus 18.62/18.07 in the immediately preceding OFF pair, so the shadow calculation did not produce an observed throughput regression.

**Interpretation:** Availability and persistence are verified, but this run provides no evidence that the current binary posture departure identifies inaccurate gaze. The temporal block at the final two targets is plausible as a small pose drift, yet it coincided with better—not worse—coordinates. Keep it as research evidence only; do not use it to exclude events or drive the adaptive interface, and do not tune a favorable threshold from this single participant/run.

**Next inquiry:** Move to one reversible feature-geometry intervention that is participant-independent: a roll-aware eye-local iris displacement normalized by eye width, retaining two-eye averaging and every downstream mapper/filter/target/sequence component. Do not combine it with posture terms.

### 2026-08-30 — Roll-aware eye-local raw-feature candidate implemented

**Question:** Can a participant-independent eye-local coordinate improve the unstable vertical feature without adding calibration coefficients or fitting the participant's posture?

**Action:** Added `eye_local_width_average_v1` as the active reversible raw-feature mode. For each eye, iris displacement is projected onto the corner-to-corner eye axis and its downward perpendicular in aspect-correct pixel coordinates, then normalized by eye width. The two eyes are still averaged before the unchanged live filters and quadratic mapper. The original `eyelid_fraction_average` path remains available as the rollback enum value. Calibration and accuracy logs now persist `raw_feature_mode`, and `calibration_feature_mode.txt` prevents candidate/rollback builds from silently consuming an incompatible saved calibration.

**Evidence:** E-044. Pure JVM tests confirm that the candidate output is invariant to translation, uniform scale, roll, and MediaPipe eye-corner index order. All gaze-focused JVM tests and `assembleDebug` pass, and the APK was installed over the A56 app with data/logs preserved. Static diff confirms no changes to `GazeMapper`, `FixationWindowFilter`, `MedianFilter`, `OneEuroFilter`, `DriftCorrection`, or `CalibrationView`; calibration target order/timing is unchanged. Physical calibration/test evidence remains pending.

**Interpretation:** Unlike the failed per-eye mapper, this is a bounded raw-geometry intervention with the same two-dimensional averaged input and the same six-coefficient quadratic mapper. It is not personalized to the researcher: eye-width normalization and eye-local rotation use the same computation for every face. Improvement is not claimed until one prospective pair succeeds.

**Next inquiry:** Collect one telemetry-OFF calibration labelled `eye-local-v1_tel-off_cal_1` and immediate telemetry-OFF test labelled `eye-local-v1_tel-off_test_1`; do not apply affine correction. Compare live median/max and regional vertical errors with the restored quadratic and posture-shadow runs, then keep or roll back the feature mode.

### 2026-09-01 — Eye-local raw-feature candidate succeeds prospectively

**Question:** Does the roll-aware eye-local feature improve vertical accuracy in a prospective run, and does the result repeat rather than depend on one favorable calibration?

**Action:** Collected two complete telemetry-OFF calibration/immediate-test pairs. The first (`eye-local-v1_tel-off_cal_1` / `eye-local-v1_tel-off_test_1`) is the pre-specified primary result; the separately calibrated `_cal_2` / `_test_2` pair is treated only as a repeatability check. All four artifacts embed `raw_feature_mode: eye_local_width_average_v1`, `quadratic_average`, and no affine correction.

**Evidence:** E-045. The primary calibration produced 0.85-line LOO median, 1.84-line LOO max, 0.82-line held-out median, and 1.31-line held-out max. Its live test produced 0.62-line vertical median and 2.07-line max, with 6/9 targets within 1.2 lines and 209/334 px 2-D median/max. The repeat calibration produced 0.73/1.76-line LOO median/max and 1.03/1.90-line held-out median/max; its live test produced 0.96/1.51-line median/max, 7/9 within 1.2, and 126/216 px 2-D median/max. FPS remained comparable at 18.68/20.50 and 19.53/19.29 for the two calibration/test pairs. The first run retained a systematic 108–226 px left bias, while the repeat's horizontal signs were mixed and smaller overall. The second run's affine leave-one-out estimate was 117 px versus its measured 126 px median, so applying it is not justified by a meaningful expected gain.

**Interpretation:** The candidate passes the prospective keep decision. Both live medians and maxima improve over the preceding posture-shadow baseline (2.76/4.20 lines) and restored quadratic sanity run (1.35/2.58), and the improvement appears in calibration LOO, held-out, and live paths. This is strong within-device/participant evidence for retaining the geometry, not proof of cross-participant performance or direct reading compatibility. Remaining horizontal error may still limit word-level use. The posture shadow again failed as a quality proxy: 88.5% of samples were flagged in the accurate primary test and none in the similarly accurate repeat.

**Next inquiry:** Stop repeating dot-grid runs for score selection. Keep the eye-local feature, leave posture shadow non-operative, and validate actual reading-line assignment plus word/line scaffold behavior. Only pursue a separate horizontal intervention if that direct validation shows the remaining horizontal error materially harms the intended reading measures.

### 2026-09-01 — Known-target reading validation exposes line and word limits

**Question:** Does the retained eye-local pipeline assign gaze to visibly instructed words and physical article lines accurately enough for reading measurement, without assuming that an adaptive event proves what the participant read?

**Action:** Rejected the initial free-reading/adaptive instrument because it lacked independent ground truth, then rejected accuracy protocol v2 because changing instructions and timing labels remained visible during active targets. Protocol v3 shows instructions only in centered dialogs between sections and hides the orange dot and control panel during measurement. Collected one completed v3 run (`accuracy_v3`, order B) after a fresh telemetry-OFF 16-point calibration, with no affine correction. The unchanged live path was `eye_local_width_average_v1 -> median/One Euro -> quadratic mapper -> line AOI -> target stabilizer`.

**Evidence:** E-046. All 14 targets completed and all 841 measured samples produced valid text targets. Word-fixation trials achieved 34.5% exact-line, 65.2% within-one-line, and 10.9% exact-word accuracy. Guided physical-line trials achieved 40.5% exact-line and 88.6% within-one-line accuracy. The combined median/P95 absolute vertical error was 1/2 lines. The raw AOI, before target stabilization, was 35.8% exact-line, 65.2% within one line, and 17.9% exact-word on word trials; stabilization therefore did not cause the vertical error and reduced exact-word matches in this run. Order B placed top-left third rather than first, yet it still had 0% exact-line samples with a +2-line median error, so first-target timing is not the sole cause. Bottom-right word fixation was worse at a -3-line median, and both bottom guided lines had 0% exact-line accuracy; top targets tended to map lower and bottom targets higher, showing a strong center-compression pattern in both trial types. The paired calibration had 0.51/1.50-line LOO median/P95, 0.88/1.22-line held-out median/P95, and 197 px flagged drift. Reading FPS was approximately 17–18 median with 15 FPS near the fifth percentile. Retained `GazeMap` entries from every measured target showed median raw-versus-filtered vertical differences of about 0.001 or less and all sampled posture assessments were `IN_RANGE`. Bottom reading targets used raw/filtered vertical features around 0.452–0.459 rather than the calibration bottom-row band around 0.470–0.474, so the live range had already compressed before target stabilization and was not created by the temporal filters. A vertical affine fitted only to the five held-out calibration targets modestly changed raw exact-line accuracy from 38.0% to 42.1% overall, but traded top against bottom regions and reduced word within-one-line accuracy. A full x/y affine counterfactual was worse overall (37.5% exact, 68.4% within one); neither correction is retained.

**Interpretation:** The retained eye-local feature remains better than the rejected baseline candidates, but direct reading compatibility is not established. Within-one-line guided accuracy may support coarse proximity research, but exact-line and word-level AOI/scaffold claims are not defensible from this run. The result also closes the top-left sequence-only hypothesis: top-left still failed when it was not first, while the broader top/bottom sign reversal points to live vertical gain/range compression rather than one isolated corner. The stabilizer is a secondary word-label issue, not the source of the vertical compression. Protocol v2 is UI evidence only and must not enter accuracy results.

**Next inquiry:** Keep adaptive validation blocked. The held-out-derived affine shortcuts are rejected, and word stabilization is secondary because six of eight targets had no raw exact-word matches to recover. The next bounded candidate should measure and correct vertical gain/bias through the live filtered pipeline in the reading context, using a very small top/middle/bottom reference step and a separate guarded vertical-only layer rather than altering the quadratic mapper. The procedure and guard limits must be identical for every participant, and the layer must pass a prospective known-target run before retention. Do not add posture coefficients or another per-eye mapper from this evidence.

### 2026-09-01 — Reading-surface vertical alignment control implemented

**Question:** Can a three-reference live reading-context layer correct the center compression found in E-046 while leaving the retained estimator, quadratic mapper, temporal filters, target sequence, and existing affine layer unchanged?

**Action:** Extended the known-target instrument to protocol/schema v4 with explicit vertical-alignment OFF/ON selection. Both modes collect the same top/middle/bottom orange bullseyes on the expanded reading surface, with identical 2-second acquisition and 2.5-second measurement windows and no concurrent instruction UI. A pure vertical least-squares layer fits `targetY = intercept + gain * observedY` from the three live filtered medians. OFF logs but never applies it. ON applies it only within the current validation activity and only after fixed guards for sample count, target span, monotonicity, gain, fit residual, leave-one-out error, and maximum correction. A rejected ON fit saves and closes as `alignment_rejected`; it cannot masquerade as an ON accuracy result. Logs retain base/effective coordinates and AOIs for direct counterfactual checks.

**Evidence:** E-047. Seven focused JVM tests cover compressed-range recovery, identity behavior, non-monotonic rejection, gain rejection, nonlinear-reference rejection, insufficient samples, and median aggregation. The focused protocol/correction suite and full debug APK assembly succeed. Physical OFF/ON evidence remains pending.

**Interpretation:** This is a reversible calibration-layer candidate, not a retained accuracy improvement. Its algorithm and guards are participant-independent, while the fitted two parameters are session-specific in the same ordinary sense as gaze calibration. It neither persists nor replaces the 16-point map or nine-point affine layer.

**Next inquiry:** With one fresh telemetry-OFF 16-point calibration and no nine-point affine correction, run `vertical_off_1` followed immediately by `vertical_on_1` without changing posture. Compare base versus effective target-wise line errors, tails, regions, FPS, and guard evidence. Retain the layer only if ON improves both typical and tail reading accuracy without creating a new regional failure.

### 2026-09-02 — First reading-surface vertical alignment pair is adverse

**Question:** Does the guarded three-reference session layer improve both typical and tail known-target reading accuracy without creating a new regional failure?

**Action:** Collected the predeclared telemetry-OFF pair after one fresh 16-point calibration and with no nine-point affine correction: `vert_off_1` (order B) followed by `vert_on_1` (order A). Both protocol/schema-v4 runs completed all 14 checkpoints. The logs preserve the uncorrected base AOI and corrected raw AOI for every ON sample, permitting a within-run counterfactual that is stronger than comparing the two runs alone.

**Evidence:** E-048. The calibration used `eye_local_width_average_v1`, had no high-drift flag (10.7 px post-fit repeat), and produced 0.92/2.41-line LOO median/max and 1.23/2.38-line held-out median/max at 19.04 FPS. OFF references had top/middle/bottom signed errors of +181/+5/-300 px; their candidate fit was correctly rejected for 270 px leave-one-out error and was not applied. ON references changed to +13/-107/-291 px; its fit passed the original guards at gain 1.350, intercept -258.6 px, and 92.6 px leave-one-out maximum, so it was applied. In the ON run, uncorrected base versus corrected raw accuracy changed from 17.7% to 20.5% exact-line and 70.5% to 47.8% within-one-line, while valid output fell from 100% to 80.2%. Word fixation improved from 13.1% to 35.7% exact-line and 7.1% to 29.7% exact-word, but guided-line exact/within-one accuracy fell from 20.6%/74.6% to 10.7%/29.2%. One corrected bottom-line checkpoint had no valid text samples; another was only 47.6% valid with a +3-line median error versus the base path's +1. The correction shifted measured y by a median +199 px and as much as +491 px. Final stabilized corrected accuracy was 21.5% exact-line, 48.4% within one line, 19.1% exact-word, and 12.7%/30.0% guided exact/within-one. OFF/ON measured FPS was comparable at 18.68/19.11 median and 15.62/15.54 fifth percentile.

**Interpretation:** Reject the global three-reference y gain/bias layer. It recovered several stationary word targets but introduced severe bottom and guided-reading failures, invalidated coordinates, and worsened the within-one-line tail on the same ON samples. The top reference also moved by 168 px and the middle by 112 px between consecutive runs, so the short reference fit is not stable enough to represent the later session. The result is not an FPS effect. Do not repeat this pair merely to seek a favorable result, and do not tune its guard thresholds on E-048.

**Next inquiry:** Restore the ordinary uncorrected reading path before another device run while retaining the v4 logs and offline analyzer as evidence. The next accuracy candidate must operate upstream or use a validation design that can detect time-varying drift; another session-global affine shortcut is not supported.

**Subsequent decision:** The initial recommendation above was too conclusive for one session. At the user's request, it was superseded by two unchanged replications, one with reversed ON/OFF order. No rollback occurred before those runs. See E-049 and E-050 for the current decision; preserve the first adverse result as part of the full evidence.

### 2026-09-03 — Two unchanged replications show a mixed, non-repeatable correction benefit

**Question:** Was the first pair simply unfavorable, or does the unchanged three-reference layer repeatedly help after fresh calibrations and a reversed mode order?

**Action:** Evaluated `vert_on_2` (01:16, order B) then `vert_off_2` (01:19, order A), followed in a later session by `vertical_off_4` (21:02, order B) then `vertical_on_4` (21:04, order A). All four logs embed protocol/schema v4, telemetry OFF, the retained eye-local feature, no nine-point correction, and completed 14-target outcomes. The `_4` suffix is retained exactly and is not an exclusion reason. The latest preceding accepted calibrations are `calibration_session_20260903_011422.json` and `calibration_session_20260903_210045.json`; another accepted calibration at 01:12 preceded the first of these and has no subsequent reading pair before it was replaced. Linkage is inferred from chronology because v4 does not embed the calibration fingerprint. New raw artifacts were read in memory from the phone, not copied into OneDrive.

**Evidence:** E-049. Both ON fits passed the original guards and were applied. On identical `vert_on_2` samples, base->corrected raw guided exact/within-one accuracy improved from 10.8%/66.5% to 32.0%/72.1%, while word exact-line fell from 28.5% to 14.8% and exact-word from 17.1% to 14.0%; total within-one accuracy changed only 71.6%->72.3%, and raw valid fraction was 99.6%. On identical `vertical_on_4` samples, guided exact/within-one fell from 45.5%/84.3% to 23.9%/74.8%; word exact-line fell from 8.6% to 3.0%, total within-one from 59.2% to 53.5%, and validity from 96.5% to 95.1%. Its top-right word checkpoint was already mostly invalid before correction and became wholly invalid after it. Both ON measured FPS medians were approximately 19.0-19.3. Summary recomputation and the applied numeric formula matched the logs. Giving each checkpoint equal weight preserved the directional findings.

**Interpretation:** Replication was informative: the second ON recording demonstrates a real guided-reading benefit, so the method is not universally ineffective. However, the current layer does not reliably help across task types and sessions, and its guard allows substantial new regional failures. A better separate ON score is not sufficient: in the last pair, the ON session's uncorrected baseline was already markedly better than the OFF session, while the correction itself harmed those same ON samples. Retain the eye-local baseline; leave this layer experimental/OFF for ordinary use.

**Next inquiry:** The user authorized an offline comparison of a simpler offset-only rule using the existing references and separate reading targets, without another phone run or app change. Evaluate all six recordings and keep failures included (E-050).

### 2026-09-03 — Fixed reference-median offset replay does not clear the regional-accuracy gate

**Question:** Does removing the vertical stretch yield a reliable correction when the shift is derived only from the three references, not from expected reading answers?

**Action:** Specified one rule, `offset = median(target_y - observed_median_y)` over the three references, gain 1, with no clipping, shrinkage, parameter search, or outcome-dependent gate. Applied it counterfactually to base coordinates in all six recordings from three sessions. Compared uncorrected base, offset-only, and the original guarded gain/bias path (with its original rejection/identity behavior). Added the read-only analyzer and pure synthetic tests; no Android source, build, install, calibration, source trace, or git staging/commit change was made.

**Evidence:** E-050. `docs/gaze-offset-replay.md` and `docs/gaze-offset-replay-2026-09-03.json` retain aggregate and checkpoint results plus source hashes. The nominal 137 px line height cannot reproduce actual Android assignments; unscored coordinate-to-AOI observations uniquely reconstruct a 133 px interior line pitch and 12 px boundary intercept, and completed bottom-clamped scroll geometry recovers the shorter final line. All 10,580 measured base/effective line assignments and valid flags over 5,290 samples reproduce exactly; 11 pure tests pass. Overall within-one-line percentages (base->offset) are 75.3->74.8, 70.5->53.7, 71.6->88.7, 83.1->68.6, 48.9->86.5, and 59.2->70.5 in chronological order. Nonetheless, top-left within-one-line goes from 100% to 0% in `vert_on_1`, `vert_off_2`, and the otherwise substantially improved `vertical_off_4`. In `vertical_on_4`, guided exact-line falls from 45.5% to 23.9% even though overall within-one-line and validity improve. In `vert_on_2`, the offset improves aggregate/P95 errors but raises the all-sample maximum center error from 3.43 to 4.58 line pitches. No offset-only checkpoint is wholly invalid. Exact-word replay is unavailable because glyph geometry was not logged; no such improvement is claimed. Continuous center-error tails include off-text samples, unlike valid-only assignment tails.

**Interpretation:** Removing stretching improves on the original failed layer in several recordings, but does not reliably improve the uncorrected baseline without regional harm. Do not implement this fixed session-global offset as an accuracy improvement. This retrospective screen does not prove that every offset method is ineffective, and it does not justify optimizing a new cap against these same answers. The actual-line-spacing reconstruction is a replay requirement, not evidence that the original Android exact-line counts were wrong. Nominal top-center labels can also be displaced by document-end scroll clamping; actual target positions are used for scoring. The evidence suggests a reference-to-reading transfer limitation, but cannot uniquely assign it to posture, time drift, gaze behavior, or mapper geometry.

**Next inquiry:** No new phone run for this candidate. Preserve the baseline and both experimental results; choose a bounded calibration-to-reading transfer investigation before the next intervention. No runtime removal or replacement was performed by this offline task.

### 2026-09-03 — Fixed vertical-only mapper screen worsens calibration tails

**Question:** Would removing horizontal-input dependence from predicted y reduce regional error, without changing the retained eye-local feature, x mapping, filters, or post-map correction?

**Action:** Compared exactly the deployed six-term y polynomial and a three-term `1 + zy + zy^2` candidate, both ridge 1 with unpenalized intercept, fold-local population standardization/std floor 1e-6, Android Float boundaries, and the current 2.5-z tanh boundary-gradient extension. Included all 11 complete accepted eye-local calibrations on the phone (September 1–3), including earlier UI-development calibrations and the accepted September 3 01:12 calibration replaced before reading. Fit only the 16 saved aggregates; score 16 leave-one-out predictions and five separate held-out targets. No ridge sweep, reading-answer fit, outcome-based exclusion, or runtime intervention. Raw device payloads stayed in memory; only derived errors and provenance were saved.

**Evidence:** E-051. Baseline predictions reproduce all 176 LOO x/y residual pairs and 55 held-out x/y prediction pairs exactly (462 scalar components; maximum delta 0 px). Candidate x is unchanged. Its LOO maximum worsens in 11/11 sessions; held-out maximum worsens in 8/11 and improves in three; no session improves both. The median session LOO maximum rises 193.0->322.3 px and held-out maximum 155.9->202.5 px. LOO medians improve in 4/11 and held-out medians in 6/11, showing why median-only selection is insufficient. In the calibration preceding `accuracy_v3`, top-left improves 177.6->4.1 px but row 3/column 1 worsens 144.9->331.9 px. In the latest calibration, held-out maximum improves while top-left worsens 45.3->234.9 px. All pointwise signed results, source hashes, and limitations are in `gaze-vertical-mapper-screen.md` and its aggregate JSON. Twelve new synthetic tests and eleven existing replay tests pass.

**Interpretation:** Keep the current mapper; do not deploy this vertical-only candidate. Horizontal terms may be compensating for cross-axis eye geometry, not merely introducing harmful coupling, but the physical cause is not established. This closes a fixed candidate across repeated sessions, not all possible mapper improvements. These are calibration-aggregate errors from one participant/device, not live reading replay, population validation, or exact-line/word accuracy. P95 nearest-rank equals maximum with 16/five points.

**Next inquiry:** Shift attention to upstream eye measurements: audit eye-local projection/normalization and available evidence for why vertical feature separation changes from calibration to reading. Do not ask for another run for the failed candidate, sweep parameters against these answers, add fixed-sequence posture coefficients, or change runtime without a specific new proposal. No Android source, build/install, calibration, git staging, or commit action occurred in this offline task; the user separately committed the preceding reading checkpoint as `ef8946f`.

### 2026-09-03 — Eye-path audit confirms missing calibration screen-origin conversion

**Question:** Do calibration and reading use different eye measurements, or does an implementation mismatch explain part of their transfer error?

**Action:** Traced shared camera/eye-local extraction, calibration aggregation, live filtering, storage, nine-point scoring, and reading AOI/overlay coordinates. Re-read all 11 eye-local calibration aggregates in memory and matched remaining gaze-only debug samples to the two latest completed reading logs. After explicit user approval, briefly opened only the idle calibration screen for read-only window/view inspection, then closed it without pressing Start. No calibration/test was collected, and no raw payload or camera image was archived.

**Evidence:** E-052. Calibration and reading use the same raw feature source. Both eyes' row-median vertical features rise top-to-bottom in all 11 calibrations; averaged row spans are 0.0526–0.0671. The latest reading pair has 59/61 matched measured debug probes across all 14 targets each, generally close raw/filtered medians but occasional differences up to 0.0055/0.0091, so sparse evidence does not rule out all filtering effects. The physical display is 1080x2340, but the calibration window frame is `[0,101][1080,2340]`, with root and CalibrationView at local `(0,0)` sized 1080x2239. Code stores local target coordinates without adding the measured view origin; reading consumes mapper output as full-screen coordinates. For the inspected layout, a center target at screen y=1220.5 is saved as y=1119.5. All 11 prior calibration logs record the same view dimensions, but omit origin metadata. The nine-point test also scores local targets, masking this mismatch when its inset matches calibration. The remaining MainActivity window has frame `[0,0][1080,2340]`. After closing calibration, the saved CSV remains float32-equal to the latest September 3 21:00 calibration's 16 FIT pairs; the feature sidecar is unchanged and no newer calibration file exists. Full evidence and API references are in `gaze-coordinate-audit.md`.

**Interpretation:** This is a real coordinate-contract defect, not evidence for a new personalized offset or an estimator replacement. On this layout it omits a 101-px translation (about 0.76 actual reading line pitches), which can affect AOI assignment but cannot alone explain opposite top/bottom signs or larger regional failures. Correct coordinates can also expose compensating estimator errors, so do not guarantee accuracy gains. Prior grid results remain local-space evidence; reading results describe the app actually tested, now with this limitation documented. Earlier raw-range interpretations (E-046) must account for physical target positions and the omitted origin before attributing differences to eye geometry. No old result is discarded or silently relabelled. The calibration-only mapper screen (E-051) remains a comparison within a common target space.

**Next inquiry:** With separate implementation approval, make stored/validated calibration targets true screen coordinates using measured view origins, update nine-point scoring/affine targets and local drawing, version coordinate compatibility, and normalize visible rectangles into screen space for AOI/overlay/reference use. Do not hard-code 101 px or silently migrate legacy files with unknown origins. Preserve eye features, mapper formula, filters, camera, physical target pattern, and timing. Verify layout conversions before another ordinary fresh calibration and existing OFF reading test. No runtime fix, build/install, staging, or commit occurred during this audit.

### 2026-09-03 — Approved screen-coordinate repair (E-053)

The user approved implementation after E-052. Calibration targets are converted from unchanged
local drawing positions using the measured view origin. Saved FIT pairs, held-out validation,
nine-point scoring and affine observations all use screen pixels. Gaze drawing subtracts its
own view origin; AOI/overlay clipping and reading references normalize root-local visible
rectangles into screen space. No 101-px constant, fitted offset, new eye feature, mapper formula,
filter, camera or calibration-sequence change is included.

Calibration coordinate metadata (`screen_px_v1`) is hash-bound to CSV contents and raw-feature
mode. Incompatible legacy files are preserved but refused, with a fresh-calibration prompt.
Drift metadata is also bound to the matching calibration. Logs retain measured origins, local
and screen targets, physical display dimensions and calibration hashes; reading log schema 5
retains protocol 4. Captures whose start/end view frames differ cannot be saved/applied.

Verification: 67 gaze JVM tests and all four Android storage/layout tests pass; debug app and
test APK assemble and are installed. The initial locked-phone layout attempt failed with
`NoActivityResumedException`; after unlocking, the full four-test rerun passed in 2.052 seconds.
The calibration frame is `(0,101,1080,2239)`, display `1080x2340`, converted center `(540,1220.5)`.
Reading's root is `(0,0)` and its idle visible viewport is `[0,259][1080,1632]`. An inset fixture
at screen `(91,272)` validates visible-rectangle conversion, line/word AOIs before/after 165 px
scrolling, and both dot views' screen-to-local drawing. The actual accuracy screen displays the
fresh-calibration warning with only Full recalibration available; it was closed without Start.
All 105 existing gaze/calibration artifacts retain their pre-install SHA256 hashes after final
installation and testing. Test saves use only temporary cache directories, never participant
files. The real calibration was not replaced, no participant run was collected, and nothing
was staged or committed. This is an implementation checkpoint, not accuracy evidence.

Next: a user-performed fresh telemetry-OFF full
calibration and existing alignment-OFF reading test (`coords_v1_tel-off_cal_1`,
`coords_v1_off_reading_1`). Keep older results; do not silently rescore them or revive the
unsupported global corrections based on this repair alone.

### 2026-09-04 — Screen-coordinate repair evaluated twice on the Galaxy A56 (E-054)

**Question:** After E-053, does a fresh screen-coordinate calibration transfer to the existing
known-target reading test, and is the result repeatable enough to support a reading-accuracy
claim?

**Action:** Collected two separate telemetry-OFF 16-point calibration plus immediate
alignment-OFF reading pairs on the Galaxy A56. Pair 1 is
`coords_v1_tel-off_cal_1` / `coords_v1_off_reading_1`; pair 2 is
`coords_v1_tel-off_cal_2` / `coords_v1_off_reading_2`. Both calibrations were accepted and both
14-checkpoint reading sessions completed. The logs embed `screen_px_v1`,
`eye_local_width_average_v1`, `quadratic_average`, no nine-point affine correction, no reading
vertical alignment, and calibration-content hashes that link each reading run to its immediately
preceding calibration. All seven archived files are retained under
`diagnostics-local/2026-09-04/coords-v1-a56/`; nothing was renamed, overwritten, staged, or
committed.

**Evidence:** Pair 1 calibration averaged 20.28 FPS, had 0.51/2.77-line LOO median/max,
2.59/5.08-line held-out median/max, and 332 px high drift. Its reading run measured 861 samples,
91.8% valid output, 9.1% exact-line and 36.5% within-one-line accuracy, 0% exact-word accuracy,
11.3%/26.9% guided exact/within-one accuracy, and 2/4-line median/P95 absolute error. Measured
reading FPS was 18.40 median and 15.45 at P05. Pair 2 calibration averaged 20.17 FPS, had
0.40/1.60-line LOO median/max, 0.55/1.28-line held-out median/max, and 45.6 px non-high drift.
Its reading run measured 875 samples with 100% valid output, 36.0% exact-line and 85.4%
within-one-line accuracy, 13.1% exact-word accuracy, 45.5%/95.0% guided exact/within-one
accuracy, and 1/2-line median/P95 absolute error. Measured reading FPS was 18.54 median and
15.44 at P05.

**Interpretation:** Pair 2 is the strongest current post-repair A56 reference, but pair 1 must
remain included. The large same-build spread means the coordinate repair is operational and can
support a strong run, but it does not by itself establish repeatable exact-line or word accuracy.
The repair must not be credited with all of pair 2's improvement, and neither pair may be
discarded during later FPS work. Together they define the preserved pre-performance-change
accuracy baseline.

**Next inquiry:** A second-device run was planned to test cross-device behavior, using a fresh
device-specific calibration and the same build. E-055 supersedes that immediate action because
the second device exposed a performance-readiness failure before an official accuracy run began.

### 2026-09-04 — Second-device accuracy run paused for FPS and thermal readiness (E-055)

**Question:** Is the current build operationally comparable on the second Android phone before
collecting cross-device accuracy evidence?

**Action:** Connected a Samsung SM-G991B by USB and verified `com.newsmead` version 0.2. The
installed base APK SHA256 exactly matched the preserved local tested APK:
`ce6b064588921049156ab4cc063c7c2bd225be11a9a3a90b979932ff234d356f`. Read-only logcat
inspection confirmed the GPU delegate and a 640x480 CameraX analysis frame rotated 270 degrees.
Before calibration, the user observed approximately 16 FPS with substantial visible lag. ADB
reported Battery Saver off, Android thermal status 3 (severe), 41.3 C skin, 45.0 C application
processor, and 39.5 C battery. No official second-phone calibration or reading accuracy run was
started or accepted for this comparison.

**Evidence:** The second phone ran the intended APK, GPU path, and 640x480 input, so the observed
lag is not explained by installing the wrong build, CPU-delegate fallback, or silent low-resolution
negotiation. Static inspection shows that the camera is requested at exactly 30 FPS while results
arrive more slowly; each accepted RGBA frame is copied and CPU-rotated/mirrored before asynchronous
GPU inference, while busy frames are dropped. The closest historical lower-resolution and current
640x480 sessions are not a controlled resolution-only comparison: apparent median/tail changes are
smaller than or entangled with known session variability and later eye-feature changes. A specific
accuracy gain from 640x480 is therefore unproven.

**Interpretation:** The hot-device observation alone cannot separate pre-existing heat from heat
caused by NewsMead, and it does not estimate cool-device steady-state FPS. It is nevertheless a
valid readiness failure: cross-device accuracy collected while the UI visibly lags and Android is
severely throttling would confound device generalization with runtime performance. Do not label the
aborted observation as accuracy evidence and do not ask the user to manage an elaborate cooling
ritual as the product solution.

**Next inquiry:** Pause new accuracy interventions and cross-device accuracy collection. Profile
and implement a bounded performance-only change first, prioritizing avoidable frame conversion,
copy, UI, and logging work. Preserve the active eye-local estimator, quadratic mapper, temporal
filters, 640x480 request during the first optimization pass, calibration target/order/timing, and
reading protocol. Verify source-output equivalence and tests before a short performance check on
both phones. Reconsider adaptive or lower analysis resolution only if optimized 640x480 remains
unsustainable; do not claim a resolution accuracy benefit without a controlled comparison.

### 2026-09-04 — Bounded 640x480 performance profile and first pass (E-056)

**Question:** Can avoidable conversion and per-frame callback work recover operational face-visible
throughput on the SM-G991B without changing resolution or gaze/calibration behavior?

**Action:** Measured a cool-start calibration-screen baseline without pressing Start. Before the
baseline Android reported thermal status 1, 38.7 C application processor, 37.1 C skin, and 36.6 C
battery. With a face visible, twelve successive MediaPipe log samples ranged from 14.0 to 15.5 FPS.
The NewsMead process used approximately one CPU core. Android frame rendering remained healthy:
13/588 frames were janky (2.21%), the UI median was 5 ms and P95 was 10 ms. During the short run the
phone rose to thermal status 2, 51.4 C application processor, 41.8 C skin, and 38.6 C battery.

A local ART trace and static call-path inspection showed CameraX converting to RGBA, followed by a
reusable 1.2 MB bitmap copy and CPU draw for rotation/mirroring before packet submission. The ART
trace's instrumented interval was only 81.6 ms, so it is supporting call-path evidence rather than a
reliable end-to-end time allocation; asynchronous native face inference is not ranked by that trace.
Three temporary no-calibration comparisons were then evaluated: dense RGBA camera-buffer submission
at 640x480, the same direct path at 320x240, and the proven bitmap/640x480 path with MediaPipe's CPU
delegate. The direct path failed source-equivalence review because the result coordinate convention
changed the derived openness geometry, so it was rejected independently of speed. The lower input
and CPU delegate were also rejected for failing to recover throughput. All temporary source changes
were removed. The only retained runtime change throttles the FPS display/lightweight FPS logger from
every result to four callbacks per second; it cannot affect raw gaze, mapping, filtering, targets,
order, timing, or the reading protocol.

**Verification:** The final restored source passes all 67 gaze-focused JVM tests and the debug APK
assembles. The final APK SHA256 is
`e8d86c7631b1e121143b40abefb901dc50a33a0a274ef57eb050d1a01fb233fa`; `adb install -r` preserved
app data, and the installed base APK hash matches exactly. At 640x480/GPU, no-face results reached
approximately 30 FPS while face-visible results remained approximately 15.3-17.1 FPS. The temporary
320x240/direct run remained approximately 14.5-18.3 FPS, and the temporary 640x480/CPU run remained
approximately 14.9-17.5 FPS. A later unmatched thermal snapshot was status 1, 45.8 C application
processor, 39.3 C skin, and 36.8 C battery. No calibration or accuracy run was started or saved.

The final restored APK was then installed on the A56 with data preserved and its installed hash also
matched exactly. Starting at thermal status 0, its 79 face-visible FPS samples had median 18.5, mean
18.73, P05 15.2, P95 24.2, and range 14.9-27.2. The process used approximately 118% of one CPU core.
After the short screen, Android still reported thermal status 0, with current 34.3 C application
processor and 32.7 C skin. The app was closed without pressing Start; no calibration or accuracy run
was created.

**Interpretation:** Avoidable app-side copy/callback work is not the main cause. The sharp difference
between no-face and face-visible throughput, plus the lack of meaningful recovery at 320x240 or on
the CPU delegate, isolates the remaining limit primarily to the 478-point face-landmark workload.
The direct-buffer approach is not safe to retain merely for its small apparent speed change because
it failed the source-output contract. Lower resolution is also not justified as a speed fix on this
phone. Cross-device accuracy therefore remains paused. Do not present the unmatched thermal readings
as a controlled heat improvement.

**Next inquiry:** The two-phone performance gate is complete. Do not spend another accuracy run on
the current backend while expecting 30 FPS, and do not repeat the rejected resolution/delegate/direct
experiments. Decide whether the product accepts an approximately 15 FPS floor (18.5 median on the
A56) for the current 478-point model. If broad-device responsiveness is required, evaluate a smaller
eye/face landmark backend in a shadow benchmark first, keeping the current pipeline as the reference
and making no mapper/filter/target/timing change until output compatibility and accuracy are measured.

### 2026-09-05 — Legacy iris-model feasibility and bounded shadow comparison (E-057)

**Question:** Is MediaPipe's smaller 64x64 eye-landmark model computationally feasible on the A56,
and can its iris centre reproduce the active eye-local raw feature closely enough to justify a
reversible hybrid prototype without changing the current gaze output?

**Action:** Used the official legacy MediaPipe iris landmark model only as a temporary benchmark
asset. The model was run twice per observation (one crop per eye) through the Play Services LiteRT
CPU runtime. An isolated benchmark first measured pre-prepared 64x64 inputs, then a reusable
640x480-to-eye-crop path including rotation, crop, pixel extraction, float conversion, and both-eye
inference. A temporary in-app shadow then used the current Face Landmarker eye corners to construct
MediaPipe-style rotated square eye ROIs, ran the small model on the same accepted frames, and compared
its two-eye eye-local feature with the current 478-point result. The shadow never emitted gaze,
altered calibration, mapping, filters, targets, sequence, timing, or persisted participant data.

**Evidence:** On the cool A56, two-eye inference over prepared inputs averaged 4.144 ms (3.954 ms
median, 5.636 ms P95). Two repetitions of the reusable synthetic end-to-end crop plus inference path
averaged 5.851 and 6.123 ms, with 6.696 and 7.285 ms P95; preprocessing averaged approximately
1.27 ms. The Play Services GPU delegate was unavailable in this configuration, but CPU execution was
well below a 33.3 ms frame budget. The real face-visible shadow completed 120 post-warm-up frames:
added comparison latency averaged 10.908 ms (14.018 ms P95); median absolute raw-feature differences
were 0.0133 horizontal and 0.0137 vertical, with P95 differences 0.0656 and 0.0507. The user held a
mostly central fixation, so the observed horizontal/vertical correlations of 0.615/0.519 do not have
enough feature-range coverage to be treated as a full-screen compatibility or accuracy score. Android
thermal status remained 0 during the isolated benchmark. No calibration or accuracy pass was started.

**Interpretation:** The candidate passes the compute-feasibility and central-feature sanity gates,
but not the replacement gate. Its real shadow cost is small enough to warrant a reversible hybrid
prototype, while the stationary-centre comparison cannot establish full-range feature equivalence,
calibration compatibility, screen accuracy, or cross-device generalization. The legacy iris pipeline
also depends on face/eye ROI acquisition; the benchmark supplied those corners from the expensive
reference model and therefore did not yet remove the 478-point bottleneck. The official legacy
solution is maintained as-is, so it is evidence/prototyping material rather than an automatic
production dependency choice.

**Cleanup and next inquiry:** Removed the temporary iris source, runtime dependency, Windows/device
model copies, and Android benchmark package. The existing calibration and accuracy files remained in
app-private storage. A clean rebuild passed all 67 gaze-focused JVM tests; the final APK SHA256 was
`2a5230601ee8b4bc228ff03631338c8bb567a8a220cfbea4eda922d88a411989`, and the installed A56 APK
matched it exactly. The active calibration SHA256 remained
`713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343` before and after installation.
The clean rebuild's whole-file hash differs from E-056 because generated build metadata was recreated;
source and dependency checks, rather than the historical APK hash, verify removal of the temporary
runtime. Rebuild and installation verification are also recorded in the handoff verification state.
Next, design a reversible hybrid that uses a lightweight face/eye detector for ROI acquisition, runs
the 64x64 eye model per camera frame, and uses the 478-point reference only for bootstrap/periodic
shadow comparison. It must remain non-authoritative until moving full-screen targets demonstrate
compatible raw features and held-out calibration/live accuracy, and until both phones show a material
throughput improvement.

### 2026-09-05 — Independent-ROI hybrid prototype and A56 full-screen shadow (E-058)

**Question:** Can a lightweight face detector acquire both eye ROIs without the 478-point model,
run the 64x64 iris model at camera rate, and preserve the reference feature's full-screen ordering?

**Action:** Added a development-only shadow using the official short-range BlazeFace detector for
face/eye locations and the official 64x64 iris landmark model for each eye. Both run on a single
lower-priority CPU worker with a bundled TensorFlow Lite runtime. The current 478-point GPU Face
Landmarker still runs every accepted frame and remains the only source delivered to the mapper,
filters, calibration collector, visible dot, and accuracy calculation. The nine-point logger now
associates same-frame shadow records with the existing target SAMPLE windows even when detailed
telemetry is OFF. No camera images are retained.

**Evidence:** The face-visible A56 preview initialized both models and, after warm-up, usually
completed the independent candidate at approximately 28-31 frames/s with zero busy drops and zero
invalid results. The target-linked telemetry-OFF run `hybrid_shadow_fullscreen_a56_1` completed with
198 shadow samples. Its active-pipeline FPS summary was 29.52 mean (25.27 minimum, 30.55 maximum),
while candidate work averaged 17.800 ms with 22.396 ms P95. Across same-frame samples, absolute
candidate/reference differences were 0.0210/0.0403 horizontal median/P95 and 0.0383/0.0611 vertical;
pooled correlations were 0.8014 horizontal and 0.8339 vertical. Correlation across the nine target
medians was 0.8811 horizontal and 0.9158 vertical. Candidate medians preserved ordered left/centre/
right and top/middle/bottom movement, but used a visibly compressed and shifted raw-feature range.
All 71 gaze-focused JVM tests pass and the debug APK assembles. The calibration SHA256 remained
`713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343` before and after installation
and the run; the app was left closed.

**Interpretation and boundary:** Independent ROI acquisition and A56 full-screen raw-feature
compatibility are now supported, but direct substitution into the existing reference calibration is
not: the candidate's range differs. The user correctly noted that the saved calibration was about one
day old. Therefore this run's screen-coordinate accuracy is excluded from candidate evaluation; it
is not a prospective calibration/accuracy result. The raw same-frame comparison remains valid because
both extractors observed the same frames and were grouped by known targets. With explicit user
authorization, the numeric-only JSON was copied to
`diagnostics-local/2026-09-05/hybrid-shadow/gaze_accuracy_session_20260905_175438_286.json`; its local
SHA256 matches the app-private original at
`cb579bc0c50cd410e9bd9ad8f692698a790e0429b8ef23958e7b786681378664`.

**Next inquiry:** Keep the candidate non-authoritative. Repeat only the face-visible performance
preview on the SM-G991B to establish cross-device candidate throughput; no calibration is needed for
that check. Then design and validate a conservative candidate-to-reference feature alignment or
candidate-specific calibration path before routing any candidate output. Only after that offline/
shadow gate passes should a fresh telemetry-OFF 16-point calibration and immediate nine-point test be
used as the prospective accuracy gate.

### 2026-09-05 — SM-G991B candidate throughput and periodic face ROI (E-059)

**Question:** Does the independent candidate materially improve face-visible throughput on the
slower SM-G991B, and can redundant face detection be reduced without changing the authoritative
gaze pipeline?

**Action:** First ran the candidate-only 640x480 performance preview with BlazeFace on every accepted
frame. The preview emits no gaze and does not read or write calibration. Then changed only the shadow
backend so BlazeFace refreshes the face/eye ROI every fourth accepted candidate frame and the most
recent ROI is reused between refreshes. The two 64x64 iris passes still run on every accepted
candidate frame. The 478-point estimator, mapper, filters, target, sequence, timing, posture monitor,
and affine layer were not changed, and the candidate remains non-authoritative.

**Evidence:** Before periodic ROI reuse, the steady face-visible interval completed 885 candidate
frames in 51.038 seconds, or approximately 17.34 FPS. It busy-dropped 644 of 1,529 candidate frame
opportunities (42.1%). After the change, the stable interval from completed sample 30 to sample 930
completed 900 frames in 34.656 seconds, or approximately 25.97 FPS. It busy-dropped 138 of 1,038
opportunities (13.3%). The user's visible range was 25-31 FPS. Seven consecutive 120-frame backend
summaries reported 25% detector-run fractions, zero invalid results, and candidate work means from
11.36 to 19.55 ms after the transition. Initial no-face/warm-up time and a later face-loss interruption
are excluded from the steady calculation. The saved SM-G991B calibration SHA256 remained
`77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`, and the app was closed.

**Interpretation and boundary:** Periodic ROI reuse materially improves isolated candidate
throughput on the SM-G991B: sustained completion increased about 50% relative to the every-frame
detector candidate and now exceeds the approximately 15-17 FPS reference preview. This closes the
bounded cross-device compute question, not the gaze-accuracy question. Reusing an ROI changes its
freshness during eye/head movement, and the A56 target-linked result in E-058 used every-frame face
detection. Therefore E-058 cannot validate the optimized cadence, and this preview contains no
coordinate-accuracy evidence.

**Next inquiry:** Keep the reference authoritative. On the currently connected SM-G991B, run one
telemetry-OFF target-linked nine-point shadow session with the periodic-ROI build. The saved
calibration may remain in place because only same-frame candidate/reference raw-feature compatibility
is being evaluated; exclude its screen-coordinate score. If target ordering and regional agreement
remain acceptable, repeat the same compatibility check on the A56 before designing feature alignment
or a candidate-specific calibration path.

### 2026-09-05 — SM-G991B periodic coarse-ROI compatibility replication (E-060)

**Question:** Does the faster every-fourth-frame BlazeFace cadence preserve the reference raw
feature's moving-target pattern on the SM-G991B?

**Action:** Ran two unchanged telemetry-OFF nine-point shadow sessions,
`hybrid_periodic_fullscreen_g991b_1` and `hybrid_periodic_fullscreen_g991b_2`. The reference remained
authoritative, the saved calibration was not replaced, and its displayed screen-coordinate scores
are excluded. Both sessions compare candidate and reference features derived from the same submitted
frames inside the existing target SAMPLE windows.

**Evidence:** Run 1 saved 147 shadow samples and produced target-median candidate/reference
correlations of 0.388 horizontal and 0.800 vertical. Run 2 saved 142 and produced 0.190 horizontal
and 0.777 vertical. Across the two sessions, the reference target pattern repeated at 0.908/0.854
horizontal/vertical correlation, while the candidate repeated at only 0.251/0.600. Run 1's freshly
detected and cached subsets had horizontal correlations of 0.350 and 0.489 respectively; freshly
detected samples were therefore not better than cached samples inside that run. The session SHA256
values are `eb1535d532e98db4a046a254f5b187fd72b30c5c74ae3fa605c3ad62837cbc24` and
`d53b913339bc2becb21b2223ca5280218a69ea3276ddfeebc2b3a8279fa5f72c`.

**Interpretation and boundary:** The periodic coarse detector ROI fails the replicated SM-G991B
moving-target feature gate. The four-frame reuse is not isolated as the cause: coarse BlazeFace eye
geometry is also used on refresh frames, and those samples were no better. Do not route this version
to the mapper or collect a candidate calibration.

**Next inquiry:** Retain the successful periodic compute cadence but replace the repeatedly coarse
eye crop with an eye-corner-refined ROI derived from the iris model output, following the official
iris ROI geometry. Keep this change inside the non-authoritative shadow and repeat the same gate.

### 2026-09-05 — Eye-corner-refined ROI and replicated SM-G991B gate (E-061)

**Question:** Can a tight, model-derived eye ROI restore feature ordering without losing the
candidate's throughput advantage?

**Action:** Added a bounded shadow-only feedback crop. Each 64x64 iris result projects its two eye
corners back into the source frame and refines the next crop; BlazeFace remains the every-fourth-frame
safety refresh. The crop uses the official MediaPipe iris graph's two-corner, square, 2.3x ROI rule
([source](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/modules/iris_landmark/iris_landmark_landmarks_to_roi.pbtxt)),
with conservative centre/scale/rotation blending to prevent a single outlier from running away. This
does not change the active 478-point estimator, mapper, filters, target, sequence, timing, posture
monitor, affine layer, or gaze output.

**Evidence:** All 74 gaze-focused JVM tests pass and the debug APK assembles with SHA256
`470f416ee85a3ea38ef94dc59e8e16f850ce150515c726a0991009e6c5743139`; the five protected active
pipeline files have no diff. Two telemetry-OFF target-linked sessions saved 136 and 148 shadow
samples. Run 1 (`hybrid_refined_roi_g991b_1`) produced target-median H/V correlations of 0.904/0.895.
Run 2 was accidentally labelled `hybrid_refined_roi_g911b_2`; its embedded mode/outcome are correct,
so the label is retained and the run is included. It produced 0.778/0.967. Candidate target patterns
repeated across the two runs at 0.808 horizontal and 0.932 vertical, and every row/column preserved
the correct overall direction. The visible/recorded active FPS was 15.19 and 14.47 respectively.
Those screens ran the authoritative 478-point reference and the candidate shadow together; because
candidate submission follows an accepted reference result, these values measure the dual shadow
configuration rather than candidate-only throughput. Their SHA256 values are
`52a1fe929eefd569c87fa7cbc9a81a9b6a51f3abb1bfd470a14e96f41e5eb574` and
`77cc9ffef7b2ffa2bd0435b295d8a76f37e1be121373d2ad755a71368270149c`.

The subsequent candidate-only preview completed 1,080 face-visible frames from sample 30 to 1,110
in 47.195 seconds, or approximately 22.88 FPS, with 334 busy drops among 1,414 opportunities (23.6%).
The user observed 21-27 FPS. The phone was moderately warm after the repeated tests: Android thermal
status 2, battery 37.4 C, and skin 39.3 C. This is below the earlier cooler 25.97 FPS result but still
materially above the approximately 15-17 FPS reference. The calibration SHA256 remained
`77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`, all four session hashes
remained stable across installation, and the app was closed.

**Interpretation and boundary:** The refined candidate passes the replicated SM-G991B raw-feature
and warm-device throughput gates. This is still shadow evidence, not candidate screen-coordinate
accuracy: the old reference calibration's displayed scores are excluded, the candidate has a
different feature range, and it has never controlled gaze output. The 14.47-15.19 FPS target-linked
runs also do not demonstrate a production speed improvement: only the isolated preview establishes
the candidate's 22.88 FPS potential. End-to-end throughput must be measured again if candidate output
is ever routed experimentally.

**Next inquiry:** Install the same build on the A56 with app data preserved and repeat the
telemetry-OFF target-linked nine-point raw-feature check. If one run passes, replicate it once, then
confirm candidate-only A56 throughput. Only after both phones pass should feature alignment or a
candidate-specific calibration path be designed.

### 2026-09-06 — Recursive eye-corner ROI A56 replication (E-062)

**Question:** Does the recursively refined eye-corner ROI that passed on the SM-G991B generalize to
the A56?

**Action:** Installed the exact E-061 APK with A56 data preserved and ran two unchanged
telemetry-OFF target-linked sessions: `hybrid_refined_roi_a56_1` and
`hybrid_refined_roi_a56_2`. The reference remained authoritative; no calibration or correction was
created, and the saved reference calibration's displayed coordinate scores are excluded.

**Evidence:** Run 1 saved 174 shadow samples at 26.48 active FPS and produced target-median H/V
candidate/reference correlations of 0.090/0.634. Run 2 saved 194 at 27.96 active FPS and produced
0.653/0.922. Across the two runs, the reference target pattern repeated at 0.991/0.991 H/V while the
candidate repeated at only 0.703/0.543. In run 1, freshly detected versus recursively cached target
agreement was 0.735/0.150 versus -0.098/0.940; in run 2 it was 0.896/0.734 versus 0.473/0.828. Thus
cached horizontal agreement was weak in both repetitions even though vertical behavior differed.
The session SHA256 values are `53cd6d2d634515f214492ba0cfc8b594fbcadce914e21ecc3c4284de8bf1ab07`
and `8b5b72d251d26cfda973d0b81618a2817ec2f3e12e46d728a49ce6234d920f5b`. The calibration SHA256
remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`.

**Interpretation and boundary:** Recursive centre/scale/rotation feedback is not cross-device
reliable and is rejected despite its replicated SM-G991B result. This does not reject the 64x64 iris
candidate or periodic detector cadence. It specifically rejects allowing the candidate's own corner
output to repeatedly translate and rotate its future crop.

**Next inquiry:** Test one detector-anchored, non-recursive refinement: BlazeFace continues to set
eye centre and roll every fourth accepted frame, while the current detector frame's iris-corner
distance tightens only the size of the next three cached crops. Do not update that cached crop again
from its own outputs. Keep the candidate shadow-only and repeat the A56 target gate before returning
to the SM-G991B.

### 2026-09-06 — Detector-anchored size-only ROI A56 replication (E-063)

**Question:** Does removing recursive centre/rotation feedback while retaining a one-step
iris-corner size refinement produce a repeatable A56 candidate feature?

**Action:** Ran two unchanged telemetry-OFF target-linked sessions,
`hybrid_anchored_roi_a56_1` and `hybrid_anchored_roi_a56_2`, using the detector-anchored
size-only APK from E-062. BlazeFace supplied eye centre and roll every fourth accepted frame; only
the detected frame's iris-corner distance set the cached crop size. The reference remained
authoritative, no calibration or correction was created, and the displayed coordinate scores from
the old reference calibration are excluded. With user authorization, both numeric artifacts were
copied to `diagnostics-local/2026-09-06/hybrid-shadow/`.

**Evidence:** Run 1 saved 178 shadow samples at 26.38 active FPS and produced target-median H/V
candidate/reference correlations of 0.635/0.479. Run 2 saved 179 at 27.36 active FPS and produced
-0.049/0.396. The reference target pattern repeated across the two runs at 0.860/0.996 H/V, while
the candidate repeated at -0.240/0.229. Fresh detector-frame target agreement was 0.529/0.692 then
-0.064/0.517; cached size-refined agreement was 0.699/0.402 then -0.055/0.294. Candidate work
averaged 13.95 and 13.75 ms, with detector-frame shares of 24.7% and 25.1%. Session SHA256 values
are `49887c7ad453a5687581534bea25e61c93814c023d1c3de84269ab6170db238c` and
`7b43cf52673f29372eb63a883e6a134f2d0f45bc61f9e896b9691b2989e6800c`. The calibration SHA256
remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343` in both runs.

**Interpretation and boundary:** The size-only anchored crop is rejected after two A56 failures.
Neither the detector frames nor cached frames preserve reliable two-axis target geometry, so the
failure cannot be attributed only to recursive crop walking. This still does not reject the 64x64
iris model itself: E-058 showed that the A56 every-frame coarse path can preserve target order, and
E-061 showed that eye-corner centring can recover the SM path. It rejects this particular attempt to
combine coarse detector centres with periodic size-only reuse. No production gaze component changed.

**Next inquiry:** Before another physical run, implement one bounded shadow candidate that combines
the two useful observations without recursion: on each BlazeFace refresh, obtain iris corners from
the detector crop, derive one corrected centre and official 2.3x corner-distance size from that
detector-anchored crop, re-extract once, and cache that corrected crop until the next detector
refresh. Never update centre, size, or roll from cached-frame outputs. Log numeric crop correction
magnitudes so device-specific instability can be diagnosed without retaining images. Run the same
two-run A56 gate before returning to the SM-G991B; do not alter the authoritative source, mapper,
filters, targets, sequence, timing, posture monitor, or affine layer.

### 2026-09-06 — Detector-anchored one-step re-extraction implementation (E-064)

**Question:** Can eye-corner centring be recovered without the recursive crop walk rejected in
E-062 or the coarse-centre limitation exposed in E-063?

**Action:** Replaced the size-only shadow refinement with a detector-anchored one-step re-extraction.
On each fourth-frame BlazeFace refresh, the first two iris passes project each decoded corner
midpoint back through the detector crop's scale and roll, derive the official 2.3x corner-distance
square, and bound implausible centre/size changes. The candidate immediately re-extracts both eyes
once from those corrected crops and caches them for the next three frames. Cached-frame output can
never update centre, size, or roll. Numeric per-eye centre shift, side ratio, re-extraction status,
and re-extraction time are persisted; no camera image is retained.

**Evidence:** All 75 gaze-focused JVM tests pass, including direct checks of centre projection
through detector roll, official size, size bounding, and severe-centre rejection. The debug APK
assembles with SHA256 `ea5475d5705f0a0eb260191c8f8dea62d66e03716f6f74ee8d6d276caf6fa214`.
It was installed on the A56 with `-r`, and the installed base APK has the same hash. Before and after
installation, the calibration and two E-063 session hashes remained respectively
`713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`,
`49887c7ad453a5687581534bea25e61c93814c023d1c3de84269ab6170db238c`, and
`7b43cf52673f29372eb63a883e6a134f2d0f45bc61f9e896b9691b2989e6800c`. The five protected
authoritative mapper/provider/filter files have no diff.

**Interpretation and boundary:** This is a genuine acquisition improvement candidate, not a
production gaze change. It addresses the specific cross-device ROI failure while preserving the
successful periodic compute design, but it has no accuracy evidence yet. The 478-point reference
still exclusively controls calibration, mapping, filtering, visible gaze, and accuracy scores.

**Next inquiry:** On the A56, run two unchanged telemetry-OFF target-linked nine-point shadows,
starting with `hybrid_reextract_roi_a56_1`. Do not recalibrate, apply correction, or interpret the
displayed old-calibration coordinate score. Evaluate same-frame target agreement, repeatability,
re-extraction success/correction magnitudes, and active throughput before any SM-G991B run.

### 2026-09-06 — One-step re-extraction replicated A56 gate (E-065)

**Question:** Does the detector-anchored one-step centre/size re-extraction preserve repeatable
full-screen target geometry on the A56 without sacrificing throughput?

**Action:** Ran two unchanged telemetry-OFF target-linked sessions,
`hybrid_reextract_roi_a56_1` and `hybrid_reextract_roi_a56_2`. The E-064 version and crop mode are
embedded in both artifacts. The reference remained authoritative, no calibration or correction was
created, and the displayed old-calibration coordinate scores are excluded. Both numeric artifacts
were copied with authorization to `diagnostics-local/2026-09-06/hybrid-shadow/`.

**Evidence:** Run 1 saved 173 shadow samples at 28.76 active FPS. Candidate/reference target-median
H/V correlations were 0.863/0.922, while candidate/intended-target correlations were 0.876/0.965.
Run 2 saved 179 samples at 28.38 active FPS. Candidate/reference correlations fell to 0.535/0.499,
but candidate/intended-target correlations remained 0.825/0.902. In that second run the reference's
own intended-target H/V correlations were only 0.830/0.684, so the weak vertical candidate/reference
comparison does not by itself identify a candidate ordering failure. Across runs, reference target
patterns repeated at 0.948/0.770 H/V and candidate patterns at 0.829/0.903. Candidate column and row
medians remained monotonic in both runs.

Re-extraction succeeded on all 42/42 and 46/46 detector frames. Detector-frame target agreement was
0.896/0.897 then 0.337/0.593; cached-frame agreement was 0.854/0.933 then 0.669/0.497. Right/left
median centre shifts were 6.52/3.89 px then 6.51/2.23 px, and median side ratios were 0.843/0.828
then 0.861/0.814. Median re-extraction cost was 9.33 and 10.00 ms on detector frames. Total candidate
work averaged 15.29 and 15.80 ms, with approximately 31.95 and 31.71 ms P95. Session SHA256 values
are `6e37125cef9a43f68a1bc0cfc054749b3044d9ddc6baa484899b91fddc4d9d05` and
`297a3cc849c934e3af6dd398aa3c3f5855f3ed8c21c94a9f9d88d20cd88ccc9a`. The calibration SHA256
remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`.

**Interpretation and boundary:** The candidate passes the bounded A56 ordering/throughput gate for
cross-device evaluation: it preserves the intended two-axis target order twice, its own vertical
pattern is more repeatable than the reference in this pair, all re-extractions succeed, and active
throughput stays near camera rate. This is not a production accuracy pass. The weaker second-run
candidate/reference correlations and detector/cached subset variability remain material, the raw
range differs, and no candidate calibration or screen-coordinate mapping exists.

**Next inquiry:** Install the exact E-064 APK on the SM-G991B with app data preserved and run two
unchanged telemetry-OFF target-linked sessions labelled `hybrid_reextract_roi_g991b_1` and
`hybrid_reextract_roi_g991b_2`. Exclude the displayed old-calibration coordinate score. Require
intended-target ordering, inter-run repeatability, successful re-extraction, and materially better
throughput than the 15-17 FPS reference before designing candidate alignment or calibration.

### 2026-09-06 — One-step re-extraction replicated SM-G991B gate (E-066)

**Question:** Does the exact one-step re-extraction build that passed the bounded A56 gate preserve
two-axis target geometry on the SM-G991B?

**Action:** Verified the SM-G991B calibration and all four prior E-060/E-061 artifact hashes,
installed the exact E-064 APK with data preserved, and verified those hashes again. Ran two unchanged
telemetry-OFF target-linked sessions, `hybrid_reextract_roi_g991b_1` and
`hybrid_reextract_roi_g991b_2`. The reference remained authoritative; no calibration or correction
was created, and displayed old-calibration coordinate scores are excluded. Both numeric artifacts
were copied with authorization to `diagnostics-local/2026-09-06/hybrid-shadow/`.

**Evidence:** Run 1 saved 146 samples at 15.50 dual-pipeline FPS. Candidate/intended-target H/V
correlations were 0.154/0.939 and candidate/reference correlations were -0.005/0.930. Run 2 saved
146 samples at 14.88 dual-pipeline FPS. Candidate/intended-target H/V correlations were 0.733/0.980
and candidate/reference correlations were 0.508/0.656. Candidate target patterns repeated across
runs at only 0.513 horizontal but 0.905 vertical; reference repeatability was 0.817/0.594. Candidate
horizontal column medians were effectively flat in run 1 (`0.4875, 0.4891, 0.4856`) and became
ordered only in run 2 (`0.4878, 0.4879, 0.5118`). Candidate vertical row medians were monotonic in
both runs.

Re-extraction succeeded on all 36/36 and 34/34 detector frames. Right/left median centre shifts were
6.79/2.74 px then 5.93/2.62 px, and median side ratios were 0.861/0.812 then 0.848/0.808. Median
re-extraction cost rose from 13.55 to 16.44 ms; total candidate work averaged 20.52/28.87 ms with
43.22/57.13 ms P95. The reported 15.50/14.88 FPS is the authoritative 478-point path and candidate
shadow running together, not candidate-only throughput; a separate speed screen is unnecessary after
the feature gate failure. Session SHA256 values are
`9434d18ebb35ccd286964ffcfe1ba851f399abca4065475d9a5b9e7d7b752bac` and
`f6df3939ab9df4cc83364ebb7571c96ede73995d30684ad71423fee7dcf6aef3`. The calibration SHA256
remained `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`.

**Interpretation and boundary:** The one-step re-extraction candidate fails the replicated
cross-device horizontal gate and is rejected. The result is not explained by re-extraction failure,
recursive crop walking, or vertical feature loss: every re-extraction succeeded and vertical target
order was excellent. Coarse BlazeFace eye anchoring plus this 64x64 iris path does not provide
reliable horizontal geometry across both phones under the tested architecture. Do not tune this
candidate against the SM recordings, create a candidate calibration, route it to gaze output, or run
another repetition. This does not prove that every use of the iris model is impossible, but it closes
the current BlazeFace/periodic-crop replacement branch.

**Next inquiry:** Disable the rejected hybrid shadow by default so ordinary reading uses only the
authoritative path, but retain the research harness and prior evidence. Before another full target or
calibration run, test only the compute cost of the official compact `face_landmark.tflite` model on
the SM-G991B. If it leaves sufficient frame budget for two iris passes, use its eye-corner landmarks
instead of BlazeFace eye centres in a new candidate-only three-position horizontal gate on both
phones. Stop before full calibration if either compute or horizontal ordering fails.

### 2026-09-06 — Official compact face-landmark compute gate (E-067)

**Question:** Is the official compact 468-landmark face model itself too expensive to justify a
face-corner-driven iris candidate on the slower SM-G991B?

**Action:** Added an explicit compute-only mode to the researcher performance activity, bundled the
official 1,242,398-byte `face_landmark.tflite` artifact (SHA256
`1055cb9d4a9ca8b8c688902a3a5194311138ba256bcc94e336d8373a5f30c814`), and ran 20 warm-up plus
100 measured CPU inferences with two threads on a fixed in-memory `[1,192,192,3]` float tensor. The
mode never requested camera access, created gaze output, or read/wrote calibration.

**Evidence:** On SM-G991B, interpreter initialization took 12.56 ms; inference median/P95/mean was
3.09/3.58/3.49 ms. The graph exposed the expected 1,404-float landmark output and one-float face
presence output. The benchmark-only APK SHA256 was
`82fccc15ba505917affca0d692ccbdd4f45037512d2e4013f93d50dd03f0b678`. The existing calibration
SHA256 remained `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`.

**Interpretation and boundary:** The compact graph passes the compute gate decisively, leaving about
46.4 ms at P95 inside the provisional 50 ms frame budget before crop preparation and the two iris
passes. This is not a full-pipeline throughput or gaze-accuracy result.

**Next inquiry:** Build one candidate-only, no-calibration horizontal gate that uses the compact
model's actual eye-corner landmarks to create the official 2.3x iris ROIs. Run once per phone before
any full grid or candidate mapper.

### 2026-09-06 — Compact face-corner + iris cross-device horizontal gate (E-068)

**Question:** Does replacing coarse BlazeFace eye centres with compact face-mesh eye corners restore
repeatable horizontal iris geometry on both tested phones?

**Action:** Added a researcher-only three-position gate using periodic BlazeFace face localization,
the compact 468-point face model, canonical mesh eye corners, and two 64x64 iris passes. The fixed
sequence presents left/centre/right twice after a pre-run instruction screen and 3-2-1 countdown.
It saves raw numeric samples and component timings but has no mapper, calibrated provider, gaze dot,
correction, or calibration-store dependency. One unchanged run was completed on each phone.

**Evidence:** SM-G991B accepted 6/6 presentations with target/candidate horizontal correlation
0.977. Its paired left presentations were 0.4948/0.4912, centres 0.5167/0.5103, and rights
0.5297/0.5246. Full candidate median/P95 work was 33.55/61.26 ms and active FPS was 15.53. A56
accepted 6/6 with correlation 0.994; paired left values were 0.4700/0.4703, centres 0.4836/0.4869,
and rights 0.4995/0.5029. Its work was 22.26/43.32 ms and active FPS 27.05. Artifacts are preserved
under `diagnostics-local/2026-09-06/compact-face/` with SHA256
`ac7e5d9d00fae3363b47b9244c6f236027a6776ad5512dce56920589a2f0058f` (SM) and
`eb554d07db507aa0a43e6f2f04bfc6f82542a4b67ef0c45d49c2e112929dd09d` (A56). Calibration hashes
remained `77b924...af17` and `713adc...5343`. The gate APK SHA256 was
`e4b66e48b833286d47b6f2827fc61eb855429647c4e735147854b9bc5a113ce0`; 80 gaze-focused JVM
tests passed.

**Interpretation and boundary:** Compact face corners resolve the earlier cross-device horizontal
geometry failure strongly enough to retain this architecture as a research candidate. They do not
yet solve SM throughput: its full candidate remains at the same roughly 15-16 FPS tier as the
authoritative 478-point path. On cached SM frames, total work was 32.28 ms median; face
crop/preparation/inference was 16.76 ms and two-eye iris crop/preparation/inference was 16.27 ms.
The 3.09 ms isolated face-graph result identifies image extraction/float packing as a bounded next
optimization target. No candidate calibration or screen-coordinate accuracy claim is authorized.

**Next inquiry:** Optimize only candidate tensor preparation with preallocated normalized float
arrays and bulk copies, then run a no-target SM performance preview. Preserve the geometry and stop
before a participant repeat or full calibration if throughput does not materially improve.

### 2026-09-06 — Compact-candidate bulk tensor preparation (E-069)

**Question:** Can the compact candidate materially improve SM throughput without changing its
models, crops, landmark indices, or raw feature geometry?

**Action:** Replaced individual RGB `ByteBuffer.putFloat` calls with preallocated float arrays, a
256-value normalization table, bit-extracted channels, and bulk `FloatBuffer.put`. Added 120-frame
candidate-only timing summaries. Installed the exact build on SM-G991B, opened only the pre-run
screen, and collected 11 face-visible windows without pressing Start or creating a target artifact.

**Evidence:** Across 1,320 valid frames, the median of window medians was 23.38 ms and the median of
window P95s was 41.81 ms, versus 33.55/61.26 ms in the E-068 SM target gate. Face
crop/preparation/inference fell from 16.77 to 8.00 ms median; the two iris passes fell from 16.32 to
14.42 ms. Window FPS averaged 20.13, median 19.24, and ranged 17.07-24.54. The optimized APK SHA256
was `31b5a3be1ce2c6b0847480fd6e02fa904702db1fa1ee9838fe1ce826b74214ac`. The preserved log SHA256
is `8b069f806ea4b99df01dce371faf7cf9080618ac1766c3cbd8020cde7caf48f0`; the SM calibration SHA256
remained `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`. All 80 gaze-focused JVM
tests and APK assembly passed.

**Interpretation and boundary:** This is a material candidate-only improvement: median work fell
about 30% and P95 about 32%, while sustained FPS improved beyond the prior 15-17 tier. It does not
reach a stable 30 FPS and does not prove mapped accuracy. Because the transformation is only an
equivalent input-packing implementation and the E-068 geometry passed on both phones, the next gate
is standalone candidate calibration rather than another three-position repetition.

**Next inquiry:** Add a researcher-only 16-point fit plus five held-out validation points on SM. Fit
the existing quadratic only in memory, save a separate numeric artifact, and never overwrite the
authoritative calibration or route candidate output into reading.

### 2026-09-06 — First standalone compact-candidate calibration gate (E-070)

**Question:** Can the optimized compact face-corner/iris feature support the current six-term ridge
quadratic across the full screen on SM-G991B without touching the authoritative calibration?

**Action:** Added and ran a separate researcher-only calibration activity. After an instruction
screen and 3-2-1 countdown, it uses one unrecorded centre practice, the unchanged 16 fixed row-major
fit targets, and five held-out centre/quadrant targets. It uses the existing collector timing and
`GazeMapper`, fits only in memory, writes a standalone numeric artifact, and has no calibrated gaze
provider or calibration-store dependency. The run label was `compact_face_cal_sm_1`.

**Evidence:** All 16 fit and five held-out targets were accepted on their first attempts. LOO
vertical median/P95/max was 1.48/4.98/4.98 lines (176/594/594 px), with 2-D median/P95/max
215/595/595 px. Held-out vertical median/P95/max was 1.75/2.22/2.22 lines (208/264/264 px), with
2-D median/P95/max 253/311/311 px. Held-out signed `(dx,dy)` was centre `(-161,-27)`, top-left
`(-185,+250)`, top-right `(-252,-19)`, bottom-left `(-82,+264)`, and bottom-right `(-114,+208)` px.
Fit-feature target correlations were 0.908 horizontal and 0.944 vertical. The run retained 395
numeric samples; candidate work was 21.84/36.96 ms median/P95, with 22.82 median and 23.54 mean
completion FPS from 480x640 source frames. The artifact is preserved under
`diagnostics-local/2026-09-06/compact-face/` with SHA256
`c68200eb01405ec1c97576d9d09febc47322d861b5288aa80c6e9cc4e7a04ca6`. The authoritative
`calibration_16point.csv` remained `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`,
and the app was closed.

**Interpretation and boundary:** This first run is adverse against the provisional 1.2-line
all-target spatial reference and does not establish an accuracy improvement. It is not simply the
historical first/top-left failure: all five held-out horizontal errors point left, while the largest
vertical errors occur at top-left and both lower quadrants. That pattern is compatible with
post-fit/session shift and/or mapper instability, but one run cannot distinguish them. The candidate
remains isolated and non-authoritative; no reading or cross-participant claim follows.

**Next inquiry:** Retain this run and perform exactly one unchanged SM-G991B replication labelled
`compact_face_cal_sm_2`. Compare every fit/held-out target and performance result before either an
A56 gate or rejection; do not tune the model against this first run.

### 2026-09-06 — Replicated SM compact-candidate calibration gate (E-071)

**Question:** Does the materially adverse first SM candidate calibration repeat under the exact same
model, mapper, target, timing, and device conditions?

**Action:** Repeated the standalone 16+5 activity unchanged. The launch omitted its optional
`run_label` intent extra, so the artifact correctly contains the default label
`compact_face_calibration`; this activity has no editable label field. The unique timestamp/file and
completion order identify it as the second run, and the missing custom label does not invalidate or
require repeating the measurement.

**Evidence:** All 16 fit and five held-out targets again passed on their first attempts. LOO vertical
median/P95/max improved to 1.01/3.24/3.24 lines, with 2-D median/P95/max 161/416/416 px. Held-out
vertical median/P95/max improved to 0.83/2.09/2.09 lines, with 2-D median/P95/max 100/251/251 px.
Held-out signed `(dx,dy)` was centre `(-103,+180)`, top-left `(-8,+77)`, top-right `(-23,-97)`,
bottom-left `(+36,-249)`, and bottom-right `(+2,-99)` px. Across the two runs, fit-feature patterns
correlate at 0.941 H and 0.840 V, but the mean feature position shifted -0.0256 H/+0.0172 V and the
held-out signed vertical error pattern correlates at -0.456. The run retained 407 samples; candidate
work was 17.42/31.95 ms median/P95 and completion FPS was 27.97 median/27.85 mean. Its preserved
artifact SHA256 is `cf635b8c6efb7c8becfaba7f829a5fcc58c79d7f1ea761390f5359e71f14aee4`.
The real calibration remained `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`,
and the app was closed.

**Interpretation and boundary:** The second run is materially better, and the raw full-grid feature
shape plus roughly 28 FPS throughput justify retaining the architecture through one A56 gate. It is
not a repeatable accuracy pass: neither SM run keeps every held-out target within the provisional
1.2-line reference, the worst held-out vertical error remains about 2.1 lines in both, and the
regional signed-error pattern moves substantially between runs. No runtime integration, mapper
tuning, reading test, or accuracy-improvement claim is authorized.

**Next inquiry:** Run one unchanged standalone candidate calibration on A56 with the label supplied
through the launch intent as `compact_face_cal_a56_1`. Compare it with both SM runs before deciding
whether one A56 replication is warranted.

### 2026-09-06 — First A56 compact-candidate calibration gate (E-072)

**Question:** Does the isolated compact candidate retain useful full-screen geometry and accuracy on
the second phone while preserving its expected throughput advantage?

**Action:** Installed the exact E-070/E-071 build on A56 with `adb install -r`, after recording the
existing app and calibration hashes. Verified the installed APK matched local SHA256
`f417ceece6d6654e9b66f0f1e153be7b084baf605c78f241b53414c9856426fb` and the calibration was
unchanged, then launched the isolated activity with embedded label `compact_face_cal_a56_1`. No
normal calibration or authoritative gaze path was used.

**Evidence:** All 16 fit and five held-out targets were accepted on their first attempts. LOO
vertical median/P95/max was 2.89/8.11/8.11 lines, with 2-D median/P95/max 348/963/963 px. Held-out
vertical median/P95/max was 2.89/4.76/4.76 lines, with 2-D median/P95/max 448/633/633 px. Held-out
signed `(dx,dy)` was centre `(-289,+343)`, top-left `(-287,+564)`, top-right `(-379,+323)`,
bottom-left `(-168,-1)`, and bottom-right `(+52,+384)` px. Fit-feature target correlation was 0.931
horizontal but only 0.778 vertical; the first two vertical row means slightly reverse (0.42 then
0.41) before rising to 0.43/0.46. The run retained 444 samples. Candidate work was 17.32/27.23 ms
median/P95, with 30.40 median and 30.00 mean completion FPS. The artifact is preserved under
`diagnostics-local/2026-09-06/compact-face/` with SHA256
`5500a2623fa32065481bc58ebf24404be49b5ae5388d71d19f64e4610221157e`. The real A56 calibration
remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`, and the app was closed.

**Interpretation and boundary:** Throughput reaches the desired camera-rate tier on A56, but this
first full-screen accuracy result is clearly adverse. Weak vertical target ordering within the fit
data means the failure cannot be attributed only to the quadratic mapper or held-out temporal drift.
Consistent with the preregistered replication rule, retain rather than invalidate this run and make
one unchanged A56 replication. It does not authorize integration or tuning.

**Next inquiry:** Repeat once on A56 under the same setup with embedded label
`compact_face_cal_a56_2`. If that run is also adverse, close the candidate; if it improves, compare
both rather than discarding the first.

### 2026-09-06 — Replicated A56 failure and third sanity run (E-073)

**Question:** Was the adverse first A56 compact calibration a bad single run, or does full-screen
accuracy fail to repeat even though candidate throughput reaches camera rate?

**Action:** Collected the planned unchanged replication as `compact_face_cal_a56_2`. At the user's
request, retained that result and collected one additional unchanged sanity run as
`compact_face_cal_a56_3`, with the decision rule fixed in advance: a good third run would establish
instability rather than erase the first two; another poor run would close the candidate. No normal
calibration, candidate change, or mapper tuning occurred.

**Evidence:** Run 2 accepted all targets but produced LOO vertical median/max 1.74/6.13 lines and
held-out median/max 3.31/6.80 lines (2-D median/max 748/1,160 px). Its five held-out `(dx,dy)` errors
were `(+46,+806)`, `(+142,+392)`, `(+1145,-184)`, `(+271,+62)`, and `(-73,-744)` px. Run 3 again
accepted all targets but produced LOO 1.66/6.70 lines and held-out 4.68/6.45 lines (2-D median/max
560/766 px); held-out errors were `(+37,+765)`, `(+154,+678)`, `(+77,+555)`, `(+109,+62)`, and
`(+122,-48)` px. Run 2 fit-target H/V correlations were 0.937/0.909; run 3 was 0.846/0.911, showing
that apparently ordered fit geometry did not prevent severe immediate held-out errors. Run 2/3 mean
completion FPS was 30.26/30.48 and median/P95 candidate work was 16.83/27.22 and 16.66/27.50 ms.
The A56 run-2/run-3 artifacts are preserved with SHA256
`3324ffd8c16b8c10cd82dfbaa6d73247032ba805d5e2916d7b970fb139116f64` and
`62786459f4b4b5e799d967f560d38e27b5f2bd9563b28d76b469b532d10a6b83`. The normal A56 calibration
remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343` after all three runs.

**Interpretation and decision:** The compact candidate meets the A56 throughput objective but fails
the replicated cross-device accuracy gate. Across all three A56 runs, held-out vertical median/max
was 2.89/4.76, 3.31/6.80, and 4.68/6.45 lines. The third run confirms rather than rescues the failure;
no run may be discarded. Because runs 2 and 3 can have strong fit-target vertical correlation yet
catastrophic immediate held-out errors, a mapper-only retune against these five answers is not a
credible general solution. Reject runtime integration and close this compact-candidate branch.
Retain its harness/artifacts as evidence that the speed improvement is real but insufficient.

**Next inquiry:** Stop candidate phone tests. Checkpoint the isolated research harness and evidence,
excluding private artifacts, then resume accuracy work on the authoritative 478-point eye-local path.
Prioritize calibration-to-reading stability; do not repeat the rejected compact/hybrid, lower-input,
delegate, mapper, simple-offset, posture-coefficient, or target-order experiments.

### 2026-09-06 — Fixed end-pose feature-translation screen (E-074)

**Question:** Can the existing repeated same-target drift measurement translate the 16 calibration
features into the end-of-calibration pose and improve transfer to the five later held-out targets?

**Action:** Implemented an offline-only analyzer for one preregistered candidate. It adds the complete
raw two-axis `second - first` same-target feature vector once to every fit aggregate, then fits the
unchanged six-term ridge quadratic. Held-out observations and answers remain untouched. No fraction,
cap, axis choice, ridge value, or alternative is searched. Included all 13 accepted complete
eye-local calibrations from September 1–4, including both post-coordinate-repair sessions and every
high-error/high-drift result. Phone payloads were read in memory; only derived results and hashes are
retained.

**Evidence:** The analyzer reproduces all 273 logged baseline prediction pairs exactly (0 px maximum
parity delta). Candidate held-out vertical median improves in 10/13 sessions, but maximum improves in
only 7/13; both improve in 5/13. Requiring vertical and 2-D median/max together leaves only 2/13
session wins. Across equally weighted sessions, median vertical median changes 97.4→85.9 px while
median vertical maximum worsens 155.9→183.8 px. Median 2-D median changes 147.4→137.8 px and median
2-D maximum 204.8→205.6 px. All 27 offline gaze-analysis tests pass. The complete derived report is
`docs/gaze-end-pose-translation-screen-2026-09-06.json` (SHA256
`60c40404cd0eac19c4f8d16c6fa0c3ebad1afc4315e50131aa0af426bccbb238`); the readable decision table
is in `docs/gaze-end-pose-translation-screen.md`.

**Interpretation and decision:** The repeated target identifies real feature movement, but applying
one global end-pose translation trades typical error against regional tails. The large improvement
in the poor September 4 02:42 calibration does not generalize. Reject this correction and do not tune
scaled, capped, or single-axis variants against the same answers. No Android code, installed app,
calibration, or gaze output changed.

**Next inquiry (superseded by E-075):** The original closure proposed freezing the retained
pipeline or a separate model replacement. That inference exceeded this experiment's scope.
E-074 rejects one constant-shift correction, not all small corrections or pose compensation.
Do not request another calibration repetition as though repetition itself were an intervention.

### 2026-09-06 — Evidence reassessment and native MGazeNet handoff (E-075)

**Question:** Have the practical options actually been exhausted, and which next inquiry best
addresses on-device accuracy and throughput across both phones without repeating failed work?

**Action:** At the user's request, revisited the handoff, relevant diagnostic/calibration records,
active eye geometry and mapper, historical laptop bridge, model-replacement proposal, thesis
tracking/validation sections, and primary model sources. The user subsequently requested a
handoff for a fresh task. No phone interaction, camera capture, calibration, benchmark, or app
source modification occurred in this review or handoff preparation.

**Evidence and interpretation:** E-043 evaluated a posture envelope flag, not coordinate
compensation. E-038 changed both separate-eye inputs and mapping family; its failures cannot
exclude all uses of per-eye information. E-074 shifted all fit features by one constant vector,
not a measured per-point temporal/pose transform. E-070-E-073 repeatedly failed the candidate's
spatial gate, but lacked fresh matched reference calibrations and cannot establish the baseline
as superior under those same sessions or isolate device causality. These qualifications retain
every recorded adverse result and do not promote any rejected candidate.

The historical `tools/gazefollower_stream.py` used a laptop webcam/UDP arrangement. In contrast,
upstream GazeFollower exposes a pretrained MGazeNet `base.mnn` and reference estimator/calibrator;
associated research includes smartphone MNN deployment. The public model's accuracy and full-path
speed on A56/SM-G991B are unknown. Public/source versions, weights, processing conventions,
calibration, and usage terms must be pinned before an implementation. Source links and audit
details are recorded in `gaze-mgazenet-handoff.md`.

**Decision:** Withdraw the categorical freeze/exhaustion conclusion. Select a bounded native
MGazeNet feasibility audit as the next inquiry, with correctly evaluated limited pose compensation
as a second option. Keep the existing 478-point path as an unchanged comparison baseline, not a
certified research instrument. The latest authorization is documentation/handoff preparation;
obtain direction on the bounded implementation proposal before changing/installing the app.

**Next inquiry:** Verify exact model, preprocessing, calibration, and Android runtime requirements;
propose a source-equivalent isolated full-path benchmark before requesting accuracy runs. Neither
published model-only speed nor historical laptop accuracy establishes a phone improvement.

## Evidence Registry

| ID | Date | Evidence source | Conditions | Artifact or location | Notes |
|---|---|---|---|---|---|
| E-001 | 2026-08-20 | Manuscript historical measurements | Pre-rebuild calibration | `Manuscript/Thesis Paper.pdf` | Context only; requires remeasurement |
| E-002 | 2026-08-21 | Raw gaze source audit | Static code inspection | `MediaPipeRawGazeSource.kt` | Both eyes averaged; callback-time timestamp |
| E-003 | 2026-08-21 | Live provider audit | Static code inspection | `LocalCalibratedGazeProvider.kt` | Median + One Euro before mapping; optional correction after mapping |
| E-004 | 2026-08-21 | Mapper audit | Static code inspection | `GazeMapper.kt` | Standardized ridge-regularized quadratic mapping with bounded extension |
| E-005 | 2026-08-21 | Calibration persistence audit | Static code inspection | `CalibrationSessionLog.kt` | Aggregates persist; individual raw samples do not |
| E-006 | 2026-08-21 | Calibration validation audit | Static code inspection | `GazeCalibrationActivity.kt` | Validates aggregated raw feature through base mapper |
| E-007 | 2026-08-21 | Accuracy-test audit | Static code inspection | `GazeTestActivity.kt` | Live path measured; active correction may be loaded; results not persisted |
| E-008 | 2026-08-21 | Article coordinate/AOI audit | Static code inspection | `ArticleFragment.kt`, `LineAoiMapper.kt`, `GazeOverlayView.kt` | Full-screen contract is explicit; on-device verification remains |
| E-009 | 2026-08-21 | Existing unit-test execution attempt | JDK 21 then JDK 17 | Gradle output | Blocked by KAPT/JDK mismatch, then inaccessible Android SDK metadata/build tools |
| E-010 | 2026-08-21 | Raw-source diagnostics callback | Local source edit, not yet device-verified | `LocalRawGazeSource.kt`, `MediaPipeRawGazeSource.kt` | Numeric per-result eye/source/timing telemetry |
| E-011 | 2026-08-21 | Pipeline diagnostics callback | Local provider edit, not yet device-verified | `LocalCalibratedGazeProvider.kt` | Observes raw, median, filtered, mapped, and output values |
| E-012 | 2026-08-21 | SAMPLE-window boundary callback | Local collector edit, not yet device-verified | `CalibrationPointCollector.kt` | Restricts recorded samples to the existing measurement window |
| E-013 | 2026-08-21 | Persistent accuracy-session writer | Local new file, not yet device-verified | `GazeAccuracySessionLog.kt` | Numeric-only JSON; no camera images retained |
| E-014 | 2026-08-21 | Nine-point diagnostics integration | Local activity edit, not yet device-verified | `GazeTestActivity.kt` | Labelled runs, per-attempt evidence, persisted summary |
| E-015 | 2026-08-21 | Full calibration source diagnostics | Local activity edit, not yet device-verified | `GazeCalibrationActivity.kt` | Covers practice, 16 fit targets, repeated drift target, retries, and 5 validation targets |
| E-016 | 2026-08-21 | Extended calibration-session artifact | Local logger edit, not yet device-verified | `CalibrationSessionLog.kt` | Per-attempt source events plus validation mapped coordinates and residuals; no images |
| E-017 | 2026-08-23 | On-device calibration diagnostics | Galaxy A56; normal-light handheld run with glasses | `calibration_session_20260823_181644.json`, `calibration_session_20260823_181801.json` | First session redone; second accepted with high drift flag |
| E-018 | 2026-08-23 | Immediate uncorrected live-path run | Galaxy A56; run label omitted | `gaze_accuracy_session_20260823_181952_096.json` | 201 px / 1.70-line median; no correction active |
| E-019 | 2026-08-23 | Three labelled uncorrected baseline repetitions | Galaxy A56; normal light, glasses, handheld | `gaze_accuracy_session_20260823_182120_179.json`, `...182148_794.json`, `...182216_339.json` | 3.41-4.15-line medians; persistent leftward compression |
| E-020 | 2026-08-23 | Source reliability and timing telemetry | Same calibration and accuracy SAMPLE windows | Per-frame `source_events` in E-017-E-019 | No face/blink loss in-window; ~29-31 ms median inference result time; actual 320x240 input |
| E-021 | 2026-08-23 | Fixed-phone calibration repetitions | Galaxy A56; normal light, glasses, supported at ~50 cm | Five `calibration_session_20260823_184*.json` artifacts | Large between-calibration variability despite fixed phone |
| E-022 | 2026-08-23 | Fixed-phone immediate/delayed live-path runs | Same final accepted calibration; no correction | Three `gaze_accuracy_session_20260823_184*.json` artifacts | 2.58-3.37-line medians; 8.28-9.33-line P95 |
| E-023 | 2026-08-23 | Fixed-phone feature-drift comparison | Immediate, +1 minute, +2 minutes | Per-frame raw/pipeline samples in E-022 | Raw horizontal range shifts left while phone remains fixed |
| E-024 | 2026-08-23 | Final calibration regional/feature geometry | Final accepted fixed-phone calibration | `calibration_session_20260823_184452.json` | 500 px drift, overlapping lower-row vertical features, one 4.80-line validation miss |
| E-025 | 2026-08-23 | Researcher historical observation | Prior hands-on NewsMead/prototype use | Conversation record | Earlier behavior perceived near the ~1.2-line useful range; current behavior clearly worse |
| E-026 | 2026-08-23 | Pre-redesign accuracy record | Galaxy A56 and sister prototype before calibration rebuild | `docs/progress-notes.md` | A56 vertical 0.67-1.01 cm; prototype approximately 0.7 cm consistently |
| E-027 | 2026-08-23 | Regression-window audit | Git history around July 24 rebuild | Commits `4b5198f`, `3dca061`, `4322154`, `8dddc02` | Sampling, filtering, extrapolation, and accuracy-test behavior changed after earlier measurements |
| E-028 | 2026-08-23 | Offline mapper/aggregation replay | Final fixed-phone calibration and three live runs | `diagnostics-local/analyze_mapper_replay.py` | Exact current replay; clamp reduces tails but does not restore line accuracy; plain median/affine results mixed |
| E-029 | 2026-08-23 | Reading-oriented validation revision | Local implementation and JVM tests | `ReadingSpatialMetrics.kt`, calibration and accuracy activities/loggers | Per-target dx/dy, vertical line metrics, tails, regions, provisional all-target reference; estimator unchanged |
| E-030 | 2026-08-24 | On-device revised-metrics verification | Galaxy A56; fixed phone, normal light, no glasses; saved calibration plus immediate uncorrected run | `diagnostics-local/2026-08-24/metrics-v2/run-2/` | Complete 16 LOO + 5 held-out + 9 live target metrics; large regional errors and vertical drift correctly exposed |
| E-031 | 2026-08-24 | Older-adult target/order intervention | Visual behavior reviewed on Galaxy A56; accuracy comparison pending | `CalibrationView.kt`, `GazeCalibrationActivity.kt`, `CalibrationSessionLog.kt` | Fixed hit-marker, contraction through expected base sample, fixed row-major traversal; estimator unchanged |
| E-032 | 2026-08-24 | Conversation-era regression audit | Git comparison of `f77ce6e`, `79a5ceb`, and `e214eb6` | Repository history and relevant gaze source diffs | Estimator/filter math unchanged; detailed telemetry is a remaining runtime confound; July redesign predates conversation |
| E-033 | 2026-08-24 | Detailed-telemetry ON/OFF control | Compile, focused/all gaze JVM tests, APK assembly, A56 install, source diff audit | Telemetry mode UI, session loggers, diagnostic listener boundary, FPS accumulator/test | OFF removes detailed callbacks/arrays; lightweight comparison evidence remains in both modes; physical A/B pending |
| E-034 | 2026-08-24 | Replicated natural-handheld telemetry control | Galaxy A56; normal light, no glasses; two valid calibration/immediate-nine-point pairs per mode | Ten new app-private artifacts from 15:21–15:37; eight included, one mismatched pair excluded | OFF greatly reduced artifact size but did not restore FPS, samples, or accuracy; instrumentation confound closed |
| E-035 | 2026-08-24 | Centered pre-run countdown | Static entry-path audit, compile, all gaze JVM tests, APK assembly/install | Calibration/test activities and layouts | 3–2–1 occurs only before the first target; capture behavior unchanged; fresh OFF confirmation pending |
| E-036 | 2026-08-28 | Independent-session handheld repeatability | Galaxy A56; natural handheld, normal light, no glasses, telemetry OFF, no correction | `calibration_session_20260828_060527.json`, `gaze_accuracy_session_20260828_060732_402.json` in app-private storage | Median remained improved at 1.55 lines, but 4.87-line live and 7.44-line held-out tails confirm unresolved regional/repeatability risk |
| E-037 | 2026-08-28 | Five-pair offline tail-predictor analysis | Five valid recent natural-handheld calibration/immediate-test pairs; descriptive n=5 comparison | `diagnostics-local/analyze_recent_repeatability.py`; source artifacts streamed from app-private storage | No reliable gate predictor; drift is directional only; top-left is worst calibration LOO point in 5/5 and worst live point in 4/5 |
| E-038 | 2026-08-29 | Prospective per-eye mapper intervention | Galaxy A56; natural handheld, telemetry OFF, two full calibration/immediate-test pairs, no affine correction | Four app-private artifacts at 20:49–20:54; `tools/gaze/compare_mapper_candidates.py` | Per-eye live medians/tails 5.74/8.83 and 2.63/4.19 lines; candidate rejected and quadratic default restored |
| E-039 | 2026-08-29 | Quadratic rollback sanity check | Galaxy A56; fresh calibration OFF, immediate test inadvertently ON, no affine correction | `calibration_session_20260829_214116.json`, `gaze_accuracy_session_20260829_214301_000.json` in app-private storage | Mapper confirmed quadratic; live median/max 1.35/2.58 lines, materially better than both per-eye runs |
| E-040 | 2026-08-29 | Upper-left acquisition-practice intervention | Static sequence/diff audit, focused gaze tests, APK assembly/install | Calibration activity and lightweight session fields | Adds one unrecorded upper-left practice before fit point 0; rejected per-eye runtime removed; prospective pair pending |
| E-041 | 2026-08-29 | Prospective upper-left-practice evaluation and rollback | Galaxy A56; telemetry OFF calibration/test, quadratic mapper, no affine correction | `calibration_session_20260829_220839.json`, `gaze_accuracy_session_20260829_221046_730.json`; subsequent rollback build/install | Median improved but top-left and global tail did not; intervention rejected and original sequence restored |
| E-042 | 2026-08-29 | Posture-departure shadow monitor | Static path audit, compile, gaze-focused JVM tests, APK assembly/install | Posture features/profile, additive calibration CSV fields, live assessment and lightweight summaries | Evidence-only; no coordinate/filter/mapper/AOI/scaffold effect; physical run pending |
| E-043 | 2026-08-29 | Posture-departure shadow control | Galaxy A56; telemetry-OFF calibration/immediate test, quadratic mapper, no correction | `calibration_session_20260829_230755.json`, `gaze_accuracy_session_20260829_230917_726.json` | 145/145 assessed; flags confined to two relatively accurate final targets; shadow status not validated for downstream gating |
| E-044 | 2026-08-30 | Roll-aware eye-local raw-feature candidate | Static path audit, pure geometry/focused gaze JVM tests, APK assembly/install | `RawGazeFeatureMode.kt`, MediaPipe feature extraction, calibration-mode sidecar and explicit log field | Reversible raw geometry only; mapper/filter/target/sequence unchanged; evaluated in E-045 |
| E-045 | 2026-09-01 | Prospective eye-local candidate evaluation | Galaxy A56; two telemetry-OFF calibration/immediate-test pairs; no correction | `calibration_session_20260901_191457.json`, `gaze_accuracy_session_20260901_191648_408.json`, `calibration_session_20260901_191802.json`, `gaze_accuracy_session_20260901_191926_612.json` | Vertical improvement repeats; candidate retained; remaining horizontal/read-context limits documented |
| E-046 | 2026-09-01 | Known-target reading accuracy validation | Galaxy A56; protocol v3/order B, fresh telemetry-OFF calibration, no correction | `diagnostics-local/calibration_session_20260901_210620.json`, `diagnostics-local/reading_validation_session_20260901_210750_610.jsonl` | 14/14 targets and 841 valid samples; guided exact/within-one line 40.5%/88.6%, exact word 10.9%; center-compression pattern; v2 excluded |
| E-047 | 2026-09-01 | Reading-surface vertical alignment control | Static path audit, focused JVM tests, debug APK assembly | Protocol/schema v4, `ReadingVerticalAlignment.kt`, v4 activity/logger fields | Identical OFF/ON references; guarded session-only y gain/bias; physical pair pending |
| E-048 | 2026-09-02 | First prospective reading-surface vertical alignment pair | Galaxy A56; one telemetry-OFF calibration; OFF then ON; no affine correction | `diagnostics-local/calibration_session_20260902_212424.json`, `diagnostics-local/reading_validation_session_20260902_212604_503.jsonl`, `diagnostics-local/reading_validation_session_20260902_212925_888.jsonl` | Adverse first result retained; user-requested replications follow in E-049 |
| E-049 | 2026-09-03 | Two unchanged vertical-alignment replications | Galaxy A56; telemetry OFF; ON->OFF then OFF->ON in separate sessions; no affine correction | App-private reading files `20260903_011603_530`, `011909_341`, `210221_564`, `210447_787`; identities/hashes in offset replay JSON | Second ON helps guided reading but harms words; latest ON harms its own baseline; no reliable cross-task benefit |
| E-050 | 2026-09-03 | Fixed offset-only offline screen | All six v4 recordings; references-only median shift; no new phone run | `tools/gaze/replay_reading_offsets.py`, `docs/gaze-offset-replay.md`, aggregate JSON | 10,580 reconstructed assignments match; 11 pure tests pass; aggregate gains coexist with major regional failures; no app implementation |
| E-051 | 2026-09-03 | Fixed vertical-only quadratic screen | All 11 complete accepted eye-local calibrations; ridge 1, LOO and separate validation; no phone run | `tools/gaze/compare_vertical_mapper.py`, `docs/gaze-vertical-mapper-screen.md`, aggregate JSON | Baseline exact parity on 231 coordinate pairs; LOO maxima worse 11/11, held-out maxima worse 8/11; mapper unchanged; 12 new tests pass |
| E-052 | 2026-09-03 | Eye-path and coordinate-contract audit | Shared source audit; 11 calibration aggregates; sparse latest-pair probes; user-approved idle-screen geometry inspection | `docs/gaze-coordinate-audit.md`; A56 window/view bounds | Confirmed calibration origin y=101 omitted from stored targets; reading expects screen pixels; no new calibration or runtime repair; saved data preserved |
| E-053 | 2026-09-03 | Screen-coordinate repair | Approved implementation; 67 JVM and four on-device storage/layout tests pass | Coordinate helpers, store tests and `GazeScreenCoordinatesTest`; design §7.5 | Installed; legacy warning verified; 105 existing file hashes unchanged; no participant accuracy result yet |
| E-054 | 2026-09-04 | Screen-coordinate repair physical evaluation | Galaxy A56; two fresh telemetry-OFF calibration/immediate alignment-OFF reading pairs | `diagnostics-local/2026-09-04/coords-v1-a56/` | Both pairs preserved; pair 1 weak/high-drift and pair 2 strong/low-drift; repeatability remains unresolved |
| E-055 | 2026-09-04 | Second-device performance-readiness check | Samsung SM-G991B; exact tested APK; GPU; 640x480; no official accuracy run | Read-only package/hash, MediaPipe logcat, battery and thermal inspection | About 16 FPS with visible lag and severe thermal status; cross-device accuracy paused for bounded FPS optimization |
| E-056 | 2026-09-04 | Two-phone performance profile and temporary comparisons | SM-G991B plus A56; cool-start 640/GPU checks; direct-buffer, 320/direct, and 640/CPU temporary screens; no calibration started | ART trace, gfx/CPU/thermal snapshots, source audit, 67 final JVM tests, APK build/install/hash, MediaPipe logcat | Temporary paths rejected and safe 640/GPU restored; face-visible throughput is about 15-17 FPS on SM-G991B and 18.5 median on cool A56; current 478-point backend is the limit |
| E-057 | 2026-09-05 | 64x64 two-eye model feasibility and same-frame shadow | Galaxy A56; CPU LiteRT; isolated synthetic crop/inference plus 120 real face-visible shadow frames; no calibration started | Temporary benchmark output and `IrisShadow` logcat; summary retained in this entry | Two-eye compute is feasible; central raw-feature deltas are small enough for a hybrid prototype, but ROI acquisition and full-range accuracy remain unproved; temporary runtime/model removed; clean APK restored and installed |
| E-058 | 2026-09-05 | Independent-ROI hybrid and target-linked full-screen shadow | Galaxy A56; BlazeFace plus two 64x64 iris passes on CPU; reference remains authoritative; telemetry OFF; day-old calibration | App-private original plus authorized hash-matched copy under `diagnostics-local/2026-09-05/hybrid-shadow/` | Candidate sustains near camera rate and preserves full-screen target ordering with 0.881/0.916 target-median H/V correlation; range differs, so direct substitution and this run's coordinate accuracy are excluded |
| E-059 | 2026-09-05 | Candidate-only cross-device throughput and periodic face ROI | SM-G991B; 640x480; BlazeFace every frame followed by every fourth frame; no gaze output or calibration run | `HybridEyePerformanceActivity` logcat summaries; numeric results retained in this entry | Periodic ROI raises stable candidate completion from about 17.34 to 25.97 FPS and reduces busy-drop share from 42.1% to 13.3%; optimized full-screen feature compatibility remains unproved |
| E-060 | 2026-09-05 | Replicated periodic coarse-ROI target compatibility | SM-G991B; two telemetry-OFF nine-point shadows; reference authoritative; old calibration score excluded | Four app-private session/hash identities retained in E-060/E-061 | Candidate target H/V agreement is 0.388/0.800 then 0.190/0.777; coarse ROI version rejected before candidate calibration |
| E-061 | 2026-09-05 | Eye-corner-refined ROI, replicated compatibility, and warm throughput | SM-G991B; two telemetry-OFF nine-point shadows plus candidate-only preview; reference authoritative | App-private numeric sessions plus logcat; hashes and statistics retained in this entry | Target H/V agreement recovers to 0.904/0.895 and 0.778/0.967; warm candidate sustains 22.88 FPS; A56 replication remains required |
| E-062 | 2026-09-06 | Recursive eye-corner ROI A56 replication | A56; two telemetry-OFF nine-point shadows; reference authoritative; coordinate scores excluded | App-private numeric sessions; hashes and statistics retained in this entry | Candidate H/V target agreement is 0.090/0.634 then 0.653/0.922; cached horizontal behavior fails twice; recursive refinement rejected |
| E-063 | 2026-09-06 | Detector-anchored size-only ROI A56 replication | A56; two telemetry-OFF nine-point shadows; reference authoritative; coordinate scores excluded | App-private originals plus authorized copies under `diagnostics-local/2026-09-06/hybrid-shadow/` | Candidate H/V target agreement is 0.635/0.479 then -0.049/0.396; inter-run repeatability is -0.240/0.229; size-only refinement rejected |
| E-064 | 2026-09-06 | Detector-anchored one-step centre/size re-extraction | Shadow-only implementation, 75 gaze JVM tests, APK build/install/hash and data-preservation checks | Hybrid geometry/backend/sample logger and A56 debug APK | Installed APK matches `ea5475d...a214`; calibration and E-063 files unchanged; physical A56 gate pending |
| E-065 | 2026-09-06 | Replicated one-step re-extraction A56 gate | A56; two telemetry-OFF nine-point shadows; reference authoritative; coordinate scores excluded | App-private originals plus authorized copies under `diagnostics-local/2026-09-06/hybrid-shadow/` | Candidate/intended-target H/V is 0.876/0.965 then 0.825/0.902 at 28.76/28.38 FPS; conditional A56 pass for SM replication |
| E-066 | 2026-09-06 | Replicated one-step re-extraction SM-G991B gate | SM-G991B; exact E-064 APK; two telemetry-OFF nine-point shadows; coordinate scores excluded | App-private originals plus authorized copies under `diagnostics-local/2026-09-06/hybrid-shadow/` | Candidate/intended horizontal is 0.154 then 0.733 and repeats at 0.513; cross-device candidate rejected despite strong vertical order |
| E-067 | 2026-09-06 | Official compact face-landmark compute gate | SM-G991B; fixed in-memory tensor; 20 warm-up + 100 measured CPU runs; no camera/calibration/gaze | Researcher performance activity and logcat | Face graph median/P95 3.09/3.58 ms; compute gate passed |
| E-068 | 2026-09-06 | Compact face-corner + iris horizontal gate | One six-presentation left/centre/right candidate-only run per phone; no mapper or calibration access | Authorized copies under `diagnostics-local/2026-09-06/compact-face/` | H correlation 0.977 SM and 0.994 A56; geometry passes, but full candidate is only 15.5 FPS on SM versus 27.0 on A56 |
| E-069 | 2026-09-06 | Bulk compact-candidate tensor preparation | SM-G991B; 11 no-target face-visible 120-frame windows; no calibration/gaze output | `diagnostics-local/2026-09-06/compact-face/compact_face_perf_sm_bulk_v2.txt` | Median work 23.38 ms, P95 41.81 ms, mean window FPS 20.13; bounded performance gate passes |
| E-070 | 2026-09-06 | First standalone compact-candidate calibration | SM-G991B; in-memory 16-point quadratic plus five held-out targets; authoritative calibration untouched | `diagnostics-local/2026-09-06/compact-face/compact_face_calibration_20260905_184207_compact_face_cal_sm_1.json` | Held-out vertical median/max 1.75/2.22 lines and all five dx values leftward; adverse first run retained for one unchanged replication |
| E-071 | 2026-09-06 | Replicated standalone compact-candidate calibration | SM-G991B; exact candidate and protocol unchanged; default embedded label retained | `diagnostics-local/2026-09-06/compact-face/compact_face_calibration_20260905_184855_compact_face_calibration.json` | Held-out median improves to 0.83 lines but maximum remains 2.09; raw grid shape repeats while regional signed errors do not; conditional A56 gate only |
| E-072 | 2026-09-06 | First A56 standalone compact-candidate calibration | A56; exact SM-gate APK and protocol; normal calibration untouched | `diagnostics-local/2026-09-06/compact-face/compact_face_calibration_20260905_185734_compact_face_cal_a56_1.json` | Candidate reaches 30.00 mean FPS but held-out median/max is 2.89/4.76 lines and vertical grid correlation is 0.778; retained for one unchanged replication |
| E-073 | 2026-09-06 | A56 compact-candidate replication plus sanity run | A56; unchanged run 2 and user-requested unchanged run 3; all earlier failures retained | Two preserved artifacts under `diagnostics-local/2026-09-06/compact-face/` | Approximately 30 FPS repeats, but held-out median/max worsens to 3.31/6.80 and 4.68/6.45 lines; cross-device accuracy gate fails and candidate closes |
| E-074 | 2026-09-06 | Fixed end-pose feature-translation screen | 13 accepted complete A56 eye-local calibrations; unchanged quadratic; phone inputs read in memory | `docs/gaze-end-pose-translation-screen.md` plus derived JSON | Typical vertical error improves often, but median tail worsens and only 2/13 sessions improve all selected vertical/2-D metrics; candidate rejected |
| E-075 | 2026-09-06 | Evidence/source reassessment and handoff; no new measurement | Repository, thesis, and primary upstream model/code review | `docs/gaze-mgazenet-handoff.md` | Freeze/exhaustion inference withdrawn; native MGazeNet feasibility selected, not implemented or validated; pose compensation remains untested |

## Confirmed Findings

1. No definite gaze-coordinate defect was established by static inspection alone.
2. The current saved artifacts are insufficient for a reproducible end-to-end baseline because nine-point observations are not persisted or condition-labelled.
3. Current data cannot isolate individual-eye behavior or camera-to-output latency.
4. Calibration validation and live accuracy testing measure different processing paths and must not be treated as interchangeable.
5. A saved drift correction can make a nine-point run a corrected measurement rather than a base-pipeline baseline.
6. The initial A56 baseline shows a repeatable leftward live-output compression that worsens toward the right side of the screen.
7. The current median-only green calibration advisory can pass a session despite a high drift flag and very large leave-one-out tail error.
8. The diagnostics build receives 320x240 CameraX frames on the A56 despite the intended 480x360 analysis profile.
9. Physically supporting the phone improves typical error but does not eliminate rapid raw-feature drift or severe regional failures.
10. The current two-value averaged iris-in-eye feature has weak and overlapping vertical separation in the lower screen region under the observed participant geometry.
11. Median accuracy alone materially understates current live-path risk: fixed-phone P95 error exceeded eight line-heights in all three repetitions.
12. Historical records and researcher observation establish a credible regression relative to the earlier NewsMead/prototype behavior.
13. Current soft extrapolation amplifies some catastrophic regional errors, but it is not the sole cause of the regression.
14. Accuracy and correction decisions must include individual target and regional errors; session median alone is insufficient.
15. The 16-point and nine-point result screens now implement target-wise, reading-oriented spatial reporting while keeping reading compatibility as an unestablished outcome.
16. The revised measurements are verified on device and correctly prevent favorable aggregate interpretation when individual targets, tails, or drift fail (E-030).
17. A five-coefficient per-eye ridge-linear mapper failed both prospective live runs and was restored to the averaged quadratic default; retrospective multi-session wins were not sufficient deployment evidence.
18. A fresh rollback sanity run explicitly recorded the quadratic mapper and recovered to a 1.35-line median/2.58-line maximum; it confirms restoration but also confirms unresolved base-pipeline session variability.
19. The next isolated intervention changes only acquisition context: point 0 is preceded by an unrecorded fixation at the same upper-left coordinate while all recorded points and gaze math remain unchanged.
20. Upper-left pre-acquisition did not improve live top-left error and worsened the live maximum, 2-D median, held-out tail, and drift despite improving the vertical median; the original sequence was restored.
21. Posture-departure monitoring is implemented only as a shadow side channel. It does not compensate coordinates or exclude samples, and older calibrations correctly produce `UNAVAILABLE` until a posture-aware calibration is accepted (E-042).
22. The first physical posture-shadow control had complete availability but its only flagged targets were relatively accurate; posture status must remain evidence-only rather than an AOI/scaffold reliability gate (E-043).
23. The eye-local candidate changes only the two raw averaged iris features and is explicitly bound to its calibration mode; it has passed local invariance/compile tests but has no accuracy evidence until its prospective pair is collected (E-044).
24. Two prospective eye-local pairs substantially improved vertical calibration and live accuracy, supporting retention on the A56; typical horizontal error remains too large to assume word-level compatibility without direct reading validation (E-045).
25. Direct known-target reading validation does not support exact-line or word-level claims: guided reading was 40.5% exact-line and word fixation was 10.9% exact-word, with a repeatable top/bottom center-compression pattern inside the run (E-046).
17. The next controlled intervention uses a predictable row-major order and a fixed-center contracting hit-marker, with temporal/spatial order coupling documented as a tradeoff (E-031).
18. Conversation-era commits did not change mapper/filter mathematics, and the replicated ON/OFF control found no material FPS, sample-count, or accuracy recovery with detailed telemetry disabled (E-032, E-034).
19. Detailed telemetry materially increases artifact size, but it is not supported as the cause of the observed gaze-accuracy regression; keep it OFF for leaner routine runs rather than as an accuracy fix (E-034).
20. The current handheld build can produce markedly better accuracy than the earlier diagnostic sessions, so repeatability across an independent session must be checked before treating the July path as regressed (E-034, E-035).
21. The independent-session run retained the typical-error improvement but reproduced severe regional tails; healthy FPS/samples and low calibration dispersion rule out simple throughput or noisy-fixation failure for that session (E-036).
22. Top-left was the worst vertical live target in four of the five recent valid runs, making it the clearest recurring regional failure for the next bounded analysis (E-034, E-036).
23. Across those five pairs, top-left was also the worst calibration LOO target in every run, while mapper conditioning and held-out validation did not rank the later live tails (E-037).
24. No saved calibration metric reliably selects a good immediate live run. High drift is the only directional warning observed, so it can support a conservative redo/abort rule but not an accuracy claim (E-037).
25. The legacy 64x64 two-eye model is computationally feasible on both tested phones. Periodic face-ROI reuse raises isolated SM-G991B candidate throughput to about 25.97 FPS, but optimized full-screen feature compatibility and prospective accuracy remain unproved (E-057-E-059).
26. A periodically reused coarse BlazeFace eye ROI fails replicated SM-G991B target compatibility, while an official-geometry eye-corner-refined ROI restores target ordering in two runs and retains about 22.88 FPS on the warmed phone. The candidate remains non-authoritative pending A56 replication and a valid calibration path (E-060/E-061).
27. Recursive eye-corner crop feedback does not generalize to the A56: the reference repeats almost exactly across two runs, but candidate horizontal/vertical patterns do not, and cached horizontal agreement remains weak. This motivated the detector-anchored, non-recursive size-refinement test recorded in E-063 (E-062).
28. Detector-anchored size-only refinement also fails two A56 target checks. Both fresh and cached subsets are unreliable and the candidate target pattern does not repeat, so the next candidate must recover eye-corner centring in one detector-anchored step without allowing cached outputs to recursively move their own crops (E-063).
29. Detector-anchored one-step centre/size re-extraction is implemented only in the shadow and preserves the authoritative pipeline. Its geometry/build/data-preservation checks pass, but it has no physical compatibility evidence until the replicated A56 gate is completed (E-064).
30. One-step re-extraction preserves intended horizontal/vertical target ordering in two A56 runs and remains near camera rate. Its weaker second-run agreement with a vertically weak reference prevents a production claim but does not block an exact-build SM-G991B replication (E-065).
31. The same one-step build fails replicated SM-G991B horizontal geometry despite complete re-extraction and strong vertical ordering. The current BlazeFace/periodic-crop hybrid branch is rejected as non-generalizable and must not control gaze or remain active during ordinary reading tests (E-066).
32. The official compact 468-landmark face graph is not itself the SM bottleneck: isolated CPU inference is 3.09 ms median and 3.58 ms P95 (E-067).
33. Compact face-mesh eye corners followed by the existing iris model restore strong, repeated horizontal ordering on both SM-G991B and A56. This clears the bounded geometry gate but not full-pipeline throughput on SM; no candidate calibration or accuracy claim follows yet (E-068).
34. Equivalent bulk float packing materially reduces compact-candidate SM work and raises sustained candidate-only throughput beyond the 15-17 FPS reference tier, clearing the bounded performance gate without changing candidate geometry (E-069).
35. The first full-screen compact-candidate calibration misses the provisional all-target spatial reference despite accepting every target and preserving useful raw target order. Its held-out errors indicate a broad leftward shift and lower-region vertical misses rather than only a first/top-left failure, so it remains isolated pending one unchanged SM replication (E-070).
36. The unchanged SM replication is much better typically and sustains about 28 FPS, but the worst held-out error remains about 2.1 lines and the target-wise error direction changes between runs. This supports one isolated A56 generalization gate, not integration or a repeatable accuracy claim (E-071).
37. The first A56 compact calibration reaches approximately 30 FPS but has weak vertical grid ordering and severe LOO/held-out tails. It is an adverse accuracy result retained for one unchanged replication, not evidence to integrate or tune the candidate (E-072).
38. Two further unchanged A56 runs retain approximately 30 FPS but reproduce severe full-screen errors, including 6.80- and 6.45-line held-out maxima. The compact architecture improves speed but fails replicated cross-device accuracy and must remain non-authoritative (E-073).
39. Translating every calibration feature by the complete repeated-target drift vector improves typical held-out vertical error in many sessions but fails to control vertical and 2-D tails. This fixed global correction is rejected and must not be tuned against the same records; it does not test measured-pose or time-dependent compensation (E-074/E-075).

## Open Questions

- What is the complete current path from camera frame to `GazeProvider` output?
- Which transformations or processing stages can be observed independently?
- Are the historical errors still reproducible with the rebuilt calibration flow?
- Is any observed failure caused by gaze estimation itself or by a downstream coordinate or AOI interpretation?
- Which uncertainties cannot be resolved without additional instrumentation?
- Does the upright/mirror transformation align with physical A56 screen direction in practice?
- What are the per-eye feature ranges and dispersions at each target?
- How much error is introduced or removed by mapping, temporal filtering, and drift correction individually?
- What is the actual frame-capture-to-gaze-output latency and its variance?

## Decisions Resulting From Diagnostics

1. Revise validation measurement before modifying the gaze estimator (E-029).
2. Use vertical error in rendered article line-heights as the primary spatial reading metric while retaining signed horizontal error and total 2-D error (E-029).
3. Treat 1.2 line-heights as a provisional all-target spatial reference, not a validated reading pass threshold (E-025, E-026, E-029).
4. Do not allow session median alone to produce a favorable assessment; report and consider every target, tail error, region, drift, and coverage (E-022, E-024, E-028, E-029).
5. Prioritize older-adult target predictability for the next controlled comparison by restoring fixed row-major order and replacing continuous pulsing with a one-way pre-sample contraction around a fixed reticle (E-031).
6. Do not use calibration LOO, held-out error, or mapper conditioning to claim that a run will have a small live tail; the five-pair comparison does not support that inference (E-037).
7. The E-037 proposal to make high drift an automatic stopping rule was superseded by later evidence: a 201 px-drift calibration produced the best live result (0.76-line median/1.04 maximum). Keep drift as evidence; do not implement automatic rejection or sample suppression from that rule.
8. E-075 selects native MGazeNet feasibility, not a model promotion. Preserve failed variants and the current comparison baseline; do not equate absence of a successful small correction with proof that improvements are exhausted.

# Gaze Accuracy Work — Task Handoff

## Purpose

This is the concise entrypoint for continuing NewsMead gaze-accuracy work in a new Codex task. Read this file first, then consult `gaze-diagnostics-log.md` only for detailed evidence.

**2026-09-06 reassessment supersedes the earlier freeze-or-replace conclusion.** Read
`gaze-mgazenet-handoff.md` next for the current objective, source-backed candidate,
uncertainties, authorization boundary, and exact first action. Historical next steps below
are not instructions to repeat completed phone runs.

## Current checkpoint

- Date: 2026-09-06
- Branch: `gaze-pipeline-improvements`
- Latest committed gaze checkpoint: `6104d56` (`test(gaze): add compact tracking research evaluation`). Both rejected hybrid/compact paths are retained as isolated evidence; the ordinary gaze path is unchanged and the hybrid shadow is disabled by default.
- Prior diagnostics commit: `79a5ceb` (`feat(gaze): add diagnostic telemetry and session logging`)
- Broad initial diagnosis: complete
- Required final control before estimator changes: complete; telemetry OFF did not materially restore FPS or accuracy
- Current decision: the tested compact implementation (E-067-E-073) and fixed global translation (E-074) remain rejected for integration; this does not exhaust estimator improvements. E-075 records a source/code reassessment selecting native MGazeNet feasibility as the next inquiry, with properly evaluated pose compensation as an alternative. MGazeNet is not implemented or validated on either phone. The 478-point eye-local path remains the comparison baseline, not a certified final research instrument.
- The user handles staging and commits themselves. Suggest explicit Windows CMD commands when requested; do not stage, commit, push, or include private artifacts automatically.

## Project goal and constraints

Improve the existing fully on-device MediaPipe Face Landmarker + iris-relative gaze system enough for gaze-adaptive reading measurement. The thesis's primary contribution is the gaze-adaptive interface and its measurement, not a novel gaze-estimation model.

Current constraints:

- No paid EyeDid dependency.
- No mounted Tobii or laptop-mounted tracker.
- No co-located laptop/WiFi gaze backend for the intended deployment.
- Improve and diagnose the existing system before considering a new model.
- Do not claim reading-line compatibility from spatial calibration alone.
- Do not retain camera images under the current consent conditions.
- Both Galaxy A56 and SM-G991B matter; do not solve the requirement by selecting only A56 or treating a 15-18 FPS floor as user-approved.
- The latest authorization is to prepare this handoff, not to replace the runtime, install an APK, start camera capture, or collect a calibration.

## Historical diagnostic findings (read chronologically)

These numbered observations describe earlier checkpoints, not all current runtime properties.
In particular, the current camera request is 640x480 (upright 480x640), the active feature is
eye-local/width-normalized, and later runs did not establish a persistent regression. The
current constraints and the reassessment above take precedence over historical proposals.

1. The current tracker is substantially worse and less stable than the earlier recorded behavior. Historical A56 vertical error was approximately 0.67–1.01 cm, while recent fixed-phone live vertical medians were roughly 2.3–2.9 rendered line-heights with tails above eight lines.
2. Median-only reporting hid severe regional failures. The new validation reports every target, signed dx/dy, vertical error in line-heights, median/P95/max, threshold counts, worst region, coverage, and drift.
3. Phone stabilization helps typical error but does not eliminate large drift or regional failures.
4. MediaPipe availability and inference execution were not the dominant observed failure: SAMPLE windows had adequate accepted samples, little/no face or blink loss, and roughly 30–31 ms median submit-to-result time.
5. The A56 receives 320×240 CameraX frames despite the previously intended 480×360 profile.
6. Raw two-eye averaged iris features shift over minutes and have weak/overlapping vertical separation, especially in the lower screen region.
7. Mapper soft extrapolation amplifies some catastrophic tails, but offline restoration of the old hard clamp did not recover line-level performance by itself.
8. A single global affine correction is not justified for strongly regional errors and must be evaluated on a separate post-correction run.
9. The latest valid pre-target-change paired artifacts are in `diagnostics-local/2026-08-24/metrics-v2/run-2/`: 16-point LOO vertical median 3.61 lines, held-out median 3.00, vertical drift about -3.23 lines, and immediate nine-point median 2.69 with an 8.17-line maximum.
10. Two natural-handheld telemetry pairs per mode showed comparable throughput and mixed accuracy: calibration FPS averaged 26.54 ON versus 26.25 OFF, nine-point FPS 27.08 ON versus 26.60 OFF, and OFF did not consistently improve samples, dispersion, typical error, tails, drift, or regions. Detailed instrumentation is not supported as the regression cause.
11. Those four valid live runs were much better than the earlier diagnostic set: 0.59–1.38-line vertical medians and 2.54–3.43-line tails. A persistent regression is therefore not established; independent-session repeatability is the next question.
12. The independent August 28 OFF pair produced a 1.55-line live median but a 4.87-line top-left maximum; its calibration held-out median/max was 4.49/7.44 lines despite healthy throughput, samples, and low dispersion. Typical performance is improved, but severe regional tails remain insufficiently repeatable.
13. Across all five recent valid pairs, top-left was the worst calibration LOO target in 5/5 and the worst live target in 4/5. This is a systematic regional/first-target weakness, not a metric that distinguishes good from bad sessions.
14. Mapper conditioning was modest and did not rank the live tails. Held-out maximum also failed to rank them: OFF2 had 7.82 held-out but the smallest 2.54 live maximum, while fresh OFF had 7.44 held-out and the largest 4.87 live maximum.
15. High drift was the only useful directional warning: the three high-drift sessions had 3.08–4.87-line live maxima versus 2.54–3.04 for the two low-drift sessions. It is not sufficient to certify accuracy, and n=5 supports only a conservative stopping rule.
16. A five-coefficient ridge-linear mapper over the four separate eye features looked promising retrospectively but failed two prospective runs: live vertical median/max was 5.74/8.83 and 2.63/4.19 lines, versus 0.76/1.04 for the immediately preceding quadratic run. Same-calibration held-out comparisons were mixed, so the quadratic mapper was restored.
17. A fresh post-rollback calibration/test confirmed `quadratic_average` was active and recovered to a 1.35-line live median and 2.58-line maximum. The calibration was telemetry OFF, while the test was inadvertently telemetry ON despite its typed label; this remains valid as a mapper sanity check but not as an ON/OFF pair.

## What changed during the conversation

The branch began from `f77ce6e`, which already contained the July 24 calibration redesign.

Commit `79a5ceb` added detailed telemetry and JSON persistence. Git confirms that it did not change `GazeMapper`, `FixationWindowFilter`, `DriftCorrection`, `MedianFilter`, or `OneEuroFilter`, and the final MediaPipe two-eye average remained numerically the same. It added per-frame callbacks, UI-thread work, event buffering, and repeated synchronous JSON rewrites, but the completed E-034 ON/OFF control found no material FPS or accuracy recovery when that work was disabled.

Commit `e214eb6` added reading-oriented validation and the researcher-requested older-adult target/order behavior. The 16-point order is fixed row-major; the nine-point order was already fixed row-major. The shared target uses a fixed black ring/reticle and center plus with a saturated red, black-outlined disk contracting over approximately 1800 ms. These target/order changes occurred after the poor E-030 pair and cannot explain that earlier result, but they must remain identical across the next A/B.

After E-034, a centered 3–2–1 pre-run countdown was added to remove the abrupt start. It finishes before the first target and does not alter any target, sampling, estimator, mapper, filter, correction, or ordering behavior. The verified build is installed on the A56.

No uncommitted gaze source changes remained immediately after `e214eb6`; unrelated pre-existing working-tree changes and local diagnostic artifacts remain.

On August 29, per-eye aggregate persistence, legacy CSV compatibility, a small experimental per-eye mapper, and an offline candidate-comparison tool were added locally. After two prospective failures, the experimental mapper/factory/live filters were removed completely. Aggregate-only per-eye calibration evidence and the offline tool remain for research; the live runtime is again the direct quadratic mapper.

## Exact next action

Continue with the bounded **native MGazeNet feasibility audit** in
`gaze-mgazenet-handoff.md`. Verify the public model/version, preprocessing and calibration
contract, usage terms, and Android runtime requirements before proposing an isolated benchmark.
Do not restore the laptop/WiFi backend, request another calibration, or switch the active
estimator at task startup. The failed posture warning flag was not a compensation test; native
MGazeNet was not ruled out by rejection of the laptop arrangement. No MGazeNet gain is established.

## Completed controls and historical decisions

The user requested replication after the adverse first pair rather than treating one failure as conclusive. The two additional pairs are complete (E-049): `vert_on_2` then `vert_off_2`, and `vertical_off_4` then `vertical_on_4`. All four embedded modes are correct; the `_4` labels are valid and must not be renamed or excluded. All ON fits passed. The second ON run improved guided reading but harmed word targeting; the last ON run harmed both on its own samples. The method is not consistently beneficial, rather than universally ineffective.

The authorized next step, a fixed offset-only offline comparison, is also complete (E-050). Each recording uses only its own three reference medians to calculate `offset = median(target_y - observed_y)`, with gain 1 and no sweep/cap/shrinkage. On identical samples, overall within-one-line accuracy changes 75.3->74.8, 70.5->53.7, 71.6->88.7, 83.1->68.6, 48.9->86.5, and 59.2->70.5 percent in chronological order. However, top-left within-one-line falls from 100% to 0% in three recordings, including one with a large aggregate gain. Do not deploy this simple offset or tune it against these answers.

The next authorized offline screen is now complete (E-051): compare the current six-term vertical polynomial with `1 + zy + zy^2`, retaining ridge 1, training-fold standardization, float precision, 2.5-z soft extension, and unchanged x mapping. All 11 available complete eye-local calibrations are included, including the calibration replaced at September 3 01:12. Baseline replay matches all 231 logged x/y coordinate pairs exactly. The candidate worsens LOO maximum in 11/11 and held-out maximum in 8/11; no session improves both. Do not implement or request a phone run for this candidate. See `docs/gaze-vertical-mapper-screen.md` and its aggregate JSON.

The upstream audit is now complete (E-052). Both activities use the same camera source and eye-local formula; calibration robustly aggregates raw features while reading adds temporal filters. All 11 calibrations have ordered top-to-bottom row medians in each eye. Sparse latest-pair debug probes do not identify a reliable alternative eye feature or exclude every filter effect. Crucially, code inspection and a user-approved idle calibration-screen inspection establish a missing screen-origin conversion: the window frame is `[0,101][1080,2340]`, with a `CalibrationView` at local `(0,0)` of size `1080x2239`. Its saved targets omit that 101-px origin. The screen was closed without pressing Start, and the saved 16-point calibration still matches the latest September 3 21:00 record. No new calibration file was created.

The user subsequently approved that bounded repair and it is implemented, verified and installed (E-053). All four on-device checks pass after unlocking; the calibration frame is still `(0,101,1080,2239)` with physical display `1080x2340` and converted center `(540,1220.5)`. Reading's root is `(0,0)`; a separate test window at screen `(91,272)` verifies clipping, scrolled line/word AOIs and local dot drawing. The legacy-calibration warning was opened and closed without starting a test. All 105 pre-repair gaze/calibration file hashes matched the pre-install snapshot.

The requested post-repair evaluation is complete twice on the A56 (E-054). Pair 1 (`coords_v1_tel-off_cal_1` / `coords_v1_off_reading_1`) had high 332 px calibration drift and reading validity/within-one-line accuracy of 91.8%/36.5%, with 2/4-line median/P95 error. Pair 2 (`coords_v1_tel-off_cal_2` / `coords_v1_off_reading_2`) had non-high 45.6 px drift and 100%/85.4% validity/within-one-line accuracy, with 1/2-line median/P95 error. Both completed artifacts and their calibration hashes are preserved under `diagnostics-local/2026-09-04/coords-v1-a56/`; pair 1 may not be discarded. This spread prevents a repeatability claim while preserving pair 2 as the strongest current post-repair A56 reference.

The standalone compact-candidate calibration gate is implemented and its first two SM-G991B runs are preserved as E-070/E-071. All 16 fit and five held-out targets were accepted on their first attempts in both runs. Run 1 held-out signed errors were centre -161/-27 px, top-left -185/+250 px, top-right -252/-19 px, bottom-left -82/+264 px, and bottom-right -114/+208 px. Run 2 improved its held-out vertical median from 1.75 to 0.83 lines and 2-D median from 253 to 100 px, while its vertical maximum remained 2.09 lines. Its errors were centre -103/+180 px, top-left -8/+77 px, top-right -23/-97 px, bottom-left +36/-249 px, and bottom-right +2/-99 px. The raw fit patterns repeat substantially, but the held-out error pattern does not; this is not a stable accuracy improvement yet. Neither artifact read or wrote the calibration store, and the real calibration hash remained unchanged.

E-074 closure: the compact research harness is checkpointed at `6104d56`, and the fixed
end-pose translation screen is complete and rejected. Do not tune fractions/caps/axes against
its held-out answers. The earlier inference that this required freezing the system or abandoning
all small improvements is withdrawn by E-075. Keep the current pipeline unchanged during the
next feasibility audit; ordinary calibration repeats alone are not an accuracy intervention.

These analysis tasks did not stage or commit anything; the user committed the previous reading checkpoint separately. Do not claim reading accuracy from the mapper screen: reading logs lack raw input for that replay. Also do not claim exact-word offset replay: v4 lacks glyph geometry. The offset analyzer reproduces the original app assignments; E-052 now qualifies their interpretation as the behavior of an app with a coordinate-contract defect, not grounds to discard or silently rescore historical recordings.

The notes below summarize earlier controls and are not requests to repeat them. Current decisions above supersede their historical next steps.

The posture-shadow control is complete. Keep its fields for research evidence, but do not suppress coordinates or connect the flag to AOI/RSI/scaffold decisions: all 145 live samples were assessable, but the 31 flagged samples occurred only at bottom-center/right, whose vertical errors (1.58/0.66 lines) were substantially smaller than the unflagged 4.20-line maximum. This run therefore does not validate the binary flag as an unreliability gate.

Keep `eye_local_width_average_v1` active. Do not request another calibration-grid repeat merely to improve the score, and do not apply the second run's affine candidate: its leave-one-out 2-D estimate is 117 px versus the measured 126 px median, a marginal expected gain with mixed regional horizontal signs.

The next validation should test the retained candidate in the actual reading context, especially line assignment and whether the remaining 126–209 px typical horizontal error is acceptable for the intended word/line scaffolds. The two grid runs establish a substantial and repeated vertical improvement but do not by themselves establish reading-line or word-level compatibility.

The candidate replaces each eye's single image-axis eyelid fraction with a roll-aware eye-local iris displacement normalized by eye width. It retains two-eye averaging, the quadratic mapper, median/One Euro filters, target, timing, sequence, posture shadow channel, and affine layer. Logs identify `raw_feature_mode: eye_local_width_average_v1`; a sidecar binds the saved calibration to that mode so rollback cannot silently use incompatible features.

Keep `QUADRATIC_AVERAGE` active. Do not deploy another mapper from the retrospective ranking alone, and do not add the previously proposed automatic high-drift rejection: the later 201 px-drift quadratic calibration produced the best live result (0.76-line median, 1.04-line maximum), showing that such a rule would reject a demonstrably useful calibration.

The next accuracy intervention requires evidence that is independent of target position. In particular, do not fit face position or scale as calibration coefficients from the existing fixed sequence, because posture drift can be correlated with row-major target order. Per-eye aggregates may remain in telemetry-OFF logs for research. The implemented pose-departure detector is therefore evidence-only: it records eye-midpoint face centre, eye separation/scale, and roll against a conservative calibration envelope, but cannot alter coordinates, filters, the mapper, AOI, scaffold decisions, or sample inclusion.

The rollback sanity check is complete; do not request another run merely because its nine-point telemetry checkbox was ON. It explicitly confirmed the quadratic mapper and no affine correction. Its 1.35/2.58-line median/max was materially better than both per-eye runs, though worse than the preceding unusually strong 0.76/1.04-line quadratic result.

The upper-left-practice intervention is complete and rejected. Its live median improved from 1.35 to 0.96 lines, but top-left worsened from 2.52 to 2.78, the maximum worsened from 2.58 to 3.53, the 2-D median worsened from 215 to 343 px, and calibration held-out median/max worsened to 5.72/10.12 lines with 850 px drift. The original single-center-practice sequence was rebuilt, retested, and installed. Do not repeat this intervention.

At that historical rollback, the app-private `calibration_16point.csv` still contained the
intervention calibration. This is not a current device-state claim. Installing a rollback does
not rewrite calibration data; verify present hashes before future device work and never replace
calibration automatically from tooling.

Both modes must retain lightweight evidence:

- Run/mode label
- Point target and accepted estimate
- dx/dy and reading-line metrics
- Retained/raw sample counts and dispersion
- Lightweight FPS value or summary
- Drift and validation summaries

Telemetry OFF must omit per-frame source/pipeline arrays and avoid repeatedly serializing the large detailed event history. Telemetry ON preserves current instrumentation.

Control result:

- Included: two correctly labelled/embedded ON calibration-test pairs and two OFF pairs.
- Excluded: `handheld-tell-off_cal_redo-1` plus its following test because the calibration embedded `detailed_on` while the test embedded `detailed_off`.
- Decision: modes were comparable; telemetry OFF produced no material recovery. Close the instrumentation diagnostic, but defer any July 24 regression comparison until the fresh-session check establishes whether the current improvement is repeatable.

Fresh-session result:

- Included pair: `handheld_fresh_off_cal_20260828_1` plus `handheld_fresh_off_test_20260828_1`.
- Calibration: 25.81 FPS, 20/14 median raw/retained samples, 1.29-line LOO median, 6.24-line LOO tail, 286 px drift, and 4.49/7.44-line held-out median/max.
- Live: 25.61 FPS, 19/12 raw/retained samples, 1.55-line median, 4.87-line tail, 4/9 within 1.2 lines, no correction.
- Decision: typical improvement partly repeated, but severe tails returned. Investigate calibration repeatability/region geometry before estimator or mapper changes.

Offline result:

1. No saved calibration metric reliably predicted the live tail across the five pairs.
2. Mapper conditioning and held-out validation were not useful selectors.
3. High drift was directional but imperfect in those five pairs. Later evidence invalidated automatic high-drift rejection: the 201 px-drift run had the best live result. Keep drift as evidence, not an automatic stopping or sample-suppression rule.
4. Top-left was systematically weak in both calibration LOO and live testing; fixed row-major order confounds screen region with first-target timing.

## Files to read next

1. `docs/gaze-accuracy-handoff.md` — this checkpoint.
2. `docs/gaze-mgazenet-handoff.md` — current next-task brief; read before acting on any historical proposal.
3. `docs/gaze-diagnostics-log.md` — evidence registry through E-075; E-075 is a reassessment, not a new measurement.
4. `docs/calibration-design.md` — calibration and validation behavior; newer entries and code supersede historical descriptions.
5. `docs/build-context.md` — architecture history and deployment constraints; laptop rejection does not establish native MGazeNet infeasibility.
6. `docs/gaze-model-replacement.md` — historical alternatives/proposal only; custom training and content-aware estimation are not authorized next steps.
7. `docs/gaze-offset-replay.md` and `docs/gaze-vertical-mapper-screen.md` — completed narrow offline screens, parity, and limitations.
8. `docs/gaze-coordinate-audit.md` — historical coordinate defect; the approved E-053 repair is already implemented.

## Verification state

- E-066: the exact E-064 build fails two SM-G991B horizontal target checks. Candidate/intended-target H/V is 0.154/0.939 then 0.733/0.980; candidate horizontal/vertical inter-run repeatability is 0.513/0.905. All 70 detector-frame re-extractions succeed, so the failure is not missing inference. Dual-pipeline FPS is 15.50/14.88 and is not a candidate-only speed measurement. Both artifacts are preserved locally with SHA256 `9434d18ebb35ccd286964ffcfe1ba851f399abca4065475d9a5b9e7d7b752bac` and `f6df3939ab9df4cc83364ebb7571c96ede73995d30684ad71423fee7dcf6aef3`; calibration remains `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`. The current crop branch is rejected and the ordinary-runtime shadow switch is now false; the harness is retained for the separately gated compact-landmark architecture.
- E-065: two A56 telemetry-OFF target-linked re-extraction sessions save 173/179 samples at 28.76/28.38 active FPS. Candidate/reference target H/V is 0.863/0.922 then 0.535/0.499, while candidate/intended-target H/V remains 0.876/0.965 and 0.825/0.902; the second run's reference/intended vertical correlation is only 0.684. Candidate inter-run repeatability is 0.829/0.903 versus reference 0.948/0.770. Re-extraction succeeds on all 88 detector frames. Both numeric artifacts are preserved locally with SHA256 `6e37125cef9a43f68a1bc0cfc054749b3044d9ddc6baa484899b91fddc4d9d05` and `297a3cc849c934e3af6dd398aa3c3f5855f3ed8c21c94a9f9d88d20cd88ccc9a`; calibration remains unchanged. The candidate passes only to exact-build SM replication and remains shadow-only.
- E-064: detector-anchored one-step centre/size re-extraction is implemented in the shadow only. Detector frames use a first iris pass to derive a bounded official-geometry crop, immediately re-extract once, and cache it for three frames; cached results cannot feed back. Numeric correction evidence is logged and no images are retained. All 75 gaze-focused JVM tests pass. The local and installed A56 APK SHA256 is `ea5475d5705f0a0eb260191c8f8dea62d66e03716f6f74ee8d6d276caf6fa214`; calibration and E-063 hashes match before and after installation. Protected authoritative mapper/provider/filter files remain unchanged. E-065 records the completed physical A56 gate.
- E-063: the detector-anchored size-only build fails two A56 telemetry-OFF target-linked checks. Target H/V agreement is 0.635/0.479 and -0.049/0.396; candidate inter-run repeatability is -0.240/0.229 while the reference is 0.860/0.996. Fresh detector-frame and cached-frame subsets both fail to preserve reliable two-axis geometry. Both artifacts are preserved in app-private storage and as authorized copies under `diagnostics-local/2026-09-06/hybrid-shadow/`, with SHA256 values `49887c7ad453a5687581534bea25e61c93814c023d1c3de84269ab6170db238c` and `7b43cf52673f29372eb63a883e6a134f2d0f45bc61f9e896b9691b2989e6800c`. The calibration hash remains `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`. Size-only refinement is rejected; candidate output remains shadow-only.
- E-062: the exact E-061 recursive-refinement build fails two A56 telemetry-OFF target-linked checks. Target H/V agreement is 0.090/0.634 and 0.653/0.922; candidate inter-run repeatability is 0.703/0.543 while the reference is 0.991/0.991. Cached horizontal target agreement is -0.098 and 0.473. Both failures and their hashes are preserved, their stale-reference coordinate scores are excluded, and the calibration hash remains `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`. The recursive update is rejected. Its detector-anchored size-only replacement passed all 74 gaze-focused JVM tests and assembled at SHA256 `39ff4f8d792c2821ca3d4ff946ad57c570a8a2d630e35f1d1bae63863a2f3d64`; E-063 records its subsequent physical rejection. Protected active-pipeline files remain unchanged.
- E-061: the eye-corner-refined candidate passes two SM-G991B telemetry-OFF moving-target shadows. Run 1 saved 136 samples with 0.904/0.895 target-median H/V agreement; run 2 saved 148 with 0.778/0.967. Its mistyped `g911b` label is retained and the valid run is included. Candidate target patterns repeat at 0.808/0.932 H/V. The two target-linked screens ran the reference and shadow together at 15.19 and 14.47 FPS; they establish compatibility, not a speed gain. A subsequent warm candidate-only preview sustains about 22.88 FPS over 1,080 frames; the user observed 21-27 FPS, Android thermal status was 2, and the battery/skin were 37.4/39.3 C. That isolated preview establishes potential, not production end-to-end throughput. All 74 gaze-focused tests pass, the APK assembles at SHA256 `470f416ee85a3ea38ef94dc59e8e16f850ce150515c726a0991009e6c5743139`, protected active-pipeline files are unchanged, the calibration hash remains `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`, and the app is closed. Candidate output remains shadow-only.
- E-060: the preceding coarse periodic ROI fails two SM-G991B target checks. Target H/V agreement was 0.388/0.800 and 0.190/0.777; the candidate repeated across runs at only 0.251/0.600 while the reference repeated at 0.908/0.854. Fresh detector frames were not better than cached frames in run 1. Both failed runs remain preserved and are not discarded; their old-calibration coordinate scores are excluded.
- E-059: on the SM-G991B candidate-only 640x480 preview, every-frame BlazeFace completed about 17.34 FPS and busy-dropped 42.1% of candidate opportunities. Refreshing BlazeFace every fourth accepted frame while retaining per-frame two-eye iris inference completed about 25.97 FPS over the stable 900-frame interval and busy-dropped 13.3%; the user observed 25-31 FPS. Warm-up/no-face time and a later face-loss interruption are excluded. The candidate emitted no gaze, wrote no calibration/accuracy artifact, and remains non-authoritative. The saved calibration SHA256 remains `77b92423326b912c9eaa30dcdba48d1cb45ba046729bde6370eee1ab575caf17`, and the app is closed. Optimized moving-target raw-feature compatibility is the next gate.
- E-058: the reversible BlazeFace-plus-two-eye-iris shadow initializes and runs independently on the A56 while the 478-point GPU result remains authoritative. A target-linked telemetry-OFF nine-point run saved 198 same-frame samples. Active FPS averaged 29.52; candidate work averaged 17.800 ms (22.396 ms P95); target-median H/V correlations were 0.881/0.916. Candidate target ordering is compatible but its raw range differs, so direct substitution is unsafe. The run used a roughly one-day-old calibration, and its coordinate accuracy is explicitly excluded. All 71 gaze-focused JVM tests pass, the debug APK assembles, the calibration SHA256 remains `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343`, and the app is closed. With explicit user authorization, the numeric JSON was copied to `diagnostics-local/2026-09-05/hybrid-shadow/`; its local/app-private SHA256 is `cb579bc0c50cd410e9bd9ad8f692698a790e0429b8ef23958e7b786681378664`.
- E-057: the temporary 64x64 iris benchmark completed on the A56. Prepared two-eye CPU inference averaged 4.144 ms; reusable crop/preprocess plus two-eye inference averaged 5.851/6.123 ms in two runs. A 120-frame real shadow averaged 10.908 ms added work and differed from the current raw feature by median 0.0133 horizontal/0.0137 vertical (P95 0.0656/0.0507). Central fixation makes the correlation values non-decisive. The benchmark never controlled gaze output or wrote a calibration/accuracy artifact. Its source, runtime dependency, test package, and local/device model copies were removed; the clean runtime graph contains no Play Services LiteRT dependency. All 67 gaze-focused tests pass. The final local and installed A56 APK hashes match at `2a5230601ee8b4bc228ff03631338c8bb567a8a220cfbea4eda922d88a411989`; generated build metadata makes this whole-file hash differ from the historical E-056 artifact. The active calibration hash remained `713adc47a00e84d1c3b346985ace4d13d09b7652a34c7e35e0438761a2d85343` across installation, and the app was left closed.
- E-056: at completion of that experiment, both phones had the final proven bitmap/640x480/GPU build with only four-Hz FPS callback throttling retained. All 67 gaze-focused JVM tests passed; the debug APK assembled; local and both installed APK SHA256 values then equalled `e8d86c7631b1e121143b40abefb901dc50a33a0a274ef57eb050d1a01fb233fa`. The A56 installation was subsequently replaced by the clean equivalent E-057 rebuild recorded above; the SM-G991B was not changed. Temporary direct-buffer, 320x240, and CPU builds were removed. Face-visible throughput was about 15-17 FPS on the SM-G991B and median/mean 18.5/18.73 FPS on the cool A56 (P05 15.2); no-face reached about 30 FPS. Neither phone produced a calibration or accuracy artifact during the performance screen.
- E-055: the Samsung SM-G991B package/version and installed APK hash matched the pre-screen tested build; MediaPipe logs confirmed GPU and 640x480. The initial observation was about 16 FPS with visible lag and severe thermal status. E-056 supersedes its optimization action.
- E-054: both A56 coordinate-repair pairs are archived locally and calibration-hash linked. Pair 1 reading within-one-line accuracy was 36.5% with 2/4-line median/P95 error; pair 2 was 85.4% with 1/2-line error. Both remain required regression baselines.
- E-053: repaired debug app and Android-test APK assemble; 67 gaze JVM tests pass. All four Android tests pass: two isolated-cache storage tests and two real-layout checks, including an inset scrolled-text/dot fixture. The initial locked-phone attempt failed with `NoActivityResumedException`; the unlocked rerun passed all four in 2.052 seconds. Legacy rejection is verified on the actual accuracy screen. All 105 gaze/calibration files match their pre-install hashes after final installation/testing; the saved CSV SHA256 remains `cdc9d4f91f782dece7819a63907d713068d514e33834e2942bb5abe4172728ed`. No real calibration or participant run was started, and nothing was staged or committed.
- For this host's CLI build, use Java 17 at `C:\Users\USER\.jdks\jbr-17.0.14`, `GRADLE_USER_HOME=C:\Users\USER\.gradle`, `-Dorg.gradle.java.home=C:\Users\USER\.jdks\jbr-17.0.14`, and `-Pkotlin.compiler.execution.strategy=in-process`. The explicit in-process option avoids a stale Java 21 Kotlin daemon. No project build configuration was changed.

- The posture-shadow implementation compiles, all gaze-focused JVM tests pass, the debug APK assembles, and it is installed on the A56 with app data preserved (the 52-test full suite has the unrelated existing `FirebaseTest.createAccount` failure).
- E-043 completed the physical shadow evaluation: 145/145 samples assessable, 31/145 flagged only on horizontal face-centre departure, and no relationship between the flag and worse target error. Calibration/test FPS was 20.65/20.40, above the immediately preceding E-041 OFF pair's 18.62/18.07, so no shadow-monitor throughput regression is indicated.
- E-044 implements the eye-local candidate behind one mode switch. Pure geometry tests verify translation/scale/roll and eye-corner-order invariance; all gaze-focused JVM tests and APK assembly pass, and the APK is installed on the A56 with app data preserved. Static diff confirms the mapper, fixation filter, temporal filters, target view, correction, and sequence are unchanged.
- E-045 prospectively validates the candidate. Primary live vertical median/max was 0.62/2.07 lines and the independent repeat was 0.96/1.51, versus 2.76/4.20 in the preceding posture-shadow baseline and 1.35/2.58 in the restored quadratic sanity run. Candidate calibration LOO medians were 0.85/0.73 lines and held-out medians were 0.82/1.03. Retain the candidate, while documenting remaining horizontal bias and the lack of direct reading validation.
- The August 29 quadratic-default rollback also passed all 46 gaze-focused JVM tests, assembled successfully, and was installed over the existing app with data preserved.
- The subsequent quadratic-only upper-left-practice build passed the focused gaze tests, assembled successfully, and was installed with existing data preserved.
- After E-041, the original one-practice sequence was rebuilt, passed the focused gaze tests, assembled, and installed with diagnostic data preserved.
- The complete unit suite has one unrelated existing failure in `FirebaseTest.createAccount`.
- Do not apply the nine-point affine candidate during baseline comparisons.
- Preserve diagnostic JSON locally and keep participant artifacts unstaged.

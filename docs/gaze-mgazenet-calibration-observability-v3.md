# MGazeNet calibration observability v3 software checkpoint — 2026-09-09

Status: the isolated input-only calibration audit is implemented and
host-verified. Authorized setup-only checks passed on both phones using the
corrected exact APK pair, after one retained G991B cross-layout protocol failure
and repair. CAMERA remained denied. No camera, participant calibration or
personal data was used. The work does not modify the active NewsMead tracker or
calibration store or assign an accuracy gate. The real native 16-fold audit path
now passes with synthetic input on both the A56 and G991B. The subsequent
camera-free four-session collection protocol is frozen in
`gaze-mgazenet-calibration-observability-v3-protocol.md`. Its later collection
and analysis are complete; see
`gaze-mgazenet-calibration-observability-v3-analysis.md`.

Read after `gaze-mgazenet-post-pilot-review.md`. This is the current MGazeNet
continuation point.

## Why the 16-point NewsMead procedure is reused—but not its calibration

The v3 audit deliberately reuses the active calibration's spatial procedure:

- one practice target at viewport centre;
- sixteen fit targets in fixed row-major order on the 4×4 viewport fractions
  `.10`, `.3667`, `.6333`, `.90`;
- a post-fit repeat of the nearest-centre fit target (`fit_6`);
- five held-out checks at centre and the four quarter diagonals.

It cannot reuse the active calibration observations or mapper. The active
tracker reduces each target to one robustly aggregated two-value eye-local
observation and fits a six-term quadratic ridge mapper. MGazeNet emits 258-value
features, keeps 45 accepted rows at each target, and fits independent X/Y OpenCV
RBF EPS-SVRs. V3 therefore shares target geometry and validation roles while
keeping the model-specific input, fit and storage paths isolated. It never reads
`calibration_16point.csv` or any active tracker state.

## Frozen v3 collection and audit contract

`CalibrationAuditSession.VERSION` is
`mgazenet_calibration_observability_v3`. Opening its setup Activity leaves the
camera off and creates no record. A run label is required before the explicit
Start action; the 4×4 target order is fixed and has no order selector.

The unchanged MGazeNet eligibility rule accepts a row only when all 258 features
are finite and both eye polygon areas are strictly above 10 pixels. Each target
has a 1,500 ms settle, exactly 45 accepted samples, a 500 ms post-collection
wait, and a 30,000 ms timeout. The practice target never enters training. The
sixteen fit groups therefore contain exactly 720 rows.

Before the final calibration is used, `CalibrationAuditEngine` performs sixteen
leave-one-complete-target-group-out folds. Each fold fits the same fixed X/Y RBF
EPS-SVRs on 15 whole groups (675 rows), then predicts all 45 rows of the omitted
group. No frame-level random split is available. After the audit, a separate
final SVR is fit on all 720 rows.

Post-fit verification is fixed to six blocks in this order:

1. `drift_repeat_fit_6`, at the exact `fit_6` position;
2. `validation_1`, viewport centre;
3. `validation_2`, upper-left quarter;
4. `validation_3`, upper-right quarter;
5. `validation_4`, lower-left quarter;
6. `validation_5`, lower-right quarter.

Each block uses a 3,000 ms settle, 2,500 ms measurement window and 250 ms drain.
The repeat observes within-session drift; it does not refit or correct. The five
validation positions are disjoint from all fit positions. No prediction filters,
bias subtraction, line snapping, target selection or post-hoc correction are
applied.

## Numeric evidence and privacy boundary

Complete and partial records use `mgazenet_calibration_audit_v3` and
`mgazenet_calibration_audit_partial_v3`. The manifest binds the protocol,
pipeline and device identity, physical screen and viewport, exact fit and
verification geometry/order, timings, whole-target audit method, row counts,
unchanged SVR parameters and calibration digest.

The record contains per-target collection counts and rejections, timing and
output-age summaries, one scalar RMS within-target feature-dispersion value,
the 16×45 numeric LOO predictions, and numeric/null post-fit verification
results. It contains no image, landmark, 258-value feature vector or persistent
personal model. Feature dispersion describes input variation only; it is not a
gaze-error metric.

`tools/gaze/mgazenet/calibration_audit_metrics.py` independently validates the
manifest hash and identity, reconstructs every target from the viewport,
requires exactly sixteen 45-row target-isolated folds and all six verification
blocks, and rejects changed clocks, timings, methods, targets or privacy flags.
It reports LOO and post-fit results separately using X/Y, Euclidean and vertical
line-height errors, including target-balanced summaries. It always emits
`accuracy_gate_pass: null` and `promotion_decision: not_evaluated`.

## Camera-free verification

The following checks passed on 2026-09-09:

- all 147 benchmark JVM tests, including 9 new audit engine/session tests;
- compilation of the debug app, JVM tests and instrumentation tests;
- all 49 MGazeNet Python tests, including 8 new audit scorer tests;
- independent Python validation of the complete synthetic JSON emitted by the
  Kotlin report path: 16 target-isolated folds and 6 verification blocks;
- debug and instrumentation APK assembly;
- `git diff --check`.

The signed pre-repair artifacts used for the A56 setup check were:

- app APK SHA-256
  `7af28170f07e6fe4fcd661b8489b5fa329e6bdda129d25f7ea218bb53b3465d4`;
- instrumentation APK SHA-256
  `1557e96565df7d72a3d5cd3eecc5a5b753da543aedb76e8a6b846f0f5a350899`.

The read-only native package audit still reports the already documented
MediaPipe `libimage_processing_util_jni.so` 4 KB alignment exception, so the
package as a whole cannot claim 16 KB page-size compatibility. The required JNI
exports remain present and strong C++ imports resolve. V3 did not alter native
dependencies or treat that packaging observation as an accuracy result.

The synthetic cases cover a well-separated calibration, one corrupted target,
global feature collapse, a successful final-fit record with poor held-out
behavior, target-group omission, malformed row counts, non-finite values,
changed anchors, manifest removal and report identity changes. The setup-only
instrumentation test was compiled here and later executed only within the
camera-disabled A56 boundary below.

## A56 camera-disabled setup check

After separate authorization, the exact signed APK pair above was installed as
an update on the connected A56 (`SM-A566B`, serial `R5CY40Y2C5W`) on 2026-09-09.
The first install attempt used a newly generated temporary debug key and Android
rejected both APKs with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; neither package was
changed. The same source was rebuilt with the established user debug key, after
which both `install -r` updates succeeded. The existing app was never
uninstalled, so its retained private data was not deleted.

Before the check, CAMERA had runtime grant `false` and effective app-op
`ignore`. Only `AccuracyHarnessTest` ran. All four tests passed in 2.976 seconds:

- opening the v3 calibration-audit setup kept the screen-on session flag clear,
  exposed no order selector and created no calibration-audit record;
- the artificial measured-layout check retained 16 distinct row-major fit
  positions, exact `fit_6` repeat geometry and five validation positions with no
  fit overlap;
- the existing v2 accuracy and input-only setups also remained camera-off and
  created no record;
- physical-screen coordinate conversion and drawn-target acknowledgement still
  passed on the device layout.

No test pressed Start or requested CAMERA. Afterward, CAMERA was again explicitly
revoked and set to `ignore`; the runtime grant was confirmed `false`. The app and
test package were force-stopped and the benchmark process was confirmed absent.
The app-op display retained an older historical `allow` timestamp/duration from
the previous authorized pilot; it did not advance during this setup check.

## G991B setup check and retained protocol repair

The same pre-repair APK pair was then installed as an in-place update on the
G991B (`SM-G991B`, serial `R5CR60YSJ2X`). CAMERA was first confirmed with runtime
grant `false` and effective app-op `ignore`. The first `AccuracyHarnessTest` run
retained a real failure: 3/4 tests passed, while the artificial geometry test
reported expected `fit_6` but actual `fit_10`. No record was created and no test
pressed Start.

The cause was confined to the isolated v3 controller. It selected the repeated
target by the smallest physical-pixel distance among four mathematically tied
near-centre grid points. Floating-point/layout differences broke that tie
differently on the two phones. `CalibrationAuditSession` now freezes the
row-major first candidate explicitly as `DRIFT_FIT_ID = "fit_6"`; an invariant
also binds it to grid entry 6. The report and offline reader already required
that identity. The active NewsMead tracker was inspected because its procedure
motivated this check, but it was not modified.

After the repair, all 147 JVM tests and all 49 Python tests passed again, the
Kotlin synthetic report again passed independent Python validation, and both
APKs assembled. The corrected signed artifacts are:

- app APK SHA-256
  `2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`;
- instrumentation APK SHA-256
  `1557e96565df7d72a3d5cd3eecc5a5b753da543aedb76e8a6b846f0f5a350899`.

The corrected app updated in place and the unchanged test APK remained valid.
The same four setup-only tests then passed in 2.629 seconds. The v3 setup created
no record, retained the fixed 16-point order, exact `fit_6` repeat and five
disjoint validation positions, and never requested CAMERA. Afterward, CAMERA
was explicitly revoked and set to `ignore`, its runtime grant was confirmed
`false`, both packages were force-stopped and the benchmark process was absent.
The older app-op `allow` timestamp/duration did not advance.

## Corrected A56 exact-build parity check

Because the first A56 pass used the pre-repair app, the corrected app and
unchanged instrumentation APK were installed in place on the A56 under a final
camera-disabled authorization. Their hashes exactly matched the corrected pair
recorded above. CAMERA was confirmed with runtime grant `false` and effective
app-op `ignore` before the test.

All four `AccuracyHarnessTest` cases passed in 2.984 seconds. This independently
confirmed the deterministic `fit_6` repeat, 16 fit positions, five disjoint
validation points, physical-screen conversion, camera-off setup behavior and
absence of a newly created record on the A56 layout. No test pressed Start or
requested CAMERA. CAMERA was revoked and set to `ignore` again afterward, the
runtime grant remained `false`, both packages were force-stopped and the
benchmark process was confirmed absent. The historical app-op `allow` record
did not advance.

## Camera-free native SVR audit checkpoint

The setup checks do not execute the expensive v3 fit path. Before any personal
calibration, a separate deterministic instrumentation test was therefore added
to run the real OpenCV implementation rather than the fake JVM regressor. It
constructs exactly sixteen groups of 45 finite 258-value synthetic rows and
checks every native operation used by v3:

- sixteen folds are created in fixed `fit_1` through `fit_16` order;
- every fold receives exactly 675 rows from the other 15 complete target groups;
- the omitted group marker is absent from every corresponding training set;
- both native SVRs train in every fold and emit 45 finite two-value predictions;
- the final native SVRs train on all 720 rows and emit finite predictions;
- all copied features and labels are zeroed after the test.

The app APK remains
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
The instrumentation APK containing `CalibrationAuditNativeTest` is
`b3d1e229f9b181faab93e503dd2ffa10870cf5dae3d5f6699f6fd0380c43621f`.
All 147 JVM tests and 49 Python tests passed before assembly.

On the A56, the single native test passed in 5.338 seconds. The sixteen-fold
audit itself took 4,899.515 ms and the final 720-row fit took 316.651 ms. These
are descriptive execution timings, not a speed or accuracy gate. CAMERA had
runtime grant `false` and effective app-op `ignore`; the test used no camera,
image, landmark, participant feature or personal model and wrote no evidence
record. CAMERA was re-denied afterward, both packages were force-stopped, and
the benchmark process was absent.

The same exact app and instrumentation APKs were then installed in place on the
G991B. CAMERA had runtime grant `false` and effective app-op `ignore` before the
test. The same single native test passed in 3.608 seconds; the sixteen-fold audit
took 3,300.128 ms and the final 720-row fit took 226.245 ms. It exercised the
same complete-target omissions, finite predictions, final fit and cleanup
assertions, with no speed gate. It used no camera, image, landmark, participant
feature or personal model and wrote no evidence record. CAMERA was explicitly
revoked and set to `ignore` afterward, both packages were force-stopped, and the
benchmark process was confirmed absent. The historical app-op `allow` entry did
not advance. This completes the camera-free native-execution checkpoint on both
phones.

## Files in this checkpoint

- `CalibrationAuditEngine.kt`: pure whole-target-fold audit and memory cleanup;
- `CalibrationAuditSession.kt`: fixed 16-point collection and six checks;
- `CalibrationAuditReport.kt`: hashed numeric-only v3 evidence;
- `CalibrationAuditActivity.kt`: explicit-start isolated UI;
- `AccuracyCameraSource.kt` and `SvrCalibration.kt`: audited fit path using the
  unchanged SVR implementation;
- `BenchmarkActivity.kt` and the benchmark manifest: camera-off setup entry;
- JVM/instrumentation tests under `mgazenet-benchmark/src/test` and
  `mgazenet-benchmark/src/androidTest`;
- `calibration_audit_metrics.py` and
  `test_calibration_audit_metrics.py`: independent validator/scorer and tests.

## Authorization boundary and next decision

The software checkpoint and corrected exact-build camera-disabled setup checks
on both phones are complete, as is the same-build camera-free native SVR audit
on both phones. Do not grant CAMERA, run a personal calibration, collect a v3
record, change a threshold, promote MGazeNet or modify the active tracker
without a new explicit authorization. The v3 protocol is now frozen, without
contacting a phone or starting a camera run at this software checkpoint. The
four sessions were later separately authorized and completed. Their analysis
rejects the LOO diagnostic as a quality gate; it authorizes no further camera
run or active-tracker change.

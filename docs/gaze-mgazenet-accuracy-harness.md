# Isolated accuracy harness checkpoint — 2026-09-07

The stationary-target harness is implemented in the separate
`com.newsmead.mgazenetbenchmark` app. **No real-camera accuracy session or personal
calibration has run. No MGazeNet gaze-accuracy claim is established.** The active
NewsMead tracker, its calibration store and reading behavior remain unchanged.
The user handles staging and commits; all earlier uncommitted work is retained.

Follow-up: `gaze-mgazenet-protocol-review.md` reviews what this instrument can
measure, fixes host-side manifest/geometry validation, and specifies the next
input-only preparation. At this 2026-09-07 checkpoint the v2 repeat/order pilot
was still proposed and had not been implemented or run.

Input-check follow-up: `gaze-mgazenet-input-check.md` records the completed
software implementation, the retained first SM-G991B software failure and its
retained next-frame failure. The complete repair then passed its exact on-device
regression and full 20-second input check on both phones. No personal calibration
or accuracy trial occurred; read the input-check checkpoint for timing, drops,
eligibility and the A56 warm-up gap.

Stationary-v2 follow-up: `gaze-mgazenet-stationary-v2.md` records the new
ten-location/two-sweep protocol, neutral presentation text, strict report
metadata and four-age offline sensitivity analysis. That software passes host
verification and retains frozen v1 scoring. Its later camera-free A56
and SM-G991B setup/geometry checks both pass for the same hashed APK pair. No
personal calibration or accuracy trial has run.

## Implemented behavior

- The v1 benchmark launcher opened a separate accuracy setup screen. Opening setup
  does not start a camera, initialize a personal calibration or create a report.
  Camera permission/start requires the explicit session button. Run label and
  protocol-defined analysis age limit are required; no numeric study threshold
  is filled in automatically.
- `AccuracySession` is a pure state machine. It requires acknowledgement of the
  target view's draw callback before settling/sampling. Calibration uses one
  separate practice target, then 13 fit targets, 1.5-second settle, exactly 45
  eligible features per point and .5-second post-collection wait. Practice
  features are discarded; only 585 fit rows can reach the two SVRs.
- Admission checks feature length/finiteness and both source pixel-polygon eye
  areas strictly above 10. A calibration target has a 30-second deadline; failure
  is retained instead of silently skipping a target or relaxing the rule.
- Model/SVR operations run on one worker. Training uses normalized **physical
  screen** labels, including the target view's actual origin. Personal training
  arrays are erased after fitting/cancellation; no calibration model is saved
  or loaded, and another session must start fresh.
- V1 validation used eight held-out points, a fixed 3-second settle and 2.5-second
  window per target. Empty targets remain. There is a 250 ms drain interval;
  delayed outputs are routed by capture timestamp to their original block,
  including final callbacks drained while the source closes. No interpolation,
  filtering, snapping, affine correction or estimated-gaze dot is used.
- Capture uses the pinned Tasks/MGazeNet/OpenCV path with the original four-thread
  normal-precision inference method. The performance candidate was not promoted.
  The source requires Camera2 `REALTIME` timestamps compatible with elapsed
  realtime; unknown/incompatible clocks stop the run instead of substituting
  arrival timestamps as capture time. Source dimensions/rotation and screen
  geometry must remain unchanged throughout a calibration/session.
- Stop, backgrounding, layout change, clock errors, initialization errors, fit
  errors and the overall 10-minute deadline close the isolated source and retain
  an incomplete numeric record. Complete and incomplete schemas are distinct.
  The evaluator refuses an incomplete record as a completed accuracy trial.

## Protocol adaptations and interpretation

The public source's [grid generator](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/gazefollower/misc/__init__.py)
uses a five-by-nine grid with fixed normalized margins derived from 1920×1080.
Its calibration/validation indices and grid fractions are retained, but applied
to the measured Android viewport. Held-out Y positions are moved to the centers
of actual rendered text lines and X positions constrained to their text bands.
The harness rejects duplicate held-out positions and exact fit/test coordinate
overlap. These are explicit adaptations, not a claim of identical upstream
target placement or an approved final participant protocol.

The text uses measured 20sp paint geometry and line pitch in the harness view.
Line bounds are actual physical-screen text bands. They are not glyph/word AOIs,
and this screen is not the NewsMead article layout. A stationary red target is
shown against text during validation; calibration uses the target without text.
No current production target behavior is changed. A future baseline comparison
must deliberately match presentation and calibrate each estimator compatibly.

The stored onset is a draw-callback timestamp, not a measured photon/scanout
timestamp. Output time is main-callback delivery, including queue delay. The
camera clock declaration is checked before accepting frames. These definitions
support the declared stationary windows; they do not establish sub-frame timing
accuracy, fixation detection, dwell or regression validity.

The fixed eye-area rule remains resolution dependent; source parity does not
validate it on real eyes. Tasks localization remains an explicitly unvalidated
adaptation of legacy refined FaceMesh. See the accuracy protocol/readiness notes
for these unresolved input and measurement issues.

## Export and provenance

`AccuracyReport` writes only numeric JSON and its SHA-256 under a new
`files/accuracy/<timestamp_uuid>/` directory in the separate app. No images,
crops, landmarks, feature vectors or personal SVR models are written. The
record includes every planned test block, accepted/rejected calibration counts,
raw calibrated test coordinates or explicit null results, capture/delivery
timestamps, reasons, camera arrivals/busy drops and closure errors. CameraX
frames never delivered to the analyzer remain explicitly unobservable.

The calibration manifest binds protocol, device, installed APK hash, vendor/model
identities, actual camera shape/rotation, localizer/delegate, screen/viewport,
label convention, eligibility, timing, absence of filters/correction, fit target
positions and a digest of fit features/labels. The canonical manifest JSON is
retained with its hash so the Python evaluator can verify it without assuming
identical cross-language floating-point JSON formatting.

The v1 report also retained the canonical pipeline JSON. The independent scorer
checks its SHA-256 and exact equality with the calibration manifest, then checks
duplicated protocol, device, coordinate, timing, geometry, fit-count and
data-retention fields. Harness manifests are mandatory for recorded evidence;
fit/test positions were reconstructed from the declared v1 layout and protocol.
Recorded evidence must identify the pinned MGazeNet and
Tasks localizer asset hashes. Signed X/Y summaries retain both error tails instead
of allowing a near-zero median to hide opposing regional bias.

In v1, only completed records use `mgazenet_accuracy_v1`. Stopped/failed records use
`mgazenet_accuracy_partial_v1` and keep all planned blocks, including unshown
blocks with null timing. They must remain in the study record; they are not
silently converted into successful low-coverage trials or discarded.

`collect_accuracy.py` retrieves only a named numeric report and hash from this
package, checks session identity and preserves bytes in a new local directory.
It never launches anything or reads the main app's data. Participant record
collection still requires authorization. `collect_synthetic.py` remains limited
to its original synthetic modes.

## Verification

- All **130 benchmark JVM tests pass**, including ten controller/export tests.
  They cover exact fit row counts, practice exclusion, real screen origins,
  draw/settle admission, invalid samples, source-buffer reuse, empty windows,
  delayed routing, cancellation during fitting and clock rejection.
- All **37 host tests pass**, including required manifest/pipeline integrity,
  reconstructed target geometry,
  signed error tails, partial-record rejection and numeric collection integrity.
- A full **invented** session exported by Kotlin was accepted and scored by the
  independent Python evaluator: eight planned blocks, one deliberately empty.
  Its intentionally sparse data produces 1.4% target-balanced fresh correct-line
  availability at an illustrative 100 ms limit. This is a cross-language software
  check, not a human or phone accuracy result.
- The final benchmark and instrumentation APKs build successfully.
- **A56: both camera-free Android tests pass**: setup
  creates no calibration record; a deliberately inset target view uses physical
  coordinates and acknowledges the drawn target. CAMERA remains ungranted;
  app-ops reports CAMERA ignored. No camera or calibration Start was invoked.
- **SM-G991B: the same two camera-free setup/coordinate checks pass.** CAMERA
  remains ungranted and app-ops reports CAMERA ignored.
  Both Activities were closed by their tests; no personal record was created.

Final benchmark APK SHA-256:
`e2aff9a1c61622bbf1eccca571455379688fece48cf21f90a79e441da32f6865`.
Instrumentation APK SHA-256:
`c4a2ee729a1d00bbeafd4509423308ec7a8c8e33ce814d72447dded376f39a32`.
Both-phone camera-free checks used benchmark APK
`272ac2c20b2278523a841cf000514ee4901d3215fef33e9fd4698e84b2ed44ac`.
The final APK differs only by the canonical pipeline field in numeric export;
that export-only revision was rebuilt and host/JVM verified, but was not installed
or rerun on either phone. The earlier A56 setup test used APK
`d8e491854637c0ca8e26d7d2c4454e9721907b5fcc61784eb8556db7a7e1f9d1`;
subsequent changes were readable calibration labels and bundled attribution, and
both camera-free checks were repeated successfully on APK
`272ac2c20b2278523a841cf000514ee4901d3215fef33e9fd4698e84b2ed44ac`.

Evidence is retained under
`diagnostics-local/2026-09-07/mgazenet-accuracy-harness/`, with test logs,
invented Kotlin exports, independent Python results and checksums. The one initial
build failure was an Android test's missing generic type argument; it was fixed
before passing builds/tests. A later build attempt was stopped by usage-limit
approval review and succeeded after the user resumed; it was not a code failure.

## Next work and handoff boundary

Both-phone setup/coordinate verification is complete. This implementation is
ready for the user to checkpoint before proceeding to input/accuracy evaluation.
No further framerate experiment is implied. Do not repeat these successful
checks unless relevant code changes or a new failure justify it.

The remaining substantive questions are licensed-image/localizer characterization,
real camera-clock and crop/eligibility behavior, agreement on the participant
protocol and analysis limits, and fresh independent gaze accuracy on each phone.
Camera use and personal calibration have not been authorized by this checkpoint.
Do not press Start, request calibration repeats, or promote the estimator from
these software checks. Natural-reading outcomes and the main app's baseline
comparison still need separately designed evaluation.

After this checkpoint is frozen, routine test execution, evidence collection,
checksum verification and documentation updates are suitable for a lighter
model following these instructions. Retain stronger review for changes to the
measurement protocol, interpreting real errors/coverage/latency, and deciding
whether the gaze signal supports reading-event or intervention claims.

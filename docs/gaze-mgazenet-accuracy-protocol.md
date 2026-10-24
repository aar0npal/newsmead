# MGazeNet accuracy harness contract — 2026-09-07

Status: offline evaluator and synthetic verification implemented; participant
UI, camera collection and personal calibration are not implemented by this
checkpoint. No active tracker changes, phone actions, staging or commits.
Read after `gaze-mgazenet-accuracy-readiness.md`.

**Implementation follow-up:** `gaze-mgazenet-accuracy-harness.md` records the
now-built separate controller, setup/target UI, camera source and numeric export,
plus camera-free verification. The initial status above describes this protocol
preparation checkpoint; no personal accuracy run has occurred.

**Current v2 follow-up:** `gaze-mgazenet-stationary-v2.md` records the isolated
v2 implementation and host verification. V1 remains accepted as frozen
historical evidence. V2 uses ten repeated locations, explicit counterbalanced
sweeps and the fixed 50/100/200/500 ms sensitivity table with no primary
freshness threshold. No personal accuracy run has occurred.

## V2 extension (implemented 2026-09-08)

The tables below freeze the original v1 input and output contract. They are not
silently reinterpreted. V2 uses schema `mgazenet_accuracy_v2`, protocol
`mgazenet_stationary_viewport_v2`, and adds the following required fields:

| Field | V2 contract |
| --- | --- |
| `validation_order` | Exactly `forward_then_reverse` or `reverse_then_forward`; must match the hashed manifest. |
| `analysis_age_limits_ms` | Exactly `[50,100,200,500]`; `max_output_age_ms` is null because no column is primary. |
| manifest `validation_locations` | Exactly `[2,8,13,15,22,24,31,33,38,44]`. |
| manifest `sweep_directions` | The two directions implied by `validation_order`. |
| block `location_id`, `grid_index` | Stable repeated-location identity and its frozen grid index. |
| block `sweep`, `sweep_direction`, `order_in_sweep` | Exact counterbalanced presentation metadata; block IDs are unique across repeats. |

V2 contains twenty ordered blocks and preserves the original 3,000/2,500/250 ms
settle/measure/drain timing. Offline output separates invariant spatial metrics
from the four availability views and reports the `n-1` within-target coordinate
SD, repeated-location signed-bias change, and session/sweep/location and
target-balanced summaries. See the v2 checkpoint for the complete implemented
contract and verification boundary.

## Localizer decision

Treat the existing Tasks-based candidate as an **explicit adaptation**, not an
exact native reproduction of GazeFollower's complete acquisition pipeline.
The pinned GazeFollower alignment enables legacy refined FaceMesh. Its
[release setup](https://github.com/GanchengZhu/GazeFollower/blob/553920edcb7998c029828677f50f6d8eb4a16249/setup.py)
does not pin a MediaPipe version, so that release alone cannot identify one
historical MediaPipe binary/model combination.

Google's [legacy v0.10.21 graph](https://github.com/google-ai-edge/mediapipe/blob/v0.10.21/mediapipe/modules/face_landmark/face_landmark_cpu.pbtxt)
uses a 192×192 input and selects the attention model for refined landmarks.
This is a versioned example of the legacy path, not evidence that GazeFollower
historically used v0.10.21. The current [Tasks documentation](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker)
describes FaceMesh-V2 at 256×256 and single-face smoothing. The shared count of
478 landmarks therefore cannot establish identical localization or temporal
behavior. It also does not prove either pipeline more accurate for gaze.

The local task bundle was inspected without running inference. Its exact hash
and four embedded component hashes are in `gaze-mgazenet-localizer-audit.json`.
It contains `face_detector.tflite`, `face_landmarks_detector.tflite`, geometry
metadata and blendshapes. The benchmark uses Tasks 0.10.29 VIDEO, one face,
detection/tracking thresholds .1/.1, presence .5, GPU with initialization-only
CPU fallback. The active tracker uses LIVE_STREAM and different configuration;
it remains unchanged. These are separate pipeline identities.

A subsequent licensed-image comparison should characterize the adaptation,
not demand bitwise equivalence from different networks. Use identical upright,
unmirrored RGB inputs for a deliberately pinned legacy reference and the Tasks
candidate; test landmark index semantics, all three crop boxes, crop rejection,
eye areas and all 258 downstream features. Preserve no-face and rejected cases.
Include portrait/landscape dimensions, face scale, off-center position and
open/closed eyes. Independent stills test initialization; a declared ordered
sequence is needed to assess tracking/smoothing. Artificial image transforms
do not simulate physiological changes in gaze or supply gaze ground truth.

No suitably licensed image suite has been selected or executed in this
checkpoint. The exact historical legacy package is not recoverable from the
unversioned dependency. Do not keep pursuing a nonexistent exact historical
match or let image-level agreement stand in for eventual held-out gaze accuracy.

## Calibration eligibility: new source-derived evidence

`openness_reference.py` executes the unchanged, hash-checked upstream numeric
alignment methods on synthetic contours. `UpstreamOpennessParityTest` compares
both Kotlin polygon areas and the source threshold decision in 21 cases.
**All 21 pass exactly**, bringing the benchmark JVM total to 120 passing tests.
The previous 89 crop cases did not assert eye-openness values; this closes that
specific arithmetic gap without asserting correct blink detection on people.

For the same normalized synthetic thin-eye contour, the upstream areas and
`both > 10` decision are:

| Frame size | Left area | Right area | Accepted by area rule |
| --- | --- | --- | --- |
| 100×100 | 0 | 0 | No |
| 480×640 | 52 | 45.5 | Yes |
| 640×480 | 0 | 0 | No |

Pixel rounding as well as scale/aspect affects this deliberately small contour.
These are invented geometric inputs, not measured eyes, but they demonstrate
that a fixed pixel-area threshold is not a resolution-invariant validity rule.
No threshold was tuned. The first reference-family calibration implementation
should expose and version the source rule, record rejected counts and dimensions,
and distinguish it from any later validated adaptation. Finite crop geometry
alone cannot certify open eyes or attention to the target.

## Implemented offline evaluator

`tools/gaze/mgazenet/accuracy_metrics.py` scores stationary instructed-point
blocks independently of any estimator. It never fits, filters, snaps, loads a
person's calibration, contacts a device or modifies evidence. It refuses to
overwrite an existing output file. The ten analytical tests cover missing
intervals/targets, off-text coordinates with zero vertical error, adjacent-line
assignment, invalid/stale/late results, regional tails, screen-origin invariance,
fit/test leakage, signed error tails and corrupt input. With the subsequent
cross-field provenance and target-geometry checks, all 37 MGazeNet host tests pass.
Recorded reports must supply the complete v1 harness manifests; omission cannot
fall back to the generic synthetic analytical schema. The v2 pilot proposal in
`gaze-mgazenet-protocol-review.md` is not accepted as v1 evidence.

Input schema `mgazenet_accuracy_v1` requires:

| Field | Contract |
| --- | --- |
| `evidence_kind` | `synthetic_contract` or `recorded`; synthetic output stays explicitly synthetic. |
| `protocol_id`, `session_id`, `device_id`, `pipeline_id`, `calibration_id` | Nonempty provenance identifiers; the producer must bind them to immutable manifests/hashes. The scorer cannot independently authenticate their truth. |
| `coordinate_space`, `clock` | `physical_screen_px`, `shared_monotonic_ms`; do not substitute wall-clock timestamps or unsynchronized camera time. |
| `max_output_age_ms` | Explicit positive analysis parameter fixed by the protocol; no default or study threshold is selected. |
| `fit_block_ids` | Unique IDs used for fitting; none may overlap test IDs. Whole-target independence still needs producer/protocol verification. |
| `blocks` | Every planned held-out block, including empty/failed blocks; ordered, non-overlapping windows. Never omit an unsuccessful target. |
| Block `id`, `region`, `role`, `task` | Unique target-block ID, regional label, `held_out`, `stationary_point`. Fit or natural-reading blocks are rejected. |
| Block `start_ms`, `end_ms` | Stable target's measurement interval, half open; preparation and target movement are separate. |
| Block `target_px`, `target_line_index`, `line_height_px` | Physical target point and actual rendered line index/height. These must agree with the recorded layout. |
| Block `viewport_screen_px`, `lines` | Clipped physical rectangles `[left,top,right,bottom]`; ordered non-overlapping line bands with document line indices. Right/bottom edges are exclusive. No nearest-line snapping. |
| Block `samples` | One entry per emitted coordinate or explicit invalid result. `capture_ms`, `output_ms`, `point_px` (pair or null). Capture occurs within the stable block; output may be late. Both timestamp sequences must strictly increase. |

The complete input example is
`tools/gaze/mgazenet/fixtures/accuracy-contract-synthetic.json`. Coordinates may
fall off the viewport; they remain coordinate observations with spatial errors,
but are not correct-line assignments. Explicit nulls remain received invalid
observations. No-face events that produce no callback cannot be counted as
received samples; the full planned time denominator still exposes missing output.

## Exact scoring definitions and limits

For each coordinate, signed error is prediction minus target. Vertical error
is `abs(dy)/rendered_line_height`; 2-D error is Euclidean pixels. Report each
block's median, nearest-rank P95 and maximum. The median averages two central
values for even counts. Missing spatial summaries are null, never zero.
Large off-screen errors remain. Signed-displacement summaries are distinct
from absolute-error summaries.

Exact-line assignment requires the coordinate inside the target's clipped
line band. Within-one-line assignment requires a visible assigned line with
absolute document-index difference at most one. This is different from having
vertical distance below one line height. A point horizontally off text may have
zero vertical error while failing line assignment. Rectangular line bands are
not glyph-level word geometry, so no word accuracy is computed here.

Sample summaries retain both received-result and coordinate-only denominators.
They cannot account for periods when no result was emitted. Accordingly,
the evaluator also computes bounded latest-result availability over **all
planned measurement time**. A valid result becomes available at its output
timestamp, and expires at the earliest of the next emitted result (including
an invalid result), its capture time plus the declared maximum age, or block
end. It never fills the initial gap or carries across blocks. Late/stale results
still have sample spatial errors but contribute no unavailable past time.

Report fresh-coordinate, fresh-exact-line and fresh-within-one-line time
fractions, unavailable duration and longest unavailable interval per target.
Equal-target means include empty targets with zero time coverage. These are
availability measures under an explicit expiry policy, **not reconstruction
of actual eye position between samples**. Keep them alongside the full sample
trace and spatial error distributions, not as a single overall quality score.

In the invented demonstration, the first block has two correct coordinates and
one explicit invalid result: 66.7% correct among received results, 100% among
coordinates, but only 16% fresh correct-line availability under its illustrative
100 ms age limit. The second block has no outputs. Equal-target fresh correct
availability is therefore 8%. This is a test of the evaluator, not phone data;
100 ms is not a proposed or approved study limit.

Reproduce from Windows CMD, choosing a new output filename:

```bat
python tools\gaze\mgazenet\accuracy_metrics.py tools\gaze\mgazenet\fixtures\accuracy-contract-synthetic.json --output tmp\accuracy-contract-check.json
python -m unittest discover -s tools\gaze\mgazenet -p test_accuracy_metrics.py -v
```

`gaze-mgazenet-accuracy-contract-synthetic-results.json` preserves the initial
demonstration with its input SHA-256. It deliberately returns no accuracy pass
or promotion decision. Existing historical recordings are not silently rescored
under this new schema; their missing timestamps/geometry cannot be invented.

## Concrete next implementation boundary

An isolated accuracy harness can reuse the verified model/preprocessing/SVR
with a new calibration controller and this measurement contract. Keep the main
app and its calibration store independent. Bind model, localizer, input size,
coordinate convention, eligibility and filter configuration into the calibration
manifest. Capture target/layout and fit/test identities at collection time,
not after viewing results. The proposed initial controller preserves the source
1.5-second settle, 45 accepted features per point, .5-second post-collection
wait and separate practice; a longer or shorter participant procedure requires
an explicit methodological decision rather than an incidental speed change.

Before starting any personal run, the target/coordinate contract, fit-only
training, invalid admission behavior, cancellation and data isolation need
camera-free checks. Then conduct separately authorized image/camera checks
and fresh matched estimator evaluation on both phones, with independent
replication and frozen analysis choices. The readiness note lists the required
spatial and reading outcomes. This evaluator covers stationary target scoring
only; it does not yet implement transitions, word geometry, fixation/dwell or
regression validity. No acceptance threshold or participant run is requested
at this checkpoint.

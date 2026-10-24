# MGazeNet native-calibration branch

Status: software implementation complete at the host-build checkpoint. No APK
from this branch has been installed and no camera or personal calibration has
been run. The frozen 16-point matched-comparison implementation remains at
`mgazenet-feasibility` commit `054ae91`; the previous NewsMead tracker remains
at `gaze-pipeline-improvements` commit `ec635fd`.

## Why this branch exists

MGazeNet is not intrinsically a 16-point estimator. GazeFollower 1.0.2 exposes
5-, 9-, and 13-point modes and defaults to 13. Its 13-point controller presents
one centre practice target followed by grid indices
`1,5,9,12,16,19,27,30,34,37,41,45,23`. It waits 1.5 seconds from target onset,
collects 45 admitted frames at each fit target, waits 0.5 seconds, and fits
independent RBF SVRs for X and Y.

The integrated 16-point design was deliberately specified for a matched
NewsMead comparison: it retained the previous tracker's 4x4 full-screen target
geometry while collecting MGazeNet's own 258-value features. That design is a
valid frozen comparison protocol, but it is not the upstream default and has
not been shown to be the best calibration burden for older adults.

Pinned upstream sources:

- GazeFollower 1.0.2 commit `553920edcb7998c029828677f50f6d8eb4a16249`;
- `gazefollower/calibration/CalibrationController.py` for target modes, order,
  timing, and 45-frame collection;
- `gazefollower/calibration/SVRCalibration.py` for the two RBF regressors; and
- Zhu et al. (2024), DOI `10.1155/2024/2644725`, for the reported 3/5/9/13-point
  calibration comparison and the separate smooth-pursuit experiment.

The paper reports that 13-point calibration generally improves error over 9
points and describes 13-point or approximately 75-second smooth pursuit as the
best practical choices in its experiments. Those results do not establish
accuracy for NewsMead's public `base.mnn`, Android localizer/runtime, target
phones, reading task, or older-adult population. This branch therefore adopts
the source-native 13-point default without claiming that it is already the
final population-validated protocol.

## Implemented contract

- Keep the verified public `base.mnn`, exact GazeFollower crop/preprocessing
  contract, 258-value feature vector, and fixed OpenCV X/Y RBF SVRs.
- Keep the exact upstream 13-point order and 9x5 topology, mapped to a 10%-90%
  phone-safe grid inside NewsMead's measured full-screen calibration viewport.
  The desktop implementation's normalized 50-pixel side margin would place an
  outer target only 28 pixels from the edge on a 1080-pixel-wide portrait phone,
  clipping NewsMead's 44dp ring. MGazeNet is trained on the recorded screen
  labels and does not require those desktop pixel margins.
- Keep 45 admitted finite rows per target, 1.5-second target-onset settling,
  0.5-second post-collection hold, eye-area eligibility, and the 30-second
  per-target safety deadline.
- Start settling when the target is first drawn and finish the 1.2-second visual
  contraction 0.3 seconds before sampling begins. The frozen comparison had an
  extra 1.8-second delay before starting its 1.5-second settle interval.
- Keep the six descriptive post-fit checks for the first device evaluation.
  They are validation observations, not training points or corrections.
- Save under `newsmead_mgazenet_calibration_v2` in the distinct
  `noBackupFilesDir/mgazenet-v2` namespace. A v1 16-point calibration is never
  loaded, rewritten, or deleted.
- Continue using MGazeNet as the primary provider with no runtime backend
  selector and no old affine/drift correction.

The resulting fit contains 585 rows rather than 720. The model identity binds
the protocol string and all 13 physical target coordinates, so any target,
timing, model, preprocessing, display, device, or acquisition change requires
a fresh calibration.

## Verification and next boundary

The MGazeNet JVM tests, Android-test Kotlin compilation, debug APK assembly,
and `git diff --check` pass. The debug APK is a new development artifact and is
intentionally not the hash-locked 16-point comparison APK.

The next step is one non-participant device timing and functional check on each
phone, followed by the fixed 20%/50%/80% nine-point check and reading validation.
Eight of those nine accuracy targets are held out from the 13-point fit; the
centre is retained as a repeat check. Record actual completion time, target
timeouts/rejections, output coverage, median and worst two-dimensional target
error, regional error, and line accuracy. Do not reduce the 45-row count or
remove validation based on duration alone; compare any shorter protocol
prospectively against this 13x45 checkpoint.

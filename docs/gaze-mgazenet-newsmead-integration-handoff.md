# MGazeNet-as-primary NewsMead integration handoff — 2026-09-10

**Software integration follow-up:** the user explicitly authorized the software
work, now completed to the host-build boundary in
`gaze-mgazenet-newsmead-host-checkpoint.md`. That checkpoint records the primary
provider wiring, isolated calibration storage, exact test/build evidence and
unexecuted Android checks. No phone contact, installation, CAMERA use or
participant calibration occurred. The original handoff below remains the
requirements and historical starting state, not a claim that `app/` is still
unchanged.

Status: the MGazeNet feasibility, input, stationary-accuracy and evidence-review
phases are complete. The next distinct project is to make MGazeNet the primary
gaze implementation on branch `mgazenet-feasibility`. Do not add a permanent
runtime current/MGazeNet selector merely for caution. The existing implementation
is preserved on branch `gaze-pipeline-improvements` and remains the comparison
and rollback reference.

This document authorizes nothing by itself. The user requested this handoff so a
new chat can continue with full context. Obtain or receive an explicit “go
ahead” for software integration before editing `app/`. Phone installation,
CAMERA access and participant calibration remain later, separate boundaries.

## Required reading order

Read these files completely and in this order before changing code:

1. `docs/gaze-accuracy-handoff.md`
2. `docs/gaze-mgazenet-handoff.md`
3. `docs/gaze-mgazenet-accuracy-readiness.md`
4. `docs/gaze-mgazenet-accuracy-protocol.md`
5. `docs/gaze-mgazenet-accuracy-harness.md`
6. `docs/gaze-mgazenet-protocol-review.md`
7. `docs/gaze-mgazenet-input-check.md`
8. `docs/gaze-mgazenet-calibration-observability-v3.md`
9. `docs/gaze-mgazenet-direct-validation-screen-protocol.md`
10. `docs/gaze-mgazenet-direct-validation-confirmation-analysis.md`
11. `docs/gaze-current-vs-mgazenet-evidence.md`
12. this handoff

Follow links from those documents when a source or implementation detail is
needed. Do not repeat completed device checks or participant sessions just to
reconstruct context.

## User decision and corrected interpretation

The user explicitly challenged the earlier plan to keep MGazeNet as a runtime
“research backend.” They are right that branch isolation already provides the
implementation rollback boundary. The agreed direction is:

- make MGazeNet the normal/primary gaze path on `mgazenet-feasibility`;
- do not add a user-visible or permanent runtime backend selector;
- preserve the current implementation untouched on
  `gaze-pipeline-improvements`;
- compare exact commits/builds from the two branches later; and
- do not claim that simply switching branches makes the later measurement a
  controlled experiment.

The active system was never shown superior to MGazeNet. The frozen MGazeNet
confirmation returned `inconclusive` because the proposed five-point *screen*
could not demonstrate a true acceptance and a true rejection; all four
prospective screens rejected on their regional tail. That result evaluates the
screen as a selector, not MGazeNet versus the current estimator.

The retained evidence leans toward MGazeNet on stationary accuracy. Across the
closest line-normalized 16-fit/five-held-out summaries, median session target-
balanced mean error was 0.721 lines for eight MGazeNet sessions and 1.694 for
the two clean post-coordinate-repair current sessions. The A56-only MGazeNet
value was 0.910. MGazeNet used a narrower inset viewport, so the outer targets
are not like-for-like. A much closer A56 centre-target comparison in raw pixels
also leans modestly toward MGazeNet: 136.410 px median across four sessions
versus 162.379 across the two current sessions. See
`gaze-current-vs-mgazenet-evidence.md` for the full inventory and limitations.

This evidence justifies integration as the leading candidate. It does not yet
establish exact-line reading, word selection, scrolling behavior, population
reliability or production superiority.

## Repository and git boundary

Inspection immediately before creating this handoff found:

- current branch: `mgazenet-feasibility`;
- pre-handoff HEAD: `f4721b4` (`Document current model and MGazeNet evidence comparison`);
- preserved reference branch: `gaze-pipeline-improvements` at `ec635fd`;
- merge base with the reference branch: `ec635fd01905544c8251c37b6891105ebbdee923`;
- `git diff gaze-pipeline-improvements..HEAD -- app` returned no files, so the
  application implementation was still identical across the two branches; and
- the worktree was clean before this handoff file was added.

The user handles all staging and commits. At every meaningful software
checkpoint, report an exact narrow `git add` command and a proposed commit
message; never stage or commit automatically. Preserve unrelated work. Never
use `git add .`.

`diagnostics-local/` and `mgazenet-benchmark/vendor/` are ignored local evidence
and dependencies. Never stage them. The populated vendor directory is currently
available locally, but another checkout must reproduce it through the pinned
fetch/verification path rather than trusting unverified binaries.

Do not switch branches while the user has uncommitted work. Do not reset,
checkout away, delete or rewrite existing changes. The new chat should recheck
branch, HEAD and status before acting because this snapshot may have advanced.

## Integration architecture

Do not try to feed MGazeNet's 258 values into `GazeMapper` or load them through
the two-value current `CalibrationStore`. The representations and calibration
algorithms are incompatible.

The intended application path is:

`CameraX front frame -> MediaPipe face/eye localization -> exact MGazeNet crops
and rectangle input -> base.mnn 258-value feature -> local X/Y RBF SVRs ->
finite physical-screen coordinate -> existing GazeProvider consumer boundary`

The downstream article/AOI/RSI code should continue consuming only full-screen
pixels through `GazeProvider`. Do not rewrite reading inference, AOI geometry,
scrolling or RSI logic as part of the tracker replacement.

Likely production components to port or adapt from `mgazenet-benchmark` are:

- `CameraFrames.kt` — upright CameraX bitmap conversion and ownership cleanup;
- `GazeGeometry.kt` — executed face/eye crop geometry;
- `Preprocessor.kt` — exact face/left-eye/right-eye/rectangle tensors;
- `MnnEstimator.kt` — pinned `base.mnn` loading and 258-value inference;
- `AccuracyCameraSource.kt` — integrated CameraX/Face Landmarker/inference
  lifecycle, renamed and separated from benchmark reporting concerns;
- `FloatInputValidation.kt` — finite input guards; and
- `SvrCalibration.kt` — the pinned two-regressor OpenCV EPS-SVR behavior.

Do not make the production app depend on the other Android *application*
module. Move or adapt the necessary implementation into the production gaze
package, or extract truly shared code only if that remains simpler and keeps
the ordinary build understandable.

The existing construction sites that require deliberate rewiring are
`GazeCalibrationActivity`, `GazeTestActivity`, `ReadingValidationActivity` and
`ArticleFragment`. `GazeProvider` is already the downstream abstraction, but
the latter three directly construct `LocalCalibratedGazeProvider` and load the
current store. Avoid leaving one hidden current-provider path active.

## Calibration and persistence requirements

Branch separation does not isolate Android app-private data when both builds
use application ID `com.newsmead`. An update installed from another branch may
see the same files. Prevent silent cross-model contamination in software before
any phone install:

1. Introduce a distinct MGazeNet calibration schema and storage namespace.
   Never read `calibration_16point.csv`, the current feature-mode marker, its
   mapper, affine correction or drift correction as MGazeNet input.
2. Keep the old calibration files untouched but incompatible/unused on this
   branch. Do not silently delete participant data merely to avoid loading it.
3. Bind each saved MGazeNet calibration to the model hash, localizer hash,
   preprocessing/protocol version, SVR parameters, physical screen dimensions,
   physical-screen coordinate contract and exact target viewport/geometry.
4. Persist only the minimum local calibration artifact needed for normal app
   reuse—prefer the two trained SVR models plus a hash-bound manifest. Do not
   persist frames, crops, landmarks or 258-value training rows. Treat the
   trained model as participant-specific local data; do not export it through
   ordinary diagnostics.
5. Use atomic writes and reject missing, partial, changed-hash, non-finite,
   wrong-device/geometry or wrong-version artifacts. A load failure should
   require a new MGazeNet calibration, never fall back to the old mapper.
6. Prove save/load prediction parity using deterministic synthetic features
   before relying on OpenCV Android model serialization. If OpenCV's supported
   persistence path cannot be made deterministic and robust, stop and document
   the blocker rather than retaining all 720 personal feature rows.

Use NewsMead's actual physical-screen coordinate helpers added by E-053. Do not
reintroduce the omitted screen-origin defect or hard-code the observed 101-px
A56 origin.

## Calibration and live-output behavior

Use the existing NewsMead calibration screen's real full-screen target area,
not the benchmark Activity's narrower inset viewport. Preserve the current
sixteen row-major target fractions and physical screen-coordinate conversion.
MGazeNet must collect its own compatible features—45 admitted finite rows per
fit target is the already exercised evidence-backed setting—and fit its own two
RBF SVRs. Accuracy takes priority over shortening calibration or maximizing
framerate.

The first integrated implementation should retain the validated MGazeNet model
contract: no target-answer correction, line snapping, current affine layer,
current drift correction or hidden refit. Do not silently add the current
two-feature filters to MGazeNet output and then attribute the result to the
tested benchmark configuration. If temporal smoothing is needed for reading,
make raw and filtered coordinates separately observable and freeze it as a
later measured change.

Output only finite full-screen coordinates. Preserve explicit no-face,
invalid-crop, busy-drop and stale-output behavior rather than replaying a stale
coordinate as though it were a fresh measurement. The input check observed
about 7.25 emitted results/s, 52.0%/61.1% busy-drop fractions and roughly
220 ms median output age on G991B/A56, including a 2.66-second A56 first-result
gap. These are measurement-validity risks even though accuracy is the priority.
Do not hide them with interpolation or expected-reading-order assumptions.

## Pinned model/runtime and privacy facts

Keep the exact verified vendor identities unless a separate audited change is
proposed:

- `base.mnn` SHA-256:
  `2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96`;
- `face_landmarker.task` SHA-256:
  `64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff`;
- `libMNN.so` SHA-256:
  `c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60`;
- `libmnncore.so` SHA-256:
  `075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9`;
- bundled MNN `libc++_shared.so` SHA-256:
  `f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970`.

Preserve the vendor verification task and attribution notices. GazeFollower's
reviewed source/model notice is CC BY-NC-SA 4.0 and MNN's is Apache 2.0. This
research branch is not a commercial-license conclusion; do not remove notices
or claim unrestricted distribution.

The system must remain fully on-device and network-independent. No camera frame,
crop, landmark or raw 258-value feature may be written to disk, logs, analytics,
Crashlytics or Firestore. Reuse buffers carefully, zero personal feature arrays
when practical, close every ImageProxy/native tensor/model/Mat on lifecycle and
failure paths, and retain the bitmap-lifetime repairs documented in
`gaze-mgazenet-input-check.md`.

## Recommended software-only checkpoints

The first new chat should progress through safe host work without contacting a
phone:

1. Recheck status/branch and inspect the production construction, calibration,
   coordinate and lifecycle paths alongside the benchmark components.
2. Add pinned MNN/OpenCV/vendor verification to the ordinary app build without
   weakening existing release packaging or notices.
3. Port the camera/preprocessor/estimator/SVR primitives with their existing
   parity, finite-guard and resource-cleanup tests.
4. Implement the separate MGazeNet calibration manifest and two-SVR atomic
   persistence, including synthetic train-save-load-predict parity and explicit
   rejection of the old calibration format.
5. Replace the current provider/calibration wiring on this branch while leaving
   `GazeProvider` consumers and article/AOI/RSI behavior unchanged.
6. Add host/instrumentation-testable geometry and lifecycle seams. Verify that
   merely opening setup does not start CAMERA, write calibration data or keep
   the screen-on session flag.
7. Run the focused production and MGazeNet test suites, all practical app JVM
   tests, vendor verification, `assembleDebug`, and `git diff --check`. Confirm
   every changed `app/` file is intentional and no ignored evidence/vendor file
   is staged.

Stop at that software checkpoint and report results plus the exact narrow
staging/commit suggestion. Do not install an APK or contact either phone merely
because the build succeeds. A later explicit authorization may permit a
camera-disabled install/setup/layout/native-load check. CAMERA and personal
calibration require another explicit authorization after that.

## What must be true before participant comparison

Before any matched current/MGazeNet measurement is proposed, software evidence
must show:

- MGazeNet is the only normal gaze provider on this branch;
- every production construction site uses it;
- its saved calibration survives process restart with identical finite
  synthetic predictions and rejects incompatible data;
- target coordinates use NewsMead's real physical screen space and full
  calibration viewport;
- opening setup remains camera-off and mutation-free;
- no sensitive visual/model-input artifacts are retained;
- the app closes resources on success, cancellation, lifecycle stop and error;
- the app build is reproducible from pinned dependencies; and
- current-branch files and calibration remain available as the external
  comparison/rollback reference.

Only then freeze a concise counterbalanced comparison using exact commit/APK
hashes, separate compatible calibrations and identical independent targets.
Because branch builds share package data by default, either use rigorously
versioned isolated stores with verified resets between arms or prepare a
separate reference application ID for the comparison. Do not let install order,
old calibration state, unequal viewports, fatigue or result-driven repetition
decide the outcome.

## User and device context

The participant disclosed severe ongoing sleep loss during the earlier pilot,
with later sessions ranging from mild to greater fatigue. Retain fatigue and an
attention lapse as context; never delete, correct or repeat an unfavorable run
on that basis. The user can connect only one phone at a time. Last-known device
identifiers were A56 `R5CY40Y2C5W` and G991B `R5CR60YSJ2X`, but connection,
installed build, permission and process state must be verified rather than
assumed in any future authorized device phase.

Accuracy and measurement validity take priority over framerate. Do not pressure
the user into another participant run while fatigued. No additional personal
measurement is required to complete the initial software integration.

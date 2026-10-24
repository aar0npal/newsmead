# Gaze Model Replacement Plan

> Status update, 2026-09-06: this is a historical proposal, not the current implementation
> authorization. Read `gaze-mgazenet-handoff.md` for the source-backed next inquiry. Native
> MGazeNet feasibility is now prioritized before custom network training. Rejection of the
> laptop/WiFi GazeFollower arrangement does not exclude its underlying model running on-device.
> The active eye feature is now roll-aware and eye-width-normalized, not the eyelid fraction
> described below; the current camera request is 640x480. Claims below about training a new
> model or adding reading-content priors are proposals only, not approved next actions.

## Objective

Replace the current MediaPipe iris-landmark plus polynomial calibration backend
with a gaze estimator that is materially more accurate and stable on the study
phone while preserving the existing `GazeProvider.onGaze(x, y)` boundary.

The replacement must improve the measurements that matter to this thesis:
vertical error in article-line heights, exact-line and adjacent-line accuracy,
within-fixation dispersion, sudden-jump rate, and drift across time and posture.
Model-reported angular error or performance on an unrelated desktop dataset is
not sufficient evidence.

## Why the current backend is not the final measurement system

The current raw feature contains only two values: the iris position between the
eye corners and eyelids, averaged across both eyes. It discards most appearance,
head-pose, face-position, viewing-distance, and per-eye information before the
16-point polynomial is fitted. This makes the mapping sensitive to small posture
changes and gives the same noisy stream two roles: detecting instability and
placing the resulting scaffold.

The integration remains useful. CameraX capture, calibration presentation,
quality logging, article AOI mapping, RSI, scaffolding, and `GazeProvider` should
be retained. Only the estimator behind the provider should change.

## Relationship to the manuscript's completed exploration

Chapter 4 already compares six approaches: GazeFollower, MediaPipe Iris plus
polynomial calibration, retrained GAZEL, iTracker/GazeCapture, GazeRecorder, and
WebGazer. The engineering record additionally documents the rejected
MobileGaze/L2CS angular-model path. This proposal does not recommend rerunning
any of those as the new contribution.

The manuscript selected MediaPipe because it was the only evaluated option that
was on-device, license-free, independent of a laptop/network, and did not require
training. The proposal deliberately changes the final condition: it accepts the
cost of training a study-specific model in exchange for a chance to solve the
binding limitation that calibration alone has not solved--run-to-run vertical
instability under head/posture change.

Existing approaches remain comparison evidence:

- the current MediaPipe backend is the paired on-device baseline;
- GazeFollower's measured result is an accuracy reference but not a deployable
  candidate;
- GAZEL and MobileGaze/L2CS are negative results showing that generic
  cross-device or angular gaze models are insufficient;
- iTracker/GazeCapture is prior evidence for direct 2D appearance modeling, not
  the proposed replacement;
- browser, cloud, and commercial paths remain outside the deployment boundary.

## Candidate assessment

| Candidate | Output and evidence | Android/deployment status | Decision |
| --- | --- | --- | --- |
| iTracker / GazeCapture | Direct 2D mobile-screen point. The original paper reports 1.71 cm phone error without calibration and 1.34 cm with 13 calibration points. Official Caffe weights and source are available under a research-only license. | Old Caffe model; requires conversion and was trained on Apple devices. The manuscript already assessed its reported performance and training burden. | **Historical comparison only, not the proposed replacement.** |
| SAGE with supervised few-shot personalization | Direct 2D gaze. The paper reports roughly 24% improvement over iTracker with three calibration points, about 41.8M FLOPs, and 10 ms inference on a Pixel 2. | Architecture is described, but no official public weights or implementation were found. Requires reimplementation and training. | **Preferred design target if iTracker proves directionally useful.** |
| 3D Prior Is All You Need (CVPR 2025) | Adapts a pretrained 3D estimator to an unseen 2D device using a learned screen projection and few calibration images. Evaluated on GazeCapture as well as desktop datasets. | Best conceptual match, but no official public implementation or mobile runtime evidence was found. | **Research candidate, not the first integration.** Revisit if code/weights appear or reimplement after a working 2D baseline. |
| MobileGaze / L2CS-Net | Predicts yaw/pitch direction. Available MobileOne-S0 weights are small, but the published repository reports about 12.58 degrees MAE and training only on Gaze360. | Easy to convert and run, but direction error is comparable to the phone's angular span. The project already rejected direct angle-to-screen use. | **Do not use as the final output model.** It may be tested only as a frozen appearance embedding feeding a personalized 2D head. |
| iMon | Direct mobile gaze; reports 1.49 cm per-frame phone error and 1.11 cm after fixation averaging plus personal calibration. | Paper demonstrates an iPhone implementation, but no official deployable model/code was found. | **Evidence for design choices** (probabilistic labels, enhancement, fixation averaging), not currently reproducible here. |
| HiFiGaze (CHI 2026) | Uses visible screen reflections in high-resolution eye images and known screen content; reports an improvement over an appearance baseline. | Requires 4K-class eye detail, and the reported study excluded glasses. Current 480x360 analysis and the target population make it a poor first fit. | **Not selected.** Useful future direction only. |

Primary sources:

- GazeCapture/iTracker paper and release:
  <https://gazecapture.csail.mit.edu/cvpr2016_gazecapture.pdf>,
  <https://github.com/CSAILVision/GazeCapture>
- On-device few-shot personalization (SAGE):
  <https://research.google/pubs/on-device-few-shot-personalization-for-real-time-gaze-estimation/>
- Cross-task few-shot 2D gaze estimation:
  <https://openaccess.thecvf.com/content/CVPR2025/html/Cheng_3D_Prior_Is_All_You_Need_Cross-Task_Few-shot_2D_Gaze_CVPR_2025_paper.html>
- MobileGaze model release: <https://github.com/yakhyo/gaze-estimation>
- iMon: <https://doi.org/10.1145/3494999>
- HiFiGaze: <https://arxiv.org/abs/2603.19588>

## Proposed model: personalized, content-aware 2D gaze

The proposal is an eye-centric 2D model rather than a generic full-face or
gaze-angle model:

```text
upright mirrored camera frame
-> paired eye crops + normalized eye bounds
-> shared mobile appearance encoder
-> participant calibration embeddings
-> article-layout context + short temporal context
-> screen x,y + 2D uncertainty (and optional line posterior)
-> GazeProvider.onGaze(x, y)
```

The primary visual input is the pair of eye crops. Normalized eye bounds retain
camera-relative face position at very low cost. A full-face image, all facial
landmarks, or an explicit head-pose branch must be evaluated as an ablation,
not assumed useful: HiFiGaze found that adding a full-face crop or all 468
MediaPipe landmarks performed the same or worse than its eye-crop baseline.

NewsMead can additionally supply context that generic gaze models do not have:
the visible article-line geometry, scroll position, and a target-free thumbnail
or encoding of the reading surface. This context must constrain ambiguous gaze,
not reveal the calibration label. A visible calibration target must therefore
be masked from any screen-context input, and the target design must be checked
for reflections that could leak its location into the eye image. HiFiGaze
explicitly warns that a colored target can become a learnable reflection cue.

The output should include a 2D uncertainty estimate or covariance. High
uncertainty must gate AOI events, RSI, and scaffolding instead of allowing a
low-quality coordinate to create apparent regressions and then position an
intervention. A short temporal model may stabilize consecutive estimates, but
all comparisons must report both raw per-frame and temporally aggregated error
so smoothing is not mistaken for estimator accuracy.

Phone orientation, face position, and optional head-pose signals should first be
used to detect departure from the calibrated operating range. Whether they
improve direct prediction must be established by ablation. This is safer than
assuming that adding more correlated inputs will improve a small personalized
model.

## Two-stage implementation strategy

### Stage A: train and evaluate the proposed model offline

1. Initialize a small shared eye encoder from a general mobile vision backbone;
   optionally pretrain the gaze task on a licensed public gaze dataset.
2. Train the direct 2D, uncertainty, and optional line-posterior heads using
   development-team A56 recordings collected under the protocol below.
3. Fit the participant-personalization layer only from the documented 16
   calibration targets. Do not train or select it using held-out validation or
   9-point accuracy targets.
4. Compare the frozen proposed model and the current backend on the same frames,
   targets, sessions, posture conditions, and aggregation windows.
5. Run ablations for screen/layout context, temporal context, full-face/head-pose
   inputs, and personalization. Retain a branch only when paired held-out results
   show that it helps.

This stage is an experiment, not an architecture commitment. Published model
accuracy does not establish performance on the A56 or on the target population.

### Stage B: build the deployable personalized estimator

If Stage A passes the paired replacement gate, convert and integrate the frozen
model behind `GazeProvider`. The proposed network is inspired by SAGE-style
personalization and the eye-crop baseline evaluated by HiFiGaze:

```text
upright mirrored camera frame
-> paired eye crops + normalized eye bounds
-> lightweight shared appearance encoder
-> participant calibration embeddings
-> article-layout/scroll context + short temporal context
-> small supervised few-shot personalization head
-> screen x,y plus 2D quality/uncertainty
-> temporal filter
-> GazeProvider.onGaze(x, y)
```

The screen-coordinate head must be participant- and device-specific. A generic
3D yaw/pitch estimate must not be projected onto the screen without measured
personalization. The model should also expose an uncertainty or spatial-quality
signal so tracking error can gate RSI and scaffolding instead of merely being
smoothed.

## Required benchmark capture

No camera frames or synchronized target dataset currently exist in the three
project workspaces. A developer-only capture mode is therefore required before
any learned estimator can be validated.

For each accepted calibration and accuracy target, retain:

- upright, mirrored source frame or lossless face/eye crops;
- target screen coordinate and target identifier;
- capture and result timestamps;
- device orientation, display size/density, and camera resolution;
- face bounding box, eye landmarks, and head-pose/face-position features;
- a target-masked reading-surface/layout encoding when context-aware variants
  are evaluated;
- blink/no-face status and current raw MediaPipe feature;
- session identifier, presentation order, and whether the point belongs to
  calibration, held-out validation, or the 9-point accuracy test.

Capture must be disabled by default and limited to development-team benchmark
sessions. The study consent currently states that camera images are not retained;
participant frames must not be collected under that consent. If later collection
from target-age participants is required for model development, the ethics and
consent documents must be amended first.

Split evaluation by complete session and participant, never by individual frame.
Adjacent frames from one fixation are highly correlated and would produce severe
train/test leakage.

The proposed model needs denser development data than the 16 calibration points
alone. A separate target sequence should cover the screen with slow horizontal
and vertical paths plus static fixation targets, while recording phone/head
motion conditions. The calibration, held-out validation, and 9-point accuracy
targets remain separate and must never be folded into training. Target appearance
must be achromatic or otherwise designed and masked so its location cannot be
learned from a reflection or from the supplied screen-context representation.

## Fair comparison protocol

For every session:

1. Collect one 16-point calibration sequence.
2. Freeze every backend and its participant-specific calibration.
3. Run the existing 5 held-out targets and the 9-point 20/50/80 verification.
4. Repeat after a controlled posture change and again after ten minutes of
   reading/scrolling.
5. Aggregate samples using the same settling interval and median window for all
   backends.
6. Record both per-frame and per-fixation results; do not compare a fixation
   median from one model with a single frame from another.

Report at minimum:

- median and P95 Euclidean error in pixels and centimetres;
- median and P95 vertical error in pixels and line heights;
- exact-line and within-one-line accuracy;
- horizontal word-region accuracy;
- within-target dispersion and sudden-jump rate;
- off-screen/off-text rate and tracking loss;
- error change after posture shift, scrolling, and elapsed time;
- inference FPS, P95 latency, model size, and device temperature/throttling;
- results per session and participant, not only a pooled average.

## Replacement acceptance gate

A candidate is materially better only if all of the following hold on paired A56
sessions:

1. At least a 25% reduction in both median and P95 vertical error relative to
   the current backend, with improvement in a majority of sessions rather than
   only in the pooled mean.
2. Lower between-session variance and a smaller accuracy loss after the posture
   and elapsed-time checks.
3. At least 20 processed frames per second on the Galaxy A56 with no sustained
   thermal collapse during a study-length run.
4. No regression in tracking-loss rate, calibration completion, or participant
   setup burden.
5. For an exact-line claim, exact-line and within-one-line accuracy must satisfy
   pilot-frozen acceptance limits. A median error alone cannot establish this.
6. Word-level scaffolding remains provisional unless horizontal word-region
   accuracy independently passes a pilot-frozen gate.

If no candidate passes, retain the `GazeProvider` interface but revise the study
toward region-level scaffolding or use external research-grade ground truth. A
more complex model that does not pass the paired device benchmark is not an
improvement.

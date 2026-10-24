# Native MGazeNet feasibility - next-task handoff

Updated 2026-09-06. Read after `gaze-accuracy-handoff.md`.
This brief supersedes conflicting historical freeze, replacement, and repeat-test proposals.

**2026-09-07 continuation:** the authorized isolated benchmark and two-phone
camera-free checks are now complete. Read `gaze-mgazenet-benchmark.md`, then
`gaze-mgazenet-accuracy-readiness.md` for current evidence and next work. The user
clarified that accuracy and trustworthy measurements take priority; further
speed optimization is not the default next task. Historical authorization and
pending-check statements below describe the original handoff, not present state.
The next accuracy preparation checkpoint is `gaze-mgazenet-accuracy-protocol.md`:
localizer adaptation audit and tested offline measurements, without camera or
participant calibration runs.
The implemented harness and latest device-check status are recorded in
`gaze-mgazenet-accuracy-harness.md`; this is the current continuation entrypoint
after the required historical handoff reading.

**Protocol review follow-up:** `gaze-mgazenet-protocol-review.md` identifies
the fixed-order/region confound, limits of stationary validation, and the bounded
input-only check and subsequent pilot proposal. Its host-only scorer repairs
close manifest-removal and target-geometry bypasses; 37 host tests pass. No new
phone action or participant session occurred. Continue from that review rather
than repeating the completed performance or setup checks.

**Input-check implementation follow-up:** `gaze-mgazenet-input-check.md` records
the completed software work, the retained first SM-G991B bitmap-lifetime failure
and the retained next-frame recycled-buffer failure. The complete repair is
host- and device-verified and completed the full input check on both phones.
No personal calibration or accuracy trial occurred; continue from the input-check
checkpoint before considering the prospective v2 pilot.

**Stationary v2 follow-up:** `gaze-mgazenet-stationary-v2.md` is now the current
continuation point. The isolated app and offline scorer implement the reviewed
ten-location/two-sweep protocol and pass host verification while retaining v1
support. Its authorized camera-free setup/geometry check now also passes on the
A56 and SM-G991B with CAMERA still disabled and no record created. Both actual
layouts retain ten distinct repeated locations, no fit overlap and no default
order choice for the exact hashed APK pair. No personal calibration occurred;
participant collection remains a separate later authorization.

**Pilot-results follow-up:** `gaze-mgazenet-stationary-pilot-results.md` records
the complete two-phone pilot: two authorized sessions per phone, each with a
fresh calibration and opposite predeclared order. It retains every target block,
extreme spatial tails, fixed four-age availability tables, off-text results and
signed bias changes. No gate was assigned and CAMERA was revoked after each. Do
not tune or rerun a completed session. The participant disclosed severe fatigue
and roughly two weeks without good sleep; the same no-glasses,
approximate-distance, medium-brightness and slightly dimmer nighttime
lighted-room conditions reported for G991B session 2 were prospectively declared
for both A56 sessions. Treat all four as fatigue-context feasibility evidence
and do not causally subtract fatigue from tracker error. A56 session 1 produced
0.59/1.53/6.19-line median/P95/max and 32.1%/79.3% exact/within-one-line, but
session 2 did not reproduce it: 1.21/2.26/4.66 lines and 14.8%/56.5%. This is
neither a pass nor a device ranking. No more personal trials are authorized;
the next boundary is a camera-free evidence review and explicit proposal.

**Post-pilot review follow-up:** `gaze-mgazenet-post-pilot-review.md` completes
that camera-free review. It finds meaningful stationary spatial signal but
insufficient line-assignment repeatability, with vertical bias stable within
each session and reversed between each phone's fresh sessions. The current
calibration export cannot distinguish the stronger and weaker calibrations:
it retains counts and a digest but no target-held-out quality diagnostic. The
bounded proposed next change is isolated calibration observability v3 using
leave-one-target-group-out SVR diagnostics and six fixed, non-correcting
verification anchors. It is not implemented or authorized by the review. Do
not use pilot targets to fit a correction, optimize framerate first, contact a
phone, or change the active tracker.

**Calibration-observability v3 follow-up:**
`gaze-mgazenet-calibration-observability-v3.md` is now the current continuation
point. After explicit software authorization, the isolated benchmark adopted
the active NewsMead procedure's 4×4 16-point geometry, near-centre repeat and
five held-out validation positions while retaining MGazeNet's incompatible
258-value inputs and RBF SVRs. It performs sixteen whole-target-held-out folds
of 675 training rows each, then fits all 720 rows and records six diagnostic-only
checks. The numeric schema/scorer retain no features or personal model and
assign no gate. All host verification passed. After separate authorization, the
exact signed APK pair received a setup-only A56 check: all four harness tests
passed in 2.976 seconds with CAMERA runtime grant false and effective app-op
`ignore`; no record was created and the packages were force-stopped. No camera
or personal calibration was used. The first matching G991B check then retained
a 3/4 failure: its layout resolved the four-way near-centre tie as `fit_10`
instead of the frozen `fit_6`. The isolated controller now selects `fit_6`
explicitly; the active tracker remains unchanged. After all 147 JVM and 49
Python tests passed again, corrected app hash
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`
passed all four G991B setup tests in 2.629 seconds with CAMERA still denied, no
record, and processes stopped. Since the earlier A56 check used the pre-repair
app, the corrected exact pair was then rechecked on the A56: all four tests
passed in 2.984 seconds with CAMERA denied, no record, and processes stopped.
Both phones now pass the same deterministic build. The software and setup-only
boundary is complete. A new camera-free `CalibrationAuditNativeTest` then ran
the real OpenCV path on the A56: sixteen whole-target folds of 675 rows produced
45 finite predictions each in 4,899.515 ms, and the final 720-row fit completed
in 316.651 ms. Cleanup passed, CAMERA stayed denied, no record was written and
processes were stopped. The matching test APK hash is
`b3d1e229f9b181faab93e503dd2ffa10870cf5dae3d5f6699f6fd0380c43621f`.
The same test then passed on the G991B: the sixteen-fold audit took 3,300.128 ms,
the final fit took 226.245 ms and total test time was 3.608 seconds. CAMERA was
denied before and after, no record or personal data was produced, cleanup
passed, and both packages were force-stopped. The historical app-op `allow`
entry did not advance. The two-phone native-execution checkpoint is complete;
these timings are descriptive and establish no speed or accuracy gate. The next
boundary is a camera-free draft and freeze of the v3 collection protocol.
CAMERA grant or any participant run remains separately unauthorized.

**Calibration-observability v3 protocol follow-up:**
`gaze-mgazenet-calibration-observability-v3-protocol.md` is now the current
continuation point. The protocol was frozen camera-free before any v3
participant data. It predeclares exactly four fresh-calibration development
sessions in alternating device order: G991B round 1, A56 round 1, A56 round 2,
then G991B round 2. All started attempts and unfavorable results remain; there
is no automatic replacement, early scoring, threshold, correction or promotion.
The primary comparison is the target-balanced mean of target-median absolute
vertical line error for the 16 whole-target LOO folds versus the five held-out
post-fit validation points, kept separate per session and compared within each
phone. The `fit_6` repeat remains a separate drift observation. The disclosed
severe-fatigue context is recorded, never subtracted or used for exclusion.
No phone was contacted while freezing the protocol. Each participant session
and CAMERA grant requires new explicit authorization; after all four attempts,
the next boundary is camera-free analysis.

**Calibration-observability v3 collection follow-up:**
`gaze-mgazenet-calibration-observability-v3-results.md` is now the current
continuation point. Frozen sequence 1, G991B `g991b_v3_r1`, completed under the
declared no-glasses, approximately 30 cm, medium-brightness, lighted-room,
daytime and mild-fatigue conditions. The participant retrospectively reported
an approximately one-second attention lapse at one unidentified target; retain
it as context, with no exclusion, correction or rerun. Raw session
`1788944307040_7525fa1f-cca6-4112-afcb-ebdae2788a34` is hash-verified at
`fe2f2edc9750d4761b6027f570004ad17a8fbaaa72fcacffca55a0e9dbad0403`.
It was preserved without scoring or exposing performance values until the
four-session collection completed. CAMERA was re-denied, both packages were
force-stopped and the process was absent. The next frozen attempt is A56
`a56_v3_r1`; do not score early or contact that phone without separate
authorization.

A56 `a56_v3_r1` then completed under the same declared no-glasses,
approximately 30 cm, medium-brightness, lighted-room, daytime and mild-fatigue
conditions, with no new discomfort or change reported. Raw session
`1788948001184_f08c9240-525c-44d7-b63a-b49d93785da1` is hash-verified at
`4fdf42813ee85f7335dc4e47928c3809f49b2924ece860ad0d491b6ff131a680`.
It too was retained unscored until collection ended. CAMERA was re-denied, both
packages were force-stopped and the process was absent. Two of four frozen
attempts are complete. The next attempt is A56 `a56_v3_r2` after the
between-round interval and separate authorization; G991B round 2 remains last.

A56 `a56_v3_r2` completed more than three hours after its first round. The
session used the same no-glasses, approximate-distance, medium-brightness and
lighted-room conditions at night. The participant described fatigue afterward
as “a little more normal in terms of fatigue, not as mild anymore”; retain that
ambiguous wording verbatim without exclusion or correction. Raw session
`1788959696940_91ff6a25-97dd-4dc4-8e93-0d163d9d00cf` is hash-verified at
`6b24ebd023bd479a8dd35402ffe71e988506d7135e261c8fb12c446073ef2c7c`.
It was retained unscored until collection ended. CAMERA was re-denied, both
packages were force-stopped and the process was absent. At that checkpoint
three of four attempts were complete; only G991B `g991b_v3_r2` remained before
the frozen camera-free scoring and comparison.

**Calibration-observability v3 analysis follow-up:**
`gaze-mgazenet-calibration-observability-v3-analysis.md` is now the current
continuation point. G991B `g991b_v3_r2` completed under the same nighttime and
fatigue context as A56 round 2; raw hash is
`c9426c6c8f4a2e808ade80c88a9f8fc0cd7df9ace7366c9a812fdd18fbf6df46`.
CAMERA was re-denied and processes were stopped. All four reports were scored
only after collection ended. Primary LOO versus five-target held-out vertical
error was G991B R1 0.361/0.845 lines, A56 R1 0.611/0.391, A56 R2
0.582/1.793, and G991B R2 0.330/0.686. G991B round changes agreed, but A56
LOO improved by 0.028 line while held-out error worsened by 1.402 lines. The
four-session Spearman value is -0.2. The LOO diagnostic therefore failed to
replicate as a quality screen and must not receive a threshold. Three sessions
remain encouraging stationary evidence, but the A56 round-2 failure prevents a
reliability or promotion claim. A direct five-point post-fit screen is the only
bounded next candidate; it requires a camera-free threshold/confirmation
proposal and new authorization. No additional collection or active-tracker
change is authorized.

**Direct-validation screen follow-up:**
`gaze-mgazenet-direct-validation-screen-protocol.md` is now the current
continuation point. The camera-free decision rejects LOO as a gate and freezes
a development-only five-point vertical screen: all five targets complete, at
least ten coordinate outputs each, target-balanced mean target-median error at
most 1.0 line, and no target median above 1.2 lines. Retrospective classification
is G991B R1 fail, A56 R1 pass, A56 R2 fail and G991B R2 pass; it is not
confirmation because the rule was chosen after inspecting those reports. The
reproducible evaluator leaves the accuracy gate null, assigns no promotion and
passes the 61-test MGazeNet Python suite. A separate confirmation design is
frozen, but its Android flow is not implemented. No phone was contacted and no
CAMERA, participant session or active-tracker action is authorized. The next
bounded step requires explicit approval for isolated confirmation-software and
host-only verification; device setup and participant collection remain later
authorization boundaries.

**Direct-validation confirmation device-check follow-up:**
`gaze-mgazenet-direct-validation-confirmation-device-check.md` is now the current
continuation point. After explicit software-only authorization, the isolated
benchmark added a distinct 16-fit/5-screen/20-confirmation flow. The five-point
result is sealed and hidden before the independent blocks, and passing and
failing screens both continue through the same two-sweep sequence. Numeric-only
reporting, a hash-verifying collector, an independent scorer and the frozen
four-session comparator are implemented. All 153 JVM and 74 Python tests pass;
the rebuilt debug app/instrumentation APK hashes are
`95cfc1c457955131e0271ae81472afd66d90a5006556d98fd05d53570410e73d` and
`9e80c0c8d542be19a66ebcfe9e537475fb87200042838681eb1c0adef52b6bbb`.
Production and instrumentation now share the exact confirmation measurement
layout. The six-test camera-free setup/layout suite passes on both the A56 and
G991B with CAMERA granted=false/effectively ignored, no confirmation directory,
and stopped processes afterward. The software and two-phone camera-free
checkpoint is complete. The next frozen collection attempt would be A56
confirmation 1, forward then reverse, but participant collection and CAMERA
grant remain a later explicit decision. `app/` remains unchanged.

**Direct-validation confirmation collection follow-up:**
`gaze-mgazenet-direct-validation-confirmation-collection.md` is now the current
continuation point. After explicit participant/CAMERA authorization, A56
`a56_confirmation_1` completed forward-then-reverse under the declared
no-glasses, approximately 30 cm, medium-brightness, lighted-room, daytime and
“more fatigued” context. Its raw session/hash are
`1788996966439_c741e1b1-4887-4c01-9527-b58228a44b57` /
`a498f3584142ae5f69e6b0f090b543684f353064be8c87de8ef78852a190986a`.
After approximately 17.36 minutes, G991B `g991b_confirmation_1` completed the
required reverse-then-forward order under the same declared conditions. Its raw
session/hash are `1788998305355_3d94ff60-2cd2-4969-97ce-57d8903b7eaa` /
`b9e42e276239b9f1dc1dbb3041a91d03c7e1eeadfc0c474aa8a439bb1b4ad2d6`.
After approximately 18.24 minutes between rounds, G991B
`g991b_confirmation_2` completed the required forward-then-reverse order under
the same conditions. Its raw session/hash are
`1788999691168_0203775a-cb4e-4528-a1f4-875fc5137f09` /
`d79a86e2a1ea70e89186856f204ccbeb7bb70cd5ba9fe6887600dccfd9202011`.
All three records are complete and hash-verified but unscored, with spatial
results unexamined. CAMERA was re-denied and packages were force-stopped after
each. Three of four attempts are complete. After at least ten minutes away from
the target task, the last frozen attempt is A56 `a56_confirmation_2`, reverse
then forward, with separate authorization. Never stage ignored
`diagnostics-local` evidence.

**Direct-validation confirmation analysis follow-up:**
`gaze-mgazenet-direct-validation-confirmation-analysis.md` is now the current
continuation point. A56 `a56_confirmation_2` completed reverse-then-forward
under the same declared conditions after approximately 15.14 minutes; raw
session/hash are `1789000895500_8f468e21-5e04-4a6a-a46a-c75b2001a088` /
`420c99582445feff2a87521ecadc6b6b3dbe23ded27ac3b0bc4fab3d10cd3ca5`.
Only after all four hashes were preserved, the frozen scorer classified every
screen and every independent confirmation as fail: four true rejections, zero
true acceptances, zero false acceptances and zero false rejections. The frozen
comparator therefore returns `inconclusive`, not support for the proposed next
model comparison. Every run passed coverage, but every run exceeded the
1.2-line regional guard. The current MGazeNet screen is not validated and the
active tracker remains unchanged. Do not tune thresholds, add/replay attempts
or infer that MGazeNet was compared head-to-head with the active model.

**Current-versus-MGazeNet evidence-synthesis follow-up:**
`gaze-current-vs-mgazenet-evidence.md` is now the current camera-free
continuation point. It inventories all thirteen accepted complete current
eye-local 16-point calibrations, separates the eleven legacy pre-coordinate-
repair records from the two clean `screen_px_v1` sessions, and compares the
latter with all eight MGazeNet sessions that reused the same sixteen-fit/five-
held-out physical geometry. On the closest normalized stationary measure, the
MGazeNet distribution is descriptively better: median session target-balanced
mean error is 0.721 lines across eight MGazeNet sessions versus 1.694 across the
two clean current sessions; the A56-only MGazeNet value is 0.910. Median session
worst-target error is 1.620 lines for MGazeNet, 1.879 for A56-only MGazeNet and
3.183 for the current post-repair set. The fractional target pattern matched,
but MGazeNet's physical viewport was narrower. A separate near-matched A56
centre-target comparison in raw pixels also leans modestly toward MGazeNet:
136.410 px median across four sessions versus 162.379 across the two current
sessions. These unequal, noncontemporaneous samples are not a matched model-
selection experiment, and MGazeNet has no known-target reading result.
Therefore the active system remains unchanged procedurally, not because it was
shown superior. The prior `inconclusive` label applies to validation of the
five-point screen as a selector, not to a head-to-head model outcome. No phone,
camera, participant run, threshold, model or tracker change occurred during the
synthesis.

**NewsMead integration handoff follow-up:**
`gaze-mgazenet-newsmead-integration-handoff.md` is now the current continuation
point for a new chat. The user chose to make MGazeNet the primary gaze path on
the dedicated `mgazenet-feasibility` branch instead of adding a permanent
runtime research-backend selector. The current implementation remains preserved
on `gaze-pipeline-improvements`; immediately before the handoff,
`git diff gaze-pipeline-improvements..HEAD -- app` was empty. The integration
must use a distinct hash-bound MGazeNet calibration store, never load or erase
the current mapper calibration, preserve full-screen coordinate/AOI/RSI
contracts, retain no frames/crops/landmarks/feature rows, and verify synthetic
SVR persistence plus host builds before any phone action. The handoff itself
does not authorize `app/` edits, installation, CAMERA or participant data; the
new chat should receive explicit software authorization first.

## Goal and authority

Find the best feasible on-device gaze approach under the project's actual accuracy, FPS,
device, privacy, and researcher-time constraints. The user challenged a premature conclusion
that improvements were exhausted and described native MGazeNet as promising. The latest
explicit authorization is **prepare the handoff**. No MGazeNet implementation, installation,
camera session, calibration, or model promotion has been performed or authorized by that request.

Start the next task with a read-only feasibility audit. Present its bounded implementation
proposal before changing the app. Do not treat this document as advance permission for phone
actions or an open-ended model-training project.

## Why this route, and what it is not

The historical GazeFollower integration used a laptop webcam and streamed laptop-screen
coordinates to the phone (`tools/gazefollower_stream.py`). That arrangement was removed for
field-study logistics, not because its model had been tested natively on Android and failed.
Native MGazeNet would use the phone camera and local inference: no WiFi bridge or laptop camera.
The review found no documented native MGazeNet gate in the project evidence.

Public GazeFollower source contains an MGazeNet estimator and pretrained `base.mnn` (GitHub
reports 5.96 MB). It uses eye and face appearance plus crop geometry, retaining information
that the current two averaged landmark features omit. The related smartphone research includes
Android/MNN deployment. These are reasons to test feasibility, not proof of better gaze.
Sources below were inspected on 2026-09-06; pin exact revisions before implementation.

MGazeNet is **not** the previously rejected MobileGaze/L2CS angular model. It is also not the
rejected compact 468-face/two-iris landmark pipeline. Do not silently substitute one for another.

## Exact first action - no new calibration required

1. Recheck repository status and the source/evidence anchors below. Preserve all dirty work.
2. Audit and pin the intended public model, source revision, model hash, and usage terms.
   The historical Python requirements pin `gazefollower==1.0.2`; reviewed upstream `main`
   is not automatically that version or the exact historically tested weights.
3. Write down the exact preprocessing contract: frame rotation/mirroring, RGB/BGR, left/right
   eye identity and flip, crop bounds/padding, resize interpolation, tensor layouts, numerical
   normalization, and rectangle coordinate units. Inspect executed code, not comments alone.
4. Verify output shape/meaning and calibration. Upstream exposes the complete output as
   features and its first two values as raw coordinates; its default calibration uses two
   OpenCV RBF support-vector regressors. Do not plug these outputs into the current eye-local
   calibration or call a new ridge fit equivalent to upstream calibration.
5. Identify an Android inference/build path and its total cost, including face/eye localization.
   Produce a short feasibility decision, unresolved requirements, and a bounded benchmark plan.
   Do not download participant datasets, contact authors, or train a new network automatically.

Expected first deliverable: a verified model/preprocessing/calibration manifest and a go/no-go
proposal for an isolated benchmark, not a request for another 16-point/9-point run.

## Proposed gates after implementation approval

- **Numerical correctness:** compare reference and Android preprocessing/inference on identical
  permitted inputs. Use synthetic/public appropriately licensed fixtures, or explicitly approved
  in-memory comparison; never retain participant camera frames. Test boundaries, eye flipping,
  colour conventions, layout, and coordinate units before interpreting accuracy.
- **Full-path speed on both phones:** measure capture-to-output latency, sustained completion
  FPS, dropped frames, warm-up, and thermal conditions. Separate isolated-model time from total
  face detection/cropping/model/calibration cost. A benchmark must not touch the real calibration
  store or feed reading/AOI/RSI. Confirm authority before installation or starting its camera.
- **Accuracy only after feasibility:** plan a fresh matched reference/candidate comparison,
  each with its own compatible calibration and independent held-out targets. Retain failures and
  preregister replication. Measure natural modest posture changes and known-target reading,
  vertical median/P95/max in actual line heights, signed regional errors, 2-D error, coverage,
  and sustained FPS. Five held-out targets give a very coarse tail estimate, not precise P95.
- **No test-set tuning:** fix preprocessing/calibration choices before held-out evaluation;
  split whole targets/blocks/sessions, not adjacent frames of one fixation. If same-frame dual
  inference is used for paired numeric evidence, benchmark each path alone for speed.
- Agree numerical acceptance criteria before trials. The historical 1.2-line all-target screen
  is provisional, not a validated reading requirement. There is no user-approved 15-18 FPS floor.

## Important uncertainties and alternatives

- The public repository distinguishes a model trained on 7 million images from a larger model
  available by request. Do not assign the latter's results, or paper results, to `base.mnn`.
- The smartphone paper reports about 12.19-17.20 ms Android model inference and about 16 ms
  preprocessing separately. Neither is measured on our current phones/build; 30 FPS is not assured.
- The default upstream face alignment uses refined MediaPipe FaceMesh; adding MGazeNet to our
  slow 478-point path might worsen throughput. Upstream also provides a BlazeFace alignment
  variant, but switching crop generators needs numerical/accuracy evaluation, not assumption.
- Public source lists CC BY-NC-SA 4.0. Verify model/runtime terms and required notices before
  inclusion; public availability is not unrestricted use. No legal conclusion is asserted here.
- Historical laptop accuracy used a different camera/setup and is not a paired phone baseline.
- One researcher on two phones establishes neither population generalization nor a causal
  device effect. Avoid constants fitted to this person's known failures. Participant calibration
  is expected; a long per-participant experimental A/B workflow is not acceptable logistics.
- Proper limited pose compensation remains the second candidate. Current eye-local geometry
  handles image-plane roll/translation/scale, not explicit pitch/yaw or perspective compensation.
  The posture envelope is shadow-only, not compensation. Collect independent within-target pose
  variation before fitting extra coefficients; simply adding pose to row-major calibration can
  learn target/time confounds. Verify pose geometry and overhead rather than assuming accuracy.
- Alternatives reviewed: MobileGaze/L2CS already failed in its previous form; iTracker requires
  legacy conversion; SAGE and the reviewed CVPR 2025 cross-task project's public materials did
  not establish a ready mobile implementation. 3DGazeNet has public code/model links, but its
  Android cost and screen calibration remain unverified. These are not declared impossible.
- Do not use expected reading order, line snapping, or scaffold behaviour as gaze ground truth.
  Such priors can hide genuine regressions and contaminate the thesis outcome measures.

## What the previous experiments actually rule out

| Evidence | Keep this conclusion | Do not infer |
| --- | --- | --- |
| E-034 telemetry control | OFF did not restore accuracy/FPS materially | Every instrumentation or performance question is resolved |
| E-038 separate-eye linear mapper | That prospective mapper failed twice | Per-eye information has no value in other representations |
| E-043 posture envelope | Its flag did not identify high-error targets | Head-pose compensation was tested and failed |
| E-045 eye-local geometry | Retained after two favorable prospective runs | Generalizable reading-line accuracy is certified |
| E-053 coordinate repair | Real view/screen-origin bug repaired | Remaining errors are all estimator limitations |
| E-056 performance screen | Those implementations did not recover throughput | An 81.6 ms trace or a source-inequivalent low-resolution path exhausts optimization |
| E-070-E-073 compact calibration | Exact candidate repeatedly missed the accuracy gate | Current baseline was proven superior in fresh matched sessions |
| E-074 fixed translation | Full constant shift gives mixed benefit and tails | Time-dependent or measured-pose correction was disproved |

E-074 changes every fit feature by the same vector; it is not per-point temporal dewarping.
The rejected results stay in the record. Do not tune variants against the same held-out answers.
Also do not add automatic high-drift rejection: a prior 201 px-drift calibration produced the
best live result (0.76-line median/1.04 maximum). Drift is evidence, not a validated quality gate.

## Workspace, runtime, and preserved evidence

- Work only in `C:\Users\USER\OneDrive\Desktop\newsmead` for app changes, not the old prototype.
- Branch verified: `gaze-pipeline-improvements`; HEAD `6104d56`, compact research harness.
  Earlier commits: `03c74f3` hybrid harness; `74f43c4` screen-coordinate repair/performance record.
- Active runtime: MediaPipe 478-point GPU with CPU fallback; 640x480 request, upright 480x640;
  `eye_local_width_average_v1`; two-eye average; standardized six-term ridge quadratic (ridge 1,
  2.5-z soft extension), temporal filters, separate affine correction, posture shadow only.
  Preserve target timing/order, countdown, screen-coordinate/hash contracts, and telemetry control.
  Hybrid shadow is disabled. No native MGazeNet assets/runtime changes were made in this review.
- Last-checkpoint device IDs: A56 `R5CY40Y2C5W`; SM-G991B `R5CR60YSJ2X`. These are identifiers,
  not assertions of current connection, calibration freshness, installed APK, or phone state.
  No device was contacted while preparing this handoff. Verify hashes before later device work.
- E-054 references: `diagnostics-local/2026-09-04/coords-v1-a56/`, calibration JSON timestamps
  `20260904_024210` / `20260904_195333`, and corresponding reading JSONL files
  `20260904_024346_370` / `20260904_195507_232`. Within-one-line accuracy was 36.5% / 85.4%,
  with median/P95 line error 2/4 and 1/2 respectively. Both remain; neither proves repeatability.
- Compact/hybrid evidence: `diagnostics-local/2026-09-06/compact-face/` and `hybrid-shadow/`.
  Compact SM held-out vertical median/max: 1.75/2.22, 0.83/2.09 lines; A56: 2.89/4.76,
  3.31/6.80, 4.68/6.45. Faster candidate FPS does not erase these errors.
- E-074: `docs/gaze-end-pose-translation-screen.md`, derived JSON, analyzer and tests below;
  13 calibrations, 273 baseline prediction pairs reproduced exactly; mixed held-out changes.
- Historical verification, not rerun by this documentation task: compact checkpoint reports
  80 gaze-focused JVM tests; E-074 reports 27 offline analysis tests. Full Android unit suite
  has the previously reported unrelated `FirebaseTest.createAccount` failure.
- Local Java 17 exists at `C:\Program Files\Java\jdk-17`; the older documented JBR 17 path
  also exists. Avoid stale JDK 21 Kotlin daemons; use a consistent Java 17/in-process build.
  ADB: `C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### Dirty work and git boundary

At handoff preparation, no staged changes were present. Pre-existing tracked modifications:
`docs/calibration-design.md`, `docs/gaze-accuracy-handoff.md`, `docs/gaze-diagnostics-log.md`.
Pre-existing untracked paths:

- `docs/gaze-end-pose-translation-screen.md`
- `docs/gaze-end-pose-translation-screen-2026-09-06.json`
- `tools/gaze/screen_end_pose_translation.py`
- `tools/gaze/test_screen_end_pose_translation.py`
- `diagnostics-local/`, `tmp/`, `tools/gaze/__pycache__/`

This handoff adds this file and updates the three already-dirty documents plus a supersession
notice in `docs/gaze-model-replacement.md`. It does not erase the E-074 changes. Recheck status
on arrival; do not treat this snapshot as a clean checkout or migrate only committed files and
lose the new handoff. The user stages/commits themselves and wants Windows CMD suggestions,
not PowerShell, when requested. Never use a broad `git add .` that sweeps private data into git.

## Source and context reading order

After this brief, read E-038/E-043/E-045/E-053-E-056/E-070-E-075 as needed in
`gaze-diagnostics-log.md`; calibration design sections 0, 3-7; then `build-context.md` and
`gaze-model-replacement.md` with their historical-status qualifications. The actual thesis is
`Manuscript/Thesis Paper.pdf`: sections 4.2-4.4 (PDF pages 43-61 contain key constraints).
It targets adults 45+, uses line-level measurement, and prohibits retained camera frames.
Use `manuscript-implementation-alignment.md` for the circular gaze/RSI contamination warning.

Primary upstream sources inspected during the reassessment:

- [GazeFollower release/readme](https://github.com/GanchengZhu/GazeFollower): public/larger weights distinction and project terms.
- [Public base model](https://github.com/GanchengZhu/GazeFollower/blob/main/gazefollower/res/model_weights/base.mnn).
- [MGazeNet estimator](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/gaze_estimator/MGazeNetGazeEstimator.py): face 224x224, eyes 112x112, 12 rectangle values; inspect exact input/output contract.
- [Reference calibration](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/calibration/SVRCalibration.py).
- [MediaPipe alignment](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/face_alignment/MediaPipeFaceAlignment.py) and [BlazeFace alternative](https://raw.githubusercontent.com/GanchengZhu/GazeFollower/main/gazefollower/face_alignment/BlazeFaceAlignment.py).
- [Smartphone model research/code](https://github.com/GanchengZhu/PhoneRealTimeGazeEstimation) and [paper](https://onlinelibrary.wiley.com/doi/full/10.1155/2024/2644725): Android speed and calibration experiments, not our phone results.
- [MNN runtime](https://github.com/alibaba/MNN).
- [MediaPipe pose/landmarker capabilities](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker): optional face transforms do not themselves establish gaze compensation accuracy.

No claim of a best possible model, expected success probability, or population accuracy is
justified yet. The decision is which bounded experiment has the strongest next-step rationale.

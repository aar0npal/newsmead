# MGazeNet calibration-observability v3 analysis — 2026-09-09

Status: the frozen four-session development collection and camera-free analysis
are complete. The leave-one-complete-target-group-out diagnostic failed to
replicate as a calibration-quality screen. MGazeNet remains an isolated research
candidate because three sessions retained encouraging stationary accuracy, but
the four sessions do not justify a threshold, promotion, active-tracker change
or natural-reading claim. Read after
`gaze-mgazenet-calibration-observability-v3-results.md`.

## Decision

Do not use the 16-fold LOO value to accept or reject an MGazeNet calibration.
It moved in the same direction as held-out accuracy across the two G991B rounds,
but in the opposite direction across the two A56 rounds. Across all four
sessions, the predeclared rank association between LOO and held-out primary
error was `-0.2`. With only four sessions this coefficient is descriptive, but
its sign and the A56 contradiction provide no basis for threshold development.

The post-fit five-point check itself remains informative direct validation. It
clearly exposed the poor A56 round-2 session, but it has not been validated as a
prospective gate. If work continues, the bounded next proposal is a camera-free
freeze of a direct five-point post-fit calibration screen followed by separate
confirmation data. Do not choose or claim such a threshold from these four
sessions without labeling it development-only, and do not run confirmation or
integrate MGazeNet without new authorization.

## Integrity and provenance

All four raw reports passed the independent v3 manifest, pipeline, geometry,
whole-target isolation, timing, privacy and no-decision checks. Every report is
complete, contains 720 training rows, sixteen 45-row LOO folds, all six
verification blocks, and a distinct calibration digest. All five held-out
targets contributed coordinates in every session; there were no verification
nulls or empty blocks.

| Session | Raw report SHA-256 | Derived summary SHA-256 | Calibration digest |
| --- | --- | --- | --- |
| G991B R1 | `fe2f2edc9750d4761b6027f570004ad17a8fbaaa72fcacffca55a0e9dbad0403` | `04cc7b446fe6892eb5697a2f784aa19185993fb74e024412497192b92e75b046` | `fc5471a7177c85e3165b73315ae560848b5b4f5302a68a1b0bb9eefa7218b913` |
| A56 R1 | `4fdf42813ee85f7335dc4e47928c3809f49b2924ece860ad0d491b6ff131a680` | `fa2c2fa04f2f423a8c85af7227fc8f9ceb42ca69afc131979eb9e5ec70e945f9` | `85d9546891c78af5025c5d5d4a634fd73cc1019db2aff30dd9fee04b4da6eb77` |
| A56 R2 | `6b24ebd023bd479a8dd35402ffe71e988506d7135e261c8fb12c446073ef2c7c` | `106b533a67e9852bfe4177f74d2f3e8a8d3f35bf757e9ff421a1257a21f86b7d` | `9f6b6fac88316ddc668f162ed2da10adb98e38ace7ad858d87ebcf31a2fae837` |
| G991B R2 | `c9426c6c8f4a2e808ade80c88a9f8fc0cd7df9ace7366c9a812fdd18fbf6df46` | `3f9f23f998ad90c9436d75e3e0039eb699e3b4ca098f2a8cfac813bba500fc31` | `cf3c4d4e8d68fd827ebd7dee4a178d11bb80576972f1fec63a562ccba468bdb0` |

The reproducible four-session comparison is retained as
`diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/comparison.json`
with SHA-256
`8c64431220510bab739f574af305fd1fa77a4361ffa51a730d66deb0a8aa46f0`.
`compare_calibration_audits.py` binds the frozen order and expected device,
requires all sixteen LOO and five held-out targets for the primary comparison,
and emits no gate or promotion decision. All 57 MGazeNet Python tests pass.

## Primary result

Both primary values are target-balanced means of target-specific median
absolute vertical error in physical line heights. Lower is better. LOO uses all
sixteen complete target-held-out folds; validation uses only the five post-fit
held-out targets and excludes the `fit_6` repeat.

| Frozen order | LOO primary | Five-point held-out primary |
| --- | ---: | ---: |
| G991B R1 | 0.361 lines | 0.845 lines |
| A56 R1 | 0.611 lines | 0.391 lines |
| A56 R2 | 0.582 lines | 1.793 lines |
| G991B R2 | 0.330 lines | 0.686 lines |

| Within phone, R2−R1 | Δ LOO | Δ held-out | Direction agrees? |
| --- | ---: | ---: | --- |
| G991B | -0.031 lines | -0.159 lines | Yes |
| A56 | -0.028 lines | +1.402 lines | **No** |

The LOO metric judged A56 round 2 slightly better than A56 round 1 while the
independent held-out error became 1.402 lines worse. This is not a marginal
threshold miss: the five-target held-out median rose from 0.391 to 1.793 lines.
The predeclared four-session Spearman association is `-0.2`. These observations
fail the protocol's two-phone directional-replication condition.

## Target-balanced secondary results

Each magnitude cell is target-balanced median/P95/maximum. Bias is the mean of
target-specific signed coordinate bias. G991B line height is 118 px and A56 line
height is 98 px. LOO and direct held-out evidence remain separate.

| Session | Evidence | abs X px | abs Y px | 2-D px | vertical lines | signed X/Y bias px |
| --- | --- | --- | --- | --- | --- | --- |
| G991B R1 | LOO | 65.1/127.4/155.6 | 42.6/97.1/129.5 | 89.3/154.8/189.4 | 0.361/0.823/1.098 | +8.5/-3.9 |
| G991B R1 | held-out | 42.3/89.3/98.8 | 99.7/143.5/156.8 | 127.0/169.7/179.1 | 0.845/1.216/1.328 | +37.7/-86.3 |
| A56 R1 | LOO | 57.8/119.7/167.1 | 59.8/128.2/201.8 | 96.2/163.1/242.9 | 0.611/1.308/2.059 | -4.0/-1.7 |
| A56 R1 | held-out | 80.2/134.1/154.5 | 38.3/97.6/118.1 | 94.8/159.3/188.2 | 0.391/0.996/1.205 | +70.0/+15.7 |
| A56 R2 | LOO | 53.5/114.2/127.0 | 57.1/108.0/144.4 | 94.4/154.6/179.1 | 0.582/1.102/1.473 | +0.9/+3.4 |
| A56 R2 | held-out | 40.7/73.8/88.2 | 175.7/221.5/233.8 | 183.1/234.7/241.6 | 1.793/2.260/2.385 | +26.2/-171.5 |
| G991B R2 | LOO | 53.4/126.2/155.2 | 39.0/97.6/130.6 | 76.7/154.9/185.7 | 0.330/0.827/1.107 | -0.8/-5.6 |
| G991B R2 | held-out | 45.5/110.2/125.9 | 81.0/140.1/153.5 | 104.3/167.7/177.1 | 0.686/1.188/1.301 | +3.4/+31.0 |

Three sessions have target-balanced held-out median vertical error from 0.391
to 0.845 lines. Those stationary results are encouraging and broadly align
with the better historical NewsMead gaze runs. A56 round 2 is materially worse
at 1.793 lines, with a coherent -171.5 px target-balanced Y bias. The variation
prevents a reliability or natural-reading claim.

## All held-out and drift blocks

`n` is the number of admitted coordinates; every block has zero nulls. Errors
are block median/P95/maximum absolute vertical line heights. Output age is
median/P95 milliseconds. The drift repeat is reported separately and never
mixed into the five-target primary.

| Session | Block | n | mean dy px | vertical lines | 2-D median px | output age ms |
| --- | --- | ---: | ---: | --- | ---: | --- |
| G991B R1 | drift `fit_6` | 18 | +59.1 | 0.490/0.780/0.880 | 79.1 | 222.5/268.6 |
| G991B R1 | validation 1 | 17 | +9.7 | 0.230/0.500/0.580 | 74.1 | 226.0/271.9 |
| G991B R1 | validation 2 | 18 | -18.7 | 0.210/0.690/0.700 | 95.9 | 220.9/273.9 |
| G991B R1 | validation 3 | 20 | -44.3 | 0.420/0.730/0.950 | 65.6 | 219.3/269.9 |
| G991B R1 | validation 4 | 19 | -229.4 | 1.990/2.360/2.590 | 235.2 | 214.0/272.8 |
| G991B R1 | validation 5 | 18 | -148.9 | 1.370/1.800/1.820 | 164.2 | 230.2/271.6 |
| A56 R1 | drift `fit_6` | 21 | +59.2 | 0.590/2.690/4.220 | 112.2 | 223.4/250.9 |
| A56 R1 | validation 1 | 22 | -25.6 | 0.470/1.250/1.330 | 109.9 | 222.5/236.7 |
| A56 R1 | validation 2 | 19 | +34.4 | 0.180/0.990/1.780 | 40.8 | 231.3/271.4 |
| A56 R1 | validation 3 | 17 | -19.0 | 0.240/0.510/0.610 | 99.5 | 238.9/269.5 |
| A56 R1 | validation 4 | 16 | +24.6 | 0.430/0.870/0.900 | 106.4 | 251.5/287.8 |
| A56 R1 | validation 5 | 21 | +64.0 | 0.640/1.350/1.400 | 117.6 | 224.1/236.5 |
| A56 R2 | drift `fit_6` | 21 | -142.6 | 1.590/1.880/1.930 | 168.3 | 193.6/238.5 |
| A56 R2 | validation 1 | 21 | -178.0 | 1.800/2.220/2.410 | 202.7 | 183.0/239.2 |
| A56 R2 | validation 2 | 23 | -113.9 | 1.140/1.520/1.550 | 112.8 | 198.3/229.8 |
| A56 R2 | validation 3 | 21 | -138.5 | 1.410/1.730/1.780 | 142.5 | 212.8/236.9 |
| A56 R2 | validation 4 | 17 | -217.7 | 2.360/2.780/2.930 | 232.2 | 240.6/253.5 |
| A56 R2 | validation 5 | 22 | -209.5 | 2.260/3.050/3.250 | 225.2 | 199.9/249.3 |
| G991B R2 | drift `fit_6` | 15 | +38.5 | 0.280/0.910/0.990 | 58.6 | 237.4/355.5 |
| G991B R2 | validation 1 | 16 | +84.0 | 0.680/1.250/1.350 | 121.4 | 236.1/312.4 |
| G991B R2 | validation 2 | 18 | +39.0 | 0.370/0.770/0.880 | 57.3 | 229.8/262.5 |
| G991B R2 | validation 3 | 17 | -106.2 | 0.970/1.680/1.740 | 125.4 | 231.8/290.4 |
| G991B R2 | validation 4 | 18 | +2.4 | 0.260/0.690/0.830 | 62.8 | 233.0/267.0 |
| G991B R2 | validation 5 | 17 | +135.8 | 1.160/1.550/1.690 | 154.5 | 239.1/273.8 |

A56 round 1 shows why the drift repeat must remain separate: its drift maximum
was 4.22 lines while its five held-out target-balanced median was the best of
the four sessions. A single repeat is not a validated gate either.

## All LOO target folds

Each cell is the fold's median/P95/maximum absolute vertical error in line
heights. Every fold contains exactly 45 held-out rows. The independent summary
JSON files retain the full signed X/Y, absolute X/Y, normalized and Euclidean
distributions for every fold.

| Fold | G991B R1 | A56 R1 | A56 R2 | G991B R2 |
| --- | --- | --- | --- | --- |
| `fit_1` | 0.06/0.12/0.19 | 0.53/1.34/1.47 | 0.14/0.44/0.75 | 0.12/0.31/0.51 |
| `fit_2` | 0.19/0.46/0.82 | 1.02/1.65/2.09 | 0.07/0.28/0.34 | 0.27/0.67/0.91 |
| `fit_3` | 0.19/0.59/0.69 | 0.22/0.60/0.83 | 0.27/0.46/1.83 | 0.18/1.45/1.70 |
| `fit_4` | 0.60/1.16/1.48 | 0.42/0.96/1.33 | 2.08/2.57/2.66 | 0.16/0.42/0.79 |
| `fit_5` | 0.43/1.00/1.24 | 0.91/1.98/8.35 | 0.41/0.80/1.03 | 0.69/1.70/1.80 |
| `fit_6` | 0.48/2.03/3.31 | 1.97/2.55/2.63 | 0.48/1.03/1.34 | 0.35/0.83/1.11 |
| `fit_7` | 0.27/0.61/0.73 | 0.30/1.18/1.38 | 0.35/0.88/1.15 | 0.38/1.28/1.78 |
| `fit_8` | 0.70/1.19/1.33 | 0.31/1.34/1.80 | 1.26/1.90/1.99 | 0.50/0.93/1.32 |
| `fit_9` | 0.43/0.99/1.21 | 0.91/1.75/3.21 | 0.51/1.25/1.81 | 0.49/1.03/1.25 |
| `fit_10` | 0.34/1.14/1.52 | 1.04/1.84/2.23 | 0.48/1.10/1.65 | 0.21/0.57/0.85 |
| `fit_11` | 0.28/0.62/0.89 | 0.28/0.97/1.26 | 0.52/1.49/2.16 | 0.32/0.87/1.19 |
| `fit_12` | 0.42/0.81/1.04 | 0.29/0.95/1.18 | 0.75/2.01/2.12 | 0.38/1.09/2.23 |
| `fit_13` | 0.52/1.03/1.14 | 0.40/0.66/1.14 | 0.16/0.40/0.56 | 0.84/1.16/1.21 |
| `fit_14` | 0.08/0.21/0.49 | 0.19/1.07/1.30 | 0.05/0.22/0.26 | 0.14/0.34/0.42 |
| `fit_15` | 0.07/0.30/0.45 | 0.36/1.08/1.48 | 0.23/0.68/1.33 | 0.04/0.16/0.20 |
| `fit_16` | 0.71/0.91/1.02 | 0.59/1.00/1.25 | 1.56/2.14/2.58 | 0.23/0.42/0.44 |

LOO contains real regional failures, but averaging them did not predict the
post-fit A56 round-2 shift. The ordered, adjacent-frame calibration rows and
model extrapolation between fit locations remain limitations even though each
fold correctly omits a whole target group.

## Collection health and timing context

Every practice and fit target reached 45 accepted rows. The only eligibility
rejection was one eye-area rejection at A56 round-1 `fit_5`. No target timed out
and no resource-close error occurred.

| Session | fit collection range ms | accepted-age median range ms | accepted-age P95 range ms | feature-dispersion range | analyzer busy drops |
| --- | --- | --- | --- | --- | --- |
| G991B R1 | 5,849–6,495 | 216.6–235.1 | 236.6–296.2 | 0.04–0.21 | 1,481/2,772 (53.4%) |
| A56 R1 | 5,158–6,765 | 204.2–243.1 | 222.0–273.3 | 0.03–0.10 | 2,238/3,557 (62.9%) |
| A56 R2 | 5,043–6,595 | 179.4–234.0 | 231.3–265.5 | 0.03–0.06 | 2,012/3,368 (59.7%) |
| G991B R2 | 5,977–6,774 | 218.2–237.1 | 235.9–291.7 | 0.03–0.07 | 1,424/2,708 (52.6%) |

G991B round-1 `fit_6` has the largest feature-dispersion scalar, 0.21, and its
LOO fold retains a 3.31-line maximum. The participant reported an approximately
one-second attention lapse at an unidentified target, so no causal match can be
made and no row or target is excluded.

A56 round 2 is more important diagnostically: it had no rejection, all targets
and verification blocks contributed, its feature dispersion was only 0.03–0.06,
and its accepted output ages were at least as good as the other sessions. Those
health fields did not flag its coherent held-out vertical error. Framerate or
missing-coordinate optimization therefore cannot rescue the failed calibration
screen.

## Validity limits and next boundary

The participant conditions and fatigue statements remain as recorded in the
collection-results document. The G991B round-1 attention lapse and ambiguous
later fatigue description cannot be causally separated from estimator,
calibration, posture or compliance error. No data are removed or corrected.

These are stationary instructed points from one participant on two devices.
They do not establish reading-event accuracy, a population estimate or device
superiority. The direct held-out results show useful spatial signal in three
sessions and a serious session-level failure in one. That is evidence to improve
calibration acceptance, not permission to promote MGazeNet.

No more participant collection is authorized by this analysis. The next step is
a camera-free decision on whether to freeze a direct five-held-out-point screen
for separate confirmation or stop the MGazeNet route. Do not tune the current
four reports, rerun A56 round 2, assign an LOO threshold, modify the active
tracker, grant CAMERA or begin natural-reading trials without a new explicit
proposal and authorization.

## Camera-free direct-screen follow-up

That decision is now frozen in
`gaze-mgazenet-direct-validation-screen-protocol.md`. The development-only
screen requires complete five-target coverage, at least ten admitted coordinates
per target, target-balanced mean target-median vertical error no greater than
one line and no target median above the historical provisional 1.2-line regional
reference. It retrospectively classifies G991B R1 and A56 R2 as failures and A56
R1 and G991B R2 as passes. Because the rule was selected after inspecting these
four reports, that split is development evidence only, not confirmation.

The independent evaluator and 61-test host suite are complete. It leaves the
accuracy gate null and makes no promotion decision. The next separately
authorized boundary is isolated confirmation-software implementation; no phone,
CAMERA, calibration, natural-reading or active-tracker action follows from this
analysis.

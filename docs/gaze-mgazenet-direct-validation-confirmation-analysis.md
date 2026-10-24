# MGazeNet direct-validation confirmation analysis — 2026-09-10

Status: all four frozen confirmation attempts are complete, hash-preserved and
independently scored. The preregistered comparison is **inconclusive**: all four
five-point screens rejected their calibrations and all four independent
ten-location confirmations also failed, yielding four true rejections but no
true acceptance. This does not support the proposed next model-comparison step,
does not prove MGazeNet worse than the active model, and does not authorize an
active-tracker change.

Read after `gaze-mgazenet-direct-validation-confirmation-collection.md`. No
threshold, target, timing, order, exclusion or scoring rule was changed after
collection. Spatial values were not inspected until all four complete raw
records and device-written hashes had been preserved.

## Evidence and integrity

All records use `mgazenet_direct_validation_confirmation_v1`, the exact checked
APK, the four frozen labels/devices/orders, distinct session and calibration
identities, 16 fit groups, five screen targets and twenty confirmation blocks.
All screen targets and confirmation blocks met the minimum coordinate count.
The scorer independently reconstructed every decision while keeping
`accuracy_gate_pass` null and `promotion_decision` equal to `not_evaluated`.

The participant declared the same conditions throughout: no glasses,
approximately 30 cm with ordinary closer/farther variation, medium screen
brightness, the same lighted room, daytime and more fatigue. Fatigue is context,
not a post-hoc exclusion or correction. The measured separations were about
17.36 minutes from attempt 1 to 2, 18.24 minutes between rounds, and 15.14
minutes from attempt 3 to 4, exceeding the frozen minima.

| Session | Raw report SHA-256 | Summary SHA-256 |
| --- | --- | --- |
| A56 confirmation 1 | `a498f3584142ae5f69e6b0f090b543684f353064be8c87de8ef78852a190986a` | `ff493f8cd14f93ca9262bf123a978ebf6b190e17188a9fb40961b7ef3d3fa57e` |
| G991B confirmation 1 | `b9e42e276239b9f1dc1dbb3041a91d03c7e1eeadfc0c474aa8a439bb1b4ad2d6` | `c1a9d0f99f56f8f9d2183bb95011f7f5fe7c743e52d5001b6be997d2f5924c4f` |
| G991B confirmation 2 | `d79a86e2a1ea70e89186856f204ccbeb7bb70cd5ba9fe6887600dccfd9202011` | `545075a09a2fec9dfc042c1aa07a4f8122b854939107645197db8eca9b24b6a1` |
| A56 confirmation 2 | `420c99582445feff2a87521ecadc6b6b3dbe23ded27ac3b0bc4fab3d10cd3ca5` | `d5e9e3680e8ee713eba6d636c8699e158be4a24e2f981422ba621a6c9011dc02` |

The frozen comparison file is SHA-256
`23ff145d31f32ff92905ba35d204c1c357853ef706404fc95f10c86e11b4a9f3`.
All raw, summary and comparison JSON files remain ignored under
`diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/` and
must not be staged.

## Five-point screen results

The screen required all five targets, at least ten coordinates per target,
mean target-median absolute vertical error at most 1.0 line and every target
median at most 1.2 lines. Mean P95 is reported but not thresholded.

| Session | Mean target median | Worst target median | Mean target P95 | Minimum n | Screen |
| --- | ---: | ---: | ---: | ---: | --- |
| A56 confirmation 1 | 1.128 lines | 1.984 lines | 1.558 lines | 17 | Fail — aggregate and regional |
| G991B confirmation 1 | 0.616 lines | 1.384 lines | 1.081 lines | 17 | Fail — regional |
| G991B confirmation 2 | 0.749 lines | 1.467 lines | 1.196 lines | 16 | Fail — regional |
| A56 confirmation 2 | 0.693 lines | 1.774 lines | 1.151 lines | 15 | Fail — regional |

Coverage passed in all four screens. Three sessions met the aggregate one-line
condition, but every session had at least one region above 1.2 lines. The screen
therefore rejected all four calibrations under the frozen conjunction.

## Independent ten-location results

The downstream rule combined each location's two sweeps, required all twenty
blocks and at least ten coordinates per block, then applied the same 1.0-line
aggregate and 1.2-line regional limits. P95 and line fractions are descriptive,
not extra gates.

| Session | Mean location median | Worst location median | Mean location P95 | Minimum block n | Exact line | Within one line | Confirmation |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| A56 confirmation 1 | 1.021 lines | 1.786 lines | 1.791 lines | 15 | 22.1% | 76.3% | Fail — aggregate and regional |
| G991B confirmation 1 | 0.663 lines | 1.578 lines | 1.254 lines | 10 | 46.5% | 85.9% | Fail — regional |
| G991B confirmation 2 | 1.203 lines | 1.684 lines | 2.140 lines | 12 | 10.6% | 65.4% | Fail — aggregate and regional |
| A56 confirmation 2 | 1.457 lines | 2.603 lines | 2.461 lines | 15 | 12.0% | 49.4% | Fail — aggregate and regional |

All twenty blocks and all ten independent locations contributed in every
session. The failures are therefore spatial-threshold failures, not missing
coverage. G991B confirmation 1 was the strongest downstream run and met the
aggregate rule, but its 1.578-line worst location failed the frozen regional
guard.

## Frozen comparison decision

| Classification | Count |
| --- | ---: |
| True acceptance | 0 |
| True rejection | 4 |
| False acceptance | 0 |
| False rejection | 0 |

The five-point screen and independent confirmation agreed in all four sessions,
so no false acceptance was observed. The frozen decision, however, required at
least one true acceptance and one true rejection with zero false accepts to
return `supports_next_proposal`. With no accepted calibration, the result is
`inconclusive`, exactly as preregistered.

This does not establish that MGazeNet is worse than NewsMead's active gaze
model: the confirmation was not a matched head-to-head comparison. It does show
that this MGazeNet configuration produced no session satisfying the frozen
regional stationary-accuracy requirement in this four-run, fatigued,
single-participant evidence set. The result cannot validate exact-line reading,
word selection, scrolling, population reliability or production use.

## Verification and decision boundary

The 74-test MGazeNet Python suite passed after scoring. Each comparison-bound
summary hash matches the corresponding output file. `git diff --check` passes,
the active `app/` tracker remains unchanged, CAMERA is denied on the last-used
A56, and both benchmark packages were stopped after collection.

The frozen result does not support proceeding automatically to a new
participant known-target current-model/MGazeNet comparison. Do not lower the
regional limit, discard fatigued sessions, add repetitions, tune against these
targets or call the current MGazeNet screen validated. Leaving the active gaze
system unchanged is an authorization and measurement-validity boundary, not a
finding that it outperformed MGazeNet.

A subsequent camera-free synthesis of the retained current-model and MGazeNet
16-point evidence is in `gaze-current-vs-mgazenet-evidence.md`. On the closest
normalizable five-target stationary metric, the recorded distribution leans in
MGazeNet's favor, including an A56-only comparison. It remains observational,
not a contemporaneous paired test; the fractional target pattern matched but
the actual viewports differed. MGazeNet also still lacks known-target reading
evidence. The `inconclusive` result in this document classifies the proposed
screen as a selector; it must not be misreported as a current-model victory.

Any further MGazeNet work requires a separately justified, materially distinct
prospective hypothesis and a newly frozen protocol before new participant data.
It must not be presented as continuation or repair of this confirmation set.

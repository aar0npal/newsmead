# Current NewsMead versus MGazeNet evidence synthesis — 2026-09-10

Status: this camera-free audit inventories the retained 16-point evidence and
normalizes the closest comparable stationary metrics. It does **not** support
the earlier implication that NewsMead's active gaze estimator was shown to be
better than MGazeNet. The broad line-normalized summaries and the narrower
A56 centre-target check both lean in MGazeNet's favor. That finding is
suggestive rather than a matched head-to-head result, and MGazeNet still has no
known-target reading result.

No phone, camera, calibration, participant session, model fit, threshold change,
active-tracker change, staging or commit occurred during this audit.

**Prospective protocol follow-up:**
`gaze-current-vs-mgazenet-comparison-protocol.md` freezes the matched four-slot
known-target design, exact estimator baselines, endpoints and pre-collection
software controls. It authorizes no participant or CAMERA action.

## Direct answer

The current NewsMead system has been tested repeatedly. The retained archive
contains thirteen accepted complete calibrations using the current
`eye_local_width_average_v1` feature, sixteen fit points and five held-out
points. Two of those sessions occurred after the screen-coordinate repair and
are the clean current-model comparison set. Earlier sessions remain real and
useful evidence, but their saved targets omitted the 101-pixel screen origin;
they cannot be pooled with later physical-screen MGazeNet results as though the
coordinate contracts were identical.

MGazeNet has eight sessions using the same NewsMead 4×4 sixteen-point
*fractional procedure* and five centre/quadrant fractions: four v3 development
sessions and four later confirmation sessions. The actual MGazeNet target view
was inset, so its physical target coordinates were not identical to the current
full calibration view. Its earlier v2 pilot used thirteen fit positions and is
therefore excluded from this sixteen-point comparison.

The closest normalized stationary comparison is:

| Evidence set | n | Median session five-target median | Median session target-balanced mean | Median session worst target |
| --- | ---: | ---: | ---: | ---: |
| Current NewsMead, post-repair A56 | 2 | 1.573 lines | 1.694 lines | 3.183 lines |
| MGazeNet, all recorded 16-point sessions | 8 | 0.582 lines | 0.721 lines | 1.620 lines |
| MGazeNet, A56 only | 4 | 0.818 lines | 0.910 lines | 1.879 lines |

Lower is better. The A56-only MGazeNet slice is included to avoid making the
two-phone MGazeNet set look artificially stronger merely because the current
post-repair evidence exists only on the A56. It favors MGazeNet on all three
summaries, but MGazeNet's narrower vertical viewport made the outer targets less
extreme. The line-height denominator also differed: 98 px in the A56 MGazeNet
view versus 118.546875 px in the current calibration report. These values are
comparable reading-oriented units, not identical test difficulty.

The closest physical A56 check is the centre target. Current NewsMead placed it
at `(540, 1220.5)` and MGazeNet at `(540, 1233.75)`, only 13.25 px apart. Raw
vertical errors avoid the unequal line-height denominators:

| A56 session | Centre-target central absolute Y error |
| --- | ---: |
| Current post-repair 1 | 259.343 px |
| Current post-repair 2 | 65.416 px |
| MGazeNet v3 R1 | 45.787 px |
| MGazeNet v3 R2 | 176.149 px |
| MGazeNet confirmation 1 | 98.948 px |
| MGazeNet confirmation 2 | 173.872 px |
| Current median across sessions | 162.379 px |
| MGazeNet median across A56 sessions | 136.410 px |

This narrower comparison also leans toward MGazeNet, though modestly. Together
the two views are meaningful evidence that MGazeNet may be the better
stationary estimator for this participant. They are not proof of production
superiority: the systems were not run back-to-back under a frozen paired
protocol, the sample counts are two versus eight, dates and fatigue context
differ, outer target positions differ, and the current model has reading-task
evidence that MGazeNet does not yet have.

## Normalization contract

Both systems used sixteen row-major fit targets at fractions `.10`, `.3667`,
`.6333` and `.90`, followed by five disjoint checks at centre and the four
quarter diagonals. The fraction pattern and target roles match; the containing
viewports do not. On the A56, current held-out Y positions were 660.75, 1220.5
and 1780.25 px, while MGazeNet's were 841.75, 1233.75 and 1625.75 px. The broad
table therefore describes cross-protocol performance in line units rather than
an exact target-by-target comparison.

For each of those five checks, the comparison reduces the system's output to
one central absolute vertical error in physical text-line heights:

- current NewsMead maps the target's robustly aggregated two-value eye-local
  observation once;
- MGazeNet takes the median absolute vertical error across the admitted online
  coordinate outputs for that target.

The five target-central errors are then summarized three ways: their median,
their target-balanced mean and their maximum. This makes the semantic unit and
target weighting common, but not the physical target range or per-target
sampling method. The result remains an observational comparison rather than a
paired experiment. The centre-only A56 table above is retained separately
because it is much closer in physical position and uses raw pixels.

MGazeNet sample P95 and within-one-line rates are not compared to the current
calibration screen because the current record has one aggregate prediction per
held-out target. Likewise, NewsMead reading AOI results are not pooled with
MGazeNet's instructed stationary-target results.

## Session-level comparable results

The `five-target median` is the median of the five target-central errors. The
`mean` is the equally weighted mean of the same five values. `Worst` is their
maximum. The last column applies only the two numerical MGazeNet spatial limits
descriptively: mean at most 1.0 line and worst at most 1.2 lines. It does not
retroactively turn current-model sessions into MGazeNet protocol passes because
their acquisition/count contracts differ.

| System and session | Device | Evidence role | Five-target median | Mean | Worst | Numerical spatial limits |
| --- | --- | --- | ---: | ---: | ---: | --- |
| Current `coords_v1_tel-off_cal_1` | A56 | post-repair baseline | 2.594 | 2.717 | 5.084 | misses mean and regional limits |
| Current `coords_v1_tel-off_cal_2` | A56 | post-repair baseline | 0.552 | 0.671 | 1.282 | meets mean; misses regional limit by 0.082 |
| MGazeNet G991B v3 R1 | G991B | development | 0.420 | 0.845 | 1.991 | misses regional limit |
| MGazeNet A56 v3 R1 | A56 | development | 0.431 | 0.391 | 0.640 | meets both limits |
| MGazeNet A56 v3 R2 | A56 | development | 1.797 | 1.793 | 2.359 | misses mean and regional limits |
| MGazeNet G991B v3 R2 | G991B | development | 0.675 | 0.686 | 1.159 | meets both limits |
| MGazeNet A56 confirmation 1 | A56 | prospective confirmation | 1.145 | 1.128 | 1.984 | misses mean and regional limits |
| MGazeNet G991B confirmation 1 | G991B | prospective confirmation | 0.466 | 0.616 | 1.384 | misses regional limit |
| MGazeNet G991B confirmation 2 | G991B | prospective confirmation | 0.739 | 0.749 | 1.467 | misses regional limit |
| MGazeNet A56 confirmation 2 | A56 | prospective confirmation | 0.490 | 0.693 | 1.774 | misses regional limit |

Across all eight MGazeNet sessions, six met the 1.0-line aggregate number, two
met the 1.2-line regional number, and two met both. Across the two clean current
sessions, one met the aggregate number and neither met the regional number. The
MGazeNet development thresholds were chosen after inspecting the four v3
sessions, so only the later four-session confirmation was prospective. All four
confirmation screens missed the regional number; that is a genuine
repeatability/tail warning, not evidence that the current model won.

## Complete current 16-point calibration inventory

The following table includes every accepted complete eye-local calibration in
the E-074 aggregate. Values are recomputed from the five unmodified deployed
held-out residuals with the A56 line pitch used by the existing documented
metrics, `118.546875` pixels.
The first eleven sessions are retained historical evidence but use the legacy
pre-repair target coordinate contract. The final two use `screen_px_v1` and are
the clean current-model comparison set above.

| # | Source record | Coordinate status | Five-target median | Mean | Worst | High drift |
| ---: | --- | --- | ---: | ---: | ---: | --- |
| 1 | `calibration_session_20260901_191457.json` | legacy pre-repair | 0.821 | 0.917 | 1.312 | yes |
| 2 | `calibration_session_20260901_191802.json` | legacy pre-repair | 1.030 | 1.099 | 1.895 | yes |
| 3 | `calibration_session_20260901_202803.json` | legacy pre-repair | 0.503 | 0.480 | 0.993 | no |
| 4 | `calibration_session_20260901_204922.json` | legacy pre-repair | 0.707 | 0.552 | 1.080 | no |
| 5 | `calibration_session_20260901_205322.json` | legacy pre-repair | 1.195 | 0.964 | 1.323 | no |
| 6 | `calibration_session_20260901_210620.json` | legacy pre-repair | 0.883 | 0.796 | 1.216 | yes |
| 7 | `calibration_session_20260901_230844.json` | legacy pre-repair | 0.496 | 0.568 | 0.817 | no |
| 8 | `calibration_session_20260902_212424.json` | legacy pre-repair | 1.232 | 1.325 | 2.382 | no |
| 9 | `calibration_session_20260903_011246.json` | legacy pre-repair | 1.994 | 1.748 | 2.453 | no |
| 10 | `calibration_session_20260903_011422.json` | legacy pre-repair | 0.621 | 0.688 | 1.316 | no |
| 11 | `calibration_session_20260903_210045.json` | legacy pre-repair | 0.705 | 0.936 | 1.924 | no |
| 12 | `calibration_session_20260904_024210.json` | `screen_px_v1` post-repair | 2.594 | 2.717 | 5.084 | yes |
| 13 | `calibration_session_20260904_195333.json` | `screen_px_v1` post-repair | 0.552 | 0.671 | 1.282 | no |

This inventory is why it is wrong to say there was no current-model evidence.
It also shows why selecting only the strongest current run would be misleading:
the two clean same-build sessions range from 0.671 to 2.717 lines on the
five-target mean and from 1.282 to 5.084 lines at the worst target.

## Reading evidence kept separate

NewsMead's current estimator has direct known-target reading evidence:

- E-046, before the coordinate repair, used a fresh 16-point calibration and
  recorded 841 valid samples. Guided-line exact/within-one-line accuracy was
  40.5%/88.6%; combined median/P95 absolute error was 1/2 lines. The result is
  valid evidence for the app behavior tested, qualified by the later-discovered
  coordinate defect.
- E-048 through E-050 retained three more fresh-calibration sessions and six
  reading recordings while evaluating session-level alignment corrections.
  Those experiments rejected the corrections; their uncorrected paths remain
  evidence but are not a clean estimator replacement comparison.
- E-054 supplied the two clean post-repair A56 calibration/reading pairs. Pair
  1 measured 36.5% within one line and 2/4-line median/P95 error; pair 2 measured
  85.4% within one line and 1/2-line median/P95 error. The large spread prevents
  a repeatability claim, while pair 2 proves the active system can produce a
  strong coarse line-level run.

MGazeNet's later confirmation recorded instructed stationary targets, not
reading. Its downstream within-one-line values of 76.3%, 85.9%, 65.4% and
49.4% must not be ranked against the NewsMead reading percentages as though the
tasks were identical. MGazeNet exact-line reading, word selection and scrolling
remain unmeasured.

## Correct interpretation and next decision

The frozen MGazeNet confirmation result was `inconclusive` because the proposed
five-point *screen* could not demonstrate both a true acceptance and a true
rejection: all four prospective screens rejected. That decision answers whether
the screen was validated as a selector. It does not mean MGazeNet lost to the
current system, and it does not erase the favorable stationary-error evidence.

The evidence-backed conclusions are:

1. The current model has substantial documented 16-point and reading evidence.
2. Neither estimator is repeatably free of regional tails for this participant.
3. The cross-protocol line summaries and near-matched A56 centre check both
   lean toward MGazeNet; unequal viewports prevent calling the outer targets a
   like-for-like comparison.
4. The active tracker remains unchanged because no frozen matched replacement
   comparison or MGazeNet reading validation has occurred, not because the
   current estimator was proven better.

If a future participant phase is authorized, the scientifically useful next
measurement is a short, frozen, counterbalanced current-versus-MGazeNet
known-target comparison on the same device and session conditions, with each
system receiving its own compatible 16-point calibration. It should score
physical line error and regional tails on identical independent targets and
include a known-target reading section. No thresholds should be selected from
the retained answers before that protocol is frozen.

## Provenance and verification

Current-model values come from
`gaze-end-pose-translation-screen-2026-09-06.json`, whose SHA-256 was rechecked
as `60c40404cd0eac19c4f8d16c6fa0c3ebad1afc4315e50131aa0af426bccbb238`.
That report reproduces all 273 deployed baseline prediction pairs exactly and
retains each source-record SHA-256.

MGazeNet values come from the four v3 `summary.json` files under
`diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/` and the
four confirmation `summary.json` files under
`diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/`.
All eight summary `input_sha256` values were rechecked against their preserved
raw `report.json` bytes with eight matches and zero mismatches. The private
`diagnostics-local` evidence remains ignored and must never be staged.

The canonical interpretation sources are `gaze-diagnostics-log.md` E-045,
E-046, E-048–E-054 and E-074; `gaze-coordinate-audit.md`;
`gaze-mgazenet-calibration-observability-v3-analysis.md`; and
`gaze-mgazenet-direct-validation-confirmation-analysis.md`.

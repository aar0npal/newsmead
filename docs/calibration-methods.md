  # Section 4.3.2 Gaze Tracking — Manuscript Draft

## Purpose and status

Insertion draft for **Section 4.3.2 (Gaze Tracking)** of the manuscript, covering
gaze feature extraction, the 16-point calibration procedure, the gaze mapping
model, calibration quality assurance, and the 9-point accuracy verification and
drift-correction procedure.

Companion to `docs/manuscript-implementation-alignment.md` (Sections 4.3.4 /
4.3.4.1 / 4.3.5, reading-state and scaffolding). This document follows the same
conventions: **blockquoted text is insertable manuscript prose**, code fences are
implemented formulas, and every engineering parameter is marked as fixed or
provisional. Canonical engineering reference: `docs/calibration-design.md`.

> **Accuracy figures must be re-measured before submission.** The device accuracy
> values recorded in `docs/progress-notes.md` (Galaxy A56: overall median
> 1.68–2.42 cm, vertical median 0.67–1.01 cm) predate the calibration redesign
> and the drift-correction layer. They are retained here only as the basis for
> current threshold anchoring. Section 4.3.2.6 lists the metrics to report.

## Terminology decision

The manuscript must distinguish two procedures that are easily conflated. Both
present fixation targets; only one estimates the mapping.

| Term | Procedure | Model estimated |
| --- | --- | --- |
| **Calibration** | 16-point, once per participant/session | Second-degree polynomial, 6 coefficients per axis |
| **Accuracy verification** | 9-point (3×3), any time | None; measurement only |
| **Drift correction** | Fitted from the 9-point measurement | Affine, 3 coefficients per axis |

Do not describe the 9-point procedure as a "9-point calibration." It never
re-estimates the polynomial mapping; it measures error and optionally fits a
corrective affine transform on top of the existing calibration.

---

## 4.3.2.1 Gaze feature extraction

> Gaze is estimated on-device from the front-facing camera. Each frame is
> processed by the MediaPipe Face Landmarker, which returns 478 facial landmarks
> including refined iris contours. The left and right iris centres are computed
> as the mean position of landmark rings 468–472 and 473–477 respectively. Rather
> than using absolute iris position, which varies with head translation, the
> system computes the position of each iris *relative to its own eye aperture*:
> horizontally as the fractional position between the inner and outer eye
> corners, and vertically as the fractional position between the upper and lower
> eyelid margins. The two eyes' values are averaged, producing a two-dimensional
> appearance feature that is approximately invariant to head translation and
> camera distance.

Implemented landmark indices:

| Quantity | Eye 1 | Eye 2 |
| --- | --- | --- |
| Iris ring | 468–472 | 473–477 |
| Eye corners (horizontal reference) | 33, 133 | 263, 362 |
| Eyelid margins (vertical reference) | 159, 145 | 386, 374 |

Each iris ring is paired with its nearer eye by horizontal proximity, so the
feature is invariant to the landmarker's left/right ordering and to the
front-camera mirroring applied before inference.

> Frames in which no face is detected, or in which a blink is in progress, are
> discarded before any aggregation, because iris landmarks are not meaningful
> when the eyelids are closed. Blink state is determined from an eye-aspect
> ratio — the vertical eyelid gap divided by the horizontal eye width, computed
> in pixels so that the ratio is aspect-correct and averaged across both eyes.
> Hysteresis is applied: the blink state is entered when the ratio falls below a
> lower threshold and exited only when it rises above a higher one, preventing
> state oscillation when the ratio fluctuates near a single boundary.

```text
EAR = mean over eyes of ( |y_upper_lid - y_lower_lid| * H ) / ( |x_corner_A - x_corner_B| * W )

blink entered when EAR < 0.040
blink exited  when EAR > 0.055
```

Camera and inference configuration:

| Parameter | Value | Rationale |
| --- | --- | --- |
| Analysis resolution | 480 × 360 | Full-resolution inference runs below real time and starves temporal filtering |
| Frame rate | Pinned to 30 fps | Stable sampling interval for filtering |
| Inference delegate | GPU, CPU fallback | Throughput on target hardware |
| Faces detected | 1 | Single-participant setting |
| Detection / presence / tracking confidence | 0.5 | See verification note below |

> **Verify before writing this number.** `docs/progress-notes.md` records these
> thresholds being lowered to 0.35 during on-device debugging, but the current
> source specifies 0.5. The source is authoritative; confirm the value in
> `MediaPipeRawGazeSource.kt` before citing.

---

## 4.3.2.2 Calibration procedure

> Calibration establishes the participant-specific mapping between the appearance
> feature and on-screen position. Sixteen fixation targets are presented in a 4×4
> grid inset from the screen edges, where landmark geometry is most reliable. The
> procedure is preceded by an unrecorded practice target so that the participant
> becomes familiar with the pacing before data collection begins, and the target
> order is randomised for each session to prevent anticipatory saccades toward a
> predictable next location. A near-centre target is additionally presented twice
> — once early and once at the end of the sequence — to quantify participant
> movement over the course of calibration.

Target layout and sequence:

| Element | Value |
| --- | --- |
| Grid | 4 × 4 at screen fractions 0.10, 0.3667, 0.6333, 0.90 |
| Practice target | 1, screen centre, discarded |
| Calibration targets | 16, randomised order (seed recorded) |
| Drift-check repeat | 1 near-centre target, presented within the first three and again last; excluded from the fit |
| Validation targets | 5, at (0.50, 0.50), (0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75) |
| Approximate duration | 45–60 s |

> Each target follows a fixed five-state presentation sequence. The target
> appears with a brief scale-and-fade animation to cue the saccade, remains
> static during an attention-holding interval that accommodates the longer
> saccadic latencies reported in older adults, and then passes through an
> unsampled settling interval during which overshoot and corrective saccades
> occur. Only after settling does sampling begin, marked by an auditory tone.
> Sampling is adaptive rather than fixed in duration: the window closes as soon
> as sufficient clean samples have been obtained, and extends up to a ceiling
> otherwise, because the effective sample rate varies with blink frequency and
> face-detection reliability. Capture is confirmed by a visual flash and a second
> tone. The inner target dot pulses in size, without translating, to sustain
> fixation on the target centre; a translating target would elicit smooth pursuit
> rather than fixation.

| State | Duration | Sampled | Function |
| --- | ---: | :---: | --- |
| Appear | 200 ms | No | Saccade cue |
| Hold attention | 500 ms | No | Saccadic latency allowance |
| Settle | 400 ms | No | Discards overshoot and corrective saccades |
| Sample | 700 ms base, re-evaluated every 250 ms, 1500 ms ceiling | Yes | Data collection |
| Confirm | 250 ms | No | Capture feedback |

> Within each sampling window, the first 120 ms is discarded as residual
> settling. Remaining samples are trimmed of outliers using the median absolute
> deviation: samples deviating from the per-axis median by more than 2.5 median
> absolute deviations are removed. The median absolute deviation is used in place
> of the standard deviation because it does not assume normally distributed noise
> and is not itself inflated by the outlier it is intended to detect. A minimum
> of ten retained samples is required, and the stored calibration value is the
> component-wise median of the retained samples.

> A target that fails these criteria is automatically re-presented once. If it
> fails a second time it is excluded from the model fit rather than contributing
> a known-unreliable observation, since least-squares estimation distributes the
> error of a single corrupted observation across all model coefficients.
> Calibration proceeds if at least twelve of the sixteen targets are retained;
> below this threshold the session is rejected and calibration is repeated.
> Exclusions are recorded, including the consequence that excluding a peripheral
> target reduces the calibrated feature range.

---

## 4.3.2.3 Gaze mapping model

> The mapping from appearance feature to screen coordinates is modelled as a
> second-degree polynomial, estimated independently for the horizontal and
> vertical screen axes. Features are standardised before fitting, because the
> horizontal feature (iris position between the eye corners) has a substantially
> wider natural range than the vertical feature (iris position between the
> eyelid margins); without standardisation the regularisation penalty would be
> applied unevenly across terms.

```text
zx = (fx - mean_fx) / sd_fx
zy = (fy - mean_fy) / sd_fy

screen = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
```

> Coefficients are estimated by ridge-regularised least squares. Regularisation
> is applied to the five slope terms but not to the intercept, so that the
> penalty constrains curvature without biasing absolute screen position. Ridge
> regularisation is used because the quadratic and interaction terms are strongly
> collinear over the sampled feature range, and unregularised estimation is
> correspondingly unstable with sixteen observations.

| Parameter | Value | Status |
| --- | --- | --- |
| Polynomial degree | 2 (6 terms per axis) | Fixed |
| Ridge penalty λ | 1.0, slope terms only | **Provisional** |
| Minimum observations to fit | 6 mathematically; 12 enforced for session validity | Fixed / **provisional** |
| Solver | Normal equations, Gaussian elimination with partial pivoting | Fixed |

> Because a second-degree polynomial diverges rapidly outside the range over
> which it was estimated, the model is not evaluated on features beyond the
> calibrated range. Instead, the estimate is continued outward along the
> polynomial's gradient at the range boundary — a first-order extension — and
> this extension is smoothly bounded by a hyperbolic-tangent soft limit. Near the
> boundary the behaviour is effectively linear, so gaze directed slightly beyond
> the calibrated range continues to produce proportionate movement; far outside,
> the estimate asymptotes, so an erroneous feature value cannot displace the
> estimate arbitrarily.

> An earlier implementation clamped out-of-range features to the calibrated
> boundary. This produced a directional limit beyond which the estimate did not
> move, observable as gaze that could not be tracked past a fixed screen
> position; the position of this limit varied between sessions with head pose.
> Gradient extension with a soft limit was adopted to remove this artefact while
> retaining bounded behaviour. This is recorded because the artefact would
> otherwise be misinterpreted as a tracking failure in pilot observations.

> During reading, feature values are temporally filtered before mapping — a
> median filter followed by a One-Euro filter, which adapts its cutoff frequency
> to movement speed and thereby suppresses jitter during fixation without adding
> latency during rapid movement. Calibration samples are not passed through this
> filter; the per-target median already provides robust aggregation, and a
> lag-inducing low-pass filter is inappropriate for a static fixation.

| Filter | Parameters | Applied to |
| --- | --- | --- |
| Median | — | Live gaze only |
| One-Euro | minimum cutoff 0.7, β 0.005 | Live gaze only |

---

## 4.3.2.4 Calibration quality assurance

> Calibration quality is assessed within the session, before the calibration is
> accepted, using two complementary procedures.

> First, leave-one-out cross-validation is performed on the calibration
> observations: the mapping is re-estimated sixteen times, each time omitting one
> target, and used to predict the omitted target. This yields a distribution of
> prediction errors at no additional cost in participant time, and measures
> generalisation rather than goodness of fit — a model may reproduce its own
> estimation points closely and still map novel gaze poorly.

> Second, five validation targets at positions not used for estimation are
> presented and measured through the fitted mapping. This constitutes the
> reportable per-session accuracy figure.

> Errors are expressed both in pixels and in text line-heights, the latter
> computed from the study's fixed article typography. Line-height is the
> decision-relevant unit for this study, because the adaptive reading interface
> requires identification of the line being read; an error expressed in
> line-heights states directly whether line-level attribution is feasible.

```text
line_height_px = (font_descent - font_ascent) * 1.6 + 1sp
where font size = 22 dp (fixed for the study; see Section 4.3.1)
```

> A calibration is classified against thresholds anchored to the measured
> accuracy of the tracker on the target device, rather than to an idealised
> criterion, and the classification is advisory: the researcher may accept the
> calibration, repeat the three worst-performing targets, or repeat the
> procedure in full. The calibration file is written only upon acceptance, so
> that an unreviewed repetition cannot overwrite a previously accepted
> calibration. The drift-check delta is reported alongside, converted to pixels
> through the fitted mapping.

| Classification | Median error (worse of cross-validation and validation) | Status |
| --- | --- | --- |
| Acceptable | ≤ 3.0 line-heights | **Provisional** |
| Marginal | ≤ 4.5 line-heights | **Provisional** |
| Poor | > 4.5 line-heights | **Provisional** |
| Movement flag | Drift-check delta > 1.0 line-height | **Provisional** |

> Each calibration session writes a record containing, for every presented
> target: its screen position, presentation index, whether it was a practice,
> drift-check, or validation presentation, whether it was re-presented or
> excluded, raw and retained sample counts, blink-rejected and no-face frame
> counts, the aggregated feature value, and the sample dispersion. Cross-
> validation results, validation errors, the drift delta, and the researcher's
> decision are appended. Records are written incrementally so that an interrupted
> session remains analysable.

---

## 4.3.2.5 Accuracy verification and drift correction

> Gaze accuracy degrades within a session as posture changes relative to the
> position held during calibration. To detect and compensate for this without
> repeating the full procedure, the system provides a nine-point verification
> procedure presenting a 3×3 grid at 20%, 50%, and 80% of each screen dimension.
> These positions do not coincide with any calibration target, so the procedure
> constitutes an independent test rather than a re-measurement of estimation
> points. Target presentation, sampling, and outlier rejection are identical to
> calibration.

> The procedure reports median, 95th-percentile, and vertical median error in
> pixels and line-heights. Because measurements are taken at the output of the
> mapping, each target yields a pair of predicted and true screen positions.

> Postural drift displaces gaze estimates principally by translation and scale
> rather than by altering the form of the appearance-to-screen relationship.
> Accordingly, the correction estimated from these pairs is affine — six
> parameters, capturing translation, scale, and shear — and is applied after the
> polynomial mapping, leaving the calibration itself unmodified and the
> correction fully reversible.

```text
x' = c0 + c1*x + c2*y
y' = d0 + d1*x + d2*y

estimated by least squares over the nine (predicted, true) screen-position pairs
```

> Re-estimating the full second-degree polynomial from nine observations was
> considered and rejected: with six coefficients per axis, nine observations
> leave few residual degrees of freedom, producing an unstable fit at the
> periphery and risking replacement of a well-estimated but displaced mapping
> with a poorly estimated one. Where the residual error is not affine in form,
> the appropriate remedy is full recalibration, and the system presents this as
> an explicit option rather than applying a correction that cannot address the
> error.

> The expected post-correction error is reported using leave-one-out
> cross-validation over the nine observations, not the in-sample residual. An
> affine transform can always reduce the residual of the observations from which
> it was estimated; the in-sample figure would therefore systematically overstate
> the benefit of applying the correction.

> Verification always measures the pipeline in its current state, including any
> correction already in effect. A newly estimated correction therefore maps
> already-corrected coordinates to true positions, and is stored as the
> composition of the new and existing transforms rather than as a replacement.
> Accepting a new full calibration clears any stored correction. Application and
> reversion are recorded with timestamps and the associated error figures.

Researcher options presented after verification:

| Option | Effect |
| --- | --- |
| Apply correction | Stores the affine correction, composed with any active correction |
| Revert to calibration | Clears the correction; the base calibration alone is used |
| Full recalibration | Launches the 16-point procedure |
| Measure again | Repeats verification |

---

## 4.3.2.6 Reportable measures

Report per participant and per session, before any participant-facing accuracy
claim (consistent with the gaze-quality requirements in
`manuscript-implementation-alignment.md`):

| Measure | Unit | Source |
| --- | --- | --- |
| Validation error, median and 95th percentile | px and line-heights | 5-point held-out validation |
| Cross-validated calibration error | px and line-heights | Leave-one-out over 16 targets |
| Vertical error, median and 95th percentile | px and line-heights | Verification procedure |
| Exact-line and within-one-line accuracy | proportion | Line attribution against ground truth |
| Targets excluded at calibration | count | Session record |
| Blink-rejected and no-face frame proportion | proportion | Session record |
| Drift-check delta | px and line-heights | Repeated near-centre target |
| Within-session drift | px over elapsed time | Successive verification runs |
| Corrections applied per session | count, with pre/post error | Correction history |

---

## Required limitations

Add to the manuscript limitations, alongside the circular measurement
contamination limitation already specified in
`manuscript-implementation-alignment.md`:

> Gaze is estimated from eye appearance rather than from corneal reflection.
> The vertical component is derived from the iris position between the eyelid
> margins, a comparatively small range that is partly confounded with blinking
> and head pitch. Vertical accuracy is therefore the binding constraint on
> line-level attribution, and is the dimension most likely to limit the validity
> of line- and word-level interventions.

> Calibration is valid for the head pose and viewing distance held during the
> procedure. The affine drift correction compensates translation- and
> scale-dominated departures from that pose, but cannot compensate changes that
> alter the form of the appearance-to-screen relationship. Accuracy reported at
> calibration should therefore be treated as an upper bound on accuracy during
> reading.

> The calibration and correction thresholds, sampling durations, outlier
> criterion, and classification bands are provisional engineering parameters
> selected to be workable on the target device. They have not been empirically
> optimised and must be validated with pilot data before the main data
> collection.

> Tracker accuracy has been characterised on the development team only.
> Performance on the target participant population — including the effects of
> corrective lenses, presbyopia, reduced contrast sensitivity, and varied
> ambient lighting — remains to be established, and is the principal external
> validity risk for the gaze-tracking component.

---

## Alternatives considered and not adopted

Record these; each was implemented or specified, then rejected on evidence.

| Alternative | Reason not adopted |
| --- | --- |
| Nine-point polynomial re-estimation for drift | Six coefficients per axis from nine observations yields an unstable peripheral fit; risks replacing a displaced but well-estimated mapping with a poorly estimated one |
| Hard clamping of out-of-range features | Produces a fixed directional limit beyond which the estimate does not move; position varies between sessions with head pose |
| Absolute dispersion criterion for fixation acceptance | The threshold trialled (0.02 feature units) fell below the tracker's own per-frame noise, causing systematic target rejection and calibration abort |
| Most-stable sub-window selection within the sampling window | Preferred intervals of stable-but-displaced gaze over the target-centred median, reducing accuracy |
| In-sample residual as the post-correction estimate | Systematically overstates correction benefit; replaced with leave-one-out |

---

## Implementation traceability

| Manuscript content | Implementation |
| --- | --- |
| 4.3.2.1 Feature extraction, blink rejection | `MediaPipeRawGazeSource.kt`, thresholds in `StudyConfig.kt` |
| 4.3.2.2 Target presentation and sampling | `CalibrationPointCollector.kt`, `CalibrationView.kt`, `FixationWindowFilter.kt` |
| 4.3.2.2 Sequence, exclusion, session record | `GazeCalibrationActivity.kt`, `CalibrationSessionLog.kt` |
| 4.3.2.3 Mapping model, extrapolation | `GazeMapper.kt` |
| 4.3.2.3 Live filtering | `LocalCalibratedGazeProvider.kt`, `MedianFilter.kt`, `OneEuroFilter.kt` |
| 4.3.2.4 Cross-validation and quality gate | `CalibrationQuality.kt`, `GazeCalibrationActivity.kt` |
| 4.3.2.5 Verification and drift correction | `GazeTestActivity.kt`, `DriftCorrection.kt` |
| Persistence and audit trail | `CalibrationStore.kt` |

Engineering rationale and revision history: `docs/calibration-design.md`,
`docs/progress-notes.md`.

---

## Consistency checklist

- Use **calibration** only for the 16-point procedure that estimates the mapping.
- Use **accuracy verification** for the 9-point procedure; never "9-point
  calibration."
- Use **drift correction** for the affine transform, and state that it is applied
  after, and does not modify, the calibration.
- Report all gaze errors in **both pixels and line-heights**.
- Distinguish **cross-validated** from **held-out validation** error; do not
  report in-sample residuals as accuracy.
- Mark all thresholds, durations, and classification bands as **provisional**
  pending pilot validation.
- State that calibration accuracy is an **upper bound** on reading-time accuracy.
- Re-measure and update all device accuracy figures before submission.
- Keep the vertical-accuracy limitation explicit wherever line- or word-level
  intervention claims appear.

# Reading-reference offset-only replay — 2026-09-03

## Decision

Do not deploy the tested session-global median offset. It improves some whole-run
scores substantially, but does not reliably improve both task types and introduces
major regional regressions. This is an offline screening result across all six
recordings from three calibration sessions, not a prospective test of offset-only
and not evidence that every possible offset correction is ineffective.

No Android source, estimator, mapper, filter, calibration, target sequence, or
installed app was changed for this replay. The retained eye-local feature remains
in place. No additional phone run is requested for this candidate.

## Fixed candidate and comparison

For each recording independently, calculate:

```text
offset = median(top_target_y - top_observed_median_y,
                middle_target_y - middle_observed_median_y,
                bottom_target_y - bottom_observed_median_y)
candidate_y = base_y + offset
```

Only the three reference medians fit the offset. Reading targets are evaluation
data, never fitting data. All six recordings use this identical rule with gain 1,
no clipping, no cap, no shrinkage, no parameter sweep, and no post-hoc acceptance
gate. All three reference counts must meet the existing 15-sample floor. This
tests an always-applied reference offset, not a newly designed guarded candidate.

Compare it with uncorrected base output and the original guarded gain/bias layer.
For an OFF recording the original layer is simulated only when its logged fit
passed; when that fit failed, the original guarded arm remains uncorrected. None
of the OFF recordings is discarded because its original gain/bias fit failed.
All three ON fits passed and were actually applied in their original runs.

## Replay validity and limits

The nominal `TextView.lineHeight` is 137 px, but it cannot reproduce the recorded
line assignments. Unscored coordinate-to-raw-AOI observations uniquely identify
the actual interior boundaries as `12 + 133 * line_index` px relative to the text
origin (the first line starts at 0). This is reconstruction of the deterministic
text layout, not fitting the correction to expected reading answers.

The three reference target positions recover a reading viewport from y=259 to
2205 px. The fixed 16 dp margin is 45 px. A completed, bottom-clamped scroll
recovers a 6340 px text height, including the shorter last line. The reconstruction
reproduces **all 10,580 base/effective valid flags and line assignments across
5,290 measured samples**. The replay refuses ambiguous geometry or any mismatch.
The nominal-height discrepancy does not invalidate the app's original exact-line
counts: the app uses Android Layout for those counts, not division by 137.

All comparisons are before target stabilization. Exact-line and within-one-line
denominators include every measured sample, including off-text samples. In
addition to valid-only assignment tails, report continuous distance to the
expected physical line center, divided by the actual 133 px line pitch; these
tails include off-text coordinates, avoiding an apparently better tail merely
because poor coordinates become invalid.

Glyph/word boxes were not logged. **No offset-only exact-word accuracy is claimed.**
Nominal region labels are not always actual screen positions: document-end scroll
limits place `localization_7` at y=1351.5 px rather than the requested top region.
The replay uses its actual physical-line position and records that distinction.

These are repeated observations from one participant/device, with correlated
frames, not six independent participants. No frame-level significance claim or
population-generalization claim is made. Offset-only was proposed after seeing
the original gain/bias outcomes; even a favorable replay would need a later
prospective test before retention.

## All six recordings

Percentages below are **uncorrected base -> offset-only** on identical samples.
The raw source labels are preserved; the last pair's `_4` suffix is not an error.

| Recording | Offset (px) | All samples within one line | Guided exact-line | Guided within one line | Valid output |
|---|---:|---:|---:|---:|---:|
| `vert_off_1` | -4.9 | 75.3 -> 74.8 | 28.6 -> 27.8 | 85.3 -> 85.1 | 100.0 -> 100.0 |
| `vert_on_1` | +107.1 | 70.5 -> 53.7 | 20.6 -> 14.2 | 74.6 -> 39.9 | 100.0 -> 96.3 |
| `vert_on_2` | +219.9 | 71.6 -> 88.7 | 10.8 -> 37.5 | 66.5 -> 86.9 | 100.0 -> 100.0 |
| `vert_off_2` | +232.5 | 83.1 -> 68.6 | 29.2 -> 22.0 | 81.7 -> 69.2 | 100.0 -> 100.0 |
| `vertical_off_4` | +203.4 | 48.9 -> 86.5 | 6.2 -> 55.7 | 39.4 -> 87.5 | 100.0 -> 100.0 |
| `vertical_on_4` | +103.8 | 59.2 -> 70.5 | 45.5 -> 23.9 | 84.3 -> 87.4 | 96.5 -> 100.0 |

The recorded-mode recordings are paired as OFF->ON, ON->OFF, OFF->ON. Analyze
each recording's own references and samples; do not attribute a between-run
difference entirely to the correction. Equal weighting of the 14 checkpoints
preserves the sign of each recording's within-one-line change shown above.

Offset-only beats the original guarded gain/bias arm on overall within-one-line
accuracy in four recordings, but beats the uncorrected base in only three of six.
That distinction matters: improving on the failed correction is not sufficient
to justify modifying the retained baseline.

### Regional failures and tails

- Top-left within-one-line accuracy falls from 100% to 0% in `vert_on_1`,
  `vert_off_2`, and even the otherwise strongly improved `vertical_off_4`.
- In `vert_on_2`, top-left exact-line accuracy falls from 69.8% to 0%, while
  top-left within-one-line falls from 90.7% to 53.5%. The all-sample maximum
  center error grows from 3.43 to 4.58 line pitches despite the improved P95.
- The `vert_on_2` and `vert_off_2` offsets are similar (+220/+232 px), yet the
  first improves guided reading and the second harms it. Offset magnitude alone
  does not explain whether a recording benefits.
- In `vertical_on_4`, overall within-one-line and validity improve, but guided
  exact-line falls from 45.5% to 23.9%, and guided all-sample center-error P95
  increases from 1.81 to 2.10 line pitches.
- In `vert_on_1`, all-sample center-error P95 increases from 2.70 to 3.50 line
  pitches with offset-only (the original gain/bias layer was worse at 6.16).
- No offset-only checkpoint becomes wholly invalid; that is an improvement over
  the original layer, but does not compensate for the severe regional misses.

This supports a limited inference: stretching contributed to some extreme
failures, but removing it does not make the reference-derived correction
representative across later tasks and positions. These logs alone cannot
separate pose drift, gaze behavior, horizontal-dependent mapping error, and other
causes of that transfer failure.

## Reproduction and artifacts

- Analyzer: `tools/gaze/replay_reading_offsets.py`.
- Pure tests: `tools/gaze/test_replay_reading_offsets.py` (11 tests pass).
- Aggregate metrics, checkpoint deltas, source filenames, byte counts, and SHA256:
  [gaze-offset-replay-2026-09-03.json](gaze-offset-replay-2026-09-03.json).
- The first pair remains in the previously authorized `diagnostics-local` copies.
  Four newer raw logs were read directly from app-private device storage into
  memory and were **not** copied into OneDrive. This report contains derived
  results only. No source artifact was removed, relabelled, or overwritten.

Run the tests in CMD:

```bat
python -m unittest discover -s tools\gaze -p test_replay_reading_offsets.py -v
```

The analyzer accepts existing local JSONL paths. With `--adb` it also accepts
explicit app-private `reading_validation_session_<timestamp>.jsonl` filenames,
reads them without persisting the raw payload, and prints the aggregate report.

## Next decision

Do not implement the simple offset as an accuracy improvement or ask for another
identical phone run. Keep both global corrections experimental/OFF and preserve
the retained eye-local baseline. No runtime removal was authorized or performed
by this offline task. A next intervention requires a bounded explanation and
prospective decision rule for calibration-to-reading transfer; do not select a
new cap, shrinkage factor, or per-target correction by optimizing this replay.

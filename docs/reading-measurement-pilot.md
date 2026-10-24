# Reading measurement pilot — specification v1

2026-09-13. Scope: raw natural-reading recording and reproducible offline measurements.
No RII formula, weights, baseline, attention classifier, or scaffold-intensity policy is
selected by this implementation. The existing adaptive/demo pipeline remains available
outside recording; its line-visit counter is not used by the new measurement analysis.

**Offline extension 2026-09-30:** analyzer v5 retains the extraction/coverage
contracts and adds the [named P01–P13 detector layer](scanpath-detectors.md), unique
geometry snapshots and evidence-availability provenance. Return-sweep regions
and long/slow thresholds require explicit development settings. This changes
offline analysis, not schema-1 recording or Android aid behavior.

## Running the first recording

1. Use the current MGazeNet calibration and open a bundled article.
2. Tap the article's gaze diagnostics button, then **Record natural reading**.
3. Read the instructions and tap **Start reading**. Read silently at your own pace.
   The orange gaze dot stays visible at the user's request. FPS, scaffold caption, and
   all visual scaffolds are hidden. Speech is
   stopped, and the reading mode does not feed the old index/controller.
4. Tap the diagnostics button once to stop. Leaving/pausing the screen also stops.
5. Wait for the saved message. A failed/incomplete message means repeat the recording.

For the first plumbing check, record a short passage, include a deliberate reread,
scroll once, and briefly look away. This checks whether the recording is interpretable;
the instructed behavior is not evidence of spontaneous attention detection. Then collect
ordinary reading separately. Read from the beginning of a fresh passage for
first-pass analysis and keep a researcher note of the task and starting position.

These are diagnostic-dot recordings, explicitly identified in the metadata, rather than
an unassisted-reading study condition. The dot retains the existing smoothed display
coordinates; the file retains both raw and display coordinates, and analysis uses raw.
Watching the dot helps inspect tracking but is not independent fixation ground truth.

The existing **Reading validation** menu option is the separate known-target instrument
and uses MGazeNet. Do not combine its prompted target fixations with natural-reading data.

## Records and coordinate contract

`ReadingMeasurementLog.kt` observes `MgazeNetGazeProvider.onObservation` on the main
thread. It logs the calibrated **raw** screen coordinates before the article's One Euro
filter, plus filtered display coordinates for comparison. The legacy UI target holds
are not applied. No camera images or face features are retained.

Files are app-private and excluded from Android backup:
`no_backup/reading_measurement_<UUID>.jsonl`. They are explicitly started, not recorded
automatically on every article visit. A bounded queue moves JSON serialization and file
writes to a dedicated worker; flush occurs approximately every second. Queue loss is
counted. A clean footer is required for ordinary analysis; process death can leave a
partial recording, which must not be silently treated as complete.

| Record | Fields / interpretation |
|---|---|
| `session_start` | Schema 1, UUID, UTC start, monotonic start, device, calibration SHA-256, article-text SHA-256, UTF-16 word offsets, locale, display density, mode and stream identity |
| `geometry` | Monotonic time, geometry version, padded text origin in physical-screen pixels, font size, color, line height, line offset ranges and local rectangles |
| `sample` | Camera capture time, delivery time and ID, provider availability reason, raw and display coordinates, geometry version, measurement validity, unstabilized line and word offsets |
| `session_end` | End time/reason, observed sample count, dropped-record count, completeness flag |

Capture and delivery use the same monotonic elapsed-realtime clock. Duration calculations
use capture time, not UI receipt time. Word offsets are UTF-16 indices consistent with
Android Layout/BreakIterator, not Python character indices. Word ordinal is the position
in the logged word-range list; this permits correct document-order line-wrap handling.

Scrolling, text-view movement, size/color changes, unavailable gaze, nonfinite coordinates,
duplicate/out-of-order timestamps, and long gaps break measurement continuity. Each geometry
change excludes samples captured before **150 ms after the change**. This provisional
settling exclusion also rejects frames captured before a scroll but delivered after it.
Changing article text ends the recording. No dwell or transition is interpolated over a gap.

Coordinates must fall within the visible text-view rectangle and its laid-out vertical
range. Whitespace may have no word assignment. `measurement_valid` establishes operational
eligibility, not verified spatial accuracy or attention. A provider-unavailable event is
not labelled an attention lapse. The log is not a raw camera-frame census: provider drops
can also manifest as capture-time gaps.

## Fixation extraction (provisional I-DT)

`tools/gaze/analyze_reading_measurement.py` implements a dispersion-threshold detector.
For a candidate sequence of samples:

`dispersion = (max(x) - min(x)) + (max(y) - min(y))`

Default engineering parameters, all included in each analysis result:

| Parameter | Default | Purpose |
|---|---:|---|
| Maximum dispersion | 0.5 × current line height in pixels | Spatial compactness; not angular accuracy |
| Minimum observed duration | 100 ms | Minimum candidate span |
| Minimum samples | 3 | Reject insufficient evidence |
| Maximum consecutive-sample gap | 125 ms | Prevent bridging missing input |
| Word assignment agreement | 80% of fixation samples | Leave boundary/neighbor ambiguity explicit |

Find a compact minimum-duration window, expand while its dispersion remains within the
limit, and end it when the next sample exceeds that limit. Short unstable candidates
slide forward. Duration is the last minus first capture time; no half-frame extension or
interpolation is added. At the device's sampling rate this is a sampled estimate, not an
exact physiological onset/offset measurement.

Every event retains its sample count, centroid, dispersion, word agreement, segment,
and left/right censor flags. Events interrupted by loss, movement, or session end have
an unknown right boundary. The beginning of a segment can have an unknown left boundary.
Only events with both boundaries observed enter mean/median fixation duration. Censored
event spans remain in the event list as observed lower bounds, not discarded evidence.

Word attribution uses the most frequent raw sample word range, requiring 80% agreement.
It is a provisional mapping rule, not proof of which word was fixated. Unassigned
fixations remain in the timing data but break word-sequence continuity. No saccade
velocity, microsaccades, or pupil-based medical inference is computed.

## Candidate feature definitions

The output includes all fixation events, word visits, numerator/denominator counts, and
session-level summaries. Missing denominators produce JSON null, never zero instability.
A visit combines consecutive fixations assigned to one word, summing fixation durations
rather than elapsed wall time between them.

| Output | Exact v1 interpretation |
|---|---|
| `mean/median_fixation_duration_ms` | Summary of uncensored detected fixation spans, including spatially ambiguous events within the visible text region |
| `mean_observed_first_pass_gaze_duration_ms` | Mean summed fixation time for complete visits advancing beyond the known word frontier in a contiguous observation segment; segment-start and terminal visits excluded |
| `observed_rereading_proportion` | Observed fixation time on subsequent visits to previously visited words within the same unambiguous segment / all word-attributed observed fixation time |
| `regression_probability` | Backward word-visit transitions / all eligible between-word visit transitions within those segments |
| `first_pass_skipping_rate` | Unfixated intervening words / newly traversed eligible words, including the landing word; only forward advances starting at the known frontier count |

The first-pass result is explicitly an **observed proxy**: a gap can hide an earlier
visit, and starting midway through a passage cannot establish its true initial pass.
Repeated visits across gaps are conservatively not inferred. Rereading time includes
the observed portion of censored fixation spans; its denominator is the same observed
quantity, not total reading time. Transition rates are conditional on usable word
sequences, so always inspect their denominators and ambiguity counts alongside the rate.
No skipped word is assumed unread or unattended. Saccades across line wraps are forward
when the document word ordinal increases, even when screen x decreases.

For example: two fixations of 220 and 180 ms during a word's initial visit give 400 ms
of first-visit gaze duration; a later 250 ms visit gives 250 ms of observed rereading.
The first-visit value is eligible only if its entry/exit and progression context qualify.

## Replay and verification

Analysis version 2 additionally breaks word-sequence continuity when consecutive detected
fixations are separated by more than the configured gap limit (default 125 ms), even if
camera samples continued during that unclassified interval. Version 1 could bridge
discarded candidates and distort skipping/regression estimates. See `reading-measurement-run-01.md`.

The subsequent [scanpath measurement pilot](scanpath-measurement-pilot.md) defines
analysis version 3's separately reported word and physical-line ordered paths and
phone-only validation limits. The v1/v2 session-level word metrics remain provisional;
the path replay does not turn them into a reading-difficulty detector.

Analysis version 4 additionally preserves chronological capture order across
boundaries, separates coordinate eligibility from detector-accepted sample coverage,
and reports duration coverage, visit censoring, output age and boundary-event counts.
See the [2026-09-30 audit](scanpath-pilot-audit.md) for the corrected semantics.

Analysis version 5 adds the [offline named detector layer](scanpath-detectors.md),
unique geometry snapshots and availability of each finalized candidate's closing
evidence. Its optional `--pattern-config` JSON object records development settings;
unset direction/endpoints or long/slow thresholds do not supply a positive label.
Existing extraction/coverage definitions remain unchanged.

Retrieve only the desired `reading_measurement_*.jsonl` from the phone's app-private
no-backup directory. Existing calibration/accuracy logs are separate schemas. Example
once a recording is available locally:

```text
python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json
python tools/gaze/analyze_reading_measurement.py recording.jsonl --output sensitivity.json --dispersion-lines 0.75 --min-ms 120
python -m unittest discover -s tools/gaze -p test_analyze_reading_measurement.py -v
```

Threshold variants are sensitivity checks, not alternate validated settings. Do not
choose thresholds merely because their output appears smoother or more supportive of
the hypothesis. `--allow-incomplete` is diagnostic-only and marks the output incomplete.

Synthetic tests cover same-line multiple fixations, timing gaps, noise, duplicated times,
word ambiguity, revisit/skip denominators, line-wrap order, censoring, raw-vs-display
coordinates, and incomplete recordings. The Android instrumentation test exercises the
writer and screen-coordinate mapping using synthetic coordinates on an idle real layout;
it does not open the camera or establish gaze accuracy.

## Decisions after the first recordings

1. Confirm usable sampling intervals, spatial assignment, fixation counts/durations,
   ambiguous-word fraction, and behavior around scrolling. Compare threshold sensitivity.
2. Run the known-target check at the current font/layout. Report exact-word and
   neighboring-line placement, coverage and false movement within target holds, not
   only average error. Its separate target holds do not verify a natural reading path.
3. Collect natural reading with independent reports of following, comprehension difficulty,
   losing place, or unrelated thoughts, plus passage comprehension. These labels are not
   inferred from this recording mode and are not automatically collected by it yet.
4. Select a small feature set and rolling-window policy. The v1 replay produces session
   summaries and timestamped events; rolling features, online extraction, scanpath reference
   models, and composite scores are subsequent work. Evaluate whether the composite adds
   information over individual measures, with participant/passage separation and realistic
   expectations for the planned small pilot sample.
5. Only then connect a supported index to continuous or combined visual assistance and
   evaluate comprehension, effort, usability, and unwanted interventions independently.

## Scientific basis and limits

- Salvucci & Goldberg (2000), *Identifying fixations and saccades in eye-tracking
  protocols*: [DOI](https://doi.org/10.1145/355017.355028). Basis for I-DT; the engineering
  thresholds above are ours and require validation on this stream.
- von der Malsburg, Kliegl & Vasishth (2015), *Determinants of Scanpath Regularity in
  Reading*: [author PDF](https://tmalsburg.github.io/MalsburgEtAl2015.pdf). Numerical
  spatial/temporal sequence regularity with age and sentence-property effects; a
  corpus-relative sentence method, not a ready-made live RII.
- Mézière et al. (2024), *Scanpath Regularity as an Index of Reading Comprehension*:
  [DOI](https://doi.org/10.1080/10888438.2023.2232063). Supports evaluating timing and
  sequence information together; comprehension association is not momentary diagnosis.
- Bixler & D'Mello (2016), *Automatic gaze-based user-independent detection of mind
  wandering during computerized reading*: [DOI](https://doi.org/10.1007/s11257-015-9167-1).
  Precedent for independently labelled, cross-user gaze predictions.
- Valliappan et al. (2020), *Accelerating eye movement research via accurate and
  affordable smartphone eye tracking*: [DOI](https://doi.org/10.1038/s41467-020-18360-5).
  Smartphone reading-measure associations, with sampling limits; not clinical thresholds.
- Mézière et al. (online 2025; issue 2026), *Eye-movement markers of mind wandering
  during reading*: [DOI](https://doi.org/10.3758/s13421-025-01797-8). Findings caution
  against assuming every problematic measure increases.

These references motivate the measurement candidates. They do not validate this app's
detector, word attribution, proposed future RII, or scaffolding effectiveness.

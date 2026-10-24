# Scanpath detectors — offline and live implementation v1

**Implemented 2026-09-30:** `tools/gaze/scanpath_patterns.py`, integrated into
`tools/gaze/analyze_reading_measurement.py` **analysis version 5**. The original
schema-1 logger and Android behavior are unchanged. This is the offline named
pattern layer in [pilot revision 6](scanpath-measurement-pilot.md).

## Input and output

Input is finalized provisional I-DT candidates with raw centroid, capture
interval, word offsets, physical line, segment, geometry snapshot and censoring.
The classifier processes their observed order and retains independent word/line
continuity. Unknown words reset word history; valid lines can continue. Gaps,
segment/geometry changes reset affected histories. No future visit is required
to emit a movement or episode update.

The analyzer JSON adds `patterns` with:

| Field | Meaning |
| --- | --- |
| `classifier_version` | Version 1 of these descriptive rules. |
| `registry` | P01–P13 labels, resolution, primary-study references and operational status. |
| `config`, `config_sha256` | Exact explicit settings and normalized configuration hash. The hash is added by analyzer integration. |
| `events` | Ordered rule evaluations/observations with deterministic event ID, contributing fixation indices, capture start/end, evidence availability, metrics and limitations. |
| `counts` | All emitted records, including sweep evaluations that abstained or did not match. Not a count of helpful/difficult episodes. |
| `matched_counts` | P09 counts only observed geometric sweep matches; other IDs count their descriptive records. P12 contains updates, not independent difficulty episodes. |
| `pattern_status` | Per-ID observation/configuration status; P09 and P12 distinguish unset settings. |
| `status` | Explicit statement that difficulty classification is false. |

The analyzer also saves `geometry_snapshots`. Each geometry record has a unique
snapshot key; candidate fixations retain this key plus the original logger ID.
Reused IDs cannot replace the older geometry used by earlier candidates.

## Pattern dictionary

| ID | Implemented observation | Definition / restriction |
| --- | --- | --- |
| P01 | Word forward step | Consecutive assigned candidates, word distance +1. |
| P02 | Word forward jump | Word distance greater than +1; no unread-word inference. |
| P03 | Word backward return | Negative word distance, retaining exact short/long displacement. |
| P04 | Same-word refixation candidate | Two distinct consecutive candidates on the same word; one hold is not two fixations. |
| P05 | Next-line progression | Next assigned physical line in document order. |
| P06 | Forward multiline landing | More than one line forward; no skipped-text diagnosis. |
| P07 | Same-line backward return | Negative word displacement with both candidates on the same assigned line. |
| P08 | Earlier-line return | Negative physical-line displacement, retaining its size. |
| P09 | Geometric return-sweep candidate | +1 line, leftward screen movement, matching visible rendered-line geometry and configured end/start regions. Reports observed/not_matched/abstain. |
| P10 | Revisit/frontier episode | Reenter previously visited word/line; track forward approach, backward movement within the episode and frontier resumption. Retain original occurrence/frontier and complete revisit-to-current evidence in capture order. Consecutive same-target candidates are not revisits. |
| P11 | Alternating cycle | Four consecutive collapsed visits A–B–A–B, A different from B, within one continuous resolution group; optional time limit. General nonalternating repeated-return grammars are not implemented. |
| P12 | Residence/progression | Append-only observed fixation-span sums and frontier progression updates; optional explicit long/slow flags. |
| P13 | Unknown/break | Resolution-specific uncertainty and continuity reset; terminal boundaries retained. |

P01–P04 draw on [Olivier et al.](https://doi.org/10.16910/jemr.15.4.5).
[Yamaya et al.](https://doi.org/10.2197/ipsjjip.25.100),
[Parker et al.](https://doi.org/10.1016/j.visres.2018.12.007) and
[von der Malsburg & Vasishth](https://doi.org/10.1016/j.jml.2011.02.004)
provide related segment, sweep and rereading precedents. NewsMead's line bins,
frontier grammar, A–B–A–B detector and numerical settings are operational
adaptations. Their implementation is not study validation of difficulty.
See the [primary-study ledger](scanpath-scaffolding-evidence.md) for populations,
hardware, findings and transfer limits.

## Configuration and abstention

Default replay emits basic movements, revisits/resumption, structural cycles and
continuous residence/progression descriptors. It makes no default long/slow
judgment. P09 evaluates line-transition opportunities but abstains without both
explicit endpoint settings and a known left-to-right condition.

| Setting | Units / requirement |
| --- | --- |
| `return_sweep_end_fraction`, `return_sweep_start_fraction` | Normalized rendered-line x position; provide both, 0 <= start < end <= 1. End region is at/above end fraction; start region at/below start fraction. |
| `text_direction` | Optional `ltr`/`rtl` declaration for a verified study condition when geometry lacks direction. P09 requires LTR; unknown direction abstains and explicit RTL geometry cannot be overridden. |
| `cycle_window_ms` | Optional positive maximum elapsed span for A–B–A–B; otherwise the structural grammar uses uninterrupted visits without a separate time limit. |
| `word_residence_ms`, `line_residence_ms` | Positive observed-span threshold for a long flag at that resolution. Missing time does not add to residence. |
| `progression_window_ms` | Positive target minimum window duration; the latest visit anchor at least that duration behind the current endpoint starts the window, so actual elapsed duration is retained. |
| `word_progression_max_units_per_s`, `line_progression_max_units_per_s` | Nonnegative observed frontier-rate threshold, in word ordinals/s or physical lines/s. These are not words actually read per second. |
| `progression_min_observed_fraction` | Required with slow thresholds, in (0, 1]; observed fixation-span sum divided by analysis-window elapsed time. Slow flags also require an explicit window and enough elapsed evidence. |

Unset thresholds produce null flags. Invalid/nonfinite/boolean settings and
unknown keys are rejected. `text_direction` is a validated string setting rather
than a numerical threshold. No values are calibrated difficulty probabilities
or universal reading-speed standards. Select/freeze values on development data,
retain the exact configuration, and evaluate on untouched readers/passages.

P09 uses raw screen pixels, text origin and actual line bounds/visible rectangle.
An explicitly unsupported text direction, missing geometry or out-of-line
location causes abstention. Existing schema-1 logs do not record bidi direction;
verify the left-to-right study condition and declare it explicitly in settings,
or supply actual direction in geometry. Unknown direction produces no positive
sweep. It permits an undershoot within the
configured start region rather than demanding the first word.

P12 retains observed span separately from elapsed time/coverage and censoring.
`observed_threshold_crossed` describes whether the measured span crosses the
supplied threshold. `long_flag` is true after crossing, otherwise null because
ongoing/censored updates cannot establish a short complete visit. Censored
visits do not become complete visits. Progression is change in the
previously observed frontier, not proof that intermediate text was read. Line
pace is in physical lines and does not establish text-normalized reading speed.

## Evidence timing

Candidate fixation end time is not detector availability time. Analyzer v5
records the observation that closes a candidate: valid delivery time where
logged, otherwise trigger capture time labeled as a lower bound. Geometry/footer
boundary time is retained separately; missing or inconsistent timing stays null.
The terminal footer includes writer draining, so it cannot estimate timely live
aid response. Event availability aggregates its supporting evidence conservatively.

These timestamps describe what the recorded stream could reveal. They do not
measure live classifier runtime, render delay or physiological eye-to-cue latency.
Finalized-fixation prefix checks do not turn EOF-flushed diagnostic candidates
into a verified on-device causal extractor.

## Run and check

~~~powershell
python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json
python tools/gaze/analyze_reading_measurement.py recording.jsonl --output analysis.json --pattern-config settings.json
python -m unittest discover -s tools/gaze -p test_analyze_reading_measurement.py -v
python -m unittest discover -s tools/gaze -p test_scanpath_patterns.py -v
~~~

`settings.json` is an explicit JSON object containing selected keys above. Keep
participant recordings/results in approved private storage. Software fixtures
check named rules, uncertainty, timing and integration; they do not validate
phone eye placement, physiological fixations, difficulty or visual-aid benefit.

Verification checkpoint: **52 focused tests passed** (39 extraction/integration,
13 classifier). Independent review findings were corrected and covered by
regressions for backward episode movement, ordered complete resumption evidence,
unknown/RTL direction and censored residence. No device data was used.

## Live Android checkpoint — 2026-10-01

The live engine now runs from the article's raw MGazeNet `Observation` callback:

- `app/src/main/java/com/newsmead/gaze/scanpath/ScanpathEngine.kt`: incremental
  provisional I-DT extraction and P01–P13; extractor/classifier version 1.
- `ScanpathModels.kt`: typed raw sample, UTF-16 word, physical-line geometry,
  configuration, fixation and event contracts.
- `ScanpathTextGeometry.kt` and `ArticleScanpathSession.kt`: physical-screen
  origin/visibility, actual Layout lines, conservative bidi direction, raw word
  assignment and a 150 ms geometry eligibility guard. Layout/bidi data are cached;
  scrolling still updates screen bounds before drawing and receiving gaze.
- `app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt`: one raw
  observation entry, lifecycle/provider boundaries and in-memory pattern counts.

Both resolutions retain independent assignment agreement. Layout/text changes,
invalid observations, timestamp reversals, sample/unclassified gaps and screen
interruptions break continuity. Changing body text/locale starts a new generation;
event/fixation IDs are scoped to that generation. Touch input is not fed as gaze.
The adapter does not record raw gaze. Its events and established ongoing-prefix
evidence now feed the independent [aid runtime](scanpath-aid-runtime.md), which
keeps a bounded numeric action journal for explicit private export. The schema-1
diagnostic logger and frozen validation protocols retain their existing meanings.

Default live settings match the reference extractor: half a line-height
dispersion, 100 ms minimum span, at least three samples, 125 ms maximum sample
and transition gaps, and 80% assignment agreement. These are provisional
engineering settings. Generic-engine P09 endpoint and P12 long/slow settings remain
unset; the article runtime supplies the explicit experimental profile in the
runtime guide, including 0.25/0.75 endpoints and configured residence/progression.
The live pending buffer is capped at **4,096 samples**; exceeding it right-censors
the established candidate, emits `sample_buffer_limit`, and starts a fresh
segment. This memory guard is not a reading threshold. Android uses
`retainEvents=false`: callbacks receive events without retaining every support
list. Only the current full geometry snapshot is retained. The Android adapter
rotates after **512 finalized candidates**, emitting `history_limit` and starting
a new generation with the capture-time floor preserved. This bounds retained
history and P12 prefix-support work; no episode joins across that resource break.

**Causal boundary contract:** live P13 is emitted immediately on an explicit
boundary, with no future fixation support, and resets both paths. Offline batch
P13 can instead describe a between-segment break using the next fixation.
Therefore these P13 representations are intentionally different. Behavioral
P01–P12 and common fixation fields are compared at each raw-action prefix;
event IDs and episode group IDs are normalized, numbers to eight decimals.
P13 immediacy/reset and censoring are tested separately. Pattern emission waits
for candidate finalization. `currentCandidateEvidence()` separately returns an
established ongoing compact prefix with capture/delivery, assignment agreement
and observed duration. It does not mutate finalized candidates, increment P04 or
emit a P12 event. The aid policy may add its observed duration to earlier
finalized within-visit evidence, with separate provenance.

**Software checks:** nine focused engine JVM tests plus one parity test covering
**13 synthetic cases / 183 raw-action prefixes** pass. The 52 Python reference
tests still pass. `:app:assembleDebug` and `:app:compileDebugAndroidTestKotlin`
succeed. Two Android geometry tests cover UTF-16/origin mapping and cached
layout/mixed bidi; two additional aid drawing tests cover magnifier preservation
and expiration. [Device check 01](scanpath-device-check-01.md) initially found
three detached-fixture layout failures; after correction and authorized retest,
**all four passed on SM-A566B/API 36**. These are synthetic view checks, not live
article/gaze validation. Aid policy and real
engine-to-policy tests cover variable strength, source support, controls and
invalid-input clearing. Focused independent
review found excessive repeated layout scans and retained scroll geometry;
both were corrected and closure-reviewed. Integrated review also led to bounded
history and embedded numeric action support. Human accuracy, phone runtime latency,
natural fixation validity and aid benefit remain unmeasured in this checkpoint.

Reproduce the reference fixture without reading participant data:

~~~powershell
python tools/gaze/build_live_scanpath_parity_fixtures.py --check
& 'C:\Program Files\Java\jdk-17\bin\java.exe' '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-17' -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --gradle-user-home "$env:USERPROFILE\.gradle" --offline '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest --tests 'com.newsmead.gaze.scanpath.*' --tests 'com.newsmead.gaze.aid.*' :app:assembleDebug :app:compileDebugAndroidTestKotlin
~~~

The explicit Java 17/in-process compiler avoids the existing Java 21/Kapt
compatibility issue and reuse of a Java 21 Kotlin compiler daemon.

## Remaining implementation

The independent policy, adjustable aids, reader controls and bounded action/render
journal are integrated; see the [runtime checkpoint](scanpath-aid-runtime.md).
The [aid design contract](scanpath-adaptive-aid-design.md) specifies independent
forms and source-linked rules; not every pattern activates help. The next build
is the matched raw placement/continuous-order evaluation instrument. Complete
that instrument before running matched prompted raw/order tasks or
participant aid evaluation. Whole-path clusters and HMM/HSMM phases are outside
this deterministic implementation. The live and offline events provide inspectable inputs
for the prototype; software checks accompany implementation.

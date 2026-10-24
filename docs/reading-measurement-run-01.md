# First natural-reading diagnostic — 2026-09-13

Session: `reading_measurement_d223e030-6454-40a6-8517-51e769f42811`.
Recording SHA-256: `0afd72a5be4806922116b0ed9eb948f9d149aa99df207593fdbf4144bd6c5a45`.
Raw recording read from the phone in memory; this document retains aggregates only.
No phone installation, calibration change, or recording modification was performed.

The reader reports the orange dot followed horizontal/vertical movement but was vertically
misaligned by roughly one or two lines, using a calibration that was not fresh. This is
self-reported spatial error, not independently measured ground truth. Treat the run as
a diagnostic with reported misalignment, not a reading-instability validation sample.

## Recording and extraction

| Quantity | Result |
|---|---:|
| Duration | 86.47 s |
| Recorded observations | 1,537 |
| Logger dropped records | 0; complete manual-stop footer |
| Provider coordinate observations | 1,523 |
| Measurement-eligible observations | 1,348 (87.7%) |
| Geometry-settling exclusions | 159 |
| Outside-visible-text exclusions | 16 |
| Eye-area / no-face exclusions | 13 / 1 |
| Median / P95 capture interval | 58.63 / 66.96 ms |
| Geometry changes after initial snapshot | 356; all include vertical text-origin movement |
| Detected fixation candidates, default parameters | 87 |
| Candidates with ambiguous word assignment | 35 (40.2%) |
| Sum of detected fixation spans | 11.36 s |
| Sum of word-assigned fixation spans | 6.74 s |
| Eligible word transitions after gap correction | 4 |
| Eligible observed-first-pass visits after correction | 0 |

Eligibility is not tracking accuracy. Median capture spacing corresponds to approximately
17 Hz. The acquisition logs are intact, but the default raw-coordinate detector retains
only a small part of the session as fixation spans. This does not establish whether the
cause is spatial noise, detector settings, reading movements, or their combination.
The visible dot is smoothed; raw and displayed coordinates are both retained.

## Analyzer defect identified and corrected

Version 1 reset sequence continuity for invalid samples, scrolling and missing frames,
but could bridge long intervals of continuously sampled yet unclassified gaze between
detected fixations. Of its 24 purported eligible word transitions, 20 had inter-event gaps
over 125 ms. Intervening short/noisy fixation candidates may have been discarded; those
transitions cannot support confident skipping or rereading inference.

Analysis version 2 also breaks word-sequence/visit continuity across unclassified
inter-fixation gaps exceeding the configured 125 ms gap limit. This is a provisional
engineering exclusion, not a medically established threshold. Two regression tests were
added; all 16 replay tests pass. The Android app and saved recording are unchanged.

| Dispersion limit (line heights) | Candidates | Ambiguous words | Eligible transitions after correction |
|---|---:|---:|---:|
| 0.25 | 22 | 6 | 0 |
| 0.50 (default) | 87 | 35 | 4 |
| 0.75 | 165 | 90 | 5 |
| 1.00 | 213 | 139 | 9 |

Widening dispersion admits more candidates but also increases word ambiguity; these are
sensitivity results, not justification for choosing a wider limit. The apparent zero
rereading/regression values or high skipping fraction must not be described as the
reader's behavior: word sequences are sparse and the reader reports a substantial offset.
No RII is calculated or inferred from this run.

## Next comparison

Use a fresh calibration, verify alignment on text, then collect a short comparable
recording. Inspect raw-versus-display behavior, event coverage, word assignment and
threshold sensitivity before selecting detector parameters or building the composite.
If alignment remains wrong, document that separately from reading behavior. Retain this
run as a useful acquisition/detector diagnostic rather than tune a correction to it.

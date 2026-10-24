# One calibrated, smoothed path for live reading

## Current implementation — 2026-10-13

**Bookmark amendment:** current runtime policy **7** and smoothed condition
suffix **V2** add a 500 ms delivery-time missing-gaze bookmark. The table and
166-test checkpoint below describe the initial policy-6 stream migration;
its shared-coordinate contract is retained. See
[current bookmark behavior and checks](scanpath-reading-bookmark.md).

The owner authorized using the same calibrated, smoothed point for the orange
dot, live scanpaths and aid decisions. The previous raw controller route is
historical. Saved calibration and the existing filter parameters are retained.

```text
phone model → saved SVR calibration → eligibility/age gate → One Euro filter
                                                          │
                                      provider Observation.x/y
                                                          │
                              ┌───────────────────────────┴──────────────┐
                              ↓                                          ↓
                         orange dot                         rendered word/line mapping
                                                                         ↓
                                                         candidates and P01–P13 paths
                                                                         ↓
                                                             independent aid rules
```

`Observation.rawX/rawY` retains the calibrated point before filtering.
`Observation.x/y` is the existing article-filter output. Sessions select
`article_smoothed_v1`; they never run a second filter. Mapping, dispersion,
centroids, ongoing evidence, guide/definition anchors and bookmark departure
checks all use that selected stream. Missing or nonfinite filtered coordinates
cannot be replaced with a raw point. Controls/consultation, scroll/reflow,
capture ordering, gaps and expiry retain their interpretation exclusions. The
dot displays the provider point over controls, while excluded input cannot
contribute reading evidence.

## Exact identities and records

| Component | Current contract |
| --- | --- |
| Filter | Existing One Euro: minCutoff **0.7**, beta **0.002**, capture-time clock; parameters unchanged |
| Coordinates | Physical screen pixels; text offsets are Android UTF-16 |
| Article condition | `SMOOTHED_ARTICLE_DEFINITION_V1` |
| Pilot conditions | `SMOOTHED_PILOT_DEFINITION_<condition>_V1` |
| Replay condition | `SMOOTHED_REPLAY_DEFINITION_V1` |
| Aid configuration | `DEFINITION_PROFILE_V4`, policy version **6**; visual rules/timings unchanged |
| Pilot | `newsmead_scanpath_pilot_v2`, schema **2**, `raw_scoring=false`, `processed_scoring=true` |
| Trace sample | `x/y` = selected processing point; separate `raw_x/y`, `display_x/y`, `coordinate_stream`, nonfinite field flags |
| Article action export | Schema **2**, selected/original numeric coordinates and declared stream in input context; explicit private export |

Live pilot enables the same provider filter and scores its output. Its dot stays
hidden by the pilot protocol; that does not change the selected coordinates.
The summarizer verifies selected/display/provider pairing and original raw
retention. Historical schema-1 pilot records are explicitly interpreted as
`legacy_raw`; past measurements are not rewritten or pooled across conditions.

Replay delivers **prescribed post-filter synthetic trajectories**. Its visible
cursor and engine get the same float coordinates, with raw originals **null**.
It declares `input_phase=post_filter_synthetic_trajectory` and
`smoothing_applied_to_synthetic_input=false`. Replay tests controller behavior;
it does not simulate or validate the model's filter response. Separate fixtures
exercise real One Euro output differing from the original point.

## Verification and limits

Focused checks cover deliberately disagreeing raw/filtered points, selected
mapping and dispersion, raw retention, mixed-stream boundaries, missing/NaN
filtered data, selected pilot scoring, runtime aid eligibility, consultation
capture floors and geometry changes. Build/check evidence is retained under
`output/smoothed-scanpath-check-01/summary.json`: **166 JVM tests, 10 host checks,
zero test failures/errors**, and successful app/test APK assembly. The initial
host command failed before tests due to its import path; owner approved the
successful discovery retry. Review corrections cover the replay policy-version
assertion and provider/selected/display trace pairing. Native fixtures compile
but were not executed on a phone; no device install or camera activity occurred.

Frozen independent validation and schema-1 natural-reading diagnostics keep
their original protocols and stream identities. No accuracy radius or detector
threshold was changed. Matching the dot and controller repairs their software
consistency; it does not prove word accuracy, physiological fixations, difficulty
classification or reading benefit. Smoothing changes timing/dispersion, so prior
raw-path scores do not validate the new stream. The
[One Euro authors](https://gery.casiez.net/1euro/) describe a jitter/lag tradeoff,
not validation of NewsMead's detector or aid rules.

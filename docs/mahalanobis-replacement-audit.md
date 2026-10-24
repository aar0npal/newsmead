# Mahalanobis replacement audit

Audit before integration, 2026-10-17. Branch `measurement/mahalanobis`, starting
HEAD `0833cde7`; merge base with `main` is `ba80c9f0`. Existing local edit:
`.idea/gradle.xml` selects Temurin 17 and is outside this change.

## Active paths and provenance

`StudyConfig.SCANPATH_READING_AIDS=true` selects the article path:
`MgazeNetGazeProvider` -> `ArticleScanpathSession` -> `ScanpathEngine` ->
`ArticleReadingAidRuntime` / `ReadingAidPolicy` / focus, continuation and bookmark
policies -> aid renderers. The article's older R/D/F estimator/controller is
bypassed by this flag. Home and article menus additionally launch
`ScanpathPilotActivity` and `ScanpathReplayActivity`; both appear in the manifest.
Their layouts, scenario/task models, trace writers, and dedicated tests are
detector-specific. `scanpath_patterns.py` is also called by the offline reading
measurement analyzer and the parity-fixture builder.

History includes e5fa5b24 (live aids), 50ff2b75 (replay), b2b7a237 (pilot),
521ad6de (contextual aids), e86228c7 (dictionary/integration), f10ec53b (lookup),
and 0833cde7 (motion compensation). No external dependency is exclusive to this
classifier. Historical Mahalanobis branches are not the source of this change.

## Reusable source

Preserve gaze estimation, saved spatial calibration, smoothing/blink filtering,
`TextTarget`, `LineAoiMapper`, `GazeTargetStabilizer`, `ReadingStateInferencer`,
`GazeOverlayView`, navigation and research session logging. Extract text geometry,
word mapping, coordinate provenance, geometry timeline, capture-time mapping,
and consultation exclusion from the old package into neutral reading components.
Preserve the independent displaced-return evidence in `AdaptiveScaffoldController`.
The navigation controller uses coordinates independently of classification.

## Existing metrics and lifecycle

`ReadingStateInferencer` counts one sustained line stay after 300 ms, completed
line dwell lasting at least 120 ms, and regressions of at least one line behind
the maximum reached line after 300 ms confirmation. A regression episode is
rearmed after reaching the prior high-water line. These are line-based proxies.
`WindowedStabilityEstimator` calculates cumulative counter differences:
R = regressions/minute, D = completed dwell milliseconds/elapsed milliseconds
(clipped to [0,1]), F = sustained line-stay events/minute. All use the same span.
It retains one sample before the nominal left boundary.

Existing settings: 10,000 ms window, 500 ms metric updates, 2,000 ms minimum
metric span, 20,000 ms automatic per-article warm-up, eight baseline vectors,
0.65 sample-validity fraction, 1,500 ms escalation and 3,500 ms recovery.
The weighted positive-z estimator is replaced, retaining the extraction rules.
Full windows replace the 2-second startup span for baseline/scoring.

There is **no standardized unassisted baseline-reading activity**. The existing
reading-validation activity is an accuracy task with guided checkpoints, and must
not be relabeled as baseline data. A separate explicit baseline phase is required.
Its fixed passage can reuse the existing validation text as a provisional stimulus;
this does not establish stimulus suitability or sufficient baseline duration.

## Removal boundary

Remove classifier engine/config/event models, pattern-driven aid policies/runtime,
pilot/replay screens and scenarios, associated logs, manifest/menu entries, and
classifier tests. Preserve geometry and input tests under neutral names. The
existing scaffold renderer/controller becomes the sole adaptive output, driven
by confirmed Mahalanobis deviation. Historical research/evidence documents and
private output artifacts are retained and explicitly identified as historical.

## Measurement limits

Completed-dwell accounting attributes a visit when it closes; it can lag an
ongoing stay and cross the rolling boundary. This existing feature definition is
preserved and reported, not silently replaced with physiological fixation time.
Overlapping windows are correlated, empirical training percentiles are in-sample,
and neither software tests nor baseline deviation establishes reading difficulty.

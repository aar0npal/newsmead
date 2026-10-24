# Live reading blink filter

## Behavior — 2026-10-15

The owner reports closed eyes still producing rapid vertical gaze movement.
Source inspection finds the older 16-point path uses an eyelid/eye-width ratio
with 0.040 close and 0.055 reopen hysteresis. Current MGazeNet admission only
checks its pinned pixel-polygon eye areas (>10); this is not the same guard.
The report motivates this change; no human blink-labeled trace was collected.

- **Enabled:** normal live articles and live Scanpath pilot. The same final
  selected point drives the orange dot, live scanpaths and aid input.
- Compute each eyelid gap perpendicular to that eye's corner axis in upright
  image pixels, divided by eye width. This is aspect/roll-correct and unitless.
  It uses the existing landmarks (33/133,159/145;263/362,386/374).
- Either ratio **below 0.040** blocks output. Both must exceed **0.055** on
  **two fresh consecutive frames** to resume; startup and missing eye geometry
  also require that opening guard. An open eye state retains its state in the
  threshold deadband. These initial values reuse the old tuning; the per-eye,
  projected ratio/reopening rule is a new live adaptation, not a validated model.
- Closed/reopening/startup-guard frames have reason `blink`; unavailable ratios
  have `eye_state_unavailable`. Both have **null selected X/Y** and no `onGaze`
  callback. Existing article code hides the dot on non-coordinate outcomes.
  `blink` is an exclusion reason, not a physiological blink event/count.
- The clock/expiry gate runs first: duplicate, reversed or >500 ms old captures
  cannot count toward reopening. Invalid frames reset reading smoothers. First
  admitted open frame starts a fresh filter; closed-eye predictions cannot pollute it.
- Raw model predictions may remain as explicitly excluded numeric diagnostics.
  They do not receive line/word labels or become reading samples. No camera image
  is retained. Normal blink exclusions are not added to focus/bookmark look-away
  loss sets, and do not contribute observed reading recovery time. Existing brief
  focus presentation grace remains; blink gaps break scanpath continuity.
- Blink/unknown-eye intervals also cannot extend a preceding short no-face glitch
  into a qualifying focus loss. Missing-source spans that already met the loss
  threshold, or already qualified before eye exclusion, may still complete on
  a confirmed return. Source reasons remain separate.
- Very small eye areas with measured closed lids are labeled `blink`; no-face
  and invalid-crop failures retain their existing reasons.

## Scope and audit

`rejectBlinks` defaults false in the provider. Only live article and live Pilot
explicitly enable it and camera eyelid telemetry. Calibration, GazeTest and frozen
ReadingValidation modes retain their previous crop arithmetic, model/features,
eligibility and output settings. Saved calibration stays compatible; no automatic
calibration or collection is started. Replay consumes its synthetic inputs.

Provider observations optionally carry per-eye ratios and an applied flag.
Article diagnostic/Pilot logs retain those numeric fields when the user records.
Aid configuration records enabled flag, `EYELID_RATIO_HYSTERESIS_V1`, thresholds,
geometry and reopen count; existing missingness/exclusion denominators remain.
Human false closures while reading downward, reopening delay, glasses and phone
angle still need a manual check. Software fixtures cannot establish blink
sensitivity/specificity or improve the saved calibration's spatial accuracy.

## Verification

Independent source review caught the mixed no-face/blink duration case above;
the correction and regression fixtures are reviewed with no remaining actionable
findings. All **70/70** focused JVM checks and both APK builds pass. With the
owner's approval, app/test updates on the A56 preserve app data and calibration;
**2/2** camera-free native checks pass in **3.276 s**. Evidence:
`output/live-blink-check-01/summary.json`, build/install/native logs and copied
JVM XML results.
Camera-free native fixture uses the production output helper and actual TextView
geometry with real elapsed clocks and waits off the UI thread: closed/reopening
frames are excluded and cannot join old/new reading fixations. The second check
covers focus scroll continuity and fresh geometry anchoring. No camera started;
actual eyelid detection still needs a manual live article check.

Source: `MgazeNetEyelidGeometry.kt`, `MgazeNetBlinkGate.kt`,
`MgazeNetLiveOutput.kt`, `MgazeNetCameraSource.kt`, `MgazeNetGazeProvider.kt`;
integration: `ArticleFragment.kt`, `ScanpathPilotActivity.kt`,
`ArticleReadingAidRuntime.kt`, `ReadingMeasurementLog.kt`.

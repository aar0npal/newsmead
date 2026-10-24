# Synthetic accuracy fixtures

Neither file contains participant observations, camera pixels, landmarks,
real gaze-model features or a person's fitted calibration.

- `accuracy-contract-synthetic.json`: small analytical example for the generic
  stationary scorer. It deliberately does not claim the Android harness protocol.
- `accuracy-harness-kotlin-synthetic.json`: frozen complete export from
  `AccuracySessionTest` using invented features and predictions. Copied from the
  preserved `synthetic-kotlin-export-integrity.json` on 2026-09-07. SHA-256:
  `bd4cd99ccb05c6ed534ddc0ad472cd4e2e13392080305fea0d677dc8a187f619`.
  One of eight test blocks is deliberately empty. Integrity tests use this
  fixture so they run without depending on ignored Gradle outputs.

The current JVM-generated export can separately be scored with
`accuracy_metrics.py`; use a new output path. Do not regenerate this reference
merely to make a failing integrity test pass. Investigate contract differences
and update its provenance when an intentional protocol revision requires it.

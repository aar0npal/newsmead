# Current NewsMead versus MGazeNet host-build checkpoint — 2026-09-12

Status: the software-only comparison controls, strict offline scorer and two
exact comparison APKs are host-verified for frozen protocol
`newsmead_current_vs_mgazenet_reading_v1`. Device installation, CAMERA,
participant calibration and four-slot collection remain unauthorized and were
not performed during this checkpoint.

## Frozen build identities

Both APKs use application ID `com.newsmead`, version code 1, version name `0.2`
and debug signing certificate SHA-256
`3056e94475f68ae47cd7d25babf34eeb7d201acb97999f20eaa3ab95fa200332`.

| Arm | Frozen estimator base | Comparison controls SHA-256 | Final comparison APK SHA-256 | Bytes |
| --- | --- | --- | --- | ---: |
| Current NewsMead | `ec635fd01905544c8251c37b6891105ebbdee923` | `e577ef8f8f27e7bd0aef688ee1ff9e6153f3f41aaf76a7cc1b2cdfe28b9b4dea` | `2147ff1b1e3e1761e144d95499001c58dad0aa4b932d0c737611062ffa93eb87` | 84,746,470 |
| MGazeNet primary | `bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22` | `fc5e1219506731e622d236813aa731606a640e1a7ce3005f1863d4bf4658ab7f` | `8146df9bbab6facf17fab4687c2cee4fb79589d4a3ce0201bf8a3bf434dc50ea` | 47,861,232 |

The frozen protocol manifest remains byte-identical at SHA-256
`41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185`.
The immutable collection manifest is
`tools/gaze/mgazenet/manifests/newsmead-current-vs-mgazenet-collection-v1.json`,
SHA-256 `d1a6094d399b9788f5dd15cefce6754fdc3e4a50003e2e7b61776083ae59e9ad`.

Exact APK copies and their SHA sidecars are retained only under ignored
`tmp/newsmead-current-vs-mgazenet-reading-v1-builds/`. They must not be staged.

## Implemented comparison controls

Both estimator-specific builds now require the exact protocol ID, slot ID and
order variant. The fixed slot mapping rejects the wrong estimator, phone model
or order, and comparison order never falls back to preferences. Setup remains
camera-off and mutation-free until explicit Start.

Each slot reserves one non-replaceable record after Start. Calibration and
reading records bind the estimator base and installed APK hash, protocol hash,
slot, order, calibration fingerprint, device instance, physical screen,
rendered reading layout, passage and `elapsedRealtimeNanos` clock. Completed,
failed and interrupted outcomes are retained; successful JSONL files receive a
device-written SHA-256 sidecar.

Current NewsMead comparison calibration forces telemetry OFF, requires all 16
fit targets without exclusion, applies no correction, clears old drift through
the existing fresh-calibration save, masks its spatial gate and continues
without result-based Accept/Redo selection. MGazeNet uses its unchanged 45 x 16
finite 258-feature procedure and six descriptive post-fit checks, masks their
values and saves a technically successful fit without result-based selection.

Reading uses protocol v4 with vertical alignment OFF, the unchanged target/AOI
and stabilizer implementations, monotonic delivery timestamps and raw plus
stabilized assignments. MGazeNet additionally records linked capture age,
invalid/stale outcomes and cumulative busy drops. Its source is fully drained
and its exact terminal analyzer-arrival and busy-drop totals are written before
the slot log is sealed. Comparison completion shows only the artifact name and
hash; spatial results remain offline.

The offline scorer validates all four device-written artifacts and sidecars
before producing spatial output. It reconstructs the stabilizer, checks the
exact target/state/timing sequence, treats off-text coordinates as incorrect,
keeps empty targets null, computes target-balanced endpoints and availability
at 50/100/200/500 ms, requires the terminal MGazeNet counters immediately
before session sealing, and applies only the frozen paired classification rules.

## Reproducible current-arm construction

The preserved `gaze-pipeline-improvements` branch still resolves to
`ec635fd01905544c8251c37b6891105ebbdee923`. Its frozen source archive remains
SHA-256 `5c631be6d12892768b707d433eebbdab4c7f102f18738b2f9d21d19fe2518aca`.

`tools/gaze/mgazenet/materialize_current_comparison.py` verifies that branch,
archive and the allowlisted current-arm patch, refuses an existing output,
materializes below ignored `tmp/`, restores the frozen manifest's exact bytes,
and checks that shared controls match the MGazeNet worktree. The verified patch
is `tools/gaze/mgazenet/patches/current-reference-ec635fd-comparison-v1.patch`.
The reference branch and preserved reference directory were not modified.

The MGazeNet comparison controls are independently frozen as
`tools/gaze/mgazenet/patches/mgazenet-primary-bea2dac-comparison-v1.patch` so
the final APK can be audited against its estimator base without treating the
comparison-mode edits as estimator changes. Neither patch introduces a runtime
backend selector.

## Host verification

Java 17.0.14 was used for both Android builds.

- MGazeNet: the focused `com.newsmead.gaze.*` JVM suite, Android-test Kotlin
  compilation, pinned vendor verification, debug APK verification and
  `assembleDebug` passed.
- Current reference: a clean materialization from the frozen archive and patch
  passed the focused `com.newsmead.gaze.*` JVM suite, Android-test Kotlin
  compilation and `assembleDebug`.
- APK signature verification passed for both exact APKs with one matching v2
  signer and the certificate SHA-256 above.
- The full MGazeNet Python host suite passed 85 tests using the pinned OpenCV
  4.11.0 and NumPy 1.26.4 environment. Ten of those tests cover the new strict
  comparison scorer, including complete directional cases, mixed results,
  hash-valid interruption, an empty target, wrong builds/hashes, altered
  protocol/stabilizer/correction/timing records, invalid-event availability and
  exclusive output creation.
- `git diff --check` passed for tracked worktree changes.

The ordinary app-wide Firebase unit test remains outside this focused offline
suite because it depends on external Firebase initialization; it is unrelated
to the gaze comparison controls. The existing 4 KB-aligned native-library
limitation also remains unchanged.

## Stop boundary

Stop here. The next distinct authorization would cover installation of these
exact preserved APKs and camera-disabled setup/layout checks on the A56 and
G991B. It must still exclude Start, CAMERA, participant calibration and slot
collection. Four-slot collection requires a later explicit authorization after
those checks pass.

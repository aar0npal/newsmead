# MGazeNet direct-validation confirmation device check — 2026-09-09

Status: the camera-free confirmation setup and actual-layout check passes on
both the A56 and G991B. No Start button was pressed, CAMERA was denied, no
participant calibration or measurement occurred, and no confirmation record
was created. The active NewsMead tracker under `app/` remains unchanged.

Read after `gaze-mgazenet-direct-validation-confirmation-software.md`. This
checkpoint does not change the frozen confirmation targets, timing, thresholds,
orders or analysis rules.

## Pre-device validity repair

The earlier camera-free instrumentation constructed a generic artificial target
view. That established physical screen-coordinate behavior, but did not directly
exercise the confirmation activity's real 112 dp status header, weighted target
surface and Stop button. Before installing either artifact, production and test
code were changed to construct that exact surface through the shared
`ConfirmationMeasurementLayout` factory.

The new camera-free instrumentation test installs the shared production layout
without invoking the session start path or camera source, waits for real device
measurement, then verifies:

- a non-empty rendered target surface and its physical screen/viewport origin;
- the frozen 16 non-practice fit targets, five screen targets and twenty
  confirmation blocks;
- ten distinct independent confirmation locations, each repeated exactly twice;
- every planned point falls within the measured confirmation viewport; and
- the first target receives a post-draw presentation acknowledgement.

Opening-setup tests also continue to verify that the input, accuracy,
calibration-audit and confirmation activities do not set keep-screen-on or
create a record. Both order choices exist and neither is selected by default in
confirmation setup.

## Rebuilt host checkpoint

The layout-sharing repair was rebuilt and verified with Java 17:

- 153 benchmark JVM tests in 9 suites passed with zero failures, errors or
  skips;
- 74 MGazeNet Python tests passed;
- the debug app and instrumentation APKs assembled successfully;
- `git diff --check` passed; and
- `git diff --name-only -- app` was empty.

The exact APK pair for this device check is:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug app APK | 32,847,143 | `95cfc1c457955131e0271ae81472afd66d90a5006556d98fd05d53570410e73d` |
| Debug instrumentation APK | 386,886 | `9e80c0c8d542be19a66ebcfe9e537475fb87200042838681eb1c0adef52b6bbb` |

These supersede the pre-layout-check APK hashes only as installable build
identities. The protocol and previously verified synthetic record are unchanged.

## A56 camera-free result

Device `R5CY40Y2C5W` (`SM-A566B`) was the only connected phone. Before install,
both benchmark process IDs were absent, the effective CAMERA UID app-op was
`ignore`, and `files/confirmation` did not exist. The exact pair above was
installed with CAMERA explicitly revoked and its app-op set to `ignore` again
after install.

`AccuracyHarnessTest` passed all six tests on-device in 4.368 seconds, including
`confirmationMeasurementLayoutSupportsTheFrozenTargetPlanWithoutCamera`.
Afterward:

- the package permission dump reported
  `android.permission.CAMERA: granted=false`;
- the effective CAMERA UID app-op remained `ignore`;
- the historical CAMERA last-use entry did not advance during the check;
- both benchmark process IDs were absent after force-stop; and
- `files/confirmation` still did not exist.

The test therefore produced layout/setup evidence only. It did not start camera
capture, create a personal model, collect gaze coordinates or write a numeric
confirmation attempt.

## G991B camera-free result

Device `R5CR60YSJ2X` (`SM-G991B`) was connected next. Before install, both
benchmark process IDs were absent, the package permission dump reported
`android.permission.CAMERA: granted=false`, the effective CAMERA UID app-op was
`ignore`, and `files/confirmation` did not exist. The exact same APK pair was
installed with CAMERA explicitly revoked and its app-op set to `ignore` again
after install.

`AccuracyHarnessTest` passed all six tests on-device in 4.751 seconds, including
the shared production confirmation-layout test. Afterward:

- the package permission dump still reported CAMERA `granted=false`;
- the effective CAMERA UID app-op remained `ignore`;
- the historical CAMERA last-use entry did not advance during the check;
- both benchmark process IDs were absent after force-stop; and
- `files/confirmation` still did not exist.

As on the A56, the G991B test produced only setup/layout evidence and no
participant or gaze record.

## Current authorization boundary

The software and camera-free two-device checkpoint is complete. All four
confirmation attempts were subsequently authorized, completed and hash-
preserved before scoring. Continue from
`gaze-mgazenet-direct-validation-confirmation-analysis.md` for the frozen
inconclusive result. Both phones were CAMERA-denied and stopped at their latest
terminal checks.

Threshold changes, model promotion and active-tracker changes remain outside
this checkpoint.

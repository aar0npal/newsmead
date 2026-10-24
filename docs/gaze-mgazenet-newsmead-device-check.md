# MGazeNet primary integration: camera-disabled device check — 2026-09-10

The user committed the host integration as `5811486` and separately authorized
in-place installation and camera-disabled setup, geometry, native loading and
synthetic persistence checks. The worktree was clean at the start. No CAMERA
grant, camera capture, calibration Start, participant calibration or accuracy
trial is authorized or performed in this phase. Staging and commits remain the
user's responsibility.

## Exact artifacts

The existing host-built artifacts were verified before installation; no source
change or rebuild was needed:

- NewsMead APK SHA-256:
  `131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9`.
- Android-test APK SHA-256:
  `76bcf8759237409549f82257c6f360bd465ca0d7d3d4246a5c03e2d0f0feb7bd`.

Only these reviewed test classes run, nine tests total:

1. `com.newsmead.gaze.mgazenet.MgazeNetIntegrationTest`: idle setup and isolated
   synthetic native SVR persistence, reload, canceled-save rejection and cleanup.
2. `com.newsmead.gaze.mgazenet.NativePipelineTest`: RGBA stride/rotation,
   RGB/NCHW/right-eye flip, recycled-bitmap recovery, MNN 258-value synthetic
   inference/finite guards and native OpenCV SVR fitting.
3. `com.newsmead.gaze.GazeScreenCoordinatesTest`: actual idle calibration/reading
   geometry and an artificial inset/scrolled text/AOI/dot fixture.

The native persistence test uses synthetic inputs and an isolated cache-backed
context; it does not touch the participant calibration namespace. It verifies
reloading into a new store/model instance within the test process. It is not an
Android process-death/restart experiment or an accuracy measurement. Native
camera acquisition and the Face Landmarker camera path are not exercised.

## A56: passed

- Device: `SM-A566B`, serial `R5CY40Y2C5W`, 4096-byte page size, unlocked.
- Prior installed NewsMead APK SHA-256:
  `a0d857fce114d29bc232c24b14886e7db554849277e7dd435ecd59fcdcd5ee07`.
- CAMERA was initially granted. Before installation, NewsMead was force-stopped,
  the runtime grant was revoked, and the effective CAMERA app-op set to `ignore`.
- Both `install -r` updates succeeded. No uninstall or app-data reset occurred.
  Device-side hashes of both installed APKs matched the exact pair above.
- All **9 tests passed in 5.063 seconds**, with no failed or repeated test run.
- All **122 pre-existing protected gaze/calibration/reading file hashes** matched
  after installation and after testing. No new protected file or personal
  MGazeNet calibration was created.
- Both packages were force-stopped afterward; the NewsMead process was absent.
  CAMERA remained `granted=false` and effectively `ignore`.
- The historical CAMERA `allow` entry remained approximately four days old; its
  age increased throughout these checks and no new access was recorded.

Measured layout evidence:

| Layout | Physical geometry |
| --- | --- |
| Calibration view | origin `(0,101)`, size `1080×2239`, display `1080×2340` |
| Calibration centre | `(540,1220.5)` physical pixels |
| Artificial inset window | origin `(91,272)`, size `650×850`; scrolled AOI and dot checks passed |
| Reading root | origin `(0,0)`, size `1080×2340` |
| Reading viewport | origin `(0,259)`, size `1080×1373` |

This confirms use of the real view origin rather than hard-coding or omitting
the 101-pixel offset. No calibration target sequence was started.

Hash-only snapshots, instrumentation output and coordinate-only log output are
retained under ignored
`diagnostics-local/2026-09-10/mgazenet-primary-device-check/a56/`. They must not
be staged. No camera images or participant feature/model contents were copied.

## SM-G991B: passed

The user connected the second phone after committing the A56 report as
`cc3f4db`. The worktree was clean and the local APK hashes were unchanged.

- Device: `SM-G991B`, serial `R5CR60YSJ2X`, 4096-byte page size, unlocked.
- Prior installed NewsMead APK SHA-256:
  `f417ceece6d6654e9b66f0f1e153be7b084baf605c78f241b53414c9856426fb`.
- CAMERA was initially granted. Before installation, NewsMead was force-stopped,
  the runtime grant was revoked, and the effective CAMERA app-op set to `ignore`.
- Both `install -r` updates succeeded without uninstalling or resetting app data.
  Device-side hashes of both installed APKs matched the exact pair above.
- All **9 tests passed in 5.065 seconds**, with no failed or repeated test run.
- All **13 pre-existing protected gaze/calibration/reading file hashes** matched
  after installation and after testing. No new protected file or personal
  MGazeNet calibration was created.
- Both packages were force-stopped afterward; the NewsMead process was absent.
  CAMERA remained `granted=false` and effectively `ignore`.
- The historical CAMERA `allow` entry remained approximately four days old; its
  age increased throughout these checks and no new access was recorded.

Measured layout evidence:

| Layout | Physical geometry |
| --- | --- |
| Calibration view | origin `(0,80)`, size `1080×2320`, display `1080×2400` |
| Calibration centre | `(540,1240)` physical pixels |
| Artificial inset window | origin `(91,251)`, size `650×850`; scrolled AOI and dot checks passed |
| Reading root | origin `(0,0)`, size `1080×2400` |
| Reading viewport | origin `(0,238)`, size `1080×1414` |

The checked view origins differ from the A56 and are measured on this phone.
No calibration target sequence was started. Hash-only snapshots,
instrumentation output and coordinate-only log output are retained under ignored
`diagnostics-local/2026-09-10/mgazenet-primary-device-check/g991b/` and must not
be staged. No camera images or participant feature/model contents were copied.

## Fresh-process persistence follow-up

A separate camera-free instrumentation test now splits synthetic save and load
across two explicitly orchestrated invocations. The preparation invocation saves
a deterministic synthetic two-SVR bundle and hashes its finite predictions in a
dedicated `mgazenet-process-restart-test-v1` no-backup test namespace. After the
target package is force-stopped, the verification invocation must have a
different process ID, load the unchanged bundle and reproduce the exact
prediction hash. It then deletes the entire synthetic test namespace. Running
the class without an explicit `prepare` or `verify` phase skips it, preventing a
normal whole-suite invocation from claiming an in-process result as a restart.

The production APK remained unchanged at
`131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9`.
The rebuilt Android-test APK SHA-256 is
`3036a45ff50af7ca59737079cc9500c031ccd3a9e07c21e3f75733efec0b0945`.
The pinned vendor check and Android-test APK assembly passed.

On the SM-G991B, preparation passed in process `11087` and verification passed
in process `11659`. The calibration artifact hash and prediction hash matched
exactly after the forced stop. The synthetic namespace was absent afterward;
all 13 pre-existing protected file hashes remained unchanged; both packages
were stopped; and CAMERA remained `granted=false` and effectively `ignore`.
Evidence is retained under ignored
`diagnostics-local/2026-09-10/mgazenet-process-restart/g991b-final/` and must not
be staged.

On the SM-A566B, preparation passed in process `30688` and verification passed
in process `30815`. The calibration artifact hash and device-local prediction
hash matched exactly after the forced stop. The synthetic namespace was absent
afterward; all 122 pre-existing protected file hashes remained unchanged; both
packages were stopped; and CAMERA remained `granted=false` and effectively
`ignore`. Evidence is retained under ignored
`diagnostics-local/2026-09-10/mgazenet-process-restart/a56-final/` and must not
be staged.

## Interpretation and next boundary

Both phones passed all nine camera-disabled checks using the same exact APK pair.
This establishes executable synthetic model persistence and the checked layout
behavior on these devices, not calibrated gaze
accuracy, reading-event validity or population reliability. The known APK
16 KB page-size limitations remain; a 4 KB device pass does not resolve them.
Fresh-process persistence additionally passed on both phones.
CAMERA and participant calibration remain separate later authorization boundaries.

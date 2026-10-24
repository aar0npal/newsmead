# Gaze Study — Session Setup Checklist

> **Current local-tracker workflow (2026-07-20):** The WiFi/GazeFollower
> instructions below are historical. The current app uses the phone front
> camera, MediaPipe, 16-point local calibration, and no laptop stream.

For the current build:

1. Confirm GAZE_TOUCH_VALIDATION is false and SCAFFOLD_MODE is ADAPTIVE in
   StudyConfig. The current diagnostic build has GAZE_DEBUG_VISUALS enabled so
   researchers can see the gaze dot and FPS; disable it before participant-facing
   data collection.
2. Build/install with JDK 17.
3. On the **home screen**, tap "Calibrate eye tracker", grant camera permission,
   and complete all 16 targets while keeping phone/head position stable. Review
   the quality gate (error in px and line-heights) and Accept, or redo. The
   calibration file is written only on Accept.
4. Open a study article. The **accuracy check** is the dots control in the
   article top bar: it measures error over 9 points and can apply an affine
   drift correction (or revert it) without redoing the full calibration.
5. Return to the article. The adaptive condition spends its first 20 seconds
   collecting the provisional per-article baseline, during which no scaffold is
   shown.
6. Monitor GazeAOI, GazeRSI, and GazeScaffold. The last tag reports baseline
   readiness, confidence, level transitions, triggers, target lines, and recovery
   latency.

During monitoring, distinguish `GazeRSI score` (cumulative 0-100 Session RSI)
from `GazeScaffold index` (recent 0-4 Adaptive Instability Index). Predict
scaffold levels from the latter plus baseline, confidence, persistence, and
re-entry conditions; do not use the Session RSI score as the trigger.

For a natural adaptive-trigger smoke test, read normally during the 20-second
baseline, then spend 10-15 seconds pausing and rereading within one difficult
paragraph while keeping gaze on the article body. Do not interpret the
cumulative GazeRSI score as the scaffold trigger. Require baselineReady=true,
confidence at least 0.65, and a persistently elevated adaptive index. At an index
of at least 2.0, stepwise escalation can take about 4.5 seconds to reach FOCUS.
If confidence is 0.64 or lower, recalibrate or keep gaze on valid article text
for a complete 10-second rolling window before diagnosing the scaffold.

Confidence at least 0.65 is necessary but not sufficient: it measures visible-
text coverage, not correct-line accuracy. If the dot or scaffold remains
spatially unstable, stop the participant-facing run and record a known-target
accuracy check, dispersion/jump behavior, posture, lighting, scroll position,
and elapsed time since calibration. Do not interpret the transition as genuine
reading difficulty until tracker error has been ruled out.

For geometry validation without live gaze, enable touch validation and force
WORD, LINE, FOCUS, and REENTRY one at a time. See
adaptive-visual-scaffolding.md. Restore ADAPTIVE before a study session.

The remaining laptop/WiFi checklist is retained only for reproducing the older
GazeFollower rig and should not be mixed with the current local calibration.

Runbook for a live gaze-tracked reading session (NewsMead + GazeFollower over
WiFi). Follow top to bottom. Commands are for the PC; run them from a terminal.

`adb` lives at
`C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe` (or add it to
PATH). The laptop stream script `gazefollower_stream.py` lives in the
**gaze-prototype repo**: `C:\Users\USER\OneDrive\Desktop\gaze-thesis-prototype\tools\`.

---

## 0. One-time prep (per study build, not per participant)
- [ ] In `StudyConfig.kt`, set **`GAZE_TOUCH_VALIDATION = false`** (this is the
      only toggle that switches the article screen from finger-validation to live
      WiFi gaze). Build + install the study APK with **JDK 17**:
      ```
      $env:JAVA_HOME="C:\Program Files\Java\jdk-17"
      .\gradlew.bat assembleDebug
      adb install -r app\build\outputs\apk\debug\app-debug.apk
      ```
- [ ] Laptop has GazeFollower: `python -m pip install -r tools\gazefollower-requirements.txt` (first run downloads the model).
- [ ] Phone has USB debugging on; `adb devices` shows it as `device`.

---

## 1. Rig (every session)
- [ ] Phone sits **within / just in front of the laptop screen area**, laptop
      webcam above it, participant ~35–50 cm away. (GazeFollower calibrates to the
      laptop screen, so the phone must be inside that field.)
- [ ] Phone and laptop on the **same WiFi**.
- [ ] Head reasonably stable (chin rest / fixed posture) — run-to-run accuracy is
      posture-driven.

## 2. Get the phone's IP
```
adb shell ip addr show wlan0        # look for the inet a.b.c.d address
```
(Also logged by the calibration screen under tag `GazeCalib` once it opens.)

## 3. Start the laptop backend (streams gaze to the phone)
This is the **gaze-prototype** repo's script (NOT this repo — NewsMead has no
Python), run on the machine with the webcam:
```
cd C:\Users\USER\OneDrive\Desktop\gaze-thesis-prototype
$env:PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION="python"   # PowerShell; once per terminal
python tools\gazefollower_stream.py --phone-ip <PHONE_IP> --port 5005
```
- Needs `python -m pip install -r tools\gazefollower-requirements.txt` on that
  machine (protobuf must be **4.25.x** — mediapipe 0.10.18 needs `protobuf<5,>=4.25.3`).
- The `PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=python` line is required because
  TensorFlow 2.10 (also installed) and mediapipe want incompatible protobuf
  versions; pure-Python protobuf lets both load, and it does NOT slow gaze
  streaming (protobuf isn't in the per-frame path). cmd.exe form:
  `set PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=python`.
Flow: a camera preview opens (confirm it sees the face; close it) → **laptop
calibration dots** (participant looks at each) → streaming begins. A `[heartbeat]`
line should print a sample rate (~/s). Leave this running the whole session.
- Verify-only (no phone): `python gazefollower_stream.py --print --phone-ip 0.0.0.0`

## 4. Calibrate the phone (per participant)
Launch the 16-dot phone calibration:
```
adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity
adb logcat -s GazeCalib        # optional: watch pairs + "Saved"
```
The screen opens and **waits on a "Start" button** — it does NOT auto-run, so you
have time to position the phone in front of the laptop screen and confirm the
laptop is streaming. When positioned, **tap Start** and have the participant look
at each dot. On completion it shows "Saved" and writes `calibration_wifi.csv`
(app-private). Press back. Re-run per participant, or whenever posture/seating
changes.

## 5. Run the reading session
Open NewsMead (already on home — login is bypassed), open a study article, and
watch the live gaze-to-line output:
```
adb logcat -s GazeAOI          # gaze=(x,y) line=N/total, updates as they read
```
The red dot is visible because the current diagnostic build enables
GAZE_DEBUG_VISUALS. Disable it before participant-facing sessions. Current logs
use rawLine and stableLine; stableLine is
the debounced line supplied to RSI and scaffolding.

---

## Troubleshooting
- **0 UDP packets on the phone / dot doesn't move:** WiFi client isolation or
  firewall. Try a phone hotspot the laptop joins, or confirm same subnet.
- **`am start` → "Permission Denial: not exported":** already handled —
  `GazeCalibrationActivity` is `android:exported="true"` in the manifest (Android
  16 / One UI blocks adb from launching non-exported activities). If you ever set
  it back to `false`, adb can't launch it.
- **Calibration shows "0 samples, re-collecting" and never advances:** the laptop
  stream isn't reaching the phone. Start Step 3 (laptop stream) BEFORE launching
  calibration, and check the phone IP is right / same WiFi / no client isolation.
- **`GazeAOI` shows `line=-1` a lot:** gaze is landing in the margins/image, or
  calibration is off — re-run step 4. Sanity-check with touch first by temporarily
  setting `GAZE_TOUCH_VALIDATION = true`.
- **Accuracy poor / drifting:** re-seat for the rig (phone centered in the laptop
  screen field), stabilize the head, recalibrate (steps 3–4).
- **Build fails with `jdk.compiler does not export ...`:** you're on JDK 21; build
  with JDK 17 (`C:\Program Files\Java\jdk-17`).

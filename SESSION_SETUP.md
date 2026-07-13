# Gaze Study — Session Setup Checklist

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
The red dot follows gaze; `line=N` is the line being read (scroll-aware).

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

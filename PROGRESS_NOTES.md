# NewsMead — Progress Notes

## 2026-07-07 — Align MediaPipe gaze feature with the prototype

Reworked `gaze/MediaPipeRawGazeSource.kt` to match the sister-repo prototype's
`FaceLandmarkerHelper` feature extraction: front-camera frame is now mirrored
(`postScale(-1,1)` in `uprightMirrored`), each iris is paired to its nearer eye
via the `frac`-based corner/eyelid feature (iris rings 468..472 / 473..477), and
blink frames are dropped via eye-aspect-ratio openness with hysteresis
(BLINK_CLOSE 0.04 / BLINK_OPEN 0.055) before `onRawGaze` emits. Calibration and
live gaze share this source, so both benefit and stay self-consistent. Raw gaze
feature values are NOT comparable to the pre-change ones — recalibrate.

Follow-up (same day) — fixed low FPS / lost vertical accuracy: `MediaPipeRawGazeSource`
was running CPU inference on an uncapped-resolution stream with no FPS floor, so it
ran far below real time and starved the median/One Euro smoothing. Ported the
prototype's Stage 1 camera profile: GPU delegate with CPU fallback, 480x360 analysis
resolution, and `CONTROL_AE_TARGET_FPS_RANGE` pinned to 30. Also switched
`detectAsync` to the monotonic `nextTimestampMs()` (avoids MediaPipe timestamp
collisions at 30 fps). Added an FPS readout: `LocalRawGazeSource.OnFps` callback
surfaced from the source, shown top-right on the calibration screen (`fpsText`) and
top-left on the article `GazeOverlayView` during live reading.
Verified `:app:compileDebugKotlin` passes. NOTE: CLI kapt fails on JDK 21 without
`--add-opens jdk.compiler/...`; build in Android Studio or add those to
`gradle.properties` `org.gradle.jvmargs`.

Follow-up (same day) — crash fix + gaze test. On-device: repeated native SIGBUS in
`libmediapipe_tasks_vision_jni.so` (~75-90s in). Bumped MediaPipe tasks-vision
0.10.14 -> 0.10.29 (matches prototype, which crashed far less); crash gone in
testing. Diagnosed "dot stuck at top": logcat of live raw feature shows gaze_y
varies fine (0.19-0.38) and mapped Y sweeps full screen (108-2207) on deliberate
up/down, but median gaze_y in reading ~0.33 (upper part of calib range) so the dot
biases high during natural reading — this is the inherent appearance-based limit,
not a mapper bug. Ported the prototype's Stage 3 accuracy test as
`GazeTestActivity` (+ `GazeDotView`, `activity_gaze_test.xml`, `gaze_test_*`
strings): live calibrated dot on a blank screen + 3x3 (20/50/80%) benchmark that
logs median + vertical error (px/cm) under tag `GazeStage3`. Launch via the new
dot button next to the calibrate button in the article toolbar (needs calibration
first). Diagnostic raw-gaze logging added to `MediaPipeRawGazeSource` (tag
`MediaPipeGaze`, every 15 frames).

Measured accuracy (A56, MediaPipe on-device path, 3 runs): vertical median
0.67-1.01 cm, overall median 1.68-2.42 cm; horizontal poor/right-shifted. James
reports the prototype gets ~0.7 cm CONSISTENTLY with this same adopted logic, so
this is NOT a ceiling - newsmead was still diverging. Gaze-test UX: per-point
error now shown on-screen (numeric + yellow estimate dot + miss line); fixed the
article toolbar so Read Aloud no longer overlaps the new test button.


Follow-up (same day) - blink threshold configurability + accuracy diagnostics: moved the MediaPipe blink hysteresis thresholds out of hardcoded source constants into `StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD` / `GAZE_BLINK_OPEN_THRESHOLD` (defaults remain 0.04 / 0.055). Added `LocalRawGazeSource.BlinkStats` so the raw source reports emitted samples, blink-dropped frames, no-face frames, last openness, blink state, and active thresholds. `GazeTestActivity` now logs these as per-point and whole-run deltas in `GazeStage3`, alongside signed `dx/dy`, so accuracy runs can show whether poor results came from bias, false blink filtering, missing landmarks, or low usable sample count.
Found + fixed the last calibration divergence: `GazeCalibrationActivity` was
collecting differently from the prototype - COLLECT_MS 2500 (proto 1000),
MIN_SAMPLES 5 (proto 10), and MAD outlier rejection (`robustCenter`) instead of a
plain component-wise median. Since the mapper is only as good as its calibration
points, aligned all three to the prototype (1000 ms, 10 samples, plain median;
removed robustCenter/OUTLIER_K). MUST RECALIBRATE for this to take effect, then
re-run the accuracy test. If still short, next suspect is the live provider blink
handling: prototype `MediaPipeGazeProvider` resets the median filter on
blink-reopen; newsmead drops blinks at the source and doesn't reset - not yet
ported.

## 2026-07-01 — Bypass login gate for thesis research-instrument use

**Context:** NewsMead is being repurposed as a research instrument for a thesis
study. No real users, no personalization, no account system. The Firebase login
screen was blocking access to the home/article screens, and we don't want to
stand up the backend auth flow just to get past it.

### What the login gate was
- Enforced in `app/src/main/java/com/newsmead/activities/SplashActivity.kt`
  (the `LAUNCHER` activity, see `AndroidManifest.xml`).
- Check: `if (auth.currentUser != null)` — a **Firebase Authentication** check
  (`FirebaseAuth.getInstance().currentUser`). Not a local session token, not a
  `newsmead-api` call. If a Firebase user existed → `MainActivity` (home);
  otherwise → `AccountActivity` sign-up/login.
- `MainActivity` hosts `nav_graph.xml`, whose `startDestination` is already
  `homeFragment`. So the only thing gating the home screen was that one `if`.

### The change (smallest, reversible, non-destructive)
Added a feature flag in `SplashActivity` and short-circuited the auth check:

```kotlin
companion object {
    const val BYPASS_LOGIN = true   // false = restore normal login/sign-up flow
}
...
if (BYPASS_LOGIN || auth.currentUser != null) {
    navigateToMainActivity()
}
```

- All login code (LogInFragment, SignUpFragment, AccountActivity, Firebase) is
  left fully intact for later reference. Flip `BYPASS_LOGIN` to `false` to revert.
- Launch now goes straight to `MainActivity` → `homeFragment`.

### Known secondary consideration (feed content)
- Article/home content comes from a **public REST API** (Volley GETs to
  `newsmead.southeastasia.cloudapp.azure.com`) that does **not** require a
  Firebase token — see `DataHelper.loadArticleData()`. Browsing by
  category/source/search uses the `/articles/?…` endpoint and works with no login.
- BUT the **default home feed** hits `…/recommendations/{uid}`, where
  `uid = FirebaseHelper.getUid()`. With no Firebase user, `getUid()` returns the
  literal string `"null"`, so the request becomes `…/recommendations/null`.
  If the recommender rejects an unknown uid, the **default home feed may be
  empty** (screen still renders; list just comes back empty).
  - Server behavior for `recommendations/null` was not verifiable at edit time
    (the Azure host was unreachable from the dev sandbox).
  - If the feed comes up empty on-device, the smallest fix is to fall back to the
    generic `/articles/` endpoint when `uid == "null"` inside
    `DataHelper.loadArticleData()` (the code already knows how to parse that
    response). Not applied yet — pending confirmation of actual server behavior.
- Firebase-backed features (Saved lists, History, Profile) already guard on
  `uid == "null"` and degrade gracefully to a "Please login" toast — no crash.

---

## 2026-07-02 — Offline local article dataset (backend is gone)

**Context:** The content backend no longer exists — both API hosts
(`newsmead.southeastasia.cloudapp.azure.com` and `…-fil.…`) **no longer resolve
in DNS** (Azure VMs deprovisioned). So bypassing login alone leaves every feed
empty. Per the study plan (Option 1), article content is now served from a
bundled local JSON asset so the app runs **fully offline**.

### Switches
All study-mode flags now live in one place:
`app/src/main/java/com/newsmead/data/StudyConfig.kt`
- `BYPASS_LOGIN = true` — used by `SplashActivity` (replaces the local const
  from the previous entry).
- `OFFLINE_MODE = true` — serve articles from the local asset.
- `ARTICLES_ASSET = "study_articles.json"`.
Flip both to `false` to restore full production behaviour.

### What changed
1. **`DataHelper.loadArticleData()`** — when `OFFLINE_MODE`, delegates to a new
   private `loadLocalArticleData()` that reads `assets/study_articles.json`,
   parses the **same JSON schema the old API returned**, and applies the same
   filters server-side used to (source / category / language / search / pageSize).
   So home feed, search, by-source, by-category, and the recommended list all
   work unchanged against local data.
2. **`FirebaseHelper.isNetworkAvailable()`** — returns `true` immediately when
   `OFFLINE_MODE`. The feed screens only render inside
   `if (isNetworkAvailable(...))` (8 sites), so without this the shimmer would
   spin forever on a device with no network.
3. **`app/src/main/assets/study_articles.json`** — the dataset. Now holds **2
   original dummy study articles** (`article_id` 1 = health/lifestyle,
   2 = drainage/news), both English, `source: "study"`. Source anonymized:
   `"study"` maps to the neutral in-app label **"NewsMead"** (via `sourceNameMap`
   / `reverseSourceNameMap` in `DataHelper`) so nothing looks like a real outlet.
   Add more articles (up to the 4 A–D conditions) by appending to the array.

### JSON schema (dictated by the parser in `DataHelper`)
Top-level object with an `articles` array. Each article:

| field        | type   | notes |
|--------------|--------|-------|
| `source`     | string | raw key: `gmanews`, `inquirer`, `philstar`, `manilabulletin`, `news5`, `abantenews`, or `study`. Maps to the bundled logo + display name; any other value → generic logo + blank name. `study` → display name **"NewsMead"** + generic logo (added for the anonymous thesis articles). |
| `title`      | string | headline |
| `image_url`  | string | hero image. `""` = none. For an offline image, put the file in `assets/study_images/` and use `file:///android_asset/study_images/<file>` (Glide loads it). A normal `http(s)` URL only shows if the device happens to be online. |
| `date`       | string | **must** be `yyyy-MM-dd HH:mm:ss` (falls back to the raw string if unparseable, logged as a warning) |
| `body`       | string | full article text, no length limit |
| `category`   | string | e.g. `news`, `opinion`, `sports`, `technology`, `lifestyle`, `business`, `entertainment` |
| `language`   | string | `English` or `Filipino` (non-English hides the Translate button, which needs the dead API) |
| `read_time`  | string | free text, e.g. `"5 min read"` |
| `url`        | string | original article URL (used by the Share button) |
| `article_id` | int    | unique integer; identity used for de-dup and Latin-square condition assignment |

Latin-square note: the 4 articles map cleanly to conditions A–D via
`article_id`. The feed shows them in array order; per-participant ordering is a
study-runtime concern, not yet wired into the app.

---

## 2026-07-02 — Stage 4 prep: article view confirmed native + font size locked

- **WebView vs native (Stage 4 open question):** RESOLVED — the article body is a
  native `TextView` (`tvArticleText`, set via `.text = article.body`). No WebView
  anywhere (only a boilerplate mention in `proguard-rules.pro`). So line boxes for
  AOI mapping come from the `TextView` `Layout` API, not injected JS.
- **Font size (Stage 4 blocker):** RESOLVED and locked.
  - The article view had **no "large" preset** — just a ± stepper (min 14 / max 60,
    `ArticleFragment.addBottomAppBarListeners`). Default body = **20sp**
    (`ts_body_large`). The stepper is buggy (mixes px/sp: "larger" +~0.7sp/tap,
    "smaller" −2sp/tap) and density-dependent — unusable for fixed AOIs.
  - **Decision: 22sp**, locked. Implemented in `StudyConfig`
    (`LOCK_ARTICLE_FONT_SIZE`, `ARTICLE_FONT_SIZE_DP = 22f`) and applied in
    `ArticleFragment` via `setTextSize(COMPLEX_UNIT_DIP, 22f)` — **dp, not sp**, so
    the OS accessibility font-scale can't change the pixel size. The ± controls
    (`llArticleBottomButtons`) are hidden in study mode → no runtime adjustment.
  - NOTE: body line spacing is `lineSpacingMultiplier 1.6` + `lineSpacingExtra 1sp`
    (from `ts_body_large`) — relevant when computing per-line vertical bands.
  - Color-mode buttons (light/dark/sepia) are left active; they don't change line
    geometry. Flag if you want them locked too for visual consistency.

---

## 2026-07-02 — Stage 4 slice 1: WiFi gaze stack ported into NewsMead

Ported the self-contained GazeFollower-over-WiFi tracker from
gaze-thesis-prototype into **`app/src/main/java/com/newsmead/gaze/`** (verbatim
except `package`): `GazeProvider` (the swappable boundary), `WiFiGazeProvider`,
`GazeStream` (UDP listener, port 5005), `GazeMapper` (affine laptop-px→phone-px),
`OneEuroFilter`, `MedianFilter`, `CalibrationStore` (+`CalibrationSample`).
- Added dep `org.apache.commons:commons-math3:3.6.1` (GazeMapper least-squares).
- Added `<uses-permission android:name="android.permission.INTERNET"/>` for the
  UDP socket (was only present transitively via Firebase/Volley).
- `:app:compileDebugKotlin` → BUILD SUCCESSFUL. Not yet wired to any screen.
- NOT ported (prototype-only, not needed here): the Stage-1/2/3 camera/menu
  activities, `WiFiListenerActivity`, `CalibrationActivity`/`CalibrationView`,
  `GazeDotView`. The calibration *capture* UI is still needed to produce
  `calibration_wifi.csv` before `WiFiGazeProvider` can map real gaze — see slice
  decision below.

## 2026-07-02 — Stage 4 slice 2: article-screen wiring (touch-validation)

Wired the gaze AOI layer into the article reading screen, driven by touch for
validation (no tracker/laptop needed yet). New files in `com.newsmead.gaze`:
- **`LineAoiMapper`** — `lineAt(screenY)` → body line index. Scroll-aware for free:
  uses `tvArticleText.getLocationOnScreen()` (which already moves with scroll), so
  `screenY − textViewTop − totalPaddingTop` indexes the `Layout` directly; returns
  −1 when the point is off the text. Stable because font size is locked (22dp).
- **`GazeOverlayView`** — translucent dot drawn from full-screen px; non-interactive
  (passes touch through), sits on top of the article.
- **`ArticleFragment.setupGazeDebug()`** — gated by `StudyConfig.GAZE_ENABLED`. Adds
  the overlay over `binding.root`, and routes all gaze through one
  `GazeProvider.OnGaze` entry point (so live `WiFiGazeProvider.setOnGaze` drops in
  later with no fragment changes). With `GAZE_TOUCH_VALIDATION`, an
  `nsvArticleText` touch listener feeds `event.rawX/rawY` and returns false so
  scrolling still works.
- Logs `GazeAOI: gaze=(x,y) line=N/total` per event.

**On-device test (James, A56 — no laptop needed):** open a study article, drag a
finger down the text, `adb logcat -s GazeAOI`. The logged `line=N` should match the
line under your finger, and stay correct after scrolling. This is the build-spec
touch-validation gate; passing it isolates any later error to the gaze tracker,
not the integration.

`:app:assembleDebug` → BUILD SUCCESSFUL (with JDK 17).

**On-device (A56):** VALIDATED — `GazeAOI` logs stream and `line=N` is responsive
to touch as the finger moves down the passage (touch-validation gate). Scroll-
awareness confirmation recommended as a final check.

## 2026-07-02 — Stage 4 slice 3: live WiFi gaze wired (needs laptop rig to test)

Ported the calibration *capture* screen and wired the live tracker into the
article screen. Builds (`:app:assembleDebug` OK, JDK 17). **VALIDATED live on the
A56 (2026-07-02):** full rig run passed — laptop stream → 16-dot phone
calibration → live gaze on the article view, dot follows gaze and `line=N` tracks
the read line, scroll-correct. Stage 4 acceptance met.
- **`GazeCalibrationActivity`** (`com.newsmead.activities`) + `CalibrationView`
  (`com.newsmead.gaze`) + `activity_gaze_calibration.xml` + `calib_*` strings —
  full-screen 16-dot calibration, reads UDP gaze via `GazeStream`, robust
  outlier-rejected median per dot, writes `calibration_wifi.csv` via
  `CalibrationStore`. Registered in manifest (`exported=true`, see below). Logs
  the phone IP (`GazeCalib` tag) for the laptop's `--phone-ip`. Has a **"Start"
  gate** — opens and waits for a tap so the researcher can position the phone
  before the dots begin (doesn't auto-run).
- **`ArticleFragment`**: when `GAZE_TOUCH_VALIDATION = false`, builds
  `WiFiGazeProvider(GazeMapper(CalibrationStore.load()))`, streams gaze through
  the same `GazeProvider.OnGaze` entry point (main-thread hop), and stops it in
  `onDestroy` (releases the UDP socket). Falls back to touch validation if no
  calibration exists or the fit fails.
- Flags unchanged (`GAZE_TOUCH_VALIDATION = true`), so current behaviour is still
  touch validation until the flag is flipped for a live session.

**Launch trigger (decided):** adb, no in-app UI (laptop is always present at a
session). Launched with
`adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity`.
NOTE: `GazeCalibrationActivity` must be `android:exported="true"` — Android 16 /
One UI on the A56 blocks adb from starting non-exported activities (confirmed:
`SecurityException: not exported`). Verified launching on-device. Full runbook:
**SESSION_SETUP.md**.

**End-to-end live test — PASSED (A56, 2026-07-02):** per SESSION_SETUP.md, all
steps 1–6 confirmed by James (laptop stream → phone calibration → live
gaze-to-line on the article, scroll-aware, no socket leak on re-entry).
Manifest fix required along the way: `GazeCalibrationActivity` set
`exported="true"` (Android 16 blocks adb launching non-exported activities).

### Build note
- The project's kapt is **incompatible with JDK 21** (fails with
  `module jdk.compiler does not export com.sun.tools.javac.main`). This is a
  pre-existing environment issue, unrelated to this change.
- Build with **JDK 17** instead, e.g. `C:\Program Files\Java\jdk-17`:
  `JAVA_HOME="C:\Program Files\Java\jdk-17"; ./gradlew :app:compileDebugKotlin`
- Verified: `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** with all changes
  above (login bypass + offline dataset). `study_articles.json` validated as
  well-formed. Full on-device confirmation (home feed renders, article opens)
  still requires an emulator/device.

## 2026-07-02 - Stage 5 slice 1: RSI feed from line AOI stream

Added `ReadingStateInferencer` in `com.newsmead.gaze` and wired it at the existing `GazeProvider.OnGaze` entry point in `ArticleFragment`. It emits/logs `GazeRSI` line samples, fixation, dwell, and regression events only; no saccade or microsaccade features. Targeted JVM tests for the RSI inferencer pass; full `testDebugUnitTest` still has the pre-existing `FirebaseTest` JVM Android API failure.
## 2026-07-05 - Stage 5 slice 2: RSI scoring, regression debounce, and interpretation docs

Extended `ReadingStateInferencer` with a composite RSI score logged through `GazeRSI`: `score`, normalized `reg`, `dwell`, `fix`, and raw `events=fN/dNms/rN`. The score is a reading-effort signal, not a comprehension grade.

Adjusted regression detection to reduce false positives from gaze jitter: a backward move counts as a regression only after gaze remains on the earlier line for 300ms. Brief upward bounces are ignored. Later follow-up changed the anchor from the immediately previous line to the highest line reached, so confirmed one-line rereads are caught while duplicate counts during the same backward episode are suppressed.

Added targeted JVM coverage for sustained regressions, ignored upward bounces, and the composite score. Verified:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Result: BUILD SUCCESSFUL.

Added `RSI_README.md` with score ranges, raw metric meanings, interpretation examples, and calibration/noise caveats. `README.md` now links to the RSI guide from the Stage 5 section.

---

## 2026-07-06 - Local 16-point MediaPipe gaze integration, no backend/Wi-Fi

**Context:** `specs.md` was updated to remove the backend/Wi-Fi gaze path and require Stage 4/5 to depend only on the `GazeProvider` boundary. Stage 3 remains accuracy-limited, but the app now runs the gaze stack locally on the phone.

### What changed
- Removed the live UDP/Wi-Fi gaze path from source:
  - deleted `app/src/main/java/com/newsmead/gaze/GazeStream.kt`
  - deleted `app/src/main/java/com/newsmead/gaze/WiFiGazeProvider.kt`
  - removed the gaze-related `INTERNET` permission from `AndroidManifest.xml`
- Added local raw gaze pipeline:
  - `MediaPipeRawGazeSource` uses CameraX front camera + MediaPipe FaceLandmarker.
  - `LocalRawGazeSource` is the raw feature boundary: emits `gaze_x,gaze_y,timestamp`.
  - `LocalCalibratedGazeProvider` smooths raw features, applies `GazeMapper`, and emits screen px through `GazeProvider`.
  - `LocalGazeSources.create(context)` now returns `MediaPipeRawGazeSource(context)`.
- Added required runtime pieces:
  - `android.permission.CAMERA`
  - CameraX dependencies
  - `com.google.mediapipe:tasks-vision:0.10.14`
  - `app/src/main/assets/face_landmarker.task`
- Updated calibration storage:
  - new file name: `calibration_16point.csv`
  - legacy `calibration_wifi.csv` can still be read with a warning, but recalibration should write the new file.
- Updated `GazeMapper` from the old affine/Wi-Fi mapper to the Stage 3 local mapper:
  - second-degree polynomial features
  - standardized gaze inputs
  - clamping to calibration feature range
  - ridge regularization (`lambda = 1.0`)
- Updated `GazeCalibrationActivity`:
  - collects samples from `LocalRawGazeSource`, not UDP.
  - still uses the 4x4 / 16-dot sequence.
  - restarts the local raw gaze source after camera permission is granted.
- Updated `ArticleFragment`:
  - live mode now builds `LocalCalibratedGazeProvider(GazeMapper(samples), LocalGazeSources.create(...))`.
  - Stage 4 line mapping and Stage 5 RSI still consume only the `GazeProvider.OnGaze` stream.
- Set `StudyConfig.GAZE_TOUCH_VALIDATION = false`, so article reading now uses live local MediaPipe gaze by default.

### Current pipeline

```text
CameraX front camera
-> MediaPipe FaceLandmarker
-> raw iris/eye ratio feature
-> 16-point calibration CSV
-> GazeMapper
-> GazeProvider
-> LineAoiMapper
-> ReadingStateInferencer / RSI logs
```

### Test plan
1. Run app on a real phone.
2. Grant camera permission.
3. Launch calibration:

```powershell
adb shell am start -n com.newsmead/.activities.GazeCalibrationActivity
```

4. Complete all 16 dots.
5. Open an article normally.
6. Watch Logcat tags:

```text
MediaPipeGaze
GazeCalib
GazeAOI
GazeRSI
```

Expected logs:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: Wrote 16 calibration pairs
GazeAOI: gaze=(x,y) line=N/total
GazeRSI: score=...
```

### Verification
Verified locally:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
```

Both commands returned `BUILD SUCCESSFUL`.

### Remaining validation
- Needs on-device calibration quality check with the new phone-local MediaPipe source.
- If calibration dots keep retrying, likely causes are camera permission, missing face visibility, poor lighting, unstable phone/head position, or FaceLandmarker not producing iris landmarks consistently.
- Stage 4/5 integration is still isolated behind `GazeProvider`; if the raw feature needs tuning, downstream line mapping and RSI should not need rewrites.

### 2026-07-06 follow-up: MediaPipe calibration read fixed on device

Initial on-device logs showed two issues:

```text
MediaPipeGaze: No face landmarks
GazeCalib: Dot 1: only 0-4 samples, re-collecting
GazeCalib: Loading legacy calibration_wifi.csv
```

Fixes applied:
- `MediaPipeRawGazeSource` now converts CameraX `RGBA_8888` frames with row-stride handling before passing them to FaceLandmarker. The previous direct buffer copy could corrupt frames on devices where `rowStride != width * pixelStride`, causing repeated `No face landmarks`.
- FaceLandmarker confidence thresholds lowered from `0.5` to `0.35` for the phone-local source.
- Live-stream timestamps are forced monotonic before `detectAsync()`.
- Calibration no longer loads legacy `calibration_wifi.csv`; local MediaPipe gaze requires `calibration_16point.csv`.
- Calibration collection window changed to `2500ms`, with `MIN_SAMPLES = 5`, because phone-local FaceLandmarker throughput is lower than the old UDP stream.
- Added periodic `MediaPipeGaze raw=(x,y)` diagnostic logging.

Confirmed on connected phone after installing the fixed debug build:

```text
MediaPipeGaze: Local MediaPipe raw gaze source started
GazeCalib: pair 1/16 ... n=10 used=10
...
GazeCalib: pair 16/16 ...
GazeCalib: Wrote 16 calibration pairs to /data/user/0/com.newsmead/files/calibration_16point.csv
```

Verified again:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All returned `BUILD SUCCESSFUL`; the fixed APK was installed on the connected phone.

### 2026-07-06 follow-up: in-article recalibration button

Added an in-app control to rerun calibration without using adb:
- `fragment_article.xml` now has `btnRunGazeCalibration` in the article top bar beside the back button.
- The button launches `GazeCalibrationActivity` directly.
- `ArticleFragment` stops the current live gaze provider before launching calibration.
- When the user returns from calibration, `ArticleFragment` reloads `calibration_16point.csv` and restarts `LocalCalibratedGazeProvider` so the new calibration is used immediately.
- Added string `article_calibrate_gaze` for the button content description.

Verified:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.newsmead.gaze.ReadingStateInferencerTest
.\gradlew.bat :app:installDebug
```

All completed successfully.

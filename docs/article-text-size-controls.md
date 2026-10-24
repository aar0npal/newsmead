# Article text size

## Current behavior — 2026-10-15

- Ordinary articles expose **A− / A+** beside the theme buttons and Aids entry.
- Default **22 sp**, range **14–60 sp**, steps **2 sp** in either direction.
  The selected size is remembered across articles and app restarts. Android's
  system font scaling applies.
- Line spacing uses **1.6 ×** font metrics plus extra physical pixels equal to
  `rendered text size in px / 22`. Both font and extra spacing scale together.
- Resizing retains the first visible source-text line where possible. Near the
  document bottom, normal scroll limits still apply.
- A resize clears old aid/lookup state, ends any article demo, gates observations
  during reflow, rebuilds actual TextView geometry and waits for the existing
  **150 ms** geometry settling guard. Late observations captured before the
  control boundary remain excluded. Footer controls are excluded from reading.
- Size controls are unavailable while an article diagnostic recording is active.
  Dedicated Validation, Pilot and Replay views keep their controlled **22 dp**
  settings. Article diagnostic recordings use the selected article size.
- Aid traces record rendered text size, line height and extra spacing in pixels,
  the spacing multiplier, and system font scale. Geometry records repeat current
  physical typography so a trace can identify a resize.

These sizes and spacing are interface choices, not validated reading benefits.
The **1,500 ms** word-residence trigger remains unchanged and is not normalized
or validated across font sizes. Focus now has stricter small-font line-cycle
counts/visit guards; [current focus rules](repeated-interruption-focus-window.md)
give the values and validation limits. Look-away remains 3 episodes/30 seconds.
Actual line
and word bounds are recalculated; natural gaze accuracy at each size still needs
human evaluation.

## Verification

### Footer appearance revision

The owner requests tighter left-grouped theme swatches and right-grouped font
and aid controls. The shared `ArticleFooterControl` style uses transparent
backgrounds, 28 dp circular symbols, Arial captions and 48 dp touch widths.
A−/A+ use outlined circular glyphs; Aids uses an outlined circle with a gear.
The left and right groups each occupy 144 dp with 8 dp outer padding and a
flexible center gap (16 dp at the 320 dp minimum checked width). Font/gear
foregrounds follow the article's Light/Dark/Sepia text color. Previous filled
yellow font/Aids backgrounds are removed.

The first build found plain-Button bindings incompatible with MaterialButton
icon tinting; all six controls now explicitly declare MaterialButton. The
owner-approved retry passes both APK builds and data-preserving A56 installs.
The existing native footer check passes at 320/360/400 dp: all controls visible,
contained, nonoverlapping and at least 48 dp wide/high. Synthetic widget previews
are visually inspected at 320 and 400 dp; circles, captions and grouping fit.
Detached TextView pre-draw was added to the preview setup so labels render;
the app stayed unchanged for that test-APK refinement. Final layout check passes
in 0.513 s. No camera or live article capture. Evidence:
`output/article-footer-style-check-02/summary.json`, build/install/native logs
and `article-footer-preview-*.png`. First failed build retained in 01.

### Font resize verification (before the footer appearance revision)

Independent review caught loss of the pending reading-position restore on pause;
the callback and capture gate now remain pending for the same view's next draw.
View destruction still cancels it. Follow-up review has no actionable findings.
The first build assembled the app, then found a parent-property ambiguity in the
new Android test; its explicit-View correction is saved and reviewed.
The approved retry passes both APK builds and **10/10** focused JVM checks
(control capture gate and focus integration). Data-preserving app/test updates on
the A56 (`SM_A566B`) succeed. All **7/7** camera-free native checks pass in
**2.002 s**, including typography, real footer layout, geometry mapping and stale
lookup invalidation. Evidence: `output/article-text-size-check-02/summary.json`,
build/native/install logs and copied JVM XML results. The first failed build is
retained in `output/article-text-size-check-01/`.

The Android fixtures cover bounds/steps, Arial reflow
and UTF-16 word anchors at 14/22/60 sp, observation boundaries after resizing, and
the real footer at 320/360/400 dp widths. No gaze provider or camera is started.
The fragment's pause/resume callback guards were reviewed in source; the native
fixtures do not launch the live article or validate that full lifecycle path.

Implementation: `ArticleTypography.kt`, `ArticleFragment.kt`,
`fragment_article.xml`, `StudyConfig.kt`; trace metadata in
`ArticleReadingAidRuntime.kt`. Test: `ArticleTypographyTest.kt`.

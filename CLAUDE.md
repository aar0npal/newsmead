# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

# Project Context — NewsMead (Research Instrument)

## What this is
NewsMead, a prior DLSU thesis Android app (Kotlin), repurposed as
a research instrument for a separate gaze-driven adaptive reading
thesis. This is someone else's existing codebase we're extending,
not rewriting — surgical changes only.

## Gaze tracker — on-device MediaPipe, see docs/build-context.md
Current tracker: **MediaPipe Face Landmarker running on-device** (CameraX
front camera) with 16-point polynomial calibration. No laptop, no
backend, no network.

History matters here: the team tried MediaPipe on-device (failed
accuracy validation), MobileGaze/TFLite (failed), then GazeFollower
over WiFi (passed, adopted 2026-07-02), then reverted to on-device
MediaPipe (2026-07-06) because a co-located laptop per session is
impractical for a field study with older adults. That trade chose
deployability over the better raw accuracy number — accuracy is the
known, documented limitation, not a solved problem.

Do NOT re-add the WiFi/GazeFollower path; `WiFiGazeProvider.kt` and
`GazeStream.kt` were deliberately deleted. Full decision table and
rationale: the STATUS UPDATE at the top of docs/build-context.md.

## Study modifications made so far
- Login bypassed for research use (StudyConfig.kt — BYPASS_LOGIN flag)
- Original newsmead-api backend is dead (Azure VMs deprovisioned,
  DNS doesn't resolve). Articles load from local bundled JSON
  (study_articles.json in assets/) instead. OFFLINE_MODE flag in
  StudyConfig.kt.
- Study articles are original content (not real outlet articles,
  to avoid copyright issues). Source displays as "NewsMead" rather
  than a real outlet name.
- See StudyConfig.kt for all study-specific flags/switches

## Current state — Stages 4 and 5 complete
Gaze-to-line AOI mapping, the RSI pipeline, and all four adaptive
scaffold renderers are implemented. The two former Stage 4 blockers
are resolved: the article body is a native `TextView` (line boxes come
from the `Layout` API, not injected JS), and the study font size is
locked at 22dp (`StudyConfig.LOCK_ARTICLE_FONT_SIZE` /
`ARTICLE_FONT_SIZE_DP`).

Active work is now calibration quality and validation:
- Calibration/re-calibration subsystem was rebuilt 2026-07-24 —
  canonical guide is docs/calibration-design.md.
- **Outstanding:** re-measure device accuracy with the rebuilt
  calibration (prior figures in progress-notes.md predate it), and
  validate on a target-age (45-65) face.
- Thresholds across calibration and scaffolding are provisional
  engineering values pending pilot tuning.

## Read before starting any work
- docs/progress-notes.md — what's been done in THIS repo specifically.
  A teammate using Codex may have worked here since your last
  session. Read this first every session.
- docs/build-context.md — the detailed build spec; its STATUS UPDATE holds the
  tracker decision history (read before touching anything gaze-related)
- docs/calibration-design.md — calibration/re-calibration architecture
- StudyConfig.kt — all study-specific flags in one place

## Non-negotiable architecture rule
All gaze output flows through GazeProvider.onGaze(x, y). The RSI
uses only fixation, dwell, and regression signals — never saccade
velocity or microsaccades (not measurable at consumer camera
frame rates).

## Target devices
- James: Samsung Galaxy A56 5G, One UI 8.5 (Android 16)
- Groupmate: TBD

## After finishing any work session
Update docs/progress-notes.md with what was completed, decided, and
what the next session needs to know. Keep it to 1-3 lines.

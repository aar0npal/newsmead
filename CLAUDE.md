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

## Gaze tracker — resolved, see context.md
Tracker selection is DONE (GazeFollower, WiFi bridge). See the
STATUS UPDATE at the top of context.md for the full investigation
and what's still active (Stage 4-5) vs. historical (Stage 1-3).

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

## Current task — Stage 4: AOI mapping
Porting the GazeProvider (GazeFollower WiFi bridge implementation)
into this codebase and wiring it to the article reading screen.

Open questions before this can be completed:
- Does the article view render text via WebView or native
  TextViews? (Determines how line bounding boxes are obtained)
- Study font size not yet finalized — check article view's
  large-text control actual point size, then lock it (no runtime
  adjustment during study sessions — required for AOI mapping to
  be stable, see StudyConfig.kt)

## Read before starting any work
- PROGRESS_NOTES.md — what's been done in THIS repo specifically.
  A teammate using Codex may have worked here since your last
  session. Read this first every session.
- context.md — the detailed build spec (Stage 4-5 are the active
  work here; Stages 1-3 are historical, see its STATUS UPDATE)
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
Update PROGRESS_NOTES.md with what was completed, decided, and
what the next session needs to know. Keep it to 1-3 lines.

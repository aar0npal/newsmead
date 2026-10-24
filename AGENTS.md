# NewsMead agent working agreement

## Responses

- Keep answers concise and report changed files, checks, results, risks, and blockers.
- Start with the current question, source, and `git status`; historical handoffs may be obsolete.

## Planning and delegation

- Clarify meaningful unknowns that could change scope, interpretation, or validation; skip routine questions.
- Match model and reasoning effort to complexity using `.codex/agents/` and `docs/codex-agent-workflow.md`.
- The lead owns scope, research judgment, architecture, integration, review, and the final answer.
- Delegate bounded, useful research, review, or feature implementation when it saves meaningful lead work;
  keep trivial documentation and dependent steps local.
- Use at most two independent children, in fresh contexts, with no nested delegation; parallelize only useful work.
- Each brief names the question, relevant paths, file owner, acceptance checks, exclusions, and stopping condition.
- Give each edited file one owner, preserve other agents' edits, and avoid duplicate investigations or device work.

## Changes and checks

- Follow the existing Android UI design and keep framework changes separate from app behavior.
- Do not redesign architecture or change frozen protocols without an explicit task.
- Reuse existing deterministic analysis and replay tools; run focused checks for changed behavior.
- Report what checks establish and their limits; software tests or synthetic data do not prove human validity.
- Use focused review for substantive metric, timestamp, coordinate, calibration, concurrency, or controller changes;
  skip ceremonial review for trivial edits.

## Research and measurement

- For gaze, measurement, or research work, read `docs/agent-context.md` first, then only relevant installed skills and evidence.
- Treat that context file as the current research checkpoint and retain evidence paths after substantive decisions.
- Keep implemented behavior, measured evidence, user observations, and proposed methods distinct.
- Use primary sources for research claims and state population, hardware, assumptions, and validation limits.
- Preserve units, denominators, time bases, missingness, coordinate systems, and detector assumptions.
- Tracking eligibility is not accuracy; heuristic thresholds are not validated outcomes.
- Do not assume rereading is harmful or that four scaffold levels are required; establish defensible measurements first.
- Keep tuning and independent validation data separate, and label exploratory work.

## Boundaries

- Respect existing edits, private diagnostics, and unrelated tasks. Do not stage or commit unless explicitly requested.
- Do not upload private recordings, retain camera images, or start device capture incidentally.
- Device installs, calibration, and collection require task-specific authorization.
- Read only task-relevant files and logs; stop after one bounded failed attempt and return the evidence or blocker.

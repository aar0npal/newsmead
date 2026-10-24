# Using NewsMead's Codex agents

This setup is local to NewsMead. Ask normally for the outcome you want. The main
agent plans, chooses whether a helper is worthwhile, checks the result, and answers
you. You do not need to assign every role or run a separate task for each helper.

## What is configured

| Role | Model | Why |
| --- | --- | --- |
| Main agent / lead | Your selected model and reasoning level | Research decisions, architecture, integration, final judgment |
| Scout | GPT-6 Luna, low | Fast, targeted code and context lookup |
| Researcher | GPT-6 Luna, medium | Primary-source retrieval and evidence extraction |
| Worker | GPT-6 Luna, medium | Routine changes with clear acceptance criteria |
| Engineer | GPT-6.1 Sol, medium | More involved code and reproducible analysis |
| Reviewer | GPT-6.1 Sol, high | Independent scrutiny at substantive checkpoints |

The lead is the main conversation, not an extra paid coordinator spawned beside
it. Definitions live in `.codex/agents/`; defaults are in `.codex/config.toml`.
`AGENTS.md` explains selective delegation and `docs/agent-context.md` supplies a
short project checkpoint. No personal/global config, provider, billing, permission
policy, or runtime app code is changed by this setup.

## First use

Use a fresh task in this trusted NewsMead project to load the new configuration.
Existing tasks may retain earlier settings. Choose your preferred primary model
and reasoning level in the model picker. The project sets neither; your app or
personal defaults apply. If the picker still shows stale settings, reopen the app
and start a fresh task. This configuration does not switch an already-running
conversation's model. No new API key or third-party service is required.

Example requests:

- "Use the NewsMead workflow to assess whether our current recordings support
  reliable rereading measurements. Produce the smallest next experiment."
- "Implement this agreed measurement change. Use a cheap worker for the focused
  edit and an independent reviewer if it changes metric semantics."
- "Research this specific feature in primary papers, then assess whether it
  transfers to our sampling rate and older-adult reading task."
- "Handle this small documentation fix directly; no subagents."

Creating these files does not launch background work. Helpers only run during
requested tasks. They report to the lead; the user does not have to manage them.
If custom role selection is absent from a runtime's spawn tool, AGENTS.md tells
the lead to load the role instructions and pass the exact model/effort explicitly
with a fresh context. This fallback does not load the role's other TOML settings:
the parent runtime permissions still apply, while read-only behavior and no nested
delegation are instructions, not extra enforcement from the role file.

## How work is divided

1. The lead checks the current question and relevant checkpoint. Simple tasks stay
   local. For substantial work, select one bounded helper, or two independent ones.
2. The helper receives the question, a short context summary, relevant file paths,
   file ownership, acceptance criteria, and stopping condition. No full task-history
   copy and no nested delegation.
3. The lead performs complementary work while the helper runs. Numerical batches
   are handled by local scripts, with only summaries returned to the model.
4. Substantive algorithm/controller changes receive a focused independent review;
   simple edits do not. The lead resolves findings and reports evidence and limits.
5. Significant decisions update the relevant concise checkpoint for later tasks.

## Cost controls and practical limits

- Two concurrent children maximum in project configuration; normally zero or one.
- Each child disables its own delegation when its TOML role is loaded. Generic
  fallback children are instructed not to delegate. The lead decides escalation.
- Luna handles routine extraction and edits; GPT-6.1 Sol handles greater implementation
  complexity and review. Your selected lead decides research and architecture questions.
- Short briefs, targeted reads, compact outputs, no duplicate investigations, and
  stopping after a bounded unsuccessful attempt are workflow rules.
- Concurrency is not a token or dollar cap. These rules are not a guaranteed hard
  spending limit. All agent work consumes account allowance; parallel work can
  consume more than one agent. Compare completed-task quality and usage, not speed
  alone. Use the Codex usage dashboard to inspect actual allowance consumption.
- Do not repeatedly benchmark all models on the same work. If a cheap worker fails,
  route the concrete blocker to the engineer or lead instead of more cheap retries.
- A new model assignment cannot be guaranteed by writing a prompt alone. If the
  requested model is unavailable or the client does not load the role, report it.

## Editing and disabling

Change a role's model and effort in its TOML file. Choose the lead in the app;
keep primary `model` and `model_reasoning_effort` unset in the project config.
Change default helper settings and concurrency in `.codex/config.toml`.
Restart in a fresh task and verify the
model selection after changes. To disable delegation, set `[agents] enabled = false`
in the project config and use a fresh session that loads it. An already-running
generic runtime may retain its tools; say "no subagents" to stop delegation there.
Explicit user instructions take precedence over the workflow.

Setup uses the current standalone TOML agent format. Older examples that register
roles using `config_file` are not needed for this installed version. Settings are
defaults; live session overrides, trust, runtime tools, and permissions can change
effective behavior. See the verification note below for what was actually checked.

## Sources and verification

2026-10-01 model update: engineer and reviewer now use GPT-6.1 Sol at their
existing medium and high reasoning levels. Scout, researcher, worker, and the
default helper remain on GPT-6 Luna. The lead model remains the user's choice.
[OpenAI's subagent guide](https://learn.chatgpt.com/docs/agent-configuration/subagents)
recommends GPT-6.1 Sol for demanding agents and Luna for narrow work. The
installed CLI `0.159.2` includes both models and the assigned reasoning levels
in its bundled catalog. All six project TOML files parsed; the five role
model/effort pairs, disabled child delegation, and two-child limit passed focused
checks. Live named-role dispatch was not tested in this existing task; start a
fresh task to load and verify the new definitions.

2026-09-23 model update (historical): helper defaults and named roles used GPT-6 Luna and
GPT-6 Sol. Reasoning levels, role instructions, delegation limits, and the user's
primary-model choice are unchanged. [OpenAI's Codex release note](https://learn.chatgpt.com/docs/changelog)
announces Sol and Luna availability, subject to rollout and workspace settings;
the [official subagent guide](https://learn.chatgpt.com/docs/agent-configuration/subagents)
recommends Luna for focused work and Sol for demanding agents. This project keeps
its existing reasoning levels because GPT-6 supports them. Verify effective model
selection in a fresh task; the current task can retain its earlier role catalog.
The six project TOML files parsed on 2026-09-23; the five role model/effort pairs,
disabled child delegation, and two-child limit matched the then-current table. Live
named-role dispatch with the new models was not tested. The installed CLI was
`0.155.0-alpha.16`; its bundled catalog lacked Sol and Luna, and a live catalog
refresh could not run because that CLI could not find its home directory. OpenAI's
[2026-09-23 CLI release](https://learn.chatgpt.com/docs/changelog) adds the models
to the CLI picker. CLI users need that release or later for this configuration.

Primary-selection update (earlier): removed the project's fixed Astra/high settings
at the user's request. The initial verification records below describe the original
setup and do not represent the current primary-model policy.

- [Official subagent configuration](https://learn.chatgpt.com/docs/agent-configuration/subagents)
- [Configuration scope and precedence](https://learn.chatgpt.com/docs/config-file/config-basic)
- [Model roles and usage](https://learn.chatgpt.com/docs/pricing)

Prepared against the documentation on 2026-09-20 and installed Codex CLI
`0.155.0-alpha.9.2`.

Initial setup verification (historical; current-session checks follow):

- All six TOML files parsed successfully; checked required role fields, model and
  effort assignments, two-child limit, and disabled delegation in every role.
- The native Codex configuration API accepted strict configuration loading and
  returned the effective Astra/high lead, Luna/medium default helper, and limit of
  two. The existing personal configuration already marks NewsMead trusted.
- Native prompt preview confirmed the new AGENTS.md instructions are loaded.
- A live Luna/medium helper completed a bounded measurement-context extraction;
  a live Terra/high helper independently reviewed the framework. Two documentation
  consistency findings were corrected. Both used the current generic spawn tool.
- Named-role dispatch was not exercised because the initial task's spawn interface had
  no named-role selector. Role files are structurally checked; the explicit-model
  fallback is the path exercised here. Verify named-role loading in a fresh client
  task before assuming its sandbox or delegation settings apply.

No Android build/device test was run for this configuration-only change. Existing
app and analysis edits, personal configuration, and Git staging were left alone.

## Source-installed skills — 2026-09-20

The user requested existing community skills instead of custom project skills.
The two custom skills were retired from discovery. NewsMead-specific measurement
rules remain in `docs/agent-context.md` and the existing pilot/tool documentation.

| Installed skill | Use |
| --- | --- |
| `scientific-critical-thinking` | Evidence quality, methodology, confounding and claim assessment |
| `statistical-analysis` | Statistical design, assumptions, effect sizes and reporting |

Source: [K-Dense Scientific Agent Skills](https://github.com/K-Dense-AI/scientific-agent-skills),
revision `330c8e764435a731eff571e3efdda70b363d0792`, paths `skills/<name>`.
The [statistical-analysis listing](https://www.skills.sh/k-dense-ai/scientific-agent-skills/statistical-analysis)
was inspected on skills.sh. Critical-thinking was found in the directory via a
fork; its original K-Dense source was inspected and installed directly because
the K-Dense directory page could not be fetched. Directory popularity and security
checks do not establish scientific peer review; no such certification was verified.

Both complete skill folders are copied unchanged into `.agents/skills/` with
upstream references and the statistical helper. The MIT license is retained at
`.agents/skills/K-Dense-LICENSE.md`; file hashes are in
`docs/third-party-skills.json`. Use the upstream names explicitly or let the
client select them for matching requests. They should be available next turn.
Do not create project forks or custom replacement skills without a user request.

### Checks and practical limits

- Named-role worker (then GPT-5.6 Luna/medium) shortened AGENTS.md; named-role
  reviewer (then GPT-5.6 Terra/high) inspected the upstream skills. No model fallback
  was needed. All five named roles were exposed. This was before the primary-model
  choice and GPT-6 helper updates; no live lead picker selection was changed.
- YAML parsed for both skills; names, descriptions, supported metadata and all
  13 bundled reference/script paths passed checks. Installed files matched the
  inspected upstream downloads byte-for-byte; helper Python syntax parsed.
- Statistical-analysis passed bundled `quick_validate.py`. Critical-thinking has
  a `compatibility` field which that local validator rejects, but the current
  [Agent Skills specification](https://agentskills.io/specification) permits it.
  Used explicit specification checks rather than editing upstream metadata.
- Representative review retained the run-01 exclusion (four transitions do not
  validate an index) and rejected treating 100 windows from five participants
  as 100 independent units. These are bounded checks, not scientific validation.
- The statistical helper does not establish participant/passage independence;
  its generic normality/outlier recommendations are not an automatic analysis
  gate. Follow the current measurement protocol and declared handling rules.
- Helper execution was not tested: the existing project environment lacks pandas,
  scipy, seaborn and statsmodels. No packages were installed. Existing gaze
  analysis remains the primary tool; inspect dependencies only when a future
  task actually needs additional statistical computation.
- Optional scientific-schematics/OpenRouter integration and sibling modeling
  skills were not installed. Basic appraisal needs neither a service nor an API
  key. No private data may be sent externally as an incidental skill action.

App behavior, analyzer code, agent configuration and Git staging were unchanged.
Previous custom-skill validation is historical and does not validate these upstream
skills. Archived custom text is retained only in ignored `tmp/retired-project-skills/`.

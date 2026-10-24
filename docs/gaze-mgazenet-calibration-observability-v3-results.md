# MGazeNet calibration-observability v3 results — collection in progress

Updated 2026-09-09. Read after
`gaze-mgazenet-calibration-observability-v3-protocol.md`.

Status: all four frozen development attempts are complete, preserved and scored
only after collection ended. The full camera-free comparison and decision are in
`gaze-mgazenet-calibration-observability-v3-analysis.md`. The active tracker
remains unchanged.

## Frozen sequence status

| Sequence | Device | Run label | Status |
| ---: | --- | --- | --- |
| 1 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r1` | Complete and hash-verified |
| 2 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r1` | Complete and hash-verified |
| 3 | A56 / SM-A566B (`R5CY40Y2C5W`) | `a56_v3_r2` | Complete and hash-verified |
| 4 | SM-G991B (`R5CR60YSJ2X`) | `g991b_v3_r2` | Complete and hash-verified |

Do not reorder, replace or add a run after seeing evidence. All four attempts
must be preserved before scoring or comparison.

## G991B round 1

- Run label: `g991b_v3_r1`
- Session ID: `1788944307040_7525fa1f-cca6-4112-afcb-ebdae2788a34`
- Start timestamp encoded by the session ID: 2026-09-09 16:58:27 +08:00
- Outcome exposed by the collector: complete
- Raw report SHA-256:
  `fe2f2edc9750d4761b6027f570004ad17a8fbaaa72fcacffca55a0e9dbad0403`
- Preserved raw directory:
  `diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/sm-g991b/session-1/`

Before the run, the installed app SHA-256 exactly matched the frozen build:
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
CAMERA runtime grant was `false`, effective app-op was `ignore`, and the
benchmark process was absent. Opening the benchmark home screen did not change
that state. The participant explicitly started the named calibration audit and
granted CAMERA for the run.

The prospectively declared conditions were no glasses, approximately 30 cm
viewing distance with ordinary small variation, medium screen brightness, a
lighted room, daytime and mild fatigue. After completion, the participant
reported losing attention for approximately one second during one target. The
target is not identified. This note is retained without inspecting the results;
it is not an exclusion, correction or reason to rerun the session.

After completion, the camera app-op showed a run duration of approximately
3 minutes 1 second. CAMERA was explicitly revoked and returned to effective
app-op `ignore`; runtime grant `false` was confirmed. The app and instrumentation
packages were force-stopped and the benchmark process was confirmed absent.

`collect_calibration_audit.py` copied only the named numeric record through the
debug package boundary, verified its device-written SHA-256, schema, protocol,
session identity, privacy flags, correction flags and null decision fields, and
saved it without scoring. Its collector contract tests bring the MGazeNet Python
suite to 53 passing tests. The report hash above was independently recomputed on
the preserved bytes and matched. At this preservation checkpoint, no raw gaze
values were printed or opened; scoring occurred only after all four attempts.

## A56 round 1

- Run label: `a56_v3_r1`
- Session ID: `1788948001184_f08c9240-525c-44d7-b63a-b49d93785da1`
- Start timestamp encoded by the session ID: 2026-09-09 18:00:01 +08:00
- Outcome exposed by the collector: complete
- Raw report SHA-256:
  `4fdf42813ee85f7335dc4e47928c3809f49b2924ece860ad0d491b6ff131a680`
- Preserved raw directory:
  `diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/sm-a56/session-1/`

Before the run, the installed app SHA-256 exactly matched the frozen build:
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
CAMERA runtime grant was `false`, effective app-op was `ignore`, the benchmark
process was absent, and there was no pre-existing A56 calibration-audit record.
The benchmark home screen opened without changing CAMERA state. The participant
then started the named run and granted CAMERA.

The participant reported no change from the declared G991B round-1 conditions:
no glasses, approximately 30 cm viewing distance with ordinary small variation,
medium screen brightness, a lighted room, daytime and mild fatigue. No new
discomfort or other condition change was reported.

The camera app-op recorded approximately 2 minutes 56 seconds for the run.
After completion, CAMERA was explicitly revoked and returned to effective
app-op `ignore`; runtime grant `false` was confirmed. The app and instrumentation
packages were force-stopped and the benchmark process was confirmed absent.

The collector preserved only the named numeric record, verified the hash and
non-retention/no-decision contract, and printed no gaze values. The raw report's
SHA-256 was independently recomputed on the preserved bytes and matched the
device-written value above. The scorer was not run until all four attempts were
complete.

## A56 round 2

- Run label: `a56_v3_r2`
- Session ID: `1788959696940_91ff6a25-97dd-4dc4-8e93-0d163d9d00cf`
- Start timestamp encoded by the session ID: 2026-09-09 21:14:56 +08:00
- Outcome exposed by the collector: complete
- Raw report SHA-256:
  `6b24ebd023bd479a8dd35402ffe71e988506d7135e261c8fb12c446073ef2c7c`
- Preserved raw directory:
  `diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/sm-a56/session-2/`

More than three hours had elapsed since A56 round 1, exceeding the frozen
between-session and between-round intervals. Before round 2, the installed app
again matched frozen SHA-256
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
CAMERA runtime grant was `false`, effective app-op was `ignore`, the process was
absent, and the round-1 device record remained present. Opening the benchmark
home screen did not change CAMERA state.

The session occurred at night. No change to the previously declared no-glasses,
approximately 30 cm, medium-brightness or lighted-room conditions was reported.
After completion, the participant described fatigue as “a little more normal in
terms of fatigue, not as mild anymore.” Because that wording does not establish
a single better/worse category, it is retained verbatim without reinterpretation.
It is not an exclusion, correction or reason to rerun.

The camera app-op recorded approximately 2 minutes 53 seconds for the run.
After completion, CAMERA was explicitly revoked and returned to effective
app-op `ignore`; runtime grant `false` was confirmed. The app and instrumentation
packages were force-stopped and the benchmark process was confirmed absent.

The collector preserved only the named numeric record, verified its device-side
hash and non-retention/no-decision contract, and displayed no gaze values. The
raw SHA-256 was independently recomputed on the preserved bytes and matched.
The scorer was not run until all four attempts were complete.

## G991B round 2

- Run label: `g991b_v3_r2`
- Session ID: `1788960878269_a10b1e51-8902-4e2e-b3cc-640e7ffc5a11`
- Start timestamp encoded by the session ID: 2026-09-09 21:34:38 +08:00
- Outcome exposed by the collector: complete
- Raw report SHA-256:
  `c9426c6c8f4a2e808ade80c88a9f8fc0cd7df9ace7366c9a812fdd18fbf6df46`
- Preserved raw directory:
  `diagnostics-local/2026-09-09/mgazenet-calibration-observability-v3/sm-g991b/session-2/`

The final run began approximately twenty minutes after A56 round 2, exceeding
the frozen interval. Before it, the installed app again matched frozen SHA-256
`2fba004144d705102c76c76b25ab6c8426892797fb6a2f923bb45d8c4230ccab`.
CAMERA runtime grant was `false`, effective app-op was `ignore`, the process was
absent, and G991B round 1 remained present. Opening the home screen did not
change CAMERA state.

The participant declared the same conditions as A56 round 2: no glasses,
approximately 30 cm, medium screen brightness, a lighted room, nighttime, and
the same verbatim fatigue description retained for that run. No other change
was reported.

The camera app-op recorded approximately 3 minutes 2 seconds. CAMERA was then
explicitly revoked and returned to effective app-op `ignore`; runtime grant
`false` was confirmed. Both packages were force-stopped and the process was
absent. The collector verified and preserved the named raw report without
showing gaze values, and the local SHA-256 matched the device-written value.

## Current boundary

All four attempts are immutable and will not be repeated. Only after the fourth
raw hash was verified were the independent summaries generated and compared.
That analysis assigns no gate or promotion decision and is documented in
`gaze-mgazenet-calibration-observability-v3-analysis.md`. No further participant
run, CAMERA grant, threshold, correction, natural-reading trial or active-tracker
change is authorized by collection completion.

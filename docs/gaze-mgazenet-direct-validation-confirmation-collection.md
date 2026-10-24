# MGazeNet direct-validation confirmation collection — 2026-09-10

Status: all four frozen confirmation attempts are complete and hash-preserved.
Only after the fourth record was secured were all four independently scored.
The preregistered comparison is inconclusive; continue from
`gaze-mgazenet-direct-validation-confirmation-analysis.md`. The active NewsMead
tracker under `app/` remains unchanged.

Read after
`gaze-mgazenet-direct-validation-confirmation-device-check.md`. The software and
camera-free checks passed on both phones before participant collection began.
The frozen targets, timing, thresholds, session sequence and delayed-analysis
rule remain unchanged.

## Retained attempt 1 — A56 confirmation 1

After explicit participant/CAMERA authorization, the exact checked app APK
(`95cfc1c457955131e0271ae81472afd66d90a5006556d98fd05d53570410e73d`)
was reinstalled on A56 `R5CY40Y2C5W`. Before setup, CAMERA was denied/ignored,
the app was stopped and `files/confirmation` did not exist. The setup used:

- run label `a56_confirmation_1`;
- device identity `samsung/SM-A566B`;
- validation order `forward_then_reverse`; and
- protocol/schema `mgazenet_direct_validation_confirmation_v1`.

The participant declared the same viewing setup as the preceding sessions:
no glasses, approximately 30 cm viewing distance with ordinary closer/farther
variation, medium screen brightness and the same lighted room. The changed
context was daytime and “more fatigued.” Fatigue is retained as context and is
not an exclusion, correction or reason to rerun.

The app reported completion. CAMERA was immediately re-denied, its effective
UID app-op was returned to `ignore`, both benchmark package processes were
force-stopped and absent, and the single new numeric record was copied through
the hash-verifying collector to ignored local evidence:

| Field | Value |
| --- | --- |
| Session ID | `1788996966439_c741e1b1-4887-4c01-9527-b58228a44b57` |
| Outcome | `complete` |
| Raw report SHA-256 | `a498f3584142ae5f69e6b0f090b543684f353064be8c87de8ef78852a190986a` |
| Local evidence | `diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/a56_confirmation_1/1788996966439_c741e1b1-4887-4c01-9527-b58228a44b57/` |

The computed local report hash exactly matches the device-written hash. The
collector verified the complete schema, protocol/session identity, privacy,
no-correction, hidden-screen and null-decision contracts. The record reports
no active-tracker access and no retained camera frames, features or personal
model. The accuracy gate remains null and promotion remains `not_evaluated`.

No confirmation scorer was run. The screen result and all spatial performance
values remain unexamined until all four frozen attempts have terminated and
their hashes are preserved. The ignored `diagnostics-local` evidence must not
be staged.

## Retained attempt 2 — G991B confirmation 1

The second attempt began approximately 17.36 minutes after the estimated A56
terminal time, exceeding the frozen ten-minute separation. The G991B
`R5CR60YSJ2X` baseline had CAMERA denied/effectively ignored, absent benchmark
processes and no `files/confirmation` directory. The same exact app APK was
reinstalled. The setup used:

- run label `g991b_confirmation_1`;
- device identity `samsung/SM-G991B`;
- validation order `reverse_then_forward`; and
- protocol/schema `mgazenet_direct_validation_confirmation_v1`.

The participant declared the same conditions as attempt 1: no glasses,
approximately 30 cm with ordinary distance variation, medium screen brightness,
the same lighted room, daytime and more fatigue. The app reported completion.
CAMERA was immediately re-denied/effectively ignored and both package processes
were force-stopped and absent.

| Field | Value |
| --- | --- |
| Session ID | `1788998305355_3d94ff60-2cd2-4969-97ce-57d8903b7eaa` |
| Outcome | `complete` |
| Raw report SHA-256 | `b9e42e276239b9f1dc1dbb3041a91d03c7e1eeadfc0c474aa8a439bb1b4ad2d6` |
| Local evidence | `diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/g991b_confirmation_1/1788998305355_3d94ff60-2cd2-4969-97ce-57d8903b7eaa/` |

The collector verified the matching device-written/local hash, complete
identity, privacy, hidden-result, no-correction and null-decision contracts.
No scorer was run and no spatial value was inspected.

## Retained attempt 3 — G991B confirmation 2

The first attempt of round 2 began approximately 18.24 minutes after the
estimated G991B confirmation-1 terminal time, exceeding the frozen 15-minute
between-round separation. The same G991B and exact app APK were used. Its
pre-run baseline had CAMERA denied/effectively ignored, both processes absent,
and only the already-preserved confirmation-1 device record. The setup used:

- run label `g991b_confirmation_2`;
- device identity `samsung/SM-G991B`;
- validation order `forward_then_reverse`; and
- protocol/schema `mgazenet_direct_validation_confirmation_v1`.

The participant again declared unchanged conditions: no glasses, approximately
30 cm with ordinary distance variation, medium screen brightness, the same
lighted room, daytime and more fatigue. The app reported completion. CAMERA was
immediately re-denied/effectively ignored, both processes were force-stopped and
absent, and the two expected G991B device record IDs were present.

| Field | Value |
| --- | --- |
| Session ID | `1788999691168_0203775a-cb4e-4528-a1f4-875fc5137f09` |
| Outcome | `complete` |
| Raw report SHA-256 | `d79a86e2a1ea70e89186856f204ccbeb7bb70cd5ba9fe6887600dccfd9202011` |
| Local evidence | `diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/g991b_confirmation_2/1788999691168_0203775a-cb4e-4528-a1f4-875fc5137f09/` |

The collector verified the matching device-written/local hash, complete
identity, privacy, hidden-result, no-correction and null-decision contracts.
No scorer was run and no spatial value was inspected.

## Retained attempt 4 — A56 confirmation 2

The final attempt began approximately 15.14 minutes after the estimated G991B
confirmation-2 terminal time, exceeding the frozen ten-minute between-session
minimum. The A56 `R5CY40Y2C5W` baseline had CAMERA denied/effectively ignored,
both processes absent, and only its already-preserved confirmation-1 device
record. The same exact app APK was reinstalled. The setup used:

- run label `a56_confirmation_2`;
- device identity `samsung/SM-A566B`;
- validation order `reverse_then_forward`; and
- protocol/schema `mgazenet_direct_validation_confirmation_v1`.

The participant confirmed the conditions remained unchanged: no glasses,
approximately 30 cm with ordinary distance variation, medium screen brightness,
the same lighted room, daytime and more fatigue. The app reported completion.
CAMERA was immediately re-denied/effectively ignored, both processes were
force-stopped and absent, and the two expected A56 device record IDs were
present.

| Field | Value |
| --- | --- |
| Session ID | `1789000895500_8f468e21-5e04-4a6a-a46a-c75b2001a088` |
| Outcome | `complete` |
| Raw report SHA-256 | `420c99582445feff2a87521ecadc6b6b3dbe23ded27ac3b0bc4fab3d10cd3ca5` |
| Local evidence | `diagnostics-local/2026-09-10/mgazenet-direct-validation-confirmation-v1/a56_confirmation_2/1789000895500_8f468e21-5e04-4a6a-a46a-c75b2001a088/` |

The collector verified the matching device-written/local hash, complete
identity, privacy, hidden-result, no-correction and null-decision contracts.
At that point all four frozen raw records were preserved, satisfying the
delayed-analysis boundary. Scoring then proceeded exactly once through the
frozen independent tools; results are recorded in the analysis document.

## Frozen sequence state and boundary

| Order | Attempt | Required sweep order | State |
| ---: | --- | --- | --- |
| 1 | A56 confirmation 1 | forward, then reverse | complete, scored after collection |
| 2 | G991B confirmation 1 | reverse, then forward | complete, scored after collection |
| 3 | G991B confirmation 2 | forward, then reverse | complete, scored after collection |
| 4 | A56 confirmation 2 | reverse, then forward | complete, scored after collection |

Collection is closed with exactly the four planned complete attempts. Continue
from `gaze-mgazenet-direct-validation-confirmation-analysis.md`. Do not add or
replace attempts, change thresholds, or tune against this evidence set.

# NewsMead scanpath-to-aid pilot — blank plan and session record

Use with [pilot revision 6](scanpath-measurement-pilot.md) and the [live instrument guide](scanpath-live-pilot.md). This is a blank template, not participant data. Fill copies in an approved private study location; keep identities/contact details and recordings out of this repository.

## Study plan frozen before recruitment

| Field | Planned value |
| --- | --- |
| Protocol revision/date; phase A/B/C/D/E; development or independent evaluation | |
| Objective O1–O6; primary outcome, reference, denominator and analysis unit | |
| Age range; reading language; inclusion/exclusion; vision correction | |
| Recruitment route; reader count and precision/size rationale | |
| Independent sessions per reader; passages/trials per session | |
| Tracking/calibration failure reporting; stopping/retry rules | |
| Device/model/runtime/calibration version; initial 22 dp condition | |
| Layout/theme/orientation/posture/lighting; dot visibility by condition | |
| Passage versions/hashes; fresh materials; condition/passage order | |
| Randomization/counterbalancing unit and assignment method | |
| Separate development/evaluation reader and passage allocation | |
| Target instrument version; raw scoring stream; geometry/target identity | |
| Target/order schedule; capture-clock scoring intervals; compliance recording | |
| Event matching tolerances; allowed extra fixations; negative/control sequences | |
| Analyzer/source hash; detector settings; prespecified sensitivity variants | |
| P01–P13 included/excluded; classifier version; exact rules/configuration hash | |
| Geometry/window/count/baseline settings and their development evidence | |
| Support-need report method/time unit; comprehension questions/scoring | |
| Reporting burden; support-need label definition; missing-outcome handling | |
| Pre-report feature window; prediction lead/guard interval; negative-window sampling | |
| Report onset/interaction/resume markers; excluded interaction/recovery intervals | |
| Guard sensitivity; anticipatory control-seeking limitation | |
| A0–A4 included; policy/renderer version; shadow or experimental display | |
| Aid type/anchor/strength parameter values; duration/expiration/reacquisition | |
| Comparator/question: no cue, another aid, constant strength or static aid | |
| Planned exposure opportunities; actual exposure reporting | |
| Wrong-cue/coverage/progression criteria; rationale from development evidence | |
| User-off/dismissal; interruption/adverse-feedback/stop response | |
| Independent outcomes and analysis plan; reader/passage clustering | |
| Study/collection authorization and approved private storage location | |

If a field is unresolved, mark **pending**. Resolve fields needed for the chosen phase before recruitment/collection. A research proposal does not freeze unspecified numeric gates or participant counts.

## Software/readiness record

| Item | Version / check / result / limit |
| --- | --- |
| Offline extraction reference and focused suite | |
| Pattern fixtures: positives, negatives, boundaries, ambiguity | |
| Per-pattern contributing evidence IDs and causal availability times | |
| Matched raw placement/order instrument complete and checked | |
| Live engine vs causal prefix replay; resets/duplicates | |
| Policy/action replay; no-aid and abstention behavior | |
| Geometry anchors and renderer acknowledgment/timestamps | |
| Event first-support/decisive-capture times; clock conversion; render-proxy meaning | |
| Strength controls; user-off; clearing/expiration | |
| Approved feature/phase for participant use; unresolved items | |

Software checks establish implementation behavior. They do not establish physiological fixations, natural word-order accuracy, difficulty interpretation or aid benefit.

## Linked participant session set

| Field | Observed value |
| --- | --- |
| Pseudonymous reader; session-set code; independent repeat | |
| Phase/condition; randomized assignment; development/holdout designation | |
| Date/device/model; orientation/posture/distance/lighting | |
| Usual correction/glasses; deviations | |
| APK SHA-256/build provenance; classifier/policy/configuration hashes | |
| Calibration SHA-256/time; elapsed time to pre-check | |
| Article ID/version/text hash; language/font/layout/theme | |
| Dot visibility; aids off/shadow/experimental; correction setting | |
| Pre-check file/hash; raw/smoothed stream; scored target schedule | |
| Prescribed sequence file/hash; target timestamps; compliance | |
| Deliberate plumbing diagnostic, if any; prescribed actions | |
| Reading/task file/hash; exact start/end; passage order | |
| Shadow/action/render log/hash; event and action IDs | |
| Independent responses/comprehension/report file and scoring provenance | |
| Help/report interaction onset/end; reading resume; input-window exclusions | |
| Post-check file/hash; unchanged calibration | |
| Scroll/look-away/interruptions; alignment complaints; deviations | |
| User-off/dismissals; unwanted cues; adverse feedback; stop reason | |
| Complete/lossy footer; record loss; missing outcomes and reasons | |
| Recalibration/retry; retained previous attempt; new linked set code | |
| Analyzer/source hash; output/configuration; input hashes | |

Use the prescribed condition for the phase. Baseline/shadow reading has aids off; experimental aid phases must record the actual displayed aid and strength. Frozen structured v6 uses correction OFF; any new correction condition requires explicit versioning.

## Report by pattern and resolution

Duplicate rows privately for each P01–P13, with word and line results separated.

| Item | Result / denominator / uncertainty |
| --- | --- |
| Pattern ID; input sequence; rule/configuration; source link and limit | |
| Software fixture/prefix results | |
| All attempted/completed readers, sessions and scheduled opportunities | |
| Prompted target agreement/confusion; compliance/missing/unknown | |
| Reference-label support: intended movement, processing audit or unavailable truth | |
| Matched prescribed events; missed/extra events; precision/recall where justified | |
| Raw eligibility vs accepted monotonic sample counts | |
| Capture span vs observed fixation/word/line spans | |
| Assigned/ambiguous candidates; complete/censored visits | |
| Contiguous group lengths; eligible word/line transitions | |
| Boundary events vs broken links by reason | |
| Pre/post change; drift onset unresolved; repeatability by reader/session | |
| Independently reported support need/outcome association, if studied | |
| Held-out status; sensitivity variants; failed/unresolved progression stage | |

## Report by aid and strength

Duplicate rows privately for each A0–A4 and studied parameter setting.

| Item | Result / denominator / uncertainty |
| --- | --- |
| Aid/type/anchor; strength parameters; trigger; comparison condition | |
| Scheduled opportunities; eligible triggers; suppressed/withheld decisions | |
| Chosen vs actually displayed actions; render failures; stale/wrong targets | |
| Coordinate output age; first-support/decisive-capture-to-render and component timing | |
| Render acknowledgment/proxy; cue timing vs prompted transition/independent response | |
| Delivered cue count/duration; expired/dismissed cues; user-off | |
| Independent localization/recognition/task performance | |
| Comprehension score and missingness; scoring blinded to gaze/policy where feasible | |
| Helpfulness/distraction/unwanted cues/adverse feedback | |
| Effect estimate/uncertainty; reader/passage unit and clustering | |
| Constant vs varying strength results, if adaptation studied | |
| Development-based selection; frozen rule/version; independent evaluation status | |

Write **not measured** when the reference/instrument/outcome is unavailable. Smoothed v6 cannot fill raw result cells. A same-stream annotation cannot fill natural eye-position truth. Paper effect sizes/state confidence cannot fill a NewsMead difficulty probability. A reported unwanted cue is not automatically proven spatial misplacement.

Focused extraction command:

~~~powershell
python -m unittest discover -s tools/gaze -p test_analyze_reading_measurement.py -v
~~~

The existing extraction suite checks software contracts. New classifier/live/policy checks must be added as those components are implemented.

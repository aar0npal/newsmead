# Article demo and word-definition aid

**2026-10-15 typography update:** ordinary article font size is now adjustable
(14–60 sp, 22 sp default) with proportional spacing. The attachment algorithm
continues to use current painted-word bounds; reflow clears stale offers.
The 22 dp setup below describes the earlier checked baseline. Dedicated Pilot
and Replay retain that controlled size. The 1,500 ms rule is unchanged and is
not font-normalized. See [article text size](article-text-size-controls.md).

**2026-10-15 closer word attachment (verified software checkpoint):** owner finds the icon-word
gap excessive. `WORD_LOOKUP_INFO_BUBBLE_INK_CORNER_V4` positions the actual vector
tip3dp above the current word's painted letter top, measured from the Layout
baseline and word ink bounds. It no longer adds distance from line-top/font
leading. Right-edge alignment/mirroring and all trigger/timeout rules remain.
The transparent48dp target may extend into unused leading, but must end above the
source letters and remain wholly inside the viewport. Empty word ink withholds.
No text reflow or font/spacing changes. The supported article path uses plain
English strings; this is an appearance setting, not a study-derived threshold.
Native checks include rendered-pixel spacing with the actualArial22dp/1.6 article
settings, normal/mirrored positions and consultation/expiry regressions.
Build/device checks are complete: both APKs and data-preserving A56 installs
pass, as do all15 focused native checks in9.384s. Source review found no actionable
issue. Normal and article-spacing previews visually inspected; actual pixel checks
verify spacing independently of the word-bound calculation. No camera/calibration
changes, staging or commit. Evidence: `output/lookup-bubble-gap-check-01/summary.json`.
Earlier spacing descriptions below are historical. These checks establish software
behavior, not human benefit or natural gaze accuracy.


**2026-10-15 blue information-bubble revision (verified software checkpoint):** after reviewing
the bubble placement, owner supplies a blue speech bubble with a white lowercase
information symbol. The28dp native vector now follows that reference: blue fill,
dark-blue outline, upright white “i”, transparent exterior and no enclosing tile.
It retains the48dp touch area and word-upper-right tail alignment; at the right
edge, only the bubble outline mirrors while the “i” stays upright. The earlier
monochrome search-bubble build and all15 focused native checks passed, but that
appearance is superseded. Blue app/test builds and3 selected A56 native checks
pass in4.657s. Visual inspection found a missing dark border; filled outer
silhouette plus inset gradient corrects it. Final app build/install and both native
placement/render checks pass in0.169s. Normal/mirrored synthetic widget previews
are visually inspected; no screen/camera capture or private article data.
Evidence: `output/lookup-bubble-corner-check-03/summary.json` and its PNG previews.
Phone data/calibration stay preserved; no staging/commit.
Style ID `WORD_LOOKUP_INFO_BUBBLE_CORNER_V3` identifies the revision. The1,500ms
trigger,5s lifetime and tap-only dictionary/consultation rules stay the same.
Shared Article/Pilot/Replay/demo uses this appearance. No source text reflow or
gaze/calibration changes. These dimensions are UI choices, not paper doses.

**Current 2026-10-15 lookup offer (verified software checkpoint):** long word
residence no longer automatically opens the panel. The existing1,500ms supported
observed-residence rule first offers an outlined speech-bubble/search icon above
the source line:20dp glyph,48dp touch target,4dp gap. It stays on the original word
for5 seconds after its first successful layout receipt; refreshing actions,
null actions and movement to another word do not renew or shuffle it. A tap
revalidates text/geometry/visibility, removes the icon, and requests the existing
panel and dictionary route. The panel highlights the source word as before.
No dictionary lookup, source-word highlight or whole-reading consultation begins
for the icon alone. Looking at the icon is excluded as `reader_controls` while
provider/raw coordinates remain preserved. Focus demand may coexist with an offer;
actual panel consultation still pauses article interpretation.

An ignored icon expires without a query. Same-word offers remain suppressed until
a valid different source word is observed or an operational boundary clears the
state. Scroll/reflow/text/menu/lifecycle changes clear the icon and its timer.
Placement uses the nearest visible article scroll viewport, including headline
headroom; with no room above, it withholds rather than appearing below or covering
the source word. Above-icon placement can overlap earlier article text, so its
actual control bounds are excluded from article reading. No layout/reflow occurs.

Article, Pilot, Replay and the article demo share this interaction. Demo stages
still advance every4s and clear their offer on stage change; native offer lifetime
is5s in normal reading. Current form `CONTEXT_WORD_LOOKUP_OFFER_V1`, policy15,
smoothed conditionV10; the `WORD_DEFINITION` enum identifies the family, while
render `presentation_phase` distinguishes `lookup_offer` and `definition_panel`.
Offer shown/accepted/expired/cleared records retain original action/evidence IDs;
acceptance time is user interaction time and never rewrites detector evidence.
Replay family draw counts can include an offer; use phase records to count panels.

The5s lifetime and glyph/target sizes are interaction choices requested for this
prototype, not study-established difficulty or distraction thresholds. The1,500ms
rule remains a trial threshold and is not font-normalized. Human benefit, visibility,
and gaze targeting require later evaluation. Older automatic-panel descriptions
below are historical.

Verification:199 focused JVM tests and both APK builds pass. Independent source
review's offer-phase labeling finding is fixed; follow-up has no actionable
findings. Owner-approved data-preserving A56 app/test installs succeed.20/21
camera-free native checks initially pass in21.148s; the remaining detached-view
hit-area test used untransformed window coordinates. Reviewed test-only correction
retains48dp and inside/outside edge assertions. Approved test APK rebuild/install
and one-check retry pass in0.030s. All21 distinct native cases now pass across the
two invocations. Cases cover no lookup before tap, fixed deadline/no renewal,
stale callbacks/clicks, suppression/rearming, geometry changes, above-only placement
including scroll headline headroom, consultation/raw exclusion, ignored offers,
Replay and actual-source demo anchors. They do not validate natural gaze targeting
or human reading benefit. Evidence: `output/definition-lookup-offer-check-04/summary.json`;
03 retains the full native run and199 JVM XML results,01/02 earlier failed gates.
No camera, calibration changes, staging or commit.

**Current 2026-10-14 stricter trigger:** owner considers the
definition card more interruptive and requests a longer gaze requirement. The
default increases from800ms to **1,500 ms of supported observed residence on the
current word**, including nonoverlapping finalized visit time plus a supported
ongoing prefix. It is not a wall-clock hover or a claim of one continuous
physiological fixation. Missing/unavailable intervals and separate short prefixes
cannot fill the threshold. Policy10/current smoothed conditionV5; English-card
layout, source highlight, dictionary providers and consultation exclusions retain
their contracts. The longer delay is a configurable prototype choice, not a
validated difficulty threshold or measured distractibility ranking. Older800ms
descriptions and scores below are historical.

The shared 207-test JVM gate passes, both APKs build and source review has no
actionable finding. A56 data-preserving updates succeeded under the owner's
existing aid-update authorization; all 3 camera-free Android checks pass in
23.743 s, including a delayed native dictionary card and consultation exclusions.
This verifies software behavior, not that 1,500 ms optimizes human interruption.
Evidence: `output/focus-stability-definition-delay-check-02/summary.json`.

Updated 2026-10-13 · `measurement/scanpath-pilot` · experimental implementation.

**2026-10-13 current dictionary fallback:** repeated owner reports say the card
opens without a definition. The previous connection gate demonstrated service
connectivity for keyboard/hello, while analyst used the small glossary; it did
not establish adequate entry coverage. New source adds one exact-word online
fallback: Free Dictionary API → Datamuse → original glossary. Live successes
from either provider enter memory; bundled fallback stays retryable. Offline-only
replay/synthetic pilot bypass both transports. The adapter identity is
`FREE_THEN_DATAMUSE_EXACT_THEN_GLOSSARY_V2`; runtime records it separately from
actual pilot online/bundled mode. Gaze selection and the 800 ms rule are unchanged.

Datamuse's [official API documentation](https://www.datamuse.com/api/) describes
`sp` as similar-spelling search and `md=d` as definitions from Wiktionary/WordNet.
The Datamuse fallback accepts only a response whose `word` exactly equals the requested
normalized English token; no nearby spelling is substituted. It displays at most
two senses/1,000 characters and acknowledges Datamuse plus its combined sources.
A public analyst probe returned HTTP 200 in 493 ms, one exact entry and six
definitions; observed `n` followed by tab is rendered as noun. Unknown prefixes
remain raw; missing/malformed/nonstring/mismatched entries withhold that result.
The definition format is an observed integration format, not a guaranteed schema.
Current docs announce API keys from February 1, 2027; this keyless prototype will
need provider configuration before then. Neither provider identifies the intended
sentence sense or guarantees every news word/name. No paid service, backend
deployment, NewsData-key reuse or invented definitions is added.

Cancellation and duplicate callbacks are guarded across both stages; offline
lookups initialize neither network queue. The existing 2,500 ms timeout applies
to each request, not a guaranteed total deadline. Live analyst checks now reject
glossary fallback as proof of online success. Source review found no actionable
P1/P2. All **125 focused JVM checks** and both APK assemblies pass. Authorized
data-preserving A56 app/test updates installed. One camera-free nine-test run
passes in 1.642 s: keyboard primary live/native card in 466 ms, hello primary live
in 105 ms, analyst Datamuse live in 794 ms. A targeted analyst native-popup check
passes separately in 3.529 s: fresh lookup 3,467 ms, meaning/source populated and
Close worked. These are ten distinct cases across two invocations, not one
ten-test run. Slow lookup can retain loading for several seconds; these latencies
are observations, not a performance guarantee. Logs, XML and APK hashes:
`output/dictionary-article-check-03/summary.json`. Live article fields could not
be read from the current UI hierarchy; natural gaze behavior is not part of the
camera-free gate. No camera/gaze, participant collection, staging or commit ran.

**Historical 2026-10-13 connected-phone verification:** owner approved ADB retry,
data-preserving app/test updates and camera-free dictionary checks on SM-A566B /
API 36. The installed build's hash matches the generated APK. Fresh public-word
requests use a new repository and `allowHttpCache=false`: keyboard returned a
live definition in 408 ms, populated the native popup and closed correctly;
unbundled hello returned live in 83 ms; analyst returned the labeled original
glossary fallback in 79 ms. **All nine phone checks pass in 0.792 s**, including
error-feedback distinctions, consultation cancellation and bundled-only lookup.
All 19 focused repository/glossary JVM checks and both APK assemblies pass.
The app's Internet permission is granted. Host keyboard returned HTTP 200 in
381 ms; analyst returned HTTP 404 in 320 ms. The host error-body parsing failed
to retain its payload, so exact 404 subtype is not claimed.

This establishes phone-to-service connectivity and native popup fixture delivery.
It does not establish that every English/news token exists in the service, or
validate live gaze targeting and article interaction. Normal reading keeps its
HTTP cache; only explicit checks disable it. No secondary dictionary service
was added. No camera, live gaze, participant data, staging or commit ran.
Logs, XML and installed hashes: `output/dictionary-connection-check-02/summary.json`.

**2026-10-13 implementation amendment:** guide/menu source uses definition V4 /
policy 5; [current controls and sentence guide](reading-aid-menu-and-sentence-guide.md).
The owner reported an unavailable/connection message for **analyst** in a normal
article. One host GET returned HTTP 404 in 406 ms; response payload was not saved,
so it does not distinguish a dictionary missing-entry payload from a generic
service error or establish the phone cause. Debug merged manifest contains
Internet permission. The blanket connection wording is replaced by classified
feedback: unsupported token, missing entry, service response failure, offline-only
absence or unknown absence. A 404 is classified missing only when JSON title is
`No Definitions Found`; malformed/generic 404 remains a service failure.

The original NewsMead glossary now has **76 entries**, including **analyst**:
a specialist interpreting facts/data to offer assessments or forecasts. Its
general sense was checked against [Cambridge's analyst entry](https://dictionary.cambridge.org/dictionary/english/analyst);
no publisher text is reproduced in the glossary. It is labeled **Offline · NewsMead
English glossary**. Online lookup still runs first; fallbacks are not cached as
online results. This corrects the reported word's empty card, without claiming
complete dictionary coverage or sentence-sense disambiguation. Detailed results
preserve the existing nullable repository interface and popup cancellation guards.
Verification: all 119 focused JVM checks pass and both APKs assemble after an
owner-approved retry correcting a test-only nullable-property compile error.
Review found a diagnostic-dot exclusion introduced by fixed entry; its order is
corrected and independently source-confirmed. Android feedback fixtures compile,
but were not executed on a phone. Logs, XML, hashes and host metadata:
`output/fixed-aids-dictionary-check-01/summary.json`. No device install, camera,
participant check, staging or commit ran.

**Historical 2026-10-13 marker amendment:** article/pilot/replay selected definition
profile V3/policy 4 and the [last recorded-line bookmark](scanpath-reading-bookmark.md).
The cue stays at the source line after outside departure or a supported non-normal
jump; the demo previews that source line. V2 continuation descriptions below are
historical. Dictionary lookup and its 800 ms rule are retained.

## Current behavior

Loaded article → diagnostics (three dots) → **Article aid demo**. The actual
article cycles every four seconds: baseline, current-line highlight, source-line
bookmark, near-word definition, light focus, strong focus. Previous/Next/Stop work
inside the article. Source text and fonts are unchanged.
The article body now reserves a fixed **16 dp start gutter**. Its line-start
marker is drawn in that gutter, with a **4 dp gap** before the first letter,
in both the live article and the demo. Padding is present from layout inflation,
including baseline stages, so showing the aid does not move the text. The narrower
text area may change wrapping; gaze/word anchors use the actual padded TextView
geometry. A clipped gutter causes marker abstention instead of text overlap.
The source word is highlighted for the whole consultation. Close uses a larger
vector × in a compact 28 dp gray box, within a transparent 48 dp tap target.
The card now follows the internet template described below. Current native
popup fixtures pass on the A56, including a real dictionary response (check 06
below); actual gaze-triggered article reading remains a manual check.
Short articles omit unavailable focus previews. Demo stops the gaze provider and keeps the camera
off after Stop/pause/return; reopen the article to resume live reading. Demo
actions/receipts have no detector evidence and never enter the live journal.

Ordinary articles use `DEFINITION_PROFILE_V3` / `EXPERIMENTAL_DEFINITION_V3` /
`CONTEXT_WORD_DEFINITION_V1`. Old magnifier IDs retain their historical meaning.
The pilot and replay now select the same definition profile with distinct
`PILOT_DEFINITION_*_V3` / `REPLAY_DEFINITION_V3` condition IDs. Replay's gallery
uses the article's current six-stage demo. Frozen comparison protocols and
measurement identities are unchanged; old magnifier results are not pooled.

The [reading bookmark](scanpath-reading-bookmark.md) marks the last supported
source line after sustained usable outside-article coordinates or a supported
non-normal line jump. It has fixed visibility and stays until supported return,
distinct continued reading or explicit invalidation. Tracking loss alone and
normal next-line movement do not create it. The demo previews the saved source
line. Earlier V1/V2 cues retain their historical meaning.

| Step | Implemented behavior | Limit |
| --- | --- | --- |
| Current word | Existing raw I-DT candidate/visit, rendered UTF-16 word assignment, eligible geometry and causal fresh evidence | Agreement is not exact-word accuracy. |
| Help opportunity | At least **800 ms observed current-word time**: finalized same-word visit time plus a nonoverlapping established prefix, or the prefix alone | Configurable exploratory setting, not a validated difficulty cutoff. No obligatory two refixations. |
| Definition | Card above/below the word, inside the visible article, without covering its source line; scrollable meanings, attribution, Close | Neighboring lines may be covered. Common meanings do not establish the intended sentence sense. Hidden/ambiguous geometry or insufficient room causes abstention. |
| Consultation | Card stays open until Close, scrolling/layout/text change, disabling help or lifecycle teardown | Conservatively excludes the entire open interval from article-path interpretation; does not assert every fixation is on the card. |
| Return | Opening/closing breaks sequence continuity. Late pre-boundary frames excluded. A dismissed word cannot reopen until a different eligible target is observed | No popup-to-article regressions or new requests from consulting the card. |

The usual 1500 ms action TTL does not dismiss consultation. Policy resets at
opening; the popup owns its lifecycle. The action envelope describes initial
selection; explicit consultation boundaries describe the commanded display
interval. Demo stage change clears its card. A definition is an aid type, with
no magnification/ordinal ladder. Line/focus rules retain their existing controls.

## Internet layout template and offline preview — 2026-10-04

The selected visual reference is the official
[Flowbite React Default Popover](https://flowbite-react.com/docs/components/popover#default-popover).
Its shaded title area, divider, padded content, rounded outline and pointer are
adapted in native Android XML. The larger close control, scrollable meanings,
source attribution and persistent source-word highlight serve this app's existing
requirements. Colors and Arial styles follow NewsMead's day/night theme.
Source provenance: [official component documentation](https://flowbite-react.com/docs/components/popover)
and [project MIT license](https://github.com/themesberg/flowbite-react/blob/main/LICENSE).
The native implementation is independently written; no React/Flowbite dependency
or webview was added. This template supplies a layout reference, not evidence of
reading benefit; H03/M25 below remain the relevant interaction precedents.

`OfflineWordDefinitions` bundles **76 English entries**, including common news
words and function words. These are original, concise NewsMead glossary entries,
not quotations from Free Dictionary API and not a complete English dictionary.
Every bundled definition is labeled **Offline · NewsMead English glossary**.
As requested on 2026-10-08, normal article/demo and live pilot lookup now use
successful online memory cache → Free Dictionary API → bundled glossary on
failure. Even glossary-covered words request the live dictionary first. Fallback
entries are not cached as online results, so a later lookup can try online again.
Replay and explicitly synthetic pilot checks remain bundled-only and never
create the lazy network queue. Unknown words without an online result or bundled
entry honestly show unavailable text. There is no guessed definition or automatic
morphological expansion. Neither dictionary supplies sentence-sense disambiguation.

The article demo prefers an actual, completely visible article word covered by
the glossary, prioritizing words of at least four UTF-16 code units. Its line must
also have a completely visible successor for the line-start stage. If no covered
word is available, it retains an actual article word and the usual online/unavailable
behavior. Source text and UTF-16 offsets are preserved. With an article already
loaded, the covered definition stage works without an internet connection; this
change does not add offline news fetching or a full dictionary download.

Native resources: `view_word_definition_popover.xml`, `bg_definition_popover.xml`,
`bg_definition_close.xml`; lookup/demo changes are confined to the aid package.

## Primary studies

**H03 — Hyrskykari, Majaranta & Räihä (2003), _Proactive Response to Eye
Movements_, INTERACT, pp. 129–136.**
[Author-uploaded paper](https://www.researchgate.net/publication/221054255_Proactive_Response_to_Eye_Movements).
Read §4.2.1, §4.2.2, §5.1–5.2 and the original gloss/dictionary figure.
iDict placed translation help near words, accumulated fixation time including
returns, and adjusted timing for word length/frequency. Its typical approximately
800 ms setting varied with those factors. It avoided further accumulation/redraw
during consultation. Readers also intentionally looked longer to request help.
The SMI iViewX exhibition used visitor feedback (approximately 60 visitors,
47 note sheets), without a controlled comprehension benefit test. This is an
interaction precedent; the flat threshold, English definitions and older phone
readers remain unvalidated.

**M25 — Minas et al. (2025), _Adaptive Real-Time Translation Assistance Through
Eye-Tracking_, AI 6(1), 5.** [Publisher DOI](https://doi.org/10.3390/ai6010005),
[author-uploaded paper](https://www.researchgate.net/publication/387693494_Adaptive_Real-Time_Translation_Assistance_Through_Eye-Tracking).
Read §3, results and user feedback. The Tobii Spectrum desktop technical-text
experiment analyzed 47 predominantly young adults after exclusions. It reports
more words read and mixed popup feedback, including intrusion and wrong-line
help after movement. Its letter-dependent trigger differs. Reading count is not
comprehension/retention evidence; group-test analysis limits interpretation of
the within-participant experiment. English definitions and older phone readers
were not tested.

The owner's reported top-magnifier distraction motivates replacing the aid.
A nearby definition still draws gaze. Consultation gating addresses recording
interpretation; participant testing must establish distraction and reading benefit.

## Dictionary and teammates' branch

Initially fetched `origin/Recommendation` at
`501e9be1f83d4d962dc4931ef9044ab1f18a3e1b`; the owner-authorized
[Recommendation merge](recommendation-merge.md) completed on 2026-10-08.
Inspected `data/news/NewsDataApi.kt`, recommendation application paths and
`logging/Analytics.kt` / `AppLog.kt`. Bounded tree/source searches found no
dictionary endpoint. Its analytics contract excludes free text; dictionary words
and gaze are not added to that remote analytics route here.

The replaceable `WordDefinitionRepository` now prioritizes the
[official Free Dictionary API](https://dictionaryapi.dev/),
a 128-entry memory cache and existing Volley request cache. HTTPS requests send one normalized
English word, without article text, user ID or gaze. Timeout 2500 ms, no retry.
The earlier 2026-10-03 host request for `keyboard` timed out after 8 seconds.
For this newly requested 2026-10-08 integration check, one public host request
returned HTTP 200 in 395 ms, `word=keyboard`, four definition senses and
license/source attribution. Evidence: `output/dictionary-online-check-01/api-probe.json`.
This confirms one host response, not phone connectivity or service uptime.
Dismissal cancels requests; invalidated callbacks cannot reopen the card.
Returned source/license attribution is displayed. Missing words/network failure
show unavailable text. Demo definitions are never fabricated.

Only explicitly English original articles permit lookup. Unsupported live article
languages abstain from definition display entirely; the render-only demo can show
an unavailable-language card to explain coverage. A teammates' backend dictionary
can later implement the repository contract. The NewsData key supplies news,
not dictionary lookup; it is never sent to the dictionary provider. No backend
deployment or database write was needed for this dictionary change.

## Recording and checks

**2026-10-08 online route:** 26 focused JVM checks pass (11 repository, two
definition-policy, 13 replay-scenario checks). App and instrumentation APKs
assemble with fresh compilation; independent source review has no actionable
finding. Tests cover live-first lookup on glossary words, offline fallback,
retry after fallback, successful caching, malformed/unavailable responses,
offline-only mode, cancellation and duplicate replies. Evidence, copied test
XMLs and APK hashes: `output/dictionary-online-check-01/summary.json`. The
HTTP-200 host probe above is separate from mock test evidence. No device install
or phone test ran for this revision; English phone lookup remains unverified.
The later phone gate below supersedes that pre-device status. The older check
records remain historical.

**2026-10-08 A56 dictionary check 06:** the owner authorized installation and
approved retry after a test-only cleanup compilation error. Both corrected APKs
installed with `-r`, preserving data. One camera-free invocation passes **15/15**
checks in **0.835 s**, including a real default-repository `keyboard` definition
with Free Dictionary API attribution delivered in **443 ms**, matching native
card content and working Close. Four popup, two offline, one synthetic
consultation and seven article-presenter checks also pass. The new network smoke
is skipped unless explicitly invoked with `liveDictionarySmoke=true`. Evidence,
APK hashes, initial error and corrected build: `output/dictionary-device-check-06/summary.json`.
No camera/live gaze, human reading or private recordings were used. Native
fixture checks do not establish human targeting, visible live-article behavior
or service uptime.

- Monotonic elapsed milliseconds, screen-pixel raw coordinates, UTF-16 offsets.
- Session input callbacks retain numeric raw observations during consultation,
  with `definition_consultation` / `definition_boundary_late_capture`, absent
  article target and false acceptance. The private bounded aid journal retains
  inputs/boundaries and eviction counters; it is not a complete study recording.
- The gate activates immediately. Engine mutation waits for callback completion,
  preventing recursive fixation flush if finalized evidence opens the popup.
- Text/geometry/lifecycle invalidation cancels lookup. Host screen origin is
  subtracted for overlay positioning; the paragraph does not reflow.
- Provider shutdown completes before demo presentation; demo callbacks/receipts
  are detached from the closed runtime. No camera session starts from demo.

The first local compile found a nullable receiver error; it was corrected.
All **61 focused JVM tests** and app/test APK assembly then pass. New camera-free Android
fixtures compile for source/UTF-16 anchors, geometry boundaries, stale lookup,
unsupported language, response parsing and consultation opening inside finalized
P12 callbacks without duplicate fixations or bridged transitions. Their device
execution is pending. Earlier A56 15-test evidence belongs to the pre-definition
build. API availability, phone rendering, natural word targeting, distraction and
reading benefit are not established by compilation or synthetic checks.

2026-10-03 local check: **61 tests, zero failures/errors**, app and instrumentation
APK assembly pass after the language guard and finalized-resumption regression.
XML parsing and `git diff --check` pass. Machine-readable summary, APK hashes
and copied JVM XML evidence: `output/article-definition-check-01/summary.json`
and neighboring `TEST-*.xml`. Independent review's reentrancy and non-vacuous
resumption findings are addressed; no new device attempt ran.

2026-10-04 template/offline check: **64 focused JVM tests, zero failures/errors or
skips**, app and instrumentation APK assembly pass. The four popup resource XMLs
parse and `git diff --check` passes. New JVM tests check real bundled content,
attribution, normalization and honest unknown-word rejection. Camera-free Android
fixtures additionally compile for immediate repository delivery and covered-word
selection/fallback with unchanged source offsets; these fixtures have not run on
a phone. Summary, APK hashes and copied JVM XML:
`output/article-definition-offline-check-01/summary.json` and neighboring XML.
No device installation or capture ran. These checks do not establish phone
appearance, natural gaze accuracy or reading benefit.

2026-10-04 line-gutter follow-up: app/test APK assembly, article layout XML parsing
and whitespace checks pass. The camera-free rendering regression compiles and
asserts nonempty marker pixels remain separated from the text at maximum width.
It has not executed on a device; phone appearance remains pending. No device
install or capture ran.

Evidence appraisal used scientific-critical-thinking;
[Kassis et al. (2026), _Scientific Agent Skills_](https://arxiv.org/abs/2609.00065)
describes tooling, not reading-aid evidence.

## Recommended checkpoint (Windows CMD)

These paths include the preceding uncommitted demo/compatibility work required
by this revision. They exclude private output, local skills/environment files
and unrelated untracked documents. Nothing was staged or committed by the agent.

```bat
git add -- app/src/main/java/com/newsmead/gaze/aid app/src/main/java/com/newsmead/gaze/scanpath app/src/main/java/com/newsmead/activities/ScanpathReplayActivity.kt app/src/main/java/com/newsmead/fragments/article/ArticleFragment.kt app/src/main/res/layout/activity_scanpath_pilot.xml app/src/main/res/layout/activity_scanpath_replay.xml app/src/main/res/layout/fragment_article.xml app/src/main/res/values/strings.xml app/src/androidTest/java/com/newsmead/gaze/aid app/src/androidTest/java/com/newsmead/gaze/scanpath/replay/ScanpathReplayActivityTest.kt app/src/test/java/com/newsmead/gaze/aid app/src/test/java/com/newsmead/gaze/scanpath/ConsultationCaptureGateTest.kt app/src/test/java/com/newsmead/gaze/scanpath/replay/ReplayDispatchTimingTest.kt docs/README.md docs/agent-context.md docs/progress-notes.md docs/scanpath-adaptive-aid-design.md docs/scanpath-aid-runtime.md docs/scanpath-live-pilot.md docs/scanpath-replay-screen.md docs/scanpath-device-check-04.md docs/scanpath-trigger-evidence-review.md docs/article-definition-aid.md
git add -- app/src/main/res/drawable/ic_definition_close.xml app/src/main/res/drawable/bg_definition_close.xml app/src/main/res/drawable/bg_definition_popover.xml app/src/main/res/layout/view_word_definition_popover.xml
git add -- app/src/main/java/com/newsmead/activities/ScanpathPilotActivity.kt app/src/androidTest/java/com/newsmead/gaze/scanpath/pilot/ScanpathPilotActivityTest.kt
git diff --cached --check
git diff --cached --stat
git commit -m "Align article, pilot and replay with contextual reading aids"
```

# Reading aids menu and sentence-context prototype

2026-10-13 · `measurement/scanpath-pilot`

Owner approved the menu and a working sentence option. After trying the build,
the owner requested fixed bottom access, replacing tap-to-reveal.
This record supersedes the proposed line-entry opacity pulse and word-step release.

## Article controls

1. Tap **Aids**, visible in its fixed bottom-bar position.
2. Turn the reading guide on/off and choose **Line** or **Sentence context**.
3. Bookmark, word definitions and focus window have independent switches.
4. **Done** closes the sheet. Changes apply immediately to this reading session.

The button remains visible during article taps and scrolling. Article touches
have no menu listener. The existing Article options menu is another route.
The button uses the same bottom position; current study mode locks font size
and hides the existing font buttons. Theme controls and article wrapping are
preserved. It has a 48 dp touch height and is disabled during raw diagnostic
recording. There is no automatic camera operation for this UI.

Master off retains the individual choices. Preferences are session-only.
Replay **Controls** uses the same sheet, stopping replay before editing. Pilot
**Settings → Reading aids** uses it; the parent **Apply** applies pilot settings.
Prompted tasks remain aids-off and shadow/display retain their existing rules.

## Focus window appearance

Under the Focus window switch, choose **Fade surrounding text** (current default,
reader-background mask) or **Dark surroundings** (black mask). Fade applies90%/98%
mask opacity at initial50%/100% strength. As of2026-10-15, Dark applies40%/60%
with a400ms smooth onset to soften the immediate visual change. Both preserve the same4–5-line opening,
motion, scroll handover and triggers. The radio choices disable when master/focus
is off and retain their selection. As with other aid preferences, this choice is
session-only. Article/demo/Pilot/Replay receive it; action/preference exports record
`focus_style`. Policy14/conditionsV9 identify this addition.18 focused JVM checks
and7 distinct camera-free A56 checks pass across the initial run and approved
two-check retry. The dismissal test now waits for its posted callback; display
was confirmed awake/unlocked for Replay retry. Both APKs build/install, with
data/calibration preserved. Evidence: `output/focus-style-menu-check-02/summary.json`.

## Guide selection and display

| Option | Current implementation |
| --- | --- |
| Line (default) | Supported current line from the existing established candidate/finalized evidence, matching eligible current input. No long-dwell or difficulty trigger is required. |
| Sentence context | Original text is segmented with English `BreakIterator`; blank lines delimit paragraphs. One sentence overlaps the supported line: select that sentence across its wrapped lines. Multiple sentences overlap the same line: select their containing paragraph. Blank, malformed or cross-paragraph ambiguous mappings: withhold. |
| Shared appearance | Gray `#808080` background behind letters, preserving foreground color, source text, UTF-16 offsets and font metrics. Default normalized strength 0.25 gives alpha 20/255 = **7.843% opacity**. |
| Strength | Adaptive guide strength remains the fixed 0.25 baseline, bounded by the reader cap. Manual guide strength is bounded by the separate cap: 0.5 gives 15.686%, 1 gives 31.373% opacity. The sheet shows actual opacity rounded to one decimal. No 20% entry pulse is implemented. |
| Release | Missing/stale input, geometry/session change, disable or invalid context clears the guide. Same sentence/paragraph range retains its action across wrapped lines within the existing bounded lifetime; guides have no post-clear cooldown. |

Guide selection does not require a correct word assignment, word residence,
word saccade, word-step release or another aid's activation. Dictionary targeting
still uses its separate word route. The other aid rules are unchanged.
Paragraph fallback can cover substantial text. English punctuation segmentation
is a prototype, not validated sentence understanding, especially for translated
text and abbreviations. Supported line assignment also still needs phone validation.

## Menu and measurement boundaries

Article options and the aids sheet share scoped runtime suspension. No resume
occurs between nested menus. Frames captured at or before the final close boundary
remain excluded when delivered later. Visible button coordinates are excluded
as reader controls. Hit tests use the project's screen-coordinate helper.
Final dismissal resumes only the same live view/runtime/session while resumed
and outside diagnostic recording. The fixed entry no longer has visibility
transitions or their capture floor; menu boundaries retain theirs. The frozen
raw measurement record call is preserved.

## Identity and evidence

Current article/pilot/replay use `DEFINITION_PROFILE_V4`, policy 5. Pilot condition
IDs end in `_V4`; replay uses `REPLAY_DEFINITION_V4`. Evaluator layout identity is
`CONTEXT_GUIDE_MENU_GUTTER_V1`. New forms are `CURRENT_LINE_HIGHLIGHT_GRAY_V1` and
`CURRENT_SENTENCE_CONTEXT_HIGHLIGHT_V1`; both trace writers record `guide_range`
separately from word offsets. Historical magnifier/V2 profiles keep their meaning.
The six-stage article/replay presentation demo shows the selected guide, without
detector evidence; only word aid previews carry word anchors.

The [guidance evidence review](scanpath-guidance-evidence-review.md) retains
populations, hardware and limits. [GazePrompt](https://arxiv.org/html/2402.12772)
supports line-navigation assistance in desktop low-vision reading;
[Vatány et al.](https://www.visible-language.org/journal/issue-60-1-highlighting-techniques/)
supports higher perceived-attention ratings for sentence/paragraph hover conditions,
without detected speed/comprehension gains. [EyeBookmark](https://idclab.skku.edu/assets/EyeBookmark.pdf)
supports sentence/block context after imposed interruptions. None validates this
automatic phone selector, gray opacity or improved reading outcomes.

[Apple Books' tap-revealed controls](https://support.apple.com/guide/iphone/read-books-iphc1af7c57/ios)
and [Material bottom sheets](https://github.com/material-components/material-components-android/blob/master/docs/components/BottomSheet.md)
are UI precedents, not evidence of reading benefit.

## Verification

Focused source review found and resolved two issues: the options dialog needed
the same capture gate, and hit tests needed screen-coordinate conversion. The
final review has no actionable P1/P2 finding. JVM fixtures cover segmentation,
null-word selection, ambiguity, exclusivity, generation/range changes, guide
cooldown, menu boundaries and trace provenance. Android fixtures cover menu
choices, gray text-background appearance, replay and selected-guide demo.

The first build attempt stopped before compilation at a sandbox-denied Gradle
cache lock. The owner approved a retry with cache access. That run compiled the
app and Android fixtures; 113/114 JVM checks passed, with one assertion still
expecting the previous demo guide strength. It was corrected to the current
0.25 baseline. The owner approved the next retry: **all 114 focused JVM checks
pass**, both debug APKs assemble, and `git diff --check` passes. Logs, original
failed XML, corrected XML and APK hashes are retained in
`output/guide-menu-check-01/summary.json`. Android fixture compilation is not
phone execution; body gestures, window insets and actual menu lifecycle remain
device checks. Software checks do not establish gaze accuracy or reading benefit.
No installation, camera/live gaze, human validation, staging or commit ran in
that automated gate. The owner later reported running the app successfully,
then requested the fixed button; this report is not a formal validation result.
The fixed-button/dictionary gate is recorded separately in
`output/fixed-aids-dictionary-check-01/summary.json`: 119 focused JVM checks and
both APK assemblies pass after an owner-approved test-compile correction. Review's
diagnostic-dot exclusion finding is corrected and source-confirmed. No device
execution covers these latest changes.

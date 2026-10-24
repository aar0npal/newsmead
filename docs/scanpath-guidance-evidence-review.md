# Guidance unit: evidence and phone design constraints

2026-10-13 · owner challenges line highlighting as the default and asks about
sentence guidance. Owner also requires avoiding heavy dependence on word-level
gaze accuracy. The original review below precedes implementation.

**Implementation amendment:** owner approved the menu and sentence prototype.
[Current contract](reading-aid-menu-and-sentence-guide.md) describes coarse line
selection, paragraph fallback/abstention, fixed gray baseline, versioned traces
and verification. The 20% entry pulse and word-step release are not implemented.
Prior installed V3/device results do not evaluate these new V4 guides.

## What the studies establish

| Primary study | Population / apparatus / intervention | Supported finding and limit |
| --- | --- | --- |
| Wang et al., *GazePrompt* (CHI 2024), [primary paper](https://arxiv.org/html/2402.12772), sections 3.1.1, 4.3.2 | Controlled study: 13 low-vision adults, desktop Tobii Pro Fusion 120 Hz. Reader-selected line highlight or arrow; follow supported line and cue line transitions. | Line-switching support shortened switching time; accuracy did not significantly improve. Highlight/arrow effects were not independently randomized. Supports spatial placekeeping, not our opacity pulse, older-phone-reader benefit or general comprehension improvement. |
| Vatány et al. (2026), [official primary article](https://www.visible-language.org/journal/issue-60-1-highlighting-techniques/), [PDF](https://journals.uc.edu/index.php/vl/article/download/8818/7090/13184), *Visible Language* 60(1), 58–83 | 80 regular digital readers, ages 18–75; severe reading difficulties excluded. MacBook, mouse/trackpad hover, short informational texts. Compared static text and line/sentence/paragraph selection; nonselected text at 30% opacity. Methods pp. 63–67. | No significant speed/comprehension differences detected. Sentence/paragraph conditions received higher perceived attention-sustainment ratings than static text. Subjective rating is not measured attention or performance. No gaze, mobile, adaptive intensity or difficulty trigger tested. Do not copy its text dimming as evidence for gray background opacity. |
| Jo, Kim & Seo, *EyeBookmark* (CHI 2015), [author PDF](https://idclab.skku.edu/assets/EyeBookmark.pdf), Figure 1 p. 2, Table 1 p. 3, results p. 4 | Main study: 13 young Korean university adults, Tobii X60/desktop/chin rest; imposed video interruption. Highlighted interrupted sentence or nearby block plus last-location marker. | Sentence/block cues shortened resumption lag; no significant comprehension improvement. Sentence cue preferred to block. Does not test a continuous reading guide or show superiority to current-line highlighting. |

The newer hover study's later PDF pages timed out during retrieval. Methods and
official abstract/implications were checked; exact final-table confidence
intervals and direct sentence-versus-line pairwise significance remain unresolved.
The official abstract supports sentence/paragraph versus static attention ratings,
not a claim that sentence highlighting improves speed or comprehension.

## Judgment

Line guidance has a defensible navigation task: indicating the supported row and
assisting line changes. It is not established as the best default for silent
phone news reading. The proposed entry-strength change, word-step release and
8%/20% gray opacity were explicit engineering hypotheses, not source-established
benefits. Select the guidance unit before further tuning those choices.

Sentence/paragraph context guidance is a credible alternative to prototype and
compare with line guidance and no guidance. A sentence can span several physical
lines; the same selected text unit can remain emphasized across those rows.
This reduces unit changes caused by wrapping, but reduced distraction and better
reading are hypotheses, not inferred outcomes.

## Phone constraint and proposed route

1. Derive sentence/paragraph boundaries from the extracted article text, preserving
   its displayed offsets. Sentence segmentation needs language-aware handling of
   abbreviations, punctuation and quotation marks; it is not a gaze measurement.
2. Match those text ranges to rendered line/region rectangles. Use only supported
   coarse region evidence to choose a guidance unit; no exact fixated word is
   required for this route.
3. A sentence may share a physical line with another sentence. If the supported
   region is compatible with both, do not guess a sentence. Use their containing
   paragraph/context region if identifiable, or withhold automatic guidance.
   Sentence units are not automatically easier to identify than physical lines.
4. Keep the cue on the selected unit across its wrapped rows. Change it after
   supported entry into another unambiguous unit. Uncertain tracking/geometry
   invalidation withholds or clears the gaze-anchored cue.
5. Core activation/release must not require a correct word target, a word-to-word
   saccade, a word duration or a P01/P02 word event. The prior proposed word-step
   release is withdrawn under the owner's accuracy constraint.
6. Retain line guidance as a navigation option. Include sentence/paragraph forms
   and no guidance as distinct inspectable conditions rather than declaring a
   universal winning form from unlike studies. Keep unrelated aid rules separate.

The sentence/paragraph targeting, ambiguity fallback and release route are design
inferences. They still require implementation, causal replay/source review and
subsequent phone/human checks. A larger region can tolerate some coordinate
uncertainty; it does not establish that phone gaze is accurate enough for it.

## Current status

The line-entry/8%–20% proposal is under reconsideration. Its word-based release
is withdrawn. The installed app still uses the existing yellow current-line
guide at fixed adaptive baseline; dictionary, focus and bookmark are unchanged.
No build, device install, camera/gaze collection, staging or commit ran for this
review. Prior software/device results do not evaluate these alternative guides.

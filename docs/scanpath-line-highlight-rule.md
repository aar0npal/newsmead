# Proposed independent line-highlight rule

**Reconsidered on 2026-10-13:** owner questions the line-only choice and requires
less reliance on word-level gaze accuracy. The word-step release below is
withdrawn. Entry modulation and 8%/20% opacity remain unvalidated candidates;
the [guidance evidence review](scanpath-guidance-evidence-review.md) now governs
the design direction. This file preserves the challenged proposal, not an
accepted implementation contract.

2026-10-13 · owner clarified that A1 must have its own rule. This supersedes the
coordinated line-support proposal. **Design only:** the installed A1 still uses
the fixed adaptive baseline; no policy/APK/device change was made here.

## Sole task

Emphasize the supported current reading line, with temporary extra prominence
when entering a different line. The controller does not infer comprehension or
classify that transition as difficulty.

## Proposed deterministic behavior

1. Once a visible current line is supported, show the 8% opacity gray baseline. An initial
   line after startup/reset also begins at baseline; no missing predecessor is
   invented.
2. Two fresh supported locations in the same uninterrupted segment and unchanged
   rendered geometry establish a line change when their line indices differ.
   Normal next-line movement, earlier-line return and later-line landing qualify.
   A raw row wobble or comparison across missing input does not qualify.
3. On that confirmed change, highlight the observed destination line at 20%
   opacity, subject to the reader's cap. Do not predict a line or point back at
   the source.
4. The first subsequent supported forward transition to a different word on that
   destination line (P01/P02) ends the entry emphasis. Same-word residence and a
   backward transition do not satisfy that release condition. Forward movement
   is an observed progress cue, not proof of comprehension.
5. Entry emphasis also ends at the existing 1500 ms transient-action lifetime,
   measured from entry onset and not extended by repeated same-word callbacks.
   If the line still has fresh support, return to baseline; otherwise withhold.
   This fallback bounds prominence when word resolution/progress is unavailable.
6. The next confirmed line change starts a new entry response. Tracking loss,
   target uncertainty and layout/lifecycle/consultation invalidation clear A1 and
   its comparison/entry state. Recovery starts at baseline; no gap-bridged jump.

## Exact proposed appearance

Color: neutral gray `#808080`. State percentages refer to actual overlay opacity,
with a default line-opacity cap of 20%, rather than the current generic strength
scale. A lower reader cap applies to both states. Fixed/manual mode keeps the
reader-selected opacity; disable/zero suppresses A1. These settings supersede
the prior proposed 25% baseline / normalized-reader-cap entry mapping.

| State | Nominal opacity | Transparency | Android 8-bit alpha |
| --- | --- | --- | --- |
| Same-line reading / initial support | 8% | 92% | 20/255 (7.84% after rounding) |
| Confirmed line entry | 20% | 80% | 51/255 (20%) |
| Disabled / uncertain target | 0% | 100% | 0/255 |

Retain the existing text size, wrapping and line geometry. The fill should be
behind the letters so changing it does not tint the glyphs; the existing overlay
draws over the TextView, so this layering requirement is also unimplemented.
Check readability and cue visibility in Light/Dark/Sepia before choosing the
final default. Gray is a candidate preference, not universally less distracting.

Current installed color is RGB (244,211,94), `#F4D35E`, with alpha
`floor(80 * normalized strength)`. Consequently the existing 25% strength is
20/255 = 7.84% opacity, not 25% opacity; maximum strength is 31.37% opacity.
This proposal uses an explicit opacity specification to remove that ambiguity.

| Supported observation | Proposed A1 response |
| --- | --- |
| Line 4, words 2 → 3 → 4 | 8% opacity gray highlight on line 4 |
| Line 4 → line 5, first supported word on line 5 | 20% opacity gray highlight on line 5 |
| Line 5, word 1 → word 2 | Return to 8% opacity gray highlight on line 5 |
| Line 5 → line 3 after the new line is supported | 20% opacity gray highlight on line 3 |
| Stay on a word, or repeat raw coordinates | No accumulating strength increase or entry renewal |

A1 does not consume P11 cycles, slow-frontier flags, word-definition activation,
focus actions or bookmark actions. Other aid lifecycles can suspend all article
interpretation, but do not supply its trigger or strength.

## Evidence relationship

[GazePrompt primary paper](https://arxiv.org/html/2402.12772), sections 3.1.1 and
3.2.3, supplies the line-following, immediate next-line cue and stabilized
jump-target precedent. Section 4.2.1 measures successful switching using a landing
followed by non-backward movement. Section 4.3.2 reports shorter switching time
with line support, without significant switching-accuracy improvement: 13
low-vision adults, desktop Tobii Pro Fusion at 120 Hz. It did not test a boosted
entry phase, this word-transition release or an intensity curve. Its fixation
votes and pixel thresholds cannot establish phone accuracy.

Sections 3.1.1 and 3.3 provide color customization and report yellow-related
eye-strain feedback during formative iteration. They do not establish that gray
is universally preferable or supply an opacity prescription.

Neutral gray, 8%/20% opacity and 1500 ms bounded display are
NewsMead prototype settings. This proposal supplies an independent navigation
response; it does not satisfy a claim of validated difficulty-to-strength dosing.
Font/layout changes reset the state. Progress release uses distinct word order;
the timeout is a UI bound rather than a universal reading-time threshold.

Before implementation: version the rule/form/profile, trace entry/release reasons
and supporting source/destination/progress evidence, add focused causal replay
and uncertainty/manual/timeout cases, and obtain substantive source review.
Participant evidence is needed for the added intensity behavior and phone use.

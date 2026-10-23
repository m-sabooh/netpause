# Pulse motion study QA

This is a proposed motion direction for the existing Pulse exploration. It does not establish native Android requirements.

## Evidence

- Source: `../28-pulse.html`, unchanged.
- Implementation: `../28-pulse-motion.html`.
- Captures are under `C:/Users/OdysseyROG/AppData/Local/Temp/`.
- Full-view source: `pulse-source.png`. Desktop study: `pulse-motion-final.png`, at 1400 x 1100 CSS pixels.
- Matched app comparison, opened together: `pulse-source-normalized.png` and `pulse-motion-normalized.png`. Both app captures are 388 x 758 pixels, at device scale factor 1. Source viewport and implementation iframe viewport are also 388 x 758 CSS pixels. No density adjustment was needed.
- Focused states: `pulse-motion-active.png`, `pulse-motion-arabic-390.png`, `pulse-motion-arabic-active-320.png`, and `pulse-motion-duration-320.png`.
- Mobile viewports: 390 x 1000 and 320 x 1000 CSS pixels.

## Fidelity

- Fonts: original system-ui app typography, weights, and hierarchy retained. The surrounding playground uses a bold system-ui title to fit Pulse.
- Spacing and layout: source status bar, app bar, split Home card, rows, fields, and full-screen navigation retained. Timer and Home card columns stack when the iframe is 360 pixels or narrower to prevent cramped content. The study frame follows the source's 16-pixel corner radius.
- Colors: original plum ink, magenta accent, pale pink background, and white surface retained. Confirmation rings use the source accent.
- Assets: source numbered device glyphs retained. No new image or icon dependencies.
- Copy: original app labels retained. Study instructions sit outside the app. Countdown initially displays the actual deadline instead of the source's temporary static 19:42 placeholder.

## Findings and fixes

1. P2: the source allows actions during a refresh because refresh does not set its pending flag. The adapter blocks app actions, language changes, and reset while a check is in flight. Verified by conflicting-request assertions.
2. P2: duplicate Home redraws in the source cancelled the resume animation. Identical redraws now retain the existing screen and its animation. Verified immediately after confirmed resume.
3. P2: programmatic heading focus produced a visible default outline on noninteractive titles. Removed that outline only from those headings. Interactive focus outlines remain intact.
4. P2: the active timer's split columns became cramped on small screens. The study stacks those columns at narrow widths. Post-fix evidence: `pulse-motion-arabic-active-320.png` and a check for horizontal overflow.
5. P3: the square playground frame conflicted with Pulse's rounded phone surface. The outer frame now follows the source radius.

No remaining actionable P0/P1/P2 findings.

## Verification

- `../check-pulse-motion.js`: 23 assertions passed with normal motion and 23 with system reduced motion. Normal-mode checks were also rerun at 320 pixels after the final visual fixes.
- Covers presets, custom minutes, invalid duration, pending/confirmed pause and resume, replacement cancellation, refresh conflicts, Arabic layout and mirrored motion, keyboard focus, Escape navigation, motion-off mode, system reduced motion, and effect cleanup.
- Browser clicks exercised study controls, language switching, and primary app flows. Console and page-error checks were clear.
- No horizontal overflow in the study or app during the checked 390-pixel duration and 320-pixel active states.
- No Android code, existing Stillwater work, original Pulse source, or real router configuration was changed.

Native Android motion, real haptics, physical-device performance, and screen-reader behavior were not tested.

final result: passed

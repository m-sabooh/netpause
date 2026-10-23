# Stillwater with Pulse colors

Source visual truth: `../11-stillwater.html` and `../11-stillwater-motion.html`, with colors from `../28-pulse.html`. Implementations: `../11-stillwater-pulse.html` and `../11-stillwater-pulse-motion.html`.

Captures are under `C:/Users/OdysseyROG/AppData/Local/Temp/`:

- Matched desktop comparison: `stillwater-before-colors.png` and `stillwater-pulse-desktop.png`, opened together at 1400 x 1100 CSS pixels, scale factor 1. State: English, Home, idle. The iframe app region is 388 x 766 CSS pixels in both captures. No density normalization was needed.
- Focused Arabic duration sheet: `stillwater-pulse-arabic.png`, at 390 x 1000.
- Full board widget states: `stillwater-pulse-widgets.png`, at 1400 x 1100.

## Fidelity and checks

- The palette baseline retained Stillwater's typography, spacing, layout, radii, icons, components, and motion. At that stage, normalized CSS and scripts matched the source. The later user-requested bottom-bar removal is documented below.
- Main ink is Pulse plum `#361828`, muted ink `#755466`, and accent `#9b2459`. App surfaces use `#fffaff` and soft cards use `#f7ecf0`. Dividers and overlays use matching plum tints. No original teal remains.
- App copy and existing Lucide assets are retained. Only exploration headings and descriptions identify the combined direction.
- Contrast: main text on canvas 15.45:1; muted text on canvas 6.33:1 and soft cards 5.66:1; white text on magenta 7.53:1; magenta text on soft cards 6.53:1.
- Existing Stillwater flow checks passed: 19 with normal motion and 19 with system reduced motion, including Arabic RTL and mirrored navigation.
- All eight widget specimens use the new palette. Setup, settings, active-pause, replacement, and duration screens receive the same shared app colors.
- No horizontal overflow in the checked Arabic mobile sheet. Browser page-error check was clear.
- Original Stillwater and Pulse files remain unchanged. The selected visual direction is recorded in `docs/project-context.md`.

No actionable P0/P1/P2 findings. No layout fixes were needed during this palette-only pass.

## Bottom navigation removal

- The owner requested removal of the redundant bottom navigation. The shared frame now renders the top bar and content only; callers and 13 unused bottom-navigation CSS rules were updated. The motion study's navigation instructions now point to the top back arrow.
- Compared `stillwater-pulse-desktop.png` and `stillwater-pulse-no-bottom-nav.png` together at 1400 x 1100. The content area uses the released height without adding other controls or changing the remaining components.
- Arabic mobile evidence: `stillwater-pulse-no-nav-arabic.png`, at 390 x 1000. No visible layout defects or horizontal overflow.
- All 19 existing flow checks pass. Additional browser checks confirm that the bar is absent, the top Settings button works, the top back arrow returns to Home, and content fills the available height in English and Arabic. Browser page-error check was clear.

Native Android implementation, haptics, physical-device performance, and screen-reader behavior were not tested.

final result: passed

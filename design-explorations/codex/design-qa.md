# Stillwater motion study QA

This evaluates the proposed motion study, not a confirmed Android design decision.

- Source visual truth: `11-stillwater.html`, unchanged. Captured at `C:/Users/OdysseyROG/AppData/Local/Temp/stillwater-source-normalized.png`.
- Implementation: `11-stillwater-motion.html`. Desktop capture: `C:/Users/OdysseyROG/AppData/Local/Temp/stillwater-motion-final.png`.
- Full app comparison: source capture above and `C:/Users/OdysseyROG/AppData/Local/Temp/stillwater-motion-phone.png`, opened together. Both captures measure 388 x 766 pixels, with CSS viewport 388 x 766 and device scale factor 1. No density normalization was needed. The motion study adds its own surrounding presentation border.
- State: English, online, home route, no active pause, animations settled.
- Desktop viewport: 1400 x 1100. Responsive checks: 390 x 1000 and 320 x 1000.
- Focused evidence: `C:/Users/OdysseyROG/AppData/Local/Temp/stillwater-motion-duration.png`, `stillwater-motion-arabic.png`, `stillwater-motion-arabic-fixed.png`, and `stillwater-motion-320.png` in the same directory.

## Fidelity

- Fonts and typography: original Segoe UI/Arial stack, weights, sizes, hierarchy, and English/Arabic wrapping retained inside the app.
- Spacing and layout rhythm: app content retains the source padding, rows, cards, navigation, and sheet geometry. Additional desktop/mobile layout belongs to the surrounding motion playground.
- Colors and tokens: original teal, ink, soft surface, and borders retained. The temporary ring and press ripple use the existing accent/current text colors.
- Image quality and assets: existing licensed Lucide SVG assets reused. No replacement artwork or new image assets.
- Copy and content: original app copy retained. Proposed motion descriptions and controls appear outside the app.

## Comparison history

1. P2: resizing an already-open sheet left the selection highlight at its old width. Visible in `stillwater-motion-mobile.png`. Fixed by recalculating its position and width on iframe resize. The 320-pixel capture and Arabic post-fix capture show the corrected layout; an assertion also verifies width and position after resizing.
2. P2: on mobile, selecting a motion recipe below the preview would trigger motion outside the visible area. Fixed by bringing the preview into view before starting the demonstration. Verified through browser clicks at 320 pixels.

No remaining actionable P0/P1/P2 visual findings.

## Verification

- `check-stillwater-motion.js`: 19 checks pass with normal motion and 19 with system reduced motion enabled.
- Covered: sheet focus, presets, indefinite/timed mode, pending versus confirmed pause, repeated-request guard, replacement cancellation, confirmed resume, Arabic RTL, mirrored navigation, selection position after resizing, Escape dismissal, Still mode, reduced motion override, and animation-element cleanup.
- Browser-rendered primary interactions were exercised with clicks. Browser console and page-error checks reported no errors.
- No horizontal page or iframe overflow at 390 and 320 CSS pixels.
- Source file remains unchanged. Router behavior remains simulated. Native Android animation, real haptics, screen-reader behavior, and physical-device performance were not tested.

final result: passed

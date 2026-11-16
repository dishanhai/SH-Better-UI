# Transactional native color editor

A reusable HSV + alpha control, inspired by MuoniumPlayer's secondary theme palette editor. It is available at **Developer panel → Controls → Color editor**, and is actually used by **Appearance → effect configuration → a color field** for ARGB colors, palette entries and gradient-stop colors when at least 180 × 132 GUI units fit. Small windows and RGB-only fields retain the existing numeric channel controls.

```java
var editor = ShBetterUiApi.controls().colorEditor(
    new Bounds(x, y, 240, 132), Component.literal("Accent"),
    savedArgb, resetArgb, preferences, this::previewColor, this::commitColor);
addRenderableWidget(editor);
```

## Input and state

- Drag a visible ramp to preview; release commits once. Captured releases outside the ramp clamp to its range. The host must forward captured drag/release events.
- Use Up/Down to choose Hue, Saturation, Brightness, Opacity or Reset. Left/Right adjusts, Shift changes by larger steps, Home/End goes to either bound, Enter confirms. Escape cancels the current uncommitted edit; focus loss/removal also restores the previous accepted color.
- Alpha is editable and shown over a checker. The hexadecimal value and narrated channel/value remain available; black/white handles and focus underlines are not color-only feedback.
- Hue remains stable while saturation/brightness is zero, allowing black/grey colors to be recolored predictably.
- Reset restores the host-supplied reset color. In the effect editor this is the color at entry; the existing parent Inherit/Reset controls still provide template/default restoration.
- `onPreview` must be reversible and must not persist data. `onCommit` accepts the gesture, but the host decides when to save. The theme editor continues to save only through its existing outer Done workflow.
- Call `commitInteraction()` before an explicit Done action changes focus. Call `cancelInteraction()` when suspending/removing. `setValue()` replaces external state without callback echoes. `close()` is final.

No new texture, shader, network request or continuous animation is needed. Ramps/checkers have bounded draw budgets and the control has no animated behavior to disable.

## Source and licensing

Design reference: [MuoniumPlayer ThemePaletteOverlay](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/9b4cc7e7bd143315da716075a6d024a0d4c0c81b/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/ThemePaletteOverlay.java), Apache-2.0, at the already recorded reference revision. This is original UUK interaction/layout code, not copied upstream widget code or assets. HSV conversion reuses SHBUI's existing `HsvColor` utility, whose existing Link-Lyric copyright/license notices remain bundled unchanged. No new external resource is incorporated.

## Scope

Forge 1.20.1 and NeoForge 1.21.1 are the target builds. Experimental 26.x has its own native controls/editor and is not extended here. The music/artwork/lyric implementation from PR #16 is unchanged. Automated model, actual-widget and host-mapping tests do not replace real Minecraft GUI-scale/narrator acceptance.

# Registered-theme palette editing

Open the control panel's theme configuration, then **Colors**. The first row selects a registered theme, including the Muonium glass presets; the original custom palette remains a separate choice. Each registered theme has its own five RGB overrides. Switching themes must restore its own overrides, not copy another theme's colors. Selecting a preset does not replace its native material with a flat palette.

Each color row keeps its hex field and empty-value inheritance, and adds a named swatch/arrow entry. This opens an isolated RGB palette page with hue, saturation and value ramps, hex input, and a live background/panel/text/accent sample. No alpha channel is offered: the source theme's opacity/material stays inherited. Smaller windows retain hex input and the same explicit transaction buttons.

- Dragging/keyboard movement previews only the child draft.
- Releasing a gesture accepts the local candidate, not the parent or saved settings.
- **Inherit** stages an empty value. It does not flatten the source theme to its current resolved RGB.
- **Confirm** returns that field to the parent draft. **Cancel** discards the child edit.
- Escape during a gesture cancels that gesture first. Escape again leaves without applying the child.
- Leaving, focus transfer or resizing cancels an unfinished gesture; off-widget pointer release is forwarded to clear capture.
- Parent **Done** returns the whole theme draft to its host. Only the outer control panel's **Apply** persists it.

Tests cover isolated RGB transactions, inheritance, preservation of other fields, input capture, focus/escape/removal, single explicit delivery and entering registered presets without losing the previous custom palette. Actual Minecraft client checks supplement the automated tests below; narrator and hardware-GPU performance acceptance remain outstanding.

## Verification (2026-11-16)

34 new regression tests cover RGB controls, per-theme persistence/legacy migration, material/scope preservation, runtime filtering, editor transactions and background routing. Final `test kernelTest build verifyLibraryJar` passed on Forge 1.20.1 (1395 tests, 4 existing conditional skips) and NeoForge 1.21.1 (1407 tests, 4 existing conditional skips); each kernel suite passed 504 tests. Independent static review found no remaining blockers. A real NeoForge 1.21.1 client was run on the dot Linux desktop (1180×812, Chinese, GUI scale 2; scale 3 also exercised before the final fixes). The session verified registered-theme switching, transparent material, RGB dragging/cancel/confirmation, outer Apply, persisted theme/background after restart, and the black-hole background/parameter page. F2 screenshots were captured directly in game. This exposed and fixed an opaque-backdrop contrast exception and stale-frame accumulation in the translucent palette child; four additional regression tests cover these paths. The client used Mesa llvmpipe software OpenGL 4.5, so no hardware-GPU performance claim is made. Narrator, Forge visual acceptance, full GUI-scale matrix and experimental 26.x remain untested. That acceptance run did not modify the separate music-account worktree. The subsequent control-center integration retains those originals and incorporates copies on its own branch.

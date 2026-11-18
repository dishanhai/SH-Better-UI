# Display style suites

Four original, asset-free suites share one renderer-independent theme registry across
Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2: Porcelain, Midnight, Jade and Warm Paper.
They include surface/foreground hierarchy, primary/focus/disabled feedback, interaction
state overlays, geometry and motion tokens. Renderers consume their supported tokens;
this does not replace every native Minecraft widget or promise identical platform layouts.
Existing classic layouts, music layouts, glass themes and player font selections remain independent.

On 1.20.1/1.21.1 open Themes. Suites are first in the registry gallery; Preview opens
interactive local buttons and a sample slider without applying the draft. Apply/Undo/Cancel
keep the existing theme transaction. On 26.1.2 open Theme → appearance editor, choose a
suite, Preview its interactive states, then Apply. Native26 now resolves saved built-in
registered palettes rather than merely retaining their IDs. Each suite keeps its own five
custom RGB overrides; switching back restores them. Invalid color text cannot be silently
lost by selecting another suite. Reset can be undone. Unrelated HUD/background/font choices
are preserved. The preview is not song playback and changes no account or audio state.

The new suite definitions contain no external images or fonts. High contrast and reduced
motion remain user overrides. No third-party trademark artwork or proprietary fonts are bundled.

Unbound standard UUK buttons also consume suite state colors and corner radius in all
three versions. Explicit widget material/scope bindings and screen-local preview scopes
win over this fallback. Existing motion behavior remains authoritative; defining motion
and shadow tokens is not a claim that every old or native renderer consumes all of them.

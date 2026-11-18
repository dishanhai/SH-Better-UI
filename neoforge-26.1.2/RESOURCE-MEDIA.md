# Resource-pack rich media

This slice adds local resource-pack animated images to the native read-only rich
text document host. It depends on the reader introduced by the surfaces migration,
not on the later theme or font-management slices.

## Boundaries

Only texture identifiers inside resource packs are eligible. No arbitrary disk
paths, remote URLs, commands or embedded controls are introduced. Animated GIF,
APNG and supported PNG frame metadata reuse the existing bounded CPU decoders;
unsupported or failed resources retain document geometry and alternative text.
The editor's existing static-PNG path is retained for compatibility and is not
advertised as an advanced media authoring workflow.

Decoding runs asynchronously within byte, dimension, frame and total pixel limits.
Decoded pixels are owned by the reader until native texture preparation. GPU
uploads must happen before GUI extraction, not from worker threads or while
extracting draw commands. The host releases its requests/textures on closure;
resource reload invalidates pending and uploaded generations. Reduced-motion
mode uses the first animation frame. Media is requested for visible content rather
than eagerly decoding an entire document.

## Acceptance

The accompanying PR records decoder/owner lifecycle tests, legacy regressions,
resource reload and actual-game evidence. Included example artwork is generated
for this project. Still frames document appearance; they alone are not proof of
correct playback timing or hardware performance.

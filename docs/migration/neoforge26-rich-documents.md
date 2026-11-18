# Native 26 structured document reader

This is a passive reader, not the legacy rich-text Workbench/editor or its network synchronization.
Consumers pass their own immutable `RichDocument` to `RichDocumentsApi.screen(parent, title, document)`.
The returned Screen is not opened automatically.

Supported: styled text, paragraph breaks, current-language localized text, static resource-pack PNG images,
and resource-pack font IDs through `RichStyle.font`. Resource-pack fonts are **not** filesystem TTF uploads.
Images use the complete resource texture and their declared aspect ratio, fitted to the surrounding line height by the existing inline document layout (not full-size image blocks).
An absent/unsupported image keeps its layout rectangle and displays its alternative text. Object/extension
nodes become their alternative text, without controls, animation, commands or remote requests.

Images must have `textures/` paths ending in `.png`. Minecraft owns the borrowed resource textures;
closing this reader does not close global textures. Visible resource availability is checked again on each
extraction, so missing-image results are not permanently cached. Use Refresh document after replacing
font resources to remeasure text; language changes rebuild the view automatically. The refresh and resize
paths preserve scroll position, clamped to the new bounds.

The reader supports wheel, arrows, Page Up/Down, Home/End, native keyboard button traversal, Back/Escape,
copying the complete text projection (including image alternatives), and document narration.
Content is bounded to 8192 nodes, 131072 text/alternative characters, and 4096-pixel declared image axes.
No HTML, arbitrary local path, external download, media decoder or custom texture upload is introduced.

Headless tests cover resource-font conversion, wrapping, image geometry/alternatives, input scrolling,
resize retention and input bounds. Actual GPU images, font appearance and in-game accessibility still
require client visual verification; these tests do not claim screenshot coverage.

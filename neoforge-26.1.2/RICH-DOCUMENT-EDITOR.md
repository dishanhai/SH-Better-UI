# Native local rich-document editor

This increment is a local authoring surface, not the complete legacy media
workbench. `ShBetterUiApi.richDocuments().editor(parent, title, document)` creates
an editor without opening it or writing any file. The Tools page also exposes a
small example document. A caller can read the current committed `document()`.

The editor reuses the shared validated document transaction and property models,
with the native read-only document renderer as its preview. Document changes are
undoable. Unapplied field input is distinct from committed document state, so
resizing or switching views must not silently save or discard a draft.

Supported authoring is text/style, paragraph breaks and static resource-pack PNG
images. JSON import/export is explicit and local, using the editor/clipboard.
There is no arbitrary path access, remote media fetch, HTML execution or upload.
The shared codec's UTF-8 document and node budgets remain enforced. An image
preview fallback is never substituted into the saved document.

Other node types are preserved as data, rather than converted into preview text.
This does not add interactive-node execution, GIF/WebP decoding, audio/video,
remote URLs, an animation timeline or the full legacy editor's file workflow.
Those remain separate migration gaps. Caller-owned image/font resources retain
their existing resource-pack lifecycle.

Acceptance should cover property validation, apply/reset, selection and undo/redo,
JSON round-trip and invalid import, dirty-close cancellation, keyboard navigation,
resizing and narrow layout. In-game screenshots validate the native widgets;
headless tests alone do not establish visual quality or real media playback.

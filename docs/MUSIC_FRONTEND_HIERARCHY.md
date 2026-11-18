# Music frontend hierarchy

The existing player and original control-panel layout preference are retained.
This change reorganizes presentation, not music providers or playback ownership.

- The queue opens as a listening list. **Edit queue** opens its secondary editing
  view: reorder/remove rows, append the current track, clear, and undo remain
  available there. **Done**, Back, or Escape returns to the listening list without
  stopping playback or resetting its scroll. Entering/leaving the editor cancels
  pending control presses. The bottom transport remains the single previous/next
  playback control group; the old duplicate pair is removed.
- Page headings no longer repeat a brand and generic subtitle. Playlist rows show
  the name once with a track count beneath. Empty/error text is not repeated in a
  paging footer. Ordinary transport state stays in accessible tooltips; loading,
  errors and preview restrictions remain visible.
- Account guidance points to Library, not nonexistent buttons above it. Credential
  storage consequences remain in the account detail page. Queue scope remains
  available from the Edit queue explanation, not repeated above every song list.
- Native 26.1.2 text uses the same leading alignment, ellipsis and single-title
  hierarchy as the shared legacy view. No new bitmap/pixel icons are introduced.

No platform authorization, login, source, audio, credential or original panel-layout
setting is changed. This is a first focused frontend pass, not a redesign of every
HUD, preference screen or original layout.

## Validation

- NeoForge 26.1.2 full test: 874 cases, 5 conditional skips; kernelTest 541;
  verifyLibraryJar passed.
- Forge 1.20.1 and NeoForge 1.21.1 focused music tests: 288 cases each, 5 skips;
  kernelTest 541 each and verifyLibraryJar passed. Not full legacy suites.
- Regression coverage includes editor visibility, entry/Done/Back/Escape,
  armed-edit cancellation, viewport resize, playback/scroll preservation and
  disjoint controls at the minimum supported size. Independent static review
  found no confirmed blocking issue. No audio/provider changes are included.
- Actual NeoForge 26.1.2 client smoke check in a disposable world: opened music
  from the control panel, inspected search and empty-queue layouts, entered Edit
  queue, used Escape to return to the queue with focus on its Edit entry, then
  returned to the panel. Three raw F2 screenshots were captured. Normal shutdown
  succeeded. This run did not test signed-in libraries, populated queue editing,
  actual audio, other themes, or hardware performance; those must not be inferred
  from empty-state screenshots. Populated-queue behavior is covered by unit tests.

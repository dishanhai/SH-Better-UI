<!-- Author: dishanhai -->
# Native 26.1.2 surfaces, second migration increment

This increment depends on the repaired 26.1.2 music/page-input baseline in PR #41.
It is not a claim of full legacy UI feature parity. Existing Forge 1.20.1 and
NeoForge 1.21.1 implementations are not replaced.

## Native entry points

- Control panel Tools opens the actual online player, live-activity manager and a
  Markdown document example. The activity entry is disabled if the kernel is off.
- `ShBetterUiApi.settings().activityScreen(parent)` manages real current activity
  publications; it does not populate fake example activities.
- `ShBetterUiApi.settings().markdownScreen(parent, source)` displays caller-owned
  Markdown with native scrolling and keyboard link traversal.
- `ShBetterUiApi.markdown()` parses inline styling and the shared Markdown block
  document. `ShBetterUiApi.links()` accepts HTTP(S) destinations only, respects
  the game's link preferences and uses its native confirmation dialog.
- Modules consume `UIApis.WINDOWS`, `UIApis.DOCK`, `UIApis.ACTIVITIES`,
  `UIApis.NOTIFICATIONS` and `UIApis.LIVE_SURFACES` through the existing kernel
  permissions and scope ownership rather than a second standalone widget engine.

## Explicit limits

Markdown supports headings, emphasis, lists/tasks, quotes, code, tables and links.
Very narrow tables become labelled rows. Images remain alt text; no remote image,
video, embedded widget, HTML or command execution is silently enabled. This is a
native Markdown reader, not the full legacy rich-document/media workbench.

Activity presentation is a local compact/minimal HUD and a native management page,
not the complete animated island/music HUD or split layout. Dock supports native
module-owned Screens; HUD/persistent/glass/custom-indicator/spring windows are
explicitly rejected before allocation. See the focused activity and Dock documents
for supported controls and lifecycle details.

No actual in-game GPU, mouse/audio, account playback, screenshot or 26.2 acceptance
is claimed by headless tests. NeoForge 26.2 remains a separate compatibility task.

## Verified in this increment

NeoForge 26.1.2 full build passes with Java 25 / Gradle 9.2.1. Platform tests:
514 total, zero failures/errors, five existing skips. Shared kernel tests: 541,
zero failures/errors/skips. Production Kotlin/Java and native library-JAR contents
are checked. New tests cover activity authorization/lifecycle/minimal policy, Dock
ownership/permission/drag cancellation/focus restoration, Markdown parsing/reflow,
link glyph/emoji hit testing, scrolling and stale-screen close behavior. Independent
review corrected capture release, delayed focus, MINIMAL presentation and stale
link-confirmation callbacks before publication. These are headless tests, not
in-game visual acceptance. Legacy Java sources are unchanged in this increment;
only additive shared language keys affect the old versions.

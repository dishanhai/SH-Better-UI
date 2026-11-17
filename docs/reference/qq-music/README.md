# QQ Music-inspired web interaction reference

Open `index.html` directly in a modern browser. No build, package install, server,
credentials, media files, analytics, or network request is required. This is a
**local visual/interaction study, not a QQ Music client or audio player**. The
play button advances an explicitly simulated timeline; all song names and
artists are original placeholder data.

## Reference and original implementation

- User-supplied `IMG_1711.jpeg`: warm wooden listening room, vinyl turntable,
  large metadata and sparse outlined transport controls.
- User-supplied `IMG_1712.png`: translucent color field, left library navigation,
  song/artist rows, green active state and persistent bottom transport.
- Official QQ Music website, https://y.qq.com/ (reference checked 2026-11-17):
  search entry, music navigation and list hierarchy. Its code, branding and
  assets are not copied or redistributed here.

All artwork in this folder is original CSS geometry: mountain sleeve, black
vinyl, label, tonearm, metal chassis and wood-color background. No source image,
artist cover, logo, QQ mascot or proprietary font is bundled. Existing
repository licensing applies to this implementation, not to the reference
screenshots or external website.

## Interactions and accessibility

- Search filters title, artist and album; an empty state is explicit.
- The liked list and heart buttons update together; clicking again reverses it.
- Selecting a row changes metadata and resets the simulated progress.
- Previous/next wrap around the eight-item demonstration queue.
- Playback can pause; reaching the end stops instead of pretending to load audio.
- Native range control supports pointer and keyboard seek.
- Turntable mode preserves search, selection and playback; Escape returns to
  the list and restores focus to the mode button.
- Mobile starts in the turntable composition. Both modes remain accessible.
- Native buttons, focus rings, descriptive labels, live feedback and
  `prefers-reduced-motion` / forced-colors accommodations are provided.

## Verification

`node --check app.js` passed. `node interaction.test.cjs` checks the actual script's
state and event logic using a minimal in-memory DOM (not a rendering engine).
The script uses no external requests and no HTML injection of song metadata.

Headless Chromium rendering/interaction QA was attempted but blocked by this
executor's process-socket restriction (`socket() failed: Operation not permitted`),
including the permitted elevated retry. **Desktop/mobile screenshots and visual
layout verification have not been completed.** This is not a game screenshot.

Manual browser checks still required: 1360×800 desktop, 390×844 portrait,
200% zoom, keyboard-only navigation, filtering/empty results, liked-list removal,
repeated mode switching, seek drag, paused record motion and reduced-motion mode.

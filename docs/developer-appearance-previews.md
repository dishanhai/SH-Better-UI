<!-- Author: dishanhai -->
# Developer appearance previews

Open **Developer panel → Backgrounds** for a searchable gallery of every current
`ShBetterUiApi.backgrounds()` registration, including black hole and addon effects.
Each entry uses its actual background renderer, with its own lifetime. Only the
selected entry allocates a renderer. Switching entries, suspending a preview, or
leaving the gallery releases it. **Restart effect** resets that instance. Fullscreen
uses the existing gallery controls. High contrast hides decorative effects; reduced
motion is passed to the native renderer. No background selection is saved.

Open **Developer panel → UI themes** to cycle through every theme in the live theme
registry. The preview uses a separate theme runtime, preserving registered theme
inheritance, token extensions and styles. Its primary, secondary, disabled and selected
buttons are real interactive controls. Changing the selection does not change the
application's active theme or preferences. Resource reloads update the available list;
removing a selected theme falls back to the dark theme. Closing or suspending the scene
releases its scopes.

The background gallery is a live source of `PreviewSpec` declarations, not a second
hardcoded list. Registering/unregistering a background automatically adds/removes its
preview. `PreviewRegistry.registerSource` accepts stable, lazy descriptor lists and
returns a handle to remove the source. IDs must be unique across direct registrations
and live sources. Existing effect-parameter demonstrations remain in Materials;
dialog/toast code examples remain in Controls.

This implementation is shared by Forge 1.20.1 and NeoForge 1.21.1. The experimental
26.x renderer uses a separate developer panel and is not covered by this change.

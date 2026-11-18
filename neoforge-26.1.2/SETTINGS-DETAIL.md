# Native secondary settings pages

`ControlsApi.settingsDetailPage(title, sections[, actions])` now supplies a native
26.1.2 `UiSettingsDetailPage`. Its builder can still be passed to ScreenService.
Cards use shared SettingsDetailLayout geometry, clipped native controls and a
native scrollbar. Keyboard focus reveals fields synchronously. Resize and owned
confirmation return retain the edit session, scroll offset and field focus.
Very small windows show only Back and a resize hint instead of invalid controls.

## Consumer contract and compatibility

The Field, Section and Actions composition is retained. On 26.1.2 the field
factory receives `UiSettingsDetailPage.Context`, not legacy `UiApiContext`.
This deliberately small context offers controls, current preferences, font,
manage and onClose. It does not expose the unmigrated extension registry or theme
scope API. Resources belong to a single mounted set of controls: resize/reset
and modal return release the prior context, while the host-owned draft survives.
Do not store draft data in a per-mount context resource.

Actions owns validation and atomic persistence/merge. A null Apply result means
success and closes without Cancel; a message leaves the draft open. Reset is
local; consumers can implement Reset/Undo using its dynamic label. Cancel/Back
prompts before discarding a dirty draft. Returning from confirmation preserves
it; confirmed discard, external navigation or abandonment of the owned modal
cancels once. The native confirmation overload adds an abandonment callback;
existing confirmation signatures retain their behavior.

## Real use, not a demo setting

Control panel → Glass → Glass parameters now uses this composition instead
of the former ConfigPage. Three numeric sliders edit only local optics values.
Apply merges those three values into the latest parent draft, retaining all
other unsaved settings. Only the main control panel Apply persists to disk.
Reset/Undo remains local, and returned focus resolves the rebuilt entry button.

# Native accessibility semantics service

The NeoForge 26.1.2 kernel now publishes `UIApis.ACCESSIBILITY` with capability
`UIApis.MINECRAFT_NARRATION`. It reuses the shared AccessibilityService and
ClientAccessibility implementation unchanged. No new permissions or bypasses
are introduced: semantics can bind only to the caller's live tree/component.
Duplicate bindings are rejected; closing the subscription releases a binding,
and node/module teardown releases its supplier.

Suppliers are evaluated when semantics are requested, so updated names/hints
are reflected without rebinding. Call `narrate` from a widget's native narration
callback: it appends TITLE and, when nonempty, HINT to NarrationElementOutput.
Role remains semantic metadata, not an assertion of automatic role speech.
The service does not automatically narrate arbitrary nodes or modify voice,
audio-device, operating-system or Minecraft narrator settings.

Preferences compose current native settings with ThemeScope preferences. Null
or closed theme scopes use the current native preferences unchanged.

SYSTEM_SCREEN_READER is an identifier only and remains unavailable without a
provider. Tests inspect Minecraft's narration collector and scope lifecycle;
they do not claim that audible speech or a hardware screen reader was tested.

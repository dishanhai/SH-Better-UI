# Player control feedback

The native control preferences now persist hover animation, press animation,
selection pulse and tooltip delay (0–2000 ms). Existing configuration files use
the established defaults when these properties are absent. Disabled animation
does not remove the control's static hover, press or selected feedback. Reduced
motion remains the stronger preference.

The feedback editor owns a draft. Its real native button previews the draft
without changing global preferences. Apply saves only the feedback fields on
top of the current configuration; Cancel discards the draft. Reset is also a
draft edit and can be undone. Escape cancels an active slider edit first, then
asks before discarding other unsaved changes. Save failures leave the draft
available for correction or retry.

This is an independent main-based migration slice. When integrating with the
appearance/theme branch, retain both its theme persistence and these feedback
fields. Every `ControlPanelSettings.with*` method must preserve feedback unless
it explicitly edits it; the compatibility constructor supplies defaults and
must not be used to copy an existing player's configuration.

The PR records actual test and game checks. This does not claim that every
third-party widget consumes SHBUI preferences, or that software-rendered
screenshots establish hardware animation performance.

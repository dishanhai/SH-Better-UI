# Scoped animation and collapse services

This slice exposes the existing animation and collapse contracts to native
NeoForge 26.1.2 UI modules. It is separate from the convenience MotionApi and
from the Screen Dock implementation. Modules discover the services through
UIApis, declare the animation-property resource claim they use, and bind tracks
to an owned UI tree or component scope. Closing that scope releases its tracks.

Animation and collapse are sampled in that order during the kernel ANIMATION
phase, before native GUI extraction. The native collapse adapter reuses the
existing state machine while reading the native reduced-motion preference.
Spring retargeting retains motion continuity; ordinary timed transitions retain
the specified curve/duration. Zero duration and reduced motion reach the target
without a transition. Controlled progress, locking and state listeners follow
the existing API contract.

The accompanying PR records native module discovery, permission/ownership,
resource conflict, lifecycle and animation regressions, plus any actual-game
checks. A demonstration is not a hardware timing benchmark. This does not make
Dock SPRING/glass/HUD hosting supported, nor migrate optional mod integrations.

# Legacy Vulkan SVG compatibility

The Minecraft 1.20.1 and 1.21.1 builds do not ship a Vulkan world renderer.
SHBUI does not bundle one or require it for normal OpenGL use.

| Profile | Optional Vulkan host |
| --- | --- |
| Forge 1.20.1 | [VulkanForge](https://github.com/imjonasyt-byte/VulkanForge), 0.8 release |
| NeoForge 1.21.1 | [VulkanMod Reforged](https://github.com/TrulyRin/VulkanMod-Reforged), 0.3.5 alpha for 1.21.1 |

Both hosts create a GLFW no-client-API game window. Use that window to choose
the compatibility path rather than assuming that a loaded mod or a compatibility
OpenGL context is the actual game backend.

SVG parsing and physical-size caching remain the same. On a no-client-API
window, tinted icons, texture reveals and rich-media regions use a vanilla
textured GUI batch, and atlases upload through Minecraft's texture manager. The
legacy GL icon shaders and texture reveal blur are not ported by this change.
Framebuffer backdrop blur takes its existing non-blurred presentation;
Muonium's OpenGL Skija layer is unavailable. No claim is made about unrelated
graphics mods or shader packs.

## Release checks

For each loader, launch one ordinary OpenGL profile and one isolated profile
with the matching Vulkan host. Verify the active backend in the log before
attributing the result. Compare the same screen at GUI Scale 2 and 3: main
panel icons (tint/alpha), HUD icons (position/scale), focus/pressed states,
animated icon motion, the texture-reveal sample, and inline icons mixed with
text and animated media. Reload a resource pack with a clearly different
`sh_better_ui:textures/gui/display/info.png`, then remove it and verify the
bundled SVG returns. Confirm that closing and reopening the view does not
leak atlas textures, and explicitly record any failed or downgraded effect.

These are client checks, not implied by a green Gradle build.

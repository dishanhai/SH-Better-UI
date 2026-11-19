# UUK artwork stage

`kernel.api.music.presentation.MusicArtworkStage` is a host-owned reusable drawing
primitive. It does not inherit `Button` or `Screen`, open devices, request URLs,
read files, decode images, or own the supplied texture. Its lifetime can span
Now Playing and Lyrics without resetting the background.

```java
var stage = new MusicArtworkStage();
// Obtain this from the existing loader once, not by making another artwork request.
var texture = artwork.texture(track);
stage.renderBackground(frame, canvas,
        texture.map(OnlineArtworkTexture.Texture::palette).orElse(null),
        frame.nowMs(), lowPerformance);
texture.ifPresent(t -> stage.renderCover(frame, coverBox, t.id(), t.width(), t.height()));
// On host removal: stage.close(); artwork.close();
```

The texture is borrowed. `close()` resets only stage motion and does not release
an image or other host's resources. Missing artwork uses a darkened theme-color
fallback, never synthesized album artwork. Cover rendering preserves its actual
aspect ratio and sharp pixels, without a surrounding card or stroke.

`ArtworkPalette` and `ArtworkStageMotion` live in the game-independent kernel and
can be used by other renderers. `fromArgb` accepts an already sampled ARGB grid,
up to 4096 pixels. Extraction combines broad image regions with the overall
average, caps saturation and luminance, and preserves gray/neutral images. There
is no forced blue/pink fallback. Alpha-zero pixels do not contribute. The two
foreground constants in `MusicArtworkStage` are for the generated dark palettes;
high-contrast mode uses the host theme and its corresponding text color instead.

The default background is a shader-independent, fixed 8x6 interpolated color
mesh with slight low-frequency movement and a 650 ms interruptible palette
transition. No framebuffer blur or image readback is required. Low-performance
mode uses a static single color-interpolated quad; reduced motion snaps palette
changes and freezes movement; high contrast draws a solid theme background.

`OnlineArtworkTexture` creates its palette during the same existing worker-side
image decode. The palette and texture publish together, bound to the same
request generation; cancellation, replacement, close and resource invalidation
discard stale pairs. The patch adds no image downloads or disk cache.

For artwork that arrives asynchronously, the `renderCover(..., opacity)` overload
keeps the same fit and texture ownership. The online player blends from its
existing placeholder over 180 ms, including vinyl label art. It drops the prior
cover on replacement rather than painting a released texture. Reduced motion
and high contrast show the available cover immediately.

// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Experimental 26.x port: retain missing-cover UI until native GPU ownership is ported. */
public final class OnlineArtworkTexture implements AutoCloseable {
    public record Texture(Identifier id, int width, int height) {}
    public Optional<Texture> texture(OnlineTrack track) { return Optional.empty(); }
    public void invalidate() {}
    @Override public void close() {}
}

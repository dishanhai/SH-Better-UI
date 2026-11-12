// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import static org.junit.jupiter.api.Assertions.*;

class GlassSceneCaptureTest {
    @Test
    void opticalDepthUsesTheNativeModelViewAndGuiPose() {
        var view = new Matrix4f().translation(0, 0, -2000);
        var projection = new Matrix4f().ortho(0, 400, 300, 0, 1000, 3000);
        var content = new Matrix4f().translation(15, 20, 1);
        var overlay = new Matrix4f().translation(15, 20, 200);
        var tooltip = new Matrix4f().translation(15, 20, 400);
        assertEquals(-.001f, ClientGlassRender.clipDepth(content, view, projection), 1e-6f);
        assertEquals(-.2f, ClientGlassRender.clipDepth(overlay, view, projection), 1e-6f);
        assertEquals(-.4f, ClientGlassRender.clipDepth(tooltip, view, projection), 1e-6f);
    }

    @Test
    void fullResolutionIsReusedWhenItFitsAndLargeViewportsDownsampleUniformly() {
        var full = GlassSceneCapture.Size.fit(3840, 2160, 16384, 32L << 20);
        assertEquals(new GlassSceneCapture.Size(3840, 2160), full);
        var reduced = GlassSceneCapture.Size.fit(7681, 4321, 4096, 32L << 20);
        assertEquals(new GlassSceneCapture.Size(3841, 2161), reduced);
        assertTrue(reduced.bytes() <= 32L << 20);
    }

    @Test
    void budgetAndDriverLimitsAlsoApplyToExtremeOrEmptyViewports() {
        assertEquals(new GlassSceneCapture.Size(1, 16), GlassSceneCapture.Size.fit(1, 64, 16, 1024));
        assertEquals(new GlassSceneCapture.Size(1, 1),
                GlassSceneCapture.Size.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(0, 9, 16, 1024));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(9, 9, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(9, 9, 16, 3));
    }

    @Test
    void mipLevelsShareTheCaptureBudgetAndFollowDriverRounding() {
        var odd = new GlassSceneCapture.Size(9, 5);
        assertEquals(new GlassSceneCapture.MipChain(0, 180), GlassSceneCapture.MipChain.fit(odd, 211));
        assertEquals(new GlassSceneCapture.MipChain(1, 212), GlassSceneCapture.MipChain.fit(odd, 219));
        assertEquals(new GlassSceneCapture.MipChain(3, 224), GlassSceneCapture.MipChain.fit(odd, 224));
        var hd = GlassSceneCapture.MipChain.fit(new GlassSceneCapture.Size(1920, 1080), 32L << 20);
        assertEquals(10, hd.levels());
        assertTrue(hd.bytes() < 32L << 20);
        var uhd = new GlassSceneCapture.Size(3840, 2160);
        assertEquals(new GlassSceneCapture.MipChain(0, uhd.bytes()), GlassSceneCapture.MipChain.fit(uhd, 32L << 20));
    }
}

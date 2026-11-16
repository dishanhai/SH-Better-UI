package com.dishanhai.sh_better_ui.client.gui.display;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：图标效果是可选的，并与两个平台使用的核心 shader 资源一致。 */
class GraphicIconShaderTest {
    @Test
    void attachingAnEffectPreservesTheTextureAndLeavesTheOriginalUntouched() {
        var original = new GraphicIcon(GraphicIcon.REFRESH.texture());
        var shaded = original.withShader(GraphicIconShader.GRADIENT_SWEEP);

        assertNull(original.shader());
        assertEquals(original.texture(), shaded.texture());
        assertSame(GraphicIconShader.GRADIENT_SWEEP, shaded.shader());
        assertEquals(original, shaded.withShader(null));
    }

    @Test
    void eachPeriodWrapsAndReducedMotionUsesOneStablePhase() {
        for (long period : new long[]{2400, 3600, 2000, 1800}) {
            assertEquals(0.5f, GraphicIconShader.phase(new GraphicIconShader.Frame(0, true, 16), period));
            assertEquals(0.5f, GraphicIconShader.phase(new GraphicIconShader.Frame(1900, true, 32), period));
            assertEquals(0.25f, GraphicIconShader.phase(new GraphicIconShader.Frame(period / 4, false, 16), period));
            assertEquals(0.25f, GraphicIconShader.phase(new GraphicIconShader.Frame(period * 5 / 4, false, 16), period));
            assertEquals(0.75f, GraphicIconShader.phase(new GraphicIconShader.Frame(-period / 4, false, 16), period));
        }
    }

    @Test
    void builtInShadersReferenceTheirPackagedFragmentsAndSharedVertexStage() throws IOException {
        assertEquals(List.of(GraphicIconShader.GRADIENT_SWEEP, GraphicIconShader.COLOR_CYCLE,
                GraphicIconShader.ENERGY_WAVE, GraphicIconShader.PULSE, GraphicIconShader.HOLOGRAM,
                GraphicIconShader.GLITCH, GraphicIconShader.SCANLINE, GraphicIconShader.SPARKLE,
                GraphicIconShader.DISSOLVE, GraphicIconShader.OUTLINE), GraphicIconShader.BUILT_INS);
        String root = "/assets/sh_better_ui/shaders/core/";
        assertNotNull(GraphicIconShaderTest.class.getResource(root + "icon_gradient_sweep.vsh"));
        for (var effect : GraphicIconShader.BUILT_INS) {
            var id = effect.id();
            String path = root + id.getPath();
            try (var stream = GraphicIconShaderTest.class.getResourceAsStream(path + ".json")) {
                assertNotNull(stream, path);
                var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(GraphicIconShader.GRADIENT_SWEEP.id().toString(), config.get("vertex").getAsString());
                String fragment = config.get("fragment").getAsString();
                boolean rich = GraphicIconShader.BUILT_INS.indexOf(effect) >= 4;
                assertEquals(rich ? "sh_better_ui:icon_rich_effects" : id.toString(), fragment);
                assertEquals("Sampler0", config.getAsJsonArray("samplers").get(0).getAsJsonObject().get("name").getAsString());
                assertNotNull(GraphicIconShaderTest.class.getResource(root + fragment.substring(fragment.indexOf(':') + 1) + ".fsh"), fragment);
            }
        }
        assertNotNull(GraphicIconShaderTest.class.getResource("/assets/sh_better_ui/shaders/include/rich_icon_effects.glsl"));
    }
}

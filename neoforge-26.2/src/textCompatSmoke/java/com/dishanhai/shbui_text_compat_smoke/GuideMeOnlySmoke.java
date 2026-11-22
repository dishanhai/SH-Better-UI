// Author: dishanhai
package com.dishanhai.shbui_text_compat_smoke;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;

/** Actual official 26.2 GuideME document, native layout and transformed render path. */
@Mod(GuideMeOnlySmoke.ID)
@EventBusSubscriber(modid = GuideMeOnlySmoke.ID, value = Dist.CLIENT)
public final class GuideMeOnlySmoke {
    static final String ID = "shbui_text_compat_smoke";
    private static boolean pending;

    @SubscribeEvent
    public static void start(ClientStartedEvent event) {
        pending = true;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!pending) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        pending = false;
        client.setScreen(new Probe(client));
    }

    private static final class Probe extends Screen {
        private final Minecraft client;
        private final GuideMeCompatProbe guide = new GuideMeCompatProbe();
        private int drawn;

        Probe(Minecraft client) {
            super(Component.literal("Official GuideME 26.2 text compatibility"));
            this.client = client;
        }

        @Override protected void init() {
            guide.init(font, Math.min(220, width - 36));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xff111827);
            guide.render(graphics, 18, 30, Math.min(220, width - 36));
            if (++drawn == 12) client.execute(this::finish);
        }

        private void finish() {
            String evidence = "PASS GuideME 26.2.2-alpha; " + guide.evidence() + "\n";
            try {
                Files.writeString(client.gameDirectory.toPath().resolve("compat.txt"), evidence);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            client.stop();
        }
    }
}

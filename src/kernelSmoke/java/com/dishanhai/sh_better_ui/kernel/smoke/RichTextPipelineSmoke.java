// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.panel.RichTextPipelineScreen;
import com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncClient;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Outcome;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;

import static com.dishanhai.sh_better_ui.kernel.internal.richtext.network.NativeRichSyncServer.DEMO_VALUE;
import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Exercises native PLAY round trips and the packaged media/renderer on the actual Screen. */
final class RichTextPipelineSmoke {
    private static final UiId PULSE = UiId.parse("sh_better_ui:textures/gui/rich_text/pulse.gif");
    private static final UiId ABSENT = UiId.parse("sh_better_ui:textures/gui/rich_text/absent.gif");
    private static final UiId ANIMATION = UiId.parse("sh_better_ui:rich_text_pipeline/animation");
    private static final UiId MISSING = UiId.parse("sh_better_ui:rich_text_pipeline/missing");
    private static final UiId METER = UiId.parse("sh_better_ui:rich_text_pipeline/meter");
    private final Path output;
    private final HashSet<Integer> animationPixels = new HashSet<>();
    private RichTextPipelineScreen screen;
    private RichTextView view;
    private Rect meter;
    private Rect animationReservation;
    private int stage;
    private int firstFill;

    RichTextPipelineSmoke(Path output) { this.output = output; }

    boolean advance(Minecraft client, boolean reconnected) throws ReflectiveOperationException, IOException {
        if (screen == null) {
            ClientUiKernel.openRichTextPipeline(client);
            require(client.screen instanceof RichTextPipelineScreen, "The media/sync Screen did not open");
            screen = (RichTextPipelineScreen) client.screen;
            view = (RichTextView) field("view");
            return false;
        }
        require(client.screen == screen, "The media/sync Screen closed before verification");
        var state = (RichSyncClient.View) field("state");
        var presented = view.currentSnapshot().orElse(null);
        if (state == null || presented == null) return false;
        if (animationReservation == null) animationReservation = RichTextSmoke.fragment(presented, ANIMATION).bounds();
        require(animationReservation.equals(RichTextSmoke.fragment(presented, ANIMATION).bounds()),
                "Asynchronous media moved its layout reservation");
        if (state.status() != RichSyncClient.Status.LIVE || !presented.availableTextures().contains(PULSE)) return false;
        require(view.mediaStatus(PULSE) == RichTextView.MediaStatus.READY, "The decoded GIF did not become ready");
        if (view.mediaStatus(ABSENT) != RichTextView.MediaStatus.UNAVAILABLE) return false;
        require(!presented.availableTextures().contains(ABSENT), "The corrupt GIF was uploaded");
        if (reconnected) {
            require(energy(state) == 40 && state.lastOutcome() == null && !state.pending(),
                    "A new world retained the previous world's revision or energy");
            close(client);
            return true;
        }

        var bounds = RichTextSmoke.fragment(presented, METER).bounds();
        if (meter == null) meter = bounds;
        require(meter.equals(bounds), "A data update moved the reserved extension renderer");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int fill = RichTextSmoke.pixels(image, client, presented, meter, color -> color == 0xff9bd440);
            if (stage == 0) {
                require(energy(state) == 40 && state.writable(), "The initial server snapshot did not bind");
                require(fill > 100, "The scoped extension did not draw its server-bound fill");
                require(RichTextSmoke.pixels(image, client, presented, RichTextSmoke.fragment(presented, MISSING).bounds(),
                        color -> color == 0xff3d2b47) > 3, "The corrupt GIF did not keep its placeholder");
                for (int color : new int[]{0xffaef45a, 0xffffd05d, 0xff68caff}) {
                    if (RichTextSmoke.pixels(image, client, presented, animationReservation, pixel -> pixel == color) > 0) {
                        animationPixels.add(color);
                    }
                }
                if (animationPixels.size() < 2) return false;
                firstFill = fill;
                click("set_high");
                var predicted = (RichSyncClient.View) field("state");
                require(predicted.pending() && energy(predicted) == 40 && preview(predicted) == 65,
                        "A pending client prediction changed the authoritative value");
            } else if (stage == 1) {
                if (state.pending() || state.lastOutcome() == null || fill <= firstFill) return false;
                require(state.lastOutcome() == Outcome.ACCEPTED && energy(state) == 65 && preview(state) == 65,
                        "The authority did not accept and commit 65");
                image.writeToFile(output.resolve("rich-text-pipeline-accepted.png").toFile());
                click("request_over");
                var predicted = (RichSyncClient.View) field("state");
                require(predicted.pending() && energy(predicted) == 65 && preview(predicted) == 100,
                        "The rejected edit did not exercise client prediction");
            } else if (stage == 2) {
                if (state.pending()) return false;
                require(state.lastOutcome() == Outcome.DENIED && energy(state) == 65 && preview(state) == 65,
                        "The unauthorized 100% edit escaped server validation or correction");
                image.writeToFile(output.resolve("rich-text-pipeline-denied.png").toFile());
                close(client);
            } else {
                require(energy(state) == 65 && state.lastOutcome() == null,
                        "Reopening the view did not fetch the current server snapshot");
                close(client);
                return true;
            }
        }
        stage++;
        return false;
    }

    private void close(Minecraft client) {
        client.setScreen(null);
        require(!view.isActive() && view.currentSnapshot().isEmpty() && !ClientRichDocuments.hasActiveWatch(),
                "A removed Screen retained its media view or native subscription");
        screen = null;
        view = null;
    }

    private void click(String label) {
        var message = Component.translatable("sh_better_ui.rich_text_pipeline." + label);
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(child -> child.getMessage().equals(message)).findFirst().orElseThrow();
        require(screen.mouseClicked(button.getX() + 2, button.getY() + 2, 0), "Button did not handle input: " + label);
    }

    private Object field(String name) throws ReflectiveOperationException {
        var field = RichTextPipelineScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static long energy(RichSyncClient.View state) { return ((Number) state.authoritative().get(DEMO_VALUE)).number(); }
    private static long preview(RichSyncClient.View state) { return ((Number) state.displayed().get(DEMO_VALUE)).number(); }
}

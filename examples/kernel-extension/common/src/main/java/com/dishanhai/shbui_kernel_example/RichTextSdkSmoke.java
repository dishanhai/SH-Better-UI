// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Anchor;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.RichTextView.Presentation;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.shbui_kernel_example.ExampleSmoke.press;
import static com.dishanhai.shbui_kernel_example.ExampleSmoke.require;

/** Checks an external consumer's document and renderer through the native client. */
final class RichTextSdkSmoke {
    private static final UiId EXPORTED_INTRO = ExampleUiModule.id("export/intro");
    private static final UiId EXPORTED_DIAMOND = ExampleUiModule.id("export/diamond");
    private final Screen parent;
    private final Path output;
    private RichLayoutSnapshot initialLayout;
    private long updatedAfter;
    private long previousResource;
    private long previousRevision;
    private int previousScale;
    private CompletableFuture<Void> reload;
    private long beforeRendererFailure;

    RichTextSdkSmoke(Screen parent, Path output) {
        this.parent = parent;
        this.output = output;
        press(parent, ExampleClient.richTextEntry(parent).orElseThrow());
    }

    boolean advance(Minecraft client) throws IOException {
        require(client.screen instanceof RichTextSdkScreen, "The SDK entry did not open a native screen");
        var screen = (RichTextSdkScreen) client.screen;
        var view = screen.view();
        var presentation = view.currentSnapshot().orElse(null);
        if (presentation == null) return false;
        var project = screen.projectView().orElseThrow().document();
        require(project.documents().values().stream()
                        .anyMatch(document -> document.nodes().stream().anyMatch(node -> node.id().equals(EXPORTED_DIAMOND))),
                "The rendered guide was absent from the scoped project revision");
        var diamond = find(presentation, EXPORTED_DIAMOND);
        require(diamond.node() instanceof ImageNode image && image.intrinsicWidth() == 16
                && image.intrinsicHeight() == 16, "The packaged diamond lost its intrinsic size");
        var intro = find(presentation, EXPORTED_INTRO);
        var firstLine = presentation.layout().lines().get(0);
        require(firstLine.fragments().contains(intro) && firstLine.fragments().contains(diamond)
                        && diamond.bounds().width() == client.font.lineHeight
                        && diamond.bounds().height() == client.font.lineHeight,
                "The inline diamond did not follow the font size or stay on the first line");
        if (!presentation.availableTextures().contains(RichTextSdkScreen.DIAMOND_TEXTURE)) return false;

        var item = center(presentation, find(presentation, RichTextSdkScreen.ITEM).bounds());
        require(view.interaction().tooltip(item.x(), item.y())
                        .filter(label -> label.getString().equals(Component.translatable("item.minecraft.diamond").getString()))
                        .isPresent(), "The native diamond tooltip missed the painted item");
        var meter = find(presentation, RichTextSdkScreen.METER).bounds();
        var sample = presentation.localToGui().map(new Anchor(meter.x() + meter.width() * 0.375,
                meter.y() + meter.height() / 2.0));
        if (initialLayout == null) {
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                require(pixel(image, client, sample) == abgr(RichTextSdkScreen.METER_TRACK),
                        "The 25% energy meter painted past its expected fill");
            }
            initialLayout = presentation.layout();
            updatedAfter = presentation.frame().frameId();
            press(screen, screen.chargeButton());
            return false;
        }
        if (presentation.frame().frameId() <= updatedAfter) return false;
        if (reload == null) {
            require(presentation.layout().equals(initialLayout), "An energy sample changed the inline layout");
            checkVector(client, view, presentation, sample, "rich-text.png");
            previousResource = presentation.resourceVersion();
            previousRevision = project.stamp().revision();
            previousScale = client.options.guiScale().get();
            client.options.guiScale().set(client.getWindow().getGuiScale() == 1 ? 2 : 1);
            client.resizeDisplay();
            require(view.currentSnapshot().isEmpty(), "The SDK kept old vector hit geometry after resize");
            reload = client.reloadResourcePacks();
            return false;
        }
        if (!reload.isDone() || presentation.resourceVersion() <= previousResource) return false;
        reload.join();
        require(project.stamp().revision() > previousRevision, "The guide did not publish a new project revision on reload");
        var vector = center(presentation, find(presentation, RichTextSdkScreen.VECTOR).bounds());
        if (beforeRendererFailure == 0) {
            checkVector(client, view, presentation, sample, "rich-text-reloaded.png");
            beforeRendererFailure = presentation.frame().frameId();
            screen.vectorRenderer().close();
            require(view.hitTest(vector.x(), vector.y()).isEmpty(), "The closed renderer kept a live hit");
            screen.extensions().register(RichTextSdkScreen.VECTOR_TYPE, 1, context -> {
                var box = context.bounds();
                context.graphics().fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(),
                        RichTextSdkScreen.VECTOR_FILL);
                throw new IllegalStateException("Injected SDK vector renderer failure");
            });
            return false;
        }
        if (presentation.frame().frameId() <= beforeRendererFailure) return false;
        require(view.hitTest(vector.x(), vector.y()).isEmpty(), "The failed renderer published stale hit geometry");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixel(image, client, vector) != abgr(RichTextSdkScreen.VECTOR_FILL)
                            && pixel(image, client, sample) == abgr(RichTextSdkScreen.METER_FILL),
                    "The failed renderer leaked a queued draw or affected another inline object");
        }
        require(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0) && client.screen == parent,
                "Escape did not return to the parent screen");
        require(parent.getFocused() == ExampleClient.richTextEntry(parent).orElseThrow()
                        && !view.isActive() && !screen.extensions().isActive() && view.currentSnapshot().isEmpty()
                        && screen.projectView().isEmpty(),
                "The SDK screen did not restore focus and release its scoped registrations");
        client.options.guiScale().set(previousScale);
        client.resizeDisplay();
        return true;
    }

    private void checkVector(Minecraft client, RichTextView view, Presentation presentation, Anchor sample,
                             String screenshot) throws IOException {
        var vector = find(presentation, RichTextSdkScreen.VECTOR).bounds();
        var inside = center(presentation, vector);
        var outside = presentation.localToGui().map(new Anchor(vector.x() + vector.width() * .85,
                vector.y() + vector.height() * .20));
        require(view.hitTest(inside.x(), inside.y()).filter(hit -> hit.node().id().equals(RichTextSdkScreen.VECTOR)).isPresent()
                        && view.hitTest(outside.x(), outside.y()).isEmpty(),
                "Presented vector hit testing disagreed with the concave silhouette");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            require(pixel(image, client, sample) == abgr(RichTextSdkScreen.METER_FILL),
                    "The 50% energy sample did not reach the extension renderer");
            require(pixel(image, client, inside) == abgr(RichTextSdkScreen.VECTOR_FILL)
                            && pixel(image, client, outside) != abgr(RichTextSdkScreen.VECTOR_FILL),
                    "The concave vector pixels disagreed with its presented hit geometry");
            image.writeToFile(output.resolve(screenshot).toFile());
        }
    }

    private static Fragment find(Presentation presentation, UiId id) {
        return presentation.layout().lines().stream().flatMap(line -> line.fragments().stream())
                .filter(fragment -> fragment.node().id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing inline fragment " + id));
    }

    private static Anchor center(Presentation presentation, Rect box) {
        return presentation.localToGui().map(new Anchor(box.x() + box.width() / 2.0, box.y() + box.height() / 2.0));
    }

    private static int pixel(NativeImage image, Minecraft client, Anchor point) {
        double scale = client.getWindow().getGuiScale();
        return image.getPixelRGBA((int) (point.x() * scale), (int) (point.y() * scale));
    }

    private static int abgr(int argb) {
        return (argb & 0xff00ff00) | ((argb & 0xff0000) >>> 16) | ((argb & 0xff) << 16);
    }
}

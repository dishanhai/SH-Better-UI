// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Mounts an extension scene in the native gallery and owns its lifetime. */
final class GalleryExtensionScreen extends GallerySampleScreen {
    private static final System.Logger LOGGER = System.getLogger(GalleryExtensionScreen.class.getName());
    private static final String LABEL = "sh_better_ui.panel.developer.gallery.";
    private final PreviewSpec spec;
    private final List<AbstractWidget> sceneWidgets = new ArrayList<>();
    private final List<Runnable> pendingWidgets = new ArrayList<>();
    private PreviewScene scene;
    private Supplier<? extends PreviewScene> remount;
    private int layoutGeneration;
    private boolean failed;
    private boolean failureControls;

    GalleryExtensionScreen(DeveloperGalleryScreen parent, PreviewSpec spec) {
        super(parent, spec.id(), Component.translatable(spec.titleKey()));
        this.spec = spec;
    }

    @Override
    protected boolean previewAvailable() {
        return super.previewAvailable() && ShBetterUiApi.developerPreviews().find(spec.id()).orElse(null) == spec;
    }

    @Override
    protected void beforeSampleInit() {
        layoutGeneration++;
        sceneWidgets.clear();
        pendingWidgets.clear();
        failureControls = false;
        if (scene != null) {
            try {
                scene.suspend();
            } catch (RuntimeException | LinkageError error) {
                fail("suspend", error);
            }
        }
    }

    @Override
    protected String sampleCode() { return spec.code().isBlank() ? null : spec.code(); }

    @Override
    protected void initSample() {
        if (!previewAvailable()) return;
        if (failed) {
            addFailureControls();
            return;
        }
        try {
            if (scene == null) {
                scene = Objects.requireNonNull((remount == null ? spec.factory() : remount).get(), "preview scene");
                remount = null;
            }
            var owner = scene;
            int generation = layoutGeneration;
            var viewport = body;
            boolean[] layingOut = { true };
            var context = new PreviewContext() {
                private boolean current() {
                    return !failed && scene == owner && generation == layoutGeneration && previewAvailable();
                }
                @Override public Screen screen() { return GalleryExtensionScreen.this; }
                @Override public Bounds viewport() { return viewport; }
                @Override public Font font() { return font; }
                @Override public UiPreferences preferences() { return preferences; }
                @Override public boolean interactive() { return current() && minecraft.isWindowActive(); }
                @Override public <T extends AbstractWidget> T add(T widget) {
                    Objects.requireNonNull(widget);
                    if (!current() || !minecraft.isSameThread()) return widget;
                    if (widget.getX() < viewport.x() || widget.getY() < viewport.y()
                            || widget.getX() + widget.getWidth() > viewport.right()
                            || widget.getY() + widget.getHeight() > viewport.bottom()) {
                        throw new IllegalArgumentException("Preview widget is outside its viewport");
                    }
                    Runnable attach = () -> {
                        if (!current() || sceneWidgets.contains(widget)) return;
                        sceneWidgets.add(addRenderableWidget(widget));
                        if (layingOut[0] && initialFocus == null && widget.active && widget.visible) initialFocus = widget;
                    };
                    if (layingOut[0]) attach.run();
                    else pendingWidgets.add(attach);
                    return widget;
                }
            };
            try {
                scene.layout(context);
            } finally {
                layingOut[0] = false;
            }
        } catch (RuntimeException | LinkageError error) {
            fail("layout", error);
            addFailureControls();
        }
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (failed) {
            PanelText.drawLine(graphics, font, Component.translatable(LABEL + "preview_failed"), body.x(), body.y(),
                    body.width(), theme.text(), mouseX, mouseY);
            return;
        }
        if (scene == null || !previewAvailable()) return;
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        try {
            scene.render(new UiFrame(graphics, body, mouseX, mouseY, preferences, Util.getMillis(), partialTick));
        } catch (RuntimeException | LinkageError error) {
            fail("render", error);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    protected void tickSample() {
        if (failed && !failureControls) {
            rebuildWidgets();
            return;
        }
        var pending = List.copyOf(pendingWidgets);
        pendingWidgets.clear();
        pending.forEach(Runnable::run);
    }

    private void addFailureControls() {
        failureControls = true;
        int width = Math.min(150, body.width());
        if (width < 1 || body.height() < 48) return;
        var retry = addButton(new Bounds(body.x(), body.y() + 24, width, 22),
                Component.translatable(LABEL + "retry_preview"), ignored -> {
                    failed = false;
                    remount = null;
                    rebuildWidgets();
                }, false);
        initialFocus = retry;
    }

    @Override
    public Component getNarrationMessage() {
        return failed ? CommonComponents.joinForNarration(getTitle(), Component.translatable(LABEL + "preview_failed"))
                : super.getNarrationMessage();
    }

    private void fail(String stage, Throwable error) {
        LOGGER.log(System.Logger.Level.WARNING, "Developer preview " + spec.id() + " failed during " + stage, error);
        failed = true;
        layoutGeneration++;
        pendingWidgets.clear();
        if (sceneWidgets.contains(getFocused())) setFocused(null);
        sceneWidgets.forEach(this::removeWidget);
        sceneWidgets.clear();
        closeScene();
    }

    private void closeScene() {
        var previous = scene;
        scene = null;
        if (previous == null) return;
        try {
            previous.close();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not close developer preview " + spec.id(), error);
        }
    }

    @Override
    public void removed() {
        layoutGeneration++;
        pendingWidgets.clear();
        super.removed();
        if (scene != null) {
            try {
                if (previewAvailable()) remount = scene.saveForRemount();
            } catch (RuntimeException | LinkageError error) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not save developer preview " + spec.id(), error);
            } finally {
                closeScene();
            }
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return !previewAvailable() || super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return !previewAvailable() || super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return !previewAvailable() || super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return !previewAvailable() || super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return !previewAvailable() || super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        return !previewAvailable() || super.keyReleased(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        return !previewAvailable() || super.charTyped(event);
    }
}

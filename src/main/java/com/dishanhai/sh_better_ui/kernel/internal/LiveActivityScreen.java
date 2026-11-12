// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Cursor-enabled activity dashboard and its screen-scoped example. */
final class LiveActivityScreen extends Screen {
    private static final UiId PREVIEW_ID = UiId.parse("sh_better_ui:live/preview");
    private final ModuleContext module;
    private final Screen parent;
    private final BooleanSupplier empty;
    private final Runnable openPreview;
    private final long expandOnOpen;
    private boolean initialFocus = true;
    private ScopeHandle scope;
    private ActivityService.Handle activity;
    private Button previewButton;

    LiveActivityScreen(BooleanSupplier empty, Runnable openPreview, long expandOnOpen) {
        super(Component.translatable("sh_better_ui.live.title"));
        this.empty = empty;
        this.openPreview = openPreview;
        this.expandOnOpen = expandOnOpen;
        module = null;
        parent = null;
    }

    LiveActivityScreen(ModuleContext module, Screen parent) {
        super(Component.translatable("sh_better_ui.live.preview.title"));
        this.module = module;
        this.parent = parent instanceof ChatScreen ? null : parent;
        empty = null;
        openPreview = null;
        expandOnOpen = 0;
    }

    boolean takeInitialFocus() {
        boolean take = initialFocus;
        initialFocus = false;
        return take;
    }

    boolean expandsOnOpen(long instance) { return expandOnOpen == instance; }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        if (module != null && (scope == null || !scope.isOpen())) {
            scope = module.engine().require(UIApis.SCREENS).mount(module, this, PREVIEW_ID);
            try {
                activity = module.engine().require(ActivityService.KEY).publish(module, scope,
                        new ActivityService.Request(PREVIEW_ID, previewContent(.4), ActivityService.Priority.HIGH,
                                Duration.ZERO, true, action -> {
                                    if (ActivityService.SEEK.equals(action.id())) activity.update(previewContent(action.fraction()));
                                }));
            } catch (IllegalStateException unavailable) {
                scope.close();
            }
        }
        int buttonWidth = Math.min(72, Math.max(20, width - 12));
        int backY = Math.max(6, height - 68);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds((width - buttonWidth) / 2, backY, buttonWidth, 20).build());
        if (openPreview != null && backY >= 24) {
            int previewWidth = Math.min(180, Math.max(20, width - 12));
            previewButton = addRenderableWidget(Button.builder(Component.translatable("sh_better_ui.live.preview.open"),
                    button -> openPreview.run())
                    .bounds((width - previewWidth) / 2, backY - 24, previewWidth, 20).build());
            updatePreviewButton();
        } else previewButton = null;
    }

    @Override public void tick() {
        updatePreviewButton();
    }

    private void updatePreviewButton() {
        if (previewButton != null) {
            previewButton.active = previewButton.visible = empty.getAsBoolean();
            if (!previewButton.visible && getFocused() == previewButton) setFocused(null);
        }
    }

    private static ActivityService.Content previewContent(double progress) {
        return new ActivityService.Content(ActivityService.Text.key("sh_better_ui.live.preview.activity"),
                ActivityService.Text.key("sh_better_ui.live.preview.detail"), null, progress, true, List.of(), List.of());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        boolean previewVisible = previewButton != null && previewButton.visible;
        String key = module != null && (activity == null || !ClientUiKernel.liveActivityPreviewAvailable())
                ? "sh_better_ui.live.preview.unavailable" : previewVisible ? "sh_better_ui.live.empty" : "sh_better_ui.live.hint";
        var hint = font.split(Component.translatable(key), Math.max(1, width - 12));
        int y = Math.max(4, height - (previewVisible ? 97 : 73) - hint.size() * font.lineHeight);
        for (var line : hint) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xffeeeeee);
            y += font.lineHeight;
        }
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void removed() {
        if (scope != null && scope.isOpen()) scope.close();
        super.removed();
    }
}

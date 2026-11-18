// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** Scrollable, keyboard-accessible controls for the current real activity. No example publication. */
final class NativeActivityScreen extends Screen {
    private final ClientLiveActivities owner;
    private final LiveSurfaceRuntime live;
    private final Screen parent;
    private final Map<String, AbstractWidget> focusIds = new HashMap<>();
    private ActivityService.Snapshot shown;
    private AbstractContainerWidget body;
    private UiSlider seek;
    private long instance = -1, revision = -1;
    private boolean removed;

    NativeActivityScreen(ClientLiveActivities owner, Screen parent) {
        super(Component.translatable("sh_better_ui.live.title"));
        this.owner = owner;
        this.live = owner.runtime();
        this.parent = parent;
    }

    boolean interacting() { return seek != null && seek.isPointerEditing(); }

    @Override protected void init() {
        removed = false;
        owner.detailOpened(this);
        build();
    }

    @Override public void tick() {
        var current = live.plan().primary();
        boolean changed = current == null ? shown != null
                : current.instance() != instance || current.revision() != revision;
        if (changed && (!interacting() || shown == null || !live.isInputCurrent(shown))) build();
    }

    private void build() {
        var focused = body == null ? null : body.getFocused();
        String focus = focusIds.entrySet().stream().filter(entry -> entry.getValue() == focused)
                .map(Map.Entry::getKey).findFirst().orElse(null);
        double scroll = body == null ? 0 : body.scrollAmount();
        if (seek != null) seek.cancelEdit();
        clearWidgets(); focusIds.clear(); body = null; seek = null;
        shown = live.plan().primary();
        if (shown != null && !live.isInputCurrent(shown)) shown = null;
        instance = shown == null ? -1 : shown.instance();
        revision = shown == null ? -1 : shown.revision();
        int available = Math.max(1, Math.min(320, width - 24));
        var content = LinearLayout.vertical().spacing(8);
        if (shown == null) {
            content.addChild(new MultiLineTextWidget(Component.translatable("sh_better_ui.live.empty"), font).setMaxWidth(available));
        } else {
            var snapshot = shown;
            var data = snapshot.content();
            content.addChild(new MultiLineTextWidget(ClientLiveActivities.text(data.title()), font).setMaxWidth(available));
            content.addChild(new MultiLineTextWidget(ClientLiveActivities.text(data.detail()), font).setMaxWidth(available));
            if (data.progress() >= 0) {
                if (data.seekable() && live.hasActions(snapshot) && available >= 9) {
                    seek = new UiSlider(new Bounds(0, 0, available, 20), 0, 100, 1,
                            (int) Math.round(data.progress() * 100), UiPreferences.configured(),
                            percent -> Component.translatable("sh_better_ui.live.preview.progress", percent), ignored -> {});
                    seek.setOnCommit(percent -> dispatch(snapshot, new ActivityService.Action(ActivityService.SEEK, percent / 100d)), ignored -> {});
                    content.addChild(seek); focusIds.put("seek", seek);
                } else {
                    content.addChild(new MultiLineTextWidget(Component.translatable("sh_better_ui.live.preview.progress",
                            (int) Math.round(data.progress() * 100)), font).setMaxWidth(available));
                }
            }
            for (var control : data.controls()) {
                var button = Button.builder(ClientLiveActivities.text(control.label()), ignored ->
                        dispatch(snapshot, new ActivityService.Action(control.id(), -1))).width(available).build();
                button.active = live.hasActions(snapshot);
                content.addChild(button); focusIds.put(control.id().toString(), button);
            }
            var dismiss = Button.builder(Component.translatable("sh_better_ui.live.dismiss"), ignored -> {
                if (!current()) return;
                live.dismiss(snapshot);
                // Do not keep a dispatchable control for a hidden publication until the next kernel frame.
                build();
            }).width(available).build();
            content.addChild(dismiss); focusIds.put("dismiss", dismiss);
        }
        if (height > 64) {
            var layout = new ScrollableLayout(minecraft, content, height - 64);
            layout.setMinWidth(available); layout.arrangeElements(); layout.setPosition((width - layout.getWidth()) / 2, 28);
            layout.visitWidgets(widget -> body = (AbstractContainerWidget) addRenderableWidget(widget));
            body.setScrollAmount(scroll);
            if (focus != null && focusIds.containsKey(focus)) { body.setFocused(focusIds.get(focus)); setFocused(body); }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, ignored -> onClose())
                .bounds(Math.max(0, (width - Math.min(100, width)) / 2), Math.max(0, height - 26), Math.max(1, Math.min(100, width)), 20).build());
    }

    private boolean current() { return !removed && NativePort.screen(Minecraft.getInstance()) == this; }

    private void dispatch(ActivityService.Snapshot snapshot, ActivityService.Action action) {
        if (current()) live.dispatch(snapshot, action);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        graphics.fill(0, 0, width, height, 0xff10141d);
        graphics.centeredText(font, title, width / 2, 9, 0xfff4f7fc);
        super.extractRenderState(graphics, mouseX, mouseY, tick);
        if (shown != null && body != null && live.isInputCurrent(shown)) {
            live.markPainted(shown);
            var geometry = new SurfaceGeometry(body.getX(), body.getY(), body.getWidth(), body.getHeight(), 0, 1);
            live.present(new LiveSurfaceService.View(live.plan(), SurfacePresentation.EXPANDED,
                    SurfacePresentation.EXPANDED, geometry, SurfaceGeometry.HIDDEN, true));
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (current()) NativePort.show(Minecraft.getInstance(), parent); }
    @Override public void removed() {
        removed = true;
        if (seek != null) seek.cancelEdit();
        owner.detailClosed(this);
    }
}

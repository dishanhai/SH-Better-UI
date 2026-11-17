// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.internal.NativeMusicActivity;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.Objects;

/** Controls only the native music activity features currently wired to the live runtime. */
public final class NativeMusicSettingsScreen extends Screen {
    private final Screen parent;
    private final NativeMusicActivity music;
    private AbstractContainerWidget body;
    private Button island, notices, activity;
    private boolean removed;

    public NativeMusicSettingsScreen(Screen parent, NativeMusicActivity music) {
        super(label("title"));
        this.parent = parent;
        this.music = Objects.requireNonNull(music);
    }
    private static Component label(String suffix) {
        return Component.translatable("sh_better_ui.native.music_settings." + suffix);
    }
    @Override protected void init() {
        removed = false;
        double scroll = body == null ? 0 : body.scrollAmount();
        int available = Math.max(1, Math.min(320, width - 24));
        var content = LinearLayout.vertical().spacing(8);
        content.addChild(new MultiLineTextWidget(label("description"), font).setMaxWidth(available));
        island = content.addChild(Button.builder(Component.empty(), button -> {
            if (!current()) return;
            var settings = music.settings();
            music.settings(settings.withIsland(!settings.islandEnabled()));
            sync();
        }).width(available).build());
        notices = content.addChild(Button.builder(Component.empty(), button -> {
            if (!current()) return;
            var settings = music.settings();
            music.settings(settings.withTaskNotices(!settings.taskNotices()));
            sync();
        }).width(available).build());
        content.addChild(new MultiLineTextWidget(label("notice_hint"), font).setMaxWidth(available));
        content.addChild(Button.builder(label("player"), button -> {
            if (current()) NativePort.show(minecraft, ShBetterUiApi.settings().onlineMusicScreen(this));
        }).width(available).build());
        activity = content.addChild(Button.builder(label("activity"), button -> {
            if (current() && ShBetterUiApi.settings().activitiesAvailable())
                NativePort.show(minecraft, ShBetterUiApi.settings().activityScreen(this));
        }).width(available).build());
        body = null;
        if (height > 64) {
            var layout = new ScrollableLayout(minecraft, content, height - 64);
            layout.setMinWidth(available);
            layout.arrangeElements();
            layout.setPosition((width - layout.getWidth()) / 2, 28);
            layout.visitWidgets(widget -> body = (AbstractContainerWidget) addRenderableWidget(widget));
            body.setScrollAmount(scroll);
        }
        var back = addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(Math.max(0,(width - Math.min(100,width))/2), Math.max(0,height-26),
                        Math.max(1,Math.min(100,width)),20).build());
        setInitialFocus(back);
        sync();
    }
    private void sync() {
        var settings = music.settings();
        island.setMessage(Component.translatable("sh_better_ui.native.music_settings.island",
                settings.islandEnabled() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        notices.setMessage(Component.translatable("sh_better_ui.native.music_settings.notices",
                settings.taskNotices() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
        activity.active = ShBetterUiApi.settings().activitiesAvailable();
    }
    private boolean current() { return !removed && NativePort.screen(minecraft) == this; }
    @Override public void tick() { super.tick(); sync(); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float tick) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float tick) {
        graphics.fill(0,0,width,height,0xff10141d);
        graphics.centeredText(font,title,width/2,9,0xfff4f7fc);
        super.extractRenderState(graphics,x,y,tick);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (current()) NativePort.show(minecraft,parent); }
    @Override public void removed() { removed = true; }
}

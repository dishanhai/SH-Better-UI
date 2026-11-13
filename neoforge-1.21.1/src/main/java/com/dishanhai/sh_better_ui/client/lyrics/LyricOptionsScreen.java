package com.dishanhai.sh_better_ui.client.lyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** 山海：在 NeoForge 1.21.1 使用原版设置列表及其键盘、旁白行为。 */
abstract class LyricOptionsScreen extends OptionsSubScreen {
    LyricOptionsScreen(Screen parent, Component title) {
        super(parent, Minecraft.getInstance().options, title);
    }

    final void addOptionRow(AbstractWidget left, AbstractWidget right) {
        list.addSmall(left, right);
    }

    final void rebuildOptions() {
        double scroll = list.getScrollAmount();
        list.children().clear();
        addOptions();
        list.setScrollAmount(scroll);
    }
}

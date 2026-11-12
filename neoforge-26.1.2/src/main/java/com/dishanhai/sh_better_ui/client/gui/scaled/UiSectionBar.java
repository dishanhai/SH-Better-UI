package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** 山海：可注册为原生按钮的顶部导航栏，每个分区均保留图标、完整提示与旁白。 */
public final class UiSectionBar {
    private static final int GAP = 4;

    public record Section(Component label, GraphicIcon icon) {
        public Section {
            Objects.requireNonNull(label);
            Objects.requireNonNull(icon);
        }
    }

    private final List<UiButton> buttons;

    /**
     * 在给定区域内均分按钮，标签始终单行。空间不足时优先保留文字，最窄时使用居中图标。
     * 每个分区至少需要 16×16 GUI 单位。点击已选分区也会回调，便于重新定位内容。
     */
    public UiSectionBar(int x, int y, int width, int height, UiPreferences preferences,
                        List<Section> sections, int selectedIndex, IntConsumer onSelect) {
        Objects.requireNonNull(preferences);
        Objects.requireNonNull(onSelect);
        var entries = List.copyOf(sections);
        int count = entries.size();
        if (count == 0 || width / count < GraphicIcon.PIXELS || height < GraphicIcon.PIXELS) {
            throw new IllegalArgumentException();
        }
        Objects.checkIndex(selectedIndex, count);

        int gap = count == 1 ? 0 : Math.min(GAP, (width - count * GraphicIcon.PIXELS) / (count - 1));
        int available = width - gap * (count - 1);
        int baseWidth = available / count;
        int remainder = available % count;
        var widgets = new ArrayList<UiButton>(count);
        var font = Minecraft.getInstance().font;
        int buttonX = x;
        for (int index = 0; index < count; index++) {
            Section section = entries.get(index);
            int buttonWidth = baseWidth + (index < remainder ? 1 : 0);
            int sectionIndex = index;
            var button = new UiButton(buttonX, y, buttonWidth, height, section.label(), ignored -> {
                select(sectionIndex);
                onSelect.accept(sectionIndex);
            }, preferences, false, UiStyle.LIQUID_GLASS);
            button.setSampleBackground(false);
            int textWidth = font.width(section.label());
            int labelWidth = button.labelWidth(buttonWidth, height);
            button.setLeadingIcon(section.icon());
            int inlineWidth = button.labelWidth(buttonWidth, height);
            if (inlineWidth == labelWidth || textWidth > inlineWidth) {
                button.setIcon(textWidth <= labelWidth ? null : section.icon());
            }
            widgets.add(button);
            buttonX += buttonWidth + gap;
        }
        buttons = List.copyOf(widgets);
        select(selectedIndex);
    }

    /** 依分区顺序返回按钮，供 Screen 注册原生焦点、提示与旁白。 */
    public List<UiButton> buttons() {
        return buttons;
    }

    /** 更新选中状态，不触发用户点击回调。 */
    public void select(int index) {
        Objects.checkIndex(index, buttons.size());
        for (int position = 0; position < buttons.size(); position++) {
            buttons.get(position).setSelected(position == index);
        }
    }

    public void setPreferences(UiPreferences preferences) {
        Objects.requireNonNull(preferences);
        for (UiButton button : buttons) button.setPreferences(preferences);
    }
}

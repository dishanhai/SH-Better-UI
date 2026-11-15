// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCenteredMenu;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

final class GalleryCenteredMenuSample extends GallerySampleScreen {
    private static final int TRIGGER_HEIGHT = 22;
    private static final int GAP = 6;
    private static final int MENU_HEIGHT = UiCenteredMenu.menuHeight(3);
    private UiCenteredMenu menu;
    private Bounds target;
    private int selected;

    GalleryCenteredMenuSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected int minimumBodyHeight() { return TRIGGER_HEIGHT + GAP + MENU_HEIGHT; }

    @Override
    protected void beforeSampleInit() { menu = null; }

    @Override
    protected void initSample() {
        int width = Math.min(176, body.width());
        int x = body.x() + (body.width() - width) / 2;
        var source = new Bounds(x, body.y(), width, TRIGGER_HEIGHT);
        target = new Bounds(x, source.bottom() + GAP, width, MENU_HEIGHT);
        menu = ShBetterUiApi.controls().centeredMenu(source, target, List.of(text("png"), text("jpg"), text("webp")),
                selected, preferences, UiStyle.LIQUID_GLASS, index -> selected = index);
        initialFocus = addRenderableWidget(menu.trigger());
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (menu == null || preferences.highContrast()) return;
        // A stationary grid makes the optical blur and moving glass boundary visible over a solid panel.
        int color = (theme.accent(0xFF9ACDFF) & 0xFFFFFF) | 0x36000000;
        for (int x = target.x(); x < target.right(); x += 8) {
            graphics.fill(x, target.y(), x + 1, target.bottom(), color);
        }
        for (int y = target.y(); y < target.bottom(); y += 8) {
            graphics.fill(target.x(), y, target.right(), y + 1, color);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (menu != null && previewAvailable()) {
            UiTheme.preview(theme, () -> menu.extract(graphics, mouseX, mouseY));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return menu != null && menu.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return menu != null && menu.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return menu != null && menu.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    protected void cancelInteraction() {
        if (menu != null) menu.reset();
    }
}

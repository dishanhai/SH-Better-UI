// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

final class NativePageOptions implements UiLocalModal.Content {
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int MAX_VISIBLE = 8;

    static void open(UiScreen screen, UiLocalModal owner, UiButton source,
                     List<Component> labels, int selected, NativeTextReadability.Label readability, IntConsumer commit) {
        if (labels.isEmpty()) return;
        Bounds viewport = screen.viewport();
        int height = Math.min(viewport.height(), Math.min(MAX_VISIBLE, labels.size()) * ROW_HEIGHT + 2 * PADDING);
        if (height < ROW_HEIGHT + 2 * PADDING) return;
        int width = Math.min(244, viewport.width());
        UiLocalModal modal = UiLocalModal.current(screen);
        if (modal == null) modal = owner;
        var chosen = new int[]{selected};
        modal.open(source, viewport, UiExpansionController.anchored(width, height, .5f, .5f),
                UiExpansionController.Config.DEFAULT,
                session -> new NativePageOptions(screen, session, labels, chosen, readability),
                () -> commit.accept(chosen[0]));
    }

    private final UiLocalModal.Session session;
    private final List<Component> labels;
    private final int[] chosen;
    private final int rows;
    private final NativeTextReadability.Label readability;
    private int cursor;
    private int first;
    private int pressed = -1;

    private NativePageOptions(UiScreen screen, UiLocalModal.Session session, List<Component> labels, int[] chosen,
                              NativeTextReadability.Label source) {
        this.session = session;
        this.labels = List.copyOf(labels);
        this.chosen = chosen;
        rows = Math.max(1, (session.target().height() - 2 * PADDING) / ROW_HEIGHT);
        moveTo(Math.max(0, chosen[0]));
        readability = source == null ? null : popupReadability(screen, session, source);
    }

    private static NativeTextReadability.Label popupReadability(UiScreen screen, UiLocalModal.Session session,
                                                                 NativeTextReadability.Label source) {
        var page = source.mount();
        var scope = page.scope().openChild(ScopeKind.COMPONENT,
                new UiId(source.id().namespace(), source.id().path() + "/popup"));
        try {
            var popup = new NativeTextReadability.Mount(page.module(), scope);
            popup.service().configurePopup(popup.module(), scope, screen.context().themeScope(),
                    page.scope(), source.options().policy());
            return new NativeTextReadability.Label(popup, source.id(), source.options(), session::target);
        } catch (RuntimeException | Error failure) {
            scope.close();
            throw failure;
        }
    }

    @Override
    public void render(GuiGraphics graphics, UiLocalModal.Frame frame, int mouseX, int mouseY, float partialTick) {
        if (readability == null || !readability.mount().scope().isOpen()) {
            renderRows(graphics, frame.bounds(), mouseX, mouseY, null);
            return;
        }
        var popup = readability.mount();
        popup.service().renderPopup(popup.module(), popup.scope(), graphics,
                () -> renderRows(graphics, frame.bounds(), mouseX, mouseY, readability));
    }

    @Override
    public void close() {
        if (readability != null) readability.mount().scope().close();
    }

    private void renderRows(GuiGraphics graphics, Bounds bounds, int mouseX, int mouseY,
                            NativeTextReadability.Label readable) {
        var font = Minecraft.getInstance().font;
        int hover = indexAt(mouseX, mouseY);
        for (int row = 0; row < rows && first + row < labels.size(); row++) {
            int index = first + row;
            int y = bounds.y() + PADDING + row * ROW_HEIGHT;
            if (index == cursor || index == hover) {
                graphics.fill(bounds.x() + PADDING, y, bounds.right() - PADDING, y + ROW_HEIGHT,
                        DarkModeTheme.surfaceColor());
            }
            var line = font.substrByWidth(labels.get(index), Math.max(0, bounds.width() - 4 * PADDING));
            var visual = Language.getInstance().getVisualOrder(line);
            int textX = bounds.x() + 2 * PADDING;
            int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
            int color = DarkModeTheme.textColor();
            if (readable == null || !readable.draw(graphics, font, visual, textX, textY, color, true, index,
                    index == hover, index == cursor)) {
                graphics.drawString(font, visual, textX, textY, color);
            }
        }
    }

    @Override
    public boolean mousePressed(double x, double y, int button, long now) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        pressed = indexAt(x, y);
        return pressed >= 0;
    }

    @Override
    public void mouseReleased(double x, double y, int button, long now) {
        int index = indexAt(x, y);
        if (pressed >= 0 && index == pressed) commit(index);
        pressed = -1;
    }

    @Override
    public void mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!Double.isFinite(vertical) || vertical == 0) return;
        int direction = vertical < 0 ? 1 : -1;
        first = Math.max(0, Math.min(first + direction, labels.size() - rows));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers, long now) {
        int previous = cursor;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> moveTo(cursor - 1);
            case GLFW.GLFW_KEY_DOWN -> moveTo(cursor + 1);
            case GLFW.GLFW_KEY_HOME -> moveTo(0);
            case GLFW.GLFW_KEY_END -> moveTo(labels.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> commit(cursor);
            default -> { return false; }
        }
        if (cursor != previous) Minecraft.getInstance().getNarrator().sayNow(labels.get(cursor));
        return true;
    }

    private void moveTo(int index) {
        cursor = Math.max(0, Math.min(labels.size() - 1, index));
        first = Math.max(0, Math.min(Math.min(cursor, Math.max(first, cursor - rows + 1)),
                Math.max(0, labels.size() - rows)));
    }

    private int indexAt(double x, double y) {
        Bounds area = session.target();
        if (x < area.x() + PADDING || x >= area.right() - PADDING || y < area.y() + PADDING) return -1;
        int row = (int) ((y - area.y() - PADDING) / ROW_HEIGHT);
        int index = first + row;
        return row < rows && index < labels.size() ? index : -1;
    }

    private void commit(int index) {
        chosen[0] = index;
        session.confirm();
    }
}

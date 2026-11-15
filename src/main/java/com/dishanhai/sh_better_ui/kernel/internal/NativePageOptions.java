// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class NativePageOptions implements UiLocalModal.Content {
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int MAX_VISIBLE = 8;
    private static final int MENU_GAP = 6;
    private static final int GLIDE_MS = 220;

    record Glide(List<Component> tags, int menuWidth, boolean above, boolean right, boolean rememberPosition) {}

    static void open(UiScreen screen, UiLocalModal owner, UiButton source,
                     List<Component> labels, IntSupplier selected, NativeTextReadability.Label readability,
                     Glide glide, IntConsumer commit) {
        open(screen, screen.viewport(), owner, source, labels, selected,
                session -> readability == null ? null : popupReadability(screen, session, readability), glide, commit);
    }

    /** Opens the same dropdown from a native preview without a mounted page text scope. */
    public static void openGlide(Screen screen, Bounds viewport, UiLocalModal owner, UiButton source,
                                 List<Component> labels, List<Component> tags, IntSupplier selected, IntConsumer commit) {
        open(screen, viewport, owner, source, labels, selected, session -> null,
                new Glide(tags, 176, false, false, true), commit);
    }

    private static void open(Screen screen, Bounds viewport, UiLocalModal owner, UiButton source,
                             List<Component> labels, IntSupplier selected,
                             Function<UiLocalModal.Session, NativeTextReadability.Label> readability,
                             Glide glide, IntConsumer commit) {
        if (labels.isEmpty()) return;
        int height = Math.min(viewport.height(), Math.min(MAX_VISIBLE, labels.size()) * ROW_HEIGHT + 2 * PADDING);
        if (height < ROW_HEIGHT + 2 * PADDING) return;
        int width = Math.min(244, viewport.width());
        UiLocalModal modal = UiLocalModal.current(screen);
        if (modal == null) modal = owner;
        var chosen = new int[]{-1};
        UiExpansionController.Placement placement = glide == null
                ? UiExpansionController.anchored(width, height, .5f, .5f)
                : (anchor, clip) -> anchoredMenu(anchor, clip, height, glide);
        modal.open(source, viewport, placement, glide == null ? UiExpansionController.Config.DEFAULT
                        : UiExpansionController.Config.CENTERED_MENU,
                session -> new NativePageOptions(session, labels, selected, chosen, readability.apply(session), glide),
                () -> {
                    if (glide == null || chosen[0] != selected.getAsInt()) commit.accept(chosen[0]);
                });
    }

    static Bounds anchoredMenu(Bounds source, Bounds clip, int desiredHeight, Glide glide) {
        int above = Math.max(0, source.y() - MENU_GAP - clip.y());
        int below = Math.max(0, clip.bottom() - source.bottom() - MENU_GAP);
        int preferred = glide.above() ? above : below;
        boolean top = preferred >= desiredHeight || above == below ? glide.above() : above > below;
        int rows = Math.min(desiredHeight - 2 * PADDING, (top ? above : below) - 2 * PADDING) / ROW_HEIGHT;
        if (rows < 1) return new Bounds(source.x(), source.y(), 0, 0);
        int height = rows * ROW_HEIGHT + 2 * PADDING;
        int width = Math.min(clip.width(), Math.max(source.width(), glide.menuWidth()));
        int left = glide.right() ? source.right() - width : source.x();
        int x = Math.max(clip.x(), Math.min(left, clip.right() - width));
        return new Bounds(x, top ? source.y() - MENU_GAP - height : source.bottom() + MENU_GAP, width, height);
    }

    private final UiLocalModal.Session session;
    private final List<Component> labels;
    private final IntSupplier selected;
    private final int[] chosen;
    private final int rows;
    private final NativeTextReadability.Label readability;
    private final Glide glide;
    private int cursor;
    private int first;
    private int pressed = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private int highlighted = -1;
    private float highlightFrom;
    private long highlightStarted;

    private NativePageOptions(UiLocalModal.Session session, List<Component> labels,
                              IntSupplier selected, int[] chosen, NativeTextReadability.Label readability, Glide glide) {
        this.session = session;
        this.labels = List.copyOf(labels);
        this.selected = selected;
        this.chosen = chosen;
        this.glide = glide;
        rows = Math.max(1, (session.target().height() - 2 * PADDING) / ROW_HEIGHT);
        moveTo(selected.getAsInt());
        this.readability = readability;
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
        if (glide != null && (mouseX != lastMouseX || mouseY != lastMouseY)
                && frame.state() == UiExpansionController.State.ACTIVE) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            int hover = indexAt(mouseX, mouseY);
            if (hover >= 0) {
                cursor = hover;
                highlight(hover, frame.nowMs(), pressed >= 0);
            } else if (!glide.rememberPosition()) {
                highlighted = -1;
            }
        }
        if (readability == null || !readability.mount().scope().isOpen()) {
            renderRows(graphics, frame, mouseX, mouseY, null);
            return;
        }
        var popup = readability.mount();
        popup.service().renderPopup(popup.module(), popup.scope(), graphics,
                () -> renderRows(graphics, frame, mouseX, mouseY, readability));
    }

    @Override
    public boolean dismissesOnOutsidePress() { return glide != null; }

    @Override
    public boolean animatesContents() { return glide != null; }

    @Override
    public void close() {
        if (readability != null) readability.mount().scope().close();
    }

    private void renderRows(GuiGraphics graphics, UiLocalModal.Frame frame, int mouseX, int mouseY,
                            NativeTextReadability.Label readable) {
        Bounds bounds = frame.bounds();
        var font = Minecraft.getInstance().font;
        int hover = indexAt(mouseX, mouseY);
        int checked = selected.getAsInt();
        if (glide != null) {
            int active = highlighted < 0 ? checked : highlighted;
            if (active >= first && active < first + rows) {
                float row = highlighted < 0 ? active : highlightAt(frame.nowMs());
                float y = bounds.y() + PADDING + (row - first) * ROW_HEIGHT;
                int color = DarkModeTheme.secondaryTextColor();
                RoundedCorners.draw(graphics, bounds.x() + PADDING, y, bounds.width() - 2 * PADDING,
                        ROW_HEIGHT, 6, frame.focused() ? 1 : 0, color, DarkModeTheme.borderColor());
            }
        }
        for (int row = 0; row < rows && first + row < labels.size(); row++) {
            int index = first + row;
            int y = bounds.y() + PADDING + row * ROW_HEIGHT;
            if (glide == null && (index == cursor || index == hover)) {
                graphics.fill(bounds.x() + PADDING, y, bounds.right() - PADDING, y + ROW_HEIGHT,
                        DarkModeTheme.surfaceColor());
            }
            int space = Math.max(0, bounds.width() - 4 * PADDING);
            var tag = glide != null && index < glide.tags().size() ? glide.tags().get(index) : Component.empty();
            if (glide != null) space = Math.max(0, space - 18);
            var visualTag = Language.getInstance().getVisualOrder(font.substrByWidth(tag, space / 3));
            int tagWidth = font.width(visualTag);
            var line = font.substrByWidth(labels.get(index), Math.max(0, space - (tagWidth == 0 ? 0 : tagWidth + 4)));
            var visual = Language.getInstance().getVisualOrder(line);
            int textX = bounds.x() + 2 * PADDING;
            int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
            int color = DarkModeTheme.textColor();
            if (readable == null || !readable.draw(graphics, font, visual, textX, textY, color, true, index,
                    index == hover, index == cursor)) {
                graphics.drawString(font, visual, textX, textY, color);
            }
            if (tagWidth > 0) {
                graphics.drawString(font, visualTag, textX + space - tagWidth, textY,
                        frame.preferences().highContrast() ? color : DarkModeTheme.secondaryTextColor());
            }
            if (glide != null && index == checked && bounds.width() >= 4 * PADDING + 18) {
                GraphicIcon.SUCCESS.draw(graphics, bounds.right() - 2 * PADDING - GraphicIcon.PIXELS,
                        y + (ROW_HEIGHT - GraphicIcon.PIXELS) / 2, 1, color, frame.preferences());
            }
        }
    }

    @Override
    public boolean mousePressed(double x, double y, int button, long now) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        pressed = indexAt(x, y);
        if (glide != null && pressed >= 0) {
            cursor = pressed;
            highlight(pressed, now, true);
        }
        return pressed >= 0;
    }

    @Override
    public void mouseDragged(double x, double y, int button, long now) {
        if (glide == null || pressed < 0) return;
        int index = indexAt(x, y);
        if (index >= 0) {
            cursor = index;
            highlight(index, now, true);
        }
    }

    @Override
    public void mouseReleased(double x, double y, int button, long now) {
        int index = indexAt(x, y);
        if (pressed >= 0 && index >= 0 && (glide != null || index == pressed)) commit(index);
        pressed = -1;
    }

    @Override
    public void mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!Double.isFinite(vertical) || vertical == 0) return;
        int direction = vertical < 0 ? 1 : -1;
        int next = Math.max(0, Math.min(first + direction, labels.size() - rows));
        if (next == first) return;
        first = next;
        pressed = -1;
        int previous = cursor;
        cursor = Math.max(first, Math.min(cursor, first + rows - 1));
        if (glide != null) {
            highlighted = cursor;
            highlightFrom = cursor;
        }
        narrateCursorChange(previous);
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
        if (glide != null) highlight(cursor, now, true);
        narrateCursorChange(previous);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers, long now) {
        if (glide == null || Character.isWhitespace(character) || Character.isISOControl(character)
                || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        String prefix = String.valueOf(character).toLowerCase(Locale.ROOT);
        int previous = cursor;
        for (int offset = 1; offset <= labels.size(); offset++) {
            int index = (previous + offset) % labels.size();
            if (labels.get(index).getString().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                moveTo(index);
                highlight(cursor, now, true);
                narrateCursorChange(previous);
                break;
            }
        }
        return true;
    }

    private void highlight(int index, long now, boolean immediate) {
        immediate |= session.preferences().reducedMotion() || highlighted < first || highlighted >= first + rows;
        if (highlighted == index && !immediate) return;
        highlightFrom = immediate ? index : highlightAt(now);
        highlighted = index;
        highlightStarted = now;
    }

    private float highlightAt(long now) {
        float progress = GuiMotion.clamp01((now - highlightStarted) / (float) GLIDE_MS);
        return GuiMotion.lerp(highlightFrom, highlighted, (float) easeOut(progress));
    }

    private static double easeOut(double t) { return 1 - Math.pow(1 - t, 3); }

    private void narrateCursorChange(int previous) {
        var client = Minecraft.getInstance();
        if (cursor != previous && client != null) client.getNarrator().sayNow(labels.get(cursor));
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

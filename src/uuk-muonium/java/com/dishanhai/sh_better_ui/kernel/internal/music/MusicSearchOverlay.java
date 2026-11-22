// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.compat.lwjgl2.Mouse;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.NeteaseMusicService;
import com.muoniumplayer.core.ncm.music.NeteaseSearchSuggestService;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.rendering.entities.impl.TextField;
import com.muoniumplayer.core.rendering.font.CFontRenderer;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType;
import com.muoniumplayer.core.screens.ncm.SegmentControl;
import com.muoniumplayer.core.screens.ncm.SheetClock;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.core.settings.ConfigPaths;
import com.muoniumplayer.core.utils.KeyboardUtils;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static com.muoniumplayer.core.rendering.rendersystem.RenderSystem.reAlpha;
import static com.muoniumplayer.core.screens.ncm.NCMScreen.getColor;
import static com.muoniumplayer.render.skija.SkijaShapes.roundedOutline;
import static com.muoniumplayer.render.skija.SkijaShapes.roundedRect;

/** Muonium's sidebar search anchor, focused input and animated results surface. */
public final class MusicSearchOverlay {
    public static final double SIDEBAR_INSET = 8;
    public static final double SIDEBAR_HEIGHT = 16;
    private static final double HEADER_HEIGHT = 25;
    private static final double ROW_HEIGHT = 17;
    private static final long SUGGEST_DEBOUNCE_NANOS = 180_000_000;
    private static final Runnable CONSUME = () -> {};
    private static final Box EMPTY = new Box(0, 0, 0, 0);
    private static final MusicSearchHistory HISTORY = new MusicSearchHistory(ConfigPaths.SEARCH_HISTORY);

    private enum Control { INPUT, SONGS, PLAYLISTS, CLOSE, HISTORY }
    private record Hit(Box box, Control control) {}
    private record Choice(Box box, String keyword) {}

    private final String sourceId;
    private BiConsumer<String, Boolean> submit;
    private final TextField field = new TextField(0, 0, 0, 0, 0);
    private final Panel compact;
    private final Panel modePanel = new Panel();
    private final SegmentControl.Spec modeSpec = new SegmentControl.Spec();
    private final SegmentControl mode;
    private final List<Hit> controls = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();

    private SheetClock clock;
    private MusicPlatform platform;
    private CompletableFuture<List<String>> hot;
    private List<String> recent = List.of();
    private List<String> clearedHistory = List.of();
    private boolean playlists;
    private boolean clearDraftOnDismiss;
    private Box input = EMPTY;
    private Box surface = EMPTY;
    private Control focus = Control.INPUT;
    private String highlighted = "";
    private String query = "";
    private long queryChanged;
    private boolean suggestionsRequested;

    public MusicSearchOverlay(Panel sidebar, String sourceId, MusicSearchOverlay previous,
                              BiConsumer<String, Boolean> submit) {
        this.sourceId = sourceId;
        platform = NeteaseMusicService.getCurrentPlatform();
        if (previous != null) {
            playlists = previous.playlists && supportsPlaylists();
            if (!previous.clearDraftOnDismiss) field.setText(previous.field.getText());
            previous.dispose();
        }
        field.setFontRenderer(FontManager.pf14bold);
        field.setCanLoseFocus(false);
        field.setDrawLineUnder(false);
        field.setCallback(text -> {
            query = text.strip();
            queryChanged = System.nanoTime();
            suggestionsRequested = false;
            highlighted = "";
            choices.clear();
        });

        compact = new Panel() {
            @Override public void onRender(double mouseX, double mouseY) {
                if (active()) return;
                paintCompact(mouseX, mouseY, getAlpha());
            }
        };
        compact.setClickable(false);
        compact.setBeforeRenderCallback(() -> compact.setBounds(SIDEBAR_INSET, SIDEBAR_INSET,
                Math.max(1, compact.getParentWidth() - SIDEBAR_INSET * 2), SIDEBAR_HEIGHT));
        attach(sidebar, submit);

        modeSpec.height = 17;
        modeSpec.radius = 4;
        modeSpec.inset = 1;
        modeSpec.font = FontManager.pf10bold;
        String songs = label("songs").getString(), lists = label("playlists").getString();
        modeSpec.width = 2 * (Math.max(modeSpec.font.getStringWidthD(songs), modeSpec.font.getStringWidthD(lists)) + 6);
        mode = SegmentControl.attach(modePanel, () -> modeSpec, () -> playlists ? 1 : 0,
                () -> getColor(ColorType.ACCENT), () -> getColor(ColorType.ACCENT),
                new String[]{"", ""}, new String[]{songs, lists}, index -> {
                    selectMode(index == 1);
                    return true;
                });
    }

    public MusicSearchOverlay attach(Panel sidebar, BiConsumer<String, Boolean> submit) {
        if (compact.getParent() != null) compact.getParent().getChildren().remove(compact);
        sidebar.addChild(compact);
        this.submit = submit;
        return this;
    }

    public boolean active() {
        return clock != null;
    }

    public ScrollPanel.TouchInputScope restrictTouchInput() {
        return ScrollPanel.restrictTouchInputTo(modePanel);
    }

    private boolean acceptingInput() {
        return active() && !clock.isDismissing();
    }

    public void dispose() {
        clock = null;
        field.dragging = false;
        field.setFocused(false);
        input = surface = EMPTY;
        if (clearDraftOnDismiss) {
            clearDraftOnDismiss = false;
            field.setText("");
        }
    }

    public boolean clickAnchor(double x, double y, int button) {
        if (button != 0 || !anchor().contains(x, y)) return false;
        open();
        return true;
    }

    private Box anchor() {
        return new Box(compact.getX(), compact.getY(), compact.getWidth(), compact.getHeight());
    }

    private void paintCompact(double mouseX, double mouseY, float alpha) {
        var box = anchor();
        boolean hovered = box.contains(mouseX, mouseY);
        paintHeader(box, 3.5, alpha, hovered);
        String text = field.getText();
        drawText(FontManager.pf14bold, text.isEmpty() ? placeholder() : text, box.x() + 18,
                box.centerY(), box.width() - 22, text.isEmpty() ? ColorType.SECONDARY_TEXT : ColorType.PRIMARY_TEXT, alpha);
        if (hovered) CursorUtils.setOverride(CursorUtils.HAND);
    }

    private void open() {
        if (active()) return;
        platform = NeteaseMusicService.getCurrentPlatform();
        hot = MusicSearchRecommendations.hot(platform);
        clearDraftOnDismiss = false;
        clearedHistory = List.of();
        recent = HISTORY.recent(scope());
        query = field.getText().strip();
        queryChanged = System.nanoTime();
        suggestionsRequested = false;
        highlighted = "";
        focus = Control.INPUT;
        field.setFocused(true);
        field.selectAll();
        clock = new SheetClock();
        MusicTypography.narrate(label(playlists ? "playlist_hint" : "song_hint"));
    }

    private void dismiss() {
        if (!acceptingInput()) return;
        field.dragging = false;
        field.setFocused(false);
        clock.requestDismiss();
    }

    private boolean supportsPlaylists() {
        return platform == MusicPlatform.NETEASE || platform == MusicPlatform.KUGOU
                || platform == MusicPlatform.QISHUI || platform == MusicPlatform.BILIBILI;
    }

    private String scope() {
        return sourceId + (playlists ? ":playlists" : ":songs");
    }

    private String placeholder() {
        return label(playlists ? "playlist_hint" : "song_hint").getString();
    }

    private void selectMode(boolean playlists) {
        if (this.playlists == playlists) return;
        this.playlists = playlists;
        recent = HISTORY.recent(scope());
        clearedHistory = List.of();
        highlighted = "";
        choices.clear();
        MusicTypography.narrate(label(playlists ? "playlists" : "songs"));
    }

    private void clearOrRestore() {
        if (clearedHistory.isEmpty()) {
            clearedHistory = HISTORY.clear(scope());
            MusicTypography.narrate(label("cleared"));
        } else {
            HISTORY.restore(scope(), clearedHistory);
            clearedHistory = List.of();
            MusicTypography.narrate(label("restored"));
        }
        recent = HISTORY.recent(scope());
        highlighted = "";
        choices.clear();
    }

    private void runSearch(String text) {
        String keyword = text.strip();
        if (keyword.isEmpty() || !acceptingInput()) return;
        HISTORY.record(scope(), keyword);
        field.setText(keyword);
        clearDraftOnDismiss = true;
        dismiss();
        submit.accept(keyword, playlists);
    }

    public boolean key(char character, int keyCode) {
        if (KeyboardUtils.isKeyComboCtrl(keyCode, Keyboard.KEY_F)) {
            if (!active()) open();
            else if (acceptingInput()) {
                focusInput();
                field.selectAll();
            }
            return true;
        }
        if (!active()) return false;
        if (!acceptingInput()) return true;
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE -> dismiss();
            case Keyboard.KEY_DOWN, Keyboard.KEY_UP -> moveHighlight(keyCode == Keyboard.KEY_DOWN ? 1 : -1);
            case Keyboard.KEY_TAB -> {
                boolean reverse = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (!reverse && !highlighted.isEmpty()) {
                    field.setText(highlighted);
                    focusInput();
                } else moveFocus(reverse ? -1 : 1);
            }
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                if (focus == Control.INPUT) runSearch(highlighted.isEmpty() ? field.getText() : highlighted);
                else activate(focus);
            }
            default -> {
                if (focus == Control.INPUT) {
                    field.setFocused(true);
                    field.textboxKeyTyped(character, keyCode);
                }
            }
        }
        return true;
    }

    private void focusInput() {
        focus = Control.INPUT;
        highlighted = "";
        field.setFocused(true);
    }

    private void moveHighlight(int direction) {
        if (choices.isEmpty()) return;
        int index = direction > 0 ? -1 : 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).keyword().equals(highlighted)) {
                index = i;
                break;
            }
        }
        focusInput();
        highlighted = choices.get(Math.floorMod(index + direction, choices.size())).keyword();
        MusicTypography.narrate(Component.literal(highlighted));
    }

    private void moveFocus(int direction) {
        var order = controls.stream().map(Hit::control).toList();
        if (order.isEmpty()) return;
        focus = order.get(Math.floorMod(order.indexOf(focus) + direction, order.size()));
        highlighted = "";
        field.setFocused(focus == Control.INPUT);
        MusicTypography.narrate(switch (focus) {
            case INPUT -> label(playlists ? "playlist_hint" : "song_hint");
            case SONGS -> label("songs");
            case PLAYLISTS -> label("playlists");
            case HISTORY -> label(clearedHistory.isEmpty() ? "clear" : "undo");
            case CLOSE -> Component.translatable("gui.close");
        });
    }

    private void activate(Control control) {
        switch (control) {
            case INPUT -> focusInput();
            case SONGS -> selectMode(false);
            case PLAYLISTS -> selectMode(true);
            case CLOSE -> dismiss();
            case HISTORY -> clearOrRestore();
        }
    }

    /** The field needs the press itself to start drag selection, including with touch mode enabled. */
    public boolean pressInput(double x, double y, int button) {
        if (button != 0 || !acceptingInput() || !input.contains(x, y)) return false;
        focusInput();
        field.mouseClicked(x, y, button);
        return true;
    }

    /** Captures the visible action before a touch release or a change in async suggestions. */
    public Runnable captureClick(double x, double y, int button) {
        if (!active()) return null;
        if (!acceptingInput() || button != 0) return CONSUME;
        SheetClock pressedClock = clock;
        String pressedQuery = field.getText();
        boolean pressedMode = playlists;
        Runnable action = surface.contains(x, y) ? CONSUME : this::dismiss;
        for (Hit hit : controls) {
            if (hit.control() != Control.INPUT && hit.box().contains(x, y)) {
                action = () -> {
                    activate(hit.control());
                    if (acceptingInput()) focusInput();
                };
                break;
            }
        }
        for (Choice choice : choices) {
            if (choice.box().contains(x, y)) {
                action = () -> runSearch(choice.keyword());
                break;
            }
        }
        Runnable pressedAction = action;
        return () -> {
            if (clock == pressedClock && acceptingInput() && playlists == pressedMode
                    && field.getText().equals(pressedQuery)) pressedAction.run();
        };
    }

    public void render(Box host, double mouseX, double mouseY, float alpha) {
        if (!active()) return;
        var preferences = UiPreferences.configured();
        clock.tick();
        if (clock.isGone() || (clock.isDismissing() && preferences.reducedMotion())) {
            dispose();
            paintCompact(mouseX, mouseY, alpha);
            return;
        }

        double progress = preferences.reducedMotion() ? 1 : clock.progress();
        double reveal = preferences.reducedMotion() ? 1 : clock.layer(.30, .90);
        var start = anchor();
        double width = Math.max(1, Math.min(316, host.width() - 16));
        boolean columns = width >= 218 && query.isEmpty();
        double height = Math.max(1, Math.min(columns || !query.isEmpty() ? 191 : 220, host.height() - 16));
        // Keep the input centered when typing changes the sections below it.
        double top = host.centerY() - Math.min(191, Math.max(1, host.height() - 16)) * .5;
        height = Math.min(height, host.bottom() - 8 - top);
        double x = mix(start.x(), host.centerX() - width * .5, progress);
        double y = mix(start.y(), top, progress);
        double w = mix(start.width(), width, progress);
        double headerHeight = mix(start.height(), HEADER_HEIGHT, progress);
        double radius = mix(3.5, 9, progress);
        var header = new Box(x, y, w, headerHeight);
        surface = new Box(x, y, w, headerHeight + Math.max(0, height - HEADER_HEIGHT) * reveal);
        roundedRect(host.x(), host.y(), host.width(), host.height(), 8,
                reAlpha(0xff000000, (float) progress * alpha * (preferences.highContrast() ? .62f : .38f)));
        paintSurface(surface, radius, alpha, progress, preferences.highContrast());
        paintHeader(header, radius, alpha, true);

        controls.clear();
        choices.clear();
        double closeWidth = 16 * reveal;
        double right = header.right() - 5 - closeWidth;
        if (reveal > .45 && supportsPlaylists() && w > modeSpec.width + 76) {
            double modeX = right - modeSpec.width - 3;
            double modeY = header.centerY() - modeSpec.height * .5;
            modePanel.setBounds(modeX, modeY, modeSpec.width, modeSpec.height);
            modePanel.setAlpha((float) reveal * alpha);
            modePanel.renderWidget(mouseX, mouseY, 0);
            double half = modeSpec.width * .5;
            controls.add(new Hit(new Box(modeX, modeY, half, modeSpec.height), Control.SONGS));
            controls.add(new Hit(new Box(modeX + half, modeY, half, modeSpec.height), Control.PLAYLISTS));
            right = modeX - 4;
        }
        input = new Box(x + 18, y, Math.max(1, right - x - 18), headerHeight);
        controls.add(0, new Hit(input, Control.INPUT));
        field.xPosition = (float) input.x();
        field.yPosition = (float) input.y();
        field.width = (float) input.width();
        field.height = (float) input.height();
        field.setTextColor(getColor(ColorType.PRIMARY_TEXT));
        field.setDisabledTextColour(getColor(ColorType.SECONDARY_TEXT));
        field.setWholeAlpha(alpha);
        if (field.getText().isEmpty()) {
            drawText(FontManager.pf14bold, placeholder(), input.x() + 2, input.centerY(), input.width() - 2,
                    ColorType.SECONDARY_TEXT, alpha * .72f);
        }
        field.drawTextBox((int) mouseX, (int) mouseY);
        if (acceptingInput() && input.contains(mouseX, mouseY)) CursorUtils.setOverride(CursorUtils.TEXT);

        if (reveal > .45) {
            var close = new Box(header.right() - closeWidth - 3, header.centerY() - 8, closeWidth, 16);
            controls.add(new Hit(close, Control.CLOSE));
            paintHover(close, mouseX, mouseY, alpha, focus == Control.CLOSE);
            FoliaLucideIcons.draw(FoliaLucideIcons.CLOSE, close.centerX(), close.centerY(), 8,
                    reAlpha(getColor(ColorType.PRIMARY_TEXT), alpha * (float) reveal));
        }
        if (reveal > 0 && surface.bottom() > header.bottom() + 23) {
            drawChoices(header, height, columns, mouseX, mouseY, alpha * (float) reveal);
        }
        for (Hit hit : controls) {
            if (hit.control() == focus && focus != Control.INPUT) {
                Box box = hit.box();
                roundedOutline(box.x(), box.y(), box.width(), box.height(), 3, .85,
                        reAlpha(getColor(ColorType.ACCENT), alpha));
            }
        }
        if (acceptingInput() && controls.stream().noneMatch(hit -> hit.control() == focus)) focusInput();
        if (choices.stream().noneMatch(choice -> choice.keyword().equals(highlighted))) highlighted = "";
    }

    private void drawChoices(Box header, double height, boolean columns, double mouseX, double mouseY, float alpha) {
        double x = surface.x() + 9, y = header.bottom() + 7, width = surface.width() - 18;
        int rows = Math.max(0, Math.min(MusicSearchHistory.PER_SCOPE_LIMIT,
                (int) ((height - HEADER_HEIGHT - 29) / ROW_HEIGHT)));
        if (rows == 0 || width <= 12) return;
        List<String> hotTerms = hot.getNow(List.of());
        if (!query.isEmpty()) {
            List<String> suggestions = suggestions(hotTerms);
            boolean loading = supportsSuggestions() && !playlists && !NeteaseSearchSuggestService.isResolved(query);
            drawSection("suggestions", suggestions, loading, x, y, width, rows, mouseX, mouseY, alpha);
            return;
        }

        List<String> trending = hotTerms.stream().filter(term -> recent.stream().noneMatch(term::equalsIgnoreCase)).toList();
        if (columns) {
            double column = (width - 12) * .5;
            drawHistory(x, y, column, rows, mouseX, mouseY, alpha);
            drawSection("recommended", trending, !hot.isDone(), x + column + 12, y, column, rows,
                    mouseX, mouseY, alpha);
        } else {
            int historyRows = Math.max(1, Math.min(Math.max(1, recent.size()), (rows - 1) / 2));
            drawHistory(x, y, width, historyRows, mouseX, mouseY, alpha);
            int recommendedRows = rows - historyRows - 1;
            if (recommendedRows > 0) {
                drawSection("recommended", trending, !hot.isDone(), x, y + 16 + historyRows * ROW_HEIGHT,
                        width, recommendedRows, mouseX, mouseY, alpha);
            }
        }
    }

    private void drawHistory(double x, double y, double width, int rows, double mouseX, double mouseY, float alpha) {
        if (y + 14 > surface.bottom() - 5) return;
        drawSection("history", recent, false, x, y, width, rows, mouseX, mouseY, alpha);
        if (recent.isEmpty() && clearedHistory.isEmpty()) return;
        String caption = label(clearedHistory.isEmpty() ? "clear" : "undo").getString();
        double actionWidth = Math.min(width * .48, FontManager.pf10bold.getStringWidthD(caption) + 8);
        var action = new Box(x + width - actionWidth, y, actionWidth, 14);
        controls.add(new Hit(action, Control.HISTORY));
        paintHover(action, mouseX, mouseY, alpha, focus == Control.HISTORY);
        drawText(FontManager.pf10bold, caption, action.x() + 4, action.centerY(), action.width() - 8, ColorType.ACCENT, alpha);
    }

    private void drawSection(String key, List<String> terms, boolean loading, double x, double y, double width, int rows,
                             double mouseX, double mouseY, float alpha) {
        if (y + 14 > surface.bottom() - 5) return;
        boolean historyAction = key.equals("history") && (!recent.isEmpty() || !clearedHistory.isEmpty());
        drawText(FontManager.pf12bold, label(key).getString(), x + 4, y + 7,
                width * (historyAction ? .51 : 1) - 8, ColorType.PRIMARY_TEXT, alpha);
        if (terms.isEmpty() && y + 16 + ROW_HEIGHT <= surface.bottom() - 5) {
            drawText(FontManager.pf12, label(loading ? "loading" : key + "_empty").getString(), x + 4,
                    y + 16 + ROW_HEIGHT * .5, width - 8, ColorType.SECONDARY_TEXT, alpha);
            return;
        }
        for (int i = 0; i < Math.min(rows, terms.size()); i++) {
            var box = new Box(x, y + 16 + i * ROW_HEIGHT, width, ROW_HEIGHT - 1);
            if (box.bottom() > surface.bottom() - 5) break;
            String keyword = terms.get(i);
            choices.add(new Choice(box, keyword));
            paintHover(box, mouseX, mouseY, alpha, keyword.equals(highlighted));
            drawText(FontManager.pf12bold, keyword, box.x() + 5, box.centerY(), box.width() - 10,
                    ColorType.PRIMARY_TEXT, alpha);
        }
    }

    private List<String> suggestions(List<String> hotTerms) {
        var terms = new ArrayList<String>();
        if (!playlists && supportsSuggestions()) {
            if (!suggestionsRequested && System.nanoTime() - queryChanged >= SUGGEST_DEBOUNCE_NANOS && acceptingInput()) {
                NeteaseSearchSuggestService.prefetch(query);
                suggestionsRequested = true;
            }
            for (var item : NeteaseSearchSuggestService.peek(query)) addTerm(terms, item.text);
        }
        String filter = query.toLowerCase(Locale.ROOT);
        for (String term : recent) if (term.toLowerCase(Locale.ROOT).contains(filter)) addTerm(terms, term);
        for (String term : hotTerms) if (term.toLowerCase(Locale.ROOT).contains(filter)) addTerm(terms, term);
        return terms;
    }

    private boolean supportsSuggestions() {
        return platform == MusicPlatform.QQ || platform == MusicPlatform.NETEASE
                || platform == MusicPlatform.KUGOU || platform == MusicPlatform.QISHUI;
    }

    private static void addTerm(List<String> terms, String term) {
        if (term != null && !term.isBlank() && terms.stream().noneMatch(term::equalsIgnoreCase)) terms.add(term);
    }

    private static void paintSurface(Box box, double radius, float alpha, double progress, boolean highContrast) {
        roundedRect(box.x(), box.y(), box.width(), box.height(), radius,
                reAlpha(getColor(ColorType.GENERIC_BACKGROUND), alpha));
        roundedOutline(box.x(), box.y(), box.width(), box.height(), radius, highContrast ? 1 : .65,
                reAlpha(getColor(ColorType.BORDER), alpha * (float) progress));
    }

    private static void paintHeader(Box box, double radius, float alpha, boolean focus) {
        roundedRect(box.x(), box.y(), box.width(), box.height(), radius,
                reAlpha(getColor(ColorType.INPUT_BACKGROUND), alpha));
        roundedOutline(box.x(), box.y(), box.width(), box.height(), radius, focus ? .85 : .55,
                reAlpha(getColor(focus ? ColorType.ACCENT : ColorType.BORDER), alpha));
        FoliaLucideIcons.draw(FoliaLucideIcons.SEARCH, box.x() + 9, box.centerY(), 8,
                reAlpha(getColor(ColorType.SECONDARY_TEXT), alpha));
    }

    private void paintHover(Box box, double mouseX, double mouseY, float alpha, boolean selected) {
        boolean hovered = acceptingInput() && box.contains(mouseX, mouseY);
        if (hovered || selected) {
            roundedRect(box.x(), box.y(), box.width(), box.height(), 4,
                    reAlpha(getColor(ColorType.ACCENT), alpha * (hovered && Mouse.isButtonDown(0) ? .36f : .22f)));
        }
        if (hovered) CursorUtils.setOverride(CursorUtils.HAND);
    }

    private static void drawText(CFontRenderer font, String text, double x, double centerY, double width,
                                 ColorType color, float alpha) {
        if (width > 0) font.drawString(font.trim(text, width), x, centerY - font.getFontHeight() * .5,
                reAlpha(getColor(color), alpha));
    }

    private static Component label(String key) {
        return Component.translatable("sh_better_ui.music_search." + key);
    }

    private static double mix(double from, double to, double progress) {
        return from + (to - from) * progress;
    }
}

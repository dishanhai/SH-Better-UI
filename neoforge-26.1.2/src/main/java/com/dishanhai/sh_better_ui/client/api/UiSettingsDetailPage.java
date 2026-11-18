// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingsDetailLayout;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Reusable secondary settings page: fixed native back/title bar and scrolling cards.
 * The host owns draft values, validation and persistence. Optional Actions provide a
 * scoped edit transaction: Apply merges, Cancel/Back confirm discard, Reset stays local. Create one instance per navigation entry; reuse it for resize/reopen.
 * Inspired by Card/Field composition, not a React dependency or copied shadcn implementation.
 */
public final class UiSettingsDetailPage {
    /** Native field context; does not pretend to expose unmigrated extension/theme registries. */
    public static final class Context implements AutoCloseable {
        private final List<AutoCloseable> resources=new ArrayList<>();
        private boolean closed;
        public ControlsApi controls(){check();return ShBetterUiApi.controls();}
        public UiPreferences preferences(){check();return UiPreferences.configured();}
        public net.minecraft.client.gui.Font font(){check();return Minecraft.getInstance().font;}
        public <T extends AutoCloseable> T manage(T value){check();if(resources.stream().noneMatch(existing->existing==value))resources.add(Objects.requireNonNull(value));return value;}
        public void onClose(Runnable action){manage(action::run);}
        private void check(){if(closed)throw new IllegalStateException("Closed field context");}
        @Override public void close(){
            if(closed)return;closed=true;Throwable failure=null;
            for(int i=resources.size()-1;i>=0;i--)try{resources.get(i).close();}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            resources.clear();
            if(failure instanceof Error error)throw error;
            if(failure instanceof RuntimeException error)throw error;
            if(failure!=null)throw new IllegalStateException("Field cleanup failed",failure);
        }
    }
    public record Field(Component label, BiFunction<Bounds, Context, AbstractWidget> control) {
        public Field { label = Objects.requireNonNull(label).copy(); Objects.requireNonNull(control); }
    }
    public record Section(Component title, List<Field> fields) {
        public Section { title = Objects.requireNonNull(title).copy(); fields = List.copyOf(fields); }
    }
    /** apply returns null on success or a visible error; it must atomically merge only this scope. */
    public record Actions(Supplier<Component> resetLabel, Component cancelLabel, Component applyLabel,
                          Runnable reset, Runnable cancel, Supplier<Component> apply, BooleanSupplier dirty) {
        public Actions {
            Objects.requireNonNull(resetLabel);
            cancelLabel = Objects.requireNonNull(cancelLabel).copy();
            applyLabel = Objects.requireNonNull(applyLabel).copy();
            Objects.requireNonNull(reset); Objects.requireNonNull(cancel);
            Objects.requireNonNull(apply); Objects.requireNonNull(dirty);
        }
    }
    @FunctionalInterface interface Confirmation {
        boolean open(UiScreen screen,UiConfirmation.Prompt prompt,com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences preferences,
                     Supplier<? extends GuiEventListener> focus,Runnable accepted,Runnable abandoned);
    }
    // Package-only desktop navigation seams; production always uses the native confirmation and Screen route.
    Confirmation confirm=(screen,prompt,preferences,focus,accepted,abandoned)->ShBetterUiApi.interactions().confirm(screen,prompt,preferences,focus,accepted,abandoned);
    java.util.function.Consumer<UiScreen> closeScreen=UiScreen::onClose;
    private record Placed(AbstractWidget widget, int section, int field) {}
    private final Component title;
    private final List<Section> sections;
    private final VerticalScroll scroll = new VerticalScroll();
    private final List<Placed> widgets = new ArrayList<>();
    private SettingsDetailLayout layout;
    private Bounds clip;
    private UiVerticalScrollBar scrollbar;
    private final Actions actions;
    private boolean applied;
    private boolean discardConfirmed;
    private boolean confirmation;
    private Context context;
    private int focusIndex=-1;
    private Component status;
    private Component footerHint;
    private UiButton applyButton;
    private UiButton resetButton;
    private int footerY;
    private UiScreen owner;

    public UiSettingsDetailPage(Component title, List<Section> sections) {
        this(title, sections, null);
    }

    public UiSettingsDetailPage(Component title, List<Section> sections, Actions actions) {
        this.title = Objects.requireNonNull(title).copy();
        this.sections = List.copyOf(sections);
        this.actions = actions;
    }

    /** Optional host explanation (for example, Apply updates the parent draft, not disk). */
    public UiSettingsDetailPage footerHint(Component hint) {
        if (owner != null) throw new IllegalStateException("Set footer hint before mounting");
        footerHint = Objects.requireNonNull(hint).copy();
        return this;
    }

    public double scrollOffset() { return scroll.offset(); }

    /** Pass to UUK ScreenService.create(module, builder), then open through the host. */
    public UiScreenBuilder builder() {
        return new UiScreenBuilder(title).onInit(screen->{try{init(screen);}catch(RuntimeException|Error failure){if(owner==screen)release();throw failure;}}).onTick(screen -> syncActionState())
                .onRender(this::render).onRemoved(this::removed);
    }

    private void removed(UiScreen screen) {
        if(owner==screen && !confirmation)release();
    }
    private void release(){
        if(owner==null)return;
        try {if(actions!=null&&!applied)actions.cancel().run();}
        finally {
            owner=null;confirmation=false;widgets.clear();scrollbar=null;applyButton=null;resetButton=null;
            var old=context;context=null;if(old!=null)old.close();
        }
    }

    void init(UiScreen screen) {
        if (owner != null && owner != screen) throw new IllegalStateException("Settings page already mounted");
        if (owner == null) { applied = false; discardConfirmed = false;status=null; }
        if(context!=null)context.close();context=new Context();
        owner = screen;confirmation=false;
        widgets.clear();
        var viewport = screen.viewport();
        configureClose(screen);
        applyButton=null;resetButton=null;
        if(viewport.width()<96||viewport.height()<96){
            clip=new Bounds(0,0,0,0);scrollbar=null;addBack(screen);return;
        }
        int width = Math.max(0, Math.min(560, viewport.width() - 24));
        clip = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + Math.min(36, viewport.height()), width,
                Math.max(0, viewport.height() - 44 - (actions == null ? 0 : 54)));
        layout = SettingsDetailLayout.of(Math.max(0, width - 12), sections.stream().map(s -> s.fields().size()).toList());
        scroll.resize(clip.height(), layout.contentHeight());
        for (int s = 0; s < sections.size(); s++) {
            var fields = sections.get(s).fields();
            for (int f = 0; f < fields.size(); f++) {
                if(layout.controlWidth()<=8||clip.height()==0)continue;
                var field = fields.get(f);
                var bounds = new Bounds(clip.x() + layout.controlX(), 0, layout.controlWidth(), layout.stacked() ? 16 : 22);
                var widget = Objects.requireNonNull(field.control().apply(bounds, context), "Field widget");
                // The factory owns the control's dynamic message/narration; do not replace it.
                widget.setTooltip(Tooltip.create(field.label()));
                widget.setTabOrderGroup(1);
                screen.addClipped(widget, () -> clip,null);
                widgets.add(new Placed(widget, s, f));
            }
        }
        scrollbar = screen.addClipped(new UiVerticalScrollBar(
                new Bounds(clip.right() - 8, clip.y(), 8, clip.height()), title, scroll,
                SettingsDetailLayout.ROW, this::position), () -> clip,null);
        scrollbar.setTabOrderGroup(1);
        scrollbar.setSmoothScrolling(() -> context.preferences().reducedMotion());
        screen.onScroll( (x, y, horizontal, vertical) -> clip.contains(x, y) && scrollbar.scrollByWheel(vertical));
        if (actions != null) {
            footerY = Math.max(viewport.y(), viewport.bottom() - 30);
            int buttonWidth = Math.max(0, (width - 12) / 3);
            resetButton = addAction(screen, clip.x(), buttonWidth, actions.resetLabel().get(), () -> {
                actions.reset().run();
                status = null;
                screen.refreshLayout();
            });
            addAction(screen, clip.x() + buttonWidth + 6, buttonWidth, actions.cancelLabel(), ()->closeScreen.accept(screen));
            applyButton = addAction(screen, clip.x() + 2 * (buttonWidth + 6), buttonWidth,
                    actions.applyLabel(), () -> {
                        if(applyDraft())closeScreen.accept(screen);
                    });
        }
        screen.onFocusChanged(this::revealFocus);
        syncActionState();
        position();
        addBack(screen);
        var focus=focusTarget();if(focus!=null)screen.setFocused(focus);
    }
    private void addBack(UiScreen screen){
        var viewport=screen.viewport();
        var back=new UiButton(Math.max(0,viewport.x()+4),4,Math.max(1,Math.min(54,viewport.width()-8)),22,
                Component.translatable("gui.back"),ignored->closeScreen.accept(screen),context.preferences(),false);
        screen.add(back);
    }
    private void configureClose(UiScreen screen){
        screen.onCloseRequest(() -> {
            if (actions == null || applied || discardConfirmed || !actions.dirty().getAsBoolean()) return true;
            if(!confirmation){
                confirmation=true;
                boolean opened=false;
                try {
                    opened=confirm.open(screen,new UiConfirmation.Prompt(
                            Component.translatable("sh_better_ui.settings_detail.discard_title"),
                            Component.translatable("sh_better_ui.settings_detail.discard_details"),
                            Component.translatable("sh_better_ui.settings_detail.discard"),
                            Component.translatable("sh_better_ui.settings_detail.keep_editing")),context.preferences(),
                            this::focusTarget,()->{discardConfirmed=true;closeScreen.accept(screen);},this::release);
                }finally{if(!opened)confirmation=false;}
            }
            return false;
        });
    }
    private GuiEventListener focusTarget(){return focusIndex>=0&&focusIndex<widgets.size()?widgets.get(focusIndex).widget():null;}

    boolean applyDraft(){
        if(actions==null||applied||!actions.dirty().getAsBoolean())return false;
        status=actions.apply().get();
        if(status==null){applied=true;return true;}
        return false;
    }

    private UiButton addAction(UiScreen screen, int x, int width, Component label, Runnable action) {
        var button = new UiButton(x, footerY, width, 22, label, ignored -> action.run(),
                context.preferences(), false, UiStyle.LIQUID_GLASS);
        button.setTabOrderGroup(2);
        return screen.addClipped(button, screen::viewport,null);
    }

    private void position() {
        int offset = (int) Math.round(scroll.offset());
        for (var placed : widgets) {
            int y = clip.y() + layout.cards().get(placed.section()).fieldTop(placed.field()) - offset;
            placed.widget().setX(clip.x() + layout.controlX());
            placed.widget().setY(y + (layout.stacked() ? 14 : 4));
            // Native focus hooks reveal keyboard targets synchronously, not on the next frame.
            placed.widget().visible = clip.width() > 0 && clip.height() > 0;
            if (owner.getFocused() == placed.widget()
                    && (placed.widget().getY() >= clip.bottom()
                    || placed.widget().getY() + placed.widget().getHeight() <= clip.y())) {
                owner.setFocused(null); // Wheel/drag must not leave an invisible keyboard action armed.
            }
        }
        if (scrollbar != null) scrollbar.visible = scroll.maxOffset() > 0 && clip.height() > 0;
    }

    private void revealFocus(GuiEventListener focused) {
        if (scrollbar == null || focused == null) return;
        for (var placed : widgets) {
            if (placed.widget() != focused) continue;
            focusIndex=widgets.indexOf(placed);
            int y = placed.widget().getY();
            int adjustment = y < clip.y() ? y - clip.y()
                    : Math.max(0, y + placed.widget().getHeight() - clip.bottom());
            if (scroll.scrollBy(adjustment)) { scrollbar.synchronizeMotion(); position(); }
            break;
        }
    }

    // Keep labels, narration and click semantics aligned after host-owned draft edits.
    void syncActionState() {
        if (resetButton != null) {
            var label = actions.resetLabel().get();
            if (!resetButton.getMessage().equals(label)) resetButton.setMessage(label);
        }
        if (applyButton != null) applyButton.active = !applied && actions.dirty().getAsBoolean();
    }

    private void render(UiScreen screen, UiFrame frame) {
        if(scrollbar==null){
            frame.graphics().fill(0,0,screen.width,screen.height,UiTheme.current().background());
            if(screen.height>=44)frame.graphics().text(Minecraft.getInstance().font,
                    Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(
                            Component.translatable("sh_better_ui.settings_detail.small"),Math.max(0,screen.width-8))),4,32,UiTheme.current().text(),false);
            return;
        }
        scrollbar.advanceMotion();
        frame.graphics().fill(0,0,screen.width,screen.height,UiTheme.current().background());
        frame.graphics().text(Minecraft.getInstance().font,Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(title,Math.max(0,screen.width-70))),64,10,UiTheme.current().text(),false);
        syncActionState();
        var notice = status == null ? footerHint : status;
        if (notice != null && actions != null) frame.graphics().text(Minecraft.getInstance().font,
                Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(notice, clip.width())),
                clip.x(), footerY - 14, UiTheme.current().text(), false);
        var graphics = frame.graphics();
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            int offset = (int) Math.round(scroll.offset());
            for (int s = 0; s < sections.size(); s++) {
                var card = layout.cards().get(s);
                int top = clip.y() + card.top() - offset;
                if (top >= clip.bottom() || top + card.height() <= clip.y()) continue;
                RoundedCorners.draw(graphics, clip.x(), top, layout.width(), card.height(), 8, 1,
                        frame.preferences().highContrast() ? UiTheme.current().text() : UiTheme.current().border(),
                        UiTheme.current().surface());
                int textWidth = Math.max(0, layout.width() - 2 * SettingsDetailLayout.PADDING);
                graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(sections.get(s).title(), textWidth)),
                        clip.x() + SettingsDetailLayout.PADDING, top + 9, UiTheme.current().accent(), false);
                for (int f = 0; f < sections.get(s).fields().size(); f++) {
                    int y = clip.y() + card.fieldTop(f) - offset;
                    int labelWidth = layout.stacked() ? textWidth : Math.max(0, layout.controlX() - 2 * SettingsDetailLayout.PADDING);
                    graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(sections.get(s).fields().get(f).label(), labelWidth)),
                            clip.x() + SettingsDetailLayout.PADDING, y + (layout.stacked() ? 0 : 9), UiTheme.current().text(), false);
                }
            }
        } finally { graphics.disableScissor(); }
    }
}

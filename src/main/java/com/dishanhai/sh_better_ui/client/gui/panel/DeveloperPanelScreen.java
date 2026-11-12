package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：在同一画面试用公共控件、检查渲染状态并复制接入示例。 */
public final class DeveloperPanelScreen extends Screen implements UiScreenReturn.Target {
    private static final int SHORT_PANEL_HEIGHT = 182;
    private static final int FULL_PANEL_HEIGHT = 236;
    private static final int PADDING = 12;
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int DEMOS_Y = 64;
    private static final int EXAMPLES_Y = 136;
    private static final int DIAGNOSTICS_Y = 181;
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");
    static final String DIALOG_CODE = """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.CommonComponents;
            import net.minecraft.network.chat.Component;

            private final UiOverlayHost host = new UiOverlayHost(this, UiPreferences.configured());

            private void confirm(UiButton source, Component title, Component details, Runnable onConfirm) {
                var prompt = new UiConfirmation.Prompt(title, details,
                        CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
                host.showDialogFrom(source, prompt, onConfirm);
            }
            """;
    static final String TOAST_CODE = """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
            import net.minecraft.network.chat.Component;

            private void showNotice(Component title, Component details) {
                UiToast.show(UiToast.Kind.INFO, title, details);
            }
            """;

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperPanelScreen);
    }

    private final Screen parent;
    private final UiPreferences preferences;
    private final UiDotBackground backdrop = new UiDotBackground();
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private boolean entered;
    private UiOverlayHost overlays;
    private Supplier<UiButton> returnEntry;
    private UiButton controls;
    private UiButton materials;
    private UiButton bottomSheet;
    private UiButton animationExample;
    private UiButton dialogExample;
    private UiButton toastExample;
    private UiButton diagnostics;
    private UiButton pageStudio;
    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;
    private boolean compact;

    public DeveloperPanelScreen(Screen parent) {
        this(parent, UiPreferences.configured());
    }

    public DeveloperPanelScreen(Screen parent, UiPreferences preferences) {
        this(parent, preferences, null);
    }

    public static List<String> sampleIds() {
        return DeveloperGalleryScreen.sampleIds();
    }

    public static Screen fullscreenSample(Screen parent, String id, UiPreferences preferences,
                                          Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreen(parent, id, preferences, returnFocus);
    }

    public static Screen fullscreenMaterial(Screen parent, String id, UiPreferences preferences,
                                            Supplier<? extends GuiEventListener> returnFocus) {
        return DeveloperGalleryScreen.fullscreenMaterial(parent, id, preferences, returnFocus);
    }

    /** Shared kernel hook for the built-in optical scene. Third-party scenes use RenderService.submit. */
    public static Consumer<RenderService.Context> takeMaterialDraw(Screen owner) {
        return owner instanceof DeveloperGalleryScreen gallery ? gallery.takeMaterialDraw() : null;
    }

    DeveloperPanelScreen(Screen parent, UiPreferences preferences, Supplier<UiButton> source) {
        super(label("title"));
        this.parent = parent;
        this.preferences = preferences;
        this.overlays = new UiOverlayHost(this, preferences);
        if (source != null) {
            entrance.startFrom(source);
            returning.beginHandoff(parent, preferences);
            entered = true;
        }
    }

    @Override
    protected void init() {
        controls = null;
        materials = null;
        bottomSheet = null;
        animationExample = null;
        dialogExample = null;
        toastExample = null;
        diagnostics = null;
        pageStudio = null;
        compact = width < 212 || height < SHORT_PANEL_HEIGHT + 8;
        if (compact) {
            entrance.finish();
            if (width >= 36 && height >= BUTTON_HEIGHT + 8) {
                int backWidth = Math.min(100, width - 8);
                addButton((width - backWidth) / 2, height - BUTTON_HEIGHT - 4,
                        backWidth, BACK, GraphicIcon.BACK, this::onClose);
            }
            return;
        }
        panelWidth = Math.min(width - 16, 440);
        panelHeight = height >= FULL_PANEL_HEIGHT + 8 ? FULL_PANEL_HEIGHT : SHORT_PANEL_HEIGHT;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        if (!entered) {
            entered = true;
            entrance.startAt(width * 0.5f, top + 5f);
        }

        int contentX = left + PADDING;
        int contentWidth = panelWidth - 2 * PADDING;
        int columnWidth = (contentWidth - GAP) / 2;
        int rightX = contentX + columnWidth + GAP;
        int rightWidth = contentWidth - columnWidth - GAP;
        controls = addButton(contentX, top + 34, columnWidth, label("gallery.controls"), GraphicIcon.INTERACTION,
                () -> openGallery(Category.CONTROLS, null, () -> controls));
        String featuredMaterial = NativeMaterialRoute.catalogService()
                .flatMap(catalog -> catalog.find(LiquidGlassPreview.ID))
                .map(MaterialEntry::nativePreviewId).orElse(null);
        materials = addButton(rightX, top + 34, rightWidth, label("gallery.materials"), GraphicIcon.TEMPLATE,
                () -> openGallery(Category.MATERIALS, featuredMaterial, () -> materials));
        if (featuredMaterial != null) materials.setPreview(label("gallery.materials_hint"));
        int demoY = top + DEMOS_Y + 13;
        addRenderableWidget(button(contentX, demoY, columnWidth, BUTTON_HEIGHT,
                label("demo.dialog"), GraphicIcon.WARNING, preferences,
                pressed -> showDialog(overlays, (UiButton) pressed, this::showToast)));
        addButton(rightX, demoY, rightWidth, label("demo.toast"), GraphicIcon.INFO, this::showToast);
        int secondDemoY = demoY + BUTTON_HEIGHT + GAP;
        bottomSheet = addButton(contentX, secondDemoY, columnWidth, label("demo.bottom_sheet"),
                GraphicIcon.LIST, () -> openFullscreenDemo(DeveloperBottomSheetPreview.ID, () -> bottomSheet));
        animationExample = addButton(rightX, secondDemoY, rightWidth,
                label("demo.animation"), GraphicIcon.RESUME,
                () -> openFullscreenDemo(DeveloperAnimationPreview.ID, () -> animationExample));

        dialogExample = addButton(contentX, top + EXAMPLES_Y + 13, columnWidth, label("example.open_dialog"),
                GraphicIcon.CODE, () -> openGallery(Category.CONTROLS, "dialog", () -> dialogExample));
        toastExample = addButton(rightX, top + EXAMPLES_Y + 13, rightWidth, label("example.open_toast"),
                GraphicIcon.CODE, () -> openGallery(Category.CONTROLS, "toast", () -> toastExample));
        int backX = contentX + contentWidth - 60;
        diagnostics = addButton(backX - GAP - 24, top + 7, 24, label("diagnostics"), null, this::openDiagnostics);
        diagnostics.setIcon(GraphicIcon.INFO);
        var studioTitle = Component.translatable("sh_better_ui.pages.studio.title");
        pageStudio = addButton(diagnostics.getX() - GAP - 24, top + 7, 24, studioTitle, null, () -> {
            returnEntry = () -> pageStudio;
            com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.openPageStudio(minecraft);
        });
        pageStudio.setIcon(GraphicIcon.CODE);
        pageStudio.setPreview(studioTitle);
        addButton(backX, top + 7, 60, BACK, GraphicIcon.BACK, this::onClose);
    }

    private UiButton addButton(int x, int y, int buttonWidth, Component text, GraphicIcon icon, Runnable action) {
        return addRenderableWidget(button(x, y, buttonWidth, BUTTON_HEIGHT, text, icon, preferences, action));
    }

    static UiButton button(int x, int y, int width, int height, Component text, GraphicIcon icon,
                           UiPreferences preferences, Runnable action) {
        return button(x, y, width, height, text, icon, preferences, ignored -> action.run());
    }

    static UiButton button(int x, int y, int width, int height, Component text, GraphicIcon icon,
                           UiPreferences preferences, Button.OnPress action) {
        var button = new UiButton(x, y, width, height, text, action, preferences, false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        if (icon != null) button.setLeadingIcon(icon);
        return button;
    }

    private void openGallery(Category section, String sampleId, Supplier<UiButton> entry) {
        returnEntry = entry;
        minecraft.setScreen(new DeveloperGalleryScreen(this, preferences, section, sampleId, entry));
    }

    private void openFullscreenDemo(String sampleId, Supplier<UiButton> entry) {
        returnEntry = entry;
        minecraft.setScreen(DeveloperGalleryScreen.fullscreenFrom(this, sampleId, preferences, entry));
    }

    void restoreReturnFocus() {
        UiButton target = returnEntry == null ? null : returnEntry.get();
        returnEntry = null;
        overlays = new UiOverlayHost(this, preferences);
        if (target != null) setInitialFocus(target);
    }

    static void showDialog(UiOverlayHost overlays, UiButton source, Runnable confirmed) {
        var prompt = new UiConfirmation.Prompt(label("demo.dialog"), label("demo.dialog_details"),
                CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
        overlays.showDialogFrom(source, prompt, confirmed);
    }

    private void showToast() {
        showToast(preferences);
    }

    static void showToast(UiPreferences preferences) {
        UiToast.show(UiToast.Kind.INFO, label("demo.toast"), label("demo.toast_details"),
                UiToast.Effect.SLIDE_FROM_RIGHT, 2_500L, preferences);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderScreen(graphics, mouseX, mouseY, partialTick);
        returning.renderHandoff(graphics, preferences);
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entrance.closing()) {
            returning.renderBackdrop(graphics, width, height, partialTick);
        } else {
            backdrop.render(this, graphics, width, height, preferences, !entrance.active());
        }
        if (compact) {
            int y = PADDING;
            for (var line : font.split(TOO_SMALL, Math.max(1, width - 2 * PADDING))) {
                if (y + font.lineHeight > height - BUTTON_HEIGHT - 6) break;
                graphics.drawString(font, line, PADDING, y, LiquidGlassTheme.textColor(), false);
                y += font.lineHeight + 2;
            }
        } else {
            var panel = new Bounds(left, top, panelWidth, panelHeight);
            if (returning.renderTransition(entrance, graphics, panel, preferences, () -> {
                boolean pointer = entrance.allowsPointer(mouseX, mouseY);
                renderContent(graphics, pointer ? mouseX : -1, pointer ? mouseY : -1, partialTick);
            })) return;
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + PADDING;
        drawLabel(graphics, getTitle(), x, top + 14, pageStudio.getX() - GAP - x, LiquidGlassTheme.textColor());
        graphics.drawString(font, label("demos"), x, top + DEMOS_Y, LiquidGlassTheme.accentColor(), false);
        graphics.drawString(font, label("examples"), x, top + EXAMPLES_Y, LiquidGlassTheme.accentColor(), false);
        if (panelHeight == FULL_PANEL_HEIGHT) {
            graphics.drawString(font, label("diagnostics"), x, top + DIAGNOSTICS_Y, LiquidGlassTheme.accentColor(), false);
            int y = top + DIAGNOSTICS_Y + 13;
            for (Component line : diagnosticsLines()) {
                drawLabel(graphics, line, x, y, panelWidth - 2 * PADDING, LiquidGlassTheme.mutedTextColor());
                y += 11;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (entrance.closing()) {
            if (entrance.reopenFromSource(mouseX, mouseY, button)) returning.beginHandoff(this, preferences);
            return true;
        }
        return entrance.allowsPointer(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) {
            if (entrance.reopenFromKey(key)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (!entrance.allowsActivation(key, getFocused())) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    private List<Component> diagnosticsLines() {
        var window = minecraft.getWindow();
        var target = minecraft.getMainRenderTarget();
        Component enabled = Component.translatable(ThemeEffects.glass().enabled() ? "options.on" : "options.off");
        Component shader = label(GaussBlur.isReady() ? "shader_ready" : "shader_unavailable");
        return List.of(label("gui", width, height, (int) window.getGuiScale()),
                label("framebuffer", target.width, target.height), label("blur", enabled, shader));
    }

    private void openDiagnostics() {
        var text = Component.empty();
        for (Component line : diagnosticsLines()) {
            if (!text.getSiblings().isEmpty()) text.append("\n");
            text.append(line);
        }
        var details = new UiTextDetails(label("diagnostics"), text, 3);
        minecraft.setScreen(details.pageFrom(this, preferences, diagnostics, () -> diagnostics));
    }

    private void drawLabel(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, font.substrByWidth(text, width).getString(), x, y, color, false);
    }

    static Component label(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer." + key, args);
    }

    @Override
    public void onClose() {
        if (entrance.closing()) return;
        if (!compact && !preferences.reducedMotion()) {
            returning.beginHandoff(this, preferences);
            returning.prepare(parent, width, height);
        }
        entrance.close(preferences, () -> minecraft.setScreen(parent));
        if (compact) entrance.finish();
    }

    @Override
    public void prepareReturnLayer() {
        if (returnEntry != null) setFocused(returnEntry.get());
    }

    @Override
    public void removed() {
        returning.clearHandoff();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments;
import com.dishanhai.sh_better_ui.client.visual.RichTextExtensions;
import com.dishanhai.sh_better_ui.client.visual.RichTextView;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncClient;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Value;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.internal.richtext.network.NativeRichSyncServer.DEMO_DOCUMENT;
import static com.dishanhai.sh_better_ui.kernel.internal.richtext.network.NativeRichSyncServer.DEMO_VALUE;

/** Runnable resource, extension and server binding example, shared by both loaders. */
public final class RichTextPipelineScreen extends Screen {
    private final UiDotBackground backdrop = new UiDotBackground();
    private static final int MARGIN = 12;
    private static final int TOP = 34;
    private static final String KEY = "sh_better_ui.rich_text_pipeline.";
    private static final UiId METER = id("meter");
    private static final UiId METER_TYPE = id("meter_renderer");
    private static final UiId METER_VALUE = id("meter_value");

    private final ModuleContext module;
    private final Screen parent;
    private final GuiEventListener returnFocus;
    private ScopeHandle scope;
    private VisualLayoutBinding binding;
    private AlignmentLayout.Node content;
    private RichTextView view;
    private ClientRichDocuments.Watch watch;
    private RichSyncClient.View state;
    private RichSyncClient.View lastState;
    private Language language;
    private RichDocument document;
    private long dataRevision;
    private Button lower;
    private Button raise;
    private Button reject;

    public RichTextPipelineScreen(ModuleContext module, Screen parent) {
        super(label("title"));
        this.module = module;
        this.parent = parent;
        returnFocus = parent == null ? null : parent.getFocused();
    }

    @Override protected void init() {
        if (scope == null || !scope.isOpen()) {
            var engine = module.engine();
            scope = engine.require(UIApis.SCREENS).mount(module, this, id("screen"));
            var layout = ShBetterUiApi.layout().create(viewport());
            content = layout.node("rich_text_pipeline", AlignmentFrame.box(new Rect(0, 0, 0, 0)));
            view = RichTextView.attach(module, scope, content);
            var extensions = RichTextExtensions.attach(scope);
            extensions.register(METER_TYPE, 1, context -> {
                var box = context.bounds();
                var graphics = context.graphics();
                int right = box.x() + box.width(), bottom = box.y() + box.height();
                graphics.fill(box.x(), box.y(), right, bottom, 0xff23354a);
                int percent = context.data(METER_VALUE).map(data -> Integer.parseInt(data.value())).orElse(0);
                int width = Math.max(0, (box.width() - 2) * percent / 100);
                if (width > 0 && box.height() > 2) {
                    graphics.fill(box.x() + 1, box.y() + 1, box.x() + 1 + width, bottom - 1, 0xff40d49b);
                }
            });
            extensions.registerData(METER_VALUE, () -> new RichTextDataSource.Snapshot(dataRevision,
                    Long.toString(displayed())));
            view.bindExtensions(extensions);
            if (!ClientRichDocuments.hasActiveWatch()) {
                watch = ClientRichDocuments.watch(scope, DEMO_DOCUMENT, next -> {
                    state = next;
                    dataRevision++;
                });
            }
            binding = engine.require(UIApis.LAYOUT).bindVisual(module, scope, layout, time -> arrange());
        } else {
            view.invalidate();
        }

        int y = Math.max(0, height - 26);
        int gap = 4;
        int buttonWidth = Math.min(110, Math.max(0, (width - 2 * MARGIN - 3 * gap) / 4));
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(MARGIN, y, buttonWidth, 20).build());
        lower = action("set_low", 25, MARGIN + buttonWidth + gap, y, buttonWidth);
        raise = action("set_high", 65, MARGIN + 2 * (buttonWidth + gap), y, buttonWidth);
        reject = action("request_over", 100, MARGIN + 3 * (buttonWidth + gap), y, buttonWidth);
        updateActions();
        setInitialFocus(back);
    }

    private Button action(String name, long value, int x, int y, int width) {
        return addRenderableWidget(Button.builder(label(name), button -> {
            watch.edit(DEMO_VALUE, new Number(value), true);
            updateActions();
        }).bounds(x, y, width, 20).build());
    }

    private void updateActions() {
        boolean ready = watch != null && state != null && state.status() == RichSyncClient.Status.LIVE && state.writable()
                && !state.pending() && watch.supported();
        lower.active = ready;
        raise.active = ready;
        reject.active = ready;
    }

    private void arrange() {
        if (document == null || language != Language.getInstance() || !Objects.equals(lastState, state)) {
            language = Language.getInstance();
            lastState = state;
            document = makeDocument();
        }
        int size = Math.min(440, Math.max(0, width - 2 * MARGIN));
        var text = view.prepare(document, font, size);
        int contentHeight = Math.min(text.height(), Math.max(0, viewport().height() - TOP));
        content.update(AlignmentFrame.box(new Rect((width - size) / 2, TOP, size, contentHeight))
                .withClip(new Rect(0, 0, size, contentHeight)));
    }

    private RichDocument makeDocument() {
        var builder = RichText.document(id("document"));
        builder.text(label("local").getString()).add(new ImageNode(id("animation"),
                UiId.parse("sh_better_ui:textures/gui/rich_text/pulse.gif"), 16, 16, label("animation_alt").getString()))
                .text("  ").add(new ImageNode(id("missing"), UiId.parse("sh_better_ui:textures/gui/rich_text/absent.gif"),
                        32, 16, label("missing_alt").getString())).paragraph(8)
                .text(label("server").getString()).paragraph(4)
                .add(new RichExtensionNode(METER, METER_TYPE, 1, 144, 12, 12, label("meter_alt").getString(), ""))
                .paragraph(4);
        if (state != null && state.authoritative().containsKey(DEMO_VALUE)) {
            builder.text(label("values", number(state.authoritative().get(DEMO_VALUE)), displayed()).getString())
                    .paragraph(4);
        }
        var status = state == null ? RichSyncClient.Status.UNAVAILABLE : state.status();
        String statusKey = status == RichSyncClient.Status.OFFLINE
                ? minecraft.level == null ? "local_only" : "status.unsupported"
                : "status." + status.name().toLowerCase(Locale.ROOT);
        builder.text(label(statusKey).getString());
        if (state != null && (state.pending() || state.lastOutcome() != null)) {
            builder.paragraph(4).text(label(state.pending() ? "pending"
                    : "outcome." + state.lastOutcome().name().toLowerCase(Locale.ROOT)).getString());
        }
        return builder.build();
    }

    private long displayed() { return state == null ? 0 : number(state.displayed().get(DEMO_VALUE)); }

    private static long number(Value value) {
        return value instanceof Number number ? Math.max(0, Math.min(100, number.number())) : 0;
    }

    private Bounds viewport() { return new Bounds(0, 0, width, Math.max(0, height - 30)); }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        backdrop.render(this, graphics, width, height, UiPreferences.configured());
        if (font.width(title) <= width - 2 * MARGIN) {
            graphics.drawCenteredString(font, title, width / 2, MARGIN, DarkModeTheme.textColor());
        }
        boolean overlay = minecraft.getOverlay() != null;
        if (overlay && view != null) view.invalidate();
        if (!overlay && binding != null && binding.isActive()) {
            var frame = new UiFrame(graphics, viewport(), UiMouseState.read(), UiPreferences.configured(),
                    UiFrameScheduler.nowMs(), delta);
            binding.renderScreen(frame, pass -> view.draw(pass, DarkModeTheme.textColor()));
        }
        updateActions();
        super.render(graphics, mouseX, mouseY, delta);
        if (!overlay && view != null) view.hitTest(mouseX, mouseY).ifPresent(hit -> {
            String alt = hit.node() instanceof ImageNode image ? image.altText()
                    : hit.node() instanceof RichExtensionNode extension ? extension.altText() : "";
            if (!alt.isEmpty()) graphics.renderTooltip(font, Component.literal(alt), mouseX, mouseY);
        });
    }

    @Override public void onClose() {
        minecraft.setScreen(parent);
        if (parent != null && returnFocus != null && parent.children().contains(returnFocus)) parent.setFocused(returnFocus);
    }

    @Override public void removed() {
        if (scope != null) scope.close();
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    private static UiId id(String path) { return new UiId("sh_better_ui", "rich_text_pipeline/" + path); }
    private static Component label(String key, Object... args) { return Component.translatable(KEY + key, args); }
}

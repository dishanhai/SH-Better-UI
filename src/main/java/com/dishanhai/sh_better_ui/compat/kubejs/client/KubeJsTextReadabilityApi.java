// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService.Result;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.Mount;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.Options;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Scoped access for one client script generation. Native module and render handles stay internal. */
public final class KubeJsTextReadabilityApi {
    private static final Set<String> POLICY_KEYS = Set.of("adaptiveColor", "foregroundProtection", "contrast",
            "lockHue", "localColor", "sampling", "samplingMode");
    record PopupOptions(Options text, boolean inheritPage) {}
    private final KubeJsUiApi owner;

    KubeJsTextReadabilityApi(KubeJsUiApi owner) {
        this.owner = owner;
    }

    /** Builds a Screen owned by this script generation from a translation key. */
    public UiScreenBuilder screen(String titleKey) {
        return owner.screen(titleKey);
    }

    public Page page(UiScreen screen, Map<?, ?> configuration) {
        requireClientThread();
        if (!owner.owns(screen)) throw new SecurityException("Text requires a Screen owned by this script generation");
        return new Page(screen, options(configuration));
    }

    static Options options(Object raw) {
        var fields = KubeJsPageSchema.object(raw, POLICY_KEYS.size(), "textReadability");
        KubeJsPageSchema.onlyKeys(fields, POLICY_KEYS, "textReadability");
        TextReadabilityPolicy.Contrast contrast = null;
        if (fields.containsKey("contrast")) {
            var value = fields.get("contrast");
            contrast = value instanceof CharSequence text && "classified".contentEquals(text)
                    ? TextReadabilityPolicy.ClassifiedContrast.INSTANCE
                    : new TextReadabilityPolicy.FixedContrast(KubeJsPageSchema.number(value));
        }
        var policy = new TextReadabilityPolicy(flag(fields, "adaptiveColor"), flag(fields, "foregroundProtection"),
                contrast, null, null).withLockHue(flag(fields, "lockHue")).withLocalColor(flag(fields, "localColor"));
        if (fields.containsKey("samplingMode")) {
            policy = policy.withSamplingMode(KubeJsPageSchema.enumValue(TextReadabilityPolicy.SamplingMode.class,
                    fields.get("samplingMode")));
        }
        var sampling = fields.containsKey("sampling")
                ? KubeJsPageSchema.enumValue(TextReadabilityService.SamplingHint.class, fields.get("sampling")) : null;
        return new Options(policy, sampling);
    }

    static PopupOptions popupOptions(Object raw) {
        var fields = new HashMap<>(KubeJsPageSchema.object(raw, POLICY_KEYS.size() + 1, "popup textReadability"));
        boolean inheritPage = Boolean.TRUE.equals(flag(fields, "inheritPage"));
        fields.remove("inheritPage");
        return new PopupOptions(options(fields), inheritPage);
    }

    private static Boolean flag(Map<String, ?> fields, String key) {
        if (!fields.containsKey(key)) return null;
        if (fields.get(key) instanceof Boolean value) return value;
        throw new IllegalArgumentException("Expected a script boolean for " + key);
    }

    private static UiId id(String raw) {
        if (raw == null || raw.length() > 128) throw new IllegalArgumentException("Expected a bounded text ID");
        return KubeJsPageSchema.scriptId(raw);
    }

    private static boolean clientThread() {
        var minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    private static void requireClientThread() {
        if (!clientThread()) throw new IllegalStateException("Text readability requires the Minecraft client thread");
    }

    public final class Page {
        private final UiScreen screen;
        private final Options options;
        private final Map<UiId, Options> controls = new HashMap<>();
        private final Map<UiId, UiLocalModal.Session> popupSessions = new HashMap<>();
        private Mount mount;
        private UiLocalModal modals;

        private Page(UiScreen screen, Options options) {
            this.screen = screen;
            this.options = options;
        }

        public UiButton button(int x, int y, int width, int height, String translationKey, Runnable onPress) {
            requireClientThread();
            owner.requireOpen();
            return screen.button(x, y, width, height, translationKey, onPress);
        }

        public Page control(String controlId, Map<?, ?> configuration) {
            requireClientThread();
            owner.requireOpen();
            var id = id(controlId);
            if (controls.size() >= 256 && !controls.containsKey(id)) {
                throw new IllegalArgumentException("Text control limit reached");
            }
            controls.put(id, options(configuration));
            return this;
        }

        public Result render(UiFrame frame, Consumer<Context> draw) {
            if (owner.isClosed() || screen.isDisposed()) return Result.SCOPE_CLOSED;
            if (!clientThread() || !screen.isDrawing(frame)) return Result.INVALID_STAGE;
            if (draw == null) return Result.INVALID_REQUEST;
            var target = mount();
            renderContext(target, options, frame.graphics(), NativeTextReadability.rect(frame.viewport()), draw);
            return Result.DRAWN;
        }

        /** Opens content through the existing native local-modal manager. Escape closes the owned popup. */
        public boolean openPopup(String popupId, UiButton source, int width, int height, Map<?, ?> configuration,
                                 Consumer<Context> draw) {
            requireClientThread();
            owner.requireOpen();
            var id = id(popupId);
            var config = popupOptions(configuration);
            var overrides = config.text();
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(draw, "draw");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Popup dimensions must be positive");
            if (Minecraft.getInstance().screen != screen || screen.isDisposed() || !screen.children().contains(source)
                    || popupSessions.containsKey(id)) {
                return false;
            }
            var parent = mount();
            if (modals == null) modals = new UiLocalModal(screen, screen.context().preferences());
            return modals.open(source, screen.viewport(), UiExpansionController.anchored(width, height, 0.5f, 0.5f),
                    UiExpansionController.Config.DEFAULT, session -> {
                        var scope = parent.scope().openChild(ScopeKind.COMPONENT,
                                new UiId(parent.scope().owner().namespace(), "popup/" + id.path()));
                        var popup = new Mount(parent.module(), scope);
                        try {
                            popup.service().configurePopup(popup.module(), scope, screen.context().themeScope(),
                                    config.inheritPage() ? parent.scope() : null, overrides.policy());
                        } catch (RuntimeException | Error failure) {
                            scope.close();
                            throw failure;
                        }
                        popupSessions.put(id, session);
                        return new UiLocalModal.Content() {
                            @Override
                            public void render(GuiGraphics graphics, UiLocalModal.Frame frame,
                                               int mouseX, int mouseY, float partialTick) {
                                if (owner.isClosed() || !scope.isOpen()) return;
                                Rect clip = NativeTextReadability.rect(frame.bounds())
                                        .intersect(NativeTextReadability.rect(screen.viewport()));
                                var inherited = config.inheritPage() ? options.overlay(overrides) : overrides;
                                popup.service().renderPopup(popup.module(), scope, graphics,
                                        () -> renderContext(popup, inherited, graphics, clip, draw));
                            }

                            @Override
                            public void close() {
                                popupSessions.remove(id, session);
                                scope.close();
                            }
                        };
                    }, () -> {});
        }

        public boolean closePopup(String popupId) {
            requireClientThread();
            owner.requireOpen();
            var session = popupSessions.get(id(popupId));
            return session != null && session.cancel();
        }

        private Mount mount() {
            if (mount == null || !mount.scope().isOpen()) {
                var target = ClientUiKernel.mountScriptText(screen);
                try {
                    target.service().configure(target.module(), target.scope(), options.policy());
                    screen.context().manage(target.scope());
                } catch (RuntimeException | Error failure) {
                    target.scope().close();
                    throw failure;
                }
                mount = target;
            }
            return mount;
        }

        private void renderContext(Mount target, Options inherited, GuiGraphics graphics, Rect clip,
                                   Consumer<Context> draw) {
            var context = new Context(this, target, inherited, NativeTextReadability.transform(graphics), clip, null);
            try {
                draw.accept(context);
            } finally {
                context.live = false;
            }
        }
    }

    public final class Context {
        private final Page page;
        private final Mount target;
        private final Options inherited;
        private final Transform transform;
        private final Rect clip;
        private final RenderService.Context nativeContext;
        private boolean live = true;

        private Context(Page page, Mount target, Options inherited, Transform transform, Rect clip,
                        RenderService.Context nativeContext) {
            this.page = page;
            this.target = target;
            this.inherited = inherited;
            this.transform = transform;
            this.clip = clip;
            this.nativeContext = nativeContext;
        }

        public Bounds bounds() {
            return new Bounds((int) clip.x(), (int) clip.y(), (int) clip.width(), (int) clip.height());
        }

        public Result submit(String control, String text, Component label, double x, double y, double argb) {
            return paint(false, control, text, label, x, y, argb);
        }

        public Result drawNow(String control, String text, Component label, double x, double y, double argb) {
            return paint(true, control, text, label, x, y, argb);
        }

        public Result foreground(int z, Consumer<Context> draw) {
            var stage = stage(false);
            if (stage != null) return stage;
            if (draw == null) return Result.INVALID_REQUEST;
            return target.service().foreground(target.module(), target.scope(), z, context -> {
                if (owner.isClosed() || !target.scope().isOpen()) return;
                var scoped = new Context(page, target, inherited, transform, clip, context);
                try {
                    draw.accept(scoped);
                } finally {
                    scoped.live = false;
                }
            });
        }

        private Result paint(boolean immediate, String control, String text, Component label, double x, double y,
                             double argb) {
            var stage = stage(immediate);
            if (stage != null) return stage;
            if (label == null || label.getString().length() > 4096 || !Double.isFinite(x) || !Double.isFinite(y)
                    || argb < Integer.MIN_VALUE || argb > 0xffffffffL || Math.rint(argb) != argb) {
                return Result.INVALID_REQUEST;
            }
            try {
                var textId = new TextId(id(control), null, id(text));
                var settings = inherited.overlay(page.controls.getOrDefault(textId.text(), Options.INHERIT));
                var font = Minecraft.getInstance().font;
                var style = new TextReadabilityService.Style((int) (long) argb, false, TextReadabilityPolicy.Usage.BODY);
                var request = NativeTextReadability.line(target.scope(), textId, font, label.getVisualOrderText(),
                        x, y, style, transform, clip, 1, null, 0, settings);
                return immediate ? target.service().drawNow(target.module(), nativeContext, request)
                        : target.service().submit(target.module(), request);
            } catch (IllegalArgumentException failure) {
                return Result.INVALID_REQUEST;
            }
        }

        private Result stage(boolean immediate) {
            if (owner.isClosed() || !target.scope().isOpen()) return Result.SCOPE_CLOSED;
            return !clientThread() || !live || immediate != (nativeContext != null) ? Result.INVALID_STAGE : null;
        }
    }
}

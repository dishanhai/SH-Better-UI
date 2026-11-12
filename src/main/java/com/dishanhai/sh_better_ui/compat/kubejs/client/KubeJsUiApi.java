package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.api.UiApiContext;
import com.dishanhai.sh_better_ui.client.api.UiClientApi;
import com.dishanhai.sh_better_ui.client.api.UiExtensionRegistry;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/** 山海：每代客户端脚本独立持有的门面，重载时撤销注册并释放仍存活的界面与上下文。 */
public final class KubeJsUiApi extends UiClientApi implements AutoCloseable {
    private final List<Runnable> unregister = new ArrayList<>();
    private final Set<UiApiContext> contexts = new LinkedHashSet<>();
    private final Set<UiScreen> screens = Collections.newSetFromMap(new WeakHashMap<>());
    private final KubeJsTextApi text = new KubeJsTextApi(this);
    private final KubeJsRichTextApi richText = new KubeJsRichTextApi(this);
    private final KubeJsTextReadabilityApi readability = new KubeJsTextReadabilityApi(this);
    private final KubeJsPageBindings pages = new KubeJsPageBindings();
    private volatile boolean closed;

    public KubeJsTextApi text() {
        requireOpen();
        return text;
    }

    /** Bounded passive documents rendered by the native rich text view. */
    public KubeJsRichTextApi richText() {
        requireOpen();
        return richText;
    }

    public KubeJsTextReadabilityApi textReadability() {
        requireOpen();
        return readability;
    }

    synchronized boolean owns(UiScreen screen) {
        return !closed && screens.contains(screen);
    }

    public void registerPage(String id, Map<?, ?> definition) {
        requireOpen();
        pages.register(id, definition);
    }

    public CompletionStage<Boolean> open(String id, Map<?, ?> data) {
        requireOpen();
        return pages.open(id, data);
    }

    public CompletionStage<Boolean> update(String instanceId, Map<?, ?> updates) {
        requireOpen();
        return pages.update(instanceId, updates);
    }

    public CompletionStage<Boolean> closePage(String instanceId) {
        requireOpen();
        return pages.closePage(instanceId);
    }

    public PageSubscription on(String event, Consumer<KubeJsPageEvent> listener) {
        requireOpen();
        return pages.on(event, listener);
    }

    public interface PageSubscription extends AutoCloseable {
        @Override
        void close();
    }

    synchronized void registerTextPalette(ResourceLocation id, boolean body, UiTextEffects.PaletteAnimation animation,
                                          int[] colors) {
        requireOpen();
        unregister.add(UiTextEffects.registerScopedPalette(id, body, animation, colors));
    }

    /** 注册跟随当前脚本世代的扩展，工厂通过上下文复用其他分组与扩展。 */
    public synchronized UiExtensionRegistry.Registration registerExtension(
            String id, Function<UiApiContext, ?> factory) {
        requireOpen();
        Objects.requireNonNull(factory);
        var registration = extensions().register(id, context -> {
            requireOpen();
            return factory.apply(context);
        });
        unregister.add(registration::close);
        return registration;
    }

    @Override
    public synchronized PreviewRegistry.Registration registerDeveloperPreview(PreviewSpec spec) {
        requireOpen();
        Objects.requireNonNull(spec);
        var registration = super.registerDeveloperPreview(new PreviewSpec(spec.id(), spec.category(), spec.order(),
                spec.titleKey(), spec.preferredHeight(), spec.toolbarRightInset(), spec.code(), () -> {
                    requireOpen();
                    return spec.factory().get();
                }));
        unregister.add(registration::close);
        return registration;
    }

    @Override
    public synchronized UiApiContext context() {
        requireOpen();
        var context = super.context();
        contexts.add(context);
        context.manage(() -> {
            synchronized (this) {
                contexts.remove(context);
            }
        });
        return context;
    }

    @Override
    public UiScreenBuilder screen(Component title) {
        requireOpen();
        return super.screen(title).onError((screen, error) ->
                ShBetterUi.LOGGER.error("KubeJS UI callback failed", error));
    }

    @Override
    protected synchronized void onScreenCreated(UiScreen screen) {
        requireOpen();
        screens.add(screen);
    }

    /** 排队中的旧世代任务在执行前再次检查，重载后的新任务请使用新 SHUI 对象。 */
    @Override
    public void run(Runnable task) {
        requireOpen();
        Objects.requireNonNull(task);
        super.run(() -> {
            if (!closed) task.run();
        });
    }

    public boolean isClosed() {
        return closed;
    }

    /** 同步撤销扩展注册，客户端资源统一在客户端线程释放。重复关闭不重复执行。 */
    @Override
    public void close() {
        List<AutoCloseable> cleanup = new ArrayList<>();
        synchronized (this) {
            if (closed) return;
            closed = true;
            var pageScope = pages.retire();
            if (pageScope != null) cleanup.add(pageScope);
            unregister.forEach(Runnable::run);
            unregister.clear();
            var screenSnapshot = screens.stream().filter(screen -> !screen.isDisposed()).toList();
            if (!screenSnapshot.isEmpty()) {
                cleanup.add(() -> UiScreen.disposeAll(screenSnapshot));
            }
            for (var context : contexts) {
                if (!context.isClosed()) cleanup.add(context);
            }
            screens.clear();
            contexts.clear();
        }
        if (cleanup.isEmpty()) return;
        Minecraft.getInstance().execute(() -> {
            Throwable failure = null;
            for (var resource : cleanup) {
                try {
                    resource.close();
                } catch (Throwable error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
            if (failure != null) ShBetterUi.LOGGER.error("Could not release reloaded KubeJS UI resources", failure);
        });
    }

    void requireOpen() {
        if (closed) throw new IllegalStateException("KubeJS UI script generation is closed");
    }
}

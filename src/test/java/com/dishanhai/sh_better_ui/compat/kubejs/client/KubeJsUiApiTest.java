package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：脚本世代撤销注册及失效门面的拒绝行为，不加载 KubeJS 或启动客户端。 */
class KubeJsUiApiTest {
    @Test
    void closingGenerationReleasesScriptPalettesForReload() {
        var first = new KubeJsUiApi();
        try {
            var text = first.text();
            text.kjsRegister("reload_palette", new int[]{0x123456}, false, "static");
            assertTrue(text.kjsGetAllNames().contains("kubejs:reload_palette"));
            assertEquals("示例", text.format("&$reload_palette-示例").getString());
        } finally {
            first.close();
        }
        var second = new KubeJsUiApi();
        try {
            assertFalse(second.text().kjsGetAllNames().contains("kubejs:reload_palette"));
            assertDoesNotThrow(() -> second.text().kjsRegister("reload_palette", new int[]{0x654321},
                    false, "cycle"));
        } finally {
            second.close();
        }
    }

    @Test
    void closingGenerationSynchronouslyUnregistersItsFactories() {
        var oldGeneration = new KubeJsUiApi();
        var replacement = new KubeJsUiApi();
        String id = "sh_better_ui_test:script_generation";
        try {
            var oldRegistration = oldGeneration.registerExtension(id, context -> new Object());
            assertTrue(ShBetterUiApi.extensions().contains(id));

            oldGeneration.close();
            oldGeneration.close();
            assertTrue(oldGeneration.isClosed());
            assertFalse(ShBetterUiApi.extensions().contains(id));

            replacement.registerExtension(id, context -> new Object());
            oldRegistration.close();
            assertTrue(ShBetterUiApi.extensions().contains(id));
        } finally {
            oldGeneration.close();
            replacement.close();
        }
        assertFalse(ShBetterUiApi.extensions().contains(id));
    }

    @Test
    void closedGenerationRejectsNewStateAndScheduledWork() {
        var api = new KubeJsUiApi();
        api.close();

        assertThrows(IllegalStateException.class, api::context);
        assertThrows(IllegalStateException.class, () -> api.screen(Component.empty()));
        assertThrows(IllegalStateException.class, () -> api.run(() -> {}));
        assertThrows(IllegalStateException.class,
                () -> api.registerExtension("sh_better_ui_test:closed_generation", context -> new Object()));
    }

    @Test
    void declarativePagesAndSubscriptionsBelongToOneScriptGeneration() {
        String id = "kubejs:generation_example";
        var source = KubeJsPageSchemaTest.source();
        var old = new KubeJsUiApi();
        old.registerPage(id, source);
        var listener = old.on("pageOpened", event -> {});
        assertThrows(IllegalArgumentException.class, () -> old.registerPage(id, source));
        assertThrows(IllegalArgumentException.class, () -> old.on("networkAction", event -> {}));
        old.close();
        assertDoesNotThrow(listener::close);
        assertThrows(IllegalStateException.class, () -> old.registerPage(id, source));
        assertThrows(IllegalStateException.class, () -> old.on("pageOpened", event -> {}));
        try (var current = new KubeJsUiApi()) {
            assertDoesNotThrow(() -> current.registerPage(id, source));
        }
    }

    @Test
    void closingGenerationRemovesItsDeveloperPreviews() {
        var oldGeneration = new KubeJsUiApi();
        var replacement = new KubeJsUiApi();
        var spec = new PreviewSpec("sh_better_ui_test:preview_generation", PreviewSpec.Category.CONTROLS, 0,
                "gui.done", () -> host -> {});
        try {
            var oldRegistration = oldGeneration.registerDeveloperPreview(spec);
            assertTrue(ShBetterUiApi.developerPreviews().find(spec.id()).isPresent());

            oldGeneration.close();
            assertTrue(ShBetterUiApi.developerPreviews().find(spec.id()).isEmpty());
            assertThrows(IllegalStateException.class, () -> oldGeneration.registerDeveloperPreview(spec));

            replacement.registerDeveloperPreview(spec);
            oldRegistration.close();
            assertTrue(ShBetterUiApi.developerPreviews().find(spec.id()).isPresent());
        } finally {
            oldGeneration.close();
            replacement.close();
        }
        assertTrue(ShBetterUiApi.developerPreviews().find(spec.id()).isEmpty());
    }

    @Test
    void explicitlyClosedContextsReleaseResourcesBeforeGenerationCleanup() {
        var api = new KubeJsUiApi();
        var releases = new AtomicInteger();
        var context = api.context();
        context.manage(releases::incrementAndGet);

        context.close();
        assertEquals(1, releases.get());
        assertDoesNotThrow(api::close);
        context.close();
        assertEquals(1, releases.get());
    }

    @Test
    void scriptInitializationFailureDisposesThePageAndReleasesResources() {
        var api = new KubeJsUiApi();
        var releases = new AtomicInteger();
        var screen = api.screen(Component.empty()).backButton(false).onInit(page -> {
            page.context().manage(releases::incrementAndGet);
            throw new IllegalStateException("script initialization");
        }).build();

        assertDoesNotThrow(() -> initialize(screen));

        assertTrue(screen.isDisposed());
        assertTrue(screen.context().isClosed());
        assertEquals(1, releases.get());
        assertDoesNotThrow(api::close);
    }

    @Test
    void scriptTickFailureIsConsumedAfterPageResourcesAreReleased() {
        var api = new KubeJsUiApi();
        var releases = new AtomicInteger();
        var ticks = new AtomicInteger();
        var screen = api.screen(Component.empty()).backButton(false)
                .onInit(page -> page.context().manage(releases::incrementAndGet))
                .onTick(page -> {
                    ticks.incrementAndGet();
                    throw new IllegalStateException("script tick");
                }).build();
        assertDoesNotThrow(() -> initialize(screen));

        assertDoesNotThrow(screen::tick);
        assertDoesNotThrow(screen::tick);

        assertEquals(1, ticks.get());
        assertEquals(1, releases.get());
        assertTrue(screen.isDisposed());
        assertTrue(screen.context().isClosed());
        assertDoesNotThrow(api::close);
    }

    private static void initialize(UiScreen screen) throws ReflectiveOperationException {
        var init = UiScreen.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(screen);
    }
}

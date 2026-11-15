// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.DeveloperPreviewProvider;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleLifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform.NEOFORGE_1_21_1;
import static org.junit.jupiter.api.Assertions.*;

class ClientDeveloperPreviewsTest {
    @Test
    void onlyStartedModulesContributeAndStoppingRemovesTheirPreviews() {
        var unstartedCalls = new AtomicInteger();
        var sceneCreations = new AtomicInteger();
        var ready = new Provider(descriptor("ready").build(), () -> List.of(
                new PreviewSpec("preview_test:ready", PreviewSpec.Category.CONTROLS, 0, "gui.done", () -> {
                    sceneCreations.incrementAndGet();
                    return host -> {};
                })));
        Supplier<List<PreviewSpec>> unstarted = () -> {
            unstartedCalls.incrementAndGet();
            return List.of(preview("unexpected"));
        };
        var blocked = new Provider(descriptor("blocked")
                .dependency(UiId.parse("preview_test:absent"), VersionRange.ANY, true).build(),
                unstarted);
        var broken = new Provider(descriptor("broken").build(), unstarted) {
            @Override public void start(ModuleContext context) {
                throw new IllegalStateException("startup failed");
            }
        };
        var registry = ShBetterUiApi.developerPreviews();
        try (var host = new KernelHost(NEOFORGE_1_21_1, new EnginePublication())) {
            start(host, ready, blocked, broken);
            var modules = host.lifecycle().snapshot().modules();
            assertEquals(ModuleLifecycle.RUNNING, modules.get(ready.descriptor().id()));
            assertEquals(ModuleLifecycle.BLOCKED, modules.get(blocked.descriptor().id()));
            assertEquals(ModuleLifecycle.FAILED, modules.get(broken.descriptor().id()));
            assertEquals(0, unstartedCalls.get());
            assertEquals(0, sceneCreations.get());
            assertTrue(registry.find("preview_test:ready").isPresent());
            host.stopModule(ready.descriptor().id());
            assertTrue(registry.find("preview_test:ready").isEmpty());
        }
    }

    @Test
    void rejectsForeignAndConflictingIdsWithoutPartiallyPublishingAProvider() {
        var registry = ShBetterUiApi.developerPreviews();
        var foreign = new PreviewSpec("unrelated:foreign", PreviewSpec.Category.CONTROLS, 0, "gui.done",
                () -> fail("Registration must not create the scene"));
        var invalid = new Provider(descriptor("invalid").build(), () -> List.of(preview("partial"), foreign));
        var conflicts = new Provider(descriptor("conflicts").build(),
                () -> List.of(preview("also_partial"), preview("reserved")));
        try (var original = registry.register(preview("reserved"));
             var host = new KernelHost(NEOFORGE_1_21_1, new EnginePublication())) {
            var prior = registry.entries();
            start(host, invalid, conflicts);
            assertSame(prior, registry.entries());
            var modules = host.lifecycle().snapshot().modules();
            assertEquals(ModuleLifecycle.RUNNING, modules.get(invalid.descriptor().id()));
            assertEquals(ModuleLifecycle.RUNNING, modules.get(conflicts.descriptor().id()));
        }
    }

    private static void start(KernelHost host, Provider... providers) {
        var candidates = java.util.Arrays.stream(providers)
                .map(provider -> new ModuleDiscovery.Candidate("preview_test", provider.descriptor().id().toString(),
                        provider, Set.of(), false)).toList();
        host.startNative(candidates, (extension, context) -> ClientDeveloperPreviews.mount(host, extension, context));
    }

    private static ModuleDescriptor.Builder descriptor(String name) {
        return ModuleDescriptor.builder().id("preview_test:" + name).version("1.0.0");
    }

    private static PreviewSpec preview(String name) {
        return new PreviewSpec("preview_test:" + name, PreviewSpec.Category.CONTROLS, 0, "gui.done",
                () -> fail("Registration must not create the scene"));
    }

    private static class Provider implements DeveloperPreviewProvider {
        private final ModuleDescriptor descriptor;
        private final Supplier<List<PreviewSpec>> previews;

        Provider(ModuleDescriptor descriptor, Supplier<List<PreviewSpec>> previews) {
            this.descriptor = descriptor;
            this.previews = previews;
        }

        @Override public ModuleDescriptor descriptor() { return descriptor; }
        @Override public void register(ModuleContext context) {}
        @Override public void start(ModuleContext context) {}
        @Override public void stop(ModuleContext context) {}
        @Override public List<PreviewSpec> developerPreviews() { return previews.get(); }
    }
}

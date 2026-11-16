package com.dishanhai.sh_better_ui.client.api.preview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category.CONTROLS;
import static com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec.Category.MATERIALS;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证图库目录的稳定顺序、惰性实例化和注册句柄。 */
class PreviewRegistryTest {
    @Test
    void catalogueOrdersDescriptorsAndKeepsFactoriesLazy() {
        var registry = new PreviewRegistry();
        var creations = new AtomicInteger();
        var builtin = new PreviewSpec("sh_better_ui:button", CONTROLS, 10, "gui.done", () -> {
            creations.incrementAndGet();
            return host -> {};
        });
        var addon = new PreviewSpec("addon:widget", CONTROLS, 10, "gui.done", () -> host -> {});
        var material = new PreviewSpec("addon:surface", MATERIALS, 0, "gui.done", () -> host -> {});

        registry.register(material);
        registry.register(builtin);
        registry.register(addon);

        var entries = registry.entries();
        assertEquals(List.of(addon, builtin, material), entries);
        assertSame(entries, registry.entries());
        assertThrows(UnsupportedOperationException.class, () -> entries.clear());
        assertEquals(List.of("addon:widget", "button", "addon:surface"), registry.ids());
        assertEquals(builtin, registry.find("button").orElseThrow());
        assertSame(registry.find("button").orElseThrow(), registry.find(builtin.id()).orElseThrow());
        assertEquals(0, creations.get());
    }

    @Test
    void closingAnOldHandleCannotRemoveItsReplacement() {
        var registry = new PreviewRegistry();
        var spec = new PreviewSpec("addon:widget", CONTROLS, 0, "gui.done", () -> host -> {});
        var original = registry.register(spec);
        assertThrows(IllegalStateException.class, () -> registry.register(spec));
        var originalSnapshot = registry.entries();

        original.close();
        assertEquals(List.of(), registry.entries());
        assertEquals(List.of(spec), originalSnapshot);
        try (var replacement = registry.register(spec)) {
            original.close();
            var registered = registry.find(spec.id()).orElseThrow();
            assertEquals(spec, registered);
            assertNotSame(originalSnapshot.getFirst(), registered);
        }
        assertTrue(registry.find(spec.id()).isEmpty());
    }
    @Test
    void dynamicSourcesAreLazyStableAndRevokeCleanly() {
        var registry = new PreviewRegistry();
        var spec = new PreviewSpec("addon:live", CONTROLS, 0, "gui.done", () -> host -> {});
        var source = new java.util.concurrent.atomic.AtomicReference<List<PreviewSpec>>(List.of());
        var handle = registry.registerSource(source::get);
        source.set(List.of(spec));
        assertSame(spec, registry.find(spec.id()).orElseThrow());
        assertSame(registry.entries(), registry.entries());
        assertThrows(IllegalStateException.class, () -> registry.register(spec));
        assertThrows(IllegalStateException.class, () -> registry.registerSource(() -> List.of(spec)));
        handle.close();
        assertTrue(registry.entries().isEmpty());
    }
    @Test
    void staleSourceHandleCannotRemoveReregisteredSupplier() {
        var registry = new PreviewRegistry();
        var spec = new PreviewSpec("addon:live", CONTROLS, 0, "gui.done", () -> host -> {});
        java.util.function.Supplier<List<PreviewSpec>> supplier = () -> List.of(spec);
        var original = registry.registerSource(supplier);
        assertThrows(IllegalStateException.class, () -> registry.registerSource(supplier));
        original.close();
        var replacement = registry.registerSource(supplier);
        original.close();
        assertSame(spec, registry.find(spec.id()).orElseThrow());
        replacement.close();
        assertTrue(registry.entries().isEmpty());
    }

}

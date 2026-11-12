// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UiComponentFactoryTest {
    private static final UiId PAGE = UiId.parse("example:factory_page");
    private static final UiId CUSTOM = UiId.parse("example:styled_label");
    private static final UiId TEXT = UiBuiltinComponents.id("text");
    private static final Map<UiId, com.dishanhai.sh_better_ui.kernel.api.UiVersion> RENDERERS =
            Map.of(TEXT, UiBuiltinComponents.VERSION);

    @Test
    void localFactoriesStayPrivateUntilExplicitRemoteOptIn() {
        var registry = new UiComponentRegistry();
        var scope = new TestScope();
        var invocations = new AtomicInteger();
        var handle = registry.register(scope, descriptor(false), input -> {
            invocations.incrementAndGet();
            return new UiComponentAlternative(TEXT, Map.of("text", input.properties().get("caption")), Map.of());
        });
        var page = page(UiNode.builder("status", CUSTOM).build());
        assertTrue(registry.renderers(RENDERERS, false).containsKey(CUSTOM));
        assertFalse(registry.renderers(RENDERERS, true).containsKey(CUSTOM));
        assertEquals(UiPageProblem.Code.REMOTE_DENIED, registry.resolve(page, true, RENDERERS).problems().get(0).code());
        assertEquals(0, invocations.get());
        var projected = registry.resolve(page, false, RENDERERS).page().orElseThrow().root();
        assertEquals("status", projected.id());
        assertEquals(new UiValue.Text("Ready"), projected.properties().get("text"));
        assertEquals(1, invocations.get());
        scope.close();
        assertEquals(RegistrationHandle.Status.REVOKED, handle.status());
        assertEquals(UiPageProblem.Code.COMPONENT_MISSING,
                registry.resolve(page, false, RENDERERS).problems().get(0).code());
    }

    @Test
    void remoteInputsAreValidatedBeforeCallingTheFactoryAndProjectionIsRevalidated() {
        var registry = new UiComponentRegistry();
        var calls = new AtomicInteger();
        var scope = new TestScope();
        var handle = registry.register(scope, descriptor(true), input -> {
            calls.incrementAndGet();
            assertThrows(UnsupportedOperationException.class, input.properties()::clear);
            return new UiComponentAlternative(TEXT, Map.of("text", new UiValue.Int(4)), Map.of());
        });
        var page = page(UiNode.builder("status", CUSTOM).property("caption", new UiValue.Bool(false)).build());
        assertEquals(UiPageProblem.Code.INVALID_PROPERTY,
                registry.resolve(page, true, RENDERERS).problems().get(0).code());
        assertEquals(0, calls.get());

        var valid = page(UiNode.builder("status", CUSTOM).property("caption", new UiValue.Text("Loaded")).build());
        assertTrue(registry.renderers(RENDERERS, true).containsKey(CUSTOM));
        assertEquals(UiPageProblem.Code.INVALID_PROPERTY,
                registry.resolve(valid, true, RENDERERS).problems().get(0).code());
        assertEquals(1, calls.get());
        handle.close();

        registry.register(scope, descriptor(true), input -> new UiComponentAlternative(TEXT, Map.of(),
                Map.of("text", "foreign.data")));
        assertEquals(UiPageProblem.Code.INVALID_PROPERTY,
                registry.resolve(valid, true, RENDERERS).problems().get(0).code());
    }

    @Test
    void projectionsPreserveDeclaredBindingsAndSafetyFlags() {
        var registry = new UiComponentRegistry();
        var scope = new TestScope();
        registry.register(scope, descriptor(true), input -> new UiComponentAlternative(TEXT,
                Map.of("visible", new UiValue.Bool(true)), Map.of("text", input.bindings().get("caption"))));
        var page = UiPageDefinition.builder(PAGE).field("data.caption", UiValueSchema.text(32))
                .root(UiNode.builder("status", CUSTOM).bind("caption", "data.caption")
                        .property("visible", new UiValue.Bool(false)).build()).build();
        var projected = registry.resolve(page, true, RENDERERS).page().orElseThrow().root();
        assertEquals("data.caption", projected.bindings().get("text"));
        assertEquals(new UiValue.Bool(false), projected.properties().get("visible"));
        assertEquals(TEXT, projected.typeId());
        assertThrows(IllegalArgumentException.class, () -> registry.register(scope, descriptor(true), input -> null));
    }

    private static UiPageDefinition page(UiNode root) {
        return UiPageDefinition.builder(PAGE).root(root).build();
    }

    private static UiComponentDescriptor descriptor(boolean remote) {
        Map<String, UiPropertyDescriptor> properties = new HashMap<>();
        properties.put("caption", new UiPropertyDescriptor(UiValueSchema.text(64),
                Optional.of(new UiValue.Text("Ready")), true, true, true));
        properties.put("visible", new UiPropertyDescriptor(UiValueSchema.bool(), Optional.empty(), false, true, true));
        return new UiComponentDescriptor(CUSTOM, UiBuiltinComponents.VERSION, properties, Set.of(), remote, false,
                false, Optional.empty());
    }

    private static final class TestScope implements ScopeHandle {
        private final List<AutoCloseable> resources = new ArrayList<>();
        private boolean open = true;
        @Override public UiId id() { return PAGE; }
        @Override public UiId owner() { return PAGE; }
        @Override public ScopeKind kind() { return ScopeKind.MODULE; }
        @Override public long generation() { return 1; }
        @Override public boolean isOpen() { return open; }
        @Override public ScopeHandle openChild(ScopeKind kind, UiId id) { throw new UnsupportedOperationException(); }
        @Override public void track(AutoCloseable resource) { resources.add(resource); }
        @Override public OwnershipLease claim(ModuleDescriptor.ResourceClaim claim, AutoCloseable release) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void close() {
            open = false;
            resources.forEach(resource -> assertDoesNotThrow(resource::close));
        }
    }
}

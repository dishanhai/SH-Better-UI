// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Registry of component schemas for validation. A host separately supplies the renderers it can mount. */
public final class UiComponentRegistry {
    private static final List<String> COMMON_PROPERTIES = List.of("visible", "enabled", "narration", "tooltip",
            "width", "height");
    private final Map<UiId, UiComponentDescriptor> descriptors = new HashMap<>(UiBuiltinComponents.descriptors());
    private final Map<UiId, UiComponentFactory> factories = new HashMap<>();

    public synchronized Optional<UiComponentDescriptor> find(UiId id) {
        return Optional.ofNullable(descriptors.get(Objects.requireNonNull(id)));
    }

    public synchronized Map<UiId, UiComponentDescriptor> snapshot() {
        return Map.copyOf(descriptors);
    }

    public synchronized RegistrationHandle register(ScopeHandle owner, UiComponentDescriptor descriptor) {
        return register(owner, descriptor, null);
    }

    public synchronized RegistrationHandle register(ScopeHandle owner, UiComponentDescriptor descriptor,
                                                    UiComponentFactory factory) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(descriptor, "descriptor");
        if (!owner.isOpen() || !owner.owner().namespace().equals(descriptor.typeId().namespace())) {
            throw new IllegalArgumentException("Component registration requires an open scope in its namespace");
        }
        if (descriptors.putIfAbsent(descriptor.typeId(), descriptor) != null) {
            throw new IllegalArgumentException("Component type already registered: " + descriptor.typeId());
        }
        if (factory != null) {
            factories.put(descriptor.typeId(), factory);
        }
        RegistrationHandle handle = new RegistrationHandle() {
            private volatile Status status = Status.ACTIVE;

            @Override
            public Status status() {
                return status;
            }

            @Override
            public void close() {
                synchronized (UiComponentRegistry.this) {
                    if (status == Status.ACTIVE) {
                        descriptors.remove(descriptor.typeId(), descriptor);
                        factories.remove(descriptor.typeId());
                        status = Status.REVOKED;
                    }
                }
            }
        };
        try {
            owner.track(handle);
        } catch (RuntimeException failure) {
            handle.close();
            throw failure;
        }
        return handle;
    }

    public synchronized Map<UiId, UiVersion> renderers(Map<UiId, UiVersion> nativeRenderers, boolean remote) {
        Map<UiId, UiVersion> available = new HashMap<>(nativeRenderers);
        factories.keySet().forEach(id -> {
            UiComponentDescriptor descriptor = descriptors.get(id);
            if (!remote || descriptor.remoteAllowed()) {
                available.put(id, descriptor.version());
            }
        });
        return Map.copyOf(available);
    }

    public UiResolvedPage resolve(UiPageDefinition page, boolean remote, Map<UiId, UiVersion> nativeRenderers) {
        Map<UiId, UiComponentDescriptor> definitions;
        Map<UiId, UiComponentFactory> activeFactories;
        Map<UiId, UiVersion> available;
        synchronized (this) {
            definitions = Map.copyOf(descriptors);
            activeFactories = Map.copyOf(factories);
            available = renderers(nativeRenderers, remote);
        }
        UiPageValidator validator = new UiPageValidator(definitions);
        UiResolvedPage source = validator.resolve(page, remote, available);
        if (source.page().isEmpty() || activeFactories.isEmpty()) {
            return source;
        }
        List<UiPageProblem> problems = new ArrayList<>();
        List<String> projected = new ArrayList<>(source.degradations());
        UiPageDefinition accepted = source.page().orElseThrow();
        UiNode root = project(accepted.root(), "root", definitions, activeFactories, nativeRenderers, problems, projected);
        if (!problems.isEmpty()) {
            return new UiResolvedPage(Optional.empty(), problems, projected);
        }
        if (root == accepted.root()) {
            return source;
        }
        UiPageDefinition effective = new UiPageDefinition(accepted.id(), accepted.schemaVersion(), accepted.type(), root,
                accepted.dataSchema(), accepted.actions(), accepted.options());
        UiResolvedPage result = validator.resolve(effective, remote, available);
        projected.addAll(result.degradations());
        return new UiResolvedPage(result.page(), result.problems(), projected);
    }

    private static UiNode project(UiNode node, String path, Map<UiId, UiComponentDescriptor> descriptors,
                                  Map<UiId, UiComponentFactory> factories, Map<UiId, UiVersion> nativeRenderers,
                                  List<UiPageProblem> problems, List<String> projected) {
        UiComponentFactory factory = factories.get(node.typeId());
        UiNode rendered = node;
        if (factory != null) {
            try {
                UiComponentDescriptor descriptor = descriptors.get(node.typeId());
                Map<String, UiValue> properties = new HashMap<>(node.properties());
                descriptor.properties().forEach((name, property) -> {
                    if (!node.bindings().containsKey(name)) {
                        property.defaultValue().ifPresent(value -> properties.putIfAbsent(name, value));
                    }
                });
                UiComponentAlternative presentation = Objects.requireNonNull(
                        factory.project(new UiComponentFactory.Inputs(properties, node.bindings())), "presentation");
                if (!UiBuiltinComponents.descriptors().containsKey(presentation.typeId())
                        || !nativeRenderers.containsKey(presentation.typeId())
                        || !node.bindings().values().containsAll(presentation.bindings().values())) {
                    throw new IllegalArgumentException("Projection contains an unavailable widget or undeclared input");
                }
                Map<String, UiValue> output = new HashMap<>(presentation.properties());
                Map<String, String> bindings = new HashMap<>(presentation.bindings());
                for (String common : COMMON_PROPERTIES) {
                    if (node.properties().containsKey(common)) {
                        output.put(common, node.properties().get(common));
                        bindings.remove(common);
                    } else if (node.bindings().containsKey(common)) {
                        bindings.put(common, node.bindings().get(common));
                        output.remove(common);
                    }
                }
                rendered = new UiNode(node.id(), presentation.typeId(), output, bindings, node.action(), node.children(),
                        UiNodeFallback.REJECT);
                projected.add(path + " (" + node.id() + "): " + node.typeId() + " -> " + rendered.typeId());
            } catch (RuntimeException failure) {
                problems.add(new UiPageProblem(UiPageProblem.Code.INVALID_PROPERTY, path, "typeId",
                        "valid built-in projection", node.typeId().toString(),
                        "Check the component factory's output type, properties and bindings."));
                return node;
            }
        }
        List<UiNode> children = new ArrayList<>(node.children().size());
        boolean changed = rendered != node;
        for (int i = 0; i < node.children().size(); i++) {
            UiNode child = node.children().get(i);
            UiNode effective = project(child, path + ".children[" + i + "]", descriptors, factories, nativeRenderers,
                    problems, projected);
            children.add(effective);
            changed |= effective != child;
        }
        return changed ? new UiNode(rendered.id(), rendered.typeId(), rendered.properties(), rendered.bindings(),
                rendered.action(), children, rendered.fallback()) : rendered;
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable discovery metadata. Permissions, schemas and resource claims are declarations. */
public record ModuleDescriptor(UiId id, UiVersion version, int apiContractVersion,
                               Map<CapabilityKey, VersionRange> requiredCapabilities,
                               Map<CapabilityKey, VersionRange> optionalCapabilities,
                               Map<CapabilityKey, CapabilityOffer> providedCapabilities,
                               Map<UiId, Dependency> dependencies, Set<UiId> conflicts,
                               InitializationPhase initializationPhase, ExecutionAffinity affinity,
                               Set<UIPermission> requestedPermissions, Optional<SchemaReference> configurationSchema,
                               Set<ResourceClaim> resourceClaims, Set<Platform> platforms) {
    public ModuleDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(initializationPhase, "initializationPhase");
        Objects.requireNonNull(affinity, "affinity");
        Objects.requireNonNull(configurationSchema, "configurationSchema");
        requiredCapabilities = Map.copyOf(requiredCapabilities);
        optionalCapabilities = Map.copyOf(optionalCapabilities);
        providedCapabilities = Map.copyOf(providedCapabilities);
        dependencies = Map.copyOf(dependencies);
        conflicts = Set.copyOf(conflicts);
        requestedPermissions = Set.copyOf(requestedPermissions);
        resourceClaims = Set.copyOf(resourceClaims);
        platforms = Set.copyOf(platforms);
        if (apiContractVersion < 1 || platforms.isEmpty()) {
            throw new IllegalArgumentException("Module requires a positive contract version and at least one platform: " + id);
        }
        if (dependencies.containsKey(id) || conflicts.contains(id)) {
            throw new IllegalArgumentException("A module cannot depend on or conflict with itself: " + id);
        }
        if (requiredCapabilities.keySet().stream().anyMatch(optionalCapabilities::containsKey)) {
            throw new IllegalArgumentException("A capability cannot be both required and optional: " + id);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Dependency(VersionRange versions, boolean required) {
        public Dependency {
            Objects.requireNonNull(versions, "versions");
        }
    }

    public record CapabilityOffer(UiVersion version, int priority, boolean fallback) {
        public CapabilityOffer {
            Objects.requireNonNull(version, "version");
        }
    }

    /** Refers to the versioned schema supplied by the owning module. */
    public record SchemaReference(UiId id, int version) {
        public SchemaReference {
            Objects.requireNonNull(id, "id");
            if (version < 1) {
                throw new IllegalArgumentException("Schema version must be positive: " + id);
            }
        }
    }

    public record ResourceClaim(ResourceKind kind, UiId id) {
        public ResourceClaim {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }

    public enum ResourceKind {
        KEYBOARD_FOCUS, POINTER_CAPTURE, ANIMATION_PROPERTY, LAYOUT_MUTATION,
        MODAL_LAYER, RENDER_TARGET, TEXTURE_RESOURCE, EVENT_SUBSCRIPTION
    }

    public enum InitializationPhase { CORE, SERVICE, EXTENSION }

    public enum ExecutionAffinity { UI_THREAD, RENDER_THREAD, BACKGROUND }

    public enum Platform { FORGE_1_20_1, NEOFORGE_1_21_1, NEOFORGE_26_1_2, NEOFORGE_26_2 }

    public static final class Builder {
        private UiId id;
        private UiVersion version;
        private int apiContractVersion = UIEngine.CONTRACT_VERSION;
        private final Map<CapabilityKey, VersionRange> requiredCapabilities = new HashMap<>();
        private final Map<CapabilityKey, VersionRange> optionalCapabilities = new HashMap<>();
        private final Map<CapabilityKey, CapabilityOffer> providedCapabilities = new HashMap<>();
        private final Map<UiId, Dependency> dependencies = new HashMap<>();
        private final Set<UiId> conflicts = new HashSet<>();
        private InitializationPhase initializationPhase = InitializationPhase.EXTENSION;
        private ExecutionAffinity affinity = ExecutionAffinity.UI_THREAD;
        private final Set<UIPermission> requestedPermissions = new HashSet<>();
        private Optional<SchemaReference> configurationSchema = Optional.empty();
        private final Set<ResourceClaim> resourceClaims = new HashSet<>();
        private Set<Platform> platforms = Set.of(Platform.FORGE_1_20_1, Platform.NEOFORGE_1_21_1);

        private Builder() {
        }

        public Builder id(String value) {
            return id(UiId.parse(value));
        }

        public Builder id(UiId value) {
            id = Objects.requireNonNull(value, "id");
            return this;
        }

        public Builder version(String value) {
            return version(UiVersion.parse(value));
        }

        public Builder version(UiVersion value) {
            version = Objects.requireNonNull(value, "version");
            return this;
        }

        public Builder apiContractVersion(int value) {
            apiContractVersion = value;
            return this;
        }

        public Builder requiredCapability(CapabilityKey key, VersionRange versions) {
            declare(requiredCapabilities, key, versions);
            return this;
        }

        public Builder optionalCapability(CapabilityKey key, VersionRange versions) {
            declare(optionalCapabilities, key, versions);
            return this;
        }

        public Builder providedCapability(CapabilityKey key, UiVersion providedVersion) {
            return providedCapability(key, new CapabilityOffer(providedVersion, 0, false));
        }

        public Builder providedCapability(CapabilityKey key, CapabilityOffer offer) {
            declare(providedCapabilities, key, offer);
            return this;
        }

        public Builder dependency(UiId module, VersionRange versions, boolean required) {
            declare(dependencies, module, new Dependency(versions, required));
            return this;
        }

        public Builder conflict(UiId module) {
            conflicts.add(Objects.requireNonNull(module, "module"));
            return this;
        }

        public Builder initializationPhase(InitializationPhase value) {
            initializationPhase = Objects.requireNonNull(value, "initializationPhase");
            return this;
        }

        public Builder affinity(ExecutionAffinity value) {
            affinity = Objects.requireNonNull(value, "affinity");
            return this;
        }

        public Builder requestPermission(UIPermission permission) {
            requestedPermissions.add(Objects.requireNonNull(permission, "permission"));
            return this;
        }

        public Builder configurationSchema(SchemaReference schema) {
            configurationSchema = Optional.of(schema);
            return this;
        }

        public Builder claimResource(ResourceClaim claim) {
            resourceClaims.add(Objects.requireNonNull(claim, "claim"));
            return this;
        }

        public Builder platforms(Set<Platform> value) {
            platforms = Set.copyOf(value);
            return this;
        }

        public ModuleDescriptor build() {
            return new ModuleDescriptor(id, version, apiContractVersion, requiredCapabilities, optionalCapabilities,
                    providedCapabilities, dependencies, conflicts, initializationPhase, affinity, requestedPermissions,
                    configurationSchema, resourceClaims, platforms);
        }

        private static <K, V> void declare(Map<K, V> values, K key, V value) {
            if (values.putIfAbsent(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")) != null) {
                throw new IllegalArgumentException("Duplicate module declaration: " + key);
            }
        }
    }
}

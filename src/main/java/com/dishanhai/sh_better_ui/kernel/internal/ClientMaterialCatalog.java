// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalogSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialPreset;
import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Lifecycle-bound metadata publisher. Only extension validation consults the client resource manager. */
final class ClientMaterialCatalog implements MaterialCatalog, AutoCloseable {
    private static final String BUILTIN = "/assets/sh_better_ui/material_catalog/catalog.json";
    private final KernelHost host;
    private final String loader;
    private final Predicate<String> resourceExists;
    private final MaterialCatalogSnapshot builtins;
    private final MaterialPresetCodec presets;
    private final Map<String, Binding> registrations = new LinkedHashMap<>();
    private volatile MaterialCatalogSnapshot current;

    ClientMaterialCatalog(KernelHost host, ModuleDescriptor.Platform platform, Predicate<String> resourceExists) {
        this.host = host;
        this.loader = platform == ModuleDescriptor.Platform.FORGE_1_20_1 ? "forge" : "neoforge";
        this.resourceExists = resourceExists;
        var manifest = loadBuiltin();
        this.builtins = new MaterialCatalogSnapshot(UiVersion.parse(manifest.catalogVersion()), manifest.contentHash(), 0, manifest.materials());
        this.current = builtins;
        this.presets = new MaterialPresetCodec(manifest.presetSchemaVersion(), manifest.migrations());
    }

    private static Manifest loadBuiltin() {
        try (var stream = ClientMaterialCatalog.class.getResourceAsStream(BUILTIN)) {
            if (stream == null) throw new IllegalStateException("Missing bundled material catalog: " + BUILTIN);
            var manifest = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Manifest.class);
            if (!MaterialEntry.SCHEMA_VERSION.equals(manifest.schemaVersion())) throw new IllegalStateException("Unsupported bundled catalog schema");
            return manifest;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read bundled material catalog", failure);
        }
    }

    @Override public MaterialCatalogSnapshot snapshot() { return current; }

    @Override public String exportPreset(String id, Map<String, Object> values) { return presets.export(current, id, values); }
    @Override public MaterialPreset importPreset(String json) { return presets.read(current, json); }

    @Override
    public void validate(ModuleContext context, MaterialEntry entry) {
        host.ownedScope(context, context.scope());
        validateExtension(context, entry, null);
    }

    @Override
    public Registration register(ModuleContext context, MaterialEntry entry) {
        var scope = host.ownedScope(context, context.scope());
        validateExtension(context, entry, null);
        var binding = new Binding(context, entry);
        binding.lifetime = host.onScopeClose(scope, () -> {
            if (registrations.remove(entry.id(), binding)) publish();
        });
        registrations.put(entry.id(), binding);
        publish();
        return binding;
    }

    private void validateExtension(ModuleContext context, MaterialEntry entry, Binding previous) {
        var owner = context.descriptor().id().namespace();
        if (owner.equals("sh_better_ui") || !owner.equals(UiId.parse(entry.id()).namespace())
                || entry.nativePreviewId() != null && !owner.equals(UiId.parse(entry.nativePreviewId()).namespace())) {
            throw new SecurityException("Extension IDs and native preview IDs must use the module namespace");
        }
        for (String alias : entry.aliases()) {
            String normalized = MaterialCatalogSnapshot.normalize(alias);
            if (normalized.contains(":") && !normalized.startsWith(owner + ":")) throw new SecurityException("Foreign namespaced alias");
        }
        if (entry.replacementId() != null && builtins.entries().stream().noneMatch(builtin -> builtin.id().equals(entry.replacementId()))) {
            throw new IllegalArgumentException("A live extension may redirect only to a bundled material");
        }
        if (!available(context, entry)) throw new IllegalArgumentException("Material loader, capability or resource is unavailable: " + entry.id());
        var candidates = new ArrayList<>(builtins.entries());
        registrations.values().stream().filter(binding -> binding != previous).forEach(binding -> candidates.add(binding.entry));
        candidates.add(entry);
        new MaterialCatalogSnapshot(builtins.version(), builtins.contentHash(), current.generation(), candidates);
    }

    private boolean available(ModuleContext context, MaterialEntry entry) {
        if (entry.loaderCompatibility().get(loader).equals("unsupported")) return false;
        var capabilities = context.engine().capabilities();
        return entry.requiredCapabilities().stream().allMatch(id -> capabilities.supports(new CapabilityKey(UiId.parse(id))))
                && entry.requiredResources().stream().allMatch(resourceExists);
    }

    void refreshResources() {
        host.assertOwner();
        for (var binding : registrations.values()) {
            binding.usable = binding.lifetime.isActive() && available(binding.context, binding.entry);
        }
        publish();
    }

    private void publish() {
        var entries = new ArrayList<>(builtins.entries());
        for (var binding : registrations.values()) {
            if (binding.usable && binding.lifetime.isActive()) entries.add(binding.entry);
        }
        var prior = current;
        var next = new MaterialCatalogSnapshot(builtins.version(), builtins.contentHash(), prior.generation() + 1, entries);
        if (!prior.entries().equals(next.entries())) current = next;
    }

    @Override
    public void close() {
        host.assertOwner();
        registrations.clear();
        current = new MaterialCatalogSnapshot(builtins.version(), builtins.contentHash(), current.generation() + 1, builtins.entries());
    }

    private final class Binding implements Registration {
        final ModuleContext context;
        volatile MaterialEntry entry;
        Subscription lifetime;
        boolean usable = true;

        Binding(ModuleContext context, MaterialEntry entry) {
            this.context = context;
            this.entry = entry;
        }

        @Override public boolean isActive() {
            var value = entry;
            return current.entries().stream().anyMatch(candidate -> candidate == value);
        }

        @Override public void replace(MaterialEntry next) {
            host.ownedScope(context, context.scope());
            if (!lifetime.isActive() || registrations.get(entry.id()) != this) throw new IllegalStateException("Material registration is closed");
            if (!entry.id().equals(next.id())) throw new IllegalArgumentException("Replacement must retain the stable material ID");
            if (Objects.equals(entry, next)) return;
            validateExtension(context, next, this);
            entry = next;
            usable = true;
            publish();
        }

        @Override public void close() { lifetime.close(); }
    }

    private record Manifest(String schemaVersion, String catalogVersion, String presetSchemaVersion, String contentHash,
                            List<MaterialEntry> materials, List<MaterialPresetCodec.Migration> migrations) {}
}

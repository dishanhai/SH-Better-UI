// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One catalog for background selection, rendering and developer settings. */
public final class BackgroundRegistry {
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override void close();
    }

    private record Entry(BackgroundSpec spec, Runnable removeProfile) {}

    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private List<BackgroundSpec> snapshot = List.of();

    public synchronized Registration register(BackgroundSpec spec) {
        Objects.requireNonNull(spec);
        if (byId.containsKey(spec.id())) throw new IllegalArgumentException("Duplicate background: " + spec.id());
        Runnable removeProfile = null;
        if (spec.defaultProfile() != null) {
            removeProfile = ThemeEffects.registerProfile(spec.settingsGroup(), spec.defaultProfile());
        } else if (spec.settingsGroup() != null && !ThemeEffects.groups().contains(spec.settingsGroup())) {
            throw new IllegalArgumentException("Unknown background profile: " + spec.settingsGroup());
        }
        // Give every registration its own identity, even if the same spec is registered again.
        var entry = new Entry(new BackgroundSpec(spec.id(), spec.titleKey(), spec.settingsGroup(),
                spec.defaultProfile(), spec.factory()), removeProfile);
        byId.put(spec.id(), entry);
        refresh();
        return () -> unregister(entry);
    }

    public synchronized List<BackgroundSpec> entries() { return snapshot; }

    public synchronized Optional<BackgroundSpec> find(String id) {
        var entry = byId.get(id);
        // Preserve selections saved while reveal was offered as a background.
        if (entry == null && "reveal".equals(id)) entry = byId.get("photonKnot");
        return entry == null ? Optional.empty() : Optional.of(entry.spec());
    }

    public BackgroundLayer layer(ResourceLocation logo) { return new BackgroundLayer(this, logo); }

    private synchronized void unregister(Entry entry) {
        if (byId.get(entry.spec().id()) == entry) {
            byId.remove(entry.spec().id());
            if (entry.removeProfile() != null) entry.removeProfile().run();
            refresh();
        }
    }

    private void refresh() { snapshot = byId.values().stream().map(Entry::spec).toList(); }
}

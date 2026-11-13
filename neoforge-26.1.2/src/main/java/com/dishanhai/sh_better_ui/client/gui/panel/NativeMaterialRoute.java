// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;

/** Binds a UUK material and its native preview to their original registration generations. */
record NativeMaterialRoute(MaterialCatalog catalog, MaterialEntry entry, PreviewSpec preview) {
    static MaterialCatalog currentCatalog() {
        return UnifiedUI.isAvailable() ? UnifiedUI.get().find(MaterialCatalog.KEY).orElse(null) : null;
    }

    static NativeMaterialRoute require(String id, PreviewRegistry registry) {
        var catalog = currentCatalog();
        if (catalog == null) throw new IllegalStateException("Material catalog unavailable");
        var entry = catalog.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown material: " + id));
        if (entry.nativePreviewId() == null) {
            throw new IllegalArgumentException("No registered native preview for " + entry.id());
        }
        var preview = registry.find(entry.nativePreviewId())
                .filter(spec -> spec.id().equals(entry.nativePreviewId()))
                .orElseThrow(() -> new IllegalArgumentException("No registered native preview for " + entry.id()));
        return new NativeMaterialRoute(catalog, entry, preview);
    }

    boolean available(PreviewRegistry registry) { return available(registry, currentCatalog()); }

    boolean available(PreviewRegistry registry, MaterialCatalog current) {
        return current == catalog && catalog.find(entry.id()).orElse(null) == entry
                && registry.find(preview.id()).orElse(null) == preview;
    }
}

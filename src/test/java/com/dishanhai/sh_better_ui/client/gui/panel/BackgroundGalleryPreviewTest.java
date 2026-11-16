// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.BackgroundRegistry;
import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundGalleryPreviewTest {
    @Test void liveCatalogIncludesLateAddonsAndRevocationsWithoutCreatingRenderers() {
        var backgrounds = new BackgroundRegistry();
        var previews = new PreviewRegistry();
        var creations = new AtomicInteger();
        previews.registerSource(BackgroundGalleryPreview.catalog(backgrounds));
        assertTrue(previews.entries().isEmpty());
        var registration = backgrounds.register(new BackgroundSpec("addon:aurora", "addon.title", null, null,
                logo -> { creations.incrementAndGet(); return (g,f,a,m,x,y,n,p) -> {}; }));
        var entries = previews.entries();
        assertEquals(1, entries.size());
        assertEquals(PreviewSpec.Category.BACKGROUNDS, entries.get(0).category());
        assertSame(entries, previews.entries());
        assertEquals(0, creations.get());
        assertEquals(entries.get(0), previews.find(entries.get(0).id()).orElseThrow());
        registration.close();
        assertTrue(previews.entries().isEmpty());
        assertTrue(previews.find(entries.get(0).id()).isEmpty());
        assertEquals(0, creations.get());
    }
    @Test void idsPreserveCaseAndNamespaceWithoutCollisions() {
        assertNotEquals(BackgroundGalleryPreview.previewId("dotMatrix"), BackgroundGalleryPreview.previewId("dotmatrix"));
        assertNotEquals(BackgroundGalleryPreview.previewId("addon:aB"), BackgroundGalleryPreview.previewId("addon:a_42_"));
        assertNotEquals(BackgroundGalleryPreview.previewId("one:effect"), BackgroundGalleryPreview.previewId("two:effect"));
    }
    @Test void unrelatedRegistryChangesDoNotRemountTheSelectedScene() {
        var backgrounds = new BackgroundRegistry();
        var source = BackgroundGalleryPreview.catalog(backgrounds);
        var a = new BackgroundSpec("addon:first", "addon.first", null, null,
                logo -> (g,f,v,m,x,y,n,p) -> {});
        var first = backgrounds.register(a);
        var descriptor = source.get().get(0);
        var second = backgrounds.register(new BackgroundSpec("addon:second", "addon.second", null, null,
                logo -> (g,f,v,m,x,y,n,p) -> {}));
        assertSame(descriptor, source.get().get(0));
        second.close();
        assertSame(descriptor, source.get().get(0));
        first.close();
        assertTrue(source.get().isEmpty());
        backgrounds.register(a);
        assertNotSame(descriptor, source.get().get(0));
    }

}

// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class RichTextImagesTest {
    private static final RichDocument DOCUMENT = new RichDocument(List.of(new ImageNode(
            UiId.parse("test:node"), UiId.parse("test:textures/missing.png"), 16, 16, "missing")));

    @Test void negativeResultsStayCachedUntilResourceGenerationChanges() throws InterruptedException {
        var images = new RichTextImages();
        var lookups = new AtomicInteger();
        var resources = new MissingResources(location -> lookups.incrementAndGet());
        try {
            awaitFailure(images, resources);
            for (int i = 0; i < 60; i++) assertTrue(images.resolve(DOCUMENT, resources, i, false).isEmpty());
            assertEquals(1, lookups.get());
            images.clear();
            assertEquals(new RichTextImages.Stats(0, 0, 0, 0), images.stats());
            awaitFailure(images, resources);
            assertEquals(2, lookups.get());
        } finally {
            images.close();
        }
    }

    @Test void clearCancelsOldWorkAndClosedViewCannotQueueAnotherRequest() throws InterruptedException {
        var images = new RichTextImages();
        var started = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var blocked = new CountDownLatch(1);
        var oldPack = new MissingResources(location -> {
            started.countDown();
            try {
                blocked.await();
            } catch (InterruptedException interruption) {
                cancelled.countDown();
            }
        });
        var newLookups = new AtomicInteger();
        var newPack = new MissingResources(location -> newLookups.incrementAndGet());
        try {
            assertTrue(images.resolve(DOCUMENT, oldPack, 0, false).isEmpty());
            assertTrue(started.await(3, TimeUnit.SECONDS));
            images.clear();
            assertTrue(cancelled.await(3, TimeUnit.SECONDS));
            awaitFailure(images, newPack);
            assertEquals(new RichTextImages.Stats(0, 1, 0, 1), images.stats());
            images.close();
            assertTrue(images.resolve(DOCUMENT, newPack, 1, false).isEmpty());
            assertEquals(1, newLookups.get());
            assertEquals(new RichTextImages.Stats(0, 0, 0, 1), images.stats());
        } finally {
            blocked.countDown();
            images.close();
        }
    }

    private static void awaitFailure(RichTextImages images, ResourceManager resources) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            assertTrue(images.resolve(DOCUMENT, resources, 0, false).isEmpty());
            if (images.stats().failed() == 1) return;
            Thread.sleep(1);
        } while (System.nanoTime() < deadline);
        fail("Missing asset did not settle");
    }

    private record MissingResources(Consumer<ResourceLocation> lookup) implements ResourceManager {
        @Override public Set<String> getNamespaces() { return Set.of(); }
        @Override public Optional<Resource> getResource(ResourceLocation location) {
            lookup.accept(location);
            return Optional.empty();
        }
        @Override public List<Resource> getResourceStack(ResourceLocation location) { return List.of(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
            return Map.of();
        }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(
                String path, Predicate<ResourceLocation> filter) { return Map.of(); }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}

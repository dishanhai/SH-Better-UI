// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** Each view owns its requests, uploaded atlases and failed entries for the current resource generation. */
final class RichTextImages {
    private static final int MAX_STATIC_IMAGES = 128;
    private static final int MAX_FRAME_IMAGES = 64;
    private static final int MAX_REMOTE_IMAGES = 16;
    private static final int MAX_MOUNT_JOBS = 4;

    record Image(ResourceLocation location, int width, int height, int u, int v, int textureWidth, int textureHeight) {}
    record Stats(int ready, int failed, int active, long cancelled) {}
    private record RemoteBinding(URI uri, RichTextMediaPolicy policy) {}

    private static final class Entry {
        RichMediaJobs.Job<RichMediaTexture.Pixels> job;
        RichMediaTexture texture;
        boolean failed;
    }

    private final Map<UiId, Entry> sources = new LinkedHashMap<>();
    private final Map<UiId, RemoteBinding> remote = new LinkedHashMap<>();
    private final Map<UiId, RichMediaTexture> ready = new HashMap<>();
    private final Map<UiId, Image> visibleFrames = new HashMap<>();
    private Map<UiId, Image> snapshot = Map.of();
    private RichDocument scanned;
    private int mountedPixels;
    private int failed;
    private long cancelled;
    private boolean closed;

    Map<UiId, Image> resolve(RichDocument document, ResourceManager resources, long nowMs, boolean reducedMotion) {
        return resolve(document, resources, nowMs, reducedMotion, -1);
    }

    Map<UiId, Image> resolve(RichDocument document, ResourceManager resources, long nowMs, boolean reducedMotion,
                             long mediaElapsedMs) {
        if (closed) return Map.of();
        if (scanned != document) {
            if (!document.equals(scanned)) reconcile(document);
            scanned = document;
        }
        if (ready.size() + failed < sources.size()) {
            poll(nowMs);
            queue(resources);
        }
        boolean changed = snapshot.size() != ready.size();
        for (var entry : ready.entrySet()) {
            var texture = entry.getValue();
            var image = mediaElapsedMs < 0 ? texture.image(nowMs, reducedMotion)
                    : texture.imageAt(mediaElapsedMs, false);
            changed |= visibleFrames.put(entry.getKey(), image) != image;
        }
        if (changed) snapshot = Map.copyOf(visibleFrames);
        return snapshot;
    }

    long longestCycleMs() {
        long longest = 0;
        for (var texture : ready.values()) longest = Math.max(longest, texture.cycleMs());
        return longest;
    }

    void bindRemote(UiId id, URI uri, RichTextMediaPolicy policy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy").validate(uri);
        if (!validPath(id) || !id.path().endsWith(".png")) {
            throw new IllegalArgumentException("Expected a local PNG alias for the inline image");
        }
        if (closed) throw new IllegalStateException("Rich text media is closed");
        if (!remote.containsKey(id) && remote.size() >= MAX_REMOTE_IMAGES) {
            throw new IllegalStateException("Too many remote inline image bindings for this view");
        }
        var binding = new RemoteBinding(uri, policy);
        if (binding.equals(remote.get(id))) return;
        remote.put(id, binding);
        var previous = sources.put(id, new Entry());
        if (previous != null) release(id, previous);
    }

    void clear() {
        sources.forEach(this::release);
        sources.clear();
        snapshot = Map.of();
        scanned = null;
    }

    void close() {
        closed = true;
        clear();
        remote.clear();
    }

    Stats stats() {
        int active = 0;
        for (var entry : sources.values()) {
            if (entry.job != null) active++;
        }
        return new Stats(ready.size(), failed, active, cancelled);
    }

    RichTextView.MediaStatus status(UiId id) {
        var source = sources.get(Objects.requireNonNull(id, "id"));
        if (source == null) return RichTextView.MediaStatus.UNREQUESTED;
        if (source.texture != null) return RichTextView.MediaStatus.READY;
        return source.failed ? RichTextView.MediaStatus.UNAVAILABLE : RichTextView.MediaStatus.LOADING;
    }

    private void reconcile(RichDocument document) {
        var needed = new LinkedHashSet<UiId>();
        for (var node : document.nodes()) {
            if (node instanceof ImageNode image && validPath(image.texture())) {
                needed.add(image.texture());
                if (needed.size() == MAX_STATIC_IMAGES) break;
            }
        }
        int cap = needed.size() + MAX_FRAME_IMAGES;
        for (var node : document.nodes()) {
            if (node instanceof RichObjectNode object) {
                for (var texture : object.textures()) {
                    if (needed.size() < cap && validPath(texture)) needed.add(texture);
                }
            }
        }
        // The trusted binding itself authorizes a request regardless of what the document references.
        needed.addAll(remote.keySet());
        var iterator = sources.entrySet().iterator();
        while (iterator.hasNext()) {
            var source = iterator.next();
            if (!needed.contains(source.getKey())) {
                release(source.getKey(), source.getValue());
                iterator.remove();
            }
        }
        for (var id : needed) sources.computeIfAbsent(id, ignored -> new Entry());
    }

    private void poll(long nowMs) {
        for (var entry : sources.entrySet()) {
            var source = entry.getValue();
            if (source.job == null || !source.job.ready()) continue;
            try (var pixels = source.job.take()) {
                if (pixels != null) source.texture = RichMediaTexture.upload(pixels, mountedPixels, nowMs);
            } catch (RuntimeException failure) {
                // A failed GPU upload keeps the stable placeholder until the next generation.
            } finally {
                source.job = null;
            }
            if (source.texture == null) {
                source.failed = true;
                failed++;
                continue;
            }
            mountedPixels += source.texture.area();
            ready.put(entry.getKey(), source.texture);
            // Spread GPU uploads across frames, even when many CPU jobs complete together.
            break;
        }
    }

    private void queue(ResourceManager resources) {
        int active = 0;
        for (var entry : sources.values()) {
            if (entry.job != null) active++;
        }
        if (active >= MAX_MOUNT_JOBS) return;
        for (var entry : sources.entrySet()) {
            var source = entry.getValue();
            if (source.job != null || source.texture != null || source.failed) continue;
            var id = entry.getKey();
            var binding = remote.get(id);
            if (binding == null) {
                source.job = RichMediaJobs.submit(() -> RichMediaTexture.convert(decodePack(id, resources)),
                        () -> {}, false);
            } else {
                var call = new RichMediaRemote(binding.uri(), binding.policy());
                source.job = RichMediaJobs.submit(() -> RichMediaTexture.convert(
                        RichMediaDecoder.decode(call.download(), null)), call::cancel, true);
            }
            if (source.job != null && ++active == MAX_MOUNT_JOBS) break;
        }
    }

    private void release(UiId id, Entry source) {
        if (source.job != null) {
            source.job.cancel();
            cancelled++;
        }
        if (source.failed) failed--;
        if (source.texture != null) {
            ready.remove(id);
            visibleFrames.remove(id);
            mountedPixels -= source.texture.area();
            source.texture.release();
        }
    }

    private static boolean validPath(UiId id) {
        String path = id.path();
        if (id.namespace().length() > 64 || id.namespace().equals(".") || id.namespace().equals("..")
                || path.length() > 256
                || !path.endsWith(".png") && !path.endsWith(".apng") && !path.endsWith(".gif")) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }

    private static RichMediaDecoder.Sheet decodePack(UiId id, ResourceManager resources) throws IOException {
        var location = ResourceLocation.tryParse(id.toString());
        var resource = resources.getResource(location).orElseThrow(() -> new IOException("Missing inline image"));
        byte[] metadata = null;
        if (id.path().endsWith(".png")) {
            var metadataLocation = ResourceLocation.tryParse(id + ".mcmeta");
            var sidecar = resources.getResource(metadataLocation).orElse(null);
            if (sidecar != null && (sidecar.source() == resource.source()
                    || resources.listPacks().dropWhile(pack -> pack != resource.source())
                    .anyMatch(pack -> pack == sidecar.source()))) {
                try (var stream = sidecar.open()) {
                    metadata = RichMediaDecoder.read(stream, RichMediaPng.MAX_METADATA_BYTES);
                }
            }
        }
        try (var stream = resource.open()) {
            return RichMediaDecoder.decode(RichMediaDecoder.read(stream, RichMediaDecoder.MAX_BYTES), metadata);
        }
    }
}

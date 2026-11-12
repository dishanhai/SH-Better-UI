// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.List;
import java.util.Objects;

/** Inline replacement box. Runtime values and actions belong to the owning client view. */
public record RichObjectNode(UiId id, Content content, int intrinsicWidth, int intrinsicHeight,
                             int logicalHeight, String altText) implements InlineNode {
    public RichObjectNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(altText, "altText");
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Inline object dimensions must be positive");
        }
    }

    public List<UiId> textures() {
        return content instanceof Frames frames ? frames.textures() : List.of();
    }

    public sealed interface Content permits Frames, Item, Block, Fluid, Entity, Icon, Button, Slider {}

    /** Local PNG frames, sampled on the same frame clock as the host UI. */
    public record Frames(List<UiId> textures, int frameMillis) implements Content {
        public Frames {
            textures = List.copyOf(textures);
            if (textures.isEmpty() || textures.size() > 32 || frameMillis < 16 || frameMillis > 60_000) {
                throw new IllegalArgumentException("Expected 1 to 32 frames with a duration of 16 to 60000 ms");
            }
            if (textures.stream().anyMatch(texture -> !texture.path().endsWith(".png"))) {
                throw new IllegalArgumentException("Frame sequences require full PNG resource paths");
            }
        }

        public UiId textureAt(long frameTime, boolean reducedMotion) {
            return textures.get(reducedMotion ? 0 : Math.floorMod(Math.floorDiv(frameTime, frameMillis), textures.size()));
        }
    }

    public record Item(UiId item, int count) implements Content {
        public Item {
            Objects.requireNonNull(item, "item");
            if (count <= 0 || count > 99) throw new IllegalArgumentException("Item count must be between 1 and 99");
        }
    }

    /** The block's inventory model, identified by its block registry ID. */
    public record Block(UiId block) implements Content {
        public Block { Objects.requireNonNull(block, "block"); }
    }

    /** A source fluid's native atlas sprite and tint. */
    public record Fluid(UiId fluid) implements Content {
        public Fluid { Objects.requireNonNull(fluid, "fluid"); }
    }

    /** A fixed-pose preview of a living type while a client world is available. */
    public record Entity(UiId type) implements Content {
        public Entity { Objects.requireNonNull(type, "type"); }
    }

    public enum Symbol { CHECK, CHEVRON, STAR }

    public record Icon(Symbol symbol) implements Content {
        public Icon { Objects.requireNonNull(symbol, "symbol"); }
    }

    public record Button(String label) implements Content {
        public Button { requireLabel(label); }
    }

    /** The last step may be shorter so both endpoints remain reachable. */
    public record Slider(String label, int min, int max, int initial, int step) implements Content {
        public Slider {
            requireLabel(label);
            if (min >= max || initial < min || initial > max || step <= 0) {
                throw new IllegalArgumentException("Invalid slider range");
            }
            initial = closest(initial - (double) min, min, max, step);
        }

        public int valueAt(double fraction) {
            if (!Double.isFinite(fraction)) throw new IllegalArgumentException("Invalid slider fraction");
            double delta = Math.max(0d, Math.min(1d, fraction)) * ((long) max - min);
            return closest(delta, min, max, step);
        }

        public int stepFrom(int value, boolean increase) {
            long delta = Math.max(0L, Math.min((long) max - min, (long) value - min));
            if (increase) return (int) Math.min(max, min + (delta / step + 1) * step);
            return (int) (min + Math.max(0, delta - 1) / step * step);
        }

        private static int closest(double delta, int min, int max, int step) {
            long low = (long) (delta / step) * step;
            long high = Math.min((long) max - min, low + step);
            return (int) (min + (delta - low < high - delta ? low : high));
        }
    }

    private static void requireLabel(String label) {
        if (Objects.requireNonNull(label, "label").isBlank()) throw new IllegalArgumentException("Empty control label");
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.config;

import com.dishanhai.sh_better_ui.client.gui.panel.ConfigPageScreen;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** Native UUK configuration page for an immutable consumer-owned settings snapshot. */
public record ConfigPage<T>(Component title, Component description, List<NumberOption<T>> options,
                            List<Preset<T>> presets) {
    public ConfigPage {
        title = Objects.requireNonNull(title).copy();
        description = Objects.requireNonNull(description).copy();
        options = List.copyOf(options);
        presets = List.copyOf(presets);
        if (options.isEmpty() || presets.size() > 5 || options.stream().map(NumberOption::id).distinct().count() != options.size()) {
            throw new IllegalArgumentException("A config page requires unique options and supports up to five quick presets");
        }
        for (var preset : presets) {
            if (preset.values().isEmpty() || !options.containsAll(preset.values().keySet())) {
                throw new IllegalArgumentException("Preset refers to options outside its page");
            }
            preset.values().forEach((option, value) -> {
                if (!option.accepts(value)) throw new IllegalArgumentException("Invalid preset value for " + option.id());
            });
        }
    }

    /** Supports formatted components and text-only UUK documents in titles, labels and descriptions. */
    public static Component richText(RichDocument document) {
        return UiHudRichText.prepare(document);
    }

    /**
     * Opens with an isolated draft. Done invokes the commit once for a changed, valid draft.
     * Return false to display a failed-save message and leave the draft open for retry.
     * Pass a supplier to restore focus when the parent rebuilds its widgets.
     */
    public Screen screen(Screen parent, T current, Predicate<T> commit,
                         Supplier<? extends GuiEventListener> returnFocus) {
        return new ConfigPageScreen<>(this, parent, current, commit, returnFocus);
    }

    public Screen screen(Screen parent, T current, Predicate<T> commit) {
        var previous = parent == null ? null : parent.getFocused();
        return screen(parent, current, commit, () -> previous);
    }

    @FunctionalInterface
    public interface NumberEdit<T> {
        /** Returns a new snapshot with the field updated. */
        T apply(T current, double value);
    }

    public record NumberOption<T>(String id, Component label, Component description,
                                  double min, double max, boolean integral,
                                  ToDoubleFunction<T> read, NumberEdit<T> edit) {
        public NumberOption {
            Objects.requireNonNull(id);
            label = Objects.requireNonNull(label).copy();
            description = Objects.requireNonNull(description).copy();
            Objects.requireNonNull(read);
            Objects.requireNonNull(edit);
            if (id.isBlank() || !Double.isFinite(min) || !Double.isFinite(max) || min >= max
                    || integral && (min != Math.rint(min) || max != Math.rint(max))) {
                throw new IllegalArgumentException("Invalid numeric option " + id);
            }
        }

        public boolean accepts(double value) {
            return Double.isFinite(value) && value >= min && value <= max && (!integral || value == Math.rint(value));
        }
    }

    public record Preset<T>(Component label, Component description, Map<NumberOption<T>, Double> values) {
        public Preset {
            label = Objects.requireNonNull(label).copy();
            description = Objects.requireNonNull(description).copy();
            values = Map.copyOf(values);
        }
    }
}

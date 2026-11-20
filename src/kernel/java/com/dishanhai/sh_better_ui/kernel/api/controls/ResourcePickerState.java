// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/** Resource browsing and a single replacement draft. Confirmation is the only commit point. */
public final class ResourcePickerState<T> {
    public enum Sort { DEFAULT, NAME, MOD, ID, COUNT }
    public record Entry<T>(String key, String id, String name, String mod, List<String> tags,
                           Supplier<String> tooltip, int amount, T value) {
        public Entry {
            Objects.requireNonNull(key); Objects.requireNonNull(id); Objects.requireNonNull(name);
            Objects.requireNonNull(mod); Objects.requireNonNull(tooltip); Objects.requireNonNull(value);
            tags = List.copyOf(tags);
            if (amount < 1) throw new IllegalArgumentException("amount must be positive");
        }
        public String namespace() { int colon = id.indexOf(':'); return colon < 0 ? "" : id.substring(0, colon); }
    }
    private List<Entry<T>> source = List.of();
    private Entry<T> selected;
    private int amount = 1;
    private boolean finished;

    public ResourcePickerState(Entry<T> initial) {
        selected = initial;
        if (initial != null) amount = initial.amount();
    }
    public void source(List<Entry<T>> values) { source = List.copyOf(values); }
    public List<Entry<T>> source() { return source; }
    public Entry<T> selected() { return selected; }
    public int amount() { return amount; }
    public boolean finished() { return finished; }

    /** Left-click activates an existing value; right-click toggles it out of the draft. */
    public void choose(Entry<T> entry, boolean toggle) {
        if (finished) return;
        Objects.requireNonNull(entry);
        if (selected != null && selected.key().equals(entry.key())) {
            if (toggle) selected = null;
            return;
        }
        selected = entry;
        amount = entry.amount();
    }
    public void clear() { if (!finished) selected = null; }
    public boolean amount(String text) {
        try {
            int value = Integer.parseInt(text);
            if (finished || value < 1) return false;
            amount = value;
            return true;
        } catch (NumberFormatException ignored) { return false; }
    }
    public void stepAmount(int direction, int step) {
        if (!finished) amount = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) amount + (long) direction * step));
    }
    public Optional<Entry<T>> finish(boolean confirm) {
        if (finished) return Optional.empty();
        finished = true;
        return confirm ? Optional.ofNullable(selected) : Optional.empty();
    }

    /** GTSH search conventions: name/ID, @mod, #tag, *ID and lazy $tooltip, combined with AND. */
    public List<Entry<T>> search(String query, String module, Sort sort, boolean grouped) {
        return search(query, module, sort, grouped, (entry, word) -> false);
    }

    /** Allows platform search indexes to supply additional matches for ordinary words. */
    public List<Entry<T>> search(String query, String module, Sort sort, boolean grouped,
                                 BiPredicate<Entry<T>, String> additionalMatch) {
        return search(query, module, sort, grouped, additionalMatch, entry -> entry.tooltip().get());
    }

    /** Allows a picker session to cache tooltip text independently of its shared catalog. */
    public List<Entry<T>> search(String query, String module, Sort sort, boolean grouped,
                                 BiPredicate<Entry<T>, String> additionalMatch, Function<Entry<T>, String> tooltip) {
        String normalizedQuery = normalized(query);
        if (normalizedQuery.isEmpty() && module.isEmpty() && sort == Sort.DEFAULT && !grouped) return source;
        var stream = source.stream();
        if (!module.isEmpty()) stream = stream.filter(entry -> entry.namespace().equals(module));
        if (!normalizedQuery.isEmpty()) {
            String[] words = normalizedQuery.split("\\s+");
            stream = stream.filter(entry -> matches(entry, words, additionalMatch, tooltip));
        }
        Comparator<Entry<T>> order = switch (sort) {
            case DEFAULT -> null;
            case NAME -> Comparator.comparing(entry -> normalized(entry.name()));
            case MOD -> Comparator.comparing(entry -> normalized(entry.mod()));
            case ID -> Comparator.comparing(Entry::id);
            case COUNT -> Comparator.<Entry<T>>comparingInt(Entry::amount).reversed();
        };
        if (grouped) {
            Comparator<Entry<T>> modules = Comparator.comparing(Entry::namespace);
            order = order == null ? modules : modules.thenComparing(order);
        }
        return (order == null ? stream : stream.sorted(order)).toList();
    }
    private static <T> boolean matches(Entry<T> entry, String[] words, BiPredicate<Entry<T>, String> additionalMatch,
                                       Function<Entry<T>, String> tooltip) {
        for (String word : words) {
            if (word.isEmpty()) continue;
            String value = word.substring(1);
            boolean matches = switch (word.charAt(0)) {
                case '@' -> normalized(entry.namespace() + " " + entry.mod()).contains(value);
                case '#' -> entry.tags().stream().anyMatch(tag -> normalized(tag).contains(value));
                case '*' -> normalized(entry.id()).contains(value);
                case '$' -> normalized(tooltip.apply(entry)).contains(value);
                default -> normalized(entry.name() + " " + entry.id()).contains(word) || additionalMatch.test(entry, word);
            };
            if (!matches) return false;
        }
        return true;
    }
    private static String normalized(String value) { return value.toLowerCase(Locale.ROOT).strip(); }

    public record Layout(int x, int y, int width, int height, int sidebar, int gridX, int gridWidth,
                         int selectionX, int selectionWidth, int contentY, int contentHeight, int footerY) {}

    /** Same left/source, center/grid, right/draft structure as GTSH, scaled to the actual GUI. */
    public static Layout layout(int width, int height) {
        int panelWidth = Math.max(0, Math.min(700, width - Math.max(16, Math.min(64, width / 12))));
        int panelHeight = Math.max(0, Math.min(420, height - Math.max(12, Math.min(44, height / 12))));
        int x = (width - panelWidth) / 2, y = (height - panelHeight) / 2;
        int sidebar = Math.min(104, Math.max(62, panelWidth / 6));
        int selected = Math.min(214, Math.max(100, panelWidth / 3));
        int gridX = x + sidebar + 12, selectedX = x + panelWidth - selected;
        int contentY = y + 49, footerY = y + panelHeight - 23;
        return new Layout(x, y, panelWidth, panelHeight, sidebar, gridX,
                Math.max(0, selectedX - 6 - gridX), selectedX, selected,
                contentY, Math.max(0, footerY - 6 - contentY), footerY);
    }
}

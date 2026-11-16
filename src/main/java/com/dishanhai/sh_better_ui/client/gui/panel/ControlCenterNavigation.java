// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounded, platform-free navigation journal. It never owns or applies the settings draft. */
public final class ControlCenterNavigation {
    public record Location(String area, String section, String query, int scroll) {
        public Location {
            Objects.requireNonNull(area); Objects.requireNonNull(section);
            query = query == null ? "" : query;
            scroll = Math.max(0, scroll);
        }
    }
    public record Entry(String area, String section, String title, String keywords) {}
    private final List<Location> entries = new ArrayList<>();
    private int cursor;
    public ControlCenterNavigation(Location initial) { entries.add(Objects.requireNonNull(initial)); }
    public Location current() { return entries.get(cursor); }
    public boolean canBack() { return cursor > 0; }
    public boolean canForward() { return cursor + 1 < entries.size(); }
    public void replace(Location location) { entries.set(cursor, Objects.requireNonNull(location)); }
    public void visit(Location location) {
        Objects.requireNonNull(location);
        if (current().equals(location)) return;
        entries.subList(cursor + 1, entries.size()).clear();
        entries.add(location);
        if (entries.size() > 64) entries.remove(0);
        cursor = entries.size() - 1;
    }
    public Location back() { if (canBack()) cursor--; return current(); }
    public Location forward() { if (canForward()) cursor++; return current(); }
    public static List<Entry> search(List<Entry> entries, String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return List.copyOf(entries);
        String[] words = normalized.split("\\s+");
        return entries.stream().filter(entry -> {
            String haystack = normalize(entry.title() + " " + entry.keywords());
            for (String word : words) if (!haystack.contains(word)) return false;
            return true;
        }).toList();
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }
}

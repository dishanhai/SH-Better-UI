// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;

import java.util.Map;
import java.util.TreeMap;

/** Versioned UTF-8 layout payload codec. The caller owns the file, synchronization, and write scheduling. */
public final class DockPersistence {
    private static final String VERSION_1 = "version=1";
    private static final String VERSION_2 = "version=2";

    private DockPersistence() {}

    /** Serializes rows in ID order, with one tab-separated row per placement. */
    public static String encode(Map<UiId, WindowPlacement> placements) {
        var text = new StringBuilder(VERSION_2).append('\n');
        new TreeMap<>(placements).forEach((id, placement) ->
                text.append(id).append('\t').append(placement.edge()).append('\t').append(placement.mode())
                        .append('\t').append(placement.x()).append('\t').append(placement.y())
                        .append('\t').append(placement.width()).append('\t').append(placement.height())
                        .append('\t').append(placement.pinned()).append('\t')
                        .append(placement.group() == null ? "" : placement.group()).append('\n'));
        return text.toString();
    }

    /**
     * Version 1 records are migrated by supplying an empty group. Malformed rows are skipped,
     * and the first valid row for a window wins. Unsupported headers fail the whole read so
     * the caller can preserve data written by a newer version.
     *
     * @throws IllegalArgumentException if the layout has a missing or unsupported version
     */
    public static Map<UiId, WindowPlacement> decode(String text) {
        var lines = text.lines().iterator();
        String header = lines.hasNext() ? lines.next() : "";
        boolean hasGroup = switch (header) {
            case VERSION_1 -> false;
            case VERSION_2 -> true;
            default -> throw new IllegalArgumentException("Unsupported dock layout version");
        };
        int fieldCount = hasGroup ? 9 : 8;
        Map<UiId, WindowPlacement> placements = new TreeMap<>();
        while (lines.hasNext()) {
            String[] fields = lines.next().split("\t", fieldCount + 1);
            if (fields.length != fieldCount) continue;
            try {
                UiId id = UiId.parse(fields[0]);
                boolean pinned = switch (fields[7]) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new IllegalArgumentException("Invalid pinned flag");
                };
                UiId group = hasGroup && !fields[8].isEmpty() ? UiId.parse(fields[8]) : null;
                WindowPlacement placement = new WindowPlacement(DockEdge.valueOf(fields[1]), DockMode.valueOf(fields[2]),
                        Double.parseDouble(fields[3]), Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
                        Double.parseDouble(fields[6]), pinned, group);
                placements.putIfAbsent(id, placement);
            } catch (IllegalArgumentException ignored) {
                // Recover valid placements when one row has been edited or damaged.
            }
        }
        return Map.copyOf(placements);
    }
}

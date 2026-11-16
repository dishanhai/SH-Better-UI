// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;
import java.util.*;
/** Loader metadata only; no optional mod code is initialized. */
public record InstalledModData(String id,String name,String version,String description) {
    public InstalledModData { id=safe(id);name=safe(name);version=safe(version);description=safe(description); }
    private static String safe(String value){return value==null?"":value.substring(0,Math.min(8192,value.length()));}
    public static List<InstalledModData> filter(List<InstalledModData> mods,String query){
        String q=Objects.requireNonNullElse(query,"").strip().toLowerCase(Locale.ROOT);
        return mods.stream().filter(m->(m.id()+" "+m.name()+" "+m.version()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(InstalledModData::name,String.CASE_INSENSITIVE_ORDER).thenComparing(InstalledModData::id)).toList();
    }
}

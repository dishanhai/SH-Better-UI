// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.util.*;
import java.util.function.*;

/** UI-thread bounded deferral for the legacy island. History remains owned by the session. */
public final class MusicOperationAdmission {
    private final Map<String,MusicOperationNotices.Update> pending = new LinkedHashMap<>();
    private final Set<String> admitted = new HashSet<>();
    public void update(List<MusicOperationNotices.Update> updates, Consumer<String> cancel) {
        for(var update:updates) {
            pending.remove(update.id());
            if(admitted.remove(update.id()))cancel.accept(update.id());
            // The session source itself retains at most 32 jobs; avoid unbounded foreign input too.
            if(!update.cancel() && pending.size()<32)pending.put(update.id(),update);
        }
    }
    public void flush(Predicate<MusicOperationNotices.Update> offer) {
        for(var update:List.copyOf(pending.values()))if(offer.test(update)) {
            pending.remove(update.id());admitted.add(update.id());
        }
    }
    public void clear(Consumer<String> cancel) {
        pending.clear();for(var id:admitted)cancel.accept(id);admitted.clear();
    }
    int pendingCount() { return pending.size(); }
}

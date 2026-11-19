// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.util.*;

/** UI-thread observer, not a job owner. Never copies query/result/account data into notices. */
public final class MusicOperationNotices {
    public record Update(String id, long operationId, String key, boolean cancel, boolean error, long ttlMs) {}
    private final Map<Long, SessionMusicOperations.Snapshot> observed = new HashMap<>();
    public List<Update> observe(List<SessionMusicOperations.Snapshot> snapshots, boolean enabled) {
        var updates=new ArrayList<Update>();
        var ids=new HashSet<Long>();
        for(var next:snapshots) {
            ids.add(next.id());
            var previous=observed.put(next.id(),next);
            if(Objects.equals(previous,next))continue;
            String id="music:operation/"+next.id();
            updates.add(new Update(id,next.id(),"",true,false,0));
            if(!enabled||!next.background())continue;
            String state=switch(next.state()) {
                case RUNNING -> "running";
                case SUCCEEDED -> "succeeded";
                case FAILED -> switch (next.error()) { case LOGIN_REQUIRED -> "login_required"; case UNKNOWN -> "unknown"; case REJECTED -> "rejected"; case EXPIRED -> "expired"; case BUSY -> null; default -> "failed"; };
                default -> null;
            };
            if(state!=null)updates.add(new Update(id,next.id(),"operation_"+next.kind().name().toLowerCase(Locale.ROOT)+"_"+state,
                    false,next.state()==SessionMusicOperations.State.FAILED && next.error()!=SessionMusicOperations.Error.UNKNOWN,next.state()==SessionMusicOperations.State.RUNNING?8000:next.state()==SessionMusicOperations.State.FAILED?5000:3500));
        }
        for(var id:List.copyOf(observed.keySet()))if(!ids.contains(id)) {
            observed.remove(id);updates.add(new Update("music:operation/"+id,id,"",true,false,0));
        }
        // Sampling while disabled consumes state transitions, so enabling cannot replay old work.
        return List.copyOf(updates);
    }
    public void reset() { observed.clear(); }
}

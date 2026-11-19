// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;
import java.util.List;
/** Read-only notice seam. Observing never allocates a player or starts network work. */
public interface MusicOperationSource {
    List<SessionMusicOperations.Snapshot> operationsSnapshot();
    default java.util.Optional<MusicOperationNavigation.Target> operationTarget(long id) {
        return java.util.Optional.empty();
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.util.Optional;

/** An opaque link to an existing history entry, never an instruction to run that operation. */
public final class MusicOperationNavigation {
    private MusicOperationNavigation() {}

    public static Optional<Target> target(SessionMusicOperations operations, long id) {
        synchronized (operations) {
            return operations.find(id).map(handle -> new Target(operations, handle))
                    .filter(Target::valid);
        }
    }

    public static final class Target {
        private final SessionMusicOperations operations;
        private final SessionMusicOperations.Handle<?> handle;
        private final long accountVersion;

        private Target(SessionMusicOperations operations, SessionMusicOperations.Handle<?> handle) {
            this.operations = operations;
            this.handle = handle;
            this.accountVersion = handle.accountVersion();
        }

        public long id() { return handle.id(); }
        public boolean validFor(SessionMusicOperations current) { return operations == current && valid(); }
        public boolean valid() {
            synchronized (operations) {
                if (handle.accountVersion() != accountVersion || !handle.valid()) return false;
                var snapshot = handle.snapshot();
                return snapshot.background() && switch (snapshot.state()) {
                    case RUNNING, SUCCEEDED, FAILED -> true;
                    default -> false;
                };
            }
        }
    }
}

// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.Objects;

/** A one-shot return to the exact route stack when native browser confirmation remounts the workspace. */
final class MusicFrontendBrowserReturn {
    private Ticket pending;

    static final class Ticket {
        final MusicFrontendRoutes.Bookmark navigation;
        final String draft;

        private Ticket(MusicFrontendRoutes.Bookmark navigation, String draft) {
            this.navigation = navigation;
            this.draft = draft;
        }
    }

    Ticket begin(MusicFrontendRoutes routes, String draft) {
        pending = new Ticket(routes.bookmark(), Objects.requireNonNull(draft));
        return pending;
    }

    void discard(Ticket ticket) {
        if (pending == ticket) pending = null;
    }

    boolean restore(Ticket ticket, MusicFrontendRoutes origin, String latestDraft, MusicFrontendRoutes destination) {
        if (ticket == null || pending != ticket) return false;
        pending = null;
        if (origin.generation() != ticket.navigation.generation() || !ticket.draft.equals(latestDraft)) return false;
        return origin == destination || destination.restoreFresh(ticket.navigation);
    }
}

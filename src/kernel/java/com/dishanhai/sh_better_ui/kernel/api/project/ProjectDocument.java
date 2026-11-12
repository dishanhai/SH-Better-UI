// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.project;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable Java 17 project revision. Schema 1 contains existing rich text documents. */
public record ProjectDocument(Stamp stamp, Map<UiId, RichDocument> documents) {
    public static final int SCHEMA_VERSION = 1;

    public ProjectDocument {
        Objects.requireNonNull(stamp, "stamp");
        var sorted = new TreeMap<>(Map.copyOf(documents));
        documents = Collections.unmodifiableMap(sorted);
    }

    public Optional<RichDocument> richText(UiId id) {
        return Optional.ofNullable(documents.get(Objects.requireNonNull(id, "id")));
    }

    /** The session ID distinguishes independent histories with the same project ID and revision. */
    public record Stamp(UiId projectId, long revision, int schemaVersion, UUID sessionId) {
        private static final UUID DETACHED_SESSION = new UUID(0, 0);

        /** Creates a detached stamp for a read-only snapshot. Use a session's stamp when editing. */
        public Stamp(UiId projectId, long revision, int schemaVersion) {
            this(projectId, revision, schemaVersion, DETACHED_SESSION);
        }

        public Stamp {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(sessionId, "sessionId");
            if (revision < 0 || schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Invalid project revision or schema");
            }
        }
    }
}

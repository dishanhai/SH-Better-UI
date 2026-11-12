// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Immutable, bounded, passive messages for a single watched document per play connection. */
public final class RichSyncProtocol {
    public static final int VERSION = 1;
    public static final int DELTAS = 1;
    public static final int EDITS = 2;
    public static final int CAPABILITIES = DELTAS | EDITS;
    public static final int MAX_FIELDS = 32;
    public static final int MAX_ID_BYTES = 128;
    public static final int MAX_TEXT_BYTES = 512;

    private RichSyncProtocol() {}

    public sealed interface Value permits Text, Number {}

    /** Plain single-line text for trusted local templates to render literally. */
    public record Text(String text) implements Value {
        public Text {
            Objects.requireNonNull(text, "text");
            if (text.length() > MAX_TEXT_BYTES) throw new IllegalArgumentException("Text exceeds field limit");
            for (int offset = 0; offset < text.length();) {
                int point = text.codePointAt(offset);
                if (Character.isISOControl(point) || point == 0xA7 || (point >= 0xD800 && point <= 0xDFFF)) {
                    throw new IllegalArgumentException("Invalid plain text");
                }
                offset += Character.charCount(point);
            }
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
                throw new IllegalArgumentException("Text exceeds field limit");
            }
        }
    }

    public record Number(long number) implements Value {}

    public sealed interface ClientMessage permits Hello, WatchedRequest {}

    public record Hello(UUID nonce, int minVersion, int maxVersion, int capabilities) implements ClientMessage {
        public Hello {
            Objects.requireNonNull(nonce, "nonce");
            if (minVersion < 1 || maxVersion < minVersion || maxVersion > 255) {
                throw new IllegalArgumentException("Invalid protocol version range");
            }
            checkCapabilities(capabilities);
        }
    }

    public sealed interface WatchedRequest extends ClientMessage permits Subscribe, Unsubscribe, Resync, Edit {
        UUID session();
        long watchId();
    }

    public record Subscribe(UUID session, long watchId, UiId document) implements WatchedRequest {
        public Subscribe {
            checkWatch(session, watchId);
            checkId(document);
        }
    }

    public record Unsubscribe(UUID session, long watchId) implements WatchedRequest {
        public Unsubscribe { checkWatch(session, watchId); }
    }

    public record Resync(UUID session, long watchId) implements WatchedRequest {
        public Resync { checkWatch(session, watchId); }
    }

    public record Edit(UUID session, long watchId, long requestId, long revision, UiId key, Value value)
            implements WatchedRequest {
        public Edit {
            checkWatch(session, watchId);
            positive(requestId);
            positive(revision);
            checkId(key);
            Objects.requireNonNull(value, "value");
        }
    }

    public sealed interface ServerMessage permits Welcome, Unsupported, WatchedResponse {}

    public record Welcome(UUID nonce, UUID session, int version, int capabilities) implements ServerMessage {
        public Welcome {
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(session, "session");
            if (version < 1 || version > 255 || (capabilities & DELTAS) == 0) {
                throw new IllegalArgumentException("Invalid negotiated protocol");
            }
            checkCapabilities(capabilities);
        }
    }

    public record Unsupported(UUID nonce) implements ServerMessage {
        public Unsupported { Objects.requireNonNull(nonce, "nonce"); }
    }

    public sealed interface WatchedResponse extends ServerMessage permits Snapshot, Patch, Result, Failure {
        UUID session();
        long watchId();
    }

    public record Snapshot(UUID session, long watchId, long revision, boolean writable, Map<UiId, Value> values)
            implements WatchedResponse {
        public Snapshot {
            checkWatch(session, watchId);
            positive(revision);
            values = copyValues(values);
        }
    }

    public record Patch(UUID session, long watchId, long baseRevision, long revision,
                        Map<UiId, Value> changed, Set<UiId> removed) implements WatchedResponse {
        public Patch {
            checkWatch(session, watchId);
            positive(baseRevision);
            positive(revision);
            if (revision - 1 != baseRevision) throw new IllegalArgumentException("Nonconsecutive revision");
            changed = copyValues(changed);
            Objects.requireNonNull(removed, "removed");
            if (removed.size() > MAX_FIELDS || (changed.isEmpty() && removed.isEmpty())) {
                throw new IllegalArgumentException("Invalid patch size");
            }
            var sorted = new TreeSet<UiId>();
            for (UiId key : removed) {
                checkId(key);
                if (changed.containsKey(key)) throw new IllegalArgumentException("Patch both changes and removes a key");
                sorted.add(key);
            }
            removed = Collections.unmodifiableSet(sorted);
        }
    }

    public enum Outcome { ACCEPTED, DENIED, STALE, INVALID, RATE_LIMITED, FAILED, TIMED_OUT }

    public record Result(UUID session, long watchId, long requestId, long revision, Outcome outcome)
            implements WatchedResponse {
        public Result {
            checkWatch(session, watchId);
            positive(requestId);
            positive(revision);
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == Outcome.TIMED_OUT) throw new IllegalArgumentException("Timeout is local client state");
        }
    }

    public record Failure(UUID session, long watchId, Outcome outcome) implements WatchedResponse {
        public Failure {
            checkWatch(session, watchId);
            if (outcome != Outcome.DENIED && outcome != Outcome.FAILED) {
                throw new IllegalArgumentException("Invalid watch failure");
            }
        }
    }

    static Map<UiId, Value> copyValues(Map<UiId, Value> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_FIELDS) throw new IllegalArgumentException("Too many synchronized fields");
        var sorted = new TreeMap<UiId, Value>();
        values.forEach((key, value) -> {
            checkId(key);
            sorted.put(key, Objects.requireNonNull(value, "value"));
        });
        return Collections.unmodifiableMap(sorted);
    }

    static void checkId(UiId id) {
        Objects.requireNonNull(id, "id");
        if ((long) id.namespace().length() + id.path().length() + 1 > MAX_ID_BYTES) {
            throw new IllegalArgumentException("ID exceeds field limit");
        }
    }

    private static void checkWatch(UUID session, long watchId) {
        Objects.requireNonNull(session, "session");
        positive(watchId);
    }

    private static void checkCapabilities(int capabilities) {
        if (capabilities < 0 || capabilities > 255) throw new IllegalArgumentException("Invalid capability flags");
    }

    private static void positive(long value) {
        if (value < 1) throw new IllegalArgumentException("Expected a positive sequence, revision or request ID");
    }
}

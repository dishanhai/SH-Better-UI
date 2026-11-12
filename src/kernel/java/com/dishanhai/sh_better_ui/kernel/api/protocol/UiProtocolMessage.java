// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A typed message for one physical connection. Page instances have independent IDs. */
public record UiProtocolMessage(UiVersion version, UUID sessionId, long sequence, Payload payload) {
    public UiProtocolMessage {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(payload, "payload");
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
    }

    public Type type() {
        return payload.type();
    }

    public enum Type {
        HELLO(1), CAPABILITIES(2), PAGE_OPEN_REQUEST(3), PAGE_OPEN_RESULT(4), PAGE_CLOSE(5), DATA_UPDATE(6),
        ACTION_REQUEST(7), ACTION_RESULT(8), HUD_MOUNT(9), HUD_UNMOUNT(10), NOTIFICATION_PUSH(11),
        SESSION_TERMINATE(12), ERROR(13), STATE_REQUEST(14), STATE_SNAPSHOT(15), ACTION_CHALLENGE(16),
        ACTION_CONFIRM(17), INTERACTION_REQUEST(18), AUTHORIZED_PAGE_OPEN(19), PAGE_VISIBILITY(20), PAGE_RESUMED(21),
        INTERACTION_REJECTED(22);

        private static final Type[] TYPES = values();
        final int wireTag;

        Type(int wireTag) {
            this.wireTag = wireTag;
        }

        static Type fromWireTag(int wireTag) {
            for (Type type : TYPES) {
                if (type.wireTag == wireTag) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown page message type");
        }
    }

    public sealed interface Payload permits Hello, Capabilities, PagePayload, Terminate, Error, InteractionRequest,
            InteractionRejected {
        Type type();
    }

    public sealed interface PagePayload extends Payload permits PageOpen, PageOpened, PageClose, HudUnmount,
            DataUpdate, StateRequest, StateSnapshot, PageVisibility, PageResumed, ActionPayload {
        UUID instanceId();
    }

    public sealed interface ActionPayload extends PagePayload permits ActionRequest, ActionResult, ActionChallenge, ActionConfirm {
        UUID requestId();
    }

    public record Hello(UiProtocolCapabilities capabilities) implements Payload {
        public Hello { Objects.requireNonNull(capabilities, "capabilities"); }
        @Override public Type type() { return Type.HELLO; }
    }

    public record Capabilities(UiProtocolCapabilities capabilities) implements Payload {
        public Capabilities { Objects.requireNonNull(capabilities, "capabilities"); }
        @Override public Type type() { return Type.CAPABILITIES; }
    }

    public record PageOpen(UUID instanceId, UiPageDefinition page, Map<String, UiValue> data,
                           Optional<UiInteractionProof> interaction, boolean highPriority) implements PagePayload {
        public PageOpen(UUID instanceId, UiPageDefinition page, Map<String, UiValue> data) {
            this(instanceId, page, data, Optional.empty(), false);
        }

        public PageOpen {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(page, "page");
            data = Map.copyOf(data);
            Objects.requireNonNull(interaction, "interaction");
            if (highPriority && page.type() != UiPageType.MODAL) {
                throw new IllegalArgumentException("High priority requires a modal page");
            }
            if (interaction.isPresent() && interaction.orElseThrow().target() instanceof UiInteractionTarget.Page target
                    && !target.pageId().equals(page.id())) {
                throw new IllegalArgumentException("Interaction target and page ID disagree");
            }
        }

        @Override
        public Type type() {
            if (interaction.isPresent() || highPriority) return Type.AUTHORIZED_PAGE_OPEN;
            return switch (page.type()) {
                case HUD -> Type.HUD_MOUNT;
                case NOTIFICATION -> Type.NOTIFICATION_PUSH;
                default -> Type.PAGE_OPEN_REQUEST;
            };
        }
    }

    public record PageOpened(UUID instanceId, UiOpenResult.Status status, Optional<UiErrorCode> error)
            implements PagePayload {
        public PageOpened {
            new UiOpenResult(instanceId, status, error);
            if (status != UiOpenResult.Status.RECEIVED && status != UiOpenResult.Status.AUTHORIZED
                    && status != UiOpenResult.Status.CONFIRMED && status != UiOpenResult.Status.DISPLAYED
                    && status != UiOpenResult.Status.REJECTED) {
                throw new IllegalArgumentException("Invalid client open acknowledgment");
            }
        }
        @Override public Type type() { return Type.PAGE_OPEN_RESULT; }
    }

    public record PageClose(UUID instanceId) implements PagePayload {
        public PageClose { Objects.requireNonNull(instanceId, "instanceId"); }
        @Override public Type type() { return Type.PAGE_CLOSE; }
    }

    public record HudUnmount(UUID instanceId) implements PagePayload {
        public HudUnmount { Objects.requireNonNull(instanceId, "instanceId"); }
        @Override public Type type() { return Type.HUD_UNMOUNT; }
    }

    public record DataUpdate(UUID instanceId, long baseRevision, long revision, Map<String, UiValue> updates)
            implements PagePayload {
        public DataUpdate {
            Objects.requireNonNull(instanceId, "instanceId");
            if (baseRevision < 0 || revision <= baseRevision) {
                throw new IllegalArgumentException("Invalid data update revision");
            }
            updates = Map.copyOf(updates);
            if (updates.isEmpty()) {
                throw new IllegalArgumentException("Empty data update");
            }
        }
        @Override public Type type() { return Type.DATA_UPDATE; }
    }

    public record StateRequest(UUID instanceId) implements PagePayload {
        public StateRequest { Objects.requireNonNull(instanceId, "instanceId"); }
        @Override public Type type() { return Type.STATE_REQUEST; }
    }

    public record StateSnapshot(UUID instanceId, long revision, Map<String, UiValue> data) implements PagePayload {
        public StateSnapshot {
            Objects.requireNonNull(instanceId, "instanceId");
            if (revision < 0) {
                throw new IllegalArgumentException("Invalid snapshot revision");
            }
            data = Map.copyOf(data);
        }
        @Override public Type type() { return Type.STATE_SNAPSHOT; }
    }

    public record ActionRequest(UUID instanceId, UUID requestId, UiId actionId, long revision,
                                Map<String, UiValue> data) implements ActionPayload {
        public ActionRequest {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(actionId, "actionId");
            if (revision < 0) {
                throw new IllegalArgumentException("Invalid action revision");
            }
            data = Map.copyOf(data);
        }
        @Override public Type type() { return Type.ACTION_REQUEST; }
    }

    public record ActionResult(UUID instanceId, UUID requestId, UiActionState state, Optional<UiErrorCode> error)
            implements ActionPayload {
        public ActionResult {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(error, "error");
            if (state == UiActionState.IDLE || state == UiActionState.PENDING
                    || (state == UiActionState.SUCCEEDED) == error.isPresent()) {
                throw new IllegalArgumentException("Invalid action result status or error");
            }
        }
        @Override public Type type() { return Type.ACTION_RESULT; }
    }

    public record ActionChallenge(UUID instanceId, UUID requestId, UUID token, UiActionPrompt prompt)
            implements ActionPayload {
        public ActionChallenge {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(prompt, "prompt");
        }

        @Override public Type type() { return Type.ACTION_CHALLENGE; }
    }

    public record ActionConfirm(UUID instanceId, UUID requestId, UUID token, boolean accepted)
            implements ActionPayload {
        public ActionConfirm {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(token, "token");
        }

        @Override public Type type() { return Type.ACTION_CONFIRM; }
    }

    public record InteractionRequest(UiInteractionProof proof) implements Payload {
        public InteractionRequest { Objects.requireNonNull(proof, "proof"); }
        @Override public Type type() { return Type.INTERACTION_REQUEST; }
    }

    public record InteractionRejected(long attempt, UUID token, UiErrorCode error) implements Payload {
        public InteractionRejected {
            if (attempt < 1) throw new IllegalArgumentException("Interaction attempt must be positive");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(error, "error");
        }

        @Override public Type type() { return Type.INTERACTION_REJECTED; }
    }

    public record PageVisibility(UUID instanceId, boolean suspended) implements PagePayload {
        public PageVisibility { Objects.requireNonNull(instanceId, "instanceId"); }
        @Override public Type type() { return Type.PAGE_VISIBILITY; }
    }

    public record PageResumed(UUID instanceId, long revision, Map<String, UiValue> data,
                              Optional<UiErrorCode> error) implements PagePayload {
        public PageResumed {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(error, "error");
            data = Map.copyOf(data);
            if (revision < 0 || error.isPresent() && !data.isEmpty()) {
                throw new IllegalArgumentException("Invalid page resume result");
            }
        }

        @Override public Type type() { return Type.PAGE_RESUMED; }
    }

    public record Terminate() implements Payload {
        @Override public Type type() { return Type.SESSION_TERMINATE; }
    }

    public record Error(UiErrorCode code) implements Payload {
        public Error { Objects.requireNonNull(code, "code"); }
        @Override public Type type() { return Type.ERROR; }
    }
}
